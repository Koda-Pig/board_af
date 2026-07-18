# Board AF improvement plan

Status: Implemented through P1 (18 July 2026)  
Research basis: `docs/board-climbing-apps-route-setting-research.docx` (18 July 2026)  
Scope: Native Android app, one fixed home board, offline-first

## Implementation notes (18 July 2026)

P0.1-P0.4 and P1.1-P1.3 are implemented. Notes on interpretation:

- The v2 snapshot uses the `kotlinx-serialization-json` runtime with an explicit
  `JsonElement` codec (no compiler plugin) so records decode independently and
  unknown values fail per-record instead of per-library. Storage is DataStore
  (Preferences) behind `SnapshotIO`; the legacy `problems_v1` SharedPreferences
  entry is read-only and retained permanently as the recovery backup.
- "Undo restores both assignment and role" is implemented as: undoing a
  role-replacement returns the hold to its previous role. The palette selection
  itself is not an undoable action.
- Seed data for fresh installs passes through the same status resolution as
  migration, so the three invalid samples arrive as `NEEDS_REVIEW` there too.
- Instrumented Compose tests exist under `app/src/androidTest` (marker
  semantics, 44 dp targets, rule banner copy) and compile; run them with
  `./gradlew connectedDebugAndroidTest` on a connected device.

## Outcome

The next version should make every problem unambiguous and impossible to author in an invalid physical state. The critical change is to separate:

1. where a hold is on the board;
2. what that physical hold can be used for;
3. the hold's role in one problem; and
4. the feet rule that applies to the whole problem.

The current app compresses those concepts into `HoldRole`. That allows kickboard holds to become hand starts or finishes, labels normal holds as hand-only, and leaves climbers to infer whether unmarked kicker holds are allowed. Correcting that model is P0. Authoring speed, recovery, and library improvements follow in P1. Accounts, sharing, leaderboards, and AI should remain out of scope until the rules are correct and clear.

## What should change

| Area | Current project | Change |
| --- | --- | --- |
| Board model | `HoldDefinition` has only an ID and normalized point. | Add board zones and hold capabilities. Configure h01-h36 as main-board, hand-and-foot holds and h37-h43 as kickboard, foot-only holds after owner confirmation. |
| Problem roles | `START`, `HAND`, `FOOT`, `FINISH`. | Rename `HAND` to `REGULAR` and `FOOT` to `FOOT_ONLY`. Keep problem roles separate from physical capability. |
| Grading | The in-progress `BoulderGrade`/`GradeSystem` work adds canonical grades with French and V-scale display choices. | Retain that work, label grades as setter estimates, and carry the canonical grade through the versioned migration and library filters. |
| Feet rules | No problem-level rule. | Require `MARKED_ONLY`, `OPEN_KICKBOARD`, `FEET_FOLLOW_MARKED`, `ANY_FEET`, or `CAMPUS`; default new and migrated problems to `MARKED_ONLY`. |
| Validation | `DraftProblem.canSave` checks only a name, two holds, a start, and a finish. | Add a domain validator for hold capability, zone, role uniqueness, one/two starts, one/two finishes, and feet-rule consistency. |
| Lifecycle | Saving immediately adds a problem to the main library. | Add `DRAFT`, `NEEDS_REVIEW`, `PUBLISHED`, `BENCHMARK`, and `ARCHIVED`, with explicit forerun confirmation before publishing new problems. |
| Persistence | One unversioned `SharedPreferences` JSON array; one bad record or enum rename falls back to seed data. | Introduce a versioned local store and an explicit v1-to-v2 migration that never silently drops or rewrites user data. |
| Board markers | Some shapes exist, but colors do not match the research palette and labels are missing. | Use Moss start + dot/S, Sky regular + solid ring, Gold foot-only + dash/F, and Coral finish + double ring/check. Preserve a 44 dp target. |
| Setter | Metadata and role controls sit below the board; taps only add/remove assignments. | Put feet rule and role palette next to the board, keep the active role persistent, show role counts, and support change-role plus undo. |
| Climb view | Shows title, grade, note, generic legend, and holds. | Add a plain-language feet-rule banner and one/two start/finish explanation before the board. Keep editing affordances hidden. |
| Setup | Static photo and static values. | Show and edit the kickboard boundary, zone classification, and hold capability. Support boards with no kickboard. |
| Accessibility | Hold semantics announce only ID and assigned role; the legend uses colored dots. | Announce ID, zone, capability, and role; use the actual marker glyphs in the legend; add contrast halos and grayscale-distinct cues. |
| Tests | Geometry unit tests only. | Add migration, validation, reducer/history, accessibility semantics, UI, and zoom/pan invariant tests. |

## Product decisions for this iteration

- Use **problem** as the primary product term. Use route only as explanatory copy where helpful.
- Treat regular holds as usable by hands and feet. `FOOT_ONLY` is the restrictive role.
- Treat the kickboard as optional board configuration, not a hard-coded y-coordinate in problem logic.
- Store an explicit zone and capability on each mapped hold. The editable zone boundary helps setup and visualization, but validation reads the stored hold classification.
- Default to **Marked feet only**. This preserves the closest interpretation of existing Board AF problems.
- Permit one or two starts and one or two finishes. One means match/control one; two means split/control both.
- Save incomplete work as a draft. Publishing requires valid rules plus a setter-confirmed forerun.
- Keep the app offline and account-free.
- Do not restore timed attempts or session logging. A lightweight project/warm-up/benchmark tag can be considered without reintroducing the retired timer feature.
- Do not add Room yet. A versioned snapshot behind a storage interface is sufficient for one board and a small library. Revisit relational storage when multiple boards or sync become real requirements.

## Target domain model

The names below are implementation guidance; exact file boundaries can change during development.

```text
BoardDefinition {
  id, name, image, angle, zones, holds
}

BoardZoneDefinition {
  id, type: MAIN | KICKBOARD,
  boundary/polygon, defaultCapability
}

BoardHold {
  id, normalizedPoint, zoneId,
  capability: HAND_AND_FOOT | FOOT_ONLY
}

Problem {
  id, name, gradeEstimate, accent, setter, note, tags,
  feetRule, startRule, finishRule, publicationState,
  forerunConfirmedAt, assignments
}

ProblemAssignment {
  holdId,
  role: START | REGULAR | FOOT_ONLY | FINISH
}
```

`ProblemValidator` should return typed issues rather than one Boolean. Each issue should contain a stable code, severity, affected hold when relevant, and user-facing repair text. Drafts may contain warnings; publishing must have no errors.

Required rules:

- Exactly one or two starts; every start is hand-capable and outside the kickboard.
- Exactly one or two finishes; every finish is hand-capable and outside the kickboard.
- A hold has at most one explicit role in a problem.
- `START`, `REGULAR`, and `FINISH` require `HAND_AND_FOOT` capability.
- `FOOT_ONLY` requires a foot-capable hold.
- `CAMPUS` contains no `FOOT_ONLY` assignments.
- `OPEN_KICKBOARD` is unavailable when the board has no kickboard.
- Publishing requires valid data and explicit successful-forerun confirmation.

## Safe data migration

Migration must land before removing the legacy enum names because `HoldRole.valueOf()` currently makes renamed values unreadable.

1. Introduce a `BoardStore` interface and a versioned v2 snapshot serialized with `kotlinx.serialization` and persisted locally with DataStore.
2. On first v2 load, read `problems_v1` without changing it. Keep it as a recovery backup until the v2 snapshot has been written and read back successfully.
3. Decode records independently so one malformed problem does not replace the whole library with sample data.
4. Map legacy values:
   - `HAND` -> `REGULAR`
   - `FOOT` -> `FOOT_ONLY`
   - no feet rule -> `MARKED_ONLY`
   - one/two start assignments -> `MATCH_ONE`/`SPLIT_TWO`
   - one/two finish assignments -> `MATCH_ONE`/`CONTROL_TWO`
5. Preserve IDs, names, canonical grades, accents, setters, notes, and every hold assignment. Continue accepting legacy V labels and current `BoulderGrade` enum names during decoding.
6. Classify h37-h43 as kickboard + foot-only and h01-h36 as main + hand-and-foot for the bundled board.
7. Mark Tidepool, Moss line, and Chalk ghost `NEEDS_REVIEW` because their starts are on h43, h37, and h40 respectively. Keep the invalid assignments visible until a setter repairs them.
8. Keep Golden hour published and map its legacy h35 foot assignment to explicit `FOOT_ONLY` under `MARKED_ONLY`.
9. Surface migration errors in the UI and retain the source payload; never silently reset to `BoardDefaults.problems` after user data exists.

## Delivery plan

### P0.1 - Make the domain correct

Deliverables:

- Split board configuration, problem data, and validation out of the current `BoardModels.kt` as the model grows.
- Add `BoardZone`, `HoldCapability`, `ProblemHoldRole`, `FeetRule`, `StartRule`, `FinishRule`, and `PublicationState`.
- Add the bundled board definition and explicit classifications for all 43 holds.
- Replace `DraftProblem.canSave` with `ProblemValidator` results.
- Add pure unit tests for every validation rule and the exact h36/h37 zone boundary transition.

Acceptance:

- Invalid kickboard starts/finishes cannot validate as publishable.
- One- and two-hold start/finish conventions validate correctly.
- Board geometry remains configurable enough to represent a board without a kickboard.

### P0.2 - Migrate persistence without losing data

Deliverables:

- Add the versioned store and v1 migration described above.
- Keep storage APIs independent from Compose and the Android screens.
- Return migration/decoding results explicitly instead of falling back to seeds on any exception.
- Add repository tests using legacy, partially corrupt, and already-migrated fixtures.

Acceptance:

- A real v1 payload loads with the same number of problems and assignments.
- Legacy V-scale grades and current canonical grade values resolve to the same `BoulderGrade` values after migration.
- Re-running migration is idempotent.
- The three conflicting samples become `NEEDS_REVIEW`; their metadata and holds remain intact.
- A corrupt record is reported and retained for recovery without erasing valid records.

### P0.3 - Make rules visible and markers semantic

Deliverables:

- Update `BoardSurface` to use the research palette and non-color glyphs.
- Add a shared marker component used by the board, legend, role palette, and previews.
- Add a persistent rule banner such as `Marked feet only - 2 selected feet` to setter, detail, and climb states.
- Explain one/two start and finish behavior in climb view.
- Expand accessibility descriptions to include hold ID, zone, capability, assignment, and selected state.

Acceptance:

- All four roles can be identified in grayscale without reading the color.
- The legend exactly matches board glyphs.
- The feet rule is visible without opening a menu.
- Marker visual size may scale with role, but every interactive target remains at least 44 dp.

### P0.4 - Enforce kickboard behavior in setting and setup

Deliverables:

- Replace the static Setup screen with a board-configuration view that displays the boundary and each hold's zone/capability.
- Allow the owner to adjust the boundary and correct exceptional hold classifications.
- When Start, Regular, or Finish is active, tapping a foot-only kicker must not change the draft. Explain why, provide haptic feedback, and offer `Mark as foot instead`.
- Make `OPEN_KICKBOARD` unavailable for boards without a kickboard.

Acceptance:

- The current board initially shows h37-h43 in the kickboard and h01-h36 on the main board.
- No tap path can introduce an invalid hand role on a foot-only hold.
- A no-kickboard board configuration is valid and does not show kicker settings.

### P1.1 - Make setting fast and recoverable

Deliverables:

- Keep the board dominant and move feet rule, role palette, counts, undo, and review actions into the lower thumb zone.
- Change tap behavior: same active role removes an assignment; a different active role replaces it and creates an undoable action.
- Add bounded undo/redo history, clear confirmation, draft autosave, edit, and duplicate.
- Save incomplete work as `DRAFT`; add a review screen that groups errors and warnings by the fix required.
- Add a four-step guided path for new setters: feet rule -> start -> finish -> regular/feet. Keep Quick setter available for experienced users.

Acceptance:

- Process death or navigation does not discard an autosaved draft.
- Undo restores both assignment and role.
- Clear requires confirmation when a draft contains holds.
- A setter can switch between guided and quick modes without losing work.

### P1.2 - Add zoom and pan without breaking geometry

Deliverables:

- Add pinch zoom and pan around the shared photo/overlay coordinate space.
- Keep stored coordinates normalized and marker anchors transformed with the image.
- Keep interactive targets screen-sized at 44 dp rather than shrinking with the photo.
- Clamp transforms so the board cannot be lost off-screen; provide reset-to-fit.

Acceptance:

- Normalized hold coordinates never change during zoom/pan.
- Markers stay centered on the same holds at phone, tablet, and large widths.
- Automated transform tests cover scale, pan, reset, and edge clamping.

### P1.3 - Add lifecycle-aware library tools

Deliverables:

- Show Draft, Needs review, Published, Benchmark, and Archived status in the library.
- Add filters for grade, setter, status, tags, and feet rule; retain name search.
- Add edit, duplicate, archive, and repair actions.
- Keep decorative problem accent separate from semantic role colors.

Acceptance:

- Needs-review problems remain viewable but cannot be republished until fixed.
- Filters work entirely offline and persist across navigation.
- The library communicates provisional grade as a setter estimate.

### P2 - Defer until P0/P1 are proven

Do not build these in the next iteration:

- accounts, shared walls, comments, beta video, playlists, grade consensus, or leaderboards;
- projector/LED support and limb-specific left/right assignments;
- hold-use heatmaps and generation;
- AI hold detection for the already mapped fixed wall.

Reconsider them only after observing real use of rule selection, migration repair, setting, and climb view. If user-created boards become a goal, revisit multi-board persistence, calibration, polygon zones, and hold detection together.

## Code organization changes

Suggested destinations as work lands:

- `model/BoardDefinition.kt` - zones, hold definitions, capability, bundled board.
- `model/ProblemModels.kt` - problem, assignments, rules, lifecycle, draft.
- `model/ProblemValidator.kt` - pure validation and repair messages.
- `data/BoardStore.kt` - storage interface and versioned snapshot.
- `data/LegacyProblemMigration.kt` - isolated v1 decoder and v2 mapping.
- `setter/SetterReducer.kt` - pure assign/change/remove/undo/redo behavior.
- `ui/BoardSurface.kt` - photo, transforms, zone overlay, marker anchors.
- `ui/ProblemMarker.kt` - one semantic marker/legend implementation.
- Split the current large `Screens.kt` by Board, Setter, Problems, and Setup when each area is touched; do not perform an unrelated one-shot rewrite.

Keep `BoardViewModel` as the screen coordinator for now, but move validation, storage, and setter history into testable collaborators. Add more ViewModels only if navigation state becomes independently owned.

## Verification strategy

Each mergeable slice should run:

```bash
./gradlew testDebugUnitTest assembleDebug
```

Before completing P0 and P1, also run instrumented Compose tests on a connected emulator/device.

Test layers:

- **Domain:** validation matrix for every zone/capability/role/rule combination.
- **Migration:** v1 fixtures, corrupt-record handling, idempotence, and sample conflict statuses.
- **Setter reducer:** add, remove, replace role, undo, redo, clear, and autosave restoration.
- **Geometry:** existing normalized-coordinate tests plus zoom/pan transforms and zone classification.
- **Compose:** rule banner, status/error copy, marker semantics, 44 dp targets, and invalid-kicker shortcut.
- **Responsive UI:** phone setter, tablet split view, large-width board alignment, and accessibility font scaling.
- **Offline:** cold launch, create, edit, browse, and view with no account, network permission, or network dependency.

## Release definition of done

P0 is ready when:

- saved v1 libraries migrate with no silent data loss;
- h37-h43 are visibly and semantically kickboard holds after owner confirmation;
- invalid legacy samples are clearly marked for repair;
- every new problem has an explicit feet rule;
- marker roles are consistent and understandable without color;
- one/two starts and finishes are validated and explained;
- the app remains fully offline; and
- unit tests and the debug build pass.

P1 is ready when a setter can start, recover, forerun, repair, and publish a problem without losing work, while zooming and panning the board without marker drift.
