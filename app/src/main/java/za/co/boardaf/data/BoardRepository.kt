package za.co.boardaf.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import za.co.boardaf.model.Accent
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

    private fun Problem.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("grade", grade)
        put("accent", accent.name)
        put("setter", setter)
        put("note", note)
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
        )
    }

    private companion object {
        const val KEY_PROBLEMS = "problems_v1"
    }
}
