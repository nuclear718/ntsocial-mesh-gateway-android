# NTsocial Meshtastic Gateway for Android

> [!IMPORTANT]
> This repository is an early fork of
> [meshtastic/Meshtastic-Android](https://github.com/meshtastic/Meshtastic-Android).
> The NTsocial gateway features described here are the project direction and are not
> implemented yet. This fork is not an official Meshtastic project unless that status
> changes in the future.

## 專案使命

`NTsocial Meshtastic Gateway for Android` 的目標，是把官方 Meshtastic Android App 的
radio controller、service、database、settings 與 KMP 架構保留下來，並在其上打造一個
開源的 NTsocial LoRa 傳輸底座。

這個 App 會成為 NTsocial App 與 Meshtastic 網路之間的橋樑：

- NTsocial App 負責社交 UI、帳號/profile、Public/Channel/Private、附件、PTT、ATaK
  狀態與 canonical social history。
- 這個 Gateway App 負責 Meshtastic radio 連線、LoRa transport、node policy、封包暫存、
  訊息比對、去重、重組、回補與受保護的 IPC。
- Meshtastic node 負責實際 BLE/Serial/TCP 連線後的 LoRa mesh 收送與轉發。

長期目標不是把封閉的 NTsocial 業務邏輯塞進 GPL App，而是建立一個可信、可審查、
可由社群維護的開源傳輸平台。

## 目前狀態

這個 repo 目前仍接近剛 fork 下來的官方 Meshtastic Android 專案：

- 主要 UI、package naming、build flavors 與多數文件仍來自 upstream。
- NTsocial protocol、cache、bridge、scheduler 與 Gateway UI 尚未實作。
- 尚未提供 NTsocial Gateway 的正式 APK、F-Droid、Play Store 或 release channel。
- 根 README 先改成 fork 的真實身份與路線圖，避免讀者誤以為這裡仍是官方 release 入口。

如果你只是要安裝官方 Meshtastic Android，請使用 upstream 專案：
[meshtastic/Meshtastic-Android](https://github.com/meshtastic/Meshtastic-Android)。

## 架構方向：雙 App、單一傳輸平台

計畫中的產品形態是「NTsocial App + NTsocial Meshtastic Gateway App」：

```text
NTsocial App
  - social UX
  - canonical NTsocial history
  - NT Wire / protobuf payload generation
  - BLE mesh baseline and app-level sync
        |
        | protected Gateway IPC
        v
NTsocial Meshtastic Gateway for Android
  - Meshtastic radio controller
  - PRIVATE_APP / port 256 transport
  - cache, matching, chunk, receipt, retry
  - RF lane scheduling by Meshtastic channelIndex
  - NTsocial node policy
        |
        | BLE / Serial / TCP
        v
Meshtastic Node
  - LoRa mesh
```

這個分工讓 NTsocial App 可以保持自己的產品節奏，同時讓 Meshtastic gateway、transport
protocol 與 bridge API 以 GPL-3.0 開源方式被審查與改進。

## Gateway 職責

第一階段會以可靠傳輸為主，不先追求完整 UI 美化。Gateway 最終應負責：

- 連線並控制 Meshtastic node，讀取 node info、channel set、config 與 health。
- 以使用者同意為前提套用 NTsocial Node Policy，例如建議 `rebroadcast_mode = ALL`。
- 使用 `PRIVATE_APP / port 256` 收送 NTsocial overlay payload。
- 僅為舊版相容接收 legacy port `497`，新的送出路徑以 `256` 為準。
- 驗證 NTsocial envelope magic，例如 `NM + version + 16-byte headerMsgId + payload`。
- 將 raw packet、envelope、outbound queue、chunk session 與 delivery receipt 存進專用 cache。
- 以 transport key、overlay key、semantic key 做 duplicate detection 與 conflict detection。
- 讓 NTsocial App 在背景被系統暫停或重啟後，可以查詢 Gateway cache 補交缺失訊息。
- 以 Meshtastic `channelIndex` 作為 RF lane key，讓多個 NTsocial `channelId` 可以合法共用同一條 LoRa lane。
- 提供受權限保護的 Gateway IPC，而不是長期依賴官方 deprecated `IMeshService` AIDL。

## 不會做的事

這個 fork 不會把所有 NTsocial App 功能直接合併成單一 APK。

明確不在 Gateway 職責內的項目：

- 不保存 NTsocial canonical social history。canonical history 仍由 NTsocial App 管理。
- 不把圖片、語音、PTT media bytes 直接塞進 LoRa。
- 不在未經使用者同意時偷偷改 Meshtastic node config。
- 不把 NTsocial 的封閉業務邏輯放進 GPL-3.0 fork。
- 不把官方 Meshtastic AIDL 視為長期核心 API。它可作過渡參考，但此 fork 會設計自己的 Gateway bridge。

## MVP 路線圖

第一個可驗收版本只追求「NTsocial 可以透過新 Gateway 用 Meshtastic 收送文字」：

1. 保留官方 Meshtastic Android 的 radio/service/database/settings 基礎。
2. 新增 NTsocial Gateway Dashboard 的最小入口。
3. 顯示 connected radio、local node id、channel set 與目前 rebroadcast mode。
4. 讓使用者手動套用 NTsocial Node Policy，將 preferred rebroadcast mode 設為 `ALL`。
5. 支援送出 `PRIVATE_APP / port 256` 測試 payload。
6. 支援接收 `PRIVATE_APP / port 256` payload。
7. 辨識 NTsocial envelope magic，非 NTsocial payload 不污染 Gateway cache。
8. 將 raw packet 與 envelope 寫入 NTsocial overlay cache。
9. 提供簡化版 Gateway IPC，讓 NTsocial App 可以收 envelope 與查 cache。
10. 保留 legacy `497` receive-only 相容路徑。

後續階段才加入完整 message matching、chunk/receipt/retry、RF lane scheduler、history compare、
Packet Inspector 與更完整的 Gateway UI。

## 技術基礎

這個 fork 繼承 Meshtastic Android 的現代 Android/KMP 架構：

- Kotlin Multiplatform core modules
- JetBrains Compose Multiplatform + Material 3
- Navigation 3
- Koin annotations
- Room KMP
- DataStore
- Protobuf
- Repository pattern
- BLE/TCP/Serial radio transport abstraction
- Existing Meshtastic `DataPacket` and `RadioController.sendMessage(...)` path
- Existing radio config write path through `RadioConfigUseCase.setConfig(...)`

新 NTsocial 功能會優先放在清楚分層的模組中，例如 protocol、cache、transport、bridge 與 feature UI。
共同邏輯應放在 `commonMain`，Android-only IPC 或 service binding 才放在 Android source set。

## License and openness

This project remains licensed under the
[GNU General Public License v3.0](LICENSE), following the upstream Meshtastic Android license.

Planned openness boundary:

- This Gateway App: open source under GPL-3.0.
- NTsocial transport protocol/schema: intended to be open.
- NTsocial Gateway IPC contract: intended to be open.
- NTsocial App product/business logic: stays outside this GPL fork.
- Meshtastic firmware changes, if needed for NTsocial node profiles: should remain open source.

Upstream attribution matters. This project stands on the work of the Meshtastic Android contributors.
Fork-specific changes should remain clearly marked so bugs are not attributed to upstream maintainers by mistake.

## Development setup

Requirements:

- JDK 21
- Android SDK, with `ANDROID_HOME` pointing to the SDK directory
- Git submodules initialized, especially `core/proto/src/main/proto`
- `local.properties` initialized from `secrets.defaults.properties` for local builds

Bootstrap:

```powershell
git submodule update --init
if (-not (Test-Path local.properties)) {
    Copy-Item secrets.defaults.properties local.properties
}
```

Baseline verification before pushing implementation changes:

```powershell
.\gradlew.bat spotlessApply spotlessCheck detekt assembleDebug test allTests
```

On Unix-like shells, use `./gradlew` instead of `.\gradlew.bat`.

## English summary

`NTsocial Meshtastic Gateway for Android` is an early GPL-3.0 fork of
[Meshtastic-Android](https://github.com/meshtastic/Meshtastic-Android). Its goal is to become an
open-source transport gateway between the NTsocial App and Meshtastic radios.

The NTsocial App will own social UX and canonical history. This Gateway will own Meshtastic radio
control, `PRIVATE_APP / port 256` transport, overlay cache, duplicate matching, node policy, RF lane
scheduling, and a protected IPC API for NTsocial.

The gateway features are not implemented yet. This README defines the fork identity and development
direction before the first NTsocial-specific modules are added.
