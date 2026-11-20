package eu.kanade.tachiyomi.data.sync.service

import eu.kanade.tachiyomi.data.backup.models.Backup
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import okio.Buffer
import okio.Sink
import okio.buffer
import okio.gzip
import okio.source
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.sync.service.SyncPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import kotlin.time.Clock

/**
 * Stores the sync payload as a single gzipped protobuf file in the user own Google Drive.
 *
 * The file is byte-for-byte a normal `.tachibk` backup, so it doubles as an off-device backup:
 * it can be downloaded from Drive and fed straight into Restore backup if sync is ever abandoned.
 */
class GoogleDriveSyncService(
    private val api: GoogleDriveApi = GoogleDriveApi(),
    private val syncPreferences: SyncPreferences = Injekt.get(),
    private val protoBuf: ProtoBuf = Injekt.get(),
) : SyncService {

    override fun isAuthenticated(): Boolean = syncPreferences.isGoogleDriveLoggedIn()

    override suspend fun pull(): RemoteSnapshot? {
        val file = api.findSyncFile() ?: return null

        val bytes = api.download(file.id)
        if (bytes.isEmpty()) return null

        val backup = try {
            decode(bytes)
        } catch (e: SerializationException) {
            // A truncated or foreign file is not something we can merge against. Refusing here is
            // safer than treating it as "no remote data", which would clobber it on the next push.
            throw IOException("The sync file in Google Drive is corrupt or not a Yomi backup", e)
        }

        return RemoteSnapshot(
            backup = backup,
            revision = file.version,
            timestamp = file.appProperties[GoogleDriveApi.PROP_TIMESTAMP]?.toLongOrNull()
                ?: file.modifiedTimeMillis(),
            deviceId = file.appProperties[GoogleDriveApi.PROP_DEVICE_ID].orEmpty(),
        )
    }

    override suspend fun push(backup: Backup, expectedRevision: String?): String {
        val existing = api.findSyncFile()

        // Drive has no conditional write, so this is a check-then-act with a small race window.
        // Two devices syncing in the same second could still interleave; the loser simply picks up
        // the merged result on its next sync rather than losing data outright, because the merge
        // itself is idempotent and version-driven.
        if (existing != null && expectedRevision != null && existing.version != expectedRevision) {
            throw SyncConflictException(
                "Google Drive copy changed during sync (expected revision $expectedRevision, " +
                    "found ${existing.version})",
            )
        }
        if (existing == null && expectedRevision != null) {
            logcat(LogPriority.WARN) { "Remote sync file vanished mid-sync, recreating it" }
        }

        val now = Clock.System.now().toEpochMilliseconds()
        val uploaded = api.upload(
            fileId = existing?.id,
            content = encode(backup),
            appProperties = mapOf(
                GoogleDriveApi.PROP_DEVICE_ID to syncPreferences.uniqueDeviceId(),
                GoogleDriveApi.PROP_TIMESTAMP to now.toString(),
            ),
        )
        return uploaded.version
    }

    private fun encode(backup: Backup): ByteArray {
        val raw = protoBuf.encodeToByteArray(Backup.serializer(), backup)
        val buffer = Buffer()
        (buffer as Sink).gzip().buffer().use { it.write(raw) }
        return buffer.readByteArray()
    }

    private fun decode(bytes: ByteArray): Backup {
        val source = bytes.inputStream().source().buffer()
        val body = source.peek().apply { require(2) }.readShort()
        val decompressed = if (body.toInt() == GZIP_MAGIC) {
            source.gzip().buffer()
        } else {
            source
        }
        return decompressed.use { protoBuf.decodeFromByteArray(Backup.serializer(), it.readByteArray()) }
    }

    companion object {
        private const val GZIP_MAGIC = 0x1f8b
    }
}
