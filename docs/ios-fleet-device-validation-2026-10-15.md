# iOS fleet repair: data-preserving installation and device validation — 2026-10-15

Source revision: `cb2c7cf960b12934e1d2467aa8e8cfa416aa11e8` (`debug for multi-nodes features`). This signed device build includes the four-radio ownership repair and the corrections found during [Android device acceptance](android-fleet-device-validation-2026-10-15.md). The user requested replacement installation on both attached iPhones, preserving existing data, followed by disconnect/slot-release, Channels, QR and Settings checks. Times use Asia/Taipei.

**Completed:** both iPhones received the signed repair with existing data preserved. One physical radio was used sequentially to verify a secondary endpoint on A01E and the compatibility-primary endpoint on 201E, including slot release, remembered reconnect, Channels, QR open/close and Settings. A01E's 31 old packet rows remain unchanged. Both iPhones were left released at 0 / 4; the radio was restored to the S24 connection it had before this test. Simultaneous multi-radio and optical QR apply/readback remain unverified.

## Devices and installed artifact

The two identical-model phones were resolved by exact UDID rather than display name.

| Device alias | Hardware | UDID | Before replacement |
| --- | --- | --- | --- |
| A01E | iPhone 15 | `00008120-001A11CE02D1A01E` | Existing configuration, two remembered radio profiles, 31 stored packet rows and valid prior Terms consent |
| 201E | iPhone 15 | `00008120-001639393CC0201E` | Installed app, but no app configuration/history/consent; only OS launch snapshots |

A fresh signed Debug iPhoneOS build of scheme `NTsocialMeshLink` succeeded. Both `devicectl device install app` operations returned **App installed**, and post-install inventories confirmed the original bundle identity and version. No uninstall, app-data clear or parent-app replacement was performed.

- Bundle ID: `com.ntsocial.meshlink.ios`.
- Version/build: **1.0.0 (2)**; no release-version increment.
- Product: `.agent_artifacts/ios-fleet-qa-20261015/DerivedData/Build/Products/Debug-iphoneos/NTsocial MeshLink.app`.
- Executable SHA-256: `31b832a491239de2a5b9e4643bea12ae1cf89ae2f5578b61e300ce8a78838be2`.
- Verified company team: `D524H699HW`.
- Signed application identifier: `D524H699HW.com.ntsocial.meshlink.ios`.
- Signed App Group: `group.com.ntsocial.gateway`.
- Signed shared Keychain group: `D524H699HW.com.ntsocial.meshlink.gateway`.
- Embedded development profile covers both exact target UDIDs, expires September 10, 2027, and has `get-task-allow=true`.

Strict/deep code-signature verification passed. This is a signed **development** artifact, not a new Store/TestFlight upload. Xcode retained the existing `Metadata extraction skipped, no AppIntents.framework dependency found` warning; the build is not described as warning-free. No product source change was needed merely to produce the device artifact.

## Replacement data preservation

The app's private container was copied before replacement and immediately afterward, before the new process changed runtime state. A01E's authoritative baseline was captured after stopping its existing process. Private snapshots and comparisons remain Git-ignored; message contents, PSKs and raw configuration are not included here.

| Device | Baseline files | Replacement comparison |
| --- | ---: | --- |
| A01E | 38 | All retained files byte-identical; only four OS-managed SplashBoard launch snapshots removed |
| 201E | 4 | All four files were OS-managed SplashBoard launch snapshots; no existing app configuration, history or consent to migrate |

A01E's 31 original packet rows and stored radio/settings/consent files are covered by this byte-level replacement comparison. The app-data container UUID changed on both phones during iOS replacement; that alone is not data loss and UUID preservation is **not** claimed. Both retain the same shared App Group container path. This comparison does not read or prove individual Keychain item contents; it verifies the signed access groups and retained installation identity.

A01E retains these catalog identities:

- Compatibility-primary **Meshtastic_5d6e**: `cefa6b6d-c330-4511-a9bb-72fa43e9a622`.
- Secondary **Meshtastic_1407**: `2787be6e-a423-40c2-a577-8d3c368d5426`.

The v1-to-v2 catalog migration intentionally shows **0 / 4 active connections**, while retaining both remembered devices. This is the new explicit connection-intent contract, not deletion of a radio/history. Connecting 1407 on A01E exercises a **secondary** endpoint; it must not be reported as connected compatibility-primary validation of 5d6e.

## First launch and consent

A01E's existing valid consent, recorded October 6 for the current Terms version, was retained. Its first repaired-runtime screen showed 0 / 4 active leases and the remembered devices. The horizontal remembered-device carousel exposed the second profile after a local swipe. Offline Channels did not invent a ready radio.

201E had no prior consent record. Device UI inspection confirmed the initial acknowledgement was **unchecked** and agreement was **disabled**. For the user-authorized functional test, the acknowledgement and agreement were then explicitly selected through the visible UI; the harness does not autoaccept consent. The subsequent shared language flow was explicitly set to Traditional Chinese. These are intentional first-use choices after preservation checks, not data migration.

This is bounded real-device evidence for the first-use Terms gate and visible controls. It does not establish complete UGC moderation, App Store policy acceptance or every language/policy-link flow.

## Connected-radio acceptance

Only one physical radio, **Meshtastic_1407**, is available. S24's existing connection was temporarily disconnected through its UI and observed at 0 / 4 to allow the radio to be used by iOS. A remembered 5d6e profile is not evidence that a second physical node is present.

| Scenario | Current result | Evidence boundary |
| --- | --- | --- |
| A01E: explicitly reconnect remembered secondary 1407 | **Passed:** 1 / 4, Connected, firmware `2.8.0.b10d31e`; 5d6e remains remembered/disconnected | Actual secondary endpoint connected through its retained profile |
| A01E: selected 1407 Channels and LoRa settings | **Passed:** selector names `Meshtastic_1407 · A453`; LoRa Taiwan / `MEDIUM_FAST`; Hub shows one radio/eight channels and its own managed Channels page | Correct secondary owner and read-only contents; no configuration write |
| A01E: QR camera open, close and reopen | **Passed:** explicit camera permission, camera-use indicator/reticle, Cancel returns to all eight channels; reopen/cancel also passed | Native scanner surface and callback cleanup only; no optical payload decoded |
| A01E: top fleet-card disconnect | **Passed:** 1 / 4 → 0 / 4 on the same Connections page; remembered profiles retained | Connected secondary 1407 released its actual lease |
| A01E: lower connected-device disconnect | **Passed:** after explicit reconnect, 1 / 4 → 0 / 4 on the same page | The preceding capture was still loading Stage 2 nodes (52); this proves release during reconnect/configuration, not a second fully READY disconnect |
| Cold relaunch while released | **Passed on A01E:** process `6374` remains 0 / 4 with both remembered devices disconnected | Persisted disabled intent survived process restart |
| Explicit reconnect after release | **Passed on A01E:** reconnect admitted one lease and progressed into Stage 2; the lower detail action then released it | Same retained endpoint identity; no simultaneous second radio |
| 201E: first-radio/compatibility-primary pairing and readiness | **Passed:** native PIN pairing, 1 / 4 and configured 1407, firmware `2.8.0.b10d31e`; later capture has no Stage 2 loading | Initial loading capture is superseded by `201e-primary-ready` for readiness |
| 201E: primary Settings, LoRa and Channels Hub | **Passed:** selector `Meshtastic_1407 · A044`, Taiwan / `MEDIUM_FAST`, one radio/eight matching channels | Read-only primary endpoint navigation; LoRa values visually confirmed in screenshot when transition-time AX omitted the body |
| 201E: primary managed Channels and QR | **Passed:** eight channels; explicit camera permission, OS camera-use indicator, reticle and Cancel; close returns to correct eight channels, reopen/cancel also passes | Camera scene itself is black in the retained screenshot; no discernible live image or optical decode is claimed |
| 201E: top compatibility-primary disconnect | **Passed:** 1 / 4 → 0 / 4 on the same Connections page; profile remains remembered | Actual configured first radio released its lease |
| 201E: cold restart after primary release | **Passed:** process changed `6465` → `6569`, still 0 / 4; remembered Connect remains available | Primary disconnect intent persists across restart |
| 201E: remembered reconnect and lower detail disconnect | **Passed:** explicitly reconnected to fully READY 1 / 4 with firmware details; lower detail Disconnect returned to 0 / 4 | Tests the configured primary detail action separately from its top fleet action |
| A01E final data comparison | **Passed:** all 31 old rows unchanged, zero new/missing rows; existing epochs, root configuration, consent and endpoint IDs retained | Final stopped snapshot; both v2 profiles disabled and no selected endpoint |
| Final device handoff | A01E PID `6379` at 0 / 4; 201E PID `6574` at 0 / 4; both retain remembered profiles. S24 restored to connected 1407 at 1 / 4 | One physical radio was shared sequentially; no simultaneous two-radio claim |

A01E's eight managed channel slots are YunlinGOV (0), Fire (1), Police (2), NTsocial (3), Edu (4), Social (5), WaterEnv (6) and PubWorks (7). Evidence includes `a01e-connect-state`, `a01e-secondary-settings`, `a01e-secondary-lora`, `a01e-secondary-hub` and `a01e-secondary-managed-channels`. The unavailable compatibility-primary 5d6e did not force the 1407 page back into its own settings. This proves use of a real secondary endpoint, while simultaneous A/B switching remains untested.

After explicit OS Bluetooth permission on 201E, its empty Settings page kept radio controls disabled, Channels showed zero radios, and Nodes exposed the Connections recovery action. These no-radio screens passed without inventing a selected endpoint.

201E discovered the physical 1407 radio and displayed the native Bluetooth PIN prompt. The first attempt timed out while that prompt remained unanswered during inspection; the app reported that Bluetooth pairing did not complete. Acknowledging the error and pressing the fleet disconnect control released the failed lease **1 / 4 → 0 / 4**, retaining the newly remembered compatibility-primary profile. This is an unanswered-prompt timeout/release recovery result, **not** a rejected-PIN finding. Three subsequent auxiliary UI attempts failed their alert-existence lookup **before any PIN entry**. A fresh SpringBoard accessibility capture then exposed the exact pairing alert and its secure field. This identifies the observed accessibility owner but does not prove the cause of the prior lookup failures. Those three attempts are automation/coordination limitations, not incorrect-PIN attempts or MeshLink crashes. A combined connect/continue/pair retry with a 30-second alert wait then **passed** (`201e-primary-pair-combined`): the exact saved fixed PIN was supplied to the matching OS prompt, the app admitted one compatibility-primary lease, and the physical 1407 connection proceeded into configuration. Its first post-pair capture was still loading Stage 2 nodes (98), so that capture alone is not treated as configured READY. The subsequent `201e-primary-ready` capture confirms the fully configured radio and firmware without the Stage 2 loading indicator. Its own Settings selector, LoRa screenshot and eight-channel Hub then passed read-only checks.

A first cold-restart automation attempt failed in the **XCTest infrastructure** with a socket EOF while installing/launching the auxiliary test runner; no MeshLink assertion ran in that attempt. Subsequent operations and `a01e-released-cold-restart-final` succeeded. The infrastructure failure is retained separately from product behavior rather than counted as a product pass or hidden.

On 201E, `primary-qr-camera` visually confirms the green OS camera-use indicator, scanner reticle and Cancel control. The camera scene itself is black in that image; this report does not infer a discernible live scene or optical decode. Closing and reopening/cancelling the scanner both returned correctly to the primary's eight-channel page; returning to Connections still showed the configured radio at 1 / 4 with firmware details.

No new test message was sent and no channel/LoRa configuration was intentionally written during this iOS acceptance session. Opening Settings does not prove configuration mutation; opening the QR camera does not prove optical decoding, successful apply or fresh radio readback.

## Preservation after the first radio release

A stopped snapshot after the first connected-secondary disconnect was compared with the stopped pre-upgrade baseline. The comparison opens temporary copies of each SQLite database plus its matching WAL, preserving the original evidence files, and compares complete existing row hashes and history epochs without printing payloads.

| Existing database | Packet rows before → after release | Existing rows | Epoch result |
| --- | --- | --- | --- |
| `meshtastic_database_08dfa8970e.db` | **31 → 31** | All original UUIDs and full rows unchanged; no added rows | Existing epoch unchanged |
| `meshtastic_database_40791b3f7e.db` | 0 → 0 | No deleted or added rows | Epoch absent before and after |
| `meshtastic_database_default.db` | 0 → 0 | No deleted or added rows | Existing epoch unchanged |

Both profile UUIDs, transport-address hashes and compatibility-primary binding remain unchanged. The legacy v1 catalog remains retained; the authoritative v2 catalog records both profiles `enabled=false`, `autoConnect=false`, with no selected endpoint. Legacy v1 flags are retained migration evidence, not active connection intent.

Root `channel_set.pb`, `local_config.pb` and `module_config.pb` are byte-identical, and the existing Terms acceptance record is unchanged. The secondary 1407 runtime created its own endpoint-scoped channel/configuration files when it read the real radio; these files were absent in the baseline and are not falsely classified as byte-preserved old files. Evidence: `logs/a01e-preservation-after-release.json`, generated by the reusable local `compare.py` helper. The final stopped snapshot `private/a01e-final/` was then independently compared in `logs/a01e-final-preservation.json`: all 31 original rows still match exactly, there are zero new/missing rows, existing epochs/root configuration/Terms consent/endpoint IDs remain unchanged, and both authoritative v2 profiles remain disabled with automatic connection off. A01E was relaunched after the snapshot with no radio selected, leaving the physical node available for the 201E test and restoration to S24. `a01e-final-handoff.png` confirms its final 0 / 4 state and both remembered devices; process `6379` was live. Only the disposable Fleet QA host/runner were removed from A01E afterward; the MeshLink product was not uninstalled.

## Final 201E state and handoff

`201e-primary-reconnect` confirms the remembered primary returned to fully READY **1 / 4** with firmware `2.8.0.b10d31e`. `201e-primary-detail-release` then confirms the lower detail control released it to **0 / 4**. This is separate from the successful top fleet control and cold-restart test.

The stopped `private/201e-final/` snapshot and `logs/201e-final-preservation.json` show its newly created compatibility-primary profile `bd0d00df-246c-4658-900d-3a7c37eefd08` retained with `enabled=false`, `autoConnect=false` and no selected endpoint. Both new databases contain zero packet rows. Its new consent, channel and radio configuration files are expected results of the explicitly completed first-use flow and readback; there was no older app data to overwrite. The phone was relaunched as process `6574`, and `201e-final-handoff.png` visually confirms 0 / 4 with remembered 1407.

Only the temporary Fleet QA host and runner were removed from **both** iPhones. MeshLink remains installed with its retained/newly initialized data; no product uninstall occurred. Neither phone has an in-flight pairing dialog or an enabled radio lease at handoff.

The S24 radio connection was restored using its remembered Connect action. Final UI evidence (`private/s24-restored-ui.txt` and the Android evidence capture `private/iphone-test-restored-s24.png`) shows **1 / 4, connected Meshtastic_1407, firmware 2.8.0**. This is final UI readiness evidence; no new exact Stage 2 timestamp or RF delivery result is inferred. The temporary cross-phone BLE handoff is complete.

## Validation method

UI operations use a disposable signed XCUITest host and runner with separate identities `com.ntsocial.meshlink.fleetqa` and `com.ntsocial.meshlink.fleetqa.uitests.xctrunner`. They do not replace the parent's wirelessprobe helper. Their development profiles were checked for company team and both target UDIDs. The harness only operates the installed MeshLink app or an observed SpringBoard permission/pairing alert; it contains no parent-app or message-send controls and never autoaccepts Terms. For 201E's observed 1407 pairing prompt, a constrained helper reads the fixed Bluetooth PIN from that same radio's previously captured configuration, verifies fixed-PIN mode, and supplies it only to the matching system alert. No PIN is printed; temporary runner configuration and console logs are scrubbed, and raw xcresult diagnostics remain private.

Successful UI operations retain individual xcresults, accessibility trees and screenshots; failed auxiliary attempts retain their available diagnostics as described above. The driver consumes observed labels or XCTest-point coordinates, with local drag gestures for horizontal cards. Application snapshots, runtime console evidence and retained SQLite/configuration comparisons supplement UI observations. Raw private evidence is not checked into Git.

The preceding exact-source [architecture repair gate](multi-radio-ownership-repair-2026-10-15.md) passed formatting, Detekt, Debug builds, JVM/Android-host tests, KMP/native compilation and simulator-framework linkage. This session adds the signed Xcode device build and actual iPhone installation/UI checks. Kotlin/Native test executable link/run remain disabled by repository convention; compilation is not native-test execution. Windows receives no new hardware validation from this run.

## Remaining hardware acceptance matrix

| Remaining case | Required equipment/context | What this run cannot establish |
| --- | --- | --- |
| Switch between two simultaneously READY radios with different channels/LoRa values | **Second physical radio** on the same iPhone | Correct A/B data refresh, same local Settings page type, discarded unsaved drafts and endpoint-specific Channels/QR targeting |
| Disconnect compatibility-primary A while B remains READY | **Second physical radio** | B's uninterrupted transport/generation/history, no primary promotion and unchanged primary-only Apple Gateway authority |
| Late A scanner result or admitted channel operation while switching/releasing into B | **Second physical radio** plus reproducible QR/control fixture | No stale A result/configuration write is applied to B; B remains unaffected |
| Optical QR decode, target confirmation, safe apply and fresh readback | **One radio is sufficient**, plus a suitable QR fixture and intentionally authorized configuration test | Optical decoding and mutation/readback are separate from scanner open/close; absence of a second radio is not their blocker |
| Optical QR targeting on the second endpoint while A remains connected | **Second physical radio** and QR fixture | Full second-endpoint optical/control path and concurrent isolation |
| Four active leases, independent teardown and connection stability | **Four physical radios** | Four simultaneous real BLE links; two phones or remembered profiles are not substitutes |
| Release one of four and admit a different fifth node | **Five physical radios** | Real capacity reuse at the maximum, including compatibility-primary release |
| RF reception/delivery from a test message | Another compatible receiving radio and an explicitly executed message scenario | Firmware/local acceptance alone is not remote receipt |
| Apple Gateway route renewal/replay/parent import | Separately scoped parent integration test; additional radio for concurrent isolation | The presence of signed App Group/Keychain grants is not fresh Gateway interoperability proof |
| Long background/OS termination/restoration, Windows hardware, Store delivery | Dedicated platform/lifecycle/release tests | This bounded foreground iPhone test does not confer those results |

## Evidence index

Private evidence root: `.agent_artifacts/ios-fleet-qa-20261015/`.

- `logs/ios-artifact.json`: sanitized executable/signing/profile metadata.
- `logs/xcode-device-build.log`: signed product build, including the retained AppIntents warning.
- `logs/a01e-install.*`, `logs/201e-install.*`: installation results.
- `logs/replace-preservation.json`: before/after replacement file comparison.
- `compare.py`, `logs/a01e-preservation-after-release.json`, `logs/a01e-final-preservation.json`: full-row/history/catalog/configuration/consent comparisons after the first release and at final A01E handoff.
- `private/a01e-baseline-stopped/`, `private/a01e-after-install/`, `private/201e-before/`, `private/201e-after-install/`: private data checkpoints.
- `private/*-app-before.json`, `private/*-app-after.json`: exact bundle/version/container inventory.
- `private/*-initial-*`, `private/201e-consent-controls-*`, `private/201e-first-use-consent-*`, `private/201e-first-use-language-*`: initial UI and explicit first-use evidence.
- `harness/`, `ui.py`, `logs/harness-signature-summary.json`: reproducible auxiliary UI driver and its separate signature verification.
- Timestamped `a01e-secondary-*`, `a01e-released-cold-restart-final-*`, `201e-primary-*` UI operations: connected ownership, Channels/LoRa/QR, both disconnect controls and restart observations.
- `logs/201e-final-preservation.json`, `private/201e-final/`: final first-use primary profile/configuration/empty-history state.
- `private/a01e-final-handoff.png`, `private/201e-final-handoff.png`, final-launch records and auxiliary-removal logs: final iPhone handoff.
- `private/s24-restored-ui.txt` and `.agent_artifacts/android-fleet-qa-20261015/private/iphone-test-restored-s24.png`: restoration of the original S24 connection.

The confirmed results are bounded to one physical radio used sequentially across the phones. The hardware acceptance matrix above remains outstanding; no further product code changes, additional RF message, Store submission or Windows device test occurred in this session.
