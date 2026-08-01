# TODO

Last updated: 1 August 2026, after the setting-flow comparison.
Context: `UX_REVIEW.md` (findings + resolution status), `docs/board-af-improvement-plan.md`,
`SETTING_FLOW_COMPARISON.md` (Board AF vs MoonBoard/Kilter/Tension).

**Target viewport: mobile phone only.** Phone is the sole supported layout.
Tablet, iPad, and any wider (≥ 840 dp) viewport work is out of scope — discard
and ignore it. Do not design, polish, or re-check those branches.

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

- [ ] **Board size on the phone — make it bigger.** The board should take up
      most of the device width; as it stands it's too small. Height-first fitting
      inside the 200 dp sheet peek is proportionally correct but underuses the
      screen. Prefer a larger board (most of the width) and allow the screen to
      be slightly scrollable so board and controls can both be reached — not
      board-only or controls-only. Knob is still `sheetPeekHeight` in
      `BoardScreen`, but layout may need more than peek-height tuning.
- [ ] **Swipe-between-problems feedback.** Swiping sideways to change problem
      (not just screen-to-screen) works functionally, but there's no animation
      or other cue that you've changed problems. Add a subtle indication —
      e.g. a light slide/fade — so the transition is visible.
- [ ] **Swipe vs. pan conflict.** Swipe-to-change-problem is a parent gesture;
      `BoardSurface` consumes drags once zoomed past 1×. Confirm swiping still
      feels right at 1× and is properly suppressed when zoomed.
- [ ] **48 dp targets on 43 holds.** Larger targets mean more overlap between
      adjacent holds. Check the kickboard cluster (h37–h43) for mis-taps.
- [ ] **Outline-only overlays.** `UnassignedHint` for hand-and-foot holds is now
      a white ring at 0.85 alpha. Verify it reads against pale holds in the photo.
- [ ] **Color contrast.** Run a contrast check on text vs background (and
      chips/dividers where text sits on tinted surfaces). Tweak colors so
      foreground/background pairs meet sufficient contrast — especially
      anywhere hardcoded light-palette values meet pale or dark surfaces.

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

- [ ] **Kickboard slider:** now commits on release with a numeric readout, but a
      boundary change still silently re-validates and can demote a published
      problem to Needs review. Warn before that happens.
- [ ] **Snackbar placement:** the rejection snackbar still renders over the sheet.
      Check it does not cover the peek content.

~~F1 tablet follow-through~~ — **discarded.** Phone-only; do not re-check or
polish the ≥ 840 dp / tablet / iPad branch.

---

## 4. From the setting-flow comparison (1 Aug 2026)

Two features adopted from `SETTING_FLOW_COMPARISON.md`. The report's other
suggestions are explicitly **not** planned: mirroring/reflection assists don't
work on this asymmetric wall, and discovery structures / circuits / training
plans are out of scope (see section 5).

### Angle as metadata

The physical board is adjustable from **0° to 90°**, so angle is real data,
not a fixed property of the wall. **No global board angle** — remove any
Setup / wall-level angle. Angle lives only on each problem (set when creating
or editing that problem).

- [ ] Do **not** record a current board angle in Setup; drop that idea if it
      appears in plans or UI sketches
- [ ] Each problem carries its own angle (0–90°) — the incline it was set
      (and forerun) at; grades only mean something relative to that incline
- [ ] Adjust angle per problem in the setting / details flow, not globally
- [ ] Show the angle on the problem card / rules summary, alongside grade
- [ ] Library filter by angle once more than one angle exists in the data

### Easy mode — quick set for experienced setters

The commercial apps' *default* flow is a single canvas where you just tap
holds (Kilter/Tension: tap cycles the role; MoonBoard: tap marks the hold) —
no steps, no gating. Add an equivalent fast path next to the wizard, not
replacing it.

First-thought inference rules (**parameters subject to change**):

- [ ] Single-canvas mode: tap-tap-tap the holds you want in the problem
- [ ] Kickboard holds (h37–h43) automatically become **foot-only** — the
      capability model already forces this, so it's inference for free
- [ ] Lowest tapped hold(s) — first row of the line — automatically become
      **start**. If two (or more) holds share that lowest row, **all of them
      become start** (not "pick the closest two")
- [ ] Topmost tapped hold(s) become **finish**. If more than one hold sits on
      that top row, they are **two (or more) finish holds** — matched finish,
      not a single finish plus regulars
- [ ] Everything in between becomes **regular**
- [ ] Feet rule defaults sensibly (probably Marked feet only when kickboard
      feet were tapped, Feet follow marked otherwise) and stays editable in
      review
- [ ] Roles remain overridable afterwards — inference is a starting point, and
      the existing wizard/step-jumping stays as the guided/precise mode
- [ ] Same validation + forerun gate before publishing; easy mode changes how
      roles are *entered*, not what Published requires
- [ ] **Undo vs re-inference:** undo/redo operates on the hold *set* (each tap
      or untap is one step). After undoing/redoing a membership change, re-run
      inference on the restored set so roles always match the current holds.
      Manual role overrides are separate undoable steps and stick until the
      hold leaves the set or the user changes membership again (membership
      change re-infers the whole set and clears overrides). Do not push
      inferred role flips onto the undo stack — only user actions.

---

## 5. Out of scope (unchanged)

Per `docs/board-af-improvement-plan.md` P2: no accounts beyond the sync pilot, no
shared walls, comments, beta video, leaderboards, LED/projector support,
limb-specific assignments, heatmaps, or AI hold detection. Cloud sync remains
opt-in per build and is not provisioned for production.

Also out of scope: **tablet / iPad / large-screen layouts.** The only target
viewport is mobile phone. Any existing wide-layout code may remain unused;
do not invest further work in it.
