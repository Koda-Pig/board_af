package za.co.boardaf.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import za.co.boardaf.model.Accent
import za.co.boardaf.model.BoulderGrade
import za.co.boardaf.model.ConfiguredBoard
import za.co.boardaf.model.FeetRule
import za.co.boardaf.model.Problem
import za.co.boardaf.model.ProblemAssignment
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.model.ProblemValidator
import za.co.boardaf.model.PublicationState
import za.co.boardaf.model.finishRuleFor
import za.co.boardaf.model.startRuleFor

/**
 * Reads the unversioned `problems_v1` JSON array. Records decode independently, all
 * assignments are preserved verbatim, and problems whose legacy data breaks a rule
 * (for example a start on a kickboard hold) surface as NEEDS_REVIEW instead of
 * being repaired or dropped silently.
 */
object LegacyProblemMigration {

    data class Result(
        val problems: List<Problem>,
        val unreadable: List<UnreadableRecord>,
        val payloadCorrupt: Boolean = false,
    )

    fun migrate(rawV1: String, board: ConfiguredBoard): Result {
        val elements = runCatching { Json.parseToJsonElement(rawV1).jsonArray }
            .getOrElse { failure ->
                return Result(
                    problems = emptyList(),
                    unreadable = listOf(
                        UnreadableRecord(
                            raw = rawV1,
                            error = "Legacy library is not a JSON array: ${failure.message}",
                            source = UnreadableRecord.SOURCE_V1,
                        ),
                    ),
                    payloadCorrupt = true,
                )
            }

        val problems = mutableListOf<Problem>()
        val unreadable = mutableListOf<UnreadableRecord>()
        elements.forEach { element ->
            runCatching { decodeLegacyProblem(element.toString(), board) }
                .onSuccess { problems += it }
                .onFailure { failure ->
                    unreadable += UnreadableRecord(
                        raw = element.toString(),
                        error = failure.message ?: failure.toString(),
                        source = UnreadableRecord.SOURCE_V1,
                    )
                }
        }
        return Result(problems = problems, unreadable = unreadable)
    }

    private fun decodeLegacyProblem(raw: String, board: ConfiguredBoard): Problem {
        val json = Json.parseToJsonElement(raw).jsonObject
        val gradeRaw = json["grade"]?.jsonPrimitive?.content ?: error("Missing field: grade")
        val grade = BoulderGrade.fromPersistedOrNull(gradeRaw) ?: error("Unknown grade: $gradeRaw")
        val accentRaw = json["accent"]?.jsonPrimitive?.content ?: error("Missing field: accent")
        val accent = Accent.entries.firstOrNull { it.name == accentRaw }
            ?: error("Unknown accent: $accentRaw")

        val assignments = (json["holds"] ?: error("Missing field: holds")).jsonArray.map { element ->
            val hold = element.jsonObject
            ProblemAssignment(
                holdId = hold["id"]?.jsonPrimitive?.content ?: error("Missing hold id"),
                role = legacyRole(hold["role"]?.jsonPrimitive?.content ?: error("Missing hold role")),
            )
        }

        val problem = Problem(
            id = json["id"]?.jsonPrimitive?.content ?: error("Missing field: id"),
            name = json["name"]?.jsonPrimitive?.content ?: error("Missing field: name"),
            grade = grade,
            accent = accent,
            setter = json["setter"]?.jsonPrimitive?.content.orEmpty(),
            note = json["note"]?.jsonPrimitive?.content.orEmpty(),
            feetRule = FeetRule.MARKED_ONLY,
            startRule = startRuleFor(assignments),
            finishRule = finishRuleFor(assignments),
            publicationState = PublicationState.PUBLISHED,
            assignments = assignments,
        )
        val issues = ProblemValidator.validate(problem, board)
        return problem.copy(
            publicationState = ProblemValidator.resolveState(problem.publicationState, issues),
        )
    }

    private fun legacyRole(value: String): ProblemHoldRole = when (value) {
        "START" -> ProblemHoldRole.START
        "HAND", "REGULAR" -> ProblemHoldRole.REGULAR
        "FOOT", "FOOT_ONLY" -> ProblemHoldRole.FOOT_ONLY
        "FINISH" -> ProblemHoldRole.FINISH
        else -> error("Unknown hold role: $value")
    }
}
