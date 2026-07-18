package za.co.boardaf.model

data class NormalizedPoint(val x: Float, val y: Float)

data class PixelSize(val width: Float, val height: Float)

data class PixelPoint(val x: Float, val y: Float)

object BoardGeometry {
    const val IMAGE_ASPECT_RATIO = 3f / 4f

    fun imageSizeForWidth(widthPixels: Int): PixelSize = imageSizeForWidth(widthPixels.toFloat())

    fun imageSizeForWidth(widthPixels: Float): PixelSize {
        val safeWidth = widthPixels.coerceAtLeast(0f)
        return PixelSize(
            width = safeWidth,
            height = safeWidth / IMAGE_ASPECT_RATIO,
        )
    }

    fun centerInPixels(point: NormalizedPoint, imageSize: PixelSize): PixelPoint = PixelPoint(
        x = point.x.coerceIn(0f, 1f) * imageSize.width,
        y = point.y.coerceIn(0f, 1f) * imageSize.height,
    )
}
