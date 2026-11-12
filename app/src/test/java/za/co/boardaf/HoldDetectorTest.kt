package za.co.boardaf

import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.abs
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.boardaf.model.BoardDefaults
import za.co.boardaf.model.NormalizedPoint
import za.co.boardaf.vision.DetectorFrame
import za.co.boardaf.vision.HoldDetector

/**
 * The detector run against the real board photo, scored on the hold map that was
 * mapped by hand. This is the test that would catch a tuning change making the
 * suggestion worse, which no synthetic image can.
 */
class HoldDetectorTest {

    private val photo = File("src/main/res/drawable-nodpi/home_board.jpg")

    /**
     * The same wall re-set with different holds, shot on a phone: busier, far more
     * colourful, and with a margin of white room wall all round. The first wall
     * alone said almost nothing about the thresholds; this one is what caught the
     * board edge throwing away every hold bolted across the top of a panel.
     */
    private fun secondWall(): BufferedImage =
        javaClass.getResourceAsStream("/reset-board.jpg").use { ImageIO.read(it) }

    /**
     * Holds on the second wall picked by eye off the photo, spread over the frame
     * and over the colours, including the top row that sits across the panel edge
     * and one on the kickboard. Not every hold on that wall — enough of them, and
     * the awkward ones.
     */
    private val secondWallHolds = listOf(
        0.085f to 0.043f, 0.401f to 0.047f, 0.541f to 0.042f, 0.643f to 0.046f,
        0.896f to 0.039f, 0.460f to 0.081f, 0.581f to 0.112f, 0.152f to 0.117f,
        0.737f to 0.128f, 0.463f to 0.144f, 0.278f to 0.158f, 0.834f to 0.183f,
        0.528f to 0.216f, 0.401f to 0.279f, 0.078f to 0.302f, 0.891f to 0.309f,
        0.524f to 0.382f, 0.463f to 0.449f, 0.636f to 0.475f, 0.404f to 0.537f,
        0.696f to 0.539f, 0.105f to 0.598f, 0.222f to 0.695f, 0.761f to 0.697f,
        0.364f to 0.849f, 0.223f to 0.893f,
    ).map { NormalizedPoint(it.first, it.second) }

    /**
     * Area-average downscale. Java's bilinear `drawImage` point-samples when it
     * shrinks this far, and the aliasing that produces lifts the picture's own
     * noise floor — which is exactly what the detector scales its thresholds by.
     */
    private fun frameOf(image: BufferedImage, workWidth: Int = HoldDetector.WORK_WIDTH): DetectorFrame {
        val h = (workWidth.toLong() * image.height / image.width).toInt()
        val pixels = IntArray(workWidth * h)
        for (y in 0 until h) {
            val sy0 = y * image.height / h
            val sy1 = maxOf(sy0 + 1, (y + 1) * image.height / h)
            for (x in 0 until workWidth) {
                val sx0 = x * image.width / workWidth
                val sx1 = maxOf(sx0 + 1, (x + 1) * image.width / workWidth)
                var r = 0L
                var g = 0L
                var b = 0L
                var count = 0
                for (sy in sy0 until sy1) {
                    for (sx in sx0 until sx1) {
                        val p = image.getRGB(sx, sy)
                        r += (p shr 16) and 0xFF
                        g += (p shr 8) and 0xFF
                        b += p and 0xFF
                        count++
                    }
                }
                pixels[y * workWidth + x] =
                    (((r / count).toInt()) shl 16) or (((g / count).toInt()) shl 8) or (b / count).toInt()
            }
        }
        return DetectorFrame(workWidth, h, pixels)
    }

    /** Within half a hold's spacing, measured in frame-width units. */
    private fun hits(found: List<NormalizedPoint>, aspect: Float = 1080f / 1586f): Int =
        BoardDefaults.holds.count { hold ->
            found.any { p ->
                val dx = p.x - hold.point.x
                val dy = (p.y - hold.point.y) / aspect
                dx * dx + dy * dy < 0.045f * 0.045f
            }
        }

    private fun shift(image: BufferedImage, red: Float, green: Float, blue: Float): BufferedImage {
        val out = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val p = image.getRGB(x, y)
                val r = (((p shr 16) and 0xFF) * red).toInt().coerceIn(0, 255)
                val g = (((p shr 8) and 0xFF) * green).toInt().coerceIn(0, 255)
                val b = ((p and 0xFF) * blue).toInt().coerceIn(0, 255)
                out.setRGB(x, y, (r shl 16) or (g shl 8) or b)
            }
        }
        return out
    }

    @Test
    fun `finds most of the mapped holds in the board photo`() {
        assertTrue("bundled board photo is missing", photo.exists())
        val found = HoldDetector.detect(frameOf(ImageIO.read(photo)))

        val hit = hits(found)
        // Tuning measured 41/43 with 3 extra blobs. The floor is set below that so
        // an unrelated change doesn't fail the build, but a real regression does.
        assertTrue("only matched $hit of 43 mapped holds", hit >= 36)
        assertTrue("suggested ${found.size} blobs, far more than there are holds", found.size <= 55)
        println("detector: matched $hit/43, suggested ${found.size}")
    }

    @Test
    fun `survives the exposure and white balance a different phone would give`() {
        val source = ImageIO.read(photo)
        // Absolute thresholds scored zero on the warm copy during tuning; these are
        // multiples of the picture's own spread, which is what this test pins down.
        val cases = mapOf(
            "dim" to shift(source, 0.7f, 0.7f, 0.7f),
            "bright" to shift(source, 1.3f, 1.3f, 1.3f),
            "warm" to shift(source, 1.15f, 1.0f, 0.88f),
            "cool" to shift(source, 0.88f, 1.0f, 1.15f),
        )
        cases.forEach { (name, image) ->
            val hit = hits(HoldDetector.detect(frameOf(image)))
            println("detector ($name): matched $hit/43")
            assertTrue("$name only matched $hit of 43 mapped holds", hit >= 28)
        }
    }

    @Test
    fun `finds the holds on a second wall it was never tuned on`() {
        val image = secondWall()
        val found = HoldDetector.detect(frameOf(image))

        val aspect = image.width.toFloat() / image.height
        val missed = secondWallHolds.filterNot { want ->
            found.any { p ->
                val dx = p.x - want.x
                val dy = (p.y - want.y) / aspect
                dx * dx + dy * dy < 0.035f * 0.035f
            }
        }
        println("second wall: ${secondWallHolds.size - missed.size}/${secondWallHolds.size} " +
            "checked holds, ${found.size} suggestions, missed $missed")
        assertTrue("missed $missed of the checked holds", missed.size <= 2)
        // Enough to be a starting point without burying the setter in rejects. The
        // wall has roughly sixty holds; the extras are mostly bolt washers.
        assertTrue("only ${found.size} suggestions", found.size >= 50)
        assertTrue("${found.size} suggestions is far more than the wall has", found.size <= 75)
    }

    @Test
    fun `holds bolted across the top of a panel are not thrown away with the margin`() {
        // The flat-wall patch stops below a hold that straddles the panel's edge,
        // which used to put the whole top row outside the board and lose it.
        val image = secondWall()
        val found = HoldDetector.detect(frameOf(image))

        val topRow = found.count { it.y < 0.06f }
        assertTrue("found only $topRow holds along the top edge", topRow >= 4)
    }

    @Test
    fun `finds plain discs on a plain wall`() {
        val placed = listOf(
            NormalizedPoint(0.25f, 0.20f),
            NormalizedPoint(0.75f, 0.50f),
            NormalizedPoint(0.40f, 0.85f),
        )

        val found = HoldDetector.detect(SyntheticBoard.frame(placed))

        assertEquals(placed.size, found.size)
        placed.forEach { want ->
            assertTrue(
                "nothing suggested near $want, got $found",
                found.any { abs(it.x - want.x) < 0.01f && abs(it.y - want.y) < 0.01f },
            )
        }
    }

    @Test
    fun `a blank frame suggests nothing`() {
        val blank = DetectorFrame(120, 160, IntArray(120 * 160) { 0xFF808080.toInt() })

        assertEquals(emptyList<NormalizedPoint>(), HoldDetector.detect(blank))
    }

    @Test
    fun `a frame too small to hold anything is refused quietly`() {
        assertEquals(
            emptyList<NormalizedPoint>(),
            HoldDetector.detect(DetectorFrame(4, 4, IntArray(16))),
        )
    }
}
