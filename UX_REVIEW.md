# Board AF — UX review: user flows

**Date:** 2026-07-31
**Method:** Walked the app live on the SM-S931B via Android Studio device mirroring, then
traced each observed behaviour back to source. Every finding below was **reproduced on
device** unless marked otherwise.
**Scope note:** Text-contrast findings were explicitly excluded at your request. Everything
else from the earlier pass is retained and re-verified.

---

## Resolution status (31 July 2026)

Remediation landed in the same working tree. **None of it has been compiled or
re-tested on device** — see `TODO.md`, item 1.

| Status | Findings |
|---|---|
| **Fixed** | F1, F2, F3, F4, F5, F6, F7, F8, F10, F11, F13, F14, F15, F16, F17, F18, F19, F20, F21, F22, F23, F24, F25, F26, F29, F30 |
| **Partly fixed** | F9 (header count and active-filter chips done; filter rows are still nested horizontal scrollers, and Status now has 7 options so clipping is worse), F12 (filters now persist; scroll-restoration inconsistency remains), F27 (new problems take the account's local-part; existing "You" rows and `duplicateProblem` unchanged), F28 (dark scheme exists; ~44 hardcoded light-palette colours remain) |
| **Open** | none outstanding beyond the partials above |

Four defects were introduced by the first remediation pass and fixed in the
second: deletes resurrecting through cloud sync, a lossy archive Undo, the F2
gate hint being unreachable on the Details step, and a stretched board photo.
Their regression tests live in `SyncPlannerTest`, `VersionedBoardStoreTest`, and
`SetterReducerTest`.

**Test data note:** walking the setter flow created one untitled draft in your library.
I archived it afterwards — it is under Status → Archived if you want it back. The fact
that I *couldn't delete it* is finding F4.

---

## The one that matters most

### F1. You can never see the board and the controls at the same time
**Reproduced on device. Root cause: `BoardScreen.kt:107–148`**

The entire phone layout is a single `LazyColumn`. The board renders at
`fillMaxWidth().aspectRatio(3:4)`, which on this device consumes roughly 75% of the
viewport on its own. Everything else — the setter panel, the role palette, the step
chips, Back/Next, the problem details — sits *below* it in the same scroll.

What this means in practice, measured by scrolling:

**Setting a problem.** Scroll to the top: full board, all 43 hold targets, and the setter
panel is entirely off-screen except the words "Set the line". Scroll down far enough to
see which role is active: the top half of the board is gone. So placing holds is:

> scroll down → check active role → scroll up → tap hold → scroll down → confirm the
> count incremented → scroll up → tap next hold → …

There is a narrow scroll position where you get ~60% of the board plus the step chips, and
even there the role palette and hold count are still cut off.

**Climbing a problem.** Same split. Scrolled to read the feet rule and start/finish rules,
the bottom third of the wall is off-screen. Scrolled to see the whole wall, the rules are
gone. You cannot look at the problem and its rules together — which is the entire job the
app exists to do, performed standing at a wall with chalky hands.

**Fix.** The panel should not be a scroll sibling of the board. Options, roughly in order
of effort:

- **Bottom sheet (recommended).** Move `SetterPanel` and `ProblemDetails` into a
  `BottomSheetScaffold` with a peek height showing the step chips + active role + Back/Next.
  The board keeps the full viewport above it and the setter can drag the sheet up for the
  details form. This is the standard Material pattern for exactly this map-plus-controls
  shape.
- **Pin a compact control bar.** Keep the scroll, but hoist a single fixed row (active role
  chip · count · undo · Back/Next) into the `Scaffold` above the bottom nav so it is always
  visible.
- **Shrink the board in SET mode.** Cheapest: cap the board height in the setter so panel
  and board coexist. Weakest, because the setter is precisely who needs the board large.

The tablet path already solves this — `BoardScreen.kt:74–105` puts the board and a 400dp
side panel in a `Row` at ≥840dp. The phone layout is the one that needs the fix.

---

## Wizard flow

### F2. The step chips don't scroll to the current step, which strands the user
**Reproduced on device. `SetterPanel.kt:164–184`**

The stepper is a `LazyRow` with no `LazyListState` and no `animateScrollToItem` on step
change. On this screen width it shows **2.2 of the 5 steps**. So whenever the current step
is 4 or 5, the highlighted chip is off-screen and the user sees a stepper where **nothing
is selected**.

I hit the bad case by ordinary navigation (Problems → ⋮ → Edit → back → back). The result:

- Step chips visible: `1 · Feet rule`, `2 · Start holds`, `3 · Oth…` — **none highlighted**
- Coral hint: **"Mark one or two finish holds to continue."**
- Buttons: **`Back` only** — no `Next`, because `current == DETAILS` (`SetterPanel.kt:196`)
- Body below: the Details form (name, grade, accent, tags, notes)

So the user is on the last step, is told to go do something on step 4, is given no `Next`,
and the chip for step 4 is off-screen to the right. The only route is to discover that the
chip row scrolls horizontally. That is a dead end for anyone who doesn't.

**Fix.** Three changes, all small:

1. Give the `LazyRow` a `rememberLazyListState()` and `LaunchedEffect(step) { animateScrollToItem(step.ordinal) }`.
2. When the gate hint names a different step, make it a **button** that jumps there —
   `onGoToGuidedStep(blocking)` — instead of inert text.
3. Consider a compact "Step 5 of 5" label next to the chips so progress survives clipping.

### F3. The system back button leaves the wizard entirely
**Reproduced on device. `BoardAfApp.kt:187–193`**

`BackHandler(enabled = state.isSetting)` is supposed to walk the wizard backwards. But when
the session was entered from the Problems tab (⋮ → Edit, which calls
`navController.navigate(BOARD)` at `BoardAfApp.kt:307–312`), the nav back stack is
`Board → Problems → Board`. Pressing back from inside the wizard took me **to the Problems
list**, abandoning the session — then pressing back again returned to a wizard in the
broken state described in F2.

**Confidence: verified behaviour; the exact interaction between the `BackHandler` and the
nav back stack I'd want you to confirm with a breakpoint.** The user-visible bug is
certain; the precise cause is my inference.

**Fix.** Don't push a new `Board` destination when opening the setter from Problems — the
setter is a *mode* of the Board destination, not a new one. Use the same
`popUpTo(startDestination){saveState}` pattern the bottom nav uses, so entering the setter
never deepens the back stack.

### F4. Nothing in the app can be deleted
**Reproduced on device. `ProblemsScreen.kt:282–316`, `BoardScreen.kt:320–354`**

The overflow menu offers **Edit / Duplicate / Archive**. That is the complete set. There is
no delete in the menu, no delete in the detail card, no swipe-to-delete, no bulk action.

Combine that with `autosaveDraft()` (`BoardViewModel.kt:393–411`), which persists a draft
the moment `hasContent` is true — and `hasContent` is satisfied by **one tapped hold**
(`ProblemModels.kt:96–97`). I tapped a single hold while exploring and immediately had a
permanent library entry.

That entry rendered as a **card with no title at all** — just a coloured dot, a "Draft"
chip floating where the name should be, and "You · 1 holds · Marked feet only". It sorted
to the top of the library, above every real problem.

The library is therefore append-only. Every abandoned experiment is permanent, and the only
disposal route is Archive — which is itself hidden behind a filter labelled "All" that
excludes archived items (F9).

**Fix.** Three parts:

1. Add **Delete** with a confirmation dialog, at minimum for `DRAFT` problems.
2. Give unnamed problems a rendered fallback — `problem.name.ifBlank { "Untitled draft" }`
   in `ProblemCard` and `BoardHeader`.
3. Raise the autosave threshold so a bare tap doesn't mint a library row. Requiring a name,
   or ≥1 start hold *and* ≥1 other hold, would both work.

### F5. The bottom nav silently kills the setting session
**Reproduced on device. `BoardAfApp.kt:252–255`**

Tapping Problems or Setup mid-wizard calls `viewModel.cancelSetting()` with no dialog, no
snackbar, no indication anything happened. I did this with an in-progress draft and simply
found myself on the Problems list.

`cancelSetting()` does autosave first, so data isn't destroyed — but the *session* is, and
`selectedProblemId` is never pointed at the draft (`BoardViewModel.kt:226–231`). Going back
to the Board tab showed the previously-selected problem, not what I'd just been building.
The draft existed only as an untitled row in the library.

**Fix.** Two options depending on how modal you want the setter to feel:

- **Keep the session alive.** Don't call `cancelSetting()` on nav; let the user come back to
  the Board tab and resume where they left off. This is the least surprising behaviour and
  the draft is autosaved anyway.
- **Confirm.** If you want the setter to be modal, show a dialog: "Leave the setter? Your
  draft is saved." At minimum, set `selectedProblemId` to the draft so the Board tab shows
  what they were working on.

### F6. The "+" button stays live during a session and silently restarts it
**Reproduced on device. `BoardAfApp.kt:223–237`, `BoardViewModel.kt:198–204`**

The top-bar "+" remains visible and enabled throughout the wizard. `startSetting()`
replaces `setter` with a fresh `DraftProblem()` unconditionally. No confirmation. The
half-built problem becomes another untitled orphan (F4).

**Fix.** Hide the action when `state.isSetting`. One line.

### F7. The gate hint reads as an error before the user has done anything
**Reproduced on device. `SetterPanel.kt:186–191`**

Arriving on "2 · Start holds" for the first time immediately shows coral text: *"Mark one
or two start holds to continue."* Nothing has gone wrong — the user has been on the step
for zero seconds. Colouring the very first instruction as an error trains people to ignore
the colour.

**Fix.** Show `current.hint` in the neutral style on arrival, and only switch to the coral
`gateHint` after the user actually taps a disabled `Next`. Track a `hasAttemptedNext` flag
per step.

### F8. Back/Next sit above the content they navigate
**`SetterPanel.kt:185–202`**

Reading order on every step is: step chips → hint → **Back / Next** → the actual controls
(feet-rule chips, role palette, details form). The user meets the navigation before the
thing they're supposed to do, and the controls are the part most likely to be cut off by
the fold.

Both are also low-emphasis `TextButton`s. In a gated 5-step wizard, `Next` is the primary
action on four of the five steps.

**Fix.** Move the Back/Next row to the bottom of the card (or into the pinned bar from F1),
and make `Next` a filled `Button`.

---

## Library and navigation flow

### F9. Filtering gives no visible feedback
**Reproduced on device. `ProblemsScreen.kt:98–184`**

Expanding Filters renders four stacked filter rows (Status, Grade, Feet rule, Setter) that
together fill the **entire viewport**. The results list is pushed completely below the fold.

I selected Status → Draft. The visible screen changed by exactly one chip highlight. The
header still read **"5 problems"** while the list beneath — invisible — was down to one.
There is no way to tell a filter did anything without scrolling away from the controls.

Two separate bugs compound here:

- **The header count ignores filters** (`ProblemsScreen.kt:100` uses `state.problems.size`,
  not `visibleProblems.size`).
- **Each filter row is its own horizontal scroller**, clipped mid-option ("Published" cut
  off, the grade row cut off). Four horizontally-scrolling rows nested in a vertical
  `LazyColumn` is also a gesture-conflict risk.

**Fix.**

1. Header shows `visibleProblems.size`, with the total appended when filtering: `"1 of 5 problems"`.
2. Collapse the filter panel to a single row of *active* filter chips once a selection is
   made, so results stay visible.
3. Consider a bottom-sheet filter surface instead of inline expansion — it dismisses back to
   the results, which is the feedback the current design lacks.

### F10. "All" doesn't include archived, and archived is where deleted things go
**Reproduced on device. `ProblemsScreen.kt:79–81`**

`ALL` maps to `publicationState != ARCHIVED`. Since Archive is also the only disposal route
(F4), the flow is:

> archive something → it vanishes → open Filters → the option labelled **"All"** confirms
> it isn't there → conclude it's gone

**Fix.** Rename the default to **"Active"**, and add a real **"All"** that includes archived.

### F11. Archive has no confirmation and no feedback
**Reproduced on device. `ProblemsScreen.kt:308–314`, `BoardViewModel.kt:156–158`**

I archived the test draft from the overflow menu. The row simply disappeared. No dialog, no
snackbar, no undo. The menu item sits directly below "Duplicate", so a mis-tap silently
removes a problem from view with no trace.

Note the inconsistency: "Clear all holds" *does* get a confirmation dialog
(`SetterPanel.kt:134–151`), and it's the more recoverable action of the two.

**Fix.** `emit(BoardEvent.Message("Archived ${problem.name}"))` with an **Undo** action
wired to `unarchiveProblem`. The snackbar-with-undo pattern already exists for rejected
taps (`BoardAfApp.kt:168–178`) — reuse it. A dialog is the wrong tool here since the action
is reversible.

### F12. State restoration is inconsistent across tabs
**Reproduced on device**

Switching Board → Problems → Board **restored the Board's scroll position** exactly.
Switching Problems → Board → Problems **reset the filter panel** to collapsed and every
filter to "All", despite `restoreState = true` (`BoardAfApp.kt:256–262`).

The filter reset may be deliberate — it matches the philosophy in the comment at
`ProblemsScreen.kt:119`. But the *scroll* position resetting too means you also lose your
place in a long list. Pick one model and apply it to both.

### F13. Needs-review problems bury the fix four screens down
**Reproduced on device**

Opening "Tidepool" (status: Needs review) shows the coral **"Needs review"** chip in the
header. The explanation of *why* — "h43 is foot-only. Move the start to a hand-capable
hold." — is roughly **four scroll gestures** below it, under the full-height board.

The **Repair** button is further down still, and it is rendered as an `OutlinedButton`
identical in weight to Duplicate, Publish and Archive — four equal buttons in a 2×2 grid
(`BoardScreen.kt:306–354`). Nothing signals which one you're supposed to press.

**Fix.**

1. Put a one-line reason next to the status chip in the header, or make the chip tappable to
   scroll to the detail.
2. Promote **Repair** to a filled `Button` when `publicationState == NEEDS_REVIEW`, and
   demote Archive to a text button or move it into an overflow menu. Destructive and primary
   actions should never share a visual weight.

---

## Board rendering flow

### F14. The overlays hide the holds you're choosing between
**Reproduced on device. `BoardSurface.kt:294–352`**

In **SET** mode every unassigned hold gets a filled dark disc
(`Color(0x55273338)` at 26dp) drawn on top of it. In **CONFIGURE** mode it's worse — a solid
opaque cyan disc (`CapabilityDot`, 28dp) that completely covers the hold underneath.

The Setup screen then asks the user to "tap holds below to correct exceptions" — i.e. to
visually verify each hold's classification against the photo — while covering every hold in
the photo with an opaque dot. The one screen whose entire purpose is visual verification is
the one that blocks the view.

**Fix.** Make both overlays **outline-only** by default (stroke ring, transparent centre) so
the hold reads through. Reserve fills for *assigned* roles, where the marker is meant to
dominate. The `ProblemMarker` glyphs are already ring-based and work well — apply the same
logic to the unassigned and capability states.

### F15. The rejection snackbar names an internal hold ID
**Reproduced on device. `SetterReducer` rejection messages**

Tapping a kickboard hold as a start produced: *"h37 is a foot-only kickboard hold — it
can't be a start hold."* with a "Mark as foot instead" action.

The recovery action is genuinely good design. But `h37` is a database key. A user standing
at a wall has no way to connect it to a physical hold — they know *which hold they just
tapped*, so naming it adds nothing and costs comprehension.

Two more issues on the same snackbar:

- It renders at the bottom, **covering the setter panel header**.
- `SnackbarDuration.Short` (~4s) on a snackbar that carries an action
  (`BoardAfApp.kt:173`). Material guidance is `Long` when there's something to tap — 4s is
  not enough to read, decide and reach.

**Fix.** Drop the ID ("That hold is foot-only — it can't be a start hold"), or briefly pulse
the rejected marker on the board instead. Switch to `SnackbarDuration.Long` when
`offerFootInstead` is true.

### F16. The marker legend is clipped and hidden from the setter
**Reproduced on device. `ProblemMarker.kt:150–165`, `BoardScreen.kt:303`**

The legend renders inside a `horizontalScroll` and on this device the fourth item is cut
mid-word — it reads **"Finisl"**. It also only appears inside `ProblemDetails`, so it is
absent during setting, which is exactly when someone is learning what a dashed ring versus
a double ring means.

**Fix.** Wrap the legend (`FlowRow`) instead of scrolling it, and surface it in the setter —
the role palette chips already carry `ProblemMarker` glyphs, so extending that to a
persistent legend is cheap.

---

## Smaller flow issues

| # | Finding | Location |
|---|---|---|
| F17 | **"1 holds"** — no pluralisation on hold counts. Reproduced on device. | `ProblemsScreen.kt:264`, `SetterPanel.kt:274` |
| F18 | Long names break the card layout — "Chalk ghost" pushed its status chip onto two lines and wrapped the subtitle. No `maxLines`/`overflow`/`weight` on the title. Reproduced on device. | `ProblemsScreen.kt:254–262`, `BoardScreen.kt:184–192` |
| F19 | "Confirm your board zones" onboarding card persists indefinitely — still showing with 5 problems already in the library, and nothing gates on it. Either make it a real first-run step or let it be dismissed. | `SetupScreen.kt:96–122` |
| F20 | Publishing from the detail card emits no confirmation; publishing from the wizard does. Same action, inconsistent feedback. | `BoardViewModel.kt:180–194` vs `:321–339` |
| F21 | The kickboard slider commits on every drag frame — rebuilds the board, re-validates every problem, writes to DataStore and pushes to cloud sync, dozens of times per drag. A published problem can silently flip to Needs review mid-drag. No numeric readout of the value. | `SetupScreen.kt:187–191` → `BoardViewModel.kt:345`, `:376–390` |
| F22 | Undo/redo appear only inside `HoldStep`, but the destructive undoable action — switching to Campus, which deletes all foot marks — happens on the **Feet rule** step, where no undo button is rendered. The snackbar promises "Undo restores them" and there is no undo on screen. | `SetterPanel.kt:259–271` vs `BoardViewModel.kt:270` |
| F23 | Hold touch targets are 44dp; Android's minimum is 48dp. Relevant for a chalky, arm's-length interaction. | `BoardSurface.kt:102`, `:279` |
| F24 | Zoom/pan resets on rotation — `transform` uses `remember`, not `rememberSaveable`. | `BoardSurface.kt:89` |
| F25 | Search has no clear button and no IME action; the keyboard covers the results with no way to dismiss to them. | `ProblemsScreen.kt:107–114` |
| F26 | Sign-in buttons disable until the password is ≥6 chars with no inline explanation, and the password field has no visibility toggle. | `CloudSyncCard.kt:85`, `:100–108` |
| F27 | New problems always get `setter = "You"`. Existing data has real names (Jono, Maya), so once sync is live the library will mix "You" with real names ambiguously. *(Correction to my earlier pass: the Setter filter does appear — I confirmed Jono/Maya/You on device.)* | `ProblemModels.kt:90` |
| F28 | No dark theme. `BoardAfTheme` hardcodes `lightColorScheme`. A near-white full-screen palette in a dim home gym at night is a real cost. | `Theme.kt:25–48` |
| F29 | `TODO.md`'s swipe-between-problems is unimplemented — worth pairing with F1's layout change, since both restructure the view state. | — |
| F30 | README says sessions on existing problems "land directly on details & review". Resuming my incomplete draft landed on **step 2**. Docs and behaviour disagree; worth confirming which is intended. | `SetterReducer.start` |

---

## What's working

- **The rejection-and-repair path is genuinely well designed.** Haptic reject, a plain-language
  reason, and a one-tap "Mark as foot instead" recovery (`BoardAfApp.kt:168–178`). Most apps
  would have shown a dialog and made you start over.
- **Gate hints explain *why* Next is disabled** rather than just greying it out
  (`SetterPanel.kt:186–191`). Rarer than it should be — the problem is only the colour and
  the timing, not the idea.
- **The marker system is colour-blind safe by construction.** Four roles, four distinct
  shapes, one shared implementation used by board, legend, palette and banners
  (`ProblemMarker.kt`) so they cannot drift apart. On device the four glyphs are immediately
  distinguishable.
- **Hold semantics for TalkBack are thorough** — id, zone, capability, assignment, plus a
  "(corrected)" suffix in configure mode (`BoardSurface.kt:261–274`).
- **The tablet layout already solves F1.** The ≥840dp branch puts board and panel side by
  side. The phone layout just needs to catch up.

---

## Suggested order

1. **F1** — board/controls split. Everything else in the setter flow is downstream of this.
2. **F2, F3** — the wizard can strand users in a state with no forward path. Reachable by
   ordinary navigation.
3. **F4** — add delete, name untitled drafts, raise the autosave bar. The library currently
   can only grow.
4. **F9, F10, F11** — the library/archive loop, where the app appears to lose data.
5. **F13, F14** — surface the repair reason; stop the overlays hiding the wall.
6. **F5, F6, F7, F8** — session integrity and wizard polish.
7. The table.
