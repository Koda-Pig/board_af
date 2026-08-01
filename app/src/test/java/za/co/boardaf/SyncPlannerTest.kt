package za.co.boardaf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.boardaf.data.LibrarySnapshot
import za.co.boardaf.data.sync.RemoteBoardRecord
import za.co.boardaf.data.sync.RemoteLibrary
import za.co.boardaf.data.sync.RemoteProblemRecord
import za.co.boardaf.data.sync.SyncBaselines
import za.co.boardaf.data.sync.SyncCodec
import za.co.boardaf.data.sync.SyncPlanner
import za.co.boardaf.model.Accent
import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.BoulderGrade
import za.co.boardaf.model.GradeSystem
import za.co.boardaf.model.Problem
import za.co.boardaf.setter.SetterMode

class SyncPlannerTest {

    private val now = 1_000_000L

    private fun problem(id: String, name: String = id, setter: String = "You") = Problem(
        id = id,
        name = name,
        grade = BoulderGrade.F6A,
        accent = Accent.SKY,
        setter = setter,
    )

    private fun snapshot(vararg problems: Problem) = LibrarySnapshot(
        setup = BoardSetup.default(),
        problems = problems.toList(),
    )

    private fun remoteRecord(problem: Problem, revision: Long = 1, pending: Boolean = false) =
        RemoteProblemRecord(
            problem = problem,
            encoded = SyncCodec.encodeProblem(problem),
            revision = revision,
            pendingWrite = pending,
        )

    private fun remoteBoard(snapshot: LibrarySnapshot, revision: Long = 1, pending: Boolean = false) =
        RemoteBoardRecord(
            setup = snapshot.setup,
            gradeSystem = snapshot.gradeSystem,
            setterMode = snapshot.setterMode,
            encoded = SyncCodec.encodeBoard(snapshot.setup, snapshot.gradeSystem, snapshot.setterMode),
            revision = revision,
            pendingWrite = pending,
        )

    /** Remote state that exactly mirrors the local snapshot. */
    private fun mirroredRemote(local: LibrarySnapshot) = RemoteLibrary(
        board = remoteBoard(local),
        problems = local.problems.associate { it.id to remoteRecord(it) },
    )

    /** Baselines that agree with the given snapshot. */
    private fun agreedBaselines(local: LibrarySnapshot) = SyncBaselines(
        board = SyncCodec.encodeBoard(local.setup, local.gradeSystem, local.setterMode),
        problems = local.problems.associate { it.id to SyncCodec.encodeProblem(it) },
    )

    @Test
    fun `first sync pushes every local problem and the board`() {
        val local = snapshot(problem("a"), problem("b"))

        val plan = SyncPlanner.plan(local, RemoteLibrary(), SyncBaselines(), now)

        assertEquals(setOf("a", "b"), plan.problemPushes.map { it.problem.id }.toSet())
        assertTrue(plan.problemPushes.all { it.revision == 1L })
        assertNotNull(plan.boardPush)
        assertNull(plan.mergedSnapshot)
    }

    @Test
    fun `local edit after acknowledged first push is not a conflict`() {
        val firstSaved = problem("uppity-1", name = "uppit")
        val firstPlan = SyncPlanner.plan(
            snapshot(firstSaved),
            RemoteLibrary(),
            SyncBaselines(),
            now,
        )

        val latestLocal = firstSaved.copy(name = "uppity")
        val acknowledgedFirstPush = RemoteLibrary(
            board = remoteBoard(snapshot(firstSaved)),
            problems = mapOf(firstSaved.id to remoteRecord(firstSaved, revision = 1)),
        )
        val secondPlan = SyncPlanner.plan(
            snapshot(latestLocal),
            acknowledgedFirstPush,
            firstPlan.baselines,
            now + 1,
        )

        assertNull(secondPlan.mergedSnapshot)
        assertEquals(listOf(latestLocal), secondPlan.problemPushes.map { it.problem })
        assertEquals(2L, secondPlan.problemPushes.single().revision)
        assertTrue(secondPlan.issues.isEmpty())
    }

    @Test
    fun `identical local and remote content advances baselines and does nothing else`() {
        val local = snapshot(problem("a"))
        val remote = mirroredRemote(local)

        val plan = SyncPlanner.plan(local, remote, SyncBaselines(), now)

        assertTrue(plan.problemPushes.isEmpty())
        assertNull(plan.boardPush)
        assertNull(plan.mergedSnapshot)
        assertEquals(SyncCodec.encodeProblem(problem("a")), plan.baselines.problems["a"])
        assertNotNull(plan.baselines.board)
    }

    @Test
    fun `local edit with unchanged remote pushes with bumped revision`() {
        val base = problem("a")
        val edited = base.copy(setter = "new beta")
        val local = snapshot(edited)
        val remote = RemoteLibrary(
            board = remoteBoard(local),
            problems = mapOf("a" to remoteRecord(base, revision = 3)),
        )
        val baselines = agreedBaselines(snapshot(base)).copy(
            board = SyncCodec.encodeBoard(local.setup, local.gradeSystem, local.setterMode),
        )

        val plan = SyncPlanner.plan(local, remote, baselines, now)

        assertEquals(1, plan.problemPushes.size)
        assertEquals(edited, plan.problemPushes.single().problem)
        assertEquals(4L, plan.problemPushes.single().revision)
        assertNull(plan.mergedSnapshot)
    }

    @Test
    fun `remote edit with unchanged local is adopted`() {
        val base = problem("a")
        val remoteEdit = base.copy(setter = "edited elsewhere")
        val local = snapshot(base)
        val remote = RemoteLibrary(
            board = remoteBoard(local),
            problems = mapOf("a" to remoteRecord(remoteEdit, revision = 2)),
        )

        val plan = SyncPlanner.plan(local, remote, agreedBaselines(local), now)

        assertTrue(plan.problemPushes.isEmpty())
        val merged = plan.mergedSnapshot
        assertNotNull(merged)
        assertEquals(listOf(remoteEdit), merged!!.problems)
        assertEquals(SyncCodec.encodeProblem(remoteEdit), plan.baselines.problems["a"])
    }

    @Test
    fun `new remote problem is adopted without pushes`() {
        val local = snapshot(problem("a"))
        val incoming = problem("b", setter = "from the other device")
        val remote = mirroredRemote(local).let {
            it.copy(problems = it.problems + ("b" to remoteRecord(incoming)))
        }

        val plan = SyncPlanner.plan(local, remote, agreedBaselines(local), now)

        assertTrue(plan.problemPushes.isEmpty())
        assertEquals(setOf("a", "b"), plan.mergedSnapshot!!.problems.map { it.id }.toSet())
    }

    @Test
    fun `conflicting edits keep both versions`() {
        val base = problem("a", name = "Tidepool")
        val localEdit = base.copy(setter = "local beta")
        val remoteEdit = base.copy(setter = "remote beta")
        val local = snapshot(localEdit)
        val remote = RemoteLibrary(
            board = remoteBoard(local),
            problems = mapOf("a" to remoteRecord(remoteEdit, revision = 2)),
        )
        val baselines = SyncBaselines(
            board = SyncCodec.encodeBoard(local.setup, local.gradeSystem, local.setterMode),
            problems = mapOf("a" to SyncCodec.encodeProblem(base)),
        )

        val plan = SyncPlanner.plan(local, remote, baselines, now)

        val merged = plan.mergedSnapshot!!
        // Remote content keeps the original id.
        assertEquals("remote beta", merged.problems.first { it.id == "a" }.setter)
        // Local content survives as a conflict copy that is pushed.
        val copy = merged.problems.first { it.id != "a" }
        assertEquals("local beta", copy.setter)
        assertTrue(copy.name.contains("conflict copy"))
        assertEquals(listOf(copy), plan.problemPushes.map { it.problem })
        assertTrue(plan.issues.any { it.contains("Tidepool") })
    }

    @Test
    fun `problem deleted on the server is restored from this device`() {
        val kept = problem("a")
        val local = snapshot(kept)
        val remote = RemoteLibrary(board = remoteBoard(local), problems = emptyMap())

        val plan = SyncPlanner.plan(local, remote, agreedBaselines(local), now)

        assertEquals(listOf(kept), plan.problemPushes.map { it.problem })
        assertTrue(plan.issues.any { it.contains("disappeared") })
    }

    @Test
    fun `pending remote writes are skipped entirely`() {
        val base = problem("a")
        val local = snapshot(base.copy(setter = "newer local"))
        val remote = RemoteLibrary(
            board = remoteBoard(local),
            problems = mapOf("a" to remoteRecord(base, pending = true)),
        )

        val plan = SyncPlanner.plan(local, remote, SyncBaselines(), now)

        assertTrue(plan.problemPushes.isEmpty())
        assertNull(plan.mergedSnapshot)
        assertFalse(plan.baselines.problems.containsKey("a"))
    }

    @Test
    fun `unreadable remote problems are never pushed over`() {
        val local = snapshot(problem("a", setter = "local content"))
        val remote = RemoteLibrary(
            board = remoteBoard(local),
            problems = emptyMap(),
            unreadableProblemIds = setOf("a"),
        )

        val plan = SyncPlanner.plan(local, remote, SyncBaselines(), now)

        assertTrue(plan.problemPushes.isEmpty())
        assertTrue(plan.issues.any { it.contains("couldn't be read") })
    }

    @Test
    fun `board conflict adopts the server version and reports it`() {
        val base = snapshot()
        val localSetup = base.setup.withKickboardEnabled(false)
        val remoteSetup = base.setup.withBoundary(0.5f)
        val local = base.copy(setup = localSetup)
        val remote = RemoteLibrary(
            board = remoteBoard(base.copy(setup = remoteSetup), revision = 2),
        )
        val baselines = SyncBaselines(
            board = SyncCodec.encodeBoard(base.setup, base.gradeSystem, base.setterMode),
        )

        val plan = SyncPlanner.plan(local, remote, baselines, now)

        assertNull(plan.boardPush)
        assertEquals(remoteSetup, plan.mergedSnapshot!!.setup)
        assertTrue(plan.issues.any { it.contains("Board setup") })
    }

    @Test
    fun `grade system change syncs through the board document`() {
        val base = snapshot(problem("a"))
        val local = base.copy(gradeSystem = GradeSystem.V_SCALE)
        val remote = mirroredRemote(base)
        val baselines = agreedBaselines(base)

        val plan = SyncPlanner.plan(local, remote, baselines, now)

        val boardPush = plan.boardPush
        assertNotNull(boardPush)
        assertEquals(GradeSystem.V_SCALE, boardPush!!.gradeSystem)
        assertEquals(2L, boardPush.revision)
    }

    @Test
    fun `setter mode is part of the board unit`() {
        val base = snapshot()
        val local = base.copy(setterMode = SetterMode.QUICK)
        val remote = RemoteLibrary(board = remoteBoard(base, revision = 5))

        val plan = SyncPlanner.plan(local, remote, agreedBaselines(base), now)

        val boardPush = plan.boardPush
        assertNotNull(boardPush)
        assertEquals(SetterMode.QUICK, boardPush!!.setterMode)
        assertEquals(6L, boardPush.revision)
    }

    // --- Deletion tombstones ---------------------------------------------------

    @Test
    fun `a locally deleted problem is pushed as a tombstone, not re-adopted`() {
        val gone = problem("p1")
        val kept = problem("p2")
        val local = snapshot(kept).copy(deletedProblemIds = setOf("p1"))
        val remote = RemoteLibrary(
            board = remoteBoard(local),
            problems = mapOf("p1" to remoteRecord(gone, revision = 3), "p2" to remoteRecord(kept)),
        )

        val plan = SyncPlanner.plan(local, remote, agreedBaselines(snapshot(gone, kept)), now)

        assertEquals(listOf("p1"), plan.problemDeletes.map { it.id })
        assertEquals(4L, plan.problemDeletes.single().revision)
        // The surviving server copy must not come back as an addition.
        assertTrue(plan.mergedSnapshot?.problems.orEmpty().none { it.id == "p1" })
        assertTrue(plan.problemPushes.none { it.problem.id == "p1" })
        // A deleted record must not keep a baseline.
        assertNull(plan.baselines.problems["p1"])
    }

    @Test
    fun `a remote tombstone deletes the local copy and is recorded locally`() {
        val doomed = problem("p1")
        val local = snapshot(doomed, problem("p2"))
        val remote = RemoteLibrary(
            board = remoteBoard(local),
            problems = mapOf(
                "p1" to remoteRecord(doomed, revision = 2).copy(deleted = true),
                "p2" to remoteRecord(problem("p2")),
            ),
        )

        val plan = SyncPlanner.plan(local, remote, agreedBaselines(local), now)

        val merged = plan.mergedSnapshot
        assertNotNull(merged)
        assertEquals(listOf("p2"), merged!!.problems.map { it.id })
        // Tombstone is retained locally so an offline device can't resurrect it.
        assertTrue("p1" in merged.deletedProblemIds)
        assertTrue(plan.problemPushes.none { it.problem.id == "p1" })
        assertNull(plan.baselines.problems["p1"])
    }

    @Test
    fun `a tombstone for a problem the server never had needs no delete push`() {
        val local = snapshot(problem("p2")).copy(deletedProblemIds = setOf("p1"))
        val remote = RemoteLibrary(
            board = remoteBoard(local),
            problems = mapOf("p2" to remoteRecord(problem("p2"))),
        )

        val plan = SyncPlanner.plan(local, remote, agreedBaselines(snapshot(problem("p2"))), now)

        assertTrue(plan.problemDeletes.isEmpty())
        assertTrue(plan.problemPushes.isEmpty())
        // Nothing changed, so there is nothing to adopt.
        assertNull(plan.mergedSnapshot)
    }

    @Test
    fun `deleting still beats the restore-from-device rule for a vanished doc`() {
        // A doc that simply disappeared is data loss and gets re-pushed...
        val kept = problem("p1")
        val local = snapshot(kept)
        val remote = RemoteLibrary(board = remoteBoard(local), problems = emptyMap())
        val restored = SyncPlanner.plan(local, remote, agreedBaselines(local), now)
        assertEquals(listOf("p1"), restored.problemPushes.map { it.problem.id })

        // ...unless this device deleted it on purpose.
        val deletedLocal = snapshot().copy(deletedProblemIds = setOf("p1"))
        val afterDelete = SyncPlanner.plan(deletedLocal, remote, agreedBaselines(local), now)
        assertTrue(afterDelete.problemPushes.isEmpty())
        assertTrue(afterDelete.problemDeletes.isEmpty())
    }
}
