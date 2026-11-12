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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.CoroutineDispatcher
import za.co.boardaf.data.BoardImageSource
import za.co.boardaf.data.BoardLoadResult
import za.co.boardaf.data.BoardStore
import za.co.boardaf.data.LibrarySnapshot
import za.co.boardaf.model.Accent
import za.co.boardaf.model.BoardDefaults
import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.BoulderGrade
import za.co.boardaf.model.HoldDefinition
import za.co.boardaf.model.NormalizedPoint
import za.co.boardaf.model.Problem
import za.co.boardaf.model.ProblemAssignment
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.model.PublicationState
import za.co.boardaf.vision.DetectorFrame

/**
 * Re-mapping through [BoardViewModel]. Every hold edit re-validates the library,
 * so what matters here is that the library's state follows the map, that the
 * change reaches storage, and that a demotion is reported rather than silent —
 * the setter is looking at the board, not at the problem list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HoldMapViewModelTest {

    private class FakeStore(private val initial: LibrarySnapshot) : BoardStore {
        var saved: LibrarySnapshot? = null

        override suspend fun load() = BoardLoadResult(initial)

        override suspend fun save(snapshot: LibrarySnapshot) {
            saved = snapshot
        }
    }

    /** Starts on h30 and finishes on h06, both well up the main board. */
    private fun published(id: String = "p1") = Problem(
        id = id,
        name = id,
        grade = BoulderGrade.F6A,
        accent = Accent.SKY,
        setter = "You",
        publicationState = PublicationState.PUBLISHED,
        forerunConfirmedAt = 1L,
        assignments = listOf(
            ProblemAssignment("h30", ProblemHoldRole.START),
            ProblemAssignment("h06", ProblemHoldRole.FINISH),
        ),
    )

    private fun store(vararg problems: Problem) = FakeStore(
        LibrarySnapshot(setup = BoardSetup.default(), problems = problems.toList()),
    )

    /** Hands the detector a wall we drew ourselves, so the test controls the holds. */
    private class FakeImageSource(var frame: DetectorFrame?) : BoardImageSource {
        var loads = 0
        var onLoad: (() -> Unit)? = null

        override fun load(photoPath: String?): DetectorFrame? {
            loads++
            onLoad?.invoke()
            return frame
        }
    }

    private fun viewModel(
        store: FakeStore,
        images: BoardImageSource? = null,
        dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
    ) = BoardViewModel(
        application = Application(),
        store = store,
        cloudSync = null,
        photoStore = null,
        imageSource = images,
        detectorDispatcher = dispatcher,
    )

    /** Two mapped holds far enough apart that nothing can snap to the wrong one. */
    private val twoHolds = listOf(
        HoldDefinition("h01", NormalizedPoint(0.25f, 0.20f)),
        HoldDefinition("h02", NormalizedPoint(0.75f, 0.50f)),
    )

    private fun mappedStore(vararg problems: Problem) = FakeStore(
        LibrarySnapshot(
            setup = BoardSetup.default().withHolds(twoHolds),
            problems = problems.toList(),
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a dragged hold is saved and the board redraws from the new map`() = runTest {
        val store = store(published())
        val vm = viewModel(store)

        vm.moveHold("h20", 0.31f, 0.42f)

        val moved = vm.state.value.board.holdsById.getValue("h20").point
        assertEquals(0.31f, moved.x, 0.0001f)
        assertEquals(0.42f, moved.y, 0.0001f)
        assertEquals(moved, store.saved?.setup?.holds?.first { it.id == "h20" }?.point)
    }

    @Test
    fun `dragging a start hold onto the kickboard demotes it and says so`() = runTest {
        val store = store(published())
        val vm = viewModel(store)
        val events = mutableListOf<BoardEvent>()
        val job = launchCollect(vm, events)

        vm.moveHold("h30", 0.5f, 0.95f)

        assertEquals(
            PublicationState.NEEDS_REVIEW,
            vm.state.value.problems.single().publicationState,
        )
        assertEquals(
            "1 problem moved to Needs review.",
            events.filterIsInstance<BoardEvent.Message>().single().text,
        )
        job.cancel()
    }

    @Test
    fun `a drag that breaks nothing passes without a snackbar`() = runTest {
        val store = store(published())
        val vm = viewModel(store)
        val events = mutableListOf<BoardEvent>()
        val job = launchCollect(vm, events)

        vm.moveHold("h20", 0.31f, 0.42f)

        assertEquals(PublicationState.PUBLISHED, vm.state.value.problems.single().publicationState)
        assertTrue(events.isEmpty())
        job.cancel()
    }

    @Test
    fun `adding a hold returns its id and puts it on the board`() = runTest {
        val store = store(published())
        val vm = viewModel(store)

        val id = vm.addHold(0.5f, 0.5f)

        assertEquals("h44", id)
        assertEquals(BoardDefaults.holds.size + 1, vm.state.value.board.holds.size)
        assertEquals(PublicationState.PUBLISHED, vm.state.value.problems.single().publicationState)
        assertTrue(store.saved?.setup?.holds?.any { it.id == "h44" } == true)
    }

    @Test
    fun `removing a hold names it and reports the problems it took down`() = runTest {
        val store = store(published())
        val vm = viewModel(store)
        val events = mutableListOf<BoardEvent>()
        val job = launchCollect(vm, events)

        vm.removeHold("h30")

        assertNull(vm.state.value.board.holdsById["h30"])
        assertEquals(
            PublicationState.NEEDS_REVIEW,
            vm.state.value.problems.single().publicationState,
        )
        assertEquals(
            "h30 removed. 1 problem moved to Needs review.",
            events.filterIsInstance<BoardEvent.Message>().single().text,
        )
        job.cancel()
    }

    @Test
    fun `removing a hold nothing uses just names the hold`() = runTest {
        val store = store(published())
        val vm = viewModel(store)
        val events = mutableListOf<BoardEvent>()
        val job = launchCollect(vm, events)

        vm.removeHold("h20")

        assertEquals(
            "h20 removed.",
            events.filterIsInstance<BoardEvent.Message>().single().text,
        )
        job.cancel()
    }

    @Test
    fun `going back to the bundled map restores the holds that were moved`() = runTest {
        val store = store(published())
        val vm = viewModel(store)
        vm.moveHold("h30", 0.5f, 0.95f)
        assertEquals(
            PublicationState.NEEDS_REVIEW,
            vm.state.value.problems.single().publicationState,
        )

        vm.useBundledHoldLayout()

        assertEquals(BoardDefaults.holds, vm.state.value.setup.holds)
        // Needs review is not undone by putting the hold back: the problem has
        // lost its forerun claim on a board that has since changed under it.
        assertEquals(
            PublicationState.NEEDS_REVIEW,
            vm.state.value.problems.single().publicationState,
        )
    }

    @Test
    fun `there is nothing to report when the map is already the bundled one`() = runTest {
        val store = store(published())
        val vm = viewModel(store)
        val events = mutableListOf<BoardEvent>()
        val job = launchCollect(vm, events)

        vm.useBundledHoldLayout()

        assertTrue(events.isEmpty())
        job.cancel()
    }

    // --- Suggestions from the photo ---------------------------------------------------

    @Test
    fun `a suggestion is previewed, not applied`() = runTest {
        val store = mappedStore()
        // One disc beside h01, one nowhere near either mapped hold.
        val images = FakeImageSource(
            SyntheticBoard.frame(
                listOf(NormalizedPoint(0.27f, 0.20f), NormalizedPoint(0.30f, 0.80f)),
            ),
        )
        val vm = viewModel(store, images)

        vm.suggestHoldsFromPhoto()

        val preview = requireNotNull(vm.state.value.holdSuggestion)
        assertEquals(1, preview.snapped)
        assertEquals(1, preview.added)
        assertEquals("h02 had nothing to snap to", 1, preview.unmatched)
        assertFalse(vm.state.value.isSuggestingHolds)
        // Nothing has moved yet.
        assertEquals(twoHolds, vm.state.value.setup.holds)
    }

    @Test
    fun `accepting a suggestion moves the markers and adds the new hold`() = runTest {
        val store = mappedStore()
        val images = FakeImageSource(
            SyntheticBoard.frame(
                listOf(NormalizedPoint(0.27f, 0.20f), NormalizedPoint(0.30f, 0.80f)),
            ),
        )
        val vm = viewModel(store, images)
        val events = mutableListOf<BoardEvent>()
        val job = launchCollect(vm, events)

        vm.suggestHoldsFromPhoto()
        vm.acceptHoldSuggestion()

        val holds = vm.state.value.setup.holds
        assertEquals(3, holds.size)
        val moved = holds.first { it.id == "h01" }.point
        assertEquals(0.27f, moved.x, 0.01f)
        // The hold the detector never saw stayed exactly where it was.
        assertEquals(NormalizedPoint(0.75f, 0.50f), holds.first { it.id == "h02" }.point)
        assertEquals(3, store.saved?.setup?.holds?.size)
        assertTrue(
            events.filterIsInstance<BoardEvent.Message>().single().text.startsWith("1 marker(s) snapped"),
        )
        assertNull(vm.state.value.holdSuggestion)
        job.cancel()
    }

    @Test
    fun `dismissing a suggestion leaves the map untouched`() = runTest {
        val store = mappedStore()
        val images = FakeImageSource(SyntheticBoard.frame(listOf(NormalizedPoint(0.30f, 0.80f))))
        val vm = viewModel(store, images)

        vm.suggestHoldsFromPhoto()
        vm.dismissHoldSuggestion()

        assertNull(vm.state.value.holdSuggestion)
        assertEquals(twoHolds, vm.state.value.setup.holds)
    }

    @Test
    fun `a photo that can't be read is reported and suggests nothing`() = runTest {
        val store = mappedStore()
        val vm = viewModel(store, FakeImageSource(null))
        val events = mutableListOf<BoardEvent>()
        val job = launchCollect(vm, events)

        vm.suggestHoldsFromPhoto()

        assertNull(vm.state.value.holdSuggestion)
        assertFalse(vm.state.value.isSuggestingHolds)
        assertEquals(
            "The board photo couldn't be read.",
            events.filterIsInstance<BoardEvent.Message>().single().text,
        )
        job.cancel()
    }

    @Test
    fun `a photo with nothing hold-like in it says so`() = runTest {
        val store = mappedStore()
        val vm = viewModel(store, FakeImageSource(SyntheticBoard.frame(emptyList())))
        val events = mutableListOf<BoardEvent>()
        val job = launchCollect(vm, events)

        vm.suggestHoldsFromPhoto()

        assertNull(vm.state.value.holdSuggestion)
        assertEquals(
            "Nothing in the photo stood out as a hold.",
            events.filterIsInstance<BoardEvent.Message>().single().text,
        )
        job.cancel()
    }

    @Test
    fun `a map edited while the photo was being read discards the suggestion`() = runTest {
        val store = mappedStore()
        val images = FakeImageSource(SyntheticBoard.frame(listOf(NormalizedPoint(0.27f, 0.20f))))
        val vm = viewModel(store, images)
        // Applying a suggestion built against the old map would undo this edit.
        images.onLoad = { vm.removeHold("h02") }
        val events = mutableListOf<BoardEvent>()
        val job = launchCollect(vm, events)

        vm.suggestHoldsFromPhoto()

        assertNull(vm.state.value.holdSuggestion)
        assertTrue(
            events.filterIsInstance<BoardEvent.Message>().any {
                it.text == "The hold map changed while the photo was being read. Try again."
            },
        )
        job.cancel()
    }

    @Test
    fun `without an image source the action reports instead of doing nothing`() = runTest {
        val store = mappedStore()
        val vm = viewModel(store, images = null)
        val events = mutableListOf<BoardEvent>()
        val job = launchCollect(vm, events)

        vm.suggestHoldsFromPhoto()

        assertEquals(
            "Hold suggestions aren't available on this device.",
            events.filterIsInstance<BoardEvent.Message>().single().text,
        )
        job.cancel()
    }

    /**
     * [BoardViewModel.events] is a hot SharedFlow with no replay, so collection has
     * to be running before the action under test emits.
     */
    private fun TestScope.launchCollect(
        vm: BoardViewModel,
        into: MutableList<BoardEvent>,
    ): Job = launch(UnconfinedTestDispatcher(testScheduler)) {
        vm.events.collect { into += it }
    }
}
