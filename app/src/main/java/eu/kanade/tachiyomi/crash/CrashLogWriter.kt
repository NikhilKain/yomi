package eu.kanade.tachiyomi.crash

import android.content.Context
import android.os.Build
import android.os.Process
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.util.CrashLogUtil
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Writes a crash report the moment Yomi crashes, so nothing is lost if the user never opens the
 * crash screen. A private copy always goes to app storage; when "Save crash logs" is on (the
 * default) a copy also goes to `logs/` in the user's storage folder, where they can find and
 * attach it. Only the newest [KEEP] reports are kept in each place. Nothing leaves the device.
 */
object CrashLogWriter {

    private const val KEEP = 10
    private const val PREFIX = "yomi-crash-"
    private const val LOGCAT_LINES = 400

    fun write(context: Context, thread: Thread, exception: Throwable) {
        val name = PREFIX + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()) + ".txt"
        val report = buildReport(context, thread, exception)

        runCatching {
            val dir = File(context.filesDir, "crash-logs").apply { mkdirs() }
            File(dir, name).writeText(report)
            dir.listFiles { file -> file.name.startsWith(PREFIX) }
                ?.sortedByDescending { it.name }
                ?.drop(KEEP)
                ?.forEach { it.delete() }
        }

        runCatching {
            if (!Injekt.get<BasePreferences>().saveCrashLogs.get()) return@runCatching
            val dir = Injekt.get<StorageManager>().getCrashLogsDirectory() ?: return@runCatching
            val file = dir.createFile(name) ?: return@runCatching
            file.openOutputStream().use { it.write(report.toByteArray()) }
            dir.listFiles()
                ?.filter { it.name?.startsWith(PREFIX) == true }
                ?.sortedByDescending { it.name }
                ?.drop(KEEP)
                ?.forEach { it.delete() }
        }
    }

    private fun buildReport(context: Context, thread: Thread, exception: Throwable): String = buildString {
        appendLine("Yomi crash report")
        appendLine(
            runCatching { CrashLogUtil(context).getDebugInfo() }.getOrElse {
                """
                App ID: ${BuildConfig.APPLICATION_ID}
                App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
                Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
                Device: ${Build.MANUFACTURER} ${Build.MODEL}
                """.trimIndent()
            },
        )
        appendLine()
        appendLine("Thread: ${thread.name}")
        appendLine(exception.stackTraceToString())
        appendLine()
        appendLine("--- Recent log (this app only) ---")
        append(recentLog())
    }

    /** The last lines of this process's own log; other apps' logs are never read. */
    private fun recentLog(): String = runCatching {
        val process = ProcessBuilder(
            "logcat", "-d", "-t", LOGCAT_LINES.toString(), "-v", "threadtime", "--pid", Process.myPid().toString(),
        ).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor(3, TimeUnit.SECONDS)
        text
    }.getOrElse { "(log unavailable: ${it.message})" }
}
