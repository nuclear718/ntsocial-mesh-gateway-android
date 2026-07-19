# MeshCore 獨立整合基礎

NTsocial MeshLink 的第一階段 MeshCore 整合採用「同一個 App、兩套獨立無線電領域」的設計。現有
Meshtastic radio/service/database/settings 行為保持不變；MeshCore 的模型、Companion Radio Protocol、
狀態容器、畫面與導覽都位於新的 `core/meshcore` 與 `feature/meshcore` 模組。

## 相容性依據與授權

此實作在 2026-07-17 以公開 MeshCore 專案、client 與協定文件的下列版本為相容性基準：

- `meshcore-dev/MeshCore`：commit `219812b9f136744c3478908e9487afd0d6031b53`（firmware source 標示
  Companion `v1.16.0`）。
- `meshcore-dev/meshcore.js`：commit `bbe1f9301b801cbd48a053687f16eea9634634cd`。
- `meshcore-dev/meshcore_py`：commit `5bac3573b51c4298062881885b6d15a994109076`。
- 官方 Companion Radio Protocol wiki。

`core/meshcore` 與 `feature/meshcore` 是 LiberaNt LLC／NTsocial 在本倉庫建立的 Kotlin
Multiplatform 相容實作，不是 MeshCore C++ firmware 或官方 client 的鏡像。協定與參考
client 的權利仍屬各自作者；完整 MIT notice 與精確來源保存在
[NOTICE](../NOTICE.md) 及 [THIRD_PARTY_NOTICES](../THIRD_PARTY_NOTICES.md)。這些名稱只表示
相容性，並不表示 MeshCore 開發者對本 App 的背書。

`MeshCoreCompanionProtocol` 依這些來源建立純 Kotlin Multiplatform codec，包含：

- Nordic UART BLE service，以及 App-to-Firmware RX / Firmware-to-App TX characteristic UUID。
- Companion app target protocol version 3 與 176-byte frame 上限。
- 啟動、裝置查詢、contact/channel 同步、訊息同步、電量／儲存空間查詢、時間、廣播名稱、座標、
  LoRa 參數、TX power、direct text 與 channel text 指令。
- self/device/contact/channel/battery/message response 與 push frame 的解析。
- channel `0..7`、32-byte channel name、16-byte secret 與 contact type 的邊界驗證。

## 已完成的第一階段

- 主導覽新增獨立 MeshCore 頂層入口與 `/meshcore` deep link。
- 新增訊息、contacts/channels、radio 三個 MeshCore 專屬 UI 分區，以及獨立對話畫面。
- 新增 MeshCore-only domain model、immutable snapshot 與 StateFlow store，後續 transport 不需接觸
  Meshtastic repository 或 service。
- Android 與 Desktop 共用相同 Compose Multiplatform feature 與 Navigation 3 graph。
- MeshCore 畫面以同工作區 `NTsocial_release` 的 Android UI 為視覺基準，對齊其 NTsocial 色彩語意、
  等寬字體字級／行高、抬升式頁首、列表密度、28dp 身分圖示、狀態卡與非對稱私訊泡泡。這套視覺層只
  位於 `feature/meshcore`，並繼承 MeshLink host color scheme，因此不會改變 Meshtastic 畫面，也不會
  破壞既有 Dynamic Color 與主題選擇流程。
- 畫面清楚標示 transport 尚未連接；不會把範例狀態或本機 queue acceptance 說成 RF delivery。
- channel secret 只存在協定模型，不會顯示、記錄或暴露給 MeshLink Gateway Provider。

## 尚未完成，不能宣稱為實機支援

本階段尚未加入 MeshCore BLE/USB/TCP transport、掃描與連線生命週期、持久化、背景同步、設定寫入、
訊息傳送與第二台 MeshCore radio 的 RF 收發驗證。因此目前完成的是可編譯、可測試、可延伸的獨立 UI 與
協定基礎，不是已完成的 MeshCore 實機通訊。

後續 transport 應只實作一個 MeshCore 專屬 repository，把已解析的 snapshot 餵給
`MeshCoreStateStore.replaceSnapshot()`；不可重用或修改 `IMeshService`、Meshtastic BLE service、
Meshtastic database 或既有 Gateway IPC。等 MeshCore transport 與資料契約穩定後，再另行設計提供給
NTsocial 母程式的版本化 IPC，而不是把 MeshCore frame 混進目前的 Meshtastic Gateway v1。

## English summary

Phase 1 adds a separate MeshCore KMP domain, Companion Protocol codec, Navigation 3 graph, and dedicated
messages/contacts/channels/radio UI. Its feature-local visual layer mirrors the Android NTsocial typography, header,
list, status-card, and direct-message patterns while preserving the host theme contract. It deliberately does not claim
hardware connectivity yet. The next phase must add a MeshCore-owned transport and repository without modifying
Meshtastic service, database, settings, or Gateway v1.
