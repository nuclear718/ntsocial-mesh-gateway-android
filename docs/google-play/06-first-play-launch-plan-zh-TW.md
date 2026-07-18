# 最快完成第一版 Google Play 上架

最後更新：2026 年 7 月 18 日

這是首發的主流程。第一版採 Play Console 手動上傳，因此不需要 GCP、Maps billing、
Firebase、Crashlytics、Datadog、ML Kit 或 Play service account。

## 開始前：兩個已確認的政策／runtime 阻擋

先完成以下項目，否則即使 AAB 可以建置，也不應送 Production：

1. **Location foreground service**：目前 `MeshService` 在取得位置權限後就加入 location
   service type，即使使用者尚未開啟手機位置提供。必須改成只有使用者明確啟用位置提供、
   且實際取得位置時才加入；另一個更小的首發方案是完全移除手機位置提供與 location FGS。
2. **使用者產生內容（UGC）**：訊息功能送審前必須具備條款接受、App 內可操作的內容／
   使用者檢舉，以及一對一互動的 block／ignore。只有公開 Markdown 與客服信箱不夠。

這兩項是目前已由程式碼確認的功能／政策阻擋，不代表其餘發行工作已完成。正式 artifact
仍可能因 Play signer trust、App 內政策 URL 或最終封裝稽核而需要再修改、提高 versionCode
並重建。完成後再依下列七步進行。

## 1. 建立正式 upload key

在 repository 以外的安全目錄建立專用 keystore；不要與 Debug key 或其他 App 共用：

```powershell
keytool -genkeypair -v `
  -keystore C:\secure\meshlink-upload.jks `
  -alias meshlink-upload `
  -keyalg RSA -keysize 4096 -validity 10000
```

在 repository 根目錄新增已被 `.gitignore` 排除的 `keystore.properties`：

```properties
storeFile=C:/secure/meshlink-upload.jks
storePassword=<實際密碼>
keyAlias=meshlink-upload
keyPassword=<實際密碼>
```

將 keystore、alias 與密碼分開離線備份。遺失 upload key 會增加後續更新成本；不要把任何
密碼、keystore 或憑證提交到 Git。

## 2. 建立第一個已簽署 candidate AAB 並驗證

先完成專案 bootstrap，使用 JDK 21 與有效的 Android SDK：

```powershell
git submodule update --init
$env:JAVA_HOME='C:\path\to\jdk-21'
$env:ANDROID_HOME='C:\path\to\Android\Sdk'
$env:JAVA_TOOL_OPTIONS='-Duser.language=en -Duser.country=US'
```

程式阻擋修正後，重新跑完整基線與 release：

```powershell
.\gradlew.bat spotlessApply spotlessCheck detekt assembleDebug test allTests `
  kmpSmokeCompile :app:lintFdroidDebug :app:lintGoogleDebug `
  --continue --no-configuration-cache

.\gradlew.bat :app:verifyGoogleReleaseNoCloudRuntimeDependencies `
  :app:bundleGoogleRelease --no-configuration-cache
```

預期輸出為：

```text
app/build/outputs/bundle/googleRelease/app-google-release.aab
```

確認 package 是 `com.ntsocial.meshlink`、versionCode 尚未在 Play 使用、AAB 由 upload key
簽署且不是 Debug certificate。再執行 `bundletool validate`，並對由 bundletool 產生的
arm64 APK 檢查 16 KB page size 與所有 ELF `PT_LOAD` 對齊。

Cloud-free 防回歸檢查必須保持通過。ZXing 的 Java package 歷史名稱包含
`com.google.zxing`，但它是 App 內離線開源解碼器，不是 Google Cloud、Play Services 或
ML Kit。

目前多個語系資源仍保留未被 Kotlin 使用的舊 `analytics_notice`，文字會誤稱 Firebase／
Crashlytics／Datadog 收集資料。這不表示相關 SDK 仍存在，但最終送審 AAB 前仍應刪除或
改成 cloud-free 事實，並再次確認 UI 與封裝資源不會顯示舊聲明。

## 3. 建立 Play Console App

1. 使用已完成身分驗證的 Play 開發者帳號建立 App；
   若發布者是 `LiberaNt LLC`，帳號類型應為 Organization，並準備 D-U-N-S、組織與聯絡人
   驗證資料；不可只為省時間把公司 App 誤建成 personal account。既有帳號則核對驗證狀態。
2. 名稱填 `NTsocial MeshLink`，預設語言選繁體中文，類型選 App，免費；
3. 接受 Play App Signing；
4. 第一版採 Console 手動上傳，不設定 Fastlane service account；
5. 若是 2023 年 11 月 13 日後建立的 personal developer account，Production access 通常
   必須先完成至少 12 位已 opt-in 測試者連續 14 天的 closed test；organization／舊帳號則
   依實際 Console。這是外部時程 gate，無法用程式略過。詳見
   [官方測試規則](https://support.google.com/googleplay/android-developer/answer/14151465)。

新建 Play 開發者帳號目前通常有一次性 USD 25 註冊費，實際金額、稅務與付款方式以註冊
頁為準；既有已付費帳號不需為這個 App 重付。這和 GCP／Maps 用量計費完全不同，本 App
不需要啟用 Cloud billing。組織驗證詳見
[官方帳號要求](https://support.google.com/googleplay/android-developer/answer/13634885)，註冊費詳見
[Play Console 開始使用說明](https://support.google.com/googleplay/android-developer/answer/6112435)。

## 4. 上傳 Internal track 並取得正式簽章

1. 將已簽署 AAB 上傳 Internal track；
2. 到 Play Console 的 App integrity 保存 upload certificate 與 **App signing certificate**；
3. 取得 Play app-signing SHA-256，而不是 upload key SHA-256；
4. 將 NTsocial App 的正式 signer 加入 MeshLink 信任設定，也將 MeshLink 的 Play signer
   同步到母 App 的信任設定；
5. 若取得 Play signer 後需要改程式，增加 versionCode、重建並上傳第二個 Internal artifact。

跨 App 信任必須使用 Play 重新簽署後的 certificate。本機安裝 upload-key APK 或 Debug APK
都不能證明正式 pairing 會成功。

## 5. 完成商店頁與 App content

依序使用：

- [商店文案與素材](01-store-listing-zh-TW.md)
- [App content 答案](02-app-content-zh-TW.md)
- [Data safety](03-data-safety-zh-TW.md)
- [審查存取、權限與前景服務](04-review-and-permissions-zh-TW.md)

政策頁必須是穩定公開 HTTPS、無需登入、無地區限制、非 PDF、一般使用者不可編輯，App
內也能開啟；GitHub blob 只作草稿與版本紀錄。Data safety 不能只因移除 Firebase 就回答「不收集」；
Google 將使用者資料傳出 Android 裝置也視為 collection。現有 mesh／位置／自選端點能力
應依 [Data safety 文件](03-data-safety-zh-TW.md) 保守填寫。

目前 App 的 `privacy_url` 仍指向 repository 的 GitHub blob。正式政策頁上線後，必須把
App 內 URL 改成該公開頁並重建；政策草稿頂端的「送審前草稿」警告也要在功能與內容核對
完成後移除。若這一步或 Play signer trust 改到程式，請提高 versionCode，重新執行第 2 步，
並把新的 AAB 當成唯一測試候選版本。

## 6. 從 Play 安裝並做 Internal 測試

至少完成：

- 乾淨安裝、升級、首次啟動與拒絕非必要權限；
- 連線、訊息、節點、設定與 MeshCore 畫面沒有已刪除的地圖入口；
- 離線 ZXing QR 掃描沒有網路請求；
- Bluetooth／USB／TCP radio 連線與持續通知；
- 位置揭露、權限、啟用、背景前景服務與停止流程；
- NTsocial Provider、capability、command、event／re-query 與正式 signer trust；
- App Bundle Explorer 沒有 Maps、Firebase、Crashlytics、Datadog、ML Kit、Advertising ID
  或相關 provider／metadata。

禁止直接沿用 `fastlane/metadata/android` 內仍含 map、analytics 或 Meshtastic 官方宣稱的
舊語系文案與 screenshots；第一版只上傳已依目前 runtime 重做並核對的素材。

測試要從 Play Internal track 安裝，不能側載本機 Debug 版代替。若沒有 radio 硬體，只能
驗證無硬體路徑；不得把本機 queue accepted 宣稱為遠端 RF 收到。

## 7. Promote 到 Production

1. 用 [最終檢查表](05-release-checklist-zh-TW.md) 完成 Go／No-Go；
2. 修完 Pre-launch report 與 Policy status 的阻擋；
3. 完成帳號要求的 Closed testing／Production access；
4. 只有需要控制確切公開時間時才開啟 Managed publishing；最快流程可不開啟；
5. promote **同一個已通過 Internal 測試的 artifact**，不要臨時重建未測 AAB；
6. 設定國家／地區與 rollout 後送審。

上線後以 Android Vitals、測試報告、`adb logcat` 與使用者主動提供的重現步驟維護品質。
Android Vitals 是 Play 平台能力，不是 App 內嵌 Crashlytics，也不需要 Firebase 專案。

## 完成定義

只有在下列全部成立時，才能稱為 Play-ready：

- 已解決 location FGS 與 UGC 阻擋；
- 已用正式 upload key 產生並驗證 AAB；
- Play 配送版本完成 NTsocial signer pairing 與實機測試；
- 商店素材、政策、Data safety 與實際 AAB 完全一致；
- Production release 已被 Play Console 接受。
