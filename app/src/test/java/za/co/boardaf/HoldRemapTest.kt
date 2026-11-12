package za.co.boardaf

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.boardaf.data.SnapshotCodec
import za.co.boardaf.data.sync.SyncCodec
import za.co.boardaf.model.BoardDefaults
import za.co.boardaf.model.BoardPhoto
import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.BoardZoneType
import za.co.boardaf.model.ConfiguredBoard
import za.co.boardaf.model.GradeSystem
import za.co.boardaf.model.HoldCapability
import za.co.boardaf.model.IssueCode
import za.co.boardaf.model.NormalizedPoint
import za.co.boardaf.model.Problem
import za.co.boardaf.model.ProblemAssignment
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.model.ProblemValidator
import za.co.boardaf.model.PublicationState
import za.co.boardaf.setter.SetterMode

/**
 * Re-mapping the hold layout against a fresh board photo. The load-bearing rules
 * are that a hold id is never handed out twice (problems reference holds by id,
 * so a recycled id re-points old problems instead of flagging the hold as gone)
 * and that "tracks the bundled map" stays distinct from "a copy of it".
 */
class HoldRemapTest {

    private fun pointOf(setup: BoardSetup, id: String) =
        setup.holds.first { it.id == id }.point

    @Test
    fun `moving a hold rewrites its center and leaves the rest of the map alone`() {
        val moved = BoardSetup.default().withHoldMoved("h20", NormalizedPoint(0.31f, 0.42f))

        assertEquals(NormalizedPoint(0.31f, 0.42f), pointOf(moved, "h20"))
        assertEquals(BoardDefaults.holds.size, moved.holds.size)
        assertEquals(pointOf(BoardSetup.default(), "h21"), pointOf(moved, "h21"))
        assertTrue(moved.hasCustomLayout)
    }

    @Test
    fun `a drag outside the frame is clamped to it`() {
        val moved = BoardSetup.default().withHoldMoved("h20", NormalizedPoint(-0.4f, 2.5f))

        assertEquals(NormalizedPoint(0f, 1f), pointOf(moved, "h20"))
    }

    @Test
    fun `moving an unknown hold changes nothing`() {
        val setup = BoardSetup.default()

        assertEquals(setup, setup.withHoldMoved("nope", NormalizedPoint(0.5f, 0.5f)))
    }

    @Test
    fun `a hold dragged below the boundary becomes a kickboard foot hold`() {
        // h06 sits high on the main board; 0.95 is well under the default boundary.
        val moved = BoardSetup.default().withHoldMoved("h06", NormalizedPoint(0.3f, 0.95f))

        val classification = moved.classifications.getValue("h06")
        assertEquals(BoardZoneType.KICKBOARD, classification.zone)
        assertEquals(HoldCapability.FOOT_ONLY, classification.capability)
    }

    @Test
    fun `a manual capability correction survives the hold being dragged`() {
        // h43 is a kickboard hold corrected to hands-and-feet.
        val corrected = BoardSetup.default().withCapabilityToggled("h43")
        assertEquals(HoldCapability.HAND_AND_FOOT, corrected.classifications.getValue("h43").capability)

        val moved = corrected.withHoldMoved("h43", NormalizedPoint(0.5f, 0.2f))

        val classification = moved.classifications.getValue("h43")
        assertEquals(BoardZoneType.MAIN, classification.zone)
        assertEquals(HoldCapability.HAND_AND_FOOT, classification.capability)
    }

    @Test
    fun `an added hold lands last with the next free id and a classification`() {
        val added = BoardSetup.default().withHoldAdded(NormalizedPoint(0.5f, 0.5f))

        assertEquals(BoardDefaults.holds.size + 1, added.holds.size)
        assertEquals("h44", added.holds.last().id)
        assertEquals(NormalizedPoint(0.5f, 0.5f), added.holds.last().point)
        assertEquals(BoardZoneType.MAIN, added.classifications.getValue("h44").zone)
    }

    @Test
    fun `a removed hold's id is never handed out again`() {
        // h43 is the highest-numbered bundled hold. Deriving the next id from the
        // remaining holds would hand "h43" straight back to the new hold, and every
        // problem that still listed h43 would silently point at it.
        val reworked = BoardSetup.default()
            .withHoldRemoved("h43")
            .withHoldAdded(NormalizedPoint(0.2f, 0.2f))

        assertEquals("h44", reworked.holds.last().id)
        assertTrue(reworked.holds.none { it.id == "h43" })
    }

    @Test
    fun `removing a hold drops its classification and breaks the problems that used it`() {
        val setup = BoardSetup.default().withHoldRemoved("h30")
        val board = ConfiguredBoard.from(setup)

        assertNull(setup.classifications["h30"])
        assertNull(board.holdsById["h30"])

        val issues = ProblemValidator.validate(publishedProblem(), board)
        assertTrue(issues.any { it.code == IssueCode.UNKNOWN_HOLD && it.holdId == "h30" })
        assertEquals(
            PublicationState.NEEDS_REVIEW,
            ProblemValidator.resolveState(PublicationState.PUBLISHED, issues),
        )
    }

    @Test
    fun `removing a hold that isn't on the board changes nothing`() {
        val setup = BoardSetup.default()

        assertEquals(setup, setup.withHoldRemoved("nope"))
    }

    @Test
    fun `the bundled map is stored as absence rather than as a copy of itself`() {
        val readopted = BoardSetup.default()
            .withHoldMoved("h20", NormalizedPoint(0.1f, 0.1f))
            .withHolds(BoardDefaults.holds)

        assertNull(readopted.layout)
        assertFalse(readopted.hasCustomLayout)
        assertEquals(BoardDefaults.holds, readopted.holds)
    }

    @Test
    fun `going back to the bundled map restores positions but keeps the id counter`() {
        val custom = BoardSetup.default()
            .withHoldMoved("h20", NormalizedPoint(0.1f, 0.1f))
            .withHoldAdded(NormalizedPoint(0.9f, 0.9f))

        val bundled = custom.withBundledHolds()

        assertEquals(BoardDefaults.holds, bundled.holds)
        assertFalse(bundled.hasCustomLayout)
        // h44 is gone from the board, and must not come back as a new hold's id.
        assertEquals("h45", bundled.withHoldAdded(NormalizedPoint(0.5f, 0.5f)).holds.last().id)
    }

    // --- Storage -------------------------------------------------------------------

    @Test
    fun `a custom layout survives an encode and decode round trip`() {
        val setup = BoardSetup.default()
            .withHoldMoved("h20", NormalizedPoint(0.123f, 0.456f))
            .withHoldAdded(NormalizedPoint(0.77f, 0.88f))

        val decoded = SnapshotCodec.decodeSetup(SnapshotCodec.encodeSetup(setup))

        assertEquals(setup.holds, decoded.holds)
        assertEquals(setup.nextHoldNumber, decoded.nextHoldNumber)
        assertEquals(setup.classifications, decoded.classifications)
    }

    @Test
    fun `a setup written before re-mapping existed decodes onto the bundled map`() {
        val legacy = Json.parseToJsonElement(
            """
            {
              "kickboardEnabled": true,
              "kickboardTopY": 0.8379,
              "holds": [{"id":"h01","zone":"MAIN","capability":"HAND_AND_FOOT","overridden":false}]
            }
            """.trimIndent(),
        ).jsonObject

        val decoded = SnapshotCodec.decodeSetup(legacy)

        assertNull(decoded.layout)
        assertNull(decoded.nextHoldNumber)
        assertEquals(BoardDefaults.holds, decoded.holds)
    }

    @Test
    fun `a hold record that won't parse is skipped rather than failing the setup`() {
        val setup = BoardSetup.default().withHoldMoved("h20", NormalizedPoint(0.1f, 0.2f))
        val encoded = SnapshotCodec.encodeSetup(setup).toString()
        // Still valid JSON, but h20's layout record has lost its coordinates.
        val damaged = encoded.replace(Regex("""\{"id":"h20","x":[^}]*}"""), """{"id":"h20"}""")
        assertTrue("the h20 record was not damaged", damaged != encoded)

        val decoded = SnapshotCodec.decodeSetup(Json.parseToJsonElement(damaged).jsonObject)

        assertTrue(decoded.holds.none { it.id == "h20" })
        assertEquals(BoardDefaults.holds.size - 1, decoded.holds.size)
        assertNotNull(decoded.holds.firstOrNull { it.id == "h21" })
    }

    @Test
    fun `the cloud board document carries the hold map but not the photo`() {
        val setup = BoardSetup.default()
            .withHoldMoved("h20", NormalizedPoint(0.31f, 0.42f))
            .copy(photo = BoardPhoto("local.jpg", 1536, 2048, 7L))

        val payload = SyncCodec.encodeBoard(setup, GradeSystem.FRENCH, SetterMode.GUIDED)
        val decoded = SyncCodec.decodeBoard(payload)

        assertNull("the photo file is device-local", decoded.setup.photo)
        assertEquals(setup.holds, decoded.setup.holds)
        assertEquals(NormalizedPoint(0.31f, 0.42f), pointOf(decoded.setup, "h20"))
    }

    private fun publishedProblem() = Problem(
        id = "p1",
        name = "Test",
        grade = za.co.boardaf.model.BoulderGrade.F6A,
        accent = za.co.boardaf.model.Accent.SKY,
        setter = "You",
        publicationState = PublicationState.PUBLISHED,
        forerunConfirmedAt = 1L,
        assignments = listOf(
            ProblemAssignment("h30", ProblemHoldRole.START),
            ProblemAssignment("h06", ProblemHoldRole.FINISH),
        ),
    )
}
