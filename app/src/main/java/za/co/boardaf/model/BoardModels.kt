package za.co.boardaf.model

enum class Accent(val label: String) {
    SKY("Sky"),
    CORAL("Coral"),
    OCHRE("Ochre"),
    MOSS("Moss"),
}

enum class GradeSystem(val label: String) {
    FRENCH("French (Font)"),
    V_SCALE("V scale"),
}

enum class BoulderGrade(
    val frenchLabel: String,
    val vLabel: String,
) {
    F4("4", "V0"),
    F5("5", "V1"),
    F5_PLUS("5+", "V2"),
    F6A("6A", "V3"),
    F6A_PLUS("6A+", "V3"),
    F6B("6B", "V4"),
    F6B_PLUS("6B+", "V4"),
    F6C("6C", "V5"),
    F6C_PLUS("6C+", "V5"),
    F7A("7A", "V6"),
    F7A_PLUS("7A+", "V7"),
    F7B("7B", "V8"),
    F7B_PLUS("7B+", "V8"),
    ;

    fun label(system: GradeSystem): String = when (system) {
        GradeSystem.FRENCH -> frenchLabel
        GradeSystem.V_SCALE -> vLabel
    }

    companion object {
        fun options(system: GradeSystem): List<BoulderGrade> = when (system) {
            GradeSystem.FRENCH -> entries
            GradeSystem.V_SCALE -> entries.distinctBy { it.vLabel }
        }

        fun fromPersistedOrNull(value: String): BoulderGrade? = entries.firstOrNull { grade ->
            grade.name.equals(value, ignoreCase = true) ||
                grade.frenchLabel.equals(value, ignoreCase = true) ||
                grade.vLabel.equals(value, ignoreCase = true)
        }

        fun fromPersisted(value: String): BoulderGrade = fromPersistedOrNull(value) ?: F6A
    }
}

data class HoldDefinition(
    val id: String,
    val point: NormalizedPoint,
)

object BoardDefaults {
    const val BOARD_NAME = "Home board"
    // No board-level angle: the wall adjusts 0–90° and each problem records its own.
    const val BOARD_HEIGHT_METERS = 4.8f

    /** Midway between the h36 row (y=1248) and the h37 kicker row (y=1411) in source pixels. */
    val DEFAULT_KICKBOARD_TOP_Y = 1329f / SOURCE_IMAGE_HEIGHT

    val holds = listOf(
        hold("h01", 107f, 42f), hold("h02", 444f, 38f), hold("h03", 703f, 36f), hold("h04", 974f, 36f),
        hold("h05", 54f, 254f), hold("h06", 311f, 215f), hold("h07", 770f, 251f), hold("h08", 906f, 250f), hold("h09", 1031f, 313f),
        hold("h10", 310f, 307f), hold("h11", 442f, 309f), hold("h12", 576f, 301f), hold("h13", 834f, 413f),
        hold("h14", 242f, 466f), hold("h15", 697f, 465f), hold("h16", 445f, 519f), hold("h17", 833f, 517f),
        hold("h18", 112f, 575f), hold("h19", 378f, 625f), hold("h20", 573f, 578f), hold("h21", 762f, 621f), hold("h22", 1031f, 616f),
        hold("h23", 512f, 673f), hold("h24", 908f, 724f), hold("h25", 314f, 729f),
        hold("h26", 58f, 843f), hold("h27", 573f, 833f), hold("h28", 1026f, 880f),
        hold("h29", 312f, 931f), hold("h30", 513f, 985f), hold("h31", 767f, 986f),
        hold("h32", 111f, 1093f), hold("h33", 513f, 1140f), hold("h34", 900f, 984f), hold("h35", 967f, 1192f), hold("h36", 309f, 1248f),
        hold("h37", 247f, 1411f), hold("h38", 493f, 1412f), hold("h39", 844f, 1413f),
        hold("h40", 115f, 1539f), hold("h41", 304f, 1540f), hold("h42", 598f, 1544f), hold("h43", 974f, 1543f),
    )

    val problems: List<Problem> = run {
        val board = ConfiguredBoard.from(BoardSetup.default(holds), holds)
        listOf(
            seed(
                id = "tidepool",
                name = "Tidepool",
                grade = BoulderGrade.F6B,
                accent = Accent.SKY,
                setter = "You",
                note = "Stay square through the middle, then commit to the blue finish.",
                assignments = listOf(
                    assign("h35", ProblemHoldRole.START), assign("h34", ProblemHoldRole.FOOT_ONLY),
                    assign("h31", ProblemHoldRole.REGULAR), assign("h21", ProblemHoldRole.REGULAR),
                    assign("h17", ProblemHoldRole.FOOT_ONLY), assign("h13", ProblemHoldRole.FINISH),
                ),
            ),
            seed(
                id = "moss-line",
                name = "Moss line",
                grade = BoulderGrade.F5_PLUS,
                accent = Accent.MOSS,
                setter = "You",
                note = "A relaxed green warm-up with a long final reach.",
                assignments = listOf(
                    assign("h36", ProblemHoldRole.START), assign("h27", ProblemHoldRole.REGULAR),
                    assign("h20", ProblemHoldRole.FOOT_ONLY), assign("h16", ProblemHoldRole.REGULAR),
                    assign("h06", ProblemHoldRole.FINISH),
                ),
            ),
            seed(
                id = "chalk-ghost",
                name = "Chalk ghost",
                grade = BoulderGrade.F7A,
                accent = Accent.CORAL,
                setter = "Maya",
                note = "Compression on the left panel. The h14 catch is the whole game.",
                assignments = listOf(
                    assign("h32", ProblemHoldRole.START), assign("h29", ProblemHoldRole.REGULAR),
                    assign("h25", ProblemHoldRole.FOOT_ONLY), assign("h19", ProblemHoldRole.REGULAR),
                    assign("h14", ProblemHoldRole.REGULAR), assign("h05", ProblemHoldRole.REGULAR),
                    assign("h01", ProblemHoldRole.FINISH),
                ),
            ),
            seed(
                id = "golden-hour",
                name = "Golden hour",
                grade = BoulderGrade.F6A,
                accent = Accent.OCHRE,
                setter = "Jono",
                note = "Use the timber rail as a sidepull and keep your hips in.",
                assignments = listOf(
                    assign("h34", ProblemHoldRole.START), assign("h35", ProblemHoldRole.FOOT_ONLY),
                    assign("h24", ProblemHoldRole.REGULAR), assign("h23", ProblemHoldRole.REGULAR),
                    assign("h10", ProblemHoldRole.REGULAR), assign("h04", ProblemHoldRole.FINISH),
                ),
            ),
        ).map { problem ->
            val issues = ProblemValidator.validate(problem, board)
            problem.copy(publicationState = ProblemValidator.resolveState(problem.publicationState, issues))
        }
    }

    private fun seed(
        id: String,
        name: String,
        grade: BoulderGrade,
        accent: Accent,
        setter: String,
        note: String,
        assignments: List<ProblemAssignment>,
    ) = Problem(
        id = id,
        name = name,
        grade = grade,
        accent = accent,
        setter = setter,
        note = note,
        feetRule = FeetRule.MARKED_ONLY,
        startRule = startRuleFor(assignments),
        finishRule = finishRuleFor(assignments),
        publicationState = PublicationState.PUBLISHED,
        assignments = assignments,
    )

    private fun hold(id: String, sourceX: Float, sourceY: Float) = HoldDefinition(
        id = id,
        point = NormalizedPoint(
            x = sourceX / SOURCE_IMAGE_WIDTH,
            y = sourceY / SOURCE_IMAGE_HEIGHT,
        ),
    )

    private fun assign(id: String, role: ProblemHoldRole) = ProblemAssignment(id, role)

    private const val SOURCE_IMAGE_WIDTH = 1080f
    private const val SOURCE_IMAGE_HEIGHT = 1586f
}
