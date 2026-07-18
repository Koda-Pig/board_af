package za.co.boardaf

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import za.co.boardaf.data.BoardRepository
import za.co.boardaf.model.Accent
import za.co.boardaf.model.BoulderGrade
import za.co.boardaf.model.DraftProblem
import za.co.boardaf.model.GradeSystem
import za.co.boardaf.model.HoldRole
import za.co.boardaf.model.Problem
import za.co.boardaf.model.ProblemHold

data class BoardUiState(
    val problems: List<Problem> = emptyList(),
    val selectedProblemId: String? = null,
    val isSetting: Boolean = false,
    val selectedRole: HoldRole = HoldRole.HAND,
    val gradeSystem: GradeSystem = GradeSystem.FRENCH,
    val draft: DraftProblem = DraftProblem(),
) {
    val selectedProblem: Problem?
        get() = problems.firstOrNull { it.id == selectedProblemId } ?: problems.firstOrNull()
}

class BoardViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BoardRepository(application)
    private val initialProblems = repository.loadProblems()

    private val mutableState = MutableStateFlow(
        BoardUiState(
            problems = initialProblems,
            selectedProblemId = initialProblems.firstOrNull()?.id,
            gradeSystem = repository.loadGradeSystem(),
        ),
    )
    val state: StateFlow<BoardUiState> = mutableState.asStateFlow()

    fun selectProblem(problemId: String) {
        mutableState.value = mutableState.value.copy(
            selectedProblemId = problemId,
            isSetting = false,
        )
    }

    fun startSetting() {
        mutableState.value = mutableState.value.copy(
            isSetting = true,
            selectedRole = HoldRole.HAND,
            draft = DraftProblem(),
        )
    }

    fun cancelSetting() {
        mutableState.value = mutableState.value.copy(isSetting = false)
    }

    fun setDraftName(name: String) = updateDraft { copy(name = name) }
    fun setDraftGrade(grade: BoulderGrade) = updateDraft { copy(grade = grade) }
    fun setDraftAccent(accent: Accent) = updateDraft { copy(accent = accent) }
    fun setDraftNote(note: String) = updateDraft { copy(note = note) }

    fun selectRole(role: HoldRole) {
        mutableState.value = mutableState.value.copy(selectedRole = role)
    }

    fun setGradeSystem(gradeSystem: GradeSystem) {
        repository.saveGradeSystem(gradeSystem)
        mutableState.value = mutableState.value.copy(gradeSystem = gradeSystem)
    }

    fun toggleDraftHold(holdId: String) {
        val current = mutableState.value
        val existing = current.draft.holds.firstOrNull { it.holdId == holdId }
        val updatedHolds = if (existing != null) {
            current.draft.holds.filterNot { it.holdId == holdId }
        } else {
            current.draft.holds + ProblemHold(holdId, current.selectedRole)
        }
        mutableState.value = current.copy(draft = current.draft.copy(holds = updatedHolds))
    }

    fun clearDraftHolds() = updateDraft { copy(holds = emptyList()) }

    fun saveDraft() {
        val current = mutableState.value
        if (!current.draft.canSave) return

        val problem = Problem(
            id = "${current.draft.name.slug()}-${System.currentTimeMillis()}",
            name = current.draft.name.trim(),
            grade = current.draft.grade,
            accent = current.draft.accent,
            setter = "You",
            note = current.draft.note.trim(),
            holds = current.draft.holds,
        )
        val problems = listOf(problem) + current.problems
        repository.saveProblems(problems)
        mutableState.value = current.copy(
            problems = problems,
            selectedProblemId = problem.id,
            isSetting = false,
            draft = DraftProblem(),
        )
    }

    private fun updateDraft(transform: DraftProblem.() -> DraftProblem) {
        val current = mutableState.value
        mutableState.value = current.copy(draft = current.draft.transform())
    }

    private fun String.slug() = lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "problem" }
}
