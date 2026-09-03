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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import za.co.boardaf.data.BoardLoadResult
import za.co.boardaf.data.BoardPhotoStore
import za.co.boardaf.data.BoardStore
import za.co.boardaf.data.LibrarySnapshot
import za.co.boardaf.model.BoardGeometry
import za.co.boardaf.model.BoardPhoto
import za.co.boardaf.model.BoardSetup

/**
 * The two-step capture handshake in [BoardViewModel]: the camera app owns the
 * file between [BoardViewModel.beginBoardPhotoCapture] and
 * [BoardViewModel.completeBoardPhotoCapture], so every way that can end badly has
 * to leave the board on a photo it can actually draw, and no orphan files behind.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BoardPhotoCaptureTest {

    private class FakeStore(private val initial: LibrarySnapshot) : BoardStore {
        var saved: LibrarySnapshot? = null

        override suspend fun load() = BoardLoadResult(initial)

        override suspend fun save(snapshot: LibrarySnapshot) {
            saved = snapshot
        }
    }

    private class FakePhotoStore : BoardPhotoStore {
        val created = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val missing = mutableSetOf<String>()
        var shareUriWorks = true
        var adoptWorks = true
        private var sequence = 0

        override fun createPending(): String? {
            sequence++
            val name = "pending-$sequence.jpg"
            created += name
            return name
        }

        override fun shareUri(fileName: String): String? =
            if (shareUriWorks) "content://fake/$fileName" else null

        override fun adopt(fileName: String): BoardPhoto? = if (adoptWorks) {
            BoardPhoto(fileName, widthPx = 1536, heightPx = 2048, capturedAt = 99L)
        } else {
            deleted += fileName
            null
        }

        override fun delete(fileName: String) {
            deleted += fileName
        }

        override fun pathFor(photo: BoardPhoto): String? =
            if (photo.fileName in missing) null else "/fake/${photo.fileName}"
    }

    private fun viewModel(store: FakeStore, photos: FakePhotoStore) = BoardViewModel(
        application = Application(),
        store = store,
        cloudSync = null,
        photoStore = photos,
    )

    private fun snapshot(setup: BoardSetup = BoardSetup.default()) =
        LibrarySnapshot(setup = setup, problems = emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a successful capture becomes the board photo and resolves to a path`() = runTest {
        val store = FakeStore(snapshot())
        val photos = FakePhotoStore()
        val vm = viewModel(store, photos)

        val target = vm.beginBoardPhotoCapture()
        assertEquals("content://fake/pending-1.jpg", target)

        vm.completeBoardPhotoCapture(saved = true)

        assertEquals("pending-1.jpg", vm.state.value.setup.photo?.fileName)
        assertEquals("/fake/pending-1.jpg", vm.state.value.boardPhotoPath)
        assertEquals("pending-1.jpg", store.saved?.setup?.photo?.fileName)
    }

    @Test
    fun `a cancelled capture deletes the empty file and leaves the bundled photo`() = runTest {
        val store = FakeStore(snapshot())
        val photos = FakePhotoStore()
        val vm = viewModel(store, photos)

        vm.beginBoardPhotoCapture()
        vm.completeBoardPhotoCapture(saved = false)

        assertNull(vm.state.value.setup.photo)
        assertEquals(listOf("pending-1.jpg"), photos.deleted)
    }

    @Test
    fun `a capture that can't be read reports it and changes nothing`() = runTest {
        val store = FakeStore(snapshot())
        val photos = FakePhotoStore().apply { adoptWorks = false }
        val vm = viewModel(store, photos)
        val events = mutableListOf<BoardEvent>()
        val job = launchCollect(vm, events)

        vm.beginBoardPhotoCapture()
        vm.completeBoardPhotoCapture(saved = true)

        assertNull(vm.state.value.setup.photo)
        assertEquals(
            "That photo couldn't be read. The board is unchanged.",
            events.filterIsInstance<BoardEvent.Message>().single().text,
        )
        job.cancel()
    }

    @Test
    fun `retaking only discards the old file once the new one is adopted`() = runTest {
        val store = FakeStore(snapshot())
        val photos = FakePhotoStore()
        val vm = viewModel(store, photos)

        vm.beginBoardPhotoCapture()
        vm.completeBoardPhotoCapture(saved = true)
        assertTrue(photos.deleted.isEmpty())

        vm.beginBoardPhotoCapture()
        vm.completeBoardPhotoCapture(saved = true)

        assertEquals("pending-2.jpg", vm.state.value.setup.photo?.fileName)
        assertEquals(listOf("pending-1.jpg"), photos.deleted)
    }

    @Test
    fun `a failed retake keeps the photo that is already on the board`() = runTest {
        val store = FakeStore(snapshot())
        val photos = FakePhotoStore()
        val vm = viewModel(store, photos)

        vm.beginBoardPhotoCapture()
        vm.completeBoardPhotoCapture(saved = true)

        photos.adoptWorks = false
        vm.beginBoardPhotoCapture()
        vm.completeBoardPhotoCapture(saved = true)

        assertEquals("pending-1.jpg", vm.state.value.setup.photo?.fileName)
        assertTrue("pending-1.jpg" !in photos.deleted)
    }

    @Test
    fun `starting a capture releases a destination the camera never reported back`() = runTest {
        val store = FakeStore(snapshot())
        val photos = FakePhotoStore()
        val vm = viewModel(store, photos)

        vm.beginBoardPhotoCapture()
        // The camera app was killed: no result ever arrives, and the user retries.
        vm.beginBoardPhotoCapture()

        assertEquals(listOf("pending-1.jpg"), photos.deleted)
    }

    @Test
    fun `a destination that can't be shared is cleaned up instead of leaking`() = runTest {
        val store = FakeStore(snapshot())
        val photos = FakePhotoStore().apply { shareUriWorks = false }
        val vm = viewModel(store, photos)

        assertNull(vm.beginBoardPhotoCapture())
        assertEquals(listOf("pending-1.jpg"), photos.deleted)
    }

    @Test
    fun `clearing goes back to the bundled photo and drops the file`() = runTest {
        val store = FakeStore(snapshot())
        val photos = FakePhotoStore()
        val vm = viewModel(store, photos)

        vm.beginBoardPhotoCapture()
        vm.completeBoardPhotoCapture(saved = true)
        vm.clearBoardPhoto()

        assertNull(vm.state.value.setup.photo)
        assertNull(vm.state.value.boardPhotoPath)
        assertEquals(listOf("pending-1.jpg"), photos.deleted)
        assertNull(store.saved?.setup?.photo)
    }

    @Test
    fun `a photo whose file has vanished falls back to the bundled frame`() = runTest {
        val recorded = BoardPhoto("gone.jpg", widthPx = 1000, heightPx = 1000, capturedAt = 1L)
        val store = FakeStore(snapshot(BoardSetup.default().copy(photo = recorded)))
        val photos = FakePhotoStore().apply { missing += "gone.jpg" }
        val vm = viewModel(store, photos)

        // The record survives — nothing silently rewrites stored setup — but the
        // board falls back rather than sizing itself for an image it can't draw.
        assertNotNull(vm.state.value.setup.photo)
        assertNull(vm.state.value.boardPhotoPath)
        assertNull(vm.state.value.board.photo)
        assertEquals(BoardGeometry.IMAGE_ASPECT_RATIO, vm.state.value.board.aspectRatio, 0.0001f)
    }

    private fun TestScope.launchCollect(
        vm: BoardViewModel,
        into: MutableList<BoardEvent>,
    ): Job = launch(UnconfinedTestDispatcher(testScheduler)) {
        vm.events.collect { into += it }
    }
}
