package za.co.boardaf.model

/**
 * Zoom/pan state for the shared photo/overlay coordinate space. Stored hold
 * coordinates stay normalized; only the on-screen mapping changes. Offsets are in
 * container pixels with the transform origin at the top-left corner.
 */
data class BoardTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    val isIdentity: Boolean
        get() = scale == 1f && offsetX == 0f && offsetY == 0f

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 4f
        val IDENTITY = BoardTransform()
    }
}

object BoardTransforms {

    /** Apply a pinch/pan gesture step anchored at [centroid], then clamp to the container. */
    fun applyGesture(
        current: BoardTransform,
        containerSize: PixelSize,
        centroid: PixelPoint,
        panX: Float,
        panY: Float,
        zoom: Float,
    ): BoardTransform {
        val newScale = (current.scale * zoom)
            .coerceIn(BoardTransform.MIN_SCALE, BoardTransform.MAX_SCALE)
        val scaleRatio = newScale / current.scale
        val offsetX = centroid.x - (centroid.x - current.offsetX) * scaleRatio + panX
        val offsetY = centroid.y - (centroid.y - current.offsetY) * scaleRatio + panY
        return clamp(BoardTransform(newScale, offsetX, offsetY), containerSize)
    }

    /** Keep the board on screen: content may never leave a gap at any edge. */
    fun clamp(transform: BoardTransform, containerSize: PixelSize): BoardTransform {
        val scale = transform.scale.coerceIn(BoardTransform.MIN_SCALE, BoardTransform.MAX_SCALE)
        val minOffsetX = containerSize.width - containerSize.width * scale
        val minOffsetY = containerSize.height - containerSize.height * scale
        return BoardTransform(
            scale = scale,
            offsetX = transform.offsetX.coerceIn(minOffsetX, 0f),
            offsetY = transform.offsetY.coerceIn(minOffsetY, 0f),
        )
    }

    fun reset(): BoardTransform = BoardTransform.IDENTITY

    /** Screen position of a normalized board point under the transform. */
    fun anchor(
        point: NormalizedPoint,
        containerSize: PixelSize,
        transform: BoardTransform,
    ): PixelPoint {
        val base = BoardGeometry.centerInPixels(point, containerSize)
        return PixelPoint(
            x = base.x * transform.scale + transform.offsetX,
            y = base.y * transform.scale + transform.offsetY,
        )
    }
}
