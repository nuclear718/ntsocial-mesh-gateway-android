# Android Simultaneous Multi-Node BLE Remediation Report — 2026-08-31

## Scope and result

This change affects the Android `NTsocial MeshLink` track. It corrects the reproduced failure where the primary
Meshtastic endpoint connected but a configured secondary endpoint stopped at an error instead of owning an independent
BLE session. No production Desktop/Windows or iOS source was changed.

The corrected Android build connected both available radios concurrently on an Android 16 Samsung SM-S9280. Each
radio completed Stage 2, appeared as connected in the two-of-four fleet UI, and retained its own MeshLink GATT client.
A deliberate secondary-only disconnect and reconnect left the primary connected and recreated only the secondary
client.

## Root cause

The secondary endpoint Koin scope used constructor-reference `scopedOf` registrations for objects whose constructors
contain qualified `Lifecycle`/`CoroutineScope` parameters and Kotlin `Lazy<T>` dependencies. At runtime, the
constructor-reference path attempted to resolve unqualified `Lifecycle` and raw `Lazy` definitions, so the secondary
graph failed before `MeshConnectionManagerImpl` could activate its transport. The primary compatibility graph did not
exercise this endpoint-scope path.

The session also marked graph wiring complete before resolution. A first resolution failure could therefore prevent a
later attempt from wiring the endpoint after the underlying problem was corrected or recovered.

## Source changes

- Replaced the affected constructor-reference registrations with explicit scoped factories that request
  `ProcessLifecycle`, `ServiceScope`, and concrete `lazy { get<T>() }` dependencies.
- Bound secondary endpoints to `SecondaryGatewayRepository`, preserving the documented primary-only Android Gateway
  v1/v2 ownership boundary.
- Marked a secondary session wired only after the full connection graph resolves and its receive buffer is reset.
- Added `SecondaryRadioEndpointScopeRuntimeTest`, which starts the production Koin graph, creates a real secondary
  endpoint with its own Room/DataStore resources, resolves `MeshConnectionManagerImpl`, verifies fail-closed Gateway
  access, and closes the session.

Existing `DefaultRadioFleetManagerTest` coverage continues to verify four simultaneous endpoint-session identities and
independent generations; the product constant remains `MAX_RADIO_ENDPOINTS = 4`.

## Validation

- Focused App graph/Koin tests: 4 passed.
- Changed Android App Detekt: passed.
- Google arm64 Debug assembly: passed.
- Full JDK-21/en-US gate:
  `spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile :app:lintFdroidDebug :app:lintGoogleDebug --continue`.
  It completed 2,018 actionable tasks (394 executed, 2 from cache, 1,622 up-to-date) in 2m31s. Formatting, both Android
  Debug assemblies, tests/`allTests`, Desktop/JVM, shared KMP/iOS Simulator compilation, and both Android lints passed.
  Exit 1 was exclusively the six already-recorded Detekt findings in unmodified BLE (3), domain (1), model (1), and
  network (1) sources.
- Final Google arm64 Debug APK: 52,961,456 bytes; SHA-256
  `19CF41C5125DDA5970229A43D35C9920459294CD7BEBEAB7387E5AF6167D019D`.

## Physical-device evidence

The APK was installed with replacement semantics, preserving the S24's existing App data and radio catalog.

- `Meshtastic_5d6e` and `Meshtastic_1407` both completed Stage 2.
- Android Bluetooth state showed two concurrent encrypted LE links and two active MeshLink GATT clients in the same App
  PID.
- Connections displayed `Meshtastic 節點 2 / 4` and connected state for both radios.
- Disconnecting only `1407` removed only its MeshLink client; `5d6e` remained connected.
- Reconnecting `1407` created a new client, completed Stage 2 again, and did not replace or disconnect `5d6e`.
- The full gate's final APK was then installed data-preservingly; both radios again completed Stage 2, the UI showed
  `Meshtastic 節點 2 / 4` with both connected, and both links plus the same new App PID remained present through the
  final 30-second background check. The installed base APK hash matches the final artifact.
- No fatal, ANR, missing-Koin-definition, or secondary-setup-timeout signal appeared.

Only address suffixes are retained in this report; full Bluetooth addresses are intentionally omitted.

## Evidence boundary

This is direct two-radio Debug hardware proof for the reported Android failure plus source/test proof of the one-to-four
capacity. Only two Meshtastic radios were available on the S24, so this is not a three- or four-radio physical run. It
also does not prove RF send/remote receipt, independent connected-radio configuration mutation, Doze, a long-duration
soak, Profile/Release-device behavior, signing/store acceptance, or Windows/iOS device behavior.
