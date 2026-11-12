# TODO

Last updated: 3 September 2026. Completed items were removed in this pass; what
follows is only what is still open. Context: `UX_REVIEW.md`,
`docs/board-af-improvement-plan.md`, `SETTING_FLOW_COMPARISON.md`
(Board AF vs MoonBoard/Kilter/Tension).

**Target viewport: mobile phone only.** Phone is the sole supported layout.
Tablet, iPad, and any wider (≥ 840 dp) viewport work is out of scope — discard
and ignore it. Do not design, polish, or re-check those branches.

Baseline as of this pass: 243 unit tests and 16 instrumented tests pass
(`./gradlew testDebugUnitTest connectedDebugAndroidTest`), and `lintDebug` is
clean apart from a pre-existing Gradle-version notice.

---

## 1. Open

### Setter attribution (F27)

- [ ] Existing problems still say "You". A one-time migration mapping "You" → the
      signed-in account is still the obvious fix, but only once sync is live.

### Hold detection after a new board photo

- [ ] Setup's Hold map card drags, adds and removes hold centers, and **Suggest
      from photo** proposes a whole map from the board photo with no model and no
      network (`HoldDetector`). Measured on two photos of the wall, re-set between
      them (`HoldDetectorTest`): 40 of 43 mapped holds on the bundled one, and all
      26 spot-checked holds on the re-set one, offering ~59 suggestions for its
      ~60 holds. It is a starting point, not an answer, and it is reviewed before
      it lands.
      What is still open:
      - **A hold bolted across a panel's outer edge, overhanging it**, is missed:
        its blob merges with the room behind the board and is dropped. Four of
        these on the re-set wall. Growing the board region to cover them was tried
        and made things worse — the bright rim merges into the same blob.
      - **Bolt washers on bare t-nuts** are suggested along with the holds: they
        are the right size and roundness, and nothing distinguishes them yet.
        A handful per wall, removed with one tap each.
      - **Two holds of the same colour touching** read as one blob. Watershed on
        the distance transform would split them; nothing does today.
      - **A hold the detector can't see** has to be added by hand. It is deliberately
        never removed on its behalf (`BoardSetup.withSuggestedHolds`).
      - **A re-shoot from a different spot** is still repaired marker by marker.
        A four-corner alignment warping the whole map onto the new frame in one
        go would be a better fit for that than re-detecting.
      (AI hold detection stays out of scope, §2 — the above is all deterministic.)

### `FirestoreCloudSync` tombstone writes are untested

- [ ] **Deliberately deferred:** the class resolves `FirebaseFirestore` /
      `FirebaseAuth` through static `getInstance()` calls, so a fake needs a
      dependency-injection refactor of a data-safety-critical file. The decisions
      that matter (which documents to write, at what revision, and when a
      tombstone retires) live in `SyncPlanner` and have 22 tests; what is left
      untested is the mechanical Firestore batch mapping. Worth doing with the
      refactor, not before it.

### System back button exits the app instead of navigating back

- [ ] Pressing the OS back button closes the app rather than popping to the
      previous in-app screen. It should navigate back through the app's screen
      stack the way in-app back affordances do, only exiting the app once
      there's nowhere left to go back to.

### No seed exercises a kickboard foot hold

- [ ] The three seed starts that sat on kickboard holds were moved onto the main
      board (the validator was correctly rejecting them on a fresh install), and
      no seed's foot marks were on the kickboard to begin with. So nothing in the
      demo library now exercises a kickboard foot hold or the Open kickboard feet
      rule. Worth adding a foot mark on h37/h41 to one seed if the demo should
      show that rule off.

---

## 2. Out of scope (unchanged)

Per `docs/board-af-improvement-plan.md` P2: no accounts beyond the sync pilot, no
shared walls, comments, beta video, leaderboards, LED/projector support,
limb-specific assignments, heatmaps, or AI hold detection. Cloud sync remains
opt-in per build and is not provisioned for production.

Also out of scope: **tablet / iPad / large-screen layouts.** The only target
viewport is mobile phone. Any existing wide-layout code may remain unused;
do not invest further work in it.
