package tachiyomi.domain.sync.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import java.util.UUID

class SyncPreferences(
    private val preferenceStore: PreferenceStore,
) {

    // Which backend is active. NONE means sync is switched off entirely.
    fun syncBackend(): Preference<SyncBackend> = preferenceStore.getEnum("sync_service", SyncBackend.NONE)

    // How often the background job runs, in minutes. 0 means manual only.
    fun syncInterval(): Preference<Int> = preferenceStore.getInt("sync_interval", 60)

    fun syncOnlyOnUnmetered(): Preference<Boolean> = preferenceStore.getBoolean("sync_only_unmetered", false)

    fun syncOnlyWhenCharging(): Preference<Boolean> = preferenceStore.getBoolean("sync_only_charging", false)

    // Push local state right after the reader closes a chapter, so the other device sees progress promptly.
    fun syncOnChapterRead(): Preference<Boolean> = preferenceStore.getBoolean("sync_on_chapter_read", true)

    fun syncOnAppStart(): Preference<Boolean> = preferenceStore.getBoolean("sync_on_app_start", true)

    fun syncOnLibraryUpdate(): Preference<Boolean> = preferenceStore.getBoolean("sync_on_library_update", true)

    // Try to install extensions that the synced library needs but this device is missing.
    fun syncExtensions(): Preference<Boolean> = preferenceStore.getBoolean("sync_extensions", true)

    // What the sync payload carries. Mirrors BackupOptions but persisted.
    fun syncCategories(): Preference<Boolean> = preferenceStore.getBoolean("sync_categories", true)

    fun syncChapters(): Preference<Boolean> = preferenceStore.getBoolean("sync_chapters", true)

    fun syncHistory(): Preference<Boolean> = preferenceStore.getBoolean("sync_history", true)

    fun syncTracking(): Preference<Boolean> = preferenceStore.getBoolean("sync_tracking", true)

    /**
     * Safe to default on: device-specific values such as the storage directory live under
     * [Preference.appStateKey] and are already stripped out when the payload is built.
     */
    fun syncAppSettings(): Preference<Boolean> = preferenceStore.getBoolean("sync_app_settings", true)

    fun syncSourceSettings(): Preference<Boolean> = preferenceStore.getBoolean("sync_source_settings", true)

    fun syncPrivateSettings(): Preference<Boolean> = preferenceStore.getBoolean("sync_private_settings", false)

    fun lastSyncTimestamp(): Preference<Long> = preferenceStore.getLong(
        Preference.appStateKey("last_sync_timestamp"),
        0L,
    )

    fun lastSyncError(): Preference<String> = preferenceStore.getString(
        Preference.appStateKey("last_sync_error"),
        "",
    )

    /**
     * Stable per-install identifier. Written into the remote payload so a device can tell
     * "this is my own upload coming back" from "another device wrote this".
     */
    fun deviceId(): Preference<String> = preferenceStore.getString(
        Preference.appStateKey("sync_device_id"),
        "",
    )

    fun uniqueDeviceId(): String {
        val pref = deviceId()
        val current = pref.get()
        if (current.isNotBlank()) return current

        val generated = UUID.randomUUID().toString()
        pref.set(generated)
        return generated
    }

    // region Google Drive

    /**
     * OAuth client ID the user creates in their own Google Cloud project. Kept as a preference
     * rather than a build constant so it can be changed without rebuilding the app.
     */
    fun googleDriveClientId(): Preference<String> = preferenceStore.getString("sync_gdrive_client_id", "")

    /**
     * Only needed for "Desktop app" OAuth clients. Android clients are public and leave this blank.
     */
    fun googleDriveClientSecret(): Preference<String> = preferenceStore.getString(
        Preference.privateKey("sync_gdrive_client_secret"),
        "",
    )

    fun googleDriveRefreshToken(): Preference<String> = preferenceStore.getString(
        Preference.privateKey("sync_gdrive_refresh_token"),
        "",
    )

    fun googleDriveAccessToken(): Preference<String> = preferenceStore.getString(
        Preference.privateKey("sync_gdrive_access_token"),
        "",
    )

    fun googleDriveAccessTokenExpiry(): Preference<Long> = preferenceStore.getLong(
        Preference.appStateKey("sync_gdrive_token_expiry"),
        0L,
    )

    /**
     * PKCE code verifier, held only between opening the consent page and handling the redirect.
     */
    fun googleDrivePkceVerifier(): Preference<String> = preferenceStore.getString(
        Preference.privateKey("sync_gdrive_pkce_verifier"),
        "",
    )

    /** Cached Drive file ID, avoiding a lookup on each sync. */
    fun googleDriveFileId(): Preference<String> = preferenceStore.getString(
        Preference.appStateKey("sync_gdrive_file_id"),
        "",
    )

    fun googleDriveAccountEmail(): Preference<String> = preferenceStore.getString(
        Preference.appStateKey("sync_gdrive_account_email"),
        "",
    )

    /**
     * Set when Google refused the stored refresh token, so the settings screen can explain why it
     * is asking for a sign-in the user already did rather than silently reverting to a blank state.
     */
    fun syncNeedsReauth(): Preference<Boolean> = preferenceStore.getBoolean(
        Preference.appStateKey("sync_needs_reauth"),
        false,
    )

    fun isGoogleDriveLoggedIn(): Boolean = googleDriveRefreshToken().get().isNotBlank()

    fun logoutGoogleDrive() {
        googleDriveRefreshToken().delete()
        googleDriveAccessToken().delete()
        googleDriveAccessTokenExpiry().delete()
        googleDriveFileId().delete()
        googleDriveAccountEmail().delete()
        syncNeedsReauth().delete()
    }

    // endregion

    fun isSyncEnabled(): Boolean = syncBackend().get() != SyncBackend.NONE
}

enum class SyncBackend {
    NONE,
    GOOGLE_DRIVE,
}
