package za.co.boardaf.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import za.co.boardaf.model.Accent
import za.co.boardaf.model.Attempt
import za.co.boardaf.model.BoardDefaults
import za.co.boardaf.model.HoldRole
import za.co.boardaf.model.Problem
import za.co.boardaf.model.ProblemHold

class BoardRepository(context: Context) {
    private val preferences = context.getSharedPreferences("board_af", Context.MODE_PRIVATE)

    fun loadProblems(): List<Problem> {
        val raw = preferences.getString(KEY_PROBLEMS, null) ?: return BoardDefaults.problems
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index -> array.getJSONObject(index).toProblem() }
        }.getOrElse { BoardDefaults.problems }
    }

    fun saveProblems(problems: List<Problem>) {
        val array = JSONArray()
        problems.forEach { array.put(it.toJson()) }
        preferences.edit { putString(KEY_PROBLEMS, array.toString()) }
    }

    fun loadAttempts(): List<Attempt> {
        val raw = preferences.getString(KEY_ATTEMPTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index -> array.getJSONObject(index).toAttempt() }
        }.getOrElse { emptyList() }
    }

    fun saveAttempts(attempts: List<Attempt>) {
        val array = JSONArray()
        attempts.forEach { array.put(it.toJson()) }
        preferences.edit { putString(KEY_ATTEMPTS, array.toString()) }
    }

    private fun Problem.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("grade", grade)
        put("accent", accent.name)
        put("setter", setter)
        put("note", note)
        put("sends", sends)
        put("holds", JSONArray().apply {
            holds.forEach { hold ->
                put(JSONObject().apply {
                    put("id", hold.holdId)
                    put("role", hold.role.name)
                })
            }
        })
    }

    private fun JSONObject.toProblem(): Problem {
        val holdsJson = getJSONArray("holds")
        val holds = List(holdsJson.length()) { index ->
            val hold = holdsJson.getJSONObject(index)
            ProblemHold(
                holdId = hold.getString("id"),
                role = HoldRole.valueOf(hold.getString("role")),
            )
        }
        return Problem(
            id = getString("id"),
            name = getString("name"),
            grade = getString("grade"),
            accent = Accent.valueOf(getString("accent")),
            setter = getString("setter"),
            note = optString("note"),
            holds = holds,
            sends = optInt("sends"),
        )
    }

    private fun Attempt.toJson() = JSONObject().apply {
        put("id", id)
        put("problemId", problemId)
        put("sent", sent)
        put("durationSeconds", durationSeconds)
        put("timestamp", timestamp)
    }

    private fun JSONObject.toAttempt() = Attempt(
        id = getLong("id"),
        problemId = getString("problemId"),
        sent = getBoolean("sent"),
        durationSeconds = getInt("durationSeconds"),
        timestamp = getLong("timestamp"),
    )

    private companion object {
        const val KEY_PROBLEMS = "problems_v1"
        const val KEY_ATTEMPTS = "attempts_v1"
    }
}
