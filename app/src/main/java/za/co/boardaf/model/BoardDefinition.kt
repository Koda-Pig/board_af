package za.co.boardaf.model

enum class BoardZoneType(val label: String) {
    MAIN("Main board"),
    KICKBOARD("Kickboard"),
    ;

    val defaultCapability: HoldCapability
        get() = when (this) {
            MAIN -> HoldCapability.HAND_AND_FOOT
            KICKBOARD -> HoldCapability.FOOT_ONLY
        }
}

enum class HoldCapability(val label: String) {
    HAND_AND_FOOT("Hands and feet"),
    FOOT_ONLY("Foot only"),
    ;

    val allowsHands: Boolean
        get() = this == HAND_AND_FOOT

    val allowsFeet: Boolean
        get() = true
}

/**
 * A photo of the physical board, taken in-app and stored in the app's own files
 * directory. Only the file name is persisted: the directory is resolved at read
 * time, so the record survives the app's data directory moving.
 *
 * The pixel size is recorded so the board frame can take the photo's own aspect
 * ratio. Hold centers are normalized to the frame, so a frame matching the photo
 * shows the wall undistorted; forcing every photo into the bundled image's ratio
 * would stretch it instead.
 */
data class BoardPhoto(
    val fileName: String,
    val widthPx: Int,
    val heightPx: Int,
    val capturedAt: Long,
) {
    val aspectRatio: Float
        get() = if (widthPx > 0 && heightPx > 0) {
            widthPx.toFloat() / heightPx.toFloat()
        } else {
            BoardGeometry.IMAGE_ASPECT_RATIO
        }
}

data class HoldClassification(
    val zone: BoardZoneType,
    val capability: HoldCapability,
    val overridden: Boolean = false,
)

/**
 * The owner-editable board configuration. The boundary drives classification while
 * editing in Setup, but validation always reads the stored per-hold classifications.
 */
data class BoardSetup(
    val kickboardEnabled: Boolean,
    val kickboardTopY: Float,
    val confirmedAt: Long? = null,
    val classifications: Map<String, HoldClassification> = emptyMap(),
    /**
     * The setter's own photo of this board, or null to render the bundled one.
     * Device-local: the file never travels with the synced board document, so
     * each device keeps whichever photo was taken on it.
     */
    val photo: BoardPhoto? = null,
) {
    companion object {
        fun default(holds: List<HoldDefinition> = BoardDefaults.holds): BoardSetup {
            val base = BoardSetup(
                kickboardEnabled = true,
                kickboardTopY = BoardDefaults.DEFAULT_KICKBOARD_TOP_Y,
            )
            return base.copy(classifications = holds.associate { it.id to base.classify(it.point.y) })
        }
    }

    fun zoneFor(y: Float): BoardZoneType =
        if (kickboardEnabled && y >= kickboardTopY) BoardZoneType.KICKBOARD else BoardZoneType.MAIN

    fun classify(y: Float): HoldClassification {
        val zone = zoneFor(y)
        return HoldClassification(zone = zone, capability = zone.defaultCapability)
    }

    /** Recompute zones from the boundary while keeping manual capability overrides. */
    fun reclassified(holds: List<HoldDefinition> = BoardDefaults.holds): BoardSetup = copy(
        classifications = holds.associate { hold ->
            val zone = zoneFor(hold.point.y)
            val existing = classifications[hold.id]
            hold.id to if (existing != null && existing.overridden) {
                existing.copy(zone = zone)
            } else {
                HoldClassification(zone = zone, capability = zone.defaultCapability)
            }
        },
    )

    fun withKickboardEnabled(enabled: Boolean, holds: List<HoldDefinition> = BoardDefaults.holds): BoardSetup =
        copy(kickboardEnabled = enabled).reclassified(holds)

    fun withBoundary(y: Float, holds: List<HoldDefinition> = BoardDefaults.holds): BoardSetup =
        copy(kickboardTopY = y.coerceIn(0.1f, 1f)).reclassified(holds)

    fun withCapabilityToggled(holdId: String, holds: List<HoldDefinition> = BoardDefaults.holds): BoardSetup {
        val hold = holds.firstOrNull { it.id == holdId } ?: return this
        val zone = zoneFor(hold.point.y)
        val current = classifications[holdId] ?: HoldClassification(zone, zone.defaultCapability)
        val flipped = when (current.capability) {
            HoldCapability.HAND_AND_FOOT -> HoldCapability.FOOT_ONLY
            HoldCapability.FOOT_ONLY -> HoldCapability.HAND_AND_FOOT
        }
        val updated = HoldClassification(
            zone = zone,
            capability = flipped,
            overridden = flipped != zone.defaultCapability,
        )
        return copy(classifications = classifications + (holdId to updated))
    }
}

data class ConfiguredHold(
    val id: String,
    val point: NormalizedPoint,
    val zone: BoardZoneType,
    val capability: HoldCapability,
)

/** Board geometry combined with stored classifications; what validation and UI consume. */
data class ConfiguredBoard(
    val name: String,
    val heightMeters: Float,
    val kickboardEnabled: Boolean,
    val kickboardTopY: Float,
    val setupConfirmedAt: Long?,
    val holds: List<ConfiguredHold>,
    val photo: BoardPhoto? = null,
) {
    val holdsById: Map<String, ConfiguredHold> = holds.associateBy { it.id }

    val hasKickboard: Boolean
        get() = kickboardEnabled

    /** The board frame's shape: the setter's photo when there is one, else the bundled image. */
    val aspectRatio: Float
        get() = photo?.aspectRatio ?: BoardGeometry.IMAGE_ASPECT_RATIO

    companion object {
        fun from(
            setup: BoardSetup,
            holds: List<HoldDefinition> = BoardDefaults.holds,
        ): ConfiguredBoard = ConfiguredBoard(
            name = BoardDefaults.BOARD_NAME,
            heightMeters = BoardDefaults.BOARD_HEIGHT_METERS,
            kickboardEnabled = setup.kickboardEnabled,
            kickboardTopY = setup.kickboardTopY,
            setupConfirmedAt = setup.confirmedAt,
            photo = setup.photo,
            holds = holds.map { hold ->
                val classification = setup.classifications[hold.id] ?: setup.classify(hold.point.y)
                ConfiguredHold(
                    id = hold.id,
                    point = hold.point,
                    zone = classification.zone,
                    capability = classification.capability,
                )
            },
        )
    }
}
