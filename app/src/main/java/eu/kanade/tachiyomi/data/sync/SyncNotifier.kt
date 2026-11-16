package eu.kanade.tachiyomi.data.sync

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

class SyncNotifier(private val context: Context) {

    private val progressNotificationBuilder = context.notificationBuilder(
        Notifications.CHANNEL_BACKUP_RESTORE_PROGRESS,
    ) {
        setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
        setSmallIcon(R.drawable.ic_yomi)
        setAutoCancel(false)
        setOngoing(true)
        setOnlyAlertOnce(true)
    }

    private val completeNotificationBuilder = context.notificationBuilder(
        Notifications.CHANNEL_BACKUP_RESTORE_COMPLETE,
    ) {
        setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
        setSmallIcon(R.drawable.ic_yomi)
        setAutoCancel(true)
    }

    fun progressNotification(): NotificationCompat.Builder = with(progressNotificationBuilder) {
        setContentTitle(context.stringResource(MR.strings.syncing_library))
        setProgress(0, 0, true)
    }

    /**
     * Shown while the user is over in the browser completing Google sign-in. Its real job is to
     * keep the process in a foreground service state so the loopback listener is not frozen and
     * the token exchange still has network.
     */
    fun loginNotification(): NotificationCompat.Builder = with(progressNotificationBuilder) {
        setContentTitle(context.stringResource(MR.strings.sync_gdrive_waiting_browser))
        setProgress(0, 0, true)
    }

    fun showProgress() {
        context.notify(Notifications.ID_SYNC_PROGRESS, progressNotification().build())
    }

    fun dismissProgress() {
        context.cancelNotification(Notifications.ID_SYNC_PROGRESS)
    }

    fun showError(error: String?) {
        dismissProgress()

        with(completeNotificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.sync_error))
            setContentText(error)
            setStyle(NotificationCompat.BigTextStyle().bigText(error.orEmpty()))
            context.notify(Notifications.ID_SYNC_COMPLETE, build())
        }
    }

    /**
     * Extensions cannot be installed silently without Shizuku or a privileged installer, so when a
     * synced library needs sources this device does not have, say so instead of failing quietly.
     */
    fun showMissingExtensions(count: Int, names: String) {
        with(completeNotificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.sync_missing_extensions, count))
            setContentText(names)
            setStyle(NotificationCompat.BigTextStyle().bigText(names))
            context.notify(Notifications.ID_SYNC_EXTENSIONS, build())
        }
    }
}
