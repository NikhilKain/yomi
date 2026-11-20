package mihon.core.migration.migrations

import android.app.Application
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.domain.sync.service.SyncPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Reschedules the periodic sync on every launch, and pulls once on start when asked to, so a device
 * picked up after a while shows what was read elsewhere without waiting for the next interval.
 */
class SetupSyncMigration : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<Application>() ?: return false
        val syncPreferences = migrationContext.get<SyncPreferences>() ?: Injekt.get()

        SyncDataJob.setupTask(context)

        if (syncPreferences.isSyncEnabled() && syncPreferences.syncAutomatically().get() && syncPreferences.syncOnAppStart().get()) {
            SyncDataJob.startNow(context)
        }

        return true
    }
}
