package za.co.boardaf

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import za.co.boardaf.data.LibrarySnapshot
import za.co.boardaf.data.SnapshotCodec
import za.co.boardaf.data.sync.RemoteBoardRecord
import za.co.boardaf.data.sync.RemoteLibrary
import za.co.boardaf.data.sync.SyncBaselines
import za.co.boardaf.data.sync.SyncCodec
import za.co.boardaf.data.sync.SyncPlanner
import za.co.boardaf.model.BoardGeometry
import za.co.boardaf.model.BoardPhoto
import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.ConfiguredBoard
import za.co.boardaf.model.GradeSystem
import za.co.boardaf.setter.SetterMode

/**
 * The setter's own board photo: how it persists, how it shapes the board frame,
 * and — the part that matters for data safety — that it stays device-local and
 * survives a cloud merge that replaces the rest of the board setup.
 */
class BoardPhotoTest {

    private val photo = BoardPhoto(
        fileName = "board-1756900000000.jpg",
        widthPx = 1536,
        heightPx = 2048,
        capturedAt = 1_756_900_000_000L,
    )

    private fun snapshot(setup: BoardSetup) = LibrarySnapshot(setup = setup, problems = emptyList())

    private fun decodedSetup(snapshot: LibrarySnapshot): BoardSetup {
        val decoded = SnapshotCodec.decode(SnapshotCodec.encode(snapshot))
        return (decoded as SnapshotCodec.DecodeResult.Success).snapshot.setup
    }

    // --- Persistence ---------------------------------------------------------------

    @Test
    fun `a captured photo round-trips through the snapshot codec`() {
        val setup = BoardSetup.default().copy(photo = photo)

        assertEquals(photo, decodedSetup(snapshot(setup)).photo)
    }

    @Test
    fun `a snapshot written before capture existed decodes to the bundled photo`() {
        val setup = BoardSetup.default()

        assertNull(decodedSetup(snapshot(setup)).photo)
    }

    @Test
    fun `a malformed photo record costs the photo, not the hold classifications`() {
        val setup = BoardSetup.default().copy(photo = photo)
        val root = Json.parseToJsonElement(SnapshotCodec.encode(snapshot(setup))).jsonObject
        val board = root.getValue("board").jsonObject
        val corrupted = buildJsonObject {
            board.forEach { (key, value) ->
                if (key == "photo") put("photo", buildJsonObject { put("fileName", "x") }) else put(key, value)
            }
        }
        val patched = buildJsonObject {
            root.forEach { (key, value) -> if (key == "board") put("board", corrupted) else put(key, value) }
        }

        val decoded = SnapshotCodec.decode(patched.toString())
        val result = decoded as SnapshotCodec.DecodeResult.Success

        assertNull(result.snapshot.setup.photo)
        assertEquals(setup.classifications, result.snapshot.setup.classifications)
    }

    // --- Board frame ---------------------------------------------------------------

    @Test
    fun `the board frame takes the photo's aspect ratio`() {
        val board = ConfiguredBoard.from(BoardSetup.default().copy(photo = photo))

        assertEquals(1536f / 2048f, board.aspectRatio, 0.0001f)
    }

    @Test
    fun `without a photo the frame keeps the bundled image's ratio`() {
        val board = ConfiguredBoard.from(BoardSetup.default())

        assertEquals(BoardGeometry.IMAGE_ASPECT_RATIO, board.aspectRatio, 0.0001f)
    }

    @Test
    fun `a photo recorded with no pixel size falls back to the bundled ratio`() {
        val board = ConfiguredBoard.from(
            BoardSetup.default().copy(photo = photo.copy(widthPx = 0, heightPx = 0)),
        )

        assertEquals(BoardGeometry.IMAGE_ASPECT_RATIO, board.aspectRatio, 0.0001f)
    }

    // --- Cloud sync ------------------------------------------------------------------

    @Test
    fun `the synced board document carries no photo, so taking one is not a remote edit`() {
        val withPhoto = BoardSetup.default().copy(photo = photo)
        val without = BoardSetup.default()

        assertEquals(
            SyncCodec.encodeBoard(without, GradeSystem.FRENCH, SetterMode.GUIDED),
            SyncCodec.encodeBoard(withPhoto, GradeSystem.FRENCH, SetterMode.GUIDED),
        )
    }

    @Test
    fun `adopting a remote board setup keeps this device's photo`() {
        val local = snapshot(BoardSetup.default().copy(photo = photo, kickboardEnabled = true))
        // A different board setup on the server, with the photo absent as always.
        val remoteSetup = BoardSetup.default().copy(kickboardEnabled = false)
        val remote = RemoteLibrary(
            board = RemoteBoardRecord(
                setup = remoteSetup,
                gradeSystem = GradeSystem.V_SCALE,
                setterMode = SetterMode.QUICK,
                encoded = SyncCodec.encodeBoard(remoteSetup, GradeSystem.V_SCALE, SetterMode.QUICK),
                revision = 3,
                pendingWrite = false,
            ),
        )

        val plan = SyncPlanner.plan(local, remote, SyncBaselines(), now = 1_000L)
        val merged = plan.mergedSnapshot

        assertNotNull(merged)
        // The remote setup won, as it should…
        assertEquals(false, merged?.setup?.kickboardEnabled)
        assertEquals(GradeSystem.V_SCALE, merged?.gradeSystem)
        // …but not by taking a photo with it that this device cannot show.
        assertEquals(photo, merged?.setup?.photo)
    }

    private fun JsonObject.getValue(key: String) = this[key] ?: error("Missing $key")
}
