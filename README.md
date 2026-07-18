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
  local library with three-way merge and conflict copies (see
  `docs/firebase-setup.md`); **not yet live** — disabled unless
  `app/google-services.json` exists. Pilot users when enabled: Josh and Taylor.

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

The source photo is 960 × 1280 (3:4). `BoardSurface` forces that exact aspect ratio and draws the photo with `ContentScale.FillBounds`. Hold centers are recorded at exact source-photo pixels and normalized to `0f..1f`, then positioned inside the same Compose box as the image. Screen width only changes the size of that shared box, so the bitmap and overlays scale together.

Pinch zoom and pan transform the photo and marker anchors through the same
`BoardTransform`, clamped so the board can never leave the screen; stored
coordinates stay normalized and interactive targets stay 44 dp at every scale.
Double-tap or the fit button resets the view.

The geometry is covered by `BoardGeometryTest` and `BoardTransformTest` at
phone, tablet, and large-screen widths.

## Project layout

- `app/` — Android application source, resources, and tests
- `gradle/` and Gradle wrapper files — reproducible Android builds
- `docs/` — project plans and product research
