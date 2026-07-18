# Google Play 商店文案與素材

最後更新：2026 年 7 月 18 日

以下文字只描述目前 cloud-free 版本。送出前仍要用正式 AAB 與實機畫面做最後校對。

## App 基本資料

| Play Console 欄位 | 填寫值 |
|---|---|
| App 名稱 | `NTsocial MeshLink` |
| Package | `com.ntsocial.meshlink` |
| 預設語言 | 繁體中文（`zh-TW`） |
| App／Game | App |
| 免費／付費 | 免費 |
| 類別 | Communication／通訊 |
| 發布者 | `LiberaNt LLC`（送出前確認與 Console 法定資料一致） |
| 支援信箱 | `huangct_2025@liber-ant.com` |

## 標題（30 字元內）

```text
NTsocial MeshLink
```

## 短描述（80 字元內）

```text
連接 Meshtastic 無線電，讓 NTsocial 透過 LoRa mesh 傳送文字與狀態
```

## 完整描述（4,000 字元內）

```text
NTsocial MeshLink 是開放原始碼的 Meshtastic 無線電連線與傳輸閘道，協助 NTsocial App 在相容的 LoRa mesh 上交換文字與狀態資料。

主要功能
• 透過 Bluetooth、USB 或 TCP 連接相容的 Meshtastic 無線電
• 查看訊息、節點、頻道、連線狀態與基本無線電設定
• 以受套件與簽章驗證保護的 Gateway 與 NTsocial App 交換資料
• 在裝置上離線掃描 QR／條碼，不將相機畫面送到雲端
• 顯示與複製座標、查看節點距離與指南針
• 由使用者選擇是否將手機位置提供給連接的 mesh

隱私與去中心化
本版本不含廣告、Google Maps、Google Play Services runtime、Firebase、Crashlytics、Datadog、ML Kit 或其他分析／當機回報 SDK，也不建立發布者雲端帳號。資料主要保存在你的 Android 裝置與無線電；只有在你使用通訊、位置分享或自行設定的 MQTT、TAK、TCP 功能時，相關資料才會送往你選擇的裝置、mesh 參與者或端點。

使用前須知
部分功能需要相容的 Meshtastic 無線電與所在地允許的頻率、功率及設定。公開頻道或使用者自行設定的服務不一定端對端加密。LoRa 傳輸可能延遲、遺失或受干擾；本 App 不保證遠端送達，也不應作為唯一的緊急通訊方式。

NTsocial MeshLink 是 meshtastic/Meshtastic-Android 的 GPL-3.0 開源分支，不是 Meshtastic 官方發行版。

原始碼：
https://github.com/nuclear718/ntsocial-mesh-gateway-android

支援與問題回報：
https://github.com/nuclear718/ntsocial-mesh-gateway-android/issues
```

## 第一版更新說明

```text
NTsocial MeshLink 第一版：支援 Bluetooth、USB 與 TCP Meshtastic 無線電連線、訊息與節點管理、受保護的 NTsocial Gateway，以及裝置內離線 QR／條碼掃描。本版本不含地圖、廣告、分析或第三方當機回報 SDK。
```

## 聯絡與政策 URL

| 欄位 | 填寫方式 |
|---|---|
| Website | `https://github.com/nuclear718/ntsocial-mesh-gateway-android` |
| Support email | `huangct_2025@liber-ant.com` |
| Privacy policy | 上線前發布的穩定公開 HTTPS URL |
| Terms | 上線前發布的穩定公開 HTTPS URL |
| Community guidelines | 上線前發布的穩定公開 HTTPS URL |

Repository 中的 Markdown／GitHub blob URL只作草稿與版本紀錄。正式隱私政策頁應為無需
登入、無地區限制、非 PDF、一般使用者不可編輯的穩定 HTTPS 頁面，並從 App 內直接到達。

## 必備圖像

- App icon：512 × 512 PNG，使用 NTsocial 蝴蝶與既有綠色 `#67EA94`／黑色品牌系統；
- Feature graphic：1024 × 500 JPEG 或 24-bit PNG；
- 手機截圖：至少 2 張，建議準備 4 張目前版本的直式截圖；
- 每張截圖的替代文字／圖說應描述真實功能，不加入未實作承諾。

建議截圖順序：

1. 連線頁：Bluetooth／USB／TCP 選項；
2. 訊息頁：使用完全虛構、無個資的測試訊息；
3. 節點頁：遮蔽 node ID、精確座標與可識別裝置資訊；
4. 設定或 Gateway 狀態頁：不顯示 PSK、token、envelope bytes 或 signer digest。

所有素材都不得出現已移除的地圖頁、Google／Firebase／Crashlytics 功能、真實位置、私人
訊息、PSK、token、裝置序號、通知內容或測試者個資，也不得使用上游 Meshtastic 山形標誌
作為主要 NTsocial 品牌。

## 舊 Fastlane 中繼資料警告

`fastlane/metadata/android` 的 `zh-TW`、`en-US` 與其他語系仍可能含地圖、analytics 或
Meshtastic 官方 App 的舊文案／截圖。第一版禁止直接上傳這些資料；即使 Fastfile 目前
跳過 metadata，也要確認手動上傳只使用本文件核對過的新素材。

官方規格：[商店資訊](https://support.google.com/googleplay/android-developer/answer/9859152)、
[圖像資產](https://support.google.com/googleplay/android-developer/answer/9866151)。
