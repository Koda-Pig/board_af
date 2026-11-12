package za.co.boardaf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.HoldDefinition
import za.co.boardaf.model.NormalizedPoint

/**
 * Reconciling detector output with the map that already exists. The rule that
 * matters most is the one about what *doesn't* happen: a hold the detector failed
 * to see is left alone, because losing it would break every problem that used it.
 */
class HoldSuggestionTest {

    private val aspect = 1080f / 1586f

    private fun setupOf(vararg holds: Pair<String, NormalizedPoint>) =
        BoardSetup.default().withHolds(holds.map { HoldDefinition(it.first, it.second) })

    private fun pointOf(setup: BoardSetup, id: String) = setup.holds.first { it.id == id }.point

    @Test
    fun `a marker near a suggestion moves onto it and keeps its id`() {
        val setup = setupOf("h01" to NormalizedPoint(0.20f, 0.20f))

        val result = setup.withSuggestedHolds(listOf(NormalizedPoint(0.23f, 0.21f)), aspect)

        assertEquals(1, result.snapped)
        assertEquals(0, result.added)
        assertEquals(0, result.unmatched)
        assertEquals(NormalizedPoint(0.23f, 0.21f), pointOf(result.setup, "h01"))
        assertEquals(1, result.setup.holds.size)
    }

    @Test
    fun `a suggestion with no marker near it becomes a new hold`() {
        val setup = setupOf("h01" to NormalizedPoint(0.20f, 0.20f))

        val result = setup.withSuggestedHolds(
            listOf(NormalizedPoint(0.20f, 0.20f), NormalizedPoint(0.80f, 0.80f)),
            aspect,
        )

        assertEquals(1, result.snapped)
        assertEquals(1, result.added)
        assertEquals(2, result.setup.holds.size)
        assertNotNull(result.setup.holds.firstOrNull { it.point == NormalizedPoint(0.80f, 0.80f) })
    }

    @Test
    fun `a marker the detector missed is left exactly where it is`() {
        // The whole safety case for accepting a suggestion: a hold the detector
        // can't see is still bolted to the wall, and problems still reference it.
        val setup = setupOf(
            "h01" to NormalizedPoint(0.20f, 0.20f),
            "h02" to NormalizedPoint(0.60f, 0.60f),
        )

        val result = setup.withSuggestedHolds(listOf(NormalizedPoint(0.21f, 0.20f)), aspect)

        assertEquals(1, result.snapped)
        assertEquals(1, result.unmatched)
        assertEquals(0, result.added)
        assertEquals(2, result.setup.holds.size)
        assertEquals(NormalizedPoint(0.60f, 0.60f), pointOf(result.setup, "h02"))
    }

    @Test
    fun `two markers never snap onto the same suggestion`() {
        val setup = setupOf(
            "h01" to NormalizedPoint(0.30f, 0.50f),
            "h02" to NormalizedPoint(0.33f, 0.50f),
        )

        val result = setup.withSuggestedHolds(listOf(NormalizedPoint(0.31f, 0.50f)), aspect)

        assertEquals(1, result.snapped)
        assertEquals(1, result.unmatched)
        assertEquals(2, result.setup.holds.size)
        assertEquals(
            setOf(NormalizedPoint(0.31f, 0.50f), NormalizedPoint(0.33f, 0.50f)),
            result.setup.holds.map { it.point }.toSet(),
        )
    }

    @Test
    fun `the nearest marker wins the suggestion`() {
        val setup = setupOf(
            "h01" to NormalizedPoint(0.30f, 0.50f),
            "h02" to NormalizedPoint(0.34f, 0.50f),
        )

        val result = setup.withSuggestedHolds(listOf(NormalizedPoint(0.335f, 0.50f)), aspect)

        assertEquals(NormalizedPoint(0.335f, 0.50f), pointOf(result.setup, "h02"))
        assertEquals(NormalizedPoint(0.30f, 0.50f), pointOf(result.setup, "h01"))
    }

    @Test
    fun `the snap radius is a circle on the wall, not on the normalized frame`() {
        // 0.05 of the height is a smaller distance than 0.05 of the width on a
        // portrait board; without the aspect correction this would not snap.
        val setup = setupOf("h01" to NormalizedPoint(0.50f, 0.50f))

        val across = setup.withSuggestedHolds(listOf(NormalizedPoint(0.56f, 0.50f)), aspect)
        val down = setup.withSuggestedHolds(listOf(NormalizedPoint(0.50f, 0.56f)), aspect)

        assertEquals("0.06 across the frame is inside the radius", 1, across.snapped)
        // 0.06 of the height is 0.088 in frame-width units, which is outside it.
        assertEquals("0.06 down the frame is outside the radius", 0, down.snapped)
        assertEquals(1, down.added)
    }

    @Test
    fun `new holds take fresh ids rather than any a removed hold left behind`() {
        val setup = setupOf(
            "h42" to NormalizedPoint(0.20f, 0.20f),
            "h43" to NormalizedPoint(0.80f, 0.80f),
        ).withHoldRemoved("h43")

        val result = setup.withSuggestedHolds(listOf(NormalizedPoint(0.80f, 0.80f)), aspect)

        assertEquals("h44", result.setup.holds.last().id)
        assertTrue(result.setup.holds.none { it.id == "h43" })
    }

    @Test
    fun `no suggestions means nothing to accept`() {
        val result = BoardSetup.default().withSuggestedHolds(emptyList(), aspect)

        assertTrue(!result.changesAnything)
        assertEquals(BoardDefaultsSize, result.setup.holds.size)
    }

    private val BoardDefaultsSize get() = za.co.boardaf.model.BoardDefaults.holds.size
}
