# iOS Simultaneous Multi-Node Production-Graph Remediation Report

## Outcome

The iOS NTsocial MeshLink multi-node runtime contained the same architecture defect corrected for Android in Git
commit `5b856b5fa468327f7fc9a24eebe5d36dfc86b2da`. The UI, fleet catalog, databases, and endpoint scopes existed, but a
secondary endpoint could not reliably resolve its complete production connection graph. This could leave iOS with a
visible multi-node fleet while preventing a secondary Meshtastic node from completing connection and control setup.

The defect is corrected in the iOS track. No Android or Desktop production source was changed.

## Git and source evidence

- Android remediation `5b856b5fa` replaced constructor-reference Koin definitions where the constructor requires a
  qualified lifecycle/scope or a Kotlin `Lazy<T>` dependency, made secondary Gateway access fail closed, and moved the
  graph-wired marker after successful graph resolution.
- iOS multi-node source retained the pre-remediation pattern in
  `IosRadioEndpointKoinModule.kt`: `NodeRepositoryImpl`, `SharedRadioInterfaceService`, packet/config/router handlers,
  and channel reliability were still registered with `scopedOf(::...)`.
- iOS also instantiated `NtsocialGatewayRepositoryImpl` inside each secondary scope despite the documented product
  boundary that only the legacy-primary endpoint may own Apple Gateway.
- `IosSecondaryRadioEndpointSession.wireGraphOnce()` set `wired = true` before resolving `MeshConnectionManager`, so a
  first resolution failure could suppress a valid retry.

## Correction

- Replaced the affected iOS constructor-reference registrations with explicit scoped factories. Qualified
  `ProcessLifecycle` and endpoint-local `ServiceScope` values are selected explicitly, and every cyclic dependency is
  represented with `lazy { get<T>() }` at the intended boundary.
- Added `IosSecondaryGatewayRepository`. It rejects activation, ingress cache, parent-App sends, and durable Gateway
  admission while still accepting the internal readiness-status update required by shared control code.
- Moved `wired = true` after successful `MeshConnectionManager` resolution and radio-buffer reset.
- Added a Kotlin/Native regression that creates a real secondary endpoint scope, resolves
  `MeshConnectionManagerImpl`, and asserts the fail-closed Gateway implementation.

## Validation

- Focused Gradle gate: `BUILD SUCCESSFUL` in 49 seconds, 398 actionable tasks (71 executed, 327 up-to-date). It covered
  iOS runtime Spotless/Detekt/JVM tests, iosSimulatorArm64 and iosArm64 compilation, Simulator native test-source
  compilation, and the Simulator Debug framework link.
- Required repository gate completed 2,018 actionable tasks (186 executed, 1,832 up-to-date). Formatting, both Android
  Debug assemblies, tests and `allTests`, Desktop/JVM, KMP/iOS compilation, and F-Droid/Google Debug lint passed. The
  overall exit remained 1 only for six pre-existing Detekt findings in unmodified BLE (3), domain (1), model (1), and
  network (1) sources; changed iOS Detekt passed.
- A fresh signing-disabled Xcode Simulator Debug build succeeded, installed on `Codex iPhone 17`
  (`E3249756-57AF-4D9C-AA2B-3332E9309529`), cold-launched as PID 12071, and returned the same PID on the follow-up
  launch.

## Evidence limits

Kotlin/Native test executables remain link/run-disabled by repository convention, so the new production-graph test is
compiled but not counted as executed. The Simulator host launch validates assembly and startup, not a real secondary
BLE connection. Physical concurrent two/four-radio ownership, independent Stage 2 and control, reconnect/restoration,
background behavior, LoRa airtime, remote receipt, signed Release, TestFlight, and App Store behavior remain unproven.
