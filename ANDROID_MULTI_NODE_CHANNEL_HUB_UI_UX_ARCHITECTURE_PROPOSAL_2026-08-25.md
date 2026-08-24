# Android NTsocial MeshLink 多節點 Channel Hub UI／UX 與程式架構改造提案

- 報告日期：2026-08-25（Asia/Taipei）
- 目標專案：`nuclear718/ntsocial-mesh-gateway-android`
- 目標分支：`multi_nodes_`
- App 稽核基準：`multi_nodes_@2c62c6bc0facd50abd72b69f77601a0b32aa6435`
- DIY 節點稽核基準：`nuclear718/NTsocial-with-Meshtastic-@76219cd7562f76d1543f12944efc4379f788a233`
- 報告性質：架構與施工提案；本次只新增本文件，**未修改正式功能程式碼，也未執行本文範例的編譯或實機驗證**
- 第一階段範圍：Android 手機同時管理最多四個 Meshtastic BLE 節點，建立多節點頻道聚合首頁與每節點獨立頁面
- 後續相容方向：MeshCore 與 NTsocial 自訂無線電協議透過 protocol adapter 接入同一個 Channel Hub

---

## 1. 執行摘要

### 1.1 結論

這個需求在技術上可行，而且目前 `multi_nodes_` 分支已完成最困難的第一層基礎：最多四個 Meshtastic endpoint、每個次要 endpoint 各自擁有獨立 Room database、DataStore、Koin scope、service／repository／packet graph、BLE connection ownership 與 session generation。這表示目前不必推翻多節點底層，也不應重新複製四套 App。

真正尚未完成的是第二層：**跨 endpoint 的唯讀資料投影、明確的 endpoint-bound 導覽，以及以一般通訊產品為目標的 Channel Hub UI。**

現行 UI 雖然顯示多個節點分頁，但其本質仍是：

1. 全 App 只有一個 `selectedEndpointId`。
2. `Main.kt` 一次只把一個 endpoint 的 Koin scope 注入整棵導覽內容。
3. 現有 Conversations／Contacts 頁一次只能讀取一個 endpoint 的 `PacketRepository`、`RadioConfigRepository` 與 `NodeRepository`。
4. 頻道 key 仍是 `0^all`、`1^all` 這類只在單一 endpoint database 內唯一的字串。
5. 開啟訊息頁面的 route 只攜帶 `contactKey`，沒有攜帶 `endpointId`。

因此，現況是「四個獨立 backend session + 一次顯示其中一個」，還不是「多節點訊息收發整合 App」。

### 1.2 本提案的核心設計

第一個底部主分頁應由目前概念上的 Conversations 改造成 **Channel Hub（頻道中心）**，並提供：

- 第一個區域分頁固定為「全部」。
- 後方最多四個節點專屬分頁。
- 預設開啟「全部」。
- 「全部」頁以單一垂直清單呈現所有已登錄節點與所有已設定的 Primary／Secondary Channel。
- 每個節點使用一張節點群組色卡包覆其頻道，不要求使用者先點選節點。
- 每張節點卡使用可自訂的低彩度色調、清楚的節點名稱、位址末四碼、連線狀態與 protocol badge。
- 不以顏色作為唯一辨識方式；即使色盲、深色模式或低亮度環境，仍可由文字、圖示、排列與狀態辨認。
- 點擊任何頻道時，route 必須攜帶 `endpointId + nativeContactKey + expectedGeneration`，再進入該 endpoint 的精確 Koin scope。
- 離線節點仍顯示已快取的頻道與最後訊息，但標示「離線・唯讀」，禁止誤送。

### 1.3 最優先的工程問題

| 優先級 | 問題 | 為何是必要條件 | 建議處理 |
|---|---|---|---|
| P0 | 訊息 route 沒有 `endpointId` | 同一個 `0^all` 可同時存在於四個節點；只靠目前選取節點可能開錯 database 或由錯誤 radio 發送 | 將 endpoint identity 納入 route、ViewModel key、send command 與 generation guard |
| P0 | 沒有跨 scope 的 Channel projection | 一個 Composable 目前只能安全注入一個 endpoint scope，無法同時顯示四台 radio | 建立 root-owned projection registry 與 `FleetChannelHubRepository` |
| P0 | 不能直接把四個 `PagingData` 合併 | 分頁載入、排序、失效與 ViewModel 生命週期會變得不可控 | 「全部」頁只聚合數量有限的 configured broadcast channels；每節點頁保留原本的私訊 Paging |
| P0 | `/channels` nested deep link 已知會破壞 `MultiBackstack` | 新 Channel Hub route 若建在有缺陷的基礎上，會擴大可重現 crash 面積 | 先移植既有報告所列的 `MultiBackstack.handleDeepLink()` 修正與測試 |
| P1 | endpoint profile 沒有外觀資料，舊格式又固定八欄 | 直接增加色彩欄位會使舊 profile 全部 decode 失敗 | 建立向後相容的 v1 → v2 versioned migration |
| P1 | 現行節點分頁只有位址末四碼 | 資訊架構不足、無狀態、無未讀、無使用者命名，像工程除錯工具 | 改成「全部 + 節點名稱 + 色點 + 狀態 + 未讀」的 Channel Hub selector |
| P1 | UI 使用大量彼此分離的 outlined cards | 視覺層級鬆散，無法快速看出頻道屬於哪一台 radio | 使用 node group card、內部 channel rows、低彩度背景與 accent rail |
| P1 | 現有持續 BLE 掃描有高 CPU／持續 frame 問題 | 新 UI 若再加入永續動畫，會放大耗電問題 | Channel Hub 不使用無限脈衝動畫；先修正已知掃描 lifecycle 問題並建立效能基準 |

---

## 2. 名詞與範圍澄清

### 2.1 「第一個 Channel 主分頁」與程式中的 `ChannelsRoute` 不是同一件事

目前底部第一個 top-level destination 在程式中叫：

```kotlin
TopLevelDestination.Conversations(
    Res.string.conversations,
    ContactsRoute.ContactsGraph,
)
```

而 `ChannelsRoute.ChannelsGraph` 是 Settings 內用來編輯／分享 Meshtastic channel configuration 的 nested graph。兩者用途不同：

- **Channel Hub**：日常查看、收訊、進入頻道對話、管理未讀。
- **Channel configuration**：修改 PSK、LoRa configuration、匯入／分享 QR、套用設定到 radio。

本提案不會把兩者合併。UI 文案可以把第一個底部分頁改名為「頻道」，但程式內應新增清楚的 `ChannelHub` domain，而不是繼續讓 `Contacts`、`Channels` 兩個名稱互相混淆。

### 2.2 Endpoint、節點與 Radio

本文件使用：

- `endpoint`：App 內持久化、具有 `RadioEndpointId` 的連線與資料隔離單位。
- 節點：使用者看到的實體無線電裝置。
- radio session：一個 endpoint 當前的連線、同步與 generation 生命週期。
- native channel identity：各 protocol 原生頻道身分。Meshtastic 第一階段可用 `meshtastic:<slotIndex>`。
- `contactKey`：目前 Meshtastic message database 的 endpoint-local key，例如 `0^all`。

### 2.3 第一版刻意不做的事情

- 不把四個 endpoint 的 Room database 合併成一個 global database。
- 不在「全部」頁合併所有私訊 Paging stream。
- 不自動把 Meshtastic 訊息橋接到 MeshCore。
- 不讓同一則訊息同時由多台 radio 發送。
- 不以 BLE address 作為公開 route identity。
- 不在 UI layer 解析不同 protocol 的 wire packet。
- 不在第一版提供任意 RGB 色彩；先提供經過亮／暗模式與對比檢查的色調 token。

---

## 3. 稽核基準與現況證據

### 3.1 DIY 節點

`NTsocial-with-Meshtastic-@76219cd` 的最新版韌體為 Meshtastic `2.8.0.b10d31e`，硬體目標為 `nrf52_promicro_diy_xtal`，採用 nice!nano／nRF52840 搭配 RFM95W／SX1276。對 Android App 而言，它仍透過標準 Meshtastic Device API 與 BLE service 互動，因此 Channel Hub 不需要辨識「DIY 板」或建立硬體專屬 UI。

這對架構有兩個意義：

1. `endpointId` 必須是 App 自己的持久化身分，不能依賴裝置名稱。
2. 未來即使同一硬體刷入 MeshCore 或自訂協議，也應由 `RadioProtocol + protocol adapter` 決定資料投影，而不是由外觀或板型決定。

目前 README 記錄固定 BLE PIN。多節點產品化時，建議把「首次登錄節點」與「之後自動重連」分開處理，並在首次登錄畫面顯示穩定 radio identity、裝置名稱、位址末四碼與使用者確認，避免四台外觀相似的節點被錯誤命名。這不阻擋本次 UI 開發，但應列入後續安全工作。

### 3.2 多節點底層已完成的正確基礎

`core:radio-fleet` 已具備：

- `MAX_RADIO_ENDPOINTS = 4`。
- 不透明的 `RadioEndpointId`。
- durable `RadioEndpointProfile`。
- 每個 endpoint 獨立的 `EndpointSessionState` 與 `generation`。
- endpoint catalog、註冊、選取、連線、斷線、移除。
- address-keyed BLE ownership。
- legacy primary 與 secondary session 的分離。

Android secondary endpoint 建立時，會開啟獨立 database、DataStore 與 Koin scope，並解析該 scope 內自己的：

- `NodeRepository`
- `PacketRepository`
- `RadioConfigRepository`
- `ServiceRepository`
- `RadioInterfaceService`
- `SendMessageUseCase`
- 其他 packet／config／service graph

這個設計應保留。Channel Hub 的工作不是把這些物件變成 singleton，而是建立**每個 scope 各自輸出一份唯讀 projection，再由 root 聚合**。

### 3.3 現行 UI 的結構性限制

#### 3.3.1 全 App 共用一個 endpoint selection

`Main.kt` 目前收集：

```kotlin
val selectedEndpointId by fleetManager.selectedEndpointId.collectAsStateWithLifecycle()
```

所有 endpoint-aware top-level features 再由 `ScopedRadioNavigation()` 注入這一個 selected endpoint 的 scope。這對 Nodes、Settings 與 Firmware 是合理的，因為使用者一次只會管理一台 radio；但對 Channel Hub 不合理，因為 Channel Hub 的產品要求正是同時讀取所有節點。

#### 3.3.2 現行頂部分頁不是聚合頁

目前 `RadioEndpointTabs()`：

- 只有節點 BLE address 末四碼。
- 沒有「全部」。
- 沒有節點名稱、顏色、狀態或未讀。
- 選取後會改變全 App 的 `selectedEndpointId`。
- 切換 endpoint 時會把目前 feature 重設到 root。

這適合作為第一階段工程驗證，但不適合作為通訊產品的主要資訊架構。

#### 3.3.3 頻道與私訊目前混在 `ContactsScreen`

`ContactsViewModel` 使用：

```kotlin
val contactKey = "$ch${DataPacket.ID_BROADCAST}"
```

建立 broadcast channel placeholder，並將它和 private contact 一起放入 Contacts list。這在單一 endpoint 內可運作；跨 endpoint 後，Radio A 與 Radio B 都會有 `0^all`，因此單一字串不再是全域唯一識別。

#### 3.3.4 訊息 route 沒有 endpoint identity

目前 route：

```kotlin
@Serializable
data class Messages(
    val contactKey: String,
    val message: String = "",
) : ContactsRoute
```

`MessageViewModel` 的 endpoint 是由當下 Compose Koin scope 隱含決定。這會形成危險競態：

1. 使用者在全節點頁點擊 Radio B 的 `0^all`。
2. 全域 selected endpoint 尚未完成切換，或 process restore 後落到 Radio A。
3. route 只帶 `0^all`。
4. Message ViewModel 可能從 Radio A database 讀取或由 Radio A 發送。

因此 endpoint 必須成為 route 的明確資料，而不是依賴 UI 當下狀態推測。

#### 3.3.5 Endpoint profile 尚無外觀欄位

目前 `RadioEndpointProfile` 只有連線與排序資料，沒有 `accentToken`、icon 或外觀版本。更重要的是，`DataStoreRadioEndpointStore` 使用固定八欄的 pipe-separated 字串，而且：

```kotlin
if (fields.size != PROFILE_FIELD_COUNT) return null
```

若直接增加第九欄，舊 profile 會全部無法 decode。這是 migration 問題，不是單純新增 UI 欄位。

#### 3.3.6 `RadioEndpointSnapshot` 尚未形成產品級摘要

`RadioEndpointSnapshot` 已宣告：

```kotlin
val primaryChannelName: String? = null
val lastReceivedAtMillis: Long? = null
```

但 `DefaultRadioFleetManager` 目前只從 session 收集 state 與 generation，並未填入這兩個欄位。建議不要繼續把大量 Channel Hub 資料塞進 session snapshot；session snapshot 應保持輕量，頻道與未讀由專用 projection 負責。

### 3.4 已知 release blocker 必須先處理

2026-08-24 實機報告已確認 `/channels` nested deep link 會先把 `currentTabRoute` 改成不存在的 nested graph，再於下一次讀取 `activeBackStack` 時 crash。新增 Channel Hub route 前，必須先修正 `MultiBackstack.handleDeepLink()`：

- 先確認可用 top-level stack。
- 只有 first key 是 top-level destination 時才切換 tab。
- nested path 應加入目前有效 stack，而不是把 nested graph 當 top-level route。
- 所有 invariant 驗證完成後，才能修改 `currentTabRoute`。

---

## 4. 建議的產品資訊架構

### 4.1 底部主導覽

建議保留五個 top-level destinations，但第一個顯示名稱改為「頻道」或「訊息」；本文件採「頻道」。

```text
[ 頻道 ] [ 節點 ] [ MeshCore* ] [ 設定 ] [ 連線 ]
```

`MeshCore*` 在真正整合前可維持既有功能；未來若所有平台都進入統一 Channel Hub，是否保留獨立 MeshCore top-level tab可再評估，不應在本階段先刪除。

### 4.2 Channel Hub 內部 selector

```text
┌──────────────────────────────────────────────┐
│ 頻道                                  搜尋  ⋮ │
│                                              │
│ [ 全部 12 ] [ 山搜中繼 3 ] [ 基地台 4 ] ... │
└──────────────────────────────────────────────┘
```

Selector 規則：

- 「全部」永遠第一個。
- 節點依 `legacyPrimary desc, priority desc, displayName` 排序。
- 節點 tab 顯示短名稱；空間允許時顯示位址末四碼。
- 顯示與節點 accent 相同的色點，但同時保留名稱與狀態 icon。
- 未讀數可顯示 badge。
- 最多五個 tab，使用 scrollable tab row；不應壓縮成難以點擊的小欄位。
- selector 是 Channel Hub 的 local selection，不應直接等同全 App 的 `selectedEndpointId`。

### 4.3 「全部」頁線框

```text
┌──────────────────────────────────────────────┐
│ ▌ 山搜中繼站                         ● 已連線 │
│   Meshtastic · A91C              未讀 3   ⋮  │
│                                              │
│   PRIMARY   山搜指揮                 2  07:31 │
│             隊員 12：已抵達稜線             │
│   SECONDARY 醫療協調                    07:18 │
│             基地：待命                        │
│   SECONDARY 後勤補給                         │
└──────────────────────────────────────────────┘

┌──────────────────────────────────────────────┐
│ ▌ 北側基地台                         ○ 離線   │
│   Meshtastic · 77B2              快取資料  ⋮ │
│                                              │
│   PRIMARY   北側搜救                  1 昨天  │
│             北搜 3：返回營地                 │
│   SECONDARY 公共頻道                         │
└──────────────────────────────────────────────┘
```

設計原則：

- 一個 endpoint 一張群組卡。
- 頻道列位於同一張卡內，不再讓每一列各自成為厚重 outlined card。
- 群組卡預設全部展開，使用者進入後只需垂直滑動即可看到所有節點／所有頻道。
- 可以提供收合按鈕，但預設不得收合；第一版不必持久化收合狀態。
- 色彩只使用於低彩度 container、左側 accent rail、色點與少量 badge。
- 不能把整張卡塗成高飽和色，否則深色模式、戶外強光與長時間閱讀都會疲勞。

### 4.4 每節點專屬頁

每個節點 tab 使用相同的 channel row component，避免「全部」與「節點」兩套視覺語言。建議內容順序：

1. Endpoint compact header：名稱、protocol、位址末四碼、連線狀態、快速連線／斷線。
2. Primary／Secondary channels。
3. 私人訊息區段，沿用該 endpoint 的 `PagingData<Contact>`。
4. 可選的「只顯示未讀」filter。

第一版的「全部」頁不必顯示所有私訊；這不是功能退讓，而是避免錯誤地合併四條無界 Paging stream。之後可新增每個 endpoint 最近 3～5 個私訊的 bounded projection，但不應在第一版就混入。

### 4.5 連線狀態

| 狀態 | 群組卡呈現 | 頻道可開啟 | 可發送 |
|---|---|---:|---:|
| `Ready` | 綠色狀態點＋「已連線」 | 是 | 是，且檢查 generation |
| `Connecting` | 靜態進度 icon＋「連線中」 | 可讀快取 | 否 |
| `Synchronizing` | 「同步頻道與節點資料」 | 可讀快取 | 否 |
| `Registered` | 「離線・快取資料」 | 是 | 否 |
| `Degraded` | 黃色警示＋簡短原因 | 是 | 依 fail-closed 原則為否 |
| `Failed` | 紅色警示＋「重新連線」 | 若有快取則是 | 否 |
| projection missing | 「資料來源尚未就緒」 | 否 | 否 |

不建議使用永遠循環的 shimmer 或 pulse。只有狀態切換的一次性 transition 可以動畫化。

### 4.6 節點色調自訂

第一版提供 12 個預先驗證的色調 token：

- Indigo
- Emerald
- Amber
- Cyan
- Blue
- Violet
- Rose
- Lime
- Teal
- Orange
- Slate
- Fuchsia

建議預設前四台分別使用 Indigo、Emerald、Amber、Cyan；若使用者更換，應立即預覽並持久化。

色彩規則：

- DataStore 只保存 token，不保存衍生後的 light／dark ARGB。
- `core:ui` 依目前 theme 將 token 解析為 container、onContainer、accent 與 outline。
- 文字與 container 應維持至少 4.5:1 對比；非文字辨識元素至少 3:1。
- 節點辨識同時使用 `displayName + addressSuffix + protocol + icon`。
- 觸控目標至少 48dp。
- 允許使用者在 Connections endpoint card 的「外觀」或 Channel Hub 群組卡 overflow menu 進入選色。
- 第一版不開放任意 RGB，避免使用者選出在深色模式不可讀的組合。後續若加入 hue slider，必須由 tonal palette generator 自動產生可讀文字色，而不是直接使用 raw color。

建議的亮色 container／文字組合：

| Token | Container | On-container |
|---|---|---|
| Indigo | `#E0E7FF` | `#1E1B4B` |
| Emerald | `#D1FAE5` | `#064E3B` |
| Amber | `#FEF3C7` | `#78350F` |
| Cyan | `#CFFAFE` | `#164E63` |
| Blue | `#DBEAFE` | `#1E3A8A` |
| Violet | `#EDE9FE` | `#4C1D95` |
| Rose | `#FFE4E6` | `#881337` |
| Lime | `#ECFCCB` | `#365314` |
| Teal | `#CCFBF1` | `#134E4A` |
| Orange | `#FFEDD5` | `#7C2D12` |
| Slate | `#E2E8F0` | `#1E293B` |
| Fuchsia | `#FAE8FF` | `#701A75` |

---

## 5. 目標架構

### 5.1 分層

```text
┌─────────────────────────────────────────────────────────────┐
│ feature:messaging / Channel Hub UI                          │
│ ChannelHubScreen → ChannelHubViewModel                      │
└──────────────────────────────┬──────────────────────────────┘
                               │ protocol-neutral models
┌──────────────────────────────▼──────────────────────────────┐
│ core:channel-hub                                             │
│ FleetChannelHubRepository                                   │
│ EndpointChannelProjection                                   │
│ EndpointChannelGroup / EndpointChannelSummary               │
└──────────────────────────────┬──────────────────────────────┘
                               │ registered projections
┌──────────────────────────────▼──────────────────────────────┐
│ Android root implementation                                 │
│ EndpointChannelProjectionRegistry                           │
│ DefaultFleetChannelHubRepository                            │
└───────────────┬───────────────────────┬─────────────────────┘
                │                       │
┌───────────────▼────────────┐  ┌──────▼──────────────────────┐
│ legacy-primary root graph │  │ secondary endpoint Koin     │
│ scoped Meshtastic         │  │ scoped Meshtastic           │
│ projection                │  │ projection                  │
└───────────────┬────────────┘  └──────┬──────────────────────┘
                │                      │
       RadioConfigRepository  PacketRepository
       ServiceRepository      endpoint-local Room database
```

### 5.2 為何新增 `core:channel-hub`

不建議把所有聚合模型放進 `core:radio-fleet`：

- `radio-fleet` 應專注 endpoint identity、catalog、session lifecycle 與 generation。
- Channel Hub 是訊息／頻道的 read model，不應讓 fleet manager 依賴 Meshtastic `ChannelSet`、Paging 或 UI component。
- 未來 MeshCore 與自訂協議可實作相同 projection contract，而不用修改 `RadioFleetManager`。

建議新增：

```text
core/channel-hub/
  build.gradle.kts
  src/commonMain/kotlin/com/ntsocial/meshlink/core/channelhub/
    ChannelHubModels.kt
    EndpointChannelProjection.kt
    FleetChannelHubRepository.kt
```

其 public API 不應暴露：

- Koin `Scope`
- Android `Context`
- Meshtastic protobuf type
- Room DAO
- BLE address

### 5.3 資料流

```text
每個 endpoint 的 ChannelSet + Contacts + Unread + ConnectionState
        │
        ▼
MeshtasticEndpointChannelProjection
        │  bounded StateFlow<List<EndpointChannelSummary>>
        ▼
EndpointChannelProjectionRegistry
        │  Map<RadioEndpointId, RegisteredProjection>
        ▼
DefaultFleetChannelHubRepository
        │  merge fleet snapshots + profiles + projections
        ▼
ChannelHubUiState
        │
        ├─ selection = All
        │    └─ 所有 endpoint groups
        └─ selection = Endpoint(id)
             └─ 單 endpoint channels + 原有 private-message paging
```

### 5.4 「聚合」不是 database join

Channel Hub 只在記憶體中聚合每個 endpoint 已經整理好的 bounded summary。它不跨 database 執行 SQL join，也不把訊息複製到 global table。

這樣的好處：

- 延續現有 endpoint database 隔離。
- 刪除一台 endpoint 不會破壞其他資料。
- 同一 packet ID、contactKey 或 channel index 不會碰撞。
- App 重啟後，各 endpoint 仍能從自己的 Room／DataStore 恢復。
- 未來 protocol adapter 可有不同 storage implementation。

---

## 6. Protocol-neutral domain model

以下程式碼是具體施工範本；實作時需依專案 formatter、Koin annotation 與 source-set 編譯結果微調。

### 6.1 新增 `ChannelHubModels.kt`

檔案：

```text
core/channel-hub/src/commonMain/kotlin/
  com/ntsocial/meshlink/core/channelhub/ChannelHubModels.kt
```

```kotlin
package com.ntsocial.meshlink.core.channelhub

import com.ntsocial.meshlink.core.radiofleet.EndpointSessionState
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.RadioProtocol

@JvmInline
value class NativeChannelId(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

data class EndpointChannelKey(
    val endpointId: RadioEndpointId,
    val protocol: RadioProtocol,
    val nativeChannelId: NativeChannelId,
    /** Endpoint-local key used by the protocol implementation. */
    val nativeContactKey: String,
)

enum class ChannelRole {
    PRIMARY,
    SECONDARY,
}

data class EndpointChannelSummary(
    val key: EndpointChannelKey,
    val index: Int,
    val displayName: String,
    val role: ChannelRole,
    val isEncrypted: Boolean,
    val lastMessageText: String?,
    val lastMessageAtMillis: Long?,
    val unreadCount: Int,
    val isMuted: Boolean,
)

data class EndpointChannelGroup(
    val endpointId: RadioEndpointId,
    val protocol: RadioProtocol,
    val displayName: String,
    val addressSuffix: String,
    val accentToken: NodeAccentToken,
    val sessionState: EndpointSessionState,
    val generation: Long,
    val channels: List<EndpointChannelSummary>,
    val dataAvailable: Boolean,
) {
    val unreadCount: Int
        get() = channels.sumOf(EndpointChannelSummary::unreadCount)

    val canSend: Boolean
        get() = sessionState is EndpointSessionState.Ready
}

sealed interface ChannelHubSelection {
    data object All : ChannelHubSelection

    data class Endpoint(val endpointId: RadioEndpointId) : ChannelHubSelection
}

data class ChannelHubUiState(
    val selection: ChannelHubSelection = ChannelHubSelection.All,
    val groups: List<EndpointChannelGroup> = emptyList(),
    val loading: Boolean = true,
)
```

重點：

- `EndpointChannelKey` 把 endpoint 與 native channel identity 綁在一起。
- Meshtastic 的 `nativeChannelId` 第一版可為 `meshtastic:0`、`meshtastic:1`。
- `nativeContactKey` 仍保留現有 repository 所需的 `0^all`，但它不再被誤認為全域 key。
- `generation` 進入 group；發送前必須確認仍是同一 generation。
- UI 不需要知道 Meshtastic `ChannelSet`。

### 6.2 新增 projection contract

檔案：

```text
core/channel-hub/src/commonMain/kotlin/
  com/ntsocial/meshlink/core/channelhub/EndpointChannelProjection.kt
```

```kotlin
package com.ntsocial.meshlink.core.channelhub

import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import kotlinx.coroutines.flow.StateFlow

interface EndpointChannelProjection {
    val endpointId: RadioEndpointId

    /** Bounded list: only configured broadcast channels for this endpoint. */
    val channels: StateFlow<List<EndpointChannelSummary>>
}

interface FleetChannelHubRepository {
    val groups: StateFlow<List<EndpointChannelGroup>>
}
```

這個 contract 刻意不包含「send」。讀取投影與發送命令應分離，避免 UI 因為能顯示快取資料就誤以為一定可傳送。

---

## 7. Endpoint projection 與 registry

### 7.1 新增 root registry

檔案：

```text
app/src/main/kotlin/com/ntsocial/meshlink/app/radio/
  EndpointChannelProjectionRegistry.kt
```

```kotlin
package com.ntsocial.meshlink.app.radio

import com.ntsocial.meshlink.core.channelhub.EndpointChannelProjection
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class RegisteredChannelProjection(
    val runtimeToken: String,
    val projection: EndpointChannelProjection,
)

class EndpointChannelProjectionRegistry {
    private val mutableEntries =
        MutableStateFlow<Map<RadioEndpointId, RegisteredChannelProjection>>(emptyMap())

    val entries: StateFlow<Map<RadioEndpointId, RegisteredChannelProjection>> =
        mutableEntries.asStateFlow()

    fun register(
        endpointId: RadioEndpointId,
        runtimeToken: String,
        projection: EndpointChannelProjection,
    ) {
        require(projection.endpointId == endpointId)
        mutableEntries.update { current ->
            current + (endpointId to RegisteredChannelProjection(runtimeToken, projection))
        }
    }

    fun unregister(endpointId: RadioEndpointId, runtimeToken: String) {
        mutableEntries.update { current ->
            val existing = current[endpointId]
            if (existing?.runtimeToken == runtimeToken) current - endpointId else current
        }
    }
}
```

`runtimeToken` 是必要的。原因是：

1. 舊 session 關閉可能較慢。
2. 新 session 已為同一 endpoint 建立新 projection。
3. 若舊 session 無條件 `unregister(endpointId)`，會誤刪新 projection。

Token ownership 和目前 BLE connection ownership／generation 的 fail-closed 精神一致。

### 7.2 Meshtastic scoped projection

檔案：

```text
app/src/main/kotlin/com/ntsocial/meshlink/app/radio/
  MeshtasticEndpointChannelProjection.kt
```

```kotlin
package com.ntsocial.meshlink.app.radio

import androidx.lifecycle.ViewModel
import com.ntsocial.meshlink.core.channelhub.ChannelRole
import com.ntsocial.meshlink.core.channelhub.EndpointChannelKey
import com.ntsocial.meshlink.core.channelhub.EndpointChannelProjection
import com.ntsocial.meshlink.core.channelhub.EndpointChannelSummary
import com.ntsocial.meshlink.core.channelhub.NativeChannelId
import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.util.getChannel
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.RadioProtocol
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

class MeshtasticEndpointChannelProjection(
    override val endpointId: RadioEndpointId,
    radioConfigRepository: RadioConfigRepository,
    packetRepository: PacketRepository,
    scope: CoroutineScope,
) : EndpointChannelProjection {

    private val contacts = packetRepository.getContacts()
    private val contactSettings = packetRepository.getContactSettings()

    override val channels =
        radioConfigRepository.channelSetFlow
            .flatMapLatest { channelSet ->
                if (channelSet.settings.isEmpty()) {
                    return@flatMapLatest flowOf(emptyList())
                }

                val unreadFlows =
                    channelSet.settings.indices.map { index ->
                        packetRepository.getUnreadCountFlow(
                            "$index${DataPacket.ID_BROADCAST}",
                        )
                    }

                combine(
                    contacts,
                    contactSettings,
                    combine(unreadFlows) { values -> values.toList() },
                ) { latestContacts, settings, unreadCounts ->
                    channelSet.settings.mapIndexed { index, channelSettings ->
                        val contactKey = "$index${DataPacket.ID_BROADCAST}"
                        val lastPacket = latestContacts[contactKey]
                        val channel = channelSet.getChannel(index)

                        EndpointChannelSummary(
                            key =
                                EndpointChannelKey(
                                    endpointId = endpointId,
                                    protocol = RadioProtocol.MESHTASTIC,
                                    nativeChannelId = NativeChannelId("meshtastic:$index"),
                                    nativeContactKey = contactKey,
                                ),
                            index = index,
                            displayName = channel?.name ?: "Channel $index",
                            role =
                                if (index == 0) {
                                    ChannelRole.PRIMARY
                                } else {
                                    ChannelRole.SECONDARY
                                },
                            isEncrypted = channel?.psk?.size != 0,
                            lastMessageText = lastPacket?.text,
                            lastMessageAtMillis = lastPacket?.time,
                            unreadCount = unreadCounts[index],
                            isMuted = settings[contactKey]?.isMuted == true,
                        )
                    }
                }
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )
}
```

實作注意：

- 不要把 `ViewModel` 放進 projection；上方 import 只是說明實作時應移除的錯誤示例，正式檔案不得保留無用 import。
- `DataPacket.time` 的單位應依現有 model 契約統一轉成 UI 所需毫秒；不要在 Composable 猜測。
- 若 `combine(emptyList())` 在目前 coroutines 版本沒有合適 overload，應將空清單分支提前處理，或建立 `combineIntFlows()` helper。
- `isEncrypted` 應沿用現有 `SecurityIcon`／PSK 判斷語意，避免把 default PSK 誤標為「高安全」。UI 可以顯示「已加密／預設金鑰／明文」三態，而不是只有 Boolean；正式 model 建議改成 `ChannelSecurityKind`。
- 不可硬編碼八個 channel。以 radio 回報的 `ChannelSet.settings` 為準。

### 7.3 在 secondary endpoint scope 註冊 projection

在 `RadioEndpointKoinModule.kt` 的 endpoint scope 增加：

```kotlin
scoped {
    MeshtasticEndpointChannelProjection(
        endpointId = get<RadioEndpointScopeContext>().profile.id,
        radioConfigRepository = get(),
        packetRepository = get(),
        scope = get(named("ServiceScope")),
    )
}.bind<EndpointChannelProjection>()
```

在 `AndroidRadioEndpointSessionFactory.createSecondarySession()`：

```kotlin
val runtimeToken = Uuid.random().toString()
val projection = koinScope.get<EndpointChannelProjection>()
projectionRegistry.register(profile.id, runtimeToken, projection)

return SecondaryRadioEndpointSession(
    // existing args...
    projectionRegistry = projectionRegistry,
    projectionRuntimeToken = runtimeToken,
)
```

在 `SecondaryRadioEndpointSession.close()`，必須在關閉 Koin scope 前：

```kotlin
projectionRegistry.unregister(endpointId, projectionRuntimeToken)
```

Legacy primary 也需要註冊一份 projection。建議由 App root Koin graph 建立 `LegacyPrimaryChannelProjectionRegistrar`，在 fleet start 後以 legacy primary profile ID 綁定 root repositories。不要把 primary 特例硬寫進 Channel Hub ViewModel。

---

## 8. Root 聚合 repository

檔案：

```text
app/src/main/kotlin/com/ntsocial/meshlink/app/radio/
  DefaultFleetChannelHubRepository.kt
```

```kotlin
package com.ntsocial.meshlink.app.radio

import com.ntsocial.meshlink.core.channelhub.EndpointChannelGroup
import com.ntsocial.meshlink.core.channelhub.FleetChannelHubRepository
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.RadioFleetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

class DefaultFleetChannelHubRepository(
    fleetManager: RadioFleetManager,
    projectionRegistry: EndpointChannelProjectionRegistry,
    scope: CoroutineScope,
) : FleetChannelHubRepository {

    override val groups =
        combine(
            fleetManager.snapshots,
            projectionRegistry.entries,
        ) { snapshots, entries -> snapshots to entries }
            .flatMapLatest { (snapshots, entries) ->
                val orderedSnapshots =
                    snapshots.values.sortedWith(
                        compareByDescending { it.profile.legacyPrimary }
                            .thenByDescending { it.profile.priority }
                            .thenBy { it.profile.displayName },
                    )

                if (orderedSnapshots.isEmpty()) {
                    return@flatMapLatest flowOf(emptyList())
                }

                val channelFlows =
                    orderedSnapshots.map { snapshot ->
                        entries[snapshot.profile.id]
                            ?.projection
                            ?.channels
                            ?: flowOf(emptyList())
                    }

                combine(channelFlows) { channelLists ->
                    orderedSnapshots.mapIndexed { index, snapshot ->
                        val profile = snapshot.profile
                        val hasProjection = entries.containsKey(profile.id)

                        EndpointChannelGroup(
                            endpointId = profile.id,
                            protocol = profile.protocol,
                            displayName = profile.displayName,
                            addressSuffix = profile.addressSuffix.uppercase(),
                            accentToken = profile.appearance.accentToken,
                            sessionState = snapshot.state,
                            generation = snapshot.generation,
                            channels = channelLists[index],
                            dataAvailable = hasProjection,
                        )
                    }
                }
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )
}
```

重要不變量：

- 群組順序由 fleet profile 決定，不由 Flow 抵達順序決定。
- projection 缺失時仍保留 endpoint group，UI 顯示資料尚未就緒。
- `endpointId` 是每個 group 與 LazyColumn item 的 stable key。
- 不要每秒輪詢；所有更新由現有 Flow 推送。
- 若 endpoint 被移除，projection registry 與 snapshot 都會消失，group 自動移除。

---

## 9. Endpoint-bound 導覽與 scope

### 9.1 修改 route

檔案：

```text
core/navigation/src/commonMain/kotlin/
  com/ntsocial/meshlink/core/navigation/Routes.kt
```

由：

```kotlin
@Serializable
data class Messages(val contactKey: String, val message: String = "") : ContactsRoute
```

改為：

```kotlin
@Serializable
data class Messages(
    val endpointId: String,
    val contactKey: String,
    val expectedGeneration: Long? = null,
    val message: String = "",
) : ContactsRoute
```

`expectedGeneration` 的用途：

- 讀取歷史可容許 generation 已更新。
- 發送時若 route 帶有舊 generation，ViewModel 應重新確認 endpoint 已 Ready，並取得／驗證目前 generation。
- 不應因為 reconnect 就永久阻止使用者回到對話；但一定不能無聲改由另一 endpoint 發送。

### 9.2 新增精確 scope host

目前整棵 `RadioNavigationDisplay` 被 selected endpoint scope 包住。Channel Hub 本身必須位於 root／fleet scope，但 detail message 必須回到精確 endpoint scope。

建議新增：

```text
app/src/main/kotlin/com/ntsocial/meshlink/app/ui/
  EndpointScopeHost.kt
```

```kotlin
@Composable
fun EndpointScopeHost(
    endpointId: RadioEndpointId,
    content: @Composable () -> Unit,
) {
    val fleetManager = koinInject<RadioFleetManager>()
    val scopeRegistry = koinInject<RadioEndpointScopeRegistry>()
    val snapshots by fleetManager.snapshots.collectAsStateWithLifecycle()
    val endpointScopes by scopeRegistry.scopes.collectAsStateWithLifecycle()
    val snapshot = snapshots[endpointId]

    when {
        snapshot == null -> RemovedEndpointScreen()

        snapshot.profile.legacyPrimary -> content()

        endpointScopes[endpointId] != null ->
            UnboundKoinScope(scope = checkNotNull(endpointScopes[endpointId])) {
                content()
            }

        else -> EndpointDataUnavailableScreen(
            endpointName = snapshot.profile.displayName,
        )
    }
}
```

最重要的規則：**找不到 endpoint scope 時，不得退回目前 selected endpoint scope。** 這種 silent fallback 會把錯誤變成跨 radio 誤送。

### 9.3 修改 Contacts navigation

```kotlin
entry<ContactsRoute.Messages>(metadata = { ListDetailSceneStrategy.detailPane() }) { args ->
    val endpointId = RadioEndpointId(args.endpointId)

    EndpointScopeHost(endpointId = endpointId) {
        val messageViewModel: MessageViewModel =
            scopedViewModel(
                key = "messages-${args.endpointId}-${args.contactKey}",
            )

        messageViewModel.bindConversation(
            endpointId = endpointId,
            contactKey = args.contactKey,
            expectedGeneration = args.expectedGeneration,
        )

        MessageScreen(
            contactKey = args.contactKey,
            message = args.message,
            viewModel = messageViewModel,
            // existing callbacks...
        )
    }
}
```

`MessageViewModel` 應由 `setContactKey()` 改為一次性、可驗證的 `bindConversation()`。同一個 ViewModel instance 不應在不同 endpoint 間重綁。

### 9.4 點擊全節點頻道時

```kotlin
fun openChannel(group: EndpointChannelGroup, channel: EndpointChannelSummary) {
    backStack.add(
        ContactsRoute.Messages(
            endpointId = group.endpointId.value,
            contactKey = channel.key.nativeContactKey,
            expectedGeneration = group.generation,
        ),
    )
}
```

可以同時呼叫：

```kotlin
fleetManager.select(group.endpointId)
```

讓使用者之後切到 Nodes／Settings 時延續該節點 context；但這只是 shell convenience，不能取代 route 內的 endpoint identity。

### 9.5 Feature scope mode

目前 `Main.kt` 只有 `endpointAware: Boolean`，不足以描述三種需求。建議改為：

```kotlin
enum class FeatureScopeMode {
    ROOT,
    SELECTED_ENDPOINT,
    FLEET,
}

private fun scopeMode(route: NavKey): FeatureScopeMode =
    when (TopLevelDestination.fromNavKey(route)) {
        TopLevelDestination.Conversations -> FeatureScopeMode.FLEET
        TopLevelDestination.Nodes,
        TopLevelDestination.Settings,
        -> FeatureScopeMode.SELECTED_ENDPOINT

        TopLevelDestination.Connections,
        TopLevelDestination.MeshCore,
        null,
        -> FeatureScopeMode.ROOT
    }
```

行為：

- `FLEET`：不顯示 App-global `RadioEndpointTabs`，由 Channel Hub 自己顯示「全部 + 節點 tabs」。
- `SELECTED_ENDPOINT`：保留 endpoint tabs 或改成更精緻的 endpoint selector。
- `ROOT`：不注入 endpoint scope。
- Firmware／nested Settings route 的 scope mode 應依其 parent feature 明確決定，不要只靠 route class 猜測。

---

## 10. Channel Hub ViewModel

檔案：

```text
feature/messaging/src/commonMain/kotlin/
  com/ntsocial/meshlink/feature/messaging/hub/ChannelHubViewModel.kt
```

```kotlin
package com.ntsocial.meshlink.feature.messaging.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntsocial.meshlink.core.channelhub.ChannelHubSelection
import com.ntsocial.meshlink.core.channelhub.ChannelHubUiState
import com.ntsocial.meshlink.core.channelhub.FleetChannelHubRepository
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ChannelHubViewModel(
    repository: FleetChannelHubRepository,
) : ViewModel() {
    private val selection = MutableStateFlow<ChannelHubSelection>(ChannelHubSelection.All)

    val uiState =
        combine(repository.groups, selection) { groups, selected ->
            val normalizedSelection =
                when (selected) {
                    ChannelHubSelection.All -> selected
                    is ChannelHubSelection.Endpoint ->
                        if (groups.any { it.endpointId == selected.endpointId }) {
                            selected
                        } else {
                            ChannelHubSelection.All
                        }
                }

            ChannelHubUiState(
                selection = normalizedSelection,
                groups = groups,
                loading = false,
            )
        }.stateInWhileSubscribed(ChannelHubUiState())

    fun showAll() {
        selection.value = ChannelHubSelection.All
    }

    fun showEndpoint(endpointId: RadioEndpointId) {
        selection.value = ChannelHubSelection.Endpoint(endpointId)
    }
}
```

建議先不把 selection 寫入 endpoint-scoped DataStore。它是 App-global UI preference，可在後續加入 `UiPrefs.lastChannelHubSelection`；process restore 若 endpoint 已移除，仍須回到「全部」。

---

## 11. Compose UI 施工範本

### 11.1 `ChannelHubScreen`

檔案：

```text
feature/messaging/src/commonMain/kotlin/
  com/ntsocial/meshlink/feature/messaging/hub/ChannelHubScreen.kt
```

```kotlin
@Composable
fun ChannelHubScreen(
    uiState: ChannelHubUiState,
    onSelectAll: () -> Unit,
    onSelectEndpoint: (RadioEndpointId) -> Unit,
    onOpenChannel: (EndpointChannelGroup, EndpointChannelSummary) -> Unit,
    onEditAppearance: (RadioEndpointId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(Res.string.channel_hub_title)) },
                )
                ChannelHubTabs(
                    groups = uiState.groups,
                    selection = uiState.selection,
                    onSelectAll = onSelectAll,
                    onSelectEndpoint = onSelectEndpoint,
                )
            }
        },
    ) { innerPadding ->
        when (val selection = uiState.selection) {
            ChannelHubSelection.All ->
                AllEndpointChannels(
                    groups = uiState.groups,
                    onOpenChannel = onOpenChannel,
                    onEditAppearance = onEditAppearance,
                    modifier = Modifier.padding(innerPadding),
                )

            is ChannelHubSelection.Endpoint ->
                EndpointChannelPage(
                    group = uiState.groups.firstOrNull {
                        it.endpointId == selection.endpointId
                    },
                    onOpenChannel = onOpenChannel,
                    onEditAppearance = onEditAppearance,
                    modifier = Modifier.padding(innerPadding),
                )
        }
    }
}
```

### 11.2 「全部」清單

```kotlin
@Composable
private fun AllEndpointChannels(
    groups: List<EndpointChannelGroup>,
    onOpenChannel: (EndpointChannelGroup, EndpointChannelSummary) -> Unit,
    onEditAppearance: (RadioEndpointId) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = groups,
            key = { it.endpointId.value },
            contentType = { "endpoint-channel-group" },
        ) { group ->
            EndpointChannelGroupCard(
                group = group,
                onOpenChannel = { channel -> onOpenChannel(group, channel) },
                onEditAppearance = { onEditAppearance(group.endpointId) },
            )
        }
    }
}
```

不要在 item 中用 endpoint index 作 key。使用者改名稱或排序時，`endpointId` 才能保持 Compose state 與 accessibility focus 穩定。

### 11.3 群組色卡

```kotlin
@Composable
fun EndpointChannelGroupCard(
    group: EndpointChannelGroup,
    onOpenChannel: (EndpointChannelSummary) -> Unit,
    onEditAppearance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NodeAccentPalette.colors(group.accentToken)

    ElevatedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = false) {
                    heading()
                    contentDescription =
                        "${group.displayName}, ${group.addressSuffix}, " +
                            endpointStateDescription(group.sessionState)
                },
        colors = CardDefaults.elevatedCardColors(
            containerColor = colors.container,
            contentColor = colors.onContainer,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier
                        .width(4.dp)
                        .heightIn(min = 96.dp)
                        .fillMaxHeight()
                        .background(colors.accent),
            )

            Column(modifier = Modifier.weight(1f)) {
                EndpointGroupHeader(
                    group = group,
                    onEditAppearance = onEditAppearance,
                )

                HorizontalDivider(
                    color = colors.outline.copy(alpha = 0.45f),
                )

                group.channels.forEachIndexed { index, channel ->
                    ChannelSummaryRow(
                        channel = channel,
                        enabled = group.dataAvailable,
                        canSend = group.canSend,
                        onClick = { onOpenChannel(channel) },
                    )
                    if (index != group.channels.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            color = colors.outline.copy(alpha = 0.30f),
                        )
                    }
                }

                if (!group.dataAvailable) {
                    EndpointProjectionUnavailableRow()
                } else if (group.channels.isEmpty()) {
                    EmptyEndpointChannelsRow()
                }
            }
        }
    }
}
```

### 11.4 Channel row

每一列至少顯示：

- PRIMARY／SECONDARY badge。
- channel display name。
- security icon。
- last message preview。
- last message time。
- unread badge。
- muted icon。
- 離線時的 read-only semantics。

```kotlin
@Composable
fun ChannelSummaryRow(
    channel: EndpointChannelSummary,
    enabled: Boolean,
    canSend: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .semantics {
                    role = Role.Button
                    stateDescription =
                        if (canSend) "可收發" else "唯讀"
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelRoleBadge(channel.role)

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channel.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ChannelSecurityIcon(channel)
            }
            Text(
                text = channel.lastMessageText.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        ChannelTrailingMetadata(channel)
    }
}
```

### 11.5 Tabs

```kotlin
@Composable
private fun ChannelHubTabs(
    groups: List<EndpointChannelGroup>,
    selection: ChannelHubSelection,
    onSelectAll: () -> Unit,
    onSelectEndpoint: (RadioEndpointId) -> Unit,
) {
    val selectedIndex =
        when (selection) {
            ChannelHubSelection.All -> 0
            is ChannelHubSelection.Endpoint ->
                groups.indexOfFirst { it.endpointId == selection.endpointId }
                    .takeIf { it >= 0 }
                    ?.plus(1)
                    ?: 0
        }

    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 12.dp,
    ) {
        Tab(
            selected = selectedIndex == 0,
            onClick = onSelectAll,
            text = { Text(stringResource(Res.string.channel_hub_all)) },
        )

        groups.forEachIndexed { index, group ->
            Tab(
                selected = selectedIndex == index + 1,
                onClick = { onSelectEndpoint(group.endpointId) },
                text = {
                    NodeTabLabel(
                        name = group.displayName,
                        addressSuffix = group.addressSuffix,
                        accentToken = group.accentToken,
                        unreadCount = group.unreadCount,
                        state = group.sessionState,
                    )
                },
            )
        }
    }
}
```

### 11.6 Adaptive layout

專案已使用 `ListDetailSceneStrategy`。應保留現有 Navigation 3 scene strategy：

- Compact：Channel Hub list 與 Message detail 單頁切換。
- Medium／Expanded：左側 Channel Hub list，右側 Message detail。
- Extra pane 可保留給 node detail／channel information，但第一版不必強制使用。

不要另外建立一套只適用 Android tablet 的 fragment-based navigation；目前是 Compose Multiplatform，應維持 common UI 可編譯。

---

## 12. Endpoint 外觀資料與向後相容 migration

### 12.1 擴充 profile

檔案：

```text
core/radio-fleet/src/commonMain/kotlin/
  com/ntsocial/meshlink/core/radiofleet/RadioEndpoint.kt
```

```kotlin
enum class NodeAccentToken {
    INDIGO,
    EMERALD,
    AMBER,
    CYAN,
    BLUE,
    VIOLET,
    ROSE,
    LIME,
    TEAL,
    ORANGE,
    SLATE,
    FUCHSIA,
}

enum class NodeIconToken {
    RADIO,
    BASE_STATION,
    MOBILE,
    RELAY,
}

data class RadioEndpointAppearance(
    val accentToken: NodeAccentToken,
    val iconToken: NodeIconToken = NodeIconToken.RADIO,
    val version: Int = 1,
)

data class RadioEndpointProfile(
    val id: RadioEndpointId,
    val protocol: RadioProtocol,
    val transportAddress: String,
    val displayName: String,
    val enabled: Boolean = true,
    val autoConnect: Boolean = true,
    val priority: Int = DEFAULT_RADIO_PRIORITY,
    val legacyPrimary: Boolean = false,
    val appearance: RadioEndpointAppearance =
        RadioEndpointAppearance(NodeAccentToken.INDIGO),
)
```

不得在 `core:radio-fleet` 儲存 Compose `Color`；只存 semantic token。

### 12.2 不要直接增加 pipe-separated 欄位

現有 `radio_endpoint_profiles_v1` 應保留讀取能力。建議新增：

```text
radio_endpoint_profiles_v2
radio_endpoint_store_schema_version = 2
```

v2 使用 `kotlinx.serialization` JSON：

```kotlin
@Serializable
private data class StoredEndpointCatalogV2(
    val schemaVersion: Int = 2,
    val profiles: List<StoredEndpointProfileV2>,
)

@Serializable
private data class StoredEndpointProfileV2(
    val id: String,
    val protocol: String,
    val transportAddress: String,
    val displayName: String,
    val enabled: Boolean,
    val autoConnect: Boolean,
    val priority: Int,
    val legacyPrimary: Boolean,
    val accentToken: String,
    val iconToken: String,
)
```

讀取順序：

```kotlin
private fun decodeCatalog(preferences: Preferences): List<RadioEndpointProfile> {
    preferences[KEY_ENDPOINT_PROFILES_V2]
        ?.let(::decodeV2)
        ?.let(::normalizeProfiles)
        ?.let { return it }

    return normalizeProfiles(
        decodeProfilesV1(preferences[KEY_ENDPOINT_PROFILES_V1]),
    )
}
```

migration：

1. 讀到 v2：直接使用。
2. 沒有 v2，但有 v1：decode 八欄資料。
3. 依 profile 穩定順序分配不重複預設色。
4. 在同一個 `dataStore.edit` 寫入 v2 catalog 與 schema version。
5. 保留 v1 key 至少一個正式版本，作為 rollback fallback。
6. 後續確認所有 release migration 正常後再移除 v1 寫入。

預設色分配不可每次啟動重新 hash，否則 profile 排序或 Kotlin hash 實作改變可能讓顏色漂移。migration 或 register 新 endpoint 時分配一次並持久化。

### 12.3 更新外觀

沿用現有：

```kotlin
suspend fun RadioEndpointStore.update(profile: RadioEndpointProfile)
```

ViewModel：

```kotlin
fun setAccent(endpointId: RadioEndpointId, token: NodeAccentToken) {
    viewModelScope.launch {
        val current = endpointStore.profiles.value
            .firstOrNull { it.id == endpointId }
            ?: return@launch

        endpointStore.update(
            current.copy(
                appearance = current.appearance.copy(accentToken = token),
            ),
        )
    }
}
```

---

## 13. 發送路徑與安全不變量

### 13.1 Read model 與 command model 分離

Channel Hub projection 只回答「要顯示什麼」。發送必須經由 endpoint-bound command：

```kotlin
data class EndpointSendRequest(
    val endpointId: RadioEndpointId,
    val protocol: RadioProtocol,
    val nativeContactKey: String,
    val expectedGeneration: Long,
    val text: String,
    val replyId: Int? = null,
)
```

第一階段可由 `EndpointScopeHost` 解析該 endpoint 的 `SendMessageUseCase`，並在呼叫前執行：

```kotlin
fleetManager.requireCurrentGeneration(
    endpointId = request.endpointId,
    expectedGeneration = request.expectedGeneration,
)
```

如果 reconnect 已改變 generation：

- UI 重新讀取目前 Ready 狀態。
- 要求使用者重試或由 ViewModel 重新 bind 當前 generation。
- 不可改由 selected endpoint 發送。

### 13.2 Identity 必須包含的欄位

第一版每個 channel interaction 至少要保有：

```text
endpointId
protocol
nativeChannelId
nativeContactKey
generation
```

未來跨平台時再增加：

```text
networkIdentity
routeIdentity
securityDomain
```

### 13.3 相同名稱不代表相同頻道

以下情況都必須保持完全分離：

- 四台 radio 都有 `0^all`。
- 四台 radio 的 Primary 都叫 `LongFast`。
- 兩台 radio 使用相同 PSK。
- 兩台 radio 收到相同 packet ID。
- 一台 radio reconnect 後 generation 改變。

UI 可以顯示相同名稱，但 route、database、未讀、通知與 send path 都必須由 endpoint identity 隔離。

---

## 14. 未來 MeshCore 與自訂協議接入

### 14.1 擴充 protocol enum

真正開始接入時再擴充：

```kotlin
enum class RadioProtocol {
    MESHTASTIC,
    MESHCORE,
    NTSOCIAL_CUSTOM,
}
```

目前不必先建立不存在的 runtime graph，但 Channel Hub model 必須避免 Meshtastic-only 命名。

### 14.2 Adapter contract

```kotlin
interface ProtocolChannelProjectionFactory {
    val protocol: RadioProtocol

    suspend fun create(
        endpointId: RadioEndpointId,
        runtime: ProtocolEndpointRuntime,
    ): EndpointChannelProjection
}
```

各 adapter 自己處理：

- channel／room identity。
- display name。
- security state。
- unread count。
- last message preview。
- send capability。
- ACK／receipt 語意。

Channel Hub 只渲染共同 read model，不應假設所有平台都有 Meshtastic 的 Primary／Secondary。若 MeshCore 的模型不同，可在共同 enum 加入 `ROOM`、`DIRECT` 或用 `ChannelCategory`；不要硬把不同協議的語意偽裝成 Meshtastic slot。

### 14.3 Bridge 與統一 UI 是不同工作

把兩個平台顯示在同一頁，不代表自動跨平台轉送。Bridge 必須另有：

- loop prevention。
- bridge hop limit。
- identity mapping。
- encryption boundary。
- allowlist。
- audit log。

第一版 Channel Hub 應只做統一瀏覽與沿原 ingress route 回覆。

---

## 15. 逐檔修改清單

### 15.1 新增模組與 domain

| 檔案 | 修改 |
|---|---|
| `settings.gradle.kts` | include `:core:channel-hub` |
| `core/channel-hub/build.gradle.kts` | KMP library；依賴 `core:radio-fleet`、coroutines；不可依賴 Android |
| `ChannelHubModels.kt` | protocol-neutral group／channel／selection model |
| `EndpointChannelProjection.kt` | endpoint read projection contract |
| `FleetChannelHubRepository.kt` | root aggregate repository contract |

### 15.2 Endpoint profile 與 persistence

| 檔案 | 修改 |
|---|---|
| `core/radio-fleet/.../RadioEndpoint.kt` | 新增 appearance token；保持無 Compose dependency |
| `core/prefs/.../DataStoreRadioEndpointStore.kt` | v1 decode、v2 JSON、atomic migration、default token allocation |
| `core/prefs/.../DataStoreRadioEndpointStoreTest.kt` | 舊八欄 migration、壞資料、重複色、remove／register 後穩定性 |

### 15.3 Android projection wiring

| 檔案 | 修改 |
|---|---|
| `app/.../radio/EndpointChannelProjectionRegistry.kt` | 新增 runtime-token registry |
| `app/.../radio/MeshtasticEndpointChannelProjection.kt` | 將 scoped repositories 映射為 bounded channel summaries |
| `app/.../radio/DefaultFleetChannelHubRepository.kt` | 動態聚合 fleet snapshots 與 projections |
| `app/.../radio/RadioEndpointKoinModule.kt` | endpoint scope 內註冊 projection |
| `app/.../radio/AndroidRadioEndpointSessionFactory.kt` | session create／close 時 register／unregister |
| `app/.../di/AppKoinModule.kt` | root registry、repository、legacy-primary registrar |

### 15.4 Navigation 與 scope

| 檔案 | 修改 |
|---|---|
| `core/navigation/.../Routes.kt` | `Messages` 加入 endpointId／generation |
| `core/navigation/.../MultiBackstack.kt` | 先修 nested deep-link P1 |
| `app/.../ui/EndpointScopeHost.kt` | 以 route endpoint 解析精確 Koin scope |
| `app/.../ui/Main.kt` | `FeatureScopeMode`；Channel Hub 使用 fleet scope；隱藏 global endpoint tabs |
| `feature/messaging/.../ContactsNavigation.kt` | Channel Hub root 與 endpoint-bound detail |
| `feature/messaging/.../AdaptiveContactsScreen.kt` | 改由 Channel Hub entry 驅動 |
| `feature/messaging/.../MessageViewModel.kt` | 一次性 bind endpoint conversation；send generation guard |

### 15.5 UI

| 檔案 | 修改 |
|---|---|
| `feature/messaging/.../hub/ChannelHubViewModel.kt` | All／Endpoint local selection |
| `.../hub/ChannelHubScreen.kt` | Scaffold、tabs、all／endpoint content |
| `.../hub/EndpointChannelGroupCard.kt` | 節點色卡與 header |
| `.../hub/ChannelSummaryRow.kt` | Primary／Secondary row |
| `.../hub/NodeTabLabel.kt` | 名稱、色點、狀態、未讀 |
| `.../hub/NodeAppearanceDialog.kt` | 色調與 icon picker |
| `core/ui/.../theme/NodeAccentPalette.kt` | token → light／dark colors |
| `feature/connections/.../RadioFleetPanel.android.kt` | 加入外觀入口與色調 preview |
| `core/resources/.../values*/strings.xml` | 英文、繁體中文、日文文案 |

### 15.6 建議逐步淘汰

- `ContactsScreen` 不必立即刪除；先成為單 endpoint 私訊區段的共用 component。
- `ContactItem` 可保留給 DM，不再負責 Channel Hub 的 broadcast channel row。
- `RadioEndpointTabs` 可暫留給 Nodes／Settings，Channel Hub 不使用。
- 不要一次重寫 MessageScreen；先把 scope 與 route identity 修正，再換外觀。

---

## 16. 實作順序

### Phase 0：先修已知基礎缺陷

1. 修正 `MultiBackstack.handleDeepLink()`。
2. 補 `/channels`、`/firmware`、nested route tests。
3. 修正 Connections 持續掃描造成約 120 fps／高 CPU 的 lifecycle 問題。
4. 建立現有單 endpoint UI／memory／frame baseline。

完成條件：不新增 Channel Hub 功能，但現有 branch 的 release blocker 不再阻擋後續導覽。

### Phase 1：Endpoint appearance 與 UI component

1. 新增 appearance token model。
2. 完成 v1 → v2 DataStore migration。
3. 完成 `NodeAccentPalette`。
4. 更新 Connections endpoint card，允許改名／選色。
5. 補 dark mode、font scale、TalkBack component tests。

### Phase 2：唯讀「全部頻道」

1. 新增 `core:channel-hub`。
2. 新增 projection registry。
3. 註冊 legacy primary 與 secondary projection。
4. 建立 Channel Hub All 頁。
5. 保留現有 message detail route，暫時只允許在 endpoint tab 內開啟，直到 Phase 3 identity 完成。

這一階段可先驗證：四個 fake endpoints 的同名 channel 是否正確分組。

### Phase 3：Endpoint-bound conversation 與發送

1. route 加 `endpointId`。
2. 新增 `EndpointScopeHost`。
3. ViewModel key 加 endpoint。
4. send request 加 generation guard。
5. 從 All 頁直接開啟／回覆原 endpoint。
6. endpoint 被移除、斷線、reconnect 的錯誤處理。

### Phase 4：每節點完整頁與 adaptive polish

1. 每節點 tab 加入 scoped private-message Paging。
2. list／detail expanded layout。
3. 搜尋與未讀 filter。
4. process restore 與 scroll restoration。
5. Macrobenchmark／Perfetto／recomposition 檢查。

### Phase 5：MeshCore／自訂協議 adapter

1. protocol-neutral runtime contract。
2. MeshCore projection。
3. 自訂協議 projection。
4. route identity 擴充。
5. 若需要 bridge，另立專案與安全審查，不混入 Channel Hub PR。

---

## 17. 測試計畫

### 17.1 Unit tests

#### Endpoint profile migration

- v1 八欄 profile 可完整 migration 到 v2。
- legacy primary 不變。
- selected endpoint 不變。
- appearance token 被持久化，重啟後不漂移。
- 壞掉的單筆 profile 不會清空其他合法 profile。
- 四個 endpoint 優先取得不同預設色。
- 第五個 register 仍依 `MAX_RADIO_ENDPOINTS` 拒絕。

#### Projection registry

- register 後可見。
- 同 endpoint 新 token 覆蓋舊 token。
- 舊 token unregister 不得移除新 projection。
- 正確 token unregister 才能移除。

#### Aggregate repository

建立四個 fake projections，全部都使用：

```text
nativeContactKey = 0^all
nativeChannelId = meshtastic:0
```

驗證輸出仍有四個不同 `EndpointChannelKey`，且不互相覆蓋。

其他測試：

- profile 排序穩定。
- projection 晚到時 group 先存在，之後補上 channels。
- endpoint removal 只移除該 group。
- reconnect generation 更新。
- empty channel set。
- duplicate display name。

### 17.2 Navigation tests

- 從 All 點 Radio B channel，取得的 MessageViewModel 必須來自 Radio B scope。
- 全域 selected endpoint 即使仍為 Radio A，也不能影響 Radio B route。
- process restore 後 route endpoint 不存在，顯示 removed endpoint state，不 crash、不 fallback。
- `/channels` nested deep link 不破壞 top-level stack。
- endpoint 切換後 Back 回到 All 頁原 scroll position。

### 17.3 Compose UI tests

- 預設選取「全部」。
- 四張節點群組卡與所有 channel rows 不需點擊即可由 scroll 找到。
- 色卡有節點名稱與末四碼，不只顏色。
- dark mode 文字可讀。
- 200% font scale 不截斷主要操作。
- tab 與 row 觸控範圍至少 48dp。
- TalkBack traversal：App bar → tabs → endpoint header → channels。
- unread badge content description 正確。
- offline row 宣告「唯讀」。
- accessibility checks 不出現對比、touch target 與 traversal blocker。

### 17.4 Integration tests

- 四個 fake Koin endpoint scopes，各自有獨立 PacketRepository。
- 四個 database 都有 `0^all` 與相同訊息 UUID，仍正確顯示／開啟。
- 刪除 endpoint 2 不影響 1、3、4。
- endpoint 3 reconnect 時舊 projection unregister 不移除新 projection。
- channel configuration mutation只更新所屬 endpoint card。

### 17.5 實機測試

現有 2026-08-24 測試只證明多節點版本的單 endpoint 相容性，不能當成同一手機四 radio 證據。正式驗收至少分兩階段：

#### 兩台 radio

- 同一手機同時 Ready。
- 兩台 primary channel 設為不同名稱／PSK。
- 各自收訊，All 頁正確更新。
- 從 All 頁分別回覆並由遠端確認 ingress／egress radio。
- 斷開其中一台，另一台不受影響。
- reconnect storm。
- process death／restore。

#### 四台 radio

- 四台同時登錄、連線、同步與收訊。
- 四台都使用 slot 0，確認 key 不碰撞。
- 交錯訊息與未讀。
- 逐台斷線／重連。
- 螢幕關閉、背景、Doze、拔除 USB 供電。
- 30～60 分鐘收訊 soak，再做 2 小時 soak。
- 記錄 BLE GATT error、CPU、PSS、thread、recomposition、frame time 與電量。

### 17.6 效能門檻

- 靜止 Channel Hub 不得持續產生接近螢幕更新率的 frame。
- 無限動畫數量為 0。
- group list 使用 stable key。
- channel summary 使用 immutable／distinct data，避免同內容重組。
- 不因每筆 packet 重建所有四個 endpoint scope。
- All 頁只訂閱 configured channel summaries；不可無界載入所有歷史訊息。
- Message detail 關閉後，其 endpoint paging collector應依 lifecycle 停止。

---

## 18. 驗收條件

功能完成必須同時符合：

1. App 可登錄最多四個 Meshtastic BLE endpoint。
2. Channel Hub 預設開啟「全部」。
3. 不點選任何節點，只靠垂直滑動即可看到所有已登錄節點的所有 configured Primary／Secondary Channel。
4. 每個 endpoint 有一張清楚分組的色卡。
5. 使用者可修改每個 endpoint 的色調，設定跨重啟保留。
6. 顏色不是唯一辨識方式。
7. 同名 channel、相同 slot、相同 `contactKey` 不碰撞。
8. 從 All 頁進入訊息後，讀取與發送都綁定原 endpoint。
9. endpoint 離線時顯示快取資料，但禁止發送。
10. endpoint reconnect／generation 改變時不會由錯誤 radio 發送。
11. endpoint 被移除或 scope 尚未建立時，不 fallback 到另一 endpoint。
12. Compact 與 expanded window 都可操作。
13. TalkBack、深色模式、200% font scale 與 48dp touch target 通過。
14. nested deep link crash 已修正。
15. 兩台與四台實機矩陣通過，不能只靠 fake test 宣稱完成。
16. root build、format、Detekt、unit／KMP tests、Android lint 與 Debug assembly通過；既有 pre-existing finding 必須分開記錄。

---

## 19. 明確不建議的實作方式

### 19.1 不要在 Composable 直接迴圈取得四個 Koin scope

錯誤方向：

```kotlin
endpoints.forEach { endpoint ->
    val scope = scopeRegistry.get(endpoint.id)
    UnboundKoinScope(scope) {
        val viewModel = scopedViewModel<ContactsViewModel>()
        // render one full ContactsScreen
    }
}
```

問題：

- 同時建立四個完整 Contacts ViewModel／Paging collector。
- Composable 直接依賴 Android Koin scope registry。
- scope close／recreate 的 ownership 難以控制。
- 無法自然支援 MeshCore 或自訂協議。
- 四份 Scaffold／AppBar／selection mode 互相衝突。

正確方式是每個 endpoint scope 輸出 bounded projection，由 root repository 聚合。

### 19.2 不要把 BLE address 當 route key

BLE address 可能受平台、配對、隱私或 transport 更換影響。route 使用持久化 `RadioEndpointId`，畫面只顯示 address suffix 作輔助辨識。

### 19.3 不要把四個 database 合併

這會破壞目前已建立的 session isolation，也讓 migration、刪除 endpoint、Gateway 相容與未來 protocol storage 變得更危險。

### 19.4 不要直接合併四條 PagingData

Paging source 的 load state、refresh、key、invalidations 與排序不是簡單 `combine()` 可以正確解決。All 頁先聚合有限的 configured broadcast channels。

### 19.5 不要只改視覺而保留隱含 scope

即使卡片顏色完全正確，只要 route 不帶 endpoint，仍可能由錯誤 radio 發送。UI 重設計與 identity 修正必須一起規畫。

### 19.6 不要讓色卡高飽和、滿版上色

應使用 tonal container 與 accent rail。工程 App 需要高資訊密度，但不等於使用大量警示色或霓虹色。

---

## 20. 建議 PR 切分

為降低回歸風險，建議至少分為五個 PR：

1. **PR-A：Navigation P1 與基準修正**  
   `MultiBackstack`、deep-link tests、掃描 lifecycle／效能基準。

2. **PR-B：Endpoint appearance v2**  
   profile model、DataStore migration、palette、Connections 外觀入口。

3. **PR-C：Channel Hub read model**  
   `core:channel-hub`、projection registry、Meshtastic projections、fake integration tests。

4. **PR-D：Channel Hub UI**  
   All／endpoint tabs、group cards、adaptive layout、accessibility。

5. **PR-E：Endpoint-bound conversation／send**  
   route、scope host、ViewModel binding、generation guard、兩／四 radio hardware test。

不要把 appearance migration、navigation rewrite、跨 scope repository 與 send path 一次塞進單一巨大 PR；一旦出現 cross-node 問題，將很難定位是 storage、navigation、DI 或 UI 所造成。

---

## 21. 最終建議

目前 `multi_nodes_` 的底層方向是正確的，不需要重寫 BLE 或放棄 endpoint Koin scope。接下來最重要的架構轉折是：

> 從「全 App 選一台 radio，整個 UI 換 scope」進化為「每台 radio 保持隔離，各自輸出 projection，Channel Hub 在 root 聚合；進入 detail 時再以 endpoint-bound route 回到精確 scope」。

UI 上，應以「全部 + 每節點分頁」取代只有位址末四碼的全域 tabs；All 頁以節點色卡分組所有 Primary／Secondary Channel，預設展開並允許零額外點擊的垂直瀏覽。這可以直接滿足目前產品需求，也為 MeshCore 與自訂協議保留乾淨的 adapter 邊界。

實作時最不能妥協的是 endpoint identity：顏色、名稱、tab 與卡片只是辨識層；真正防止跨節點讀錯、寫錯與發錯的，是 `endpointId + protocol + nativeChannelId + generation` 從 projection、route、ViewModel 一路保存到 send command。

---

## 22. 參考來源

### 專案來源

- [`multi_nodes_` 分支](https://github.com/nuclear718/ntsocial-mesh-gateway-android/tree/multi_nodes_)
- [`RadioEndpoint.kt`](https://github.com/nuclear718/ntsocial-mesh-gateway-android/blob/multi_nodes_/core/radio-fleet/src/commonMain/kotlin/com/ntsocial/meshlink/core/radiofleet/RadioEndpoint.kt)
- [`RadioFleetManager.kt`](https://github.com/nuclear718/ntsocial-mesh-gateway-android/blob/multi_nodes_/core/radio-fleet/src/commonMain/kotlin/com/ntsocial/meshlink/core/radiofleet/RadioFleetManager.kt)
- [`Main.kt`](https://github.com/nuclear718/ntsocial-mesh-gateway-android/blob/multi_nodes_/app/src/main/kotlin/com/ntsocial/meshlink/app/ui/Main.kt)
- [`AndroidRadioEndpointSessionFactory.kt`](https://github.com/nuclear718/ntsocial-mesh-gateway-android/blob/multi_nodes_/app/src/main/kotlin/com/ntsocial/meshlink/app/radio/AndroidRadioEndpointSessionFactory.kt)
- [`RadioEndpointKoinModule.kt`](https://github.com/nuclear718/ntsocial-mesh-gateway-android/blob/multi_nodes_/app/src/main/kotlin/com/ntsocial/meshlink/app/radio/RadioEndpointKoinModule.kt)
- [`DataStoreRadioEndpointStore.kt`](https://github.com/nuclear718/ntsocial-mesh-gateway-android/blob/multi_nodes_/core/prefs/src/commonMain/kotlin/com/ntsocial/meshlink/core/prefs/radio/DataStoreRadioEndpointStore.kt)
- [`ContactsViewModel.kt`](https://github.com/nuclear718/ntsocial-mesh-gateway-android/blob/multi_nodes_/feature/messaging/src/commonMain/kotlin/com/ntsocial/meshlink/feature/messaging/ui/contact/ContactsViewModel.kt)
- [`Contacts.kt`](https://github.com/nuclear718/ntsocial-mesh-gateway-android/blob/multi_nodes_/feature/messaging/src/commonMain/kotlin/com/ntsocial/meshlink/feature/messaging/ui/contact/Contacts.kt)
- [`MessageViewModel.kt`](https://github.com/nuclear718/ntsocial-mesh-gateway-android/blob/multi_nodes_/feature/messaging/src/commonMain/kotlin/com/ntsocial/meshlink/feature/messaging/MessageViewModel.kt)
- [`Routes.kt`](https://github.com/nuclear718/ntsocial-mesh-gateway-android/blob/multi_nodes_/core/navigation/src/commonMain/kotlin/com/ntsocial/meshlink/core/navigation/Routes.kt)
- [`MultiBackstack.kt`](https://github.com/nuclear718/ntsocial-mesh-gateway-android/blob/multi_nodes_/core/navigation/src/commonMain/kotlin/com/ntsocial/meshlink/core/navigation/MultiBackstack.kt)
- [`ANDROID_MULTI_NODE_THREE_PHONE_HARDWARE_TEST_REPORT_2026-08-24.md`](https://github.com/nuclear718/ntsocial-mesh-gateway-android/blob/multi_nodes_/ANDROID_MULTI_NODE_THREE_PHONE_HARDWARE_TEST_REPORT_2026-08-24.md)
- [`ANDROID_MULTI_RADIO_MULTI_PROTOCOL_ARCHITECTURE_PROPOSAL_2026-08-23.md`](https://github.com/nuclear718/ntsocial-mesh-gateway-android/blob/multi_nodes_/ANDROID_MULTI_RADIO_MULTI_PROTOCOL_ARCHITECTURE_PROPOSAL_2026-08-23.md)
- [`NTsocial-with-Meshtastic-`](https://github.com/nuclear718/NTsocial-with-Meshtastic-)

### Android／Compose 設計依據

- [Build an adaptive list-detail layout](https://developer.android.com/develop/ui/compose/layouts/adaptive/list-detail)
- [Material 3 Adaptive release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive)
- [Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility)
- [Test accessibility in Compose](https://developer.android.com/develop/ui/compose/accessibility/testing)
- [Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps)
