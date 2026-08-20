# Android 手機 GPS → Meshtastic 節點異常根因調查報告

- 調查日期：2026-08-20（Asia/Taipei）
- 結論狀態：根因已由原始封包、LOGCAT、裝置狀態與兩節點 RF 實測交叉證實
- Android 原始碼基準：`3b350c68ac2970f8e10bfb7dc1d4a1e8285eb18e`
- 使用中韌體：`firmware-nrf52_promicro_diy_xtal-2.8.0.b10d31e.uf2`
- 韌體 source commit：`b10d31effb49c307eaa6f6cd8532135c456f7b20`
- 調查範圍：Android NTsocial MeshLink、Android 定位/前景服務、BLE 封包佇列、Meshtastic POSITION_APP、韌體位置隱私精度、兩節點 LoRa 收發

韌體產物核對：

- 路徑：`C:\Users\cth\Documents\GitHub\faketec-RA-01SH-P\.pio\build\nrf52_promicro_diy_xtal\firmware-nrf52_promicro_diy_xtal-2.8.0.b10d31e.uf2`
- 大小：`1,430,016` bytes
- SHA-256：`5A4EDB203BC976C13180250AE8FC3C37927033B3E7FE5A02C935AD7F96639390`
- manifest：version `2.8.0.b10d31e`、target `nrf52_promicro_diy_xtal`、MCU `nrf52840`
- 韌體 repo 稽核時的 HEAD 為 `c62b1eba112662b85cb29abb94b0444f759b12ae`；本案涉及的 Position precision、Router、Channel 與 PositionModule source 與 `b10d31e` 無差異，working tree 為 clean

## 1. 執行摘要

本案不是單一故障，而是三個不同層次的行為同時出現。

| 現象 | 判定 | 真因 | 修正責任 |
|---|---|---|---|
| S24 與 OPPO 原本都沒有把手機 GPS 定期送入各自節點 | 已證實 | 每個 node 各自的「將手機位置提供給 Mesh 網路」偏好不存在，預設值是 `false`；授予 Android 定位權限不會自動啟用分享 | NTsocial MeshLink，P0 |
| 兩端都看到 `0.02621, 0.02621, 0m MSL` | 已證實 | MeshLink 在分享關閉或無有效 fix 時仍送 explicit `0,0,0` 的 Position request；韌體將 present-zero 依 primary channel 的 13-bit 隱私精度轉成第一格中心 `262144/262144` | MeshLink 必修；韌體防禦性修正 |
| 修正後遠端位置不是手機的完整 GPS 精度 | 預期行為 | 目前 Position packet 走 channel 0；該 default/public channel 是 `position_precision=13`，只公開約 5.8 km 寬的隱私格網中心 | 頻道/產品隱私策略，不是 GPS scaling bug |
| App 本機可能先顯示已更新，但韌體實際未收到 | 程式碼風險，這次未觸發 | `sendPosition()` 在 radio queue admission 前先樂觀更新本機 DB，之後 fire-and-forget；連線世代切換時可被拒絕或丟棄 | NTsocial MeshLink，P0 |

最重要的結論：

1. Android 的經緯度 `×1e7` 換算是正確的，不應修改協定單位。
2. 韌體接收有效 `LOC_EXTERNAL` 手機位置的核心路徑正常，不需要重寫。
3. 必須先修 MeshLink 的 zero-sentinel 與發送確認語意。
4. 建議韌體同時增加 legacy/malformed request 的防禦，避免其他舊版 client 再污染 NodeDB。
5. 若產品要求「另一支手機看到精確 GPS」，必須另行採用強 PSK 的 private channel 並設 precision 32；不能在 public/default channel 靜默取消隱私量化。

## 2. 調查方式與變更界線

### 2.1 使用的方法

- `adb devices`、package/version、permission、system location 與 foreground-service 狀態
- 兩支 MeshLink process 的完整 LOGCAT
- App DataStore preference、Room/封包紀錄與 radio config 的唯讀解碼
- Android 位置 provider 註冊狀態
- POSITION_APP raw protobuf 欄位、packet ID、transport 與 QueueStatus 的交叉比對
- 韌體 `b10d31e` source 的唯讀逐行稽核
- 使用既有 UI switch 做一個可逆控制組，驗證手機 GPS → BLE → 韌體 → LoRa → 對端 MeshLink

### 2.2 本次未做的事

- 未修改任何 Android 或韌體程式碼
- 未重編 APK、未重裝 Debug/Release APK
- 未刷寫或重開 Meshtastic 節點
- 未改 LoRa、position interval、channel precision、PSK 或 fixed-position 設定
- 未清除 App/韌體資料庫；避免破壞能證明根因的既有狀態
- 報告不記錄 PSK、admin key、private key、PIN 等敏感資料

### 2.3 唯一裝置狀態變更

為執行使用者要求的實際傳送測試，本次透過既有 UI 將下列兩個 per-node switch 開啟：

- S24 / Meshtastic 5d6e：「將手機位置提供給 Mesh 網路」= ON
- OPPO / Meshtastic 1407：「將手機位置提供給 Mesh 網路」= ON

兩者在報告完成前仍維持 ON，符合「讓手機 GPS 更新節點」的目標；此變更可直接由相同 UI 關閉。S22 沒有綁定 radio，未變更。

## 3. 裝置與版本

| 角色 | ADB serial | 手機 | Android | MeshLink/radio |
|---|---|---|---|---|
| 非 radio 控制組 | `R5CT30QMRTY` | Samsung SM-S9080（S22） | Android 16 / API 36 | 未連接 Meshtastic |
| 節點 A | `R5CX42P0SDH` | Samsung SM-S9280（S24） | Android 16 / API 36 | BLE `E4:4C:CD:A7:5D:6E`，Meshtastic 5d6e |
| 節點 B | `TWBYJJRWSGHIGU55` | OPPO CPH2695 | Android 16 / API 36 | BLE `EA:D6:5C:5E:14:07`，Meshtastic 1407 |

三支手機皆安裝：

- `com.ntsocial.android.debug`：1.5.6（36）
- `com.ntsocial.meshlink.google.debug`：1.0.5（6）

GPS feed 的控制者是 MeshLink；NTsocial parent App 是否正在前景，不會取代 MeshLink 的 per-node location opt-in。

## 4. 問題發生前的實機證據

S24 與 OPPO 在開啟 switch 前均符合：

- Fine 與 Coarse location permission 已授權
- Android 系統定位已開啟
- MeshLink 與各自 BLE radio 已連線
- radio `fixed_position=false`
- radio `gps_mode=ENABLED`
- `position_broadcast_secs=3600`
- Smart position enabled，minimum 100 m / 300 s

但是兩支手機也同時符合：

- DataStore 沒有自己的 `provide-location-<nodeNum>` key
- `UiPrefsImpl` 對不存在的 key 回傳 `false`
- `dumpsys location` 沒有 MeshLink 的 location request
- MeshService foreground type 僅 `0x10`（connectedDevice），沒有 `0x8`（location）
- 開啟 switch 前的完整 process log：
  - `Starting location updates with ...`：0 筆
  - `Sending position/time update`：0 筆
  - `Phone location updates stopped`：0 筆

因此「Android 權限已給」與「MeshLink 正在把 GPS 提供給 node」是兩件不同的事。本案原始狀態是前者成立、後者未啟用。

對應程式碼：

- `core/prefs/src/commonMain/kotlin/com/ntsocial/meshlink/core/prefs/ui/UiPrefsImpl.kt:180-190`

  `provide-location-<nodeNum>` 不存在時預設 `false`。
- `core/data/src/commonMain/kotlin/com/ntsocial/meshlink/core/data/manager/MeshConnectionManagerImpl.kt:163-219`

  listener 只在 per-node opt-in、canonical Connected、`fixed_position=false` 全部成立時啟動。
- `core/data/src/androidMain/kotlin/com/ntsocial/meshlink/core/data/repository/LocationRepositoryImpl.kt:59-128`

  Android provider 的名義 cadence 為 30 秒。

## 5. `0.02621` 的確定根因

### 5.1 Android 明確送出 present-zero

位置請求流程目前用 `Position(0.0, 0.0, 0)` 同時代表「不分享」與「尚無有效位置」：

- `feature/node/src/commonMain/kotlin/com/ntsocial/meshlink/feature/node/detail/NodeRequestActions.kt:41-46`
- `core/data/src/commonMain/kotlin/com/ntsocial/meshlink/core/data/manager/MeshActionHandlerImpl.kt:230-244`

`CommandSenderImpl.requestPosition()` 再無條件建出：

~~~text
latitude_i = 0       // optional field present
longitude_i = 0      // optional field present
altitude = 0         // optional field present
time = current time
want_response = true
~~~

來源：

- `core/data/src/commonMain/kotlin/com/ntsocial/meshlink/core/data/manager/CommandSenderImpl.kt:259-279`

現有 unit test 甚至把錯誤行為鎖成預期：

- `MeshActionHandlerImplTest.kt:421-442`
- test 名稱：`handleRequestPosition_doNotProvide_sendsZeroPosition`

這條 request 路徑不經 `AndroidMeshLocationManager`，所以「沒有 location subscription / 沒有 `Sending position/time update`」不代表完全沒有 POSITION_APP 出站。

### 5.2 韌體把 0 移到 13-bit 第一格中心

Meshtastic wire coordinate 的規格是：

~~~text
degrees = latitude_i or longitude_i × 1e-7
~~~

來源：`protobufs/meshtastic/mesh.proto:20-32`。

default primary channel 的位置精度是 13：

- `src/mesh/Channels.cpp@b10d31e:147-157`

`truncateCoordinate()` 清除低位元後，加上格網中心偏移：

- `src/mesh/PositionPrecision.cpp@b10d31e:31-42`

對 present-zero 計算如下：

~~~text
precision = 13
格網寬度 = 2^(32 - 13) = 524288 protocol units
格網中心偏移 = 2^(31 - 13) = 262144 protocol units
顯示角度 = 262144 × 1e-7 = 0.0262144°
UI 五位小數 = 0.02621°
~~~

`Router.cpp@b10d31e:377-385` 在 originator POSITION_APP 加密前套用此隱私量化。optional presence 不會被清除，因此對端收到的是「present 的 262144」，並把它當真實座標寫入 NodeDB。

完整路徑：

~~~text
MeshLink Request Position
  → explicit 0/0/0, want_response=true
  → BLE toRadio
  → firmware Router originator precision=13
  → 262144/262144, precision_bits=13
  → LoRa
  → 對端 PositionModule / NodeDB
  → UI 顯示 0.02621/0.02621/0m MSL
~~~

這不是 Android 少乘 `1e7`；若是少乘，無法精確解釋兩個欄位都固定成 `2^18`。

### 5.3 成對封包時間線

#### OPPO 1407 → S24 5d6e

- 16:34:21.852：OPPO 本機出站 POSITION_APP
  - from 1407 → to 5d6e
  - packet ID `1023754331`
  - lat/lon/alt = explicit `0/0/0`
  - `want_response=true`
- 16:34:22.033：S24 從 LoRa 收到同一來源
  - lat/lon = `262144/262144`
  - `precision_bits=13`

#### S24 5d6e → OPPO 1407

- 16:34:26.835：S24 本機出站 POSITION_APP
  - from 5d6e → to 1407
  - packet ID 日誌值 `-1214521293`
  - lat/lon/alt = explicit `0/0/0`
  - `want_response=true`
- 16:34:28.253：OPPO 從 LoRa 收到
  - lat/lon = `262144/262144`
  - `precision_bits=13`

兩個原始 request 與兩個 RF 收件逐筆對上，因此根因已從「靜態碼推測」提升為「現場封包證實」。

### 5.4 為何顯示 0m MSL

`Position(0,0,0)` 的 altitude 也被 `requestPosition()` 明確編碼成 present zero，所以 UI 顯示 `0m MSL`。這不是實際手機高度。

韌體另有次要 presence 問題：`PositionModule.cpp@b10d31e:222-229` 依 position flags 無條件宣告 MSL 或 HAE altitude presence，即使 localPosition 沒有真正高度。這不是本次 16:34 request 的第一來源，但應一併 harden。

## 6. 控制組：啟用分享後的端到端結果

### 6.1 S24 / 5d6e

- 16:54:34.504：開始註冊 fused provider，30 秒 interval
- 16:54:35.626：第一筆 `Sending position/time update`
- 送出 56-byte toRadio payload
- POSITION_APP queue packet ID `3080446010`
- firmware QueueStatus：`res=0`
- OPPO 在數秒內由 LoRa 收到 S24 新位置

### 6.2 OPPO / 1407

- 16:56:34.323：開始註冊 fused provider，30 秒 interval
- 16:56:35.860：第一筆 `Sending position/time update`
- 送出 56-byte toRadio payload
- POSITION_APP queue packet ID `1023754334`
- firmware QueueStatus：`res=0`
- S24 在數秒內由 LoRa 收到 OPPO 新位置

### 6.3 遠端結果

- 兩個接收端都不再顯示 `0.02621/0.02621`。
- 由於兩台手機在同一地點附近，兩端均落在相同 p13 隱私格：
  - `24.772608, 121.0318848`
- OPPO 的 S24 node detail 實際顯示樣本：
  - `24.7726, 121.03188, 125m MSL`
- 手機的完整本機 GPS 未寫入本報告；上述值是 RF 上本來就只公開到 p13 的格網中心。

17:07 的最後快照仍顯示：

- S24 與 OPPO 持續約每 30 秒記錄 `Sending position/time update`
- 對應 QueueStatus 均為 `res=0`
- 兩台 MeshService 都是 foreground，type `0x18`（connectedDevice + location）
- 沒有 `Phone location updates stopped`

這已實證：

~~~text
Android GPS fix
→ MeshLink LOC_EXTERNAL / ×1e7
→ BLE queue accepted
→ 綁定韌體更新
→ LoRa
→ 對端 radio
→ 對端 MeshLink DB/UI
~~~

所以這次不是 radio 硬體、BLE 連線或韌體 `LOC_EXTERNAL` ingestion 全面失效。

## 7. 精確 GPS 與隱私精度是另一項產品決策

目前觀察到的 Position packet 使用 channel 0：

- default/public PSK
- `position_precision=13`

p13 每格為 `0.0524288°`；緯度方向約 5.8 km，UI 只能顯示格網中心，不能顯示手機的完整 GPS。這是隱私設計。

目前其他 private secondary channels 使用強 PSK 且設定 p32，但 Position packet 仍走 channel 0；secondary p32 不會自動讓 primary Position 變精確。

若產品需求確定為「對端顯示精確手機 GPS」，建議：

1. 定義哪一個強 PSK private channel 是位置分享 channel。
2. 將該 channel 設為 `position_precision=32`。
3. 明確決定 Position packet 要使用該 channel，並做 Android/Windows/iOS 與標準 Meshtastic client 相容性評估。
4. 不要把 default/public key 的 channel 強制升到 p32；本韌體會把 publicly-decryptable key 限制在最多 15 bits：
   - `src/mesh/PositionPrecision.h@b10d31e:7-17`
   - `src/mesh/PositionPrecision.cpp@b10d31e:24-27`

若只要求「韌體內保存手機精確位置」，現有有效 `LOC_EXTERNAL` ingestion 已可做到；被量化的是 RF 對外 packet。

## 8. 更新週期：30 秒手機 fix 不等於 30 秒 RF 廣播

目前三個週期不可混為一談：

| 層級 | 現況 |
|---|---|
| Android location listener | 約每 30 秒取得/送入一筆手機 fix |
| 韌體 phone/API 接收 | 有自身節流與 queue 行為 |
| LoRa Position broadcast | CLIENT 預設 1 小時；Smart minimum 5 分鐘；同一精度格或 fixed position 可套 12 小時 stationary floor |

來源：

- `src/mesh/Default.h@b10d31e:16-25`
- `src/modules/PositionModule.cpp@b10d31e:464-568`
- `src/mesh/NodeDB.cpp@b10d31e:1015-1022`

本次第一筆有效手機位置確實立即經 RF 到達對端；但後續靜止或仍在相同 p13 大格網內時，不能期待每 30 秒廣播一次。

若產品有「遠端位置最大陳舊時間」SLA，應另行設計：

- 以完整 localPosition 判定實際移動，而非只看 p13 cell 是否變更
- 設定合理的 minimum/maximum RF 更新時間
- 仍受 airtime、channel utilization、電量與隱私政策限制
- 不建議直接把 30 秒 Android cadence 等同 30 秒 LoRa cadence

## 9. 必須修改 NTsocial MeshLink

### P0-A：移除 `Position(0,0,0)` sentinel

推薦語意：

- 分享關閉：只送 request/time，不攜帶本機座標
- 分享開啟但尚無有效 fix：只送 request/time，或等待 first fix 後再送
- 分享開啟且有有效 fix：才填入 lat/lon 與真正存在的 altitude

最小安全修法：

~~~text
ProtoPosition(
    latitude_i = null,
    longitude_i = null,
    altitude = null,
    time = now,
)
~~~

並維持 `want_response=true`，讓它仍是一個 Position request。

較乾淨的 API 修法：

- 將 `MeshActionHandler`、`RadioController`、`CommandSender.requestPosition` 與 UI action 的 current position 改為 nullable 或明確 sealed request model。
- 禁止再讓有效 domain object `Position` 同時扮演「無位置」sentinel。
- 不要在韌體或 App 猜測小數值是否「忘記乘 1e7」。

### P0-B：讓 phone-position 發送可確認

目前：

- `CommandSenderImpl.sendPosition():235-256` 在 radio 接受前先更新 App NodeDB。
- `PacketHandler.sendToRadio()` 是 fire-and-forget。
- 連線/session generation 改變或 queue 不接受時，呼叫端不知道失敗。

應改為：

1. `sendPosition` 成為 suspend。
2. 綁定 exact connection/session generation。
3. 等待 queue admission / matching QueueStatus。
4. admission 成功後才更新 App local cache。
5. transient failure 做 bounded retry。
6. 使用 conflated/latest-wins，避免 30 秒 fix 在斷線時累積成過期佇列。
7. own node identity 不存在時回傳明確錯誤，不可 silent return。

Upstream commit `e9d09a338` 已採用相近 contract，但不是目前 HEAD ancestor。此 fork 有額外 Gateway/session 邊界，不建議直接盲目 cherry-pick 106-file commit；應選擇性移植 position admission contract 與測試。

### P0-C：修正 consent 與狀態 UX

- onboarding 可繼續維持 privacy-safe 預設 off，但要清楚說明：
  - Android 權限只允許定位
  - 使用者仍須對每個綁定 node 明確開啟分享
- Device → Position 頁應顯示目前實際狀態：
  - 分享關閉
  - 等待連線
  - fixed-position 暫停
  - 權限缺少
  - 系統定位關閉
  - 正在等待 first fix
  - 正在分享；last accepted age
- Debug/Release package、清除資料、換 node number 後會是新的 per-node consent；不可只看 OS permission 判定已分享。
- 若產品決定首次配對時預設分享，也必須在 onboarding 取得明確同意後才寫 preference，不應暗中把不存在的 key 當 true。

### P1：provider 與 protobuf 品質

`LocationRepositoryImpl` 目前在 API 31+ 只要有 FUSED provider 就只選 FUSED，並有以下缺口：

- provider list 空仍先標記 active
- 無 fresh first-fix / last-known fallback
- 無 first-fix timeout/watchdog
- FUSED 存在但不產生 fix 時不降級 GPS/NETWORK
- provider exception 後 flow 終止，只等外部 reconcile

建議加入 enabled-provider 檢查、first-fix watchdog、有界重訂閱與 fallback，並讓 UI 消費真實 subscription/fix 狀態。

`AndroidMeshLocationManager.kt:77-93` 另應修正：

- `ground_track = (bearing × 100).roundToInt()`；proto 單位為 1/100 degree
- 只有 `Location.hasAltitude()` 時才設定 `altitude_hae`
- 有高度時設定 `ALT_EXTERNAL`
- 填入 `timestamp` / millis adjustment
- 視 Android accuracy 能力填入合理 accuracy 欄位

## 10. 建議的韌體防禦修正

App 是第一修正點；若需防止舊版或第三方 client 再污染 mesh，韌體修正也應列為 release gate。

### 10.1 正規化 legacy zero request

在 originator POSITION_APP 套 precision 前辨識：

~~~text
want_response = true
location_source = LOC_UNSET
latitude_i 與 longitude_i 都是 present zero
~~~

將 lat/lon/alt 正規化為 absent、保留 time，再進行 precision/encode。

不要把所有 `(0°,0°)` 一律丟棄；真實 Null Island 在協定上仍可能是合法座標。判斷必須結合 request intent、presence 與 source。

### 10.2 precision 必須尊重 optional presence

`applyPositionPrecision()` 應只量化確實 present 的欄位，並保證：

- absent lat/lon encode 後仍 absent
- time-only packet 不會產生座標
- 單一欄位 absent 不會被合成

現行函式修改 numeric storage 但不修改 `has_*`；absent 欄位通常不會上 wire。然而缺少明確 invariant 與 regression test，且 explicit zero request 正是因此穿過。

### 10.3 修正 altitude presence

`PositionModule.cpp:222-229` 應只在 localPosition 真正有 MSL/HAE altitude 時設 `has_altitude` / `has_altitude_hae`，避免無來源的 `0m`。

### 10.4 改善 replay 可觀測性

PhoneAPI config sync 可重播舊 NodeDB Position；重連後看到舊 `262144` 不必然代表新的 RF。建議加入 replay provenance 或明確 log，使診斷能區分：

- live RF
- local/internal
- PhoneAPI DB replay

正式修復不應以清 DB 為主；有效新 Position 已證實會覆寫舊 cache。

### 10.5 不應採用的修法

- 不要停用 13-bit privacy quantization
- 不要在 public/default channel 強送精確 GPS
- 不要在韌體看到 `25`、`121` 等值就猜測 App 少乘 `1e7`
- 不要用 `0.0262144` 黑名單；它是演算法結果，不是可靠的資料來源判定
- 不要只改 UI 顯示而讓錯誤值繼續污染 NodeDB

## 11. 回歸測試與驗收標準

### 11.1 Android unit/component

- opt-out request 解碼後：
  - lat/lon/alt 全部 absent
  - time present
  - `want_response=true`
- opt-in、尚無 fix：不得送 explicit zero
- opt-in、有效正負座標：精確 `×1e7`
- 赤道/本初子午線/真實 `0,0` 的 presence 語意明確
- altitude absence/source 正確
- ground track 使用 1/100 degree
- stale、越界或 provider invalid fix 有明確處理
- queue accept 後才更新 local DB
- queue reject、timeout、session rotate 時不產生 optimistic false success
- reconnect/process restart/node switch 後 per-node consent 與 listener 唯一性正確

### 11.2 韌體 unit

- precision 0、13、15、32
- absent time-only 經 encode/decode 仍 absent
- legacy `want_response + LOC_UNSET + present 0/0` 不得變成 `262144/262144`
- valid p13 positive/negative 與 cell boundary
- 單一 coordinate 為 0、單一欄位 absent、真實 `0,0`
- altitude absent 不得合成 `0m`
- Router direct unicast 與 PositionModule periodic packet 都覆蓋
- PhoneAPI replay 不得把 time-only 項目偽造成座標

目前 `test/test_position_precision/test_main.cpp:34-93,214-235` 沒有 zero-sentinel/absent regression case。

### 11.3 兩手機/兩節點 E2E

1. fresh install，permission granted、share off：
   - listener 不啟動
   - request 不可改寫 peer 座標為 0.02621
2. share on、first fix：
   - LOC_EXTERNAL、`want_response=false`
   - local radio queue admission success
   - firmware localPosition 更新
3. p13 控制組：
   - remote 顯示應等於計算後的 cell center
   - 不應要求等於完整本機 GPS
4. private p32 控制組：
   - remote 顯示應與手機 fix 在定義容差內一致
5. permission revoke、system location off/on、background/foreground、fixed position、disconnect/reconnect
6. queue full、session rotation、provider exception、無 first fix
7. Debug 與 Release 各做一次；Release 仍須有不洩漏座標的診斷事件

### 11.4 本案驗收條件

- 分享關閉或無 fix 時，任何 request 都不得產生 `0.02621/0.02621`
- 分享開啟時，韌體 localPosition 收到有效且有來源的手機座標
- 每筆 App local-cache 更新都有對應 queue admission 證據
- p13 遠端值符合隱私格網；p32 private 遠端值符合精確座標
- 靜止/移動的 RF 最大陳舊時間符合產品 SLA
- 重連 replay 不會被誤判為 live RF

## 12. 最終責任判定

### 必須改 NTsocial MeshLink

- zero-sentinel 是產生錯誤 payload 的源頭
- per-node consent/狀態 UX 讓「權限已授予」看起來像「分享已啟用」
- phone-position 發送缺少 queue admission 確認
- provider liveness 與部分 protobuf 欄位仍需補強

### 建議也改韌體

- 防禦 legacy explicit-zero request
- 明確保證 optional presence invariant
- 修正 altitude presence
- 增加 live RF / replay provenance

### 不需要重寫的部分

- Android `Position.degI()` 的 `×1e7`
- 韌體對有效 `LOC_EXTERNAL` 的 localPosition ingestion
- 13-bit 隱私演算法本身

### 另須產品決策

- 遠端究竟需要 p13 約略位置，或 private p32 精確位置
- 遠端位置允許的最大陳舊時間
- onboarding 是否要求明確 opt-in，以及 consent 如何跨 node 保存

## 13. 結論

兩支手機原本「沒有傳 GPS」的直接原因是 per-node 分享開關預設關閉；錯誤的 `0.02621` 則是 MeshLink explicit-zero request 與韌體 p13 格網中心化的 deterministic interaction bug。

開啟兩支手機的既有分享 switch 後，已在沒有改 code、沒有重刷韌體、沒有改 radio config 的條件下，完成雙向：

~~~text
手機 GPS → MeshLink → 綁定 Meshtastic → LoRa → 對端 Meshtastic → 對端 MeshLink
~~~

因此正式修復方向是：

1. MeshLink 不再用 `0,0,0` 表示「沒有位置」。
2. MeshLink 等待 radio admission 後才宣告位置送出成功。
3. 韌體保護 time-only/legacy request 不被 precision 製造成假座標。
4. 依隱私需求決定 private p32 channel 與 RF freshness policy。

這四項完成後，才同時解決「根本沒送」、「顯示假座標」、「App 看似成功但 radio 未接收」與「遠端精度不符產品期待」四種不同失敗模式。
