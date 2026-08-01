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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import za.co.boardaf.data.BoardLoadResult
import za.co.boardaf.data.BoardStore
import za.co.boardaf.data.LibrarySnapshot
import za.co.boardaf.model.Accent
import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.BoulderGrade
import za.co.boardaf.model.Problem
import za.co.boardaf.model.ProblemAssignment
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.model.PublicationState
import za.co.boardaf.setter.GuidedStep

/**
 * Coordination tests for [BoardViewModel]: selection, archive/undo, delete
 * tombstones, and the autosave-before-restart path. The rules themselves live in
 * their own pure collaborators and are tested there.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModelTest {

    private class FakeStore(private val initial: LibrarySnapshot) : BoardStore {
        var saved: LibrarySnapshot? = null
        var saveCount = 0

        override suspend fun load() = BoardLoadResult(initial)

        override suspend fun save(snapshot: LibrarySnapshot) {
            saved = snapshot
            saveCount++
        }
    }

    private fun problem(
        id: String,
        name: String = id,
        state: PublicationState = PublicationState.PUBLISHED,
    ) = Problem(
        id = id,
        name = name,
        grade = BoulderGrade.F6A,
        accent = Accent.SKY,
        setter = "You",
        publicationState = state,
        forerunConfirmedAt = 1L,
        assignments = listOf(
            ProblemAssignment("h30", ProblemHoldRole.START),
            ProblemAssignment("h06", ProblemHoldRole.FINISH),
        ),
    )

    private fun snapshotOf(vararg problems: Problem) = LibrarySnapshot(
        setup = BoardSetup.default(),
        problems = problems.toList(),
    )

    private fun viewModel(store: FakeStore) = BoardViewModel(
        application = Application(),
        store = store,
        // Cloud sync is a separate boundary with its own tests; this is the
        // "no google-services.json" shape the app already handles at runtime.
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

    // --- Selection ---------------------------------------------------------------

    @Test
    fun `selectAdjacentProblem walks active problems and clamps at both ends`() = runTest {
        val store = FakeStore(snapshotOf(problem("a"), problem("b"), problem("c")))
        val vm = viewModel(store)
        assertEquals("a", vm.state.value.selectedProblemId)

        vm.selectAdjacentProblem(1)
        assertEquals("b", vm.state.value.selectedProblemId)

        vm.selectAdjacentProblem(1)
        assertEquals("c", vm.state.value.selectedProblemId)

        // Clamped, not wrapped.
        vm.selectAdjacentProblem(1)
        assertEquals("c", vm.state.value.selectedProblemId)

        vm.selectAdjacentProblem(-1)
        assertEquals("b", vm.state.value.selectedProblemId)
    }

    @Test
    fun `selectAdjacentProblem skips archived problems`() = runTest {
        val store = FakeStore(
            snapshotOf(
                problem("a"),
                problem("archived", state = PublicationState.ARCHIVED),
                problem("c"),
            ),
        )
        val vm = viewModel(store)

        vm.selectAdjacentProblem(1)

        assertEquals("c", vm.state.value.selectedProblemId)
    }

    @Test
    fun `selectAdjacentProblem does nothing during a setter session`() = runTest {
        val store = FakeStore(snapshotOf(problem("a"), problem("b")))
        val vm = viewModel(store)
        vm.startSetting()

        vm.selectAdjacentProblem(1)

        assertEquals("a", vm.state.value.selectedProblemId)
    }

    // --- Archive and undo --------------------------------------------------------

    @Test
    fun `archiveProblem emits an undo payload carrying the previous state`() = runTest {
        val store = FakeStore(snapshotOf(problem("draft-a", state = PublicationState.DRAFT)))
        val vm = viewModel(store)
        val events = mutableListOf<BoardEvent>()
        val job = launchCollect(vm, events)

        vm.archiveProblem("draft-a")

        val message = events.filterIsInstance<BoardEvent.Message>().single()
        assertEquals("Undo", message.actionLabel)
        assertEquals(
            BoardEvent.ArchiveUndo("draft-a", PublicationState.DRAFT),
            message.undoArchive,
        )
        job.cancel()
    }

    @Test
    fun `restoreArchived puts a draft back as a draft`() = runTest {
        val store = FakeStore(snapshotOf(problem("draft-a", state = PublicationState.DRAFT)))
        val vm = viewModel(store)

        vm.archiveProblem("draft-a")
        assertEquals(
            PublicationState.ARCHIVED,
            vm.state.value.problems.single().publicationState,
        )

        vm.restoreArchived(BoardEvent.ArchiveUndo("draft-a", PublicationState.DRAFT))

        // unarchiveProblem would have demoted this to PUBLISHED; undo must not.
        assertEquals(
            PublicationState.DRAFT,
            vm.state.value.problems.single().publicationState,
        )
    }

    @Test
    fun `archiving an untitled draft names it in the message`() = runTest {
        val store = FakeStore(snapshotOf(problem("d", name = "", state = PublicationState.DRAFT)))
        val vm = viewModel(store)
        val events = mutableListOf<BoardEvent>()
        val job = launchCollect(vm, events)

        vm.archiveProblem("d")

        assertEquals(
            "Archived Untitled draft",
            events.filterIsInstance<BoardEvent.Message>().single().text,
        )
        job.cancel()
    }

    // --- Delete ------------------------------------------------------------------

    @Test
    fun `deleteProblem tombstones the id and persists`() = runTest {
        val store = FakeStore(snapshotOf(problem("a"), problem("b")))
        val vm = viewModel(store)

        vm.deleteProblem("a")

        assertEquals(listOf("b"), vm.state.value.problems.map { it.id })
        assertEquals(setOf("a"), vm.state.value.deletedProblemIds)
        assertEquals(setOf("a"), store.saved?.deletedProblemIds)
    }

    @Test
    fun `deleting the selected problem selects a surviving active one`() = runTest {
        val store = FakeStore(snapshotOf(problem("a"), problem("b")))
        val vm = viewModel(store)
        assertEquals("a", vm.state.value.selectedProblemId)

        vm.deleteProblem("a")

        assertEquals("b", vm.state.value.selectedProblemId)
    }

    @Test
    fun `deleting the problem being edited closes the setter`() = runTest {
        val store = FakeStore(snapshotOf(problem("a"), problem("b")))
        val vm = viewModel(store)
        vm.startEditing("a")
        assertTrue(vm.state.value.isSetting)

        vm.deleteProblem("a")

        assertFalse(vm.state.value.isSetting)
    }

    @Test
    fun `deleting an unknown id changes nothing`() = runTest {
        val store = FakeStore(snapshotOf(problem("a")))
        val vm = viewModel(store)
        val savesBefore = store.saveCount

        vm.deleteProblem("nope")

        assertEquals(listOf("a"), vm.state.value.problems.map { it.id })
        assertTrue(vm.state.value.deletedProblemIds.isEmpty())
        assertEquals(savesBefore, store.saveCount)
    }

    // --- Autosave before restart -------------------------------------------------

    @Test
    fun `startSetting autosaves the live draft before starting a new one`() = runTest {
        val store = FakeStore(snapshotOf())
        val vm = viewModel(store)

        vm.startSetting()
        vm.setDraftName("Half-finished line")
        // Taps are locked on the feet-rule step by design; walk to a hold step first.
        vm.goToGuidedStep(GuidedStep.START)
        vm.tapHold("h30")

        vm.startSetting()

        // The in-progress work survived as a library record...
        val saved = vm.state.value.problems.single()
        assertEquals("Half-finished line", saved.name)
        assertEquals(1, saved.assignments.size)
        // ...and the new session started from an empty board.
        assertTrue(vm.state.value.isSetting)
        assertEquals("", vm.state.value.setter.draft.name)
        assertTrue(vm.state.value.setter.draft.assignments.isEmpty())
        assertNull(vm.state.value.setter.draft.editingProblemId)
    }

    @Test
    fun `startSetting on an empty session leaves no record behind`() = runTest {
        val store = FakeStore(snapshotOf())
        val vm = viewModel(store)

        vm.startSetting()
        vm.startSetting()

        assertTrue(vm.state.value.problems.isEmpty())
    }

    @Test
    fun `selecting another problem autosaves the live draft`() = runTest {
        val store = FakeStore(snapshotOf(problem("a")))
        val vm = viewModel(store)

        vm.startSetting()
        vm.setDraftName("Rescued")
        vm.selectProblem("a")

        assertFalse(vm.state.value.isSetting)
        assertNotNull(vm.state.value.problems.firstOrNull { it.name == "Rescued" })
    }

    // --- Setter attribution (F27) ------------------------------------------------

    @Test
    fun `duplicating a problem reassigns the copy to the current setter`() = runTest {
        val store = FakeStore(snapshotOf(problem("a").copy(setter = "Maya")))
        val vm = viewModel(store)

        vm.duplicateProblem("a")

        assertEquals("You", vm.state.value.setter.draft.setter)
        assertEquals("a copy", vm.state.value.setter.draft.name)
    }

    /**
     * Collects on an unconfined dispatcher so the subscription is live before the
     * action under test emits — [BoardViewModel.events] is a hot SharedFlow with no
     * replay, so a queued collector would miss the event entirely.
     */
    private fun TestScope.launchCollect(
        vm: BoardViewModel,
        into: MutableList<BoardEvent>,
    ): Job = launch(UnconfinedTestDispatcher(testScheduler)) {
        vm.events.collect { into += it }
    }
}
