package za.co.boardaf

import org.junit.Assert.assertEquals
import org.junit.Test
import za.co.boardaf.model.BoardGeometry
import za.co.boardaf.model.NormalizedPoint

class BoardGeometryTest {
    @Test
    fun `hold centers stay normalized at phone tablet and desktop widths`() {
        val point = NormalizedPoint(x = 0.724f, y = 0.280f)

        listOf(360, 600, 1200).forEach { width ->
            val imageSize = BoardGeometry.imageSizeForWidth(width)
            val center = BoardGeometry.centerInPixels(point, imageSize)

            assertEquals(point.x, center.x / imageSize.width, 0.0001f)
            assertEquals(point.y, center.y / imageSize.height, 0.0001f)
            assertEquals(width * 4f / 3f, imageSize.height, 0.0001f)
        }
    }

    @Test
    fun `point is clamped inside image coordinate space`() {
        val imageSize = BoardGeometry.imageSizeForWidth(390)
        val center = BoardGeometry.centerInPixels(
            NormalizedPoint(x = 1.2f, y = -0.3f),
            imageSize,
        )

        assertEquals(imageSize.width, center.x, 0.0001f)
        assertEquals(0f, center.y, 0.0001f)
    }
}
