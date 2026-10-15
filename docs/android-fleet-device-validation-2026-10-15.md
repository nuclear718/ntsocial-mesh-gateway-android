# Android fleet repair: installation and device validation — 2026-10-15

Base revision: `9e2f0cb15d728fd5f45daa8698b8e1e99e85f66d`, plus the working-tree repair and the additional corrections discovered during this device run. Times below use Asia/Taipei.

The user authorized data-preserving upgrades of the connected Android phones, release/reconnect and Channels/QR/Settings testing, and a test message on an existing secondary channel. One physical Meshtastic radio was available. This report supplements [the architecture repair report](multi-radio-ownership-repair-2026-10-15.md). Both reports identify the final artifacts below, superseding the earlier implementation/intermediate APKs.

## Devices and final installed artifact

| Device | Model | Final installation | Device validation scope |
| --- | --- | --- | --- |
| S24 Ultra | Samsung SM-S9280 | Successful replacement, approximately 19:23 | Existing Meshtastic_1407 radio, release/reconnect, Channels and Settings |
| S22 Ultra | Samsung SM-S9080 | Successful replacement, approximately 19:23 | Launch and retained language onboarding |
| OPPO | CPH2695 | Successful replacement, approximately 19:23 | Launch and retained language onboarding |

The exact serials were matched before installation. A duplicate S24 mDNS transport represented the same physical phone, not another test device. Initial wireless ADB connections failed while direct TCP and ping checks reached the phones; restarting the empty stale ADB daemon restored all three authorized transports. No phone network settings were changed.

All three phones received the same ARM64 Google Debug APK with `adb install -r -t`. No uninstall or app-data clear was performed. Installed APK hashes, app IDs and original installation timestamps were checked after replacement.

- Package: `com.ntsocial.meshlink.google.debug`.
- Version: `1.0.8 (9)`; release versions were not incremented.
- Installed ARM64 APK: `app/build/outputs/apk/google/debug/app-google-arm64-v8a-debug.apk`.
- ARM64 SHA-256: `2686677726febeb9de7eb1f0e948c9bb569f5128144c8bbe2ab8fe7ae8496a42`.
- Universal APK: `app/build/outputs/apk/google/debug/app-google-universal-debug.apk`.
- Universal SHA-256: `8ff0788f8986d8a2eed8e31172b60896afa2e45063aea8d5885144f69c690b5c`.
- Verified signing certificate: Android Debug; SHA-256 `04e0aa192544dd21d001074ee22310884a697b9f20a103bc0cb6ec193d8a690c`, matching the installed package signer.

These are development APKs, not Store/release-signing artifacts.

## Data preservation

Private snapshots were retained before the initial replacement and around subsequent installation/release checkpoints. SQLite comparisons included the matching WAL and compared packet UUIDs, row-content hashes and persisted history epochs. Raw message contents, channel keys and private configuration are not reproduced in this report.

Original baseline versus final installation:

| Device / database | Packet count | Existing-row comparison | History epoch |
| --- | --- | --- | --- |
| S24 `meshtastic_database_8107f15267` | 11 → 11 | Every original UUID and full row unchanged | Unchanged |
| S24 `meshtastic_database_c189207a55` / `meshtastic_database_default` | 0 → 0 each | No removed rows | Both unchanged |
| S22 `meshtastic_database_c189207a55` | 0 → 0 | No removed rows | Unchanged |
| S22 `meshtastic_database_default` | 0 → 0 | No removed rows | Previously absent epoch initialized |
| OPPO both existing databases | 0 → 0 each | No removed rows | Both unchanged |

All three retain the same app ID and `firstInstallTime`. S24 retains endpoint UUID `2d10610f-25b3-436d-ac95-a372ad73553d`, its address identity and compatibility-primary binding. The v1 catalog snapshot remains present; the v2 catalog retains the same endpoint identity. Migration initially shows the remembered radio disconnected, as specified by the new connection-intent contract; reconnecting does not create another endpoint.

Runtime-managed database/WAL and preference files can change during replacement/startup. This report establishes the comparisons above, not byte-for-byte identity of the entire app container.

## Device failures found and repaired

The first installed repair correctly stopped BLE and persisted release, but exposed additional UI/lifecycle defects during testing. These observations belong to the earlier artifact, not the accepted final APK.

1. **Connections remained inside the retired endpoint scope.** After opening Channels and LoRa settings, disconnecting the selected radio stopped its transport but left Connections showing endpoint-unavailable indefinitely. Waiting more than 45 seconds and pressing the same tab did not recover it. Navigation 3 1.1.1 caches its provider-created entries by the backstack key list; changing only a synthetic `contentKey` and provider closure could not replace the unchanged global Connections key. Connections now stores its selected endpoint/runtime in the actual navigation key. Release replaces it with the global management root; reconnect creates a different runtime key. First binding also clears a restored owner when no endpoint is active.
2. **Selected-radio identity leaked from the legacy scanner.** The disconnected root could still display the remembered first-radio identity and an ineffective disconnect control. The connection card now derives address/name and disconnect ownership from the exact entry endpoint/runtime, with scoped node and progress flows. A root without an active endpoint shows disconnected state. Captured disconnect actions validate their original runtime instead of consulting a later selected radio.
3. **Endpoint tabs and disconnected-state presentation needed correction.** Safe-area handling now keeps endpoint tabs below system UI, and endpoint-unavailable content provides a Connections recovery action. These shared UI changes affect Android/iOS; Windows retains its single-radio path.
4. **UI disposal could cancel release between persistence and teardown.** Persisting disabled intent can dispose the initiating Connections entry. The admitted disconnect operation now protects persistence through bounded teardown from that caller's cancellation. Removing a secondary profile likewise completes its admitted release/removal. Two deterministic store-barrier tests cover cancellation after disabled state is published and cancellation during secondary-profile removal; unrelated primary state remains unchanged.

Regression coverage includes real Compose execution through Navigation 3's `rememberDecoratedNavEntries` cache, plus root → endpoint A → disconnected → replacement A → endpoint B transitions, inactive/deep-link roots, cold restored-owner cleanup and scoped connection-identity rejection. This is stronger than a route-object comparison alone, but remains software evidence rather than simultaneous physical-radio testing.

## Final S24 acceptance

The live radio was **Meshtastic_1407**. Historical references to radios 0809/5d6e were not used as assumptions about this run. The radio exposes eight channel slots; the existing NTsocial secondary channel is slot 3.

| Scenario | Final-artifact result | Evidence boundary |
| --- | --- | --- |
| Fleet-card disconnect while on Connections | Active count changed **1 / 4 → 0 / 4**; the same page recovered and displayed the remembered device without a navigation reset | One actual radio and one released lease |
| Cold relaunch after release | Remained **0 / 4**, with the remembered profile and no transport restart | Persisted disconnect tested across process restart |
| Reconnect remembered radio | Returned to **1 / 4** and the original 1407 radio | Same retained endpoint identity |
| Hub → selected radio Channels | Correct radio/channel content opens | One ready endpoint |
| Selected radio Settings → LoRa | Correct 1407 context; existing Taiwan / `MEDIUM_FAST` settings displayed | Read-only navigation; no LoRa configuration write |

### Lower connection-card disconnect

The lower connected-device detail card's Disconnect action was tested separately from the top fleet-card action. It immediately changed **1 / 4 → 0 / 4**, with Connections usable afterward. Switching back to Settings reset the old LoRa child stack to Settings root. The offline root retained the cached 1407 label, but all radio settings were disabled; tapping LoRa did not navigate or issue control. This is retained offline identity, not an active radio owner. Evidence: `final-legacy-disconnect-released`, `final-settings-after-release.xml` and `final-released-settings-blocked` captures. Reconnect and final handoff are recorded separately below.

### QR scanner

The final APK's managed Channels page displayed the 1407 selector and all eight channel names. Its QR action opened the live Android camera, camera-use indicator, reticle and close control. Closing returned to the same Channels page and eight channels. Evidence: `final-managed-channels.xml`, `final-qr-camera.png` and `final-qr-closed.xml`. Camera permission had been granted through the OS prompt during the earlier pass. No QR payload was optically decoded or applied, and no channel/LoRa configuration was written. Optical decoding, QR mutation and fresh radio readback remain separate acceptance cases.

### Secondary-channel message: queue admission passed, delivery failed/unconfirmed

Exactly one test message, `MeshLink QA 20261015 CH3`, was sent at 19:32:12 through existing NTsocial secondary channel 3, native text port 1. Its packet ID was `1520771016`.

The local row was inserted, and firmware returned matching QueueStatus at **19:32:12.987**, with `res=0`, `free=15`, `maxlen=16`; the packet queue recorded success. At **19:32:30.162**, however, the matching routing response was `routeErr=5` (`MAX_RETRANSMIT`), `isAck=false`. The persisted test row is UUID 12 with `status=ERROR`, `routing_error=5` and `wantAck=true`.

This passes **local insertion and firmware queue admission**, not end-to-end delivery. The subsequent negative routing result is retained as a failed/unconfirmed delivery, and no second receiver was available to establish RF receipt. No manual resend or additional test message was issued; firmware retry behavior is reflected by its routing result. Evidence: `private/final-complete-test-logcat.txt` and `logs/final-preservation.json`.

### Post-test preservation and handoff

The final post-test snapshot contains 12 packets: all 11 original UUIDs and full-row hashes are unchanged, and exactly one new row contains the test payload above. All existing S24 history epochs, the endpoint UUID and compatibility-primary binding remain unchanged. The persisted `channel_set.pb`, `local_config.pb` and `module_config.pb` files are byte-identical to the original baseline.

The original radio's v2 profile was restored to `enabled=true`, `autoConnect=true`. A final relaunch created PID `12590`, started transport at 19:32:44, connected at 19:32:49 and completed configuration Stage 2 at 19:32:55. The final check therefore leaves the original 1407 radio connected; current-process evidence contained no fatal/ANR markers. The test message's recorded routing failure remains in local history.

## S22 and OPPO launch smoke

Both final APKs render the existing English / Traditional Chinese / Japanese language-selection screen. S22 PID `17834` and OPPO PID `12439` stayed alive across the read-only capture, with MeshLink `MainActivity` top-resumed. Current-process logcat contained no `FATAL EXCEPTION`, `Fatal signal` or ANR markers. Screenshots were visually inspected. No language choice, onboarding completion, Bluetooth permission change or radio connection was performed on these phones.

## Final source validation

The final gate completed successfully in **3m38s**, with **2,097 tasks: 346 executed and 1,751 up-to-date**. It includes root formatting/Detekt, Debug builds, tests/allTests, KMP smoke compilation, both Android Debug lints, Desktop tests, explicit fleet and iOS JVM tests, iOS arm64 and simulator-test-source compilation, and simulator framework linkage. The new navigation-cache and cancellation regressions passed. No suppression was added to hide the intermediate complexity findings.

Selected current JVM reports contain 437 cases with zero failures/errors/skips: fleet 15, preferences 38, navigation 70, core UI 51, service 35, domain 104, iOS runtime JVM 26, Android app Google/F-Droid 11 each, Desktop 32 and Connections 44. This is a selected-suite inventory, not the entire gate.

The first device-follow-up gate returned failure for two Detekt complexity findings despite successful tests/builds/lints/native compilation. Those functions were refactored, and the final all-green gate above includes the accepted source. Its log is `/tmp/meshlink-device-acceptance-gate.log`.

Kotlin/Native test executable link/run remain disabled by repository convention. Native test-source compilation and framework linkage are not native test execution. This Android device session does not establish a new signed iOS-device or Windows hardware result. Existing build warnings, including the common-test Android-host-target warnings, are not represented as warning-free output.

## Evidence and remaining limits

Private, Git-ignored evidence is under `.agent_artifacts/android-fleet-qa-20261015/`, including:

- `logs/final-install-results.json` and `logs/final-apk-signature.txt`.
- `preservation-final-install.jsonl` and the local `compare_preservation.py` helper.
- `logs/final-preservation.json` and `private/final-complete-test-logcat.txt` for the one authorized test message and final data comparison.
- `private/*-before/`, `private/*-final-install/` and release checkpoints containing private snapshots.
- `private/s22-final-smoke/`, `private/oppo-final-smoke/` and S24 timestamped UI/log captures.

Only one physical radio was available. The run cannot prove a second ready radio's Channels/QR/Settings switching, simultaneous radio isolation, four active radios, releasing one of four then admitting a fifth, cross-radio command targeting, long background survival or RF delivery to another receiver. Those remain hardware acceptance work. Android Gateway v1/v2/v3 and parent import were not separately exercised in this run. The known live whole-history-reset epoch defect remains outside this change.
