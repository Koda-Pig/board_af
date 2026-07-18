package za.co.boardaf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.ConfiguredBoard
import za.co.boardaf.model.FeetRule
import za.co.boardaf.model.FinishRule
import za.co.boardaf.model.IssueCode
import za.co.boardaf.model.IssueSeverity
import za.co.boardaf.model.ProblemAssignment
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.model.ProblemIssue
import za.co.boardaf.model.ProblemSpec
import za.co.boardaf.model.ProblemValidator
import za.co.boardaf.model.PublicationState
import za.co.boardaf.model.StartRule
import za.co.boardaf.model.finishRuleFor
import za.co.boardaf.model.startRuleFor

class ProblemValidatorTest {

    private val board = ConfiguredBoard.from(BoardSetup.default())
    private val noKickboard = ConfiguredBoard.from(BoardSetup.default().withKickboardEnabled(false))

    private fun spec(
        name: String = "Test problem",
        feetRule: FeetRule = FeetRule.MARKED_ONLY,
        assignments: List<ProblemAssignment>,
        startRule: StartRule = startRuleFor(assignments),
        finishRule: FinishRule = finishRuleFor(assignments),
    ) = ProblemSpec(name, feetRule, startRule, finishRule, assignments)

    private fun assign(id: String, role: ProblemHoldRole) = ProblemAssignment(id, role)

    private val validAssignments = listOf(
        assign("h34", ProblemHoldRole.START),
        assign("h27", ProblemHoldRole.REGULAR),
        assign("h35", ProblemHoldRole.FOOT_ONLY),
        assign("h04", ProblemHoldRole.FINISH),
    )

    private fun codes(spec: ProblemSpec, on: ConfiguredBoard = board) =
        ProblemValidator.validate(spec, on).map { it.code }

    @Test
    fun `a correct problem validates without issues`() {
        assertTrue(codes(spec(assignments = validAssignments)).isEmpty())
    }

    @Test
    fun `a blank name is an error`() {
        assertTrue(IssueCode.NAME_MISSING in codes(spec(name = " ", assignments = validAssignments)))
    }

    @Test
    fun `missing and excess starts are errors`() {
        val noStart = validAssignments.filterNot { it.role == ProblemHoldRole.START }
        assertTrue(IssueCode.NO_START in codes(spec(assignments = noStart)))

        val threeStarts = validAssignments + listOf(
            assign("h10", ProblemHoldRole.START),
            assign("h11", ProblemHoldRole.START),
        )
        assertTrue(IssueCode.TOO_MANY_STARTS in codes(spec(assignments = threeStarts)))
    }

    @Test
    fun `missing and excess finishes are errors`() {
        val noFinish = validAssignments.filterNot { it.role == ProblemHoldRole.FINISH }
        assertTrue(IssueCode.NO_FINISH in codes(spec(assignments = noFinish)))

        val threeFinishes = validAssignments + listOf(
            assign("h02", ProblemHoldRole.FINISH),
            assign("h03", ProblemHoldRole.FINISH),
        )
        assertTrue(IssueCode.TOO_MANY_FINISHES in codes(spec(assignments = threeFinishes)))
    }

    @Test
    fun `two starts and two finishes are valid with matching rules`() {
        val twoAndTwo = listOf(
            assign("h33", ProblemHoldRole.START),
            assign("h34", ProblemHoldRole.START),
            assign("h20", ProblemHoldRole.REGULAR),
            assign("h03", ProblemHoldRole.FINISH),
            assign("h04", ProblemHoldRole.FINISH),
        )
        val result = spec(assignments = twoAndTwo)
        assertEquals(StartRule.SPLIT_TWO, result.startRule)
        assertEquals(FinishRule.CONTROL_TWO, result.finishRule)
        assertTrue(codes(result).isEmpty())
    }

    @Test
    fun `a stored rule that disagrees with the marked count is an error`() {
        val issues = codes(
            spec(assignments = validAssignments, startRule = StartRule.SPLIT_TWO),
        )
        assertTrue(IssueCode.START_RULE_MISMATCH in issues)

        val finishIssues = codes(
            spec(assignments = validAssignments, finishRule = FinishRule.CONTROL_TWO),
        )
        assertTrue(IssueCode.FINISH_RULE_MISMATCH in finishIssues)
    }

    @Test
    fun `kickboard starts and finishes cannot validate as publishable`() {
        val kickerStart = listOf(
            assign("h43", ProblemHoldRole.START),
            assign("h20", ProblemHoldRole.REGULAR),
            assign("h04", ProblemHoldRole.FINISH),
        )
        val issues = ProblemValidator.validate(spec(assignments = kickerStart), board)
        val issueCodes = issues.map { it.code }
        assertTrue(IssueCode.START_IN_KICKBOARD in issueCodes)
        assertTrue(IssueCode.START_NOT_HAND_CAPABLE in issueCodes)
        assertTrue(ProblemValidator.hasErrors(issues))
        assertFalse(ProblemValidator.canPublish(issues, forerunConfirmed = true))

        val kickerFinish = listOf(
            assign("h10", ProblemHoldRole.START),
            assign("h41", ProblemHoldRole.FINISH),
        )
        val finishCodes = codes(spec(assignments = kickerFinish))
        assertTrue(IssueCode.FINISH_IN_KICKBOARD in finishCodes)
        assertTrue(IssueCode.FINISH_NOT_HAND_CAPABLE in finishCodes)
    }

    @Test
    fun `start issues carry the offending hold id`() {
        val kickerStart = listOf(
            assign("h43", ProblemHoldRole.START),
            assign("h04", ProblemHoldRole.FINISH),
        )
        val issue = ProblemValidator.validate(spec(assignments = kickerStart), board)
            .first { it.code == IssueCode.START_IN_KICKBOARD }
        assertEquals("h43", issue.holdId)
    }

    @Test
    fun `a hold can only carry one role`() {
        val duplicated = validAssignments + assign("h27", ProblemHoldRole.FOOT_ONLY)
        assertTrue(IssueCode.DUPLICATE_ASSIGNMENT in codes(spec(assignments = duplicated)))
    }

    @Test
    fun `regular roles require hand capability`() {
        val kickerRegular = validAssignments + assign("h38", ProblemHoldRole.REGULAR)
        assertTrue(IssueCode.REGULAR_NOT_HAND_CAPABLE in codes(spec(assignments = kickerRegular)))
    }

    @Test
    fun `capability overrides feed validation even inside the main zone`() {
        val tamperedSetup = BoardSetup.default().withCapabilityToggled("h20")
        // h20 is now foot-only in the main zone; a start there must fail.
        val tamperedBoard = ConfiguredBoard.from(tamperedSetup)
        val issues = codes(
            spec(
                assignments = listOf(
                    assign("h20", ProblemHoldRole.START),
                    assign("h04", ProblemHoldRole.FINISH),
                ),
            ),
            on = tamperedBoard,
        )
        assertTrue(IssueCode.START_NOT_HAND_CAPABLE in issues)
        assertFalse(IssueCode.START_IN_KICKBOARD in issues)
    }

    @Test
    fun `a hand-capable kickboard hold still cannot host a start`() {
        val correctedKicker = ConfiguredBoard.from(BoardSetup.default().withCapabilityToggled("h43"))
        val issues = codes(
            spec(
                assignments = listOf(
                    assign("h43", ProblemHoldRole.START),
                    assign("h04", ProblemHoldRole.FINISH),
                ),
            ),
            on = correctedKicker,
        )
        assertTrue(IssueCode.START_IN_KICKBOARD in issues)
        assertFalse(IssueCode.START_NOT_HAND_CAPABLE in issues)
    }

    @Test
    fun `campus problems cannot contain foot-only assignments`() {
        val issues = ProblemValidator.validate(
            spec(feetRule = FeetRule.CAMPUS, assignments = validAssignments),
            board,
        )
        val campus = issues.filter { it.code == IssueCode.CAMPUS_WITH_FOOT_MARKS }
        assertEquals(1, campus.size)
        assertEquals("h35", campus.single().holdId)
        assertEquals(IssueSeverity.ERROR, campus.single().severity)
    }

    @Test
    fun `open kickboard requires a kickboard`() {
        val open = spec(feetRule = FeetRule.OPEN_KICKBOARD, assignments = validAssignments)
        assertFalse(IssueCode.OPEN_KICKBOARD_WITHOUT_KICKBOARD in codes(open))
        assertTrue(IssueCode.OPEN_KICKBOARD_WITHOUT_KICKBOARD in codes(open, on = noKickboard))
    }

    @Test
    fun `redundant foot marks under permissive rules are warnings not errors`() {
        val anyFeet = ProblemValidator.validate(
            spec(feetRule = FeetRule.ANY_FEET, assignments = validAssignments),
            board,
        )
        assertTrue(anyFeet.any { it.code == IssueCode.FOOT_MARKS_IGNORED_BY_RULE })
        assertFalse(ProblemValidator.hasErrors(anyFeet))

        val kickerFootMark = validAssignments + assign("h39", ProblemHoldRole.FOOT_ONLY)
        val openKicker = ProblemValidator.validate(
            spec(feetRule = FeetRule.OPEN_KICKBOARD, assignments = kickerFootMark),
            board,
        )
        assertTrue(openKicker.any { it.code == IssueCode.KICKBOARD_FOOT_REDUNDANT })
        assertFalse(ProblemValidator.hasErrors(openKicker))
    }

    @Test
    fun `assignments to unknown holds are errors`() {
        val unknown = validAssignments + assign("h99", ProblemHoldRole.REGULAR)
        assertTrue(IssueCode.UNKNOWN_HOLD in codes(spec(assignments = unknown)))
    }

    @Test
    fun `publishing requires zero errors plus a confirmed forerun`() {
        val issues = ProblemValidator.validate(spec(assignments = validAssignments), board)
        assertTrue(ProblemValidator.canPublish(issues, forerunConfirmed = true))
        assertFalse(ProblemValidator.canPublish(issues, forerunConfirmed = false))
    }

    @Test
    fun `resolveState demotes published work with errors and keeps review sticky`() {
        val errorIssues = ProblemValidator.validate(
            spec(assignments = listOf(assign("h43", ProblemHoldRole.START))),
            board,
        )
        val clean = emptyList<ProblemIssue>()

        assertEquals(
            PublicationState.NEEDS_REVIEW,
            ProblemValidator.resolveState(PublicationState.PUBLISHED, errorIssues),
        )
        assertEquals(
            PublicationState.NEEDS_REVIEW,
            ProblemValidator.resolveState(PublicationState.BENCHMARK, errorIssues),
        )
        assertEquals(
            PublicationState.DRAFT,
            ProblemValidator.resolveState(PublicationState.DRAFT, errorIssues),
        )
        assertEquals(
            PublicationState.ARCHIVED,
            ProblemValidator.resolveState(PublicationState.ARCHIVED, errorIssues),
        )
        assertEquals(
            PublicationState.NEEDS_REVIEW,
            ProblemValidator.resolveState(PublicationState.NEEDS_REVIEW, clean),
        )
    }
}
