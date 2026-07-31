# Agent instructions

## After agent changes

After completing any change to the app's code, prompt the user to ask whether
they want the currently connected device updated with the new app changes.
If they do, build and install with:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Building is a host-machine job

Agents generally **cannot run this build themselves**. Two things block it:

- The agent shell is a Linux container that mounts only this repository. It has
  no Android SDK, and the host's macOS SDK and `cmdline-tools` will not run
  there even if they are mounted.
- Android Studio can only ever be granted at click-only tier, and in practice it
  ignores synthetic clicks on both the Run button and the Run menu item.

So an agent should assume its changes are **uncompiled** and say so plainly
rather than implying they were verified. Ask the user to run the commands above,
or `./gradlew testDebugUnitTest assembleDebug` for the full check. Never claim a
change builds or passes tests without output proving it.

## Conventions worth preserving

- Comments explain *why*, not *what*. Several non-obvious decisions here are
  load-bearing and commented as such — the deliberately permissive
  `DraftProblem.hasContent`, the tombstone rules in `SyncPlanner`, and the
  height-first sizing of `BoardSurface` on phones. Read the comment before
  "tidying" any of them.
- Data safety beats tidiness. Nothing in the sync or autosave paths may discard
  user work in order to keep the library clean.
- Keep validation, storage, and setter history in pure testable collaborators;
  `BoardViewModel` coordinates, it does not own the rules.
