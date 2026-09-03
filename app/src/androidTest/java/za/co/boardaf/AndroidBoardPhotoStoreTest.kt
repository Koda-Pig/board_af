package za.co.boardaf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import za.co.boardaf.data.AndroidBoardPhotoStore
import za.co.boardaf.model.BoardPhoto

/**
 * The photo store is the one part of board capture that can't be reached from a
 * JVM test: decoding, downsampling and EXIF rotation are all real Android APIs.
 * These run on a device against the app's own files directory.
 */
@RunWith(AndroidJUnit4::class)
class AndroidBoardPhotoStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = AndroidBoardPhotoStore(context)

    /** Resolves through the store rather than assuming its directory layout. */
    private fun fileFor(name: String): File =
        File(requireNotNull(store.pathFor(BoardPhoto(name, 0, 0, 0L))))

    private fun writeJpeg(name: String, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        FileOutputStream(fileFor(name)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
    }

    @Before
    fun clearPreviousCaptures() {
        // Each run starts from a known directory; the store is app-wide state.
        val marker = store.createPending()
        if (marker != null) {
            fileFor(marker).parentFile?.listFiles()?.forEach { it.delete() }
        }
    }

    @Test
    fun createPendingMakesAnEmptyFileTheCameraCanWriteTo() {
        val name = requireNotNull(store.createPending())

        val file = fileFor(name)
        assertTrue(file.isFile)
        assertEquals(0L, file.length())
    }

    @Test
    fun shareUriAddressesTheCaptureThroughTheAppsFileProvider() {
        val name = requireNotNull(store.createPending())

        val uri = requireNotNull(store.shareUri(name))

        assertTrue(uri, uri.startsWith("content://${context.packageName}.boardphotos/"))
    }

    @Test
    fun adoptRecordsTheStoredPixelSizeOfASmallCapture() {
        val name = requireNotNull(store.createPending())
        writeJpeg(name, width = 900, height = 1200)

        val photo = requireNotNull(store.adopt(name))

        assertEquals(900, photo.widthPx)
        assertEquals(1200, photo.heightPx)
        assertEquals(900f / 1200f, photo.aspectRatio, 0.0001f)
    }

    @Test
    fun adoptDownsamplesACaptureLargerThanTheBoardIsEverDrawn() {
        val name = requireNotNull(store.createPending())
        writeJpeg(name, width = 3000, height = 4000)

        val photo = requireNotNull(store.adopt(name))

        assertTrue("was ${photo.widthPx}x${photo.heightPx}", maxOf(photo.widthPx, photo.heightPx) <= 2048)
        // Shape survives the downsample, so the board frame stays true to the wall.
        assertEquals(3f / 4f, photo.aspectRatio, 0.01f)
        // The recorded size describes what is actually on disk now.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(fileFor(name).path, bounds)
        assertEquals(photo.widthPx, bounds.outWidth)
        assertEquals(photo.heightPx, bounds.outHeight)
    }

    @Test
    fun adoptRejectsAndCleansUpACaptureThatIsNotAnImage() {
        val name = requireNotNull(store.createPending())
        val file = fileFor(name)
        file.writeText("the camera app wrote nothing usable")

        assertNull(store.adopt(name))
        assertFalse("an unreadable capture must not linger", file.isFile)
    }

    @Test
    fun adoptRejectsACaptureTheCameraNeverWroteTo() {
        val name = requireNotNull(store.createPending())

        assertNull(store.adopt(name))
        assertNull(store.pathFor(BoardPhoto(name, 0, 0, 0L)))
    }

    @Test
    fun pathForReportsNothingOnceThePhotoIsDeleted() {
        val name = requireNotNull(store.createPending())
        writeJpeg(name, width = 400, height = 600)
        val photo = requireNotNull(store.adopt(name))
        assertNotNull(store.pathFor(photo))

        store.delete(photo.fileName)

        assertNull(store.pathFor(photo))
    }
}
