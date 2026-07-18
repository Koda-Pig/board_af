package za.co.boardaf.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import za.co.boardaf.model.BoardDefaults
import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.ConfiguredBoard
import za.co.boardaf.model.GradeSystem

/** Raw persistence primitives, kept separate so the store logic stays JVM-testable. */
interface SnapshotIO {
    suspend fun readSnapshot(): String?
    suspend fun writeSnapshot(value: String)
    suspend fun writeCorruptBackup(value: String)
    suspend fun readLegacyProblems(): String?
    suspend fun readLegacyGradeSystem(): String?
}

/**
 * v2 snapshot store with an explicit v1 migration path. The legacy `problems_v1`
 * payload is read but never modified or deleted; it stays behind as a recovery
 * backup even after the v2 snapshot is written and verified.
 */
class VersionedBoardStore(private val io: SnapshotIO) : BoardStore {
    private val mutex = Mutex()

    override suspend fun load(): BoardLoadResult = mutex.withLock {
        val issues = mutableListOf<StorageIssue>()

        val rawSnapshot = runCatching { io.readSnapshot() }.getOrElse { failure ->
            issues += StorageIssue(
                StorageIssueCode.SNAPSHOT_CORRUPT,
                "The saved library file couldn't be opened (${failure.message}).",
            )
            null
        }
        if (rawSnapshot != null) {
            when (val decoded = SnapshotCodec.decode(rawSnapshot)) {
                is SnapshotCodec.DecodeResult.Success ->
                    return BoardLoadResult(decoded.snapshot, issues + decoded.issues)
                is SnapshotCodec.DecodeResult.Corrupt -> {
                    runCatching { io.writeCorruptBackup(rawSnapshot) }
                    issues += StorageIssue(
                        StorageIssueCode.SNAPSHOT_CORRUPT,
                        "The saved library couldn't be read (${decoded.error}). " +
                            "A copy was kept and the previous backup was restored.",
                    )
                }
            }
        }

        val setup = BoardSetup.default()
        val board = ConfiguredBoard.from(setup)
        val gradeSystem = readLegacyGradeSystem()
        val legacy = runCatching { io.readLegacyProblems() }.getOrNull()

        val snapshot = if (legacy != null) {
            val migrated = LegacyProblemMigration.migrate(legacy, board)
            if (migrated.payloadCorrupt) {
                issues += StorageIssue(
                    StorageIssueCode.LEGACY_PAYLOAD_CORRUPT,
                    "The previous library couldn't be read. The original data was kept for recovery.",
                )
            } else if (migrated.unreadable.isNotEmpty()) {
                issues += StorageIssue(
                    StorageIssueCode.LEGACY_RECORD_UNREADABLE,
                    "${migrated.unreadable.size} problem(s) from the previous library couldn't be read " +
                        "and were kept for recovery.",
                )
            }
            LibrarySnapshot(
                setup = setup,
                problems = migrated.problems,
                gradeSystem = gradeSystem,
                unreadable = migrated.unreadable,
            )
        } else {
            LibrarySnapshot(
                setup = setup,
                problems = BoardDefaults.problems,
                gradeSystem = gradeSystem,
            )
        }

        val encoded = SnapshotCodec.encode(snapshot)
        val verified = runCatching {
            io.writeSnapshot(encoded)
            io.readSnapshot()?.let { SnapshotCodec.decode(it) }
        }.getOrNull()
        if (verified !is SnapshotCodec.DecodeResult.Success) {
            issues += StorageIssue(
                StorageIssueCode.SNAPSHOT_VERIFY_FAILED,
                "The migrated library couldn't be written and read back. " +
                    "Your previous data is untouched; changes made now may not persist.",
            )
        }

        BoardLoadResult(snapshot, issues)
    }

    override suspend fun save(snapshot: LibrarySnapshot) {
        mutex.withLock {
            io.writeSnapshot(SnapshotCodec.encode(snapshot))
        }
    }

    private suspend fun readLegacyGradeSystem(): GradeSystem =
        runCatching { io.readLegacyGradeSystem() }.getOrNull()
            ?.let { name -> GradeSystem.entries.firstOrNull { it.name == name } }
            ?: GradeSystem.FRENCH
}
