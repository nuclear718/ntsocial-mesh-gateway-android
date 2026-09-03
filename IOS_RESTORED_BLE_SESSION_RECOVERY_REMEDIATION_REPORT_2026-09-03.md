# iOS Restored BLE Session Recovery Remediation Report

Date: 2026-09-03  
Affected track: iOS NTsocial MeshLink, with shared BLE scanner/transport contracts compiled and tested on Android and Desktop/JVM

## Reported regression

After a Debug update, the iPhone that had previously connected to a Meshtastic node could no longer reconnect. The
Connections scan also did not list the node. The radio had not failed: iOS still owned a restored GATT link from the
earlier MeshLink process, so the single-owner Meshtastic PhoneAPI stopped connectable advertising while the new
application-level connection graph incorrectly remained disconnected.

## Root cause

Two independent defects combined into the observed failure:

1. Kable 0.42 configures CoreBluetooth restoration, but its `willRestoreState` path does not rebuild the Kable
   wrapper's logical connected state. CoreBluetooth could therefore continue delivering indications for the old
   restored session while the replacement wrapper waited for a `didConnect` callback that iOS would not replay.
2. The fallback scan supplied both the Meshtastic service UUID and the saved peripheral identifier. Kable's Apple
   scanner rejects an address filter, and the resulting exception was swallowed by the retry loop. Even a valid
   service-only scan could not rediscover this node while its existing single-owner link kept advertising stopped.

The separate NTsocial parent did not steal the radio connection. Its CoreBluetooth service and the Meshtastic PhoneAPI
service are distinct; device logs attributed the retained radio session to the MeshLink bundle.

## Remediation

- A saved CoreBluetooth identifier is now reconstructed directly instead of waiting for an advertisement. It is not
  treated as bond proof: MeshLink must still connect and complete the protected Meshtastic FROMNUM subscription.
- The verified peripheral and its live connection scope are transferred intact to `BleRadioTransport`, avoiding a
  second wrapper and second connection race.
- Prepared connections carry exact device-instance ownership. Both the transport handoff and iOS pairing UI now use
  non-cancellable discard paths, and an unclaimed lease expires after 30 seconds. A cancelled or superseded attempt
  therefore cannot leave a hidden GATT owner in the prepared-session registry or close a newer replacement. After a
  same-address pairing race, saved-device recovery re-reads the repository state and returns the exact instance that
  owns the staged peripheral instead of constructing a competing wrapper.
- Transport close waits a bounded five seconds for an in-flight prepared-peripheral handoff before disconnecting. If
  that handoff is unusually slow, a detached cleanup waits for its eventual completion and disconnects again; repeated
  close calls retain the same pending owner and cannot cancel this final cleanup.
- An OS-restored wrapper receives a bounded five-second verification probe. If it cannot publish a usable connection,
  MeshLink non-cancellably disconnects and closes that stale wrapper, waits one second for CoreBluetooth/radio settle,
  reconstructs a fresh wrapper, and performs the normal bounded connection and protected-subscription verification.
- Apple scans omit Kable's unsupported address predicate and retain the native Meshtastic service filter. The transport
  still performs the exact saved-identifier comparison. Android and Desktop retain their previous service/address OR
  scan behavior.
- Connection-screen coroutines now rethrow `CancellationException` through `safeCatching`; ordinary navigation or
  lifecycle cancellation is no longer converted into the user-facing `Job was cancelled` error dialog.
- The Xcode target declares the shared App Group and Keychain capabilities used by the existing entitlement source.

## Validation

- BLE JVM tests pass 47/47, including the new Apple filter-policy cases.
- Android BLE host tests pass 40/40, and the Android Google Debug application compiles.
- Core network tests pass, including a regression proving that a known saved device bypasses advertisement scanning.
- A second core-network regression cancels the known-device handoff and verifies that the unclaimed prepared connection
  is discarded; the iOS registry's reference-identity check makes stale cleanup fail closed against replacements.
- Two transport-close regressions verify both the normal cancellation-time install ordering and a six-second handoff
  that exceeds the five-second close bound, including a repeated close and the required deferred second disconnect.
- The common exception tests prove that ordinary failures remain values while coroutine cancellation is rethrown.
- iOS Simulator and arm64 device compilation passed for BLE, network, and Connections; the signed physical-device Xcode
  Debug build passed strict code-sign verification. Its final debug dylib is 159,911,280 bytes with SHA-256
  `E05D320E69016C5B713BDA86AD420A7DC43479C240A39E6A23F09E9D0D1770F9`.
- The final post-close-barrier data-preserving Debug build was installed on both connected iPhone 15 devices. Existing
  application data and settings were retained; the second phone, which had no saved radio, remained correctly
  unconfigured.
- On the affected phone, the corrected canary released the stale restored link, reconnected to the exact saved node,
  completed write-with-response and sustained FROMNUM/FromRadio reads, then repeated saved-identifier recovery after a
  cold process restart. Later GATT indications woke the suspended app and packet reads continued. After installation of
  the final post-close-barrier artifact, its new MeshLink process again took the live GATT data path and sustained
  characteristic reads. A second terminate-existing launch was accepted immediately before the user ended the run; no
  further soak was performed. No MeshLink crash, fatal, abort, or uncaught exception was observed.
- The entitled NTsocial parent subsequently retained two *current* automatic projections from one coherent fresh radio
  generation: primary `MediumFast` and secondary `NTsocial`, both send-capable. Parent source clears these projections
  unless `readStatus()` is READY and the complete channel catalog shares the same nonempty generation, so this is
  physical two-App Apple Gateway READY evidence rather than stale UI/cache state.
- The final JDK 21/en-US one-worker full gate ran 1,946 actionable tasks (235 executed, 1 from cache, and 1,710
  up-to-date). Formatting, both Android Debug assemblies,
  tests/`allTests`, Desktop/JVM, shared KMP/iOS Simulator compilation, and both Android Debug lints passed; the command
  exited nonzero only for the six documented pre-existing Detekt findings in unrelated unchanged sources. An earlier
  loaded run exposed a transient Compose Desktop dialog-semantics test-host failure; its complete isolated JVM suite
  passed 49/49 and the same seven dialog cases passed under both English and Traditional Chinese locales, so no UI or
  production regression was reproduced.

## Evidence boundary

This run proves recovery of a saved, non-advertising Meshtastic node, sustained physical BLE traffic, app cold-restart
recovery, and a current READY Apple Gateway channel catalog. It does not prove RF airtime, remote-radio receipt,
two/four-radio concurrent iPhone operation, Release/TestFlight/App Store delivery, or a completed manual binding to an
ordinary joined NTsocial logical channel. iPhone Mirroring was unavailable while the phone was in active use, so the
post-fix Channels-screen visual path was not re-run; the cancellation behavior is covered by source and common tests.
