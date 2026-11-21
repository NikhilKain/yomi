package tachiyomi.domain.release.interactor

import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService

class GetApplicationRelease(
    private val service: ReleaseService,
) {
    suspend fun await(arguments: Arguments): Result {
        val release = service.latest(arguments) ?: return Result.NoNewUpdate

        // Check if latest version is different from current version
        val isNewVersion = isNewVersion(
            arguments.isPreview,
            arguments.commitCount,
            arguments.versionName,
            release.version,
        )
        return when {
            isNewVersion -> Result.NewUpdate(release)
            else -> Result.NoNewUpdate
        }
    }

    private fun isNewVersion(
        isPreview: Boolean,
        commitCount: Int,
        versionName: String,
        versionTag: String,
    ): Boolean {
        // Removes prefixes like "r" or "v"
        val newVersion = versionTag.replace("[^\\d.]".toRegex(), "")
        return if (isPreview) {
            // Preview builds: based on releases in "mihonapp/mihon-preview" repo
            // tagged as something like "r1234"
            newVersion.toInt() > commitCount
        } else {
            // Release builds: based on releases in "mihonapp/mihon" repo
            // tagged as something like "v0.1.2"
            // Yomi: compare part by part, so 0.10.0 is newer than 0.9.9 and 0.1.5 is not newer than 0.2.0.
            isNewerSemVer(newVersion, versionName.substringBefore('-'))
        }
    }

    internal fun isNewerSemVer(candidate: String, current: String): Boolean {
        fun parts(version: String) = version.replace("[^\\d.]".toRegex(), "")
            .split(".")
            .map { it.toIntOrNull() ?: 0 }
        val new = parts(candidate)
        val old = parts(current)
        for (index in 0..<maxOf(new.size, old.size)) {
            val a = new.getOrElse(index) { 0 }
            val b = old.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    data class Arguments(
        val isFoss: Boolean,
        val isPreview: Boolean,
        val commitCount: Int,
        val versionName: String,
        val repository: String,
        val forceCheck: Boolean = false,
    )

    sealed interface Result {
        data class NewUpdate(val release: Release) : Result
        data object NoNewUpdate : Result
        data object OsTooOld : Result
    }
}
