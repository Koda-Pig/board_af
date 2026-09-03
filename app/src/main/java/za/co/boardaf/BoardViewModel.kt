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
import za.co.boardaf.data.AndroidBoardPhotoStore
import za.co.boardaf.data.AndroidSnapshotIO
import za.co.boardaf.data.BoardPhotoStore
import za.co.boardaf.data.BoardStore
import za.co.boardaf.data.DeletedProblem
import za.co.boardaf.data.DeletedProblems
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
import za.co.boardaf.model.ProblemAngle
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
    /** Tombstones for locally deleted problems, propagated by cloud sync. */
    val deletedProblemIds: Set<String> = emptySet(),
    /** Deleted problems still inside the recovery window, newest last. */
    val deletedProblems: List<DeletedProblem> = emptyList(),
    /** Absolute path of the setter's board photo, or null to render the bundled one. */
    val boardPhotoPath: String? = null,
    val cloud: CloudSyncState = CloudSyncState(),
) {
    val board: ConfiguredBoard
        // A photo record whose file has gone missing must not keep shaping the
        // frame: without the path there is nothing to draw but the bundled image,
        // so the bundled image's aspect ratio is the one that applies.
        get() = ConfiguredBoard.from(setup).let { configured ->
            if (boardPhotoPath == null) configured.copy(photo = null) else configured
        }

    val selectedProblem: Problem?
        get() = problems.firstOrNull { it.id == selectedProblemId }
            ?: problems.firstOrNull { it.publicationState != PublicationState.ARCHIVED }
            ?: problems.firstOrNull()

    val draftIssues: List<ProblemIssue>
        get() = ProblemValidator.validate(setter.draft.toSpec(), board)
}

sealed interface BoardEvent {
    data class TapRejected(val rejection: TapRejection) : BoardEvent
    data class Message(
        val text: String,
        val actionLabel: String? = null,
        /**
         * When set, the snackbar action restores this problem to exactly the
         * state it held before it was archived — not a recomputed one.
         */
        val undoArchive: ArchiveUndo? = null,
    ) : BoardEvent

    /** Everything needed to put an archived problem back the way it was. */
    data class ArchiveUndo(val problemId: String, val previousState: PublicationState)
}

class BoardViewModel @JvmOverloads constructor(
    application: Application,
    private val store: BoardStore = VersionedBoardStore(AndroidSnapshotIO(application)),
    private val cloudSync: CloudSync? = runCatching { FirestoreCloudSync(application) }.getOrNull(),
    /** Null when board photos are unavailable on this device; capture is then hidden. */
    private val photoStore: BoardPhotoStore? = runCatching { AndroidBoardPhotoStore(application) }.getOrNull(),
) : AndroidViewModel(application) {

    /** The capture the camera app is writing into right now, if any. */
    private var pendingPhotoFileName: String? = null

    private val mutableState = MutableStateFlow(BoardUiState(isLoading = true))
    val state: StateFlow<BoardUiState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<BoardEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<BoardEvent> = mutableEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            val result = store.load()
            // Recovery payloads expire; the tombstone ids deliberately do not,
            // since they still have a delete to propagate.
            val recoverable = DeletedProblems.pruned(
                result.snapshot.deletedProblems,
                System.currentTimeMillis(),
            )
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
                deletedProblemIds = result.snapshot.deletedProblemIds,
                deletedProblems = recoverable,
            ).withResolvedPhoto()
            if (recoverable != result.snapshot.deletedProblems) {
                persist()
            } else {
                cloudSync?.onLocalChanged(result.snapshot)
            }
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
                    deletedProblemIds = merged.snapshot.deletedProblemIds,
                    deletedProblems = merged.snapshot.deletedProblems,
                ).withResolvedPhoto()
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
        if (mutableState.value.isSetting) autosaveDraft()
        mutableState.value = mutableState.value.copy(
            selectedProblemId = problemId,
            isSetting = false,
        )
    }

    /** Swipe between active (non-archived) problems while viewing. */
    fun selectAdjacentProblem(delta: Int) {
        val current = mutableState.value
        if (current.isSetting) return
        val active = current.problems.filter { it.publicationState != PublicationState.ARCHIVED }
        if (active.size < 2) return
        val selectedId = current.selectedProblem?.id ?: return
        val index = active.indexOfFirst { it.id == selectedId }
        if (index < 0) return
        // Clamped, not wrapped: silently jumping from the last problem back to the
        // first reads as a bug when you can't see the whole list.
        val target = index + delta
        if (target !in active.indices) return
        mutableState.value = current.copy(selectedProblemId = active[target].id)
    }

    fun archiveProblem(problemId: String) {
        val problem = mutableState.value.problems.firstOrNull { it.id == problemId } ?: return
        updateProblem(problemId) {
            it.copy(publicationState = PublicationState.ARCHIVED)
        }
        val label = problem.name.ifBlank { "Untitled draft" }
        emit(
            BoardEvent.Message(
                text = "Archived $label",
                actionLabel = "Undo",
                undoArchive = BoardEvent.ArchiveUndo(problemId, problem.publicationState),
            ),
        )
    }

    /**
     * Exact inverse of [archiveProblem]. Distinct from [unarchiveProblem], which
     * recomputes a state for a problem restored long after the fact — that would
     * silently demote a published problem to draft.
     */
    fun restoreArchived(undo: BoardEvent.ArchiveUndo) = updateProblem(undo.problemId) {
        it.copy(publicationState = undo.previousState)
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

    fun deleteProblem(problemId: String) {
        val current = mutableState.value
        if (current.problems.none { it.id == problemId }) return
        val nextSelected = when (current.selectedProblemId) {
            problemId -> current.problems
                .filter { it.id != problemId && it.publicationState != PublicationState.ARCHIVED }
                .firstOrNull()?.id
                ?: current.problems.firstOrNull { it.id != problemId }?.id
            else -> current.selectedProblemId
        }
        val deleted = current.problems.first { it.id == problemId }
        mutableState.value = current.copy(
            problems = current.problems.filterNot { it.id == problemId },
            // Tombstone, so sync propagates the delete instead of re-adopting
            // the record from the server on the next merge.
            deletedProblemIds = current.deletedProblemIds + problemId,
            // The payload as well, so the delete stays recoverable on this device
            // for DeletedProblems.RETENTION_MS.
            deletedProblems = current.deletedProblems +
                DeletedProblem(deleted, System.currentTimeMillis()),
            selectedProblemId = nextSelected,
            isSetting = if (current.isSetting && current.setter.draft.editingProblemId == problemId) {
                false
            } else {
                current.isSetting
            },
        )
        persist()
        emit(
            BoardEvent.Message(
                "Deleted ${deleted.name.ifBlank { "Untitled draft" }} — recoverable from Recently deleted.",
            ),
        )
    }

    /**
     * Put a deleted problem back. Deliberately under a *new* id: SyncPlanner's
     * rule is that a tombstone on either side wins over any surviving copy, so a
     * restore that reused the old id would be deleted again by the next merge.
     * The tombstone therefore stays; only the payload leaves the recovery list.
     */
    fun restoreDeletedProblem(problemId: String) {
        val current = mutableState.value
        val record = current.deletedProblems.firstOrNull { it.problem.id == problemId } ?: return
        val id = newProblemId(record.problem.name)
        // Restored long after the fact, so the state is recomputed rather than
        // taken at face value: the board may have been reconfigured since.
        val issues = ProblemValidator.validate(record.problem, current.board)
        val restored = record.problem.copy(
            id = id,
            publicationState = ProblemValidator.resolveState(record.problem.publicationState, issues),
        )
        mutableState.value = current.copy(
            problems = listOf(restored) + current.problems,
            deletedProblems = current.deletedProblems.filterNot { it.problem.id == problemId },
        )
        persist()
        emit(BoardEvent.Message("${restored.name.ifBlank { "Untitled draft" }} restored."))
    }

    /**
     * Give up the ability to recover one deleted problem. The tombstone stays:
     * dropping it would let the next sync re-adopt the record from the server.
     */
    fun forgetDeletedProblem(problemId: String) {
        val current = mutableState.value
        if (current.deletedProblems.none { it.problem.id == problemId }) return
        mutableState.value = current.copy(
            deletedProblems = current.deletedProblems.filterNot { it.problem.id == problemId },
        )
        persist()
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
        val label = problem.name.ifBlank { "Untitled draft" }
        emit(BoardEvent.Message("$label published."))
    }

    // --- Setter session ----------------------------------------------------------

    fun startSetting() {
        // Never drop a live session on the floor; the "+" confirms before landing here.
        if (mutableState.value.isSetting) autosaveDraft()
        val current = mutableState.value
        mutableState.value = current.copy(
            isSetting = true,
            setter = SetterReducer.start(DraftProblem(setter = currentSetterName()), current.setterMode),
        )
    }

    fun startEditing(problemId: String) {
        val current = mutableState.value
        val problem = current.problems.firstOrNull { it.id == problemId } ?: return
        mutableState.value = current.copy(
            isSetting = true,
            selectedProblemId = problemId,
            setter = SetterReducer.start(DraftProblem.fromProblem(problem), current.setterMode),
        )
    }

    fun duplicateProblem(problemId: String) {
        val current = mutableState.value
        val problem = current.problems.firstOrNull { it.id == problemId } ?: return
        // The copy is this setter's, not the original author's (F27).
        val draft = DraftProblem.duplicateOf(problem).copy(setter = currentSetterName())
        mutableState.value = current.copy(
            isSetting = true,
            setter = SetterReducer.start(draft, current.setterMode),
        )
        autosaveDraft()
    }

    /** Switch quick/guided entry for this session and remember it as the preference. */
    fun setSetterMode(mode: SetterMode) {
        val current = mutableState.value
        if (current.setterMode == mode && (!current.isSetting || current.setter.mode == mode)) return
        mutableState.value = current.copy(
            setterMode = mode,
            setter = if (current.isSetting) SetterReducer.setMode(current.setter, mode) else current.setter,
        )
        persist()
    }

    fun cancelSetting() {
        val current = mutableState.value
        if (!current.isSetting) return
        autosaveDraft()
        val draftId = mutableState.value.setter.draft.editingProblemId
        mutableState.value = mutableState.value.copy(
            isSetting = false,
            selectedProblemId = draftId ?: mutableState.value.selectedProblemId,
        )
    }

    fun tapHold(holdId: String) {
        val current = mutableState.value
        if (!current.isSetting) return
        val result = if (current.setter.mode == SetterMode.QUICK) {
            SetterReducer.quickTapHold(current.setter, holdId, current.board)
        } else {
            SetterReducer.tapHold(current.setter, holdId, current.board)
        }
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

    fun setDraftAngle(angleDegrees: Int) {
        updateDraft { it.copy(angleDegrees = ProblemAngle.clamp(angleDegrees)) }
        autosaveDraft()
    }

    fun setDraftAccent(accent: Accent) {
        updateDraft { it.copy(accent = accent) }
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

    /** Commit kickboard boundary once (not on every drag frame). */
    fun setKickboardBoundary(y: Float) = updateSetup { it.withBoundary(y) }

    fun toggleHoldCapability(holdId: String) = updateSetup { it.withCapabilityToggled(holdId) }

    fun confirmBoardSetup() = updateSetup { it.copy(confirmedAt = System.currentTimeMillis()) }

    // --- Board photo ---------------------------------------------------------------

    /**
     * Hand the camera app somewhere to write. Returns the URI string to launch
     * with, or null when no destination could be prepared. The result is reported
     * back through [completeBoardPhotoCapture] — the camera app owns the file
     * until then, so nothing is adopted here.
     */
    fun beginBoardPhotoCapture(): String? {
        val store = photoStore ?: return null
        // A previous capture that never reported back left an empty file behind.
        pendingPhotoFileName?.let(store::delete)
        pendingPhotoFileName = null

        val fileName = store.createPending() ?: return null
        val uri = store.shareUri(fileName)
        if (uri == null) {
            store.delete(fileName)
            return null
        }
        pendingPhotoFileName = fileName
        return uri
    }

    /** Adopt the pending capture, or clean it up when the camera reported failure. */
    fun completeBoardPhotoCapture(saved: Boolean) {
        val store = photoStore ?: return
        val fileName = pendingPhotoFileName ?: return
        pendingPhotoFileName = null

        if (!saved) {
            store.delete(fileName)
            return
        }
        val photo = store.adopt(fileName)
        if (photo == null) {
            emit(BoardEvent.Message("That photo couldn't be read. The board is unchanged."))
            return
        }
        // Only once the replacement is safely adopted is the old file expendable.
        val previous = mutableState.value.setup.photo
        updateSetup { it.copy(photo = photo) }
        if (previous != null && previous.fileName != photo.fileName) store.delete(previous.fileName)
        emit(BoardEvent.Message("Board photo updated."))
    }

    /** Go back to the photo bundled with the app. */
    fun clearBoardPhoto() {
        val previous = mutableState.value.setup.photo ?: return
        updateSetup { it.copy(photo = null) }
        photoStore?.delete(previous.fileName)
        emit(BoardEvent.Message("Using the bundled board photo."))
    }

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
        mutableState.value = current.copy(setup = setup, problems = problems).withResolvedPhoto()
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
        deletedProblemIds = deletedProblemIds,
        deletedProblems = deletedProblems,
    )

    private fun BoardUiState.withResolvedPhoto(): BoardUiState =
        copy(boardPhotoPath = setup.photo?.let { photoStore?.pathFor(it) })

    private fun emit(event: BoardEvent) {
        mutableEvents.tryEmit(event)
    }

    private fun newProblemId(name: String): String = "${name.slug()}-${System.currentTimeMillis()}"

    private fun String.slug() = lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "problem" }

    /** Prefer the signed-in account's local-part so synced libraries don't all say "You". */
    private fun currentSetterName(): String {
        val email = mutableState.value.cloud.userEmail?.substringBefore('@')?.trim().orEmpty()
        return email.ifBlank { "You" }
    }
}
