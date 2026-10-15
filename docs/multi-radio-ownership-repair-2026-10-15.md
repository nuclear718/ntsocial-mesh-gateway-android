# Android / iOS four-radio ownership and release repair — 2026-10-15

Base revision: `9e2f0cb15d728fd5f45daa8698b8e1e99e85f66d`, plus this working-tree implementation.
Design input: [architecture investigation and proposal](../MULTI_RADIO_ARCHITECTURE_PROPOSAL_2026-10-15.md).
Affected tracks: Android and iOS fleet hosts, shared KMP features. Windows retains its single-radio host path.

## User-visible behavior

- **Disconnect releases a connection slot, including the first radio.** Remembered devices and active runtime leases are separate. A disconnected radio moves to the remembered list; its history, endpoint UUID, address-derived database and compatibility binding remain intact. Up to four active leases are admitted. A fifth remembered device can connect after teardown releases a slot.
- **Channels Hub opens the chosen radio's Channels / QR and Settings.** Hub selection and the shell use one fleet selection. Endpoint tabs also appear on radio conversation/detail pages. Changing radio in a local settings subpage keeps the kind of page (for example LoRa), reloads the selected radio's data and explains that unsaved settings were discarded. Remote node/message destinations and inactive stacks reset instead of transferring identifiers or drafts.
- **Settings and other radio ViewModels use their endpoint's dependencies.** Explicit feature constructors resolve from the owning scope. A missing endpoint ViewModel constructor fails closed rather than falling through to a root ViewModel factory. The compatibility primary also has its own disposable UI scope; its integration repositories remain host-owned.
- **QR preview names its target.** External channel/contact URLs first require an explicit ready endpoint. Scanner completion retains its originating screen/session; a retired Swift scanner is ignored. QR confirmation validates the captured radio epoch and readback revision before mutation.
- The connection badge and unread count describe the active fleet, including secondary radios and private-message unread totals.

## Ownership and lifecycle

`EndpointTarget(endpointId, runtimeId)` is stored on radio navigation entries. Entry scope, ViewModel key, saved state and callbacks retain that target. Changing UI selection never retargets an existing ViewModel or transfers Gateway ownership. A released/recreated endpoint has a new runtime ID, so restored entries cannot attach to its replacement. Production factories cover Settings, radio configuration, Channels, QR, Contacts/Messages, Nodes/metrics, sharing and Android firmware dependencies.

The fleet catalog is versioned as `radio_endpoint_catalog_v2`. The v1 serialized snapshot is retained. Upgrade migration retains IDs, addresses and compatibility-primary identity, but does not infer connection intent from the old always-enabled profiles: users reconnect desired radios from the remembered list. Later explicit disconnect persists `enabled=false` and `autoConnect=false` before teardown. No Room packet schema, protobuf, history epoch, stable message identity or accepted Gateway ledger is rewritten. Automatic downgrade/rollback to a v1 writer is not provided.

Each lease serializes its own startup/teardown, while separate radios start independently. Connection intents and transport callback epochs are fenced synchronously on revocation. Native pairing is part of the admitted iOS lease, so cancellation waits for prepared-peripheral cleanup before a slot is freed. Runtime shutdown cancels its UI models, transport/listeners, queues and child projection work before closing endpoint storage. Teardown has a 15-second bound: a failed or timed-out close keeps its slot reserved and offers another disconnect attempt; it does not pretend a fifth BLE connection is safe.

Device acceptance exposed a Navigation 3 entry-cache issue after release: changing an entry provider/content key did not replace an unchanged Connections backstack key. Connections now carries the actual endpoint/runtime key while selected, then returns to its global key on release. Its legacy device card reads the captured scope's identity and disconnect action, and cannot fall back to a stale primary device. Selector insets and the empty-endpoint connection action were also corrected. Admitted disconnect/remove operations complete durable intent persistence and bounded teardown even if publishing the new selection cancels the originating UI coroutine.

The first radio's compatibility adapter hydrates the committed address, switches the private database and loads its node cache before connection. Host startup no longer independently reconnects that root radio outside fleet admission. Android service startup checks active/desired fleet work before stopping for an absent primary selection. Primary release revokes its ingress/session and stops its queue while keeping host-owned Gateway replay state available. Other endpoints retain their own graphs and generations. Android v1/v2 and iOS primary-only Gateway ownership are unchanged; no automatic primary promotion occurs.

## Regression coverage

- Active A/B/C/D, release A, admit E, restart without A, reconnect A with the same persisted ID and a new runtime ID.
- Quota remains occupied during blocked/failed teardown; unrelated radio startup is independent.
- Cancellation of native pairing waits for peripheral cleanup; stale prepared requests and queued restoration cannot reacquire a released slot.
- Immediate revocation rejects exact-session and ordinary writes before native close; queued connect work is fenced, and a failed close retains its handle for retry.
- v1 migration, unlimited remembered catalog, primary-binding retention, and awaited address persistence.
- Settings-type navigation across radios, inactive/remote-stack reset, stale restore invalidation and endpoint-bound deep-link destinations.
- Android production endpoint factories construct real feature ViewModels and assert scoped repository/controller identity instead of root dependencies; missing constructors fail. iOS test source also checks the production factory's settings node source and QR construction.
- QR preview with an old transport epoch or readback revision emits no channel writes. Existing channel reliability, Gateway composition/durable dispatch and native/overlay tests remain part of the baseline.
- Actual Compose/Navigation 3 decorated-entry caching across release, reconnect and another owner; missing/stale Connections identity; cancellation during durable disconnect/remove publication.

## Validation

Executed on macOS with JDK 21, Android SDK `/Users/curry_tw/Library/Android/sdk`, Xcode 27.0 (27A266a), and iOS Simulator 26.5. The final gate after Android device corrections: **BUILD SUCCESSFUL**, 2,097 tasks (346 executed, 1,751 up-to-date), 3m38s.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/Users/curry_tw/Library/Android/sdk \
JAVA_TOOL_OPTIONS='-Duser.language=en -Duser.country=US' \
./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests \
  kmpSmokeCompile :app:lintFdroidDebug :app:lintGoogleDebug :desktop:test \
  :core:radio-fleet:jvmTest :ios:runtime:jvmTest \
  :ios:runtime:compileKotlinIosArm64 :ios:runtime:compileTestKotlinIosSimulatorArm64 \
  :ios:runtime:linkDebugFrameworkIosSimulatorArm64 --continue --console=plain
```

SavedState round-trips of endpoint/runtime-wrapped Settings and message routes retain the polymorphic destination and owner. The final complete gate includes these and the device-follow-up regression tests. No production source changed after this complete gate.

Selected test reports (all zero failures/errors/skips; this table is not the whole baseline):

| Suite | Executed cases |
| --- | ---: |
| core:radio-fleet JVM | 15 |
| core:prefs JVM | 38 |
| core:navigation JVM | 70 |
| core:ui JVM | 51 |
| core:service JVM | 35 |
| core:domain JVM | 104 |
| ios:runtime JVM | 26 |
| app Google Debug / F-Droid Debug | 11 / 11 |
| desktop | 32 |
| feature:connections JVM | 44 |

Kotlin/Native test **source** compiled, and the application framework linked. Native test executable link/run remain disabled by the repository convention; they were not executed.

Before the Android device follow-up, a signing-disabled Xcode build used the project scheme `NTsocialMeshLink`, a fresh DerivedData directory `/tmp/meshlink-fleet-ios-20261015`, and simulator `74AD0AA9-2188-4588-9238-1490E13DDC01` (MeshLink Fleet QA 2026-10-15). **BUILD SUCCEEDED**. The product retains AppIcon assets and minimum iOS 17.0. Xcode emitted one tooling warning: `Metadata extraction skipped, no AppIntents.framework dependency found`; therefore this run is not claimed warning-free. AppIntents integration/build settings were not changed just to hide it.

`simctl install` and `simctl launch com.ntsocial.meshlink.ios` succeeded, with process 11533 still present in the simulator's launch-service list at the follow-up check. The simulator was then shut down and retained for replay. This is installation/process-launch evidence only: no post-consent UI interaction, optical scan, physical BLE or Gateway RF experiment was performed.

The final shared corrections were compiled for iOS and the simulator framework linked in the final Gradle gate; Xcode simulator installation was not repeated for that later artifact. Local final gate: `/tmp/meshlink-device-acceptance-gate.log`. Earlier logs: `/tmp/meshlink-verified-gate.log`, `/tmp/meshlink-navigation-codec-final.log`, `/tmp/meshlink-xcode-simulator.log`. `git diff --check` passed. Existing chart deprecation/tooling warnings remain.

## Android test artifact

`app/build/outputs/apk/google/debug/app-google-universal-debug.apk` is the completed test APK. Package `com.ntsocial.meshlink.google.debug`, version `1.0.8 (9)`; release versions were not incremented. `apksigner verify` succeeds and identifies the **Android Debug** certificate. This is not a Store/release-signing artifact.

SHA-256: `8ff0788f8986d8a2eed8e31172b60896afa2e45063aea8d5885144f69c690b5c`.

The ARM64-only Debug APK installed on all three Android phones is available at `app/build/outputs/apk/google/debug/app-google-arm64-v8a-debug.apk` (SHA-256 `2686677726febeb9de7eb1f0e948c9bb569f5128144c8bbe2ab8fe7ae8496a42`). These hashes supersede the earlier implementation/intermediate device artifacts.

## Android device acceptance and remaining limits

The final Debug APK was installed with data-preserving replacement on S24 Ultra, S22 Ultra and OPPO. S24 with its existing Meshtastic_1407 verified both disconnect actions releasing 1/4 to 0/4, disabled intent surviving restart, explicit reconnect, owner-specific Channels/Settings and QR camera open/close. Its 11 existing packet rows, endpoint UUID, history epochs and radio configuration were preserved. One authorized NTsocial secondary-channel test was accepted by firmware QueueStatus, then returned MAX_RETRANSMIT; remote delivery was not confirmed. S24 was left reconnected. See [dated device validation](android-fleet-device-validation-2026-10-15.md) for exact artifacts and evidence.

Only one physical radio was available. On Android and iPhone, still verify A/B/C/D with different channel and LoRa settings; release the compatibility primary and connect E; rapidly switch Settings, Channels, QR and conversation pages; cancel a scan/pairing during a switch; and confirm commands only reach the selected radio. Optical QR decode/apply/readback, unaffected secondary sessions, Android v1/v2/v3 and primary-only Apple Gateway readiness/replay also need separate hardware acceptance. Software gates and this single-radio run do not establish four-radio RF delivery, long-background survival or Store release. The previously documented live-history-reset defect is outside this change.
