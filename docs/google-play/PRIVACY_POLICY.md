# NTsocial MeshLink 隱私權政策

最後更新：2026 年 7 月 19 日

> **送審前草稿：** location 行為、App 內政策 URL 與最終 AAB 尚待核對。完成後請移除本段，
> 再把此政策發布成正式公開 HTTPS 頁面；目前不可直接拿本檔 URL 送審。

本政策適用於 LiberaNt LLC 發布的 **NTsocial MeshLink** Android App（套件名稱
`com.ntsocial.meshlink`，以下稱「本 App」）。本 App 由 LiberaNt LLC 與 NTsocial 團隊
主導開發、整合及持續維護，是 Android NTsocial App 的核心開源 companion app，用於連接
Meshtastic 相容無線電，並在 NTsocial App 與 LoRa mesh 間提供受保護的傳輸閘道。

LiberaNt LLC 對本 App 特有的原創程式、可受著作權保護的修改、Gateway／母程式整合與
文件主張著作權，並承擔本政策所述的發布者責任。合併作品依 GPL-3.0-or-later 開放原始碼；
Meshtastic Android 衍生部分、MeshCore 參考材料與個別貢獻者仍保留各自權利。本 App 不是
Meshtastic 或 MeshCore 官方發行版，也不表示其贊助或背書。完整來源見 repository 的
`NOTICE.md` 與 `THIRD_PARTY_NOTICES.md`。

## 1. 最重要的隱私事實

- 本 App 不要求註冊，也不建立發布者雲端帳號；
- 本 App 不含廣告、Google Maps、Google Play Services runtime、Firebase、Crashlytics、
  Datadog、ML Kit 或其他第三方分析／當機回報 SDK；
- 發布者不營運用來接收或保存 App 使用分析、installation ID、使用者意見、當機堆疊或
  session 的後端；
- QR／條碼影像只在裝置上以 ZXing 解碼，不會為了解碼上傳；
- 資料主要保存在使用者的 Android 裝置與相容無線電；
- 當使用者傳送訊息、分享位置、連接 NTsocial App 或啟用自選 MQTT／TAK／TCP 端點時，
  相關資料會依使用者設定離開手機。這些通訊不是發布者分析收集，但接收者可能保存副本。

Google Play 的 Data safety 將「由 App 傳出裝置」廣義視為 collection。因此本 App 在 Play
上的申報可能包含通訊資料，即使發布者沒有中央資料庫；本政策不以一般語意的「我們沒有
後端」取代 Play 表單定義。

## 2. 本 App 處理的資料

### Radio、節點與頻道

為了連接及管理無線電，本 App 可能處理裝置型號、韌體版本、Bluetooth／USB／TCP
連線資訊、node ID、節點名稱、頻道、radio 設定、頻道金鑰、封包狀態、訊號品質、電池
及遙測。這些資料通常保存在本機資料庫或所連接的 radio。

頻道金鑰、精確位置與完整 radio 設定不會放進公開 Gateway 事件；受保護 Provider 的
節點／頻道快照也會排除 PSK、位置、備註、原始 protobuf 與敏感 radio 設定。

### 訊息與使用者產生內容

本 App 可顯示、保存、傳送及接收頻道訊息、直接訊息、反應、節點名稱和其他 mesh
封包。本機紀錄會保留到使用者刪除、清除 App 資料或解除安裝。NTsocial Gateway 的
envelope 快取只存在記憶體，最多 128 筆，程序結束後清除。

公開頻道、Ham mode、共享 PSK、MQTT 或第三方橋接不保證只有原始收件者能讀取內容。
其他 mesh 參與者可能保存或再轉送資料，發布者通常無法刪除其副本。

### 位置

本 App 沒有地圖頁，但可顯示／複製座標、計算節點距離、使用指南針，並可讓使用者選擇
將手機位置提供給 mesh。只有在使用者啟用相關功能並授予必要權限後，App 才應取得手機
位置，並依設定傳給 radio、mesh 或自選 MQTT／TAK 端點。使用者可關閉功能或在 Android
設定撤銷權限。

舊版 Android 的 Bluetooth 掃描可能需要位置權限；單純授權不代表位置一定會傳送。

### 相機、NFC 與本機網路

- 相機只在使用者開啟 QR／條碼掃描時使用，frame 在裝置內即時處理；
- NFC 用於讀取或分享相容頻道／聯絡資訊；
- Bluetooth 與 USB 用於搜尋、配對及維持 radio 連線；
- 本機網路用於 TCP、mDNS／NSD 探索及使用者啟用的 TAK 功能；
- 通知與前景服務用於顯示持續 radio 連線與選用位置提供狀態。

## 3. 資料可能傳給誰

依使用者操作與設定，資料可能送到：

1. 使用者連接的 Meshtastic radio、mesh 節點、頻道成員或直接訊息對象；
2. 通過套件、UID 與簽章驗證的 NTsocial App；
3. 使用者自行設定的 MQTT、TAK、TCP 或其他端點；
4. 使用者開啟的硬體／韌體資訊來源或外部網站；
5. 發布、安裝及更新本 App 的 Google Play／Android 平台。

外部網路服務通常會收到完成連線所必要的 IP 位址與請求內容，並依其政策處理。本 App
不出售資料、不投放廣告，也不把資料送給地圖、分析或當機診斷服務商。

若從 Google Play 安裝，Google／Android 可能依裝置設定處理安裝、安全檢查及 Android
Vitals，並在 Play Console 提供品質資訊。這是 Play 平台行為，不是本 App 內嵌
Crashlytics，也不需要發布者的 GCP 或 Firebase 專案。

## 4. 保存與刪除

- 本機資料：可由 App 內功能、Android「清除儲存空間」或解除安裝刪除；Android 雲端
  備份已停用；
- Gateway envelope：最多 128 筆記憶體快取，程序結束即清除；
- 發布者分析／當機後端：本版本沒有；
- mesh、radio、MQTT、TAK、TCP 或其他接收者副本：由各接收者與其設定決定，發布者無法
  保證遠端刪除。

本 App 沒有發布者帳號，因此沒有帳號刪除流程。使用者主動寄送支援信時，信件會由信箱
服務依其政策保存；請勿寄送 PSK、token、精確位置或私人訊息全文。

## 5. 安全與使用者選擇

本 App 使用 Android 沙箱、runtime 權限、套件／簽章驗證與最小化 Gateway 資料面。
支援 TLS 的網路端點會使用相應傳輸，但 LoRa、公開 mesh、Ham mode 或未加密 MQTT 不保證
端對端加密。LoRa 也可能延遲、遺失或受干擾，不能作為唯一的緊急通訊方式。

使用者可拒絕非必要權限、不連接 radio、不傳送訊息、不啟用手機位置或自選網路端點。

## 6. 兒童、跨境與第三方

本 App 是需要無線電與網路設定知識的技術工具，不是為兒童設計；Play 首發目標對象規劃
為 18 歲以上。mesh 參與者、使用者自選端點及 Google Play 可能在其他國家／地區處理
資料，並適用各自的條款與政策。

本政策不適用於獨立 NTsocial App、Meshtastic 韌體、使用者自行設定的伺服器、Google
Play 或使用者主動開啟的第三方網站。

## 7. 變更與聯絡

若資料行為或服務提供者改變，我們會更新本政策與日期，並同步更新 Play Data safety。

- 發布者：LiberaNt LLC
- 開發與維護：LiberaNt LLC 與 NTsocial 團隊
- 隱私與支援信箱：huangct_2025@liber-ant.com
- 原始碼：https://github.com/nuclear718/ntsocial-mesh-gateway-android
- 問題回報：https://github.com/nuclear718/ntsocial-mesh-gateway-android/issues
