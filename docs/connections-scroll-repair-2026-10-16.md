# Connections scrolling repair — 2026-10-16

Source: working-tree changes on `5fc893d8e269dd96fcc47e7a674e143693fe273a`, branch `multi_nodes_`, including the preceding [QR import repair](endpoint-qr-import-repair-2026-10-16.md). The user completed manual channel imports on two radios, then urgently reported that S24's Connections page could not scroll down to additional Bluetooth devices. S24 was prioritized and returned to the user's work before updating the other phones.

## Reproduction and cause

The existing screen placed the fleet panel outside `AdaptiveTwoPane` in a fixed-height outer Column. On compact screens, the connected-device card occupied the first part of the remaining Column and a weighted Bluetooth LazyColumn received only the remaining height. With connected and remembered fleet cards, this left almost no usable viewport for Bluetooth discovery. A swipe over the cards could not move them because they were not in a vertical scroll container.

Actual S24 reproduction with connected 1EEE and remembered 1407:

- Connected-nodes title stayed at y=333 pixels before and after a 550 ms upward swipe.
- Remembered-devices title stayed at y=846.
- Bluetooth scan action stayed at y=1938, adjacent to the bottom navigation; further devices were inaccessible.

## Repair

`ConnectionsScreenLayout` now puts fleet cards, current-radio status, region guidance, Bluetooth scanning and device rows inside **one bounded LazyColumn on compact screens**. `BluetoothDeviceList` accepts leading lazy items; no unbounded vertical scrolling container is nested inside another.

At widths of 600 dp or more, fleet/status and Bluetooth discovery remain in two independently bounded scroll panes. This preserves the Desktop/wide-screen layout and allows an oversized fleet section to scroll there too. Radio discovery, selection, disconnect, configuration and transport/session behavior were not changed.

Android and iOS both render the shared `ConnectionsScreen`; their platform fleet cards are supplied to this same layout. The repair is therefore included in both device artifacts.

## Tests and builds

- All 46 `feature:connections:jvmTest` tests passed, including two new Compose regressions.
- Compact regression uses a 360 × 480 dp viewport with 560 dp of fleet content plus 240 dp of status content, then performs an upward swipe, reaches/selects the last of 12 Bluetooth devices, and scrolls back to the fleet header.
- Wide regression verifies that a Bluetooth device remains reachable beside tall fleet content without scrolling that left pane away.
- Changed-module Spotless/Detekt and Android Google Debug assembly passed before the S24 installation.
- iOS runtime JVM tests, iOS device framework link, Connections simulator source/test compilation and Desktop tests passed before the remaining platform handoff.
- Signed Xcode Debug device build passed. Kotlin/Native executable test execution remains disabled by repository convention; compilation is not reported as execution.
- Full repository baseline/KMP/lint replay was started after the urgent S24 handoff. Its first pass found that the new Compose UI regressions had also been assigned to the plain Android host-test runner, where Android Build.FINGERPRINT is unavailable. The UI regressions were moved to the JVM UI test source set and their Compose test dependencies scoped there; production code and installed artifacts are unchanged. The corrected full replay **passed: 2,019 tasks**, covering root `spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile`, both Android Debug flavor lints, and iOS runtime simulator source/test compilation plus Debug framework linking. The production source installed on the phones did not change during this test-configuration correction.

## Five-phone delivery and data preservation

| Phone | Delivery / evidence |
| --- | --- |
| S24 Ultra | Installed first at 09:17 Asia/Taipei. Before replacement, app data/APK were backed up. Live post-install endpoint catalog equals the baseline; existing history rows available in the post-install sample are unchanged. The full post-install tar capture was incomplete during resumed runtime activity, so a whole-container byte-equivalence claim is not made. Files missing from that extracted capture were verified to still exist on the phone; no backup restoration, uninstall or data clear was performed. |
| S22 Ultra | Installed at 09:19; before/after private data archives byte-identical. |
| OPPO | Installed at 09:19; before/after private data archives byte-identical. |
| iPhone A01E | Signed replacement succeeded; 36 existing app-data files byte-identical, excluding OS SplashBoard; 31 old packet rows retained. |
| iPhone 201E | Signed replacement succeeded; 27 existing app-data files byte-identical, excluding OS SplashBoard; prior consent/settings retained. |

All five apps were launched. S24 was left on its scrollable Bluetooth list and was not operated again after the user was notified it was ready. No radio was selected, disconnected or reconfigured during scroll acceptance; no RF test message was sent.

Artifacts:

- Android `com.ntsocial.meshlink.google.debug`, **1.0.9 (10)**, arm64 SHA-256 `db8e2fc918c10339f9beefd0354214c737a08b02d94b766594a24275ea156c5a`.
- iOS `com.ntsocial.meshlink.ios`, **1.0.0 (2)**, executable SHA-256 `1a60d7e2eec5cfb2c8b96d772499f54b58483ad709eed37bf4d11269d1ffcf1b`.
- Same Android Debug identity and company iOS signing team as the preceding installation; strict/deep iOS signature verification passed. These are local development builds, not Store uploads.

## Physical scrolling acceptance

S24: after replacement, a real upward swipe moved the Bluetooth scan action from y=1927 to y=463 pixels and exposed the following rows: 0809, 1407, 1EEE, 5D6E and B8A3. This directly verifies the reported blocked-list path on the user's phone. No Bluetooth row was activated.

Both iPhones were checked through an XCTest harness against the installed app:

| iPhone | Before / after upward swipe | Result |
| --- | --- | --- |
| A01E, English UI | Connected-nodes header y=123 → -62 points; Bluetooth section y=553 → 372 | Fleet content scrolls out of the viewport; scan action and discovered 1407/24AF rows remain visible and accessible |
| 201E, Traditional Chinese UI | Connected-nodes header y=123 → -34 points; Bluetooth section y=533 → 376 | Same scroll behavior; previously clipped second discovered-device row becomes fully visible |

Screenshots were visually inspected. Top app bars and bottom navigation stay in place while the body scrolls. Both iPhones were disconnected with remembered devices and a real discovery list; this is not an iOS four-concurrent-radio acceptance run. The oversized-content regression covers the layout constraint that caused the connected-card failure without borrowing the user's working S24 radio.

Private logs, backups and comparisons: `.agent_artifacts/connections-scroll-20261016/`. Device UI attachments are under the existing Git-ignored Android/iOS QA harness directories. No message content, PSKs, pairing credentials or raw channel payloads are included here.
