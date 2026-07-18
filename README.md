# Board AF — Android

A small, offline-first app for setting and climbing problems on a single home board.

## Stack

- Kotlin
- Jetpack Compose with Material 3
- Navigation Compose
- A single `AndroidViewModel` and local `SharedPreferences` JSON persistence

No account, backend, analytics, or internet permission is required.

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

The source photo is 960 × 1280 (3:4). `BoardSurface` forces that exact aspect ratio and draws the photo with `ContentScale.FillBounds`. Hold centers are normalized photo coordinates (`0f..1f`) and are positioned inside the same Compose box as the image. Screen width only changes the size of that shared box, so the bitmap and overlays scale together.

The geometry is covered by `BoardGeometryTest` at phone, tablet, and large-screen widths.

## Previous web prototype

The original Vite prototype remains in `src/` and `public/` for reference. The native Android project is the repository root and the installable application lives in `app/`.
