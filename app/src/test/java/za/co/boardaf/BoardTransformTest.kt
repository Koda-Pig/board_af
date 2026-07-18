package za.co.boardaf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.boardaf.model.BoardDefaults
import za.co.boardaf.model.BoardGeometry
import za.co.boardaf.model.BoardTransform
import za.co.boardaf.model.BoardTransforms
import za.co.boardaf.model.NormalizedPoint
import za.co.boardaf.model.PixelPoint
import za.co.boardaf.model.PixelSize

class BoardTransformTest {

    private val container = PixelSize(width = 400f, height = 600f)
    private val center = PixelPoint(200f, 300f)

    private fun zoomAtCenter(zoom: Float, from: BoardTransform = BoardTransform.IDENTITY) =
        BoardTransforms.applyGesture(from, container, center, panX = 0f, panY = 0f, zoom = zoom)

    @Test
    fun `scale is clamped between one and four`() {
        assertEquals(BoardTransform.MAX_SCALE, zoomAtCenter(99f).scale, 0.0001f)

        val zoomedOut = zoomAtCenter(0.1f, from = BoardTransform(scale = 2f, offsetX = -200f, offsetY = -300f))
        assertEquals(BoardTransform.MIN_SCALE, zoomedOut.scale, 0.0001f)
        assertEquals(0f, zoomedOut.offsetX, 0.0001f)
        assertEquals(0f, zoomedOut.offsetY, 0.0001f)
    }

    @Test
    fun `zooming keeps the focal point stationary`() {
        val transform = zoomAtCenter(2f)
        val focalBefore = BoardGeometry.centerInPixels(NormalizedPoint(0.5f, 0.5f), container)
        val focalAfter = BoardTransforms.anchor(NormalizedPoint(0.5f, 0.5f), container, transform)

        assertEquals(focalBefore.x, focalAfter.x, 0.0001f)
        assertEquals(focalBefore.y, focalAfter.y, 0.0001f)
    }

    @Test
    fun `panning is clamped at every edge`() {
        val zoomed = zoomAtCenter(2f)

        val pannedFarRight = BoardTransforms.applyGesture(
            zoomed, container, center, panX = 5000f, panY = 5000f, zoom = 1f,
        )
        assertEquals(0f, pannedFarRight.offsetX, 0.0001f)
        assertEquals(0f, pannedFarRight.offsetY, 0.0001f)

        val pannedFarLeft = BoardTransforms.applyGesture(
            zoomed, container, center, panX = -5000f, panY = -5000f, zoom = 1f,
        )
        assertEquals(container.width - container.width * 2f, pannedFarLeft.offsetX, 0.0001f)
        assertEquals(container.height - container.height * 2f, pannedFarLeft.offsetY, 0.0001f)
    }

    @Test
    fun `the board can never be lost off screen`() {
        var transform = BoardTransform.IDENTITY
        val gestures = listOf(
            Triple(3.5f, -900f, 200f),
            Triple(0.4f, 5000f, -5000f),
            Triple(2.2f, -50f, -3000f),
        )
        gestures.forEach { (zoom, panX, panY) ->
            transform = BoardTransforms.applyGesture(transform, container, center, panX, panY, zoom)
            val minX = container.width - container.width * transform.scale
            val minY = container.height - container.height * transform.scale
            assertTrue(transform.offsetX in minX..0f)
            assertTrue(transform.offsetY in minY..0f)
        }
    }

    @Test
    fun `reset returns to identity`() {
        assertEquals(BoardTransform.IDENTITY, BoardTransforms.reset())
        assertTrue(BoardTransforms.reset().isIdentity)
    }

    @Test
    fun `identity anchors equal the plain geometry mapping at any width`() {
        listOf(360, 600, 1200).forEach { width ->
            val size = BoardGeometry.imageSizeForWidth(width)
            BoardDefaults.holds.forEach { hold ->
                val plain = BoardGeometry.centerInPixels(hold.point, size)
                val anchored = BoardTransforms.anchor(hold.point, size, BoardTransform.IDENTITY)
                assertEquals(plain.x, anchored.x, 0.0001f)
                assertEquals(plain.y, anchored.y, 0.0001f)
            }
        }
    }

    @Test
    fun `normalized coordinates never change during zoom and pan`() {
        val hold = BoardDefaults.holds.first()
        val before = hold.point.copy()

        var transform = BoardTransform.IDENTITY
        repeat(10) { step ->
            transform = BoardTransforms.applyGesture(
                transform, container, center, panX = step * 7f, panY = -step * 3f, zoom = 1.15f,
            )
            BoardTransforms.anchor(hold.point, container, transform)
        }

        assertEquals(before, hold.point)
    }

    @Test
    fun `anchors track the image transform exactly`() {
        val transform = BoardTransform(scale = 2f, offsetX = -120f, offsetY = -260f)
        val point = NormalizedPoint(0.25f, 0.75f)

        val anchor = BoardTransforms.anchor(point, container, transform)

        assertEquals(0.25f * 400f * 2f - 120f, anchor.x, 0.0001f)
        assertEquals(0.75f * 600f * 2f - 260f, anchor.y, 0.0001f)
    }
}
