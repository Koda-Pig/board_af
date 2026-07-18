package za.co.boardaf.model

enum class HoldRole(val label: String) {
    START("Start"),
    HAND("Hand"),
    FOOT("Foot"),
    FINISH("Finish"),
}

enum class Accent(val label: String) {
    SKY("Sky"),
    CORAL("Coral"),
    OCHRE("Ochre"),
    MOSS("Moss"),
}

data class HoldDefinition(
    val id: String,
    val point: NormalizedPoint,
)

data class ProblemHold(
    val holdId: String,
    val role: HoldRole,
)

data class Problem(
    val id: String,
    val name: String,
    val grade: String,
    val accent: Accent,
    val setter: String,
    val note: String,
    val holds: List<ProblemHold>,
    val sends: Int = 0,
)

data class Attempt(
    val id: Long,
    val problemId: String,
    val sent: Boolean,
    val durationSeconds: Int,
    val timestamp: Long,
)

data class DraftProblem(
    val name: String = "",
    val grade: String = "V3",
    val accent: Accent = Accent.SKY,
    val note: String = "",
    val holds: List<ProblemHold> = emptyList(),
) {
    val canSave: Boolean
        get() = name.isNotBlank() &&
            holds.size >= 2 &&
            holds.any { it.role == HoldRole.START } &&
            holds.any { it.role == HoldRole.FINISH }
}

object BoardDefaults {
    val grades = (0..8).map { "V$it" }

    val holds = listOf(
        hold("h01", 154f, 81f), hold("h02", 405f, 77f), hold("h03", 598f, 75f), hold("h04", 801f, 74f),
        hold("h05", 114f, 238f), hold("h06", 306f, 209f), hold("h07", 649f, 234f), hold("h08", 751f, 233f), hold("h09", 845f, 280f),
        hold("h10", 305f, 277f), hold("h11", 404f, 278f), hold("h12", 504f, 272f), hold("h13", 697f, 355f),
        hold("h14", 254f, 396f), hold("h15", 595f, 394f), hold("h16", 406f, 435f), hold("h17", 697f, 433f),
        hold("h18", 157f, 477f), hold("h19", 356f, 514f), hold("h20", 502f, 479f), hold("h21", 644f, 511f), hold("h22", 846f, 507f),
        hold("h23", 456f, 550f), hold("h24", 754f, 588f), hold("h25", 308f, 592f),
        hold("h26", 116f, 678f), hold("h27", 502f, 670f), hold("h28", 843f, 705f),
        hold("h29", 306f, 744f), hold("h30", 457f, 785f), hold("h31", 648f, 785f),
        hold("h32", 155f, 866f), hold("h33", 457f, 902f), hold("h34", 600f, 944f), hold("h35", 800f, 941f), hold("h36", 304f, 983f),
        hold("h37", 257f, 1107f), hold("h38", 442f, 1108f), hold("h39", 708f, 1109f),
        hold("h40", 157f, 1204f), hold("h41", 300f, 1205f), hold("h42", 522f, 1208f), hold("h43", 807f, 1208f),
    )

    val problems = listOf(
        Problem(
            id = "tidepool",
            name = "Tidepool",
            grade = "V4",
            accent = Accent.SKY,
            setter = "You",
            note = "Stay square through the middle, then commit to the blue finish.",
            holds = listOf(
                problemHold("h43", HoldRole.START), problemHold("h34", HoldRole.FOOT),
                problemHold("h31", HoldRole.HAND), problemHold("h21", HoldRole.HAND),
                problemHold("h17", HoldRole.FOOT), problemHold("h13", HoldRole.FINISH),
            ),
            sends = 7,
        ),
        Problem(
            id = "moss-line",
            name = "Moss line",
            grade = "V2",
            accent = Accent.MOSS,
            setter = "You",
            note = "A relaxed green warm-up with a long final reach.",
            holds = listOf(
                problemHold("h37", HoldRole.START), problemHold("h27", HoldRole.HAND),
                problemHold("h20", HoldRole.FOOT), problemHold("h16", HoldRole.HAND),
                problemHold("h06", HoldRole.FINISH),
            ),
            sends = 12,
        ),
        Problem(
            id = "chalk-ghost",
            name = "Chalk ghost",
            grade = "V6",
            accent = Accent.CORAL,
            setter = "Maya",
            note = "Compression on the left panel. The h14 catch is the whole game.",
            holds = listOf(
                problemHold("h40", HoldRole.START), problemHold("h29", HoldRole.HAND),
                problemHold("h25", HoldRole.FOOT), problemHold("h19", HoldRole.HAND),
                problemHold("h14", HoldRole.HAND), problemHold("h05", HoldRole.HAND),
                problemHold("h01", HoldRole.FINISH),
            ),
            sends = 2,
        ),
        Problem(
            id = "golden-hour",
            name = "Golden hour",
            grade = "V3",
            accent = Accent.OCHRE,
            setter = "Jono",
            note = "Use the timber rail as a sidepull and keep your hips in.",
            holds = listOf(
                problemHold("h34", HoldRole.START), problemHold("h35", HoldRole.FOOT),
                problemHold("h24", HoldRole.HAND), problemHold("h23", HoldRole.HAND),
                problemHold("h10", HoldRole.HAND), problemHold("h04", HoldRole.FINISH),
            ),
            sends = 9,
        ),
    )

    private fun hold(id: String, sourceX: Float, sourceY: Float) = HoldDefinition(
        id = id,
        point = NormalizedPoint(
            x = sourceX / SOURCE_IMAGE_WIDTH,
            y = sourceY / SOURCE_IMAGE_HEIGHT,
        ),
    )

    private fun problemHold(id: String, role: HoldRole) = ProblemHold(id, role)

    private const val SOURCE_IMAGE_WIDTH = 960f
    private const val SOURCE_IMAGE_HEIGHT = 1280f
}
