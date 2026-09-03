package za.co.boardaf.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import za.co.boardaf.model.BoardPhoto

/**
 * Board photos live in the app's own files directory, so they are private, need
 * no storage permission, and are removed with the app.
 */
class AndroidBoardPhotoStore(context: Context) : BoardPhotoStore {
    private val appContext = context.applicationContext

    override fun createPending(): String? = runCatching {
        val file = File(directory(), "board-${System.currentTimeMillis()}.jpg")
        file.createNewFile()
        file.name
    }.getOrNull()

    override fun shareUri(fileName: String): String? = runCatching {
        FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.boardphotos",
            File(directory(), fileName),
        ).toString()
    }.getOrNull()

    /**
     * Rewrites the capture in place: downsampled to [MAX_DIMENSION] and rotated
     * upright. Normalizing once here means nothing downstream has to carry EXIF
     * orientation around, and the board frame can trust the stored pixel size.
     */
    override fun adopt(fileName: String): BoardPhoto? {
        val file = File(directory(), fileName)
        val photo = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            }
            val decoded = BitmapFactory.decodeFile(file.path, options) ?: return@runCatching null
            val upright = decoded.rotatedUpright(file.path)

            FileOutputStream(file).use { out ->
                upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            val result = BoardPhoto(
                fileName = fileName,
                widthPx = upright.width,
                heightPx = upright.height,
                capturedAt = System.currentTimeMillis(),
            )
            if (upright !== decoded) decoded.recycle()
            upright.recycle()
            result
        }.getOrNull()

        // An unreadable capture is worthless and would otherwise sit there forever.
        if (photo == null) delete(fileName)
        return photo
    }

    override fun delete(fileName: String) {
        runCatching { File(directory(), fileName).delete() }
    }

    override fun pathFor(photo: BoardPhoto): String? {
        val file = File(directory(), photo.fileName)
        return if (file.isFile) file.path else null
    }

    private fun directory(): File = File(appContext.filesDir, DIRECTORY).apply { mkdirs() }

    private fun Bitmap.rotatedUpright(path: String): Bitmap {
        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return this
        }
        return runCatching {
            Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        }.getOrDefault(this)
    }

    private companion object {
        const val DIRECTORY = "board_photos"

        /**
         * The board renders about a phone-width wide, so a full-resolution camera
         * frame is wasted bytes — and large enough to risk OOM when decoded.
         */
        const val MAX_DIMENSION = 2048
        const val JPEG_QUALITY = 90

        fun sampleSizeFor(width: Int, height: Int): Int {
            var sample = 1
            while (maxOf(width, height) / sample > MAX_DIMENSION) sample *= 2
            return sample
        }
    }
}
