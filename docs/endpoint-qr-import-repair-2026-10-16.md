# Endpoint QR import repair and five-device installation — 2026-10-16

Source: working-tree changes on `5fc893d8e269dd96fcc47e7a674e143693fe273a`, branch `multi_nodes_`. The user reported that scanning a shared channel QR from C1F7's conversation/Channels page returned without a confirmation dialog, then confirmed the same symptom after a clean installation with one radio.

## Cause and repair

`ContactsEntryContent` created a separate app-level `UIViewModel` for its local Import FAB, then passed its pending channel/contact imports to the screen only when the current Koin scope was root. Both the compatibility-primary UI and secondary endpoint UIs now have non-root scopes. The local pending result therefore had no visible confirmation consumer, including with only one radio. The app shell's external-import dialog observes a different ViewModel instance.

The failure was reproduced before editing on S24/C1F7 using a valid, public synthetic `QRcheck` add-channel URL through the same Import FAB result handler used by QR scans: it returned directly to the existing LongFast list. No synthetic channel was applied. This establishes a result-routing failure; this URL reproduction alone is not optical camera-decoding evidence.

The fix:

- Local conversation-page imports now use screen-owned `EndpointImportState`, backed by the current endpoint's radio session. No app ViewModel or root-scope filter participates in the local preview.
- The existing scoped channel confirmation and `ScannedQrCodeViewModel` retain responsibility for target labeling, exact-session admission and fresh readback. The app does not write merely because a QR was decoded.
- Each of the four endpoint scopes has independent pending data. Disposing the owner clears the preview; a delayed result cannot enter a retired or reconnected session. Scanner/URL/NFC UI resets with that owner.
- Android captures the callback when a scan starts, including camera-permission delay. Replacement/disposal revokes the request, and a retired camera request cannot close or deliver into a newer one.
- External OS imports retain their explicit fleet endpoint chooser. iOS retains its Channels-only native scanner and primary-only Apple Gateway; Desktop retains its single-radio behavior. The shared Contacts URL/import path benefits on all three tracks.

## Basic validation requested by the user

The user explicitly requested a quick basic-test/build/install cycle on all five phones and to perform hardware QR testing personally. Further automated radio acceptance testing and the broad repository baseline were therefore deferred. No four-radio hardware claim is made.

Passed:

- Four concurrent import states retain independent channel sets; dismissing one leaves the other three intact.
- Compose regression exercises the actual Import FAB callback and confirmation UI under four separate **non-root** Koin scopes, including the first scope. Each confirmation contains that owner's current primary channel and its own imported channel, and no confirmation callback runs before Accept.
- A scan result retained across an endpoint switch cannot populate the new endpoint's dialog.
- Reconnect, unconfigured session, malformed payload and disposed-screen cases.
- Android scan-request callback ownership and cancellation tests, existing single-result gate, and dense QR decoding tests.
- `core:ui:jvmTest`: 56 tests; `core:barcode:testGoogleDebugUnitTest`: 6 tests; `feature:messaging:jvmTest`: 25 tests. All **87 passed**. The F-Droid scanner tests also passed in the focused run.
- Changed-module Spotless checks and Detekt; `:app:assembleGoogleDebug`; `:ios:runtime:linkDebugFrameworkIosArm64`; signed Xcode Debug device build of `NTsocialMeshLink`.

The full root baseline, flavor lint/KMP smoke and iOS simulator compilation/link were deferred for this initial expedited handoff. The subsequent [Connections scroll repair](connections-scroll-repair-2026-10-16.md) replay passed the full baseline on the combined QR/scroll source (2,019 tasks). Kotlin/Native executable test execution and Windows device testing remain unverified. Existing toolchain/deprecation/AppIntents metadata warnings are retained in build logs.

## Installed artifacts

Android:

- Package `com.ntsocial.meshlink.google.debug`, version `1.0.9 (10)`.
- arm64 APK SHA-256: `a2b35ea04abdb9d1b194e742a055239ef70a7cc88ad816b15c722f0e67dc0be0`.
- Existing Android Debug certificate verified against the installed packages; installed with `adb install -r`.

Apple:

- Bundle `com.ntsocial.meshlink.ios`, version `1.0.0 (2)`.
- Executable SHA-256: `9b75f248e7ecb1cdfcd45c1f3849861183ce538c41277dc5531d6e563818c004`.
- Company team `D524H699HW`; strict/deep signature verification passed. Signed App Group and shared Keychain access group were checked; development profile includes both exact target devices.
- Both `devicectl device install app` operations returned `App installed`.

These are local development builds, with no Store upload or release-version increment.

## Data preservation and installation

| Phone | Result | Preserved-data evidence |
| --- | --- | --- |
| S24 Ultra, `R5CX42P0SDH` | Installed and launched | Existing two-profile catalog unchanged; all 12 old packet rows unchanged; all prior history epochs retained |
| S22 Ultra, `R5CT30QMRTY` | Installed and launched | Catalog unchanged; no existing packet rows to migrate |
| OPPO, `TWBYJJRWSGHIGU55` | Installed and launched | Catalog unchanged; no existing packet rows to migrate |
| iPhone A01E | Installed and launched | 36 app-data files byte-identical immediately after replacement, excluding OS SplashBoard files; all 31 old packet rows retained |
| iPhone 201E | Installed and launched | 27 app-data files byte-identical immediately after replacement, excluding OS SplashBoard files; consent/settings retained; no old packet rows |

Private app containers were backed up before replacement. No uninstall, clear-data, radio configuration mutation or test RF message was performed. S24 runtime files/config caches changed during replacement/reconnection, so its entire container is not described as byte-identical; semantic history/catalog comparisons passed. User preferences and original data remain in the private backups as well. iOS container UUID changes are not treated as loss; Keychain item contents were not exported.

## Manual scan handoff

All five apps were reopened by 09:04 Asia/Taipei. S24 log capture was started before its new process launched. The user was invited to scan the original QR manually on C1F7 and verify the target label before applying. The user subsequently confirmed that optical scanning produced a confirmation dialog, that Accept was selected and the channel appeared, and that channel setup on two radios was complete with basic functions operating normally. This is user-performed physical-device acceptance; the exact radio pair and firmware readback correlation were not independently recorded. Four simultaneous physical-radio independence remains unverified. The next user-reported issue is Connections-page scrolling on S24, investigated separately.

Private evidence and build/install logs: `.agent_artifacts/android-c1f7-qr-20261016/` (Git-ignored). Raw QR payloads, PSKs, message contents and live device logs are not included in this report.
