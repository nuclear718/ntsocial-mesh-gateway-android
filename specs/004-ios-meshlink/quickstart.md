# Quickstart: iOS development verification

## 1. Environment

Use JDK 21, an English JVM locale, Xcode with the iOS Simulator runtime, and a booted simulator:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export LC_ALL=en_US.UTF-8
export JAVA_TOOL_OPTIONS='-Duser.language=en -Duser.country=US'
xcodebuild -version
xcrun simctl list devices booted
```

The source deployment target is iOS 17.0. The current verified simulator was `Codex iPhone 17`, UDID `E3249756-57AF-4D9C-AA2B-3332E9309529`.

## 2. KMP and Gateway gates

```bash
./gradlew :core:gateway:jvmTest
./gradlew :ios:runtime:jvmTest
./gradlew \
  :core:gateway:compileKotlinIosArm64 \
  :core:gateway:compileKotlinIosSimulatorArm64 \
  :ios:runtime:compileKotlinIosArm64 \
  :ios:runtime:compileKotlinIosSimulatorArm64 \
  :ios:runtime:compileTestKotlinIosSimulatorArm64 \
  :ios:runtime:linkDebugFrameworkIosSimulatorArm64
./gradlew :ios:runtime:spotlessCheck :ios:runtime:detekt
./gradlew kmpSmokeCompile --continue
```

Current-source focused evidence is 135/135 across domain 16/16, data 104/104, and iOS runtime 15/15; Gateway JVM tests separately pass 36. Runtime coverage is session/active-database guard 4, coalesced bounded-retry scheduler 3, command-drain/budget continuation 3, durable-dispatch identity 2, inbound-identity projection signal 1, and shell/deep-link 2. Deterministic tests cover same-address/same-transport reconnect, exact admin/readback/raw send, firmware-69420 host owner/token correlation, readback producer progress, premature activation, ambiguous mutation outcomes, and accepted-before-drain channel replacement. The final bounded audit found no reproducible P0/P1 in these boundaries.

The following is the required broader en-US, one-worker gate shape. The former 1,410-task/2m14s result belongs to an earlier revision; current-source exact results remain pending:

```bash
./gradlew --no-daemon --max-workers=1 \
  assembleDebug test allTests kmpSmokeCompile --continue
```

Current Spotless and five changed modules' Detekt gates are green. Root Detekt remains pending; retain its new exact result and classify every remaining finding by file rather than inheriting the earlier count.

Important: `iosSimulatorArm64Test` is not currently execution evidence. The repository convention disables iOS native test link/run tasks in `build-logic/convention/src/main/kotlin/com/ntsocial/meshlink/buildlogic/KotlinAndroid.kt`; Gradle may report `BUILD SUCCESSFUL` while the native test tasks are `SKIPPED`. Enable and run those tasks before claiming native tests.

Known P2: if a firmware-69420 exact readback is admitted, then its caller times out/cancels and firmware never emits a late response, that same epoch remains fail closed. Reconnect/new epoch before retrying. This does not open wrong-channel dispatch or data exposure; ingress stays closed and durable dispatch revalidates source identity.

## 3. Simulator host build and launch

The required clean no-Xcode/linker/ICU-warning build shape is:

```bash
xcodebuild \
  -quiet \
  -project iosApp/NTsocialMeshLink.xcodeproj \
  -scheme NTsocialMeshLink \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'id=E3249756-57AF-4D9C-AA2B-3332E9309529' \
  -derivedDataPath /tmp/ntsocial-meshlink-ios-final-audit \
  CODE_SIGNING_ALLOWED=NO \
  clean build
```

For a deterministic local install path, repeat with a task-specific Derived Data directory:

```bash
xcodebuild \
  -project iosApp/NTsocialMeshLink.xcodeproj \
  -scheme NTsocialMeshLink \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'id=E3249756-57AF-4D9C-AA2B-3332E9309529' \
  -derivedDataPath build/ios-derived \
  CODE_SIGNING_ALLOWED=NO \
  build

xcrun simctl install booted \
  'build/ios-derived/Build/Products/Debug-iphonesimulator/NTsocial MeshLink.app'
xcrun simctl launch --terminate-running-process booted com.ntsocial.meshlink.ios
```

Inspect the UI and confirm the process remains alive. An earlier revision passed this simulator path; the current-source cold-launch result remains pending.

Confirm that the current-source invocation exits 0 with no Xcode/linker/ICU warning, the bundle's `Assets.car` contains AppIcon, and `PrivacyInfo.xcprivacy` is at the App bundle root. The build phase runs `iosApp/Tools/normalize_skiko_icu_archive.sh`: it fails closed unless the Skiko ICU member is the expected data-only layout (`__text = 0`, one ICU data symbol, unchanged `__const` bytes), then relinks only that member's platform metadata to the iOS 17 target and atomically rebuilds the static archive. This closed both former simulator warnings on the earlier revision; repeat it on current source and the signed archive.

After the current-source Simulator Debug gate is retained, run the generic device-architecture source path with signing disabled:

```bash
xcodebuild \
  -quiet \
  -project iosApp/NTsocialMeshLink.xcodeproj \
  -scheme NTsocialMeshLink \
  -configuration Release \
  -sdk iphoneos \
  -destination 'generic/platform=iOS' \
  -derivedDataPath /tmp/ntsocial-meshlink-ios-release-audit \
  CODE_SIGNING_ALLOWED=NO \
  clean build
```

The earlier revision produced an arm64 Mach-O App, included `Assets.car` and `PrivacyInfo.xcprivacy`, passed plist/privacy lint, and had no Xcode/linker/ICU warning; retain the same checks for current source. Gradle may still print its separate AGP common-test advisory, generated Wire-proto warnings, or always-run script note on a first clean source compilation. This artifact is unsigned and proves neither an archive nor physical-device installation, provisioning, entitlement access, BLE, or RF behavior.

## 4. Parent adapter tests

From the separately authorized `NTsocial_release` repository:

```bash
swift test --package-path ios --filter AppleGatewayAdapterTests
swift test --package-path ios
```

The current focused result is 27/27, the full SwiftPM result is 668/668, and the parent release build is green. The focused suite validates codec/store/domain behavior, exact v1 Keychain coordinates, additive `overlay_epoch_state` migration, payload conflict, restart-stable first-send pending and multipart/final-ID correlation, slot-retaining duplicate-source routes plus PRIMARY-then-lowest canonical projection and security-conflict rejection, same-epoch historical backlog, durable gap/quarantine/abandoned-transfer records before bounded recovery, terminal rejection, and authenticated native-text enqueue. Transient store/projection failure keeps the cursor fixed, and the recovery path does not claim already-evicted rows are recoverable. These are not real entitlement, signed cross-process, device, or RF results. The adapter API exists, but a parent native-text composer remains deferred.

## 5. Signed-device gate

Before device testing, both bundle IDs must be provisioned with the same real values:

- companion: `com.ntsocial.meshlink.ios`
- parent: `com.ntsocial.ios`
- App Group: `group.com.ntsocial.meshlink.gateway`
- Keychain access-group suffix: `com.ntsocial.meshlink.gateway` under the same Team/AppIdentifier prefix
- Keychain service/account: `com.ntsocial.meshlink.gateway.hmac` / `apple-gateway-v1`
- companion deep link: `ntsocial-meshlink://process`

Then retain evidence for:

1. both signed apps resolving the same App Group container and HMAC key;
2. command write, payload-free Darwin hint, companion claim/admission/result, missed-hint recovery, and parent cursor commit;
3. Bluetooth permission/off/on, scan, selection, Meshtastic handshake, background/foreground, restoration, reconnect, and node reboot;
4. connected-radio local admission, LoRa airtime, second-radio receipt, parent canonical import, retry/restart, and duplicate handling.

Simulator builds and source entitlements do not prove App Group/Keychain provisioning, physical BLE, background permanence, connected-radio admission, RF delivery, remote receipt, TestFlight, or App Store readiness.
