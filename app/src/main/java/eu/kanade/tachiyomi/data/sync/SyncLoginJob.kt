package eu.kanade.tachiyomi.data.sync

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveApi
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveOAuthServer
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.sync.service.SyncBackend
import tachiyomi.domain.sync.service.SyncPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.minutes

/**
 * Runs the Google sign-in as a foreground worker.
 *
 * This deliberately is not a plain background coroutine. The flow spans the user leaving for the
 * browser, and a backgrounded process gets hit by two separate Android behaviours:
 *
 *  - the cached-app freezer suspends its threads, so the loopback listener never reads the redirect
 *    (the socket shows the request queued and unread while the browser spins), and
 *  - background network restrictions make DNS fail, so the token exchange dies with
 *    `UnknownHostException: Unable to resolve host "oauth2.googleapis.com"` even though the browser
 *    plainly has connectivity.
 *
 * Holding a foreground service for the duration avoids both.
 */
class SyncLoginJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val notifier = SyncNotifier(context)
    private val syncPreferences: SyncPreferences = Injekt.get()

    override suspend fun doWork(): Result {
        setForegroundSafely()

        return try {
            GoogleDriveOAuthServer().use { server ->
                val api = GoogleDriveApi()
                val url = api.buildAuthorizationUrl(server.redirectUri)

                context.startActivity(
                    Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )

                GoogleDriveLogin.report(GoogleDriveLogin.State.WaitingForBrowser)

                val params = withTimeoutOrNull(AUTH_TIMEOUT) { server.awaitRedirect() }
                if (params == null) {
                    GoogleDriveLogin.report(GoogleDriveLogin.State.Failed("Timed out waiting for the browser"))
                    return@use Result.failure()
                }

                val error = params["error"]
                val code = params["code"]
                when {
                    error != null -> {
                        GoogleDriveLogin.report(GoogleDriveLogin.State.Failed(error))
                        Result.failure()
                    }
                    code == null -> {
                        GoogleDriveLogin.report(
                            GoogleDriveLogin.State.Failed("Google returned no authorization code"),
                        )
                        Result.failure()
                    }
                    else -> {
                        api.exchangeCodeForTokens(code, server.redirectUri)
                        syncPreferences.syncBackend().set(SyncBackend.GOOGLE_DRIVE)
                        syncPreferences.syncNeedsReauth().set(false)

                        SyncDataJob.setupTask(context)
                        SyncDataJob.startNow(context, manual = true)

                        GoogleDriveLogin.report(GoogleDriveLogin.State.Success)
                        Result.success()
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Google Drive sign-in failed" }
            GoogleDriveLogin.report(GoogleDriveLogin.State.Failed(e.message ?: e::class.simpleName.orEmpty()))
            notifier.showError(e.message)
            Result.failure()
        } finally {
            context.cancelNotification(Notifications.ID_SYNC_PROGRESS)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_SYNC_PROGRESS,
            notifier.loginNotification().build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        private val AUTH_TIMEOUT = 5.minutes

        fun isRunning(context: Context): Boolean = context.workManager.isRunning(TAG)

        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncLoginJob>()
                .addTag(TAG)
                .build()
            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }

        fun stop(context: Context) {
            context.workManager.cancelUniqueWork(TAG)
        }
    }
}

private const val TAG = "SyncLogin"
