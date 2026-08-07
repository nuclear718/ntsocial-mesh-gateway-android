# iOS NTsocial MeshLink 遺留問題與後續接手報告

> 日期：2026-08-07
> 範圍：iOS NTsocial MeshLink companion、Apple Gateway，以及另行授權的
> `NTsocial_release` iOS 母程式 adapter。
> 證據界線：本文件記錄目前 source、已保留的測試證據與仍未關閉的 gate；不代表已簽章實機、
> LoRa/RF、TestFlight 或 App Store readiness。

## 1. Source 與測試狀態

目前工作樹已建立 iOS 第三產品軌，主要 source 包含：

- static `MeshLinkKit` framework 與薄 SwiftUI/Xcode host；iOS 17 deployment target、AppIcon asset
  catalog、source Privacy Manifest、source entitlements，以及 fail-closed 的 Skiko ICU data-only archive
  normalization；
- Koin、Room KMP、file-backed DataStore、shared repository/orchestrator stack、
  `DirectRadioControllerImpl`、Kable/CoreBluetooth transport 與 restoration configuration；
- Apple Gateway v1 App Group SQLite mailbox、shared-Keychain HMAC、payload-free Darwin hint、
  `ntsocial-meshlink://process` handoff、private restart-stable ledger、durable Room outbox；
- exact selected/active radio session、active per-radio database、complete channel readback、history epoch 與
  channel fingerprint guard；retired transport callback、ingress child work、outbound queue/status/log generation
  的 retirement barrier；
- `READY` 即時 drain、單一合併的 500/1,000/2,000 ms bounded retry，以及 64-command drain-budget
  continuation；
- schema-v1 additive `overlay_epoch_state`，使每個 history epoch 的 overlay high-water 不受 128-row
  retention 淘汰影響；accepted-ledger commit 後、result publication 前 crash 的 exact replay；
- 母程式 Swift adapter 的 first-send pending correlation、multipart/final-message-ID restart correlation、
  duplicate-source deterministic projection、same-epoch historical resolver、durable gap/quarantine/
  abandoned-transfer recovery，以及 authenticated native broadcast-text enqueue API。母程式 native-text
  composer UI 未納入本次範圍。

Current-source focused suites 為 135/135：domain 16/16、data 104/104、iOS runtime 15/15；
`:core:gateway:jvmTest` 另為 36 tests。母程式 `AppleGatewayAdapterTests` 27/27、完整 SwiftPM 668/668，
且母程式 release source build green。Gateway/runtime 數字涵蓋 `overlay_epoch_state`、accepted-ledger replay、
session/active-database guard、READY retry/drain-budget、durable dispatch identity、exact readback correlation 與
shell/deep-link；母程式數字涵蓋 pending/multipart correlation、duplicate source、historical resolver、gap/poison
recovery 與 native-text enqueue。

同一 current-source focused gate 的 Spotless、五個 changed modules Detekt、
`:ios:runtime:compileKotlinIosSimulatorArm64` 與 `git diff --check` 亦通過。Data 104 的分布為 config flow 32、
connection 21、provision 11、packet handler 17、Gateway 22、real-lock integration 1；runtime 15 含 durable
source/liveness 2。

以上 focused evidence 已對應最後一輪 shared-source 修正。Root full Gradle 與 fresh Xcode host evidence 見
第 2.6 節；它們仍只是 source/simulator/unsigned device-architecture 證據。

## 2. 本輪已封口的 source 邊界與唯一 open P2

下列 2.1–2.4 本輪已封口，並由 deterministic tests／最終 bounded audit 驗證，無可重現 P0/P1。這是
source/test 結論，不是 signed-device、BLE、RF 或 release 證據。

### 2.1 Durable packet 的實際 dispatch 邊界：已封口

`ACCEPTED_LOCAL` 只代表 durable local admission，不代表 packet 已由 firmware 接受，更不代表 RF 或
remote receipt。已接受但尚未 drain 的 Gateway packet 必須在 private Room row 保留原始
`source_channel_id`，並在真正 radio dispatch 前重新驗證：

- exact configured session epoch 仍為 active；
- Gateway ingress identity 仍為該 session；
- numeric slot 仍解析成相同 source identity，包括 PSK/LoRa identity；
- validation、exact-session packet-queue admission 與 matching firmware `QueueStatus` 之間不能被 channel
  mutation 或 radio replacement 插入。

目前 source 已把原始 identity 持久化到 private Room row，並在 actual drain/dispatch 重新解析 slot identity；
validation 到 exact-session packet-queue admission 及 matching firmware `QueueStatus` 均受同一 operation boundary
保護。若 slot/PSK 已變更，舊 durable packet 會 fail closed，不會因 numeric slot 相同而送到新 channel。
「accepted 後、drain 前改 channel/PSK」與「同位址 reconnect」已有 deterministic coverage；bounded audit
未找到可重現 P0/P1。

### 2.2 Config-only readback correlation：已封口

Fresh channel readback 必須沿用 firmware 支援的 `69420` config-only sentinel；不能宣稱 firmware 支援一個
新的自訂 nonce。Host 端仍需提供 exclusive handshake owner/token：

- 只有 prior FULL handshake 的 Stage 2 已完成，且沒有其他 handshake owner 時，才能開始 exact-session
  config-only readback；
- response 必須歸屬到同一 configured session 與該次 host request token；
- completion 使用專用 host completion flow，不只等待 generic readback generation；
- 舊 FULL response、parallel FULL handshake、舊 session completion 或一般 generation 增加均不得滿足
  本次 readback；
- config-only completion 不啟動 FULL handshake 的 Stage 2 或 readiness side effects。

Host exclusive owner/token、stale FULL response、parallel owner、generic generation 與 config-only completion
side-effect 均已有 deterministic coverage；bounded audit 未找到可重現 P0/P1。

### 2.3 Channel mutation 與 Gateway ingress 的線性化：已封口

Manual/QR apply、public protected-channel reconcile 與 built-in NTsocial provisioning 已共用下列語意：

```text
validate exact session / ensure admin session
→ invalidate Gateway ingress
→ perform exact-session firmware mutations
→ obtain correlated exact-session fresh readback
→ activate only the verified final identity
```

完整 mutation 由獨立 mutation boundary 序列化；短期 operation boundary 負責 exact-session admission/
activation，因此不會持續持有並阻擋 Stage 1 readback commit producer。Radio
rejection、acknowledgement timeout、readback timeout/mismatch、session replacement 或其他 ambiguous outcome
均保持 ingress 關閉。Producer progress、manual/QR/reconcile/provision、ambiguous failure 與 FULL Stage 1 到
Stage 2/on-node-database-ready 間的 premature `READY`/activate window 已有 deterministic coverage；bounded
audit 未找到可重現 P0/P1。

### 2.4 Same-address reconnect 與已排程工作：已封口

Expected radio epoch 會從 admin/readback request、packet queue admission、dequeue 一直保留到同步 transport
send 的線性化點。即使 replacement 使用相同 Bluetooth address 或同一 transport object，retired session 的
metadata request、passkey refresh、raw packet、telemetry、late callback 或 persistence child work 都不得落入
replacement session/database。Transport callback generation lock、queue retirement/await barrier、active DB/cache
hydration ordering、exact-session admin/readback/raw-send APIs 與 same-address/same-transport cases 均已有
deterministic coverage；bounded audit 未找到可重現 P0/P1。

### 2.5 唯一 open P2：exact-readback owner 的 bounded liveness

若 exact config-only readback 已成功 admit，之後 caller timeout/cancel，且 firmware 永遠不回任何 late
response，host exclusive owner 在同一 radio epoch 內會維持 fail closed。結果是該 epoch 不再接受新的 exact
readback/identity activation；需 reconnect 產生新 epoch 才恢復。

這是 bounded liveness/availability P2，不是錯頻、跨 session 寫入或資料外洩：Gateway ingress 保持關閉，
durable Gateway packet 仍受 source identity 與 exact-session dispatch revalidation 保護。後續若要改善，可在不
允許 stale response 認領新 request 的前提下，加入可證明安全的 firmware cancellation/epoch rotation 策略；
目前 release/operator recovery 是 reconnect/new epoch。

### 2.6 Final current-source host evidence

- JDK 21、en-US `JAVA_TOOL_OPTIONS`、one worker 下，root
  `./gradlew --no-daemon --max-workers=1 assembleDebug test allTests kmpSmokeCompile --continue --console=plain`
  在 2m `BUILD SUCCESSFUL`；1,406 actionable tasks（333 executed、1 from cache、1,072 up-to-date）。Native
  iOS test link/run 仍依 repository convention 為 `SKIPPED`，不能算 native execution。
- Final root `detekt --continue` 只因五項既有 finding 非零：
  `core/ble/.../JvmDesktopBluetoothPairingService.kt` line 154 `TooGenericExceptionCaught`、lines 143/188
  `ThrowsCount`；`core/model/.../NtsocialGatewayIdentity.kt` line 168 `MagicNumber`；
  `core/network/.../BleRadioTransport.kt` line 246 `ThrowsCount`。Changed modules 與 Spotless 全綠。
- Fresh Simulator Debug Derived Data `/tmp/ntsocial-ios-final-fixed.2fROK9` 的 signing-disabled
  `xcodebuild clean build -quiet` exit 0 且零輸出。App 安裝並 cold-launch 於 `Codex iPhone 17`
  (`E3249756-57AF-4D9C-AA2B-3332E9309529`) 為 PID 67524，兩秒後仍為同一 PID。
- Fresh generic-iphoneos Release Derived Data `/tmp/ntsocial-ios-final-device.YaTk5N` 的 signing-disabled
  `xcodebuild clean build -quiet` exit 0 且零輸出；bundle version 為 `1.0.0`／build `1`，`Assets.car` 與
  `PrivacyInfo.xcprivacy` 均存在。
- Final Xcode 前發現的 Koin cycle（`NtsocialGatewayRepositoryImpl ↔ IosDurableMessageQueue`）已以 cycle-free
  `GatewayIngressSessionGate.activeSessionEpoch` 取代 queue 對 repository 的依賴；修正後 runtime 15/15、runtime
  Detekt、Simulator compile/framework link 與上述 cold launch 均通過。

兩個 Xcode build 都是 signing-disabled；zero quiet output 只證明本輪沒有 Xcode/linker/ICU warning，不是 signed
archive、physical-device install 或 entitlement/BLE/RF 證據。

## 3. 外部實機與 release gates

下列事項無法由 unsigned simulator/source build 取代，仍全部未證明：

- 在 Apple Developer portal 建立並由 provisioning profile 證明 parent `com.ntsocial.ios` 與 companion
  `com.ntsocial.meshlink.ios` 共享 App Group `group.com.ntsocial.meshlink.gateway`；
- 兩個 signed App 在相同 Team/AppIdentifier prefix 下實際讀寫 shared-Keychain access-group suffix
  `com.ntsocial.meshlink.gateway`，並解析到同一 HMAC key；
- physical iPhone 的 Bluetooth authorization/off/on、scan、select、connect、Meshtastic configuration
  handshake、background/foreground、CoreBluetooth restoration、reconnect、same-address reconnect 與 node
  reboot；
- signed two-App command write/claim/result、missed Darwin hint polling、deep-link handoff、process restart、
  upgrade、SQLite concurrency/migration/corruption/interrupted-transaction 與 key rotation；
- connected-radio durable local admission、firmware queue acceptance、LoRa airtime、第二台 radio reception、
  parent canonical import、retry/restart/idempotency、remote receipt，以及 Android/iOS bidirectional RF matrix；
- 啟用並真正執行 Kotlin/Native iOS test link/run tasks；目前 repository convention 仍會 skip native test
  execution，compile/link success 不能冒充 native tests；
- signed archive、entitlements inspection、AppIcon/Privacy Manifest/linked-API/final privacy review、license/source
  offer、TestFlight、App Store metadata/review、rollback 與 release evidence。

CoreBluetooth restoration、Darwin notification 與 deep link 都是 best effort；不得宣稱永久背景執行、
terminated-App 必然喚醒或遠端送達。

## 4. 刻意 deferred / non-goals

為維持 companion 的輔助定位，本次刻意不擴張為完整社交 App 或完整 Meshtastic App。下列項目 deferred，
不應被列為目前 source defect：

- 母程式 native broadcast-text composer UI；目前只保留 authenticated adapter API；
- companion routes/channel browser、native-text diagnostic composer、command-result administration、Gateway
  reset/panic-wipe UI 與 exported diagnostics bundle；
- maps、MQTT UI、firmware management、TCP、USB/serial、MeshCore transport，以及 broad Meshtastic settings
  parity；
- 讓 NTsocial 母程式 link GPL MeshLink radio implementation 或開第二個 BLE stack；母程式只應消費 Apple
  Gateway；
- Android ContentProvider/Broadcast/AIDL 的 iOS 複製品、永久背景 service，或把 parent proprietary business
  logic/secrets 複製進 GPL repository。

若未來產品需求確實需要其中任一項，應另立 scope、threat model、授權檢查與可驗收 spec，不應在 iOS 1.0
收尾階段順手加入。

## 5. 建議下一步

1. 取得 Apple Developer identifiers/profiles，在 parent/companion 兩個 signed App 上完成 App Group、Keychain、
   SQLite/HMAC、Darwin/deep-link 與 restart/upgrade 最小 round trip。
2. 以 physical iPhone 完成 Bluetooth authorization/off/on、scan/select、handshake、background/foreground、
   CoreBluetooth restoration、reconnect、same-address reconnect 與 node reboot matrix。
3. 接上 Meshtastic nodes，驗證 local admission、firmware queue acceptance、LoRa airtime、第二台 radio receipt、
   parent canonical import、retry/restart/idempotency、remote receipt 與 Android/iOS RF interoperability。
4. 完成 signed archive、entitlement inspection、Privacy Manifest/linked-API/privacy、license/source offer、
   TestFlight、App Store、rollback 與 release evidence；前述 device/RF gate 未完成前不得宣稱 readiness。
5. 啟用並執行 Kotlin/Native iOS tests，並評估第 2.5 節 P2 是否需要比 reconnect/new epoch 更好的安全恢復。
6. 保留第 2.6 節的 current-source root/Xcode 命令、task counts、warning 分類、artifact inspection 與五項
   root Detekt finding；任何後續 production、build-logic 或 Xcode 變更都必須重新跑，不能沿用本次證據。
