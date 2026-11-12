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
    /**
     * The setter's own hold map, or null to track the one bundled with the app.
     * Null is deliberately not the same as a copy of [BoardDefaults.holds]: it is
     * what lets a later release ship a corrected bundled map to boards that never
     * re-mapped, and what "Use the bundled layout" returns to.
     *
     * Unlike [photo] this *does* travel with the synced board document. Hold ids
     * are what problems store, so two devices disagreeing about which holds exist
     * would demote published problems to Needs review on whichever synced second —
     * a worse outcome than coordinates that sit slightly off a photo taken from a
     * different spot, which the setter can re-drag.
     */
    val layout: List<HoldDefinition>? = null,
    /**
     * The number the next added hold will take. Persisted rather than derived from
     * [holds] so that removing the highest-numbered hold and adding another cannot
     * hand out its id again: problems reference holds by id, so a recycled id would
     * quietly re-point an old problem at a different hold instead of flagging the
     * old hold as missing.
     */
    val nextHoldNumber: Int? = null,
) {
    /** The holds this board actually has: the setter's own map when there is one. */
    val holds: List<HoldDefinition>
        get() = layout ?: BoardDefaults.holds

    /** True once the setter has re-mapped the holds themselves. */
    val hasCustomLayout: Boolean
        get() = layout != null

    companion object {
        fun default(holds: List<HoldDefinition> = BoardDefaults.holds): BoardSetup = BoardSetup(
            kickboardEnabled = true,
            kickboardTopY = BoardDefaults.DEFAULT_KICKBOARD_TOP_Y,
        ).withHolds(holds)

        /**
         * How far a marker may snap onto a suggestion, in frame-width units —
         * roughly half the spacing between neighbouring holds, so a marker can
         * never jump onto the hold next door.
         */
        const val SNAP_RADIUS = 0.07f

        internal fun holdId(number: Int): String = "h%02d".format(number)

        private fun holdNumber(id: String): Int? =
            id.removePrefix("h").takeIf { it.isNotEmpty() }?.toIntOrNull()
    }

    fun zoneFor(y: Float): BoardZoneType =
        if (kickboardEnabled && y >= kickboardTopY) BoardZoneType.KICKBOARD else BoardZoneType.MAIN

    fun classify(y: Float): HoldClassification {
        val zone = zoneFor(y)
        return HoldClassification(zone = zone, capability = zone.defaultCapability)
    }

    /**
     * Rebuild classifications from the current [holds] and boundary, keeping manual
     * capability corrections. Holds that are gone stop carrying a classification,
     * and a hold that moved across the boundary changes zone.
     */
    fun reclassified(): BoardSetup = copy(
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

    fun withKickboardEnabled(enabled: Boolean): BoardSetup =
        copy(kickboardEnabled = enabled).reclassified()

    fun withBoundary(y: Float): BoardSetup =
        copy(kickboardTopY = y.coerceIn(0.1f, 1f)).reclassified()

    fun withCapabilityToggled(holdId: String): BoardSetup {
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

    /** Adopt [holds] as this board's hold map and rebuild classifications from it. */
    fun withHolds(holds: List<HoldDefinition>): BoardSetup = copy(
        // A layout identical to the bundled one is stored as null rather than as a
        // copy: pinning the board to today's bundled map would only freeze out a
        // corrected map in a later release.
        layout = holds.takeIf { it != BoardDefaults.holds },
        // Pin the id counter at the high-water mark of the holds on either side of
        // this change, before the new map takes effect. Deriving it later instead
        // would let removing the highest-numbered hold lower the mark, and the next
        // added hold would inherit the removed hold's id — silently re-pointing
        // every problem that still referenced it.
        nextHoldNumber = maxOf(
            nextHoldNumber ?: 0,
            ((this.holds + holds).mapNotNull { holdNumber(it.id) }.maxOrNull() ?: 0) + 1,
        ),
    ).reclassified()

    /** Move one hold's center. [point] is in normalized photo coordinates. */
    fun withHoldMoved(holdId: String, point: NormalizedPoint): BoardSetup {
        if (holds.none { it.id == holdId }) return this
        return withHolds(
            holds.map { if (it.id == holdId) it.copy(point = point.clampedToFrame()) else it },
        )
    }

    /** Add a hold at [point]; it lands last in [holds] and takes the next free id. */
    fun withHoldAdded(point: NormalizedPoint): BoardSetup {
        val number = freeHoldNumber()
        return copy(nextHoldNumber = number + 1)
            .withHolds(holds + HoldDefinition(holdId(number), point.clampedToFrame()))
    }

    fun withHoldRemoved(holdId: String): BoardSetup {
        val remaining = holds.filterNot { it.id == holdId }
        return if (remaining.size == holds.size) this else withHolds(remaining)
    }

    /** Back to the hold map bundled with the app, keeping the id counter intact. */
    fun withBundledHolds(): BoardSetup = withHolds(BoardDefaults.holds)

    /**
     * Snap this board's markers onto [detected] — points a detector suggested from
     * the board photo — and describe what that would do.
     *
     * Three rules make a suggestion safe to accept:
     *  - a marker that matches a suggestion **moves**, keeping its id, so every
     *    problem that references it still points at the same physical hold;
     *  - a suggestion with no marker near it becomes a **new** hold;
     *  - a marker with no suggestion near it is **left alone**, never removed. The
     *    detector failing to see a hold is not evidence the hold is gone, and
     *    deleting it would break every problem that used it.
     *
     * [aspectRatio] is width over height: normalized y has to be scaled by it or
     * the snap radius would be a tall ellipse on a portrait board.
     */
    fun withSuggestedHolds(
        detected: List<NormalizedPoint>,
        aspectRatio: Float,
        snapRadius: Float = SNAP_RADIUS,
    ): HoldSuggestion {
        val current = holds
        val pairs = snapPairs(current, detected, aspectRatio, snapRadius)
        val snapped = current.mapNotNull { hold -> pairs[hold.id]?.let { hold.id } }.toSet()
        var updated = withHolds(
            current.map { hold ->
                val index = pairs[hold.id]
                if (index == null) hold else hold.copy(point = detected[index].clampedToFrame())
            },
        )
        val claimed = pairs.values.toSet()
        val leftovers = detected.filterIndexed { index, _ -> index !in claimed }
        leftovers.forEach { updated = updated.withHoldAdded(it) }
        return HoldSuggestion(
            setup = updated,
            snapped = snapped.size,
            added = leftovers.size,
            unmatched = current.size - snapped.size,
        )
    }

    /**
     * Nearest-first one-to-one matching between markers and suggestions. Greedy
     * rather than optimal: with a radius under half a hold's spacing the two agree,
     * and greedy is deterministic, which keeps a re-run on the same photo stable.
     */
    private fun snapPairs(
        current: List<HoldDefinition>,
        detected: List<NormalizedPoint>,
        aspectRatio: Float,
        snapRadius: Float,
    ): Map<String, Int> {
        val aspect = if (aspectRatio > 0f) aspectRatio else 1f
        val limit = snapRadius * snapRadius
        val candidates = mutableListOf<Triple<Float, String, Int>>()
        current.forEach { hold ->
            detected.forEachIndexed { index, point ->
                val dx = point.x - hold.point.x
                val dy = (point.y - hold.point.y) / aspect
                val distance = dx * dx + dy * dy
                if (distance <= limit) candidates += Triple(distance, hold.id, index)
            }
        }
        candidates.sortBy { it.first }
        val takenHolds = mutableSetOf<String>()
        val takenPoints = mutableSetOf<Int>()
        val pairs = mutableMapOf<String, Int>()
        candidates.forEach { (_, id, index) ->
            if (id in takenHolds || index in takenPoints) return@forEach
            takenHolds += id
            takenPoints += index
            pairs[id] = index
        }
        return pairs
    }

    private fun freeHoldNumber(): Int {
        val taken = holds.mapTo(mutableSetOf()) { it.id }
        // Deriving is only the fallback for a setup stored before the counter
        // existed; from then on the persisted counter is what hands out ids.
        var number = nextHoldNumber
            ?: ((holds.mapNotNull { holdNumber(it.id) }.maxOrNull() ?: 0) + 1)
        while (holdId(number) in taken) number++
        return number
    }

    private fun NormalizedPoint.clampedToFrame() =
        NormalizedPoint(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f))
}

/** What accepting a round of detector suggestions would do to the board. */
data class HoldSuggestion(
    val setup: BoardSetup,
    /** Markers that moved onto a suggestion, keeping their id. */
    val snapped: Int,
    /** Suggestions with no marker near them, added as new holds. */
    val added: Int,
    /** Markers the detector found nothing for; these are left exactly where they are. */
    val unmatched: Int,
) {
    val changesAnything: Boolean
        get() = snapped > 0 || added > 0
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
            holds: List<HoldDefinition> = setup.holds,
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
