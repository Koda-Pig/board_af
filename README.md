# Board AF — Android

A small, offline-first app for setting and climbing problems on a single home board.

This repository contains only the native Android application. It intentionally does not include climb timing, attempt logging, or a training-session log.

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
of arbitrary shape — on the phone layout it is sized height-first and centred so
the wall keeps its proportions instead of stretching.

Pinch zoom and pan transform the photo and marker anchors through the same
`BoardTransform`, clamped so the board can never leave the screen; stored
coordinates stay normalized and interactive targets stay 48 dp at every scale
(Android's minimum touch target). Double-tap or the fit button resets the view,
and the transform survives rotation.

The geometry is covered by `BoardGeometryTest` and `BoardTransformTest` at
phone, tablet, and large-screen widths.

## Project layout

- `app/` — Android application source, resources, and tests
- `gradle/` and Gradle wrapper files — reproducible Android builds
- `docs/` — project plans and product research

## Setting flow

Creating, editing, or duplicating a problem runs a five-step wizard on the
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

On phones the board screen is a `BottomSheetScaffold`: the board keeps the
viewport above a 200 dp peek that shows the wizard step chips, active role, and
Back/Next, so the wall and its controls are never mutually exclusive. Dragging
the sheet up reveals the details form or the problem's rules. At ≥ 840 dp the
board and a 400 dp side panel sit in a `Row` instead.

While viewing (not setting), a horizontal drag across the board moves to the
adjacent problem — 64 dp of travel, damped follow-the-finger feedback, clamped at
the ends rather than wrapping.

## Library

Problems can be searched, filtered by status, grade, feet rule, setter, and tag,
and acted on from the card overflow: edit, duplicate, archive, delete. Archive is
reversible and offers Undo that restores the exact prior state; delete is
permanent and confirms first. The default status filter is **Active**
(everything except archived); a separate **All** includes archived.
