# 審查存取、權限與前景服務

最後更新：2026 年 7 月 19 日

## 審查人員存取說明

貼入 App access：

```text
NTsocial MeshLink（com.ntsocial.meshlink）由 LiberaNt LLC 發布，並由 LiberaNt LLC 與 NTsocial 團隊主導開發、整合及維護。它是 Android NTsocial App 的開源 companion／radio gateway，不是 Meshtastic 或 MeshCore 官方發行版。

本 App 不需帳號、密碼、OTP、會員資格、訂閱或付費。審查人員可略過非必要權限並直接進入連線、訊息、節點、MeshCore 與設定頁；本版本沒有地圖頁。

實際 Bluetooth、USB、TCP radio session 與 LoRa 收發需要相容的 Meshtastic 無線電，但不需任何測試帳密。無硬體時仍可檢查主要 UI、設定及權限流程。若審查需要硬體流程，請聯絡 huangct_2025@liber-ant.com 取得可重現的測試安排。

受保護的 NTsocial Gateway 只允許通過套件與簽章驗證的 NTsocial App 存取；這不影響審查人員直接操作 MeshLink 主介面。
```

Release 版沒有 Demo Mode，也不需要 Firebase Test Lab、GCP 或審查伺服器。不要在審查說明
中要求不存在的 Demo 選項。

若審查詢問第三方名稱或來源，提供 repository 的 GPL `LICENSE`、`NOTICE.md`、
`THIRD_PARTY_NOTICES.md` 與 fork／commit 紀錄。LiberaNt 對自己的 NTsocial MeshLink 原創、
修改與整合負責；不要把這寫成 Meshtastic／MeshCore 的官方授權、贊助或背書。

## 權限用途

| 權限／能力 | 使用者可見用途 |
|---|---|
| Bluetooth Scan／Connect | 搜尋、配對並維持 Meshtastic radio 連線 |
| Fine／Coarse Location | 選用的手機位置提供與節點距離；舊 Android 的 Bluetooth 掃描相容 |
| Notifications | 顯示持續 radio 連線、訊息與前景服務狀態 |
| Internet／Network State | TCP、MQTT、TAK、硬體／韌體資訊與使用者開啟的外部連結；不供地圖或診斷 SDK |
| Local network／Multicast | mDNS／NSD 探索、LAN TCP 與本機 TAK |
| Foreground service: connectedDevice | 螢幕關閉或切換 App 後維持使用者選擇的 radio 連線 |
| Foreground service: location | 只應在使用者主動啟用手機位置提供時持續取得位置；目前實作仍需修正 |
| Camera | 使用者開啟 QR／條碼掃描時在裝置內以 ZXing 解碼 |
| NFC | 讀取或分享相容頻道／聯絡資訊 |
| USB host | 連接相容 USB serial radio |
| Boot completed／Wake lock | 恢復已選擇的 radio 服務並維持必要即時處理 |

目前正式 Manifest 沒有 `ACCESS_BACKGROUND_LOCATION`、麥克風、系統聯絡人、SMS、通話
紀錄、相片／影片讀取、`MANAGE_EXTERNAL_STORAGE`、`QUERY_ALL_PACKAGES`、VPN、
`REQUEST_INSTALL_PACKAGES`、exact alarm 或 full-screen intent。

## Location：送審前必須做決策

目前 `targetSdk` 是 37，onboarding 會要求 Fine＋Coarse location；`MeshService` 只要已有
位置權限就加入 location FGS，而不是等使用者開啟手機位置提供。這和「使用者啟動、最小
範圍、可停止」的預期不一致，不能靠 Console 文案修正。

送審前二選一：

1. **保留手機位置提供**：只在使用者點選功能後顯示揭露、要求最小必要權限並啟動
   location FGS；關閉功能後停止位置取得與 location 類型。另依 Android 17 minimum-scope
   規則評估 coarse、precise 與 location button。
2. **最小首發**：移除手機位置提供與 location FGS，只保留舊 Android Bluetooth 掃描真正
   需要的最低位置權限範圍。

Google 預計在 2026 年 10 月下旬對 target Android 17+ 的 foreground precise location
minimum-scope／location button 開始執法，應在送審當天再次核對：
[官方說明](https://support.google.com/googleplay/android-developer/answer/17033915)。

## 保留位置功能時的顯著揭露

這段必須在 App 正常流程中、系統權限對話框前獨立顯示，並由使用者肯定同意：

```text
手機位置提供

NTsocial MeshLink 只有在你開啟「將手機位置提供給 Mesh 網路」後，才會取得手機位置。當 radio 連線與前景服務通知持續顯示時，App 即使不在畫面上，也可能把位置傳給你連接的 Meshtastic radio、mesh 參與者，以及你另外啟用的 MQTT 或 TAK 端點。

公開頻道或未加密 MQTT 不一定端對端加密。你可以隨時關閉手機位置提供，或在 Android 設定撤銷權限。
```

必須有「同意並繼續」及「不要啟用」兩個清楚選項；返回、離開或逾時不能算同意。現有
版本在完成程式修正與實機驗證前，不得宣稱這項揭露已完成。

## Foreground service：connectedDevice

### 功能說明

```text
connectedDevice 前景服務用於維持使用者主動選擇之 Meshtastic 無線電的 Bluetooth、USB 或 TCP 連線，使訊息、節點狀態及必要控制資料能在切換 App 或螢幕關閉後持續收發。服務會顯示持續通知；使用者取消裝置選擇後即停止。
```

### 延後或中斷的影響

```text
Meshtastic radio 是持續連線的外接裝置。延後會中斷 radio session，無法即時接收封包或完成使用者要求的傳送；中斷後會停止訊息與節點更新，並可能使本機傳送佇列失敗。可延後的 WorkManager 無法取代即時 Bluetooth、USB 或 TCP 連線。
```

## Foreground service：location

只有完成上述程式修正後才能使用這段。

### 功能說明

```text
只有在使用者授予必要位置權限並主動開啟「將手機位置提供給 Mesh 網路」後，location 前景服務才會取得手機位置並透過目前連接的 Meshtastic radio 提供給 mesh。關閉此設定會停止位置取得與傳送。本 App 不要求 ACCESS_BACKGROUND_LOCATION。
```

### 延後或中斷的影響

```text
使用者啟用位置提供後，延後執行會讓 mesh 上的位置與距離資料過期；中斷只會停止新的手機位置更新。前景服務以持續通知讓使用者知道功能正在執行，並可從 App 關閉。
```

## FGS 示範影片

Play Console 對 target Android 14+ 的 FGS 聲明通常要求功能、延遲／中斷影響及可直接觀看
的示範影片。修正 runtime 後錄製 60–90 秒：

1. 乾淨安裝，略過位置並進入主要 UI；
2. 選擇測試 radio，顯示持續 connected-device 通知；
3. 回 App 點選手機位置提供；
4. 顯示 App 內揭露，再顯示 Android 權限對話框；
5. 授權後顯示位置功能與持續通知；
6. 關閉位置提供，證明位置更新停止；
7. 取消 radio 選擇，證明 connected-device 服務停止。

影片不得顯示真實住址、PSK、token、裝置序號、私人訊息或正式憑證。若首發移除 location
FGS，就只示範 connectedDevice，不要提交不存在的 location 功能。

## Advertising ID

```text
Does your app use advertising ID? No.
```

正式 AAB 仍須用 App Bundle Explorer 確認沒有 `AD_ID`、AdServices、Install Referrer、
AppMeasurement、Firebase、Datadog、Maps 或 ML Kit 元件。

官方參考：[FGS 聲明](https://support.google.com/googleplay/android-developer/answer/13392821)、
[User Data](https://support.google.com/googleplay/android-developer/answer/10144311)、
[敏感權限](https://support.google.com/googleplay/android-developer/answer/16558241)、
[Impersonation](https://support.google.com/googleplay/android-developer/answer/9888374)、
[Intellectual Property](https://support.google.com/googleplay/android-developer/answer/9888072)。
