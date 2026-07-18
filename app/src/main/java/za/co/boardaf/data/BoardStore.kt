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

data class LibrarySnapshot(
    val setup: BoardSetup,
    val problems: List<Problem>,
    val gradeSystem: GradeSystem = GradeSystem.FRENCH,
    val setterMode: SetterMode = SetterMode.GUIDED,
    val unreadable: List<UnreadableRecord> = emptyList(),
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
