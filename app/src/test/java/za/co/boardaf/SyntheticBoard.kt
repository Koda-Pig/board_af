package za.co.boardaf

import za.co.boardaf.model.NormalizedPoint
import za.co.boardaf.vision.DetectorFrame
import za.co.boardaf.vision.HoldDetector

/** A flat grey wall with solid discs on it, for tests that need a board the detector can read. */
object SyntheticBoard {
    const val WIDTH = HoldDetector.WORK_WIDTH
    const val HEIGHT = 793

    /** Big enough to clear the detector's minimum hold area, small enough not to merge. */
    const val HOLD_RADIUS = 12

    fun frame(
        holds: List<NormalizedPoint>,
        width: Int = WIDTH,
        height: Int = HEIGHT,
        radius: Int = HOLD_RADIUS,
    ): DetectorFrame {
        val pixels = IntArray(width * height) { 0xFF8A8A86.toInt() }
        holds.forEach { hold ->
            val cx = (hold.x * width).toInt()
            val cy = (hold.y * height).toInt()
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    if (dx * dx + dy * dy > radius * radius) continue
                    val x = cx + dx
                    val y = cy + dy
                    if (x in 0 until width && y in 0 until height) {
                        pixels[y * width + x] = 0xFF2E6F3C.toInt()
                    }
                }
            }
        }
        return DetectorFrame(width, height, pixels)
    }
}
