package za.co.boardaf

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.boardaf.data.LibrarySnapshot
import za.co.boardaf.data.SnapshotCodec
import za.co.boardaf.data.SnapshotIO
import za.co.boardaf.data.StorageIssueCode
import za.co.boardaf.data.UnreadableRecord
import za.co.boardaf.data.VersionedBoardStore
import za.co.boardaf.model.Accent
import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.BoulderGrade
import za.co.boardaf.model.FeetRule
import za.co.boardaf.model.FinishRule
import za.co.boardaf.model.GradeSystem
import za.co.boardaf.model.Problem
import za.co.boardaf.model.ProblemAssignment
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.model.PublicationState
import za.co.boardaf.model.StartRule
import za.co.boardaf.setter.SetterMode

private class FakeIO(
    var snapshot: String? = null,
    var legacy: String? = null,
    var legacyGrade: String? = null,
) : SnapshotIO {
    val backups = mutableListOf<String>()
    var snapshotWrites = 0

    override suspend fun readSnapshot(): String? = snapshot
    override suspend fun writeSnapshot(value: String) {
        snapshotWrites++
        snapshot = value
    }

    override suspend fun writeCorruptBackup(value: String) {
        backups += value
    }

    override suspend fun readLegacyProblems(): String? = legacy
    override suspend fun readLegacyGradeSystem(): String? = legacyGrade
}

class VersionedBoardStoreTest {

    private val legacyPayload = """
        [
          {"id":"tidepool","name":"Tidepool","grade":"F6B","accent":"SKY","setter":"You","note":"n",
           "holds":[{"id":"h43","role":"START"},{"id":"h13","role":"FINISH"}]},
          {"id":"golden-hour","name":"Golden hour","grade":"V3","accent":"OCHRE","setter":"Jono","note":"n",
           "holds":[{"id":"h34","role":"START"},{"id":"h35","role":"FOOT"},{"id":"h04","role":"FINISH"}]}
        ]
    """.trimIndent()

    private fun fullSnapshot(): LibrarySnapshot = LibrarySnapshot(
        setup = BoardSetup.default().withCapabilityToggled("h43"),
        problems = listOf(
            Problem(
                id = "p1",
                name = "Roundtrip",
                grade = BoulderGrade.F6C_PLUS,
                accent = Accent.CORAL,
                setter = "Maya",
                note = "note",
                tags = listOf("Project", "Power"),
                feetRule = FeetRule.OPEN_KICKBOARD,
                startRule = StartRule.SPLIT_TWO,
                finishRule = FinishRule.MATCH_ONE,
                publicationState = PublicationState.BENCHMARK,
                forerunConfirmedAt = 1721000000000L,
                assignments = listOf(
                    ProblemAssignment("h33", ProblemHoldRole.START),
                    ProblemAssignment("h34", ProblemHoldRole.START),
                    ProblemAssignment("h20", ProblemHoldRole.REGULAR),
                    ProblemAssignment("h38", ProblemHoldRole.FOOT_ONLY),
                    ProblemAssignment("h04", ProblemHoldRole.FINISH),
                ),
            ),
        ),
        gradeSystem = GradeSystem.V_SCALE,
        setterMode = SetterMode.QUICK,
        unreadable = listOf(UnreadableRecord(raw = "{broken}", error = "why", source = "v1")),
    )

    @Test
    fun `v2 snapshot roundtrips without losing anything`() {
        val snapshot = fullSnapshot()

        val decoded = SnapshotCodec.decode(SnapshotCodec.encode(snapshot))

        check(decoded is SnapshotCodec.DecodeResult.Success)
        assertEquals(snapshot, decoded.snapshot)
        assertTrue(decoded.issues.isEmpty())
    }

    @Test
    fun `first load migrates v1 and leaves the legacy payload untouched`() = runTest {
        val io = FakeIO(legacy = legacyPayload, legacyGrade = "V_SCALE")
        val store = VersionedBoardStore(io)

        val result = store.load()

        assertEquals(2, result.snapshot.problems.size)
        assertEquals(GradeSystem.V_SCALE, result.snapshot.gradeSystem)
        assertEquals(
            PublicationState.NEEDS_REVIEW,
            result.snapshot.problems.first { it.id == "tidepool" }.publicationState,
        )
        assertEquals(
            PublicationState.PUBLISHED,
            result.snapshot.problems.first { it.id == "golden-hour" }.publicationState,
        )
        // v1 stays behind as the recovery backup.
        assertEquals(legacyPayload, io.legacy)
        assertNotNull(io.snapshot)
    }

    @Test
    fun `after migration the second load reads v2 and ignores the legacy payload`() = runTest {
        val io = FakeIO(legacy = legacyPayload)
        val store = VersionedBoardStore(io)
        store.load()

        io.legacy = "[]"
        val result = store.load()

        assertEquals(2, result.snapshot.problems.size)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `migration is idempotent across repeated loads`() = runTest {
        val io = FakeIO(legacy = legacyPayload)
        val store = VersionedBoardStore(io)

        val first = store.load()
        val second = store.load()

        assertEquals(first.snapshot, second.snapshot)
    }

    @Test
    fun `fresh installs seed the bundled problems`() = runTest {
        val io = FakeIO()
        val result = VersionedBoardStore(io).load()

        assertEquals(4, result.snapshot.problems.size)
        assertEquals(
            setOf(PublicationState.NEEDS_REVIEW, PublicationState.PUBLISHED),
            result.snapshot.problems.map { it.publicationState }.toSet(),
        )
    }

    @Test
    fun `a corrupt v2 snapshot is backed up and recovered from v1`() = runTest {
        val io = FakeIO(snapshot = "{definitely not json", legacy = legacyPayload)
        val store = VersionedBoardStore(io)

        val result = store.load()

        assertEquals(listOf("{definitely not json"), io.backups)
        assertTrue(result.issues.any { it.code == StorageIssueCode.SNAPSHOT_CORRUPT })
        assertEquals(2, result.snapshot.problems.size)
    }

    @Test
    fun `a corrupt v2 problem record is retained and the rest of the library survives`() = runTest {
        val io = FakeIO()
        val store = VersionedBoardStore(io)
        store.save(fullSnapshot())

        // Tamper with one problem record inside the stored snapshot.
        val root = Json.parseToJsonElement(io.snapshot!!).jsonObject
        val brokenProblem = buildJsonObject { put("id", "mystery") }
        val tampered = JsonObject(
            root + ("problems" to JsonArray(root.getValue("problems").jsonArray + brokenProblem)),
        )
        io.snapshot = tampered.toString()

        val result = store.load()

        assertEquals(1, result.snapshot.problems.size)
        assertTrue(result.issues.any { it.code == StorageIssueCode.SNAPSHOT_RECORD_UNREADABLE })
        val retained = result.snapshot.unreadable.first { it.source == UnreadableRecord.SOURCE_V2 }
        assertTrue(retained.raw.contains("mystery"))
    }

    @Test
    fun `unreadable records survive later saves`() = runTest {
        val io = FakeIO()
        val store = VersionedBoardStore(io)
        store.save(fullSnapshot())

        val reloaded = store.load()
        store.save(reloaded.snapshot)
        val again = store.load()

        assertEquals(fullSnapshot().unreadable, again.snapshot.unreadable)
    }

    @Test
    fun `unreadable legacy records surface as issues but keep valid problems`() = runTest {
        val io = FakeIO(
            legacy = """
                [
                  {"id":"good","name":"Good","grade":"F6A","accent":"SKY","setter":"You","note":"",
                   "holds":[{"id":"h10","role":"START"},{"id":"h04","role":"FINISH"}]},
                  {"nope": 1}
                ]
            """.trimIndent(),
        )

        val result = VersionedBoardStore(io).load()

        assertEquals(listOf("good"), result.snapshot.problems.map { it.id })
        assertTrue(result.issues.any { it.code == StorageIssueCode.LEGACY_RECORD_UNREADABLE })
        assertEquals(1, result.snapshot.unreadable.size)
        assertFalse(result.snapshot.unreadable.single().raw.isBlank())
    }
}
