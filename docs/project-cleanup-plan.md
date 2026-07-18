# Project cleanup plan

Status: Completed on 18 July 2026

## Goal

Keep Board AF as a focused Android application by removing the retired web prototype and the unused timed climb/attempt logging feature.

## Verified scope

- The Gradle build includes only the `:app` Android module.
- The Vite files (`index.html`, `package.json`, `src/`, and `public/`) formed a self-contained web prototype and were not referenced by Gradle or Android source code.
- Android uses its own board image at `app/src/main/res/drawable-nodpi/home_board.png`, so the web copy is not required.
- Before cleanup, timed climb logging spanned the Start climb button, timer sheet, attempt/session UI, view-model state, model types, and SharedPreferences serialization.

## Changes

- [x] Remove the Vite/React source, package manifests, web-only image, and generated web output/dependencies.
- [x] Remove the Start climb action, timer sheet, attempt/session navigation and screens, attempt persistence, and send-count data coupled to attempt logging.
- [x] Remove obsolete web-only ignore rules.
- [x] Update the README and research document so they describe the Android-only project and current feature scope.
- [x] Run Android unit tests and assemble the debug APK.
- [x] Search the repository for stale web, timer, attempt, and session references.

## Completion criteria

- The repository has no web runtime or package-manager files.
- The Android app can browse and create problems without exposing climb timing or attempt/session logging.
- Existing saved problem JSON remains readable; obsolete extra JSON fields are safely ignored.
- Android tests pass and the debug APK builds.
- Project documentation matches the resulting app.

## Verification

- `./gradlew testDebugUnitTest assembleDebug` — passed.
- Repository search — no operational web, timer, attempt-log, or session-log references remain in the Android app.
- Research document — rendered to 12 pages and visually reviewed after the final text and mockup updates; no clipping, overlap, broken tables, or stale Start climb control remains.
