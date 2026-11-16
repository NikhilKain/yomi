package eu.kanade.tachiyomi.data.sync

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import kotlin.math.max

/**
 * Merges two [Backup] snapshots into one.
 *
 * The database maintains a monotonic `version` counter per manga and per chapter, bumped by SQL
 * triggers whenever the user actually changes something meaningful (read state, bookmark, page
 * progress, favorite, url, description) and deliberately not bumped while `is_syncing = 1`.
 * That gives a real happens-after signal rather than relying on wall clock timestamps, which are
 * unreliable across devices with skewed clocks.
 *
 * Resolution order for any conflicting pair is therefore:
 *   1. higher `version` wins
 *   2. on equal versions, higher `lastModifiedAt` wins
 *   3. on a genuine tie, merge conservatively so read progress and bookmarks are never lost
 *
 * The merge is pure so it can be unit tested without a database.
 */
object SyncMerger {

    /**
     * @param local snapshot of this device, freshly created
     * @param remote snapshot pulled from the cloud
     * @param localTimestamp when [local] was produced (epoch millis)
     * @param remoteTimestamp when [remote] was last written, by whichever device wrote it
     */
    fun merge(
        local: Backup,
        remote: Backup,
        localTimestamp: Long,
        remoteTimestamp: Long,
    ): Backup {
        val mergedCategories = mergeCategories(local.backupCategories, remote.backupCategories)

        // Manga reference their categories by `order`, not by id, so remapping the category list
        // invalidates those references. Rewrite both sides onto the merged ordering first.
        val localOrderRemap = orderRemap(local.backupCategories, mergedCategories)
        val remoteOrderRemap = orderRemap(remote.backupCategories, mergedCategories)

        val localManga = local.backupManga.map { it.remapCategories(localOrderRemap) }
        val remoteManga = remote.backupManga.map { it.remapCategories(remoteOrderRemap) }

        val mergedManga = mergeMangaLists(localManga, remoteManga)

        // Settings carry no per-key version, so the only sane rule is whole-set last writer wins.
        val localIsNewer = localTimestamp >= remoteTimestamp

        return Backup(
            backupManga = mergedManga,
            backupCategories = mergedCategories,
            backupSources = mergeSources(local.backupSources, remote.backupSources),
            backupPreferences = if (localIsNewer) local.backupPreferences else remote.backupPreferences,
            backupSourcePreferences = mergeSourcePreferences(
                local.backupSourcePreferences,
                remote.backupSourcePreferences,
                localIsNewer,
            ),
            backupExtensionStores = mergeExtensionStores(
                local.backupExtensionStores,
                remote.backupExtensionStores,
            ),
        )
    }

    // region Manga

    private fun mergeMangaLists(local: List<BackupManga>, remote: List<BackupManga>): List<BackupManga> {
        if (local.isEmpty()) return remote
        if (remote.isEmpty()) return local

        val remoteByKey = remote.associateBy { it.syncKey() }
        val seen = HashSet<Pair<Long, String>>(remote.size)

        val merged = ArrayList<BackupManga>(max(local.size, remote.size))

        local.forEach { localManga ->
            val key = localManga.syncKey()
            seen += key
            val remoteManga = remoteByKey[key]
            merged += if (remoteManga == null) localManga else mergeManga(localManga, remoteManga)
        }

        // Entries that only exist on the remote side are new to this device.
        remote.forEach { remoteManga ->
            if (remoteManga.syncKey() !in seen) merged += remoteManga
        }

        return merged
    }

    private fun mergeManga(local: BackupManga, remote: BackupManga): BackupManga {
        val winner: BackupManga
        val loser: BackupManga
        if (remote.wins(local.version, local.lastModifiedAt)) {
            winner = remote
            loser = local
        } else {
            winner = local
            loser = remote
        }

        // Favorite carries its own timestamp precisely so that removing an entry from the library
        // on one device propagates, instead of being resurrected by the other device copy.
        val localFav = local.favoriteModifiedAt ?: 0L
        val remoteFav = remote.favoriteModifiedAt ?: 0L
        val favorite: Boolean
        val favoriteModifiedAt: Long?
        when {
            localFav > remoteFav -> {
                favorite = local.favorite
                favoriteModifiedAt = local.favoriteModifiedAt
            }
            remoteFav > localFav -> {
                favorite = remote.favorite
                favoriteModifiedAt = remote.favoriteModifiedAt
            }
            // Nothing to go on (entries predating the column): keep it if either side has it.
            else -> {
                favorite = local.favorite || remote.favorite
                favoriteModifiedAt = local.favoriteModifiedAt ?: remote.favoriteModifiedAt
            }
        }

        return winner.also {
            it.favorite = favorite
            it.favoriteModifiedAt = favoriteModifiedAt
            it.version = max(local.version, remote.version)
            it.lastModifiedAt = max(local.lastModifiedAt, remote.lastModifiedAt)
            it.dateAdded = minNonZero(local.dateAdded, remote.dateAdded)
            it.initialized = local.initialized || remote.initialized
            it.chapters = mergeChapters(local.chapters, remote.chapters)
            it.history = mergeHistory(local.history, remote.history)
            it.tracking = mergeTracking(local.tracking, remote.tracking)
            it.categories = (local.categories + remote.categories).distinct()
            it.excludedScanlators = (local.excludedScanlators + remote.excludedScanlators).distinct()
            it.notes = if (winner.notes.isNotBlank()) winner.notes else loser.notes
        }
    }

    private fun BackupManga.syncKey(): Pair<Long, String> = source to url

    private fun BackupManga.remapCategories(remap: Map<Long, Long>): BackupManga = also {
        if (remap.isNotEmpty()) {
            it.categories = it.categories.mapNotNull(remap::get)
        }
    }

    // endregion

    // region Chapters

    private fun mergeChapters(local: List<BackupChapter>, remote: List<BackupChapter>): List<BackupChapter> {
        if (local.isEmpty()) return remote
        if (remote.isEmpty()) return local

        val remoteByUrl = remote.associateBy { it.url }
        val seen = HashSet<String>(remote.size)

        val merged = ArrayList<BackupChapter>(max(local.size, remote.size))

        local.forEach { localChapter ->
            seen += localChapter.url
            val remoteChapter = remoteByUrl[localChapter.url]
            merged += if (remoteChapter == null) localChapter else mergeChapter(localChapter, remoteChapter)
        }

        remote.forEach { if (it.url !in seen) merged += it }

        return merged
    }

    private fun mergeChapter(local: BackupChapter, remote: BackupChapter): BackupChapter {
        val tied = local.version == remote.version && local.lastModifiedAt == remote.lastModifiedAt

        val winner = if (remote.wins(local.version, local.lastModifiedAt)) remote else local

        return winner.also {
            if (tied) {
                // Nothing to arbitrate on, so take the most generous reading of both sides. This is
                // the only branch that ORs read state: elsewhere a deliberate "mark as unread" on
                // the newer device has to be allowed to win.
                it.read = local.read || remote.read
                it.lastPageRead = max(local.lastPageRead, remote.lastPageRead)
            }
            // A bookmark is additive by nature and cheap to keep, so never drop one.
            it.bookmark = local.bookmark || remote.bookmark
            it.version = max(local.version, remote.version)
            it.lastModifiedAt = max(local.lastModifiedAt, remote.lastModifiedAt)
            it.dateFetch = minNonZero(local.dateFetch, remote.dateFetch)
            it.dateUpload = max(local.dateUpload, remote.dateUpload)
        }
    }

    // endregion

    // region History and tracking

    private fun mergeHistory(local: List<BackupHistory>, remote: List<BackupHistory>): List<BackupHistory> {
        if (local.isEmpty()) return remote
        if (remote.isEmpty()) return local

        val byUrl = LinkedHashMap<String, BackupHistory>(local.size + remote.size)
        (local + remote).forEach { entry ->
            val existing = byUrl[entry.url]
            byUrl[entry.url] = if (existing == null) {
                entry
            } else {
                existing.copy(
                    lastRead = max(existing.lastRead, entry.lastRead),
                    readDuration = max(existing.readDuration, entry.readDuration),
                )
            }
        }
        return byUrl.values.toList()
    }

    private fun mergeTracking(local: List<BackupTracking>, remote: List<BackupTracking>): List<BackupTracking> {
        if (local.isEmpty()) return remote
        if (remote.isEmpty()) return local

        val byTracker = LinkedHashMap<Int, BackupTracking>(local.size + remote.size)
        (local + remote).forEach { entry ->
            val existing = byTracker[entry.syncId]
            byTracker[entry.syncId] = when {
                existing == null -> entry
                entry.lastChapterRead > existing.lastChapterRead -> entry
                else -> existing
            }
        }
        return byTracker.values.toList()
    }

    // endregion

    // region Categories

    private fun mergeCategories(
        local: List<BackupCategory>,
        remote: List<BackupCategory>,
    ): List<BackupCategory> {
        if (local.isEmpty()) return remote
        if (remote.isEmpty()) return local

        // Categories have no version column, so identity is the name. Keep the ordering of
        // whichever device has more of them and append anything the other side knows about.
        val primary: List<BackupCategory>
        val secondary: List<BackupCategory>
        if (local.size >= remote.size) {
            primary = local
            secondary = remote
        } else {
            primary = remote
            secondary = local
        }

        val byName = LinkedHashMap<String, BackupCategory>(primary.size + secondary.size)
        primary.sortedBy { it.order }.forEach { byName[it.name] = it }
        secondary.sortedBy { it.order }.forEach { byName.putIfAbsent(it.name, it) }

        return byName.values.mapIndexed { index, category ->
            BackupCategory(
                name = category.name,
                order = index.toLong(),
                id = category.id,
                flags = category.flags,
            )
        }
    }

    /**
     * Maps one side original category `order` values onto the merged ones, keyed by category name.
     */
    private fun orderRemap(
        original: List<BackupCategory>,
        merged: List<BackupCategory>,
    ): Map<Long, Long> {
        if (original.isEmpty()) return emptyMap()
        val mergedOrderByName = merged.associate { it.name to it.order }
        return original.mapNotNull { category ->
            mergedOrderByName[category.name]?.let { category.order to it }
        }.toMap()
    }

    // endregion

    // region Sources, stores, source settings

    private fun mergeSources(local: List<BackupSource>, remote: List<BackupSource>): List<BackupSource> {
        return (local + remote).distinctBy { it.sourceId }
    }

    private fun mergeExtensionStores(
        local: List<BackupExtensionStore>,
        remote: List<BackupExtensionStore>,
    ): List<BackupExtensionStore> {
        return (local + remote).distinctBy { it.indexUrl }
    }

    private fun mergeSourcePreferences(
        local: List<BackupSourcePreferences>,
        remote: List<BackupSourcePreferences>,
        localIsNewer: Boolean,
    ): List<BackupSourcePreferences> {
        if (local.isEmpty()) return remote
        if (remote.isEmpty()) return local

        val winner: List<BackupSourcePreferences>
        val loser: List<BackupSourcePreferences>
        if (localIsNewer) {
            winner = local
            loser = remote
        } else {
            winner = remote
            loser = local
        }

        val bySource = LinkedHashMap<String, BackupSourcePreferences>(winner.size + loser.size)
        winner.forEach { bySource[it.sourceKey] = it }
        loser.forEach { bySource.putIfAbsent(it.sourceKey, it) }
        return bySource.values.toList()
    }

    // endregion

    private fun BackupManga.wins(otherVersion: Long, otherLastModifiedAt: Long): Boolean {
        return version > otherVersion || (version == otherVersion && lastModifiedAt > otherLastModifiedAt)
    }

    private fun BackupChapter.wins(otherVersion: Long, otherLastModifiedAt: Long): Boolean {
        return version > otherVersion || (version == otherVersion && lastModifiedAt > otherLastModifiedAt)
    }

    /**
     * First-seen semantics for creation timestamps: 0 means unknown, so ignore it.
     */
    private fun minNonZero(a: Long, b: Long): Long = when {
        a == 0L -> b
        b == 0L -> a
        else -> minOf(a, b)
    }
}
