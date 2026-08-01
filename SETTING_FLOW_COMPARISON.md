# Setting-Flow Comparison: Board AF vs MoonBoard, Kilter, Tension

*Written 2026-08-01. Based on a hands-on walkthrough of Board AF's create-problem
wizard on an Android emulator (debug build, `Medium_Phone` AVD), plus current
App Store screenshots and public documentation for the MoonBoard, Kilter Board,
and Tension Board 2 apps.*

---

## 1. Board AF walkthrough (as exercised end-to-end)

The full flow was driven on-device: **＋ → feet rule → start holds → other
holds → finish holds → details & review → forerun confirmation → published.**
A problem ("Emulator Special", 6A, Marked feet only, 6 holds, Technical tag)
went from empty state to Published without errors.

Step-by-step observations:

1. **Entry.** The `＋` in the top bar opens "Set a problem" with a bottom sheet
   ("Set the line", Step 1 of 5). Step chips (`1 · Feet rule` … `5 · Details &
   review`) double as navigation and as a progress indicator; completed steps
   get a checkmark.
2. **Feet rule (step 1).** Five problem-wide rules in a horizontally scrolling
   chip row: *Marked feet only, Open kickboard, Feet follow marked, Any feet,
   Campus*, each with a one-line explanation under the row. The rule is chosen
   **before** any hold is tapped.
3. **Start holds (step 2).** The board dims non-eligible holds: hand-capable
   holds get white rings, kickboard foot-only holds get dashed ochre rings.
   Copy is explicit ("Tap one or two start holds on the main board"), and
   **Next stays disabled until at least one start hold exists**. After the
   first tap the sheet header flips from "New problem" to "Editing draft" —
   autosave is visible and immediate.
4. **Other holds (step 3).** Taps add regular (hand) holds. Tapping a
   kickboard hold triggered a guard snackbar — *"That hold is foot-only — it
   can't be a regular hold"* — with a one-tap **"Mark as foot instead"**
   recovery action. Invalid input is corrected, not silently ignored.
5. **Finish holds (step 4).** Same gated pattern (one or two finish holds).
   The system back button walks wizard steps backward rather than abandoning
   the session (verified accidentally and confirmed deliberately).
6. **Details & review (step 5).** Rules summary is restated in plain language
   ("Start with one hand on each start hold…"), holds are locked while
   reviewing, then: name, Font-grade chips (setter estimate), accent colour,
   tags (Project / Warm-up / Technical / Power), setter notes, and a live
   validation banner — *"Everything checks out. Publishing still needs a
   successful forerun."*
7. **Publish.** `Publish…` raises a **forerun confirmation** dialog ("Have you
   climbed Emulator Special from start to finish exactly as set…?") with
   *Not yet* / *Yes — publish*. Confirming lands back on the Board screen with
   a "published" snackbar and the problem live, showing lifecycle actions
   (Edit, Duplicate, Mark benchmark, Archive, Delete).

Distinctive traits: role-per-step tapping (the current step decides what a tap
means), a problem-wide feet rule chosen first, hard validation gates, undo/redo
plus hold-count readouts in every hold step, and a quality gate (forerun) in
front of Published.

---

## 2. The comparison apps

### MoonBoard (Moon Climbing)

- **Entry/flow:** `＋` → create problem on a fixed grid-board photo. Tap holds
  directly on the single board view; every problem is a set of circled holds.
- **Roles:** colour-coded circles — **green = start, blue = intermediate,
  red = finish**. One or two start holds (matched start if one); problems must
  finish on the top row, held for 2 seconds. The 2024+ app adds per-hand
  labels (START / LEFT / RIGHT / MATCH / END polygons around holds, visible in
  the current App Store screenshots).
- **Feet rules:** chosen per problem from four options — *Feet follow hands*,
  *Feet follow hands + screw-ons*, *Screw-ons only*, *Footless (kickboard
  allowed to start)*. This is the closest analogue to Board AF's problem-wide
  feet rule (Board AF adds Marked-only and splits kickboard handling out).
- **After setting:** name + setter grade, then the problem is immediately
  live for the whole community. Quality is handled *after* publication:
  community repeats, community grades, and a curated **benchmark** program.
  Rankings/leaderboards are first-class.
- **No draft/validation stage** equivalent to Board AF's; the app assumes the
  fixed global board layout, so there is nothing like per-board hold
  capability to validate against.

### Kilter Board (Aurora Climbing ecosystem)

- **Entry/flow:** `＋` in the top bar → tap holds on the LED-board diagram;
  **each successive tap on a hold cycles its role colour** on one canvas.
  There are no steps — role assignment, ordering, and review all happen on a
  single screen.
- **Roles (per-hold, no problem-wide feet rule):** **green = start,
  blue = hand or foot, purple = finish, yellow = feet only**. Foot rules are
  expressed by colouring individual holds, not by a rule the whole problem
  carries.
- **Metadata:** name, description, suggested grade; angle is a property of the
  *session* (0–70°), and grades/ascents are tracked per angle.
- **After setting:** publish immediately to the global community. Quality
  control is social: send counts, star ratings, community grade consensus,
  benchmark badges. A settings toggle offers a **colour-blind mode** (unique
  shapes per role) — an accessibility idea directly relevant to Board AF's
  colour-coded rings.
- LED sync (lightbulb icon) lights the physical wall to match the app.

### Tension Board 2 (Aurora-based)

- **Entry/flow:** same Aurora pattern as Kilter — `＋`, tap-to-cycle roles on
  a photo-realistic board render, then name + suggested grade, publish.
- **Roles:** **green = start, blue = middle, red = finish, magenta = foot
  only** (kickboard feet show as magenta circles along the bottom row).
- **Problem header** carries compact rule badges: angle (e.g. 40°), a
  foot-rule icon (e.g. screw-ons crossed out), grade `6c/V5`, benchmark/classic
  badge, star rating — a dense one-line equivalent of Board AF's rules card.
- **Extras:** mirror button (the board is symmetric, so every problem has a
  mirrored twin), LED sync, ascent logging with tries counter, and curated
  **circuits** (Intro / Intermediate / Advanced / Top 100) as a discovery
  layer.

---

## 3. Flow-by-flow comparison

| Dimension | Board AF | MoonBoard | Kilter | Tension 2 |
|---|---|---|---|---|
| Creation model | 5-step wizard, role decided by current step | Single canvas, tap holds (new app: per-hand labels) | Single canvas, tap cycles role | Single canvas, tap cycles role |
| Feet handling | **Problem-wide rule (5 options) chosen first** | Problem-wide rule (4 options) | Per-hold yellow "feet only" | Per-hold magenta "feet only" |
| Hold constraints | Board-aware: kickboard holds can never be hands; guard snackbar with recovery action | Fixed layout, top-row finish convention | Any hold, any role | Any hold, any role |
| Start/finish rules | 1–2 start, 1–2 finish, enforced before Next | 1–2 start (matched if 1), finish top row 2 s | Green start / purple finish by convention | Green start / red finish (match to send) |
| Drafts | Autosave from first tap; untitled drafts visible in library | None (create then publish) | None visible | None visible |
| Validation | Live validator; errors block publish; needs-review state | None (community corrects) | None | None |
| Publish gate | **Explicit successful-forerun confirmation** | Instant publish | Instant publish | Instant publish |
| Post-publish quality | Lifecycle: Draft → Needs review → Published → Benchmark / Archived | Community grades + curated benchmarks | Send counts, stars, grade consensus | Same + circuits, classics |
| Grading | Setter estimate (Font chips) | Setter grade → community grade (Font/V toggle) | Suggested grade + per-angle consensus | Suggested grade + consensus |
| Audience | Single private home board, offline-first | Global community, standardized wall | Global community, many walls/sizes | Global community, mirrorable wall |
| Hardware tie-in | None (photo of the actual wall) | LED sync | LED sync, Bluetooth, angle 0–70° | LED sync, mirror mode |

---

## 4. What the comparison suggests

**Where Board AF is ahead of the commercial apps:**

- **Guided integrity.** The wizard plus live validation means a published
  Board AF problem is structurally sound by construction. All three commercial
  apps accept any tap combination and let the community sort it out — fine at
  100k users, useless on a private board with two climbers. The forerun gate
  is the single biggest differentiator: Board AF publishes *climbs*, the
  others publish *proposals*.
- **Board-aware hold capability.** None of the compared apps model "this hold
  physically cannot be a hand hold". The foot-only guard with a one-tap
  "Mark as foot instead" recovery is a genuinely better interaction than
  Kilter/Tension's unconstrained colour cycling, where a nonsense role is
  simply accepted.
- **Autosaving drafts** from the first tap has no equivalent in any of the
  three apps; abandoning creation there loses the work.
- **Richer feet-rule vocabulary** (5 rules vs MoonBoard's 4; Kilter/Tension
  have none problem-wide) and the rule is restated in human language on the
  viewer and in review.

**Where the commercial apps are ahead — candidate ideas for Board AF:**

1. **Tap efficiency for experienced setters.** The Aurora tap-to-cycle model
   is faster for a practiced user: one screen, no step navigation. Board AF's
   wizard is better for correctness and first-time users, but a possible
   middle ground is letting the *role palette* (Start/Regular/Foot/Finish
   legend already shown at the bottom of the sheet) act as a role selector in
   a single free-set mode for existing problems — the wizard already allows
   free jumping between steps for problems in the library, which is most of
   the way there.
2. **Colour-blind support.** Kilter ships a mode that adds unique *shapes*
   per role. Board AF already differentiates foot-only holds with a dashed
   ring; extending distinct ring shapes/badges (the S/F letter badges help) to
   a deliberate accessibility guarantee would close this gap cheaply.
3. **Mirroring/duplication.** Tension's mirror button works because the wall
   is symmetric — not applicable to an asymmetric home wall — but its spirit
   (cheap variations of an existing problem) is served by Duplicate; a
   "shift/reflect where possible" assist could be a future experiment.
4. **Discovery structures.** Circuits (Tension) and benchmarks-as-a-program
   (MoonBoard) turn a library into a training plan. Board AF has benchmark
   status and tags; a saved-filter or "circuit" grouping would be the natural
   next step once the library grows.
5. **Angle as metadata.** Kilter/Tension treat wall angle as a first-class
   session property. A fixed home board doesn't need per-session angle, but
   recording the board's angle in Setup would make grades comparable if the
   board is ever rebuilt or shared.

**Net read:** Board AF's flow is deliberately opinionated in exactly the areas
the big apps are permissive — feet rules up front, per-step tap semantics,
validation, and a forerun gate — which fits its context (one physical wall,
a couple of setters, no community to average out errors). The commercial apps
optimize for speed of creation and post-hoc community curation; Board AF
optimizes for correctness at creation time. The walkthrough surfaced no dead
ends, disabled-state confusion, or lost work in the current build.

---

## Sources

- Hands-on: Board AF debug build `za.co.boardaf`, emulator walkthrough (2026-08-01).
- [Using Your MoonBoard FAQ](https://moonclimbing.com/using-your-moonboard)
- [Using Your MoonBoard App](https://moonclimbing.com/using-moonboard-app)
- [MoonBoard — how to use PDF (boulder guide)](https://static1.squarespace.com/static/5b680ff22714e5a2859144bf/t/5e4422a70c7e7311c6a60ecc/1581523625176/How+to+use+The+MoonBoard.pdf)
- [Moon Board on the App Store](https://apps.apple.com/app/id6446842142) (screenshots: dashboard, Create problem, rankings)
- [How to Use a Kilter Board — Gripped](https://gripped.com/indoor-climbing/how-to-use-a-kilter-board/)
- [Kilter Board app page (Setter Closet)](https://settercloset.com/pages/kb-app)
- [Kilter Board Climbing Wall App — App Store](https://apps.apple.com/us/app/kilter-board-climbing-wall-app/id6755110303) (screenshots: home, boards map, search, settings)
- [Kilter Board — Google Play](https://play.google.com/store/apps/details?id=com.kiltergrips.kilter_board_app&hl=en)
- [How to Use a Tension Board — Gripped](https://gripped.com/indoor-climbing/how-to-use-a-tension-board/)
- [Tension Board 2 — App Store](https://apps.apple.com/us/app/tension-board-2/id1488028660) (screenshots: board view "Bubbles", circuits, home)
- [Tension Climbing FAQs](https://tensionclimbing.com/pages/faqs)
- [Getting Started on the MoonBoard — Friction Labs](https://shop.frictionlabs.com/blogs/climb-your-impossible/getting-started-on-the-moon-board)
- [Moonboard 101 — Dynamite Starfish](https://dynamitestarfish.com/blogs/news/moonboard-101)
