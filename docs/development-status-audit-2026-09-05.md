# NTsocial MeshLink 開發現況與代理指南審查 — 2026-09-05

本次核對確認專案已是 Android、Windows、iOS 三條產品線；原 `AGENTS.md` 的主要問題是將不同
日期、版本與驗證層級的紀錄並列為「目前狀態」。此外，現行原始碼確有 Android Gateway v3
次要節點能力宣告與派送不一致，以及驗證清單未跟上模組擴充的問題。更新指南不能只更改日期。

審查基準為 `99bc567d9777adb15e17133403afda630bcaaa56`，開始時工作目錄乾淨。HEAD 的變更是
Xcode 使用者介面狀態；最近的產品變更為 `6db8c6a8d` 的共用訊息輸入列 IME padding。
本次只修改文件及代理指引，沒有修補產品程式、變更 CI、修改 parent 專案、安裝裝置或發送訊息。

## 審查方法與證據層級

核對近期 Git 變更、實際 host/Koin 綁定、Gateway 路由與派送、Radio Fleet、Room cursor、
iOS outbox/掃描器/CoreBluetooth、Desktop stub、Gradle convention、CI workflow，以及留存的
實機報告與 `.agent_memory/session_context.md`。Android、iOS、Desktop/build 三部分分工查核，
主審再次沿關鍵呼叫鏈確認，並執行目前 checkout 的完整本機驗證。

以下「原始碼確認」指在此 revision 可追溯的實作事實；「歷史實機證據」只適用於當時的產物與
情境；「待重現候選」不表示本次已觀察到裝置故障。這是開發現況與高風險邊界審查，並非對
所有協定、所有競態、所有機型完成無缺陷認證，也未查證外部商店或 parent 工作目錄的現行狀態。

## 優先處理的發現

### 1. Android Gateway v3 已存在，但次要節點派送接到拒絕實作

**優先度 P1；原始碼呼叫鏈確認，本次未做實機發送重現。**

原指南把 Gateway v3 列為後續工作；實際上 `3c35fe106` 已於 8 月 26 日加入 v3 Provider、
fleet catalog、route token、endpoint idempotency 與派送。8 月 31 日的 secondary Koin 修復
`5b856b5fa` 又將該 graph 的 Gateway repository 綁定改為 fail-closed 實作，與既有 v3 發送路徑
產生衝突。

| 生產路徑 | 定位與結果 |
| --- | --- |
| 載入 secondary module | `app/.../AndroidKoinBootstrap.kt:53` 載入 `radioEndpointKoinModule`。 |
| 建立 endpoint scope | `app/.../radio/AndroidRadioEndpointSessionFactory.kt:80,103` 只有 legacy-primary 使用 root，其餘建立獨立 scope。 |
| 實際 repository 綁定 | [RadioEndpointKoinModule.kt](../app/src/main/kotlin/com/ntsocial/meshlink/app/radio/RadioEndpointKoinModule.kt) 第 179 行：`scoped<NtsocialGatewayRepository> { SecondaryGatewayRepository() }`。 |
| v3 source 使用同一綁定 | [AndroidEndpointConversationSourceCoordinator.kt](../app/src/main/kotlin/com/ntsocial/meshlink/app/radio/AndroidEndpointConversationSourceCoordinator.kt) 第 112–115、150–158 行註冊 source，非 primary 從 endpoint scope 取得 repository。 |
| 對外宣告能力 | [MeshtasticEndpointGatewaySource.kt](../app/src/main/kotlin/com/ntsocial/meshlink/app/radio/MeshtasticEndpointGatewaySource.kt) 第 114–115 行可對 READY endpoint 宣告 native/overlay send available，未檢查 repository 是否能發送。 |
| 派送結果 | 同檔第 162、180 行呼叫 durable-send methods；[EndpointHostAdapters.kt](../app/src/main/kotlin/com/ntsocial/meshlink/app/radio/EndpointHostAdapters.kt) 第 217–244 行的兩個實作都直接 throw。 |
| 對 parent 的結果 | [NtsocialGatewayCommandReceiver.kt](../core/service/src/androidMain/kotlin/com/ntsocial/meshlink/core/service/NtsocialGatewayCommandReceiver.kt) 第 432、485 行派送，470、517 行捕捉例外後回 `QUEUE_FAILED`，不會因例外改走 root。 |
| 目前測試缺口 | [SecondaryRadioEndpointScopeRuntimeTest.kt](../app/src/test/kotlin/com/ntsocial/meshlink/app/SecondaryRadioEndpointScopeRuntimeTest.kt) 第 71 行反而確認該 fail-closed binding；fake source/facade/token 測試不是 production secondary send 測試。 |

因此正確狀態是「v3 surface 已實作，secondary send 有阻塞」，不能寫成「未實作 v3」或
「多節點 Gateway 全部完成」。建議後續建立 endpoint-owned 的 v3 admission 路徑，保留 v1/v2
legacy-primary 隔離，並讓能力宣告與實際可用的派送一致；切勿直接把 root repository 注入
次要節點作為快速修復。驗收應包含真實 secondary graph 的 native/overlay durable admission，
以及能區分 endpoint、generation、產物的雙節點裝置紀錄。

9 月 3 日 memory 留有雙 endpoint UI、20 次 aggregate QueueStatus 成功及 iPhone 收到
20 個 PRIVATE_APP packet 的歷史觀察，但缺少足以解釋上述現行綁定衝突的逐 endpoint 派送
證據。保留該觀察，不把 aggregate 計數提升為目前每個 secondary send 成功的證明。

### 2. 根目錄靜態檢查實際為 8 項，而非指南沿用的 6 項

**優先度 P2；本次 Gradle 重跑確認。**

| 模組 | 檔案／行 | Finding |
| --- | --- | --- |
| `core:ble` | `JvmDesktopBluetoothPairingService.kt:154` | `TooGenericExceptionCaught` |
| `core:ble` | 同檔 `:143,188` | `ThrowsCount` × 2 |
| `core:domain` | `SetPreciseLocationSharingUseCase.kt:51` | `LongMethod` |
| `core:model` | `NtsocialGatewayIdentity.kt:168` | `MagicNumber` |
| `core:network` | `BleRadioTransport.kt:245` | `ThrowsCount` |
| `core:ui` | `LocalBarcodeScannerProvider.kt:44,55` | `CompositionLocalAllowlist` × 2 |

最後兩項位於 Channels scanner capability 相關的共用 UI，現行根目錄 gate 因五個 Detekt task
失敗而退出 1。舊報告的六項是當時結果；不能延伸成後續 QR／outbox／IME 變更的最新 gate。
指南現已列出八項，不加入 suppression，也沒有在文件工作中順手修改產品實作。

### 3. CI 模組清單與 iOS 測試執行範圍被過度描述

**優先度 P2；設定檔確認。**

- [RootConventionPlugin.kt](../build-logic/convention/src/main/kotlin/RootConventionPlugin.kt) 第 69、
  84–122 行以手寫清單建立 `kmpSmokeCompile`，並非自動發現所有模組。清單漏掉
  [settings.gradle.kts](../settings.gradle.kts) 第 109 行已納入的 `core:radio-fleet`。本次 gate
  仍經相依鏈編譯該模組，故這是直接 inventory 漏列，不是完全未編譯的證明。
- [reusable-check.yml](../.github/workflows/reusable-check.yml) 第 289–355 行的 test/Kover shards
  未列 `core:gateway`、`core:meshcore`、`core:radio-fleet`、`feature:meshcore`、`ios:runtime`
  各自的測試。編譯相依模組不會自動執行它們的測試。本機 root `allTests` 與 CI shard
  是不同覆蓋集合；不能以本機通過推論遠端 CI 已涵蓋。
- [KotlinAndroid.kt](../build-logic/convention/src/main/kotlin/com/ntsocial/meshlink/buildlogic/KotlinAndroid.kt)
  第 116–129 行停用 iOS native **test executable link 與 run**。本次 log 也顯示相應 task
  `SKIPPED`。`compileTestKotlinIosSimulatorArm64` 只編譯測試來源；app framework link 不是
  test executable link，更不是 native test 執行。
- 現有 workflow 沒有明確 Xcode host build/launch gate；Ubuntu job 要求 native task，不代表
  在 Apple host 完成驗證。`DESKTOP_ONLY=true`／`-Pdesktop.only=true` 又會排除 Android/iOS。

後續應同步 settings、root inventory、CI test/Kover shards，補明確的 Apple-host 驗證工作。
此次更新指引，列出受影響模組需明確執行的 focused tasks；尚未修改 workflow。

### 4. 全歷史清除後，長期訂閱可能仍發布舊 history epoch

**優先度 P2；既有問題，本次重新核對原始碼。**

[PacketRepositoryImpl.kt](../core/data/src/commonMain/kotlin/com/ntsocial/meshlink/core/data/repository/PacketRepositoryImpl.kt)
第 174–185 行在每次 database subscription 開始時讀取 epoch，再對變動 sequence 套用該固定值。
[PacketDao.kt](../core/database/src/commonMain/kotlin/com/ntsocial/meshlink/core/database/dao/PacketDao.kt)
第 581–584 行清除歷史時卻會在 transaction 中更新 epoch。未重新訂閱的 observer 因此可能
看到新 sequence／舊 epoch 組合，影響 parent cursor reset 判斷。

第 188 行起的同步 `readCurrentGatewayHistoryState` 會重新讀取，是另一條路徑，不能因此
認定所有 observer 都已修復。應用包含 active subscriber 的 clear/reset regression 驗收。
本次保留該阻塞，沒有宣稱 live reset 已正確。

### 5. iOS QR 原生 callback 的完整 ownership 保證尚需補證

**待重現的原始碼審查候選；本次未證實裝置競態或錯誤套用。**

[BarcodeScannerHost.swift](../iosApp/NTsocialMeshLink/BarcodeScannerHost.swift) 第 128–134 行
`didAdd` 未確認 `activeScanner === dataScanner`；相鄰 unavailable callback 第 141 行有此
檢查。第 269–277 行 `finishScanning` 取用目前 scanner/request ID。若 retired scanner 的
辨識 callback 延遲至新請求，僅依 Kotlin request ID 並不足以證明原生來源沒有被誤歸屬。

原碼已有停止掃描、清除 delegate、dismiss 與重複完成防護，因此本報告不將假設時序當成
已觀察到的故障。建議以 scanner instance identity 守衛及晚到 callback 測試核對。指南已
縮小原本「所有 stale native callbacks 都被拒絕」的保證，沒有宣稱發生 wrong-channel apply。

### 6. Windows 完成度與第一次配對仍須區分

[DesktopKoinModule.kt](../desktop/src/main/kotlin/com/ntsocial/meshlink/desktop/di/DesktopKoinModule.kt)
第 166、203–210 行清楚顯示 fleet、host MQTT、位置、phone location、compass、worker 等 no-op
綁定。BLE/TCP/Serial backend 存在，但共同 Connections 只顯示 Bluetooth；backend presence
不等於 UI 或 host capability 已提供。

7 月 23 日 memory 記錄 Windows BLE discovery 成功，其後 first pairing 回報失敗且沒有 PIN
視窗。現行 [JvmDesktopBluetoothPairingService.kt](../core/ble/src/jvmMain/kotlin/com/ntsocial/meshlink/core/ble/JvmDesktopBluetoothPairingService.kt)
仍有 PowerShell/basic `PairAsync` helper；本次 macOS JVM gate 無法證明 Windows 配對已恢復。
指南改用可核對能力，移除不具測量依據的「high-completion／fully usable」暗示。

目前普通 Desktop version fallback 為 `1.0.8`；7 月 23 日 unsigned `1.0.0` MSI/EXE 是歷史
產物。Windows 視覺與封裝 metadata 已實作，不代表 Windows parent IPC、Windows Service、
Authenticator、signed installer、首次配對、縮放或實際升級 QA 已完成。

## 三平台現況與留存裝置證據

| 平台／範圍 | 現行實作 | 已保留證據 | 尚不能據此推論 |
| --- | --- | --- | --- |
| Android fleet | 最多四個隔離 BLE endpoint，per-radio Room/DataStore/Koin，Channel Hub 與 endpoint scope | 8/31 一支手機同時兩 radio 完成 Stage 2；secondary-only reconnect 保留 primary | 三／四 radio 實機、獨立 channel mutation、Doze、v3 secondary send 完成 |
| Android 最新 UI | channel/private composer 的 shared `imePadding()`；Channels 掃描入口；三語啟動 | 9/3 三手機 Debug 1.0.8 (9) 安裝／繁中 onboarding，一支 connected Samsung 的鍵盤及收合視覺 | 其他語言／accessibility 全面驗證、該次 RF send |
| Android→iPhone overlay | Android v3 surface 及 port 256 pipeline 存在，但有上述 secondary mismatch | 9/3 memory 的 aggregate Gateway acceptance、QueueStatus、iPhone PRIVATE_APP receive | 每個現行 secondary 都送達、雙方 parent canonical delivery |
| iOS UI/fleet | 真實共享 Compose shell、Settings、三語、最多四 endpoint scope、primary-only Apple Gateway | Simulator/source gates，後續單 radio signed Debug 裝置 | 同一 iPhone 同時二／四 radio、所有 shared Settings 都有 native 實作 |
| iOS BLE/Gateway | verified restored peripheral、bounded retry/lease/cleanup，App Group/Keychain，READY route renewal | 9/3 signed entitled Debug、saved radio 恢復、冷重啟、suspended indications、parent 同 generation READY catalog | permanent background、terminated app 保證喚醒、Release/store entitlement、完整 parent remote receipt |
| iOS native outbox | 以 port/origin 明確分類 Gateway，native 先於被 gate 阻塞的列 | 9/3 focused 3/3、signed device build、native 送入 radio、fresh native 狀態及使用者確認雙向文字交換 | Apple Gateway overlay 雙向 remote delivery、RF range、長期壓測 |
| iOS QR | Channels-only VisionKit camera、token bridge、endpoint shared confirmation | 9/3 實機 permission／reticle／close／返回 Channels | optical QR decoding、connected-radio QR apply/readback、callback 所有競態 |
| Windows | 品牌、共享 feature graph、single-radio host、transport backends、installer identity | 7/23 unsigned packaging／BLE discovery；此次 macOS Desktop JVM gate | 現版 Windows 配對／install／upgrade／簽章／parent 整合 |
| MeshCore | 協定、model/repository 與 UI foundation | source/fake tests | production transport、RF、multi-node |

iOS 八月的「只有兩頁工程 shell」「未簽署權限／沒有任何實機 BLE」已不再適合作為目前總結。
9 月 3 日 signed Debug 報告覆蓋先前 diagnostic entitlement-stripped 產物的限制，但不會
回頭改變舊產物的事實，也不授予未來 Release 自動通過。

Parent manual binding 與逾期 route 自動恢復已在 memory 9 月 3 日段落記錄。後者修改的是
另行授權的 iOS parent，並有 135 秒背景後一次 Send、自動 foreground recovery、四筆本機
acceptance 的保留敘述；不是本次修改，也不是本次重新查證的 parent 原始碼或遠端接收證據。

## 本次可重現驗證

macOS arm64，Homebrew OpenJDK 21.0.11，Android SDK bootstrap 完成，proto submodule
`git submodule update --init` 完成；`JAVA_TOOL_OPTIONS` 設為 en-US。

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export ANDROID_HOME="$HOME/Library/Android/sdk" # 使用本機實際存在的 SDK 位置
export JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"
./gradlew spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile \
  :app:lintFdroidDebug :app:lintGoogleDebug \
  --continue --max-workers=1 --no-configuration-cache
```

結果：**退出 1，4 分 19 秒，1,946 actionable tasks：374 executed、3 from cache、1,569 up-to-date。**

| 驗證 | 結果與限制 |
| --- | --- |
| Spotless | 通過。文件審查未對產品執行 `spotlessApply`。 |
| Android | F-Droid/Google Debug assembly 及兩種 Debug lint 通過。 |
| `test allTests` | root 所選 JVM／Android host 測試完成，無失敗；包含增量／快取結果，不宣稱每項測試都全新執行。 |
| Desktop | `:desktop:test` 通過；主機是 macOS，非 Windows device QA。 |
| KMP | `kmpSmokeCompile` 完成；本次 log 確認 radio-fleet 經相依鏈被編譯／其測試 task 被 root 選取。 |
| iOS | runtime JVM tests 與 Simulator source/test-source compile 完成；native test executable link/run 顯示 `SKIPPED`。本次未新增 Xcode／arm64 framework／signed-device run。 |
| Detekt | 5 個模組 task 失敗、共 8 findings，詳上表；整體 gate 不可標綠。 |
| 文件 | 本地 link/path、必要段落、版本／阻塞描述一致性與 `git diff --check` 另行檢查。 |

完整本機輸出位於 git-ignored `.agent_plans/status-audit-2026-09-05/full-gate.log`，未將
巨大 build log 或裝置資料加入版本控制。本文保存 command、revision、environment、task counts
與 failures，方便在其他 checkout 重現。未額外建置 Release/AAB、查驗 signing 或讀取商店狀態。

## 文件處理與後續維護

- `AGENTS.md` 改為單一目前 snapshot：身份與版本、能力矩陣、明確阻塞、證據界線、後續定位；
  保留既有協定、隱私、GPL、channel/LoRa、host 與 release 規範。
- 35 段歷史狀態保存在 [archive](archive/agent-status-history-through-2026-09-04.md)，明示其不是
  現行指令；舊 APK hash、歷史 task count／device 結果保留可追溯性。
- 同步 `.github/copilot-instructions.md` 的三平台／v3／8 findings／native testing 說明，
  更新 `CLAUDE.md`、`GEMINI.md` 的產品身份及過時轉介；不要求代理公開內部逐步推理。
- `.skills/project-overview/SKILL.md` 補上 iOS、Gateway、radio-fleet、MeshCore 與狀態入口；
  `.skills/testing-ci/SKILL.md` 更正 formatting 順序、task 名稱及 CI inventory／native limits。
- `README.md`、`docs/kmp-status.md`、`docs/roadmap.md` 加上 dated supersession notice。
  歷史 roadmap 的 TODO、completion percentage 或「全 KMP MQTT 已完成」不可代替 host source。
- Session memory 追加本次結果，明示既有 entry 並非全域時間排序；新任務須以日期／主題搜尋。

建議下一輪先處理 production secondary Gateway dispatch 與 capability 一致性，再修復八項
Detekt 和 CI/native coverage，接著處理 live history reset、驗證 Swift scanner ownership 與
Windows first pairing。每項都應建立對應的 production-path 或平台驗收證據，不能靠再追加
一段「完成」敘述消除 source/evidence 落差。

## 主要歷史證據索引

- [Android simultaneous multi-node BLE remediation, 2026-08-31](../ANDROID_SIMULTANEOUS_MULTI_NODE_BLE_REMEDIATION_REPORT_2026-08-31.md)
- [Android Channel Hub／三手機 run, 2026-08-25](../ANDROID_MULTI_NODE_CHANNEL_HUB_UI_UX_IMPLEMENTATION_AND_THREE_PHONE_REPORT_2026-08-25.md)
- [iOS shared UI parity, 2026-08-29](../IOS_UI_PARITY_PHASE1_IMPLEMENTATION_REPORT_2026-08-29.md)
- [iOS multi-node source, 2026-08-30](../IOS_MULTI_NODE_PHASE1_IMPLEMENTATION_REPORT_2026-08-30.md)
- [iOS production graph remediation, 2026-08-31](../IOS_SIMULTANEOUS_MULTI_NODE_PRODUCTION_GRAPH_REMEDIATION_REPORT_2026-08-31.md)
- [iOS historical diagnostic entitlement limitation, 2026-08-30](../IOS_PARENT_INTEGRATION_USB_PHYSICAL_DEVICE_TEST_REPORT_2026-08-30.md)
- [iOS signed restored BLE recovery／READY, 2026-09-03](../IOS_RESTORED_BLE_SESSION_RECOVERY_REMEDIATION_REPORT_2026-09-03.md)
- [iOS native outbox／two-way text, 2026-09-03](../IOS_OUTBOUND_NATIVE_MESSAGE_QUEUE_REMEDIATION_REPORT_2026-09-03.md)
- [Session memory：September 3 五手機、parent expiry、QR 與 Android IME](../.agent_memory/session_context.md)
