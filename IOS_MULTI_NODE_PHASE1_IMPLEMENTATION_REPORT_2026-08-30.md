# iOS NTsocial MeshLink Meshtastic Multi-Node Phase 1 Implementation Report

## Outcome and product boundary

The iOS NTsocial MeshLink companion now implements the same bounded Meshtastic fleet model used by Android: one
durable catalog with at most four distinct BLE endpoints, one immutable legacy-primary compatibility endpoint, and an
independent runtime graph for every secondary endpoint. This is real endpoint ownership rather than a read-only tab
projection.

The Apple Gateway boundary remains deliberately narrower than the UI fleet. NTsocial iOS continues to communicate
through the root/primary endpoint only. Secondary endpoint scopes are never candidates for App Group ingress, Gateway
status, route publication, or outbound Gateway drain.

## Runtime isolation

- `DefaultRadioFleetManager` and the shared DataStore endpoint catalog provide the maximum-four cap, address
  deduplication, stable selection, durable restore, and serialized full-session bootstrap.
- The first profile wraps the existing iOS root service/repository/orchestrator graph so established Apple Gateway and
  parent compatibility behavior is preserved.
- Each secondary profile opens its own address-derived Room database and radio/config DataStore files, creates an
  independent Koin scope, repository/service/packet/config graph, Kable/CoreBluetooth transport generation, and
  Room-backed queued-message drain.
- The existing address-keyed BLE registry prevents one endpoint from replacing another endpoint's active peripheral.
  Exact runtime tokens and expected-scope unregistering prevent a stale session or callback from deleting or
  publishing through its replacement.
- Endpoint shutdown cancels connection/session work, unregisters projections, releases the scoped dependency graph,
  and closes its pinned database. Process shutdown stops the fleet before closing the root graph.

## UI and interaction parity

- Connections exposes Android-parity fleet cards with used/capacity count, primary marker, endpoint name,
  address-last-four suffix, connection status, select, connect/disconnect, and secondary removal actions.
- Selecting a discovered BLE device creates or reuses a durable endpoint profile. The first endpoint updates the root
  compatibility selection; later endpoints connect through their own fleet sessions.
- Conversations renders an aggregate All view plus endpoint views from bounded read-only projections. Message and
  channel ownership remains inside each endpoint's database/repository graph.
- Nodes, Settings, Channels, and endpoint Conversations resolve ViewModels from the selected endpoint scope. Routes
  carry endpoint identity and generation, and fail closed if the runtime has been replaced.
- Switching endpoints returns the current top-level feature to its root, matching Android phase 1 and avoiding a
  retained child route against the wrong dependency graph. App-global preferences remain shared.
- Connections and MeshCore remain root-hosted. MeshCore is still a transport-pending destination and is not part of
  Meshtastic multi-node ownership.

## Verification

Focused validation used JDK 21, one Gradle worker, and the repository-required en-US JVM locale.

- iOS runtime JVM tests pass 19/19, including storage identity, exact scope replacement, fleet projection signaling,
  durable queue/drain, retry scheduling, Gateway guards, and shell routing.
- Shared radio-fleet JVM tests pass 8/8 and preferences JVM tests pass 36/36, covering the existing maximum-four,
  deduplication, persistence, serialized lifecycle, selection, and fleet projection contracts.
- Changed iOS runtime and Connections Spotless/Detekt checks pass.
- Simulator Debug and generic arm64 Release static frameworks link. The Release framework run completed 282 actionable
  tasks in 7m06s.
- A fresh signing-disabled Simulator Debug Xcode build installed and cold-launched on `Codex iPhone 17`
  (`E3249756-57AF-4D9C-AA2B-3332E9309529`) as PID 36827 and retained the same PID after three seconds. The visually
  inspected Traditional Chinese Connections screen rendered the five-destination shell and Bluetooth controls.
- Fresh signing-disabled generic-iphoneos Release Derived Data
  `/tmp/ntsocial-ios-multinode-release.hgDScm` built with quiet exit 0 and no output.
- The final root gate requested `spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile
  :app:lintFdroidDebug :app:lintGoogleDebug --continue`. It completed 2,015 actionable tasks (381 executed, 1,634
  up-to-date) in 3m01s. Formatting, both Android Debug assemblies, JVM/Android host tests, `allTests`, Desktop/JVM,
  shared KMP/iOS Simulator compilation, cloud-runtime guards, and both Android Debug lints passed. Exit 1 came only
  from the six recorded pre-existing Detekt findings in unmodified BLE (3), domain precise-location sharing (1), model
  identity (1), and network transport (1) sources; the changed iOS/Connections Detekt tasks passed.
- Kotlin/Native iOS tests remain link/run-disabled by project convention; native evidence is compilation, framework
  linkage, and unsigned host packaging rather than native test execution.

## Known limits and required hardware gates

- No physical iPhone with two or four concurrent Meshtastic radios was available for this source phase. Concurrent
  CoreBluetooth ownership, independent Stage 2 completion, channel/settings mutation, reconnect storms, restoration,
  and background behavior therefore remain unproven.
- The Simulator had no real or injected BLE fleet, so the empty Connections surface was visually inspected but the
  populated four-card fleet was not represented as a hardware/UI proof.
- There is no LoRa airtime, cross-radio scheduling or bridging, remote RF receipt, secondary Apple Gateway, MeshCore
  transport, signed archive, entitlement, parent-App interoperability, TestFlight, or App Store evidence.
- The existing Node dependency chain still emits Coil/Skiko alignment warnings and Vico `Paint.shader` linker
  diagnostics even though both frameworks link. Physical node-chart rendering remains a separate dependency/device
  gate.
