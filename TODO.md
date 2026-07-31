# TODO

Last updated: 31 July 2026, after the UX remediation pass.
Context: `UX_REVIEW.md` (findings + resolution status), `docs/board-af-improvement-plan.md`.

---

## 1. Blocking — verify the remediation pass

**Nothing from 31 July has been compiled.** Roughly 1,050 lines changed across 22
files with no build and no device run. Everything below this section is
speculative until this is done.

- [ ] `./gradlew testDebugUnitTest assembleDebug`
- [ ] Install on the SM-S931B and re-walk the flows in `UX_REVIEW.md`
- [ ] `./gradlew connectedDebugAndroidTest` — `BoardSurfaceSemanticsTest` now
      asserts 48 dp targets

Most likely compile failures, in order:

- Constructor changes: `LibrarySnapshot`, `SyncPlan`, `RemoteProblemRecord`,
  `BoardEvent.Message`
- `androidx.compose.animation.core.Animatable` in `BoardScreen` — assumed to
  resolve transitively via `compose.foundation`; may need an explicit
  `androidx.compose.animation:animation-core` dependency
- `BottomSheetScaffold` + `SheetValue` experimental API opt-ins

### Behaviour that needs a human eye, not a test

- [ ] **Board size on the phone.** The board is now fitted height-first inside
      whatever the 200 dp sheet peek leaves. That is correct proportionally but
      may read as small. The knob is `sheetPeekHeight` in `BoardScreen`.
- [ ] **Swipe vs. pan conflict.** Swipe-to-change-problem is a parent gesture;
      `BoardSurface` consumes drags once zoomed past 1×. Confirm swiping still
      feels right at 1× and is properly suppressed when zoomed.
- [ ] **48 dp targets on 43 holds.** Larger targets mean more overlap between
      adjacent holds. Check the kickboard cluster (h37–h43) for mis-taps.
- [ ] **Outline-only overlays.** `UnassignedHint` for hand-and-foot holds is now
      a white ring at 0.85 alpha. Verify it reads against pale holds in the photo.

---

## 2. Known incomplete work

### Dark theme (F28) — declared but not finished

`BoardAfTheme` has a dark scheme, but ~44 hardcoded light-palette references
remain across 8 UI files. Worst offenders are `BoardLine` (#D6DCCB) used for
dividers and the DRAFT chip background, which will glare on the #1A1F1C
background.

- [ ] Route `BoardPaper` / `BoardLine` / `BoardMuted` / `BoardDark` through
      `MaterialTheme.colorScheme` or add dark variants
- [ ] Audit `SetupScreen` (23 refs), `BoardScreen` (22), `SetterPanel` (14)
- [ ] Check `StatusChip` in both themes — it pairs hardcoded backgrounds with
      hardcoded foregrounds

### Library filters (F9) — half done

Header count and the active-filter chip summary are fixed. Remaining:

- [ ] Filter rows are still `LazyRow`s nested in a `LazyColumn` — a gesture
      conflict and the source of the mid-option clipping
- [ ] Status went from 5 options to 7 (Active + All + 5 states), so the clipping
      is worse than when it was first reported
- [ ] Consider the bottom-sheet filter surface the review suggested; it dismisses
      back to the results, which is the feedback inline expansion lacks

### Setter attribution (F27)

- [ ] Existing problems still say "You"; `duplicateProblem` does not reassign
- [ ] A one-time migration mapping "You" → the signed-in account is the obvious
      fix, but only once sync is actually live

### State restoration (F12)

- [ ] Board scroll position restores; the Problems list does not. Pick one model
      and apply it to both.

---

## 3. Recommended next

### Tombstone housekeeping

The soft-delete tombstones added on 31 July never expire — `deletedProblemIds`
grows for the life of the install, and the remote `deleted: true` document is
kept forever. Both are small, and retention is the safe default, but:

- [ ] Decide a retirement policy (e.g. drop a local tombstone once the remote
      document has carried `deleted: true` for N days)
- [ ] Consider surfacing deleted problems as a recoverable "Recently deleted"
      view, since the payload is still on the server

### Test coverage gaps

- [ ] No `BoardViewModel` tests at all. `deleteProblem`, `selectAdjacentProblem`,
      `restoreArchived`, and `archiveProblem`'s event payload are untested — and
      the archive-Undo bug was exactly the kind of thing a VM test catches.
- [ ] No test for the `startSetting` autosave-before-restart path
- [ ] `FirestoreCloudSync` tombstone writes are untested (needs a fake)

### Deferred from the original review

- [ ] **F1 follow-through:** the bottom sheet solves the phone case. Re-check the
      tablet ≥ 840 dp branch still makes sense alongside it.
- [ ] **Kickboard slider:** now commits on release with a numeric readout, but a
      boundary change still silently re-validates and can demote a published
      problem to Needs review. Warn before that happens.
- [ ] **Snackbar placement:** the rejection snackbar still renders over the sheet.
      Check it does not cover the peek content.

---

## 4. Out of scope (unchanged)

Per `docs/board-af-improvement-plan.md` P2: no accounts beyond the sync pilot, no
shared walls, comments, beta video, leaderboards, LED/projector support,
limb-specific assignments, heatmaps, or AI hold detection. Cloud sync remains
opt-in per build and is not provisioned for production.
