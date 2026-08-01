package za.co.boardaf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.boardaf.data.LibrarySnapshot
import za.co.boardaf.data.sync.RemoteLibrary
import za.co.boardaf.data.sync.RemoteProblemRecord
import za.co.boardaf.data.sync.SyncBaselines
import za.co.boardaf.data.sync.SyncCodec
import za.co.boardaf.data.sync.SyncPlanner
import za.co.boardaf.model.Accent
import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.BoulderGrade
import za.co.boardaf.model.Problem

class SyncPlannerTombstoneRetirementTest {

    private val now = SyncPlanner.TOMBSTONE_RETENTION_MS * 10

    private fun problem(id: String) = Problem(
        id = id,
        name = id,
        grade = BoulderGrade.F6A,
        accent = Accent.SKY,
        setter = "You",
        note = "",
    )

    private fun snapshot(deleted: Set<String>, vararg problems: Problem) = LibrarySnapshot(
        setup = BoardSetup.default(),
        problems = problems.toList(),
        deletedProblemIds = deleted,
    )

    private fun tombstone(id: String, deletedAt: Long?, pending: Boolean = false) =
        RemoteProblemRecord(
            problem = problem(id),
            encoded = SyncCodec.encodeProblem(problem(id)),
            revision = 2,
            pendingWrite = pending,
            deleted = true,
            deletedAt = deletedAt,
        )

    @Test
    fun `a local tombstone retires once the remote one is old enough`() {
        val local = snapshot(deleted = setOf("gone"))
        val remote = RemoteLibrary(
            problems = mapOf(
                "gone" to tombstone("gone", deletedAt = now - SyncPlanner.TOMBSTONE_RETENTION_MS),
            ),
        )

        val plan = SyncPlanner.plan(local, remote, SyncBaselines(), now)

        assertEquals(emptySet<String>(), plan.mergedSnapshot?.deletedProblemIds)
        assertTrue(plan.problemDeletes.isEmpty())
    }

    @Test
    fun `a fresh remote tombstone keeps the local one`() {
        val local = snapshot(deleted = setOf("gone"))
        val remote = RemoteLibrary(
            problems = mapOf("gone" to tombstone("gone", deletedAt = now - 1)),
        )

        val plan = SyncPlanner.plan(local, remote, SyncBaselines(), now)

        // Nothing changed locally, so no merged snapshot — the tombstone stays.
        assertEquals(null, plan.mergedSnapshot)
    }

    @Test
    fun `tombstones without a deletion time are retained forever`() {
        val local = snapshot(deleted = setOf("gone"))
        val remote = RemoteLibrary(
            problems = mapOf("gone" to tombstone("gone", deletedAt = null)),
        )

        val plan = SyncPlanner.plan(local, remote, SyncBaselines(), now)

        assertEquals(null, plan.mergedSnapshot)
    }

    @Test
    fun `an unacknowledged tombstone write never retires anything`() {
        val local = snapshot(deleted = setOf("gone"))
        val remote = RemoteLibrary(
            problems = mapOf(
                "gone" to tombstone("gone", deletedAt = 0L, pending = true),
            ),
        )

        val plan = SyncPlanner.plan(local, remote, SyncBaselines(), now)

        assertEquals(null, plan.mergedSnapshot)
    }

    @Test
    fun `a retired tombstone is not re-adopted on the next plan`() {
        // After retirement the local side has no tombstone; the old remote
        // tombstone must not resurrect it (which would undo retirement forever).
        val local = snapshot(deleted = emptySet())
        val remote = RemoteLibrary(
            problems = mapOf(
                "gone" to tombstone("gone", deletedAt = now - SyncPlanner.TOMBSTONE_RETENTION_MS),
            ),
        )

        val plan = SyncPlanner.plan(local, remote, SyncBaselines(), now)

        assertEquals(null, plan.mergedSnapshot)
        assertTrue(plan.problemPushes.isEmpty())
    }
}
