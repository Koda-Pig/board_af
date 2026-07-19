package za.co.boardaf.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import za.co.boardaf.data.LibrarySnapshot
import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.GradeSystem
import za.co.boardaf.model.Problem
import za.co.boardaf.setter.SetterMode

/** A problem document as last seen on the server (or in Firestore's local cache). */
data class RemoteProblemRecord(
    val problem: Problem,
    /** Canonical [za.co.boardaf.data.SnapshotCodec.encodeProblem] string, used for comparisons. */
    val encoded: String,
    val revision: Long,
    /** True while this device's own write has not yet been acknowledged by the server. */
    val pendingWrite: Boolean,
)

/** The board setup + settings document as last seen remotely. */
data class RemoteBoardRecord(
    val setup: BoardSetup,
    val gradeSystem: GradeSystem,
    val setterMode: SetterMode,
    val encoded: String,
    val revision: Long,
    val pendingWrite: Boolean,
)

/** Everything the planner needs to know about the remote store. */
data class RemoteLibrary(
    val board: RemoteBoardRecord? = null,
    val problems: Map<String, RemoteProblemRecord> = emptyMap(),
    /** Remote docs that could not be decoded. Never overwritten by pushes. */
    val unreadableProblemIds: Set<String> = emptySet(),
    val boardUnreadable: Boolean = false,
)

/**
 * Last server-acknowledged content, per record. A completed plan includes the
 * content it pushed; callers must persist these baselines only after the remote
 * write succeeds.
 */
data class SyncBaselines(
    val board: String? = null,
    val problems: Map<String, String> = emptyMap(),
)

data class ProblemPush(val problem: Problem, val revision: Long)

data class BoardPush(
    val setup: BoardSetup,
    val gradeSystem: GradeSystem,
    val setterMode: SetterMode,
    val revision: Long,
)

data class SyncPlan(
    /** Non-null when remote changes should be adopted locally (includes conflict copies). */
    val mergedSnapshot: LibrarySnapshot?,
    val problemPushes: List<ProblemPush>,
    val boardPush: BoardPush?,
    val baselines: SyncBaselines,
    val issues: List<String> = emptyList(),
)

enum class CloudSyncAvailability {
    /** No google-services.json was bundled; sync is compiled out at runtime. */
    UNCONFIGURED,
    READY,
}

data class CloudSyncState(
    val availability: CloudSyncAvailability = CloudSyncAvailability.UNCONFIGURED,
    val userEmail: String? = null,
    val isAuthBusy: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
) {
    val isSignedIn: Boolean get() = userEmail != null
}

/** A merged snapshot the ViewModel should adopt, tagged with the local state it was computed from. */
data class MergedLibrary(
    val snapshot: LibrarySnapshot,
    /** [za.co.boardaf.data.SnapshotCodec.encode] of the local snapshot the plan was based on. */
    val basedOnFingerprint: String,
)

/** Boundary the ViewModel talks to; Firestore-backed in production, fake in tests. */
interface CloudSync {
    val state: StateFlow<CloudSyncState>
    val mergedSnapshots: Flow<MergedLibrary>

    fun start(scope: CoroutineScope)
    fun onLocalChanged(snapshot: LibrarySnapshot)
    fun signIn(email: String, password: String)
    fun createAccount(email: String, password: String)
    fun signOut()
    fun syncNow()
}
