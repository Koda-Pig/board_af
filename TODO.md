# TODO

Last updated: 1 August 2026, after the verification + implementation pass.
Context: `UX_REVIEW.md` (findings + resolution status), `docs/board-af-improvement-plan.md`,
`SETTING_FLOW_COMPARISON.md` (Board AF vs MoonBoard/Kilter/Tension).

**Target viewport: mobile phone only.** Phone is the sole supported layout.
Tablet, iPad, and any wider (≥ 840 dp) viewport work is out of scope — discard
and ignore it. Do not design, polish, or re-check those branches.

---

## 1. Verification — done

The 31 July pass is now compiled, tested, and walked on a device.

- [x] `./gradlew testDebugUnitTest assembleDebug` — passes. None of the predicted
      compile failures were real: the constructor changes, `Animatable`, and the
      `BottomSheetScaffold`/`SheetValue` opt-ins all resolved as written.
- [x] Installed and walked on emulator-5554 (1080x2400 @ 420dpi)
- [x] `./gradlew connectedDebugAndroidTest` — 9/9 pass, including the 48 dp
      target assertions

One real defect surfaced: in climb view holds used `clickable(enabled = false)`,
which still publishes an `OnClick` semantics action, so a screen reader announced
all 43 holds as disabled buttons. `BoardSurface` now omits the modifier entirely
when read-only.

### Behaviour checked on device

- [x] **Board size.** The phone layout is width-first now: the board fills the
      device width and the column scrolls, so board and controls are both
      reachable. (The old height-first fit was proportionally correct but far
      too small — see the comment in `BoardScreen`.)
- [x] **Swipe-between-problems feedback.** `AnimatedContent` slides and fades in
      the swipe direction, and crossfades for selections that arrive from
      elsewhere. Verified Tidepool → Moss line.
- [x] **Swipe vs. pan.** Swipe at 1× works; `BoardSurface` consumes drags once
      zoomed, so the parent gesture is suppressed as intended.
- [x] **48 dp targets on 43 holds.** Kickboard cluster taps land on the intended
      hold; verified h37–h43 individually via the semantics tree.
- [x] **Outline-only overlays.** `UnassignedHint` reads against the pale holds.
- [x] **Colour contrast.** Fixed as part of F28 below.

---

## 2. Known incomplete work

### Dark theme (F28) — done

`BoardPaper` / `BoardLine` / `BoardMuted` / `BoardDark` no longer appear as
hardcoded UI colours. Body and label text routes through
`MaterialTheme.colorScheme.onSurfaceVariant`, dividers and borders through
`outline`, and the grade pill through the new `inverseSurface` /
`inverseOnSurface` roles so it flips with the scheme. `StatusChip` pairs themed
containers with themed foregrounds in both schemes — the `BoardLine` DRAFT chip
that would have glared on `#1A1F1C` is gone. Verified in both schemes on device.

The palette constants remain in `Theme.kt` as scheme inputs, plus the functional
role/accent colours (`Coral`, `Sky`, `Gold`, `Moss`) which are deliberately
scheme-independent so board markers stay distinguishable.

### Library filters (F9) — done

Filters moved to a `ModalBottomSheet`. That removes the `LazyRow`-in-`LazyColumn`
gesture conflict and the mid-option clipping: options now wrap in a `FlowRow`, so
all 7 status values are visible at once. The sheet dismisses back to the results
with a "Show N problems" button, which is the feedback inline expansion lacked.
Active-filter chips stay visible on the list itself.

### Setter attribution (F27) — partly done

- [x] `duplicateProblem` now reassigns the copy to the current setter
- [ ] Existing problems still say "You". A one-time migration mapping "You" → the
      signed-in account is still the obvious fix, but only once sync is live.

### State restoration (F12) — no defect found

Re-tested and could not reproduce. The Problems list restores its scroll offset
across tab switches *and* across activity recreation (verified with
`always_finish_activities 1`: the UI dumps before and after are byte-identical),
and the restored destination comes back too. `rememberLazyListState()` is already
`rememberSaveable`-backed, which is the same model `BoardSurface` uses for its
transform. Nothing to change — treat the original finding as stale.

---

## 3. Recommended next

### Tombstone housekeeping — retirement done

- [x] Local tombstones retire once the remote `deleted: true` document has
      carried a `deletedAt` server timestamp for 30 days
      (`SyncPlanner.TOMBSTONE_RETENTION_MS`). Retired ids are also excluded from
      re-adoption, or retirement would undo itself on the next plan. Tombstones
      with no `deletedAt` (written before the field existed) are retained
      forever, which is the safe default. The remote document is still kept.
- [ ] Surface deleted problems as a recoverable "Recently deleted" view, since
      the payload is still on the server

### Test coverage

- [x] `BoardViewModel` — 14 tests covering `deleteProblem` (tombstone, selection
      hand-off, closing an in-flight edit), `selectAdjacentProblem` (clamping,
      skipping archived, inert while setting), `restoreArchived` vs
      `unarchiveProblem`, and `archiveProblem`'s event payload
- [x] `startSetting` autosave-before-restart, plus autosave on problem selection
- [ ] `FirestoreCloudSync` tombstone writes are still untested. **Deliberately
      deferred:** the class resolves `FirebaseFirestore`/`FirebaseAuth` through
      static `getInstance()` calls, so a fake needs a dependency-injection
      refactor of a data-safety-critical file. The decisions that matter (which
      documents to write, at what revision, and when a tombstone retires) live in
      `SyncPlanner` and now have 22 tests; what is left untested is the
      mechanical Firestore batch mapping. Worth doing with the refactor, not
      before it.

Unit tests: 143 total. Instrumented: 9.

### Deferred from the original review

- [x] **Kickboard slider:** changing the boundary or toggling the kickboard now
      names the published problems it would demote and asks first; cancelling
      restores the slider. Both entry points share the check.
- [ ] **Snackbar placement:** the rejection snackbar still renders over the
      sheet. Check it does not cover the peek content.

~~F1 tablet follow-through~~ — **discarded.** Phone-only; do not re-check or
polish the ≥ 840 dp / tablet / iPad branch.

---

## 4. From the setting-flow comparison (1 Aug 2026)

### Angle as metadata — done

The physical board is adjustable from **0° to 90°**, so angle is real data.

- [x] No global board angle. `BoardDefaults.BOARD_ANGLE_DEGREES` and
      `ConfiguredBoard.angleDegrees` are gone; Setup reads
      "Wall angle · Set per problem (0–90°)".
- [x] Each problem carries its own `angleDegrees` (0–90, default 20), set in the
      setting/details flow on a 5° slider that commits on release
- [x] Shown on the problem card, the board header pill, and the details summary
      alongside the grade
- [x] Library filter by angle, which only appears once more than one angle
      exists in the data
- [x] Additive `angle` field in `SnapshotCodec`; older records decode at the
      default incline and out-of-range values clamp

### Easy mode — done

`SetterMode.QUICK` is a real mode again, selectable next to the wizard and
remembered as a preference. Inference rules as implemented:

- [x] Single canvas: taps toggle hold membership, no steps and no gating
- [x] Kickboard holds become **foot-only** (capability-driven, so it is free)
- [x] The lowest tapped row becomes **start** — *all* holds tied on that row, not
      the closest two. A single-row line is all start, never all finish.
- [x] The topmost tapped row becomes **finish**, again keeping ties, so a matched
      finish falls out naturally
- [x] Everything between becomes **regular**; hand-capable kickboard holds join
      the middle since they cannot legally start or finish
- [x] Feet rule defaults to Marked feet only when foot marks exist and Feet
      follow marked otherwise, and stops being inferred the moment the setter
      picks one by hand (`feetRuleTouched`)
- [x] Roles stay overridable — switching to Guided steps keeps the set and
      re-infers nothing
- [x] Same validation and forerun gate before publishing
- [x] **Undo vs re-inference:** undo/redo operates on the hold set, one step per
      tap. Each history entry stores the assignments that matched that
      membership, so a restored set always has matching roles; the feet rule is
      re-inferred on the jump. Inferred role flips are never pushed onto the
      stack — only user actions.

Covered by 12 tests in `SetterReducerQuickSetTest` and verified on device.

---

## 5. Out of scope (unchanged)

Per `docs/board-af-improvement-plan.md` P2: no accounts beyond the sync pilot, no
shared walls, comments, beta video, leaderboards, LED/projector support,
limb-specific assignments, heatmaps, or AI hold detection. Cloud sync remains
opt-in per build and is not provisioned for production.

Also out of scope: **tablet / iPad / large-screen layouts.** The only target
viewport is mobile phone. Any existing wide-layout code may remain unused;
do not invest further work in it.

---

## 6. Found and fixed during this pass

- **Seed problems started on kickboard holds.** `BoardDefaults.problems` put the
  start of `tidepool` (h43), `moss-line` (h37) and `chalk-ghost` (h40) below the
  default kickboard boundary, where holds are foot-only. The validator correctly
  rejected them, so 3 of the 4 demo problems landed in Needs review on a fresh
  install and the library looked broken on first run.

  Each start moved to the hand-capable main-board hold nearest the same column,
  so the lines keep their shape: h43 → h35 (x 974 → 967), h37 → h36 (x 247 →
  309), h40 → h32 (x 115 → 111). Foot marks were left untouched. All four seeds
  now open Published, guarded by `SeededProblemsTest`.

  `VersionedBoardStoreTest` had encoded the defect as expected behaviour — it
  asserted fresh installs seed a mix of `NEEDS_REVIEW` and `PUBLISHED`. It now
  asserts all four are `PUBLISHED`.

- **No seed uses a kickboard hold any more.** None of the foot marks were on the
  kickboard to begin with, so with the three starts moved off it, nothing in the
  demo library exercises a kickboard foot hold or the Open kickboard feet rule.
  Worth adding a foot mark on h37/h41 to one of the seeds if the demo should
  show that rule off.
