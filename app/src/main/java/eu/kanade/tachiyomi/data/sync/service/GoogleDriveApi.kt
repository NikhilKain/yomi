package eu.kanade.tachiyomi.data.sync.service

import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.sync.service.SyncPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Clock

/**
 * Minimal Google Drive v3 client, talking to the REST API over the app existing OkHttp stack
 * rather than pulling in the Play Services and google-api-client dependency tree.
 *
 * Uses the `drive.file` scope, which only grants access to files this app itself created. That is
 * enough for a single sync payload and, unlike `drive.appdata`, it is a non-sensitive scope, so a
 * self-built fork does not need Google to verify it.
 */
class GoogleDriveApi(
    private val syncPreferences: SyncPreferences = Injekt.get(),
    private val client: OkHttpClient = Injekt.get<NetworkHelper>().client,
    private val json: Json = Injekt.get(),
) {

    // region OAuth

    /**
     * Builds the consent URL and stashes the PKCE verifier for the redirect to pick up.
     *
     * @param redirectUri the loopback address the local listener is bound to, which must be
     * repeated verbatim during the token exchange.
     */
    fun buildAuthorizationUrl(redirectUri: String): String {
        val clientId = clientId()
        require(clientId.isNotBlank()) { "Google Drive client ID is not set" }

        val verifier = generateCodeVerifier()
        syncPreferences.googleDrivePkceVerifier().set(verifier)

        return AUTH_ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", SCOPES)
            .addQueryParameter("code_challenge", codeChallengeOf(verifier))
            .addQueryParameter("code_challenge_method", "S256")
            // offline + consent is what makes Google hand back a refresh token, without which the
            // user would have to sign in again every hour.
            .addQueryParameter("access_type", "offline")
            .addQueryParameter("prompt", "consent")
            .build()
            .toString()
    }

    suspend fun exchangeCodeForTokens(code: String, redirectUri: String) {
        val verifier = syncPreferences.googleDrivePkceVerifier().get()
        if (verifier.isBlank()) throw SyncAuthException("Sign-in session expired, please try again")

        val form = buildTokenForm(
            "grant_type" to "authorization_code",
            "code" to code,
            "code_verifier" to verifier,
            "redirect_uri" to redirectUri,
        )

        val token = postForTokens(form)
        syncPreferences.googleDrivePkceVerifier().delete()

        // Google asks for Drive access as a separate tick box rather than as part of accepting the
        // sign-in, and leaving it unticked still produces a perfectly valid token — just one that
        // carries `email` and nothing else. That token refreshes forever and resolves the account
        // name, so the app looks signed in and healthy while every Drive call comes back 403.
        // Refusing the sign-in here is the only point where the user is still in front of the
        // consent screen and can put it right.
        if (!token.grantsDriveAccess()) {
            throw SyncAuthException(
                "Sign-in succeeded but Drive access was not granted. Sign in again and tick " +
                    "\"See, edit, create and delete only the specific Google Drive files you use " +
                    "with this app\" on the permissions screen.",
            )
        }

        val refreshToken = token.refreshToken
        if (refreshToken.isNullOrBlank()) {
            throw SyncAuthException(
                "Google did not return a refresh token. Remove the app from your Google account " +
                    "permissions and sign in again.",
            )
        }
        syncPreferences.googleDriveRefreshToken().set(refreshToken)
        storeAccessToken(token.accessToken, token.expiresIn)

        runCatching { fetchAccountEmail() }
            .onSuccess { email -> if (email != null) syncPreferences.googleDriveAccountEmail().set(email) }
            .onFailure { logcat(LogPriority.WARN, it) { "Could not read Drive account email" } }
    }

    /**
     * Returns a usable access token, refreshing it first when it is expired or about to be.
     */
    private suspend fun accessToken(): String {
        val cached = syncPreferences.googleDriveAccessToken().get()
        val expiry = syncPreferences.googleDriveAccessTokenExpiry().get()
        if (cached.isNotBlank() && Clock.System.now().toEpochMilliseconds() < expiry) {
            return cached
        }

        val refreshToken = syncPreferences.googleDriveRefreshToken().get()
        if (refreshToken.isBlank()) throw SyncAuthException("Not signed in to Google Drive")

        val token = try {
            postForTokens(
                buildTokenForm(
                    "grant_type" to "refresh_token",
                    "refresh_token" to refreshToken,
                ),
            )
        } catch (e: SyncAuthException) {
            // A dead refresh token comes back as a bare 400. Retrying can never help, and leaving
            // the credentials in place left the app insisting it was signed in while every sync
            // failed, with no way back to the sign-in button. Drop them and flag the UI instead.
            val code = (e.cause as? HttpException)?.code
            if (code != null && code in REAUTH_CODES) {
                syncPreferences.logoutGoogleDrive()
                syncPreferences.syncNeedsReauth().set(true)
                throw SyncReauthRequiredException(
                    "Google sign-in has expired. Sign in again to resume syncing.",
                    e,
                )
            }
            throw e
        }

        // A refresh reports the scopes the original consent actually granted, so a device signed in
        // before this check existed still finds out here instead of failing every Drive call with a
        // 403 for the rest of the token's life.
        if (!token.grantsDriveAccess()) {
            syncPreferences.logoutGoogleDrive()
            syncPreferences.syncNeedsReauth().set(true)
            throw SyncReauthRequiredException(MISSING_DRIVE_SCOPE_MESSAGE)
        }

        storeAccessToken(token.accessToken, token.expiresIn)
        return token.accessToken
    }

    private fun storeAccessToken(accessToken: String, expiresInSeconds: Long) {
        syncPreferences.googleDriveAccessToken().set(accessToken)
        // Expire a minute early so a sync that starts just under the wire does not fail mid-flight.
        val expiresAt = Clock.System.now().toEpochMilliseconds() + (expiresInSeconds - 60).coerceAtLeast(0) * 1000
        syncPreferences.googleDriveAccessTokenExpiry().set(expiresAt)
    }

    /**
     * Keyboards with URL autocomplete like to turn a pasted client ID into `http://<id>`, which
     * Google then rejects as `invalid_request`. Strip that (and any stray slashes) rather than
     * making the user hunt for an invisible prefix.
     */
    private fun ownClientId(): String = syncPreferences.googleDriveClientId().get()
        .trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .trim('/')
        .trim()

    /** The user's own client when they set one, otherwise the one built into Yomi. */
    private fun clientId(): String = ownClientId().ifBlank { BuildConfig.GDRIVE_CLIENT_ID }

    private fun clientSecret(): String = if (ownClientId().isNotBlank()) {
        syncPreferences.googleDriveClientSecret().get().trim()
    } else {
        BuildConfig.GDRIVE_CLIENT_SECRET
    }

    private fun buildTokenForm(vararg params: Pair<String, String>): String {
        val clientId = clientId()
        val clientSecret = clientSecret()

        val all = buildList {
            addAll(params)
            add("client_id" to clientId)
            // Desktop app clients are issued a secret. It is not actually confidential for an
            // installed app, and Google knows that, but the token endpoint still expects it.
            if (clientSecret.isNotBlank()) add("client_secret" to clientSecret)
        }
        return all.joinToString("&") { (k, v) ->
            "${urlEncode(k)}=${urlEncode(v)}"
        }
    }

    private suspend fun postForTokens(form: String): TokenResponse {
        val request = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(form.toRequestBody(FORM_MEDIA_TYPE))
            .build()

        val response = try {
            client.newCall(request).await()
        } catch (e: Exception) {
            throw SyncAuthException("Could not reach Google to sign in: ${e.message}", e)
        }

        if (!response.isSuccessful) {
            // The OAuth endpoint explains itself in the body — `invalid_grant`, `invalid_client`,
            // and so on. Reading it turns "Google rejected the sign-in: HTTP error 400" into
            // something that says which of the several unrelated 400s this actually is.
            val body = response.use { runCatching { it.body.string() }.getOrDefault("") }
            val error = runCatching { json.decodeFromString(OAuthError.serializer(), body) }.getOrNull()
            val detail = listOfNotNull(error?.error, error?.errorDescription)
                .filter { it.isNotBlank() }
                .joinToString(": ")
                .ifBlank { "HTTP ${response.code}" }
            throw SyncAuthException("Google rejected the sign-in ($detail)", HttpException(response.code))
        }

        return response.use {
            json.decodeFromString(TokenResponse.serializer(), it.body.string())
        }
    }

    private suspend fun fetchAccountEmail(): String? {
        val request = Request.Builder()
            .url(USERINFO_ENDPOINT)
            .header("Authorization", "Bearer ${accessToken()}")
            .build()
        return client.newCall(request).awaitSuccess().use {
            json.decodeFromString(UserInfo.serializer(), it.body.string()).email
        }
    }

    // endregion

    // region Drive

    /**
     * Locates the sync payload. With the `drive.file` scope this only ever sees files this app
     * created, so a plain name query is unambiguous.
     */
    suspend fun findSyncFile(): DriveFile? {
        val cachedId = syncPreferences.googleDriveFileId().get()
        if (cachedId.isNotBlank()) {
            val byId = getFileMetadata(cachedId)
            if (byId != null) return byId
            // Deleted or trashed on the Drive side; fall through to a fresh lookup.
            syncPreferences.googleDriveFileId().delete()
        }

        val url = "$DRIVE_API/files".toHttpUrl().newBuilder()
            .addQueryParameter("q", "name = '$SYNC_FILE_NAME' and trashed = false")
            .addQueryParameter("fields", FILE_FIELDS_LIST)
            .addQueryParameter("spaces", "drive")
            .addQueryParameter("pageSize", "10")
            .build()

        val fileList = driveCall {
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${accessToken()}")
                .build()
        }.use {
            json.decodeFromString(DriveFileList.serializer(), it.body.string())
        }

        // Newest wins if a duplicate ever appears (two devices racing the very first upload).
        val file = fileList.files.maxByOrNull { it.modifiedTimeMillis() }
        if (file != null) syncPreferences.googleDriveFileId().set(file.id)
        return file
    }

    private suspend fun getFileMetadata(fileId: String): DriveFile? {
        val url = "$DRIVE_API/files/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter("fields", FILE_FIELDS)
            .build()

        return try {
            driveCall {
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer ${accessToken()}")
                    .build()
            }.use {
                json.decodeFromString(DriveFile.serializer(), it.body.string())
            }
        } catch (e: SyncReauthRequiredException) {
            // A missing grant is not "this file moved". Swallowing it here would send the caller
            // down the name-lookup path to fail again with the same cause, one layer further from
            // where it can be explained.
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "Drive sync file $fileId not retrievable" }
            null
        }
    }

    suspend fun download(fileId: String): ByteArray {
        val url = "$DRIVE_API/files/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "media")
            .build()

        return driveCall {
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${accessToken()}")
                .build()
        }.use { it.body.bytes() }
    }

    /**
     * Creates or replaces the payload. Passing a null [fileId] creates it.
     */
    suspend fun upload(fileId: String?, content: ByteArray, appProperties: Map<String, String>): DriveFile {
        val metadata = DriveFileMetadata(
            name = SYNC_FILE_NAME,
            mimeType = SYNC_MIME_TYPE,
            appProperties = appProperties,
        )

        val body = MultipartBody.Builder()
            .setType(MULTIPART_RELATED)
            .addPart(
                json.encodeToString(DriveFileMetadata.serializer(), metadata)
                    .toRequestBody(JSON_MEDIA_TYPE),
            )
            .addPart(content.toRequestBody(SYNC_MIME_TYPE.toMediaType()))
            .build()

        val url = if (fileId == null) {
            "$DRIVE_UPLOAD_API/files"
        } else {
            "$DRIVE_UPLOAD_API/files/$fileId"
        }.toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", FILE_FIELDS)
            .build()

        val file = driveCall {
            val builder = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${accessToken()}")
            if (fileId == null) builder.post(body).build() else builder.patch(body).build()
        }.use {
            json.decodeFromString(DriveFile.serializer(), it.body.string())
        }
        syncPreferences.googleDriveFileId().set(file.id)
        return file
    }

    /**
     * Runs a Drive request, translating a failed response into something that names its cause.
     *
     * [awaitSuccess] throws `HttpException(code)` and closes the body, so every Drive failure
     * reached the user as a bare "HTTP error 403" — one string covering a missing grant, a disabled
     * API, a full Drive and a rate limit, which have nothing in common but the status code. Google
     * puts the distinguishing reason in the error payload, so read it before giving up.
     *
     * [buildRequest] is re-invoked per attempt so a retry picks up a token refreshed in between.
     */
    private suspend fun driveCall(buildRequest: suspend () -> Request): Response {
        var attempt = 0
        while (true) {
            attempt++
            val response = client.newCall(buildRequest()).await()
            if (response.isSuccessful) return response

            val error = response.use { readDriveError(it) }

            if (error.isTransient && attempt <= MAX_RETRIES) {
                logcat(LogPriority.WARN) { "Drive call failed transiently (${error.reason}), retry $attempt" }
                delay(RETRY_BACKOFF_MS * attempt)
                continue
            }

            throw error.toException()
        }
    }

    private fun readDriveError(response: Response): DriveError {
        val body = runCatching { response.body.string() }.getOrDefault("")
        val parsed = runCatching {
            json.decodeFromString(DriveErrorEnvelope.serializer(), body).error
        }.getOrNull()

        return DriveError(
            code = response.code,
            reason = parsed?.errors?.firstOrNull()?.reason.orEmpty(),
            message = parsed?.message.orEmpty(),
        )
    }

    private fun DriveError.toException(): Exception {
        // Scope problems are recoverable, but only by sending the user back through consent, so
        // they take the same route as an expired token rather than being reported as a dead end.
        if (code == 401 || reason in SCOPE_REASONS) {
            syncPreferences.logoutGoogleDrive()
            syncPreferences.syncNeedsReauth().set(true)
            return SyncReauthRequiredException(
                if (reason in SCOPE_REASONS) MISSING_DRIVE_SCOPE_MESSAGE else EXPIRED_SESSION_MESSAGE,
            )
        }

        val explanation = when (reason) {
            "storageQuotaExceeded" ->
                "Google Drive is out of space, so the sync file cannot be saved. Free up space in " +
                    "Drive and try again."
            "accessNotConfigured" ->
                "The Google Drive API is disabled for this OAuth project. Enable it in the Google " +
                    "Cloud console, then sync again."
            "appNotAuthorizedToFile" ->
                "This app no longer has access to the sync file in Drive. Sign out and sign in " +
                    "again to recreate it."
            "rateLimitExceeded", "userRateLimitExceeded" ->
                "Google is rate limiting sync requests. It will retry on its own shortly."
            else -> message.ifBlank { "Google Drive refused the request (HTTP $code)" }
        }

        return SyncRemoteException(explanation, isTransient = isTransient, cause = HttpException(code))
    }

    // endregion

    // region PKCE

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return base64Url(bytes)
    }

    private fun codeChallengeOf(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return base64Url(digest)
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    // endregion

    companion object {
        private val SYNC_FILE_NAME = if (BuildConfig.APPLICATION_ID == "com.manga.yomi") {
            "yomi_sync.tachibk"
        } else {
            "${BuildConfig.APPLICATION_ID}_sync.tachibk"
        }

        const val PROP_DEVICE_ID = "deviceId"
        const val PROP_TIMESTAMP = "timestamp"

        private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        private const val USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo"
        private const val DRIVE_API = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD_API = "https://www.googleapis.com/upload/drive/v3"

        private const val SCOPES = "$DRIVE_SCOPE email"

        /** Statuses Google uses for a refresh token it will never accept again. */
        private val REAUTH_CODES = setOf(400, 401)

        /** 403 reasons that mean "this token is not allowed to do that", not "that failed". */
        private val SCOPE_REASONS = setOf("insufficientPermissions", "insufficientFilePermissions")

        const val MISSING_DRIVE_SCOPE_MESSAGE =
            "Google Drive access was not granted. Sign in again and make sure the Drive " +
                "permission is ticked on the consent screen."

        private const val EXPIRED_SESSION_MESSAGE =
            "Google sign-in has expired. Sign in again to resume syncing."

        private const val MAX_RETRIES = 2
        private const val RETRY_BACKOFF_MS = 1_500L
        private const val SYNC_MIME_TYPE = "application/octet-stream"

        private const val FILE_FIELDS = "id,name,version,modifiedTime,size,appProperties"
        private const val FILE_FIELDS_LIST = "files($FILE_FIELDS)"

        private val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType()
        private val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()
        private val MULTIPART_RELATED = "multipart/related".toMediaType()
    }
}

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    @SerialName("refresh_token") val refreshToken: String? = null,
    /** Space-separated list of what the user actually agreed to, which can be less than was asked. */
    val scope: String = "",
) {
    /**
     * Treats an absent scope list as granted. Google does send one on both the code exchange and a
     * refresh, but inferring "permission denied" from a field that simply is not there would sign
     * the user out over a response shape change, which is a far worse failure than a 403 they can
     * read.
     */
    fun grantsDriveAccess(): Boolean =
        scope.isBlank() || scope.split(' ').any { it == DRIVE_SCOPE }
}

/** The one scope that actually matters; `email` only powers the account name in settings. */
private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"

@Serializable
private data class OAuthError(
    val error: String = "",
    @SerialName("error_description") val errorDescription: String = "",
)

@Serializable
private data class DriveErrorEnvelope(
    val error: DriveErrorBody = DriveErrorBody(),
)

@Serializable
private data class DriveErrorBody(
    val code: Int = 0,
    val message: String = "",
    val errors: List<DriveErrorItem> = emptyList(),
)

@Serializable
private data class DriveErrorItem(
    val reason: String = "",
    val message: String = "",
)

/**
 * A Drive failure reduced to the three things that decide what to do about it.
 */
private data class DriveError(
    val code: Int,
    val reason: String,
    val message: String,
) {
    val isTransient: Boolean
        get() = code == 429 || code >= 500 || reason in TRANSIENT_REASONS

    private companion object {
        val TRANSIENT_REASONS = setOf("rateLimitExceeded", "userRateLimitExceeded", "backendError")
    }
}

@Serializable
private data class UserInfo(
    val email: String? = null,
)

@Serializable
data class DriveFileList(
    val files: List<DriveFile> = emptyList(),
)

@Serializable
data class DriveFile(
    val id: String,
    val name: String = "",
    /** Monotonically increasing Drive-side counter, used as the compare-and-swap revision. */
    val version: String = "0",
    val modifiedTime: String? = null,
    val size: String? = null,
    val appProperties: Map<String, String> = emptyMap(),
) {
    fun modifiedTimeMillis(): Long = modifiedTime
        ?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: 0L
}

@Serializable
private data class DriveFileMetadata(
    val name: String,
    val mimeType: String,
    val appProperties: Map<String, String>,
)
