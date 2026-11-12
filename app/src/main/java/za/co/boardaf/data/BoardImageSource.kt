package za.co.boardaf.data

import za.co.boardaf.vision.DetectorFrame

/**
 * Reads whichever board image is on screen into a frame the hold detector can
 * search. Separate from [BoardPhotoStore] because the bundled board is a drawable
 * rather than a file, and the detector has to be able to search that one too.
 */
interface BoardImageSource {
    /** [photoPath] is the setter's own photo, or null for the bundled board. */
    fun load(photoPath: String?): DetectorFrame?
}
