# Agent instructions

## After agent changes

After completing any change to the app's code, prompt the user to ask whether
they want the currently connected device updated with the new app changes.
If they do, build and install with:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Building and verifying

When the agent shell runs on the Mac itself, it can do the whole loop — verified
3 Sept 2026:

- `./gradlew testDebugUnitTest assembleDebug lintDebug` all run (Java 24;
  `local.properties` present).
- `adb` is at `~/Library/Android/sdk/platform-tools/adb`, not on `PATH`.
- `./gradlew connectedDebugAndroidTest` needs a device. If none is attached, boot
  the one AVD detached and wait for it:

```bash
nohup ~/Library/Android/sdk/emulator/emulator -avd Medium_Phone -no-snapshot-load >/dev/null 2>&1 &
```

Drive the UI with `adb shell input tap/swipe` and read it back with
`adb shell uiautomator dump /sdcard/ui.xml` — grepping that dump for
`text="…" … bounds="…"` locates Compose targets far more reliably than eyeballing
coordinates off a screenshot. The emulator has a working camera app
(`com.android.camera2`), so `ACTION_IMAGE_CAPTURE` flows can be walked end to end.

Two things still do not work, and neither is worth retrying:

- **Never drive Android Studio with computer-use.** It can only be granted at
  click-only tier, and it ignores synthetic clicks on the Run button and the Run
  menu item alike. `gradlew` plus `adb` sidesteps all of it.
- A **containerised** agent shell (Linux, repo-only mount) has no Android SDK,
  and the host's macOS SDK will not run there even if mounted. In that case,
  assume the changes are **uncompiled** and say so; ask the user to run the
  commands above.

The rule that matters either way: **never claim a change builds or passes tests
without output proving it**, and report failures with the output rather than
around it.

## Conventions worth preserving

- Comments explain *why*, not *what*. Several non-obvious decisions here are
  load-bearing and commented as such — the deliberately permissive
  `DraftProblem.hasContent`, the tombstone rules in `SyncPlanner`, and the
  width-first sizing of the board on phones in `BoardScreen`. Read the comment
  before "tidying" any of them.
- Data safety beats tidiness. Nothing in the sync or autosave paths may discard
  user work in order to keep the library clean.
- Keep validation, storage, and setter history in pure testable collaborators;
  `BoardViewModel` coordinates, it does not own the rules.
