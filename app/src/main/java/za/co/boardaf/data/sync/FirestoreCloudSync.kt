package za.co.boardaf.data.sync

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import za.co.boardaf.data.LibrarySnapshot
import za.co.boardaf.data.SnapshotCodec

/**
 * Firestore-backed [CloudSync].
 *
 * The local snapshot store stays the source of truth. This engine mirrors it into
 * `boards/{uid}` + `boards/{uid}/problems/{problemId}` and merges remote changes
 * back through [SyncPlanner]. Two safety properties:
 *
 * - Plans only run against **server-acknowledged** snapshots (`isFromCache == false`),
 *   never against Firestore's offline cache. Offline edits stay local until
 *   reconnection, at which point genuine conflicts become conflict copies instead
 *   of being lost to last-write-wins.
 * - Baselines only advance once the server has echoed content back, which is the
 *   remote equivalent of the local store's write-and-read-back verification.
 */
class FirestoreCloudSync(
    context: Context,
    private val baselineStore: BaselineStore = AndroidBaselineStore(context),
) : CloudSync {

    private val appContext = context.applicationContext
    private val configured: Boolean = FirebaseApp.initializeApp(appContext) != null

    private val mutableState = MutableStateFlow(
        CloudSyncState(
            availability = if (configured) CloudSyncAvailability.READY else CloudSyncAvailability.UNCONFIGURED,
        ),
    )
    override val state: StateFlow<CloudSyncState> = mutableState.asStateFlow()

    private val mutableMerged = MutableSharedFlow<MergedLibrary>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val mergedSnapshots: Flow<MergedLibrary> = mutableMerged

    private var scope: CoroutineScope? = null
    private val planMutex = Mutex()

    private var localSnapshot: LibrarySnapshot? = null
    private var remoteLibrary: RemoteLibrary? = null
    private var remoteIsAuthoritative = false
    private var boardDocEnsured = false

    private var boardListener: ListenerRegistration? = null
    private var problemsListener: ListenerRegistration? = null

    private val auth: FirebaseAuth? get() = if (configured) FirebaseAuth.getInstance() else null
    private val db: FirebaseFirestore? get() = if (configured) FirebaseFirestore.getInstance() else null

    override fun start(scope: CoroutineScope) {
        this.scope = scope
        val auth = auth ?: return
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            mutableState.value = mutableState.value.copy(userEmail = user?.email)
            detachListeners()
            if (user != null) attachListeners(user.uid)
        }
    }

    override fun onLocalChanged(snapshot: LibrarySnapshot) {
        localSnapshot = snapshot
        schedulePlan()
    }

    override fun signIn(email: String, password: String) {
        val auth = auth ?: return
        runAuth { auth.signInWithEmailAndPassword(email.trim(), password).await() }
    }

    override fun createAccount(email: String, password: String) {
        val auth = auth ?: return
        runAuth { auth.createUserWithEmailAndPassword(email.trim(), password).await() }
    }

    override fun signOut() {
        remoteLibrary = null
        remoteIsAuthoritative = false
        boardDocEnsured = false
        auth?.signOut()
    }

    override fun syncNow() {
        mutableState.value = mutableState.value.copy(lastError = null)
        schedulePlan()
    }

    // --- Internals -----------------------------------------------------------

    private fun runAuth(block: suspend () -> Unit) {
        val scope = scope ?: return
        mutableState.value = mutableState.value.copy(isAuthBusy = true, lastError = null)
        scope.launch {
            runCatching { block() }
                .onFailure { failure ->
                    mutableState.value = mutableState.value.copy(
                        lastError = failure.message ?: "Authentication failed.",
                    )
                }
            mutableState.value = mutableState.value.copy(isAuthBusy = false)
        }
    }

    private fun attachListeners(uid: String) {
        val db = db ?: return
        val boardDoc = db.collection("boards").document(uid)

        boardListener = boardDoc.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                reportError(error.message ?: "Listening to the board failed.")
                return@addSnapshotListener
            }
            if (snapshot == null || snapshot.metadata.isFromCache) return@addSnapshotListener
            // A payload-less doc (bootstrap {ownerId} write) means "no board synced
            // yet"; only a payload that fails to decode is unreadable.
            val payload = if (snapshot.exists()) snapshot.getString("payload") else null
            val record = payload?.let { decodeBoardDoc(snapshot, it) }
            val current = remoteLibrary ?: RemoteLibrary()
            remoteLibrary = current.copy(
                board = record,
                boardUnreadable = payload != null && record == null,
            )
            schedulePlan()
        }

        problemsListener = boardDoc.collection("problems")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    reportError(error.message ?: "Listening to problems failed.")
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.metadata.isFromCache) return@addSnapshotListener
                val problems = mutableMapOf<String, RemoteProblemRecord>()
                val unreadable = mutableSetOf<String>()
                snapshot.documents.forEach { doc ->
                    val record = decodeProblemDoc(doc)
                    if (record != null) problems[doc.id] = record else unreadable += doc.id
                }
                val current = remoteLibrary ?: RemoteLibrary()
                remoteLibrary = current.copy(
                    problems = problems,
                    unreadableProblemIds = unreadable,
                )
                remoteIsAuthoritative = true
                schedulePlan()
            }
    }

    private fun detachListeners() {
        boardListener?.remove()
        problemsListener?.remove()
        boardListener = null
        problemsListener = null
        remoteLibrary = null
        remoteIsAuthoritative = false
    }

    private fun decodeBoardDoc(doc: DocumentSnapshot, payload: String): RemoteBoardRecord? {
        val decoded = runCatching { SyncCodec.decodeBoard(payload) }.getOrNull() ?: return null
        return RemoteBoardRecord(
            setup = decoded.setup,
            gradeSystem = decoded.gradeSystem,
            setterMode = decoded.setterMode,
            encoded = payload,
            revision = doc.getLong("revision") ?: 0L,
            pendingWrite = doc.metadata.hasPendingWrites(),
        )
    }

    private fun decodeProblemDoc(doc: DocumentSnapshot): RemoteProblemRecord? {
        val payload = doc.getString("payload") ?: return null
        val problem = runCatching { SyncCodec.decodeProblem(payload) }.getOrNull() ?: return null
        return RemoteProblemRecord(
            problem = problem,
            encoded = payload,
            revision = doc.getLong("revision") ?: 0L,
            pendingWrite = doc.metadata.hasPendingWrites(),
        )
    }

    private fun schedulePlan() {
        val scope = scope ?: return
        scope.launch { runPlan() }
    }

    private suspend fun runPlan(): Unit = planMutex.withLock {
        val db = db ?: return
        val uid = auth?.currentUser?.uid ?: return
        val local = localSnapshot ?: return
        val remote = remoteLibrary ?: return
        // Never push against the offline cache; wait for a server snapshot.
        if (!remoteIsAuthoritative) return

        mutableState.value = mutableState.value.copy(isSyncing = true)
        val outcome = runCatching {
            val baselines = baselineStore.read(uid)
            val plan = SyncPlanner.plan(local, remote, baselines, now = System.currentTimeMillis())

            ensureBoardDoc(uid)

            if (plan.problemPushes.isNotEmpty() || plan.boardPush != null) {
                val batch = db.batch()
                val boardDoc = db.collection("boards").document(uid)
                plan.boardPush?.let { push ->
                    batch.set(
                        boardDoc,
                        mapOf(
                            "payload" to SyncCodec.encodeBoard(push.setup, push.gradeSystem, push.setterMode),
                            "schemaVersion" to SyncCodec.SCHEMA_VERSION,
                            "revision" to push.revision,
                            "updatedAt" to FieldValue.serverTimestamp(),
                            "updatedBy" to uid,
                        ),
                        SetOptions.merge(),
                    )
                }
                plan.problemPushes.forEach { push ->
                    batch.set(
                        boardDoc.collection("problems").document(push.problem.id),
                        mapOf(
                            "payload" to SyncCodec.encodeProblem(push.problem),
                            "schemaVersion" to SyncCodec.SCHEMA_VERSION,
                            "revision" to push.revision,
                            "name" to push.problem.name,
                            "state" to push.problem.publicationState.name,
                            "updatedAt" to FieldValue.serverTimestamp(),
                            "updatedBy" to uid,
                        ),
                    )
                }
                batch.commit().await()
            }

            baselineStore.write(uid, plan.baselines)

            plan.mergedSnapshot?.let { merged ->
                mutableMerged.emit(
                    MergedLibrary(
                        snapshot = merged,
                        basedOnFingerprint = SnapshotCodec.encode(local),
                    ),
                )
            }
            plan.issues
        }

        mutableState.value = outcome.fold(
            onSuccess = { issues ->
                mutableState.value.copy(
                    isSyncing = false,
                    lastSyncAt = System.currentTimeMillis(),
                    lastError = issues.firstOrNull(),
                )
            },
            onFailure = { failure ->
                mutableState.value.copy(
                    isSyncing = false,
                    lastError = failure.message ?: "Sync failed.",
                )
            },
        )
    }

    /** Root board doc + owner membership, required by the security rules. */
    private suspend fun ensureBoardDoc(uid: String) {
        if (boardDocEnsured) return
        val db = db ?: return
        val boardDoc = db.collection("boards").document(uid)
        boardDoc.set(mapOf("ownerId" to uid), SetOptions.merge()).await()
        boardDoc.collection("members").document(uid)
            .set(mapOf("role" to "OWNER"), SetOptions.merge()).await()
        boardDocEnsured = true
    }

    private fun reportError(message: String) {
        mutableState.value = mutableState.value.copy(lastError = message)
    }
}
