package za.co.boardaf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.boardaf.model.BoardDefaults
import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.BoardZoneType
import za.co.boardaf.model.ConfiguredBoard
import za.co.boardaf.model.HoldCapability

class BoardClassificationTest {

    private val kickerIds = setOf("h37", "h38", "h39", "h40", "h41", "h42", "h43")

    @Test
    fun `default setup classifies h01-h36 as main and h37-h43 as kickboard foot-only`() {
        val board = ConfiguredBoard.from(BoardSetup.default())

        board.holds.forEach { hold ->
            if (hold.id in kickerIds) {
                assertEquals("${hold.id} zone", BoardZoneType.KICKBOARD, hold.zone)
                assertEquals("${hold.id} capability", HoldCapability.FOOT_ONLY, hold.capability)
            } else {
                assertEquals("${hold.id} zone", BoardZoneType.MAIN, hold.zone)
                assertEquals("${hold.id} capability", HoldCapability.HAND_AND_FOOT, hold.capability)
            }
        }
    }

    @Test
    fun `exact boundary transition sits between h36 and h37`() {
        val setup = BoardSetup.default()
        val h36 = BoardDefaults.holds.first { it.id == "h36" }
        val h37 = BoardDefaults.holds.first { it.id == "h37" }

        assertEquals(BoardZoneType.MAIN, setup.zoneFor(h36.point.y))
        assertEquals(BoardZoneType.KICKBOARD, setup.zoneFor(h37.point.y))
        // A hold exactly on the boundary belongs to the kickboard.
        assertEquals(BoardZoneType.KICKBOARD, setup.zoneFor(setup.kickboardTopY))
    }

    @Test
    fun `moving the boundary reclassifies holds`() {
        val h36y = BoardDefaults.holds.first { it.id == "h36" }.point.y
        val setup = BoardSetup.default().withBoundary(h36y - 0.01f)
        val board = ConfiguredBoard.from(setup)

        val h36 = board.holdsById.getValue("h36")
        assertEquals(BoardZoneType.KICKBOARD, h36.zone)
        assertEquals(HoldCapability.FOOT_ONLY, h36.capability)
    }

    @Test
    fun `capability toggle stores an overridden classification`() {
        val setup = BoardSetup.default().withCapabilityToggled("h43")
        val classification = setup.classifications.getValue("h43")

        assertEquals(HoldCapability.HAND_AND_FOOT, classification.capability)
        assertTrue(classification.overridden)
        assertEquals(BoardZoneType.KICKBOARD, classification.zone)
    }

    @Test
    fun `toggling capability back to the zone default clears the override`() {
        val setup = BoardSetup.default()
            .withCapabilityToggled("h43")
            .withCapabilityToggled("h43")
        val classification = setup.classifications.getValue("h43")

        assertEquals(HoldCapability.FOOT_ONLY, classification.capability)
        assertFalse(classification.overridden)
    }

    @Test
    fun `boundary moves preserve manual capability overrides`() {
        val setup = BoardSetup.default()
            .withCapabilityToggled("h43")
            .withBoundary(0.9f)
        val classification = setup.classifications.getValue("h43")

        assertEquals(HoldCapability.HAND_AND_FOOT, classification.capability)
        assertTrue(classification.overridden)
    }

    @Test
    fun `board without kickboard classifies every hold as main hand-and-foot`() {
        val board = ConfiguredBoard.from(BoardSetup.default().withKickboardEnabled(false))

        assertFalse(board.hasKickboard)
        board.holds.forEach { hold ->
            assertEquals(BoardZoneType.MAIN, hold.zone)
            assertEquals(HoldCapability.HAND_AND_FOOT, hold.capability)
        }
    }

    @Test
    fun `validation reads the stored classification not the boundary`() {
        // Hand-craft a setup whose stored classification disagrees with the line.
        val base = BoardSetup.default()
        val tampered = base.copy(
            classifications = base.classifications +
                ("h20" to base.classifications.getValue("h20").copy(capability = HoldCapability.FOOT_ONLY)),
        )
        val board = ConfiguredBoard.from(tampered)

        assertEquals(HoldCapability.FOOT_ONLY, board.holdsById.getValue("h20").capability)
    }
}
