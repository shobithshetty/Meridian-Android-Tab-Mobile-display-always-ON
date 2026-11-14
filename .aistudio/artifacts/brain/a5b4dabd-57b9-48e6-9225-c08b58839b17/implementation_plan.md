# Battery Settings Launch & Live Status Refresh Plan

Fixes the unresponsive **Open Battery Settings** action so tapping it reliably opens the system App Info and Battery settings screens without requesting restricted manifest permissions, while keeping the dialog open and automatically updating battery restriction status upon return.

## User Review & Critical Decisions

> [!IMPORTANT]
> Both key behavioral decisions below were confirmed during clarification and govern this design.

- **Confirmed Decision 1 — Zero-Permission Settings Launch**: Do not add `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` to the manifest. Launch standard zero-permission Android Settings screens directly (App Info scoped to this app first, with fallback to the system Battery Optimization list) so Google Play Policy compliance is preserved and the OS never silently cancels the screen.
- **Confirmed Decision 2 — Keep Dialog Open & Auto-Refresh on Return**: Do not dismiss the Battery Saver dialog when launching system settings. Observe Android `ON_RESUME` lifecycle events so the status indicator ("Optimized" vs "Unrestricted") and card badge refresh automatically as soon as the user returns from system settings.

---

## 1. Overview & Core Concept

- **What It Does**: Replaces the silently-failing `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` call with a reliable, verified intent launch chain that opens this app's system **App Info** page (where **App battery usage / Battery** is one tap away) or the system **Battery optimization** list, keeps the in-app **Battery Saver Settings** dialog visible underneath, and re-queries `PowerManager.isIgnoringBatteryOptimizations` automatically when the user navigates back.
- **Root Cause Fixed**: On Android, calling `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` without declaring the restricted `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission resolves to a system activity that immediately finishes with no visible UI and throws no exception—preventing all fallback `catch` blocks from ever executing.
- **Target Audience & Key Value**: Ensures users relying on 15–20 minute focus chimes and scheduled world alarms can reliably locate and disable Doze restrictions and verify the updated "Unrestricted (Optimal)" badge immediately without reopening settings menus.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. User opens **Settings → Reliability & Portability → Battery Optimization** card.
  2. The **Battery Saver Settings** dialog opens showing the current status badge (`Optimized (Restricted)` or `Unrestricted (Optimal)`), a concise 2-step guide (`App Info → App battery usage → Unrestricted`), and action buttons.
  3. User taps **OPEN BATTERY SETTINGS** (primary action) or **BATTERY LIST** (secondary shortcut to the system-wide battery optimization list):
     - Android opens the requested system settings screen cleanly in a new task or on top of the app.
     - The dialog remains open in the background rather than prematurely closing.
  4. User changes battery mode to **Unrestricted** and presses Back to return to the app.
  5. On `ON_RESUME`, the dialog and underlying **Battery Optimization** settings card immediately re-evaluate `PowerManager` status and flip the badge from amber **Optimized** to green **Unrestricted (Optimal)**.
  6. If a stripped-down device or custom ROM lacks both system settings handlers, an inline error notice displays inside the dialog instead of silently doing nothing.
- **Visual Identity & Theme**:
  - Inherits the active crisp, zero-haze theme tokens (`bgEnd`, `cardBackground`, `dividerColor`, `accentColor`, `onAccentColor`, `textPrimary`, `textSecondary`) with solid opaque surfaces and `1.dp` crisp borders (`48.dp` minimum touch targets on all buttons and cards).
  - Status badges keep clear semantic contrast: green check badge when unrestricted, amber warning badge when restricted.
  - A compact step-by-step instruction card sits above the action row so users know exactly where to tap once **App Info** opens.
- **Interactive Feedback & Error Handling**:
  - Ripple feedback on both action buttons and the settings card.
  - Clear inline fallback banner inside the dialog if no compatible Android Settings activity exists on the device.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1 — App Info (`ACTION_APPLICATION_DETAILS_SETTINGS`) as Primary Target vs System Battery List**
  - *Chosen Approach*: Launch **App Info** (pre-scoped to the app's package URI) as the primary **OPEN BATTERY SETTINGS** destination, with a secondary **ALL BATTERY APPS** button for `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`, and automatic fallback between them if `resolveActivity` returns null.
  - *Why*: On modern Android (Android 12–15+ and OEM skins), `ACTION_APPLICATION_DETAILS_SETTINGS` opens directly to this specific app's page on every device and emulator, putting **App battery usage / Battery** one tap away without making the user hunt through an "All apps" list—while requiring zero restricted permissions.
  - *Alternatives Considered*: Keeping `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with manifest permission (rejected due to Google Play policy scrutiny and user choice for zero-permission compliance) or opening only the global Battery Optimization list (harder to locate the app when filtered to "Not optimized").

- **Decision 2 — Lifecycle-Driven Status Refresh & Non-Dismissing Dialog**
  - *Chosen Approach*: Keep the dialog open when launching settings and attach a `LifecycleEventObserver` listening for `Lifecycle.Event.ON_RESUME` to refresh `isIgnoringBatteryOptimizations`.
  - *Why*: Eliminates the disorienting UX of closing the dialog on tap and forcing the user to reopen Settings → Battery Optimization just to verify their change took effect.
  - *Alternatives Considered*: Refreshing only when the dialog visibility boolean toggles (fails to update while the dialog stays open across an external Activity pause/resume cycle).

---

## 4. Technical Architecture & Data Strategy

```
┌──────────────────────────────────────────────────────────────────────────┐
│ Settings Pane (Lifecycle-Aware)                                          │
│  ├─ LocalLifecycleOwner (ON_RESUME) ──► Re-queries PowerManager status   │
│  ├─ Battery Optimization Card ────────► Shows live status & opens dialog │
│  └─ Battery Settings Launcher                                            │
│       ├─ Safe Activity / NEW_TASK Context Handling                       │
│       ├─ Primary: ACTION_APPLICATION_DETAILS_SETTINGS (package:<id>)     │
│       ├─ Secondary: ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS          │
│       └─ Returns Launch Result (Opened / Unavailable)                    │
└───────────────────────────────┬──────────────────────────────────────────┘
                                │
                                ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ Battery Saver Dialog (Stays Open Until Explicitly Closed)                │
│  ├─ Live Status Header (Optimized / Unrestricted)                        │
│  ├─ 2-Step Navigation Guide (Battery Usage → Unrestricted)               │
│  ├─ Inline Unavailable Notice (Shown only if both intents fail)          │
│  └─ Actions: [Close]  [Battery List]  [Open Battery Settings]            │
└──────────────────────────────────────────────────────────────────────────┘
```

- **State & Lifecycle Flow**:
  - Track `isIgnoringOpt` in Compose state initialized from `PowerManager.isIgnoringBatteryOptimizations(packageName)` and refreshed inside a `DisposableEffect(lifecycleOwner)` whenever `Lifecycle.Event.ON_RESUME` fires (as well as when the dialog opens).
  - Track a lightweight `launchErrorNotice` boolean/message inside the dialog that clears whenever settings open successfully or on `ON_RESUME`, and surfaces a clear fallback instruction if `startActivity` cannot resolve any settings screen.
- **Intent Launch Safeguards**:
  - Check `intent.resolveActivity(packageManager)` before calling `startActivity`, and unwrap any `ContextWrapper` to an `Activity` (or attach `Intent.FLAG_ACTIVITY_NEW_TASK` if launching from a non-Activity context) so launching from inside a Compose `Dialog` never fails silently or throws a task flag exception.
  - Provide deterministic return values (`Boolean`) from the launcher helper and cover intent selection and status query edge cases with unit tests.
