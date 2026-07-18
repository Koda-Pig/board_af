# Central database and storage research

**Date:** 18 July 2026  
**Scope:** A free or inexpensive central store for Board AF problems (the app's term for climbs), board configuration, settings, and possible future user data. Prices are in US dollars before tax and should be checked again before purchase.

## Recommendation

Start with **Firebase Cloud Firestore Standard edition in `africa-south1` (Johannesburg)**.

It is the best first fit because Board AF is a native Android, offline-first app with small document-shaped records. Firestore has an official Android SDK, automatically caches and synchronizes Android data offline, includes authentication and client-side security rules, and has a Johannesburg database region. Its free allowance is far beyond the likely early workload. There is no fixed monthly charge when moving to the pay-as-you-go Blaze plan; structured data remains usage-priced.

Use **Supabase** instead if the near-term product direction is already expected to include relational features such as users belonging to many boards, ascent history, attempts, grade votes, comments, playlists, and aggregate reporting. Supabase gives a normal Postgres database and better SQL portability, but Board AF would have to build and test its own durable Android offline-sync layer. The nearest listed Supabase region is currently Frankfurt rather than South Africa, and a continuously available production project starts at $25/month.

Do not choose a database based on capacity alone. A problem record is likely to be only a few kilobytes; even 10,000 problems should occupy tens of megabytes rather than hundreds. Photos and videos will dominate storage, so keep binary media in object storage rather than in the database.

## Current Board AF constraints

The recommendation is based on the repository as it exists now:

- Native Kotlin and Jetpack Compose, with Android `minSdk` 26.
- Offline-first, currently with no account, backend, analytics, or Internet permission.
- A versioned JSON `LibrarySnapshot` persisted through DataStore.
- One fixed board with 43 bounded hold assignments per problem.
- Problem records contain an ID, name, grade, setter, note, tags, feet/start/finish rules, publication state, optional forerun time, and assignments.
- Board setup and grade/setter preferences are also local.
- The persistence code explicitly prioritizes migration safety and retaining unreadable records.

That leads to these selection criteria, in priority order:

1. Data must remain usable offline and synchronize without silent loss.
2. An Android app must be able to authenticate and access data without embedding an administrator secret.
3. Access must support one board owner plus invited setters/climbers.
4. Early cost should be $0, with a predictable low-cost production path.
5. South African latency/data location is valuable.
6. Backups, export, and a migration path must be available.
7. Realtime updates are useful, but not worth sacrificing data safety for.

## Shortlist at a glance

| Option | Free starting allowance | Cheapest practical paid path | Android/offline story | Region relevant to South Africa | Board AF fit |
| --- | --- | --- | --- | --- | --- |
| **Firebase Firestore** | 1 GiB; 50,000 document reads/day; 20,000 writes/day; 20,000 deletes/day; 10 GiB outbound/month | Blaze is usage-based with no fixed database fee | Official Android SDK; built-in cached reads, queued writes, and reconnection sync | **Johannesburg (`africa-south1`)** | **Best first choice** |
| **Supabase** | Two active projects; 500 MB database/project; 1 GB files; 50,000 MAU; 5 GB egress | Pro starts at **$25/month** | Kotlin/Android client; offline cache, outbox, conflicts, and retry logic are app work | Frankfurt is the closest currently listed region | Best relational alternative |
| **Appwrite Cloud** | Two projects; 1 database/project; 2 GB storage; 500,000 reads and 250,000 writes/month; 75,000 MAU | Pro starts at **$25/month** | Official Android SDK; documented offline sync is an architecture to implement, not transparent SDK persistence | Frankfurt | Credible open-source BaaS alternative |
| **Cloudflare D1 + Workers** | D1: 5 GB, 5M rows read/day, 100k rows written/day. Workers: 100k requests/day | Workers Paid has a **$5/month** minimum; generous D1 usage included | Build a REST API, auth, local persistence, outbox, and conflict handling | Worker runs at the edge; database placement is less directly aligned to a Johannesburg app database | Cheapest custom backend |
| **Neon Postgres** | 0.5 GB/project; 100 CU-hours/project; 60,000 auth MAU; 5 GB egress | Usage-based; example intermittent workload is about **$15/month** | No Android-first offline sync; use its Data API/RLS carefully or add an API layer | No South African region appears in the current official status list | Good database, incomplete mobile backend |
| **Turso/libSQL** | 5 GB; 500M rows read/month; 10M rows written/month; 3 GB sync | Developer is **$4.99/month** | Attractive SQLite/local-first direction, but the Android SDK is still labelled technical preview and the older embedded-replica sync is now legacy | Depends on Turso group placement | Watchlist, not the first production choice |
| **Self-hosted PocketBase** | Software is free; server is not | A small VM starts around **$4/month**, before backups and operations | Auth, API, files, and realtime included; offline synchronization remains app work | Depends on chosen host | Cheap in cash, expensive in ownership |

The figures above come from the vendors' current official material: [Firestore pricing](https://firebase.google.com/docs/firestore/pricing), [Firestore locations](https://firebase.google.com/docs/firestore/locations), [Supabase pricing](https://supabase.com/pricing), [Appwrite pricing](https://appwrite.io/pricing), [Cloudflare D1 pricing](https://developers.cloudflare.com/d1/platform/pricing/), [Workers pricing](https://developers.cloudflare.com/workers/platform/pricing/), [Neon pricing](https://neon.com/pricing), [Turso pricing](https://turso.tech/pricing), and [DigitalOcean VM pricing](https://www.digitalocean.com/pricing/droplets).

## Option analysis

### 1. Firebase Cloud Firestore — recommended

**Why it fits**

- Firestore's Android offline persistence can read and query cached data, queue writes while disconnected, and synchronize when the device reconnects. The SDK uses last-write-wins when multiple changes target the same document. See [Firestore offline data](https://firebase.google.com/docs/firestore/manage-data/enable-offline).
- Firestore has a regional Johannesburg location. Regional data is replicated across zones and has a documented 99.99% SLA. See [Firestore locations](https://firebase.google.com/docs/firestore/locations).
- Firebase Authentication and Firestore Security Rules are designed for direct mobile-client access. App Check can add another abuse barrier. See [Firestore security](https://firebase.google.com/docs/firestore/security/overview).
- Each complete Board AF problem can be one document. Its assignments are bounded by 43 physical holds, so atomic document replacement is practical and simple.
- The free quota is enough for a meaningful pilot. In Johannesburg, current overage pricing is $0.03 per 100,000 reads, $0.09 per 100,000 writes, and $0.01 per 100,000 deletes after the daily free allowance. See [Google Cloud Firestore pricing](https://cloud.google.com/firestore/pricing).

**Important tradeoffs**

- Firestore is not relational SQL. Cross-board reporting, grade-consensus analysis, and complex aggregate queries require deliberate denormalization, managed aggregations, exports, or a later analytics store.
- Automatic offline synchronization is not automatic conflict safety. Last-write-wins could overwrite two setters editing the same problem. Board AF should treat a problem as the conflict unit and retain revisions or conflict copies.
- Online transactions retry on contention but fail offline; queued batched writes can execute after reconnection. See [Firestore transactions and batched writes](https://firebase.google.com/docs/firestore/manage-data/transactions).
- Reads are billable per returned document and sometimes per index-entry batch. Do not download the entire library on every launch; query changes since the last successful sync or use a scoped listener.
- The free tier excludes managed backup, restore, clone, TTL, and point-in-time recovery features. Keep the existing verified local snapshot and add a periodic export before treating the cloud copy as the only recovery path.
- Vendor lock-in is higher than Postgres because the document structure, listeners, offline behavior, and rules language are Firebase-specific.

**Expected early cost**

Likely **$0/month** for structured data. As an illustrative upper-end pilot, 1,000 daily users each reading 100 problem documents produces 100,000 reads/day. Ignoring index-entry and network charges, only 50,000 exceed the free daily quota, which is roughly $0.015/day or $0.45 over 30 days at the current Johannesburg rate. A sensible delta-sync design should use much less.

### 2. Supabase — best if relational features are imminent

**Why it fits**

- It provides managed Postgres, authentication, row-level security, storage, realtime, edge functions, automatic REST APIs, and an officially documented Kotlin/Android path. The Kotlin client requires Android API 26, exactly matching Board AF. See [Supabase Android quickstart](https://supabase.com/docs/guides/getting-started/quickstarts/kotlin) and [Kotlin client installation](https://supabase.com/docs/reference/kotlin/installing).
- The domain naturally becomes relational if the app adds boards, memberships, problems, assignments, ascents, attempts, votes, tags, and comments.
- SQL, migrations, backups, and standard Postgres tooling reduce long-term data lock-in.
- Row-level security can make direct client access safe when policies are designed and tested correctly.

**Important tradeoffs**

- Supabase does not provide Firestore-style transparent offline persistence for Android. Board AF would need a local Room/SQLite database, a durable outbox, retry/idempotency logic, tombstones, incremental pulls, and conflict resolution.
- Free projects with low activity over seven days can be paused; they can be restored within 90 days. See [Supabase free project pausing](https://supabase.com/docs/guides/platform/free-project-pausing).
- The free plan has a 500 MB database limit, no automatic backups, and a maximum of two active projects. Pro starts at $25/month and includes an 8 GB disk, 100 GB file storage, and seven days of daily backups. See [Supabase billing](https://supabase.com/docs/guides/platform/billing-on-supabase).
- Supabase's current region list does not include South Africa; Frankfurt is the closest listed option. See [Supabase regions](https://supabase.com/docs/guides/platform/regions).

**When it should replace Firestore as the recommendation**

Choose Supabase now if ascent/session history, community voting, complex filters, or server-side reporting are committed near-term requirements and the team accepts the extra sync work and $25/month production floor.

### 3. Appwrite Cloud — capable, but less compelling here

Appwrite includes authentication, structured databases, storage, functions, realtime, messaging, and official Android APIs. The free plan is generous for Board AF: 2 GB storage, 5 GB bandwidth, 500,000 reads, 250,000 writes, and 75,000 monthly active users. Pro starts at $25/month and includes daily backups. It is also open source and can be self-hosted. See [Appwrite pricing](https://appwrite.io/pricing) and the [Android database API](https://appwrite.io/docs/references/cloud/client-android-java/databases).

The main drawback is offline behavior. Appwrite's documentation describes an application-managed push/pull architecture using a local store and explicit conflict resolution; it is not Firestore's transparent Android cache. See [Appwrite offline sync](https://appwrite.io/docs/products/databases/offline). Free projects are also paused after one inactive week. Appwrite Cloud currently offers Frankfurt, New York, Sydney, San Francisco, Singapore, and Toronto, but no African region. See [Appwrite regions](https://appwrite.io/docs/products/network/regions).

Appwrite is worth choosing when the open-source/self-hosting path matters more than built-in offline Android behavior or local region latency.

### 4. Cloudflare D1 + Workers — lowest-cost custom backend

D1 is managed serverless SQLite. The free tier includes 5 GB, five million rows read per day, and 100,000 rows written per day. A Worker can expose a narrow authenticated JSON API; Workers Free allows 100,000 requests/day, and Workers Paid starts at $5/month. D1 does not charge egress. See [D1 pricing](https://developers.cloudflare.com/d1/platform/pricing/) and [Workers pricing](https://developers.cloudflare.com/workers/platform/pricing/).

This can be an excellent cost/performance design, but it is a backend project rather than a plug-in database choice. Board AF would need to own:

- user authentication or verification of an external identity token;
- API endpoints and authorization rules;
- schema migrations and validation;
- Android local storage and incremental sync;
- idempotency, deletions, retries, and conflicts;
- backups, monitoring, and incident recovery.

It becomes attractive if the team wants a custom API anyway. It is excessive for the first central copy of a few hundred problems.

Cloudflare R2 is a particularly strong separate choice for future media: 10 GB-month, one million mutating operations, and ten million reads are free each month, and Internet egress is free. See [R2 pricing](https://developers.cloudflare.com/r2/pricing/).

### 5. Neon Postgres — inexpensive foundation, not a complete solution

Neon Free currently includes 0.5 GB per project, 100 compute-unit hours per project, 60,000 Neon Auth monthly active users, and 5 GB egress. Its Launch plan is usage-based at $0.106 per compute-unit hour and $0.35 per GB-month, with an example intermittent workload around $15/month. See [Neon pricing](https://neon.com/pricing). Its [official regional status list](https://neon.com/docs/introduction/status) includes Frankfurt, London, Singapore, Sydney, São Paulo, and US regions, but no South African region.

Neon is attractive if Board AF later has a server or custom API and wants serverless Postgres. For the present app, Supabase supplies more of the needed mobile backend around Postgres, while Firestore supplies much better offline Android behavior. Direct client access through a data API also makes row-level security a critical part of the design, and there is no Android-first offline sync.

### 6. Turso/libSQL — promising local-first watchlist

Turso's free limits are unusually generous, and its SQLite lineage matches an offline-first mobile app. It supports scoped JWT database tokens and fine-grained table/action permissions. See [Turso authorization](https://docs.turso.tech/sdk/authorization).

It is not the safest first production choice for Board AF today:

- The Android SDK documentation labels it a **technical preview**. See the [Android quickstart](https://docs.turso.tech/sdk/kotlin/quickstart).
- The older embedded-replica system is now described as legacy; new sync work is moving to Turso Sync. See [embedded replicas](https://docs.turso.tech/features/embedded-replicas/introduction).
- A secure multi-user app still needs a token-issuing auth service or external auth provider.
- The changing sync/SDK boundary increases migration risk for a feature whose primary goal is preserving user data.

Revisit Turso when its current Android SDK and new offline-first sync are stable and documented for production use.

### 7. Self-hosted PocketBase — only with an owner for operations

PocketBase packages SQLite, authentication, realtime subscriptions, files, a dashboard, and an API into one executable. A basic VM can start at $4/month. However, PocketBase itself says it is not yet recommended for production-critical applications unless the operator is comfortable following changelogs and manual migrations. See [PocketBase introduction](https://pocketbase.io/docs/) and [production deployment](https://pocketbase.io/docs/going-to-production/).

The real cost includes OS and PocketBase updates, TLS, SMTP, rate limiting, monitoring, off-machine backups, restore drills, capacity, and availability. It also does not remove the need for Android offline sync. It is reasonable for a private hobby deployment, but a managed service is safer and cheaper in engineering time for this app.

## Structured data versus media storage

Do not put images or videos inside Firestore/Postgres rows. Store only object keys, sizes, content types, hashes, and ownership metadata in the database.

| Need | Recommended starting action |
| --- | --- |
| Existing bundled board photo | Keep it in the APK; no cloud object store is needed. |
| User-uploaded board photos | Add object storage only when user-created boards are committed. |
| Problem thumbnails | Generate from the board image and assignments on-device where possible. |
| Beta videos | Treat as a later product and cost decision; enforce duration/size limits and lifecycle deletion. |
| Backups/exports | Use a separate bucket/account from live app media when possible. |

If Firebase is selected, note that Cloud Storage for Firebase now requires the Blaze plan. Its Android setup guide says only `US-CENTRAL1`, `US-EAST1`, and `US-WEST1` can use the Google Cloud Storage Always Free tier; other locations follow Cloud Storage pricing. See [Cloud Storage for Firebase on Android](https://firebase.google.com/docs/storage/android/start). For South African media, compare an appropriately located Google Cloud bucket with R2 rather than assuming storage is free.

## Recommended Firestore data shape

Use the project's domain term `problems`, not a new `climbs` synonym:

```text
users/{uid}
  displayName, createdAt

boards/{boardId}
  name, angleDegrees, heightMeters, setup, schemaVersion,
  ownerId, createdAt, updatedAt

boards/{boardId}/members/{uid}
  role: OWNER | SETTER | CLIMBER

boards/{boardId}/problems/{problemId}
  name, grade, accent, setterId, setterDisplayName, note, tags,
  feetRule, startRule, finishRule, publicationState,
  forerunConfirmedAt, assignments[], schemaVersion,
  revision, createdAt, updatedAt, updatedBy, deletedAt
```

Keep the assignment array inside its problem document. It is small, bounded, normally edited as one unit, and should be validated atomically. Keep board setup in one board document for the same reason. Only normalize assignments into separate documents if their independent querying or concurrent editing becomes a demonstrated need.

If the product later adds completion records, call them `ascents` or `sends` and add them separately; do not overload a problem document. Attempts, timing, and session logs remain outside the current product scope.

## Offline and conflict design

Firestore's cache is helpful, but Board AF's existing data-safety guarantees should not be discarded. A safe architecture is:

```text
Compose UI
    -> BoardRepository
        -> durable local database + migration/recovery data
        -> sync outbox and conflict records
        -> Firestore remote store
```

Now that multi-device sync is a real requirement, **Room is worth reconsidering**. The existing versioned snapshot is excellent for migration and backup, but a whole-library JSON value is awkward for per-problem dirty flags, tombstones, revisions, and incremental synchronization.

Minimum sync rules:

- Generate stable UUIDs on-device; retries must be idempotent.
- Add a schema version independent from the local snapshot version.
- Record server `updatedAt`, `updatedBy`, and a monotonically checked revision.
- Represent deletion with a tombstone until all relevant devices have synchronized.
- Pull only records changed since the last confirmed cursor/time, with a periodic full reconciliation.
- If a remote revision changed after a local edit began, preserve both versions or create a conflict copy; never silently overwrite the losing content.
- Keep unreadable server records and their raw payload just as the local store does.
- Mark data as synchronized only after a write and read-back verification.
- Keep a user-visible last-sync/error state and a manual retry/export action.

For a first private-board release, concurrent editing will be rare. A whole-problem conflict unit plus a conflict copy is simpler and safer than field-level merging.

## Security baseline

Whichever service is chosen:

- Require authentication before any non-public read or write.
- Never ship an administrator/service credential in the APK. Public client configuration keys are acceptable only when server-enforced rules constrain them.
- Deny access by default and authorize through board membership.
- Separate `OWNER`, `SETTER`, and `CLIMBER` capabilities.
- Validate enum values, required fields, assignment count, maximum text lengths, ownership, and immutable IDs server-side/in rules.
- Test authorization rules in an emulator or isolated test project before production.
- Rate-limit invitations, account actions, uploads, and any public endpoint.
- Minimize personal data and provide export/account deletion before inviting general users.
- Enable App Check or an equivalent abuse-control layer, but do not treat it as authorization.
- Configure budget alerts and monitor read patterns; listeners and broad queries can multiply usage.

## Suggested proof of concept

The next step should be a small reversible Firestore spike, not a wholesale persistence rewrite:

1. Create a Firebase development project and a Firestore Standard database in `africa-south1`.
2. Add Firebase Authentication and use a real signed-in test user; do not start with public rules.
3. Define `boards`, `members`, and `problems` plus deny-by-default rules.
4. Export the current `LibrarySnapshot`, upload it without deleting the local source, and read it back into domain models.
5. Test two devices for create, edit, archive, and delete while online.
6. Test cold launch and edits in airplane mode, then reconnection.
7. Deliberately edit the same problem on both devices and verify the chosen conflict-copy behavior.
8. Measure document reads during initial load, ordinary launch, and a realtime update.
9. Verify that a non-member and a climber cannot perform setter/owner writes.
10. Produce and restore an export before declaring the remote store authoritative.

### Go/no-go criteria

Proceed with Firestore if the spike demonstrates:

- no loss of local or remote problems in offline and conflict tests;
- predictable incremental reads rather than full-library reloads;
- understandable Security Rules for the membership model;
- acceptable Johannesburg latency;
- a verified export/restore route.

Switch the spike to Supabase if the Firestore model becomes dominated by joins, aggregate queries, or duplicated relational data. Consider Cloudflare D1 only if owning a custom API is already a product decision. Revisit Turso after its Android and new sync stack leave preview/evolving status.

## Final decision record

| Decision | Current answer |
| --- | --- |
| Central structured store | Firestore Standard |
| Database region | `africa-south1` (Johannesburg) |
| Source of truth during rollout | Existing local store until upload and read-back verification pass |
| Sync unit | One problem document; one board setup document |
| Conflict policy | Preserve a conflict copy rather than silent last-write-wins |
| Authentication | Firebase Authentication with board membership rules |
| Pilot users | Josh and Taylor (not yet live — see [firebase-setup.md](firebase-setup.md)) |
| Media | None initially; evaluate R2 or a regional Cloud Storage bucket when uploads are real |
| Production cost target | $0 for the pilot; usage-based thereafter |
| Relational fallback | Supabase/Postgres |
| Review trigger | Before adding ascents/attempts, community features, user-created boards, or media |
| Live status | **Not yet live** — app code exists; Firebase project and shared config not provisioned |
