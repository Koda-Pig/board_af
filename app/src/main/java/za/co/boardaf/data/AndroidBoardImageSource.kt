package za.co.boardaf.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import java.io.File
import kotlin.math.max
import za.co.boardaf.R
import za.co.boardaf.vision.DetectorFrame
import za.co.boardaf.vision.HoldDetector

/**
 * Decodes the board image straight down to the detector's working size.
 *
 * The bulk of the shrink is done by `inSampleSize`, which averages whole blocks
 * of pixels. That matters more than it looks: a plain bilinear shrink of this
 * ratio point-samples and aliases, and the detector scales its thresholds by the
 * picture's own noise — aliasing lifts that noise and costs real holds.
 */
class AndroidBoardImageSource(context: Context) : BoardImageSource {
    private val appContext = context.applicationContext

    override fun load(photoPath: String?): DetectorFrame? = runCatching {
        val target = HoldDetector.WORK_WIDTH
        val decoded = decode(photoPath, target) ?: return null
        val scaled = if (decoded.width > target) {
            val height = max(1, (target.toLong() * decoded.height / decoded.width).toInt())
            decoded.scale(target, height)
        } else {
            decoded
        }
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        val frame = DetectorFrame(scaled.width, scaled.height, pixels)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        frame
    }.getOrNull()

    private fun decode(photoPath: String?, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        readInto(photoPath, bounds)
        if (bounds.outWidth <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, target)
        }
        return readInto(photoPath, options)
    }

    private fun readInto(photoPath: String?, options: BitmapFactory.Options): Bitmap? =
        if (photoPath == null) {
            BitmapFactory.decodeResource(appContext.resources, R.drawable.home_board, options)
        } else {
            BitmapFactory.decodeFile(File(photoPath).absolutePath, options)
        }

    /** The largest power-of-two shrink that still leaves at least [target] across. */
    private fun sampleSizeFor(sourceWidth: Int, target: Int): Int {
        var sample = 1
        while (sourceWidth / (sample * 2) >= target) sample *= 2
        return sample
    }
}
