# Enabling cloud sync (Firebase Firestore)

**Status: not yet live.** The sync code is in the app, but no shared Firebase
project has been provisioned for Board AF. Until someone completes the steps
below and ships a build with `google-services.json`, every install stays fully
local — same as before cloud sync existed.

Cloud sync ships disabled by default. Without `app/google-services.json`, the
Setup screen shows "This build has no Firebase configuration" and everything
stays on-device.

This guide follows the decision record in
[central-database-storage-research.md](central-database-storage-research.md):
Firestore Standard in `africa-south1`, Firebase Authentication, deny-by-default
rules, local store remains the source of truth.

## Suggested pilot users

When the project goes live, create Email/Password accounts for the two board
users and sign each device into the matching account:

| Person | Role |
| --- | --- |
| **Josh** | Board owner / setter (first account; board id = that uid) |
| **Taylor** | Second device / setter (same account for multi-device sync, or a second account once member invites exist) |

For the first private-board release, the simplest path is one shared account
(Josh creates it; both Josh and Taylor sign in with it). Separate accounts plus
membership invites come later — rules already model `OWNER` / `SETTER` /
`CLIMBER`, but there is no invite UI yet.

## 1. Create the Firebase project

1. Go to the [Firebase console](https://console.firebase.google.com/) and create
   a project (e.g. `board-af-dev`). Google Analytics is not needed.
2. Stay on the free Spark plan. Nothing in this integration requires Blaze.

## 2. Register the Android app and download the config

1. In Project settings → Your apps, add an Android app with package name
   `za.co.boardaf`.
2. Download `google-services.json` and place it at `app/google-services.json`.
   It is gitignored; each developer/machine provisions its own copy.
3. The Gradle build detects the file and applies the `google-services` plugin
   automatically — no build-file edits needed.

## 3. Enable email/password authentication

In the console: Build → Authentication → Get started → Sign-in method →
enable **Email/Password** (the plain variant; passwordless links not needed).

## 4. Create the Firestore database in Johannesburg

1. Build → Firestore Database → Create database.
2. Edition: **Standard**. Location: **`africa-south1` (Johannesburg)** — this
   cannot be changed later.
3. Start in **production mode** (locked). The next step replaces the rules.

## 5. Deploy the security rules

The repo contains `firestore.rules` (deny by default, board membership model)
and `firebase.json`. Deploy them with the Firebase CLI:

```bash
npm install -g firebase-tools
firebase login
firebase use <your-project-id>
firebase deploy --only firestore:rules
```

(Alternatively paste `firestore.rules` into the console's Rules editor.)

## 6. Build, install, sign in

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open the app → Setup tab → **Cloud sync** card → create an account (or sign in
on additional devices with the same account). The library uploads on first
sync and every device signed into the account stays in sync from then on.

## How the sync works

- **Local first.** The versioned DataStore snapshot remains the source of
  truth. Cloud sync mirrors it to `boards/{uid}` (setup + settings) and
  `boards/{uid}/problems/{problemId}` (one document per problem, bounded by 43
  hold assignments — matching the research doc's data shape).
- **Documents carry a canonical payload.** Each doc stores the same JSON the
  local `SnapshotCodec` produces, plus `schemaVersion`, `revision`,
  `updatedAt`, `updatedBy`. Undecodable server docs are left untouched, like
  local unreadable records.
- **Three-way merge, not last-write-wins.** The device keeps a per-record
  baseline (last agreed content). Plans only run against server-acknowledged
  snapshots, and if both sides edited the same problem, the remote version
  keeps the ID and the local version is preserved and pushed as a
  "(conflict copy)" problem — nothing is silently overwritten.
- **Baselines advance only after server acknowledgement**, the remote
  equivalent of the local store's write-and-read-back verification.
- **Reads are delta-shaped in practice.** A scoped snapshot listener on the
  board's problems keeps Firestore's local cache warm; steady-state launches
  read from cache rather than re-downloading the library.

## Verification checklist (from the research doc's proof of concept)

1. Sign in on two devices; create, edit, archive a problem on each while
   online — both converge.
2. Airplane-mode a device, edit, relaunch, reconnect — edits arrive, nothing
   lost.
3. Edit the same problem on both devices while one is offline, reconnect —
   a "(conflict copy)" appears, both versions retained.
4. Sign in with a second (non-member) account and confirm it cannot read or
   write the first account's board (rules deny).
5. Check Firestore console usage: steady-state launches should stay near zero
   server reads thanks to the cached listener.

## Out of scope for this first pass

- Invited setters/climbers UI (rules already model `members` with
  OWNER/SETTER/CLIMBER roles; writing member docs is manual for now).
- Managed backups/exports — keep relying on the local snapshot; add a
  scheduled export before treating the cloud copy as the only recovery path.
- App Check, budget alerts, media storage — see the research doc's security
  baseline before inviting general users.
