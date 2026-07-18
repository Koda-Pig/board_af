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

    @Test
    fun `mapped hold centers match source photo pixels`() {
        val expectedCenters = listOf(
            Triple("h01", 154f, 81f), Triple("h02", 405f, 77f), Triple("h03", 598f, 75f), Triple("h04", 801f, 74f),
            Triple("h05", 114f, 238f), Triple("h06", 306f, 209f), Triple("h07", 649f, 234f), Triple("h08", 751f, 233f),
            Triple("h09", 845f, 280f), Triple("h10", 305f, 277f), Triple("h11", 404f, 278f), Triple("h12", 504f, 272f),
            Triple("h13", 697f, 355f), Triple("h14", 254f, 396f), Triple("h15", 595f, 394f), Triple("h16", 406f, 435f),
            Triple("h17", 697f, 433f), Triple("h18", 157f, 477f), Triple("h19", 356f, 514f), Triple("h20", 502f, 479f),
            Triple("h21", 644f, 511f), Triple("h22", 846f, 507f), Triple("h23", 456f, 550f), Triple("h24", 754f, 588f),
            Triple("h25", 308f, 592f), Triple("h26", 116f, 678f), Triple("h27", 502f, 670f), Triple("h28", 843f, 705f),
            Triple("h29", 306f, 744f), Triple("h30", 457f, 785f), Triple("h31", 648f, 785f), Triple("h32", 155f, 866f),
            Triple("h33", 457f, 902f), Triple("h34", 600f, 944f), Triple("h35", 800f, 941f), Triple("h36", 304f, 983f),
            Triple("h37", 257f, 1107f), Triple("h38", 442f, 1108f), Triple("h39", 708f, 1109f), Triple("h40", 157f, 1204f),
            Triple("h41", 300f, 1205f), Triple("h42", 522f, 1208f), Triple("h43", 807f, 1208f),
        )
        val actualById = BoardDefaults.holds.associateBy { it.id }
        val sourceImageSize = PixelSize(width = 960f, height = 1280f)

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
