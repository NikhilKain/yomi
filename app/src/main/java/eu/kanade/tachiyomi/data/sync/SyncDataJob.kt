package eu.kanade.tachiyomi.data.sync

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.sync.service.SyncAuthException
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.sync.service.SyncPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class SyncDataJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val notifier = SyncNotifier(context)
    private val syncPreferences: SyncPreferences = Injekt.get()

    override suspend fun doWork(): Result {
        if (!syncPreferences.isSyncEnabled()) return Result.success()

        // Backup and restore both rewrite the same tables, so let them finish first rather than
        // interleaving two writers over the library.
        if (BackupCreateJob.isManualJobRunning(context) || BackupRestoreJob.isRunning(context)) {
            return Result.retry()
        }

        val isManual = inputData.getBoolean(MANUAL_KEY, false)

        setForegroundSafely()

        return try {
            SyncManager(context).sync()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SyncAuthException) {
            // Retrying will not help until the user signs in again.
            logcat(LogPriority.WARN, e) { "Sync aborted, authentication needed" }
            notifier.showError(e.message)
            Result.failure()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Sync failed" }
            if (isManual) {
                notifier.showError(e.message)
                Result.failure()
            } else {
                // Transient things like being offline are the common case for scheduled runs.
                Result.retry()
            }
        } finally {
            context.cancelNotification(Notifications.ID_SYNC_PROGRESS)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_SYNC_PROGRESS,
            notifier.progressNotification().build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        fun isRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG_AUTO) || context.workManager.isRunning(TAG_MANUAL)
        }

        /**
         * (Re)schedules the periodic sync. Safe to call on every app start; WorkManager keeps a
         * single unique job and only replaces it when the parameters actually changed.
         */
        fun setupTask(context: Context, prefInterval: Int? = null) {
            val syncPreferences = Injekt.get<SyncPreferences>()
            val interval = prefInterval ?: syncPreferences.syncInterval().get()

            if (!syncPreferences.isSyncEnabled() || interval <= 0) {
                context.workManager.cancelUniqueWork(TAG_AUTO)
                return
            }

            val constraints = Constraints(
                requiredNetworkType = if (syncPreferences.syncOnlyOnUnmetered().get()) {
                    NetworkType.UNMETERED
                } else {
                    NetworkType.CONNECTED
                },
                requiresCharging = syncPreferences.syncOnlyWhenCharging().get(),
                requiresBatteryNotLow = true,
            )

            val request = PeriodicWorkRequestBuilder<SyncDataJob>(
                interval.toLong(),
                TimeUnit.MINUTES,
                // Flex window, so Android can batch the wakeup with other work.
                (interval / 4).coerceAtLeast(5).toLong(),
                TimeUnit.MINUTES,
            )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .addTag(TAG_AUTO)
                .setConstraints(constraints)
                .build()

            context.workManager.enqueueUniquePeriodicWork(TAG_AUTO, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /**
         * Runs a sync right now.
         *
         * @param manual true when the user asked for it, which makes failures surface as a
         * notification instead of being retried silently.
         */
        fun startNow(context: Context, manual: Boolean = false) {
            val syncPreferences = Injekt.get<SyncPreferences>()
            if (!syncPreferences.isSyncEnabled()) return

            val request = OneTimeWorkRequestBuilder<SyncDataJob>()
                .addTag(TAG_MANUAL)
                .setInputData(workDataOf(MANUAL_KEY to manual))
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()

            // KEEP rather than REPLACE: if a sync is already in flight, joining it is correct and
            // restarting it would throw away work that is already partly applied.
            context.workManager.enqueueUniqueWork(TAG_MANUAL, ExistingWorkPolicy.KEEP, request)
        }

        fun stop(context: Context) {
            context.workManager.cancelUniqueWork(TAG_MANUAL)
        }
    }
}

private const val TAG_AUTO = "SyncData"
private const val TAG_MANUAL = "$TAG_AUTO:manual"

private const val MANUAL_KEY = "manual" // Boolean
