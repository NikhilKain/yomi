package eu.kanade.tachiyomi.data.sync

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the conflict resolution rules, which are the part of sync that can silently lose data.
 */
class SyncMergerTest {

    // region Chapters

    @Test
    fun `newer version wins for read state`() {
        val local = backupOf(chapter("/c1", read = false, version = 5, lastModifiedAt = 200))
        val remote = backupOf(chapter("/c1", read = true, version = 3, lastModifiedAt = 900))

        val merged = merge(local, remote)

        // Version beats the wall clock: the remote timestamp is far newer but its version is older.
        assertFalse(merged.chapter("/c1").read)
    }

    @Test
    fun `marking unread on the newer device propagates`() {
        val local = backupOf(chapter("/c1", read = true, version = 1))
        val remote = backupOf(chapter("/c1", read = false, version = 4))

        val merged = merge(local, remote)

        assertFalse(merged.chapter("/c1").read, "a deliberate mark-as-unread must not be OR-ed away")
    }

    @Test
    fun `equal versions keep the most generous read state`() {
        val local = backupOf(chapter("/c1", read = true, lastPageRead = 12, version = 2, lastModifiedAt = 100))
        val remote = backupOf(chapter("/c1", read = false, lastPageRead = 30, version = 2, lastModifiedAt = 100))

        val merged = merge(local, remote)

        assertTrue(merged.chapter("/c1").read)
        assertEquals(30, merged.chapter("/c1").lastPageRead)
    }

    @Test
    fun `bookmarks are never dropped`() {
        val local = backupOf(chapter("/c1", bookmark = true, version = 1))
        val remote = backupOf(chapter("/c1", bookmark = false, version = 9))

        val merged = merge(local, remote)

        assertTrue(merged.chapter("/c1").bookmark)
    }

    @Test
    fun `chapters only on one side are kept`() {
        val local = backupOf(chapter("/c1"))
        val remote = backupOf(chapter("/c2"))

        val merged = merge(local, remote)

        assertEquals(setOf("/c1", "/c2"), merged.backupManga.single().chapters.map { it.url }.toSet())
    }

    // endregion

    // region Library membership

    @Test
    fun `removing from library propagates when its timestamp is newer`() {
        val local = backupOf(chapter("/c1")).also {
            it.backupManga.single().favorite = true
            it.backupManga.single().favoriteModifiedAt = 100
        }
        val remote = backupOf(chapter("/c1")).also {
            it.backupManga.single().favorite = false
            it.backupManga.single().favoriteModifiedAt = 500
        }

        val merged = merge(local, remote)

        assertFalse(merged.backupManga.single().favorite, "an unfavorite must not be resurrected")
    }

    @Test
    fun `without favorite timestamps the entry is kept`() {
        val local = backupOf(chapter("/c1")).also { it.backupManga.single().favorite = true }
        val remote = backupOf(chapter("/c1")).also { it.backupManga.single().favorite = false }

        val merged = merge(local, remote)

        assertTrue(merged.backupManga.single().favorite)
    }

    @Test
    fun `manga only on the remote is pulled in`() {
        val local = Backup(backupManga = emptyList())
        val remote = backupOf(chapter("/c1"))

        val merged = merge(local, remote)

        assertEquals(1, merged.backupManga.size)
    }

    // endregion

    // region History

    @Test
    fun `history keeps the furthest read time and longest duration`() {
        val local = backupOf(chapter("/c1")).also {
            it.backupManga.single().history = listOf(BackupHistory("/c1", lastRead = 100, readDuration = 60))
        }
        val remote = backupOf(chapter("/c1")).also {
            it.backupManga.single().history = listOf(BackupHistory("/c1", lastRead = 900, readDuration = 20))
        }

        val merged = merge(local, remote)

        val history = merged.backupManga.single().history.single()
        assertEquals(900, history.lastRead)
        assertEquals(60, history.readDuration)
    }

    // endregion

    // region Categories

    @Test
    fun `categories are unioned by name and manga references are remapped`() {
        val local = Backup(
            backupManga = listOf(manga(categories = listOf(0L))),
            backupCategories = listOf(BackupCategory(name = "Reading", order = 0)),
        )
        val remote = Backup(
            backupManga = listOf(manga(categories = listOf(1L))),
            backupCategories = listOf(
                BackupCategory(name = "Completed", order = 0),
                BackupCategory(name = "Reading", order = 1),
            ),
        )

        val merged = merge(local, remote)

        assertEquals(listOf("Completed", "Reading"), merged.backupCategories.map { it.name })
        assertEquals(listOf(0L, 1L), merged.backupCategories.map { it.order })

        // Local pointed at order 0 ("Reading") and remote at order 1 ("Reading"); both must end up
        // on the merged order for "Reading", which is 1.
        assertEquals(listOf(1L), merged.backupManga.single().categories)
    }

    // endregion

    @Test
    fun `merging is idempotent`() {
        val local = backupOf(chapter("/c1", read = true, lastPageRead = 5, version = 2))
        val remote = backupOf(chapter("/c1", read = false, lastPageRead = 9, version = 3))

        val once = merge(local, remote)
        val twice = SyncMerger.merge(once, once, localTimestamp = 1, remoteTimestamp = 1)

        assertEquals(once.chapter("/c1").read, twice.chapter("/c1").read)
        assertEquals(once.chapter("/c1").lastPageRead, twice.chapter("/c1").lastPageRead)
        assertEquals(once.backupManga.size, twice.backupManga.size)
    }

    // region helpers

    private fun merge(local: Backup, remote: Backup) =
        SyncMerger.merge(local, remote, localTimestamp = 1_000, remoteTimestamp = 2_000)

    private fun chapter(
        url: String,
        read: Boolean = false,
        bookmark: Boolean = false,
        lastPageRead: Long = 0,
        version: Long = 0,
        lastModifiedAt: Long = 0,
    ) = BackupChapter(
        url = url,
        name = url,
        read = read,
        bookmark = bookmark,
        lastPageRead = lastPageRead,
        version = version,
        lastModifiedAt = lastModifiedAt,
    )

    @Test
    fun `a removal beats a stale copy that is still favorited elsewhere`() {
        val removed = manga().also {
            it.favorite = false
            it.favoriteModifiedAt = 2_000L
        }
        val stillInLibrary = manga().also {
            it.favorite = true
            it.favoriteModifiedAt = 1_000L
        }

        val merged = SyncMerger.merge(
            local = Backup(backupManga = listOf(removed)),
            remote = Backup(backupManga = listOf(stillInLibrary)),
            localTimestamp = 1L,
            remoteTimestamp = 1L,
        )

        assertFalse(merged.backupManga.single().favorite)
    }

    @Test
    fun `a re-add after a removal wins over the removal`() {
        val readded = manga().also {
            it.favorite = true
            it.favoriteModifiedAt = 3_000L
        }
        val removed = manga().also {
            it.favorite = false
            it.favoriteModifiedAt = 2_000L
        }

        val merged = SyncMerger.merge(
            local = Backup(backupManga = listOf(removed)),
            remote = Backup(backupManga = listOf(readded)),
            localTimestamp = 1L,
            remoteTimestamp = 1L,
        )

        assertTrue(merged.backupManga.single().favorite)
    }

    private fun manga(
        chapters: List<BackupChapter> = emptyList(),
        categories: List<Long> = emptyList(),
    ) = BackupManga(
        source = 1L,
        url = "/manga",
        title = "Test",
        chapters = chapters,
        categories = categories,
    )

    private fun backupOf(vararg chapters: BackupChapter) =
        Backup(backupManga = listOf(manga(chapters = chapters.toList())))

    private fun Backup.chapter(url: String) = backupManga.single().chapters.single { it.url == url }

    // endregion
}
