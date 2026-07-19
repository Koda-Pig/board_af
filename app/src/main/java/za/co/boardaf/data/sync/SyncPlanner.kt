package za.co.boardaf.data.sync

import za.co.boardaf.data.LibrarySnapshot
import za.co.boardaf.model.Problem

/**
 * Pure three-way merge between the local snapshot, the remote library, and the
 * per-record baselines (the last content both sides agreed on).
 *
 * Data-safety rules, mirroring the local store's contract:
 * - Never silently drop the losing side of a conflict: the remote version keeps
 *   the original ID and the local version is preserved as a pushed conflict copy.
 * - A record's baseline advances when local and server-acknowledged remote
 *   content are identical, or to the content of a push that the caller will
 *   persist only after Firebase acknowledges it. Pending remote writes are skipped.
 * - Remote docs that failed to decode are never overwritten by pushes.
 * - A remote deletion of a record this device has synced before is treated as
 *   data loss and repaired by re-pushing the local copy.
 */
object SyncPlanner {

    fun plan(
        local: LibrarySnapshot,
        remote: RemoteLibrary,
        baselines: SyncBaselines,
        now: Long,
    ): SyncPlan {
        val issues = mutableListOf<String>()
        val newBaselines = mutableMapOf<String, String>()
        val pushes = mutableListOf<ProblemPush>()

        val localById = local.problems.associateBy { it.id }
        val localEncoded = local.problems.associate { it.id to SyncCodec.encodeProblem(it) }

        // Results keyed by original id; conflict copies collected separately.
        val resolved = linkedMapOf<String, Problem>()
        val additions = mutableListOf<Problem>()
        var changedLocally = false

        val allIds = localById.keys + remote.problems.keys
        for (id in allIds) {
            val localProblem = localById[id]
            val record = remote.problems[id]
            val baseline = baselines.problems[id]

            if (id in remote.unreadableProblemIds) {
                // Keep the raw server payload; never write over it.
                if (localProblem != null) resolved[id] = localProblem
                if (baseline != null) newBaselines[id] = baseline
                issues += "Problem \"$id\" on the server couldn't be read; it was left untouched."
                continue
            }

            if (record != null && record.pendingWrite) {
                // Our own write hasn't been acknowledged yet; decide next round.
                if (localProblem != null) resolved[id] = localProblem
                if (baseline != null) newBaselines[id] = baseline
                continue
            }

            when {
                localProblem == null && record != null -> {
                    // New on the server: adopt it.
                    additions += record.problem
                    newBaselines[id] = record.encoded
                    changedLocally = true
                }

                localProblem != null && record == null -> {
                    resolved[id] = localProblem
                    if (baseline != null) {
                        issues += "\"${localProblem.name}\" disappeared from the server and was restored from this device."
                    }
                    pushes += ProblemPush(localProblem, revision = 1)
                }

                localProblem != null && record != null -> {
                    val encoded = localEncoded.getValue(id)
                    when {
                        encoded == record.encoded -> {
                            resolved[id] = localProblem
                            newBaselines[id] = record.encoded
                        }

                        baseline == record.encoded -> {
                            // Remote unchanged since last agreement: local edit wins.
                            resolved[id] = localProblem
                            newBaselines[id] = baseline
                            pushes += ProblemPush(localProblem, revision = record.revision + 1)
                        }

                        baseline == encoded -> {
                            // Local unchanged: adopt the remote edit.
                            resolved[id] = record.problem
                            newBaselines[id] = record.encoded
                            changedLocally = true
                        }

                        else -> {
                            // Both sides changed: remote keeps the ID, local becomes a copy.
                            val copy = localProblem.copy(
                                id = "$id-conflict-$now",
                                name = "${localProblem.name} (conflict copy)",
                            )
                            resolved[id] = record.problem
                            additions += copy
                            newBaselines[id] = record.encoded
                            pushes += ProblemPush(copy, revision = 1)
                            changedLocally = true
                            issues += "\"${localProblem.name}\" was edited on two devices; both versions were kept."
                        }
                    }
                }
            }
        }

        // Board setup + settings document (single record, same three-way rules).
        var boardPush: BoardPush? = null
        var mergedSetup = local.setup
        var mergedGradeSystem = local.gradeSystem
        var mergedSetterMode = local.setterMode
        var boardBaseline = baselines.board
        val localBoardEncoded = SyncCodec.encodeBoard(local.setup, local.gradeSystem, local.setterMode)
        val boardRecord = remote.board
        when {
            remote.boardUnreadable ->
                issues += "The board setup on the server couldn't be read; it was left untouched."

            boardRecord != null && boardRecord.pendingWrite -> Unit

            boardRecord == null ->
                boardPush = BoardPush(local.setup, local.gradeSystem, local.setterMode, revision = 1)

            localBoardEncoded == boardRecord.encoded ->
                boardBaseline = boardRecord.encoded

            baselines.board == boardRecord.encoded -> {
                boardPush = BoardPush(
                    local.setup, local.gradeSystem, local.setterMode,
                    revision = boardRecord.revision + 1,
                )
            }

            baselines.board == localBoardEncoded || baselines.board == null -> {
                mergedSetup = boardRecord.setup
                mergedGradeSystem = boardRecord.gradeSystem
                mergedSetterMode = boardRecord.setterMode
                boardBaseline = boardRecord.encoded
                changedLocally = true
                if (baselines.board == null && localBoardEncoded != boardRecord.encoded) {
                    issues += "Board setup was taken from the server."
                }
            }

            else -> {
                // Both edited the one board setup; the server version wins but the event is reported.
                mergedSetup = boardRecord.setup
                mergedGradeSystem = boardRecord.gradeSystem
                mergedSetterMode = boardRecord.setterMode
                boardBaseline = boardRecord.encoded
                changedLocally = true
                issues += "Board setup was changed on two devices; the other device's setup was kept."
            }
        }

        // These baselines describe the state after the plan has been executed.
        // FirestoreCloudSync persists them only after batch.commit().await(), so a
        // subsequent local edit can compare against the exact payload Firebase
        // acknowledged instead of mistaking the edit for a cross-device conflict.
        pushes.forEach { push ->
            newBaselines[push.problem.id] = SyncCodec.encodeProblem(push.problem)
        }
        boardPush?.let { push ->
            boardBaseline = SyncCodec.encodeBoard(
                push.setup,
                push.gradeSystem,
                push.setterMode,
            )
        }

        val mergedSnapshot = if (changedLocally) {
            LibrarySnapshot(
                setup = mergedSetup,
                problems = additions + local.problems.mapNotNull { resolved[it.id] },
                gradeSystem = mergedGradeSystem,
                setterMode = mergedSetterMode,
                unreadable = local.unreadable,
            )
        } else {
            null
        }

        return SyncPlan(
            mergedSnapshot = mergedSnapshot,
            problemPushes = pushes,
            boardPush = boardPush,
            baselines = SyncBaselines(board = boardBaseline, problems = newBaselines),
            issues = issues,
        )
    }
}
