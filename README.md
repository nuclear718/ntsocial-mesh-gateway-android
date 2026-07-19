# NTsocial MeshLink

> [!IMPORTANT]
> **NTsocial MeshLink 目前由 LiberaNt LLC 與 NTsocial 團隊主導開發、整合與維護**，是
> Android [NTsocial App](https://github.com/nuclear718/NTsocial_release) 的核心
> companion app。它仍是完整開源的 GPL-3.0-or-later 專案，並以
> [meshtastic/Meshtastic-Android](https://github.com/meshtastic/Meshtastic-Android)
> 為上游基礎；本 fork 不是 Meshtastic LLC 或 MeshCore 專案的官方發行版。
>
> Gateway v1 已實作，但本機測試過的 Debug APK 不等於 Play-ready release。正式發行仍需
> production upload key、Play Console 權限、簽章互通驗證與完整政策聲明。

## 主導、著作權與開源承諾

這個 repository 自 2026 年起由 **LiberaNt LLC／NTsocial 團隊**負責產品方向、fork 架構、
NTsocial Gateway、母程式互通、品牌整合、測試與長期維護。這項主導身分應在文件、程式檔頭
與發行資訊中清楚可見。

NTsocial MeshLink 特有的原創程式與修改：

**Copyright (c) 2026 LiberaNt LLC.**

開源不表示無作者、無著作權，也不表示公眾領域。GPL 讓任何人可以使用、研究、修改與散布
本專案；準確保留 LiberaNt、NTsocial 團隊、個別貢獻者與必要上游來源，正是開源協作的一部分。
本專案不使用 `All Rights Reserved` 或母程式專有 EULA 來限制 GPL 所授予的自由。

Meshtastic Android 衍生部分仍保留 Meshtastic LLC 與其貢獻者的必要著作權、GPL 與免責聲明；
MeshCore 相容層則保留公開規格與 MIT client 實作的來源致謝。這些上游聲明只適用於各自內容，
不改變本專案目前由 LiberaNt LLC 主導開發與維護的事實。

完整來源、權利邊界與第三方許可文字請見 [NOTICE.md](NOTICE.md)、
[著作權與來源政策](docs/copyright-and-attribution.md) 及
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 專案使命

`NTsocial MeshLink` 在保留 Meshtastic Android 的 radio controller、service、database、
settings 與 KMP 架構相容性的同時，已由 LiberaNt LLC／NTsocial 團隊擴充為 NTsocial 的
開源 LoRa 傳輸底座與 companion app。

這個 App 是 NTsocial App 與 Meshtastic 網路之間的橋樑：

- NTsocial App 負責社交 UI、帳號/profile、Public/Channel/Private、附件、PTT、ATaK
  狀態與 canonical social history。
- 這個 Gateway App 負責 Meshtastic radio 連線、LoRa transport、node policy、封包暫存、
  訊息比對、去重、重組、回補與受保護的 IPC。
- Meshtastic node 負責實際 BLE/Serial/TCP 連線後的 LoRa mesh 收送與轉發。

長期目標不是把封閉的 NTsocial 業務邏輯塞進 GPL App，而是建立一個可信、可審查、
可由社群維護的開源傳輸平台。

## 目前狀態

Gateway v1 已有可執行的程式碼與測試：

- 受保護、版本化的 Provider 快照：`/v1/status`、`/v1/envelopes`、`/v1/nodes`、
  `/v1/channels`。
- 短效、單次 command capability、明確 package-scoped command/event IPC，以及
  package／UID／certificate 驗證。
- `PRIVATE_APP / port 256` 的 NTsocial envelope 傳輸；legacy `497` 僅接收相容。
- 最多 128 筆、程序內的暫時 envelope cache；NTsocial App 仍擁有 canonical history。
- 內建 NTsocial Meshtastic 頻道自動註冊，且不覆寫已設定的 LoRa region／preset。
- 本機 radio queue 與三支 Android 16 手機的 parent-App/no-radio 互通驗證已完成。

尚未完成或尚未證明的項目包括 RF scheduler 擴充、完整 node policy、持久可靠傳輸、
MeshCore transport，以及第二台 radio 的遠端 RF 接收驗證。「command accepted」只代表
本機 radio queue 接受，不代表遠端節點已收到。

Google Play 文件準備稿位於 [`docs/google-play/`](docs/google-play/)。目前沒有已驗證的
Play AAB；正式發布仍需 production upload key、Play Console 發布權限與 Play App Signing
下的跨 App 簽章配對驗證。Android 的 `google` flavor 名稱只保留既有 Play 發布流程相容性；
`google` 與 `fdroid` flavor 都不應包含 Google Cloud、Maps、Firebase、Crashlytics 或
Datadog runtime。QR／條碼則在裝置上以 ZXing 解碼，不需 Google ML Kit 或雲端服務。

如果你只是要安裝官方 Meshtastic Android，請使用 upstream 專案：
[meshtastic/Meshtastic-Android](https://github.com/meshtastic/Meshtastic-Android)。

## 架構方向：雙 App、單一傳輸平台

目前採用的產品形態是「NTsocial App + NTsocial MeshLink App」；下圖同時標示 v1 與
後續傳輸工作：

```text
NTsocial App
  - social UX
  - canonical NTsocial history
  - NT Wire / protobuf payload generation
  - BLE mesh baseline and app-level sync
        |
        | protected Gateway IPC
        v
NTsocial MeshLink
  - Meshtastic radio controller
  - PRIVATE_APP / port 256 transport
  - bounded in-memory cache (v1)
  - matching, chunk, receipt, retry (follow-up)
  - RF lane scheduling by Meshtastic channelIndex (follow-up)
  - NTsocial node policy (follow-up)
        |
        | BLE / Serial / TCP
        v
Meshtastic Node
  - LoRa mesh
```

這個分工讓 NTsocial App 可以保持自己的產品節奏，同時讓 Meshtastic gateway、transport
protocol 與 bridge API 以 GPL-3.0 開源方式被審查與改進。

## Gateway 職責

目前 Gateway v1 與後續路線的責任邊界如下：

- 連線並控制 Meshtastic node，讀取 node info、channel set、config 與 health。
- 以使用者同意為前提套用 NTsocial Node Policy，例如建議 `rebroadcast_mode = ALL`。
- 使用 `PRIVATE_APP / port 256` 收送 NTsocial overlay payload。
- 僅為舊版相容接收 legacy port `497`，新的送出路徑以 `256` 為準。
- 驗證 NTsocial envelope magic，例如 `NM + version + 16-byte headerMsgId + payload`。
- 目前只把通過驗證的 envelope 保存在 bounded in-memory cache；持久 queue、chunk
  session、delivery receipt 與重啟後補交仍是後續工作。
- 完整 transport／overlay／semantic matching、retry 與 reliable delivery 仍是後續工作。
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

## 已完成的 v1 與後續工作

目前 v1 已完成：

1. 保留官方 Meshtastic Android 的 radio/service/database/settings 基礎。
2. 送出與接收 `PRIVATE_APP / port 256` 的完整、經驗證 NTsocial envelope。
3. 保留 legacy `497` receive-only 相容路徑。
4. 使用受保護 Provider、capability command 與 metadata-only event 連接 NTsocial App。
5. 將 envelope 寫入 bounded in-memory cache，並提供受驗證的查詢介面。
6. 自動確保連接節點具有 canonical NTsocial 頻道，同時保留既有 LoRa 設定。

後續階段才加入完整 matching、chunk／receipt／retry、持久 queue、RF lane scheduler、
history compare、Packet Inspector、node policy 與 MeshCore transport。

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

## 著作權、授權與來源

NTsocial MeshLink 的合併作品依
[GNU General Public License v3.0 或後續版本](LICENSE) 散布：

- LiberaNt LLC 對 NTsocial MeshLink 的原創程式與可受保護的修改主張著作權，並以 GPL
  授權社群使用、研究、修改與散布。
- Meshtastic Android 衍生部分保留 Meshtastic LLC 與貢獻者的原宣告；這不是官方
  Meshtastic release。
- MeshCore 相容層是本倉庫建立的 Kotlin 實作，但協定語意與參考 client 的權利仍屬各自
  上游作者；必要 MIT notice 已集中保存。
- 除非另有書面權利移轉，外部貢獻者仍保有其貢獻的著作權。

開放邊界：

- 本 Gateway App、NTsocial transport schema 與 Gateway IPC：在本倉庫依 GPL 開源。
- NTsocial App 的封閉產品／業務邏輯：留在母程式，不進入這個 GPL fork。
- Meshtastic 或 MeshCore 的上游程式：保留各自來源與授權，不以 NTsocial 檔頭覆蓋。
- 私有資產、production credentials、秘密與第三方受限素材：不得進入本倉庫。

詳細規則見 [NOTICE.md](NOTICE.md) 與
[docs/copyright-and-attribution.md](docs/copyright-and-attribution.md)。

## Development setup

Requirements:

- JDK 21
- Android SDK, with `ANDROID_HOME` pointing to the SDK directory
- Git submodules initialized, especially `core/proto/src/main/proto`
- Optional `local.properties` with `sdk.dir=...` when `ANDROID_HOME` is not used

Bootstrap:

```powershell
git submodule update --init
```

Baseline verification before pushing implementation changes:

```powershell
.\gradlew.bat spotlessApply spotlessCheck detekt assembleDebug test allTests
```

On Unix-like shells, use `./gradlew` instead of `.\gradlew.bat`.

## English summary

`NTsocial MeshLink` is led and maintained by **LiberaNt LLC and the NTsocial team** as the core
open-source companion app for Android NTsocial. It is a GPL-3.0-or-later fork of
[Meshtastic-Android](https://github.com/meshtastic/Meshtastic-Android) and provides the transport
gateway between the NTsocial App and Meshtastic radios.

Copyright (c) 2026 LiberaNt LLC for NTsocial MeshLink original work and modifications. Applicable
Meshtastic and MeshCore-origin notices remain preserved in [NOTICE.md](NOTICE.md).

The NTsocial App will own social UX and canonical history. This Gateway will own Meshtastic radio
control, `PRIVATE_APP / port 256` transport, overlay cache, duplicate matching, node policy, RF lane
scheduling, and a protected IPC API for NTsocial.

Gateway v1 now provides protected Provider snapshots, single-use command capabilities, explicit
metadata-only events, port-256 envelope transport, canonical NTsocial channel provisioning, and a
bounded in-memory cache. Reliable persistent delivery, expanded RF scheduling, node policy,
MeshCore transport, and remote RF-reception verification remain follow-up work. No Play-ready AAB
has been validated yet.
