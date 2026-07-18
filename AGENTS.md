# Agent instructions

## After agent changes

After completing any change to the app's code, prompt the user to ask whether
they want the currently connected device updated with the new app changes.
If they do, build and install with:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
