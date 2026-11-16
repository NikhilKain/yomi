package eu.kanade.tachiyomi.data.sync.service

import eu.kanade.tachiyomi.data.backup.models.Backup
import java.io.IOException

/**
 * A place the sync payload can live. Implementations only move bytes around; all conflict
 * resolution happens in [eu.kanade.tachiyomi.data.sync.SyncMerger], so adding a second backend
 * later (WebDAV, a self hosted server) means implementing this interface and nothing else.
 */
interface SyncService {

    /**
     * Fetches the current remote snapshot, or null when nothing has ever been uploaded.
     */
    suspend fun pull(): RemoteSnapshot?

    /**
     * Writes [backup] to the remote.
     *
     * @param expectedRevision the [RemoteSnapshot.revision] this upload is based on, or null when
     * creating the payload for the first time. Implementations must refuse the write if the remote
     * has moved on since, so a device that synced concurrently does not get silently overwritten.
     * @return the revision of the newly written payload.
     */
    suspend fun push(backup: Backup, expectedRevision: String?): String

    /**
     * True when the backend has enough credentials to attempt a sync.
     */
    fun isAuthenticated(): Boolean
}

data class RemoteSnapshot(
    val backup: Backup,
    /** Opaque backend-specific token identifying this exact version of the payload. */
    val revision: String,
    /** When the payload was written, epoch millis. */
    val timestamp: Long,
    /** Which device wrote it, matched against [tachiyomi.domain.sync.service.SyncPreferences.deviceId]. */
    val deviceId: String,
)

/**
 * Thrown when the remote moved on between [SyncService.pull] and [SyncService.push]. The caller is
 * expected to redo the whole pull/merge/push cycle against the newer remote.
 */
class SyncConflictException(message: String) : IOException(message)

/**
 * Thrown when credentials are missing or no longer valid and the user has to sign in again.
 */
class SyncAuthException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Thrown when the stored refresh token is dead and only a fresh interactive sign-in can fix it.
 *
 * Distinct from [SyncAuthException] because the recovery is different: there is nothing to retry
 * and no transient condition to wait out, so the credentials are dropped and the settings screen
 * has to offer signing in again. Google returns this as a plain 400 once a refresh token is
 * revoked, or once it expires, which happens after seven days while the OAuth consent screen is
 * still in Testing.
 */
class SyncReauthRequiredException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Thrown when the backend refused a request for a reason that is neither a conflict nor a dead
 * credential — a disabled API, a full Drive, a rate limit.
 *
 * The message carries the backend's own wording. That matters because the alternative, which this
 * replaced, was a bare `HTTP error 403`: three unrelated causes with three different fixes all
 * rendered identically, so there was nothing to act on.
 *
 * @param isTransient true when simply waiting and retrying can succeed, which is the difference
 * between "sync will catch up on its own" and "go change something in the Google console".
 */
class SyncRemoteException(
    message: String,
    val isTransient: Boolean = false,
    cause: Throwable? = null,
) : IOException(message, cause)
