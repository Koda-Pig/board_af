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
    const val BOARD_ANGLE_DEGREES = 20
    const val BOARD_HEIGHT_METERS = 4.8f

    /** Midway between the h36 row (y=983) and the h37 kicker row (y=1107) in source pixels. */
    val DEFAULT_KICKBOARD_TOP_Y = 1045f / SOURCE_IMAGE_HEIGHT

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
                    assign("h43", ProblemHoldRole.START), assign("h34", ProblemHoldRole.FOOT_ONLY),
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
                    assign("h37", ProblemHoldRole.START), assign("h27", ProblemHoldRole.REGULAR),
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
                    assign("h40", ProblemHoldRole.START), assign("h29", ProblemHoldRole.REGULAR),
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

    private const val SOURCE_IMAGE_WIDTH = 960f
    private const val SOURCE_IMAGE_HEIGHT = 1280f
}
