# Android Multi-Node Channel Hub UI/UX Implementation and Three-Phone Report

Date: 2026-08-25  
Track: Android `NTsocial MeshLink`; shared KMP contracts were also compiled for Desktop/JVM and iOS Simulator  
Branch/base: `multi_nodes_` / `ce4c4be182972a31edd69a8dac4ac302f5c3fc58` plus the current worktree

## Result

The two 2026-08-25 proposals were combined into one bounded implementation. The Android Messages destination is now a
real fleet channel hub: it presents one aggregate view and one tab per registered Meshtastic endpoint, while every
conversation still opens against the endpoint's independent Room database, DataStores, Koin scope, BLE session, and
connection generation. No multi-database Paging merge, global RF scheduler, Gateway v3, MeshCore multi-node behavior,
or cross-radio write path was added.

The implementation, focused tests, root build/test/lint/KMP gate, exact APK installation, manual three-phone UI checks,
a controlled render-performance A/B, and a 107-minute unattended three-phone run are complete. The unattended run was
ended at the user's request after no further problem was observed; it is not represented as a three-hour result.

## Proposal decision

The selected design takes the strongest common recommendation from
`ANDROID_MULTI_NODE_CHANNEL_HUB_UI_UX_ARCHITECTURE_PROPOSAL_2026-08-25.md` and
`MULTI_NODE_CHANNEL_UI_UX_ARCHITECTURE_PROPOSAL_2026-08-25.md`:

- Keep radio/session/database ownership endpoint-scoped and immutable.
- Project only small, read-only conversation summaries out of each live endpoint scope.
- Aggregate those summaries in a root-owned `FleetChannelsRepository`.
- Address every child route with endpoint ID plus expected connection generation.
- Use a runtime-token-owned source registry so a stale closing session cannot unregister its replacement.
- Persist nickname, accent, and ordering in a separate appearance store; do not migrate or overload the existing radio
  profile schema.
- Limit phase one to channel discovery and navigation. Actual message reads/writes continue through the endpoint's
  established repository and packet graph.

This avoids the principal unsafe alternative: directly merging several independent Room/Paging streams and then
letting an unscoped route decide which radio receives a command.

## Implemented changes

### Fleet conversation projection

- Added shared fleet channel models, compact endpoint snapshots, source registry, appearance contract, and aggregate
  repository under `core/radio-fleet/.../conversation`.
- The aggregate publishes stable endpoint groups, connection state, channel role/index, last-message preview/time,
  unread counts, and a fleet total without moving database ownership out of the endpoint scope.
- Added deterministic tests for aggregation, ordering, appearance, source replacement, and empty/unavailable endpoints.

### Android endpoint ownership and routing

- Added `MeshtasticEndpointConversationSource`, an Android coordinator, and a runtime-token-protected registry.
- Session shutdown removes only its own source and closes the exact endpoint scope.
- Added `EndpointScopeHost`: endpoint routes fail closed if the endpoint or expected generation is stale instead of
  silently rendering through the root/primary graph.
- Added endpoint-addressed Messages, contacts, share, and quick-chat routes. Back navigation retains the correct
  top-level stack and endpoint ownership.

### Modern channel hub UI

- Replaced the old endpoint-only Messages entry with a fleet overview and endpoint tabs.
- Added endpoint cards with accent rail/avatar, nickname, address suffix, connection badge, unread total, channel role,
  lock state, preview, timestamp, unread badge, and an `Open all conversations` action.
- Added an empty state for a phone with no registered radio.
- Added an appearance dialog for endpoint nickname/accent/order, persisted separately in DataStore.
- Added English, Traditional Chinese, and Japanese resources and updated the repository string index.
- Nodes and Settings retain their endpoint-scoped behavior; this change does not pretend those screens are aggregate.

### Performance correction found during device testing

The first device build showed continuous frames on otherwise static screens. A controlled screen-off/static-screen
comparison traced the cause to `AnimatedConnectionsNavIcon`: every Meshtastic activity restarted a one-second glow,
so ordinary packet activity kept the common bottom navigation rendering continuously.

The fix removes that decorative perpetual animation and uses the existing static `ConnectionsNavIcon`. It does not
change scanning, BLE priority, packet processing, or connection state.

On the connected Samsung, the same 20-second static Channel Hub observation changed from 366 rendered frames to 2;
on the connected OPPO it changed from 62 to 8. Follow-up 30-second process sampling was about 3.0% and 8.87% of one
core respectively, and a subsequent per-thread window was 3.43% and 2.50%; RenderThread was no longer a leading
consumer. This is a causal device A/B for the animation, not a battery-life benchmark.

### Cross-platform integration boundary

- Desktop receives the shared dependency and an explicit no-op fleet repository, preserving its existing single-radio
  interface. The full gate found the initially missing Desktop binding; the binding was added and the entire Desktop
  Koin/test slice was rerun successfully.
- The shared KMP/iOS Simulator compilation remains green. No Windows or iOS host UI was changed or device-tested.

## Source validation

Focused tests passed:

```text
:core:radio-fleet:jvmTest
:core:prefs:jvmTest
:core:navigation:jvmTest
:feature:messaging:jvmTest
:app:testGoogleDebugUnitTest
```

Changed-module Spotless and Detekt passed. The JDK-21/en-US root gate ran:

```text
spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile
:app:lintFdroidDebug :app:lintGoogleDebug --continue
```

It completed 2,000 actionable tasks (585 executed, 3 from cache, 1,412 up-to-date). The run exposed one new Desktop
Koin omission; after the correction, `:desktop:spotlessApply :desktop:spotlessCheck :desktop:detekt :desktop:test`
completed 177 tasks with `BUILD SUCCESSFUL`, including all 32 Desktop tests. Across the final source, formatting,
Android Debug assemblies, tests/`allTests`, both Android Debug lints, Desktop/JVM, shared KMP, and iOS Simulator
compilation passed. Root Detekt remains nonzero only for the six documented pre-existing findings in unmodified BLE
(3), domain precise-location sharing (1), model (1), and network (1) sources.

`git diff --check` passes; only Git's existing LF-to-CRLF working-copy warnings are printed.

## APK and installation

Artifact:

```text
app/build/outputs/apk/google/debug/app-google-arm64-v8a-debug.apk
versionName=1.0.6
versionCode=7
size=52,672,620 bytes
SHA-256=F6E92B9C55D5837DCA920B8A5CD22713B770CFA76E693FBFA6B0DE6CE78028D8
```

Official Android SDK `zipalign -c -P 16 -v 4` reports successful verification. The APK was installed with data
preservation on all three Android 16/API-36 phones. Each installed `base.apk` has the exact same SHA-256, and the
retained first-install times show that application data was not cleared.

| Device | Role during this run | Final process/service result |
| --- | --- | --- |
| SM-S9080 (`R5CT30QMRTY`) | No configured Meshtastic radio | Stable process; no connected-device FGS after grace |
| SM-S9280 (`R5CX42P0SDH`) | One BLE node, `Meshtastic_5d6e` | Same PID throughout; Connected and FGS throughout |
| OPPO CPH2695 (`TWBYJJRWSGHIGU55`) | One BLE node, `Meshtastic_1407` | Same PID throughout; Connected and FGS throughout |

## Manual device validation

- The no-radio phone renders a localized `0 nodes / 0 channels` empty state.
- Both connected phones render the correct endpoint, five configured channels, primary/secondary roles, locks,
  previews, timestamps, and unread counts without cross-endpoint contamination.
- Endpoint appearance dialogs render and persist correctly.
- Opening a channel enters the exact endpoint conversation list; reading it updates the corresponding unread count.
- Back, Nodes, Settings, Connections, and endpoint suffix/state checks remained in the same process and showed the
  expected radio.
- No RF message was sent. Existing histories were inspected read-only to avoid generating unintended traffic.

## Unattended run

Evidence directory:

```text
.agent_plans/channel-hub-device-evidence/soak-20260825-113410
```

The run contains 324 complete samples: minutes 0 through 107 inclusive, 108 samples per phone. UI checkpoints at
minutes 0, 30, 60, and 90 produced 12 screenshots/XML dumps and all 12 passed channel-hub visibility, expected-node,
Connected/expected-empty-state, and before/after PID checks.

| Device | Avg/max one-core CPU | RSS range | First/last 10-min RSS avg | Threads | PID changes | FGS |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| SM-S9080 | 0.23% / 1.27% | 292,448–308,992 KiB | 305,324 / 304,170 KiB | 38–54 | 0 | 0/108 expected |
| SM-S9280 | 3.85% / 14.40% | 334,376–392,684 KiB | 355,322 / 365,856 KiB | 53–64 | 0 | 108/108 |
| CPH2695 | 4.51% / 13.12% | 367,496–406,520 KiB | 398,526 / 371,018 KiB | 63–75 | 0 | 108/108 |

The isolated CPU maxima were short BLE/data events and returned to the 2–5% range on following samples. RSS repeatedly
rose and was reclaimed; the first/last-window comparison and bounded threads show no monotonic leak in this interval.
All cumulative safety counters stayed zero: fatal exception, App ANR, process death, and conversation projection error.

The final App-PID-filtered logcat contained 254/6,592/7,276 lines for the three phones with zero fatal, ANR, process
death, Koin, Room, illegal-state, or projection matches. The OPPO log contained four `IJankManager slideSceneEnd`
messages from vendor UI instrumentation during automated swipes; there was no App exception or stack trace.

## Correctly bounded conclusion

The redesigned Channel Hub and the single-radio-per-phone behavior are stable and efficient in the tested Android
Debug configuration. The continuous decorative render defect is resolved, endpoint routing remains fail closed, and
the 107-minute run found no remaining reproducible P0/P1 in the exercised UI, aggregation, process, or Bluetooth
connection path.

This does **not** prove two or four simultaneous radios on one phone, independent multi-radio writes, reconnect storms,
Doze, Profile/Release energy, Gateway caller admission, RF airtime, remote receipt, signed release, Play acceptance, or
Windows/iOS device behavior. Those remain separate gates and should not be inferred from this three-phone run.
