package eu.kanade.tachiyomi.data.usage

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.getSystemService
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import eu.kanade.tachiyomi.BuildConfig
import logcat.LogPriority
import org.json.JSONObject
import tachiyomi.core.common.util.system.logcat
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors

/**
 * An anonymous count of the devices using Yomi, for the developer's admin panel (the Vythera
 * panel that several apps report to; Yomi is app "yomi" there, and Kanri reads it).
 * **It only reports.** The panel can answer that a device or the app is switched off; Yomi
 * ignores both answers and never locks anyone out. The privacy policy describes it.
 *
 * What is sent: a SHA-256 of the Android ID and package name (the ID itself never leaves the
 * device), a random installation ID, a random ID for the session and how long Yomi has been open,
 * the device's maker and model, the Android version and Yomi's version. The panel also sees the
 * connection's approximate location. Never the library, sources, searches, reading history,
 * tracker accounts or anything typed into the app.
 *
 * Sent as Yomi comes to the foreground, every five minutes while it is open, and as it goes to
 * the background. Only official release builds report; debug and side-by-side test builds don't.
 */
object UsageCheckIn {
    private const val ENDPOINT = "https://vyxel-admin.narzo9990.workers.dev/api/checkin"
    private const val APP = "yomi"
    private const val RELEASE_PACKAGE = "com.manga.yomi"

    /** Each saved check-in costs database writes on a free plan that several apps share. */
    private const val HEARTBEAT_MS = 5 * 60_000L

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var sessionId = ""
    private var sessionStarted = 0L
    private var foreground = false

    private val heartbeat = object : Runnable {
        override fun run() {
            if (!foreground) return
            send("heartbeat")
            handler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    /** From Application.onCreate: follows the whole app's foreground, not one screen's. */
    fun install(application: Application) {
        // Test builds would only add the developer's own phone to the count.
        if (BuildConfig.BUILD_TYPE != "release" || BuildConfig.APPLICATION_ID != RELEASE_PACKAGE) return
        // The crash screen runs in its own process; only the main one reports.
        if (!isMainProcess(application)) return
        appContext = application.applicationContext
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = opened()
                override fun onStop(owner: LifecycleOwner) = closed()
            },
        )
    }

    private fun isMainProcess(application: Application): Boolean {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val pid = Process.myPid()
            application.getSystemService<ActivityManager>()
                ?.runningAppProcesses
                ?.firstOrNull { it.pid == pid }
                ?.processName
        }
        return name == null || !name.contains(':')
    }

    private fun opened() {
        foreground = true
        sessionId = UUID.randomUUID().toString()
        sessionStarted = SystemClock.elapsedRealtime()
        send("start")
        handler.removeCallbacks(heartbeat)
        handler.postDelayed(heartbeat, HEARTBEAT_MS)
    }

    private fun closed() {
        foreground = false
        handler.removeCallbacks(heartbeat)
        send("pause")
    }

    private fun installationId(c: Context): String {
        val prefs = c.getSharedPreferences("usage_checkin", Context.MODE_PRIVATE)
        return prefs.getString("installation_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("installation_id", it).apply()
        }
    }

    @SuppressLint("HardwareIds")
    private fun deviceId(c: Context): String = runCatching {
        val raw = Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID) + ":" + RELEASE_PACKAGE
        MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }.getOrDefault("")

    private fun send(event: String) {
        val c = appContext ?: return
        if (sessionId.isEmpty()) return
        val session = sessionId
        val seconds = ((SystemClock.elapsedRealtime() - sessionStarted) / 1000).coerceIn(0, 604_800)
        executor.execute {
            val id = deviceId(c)
            if (id.length != 64) return@execute
            var connection: HttpURLConnection? = null
            try {
                val body = JSONObject()
                    .put("app", APP)
                    .put("id", id)
                    .put("installationId", installationId(c))
                    .put("sessionId", session)
                    .put("event", event)
                    .put("durationSeconds", seconds)
                    .put("manufacturer", Build.MANUFACTURER)
                    .put("model", Build.MODEL)
                    .put("os", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    .put("appVersion", BuildConfig.VERSION_NAME)
                    .put("kind", "phone")
                connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
                // The answer (active or disabled) is read and dropped: Yomi never locks.
                val code = connection.responseCode
                if (code != 200) logcat(LogPriority.WARN) { "Usage check-in answered HTTP $code" }
            } catch (e: Exception) {
                // Offline or the panel is down: the next one will try again.
                logcat(LogPriority.WARN) { "Usage check-in failed: ${e.javaClass.simpleName}" }
            } finally {
                connection?.disconnect()
            }
        }
    }
}
