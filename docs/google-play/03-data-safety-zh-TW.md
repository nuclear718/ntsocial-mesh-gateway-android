# Data safety 填寫稿

最後更新：2026 年 7 月 18 日

## 先分清楚兩件事

產品事實是：本版本沒有發布者分析／當機後端，也不內嵌 Firebase、Crashlytics、Datadog、
Maps、Play Services runtime 或 ML Kit；QR frame、偏好與本機資料庫也不會為了分析離開
裝置。

但是 Google Play 對 `collection` 的定義比「發布者有沒有伺服器」更廣：使用者資料只要
由 App 傳出 Android 裝置，可能就要申報。現有功能會把訊息、節點名稱／ID、選用位置或
其他資料送到 radio、mesh、受信任 NTsocial App 或使用者自行設定的端點，因此不能只因
移除雲端 SDK 就回答「不收集任何資料」。

官方定義與例外以
[Data safety 說明](https://support.google.com/googleplay/android-developer/answer/10787469)
為準：純本機處理不算 collection；符合嚴格條件的端對端加密資料可排除；特定的使用者
主動傳輸或符合顯著揭露與同意的傳輸可能不算 sharing。公開頻道、共享 PSK、未加密 MQTT
或可由中介者讀取的資料，不可直接套用端對端加密例外。

## 現有首發功能的保守答案

在沒有關閉 mesh 訊息、手機位置與自選網路端點的前提下，建議 Overview 如下：

| 問題 | 建議答案 | 原因 |
|---|---|---|
| App 是否 collect 或 share 任何必要資料類型 | **Yes** | 核心通訊會把部分資料傳出 Android 裝置 |
| 所有 collected data 是否都在傳輸時加密 | **No** | 公開 mesh／Ham mode／未加密 MQTT 等情境不能保證 |
| 是否提供刪除 collected data 的方式 | **No** | 可刪本機資料，但發布者無法刪除其他 radio／mesh／端點副本 |
| 是否建立帳號 | **No** | 沒有發布者帳號 |
| 是否有獨立安全審查 | **No** | 除非日後取得 Play 認可且仍有效的審查 |

這個答案不表示發布者建立使用者追蹤資料庫；它反映 Google 對 off-device transport 的
表單定義。若要改填「No」，必須先逐條證明所有傳輸都是純本機或完全符合官方排除，並
保存 AAB、程式與網路測試證據。

## 建議資料類型

以下是目前功能的最低保守集合；送出時仍以正式 AAB 的實際流量為準。

| Play 類別 | 資料類型 | Collected | Shared | Required／Optional | Purpose |
|---|---|---:|---:|---|---|
| Location | Approximate location | Yes | Yes（驗證例外後可改 No） | Optional | App functionality |
| Location | Precise location | Yes | Yes（驗證例外後可改 No） | Optional | App functionality |
| Personal info | Name | Yes | Yes（驗證例外後可改 No） | Optional | App functionality |
| Personal info | User IDs | Yes | Yes（驗證例外後可改 No） | Optional | App functionality |
| Messages | Other in-app messages | Yes | Yes（驗證例外後可改 No） | Optional | App functionality |
| Device or other IDs | Device or other IDs | Yes | Yes（驗證例外後可改 No） | Optional | App functionality |

`Name` 包含使用者設定的節點暱稱；`User IDs`／`Device or other IDs` 應依 Console 定義判斷
node ID、packet identity 與其他可連結識別碼。若正式測試發現 profile、自由文字欄位或
其他未列資料，加入相應的 `Other user-generated content`，不要為了縮短表單而省略。

### Shared 要選 Yes 還是 No

若每一次第三方傳輸都符合下列其中一項，可依官方 sharing exception 評估選 **No**：

- 使用者執行一個明確動作，且合理預期資料會送給指定 mesh／端點；或
- App 先提供符合 User Data 政策的顯著揭露，再取得肯定同意。

背景手機位置、Gateway 自動交換或任何未清楚揭露的路徑若不符合例外，就選 **Yes**。
目前 location FGS 與顯著揭露尚有送審阻擋，因此在修正並實測前不要先填 No。

## 不應申報為本 App 收集

在程式未再改動的前提下，不勾選：

- App interactions、Crash logs、Diagnostics、Other app performance data；
- Advertising or marketing、Personalization、Account management；
- Email、電話、地址、聯絡人、相片、影片、音訊、檔案、行事曆、健康、財務、購買紀錄、
  瀏覽紀錄或已安裝 App。

Android Vitals 是 Google Play／Android 平台的品質處理，不是 App 內嵌 Crashlytics；App
不應把它宣稱成發布者 Firebase 收集。但選擇 Play 發布仍會受 Google 平台條款約束。

## 送出表單前的最小稽核

1. 對最終已簽署 AAB 跑 cloud-runtime dependency 與 merged Manifest guards；
2. 在 App Bundle Explorer 核對 SDK、權限、provider 與 metadata，確認沒有 Advertising ID、
   Firebase、Maps、AppMeasurement、Install Referrer 或 Datadog；
3. 用乾淨安裝與受控網路側錄測試：首次啟動、離線 QR、無 radio 待機、radio 連線、發收
   訊息、NTsocial Gateway、位置開／關、MQTT／TAK／TCP 開／關；
4. 建立「資料類型 → 接收者 → 是否保存 → 是否加密 → 使用者動作／同意」證據表；
5. 讓本表、隱私政策、App 內揭露與實際 runtime 完全一致；
6. 若任何舊 artifact 含診斷 SDK 且仍在其他 Play track 散布，將其納入 package 層級申報或
   先停止散布。

本文件是依目前程式做的保守預填稿，不是法律意見。發布者對 Play 表單的完整性負責。
