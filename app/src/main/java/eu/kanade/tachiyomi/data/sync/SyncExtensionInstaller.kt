package eu.kanade.tachiyomi.data.sync

import android.content.Context
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import kotlinx.coroutines.flow.first
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Brings this device sources in line with the synced library.
 *
 * Extension repositories themselves are restored by the ordinary backup restore path, so by the
 * time this runs the repos are already present. What is left is the actual APKs: a library synced
 * from another device will reference sources this device has never installed.
 *
 * Android does not allow an app to install another APK silently without a privileged installer, so
 * each install surfaces the system package installer prompt. Anything that cannot be matched to a
 * known extension is reported in a notification rather than swallowed.
 */
class SyncExtensionInstaller(
    private val context: Context,
    private val extensionManager: ExtensionManager = Injekt.get(),
) {

    private val notifier = SyncNotifier(context)

    suspend fun installMissing(backup: Backup) {
        val requiredSourceIds = buildSet {
            backup.backupSources.forEach { add(it.sourceId) }
            backup.backupManga.forEach { add(it.source) }
        }
        if (requiredSourceIds.isEmpty()) return

        // Pull a fresh index so repos that only just arrived via sync are taken into account.
        runCatching { extensionManager.findAvailableExtensions() }
            .onFailure { logcat(LogPriority.WARN, it) { "Could not refresh extension index during sync" } }

        val installedSourceIds = extensionManager.installedExtensionsFlow.first()
            .flatMap { extension -> extension.sources.map { it.id } }
            .toSet()

        val missingSourceIds = requiredSourceIds - installedSourceIds
        if (missingSourceIds.isEmpty()) return

        val available = extensionManager.availableExtensionsFlow.first()
        val installedPkgNames = extensionManager.installedExtensionsFlow.first()
            .map { it.pkgName }
            .toSet()

        // One extension usually provides several sources, so collapse to a distinct APK set.
        val toInstall = available
            .filter { extension ->
                extension.pkgName !in installedPkgNames &&
                    extension.sources.any { it.id in missingSourceIds }
            }
            .distinctBy { it.pkgName }

        if (toInstall.isEmpty()) {
            logcat(LogPriority.INFO) {
                "Sync needs ${missingSourceIds.size} source(s) with no matching extension in the configured repos"
            }
            return
        }

        notifier.showMissingExtensions(
            count = toInstall.size,
            names = toInstall.joinToString { it.name },
        )

        // Sequentially, so the user gets one install prompt at a time instead of a stack of them.
        toInstall.forEach { extension ->
            try {
                install(extension)
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed installing ${extension.pkgName} during sync" }
            }
        }
    }

    private suspend fun install(extension: Extension.Available) {
        // Not InstallStep.isCompleted(), which also treats Idle as terminal and would return
        // before the install has actually been attempted.
        val step = extensionManager.installExtension(extension)
            .first { it == InstallStep.Installed || it == InstallStep.Error }

        logcat(LogPriority.INFO) { "Sync install of ${extension.pkgName} finished as $step" }
    }
}
