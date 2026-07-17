# 03｜Data safety 資料安全表單

## 填寫前提

以下是對目前 Google flavor 的保守申報。正式提交前必須以「實際上傳的 AAB」重做
SDK／Manifest 稽核，並確認 Firebase Crashlytics、Datadog 與地圖服務的正式設定。

目前程式事實：

- Firebase Crashlytics 與 Datadog 會在分析偏好允許時傳送資料；新安裝的
  分析偏好目前預設為開啟，使用者可在設定中退出。
- 使用者可選擇把名稱、節點 ID、訊息與位置送到 radio／mesh／MQTT／TAK。
- Google Play 正式版不啟用 Datadog Session Replay；只有 Debug 建置啟用且遮罩輸入。
- App 不顯示廣告、不使用付款，也不收集 Advertising ID；Firebase Analytics、Play
  Services Measurement、Ads Identifier 與 Privacy Sandbox Ads 依賴均已移除。

## Overview

| 問題 | 建議答案 |
|---|---|
| App 是否收集或分享必要的使用者資料類型 | 是 |
| 所有收集資料是否在傳輸時加密 | 否 |
| 使用者是否可要求刪除資料 | 目前否 |
| 是否通過獨立安全審查 | 否，除非日後取得 Play 認可的有效審查 |
| 是否允許建立帳號 | 否 |

「傳輸時加密」必須答否，因為即使 Firebase Crashlytics／Datadog 使用 TLS，公開
Meshtastic 頻道、
Ham mode 或使用者同意的未加密 MQTT 位置回報不保證所有離開裝置的資料都加密。

## 是否分享給第三方

**建議總體選擇：不分享（No data shared with third parties）**，但只在下列條件均成立時
使用：

1. Firebase Crashlytics、Datadog、地圖及託管服務在合約與實務上是代表發布者處理資料
   的 service providers；
2. Mesh 訊息、名稱與位置是使用者主動要求傳送，符合 user-initiated transfer 例外；
3. NTsocial App 與 MeshLink 由同一發布者／第一方負責，且受保護 IPC 的揭露與使用者
   預期一致；
4. 沒有把資料出售、用於跨客戶廣告画像或提供給其他未披露的公司。

任何一項不成立，就要把對應資料類型改成「有分享」。即使 Data safety 使用例外而選
「不分享」，隱私權政策仍須完整說明實際接收者。

## 應勾選的資料類型

每一列均選「Collected：Yes」、「Shared：No」、「Processed ephemerally：No」。

| Play 類別 | 資料類型 | Required/Optional | 使用目的 |
|---|---|---|---|
| Location | Approximate location | Optional | App functionality、Analytics |
| Location | Precise location | Optional | App functionality |
| Personal info | Name | Optional | App functionality |
| Personal info | User IDs | Optional | App functionality |
| Messages | Other in-app messages | Optional | App functionality |
| App activity | App interactions | Optional | Analytics |
| App info and performance | Crash logs | Optional | Analytics |
| App info and performance | Diagnostics | Optional | Analytics |
| App info and performance | Other app performance data | Optional | Analytics |
| Device or other IDs | Device or other IDs | Optional | Analytics、App functionality |

### 逐項理由

- Approximate location：Datadog 可能由 IP 推導國家／地區；位置功能也可能
  提供概略位置。
- Precise location：使用者授權並開啟「提供手機位置」後，可送往 mesh、MQTT 或 TAK。
- Name：Meshtastic long name／short name 可由使用者設定並傳送。
- User IDs：Meshtastic node ID／user ID 會在 mesh 與本機節點資料庫中處理。
- Other in-app messages：頻道及直接文字訊息由使用者主動收發，並可保存在本機。
- App interactions：Datadog RUM／自訂使用事件與 connect action。
- Crash logs：Firebase Crashlytics 與 Datadog crash reports。
- Diagnostics：ANR、long tasks、trace、錯誤、網路與連線診斷日誌。
- Other app performance data：RUM 畫面時間、背景事件、資源與效能資料。
- Device or other IDs：Firebase Crashlytics installation UUID、Datadog session ID，以及
  診斷日誌中的 radio／BLE／TCP 識別資訊。

「Optional」是因為使用者可不設定名稱、不傳送訊息／位置，並可在設定關閉分析；但
分析目前是 opt-out 而非事前 opt-in。不要在商店文案或政策中宣稱「預設不收集」。若
Play Console 對「使用者在第一次傳送前無法退出」採更嚴格判定，分析相關列應改選
Required。最穩健的產品修正是上架前把分析預設改為 false 並提供明確 opt-in。

## 不應勾選的資料類型

除非正式 AAB 或產品設定另有新增，以下選「否」：

- Email address、Phone number、Address、Race／ethnicity、Political／religious beliefs、
  Sexual orientation、Other personal info；
- Credit card、Purchase history、Credit score、Other financial info；
- Health info、Fitness info；
- Emails、SMS／MMS；
- Photos、Videos、Audio files、Music files；
- Files and docs；
- Calendar、Android 系統 Contacts；
- In-app search history、Installed apps、Web browsing history；
- Advertising or marketing purpose、Account management purpose。

相機只掃描 QR code，NFC 只處理相容分享資料；目前沒有相片／影片讀取、麥克風、系統
聯絡人、SMS、通話紀錄、行事曆、付款或 Billing 權限。

## Security practices

建議依序回答：

```text
Data encrypted in transit: No
Users can request data deletion: No（目前營運流程）
Independent security review: No
Committed to Google Play Families Policy: No／Not applicable
```

本機 Room、DataStore 與訊息資料位於 App sandbox，Android backup 與裝置轉移均停用。
本機資料可由 App 刪除、Android 清除儲存空間或解除安裝移除。遠端分析目前沒有可驗證
的逐一刪除工作流程；若日後建立並實際執行刪除申請機制，再把答案改成 Yes，並填入
直接到達刪除說明的公開 URL。

## 第三方服務核對清單

提交前由有權限的人確認：

- Firebase Crashlytics 收集狀態與未送出報告行為；
- Datadog RUM／Logs／Trace 的正式 retention、IP／GeoIP 設定與刪除流程；
- Google Maps API key 的限制與資料處理條款；
- 最終正式 AAB 是否仍未含 measurement、install referrer、Advertising ID 或 AdServices SDK；
- 發布者與 NTsocial App 是否確實屬同一第一方資料控制者。

官方定義與例外：
[Google Play Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)
