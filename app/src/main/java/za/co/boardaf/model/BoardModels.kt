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
        hold("h01", 16.1f, 5.7f), hold("h02", 42.2f, 6.1f), hold("h03", 62.4f, 6.2f), hold("h04", 83.1f, 6.4f),
        hold("h05", 12.0f, 18.4f), hold("h06", 32.1f, 16.7f), hold("h07", 67.8f, 19.2f), hold("h08", 77.3f, 18.8f), hold("h09", 88.1f, 22.1f),
        hold("h10", 32.0f, 22.8f), hold("h11", 42.1f, 22.1f), hold("h12", 52.0f, 21.8f), hold("h13", 72.4f, 28.0f),
        hold("h14", 26.2f, 31.8f), hold("h15", 62.0f, 31.1f), hold("h16", 42.6f, 34.7f), hold("h17", 71.9f, 34.9f),
        hold("h18", 16.1f, 37.7f), hold("h19", 37.1f, 40.5f), hold("h20", 51.8f, 37.8f), hold("h21", 67.3f, 40.6f), hold("h22", 87.7f, 40.5f),
        hold("h23", 47.5f, 43.4f), hold("h24", 77.7f, 46.1f), hold("h25", 31.8f, 47.1f),
        hold("h26", 12.2f, 54.0f), hold("h27", 52.2f, 53.5f), hold("h28", 88.3f, 55.7f),
        hold("h29", 32.1f, 60.1f), hold("h30", 47.6f, 62.7f), hold("h31", 67.4f, 62.8f),
        hold("h32", 16.3f, 69.5f), hold("h33", 47.7f, 72.5f), hold("h34", 62.0f, 77.3f), hold("h35", 83.9f, 76.2f), hold("h36", 31.8f, 79.2f),
        hold("h37", 27.0f, 88.8f), hold("h38", 45.4f, 88.5f), hold("h39", 72.5f, 88.7f),
        hold("h40", 16.3f, 96.4f), hold("h41", 31.5f, 96.2f), hold("h42", 54.5f, 96.3f), hold("h43", 84.5f, 96.0f),
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

    private fun hold(id: String, xPercent: Float, yPercent: Float) = HoldDefinition(
        id = id,
        point = NormalizedPoint(xPercent / 100f, yPercent / 100f),
    )

    private fun problemHold(id: String, role: HoldRole) = ProblemHold(id, role)
}
