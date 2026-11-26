package mihon.core.migration.migrations

import mihon.core.migration.Migration

val migrations: List<Migration>
    get() = listOf(
        SetupBackupCreateMigration(),
        SetupSyncMigration(),
        SetupLibraryUpdateMigration(),
        TrustExtensionRepositoryMigration(),
        CategoryPreferencesCleanupMigration(),
        InstallationIdMigration(),
        VerticalNavigatorMigration(),
        MarkDuplicateChaptersReadMigration(),
    )
