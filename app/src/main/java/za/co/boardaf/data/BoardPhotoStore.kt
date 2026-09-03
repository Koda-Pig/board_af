package za.co.boardaf.data

import za.co.boardaf.model.BoardPhoto

/**
 * Storage boundary for the setter's own board photo.
 *
 * Deliberately Android-free — file names and URI strings, no `Uri`/`Bitmap` — so
 * `BoardViewModel` can be exercised in plain JVM tests with a fake, the same way
 * [SnapshotIO] keeps the snapshot store testable.
 *
 * A capture is two steps because the camera app writes the file itself: the store
 * hands out an empty destination, and only once the camera reports success is the
 * file measured and adopted. A cancelled capture leaves an empty file behind,
 * which is why [delete] exists.
 */
interface BoardPhotoStore {
    /** Create an empty file for the camera to write into; returns its name, or null on failure. */
    fun createPending(): String?

    /**
     * A `content://` URI string the camera app may write to. Separate from
     * [createPending] because only the UI layer turns it back into a `Uri`.
     */
    fun shareUri(fileName: String): String?

    /**
     * Normalize a completed capture (downsample, apply EXIF rotation) and describe
     * it. Returns null — having deleted the file — when it can't be read as an image.
     */
    fun adopt(fileName: String): BoardPhoto?

    fun delete(fileName: String)

    /** Absolute path of a stored photo, or null when the file is gone. */
    fun pathFor(photo: BoardPhoto): String?
}
