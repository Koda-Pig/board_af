package za.co.boardaf

import org.junit.Assert.assertEquals
import org.junit.Test
import za.co.boardaf.model.BoardDefaults
import za.co.boardaf.model.BoardGeometry
import za.co.boardaf.model.NormalizedPoint
import za.co.boardaf.model.PixelSize

class BoardGeometryTest {
    @Test
    fun `hold centers stay normalized at phone tablet and desktop widths`() {
        val point = NormalizedPoint(x = 0.724f, y = 0.280f)

        listOf(360, 600, 1200).forEach { width ->
            val imageSize = BoardGeometry.imageSizeForWidth(width)
            val center = BoardGeometry.centerInPixels(point, imageSize)

            assertEquals(point.x, center.x / imageSize.width, 0.0001f)
            assertEquals(point.y, center.y / imageSize.height, 0.0001f)
            assertEquals(width * 1586f / 1080f, imageSize.height, 0.001f)
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

    @Test
    fun `mapped hold centers match source photo pixels`() {
        val expectedCenters = listOf(
            Triple("h01", 107f, 42f), Triple("h02", 444f, 38f), Triple("h03", 703f, 36f), Triple("h04", 974f, 36f),
            Triple("h05", 54f, 254f), Triple("h06", 311f, 215f), Triple("h07", 770f, 251f), Triple("h08", 906f, 250f),
            Triple("h09", 1031f, 313f), Triple("h10", 310f, 307f), Triple("h11", 442f, 309f), Triple("h12", 576f, 301f),
            Triple("h13", 834f, 413f), Triple("h14", 242f, 466f), Triple("h15", 697f, 465f), Triple("h16", 445f, 519f),
            Triple("h17", 833f, 517f), Triple("h18", 112f, 575f), Triple("h19", 378f, 625f), Triple("h20", 573f, 578f),
            Triple("h21", 762f, 621f), Triple("h22", 1031f, 616f), Triple("h23", 512f, 673f), Triple("h24", 908f, 724f),
            Triple("h25", 314f, 729f), Triple("h26", 58f, 843f), Triple("h27", 573f, 833f), Triple("h28", 1026f, 880f),
            Triple("h29", 312f, 931f), Triple("h30", 513f, 985f), Triple("h31", 767f, 986f), Triple("h32", 111f, 1093f),
            Triple("h33", 513f, 1140f), Triple("h34", 900f, 984f), Triple("h35", 967f, 1192f), Triple("h36", 309f, 1248f),
            Triple("h37", 247f, 1411f), Triple("h38", 493f, 1412f), Triple("h39", 844f, 1413f), Triple("h40", 115f, 1539f),
            Triple("h41", 304f, 1540f), Triple("h42", 598f, 1544f), Triple("h43", 974f, 1543f),
        )
        val actualById = BoardDefaults.holds.associateBy { it.id }
        val sourceImageSize = PixelSize(width = 1080f, height = 1586f)

        expectedCenters.forEach { (id, expectedX, expectedY) ->
            val center = BoardGeometry.centerInPixels(
                point = requireNotNull(actualById[id]).point,
                imageSize = sourceImageSize,
            )

            assertEquals("$id x", expectedX, center.x, 0.001f)
            assertEquals("$id y", expectedY, center.y, 0.001f)
        }
    }
}
