# Board AF — Android

A small, offline-first app for setting and climbing problems on a single home board.

This repository contains only the native Android application. It intentionally does not include climb timing, attempt logging, or a training-session log.

**Supported viewport: mobile phone only.** Tablet, iPad, and wider layouts are
not targets — do not design or spend future work on them.

## Stack

- Kotlin
- Jetpack Compose with Material 3
- Navigation Compose
- A single `AndroidViewModel` coordinating pure, testable collaborators:
  `ProblemValidator`, `SetterReducer`, `BoardTransforms`, and a `BoardStore`
- Versioned local persistence: a v2 JSON snapshot (kotlinx-serialization-json)
  in DataStore, with a one-way migration that reads the legacy
  `problems_v1` SharedPreferences entry and keeps it as a recovery backup
- Optional cloud sync: Firebase Authentication + Firestore mirroring of the
  local library with three-way merge, conflict copies, and soft-delete
  tombstones (see `docs/firebase-setup.md`); **not yet live** — disabled unless
  `app/google-services.json` exists. Pilot users when enabled: Josh and Taylor.
- Light and dark themes, following the system setting

The app runs fully offline with no account. Cloud sync is opt-in per build
(via `google-services.json`) and per user (via sign-in on the Setup screen),
and is not provisioned for production use yet.

## Domain model

A problem separates four concepts that the first version conflated:

1. where a hold is on the board (`HoldDefinition` + zone),
2. what the physical hold can be used for (`HoldCapability`),
3. the hold's role in one problem (`START`, `REGULAR`, `FOOT_ONLY`, `FINISH`), and
4. the problem-wide feet rule (`MARKED_ONLY`, `OPEN_KICKBOARD`,
   `FEET_FOLLOW_MARKED`, `ANY_FEET`, `CAMPUS`).

Problems carry a lifecycle (`DRAFT`, `NEEDS_REVIEW`, `PUBLISHED`, `BENCHMARK`,
`ARCHIVED`); publishing requires zero validation errors plus an explicit
successful-forerun confirmation. Kickboard holds (h37-h43 on the bundled board)
are foot-only and can never host a start, regular, or finish role.

The wall is adjustable from 0° to 90°, so **wall angle is per-problem data**, not
a board setting: every problem carries its own `angleDegrees` (0–90, default 20,
set on a 5° slider) and it is shown alongside the grade on the problem card, the
board header, and the details summary. Older records decode at the default
incline via the additive `angle` field in `SnapshotCodec`.

## Open and run

1. Open this repository in Android Studio.
2. Let Gradle sync.
3. Select the `app` run configuration and an Android device.
4. Press Run.

The generated debug APK is at:

`app/build/outputs/apk/debug/app-debug.apk`

To build from the terminal:

```bash
./gradlew testDebugUnitTest assembleDebug
```

## Hold alignment

The source photo is 1080 × 1586. `BoardSurface` forces that exact aspect ratio
(`BoardGeometry.IMAGE_ASPECT_RATIO`) and draws the photo with
`ContentScale.FillBounds`. Hold centers are recorded at exact source-photo pixels
and normalized to `0f..1f`, then positioned inside the same Compose box as the
image. Screen width only changes the size of that shared box, so the bitmap and
overlays scale together.

Because the aspect ratio is fixed, callers must never hand `BoardSurface` a box
of arbitrary shape — on the phone layout it is sized width-first (the board fills
the device width and the column scrolls the overflow) so the wall keeps its
proportions instead of stretching.

Pinch zoom and pan transform the photo and marker anchors through the same
`BoardTransform`, clamped so the board can never leave the screen; stored
coordinates stay normalized and interactive targets stay 48 dp at every scale
(Android's minimum touch target). Double-tap or the fit button resets the view,
and the transform survives rotation.

The geometry is covered by `BoardGeometryTest` and `BoardTransformTest` at
phone widths (the only supported viewport).

## Project layout

- `app/` — Android application source, resources, and tests
- `gradle/` and Gradle wrapper files — reproducible Android builds
- `docs/` — project plans and product research

## Setting flow

Two modes are selectable when a session starts, and the choice is remembered as a
preference.

**Quick set** is a single canvas: taps toggle hold membership with no steps and
no gating. Roles are inferred — the lowest tapped row becomes start and the
topmost becomes finish (ties included, so a matched finish falls out naturally),
everything between is regular, and kickboard holds become foot-only because the
board says they can only be feet. The feet rule is inferred too (Marked feet only
when foot marks exist, Feet follow marked otherwise) and stops being inferred
once the setter picks one by hand. Undo/redo operates on the hold set, one entry
per tap; inferred role flips never land on the stack. Every inferred role stays
overridable, and publishing runs the same validation and forerun gate.

**Guided steps** runs a five-step wizard on the
board screen: **feet rule → start holds → other holds → finish holds →
details & review**. New problems start at the feet rule; sessions on existing
problems (anything already in the library) land directly on details & review
and can jump back to any step.

Forward progress is gated per step (one or two start holds, one or two finish
holds); backward navigation and the system back button walk the steps freely.
Taps are guarded: a third start or finish hold is rejected, changing a hold's
existing role is announced in a snackbar, and switching to Campus removes
foot marks undoably. The foot-only palette only appears under feet rules
where marks mean something. Drafts autosave on every action, and publishing
still requires zero validation errors plus a forerun confirmation.

Autosave is deliberately permissive — a single tapped hold is enough to persist
a draft, because autosave must never be the reason work is lost. Untitled drafts
render as "Untitled draft" and can be deleted, which is where library clutter is
handled instead.

## Screen layout

The only supported viewport is **mobile phone**. Tablet, iPad, and large-screen
layouts are out of scope; do not plan or build for them.

On phones the board screen is a `BottomSheetScaffold` over a 200 dp peek that
shows the wizard step chips, active role, and Back/Next, so the wall and its
controls are never mutually exclusive. The board is sized width-first and the
content column scrolls the overflow the peek leaves — a height-first fit kept
everything on screen at once but rendered the wall too small to read or tap.
Dragging the sheet up reveals the details form or the problem's rules. (A
≥ 840 dp side-panel branch may still exist in code; ignore it — phone layout is
the product.)

While viewing (not setting), a horizontal drag across the board moves to the
adjacent problem — 64 dp of travel, damped follow-the-finger feedback, clamped at
the ends rather than wrapping.

## Library

Problems can be searched, filtered by status, grade, angle, feet rule, setter,
and tag, and acted on from the card overflow: edit, duplicate, archive, delete.
Archive is reversible and offers Undo that restores the exact prior state; delete
is permanent and confirms first. The default status filter is **Active**
(everything except archived); a separate **All** includes archived. The angle
filter only appears once the library holds more than one angle.

Filters live in a `ModalBottomSheet` rather than expanding inline: options wrap
in a `FlowRow` so all seven status values are visible at once, and the sheet
dismisses through a "Show N problems" button. Active filters stay visible as
chips on the list itself.
