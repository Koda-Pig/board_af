package za.co.boardaf.vision

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import za.co.boardaf.model.NormalizedPoint

/**
 * A downsampled board image to search: packed `0xAARRGGBB`, row-major.
 * Deliberately Android-free so [HoldDetector] can be exercised on the JVM.
 */
class DetectorFrame(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(pixels.size >= width * height) { "pixel buffer is smaller than $width x $height" }
    }
}

/**
 * Suggests where the holds are in a photo of the board, with no model and no
 * network: a hold is a patch that is either more colourful or further from its
 * own surroundings' brightness than the wall is, big enough to be a hold and not
 * a t-nut, and compact enough not to be a chalk scrawl.
 *
 * Every threshold is a multiple of the picture's own median and median absolute
 * deviation rather than a fixed number. That is what makes it survive a phone's
 * exposure and white balance: an absolute threshold tuned on one photo found
 * nothing at all on a warmer-toned copy of the same wall, while these numbers
 * held across exposure, contrast, white-balance, focus and rotation changes.
 *
 * It suggests; it does not decide. Callers reconcile the result against the map
 * that already exists — see [HoldSuggestions] — because the detector finding
 * nothing where a hold is must never remove that hold.
 */
object HoldDetector {

    /**
     * Width the caller should downsample to. Chosen with the tuning: holds stay
     * ~25-50px across, which keeps the morphology radii below meaningful, and
     * 540x~790 is small enough to search in well under a second on a phone.
     */
    const val WORK_WIDTH = 540

    /** Window the "local background" brightness is averaged over, in work pixels. */
    private const val BACKGROUND_RADIUS = 32

    // Thresholds, as multiples of each channel's own median absolute deviation.
    private const val SATURATION_K = 5.0f
    private const val LUMINANCE_K = 9.0f
    private const val FLAT_LUMINANCE_K = 8.0f
    private const val FLAT_SATURATION_K = 6.0f

    /** Severs the thin links — panel seams, chalk strokes — before labelling. */
    private const val OPEN_RADIUS = 2

    /** Closes a hold's bolt hole and shading back into one blob. */
    private const val CLOSE_RADIUS = 5

    private const val MIN_AREA_FRACTION = 0.0002f
    private const val MAX_AREA_FRACTION = 0.03f
    private const val MIN_SOLIDITY = 0.35f
    private const val MAX_ASPECT = 5.0f

    /** A flat patch this size or larger is a board panel; the kickboard is one too. */
    private const val MIN_PANEL_FRACTION = 0.02f

    /**
     * Bridges the bite a hold takes out of a panel's edge when it is bolted right
     * across it. Without this, the flat-wall patch stops below the top row of
     * holds, those holds fall outside the board, and they are thrown away —
     * which is exactly what happened on the first wall this was tried on.
     *
     * A panel edge is straight, so closing gives the panel back its own shape and
     * only fills the notches. Roughly the width of the widest hold.
     */
    private const val BOARD_CLOSE_RADIUS = 16

    private const val MEDIAN_BINS = 1024

    fun detect(frame: DetectorFrame): List<NormalizedPoint> {
        val w = frame.width
        val h = frame.height
        if (w < 8 || h < 8) return emptyList()
        val n = w * h

        val saturation = FloatArray(n)
        val luminance = FloatArray(n)
        for (i in 0 until n) {
            val p = frame.pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            saturation[i] = if (mx > 0f) (mx - mn) / mx else 0f
            luminance[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        val background = boxBlur(luminance, w, h, BACKGROUND_RADIUS)
        val deviation = FloatArray(n) { abs(luminance[it] - background[it]) }

        val satMedian = median(saturation)
        val satSpread = medianAbsoluteDeviation(saturation, satMedian)
        val devMedian = median(deviation)
        val devSpread = medianAbsoluteDeviation(deviation, devMedian)
        val lumMedian = median(luminance)
        val lumSpread = medianAbsoluteDeviation(luminance, lumMedian)

        val board = boardRegion(
            luminance = luminance,
            saturation = saturation,
            width = w,
            height = h,
            lumMedian = lumMedian,
            lumBand = FLAT_LUMINANCE_K * lumSpread,
            satCeiling = satMedian + FLAT_SATURATION_K * satSpread,
        )

        val satCut = satMedian + SATURATION_K * satSpread
        val devCut = devMedian + LUMINANCE_K * devSpread
        var mask = BooleanArray(n) {
            board[it] && (saturation[it] > satCut || deviation[it] > devCut)
        }
        mask = open(mask, w, h, OPEN_RADIUS)
        mask = close(mask, w, h, CLOSE_RADIUS)
        for (i in 0 until n) if (!board[i]) mask[i] = false

        var boardArea = 0
        for (i in 0 until n) if (board[i]) boardArea++
        if (boardArea == 0) return emptyList()

        val minArea = MIN_AREA_FRACTION * boardArea
        val maxArea = MAX_AREA_FRACTION * boardArea
        return components(mask, w, h)
            .filter { blob ->
                val area = blob.area.toFloat()
                if (area < minArea || area > maxArea) return@filter false
                val boxW = blob.maxX - blob.minX + 1
                val boxH = blob.maxY - blob.minY + 1
                val longer = max(boxW, boxH).toFloat()
                val shorter = max(1, min(boxW, boxH)).toFloat()
                if (longer / shorter > MAX_ASPECT) return@filter false
                area / (boxW * boxH) >= MIN_SOLIDITY
            }
            // Top to bottom, then left to right: a stable order means the ids a
            // caller hands out don't shuffle between two runs on the same photo.
            .sortedWith(compareBy({ it.sumY / it.area }, { it.sumX / it.area }))
            .map { blob ->
                NormalizedPoint(
                    x = (blob.sumX / blob.area / w).toFloat().coerceIn(0f, 1f),
                    y = (blob.sumY / blob.area / h).toFloat().coerceIn(0f, 1f),
                )
            }
    }

    /**
     * The board's panels: large patches of ordinary, uncoloured wall with their
     * holes filled back in, so the holds sitting on them count as board again.
     *
     * Every large patch qualifies, not just the biggest — the kickboard is a
     * panel of its own, and keeping only the largest dropped every kickboard hold
     * during tuning. Anything off the board (the room behind it, the floor) is
     * excluded, which is what stops a hold at the very edge of the frame from
     * merging into the surroundings and being thrown away as one huge blob.
     */
    private fun boardRegion(
        luminance: FloatArray,
        saturation: FloatArray,
        width: Int,
        height: Int,
        lumMedian: Float,
        lumBand: Float,
        satCeiling: Float,
    ): BooleanArray {
        val n = width * height
        var flat = BooleanArray(n) {
            abs(luminance[it] - lumMedian) < lumBand && saturation[it] < satCeiling
        }
        flat = open(flat, width, height, 2)

        val board = BooleanArray(n)
        val minPanel = MIN_PANEL_FRACTION * n
        var any = false
        for (blob in components(flat, width, height)) {
            if (blob.area < minPanel) continue
            any = true
            for (i in blob.pixels) board[i] = true
        }
        if (!any) {
            // Nothing looked like a flat panel — a tight crop, or a wall with no
            // plain surface left. Searching the whole frame beats finding nothing.
            return BooleanArray(n) { true }
        }
        fillHoles(board, width, height)
        return boxClose(board, width, height, BOARD_CLOSE_RADIUS)
    }

    /**
     * Square-structuring-element dilate or erode, separably, using a prefix sum
     * per line — constant work per pixel whatever the radius, which is what makes
     * a radius as large as [BOARD_CLOSE_RADIUS] affordable. The disk element the
     * hold mask uses is a better shape for holds but far too slow at this size.
     *
     * Outside the frame counts as empty when dilating and as filled when eroding,
     * so a board that runs off the edge of the photo is neither grown into the
     * margin nor eaten away from it.
     */
    private fun boxMorph(
        mask: BooleanArray,
        width: Int,
        height: Int,
        radius: Int,
        dilate: Boolean,
    ): BooleanArray {
        val horizontal = BooleanArray(mask.size)
        boxPass(mask, horizontal, width, height, radius, dilate, alongRows = true)
        val out = BooleanArray(mask.size)
        boxPass(horizontal, out, width, height, radius, dilate, alongRows = false)
        return out
    }

    private fun boxPass(
        source: BooleanArray,
        target: BooleanArray,
        width: Int,
        height: Int,
        radius: Int,
        dilate: Boolean,
        alongRows: Boolean,
    ) {
        val length = if (alongRows) width else height
        val lines = if (alongRows) height else width
        val prefix = IntArray(length + 1)
        for (line in 0 until lines) {
            for (i in 0 until length) {
                val index = if (alongRows) line * width + i else i * width + line
                prefix[i + 1] = prefix[i] + if (source[index]) 1 else 0
            }
            for (i in 0 until length) {
                val lo = max(0, i - radius)
                val hi = min(length - 1, i + radius)
                val count = prefix[hi + 1] - prefix[lo]
                val index = if (alongRows) line * width + i else i * width + line
                target[index] = if (dilate) count > 0 else count == hi - lo + 1
            }
        }
    }

    private fun boxClose(mask: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray =
        boxMorph(boxMorph(mask, width, height, radius, dilate = true), width, height, radius, dilate = false)

    /**
     * Mean luminance over a (2r+1) square, in constant time per pixel.
     *
     * The edges are extended by repeating the border pixel, so every window
     * averages the same number of samples. Shrinking the window at the edges
     * instead looks harmless and is not: it makes the background noisier exactly
     * where there are fewer samples, and the detector scales its thresholds by
     * the spread of this signal — a clamped window cost 8 of 43 holds in testing.
     */
    private fun boxBlur(source: FloatArray, width: Int, height: Int, radius: Int): FloatArray {
        val padW = width + 2 * radius
        val padH = height + 2 * radius
        val stride = padW + 1
        val integral = DoubleArray(stride * (padH + 1))
        for (y in 0 until padH) {
            val sy = (y - radius).coerceIn(0, height - 1) * width
            var rowSum = 0.0
            for (x in 0 until padW) {
                rowSum += source[sy + (x - radius).coerceIn(0, width - 1)]
                integral[(y + 1) * stride + x + 1] = integral[y * stride + x + 1] + rowSum
            }
        }
        val side = 2 * radius + 1
        val divisor = (side.toDouble() * side)
        val out = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val sum = integral[(y + side) * stride + x + side] -
                    integral[y * stride + x + side] -
                    integral[(y + side) * stride + x] +
                    integral[y * stride + x]
                out[y * width + x] = (sum / divisor).toFloat()
            }
        }
        return out
    }

    private fun median(values: FloatArray): Float {
        val bins = IntArray(MEDIAN_BINS)
        for (v in values) bins[binOf(v)]++
        return quantile(bins, values.size)
    }

    private fun medianAbsoluteDeviation(values: FloatArray, median: Float): Float {
        val bins = IntArray(MEDIAN_BINS)
        for (v in values) bins[binOf(abs(v - median))]++
        // A spread of exactly zero would make every threshold collapse onto the
        // median and flag half the picture; a floor of one bin keeps it sane.
        return max(quantile(bins, values.size), 1f / MEDIAN_BINS)
    }

    private fun binOf(value: Float): Int =
        ((value.coerceIn(0f, 1f)) * (MEDIAN_BINS - 1)).toInt()

    private fun quantile(bins: IntArray, count: Int): Float {
        val target = count / 2
        var seen = 0
        for (i in bins.indices) {
            seen += bins[i]
            if (seen > target) return (i + 0.5f) / MEDIAN_BINS
        }
        return 1f
    }

    private class Blob(
        val area: Int,
        val sumX: Double,
        val sumY: Double,
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int,
        val pixels: IntArray,
    )

    /** Four-connected components of [mask], each with the stats the filters need. */
    private fun components(mask: BooleanArray, width: Int, height: Int): List<Blob> {
        val n = width * height
        val seen = BooleanArray(n)
        val stack = IntArray(n)
        val members = IntArray(n)
        val blobs = mutableListOf<Blob>()
        for (start in 0 until n) {
            if (!mask[start] || seen[start]) continue
            var top = 0
            stack[top++] = start
            seen[start] = true
            var count = 0
            var sumX = 0.0
            var sumY = 0.0
            var minX = width
            var maxX = 0
            var minY = height
            var maxY = 0
            while (top > 0) {
                val i = stack[--top]
                members[count++] = i
                val x = i % width
                val y = i / width
                sumX += x
                sumY += y
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                if (x > 0 && mask[i - 1] && !seen[i - 1]) { seen[i - 1] = true; stack[top++] = i - 1 }
                if (x < width - 1 && mask[i + 1] && !seen[i + 1]) { seen[i + 1] = true; stack[top++] = i + 1 }
                if (y > 0 && mask[i - width] && !seen[i - width]) { seen[i - width] = true; stack[top++] = i - width }
                if (y < height - 1 && mask[i + width] && !seen[i + width]) { seen[i + width] = true; stack[top++] = i + width }
            }
            blobs += Blob(count, sumX, sumY, minX, maxX, minY, maxY, members.copyOf(count))
        }
        return blobs
    }

    /** Fill anything enclosed by [region]: flood the outside in, invert the rest. */
    private fun fillHoles(region: BooleanArray, width: Int, height: Int) {
        val n = width * height
        val outside = BooleanArray(n)
        val stack = IntArray(n)
        var top = 0
        fun push(i: Int) {
            if (!region[i] && !outside[i]) {
                outside[i] = true
                stack[top++] = i
            }
        }
        for (x in 0 until width) {
            push(x)
            push((height - 1) * width + x)
        }
        for (y in 0 until height) {
            push(y * width)
            push(y * width + width - 1)
        }
        while (top > 0) {
            val i = stack[--top]
            val x = i % width
            val y = i / width
            if (x > 0) push(i - 1)
            if (x < width - 1) push(i + 1)
            if (y > 0) push(i - width)
            if (y < height - 1) push(i + width)
        }
        for (i in 0 until n) if (!outside[i]) region[i] = true
    }

    private fun diskOffsets(radius: Int, width: Int): IntArray {
        val offsets = mutableListOf<Int>()
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                if (dx * dx + dy * dy <= radius * radius) offsets += dy * width + dx
            }
        }
        return offsets.toIntArray()
    }

    private fun erode(mask: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray {
        val offsets = diskOffsets(radius, width)
        val out = BooleanArray(mask.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                if (!mask[i]) continue
                // Out-of-frame counts as set, so a hold running off the edge of the
                // photo isn't eaten away from the border inwards.
                var keep = true
                if (x >= radius && x < width - radius && y >= radius && y < height - radius) {
                    for (o in offsets) {
                        if (!mask[i + o]) {
                            keep = false
                            break
                        }
                    }
                }
                out[i] = keep
            }
        }
        return out
    }

    private fun dilate(mask: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray {
        val offsets = diskOffsets(radius, width)
        val out = BooleanArray(mask.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                if (mask[i]) {
                    out[i] = true
                    continue
                }
                if (x < radius || x >= width - radius || y < radius || y >= height - radius) continue
                for (o in offsets) {
                    if (mask[i + o]) {
                        out[i] = true
                        break
                    }
                }
            }
        }
        return out
    }

    private fun open(mask: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray =
        dilate(erode(mask, width, height, radius), width, height, radius)

    private fun close(mask: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray =
        erode(dilate(mask, width, height, radius), width, height, radius)
}
