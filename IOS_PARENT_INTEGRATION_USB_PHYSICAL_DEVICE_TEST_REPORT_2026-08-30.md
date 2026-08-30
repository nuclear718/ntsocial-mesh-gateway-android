# iOS Parent Integration USB Physical-Device Test Report — 2026-08-30

## Scope and conclusion

This run affected only the iOS product track. It exercised the current `NTsocial MeshLink` companion against the
already-installed `NTsocial` parent on one USB-C-connected iPhone 15 running iOS 26.6.1.

The companion can be compiled as a device artifact, installed, launched, relaunched, and addressed through its
`ntsocial-meshlink://process` handoff. Its private Room database initializes at schema 43, remains valid across a
process restart, and no MeshLink crash report was present after the run. The installed parent and companion also remain
alive when foreground ownership moves between them.

Real Apple Gateway communication is **not enabled by the currently available signing setup**. The local provisioning
profiles for both installed Apps omit the required App Group entitlement, and the parent Debug configuration uses its
empty personal-team entitlement file. A normal MeshLink build with the source entitlements fails signing because the
profile does not authorize `group.com.ntsocial.meshlink.gateway`. The diagnostic build installed for this run therefore
omitted entitlements by command-line override only; it cannot and must not be treated as Apple Gateway integration,
release-signing, TestFlight, or App Store evidence.

No product code was changed. The runtime already handles this condition correctly by clearing Apple Gateway
configuration and failing closed instead of creating an insecure private-container fallback.

## Device and source baseline

- Source branch: `multi_nodes_`
- Source HEAD: `6a8a665d4781ea60cd91697cc42a0a422210d78a`
- Device: iPhone 15 (`iPhone15,4`), iOS 26.6.1 (`23G83`), wired, paired, Developer Mode enabled
- Installed parent: `NTsocial` `com.ntsocial.ios`, version `1.0.0 (1)`
- Installed companion: `NTsocial MeshLink` `com.ntsocial.meshlink.ios`, version `1.0.0 (1)`
- Diagnostic companion bundle size: 167,399,424 bytes
- Diagnostic companion executable SHA-256:
  `92E0816D4445615B2569EF9DF4085427343115DC51823816821394BD838E6FDC`

The repository and sibling parent worktree were clean before testing. The sibling parent was inspected and tested
read-only.

## Automated source baseline

The focused pre-device baseline passed 126 executed tests with no failures:

- `:core:gateway:jvmTest`: 36/36
- `:ios:runtime:jvmTest`: 19/19
- `:core:radio-fleet:jvmTest`: 8/8
- `:core:prefs:jvmTest`: 36/36
- `NTsocial_release` `AppleGatewayAdapterTests`: 27/27

`iosArm64` production compilation and iOS Simulator test-source compilation also passed. These source tests cover the
HMAC contract, mailbox/cursor behavior, route and session guards, durable queue/retry behavior, handoff state, and fleet
persistence, but do not substitute for two correctly entitled Apps on one physical device.

## Signing root cause

The source companion requests:

- App Group `group.com.ntsocial.meshlink.gateway`
- Keychain access-group suffix `com.ntsocial.meshlink.gateway`

The parent full entitlement file requests the same coordinates. However:

1. The only available automatic/local MeshLink provisioning profile contains neither the requested App Group nor the
   shared Keychain group.
2. A normal device build fails with: `Provisioning profile ... doesn't match the entitlements file's value for
   com.apple.security.application-groups`.
3. The currently installed parent is a personal-team Developer App, and the parent Debug build configuration selects
   `NTSocialAppPersonal.entitlements`, which is an empty dictionary.
4. A CoreDevice lookup of `group.com.ntsocial.meshlink.gateway` fails with `ContainerLookupErrorDomain error 7`.
5. The diagnostic companion's effective signed entitlements contain its application identifier and development flags,
   but no App Group. Removing the source entitlement requirement was not committed; it was a one-build diagnostic
   override used only to prove the remaining device boot path.

This is a provisioning/capability blocker, not a KMP, SQLite-mailbox, Darwin-notification, or Swift/Kotlin contract
failure. The source bootstrap guards the exact container and shared Keychain key; if either is unavailable, it clears
Gateway configuration and returns.

## Physical-device results

| Check | Result | Evidence boundary |
|---|---|---|
| Diagnostic device build and signature verification | Pass | Device architecture and local diagnostic signature only |
| Install and version readback | Pass | `com.ntsocial.meshlink.ios` reports `1.0.0 (1)` |
| First launch | Pass | The expected localized Bluetooth permission prompt rendered |
| Graceful terminate and cold relaunch | Pass | MeshLink PID changed from 1776 to 1795; parent PID 1444 remained alive |
| Parent foreground, companion return | Pass | Both processes remained alive across both launch orders |
| `ntsocial-meshlink://process` routing | Pass | CoreDevice routed the URL and the existing MeshLink PID remained 1795 |
| Private Room persistence | Pass | Post-restart `integrity_check=ok`, schema 43, `history_epoch_v2` retained |
| MeshLink crash-log check | Pass | Zero matching crash logs after the run |
| App Group container access | Blocked as expected | Missing signed entitlement; container lookup fails |
| Shared Keychain/HMAC and mailbox round trip | Not runnable | Both Apps need matching signed capabilities first |
| Bluetooth permission acceptance and navigation | Not completed | User was away; physical-device UI Automation timed out twice before any test method ran |
| BLE scan, radio connection, Stage 2, configuration | Not tested | Bluetooth was reported off and no user/radio interaction was available |
| Local radio admission, RF airtime, remote receipt | Not tested | Requires enabled Bluetooth and Meshtastic radio hardware; remote receipt requires a second radio |

The USB screenshot confirms that the App identity and localized Bluetooth usage description are packaged correctly.
The system permission decision itself remains pending, so the screenshot is not Bluetooth authorization or scanning
evidence.

## UI automation and cleanup

A disposable, Git-ignored XCUITest harness was created to launch the already-installed companion and accept the
Traditional Chinese or English Bluetooth prompt without user assistance. The first attempt was blocked by the free
profile's three-App limit. The obsolete installed parent UI-test Runner was removed because it is rebuildable. The new
Runner then installed, but two runs timed out while iOS was enabling UI Automation, before the test method or any App
assertion ran. This is recorded as unavailable automation, not an App test failure. The disposable Runner was removed
afterward; the parent and MeshLink Apps remain installed and running.

## Required follow-up

Before claiming parent/companion interoperability, use one eligible Apple development team to provision both exact
bundle identifiers with the same App Group and compatible shared Keychain access group. Build the parent with its full
entitlement file and build MeshLink without the diagnostic override. Verify the effective entitlements in both signed
artifacts before installation.

Then repeat, on the same unlocked device:

1. App Group container and shared HMAC-key access from both Apps.
2. Parent command write → Darwin hint/deep link → MeshLink claim/admission/result → parent cursor commit.
3. Companion-not-running missed-hint recovery and both process-restart orders.
4. Background/foreground handoff with protected data available.
5. Bluetooth permission, Meshtastic scan, primary-radio selection, Stage 2 readiness, and connected-radio admission.
6. A second-radio RF receive check before making any delivery or interoperability claim.

