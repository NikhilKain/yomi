package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.data.Database
import tachiyomi.domain.library.service.LibraryPreferences

/**
 * Yomi 0.2.2 turns "mark duplicate chapters as read" on by default. Catch up once: other uploads of
 * a chapter number that is already read become read too, so series don't keep showing them unread.
 */
class MarkDuplicateChaptersReadMigration : Migration {
    override val version: Float = 4f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val libraryPreferences = migrationContext.get<LibraryPreferences>() ?: return false
        val database = migrationContext.get<Database>() ?: return false
        val enabled = libraryPreferences.markDuplicateReadChapterAsRead.get()
            .contains(LibraryPreferences.MARK_DUPLICATE_CHAPTER_READ_EXISTING)
        if (enabled) database.chaptersQueries.markDuplicatesOfReadAsRead()
        return true
    }
}
