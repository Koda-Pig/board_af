package za.co.boardaf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.boardaf.data.LegacyProblemMigration
import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.BoulderGrade
import za.co.boardaf.model.ConfiguredBoard
import za.co.boardaf.model.FeetRule
import za.co.boardaf.model.FinishRule
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.model.PublicationState
import za.co.boardaf.model.StartRule

class LegacyProblemMigrationTest {

    private val board = ConfiguredBoard.from(BoardSetup.default())

    /** A faithful v1 payload as the retired SharedPreferences repository wrote it. */
    private val realV1Payload = """
        [
          {"id":"tidepool","name":"Tidepool","grade":"F6B","accent":"SKY","setter":"You",
           "note":"Stay square through the middle, then commit to the blue finish.",
           "holds":[{"id":"h43","role":"START"},{"id":"h34","role":"FOOT"},{"id":"h31","role":"HAND"},
                    {"id":"h21","role":"HAND"},{"id":"h17","role":"FOOT"},{"id":"h13","role":"FINISH"}]},
          {"id":"moss-line","name":"Moss line","grade":"V2","accent":"MOSS","setter":"You",
           "note":"A relaxed green warm-up with a long final reach.",
           "holds":[{"id":"h37","role":"START"},{"id":"h27","role":"HAND"},{"id":"h20","role":"FOOT"},
                    {"id":"h16","role":"HAND"},{"id":"h06","role":"FINISH"}]},
          {"id":"chalk-ghost","name":"Chalk ghost","grade":"7A","accent":"CORAL","setter":"Maya",
           "note":"Compression on the left panel. The h14 catch is the whole game.",
           "holds":[{"id":"h40","role":"START"},{"id":"h29","role":"HAND"},{"id":"h25","role":"FOOT"},
                    {"id":"h19","role":"HAND"},{"id":"h14","role":"HAND"},{"id":"h05","role":"HAND"},
                    {"id":"h01","role":"FINISH"}]},
          {"id":"golden-hour","name":"Golden hour","grade":"F6A","accent":"OCHRE","setter":"Jono",
           "note":"Use the timber rail as a sidepull and keep your hips in.",
           "holds":[{"id":"h34","role":"START"},{"id":"h35","role":"FOOT"},{"id":"h24","role":"HAND"},
                    {"id":"h23","role":"HAND"},{"id":"h10","role":"HAND"},{"id":"h04","role":"FINISH"}]}
        ]
    """.trimIndent()

    @Test
    fun `a real v1 payload loads with the same problems and assignments`() {
        val result = LegacyProblemMigration.migrate(realV1Payload, board)

        assertFalse(result.payloadCorrupt)
        assertTrue(result.unreadable.isEmpty())
        assertEquals(4, result.problems.size)
        assertEquals(
            listOf("tidepool", "moss-line", "chalk-ghost", "golden-hour"),
            result.problems.map { it.id },
        )
        assertEquals(listOf(6, 5, 7, 6), result.problems.map { it.assignments.size })

        val tidepool = result.problems.first { it.id == "tidepool" }
        assertEquals("You", tidepool.setter)
        assertEquals("Stay square through the middle, then commit to the blue finish.", tidepool.note)
    }

    @Test
    fun `legacy roles map to the new problem roles`() {
        val goldenHour = LegacyProblemMigration.migrate(realV1Payload, board)
            .problems.first { it.id == "golden-hour" }

        assertEquals(
            ProblemHoldRole.FOOT_ONLY,
            goldenHour.assignments.first { it.holdId == "h35" }.role,
        )
        assertEquals(
            ProblemHoldRole.REGULAR,
            goldenHour.assignments.first { it.holdId == "h24" }.role,
        )
        assertEquals(
            ProblemHoldRole.START,
            goldenHour.assignments.first { it.holdId == "h34" }.role,
        )
    }

    @Test
    fun `legacy V grades and canonical names resolve to the same grades`() {
        val problems = LegacyProblemMigration.migrate(realV1Payload, board).problems

        assertEquals(BoulderGrade.F6B, problems.first { it.id == "tidepool" }.grade)
        assertEquals(BoulderGrade.F5_PLUS, problems.first { it.id == "moss-line" }.grade)
        assertEquals(BoulderGrade.F7A, problems.first { it.id == "chalk-ghost" }.grade)
        assertEquals(BoulderGrade.fromPersisted("V4"), BoulderGrade.fromPersisted("F6B"))
    }

    @Test
    fun `problems with kickboard starts become needs-review with assignments intact`() {
        val problems = LegacyProblemMigration.migrate(realV1Payload, board).problems

        listOf("tidepool", "moss-line", "chalk-ghost").forEach { id ->
            val problem = problems.first { it.id == id }
            assertEquals(id, PublicationState.NEEDS_REVIEW, problem.publicationState)
        }
        // The invalid start assignments stay visible for repair.
        assertEquals(
            "h43",
            problems.first { it.id == "tidepool" }
                .assignments.first { it.role == ProblemHoldRole.START }.holdId,
        )
    }

    @Test
    fun `golden hour stays published under marked-only feet`() {
        val goldenHour = LegacyProblemMigration.migrate(realV1Payload, board)
            .problems.first { it.id == "golden-hour" }

        assertEquals(PublicationState.PUBLISHED, goldenHour.publicationState)
        assertEquals(FeetRule.MARKED_ONLY, goldenHour.feetRule)
        assertEquals(StartRule.MATCH_ONE, goldenHour.startRule)
        assertEquals(FinishRule.MATCH_ONE, goldenHour.finishRule)
    }

    @Test
    fun `one corrupt record is retained without erasing valid records`() {
        val payload = """
            [
              {"id":"good","name":"Good","grade":"F6A","accent":"SKY","setter":"You","note":"",
               "holds":[{"id":"h10","role":"START"},{"id":"h04","role":"FINISH"}]},
              {"id":"broken","grade":"F6A","accent":"SKY"},
              {"id":"weird-role","name":"Weird","grade":"F6A","accent":"SKY","setter":"You","note":"",
               "holds":[{"id":"h10","role":"MYSTERY"}]}
            ]
        """.trimIndent()

        val result = LegacyProblemMigration.migrate(payload, board)

        assertFalse(result.payloadCorrupt)
        assertEquals(listOf("good"), result.problems.map { it.id })
        assertEquals(2, result.unreadable.size)
        assertTrue(result.unreadable[0].raw.contains("broken"))
        assertTrue(result.unreadable[1].raw.contains("MYSTERY"))
    }

    @Test
    fun `a payload that is not an array is reported and retained`() {
        val result = LegacyProblemMigration.migrate("{\"oops\": true}", board)

        assertTrue(result.payloadCorrupt)
        assertTrue(result.problems.isEmpty())
        assertEquals("{\"oops\": true}", result.unreadable.single().raw)
    }

    @Test
    fun `migration is idempotent`() {
        val first = LegacyProblemMigration.migrate(realV1Payload, board)
        val second = LegacyProblemMigration.migrate(realV1Payload, board)

        assertEquals(first, second)
    }

    @Test
    fun `two legacy starts derive a split-two start rule`() {
        val payload = """
            [
              {"id":"double","name":"Double","grade":"F6A","accent":"SKY","setter":"You","note":"",
               "holds":[{"id":"h33","role":"START"},{"id":"h34","role":"START"},{"id":"h04","role":"FINISH"}]}
            ]
        """.trimIndent()

        val problem = LegacyProblemMigration.migrate(payload, board).problems.single()

        assertEquals(StartRule.SPLIT_TWO, problem.startRule)
        assertEquals(PublicationState.PUBLISHED, problem.publicationState)
    }
}
