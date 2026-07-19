package za.co.boardaf

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import za.co.boardaf.data.AndroidSnapshotIO
import za.co.boardaf.data.BoardStore
import za.co.boardaf.data.LibrarySnapshot
import za.co.boardaf.data.SnapshotCodec
import za.co.boardaf.data.StorageIssue
import za.co.boardaf.data.UnreadableRecord
import za.co.boardaf.data.VersionedBoardStore
import za.co.boardaf.data.sync.CloudSync
import za.co.boardaf.data.sync.CloudSyncState
import za.co.boardaf.data.sync.FirestoreCloudSync
import za.co.boardaf.model.Accent
import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.BoulderGrade
import za.co.boardaf.model.ConfiguredBoard
import za.co.boardaf.model.DraftProblem
import za.co.boardaf.model.FeetRule
import za.co.boardaf.model.GradeSystem
import za.co.boardaf.model.Problem
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.model.ProblemIssue
import za.co.boardaf.model.ProblemValidator
import za.co.boardaf.model.PublicationState
import za.co.boardaf.setter.GuidedStep
import za.co.boardaf.setter.SetterMode
import za.co.boardaf.setter.SetterReducer
import za.co.boardaf.setter.SetterState
import za.co.boardaf.setter.TapRejection

data class BoardUiState(
    val isLoading: Boolean = false,
    val problems: List<Problem> = emptyList(),
    val selectedProblemId: String? = null,
    val isSetting: Boolean = false,
    val setter: SetterState = SetterState(),
    val setterMode: SetterMode = SetterMode.GUIDED,
    val gradeSystem: GradeSystem = GradeSystem.FRENCH,
    val setup: BoardSetup = BoardSetup.default(),
    val storageIssues: List<StorageIssue> = emptyList(),
    val unreadableRecords: List<UnreadableRecord> = emptyList(),
    val cloud: CloudSyncState = CloudSyncState(),
) {
    val board: ConfiguredBoard
        get() = ConfiguredBoard.from(setup)

    val selectedProblem: Problem?
        get() = problems.firstOrNull { it.id == selectedProblemId }
            ?: problems.firstOrNull { it.publicationState != PublicationState.ARCHIVED }
            ?: problems.firstOrNull()

    val draftIssues: List<ProblemIssue>
        get() = ProblemValidator.validate(setter.draft.toSpec(), board)
}

sealed interface BoardEvent {
    data class TapRejected(val rejection: TapRejection) : BoardEvent
    data class Message(val text: String) : BoardEvent
}

class BoardViewModel @JvmOverloads constructor(
    application: Application,
    private val store: BoardStore = VersionedBoardStore(AndroidSnapshotIO(application)),
    private val cloudSync: CloudSync? = runCatching { FirestoreCloudSync(application) }.getOrNull(),
) : AndroidViewModel(application) {

    private val mutableState = MutableStateFlow(BoardUiState(isLoading = true))
    val state: StateFlow<BoardUiState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<BoardEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<BoardEvent> = mutableEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            val result = store.load()
            // Preserve cloud state: startCloudSync() may already have collected READY.
            mutableState.value = mutableState.value.copy(
                isLoading = false,
                problems = result.snapshot.problems,
                selectedProblemId = result.snapshot.problems
                    .firstOrNull { it.publicationState != PublicationState.ARCHIVED }?.id,
                gradeSystem = result.snapshot.gradeSystem,
                setterMode = result.snapshot.setterMode,
                setup = result.snapshot.setup,
                storageIssues = result.issues,
                unreadableRecords = result.snapshot.unreadable,
            )
            cloudSync?.onLocalChanged(result.snapshot)
        }
        startCloudSync()
    }

    private fun startCloudSync() {
        val sync = cloudSync ?: return
        sync.start(viewModelScope)
        viewModelScope.launch {
            sync.state.collect { cloudState ->
                mutableState.value = mutableState.value.copy(cloud = cloudState)
            }
        }
        viewModelScope.launch {
            sync.mergedSnapshots.collect { merged ->
                val current = mutableState.value
                // Ignore merges computed against a stale local snapshot; the engine
                // re-plans from the latest onLocalChanged call.
                if (SnapshotCodec.encode(current.toSnapshot()) != merged.basedOnFingerprint) return@collect
                mutableState.value = current.copy(
                    problems = merged.snapshot.problems,
                    setup = merged.snapshot.setup,
                    gradeSystem = merged.snapshot.gradeSystem,
                    setterMode = merged.snapshot.setterMode,
                    unreadableRecords = merged.snapshot.unreadable,
                )
                persist()
            }
        }
    }

    // --- Cloud sync ----------------------------------------------------------------

    fun cloudSignIn(email: String, password: String) {
        cloudSync?.signIn(email, password)
    }

    fun cloudCreateAccount(email: String, password: String) {
        cloudSync?.createAccount(email, password)
    }

    fun cloudSignOut() {
        cloudSync?.signOut()
    }

    fun cloudSyncNow() {
        cloudSync?.syncNow()
    }

    // --- Library -----------------------------------------------------------------

    fun selectProblem(problemId: String) {
        mutableState.value = mutableState.value.copy(
            selectedProblemId = problemId,
            isSetting = false,
        )
    }

    fun archiveProblem(problemId: String) = updateProblem(problemId) {
        it.copy(publicationState = PublicationState.ARCHIVED)
    }

    fun unarchiveProblem(problemId: String) = updateProblem(problemId) { problem ->
        val issues = ProblemValidator.validate(problem, mutableState.value.board)
        problem.copy(
            publicationState = when {
                ProblemValidator.hasErrors(issues) -> PublicationState.NEEDS_REVIEW
                problem.forerunConfirmedAt != null -> PublicationState.PUBLISHED
                else -> PublicationState.DRAFT
            },
        )
    }

    fun toggleBenchmark(problemId: String) = updateProblem(problemId) { problem ->
        when (problem.publicationState) {
            PublicationState.PUBLISHED -> problem.copy(publicationState = PublicationState.BENCHMARK)
            PublicationState.BENCHMARK -> problem.copy(publicationState = PublicationState.PUBLISHED)
            else -> problem
        }
    }

    /** Publish directly from the library/detail once a problem is valid and forerun. */
    fun publishProblem(problemId: String) {
        val current = mutableState.value
        val problem = current.problems.firstOrNull { it.id == problemId } ?: return
        val issues = ProblemValidator.validate(problem, current.board)
        if (ProblemValidator.hasErrors(issues)) {
            emit(BoardEvent.Message("Fix the errors before publishing."))
            return
        }
        updateProblem(problemId) {
            it.copy(
                publicationState = PublicationState.PUBLISHED,
                forerunConfirmedAt = System.currentTimeMillis(),
            )
        }
    }

    // --- Setter session ----------------------------------------------------------

    fun startSetting() {
        val current = mutableState.value
        mutableState.value = current.copy(
            isSetting = true,
            setter = SetterReducer.start(DraftProblem()),
        )
    }

    fun startEditing(problemId: String) {
        val current = mutableState.value
        val problem = current.problems.firstOrNull { it.id == problemId } ?: return
        mutableState.value = current.copy(
            isSetting = true,
            selectedProblemId = problemId,
            setter = SetterReducer.start(DraftProblem.fromProblem(problem)),
        )
    }

    fun duplicateProblem(problemId: String) {
        val current = mutableState.value
        val problem = current.problems.firstOrNull { it.id == problemId } ?: return
        mutableState.value = current.copy(
            isSetting = true,
            setter = SetterReducer.start(DraftProblem.duplicateOf(problem)),
        )
        autosaveDraft()
    }

    fun cancelSetting() {
        val current = mutableState.value
        if (!current.isSetting) return
        autosaveDraft()
        mutableState.value = mutableState.value.copy(isSetting = false)
    }

    fun tapHold(holdId: String) {
        val current = mutableState.value
        if (!current.isSetting) return
        val result = SetterReducer.tapHold(current.setter, holdId, current.board)
        mutableState.value = current.copy(setter = result.state)
        result.rejection?.let { emit(BoardEvent.TapRejected(it)) }
        result.notice?.let { emit(BoardEvent.Message(it)) }
        if (result.rejection == null) autosaveDraft()
    }

    fun markFootInstead(holdId: String) {
        updateSetter { SetterReducer.markFootInstead(it, holdId) }
        autosaveDraft()
    }

    fun undo() {
        updateSetter { SetterReducer.undo(it) }
        autosaveDraft()
    }

    fun redo() {
        updateSetter { SetterReducer.redo(it) }
        autosaveDraft()
    }

    fun clearDraftHolds() {
        updateSetter { SetterReducer.clearAssignments(it) }
        autosaveDraft()
    }

    fun selectRole(role: ProblemHoldRole) = updateSetter { SetterReducer.selectRole(it, role) }

    fun setFeetRule(feetRule: FeetRule) {
        val before = mutableState.value.setter.draft.countFor(ProblemHoldRole.FOOT_ONLY)
        updateSetter { SetterReducer.setFeetRule(it, feetRule) }
        val removed = before - mutableState.value.setter.draft.countFor(ProblemHoldRole.FOOT_ONLY)
        if (removed > 0) {
            emit(BoardEvent.Message("Removed $removed foot mark(s) — campus problems have no feet. Undo restores them."))
        }
        autosaveDraft()
    }

    fun setDraftName(name: String) {
        updateDraft { it.copy(name = name) }
        autosaveDraft()
    }

    fun setDraftGrade(grade: BoulderGrade) {
        updateDraft { it.copy(grade = grade) }
        autosaveDraft()
    }

    fun setDraftAccent(accent: Accent) {
        updateDraft { it.copy(accent = accent) }
        autosaveDraft()
    }

    fun setDraftNote(note: String) {
        updateDraft { it.copy(note = note) }
        autosaveDraft()
    }

    fun toggleDraftTag(tag: String) {
        updateDraft { draft ->
            draft.copy(
                tags = if (tag in draft.tags) draft.tags - tag else draft.tags + tag,
            )
        }
        autosaveDraft()
    }

    fun guidedNext() = updateSetter { SetterReducer.nextStep(it) }

    fun guidedBack() = updateSetter { SetterReducer.previousStep(it) }

    fun goToGuidedStep(step: GuidedStep) = updateSetter { SetterReducer.goToStep(it, step) }

    /** Save whatever is on the wall as a draft (or its previous state) and close the setter. */
    fun saveDraftAndClose() {
        autosaveDraft()
        val saved = mutableState.value.setter.draft.editingProblemId
        mutableState.value = mutableState.value.copy(
            isSetting = false,
            selectedProblemId = saved ?: mutableState.value.selectedProblemId,
        )
    }

    /** Called after the setter explicitly confirms a successful forerun. */
    fun confirmForerunAndPublish() {
        val current = mutableState.value
        val issues = current.draftIssues
        if (ProblemValidator.hasErrors(issues)) {
            emit(BoardEvent.Message("Fix the errors before publishing."))
            return
        }
        val draft = current.setter.draft.copy(forerunConfirmedAt = System.currentTimeMillis())
        val id = draft.editingProblemId ?: newProblemId(draft.name)
        val problem = draft.toProblem(id, PublicationState.PUBLISHED)
        val problems = upsert(current.problems, problem)
        mutableState.value = current.copy(
            problems = problems,
            selectedProblemId = id,
            isSetting = false,
        )
        persist()
        emit(BoardEvent.Message("${problem.name} published."))
    }

    // --- Board setup -------------------------------------------------------------

    fun setKickboardEnabled(enabled: Boolean) = updateSetup { it.withKickboardEnabled(enabled) }

    fun setKickboardBoundary(y: Float) = updateSetup { it.withBoundary(y) }

    fun toggleHoldCapability(holdId: String) = updateSetup { it.withCapabilityToggled(holdId) }

    fun confirmBoardSetup() = updateSetup { it.copy(confirmedAt = System.currentTimeMillis()) }

    fun setGradeSystem(gradeSystem: GradeSystem) {
        mutableState.value = mutableState.value.copy(gradeSystem = gradeSystem)
        persist()
    }

    // --- Internals ---------------------------------------------------------------

    private fun updateSetter(transform: (SetterState) -> SetterState) {
        val current = mutableState.value
        if (!current.isSetting) return
        mutableState.value = current.copy(setter = transform(current.setter))
    }

    private fun updateDraft(transform: (DraftProblem) -> DraftProblem) =
        updateSetter { it.copy(draft = transform(it.draft)) }

    private fun updateProblem(problemId: String, transform: (Problem) -> Problem) {
        val current = mutableState.value
        val problem = current.problems.firstOrNull { it.id == problemId } ?: return
        mutableState.value = current.copy(
            problems = current.problems.map { if (it.id == problemId) transform(problem) else it },
        )
        persist()
    }

    private fun updateSetup(transform: (BoardSetup) -> BoardSetup) {
        val current = mutableState.value
        val setup = transform(current.setup)
        val board = ConfiguredBoard.from(setup)
        val problems = current.problems.map { problem ->
            val issues = ProblemValidator.validate(problem, board)
            val resolved = ProblemValidator.resolveState(problem.publicationState, issues)
            if (resolved == problem.publicationState) problem else problem.copy(publicationState = resolved)
        }
        mutableState.value = current.copy(setup = setup, problems = problems)
        persist()
    }

    /**
     * Persist the in-progress draft as a library record so process death or
     * navigation never discards work. Empty sessions leave no record behind.
     */
    private fun autosaveDraft() {
        val current = mutableState.value
        if (!current.isSetting) return
        val draft = current.setter.draft
        if (!draft.hasContent) return

        val id = draft.editingProblemId ?: newProblemId(draft.name)
        val issues = ProblemValidator.validate(draft.toSpec(), current.board)
        val state = ProblemValidator.resolveState(draft.baseState ?: PublicationState.DRAFT, issues)
        val problem = draft.toProblem(id, state)
        val updatedDraft = draft.copy(editingProblemId = id)
        mutableState.value = current.copy(
            problems = upsert(current.problems, problem),
            setter = current.setter.copy(draft = updatedDraft),
        )
        persist()
    }

    private fun upsert(problems: List<Problem>, problem: Problem): List<Problem> =
        if (problems.any { it.id == problem.id }) {
            problems.map { if (it.id == problem.id) problem else it }
        } else {
            listOf(problem) + problems
        }

    private fun persist() {
        val snapshot = mutableState.value.toSnapshot()
        viewModelScope.launch { store.save(snapshot) }
        cloudSync?.onLocalChanged(snapshot)
    }

    private fun BoardUiState.toSnapshot() = LibrarySnapshot(
        setup = setup,
        problems = problems,
        gradeSystem = gradeSystem,
        setterMode = setterMode,
        unreadable = unreadableRecords,
    )

    private fun emit(event: BoardEvent) {
        mutableEvents.tryEmit(event)
    }

    private fun newProblemId(name: String): String = "${name.slug()}-${System.currentTimeMillis()}"

    private fun String.slug() = lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "problem" }
}
