package za.co.boardaf

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import za.co.boardaf.data.BoardLoadResult
import za.co.boardaf.data.BoardStore
import za.co.boardaf.data.DeletedProblem
import za.co.boardaf.data.DeletedProblems
import za.co.boardaf.data.LibrarySnapshot
import za.co.boardaf.data.SnapshotCodec
import za.co.boardaf.data.sync.RemoteLibrary
import za.co.boardaf.data.sync.RemoteProblemRecord
import za.co.boardaf.data.sync.SyncBaselines
import za.co.boardaf.data.sync.SyncCodec
import za.co.boardaf.data.sync.SyncPlanner
import za.co.boardaf.model.Accent
import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.BoulderGrade
import za.co.boardaf.model.Problem
import za.co.boardaf.model.ProblemAssignment
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.model.PublicationState

/**
 * "Recently deleted": a delete now keeps the payload as well as the tombstone, so
 * it can be undone locally. The load-bearing rules are that the tombstone always
 * outlives the payload — dropping it would let sync re-adopt the record — and
 * that a restore never reuses the deleted id.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecentlyDeletedTest {

    private class FakeStore(private val initial: LibrarySnapshot) : BoardStore {
        var saved: LibrarySnapshot? = null

        override suspend fun load() = BoardLoadResult(initial)

        override suspend fun save(snapshot: LibrarySnapshot) {
            saved = snapshot
        }
    }

    private fun problem(
        id: String,
        name: String = id,
        state: PublicationState = PublicationState.PUBLISHED,
        assignments: List<ProblemAssignment> = listOf(
            ProblemAssignment("h30", ProblemHoldRole.START),
            ProblemAssignment("h06", ProblemHoldRole.FINISH),
        ),
    ) = Problem(
        id = id,
        name = name,
        grade = BoulderGrade.F6B,
        accent = Accent.MOSS,
        setter = "You",
        publicationState = state,
        forerunConfirmedAt = 1L,
        assignments = assignments,
    )

    private fun snapshot(
        problems: List<Problem> = emptyList(),
        deletedIds: Set<String> = emptySet(),
        deleted: List<DeletedProblem> = emptyList(),
    ) = LibrarySnapshot(
        setup = BoardSetup.default(),
        problems = problems,
        deletedProblemIds = deletedIds,
        deletedProblems = deleted,
    )

    private fun viewModel(store: FakeStore) = BoardViewModel(
        application = Application(),
        store = store,
        cloudSync = null,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Deleting ------------------------------------------------------------------

    @Test
    fun `deleting keeps the payload alongside the tombstone`() = runTest {
        val store = FakeStore(snapshot(listOf(problem("moss"), problem("tide"))))
        val vm = viewModel(store)

        vm.deleteProblem("moss")

        val state = vm.state.value
        assertEquals(listOf("tide"), state.problems.map { it.id })
        assertEquals(setOf("moss"), state.deletedProblemIds)
        assertEquals("moss", state.deletedProblems.single().problem.id)
        // …and it reached storage, not just the in-memory state.
        assertEquals("moss", store.saved?.deletedProblems?.single()?.problem?.id)
    }

    @Test
    fun `the delete message points at where the problem went`() = runTest {
        val store = FakeStore(snapshot(listOf(problem("moss", name = "Moss line"))))
        val vm = viewModel(store)
        val events = mutableListOf<BoardEvent>()
        val job = launchCollect(vm, events)

        vm.deleteProblem("moss")

        assertEquals(
            "Deleted Moss line — recoverable from Recently deleted.",
            events.filterIsInstance<BoardEvent.Message>().single().text,
        )
        job.cancel()
    }

    // --- Restoring -----------------------------------------------------------------

    @Test
    fun `restoring brings the problem back under a new id and keeps the tombstone`() = runTest {
        val store = FakeStore(snapshot(listOf(problem("moss", name = "Moss line"))))
        val vm = viewModel(store)

        vm.deleteProblem("moss")
        vm.restoreDeletedProblem("moss")

        val state = vm.state.value
        val restored = state.problems.single()
        assertEquals("Moss line", restored.name)
        assertEquals(BoulderGrade.F6B, restored.grade)
        assertEquals(2, restored.assignments.size)
        // A reused id would be deleted all over again by the next merge, because
        // SyncPlanner lets a tombstone on either side win.
        assertNotEquals("moss", restored.id)
        assertEquals(setOf("moss"), state.deletedProblemIds)
        assertTrue(state.deletedProblems.isEmpty())
    }

    @Test
    fun `restoring a problem the board no longer supports returns it for review`() = runTest {
        // A start on h43: a kickboard, foot-only hold, so the validator rejects it.
        val broken = problem(
            "broken",
            assignments = listOf(
                ProblemAssignment("h43", ProblemHoldRole.START),
                ProblemAssignment("h06", ProblemHoldRole.FINISH),
            ),
        )
        val store = FakeStore(snapshot(listOf(broken)))
        val vm = viewModel(store)

        vm.deleteProblem("broken")
        vm.restoreDeletedProblem("broken")

        assertEquals(PublicationState.NEEDS_REVIEW, vm.state.value.problems.single().publicationState)
    }

    @Test
    fun `restoring a still-valid published problem keeps it published`() = runTest {
        val store = FakeStore(snapshot(listOf(problem("moss"))))
        val vm = viewModel(store)

        vm.deleteProblem("moss")
        vm.restoreDeletedProblem("moss")

        assertEquals(PublicationState.PUBLISHED, vm.state.value.problems.single().publicationState)
    }

    @Test
    fun `restoring an id that is not recoverable does nothing`() = runTest {
        val store = FakeStore(snapshot(listOf(problem("moss"))))
        val vm = viewModel(store)

        vm.restoreDeletedProblem("never-existed")

        assertEquals(listOf("moss"), vm.state.value.problems.map { it.id })
    }

    // --- Forgetting ----------------------------------------------------------------

    @Test
    fun `forgetting drops the payload but never the tombstone`() = runTest {
        val store = FakeStore(snapshot(listOf(problem("moss"))))
        val vm = viewModel(store)

        vm.deleteProblem("moss")
        vm.forgetDeletedProblem("moss")

        assertTrue(vm.state.value.deletedProblems.isEmpty())
        assertEquals(setOf("moss"), vm.state.value.deletedProblemIds)
        assertEquals(setOf("moss"), store.saved?.deletedProblemIds)
    }

    // --- Expiry --------------------------------------------------------------------

    @Test
    fun `pruning keeps records inside the window and drops the rest`() {
        val now = 10 * DeletedProblems.RETENTION_MS
        val fresh = DeletedProblem(problem("fresh"), deletedAt = now - 1)
        val stale = DeletedProblem(problem("stale"), deletedAt = now - DeletedProblems.RETENTION_MS)

        assertEquals(listOf(fresh), DeletedProblems.pruned(listOf(fresh, stale), now))
    }

    @Test
    fun `loading expires stale payloads and leaves their tombstones in place`() = runTest {
        val expired = DeletedProblem(
            problem("old"),
            deletedAt = System.currentTimeMillis() - DeletedProblems.RETENTION_MS - 1,
        )
        val kept = DeletedProblem(problem("recent"), deletedAt = System.currentTimeMillis())
        val store = FakeStore(
            snapshot(
                deletedIds = setOf("old", "recent"),
                deleted = listOf(expired, kept),
            ),
        )

        val vm = viewModel(store)

        assertEquals(listOf("recent"), vm.state.value.deletedProblems.map { it.problem.id })
        assertEquals(setOf("old", "recent"), vm.state.value.deletedProblemIds)
        // The pruning was written back rather than being recomputed every launch.
        assertEquals(setOf("old", "recent"), store.saved?.deletedProblemIds)
        assertEquals(listOf("recent"), store.saved?.deletedProblems?.map { it.problem.id })
    }

    // --- Persistence ---------------------------------------------------------------

    @Test
    fun `payloads round-trip through the snapshot codec`() {
        val record = DeletedProblem(problem("moss", name = "Moss line"), deletedAt = 1_756_000_000_000L)
        val original = snapshot(deletedIds = setOf("moss"), deleted = listOf(record))

        val decoded = SnapshotCodec.decode(SnapshotCodec.encode(original))
        val result = decoded as SnapshotCodec.DecodeResult.Success

        assertEquals(listOf(record), result.snapshot.deletedProblems)
    }

    @Test
    fun `a payload with no tombstone is dropped rather than resurrecting the problem`() {
        // Hand-built: only reachable if a snapshot were edited or half-written.
        val orphan = snapshot(
            deletedIds = emptySet(),
            deleted = listOf(DeletedProblem(problem("moss"), deletedAt = 1L)),
        )

        val decoded = SnapshotCodec.decode(SnapshotCodec.encode(orphan))
        val result = decoded as SnapshotCodec.DecodeResult.Success

        assertTrue(result.snapshot.deletedProblems.isEmpty())
        assertTrue(result.snapshot.problems.isEmpty())
    }

    // --- Cloud sync ------------------------------------------------------------------

    @Test
    fun `a delete from another device is just as recoverable as a local one`() {
        val local = snapshot(listOf(problem("moss", name = "Moss line")))
        val remote = RemoteLibrary(
            problems = mapOf(
                "moss" to RemoteProblemRecord(
                    problem = problem("moss"),
                    encoded = SyncCodec.encodeProblem(problem("moss")),
                    revision = 2,
                    pendingWrite = false,
                    deleted = true,
                    deletedAt = 5_000L,
                ),
            ),
        )

        val plan = SyncPlanner.plan(local, remote, SyncBaselines(), now = 6_000L)
        val merged = plan.mergedSnapshot

        assertEquals(setOf("moss"), merged?.deletedProblemIds)
        assertEquals("Moss line", merged?.deletedProblems?.single()?.problem?.name)
        assertEquals(5_000L, merged?.deletedProblems?.single()?.deletedAt)
    }

    @Test
    fun `a retiring tombstone takes its payload with it`() {
        val now = SyncPlanner.TOMBSTONE_RETENTION_MS * 10
        val local = snapshot(
            deletedIds = setOf("moss"),
            deleted = listOf(DeletedProblem(problem("moss"), deletedAt = 1L)),
        )
        val remote = RemoteLibrary(
            problems = mapOf(
                "moss" to RemoteProblemRecord(
                    problem = problem("moss"),
                    encoded = SyncCodec.encodeProblem(problem("moss")),
                    revision = 2,
                    pendingWrite = false,
                    deleted = true,
                    deletedAt = now - SyncPlanner.TOMBSTONE_RETENTION_MS,
                ),
            ),
        )

        val plan = SyncPlanner.plan(local, remote, SyncBaselines(), now)

        assertEquals(emptySet<String>(), plan.mergedSnapshot?.deletedProblemIds)
        assertEquals(emptyList<DeletedProblem>(), plan.mergedSnapshot?.deletedProblems)
    }

    @Test
    fun `a plan that changes nothing still leaves recovery payloads alone`() {
        val record = DeletedProblem(problem("moss"), deletedAt = 1L)
        val local = snapshot(
            problems = listOf(problem("tide")),
            deletedIds = setOf("moss"),
            deleted = listOf(record),
        )
        val remote = RemoteLibrary(
            problems = mapOf(
                "tide" to RemoteProblemRecord(
                    problem = problem("tide"),
                    encoded = SyncCodec.encodeProblem(problem("tide")),
                    revision = 1,
                    pendingWrite = false,
                ),
            ),
        )

        val plan = SyncPlanner.plan(local, remote, SyncBaselines(), now = 100L)

        // Nothing to adopt, so no merged snapshot at all — the local one stands.
        assertNull(plan.mergedSnapshot)
    }

    private fun TestScope.launchCollect(
        vm: BoardViewModel,
        into: MutableList<BoardEvent>,
    ): Job = launch(UnconfinedTestDispatcher(testScheduler)) {
        vm.events.collect { into += it }
    }
}
