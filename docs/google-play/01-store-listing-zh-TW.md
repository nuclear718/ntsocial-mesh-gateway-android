# 01｜主要商店資訊（繁體中文）

## 建立 App

| Play Console 欄位 | 選擇／內容 |
|---|---|
| 預設語言 | 中文（繁體）－ `zh-TW` |
| App 或遊戲 | App |
| 免費或付費 | 免費 |
| 套件名稱 | `com.ntsocial.meshlink` |
| 類別 | 通訊（Communication） |
| 標籤 | 從 Console 實際提供的標籤中，優先選「通訊」、「工具」、「地圖與導航」等最貼近功能者，最多 5 個；不要選「社交」來暗示母程式功能。 |

免費 App 公開後不能改成付費；若未來要收費，應使用 Play Billing 的產品或另行規劃。

## App 名稱（上限 30 字元）

```text
NTsocial MeshLink
```

## 簡短說明（上限 80 字元）

```text
連接 NTsocial 與 Meshtastic 相容無線電的開源 LoRa 傳輸閘道
```

## 完整說明（上限 4,000 字元）

```html
NTsocial MeshLink 是連接 NTsocial App 與 Meshtastic 相容無線電的開源傳輸閘道，也可作為 Meshtastic 無線電的管理工具。

您可以透過 Bluetooth、USB 或 TCP 連接相容節點，管理無線電、頻道與裝置設定，查看節點、訊息、位置與遙測資料，並透過 LoRa mesh 傳送文字訊息。

<b>NTsocial Gateway</b>

• 以受保護的 Provider、單次短效命令 capability 與明確事件介面，連接 NTsocial App
• NTsocial 文字與狀態／控制封包使用 PRIVATE_APP／port 256
• 舊版 port 497 僅保留接收相容性，不作為新的送出路徑
• 內建公開 NTsocial Meshtastic 頻道，並在無線電資料庫就緒後檢查註冊狀態
• 暫時性 envelope 快取最多 128 筆；完整社交歷史仍由 NTsocial App 管理
• 圖片、語音及 PTT 媒體 bytes 不會經由 LoRa 傳送

<b>無線電管理</b>

• Bluetooth、USB 與 TCP 連線
• 頻道、節點、無線電與模組設定
• 頻道及直接文字訊息
• 節點清單、地圖、位置、遙測與通知
• QR code、NFC 與相容硬體韌體更新

核心無線電功能不依賴中央 NTsocial 訊息伺服器；但地圖、韌體資訊、MQTT、TAK、外部連結，以及 Google Play 版本的分析與當機回報可能使用網路。分析與當機回報可在設定中關閉。

<b>使用前請注意</b>

• 需要相容的 Meshtastic 無線電；只有手機無法產生 LoRa 通訊
• 公開 mesh 或未加密 MQTT 不應視為私密管道
• 「指令已接受」只表示命令已加入本機無線電佇列，不代表遠端節點已收到
• 無線電可用性受硬體、韌體、區域法規、頻率設定、距離、地形與 airtime 影響
• 本 App 不保證即時或緊急送達，不應作為唯一的緊急通訊方式

<b>開放原始碼</b>

NTsocial MeshLink 是以 meshtastic/Meshtastic-Android 為基礎的 GPL-3.0 開源分支，保留 Meshtastic 上游及第三方貢獻者的著作權與授權聲明。本 App 不是 Meshtastic 官方發行版。

原始碼：
https://github.com/nuclear718/ntsocial-mesh-gateway-android

問題回報：
https://github.com/nuclear718/ntsocial-mesh-gateway-android/issues

隱私權政策：
https://github.com/nuclear718/ntsocial-mesh-gateway-android/blob/main/docs/google-play/PRIVACY_POLICY.md
```

## 聯絡資料

| 欄位 | 貼上內容 |
|---|---|
| 客服電子郵件（必填） | `huangct_2025@liber-ant.com` |
| 網站 | `https://github.com/nuclear718/ntsocial-mesh-gateway-android` |
| 隱私權政策 | `https://github.com/nuclear718/ntsocial-mesh-gateway-android/blob/main/docs/google-play/PRIVACY_POLICY.md` |
| 電話 | 留白；Google Play 主商店頁非必填。 |

提交前確認 Play Console 顯示的發布者確實是 `LiberaNt LLC`，且支援信箱有人收信。若
發布者名稱不同，必須同步修改隱私權政策中的發布者名稱。

## 首次正式版本更新說明（上限 500 字元）

```text
首次 Google Play 發行。提供 Meshtastic 無線電連線與管理、訊息、節點、地圖、遙測、內建 NTsocial 頻道，以及受保護的 NTsocial Gateway。NTsocial 文字與狀態／控制封包使用 port 256；圖片、語音與 PTT 媒體不經 LoRa 傳送。MeshCore 目前僅提供介面與協議基礎，實際傳輸尚未啟用。
```

## 圖像與截圖

### 可保留

- Play icon：`fastlane/metadata/android/en-US/images/icon.png`，512 × 512，NTsocial 綠色蝴蝶
  黑底；上傳前仍應用 Play Console 預覽確認遮罩與邊界。

### 必須重做

- Feature graphic：現有 1024 × 500 圖檔是純黑畫面，不能使用。
- 手機截圖：現有 5 張仍顯示「Meshtastic」、英文模擬節點與座標，不代表目前
  NTsocial MeshLink，也可能不必要地公開位置資訊。

至少準備 4 張 1080 × 1920 或更高的直式正式截圖，建議順序：

1. 連線頁：顯示 Bluetooth／USB／TCP 選擇。
2. 節點頁：顯示節點清單，但使用虛構名稱且不顯示真實座標。
3. 訊息頁：只放虛構、無個資的示範文字。
4. 設定頁：顯示無線電設定、分析開關與位置分享開關。
5. 地圖頁（選填）：使用測試地點，不顯示住家或真實行蹤。
6. NTsocial Gateway 狀態（若有可見 UI）：不得顯示 envelope bytes、PSK、token 或精確位置。

可貼入替代文字（每張上限 140 字元）：

```text
NTsocial MeshLink 連線頁，提供 Bluetooth、USB 與 TCP 相容無線電連線選項。
```

```text
NTsocial MeshLink 節點頁，以卡片顯示測試節點、訊號與裝置狀態。
```

```text
NTsocial MeshLink 訊息頁，顯示測試頻道中的離網文字訊息。
```

```text
NTsocial MeshLink 設定頁，提供無線電、隱私與位置分享控制。
```

```text
NTsocial MeshLink 地圖頁，顯示虛構測試節點的位置與連線狀態。
```

Feature graphic 必須為 1024 × 500 JPEG 或無透明 24-bit PNG，視覺應使用黑底、
NTsocial 蝴蝶與 Meshtastic 綠 `#67EA94`，但不得使用 Google Play badge、排名、價格、
「最佳」、「第一」或「立即下載」等文字。
