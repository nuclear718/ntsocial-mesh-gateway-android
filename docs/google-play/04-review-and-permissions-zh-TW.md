# 04｜審查存取、權限與前景服務

## 審查人員存取說明

貼入 App access：

```text
本 App 不需帳號、密碼、OTP、會員資格、訂閱或付費。首次啟動可略過非必要權限並進入主要介面。

無 Meshtastic 硬體時：可直接開啟「連線」、「訊息」、「節點」、「地圖」、「MeshCore」與「設定」頁；沒有登入、付費牆或隱藏導覽，但無法建立 radio session 或產生真實節點資料。

有相容硬體時：使用者可在「連線」頁選擇 Bluetooth、USB 或 TCP。實際無線電設定寫入與 LoRa 收發需要相容的 Meshtastic radio，審查不需任何測試帳密。

若審查需要實際硬體流程，請透過 huangct_2025@liber-ant.com 聯絡發布者安排可重現的測試方式。

受保護 NTsocial Gateway 的跨 App 存取只允許通過套件與簽章驗證的 NTsocial App；這不影響審查人員直接操作 MeshLink 主介面。
```

Release 版一般使用者與人工審查裝置沒有 Demo Mode；只有 Firebase Test Lab 環境可啟用
mock transport。不得在審查說明中要求人工審查人員選擇不存在的 Demo 項目。

## 權限用途總表

| 權限／能力 | 對使用者的用途 |
|---|---|
| Bluetooth Scan／Connect | 搜尋、配對並維持 Meshtastic radio 連線 |
| Fine／Coarse Location | 使用者主動提供手機位置給 mesh、地圖定位；舊版 Android Bluetooth 掃描相容 |
| Notifications | 持續連線、訊息、節點與前景服務狀態通知 |
| Internet／Network State | Google Maps、韌體資訊、Firebase Crashlytics／Datadog、TCP、MQTT、TAK 與外部連結 |
| Local Network | mDNS／NSD 裝置探索、LAN TCP 與 localhost TAK server |
| Foreground Service: connectedDevice | 螢幕關閉或切換 App 後維持使用者選擇的 radio 連線 |
| Foreground Service: location | 實際 GPS 取得與傳送只在位置提供開啟時發生；目前 service type 的啟用條件另有送審阻擋，見下方 |
| Boot completed | 已有使用者選擇的裝置時恢復 radio 服務 |
| Camera | 使用者主動掃描 QR code |
| NFC | 讀取／分享頻道與聯絡資訊 |
| Wake lock | 維持必要的即時 radio 處理 |
| Wi-Fi multicast | 本機網路裝置探索 |
| USB host | 連接相容 USB serial radio |

目前正式來源沒有 `ACCESS_BACKGROUND_LOCATION`、麥克風、系統聯絡人、SMS、通話紀錄、
相片／影片讀取、`MANAGE_EXTERNAL_STORAGE`、`QUERY_ALL_PACKAGES`、VPN、
`REQUEST_INSTALL_PACKAGES`、exact alarm 或 full-screen intent。

## 位置權限顯著揭露文字

下列文字是**必須實作在 App 內、且顯示於 runtime permission 前**的建議稿，不是只貼到
Console 就會生效：

```text
位置分享

只有在您開啟「將手機位置提供給 Mesh 網路」並授予位置權限後，NTsocial MeshLink 才會讀取手機的精確位置。位置可在 App 位於背景、但持續顯示前景服務通知時，透過您目前連接的 Meshtastic radio 傳送到 mesh；若另啟用 MQTT 或 TAK，也可能傳送到您選擇的伺服器及其他參與者。

公開頻道或未加密 MQTT 不一定具備端對端加密。您可以隨時在 App 設定關閉位置提供，或在 Android 系統設定撤銷權限。
```

揭露必須顯示在系統位置權限對話框**之前**，不能只放在隱私權政策或商店頁。
目前首次位置畫面尚未完整寫出背景持續取得、mesh／MQTT／TAK 接收者與未加密風險，
因此在正式 App 補齊並實機驗證前，不能依下方腳本錄影或宣稱顯著揭露已完成。

## Foreground service：Connected device

### 功能說明

```text
此 connectedDevice 前景服務用於維持使用者主動選擇之 Meshtastic 無線電節點的 Bluetooth、USB 或 TCP 連線，使網狀網路訊息、節點狀態及必要控制資料能在使用者切換畫面或螢幕關閉後持續收發。服務會顯示持續通知；使用者在 App 中取消裝置選擇後即停止。無線電意外離線時，服務可能保留通知並嘗試重新連線。
```

### 為何必須立即且持續

```text
Meshtastic radio 是持續連線的外接裝置。若延後執行，App 會中斷 radio session，無法即時接收封包、節點狀態或完成使用者已要求的傳送。WorkManager 適合可延後的批次工作，無法取代即時 Bluetooth／USB／TCP 連線。
```

### 中斷影響

```text
中斷服務會使無線電離線，停止即時訊息與節點更新，並可能讓本機傳送佇列失敗；重新開啟 App 後需重新建立連線。
```

## Foreground service：Location

### 目前必須先修正的 runtime 差異

目前 `MeshService` 只要已有位置權限，就會把 `FOREGROUND_SERVICE_TYPE_LOCATION` 加入
前景服務，即使「將手機位置提供給 Mesh 網路」仍為關閉；實際 GPS 更新與傳送才有受
開關控制。這和下方預期的使用者啟動條件不一致，是送審阻擋。最正確的產品修正是只在
使用者開啟位置提供、且實際需要位置時加入 location service type，再用正式 AAB 與影片
驗證。單靠修改 Console 文案不能消除 runtime 差異。

### 完成上述程式修正後可貼的功能說明

```text
只有在使用者授予位置權限並主動開啟「將手機位置提供給 Mesh 網路」後，location 前景服務才會取得手機 GPS 位置，並透過目前連接的 Meshtastic radio 提供給 mesh，用於位置分享、距離與地圖功能。關閉此設定或中斷 radio 會停止位置傳送。本 App 不要求 ACCESS_BACKGROUND_LOCATION。
```

### 為何必須立即且持續

```text
使用者開啟位置提供後，mesh 上的節點需要在 App 不位於前景時仍取得合理時效的位置更新。延後批次執行會使位置過期，導致地圖、距離與協作資訊不正確；前景服務同時向使用者顯示持續通知。
```

### 中斷影響

```text
中斷後只會停止新的手機位置更新，不會影響使用者關閉位置分享的能力；mesh 上既有的舊位置可能持續到各節點自行過期或刪除。
```

## 前景服務示範影片腳本

Play Console 可能要求可公開或不公開、可由審查人員直接觀看的 YouTube URL。影片不要
顯示真實住址、PSK、token、裝置序號、私人訊息或正式服務憑證。

只有在 App 內顯著揭露與 location FGS runtime 條件都修正後，才錄製下列 60–90 秒影片：

1. 從乾淨安裝啟動，略過非必要權限並進入連線頁。
2. 選擇測試 radio，顯示連線開始。
3. 回到桌面或關閉螢幕後再喚醒，顯示持續的 radio 前景服務通知。
4. 回 App 開啟「將手機位置提供給 Mesh 網路」。
5. 顯示顯著揭露與 Android 位置權限對話框，再授權。
6. 顯示通知仍存在，地圖／節點位置開始更新。
7. 關閉位置提供，證明位置更新停止。
8. 在 App 內取消裝置選擇，證明 connected-device 服務與通知停止；單純讓 radio 意外離線可能觸發自動重連。

貼入影片描述：

```text
NTsocial MeshLink foreground service declaration demo: user-initiated Meshtastic radio connection (connectedDevice), optional phone-to-mesh location sharing (location), persistent notification, and user stop controls. No background-location permission is requested.
```

## Advertising ID 聲明

產品答案：

```text
Does your app use advertising ID? No.
```

已完成的程式處理：

- 已移除 Firebase Analytics 與其 Play Services Measurement、Ads Identifier、Privacy
  Sandbox Ads 傳遞依賴；
- 已移除 Firebase Analytics 的 consent、事件與 metadata 程式碼；一般選用分析事件改由
  Datadog RUM 處理，Firebase 僅保留 Crashlytics；
- Google release 建置加入自動防回歸檢查，若合併 Manifest 再出現 `AD_ID`、
  `ACCESS_ADSERVICES_ATTRIBUTION`、`ACCESS_ADSERVICES_AD_ID`、Play Install Referrer、
  `android.ext.adservices` 或 AppMeasurement 元件，組裝／Bundle 任務會失敗。

2026-07-17 重建的 Google release 合併 Manifest 已確認沒有 Advertising ID、AdServices、
Install Referrer、AppMeasurement 或 `android.ext.adservices`。正式送審時仍應用 Play Console
App Bundle Explorer 再核對實際上傳 AAB，確認與本次驗證結果一致。
