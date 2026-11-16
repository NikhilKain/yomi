package eu.kanade.tachiyomi.data.sync

import android.content.Context
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.restore.BackupRestorer
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveSyncService
import eu.kanade.tachiyomi.data.sync.service.SyncAuthException
import eu.kanade.tachiyomi.data.sync.service.SyncConflictException
import eu.kanade.tachiyomi.data.sync.service.SyncService
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import okio.buffer
import okio.gzip
import okio.sink
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.domain.sync.service.SyncBackend
import tachiyomi.domain.sync.service.SyncPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import kotlin.time.Clock

/**
 * Runs one full sync cycle: snapshot this device, pull the shared snapshot, merge the two, apply the
 * result locally, and write it back so the other devices converge on it.
 *
 * The payload is the existing backup format, which already carries the library, per-chapter read
 * state and page progress, history, tracking, categories, the source list, extension repositories,
 * and app/source settings.
 */
class SyncManager(
    private val context: Context,
    private val syncPreferences: SyncPreferences = Injekt.get(),
    private val database: Database = Injekt.get(),
    private val protoBuf: ProtoBuf = Injekt.get(),
) {

    private val notifier = SyncNotifier(context)

    suspend fun sync() {
        val service = resolveService() ?: throw SyncAuthException("Sync is not set up")
        if (!service.isAuthenticated()) throw SyncAuthException("Not signed in to the sync account")

        notifier.showProgress()

        try {
            runCycle(service)
            syncPreferences.lastSyncTimestamp().set(Clock.System.now().toEpochMilliseconds())
            syncPreferences.lastSyncError().delete()
            notifier.dismissProgress()
        } catch (e: Exception) {
            notifier.dismissProgress()
            syncPreferences.lastSyncError().set(e.message ?: e::class.simpleName.orEmpty())
            throw e
        } finally {
            // MangaRestorer marks rows as syncing so the version triggers stay quiet while data is
            // being written. If we do not clear that afterwards the rows stay frozen and later user
            // edits would never bump their version, silently breaking conflict resolution.
            withContext(NonCancellable) { clearSyncingFlags() }
        }
    }

    private suspend fun runCycle(service: SyncService) {
        var attempt = 0
        while (true) {
            attempt++

            val localTimestamp = Clock.System.now().toEpochMilliseconds()
            val local = BackupCreator(context, isAutoBackup = false).createBackup(backupOptions())

            val remote = service.pull()

            if (remote == null) {
                // First device to sync: seed the remote with what we have and stop.
                logcat(LogPriority.INFO) { "No remote sync payload yet, uploading this device library" }
                service.push(local, expectedRevision = null)
                return
            }

            val merged = SyncMerger.merge(
                local = local,
                remote = remote.backup,
                localTimestamp = localTimestamp,
                remoteTimestamp = remote.timestamp,
            )

            applyLocally(merged)

            try {
                service.push(merged, expectedRevision = remote.revision)
            } catch (e: SyncConflictException) {
                if (attempt >= MAX_ATTEMPTS) throw e
                // Another device wrote while we were merging. Everything we just applied locally is
                // already durable, so redoing the cycle folds their changes in on top.
                logcat(LogPriority.WARN, e) { "Sync raced with another device, retrying ($attempt)" }
                continue
            }

            installMissingExtensions(merged)
            return
        }
    }

    /**
     * Writes the merged snapshot through the ordinary restore path, which already knows how to
     * reconcile a backup against the live database entry by entry.
     */
    private suspend fun applyLocally(merged: Backup) {
        val file = File(context.cacheDir, MERGED_FILENAME)
        try {
            file.outputStream().use { out ->
                out.sink().gzip().buffer().use {
                    it.write(protoBuf.encodeToByteArray(Backup.serializer(), merged))
                }
            }

            BackupRestorer(context, BackupNotifier(context), isSync = true)
                .restore(file.toUri(), restoreOptions())
        } finally {
            file.delete()
        }
    }

    private suspend fun installMissingExtensions(merged: Backup) {
        if (!syncPreferences.syncExtensions().get()) return

        try {
            SyncExtensionInstaller(context).installMissing(merged)
        } catch (e: Exception) {
            // Never fail a sync over extensions; the data is already merged and pushed.
            logcat(LogPriority.WARN, e) { "Could not reconcile extensions after sync" }
        }
    }

    private suspend fun clearSyncingFlags() {
        try {
            database.transaction {
                database.mangasQueries.resetIsSyncing()
                database.chaptersQueries.resetIsSyncing()
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to clear is_syncing flags" }
        }
    }

    private fun resolveService(): SyncService? = when (syncPreferences.syncBackend().get()) {
        SyncBackend.GOOGLE_DRIVE -> GoogleDriveSyncService()
        SyncBackend.NONE -> null
    }

    private fun backupOptions() = BackupOptions(
        libraryEntries = true,
        categories = syncPreferences.syncCategories().get(),
        chapters = syncPreferences.syncChapters().get(),
        tracking = syncPreferences.syncTracking().get(),
        history = syncPreferences.syncHistory().get(),
        readEntries = true,
        unfavoriteTombstones = true,
        appSettings = syncPreferences.syncAppSettings().get(),
        extensionStores = true,
        sourceSettings = syncPreferences.syncSourceSettings().get(),
        privateSettings = syncPreferences.syncPrivateSettings().get(),
    )

    private fun restoreOptions() = RestoreOptions(
        libraryEntries = true,
        categories = syncPreferences.syncCategories().get(),
        appSettings = syncPreferences.syncAppSettings().get(),
        extensionStores = true,
        sourceSettings = syncPreferences.syncSourceSettings().get(),
    )

    companion object {
        private const val MERGED_FILENAME = "sync_merged.tachibk"
        private const val MAX_ATTEMPTS = 3
    }
}
