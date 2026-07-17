# 02｜App content 聲明貼上稿

這份文件依目前 `NTsocial MeshLink` Google release 行為提供建議答案。Play Console 的
題目順序可能因帳號、國家與 AAB 權限不同而調整；遇到語意不同的題目時，以實際發布
版本為準，不要為了取得較低分級而否認通訊、UGC 或位置分享。

## 隱私權政策

```text
https://github.com/nuclear718/ntsocial-mesh-gateway-android/blob/main/docs/google-play/PRIVACY_POLICY.md
```

送審前須先在無登入／無痕視窗開啟一次，確認頁面存在、不是 PDF、沒有地區限制，且
頁面中的 App 名稱、發布者與 Play 商店頁一致。

## 廣告

**選擇：否，本 App 不含廣告。**

本 App 沒有橫幅、插頁、原生廣告、獎勵式廣告、自家交叉促銷或廣告 SDK。Firebase
Analytics／Play Services Measurement、Ads Identifier 與 Privacy Sandbox Ads 依賴已移除；
保留的 Firebase Crashlytics 與 Datadog 只用於使用者可關閉的當機、效能與產品診斷。
Google release 合併 Manifest 已驗證不含 Advertising ID、AdServices 或安裝歸因權限。

## App access／審查存取

**選擇：部分功能需要外接硬體；不需要帳號或其他驗證。**

不需要帳號、密碼、OTP、訂閱或付費，但實際 Bluetooth／USB／TCP 連線與 LoRa 收發
需要相容的 Meshtastic radio。請新增一組審查指示並貼上：

```text
NTsocial MeshLink 不需要註冊、登入、訂閱或付費。首次啟動時，審查人員可略過非必要權限並進入主要介面。

審查人員可直接開啟「連線」、「訊息」、「節點」、「地圖」、「MeshCore」與「設定」頁；沒有帳密、OTP、付費牆或隱藏導覽。Bluetooth、USB 或 TCP 連線、無線電設定寫入及 LoRa 收發需要相容的 Meshtastic radio。若審查需要實際硬體流程，請透過 huangct_2025@liber-ant.com 聯絡發布者安排可重現的測試方式。

受保護的 NTsocial Gateway 只接受套件名稱與正式簽章皆符合信任設定的 NTsocial App；一般審查裝置不需安裝母程式即可檢視 MeshLink 的主要介面。
```

Release 版只會在 Firebase Test Lab 環境顯示 mock transport，一般使用者與人工審查裝置
沒有 Demo Mode。不得把 Test Lab 行為寫成公開功能或人工審查步驟。

## 目標對象與內容

| 題目 | 建議選擇 |
|---|---|
| 目標年齡 | 僅 `18 歲以上` |
| App 是否特別吸引兒童 | 否 |
| 是否加入 Designed for Families | 否 |
| 商店頁是否含可能吸引兒童的角色／兒童導向素材 | 否 |

如 Console 要求說明為何不是兒童 App，可貼：

```text
本 App 是供成年人設定及操作 Meshtastic 相容無線電、頻率、頻道、位置與網狀網路的技術工具。使用者可能與不特定 mesh 參與者交換自由文字、節點資料與位置，內容不由發布者預先審核；產品設計、文案與素材均不以兒童為目標。
```

## IARC 內容分級問卷

依問卷實際措辭選擇下列等價答案：

| 內容／互動題目 | 建議答案 | 原因 |
|---|---|---|
| 使用者能否彼此通訊 | 是 | 頻道訊息與直接訊息 |
| 是否包含 User Generated Content | 是 | 收到的自由文字、節點名稱等由其他使用者產生 |
| 是否分享使用者位置 | 是 | 使用者可主動把手機位置提供給 mesh／MQTT／TAK |
| 是否有封鎖／忽略其他使用者功能 | 是 | 可 ignore／mute 節點並過濾其封包 |
| 是否有向開發者檢舉內容／使用者的功能 | 否 | 目前未找到 App 內檢舉入口；不可答是 |
| 是否有未經審核的線上內容 | 是 | 公開 mesh 內容不由發布者事前審核 |
| 內建暴力內容 | 否 | App 本身沒有 |
| 內建色情／裸露內容 | 否 | App 本身沒有 |
| 內建粗俗語言 | 否 | App 文案沒有；UGC 仍可能出現 |
| 內建毒品、酒精或菸草內容 | 否 | App 本身沒有 |
| 內建賭博或模擬賭博 | 否 | 沒有 |
| 可購買數位商品／隨機商品 | 否 | 無 Billing／IAP |
| 顯示廣告 | 否 | 無廣告 |

目前有 ignore／mute，但沒有 UGC 使用條款接受與向開發者檢舉入口。這是去中心化 radio
通訊在 Play 審查中的政策風險；不要以「資料不在我們伺服器」為理由把 UGC 或使用者
互動回答成否。

公開稿已準備於：

- `https://github.com/nuclear718/ntsocial-mesh-gateway-android/blob/main/docs/google-play/TERMS_OF_USE.md`
- `https://github.com/nuclear718/ntsocial-mesh-gateway-android/blob/main/docs/google-play/COMMUNITY_GUIDELINES.md`

這兩份頁面必須先 push 並可公開開啟；正式版仍須加入可到達的條款／規範、接受流程與
檢舉入口，不能只在 Console 備註 URL 就當作已完成。

## 帳號與資料刪除

| 題目 | 選擇 |
|---|---|
| App 是否允許建立帳號 | 否 |
| 是否需要提供刪除帳號 URL | 不適用 |
| 是否有可操作的遠端分析資料刪除申請流程 | 目前否 |

本 App 沒有雲端 App account。本機資料可在 App 內刪除、由 Android「清除儲存空間」
清除，或隨解除安裝移除。隱私信箱可接受詢問，但在尚未建立可依安裝識別碼處理
Firebase Crashlytics／Datadog 刪除請求的流程前，不要在 Data safety 宣稱已提供完整遠端
刪除機制。

## 其他必填或條件式聲明

| Play Console 聲明 | 建議答案 |
|---|---|
| News／新聞或雜誌 App | 否 |
| Health apps／健康功能 | 不提供任何健康或醫療功能 |
| Financial features／金融功能 | 不提供任何金融功能 |
| Government app／政府 App | 否，未代表政府或提供官方政府服務 |
| COVID-19 接觸追蹤／狀態 | 否 |
| VPN service | 否 |
| 購買、訂閱、付款 | 否 |
| 教育類 App | 否 |
| Dating／交友 | 否 |
| Advertising ID | **否；程式不含廣告 SDK，Google release 合併 Manifest 已驗證不含相關權限** |

裝置遙測、環境感測數值或無線電節點電池資料，不是使用者的健康資料。地圖與位置分享
也不使本 App 成為健康、政府或新聞 App。

## 開源與官方關係

若審查詢問商標、原始碼或與 Meshtastic 的關係，可貼：

```text
NTsocial MeshLink 是以 meshtastic/Meshtastic-Android 為基礎的 GPL-3.0 開源分支，保留上游與第三方著作權及授權聲明。本 App 不是 Meshtastic 官方發行版，也不冒充官方 App。公開原始碼位於：https://github.com/nuclear718/ntsocial-mesh-gateway-android
```
