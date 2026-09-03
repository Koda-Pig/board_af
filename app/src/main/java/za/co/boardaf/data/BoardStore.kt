package za.co.boardaf.data

import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.GradeSystem
import za.co.boardaf.model.Problem
import za.co.boardaf.setter.SetterMode

/** One record that could not be decoded. The raw payload is retained for recovery. */
data class UnreadableRecord(
    val raw: String,
    val error: String,
    val source: String,
) {
    companion object {
        const val SOURCE_V1 = "v1"
        const val SOURCE_V2 = "v2"
    }
}

/**
 * A deleted problem kept for recovery. The tombstone id alone propagates the
 * delete; this keeps the payload as well, so "Recently deleted" can put the
 * problem back without a round trip to the server.
 */
data class DeletedProblem(
    val problem: Problem,
    val deletedAt: Long,
)

object DeletedProblems {
    /**
     * How long a deleted problem stays recoverable on this device. Matches the
     * remote tombstone retention so the two windows don't disagree, but they are
     * independent: retiring a *payload* only ends recovery, while retiring the
     * *tombstone* would stop the delete propagating.
     */
    const val RETENTION_MS: Long = 30L * 24 * 60 * 60 * 1000

    fun pruned(records: List<DeletedProblem>, now: Long): List<DeletedProblem> =
        records.filter { now - it.deletedAt < RETENTION_MS }
}

data class LibrarySnapshot(
    val setup: BoardSetup,
    val problems: List<Problem>,
    val gradeSystem: GradeSystem = GradeSystem.FRENCH,
    val setterMode: SetterMode = SetterMode.GUIDED,
    val unreadable: List<UnreadableRecord> = emptyList(),
    /**
     * Tombstones for problems deleted on this device. Kept so cloud sync can
     * propagate the delete instead of re-adopting the record from the server.
     */
    val deletedProblemIds: Set<String> = emptySet(),
    /**
     * Recoverable payloads for the tombstones above. Every id here is also in
     * [deletedProblemIds]; the reverse need not hold — a tombstone adopted from
     * another device before this one ever saw the problem has no payload.
     */
    val deletedProblems: List<DeletedProblem> = emptyList(),
)

enum class StorageIssueCode {
    LEGACY_PAYLOAD_CORRUPT,
    LEGACY_RECORD_UNREADABLE,
    SNAPSHOT_CORRUPT,
    SNAPSHOT_RECORD_UNREADABLE,
    SNAPSHOT_VERIFY_FAILED,
}

data class StorageIssue(
    val code: StorageIssueCode,
    val message: String,
)

data class BoardLoadResult(
    val snapshot: LibrarySnapshot,
    val issues: List<StorageIssue> = emptyList(),
)

/** Storage boundary. Implementations must never silently drop or rewrite user data. */
interface BoardStore {
    suspend fun load(): BoardLoadResult
    suspend fun save(snapshot: LibrarySnapshot)
}
