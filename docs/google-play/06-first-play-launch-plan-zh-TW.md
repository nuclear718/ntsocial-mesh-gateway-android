# NTsocial MeshLink 首次上架 Google Play 計畫

## 摘要

目前程式可以作為開發／測試版，但尚不能把任何 AAB 稱為 Play-ready。首發設定鎖定如下：

- 發布者：LiberaNt LLC，已驗證的組織帳號。
- 套件名稱：`com.ntsocial.meshlink`，不得更改。
- 首發版本：沿用 `2.7.14` 與現有 Git commit count 產生的單調遞增 versionCode。
- 市場：僅台灣；類別 Communication；免費；18 歲以上；不面向兒童。
- 語言：English、繁體中文。
- 發布方式：本機產生 AAB，手動上傳 Internal testing；通過後將同一個 artifact 提升至 Production。
- 啟用 Managed publishing；首次正式版不能使用百分比分批發布，因此核准後一次開放台灣市場。[首次發布規則](https://support.google.com/googleplay/android-developer/answer/6346149?hl=en)
- 保留 Google Maps 功能；移除 Firebase Crashlytics、Datadog 與其安裝識別碼，但如實揭露 Maps SDK 的自動資料收集。[Maps SDK 資料揭露](https://developers.google.com/maps/documentation/android-sdk/play-data-disclosure)
- MeshCore 首版只能描述為 UI／協定基礎，不能宣稱傳輸已啟用。

## 1. 先完成程式、後端與文件的 Play readiness

### 診斷與隱私

- 移除 Firebase、Crashlytics、Datadog 的 Gradle plugin、依賴、Manifest metadata、BuildConfig 欄位、`google-services.json`、上傳 mapping 的 finalized task，以及 GitHub Actions 中的 GSERVICES／DATADOG secrets。
- 保留 `PlatformAnalytics` 介面和現有呼叫點，但所有平台統一綁定 no-op 實作。
- 刪除 `AnalyticsPrefs`、install ID、分析同意開關、分析 onboarding 內容和相關字串／測試；依賴稽核必須證明 release runtime 不再含 Firebase 或 Datadog。
- Google Maps 仍是功能性 SDK，因此隱私政策不可宣稱「完全沒有外部資料傳輸」。

### 社群規範與檢舉

- 新增 `LegalPrefs.acceptedPolicyVersion`，首版條款版本固定為 `1`。
- 使用者可唯讀使用 App；第一次發送訊息、反應、快速回覆或公開節點／頻道名稱前，顯示《使用條款》《社群規範》《隱私權政策》連結及明確同意框。
- 同意後只重送原本待執行動作一次；取消時不傳送。通知快速回覆若尚未同意，改以通知 deep link 帶使用者回 App 審閱，不得背景直接傳送。
- Gateway 受信任父 App 的 capability/command 路徑不套用 MeshLink UI 條款 gate；父 App 必須自行完成它的使用者同意。既有 Provider、AIDL compatibility adapter、port 256、180-byte envelope 與信任邊界均不變。
- 僅在收到的訊息 action sheet 與節點 context menu 顯示「檢舉」；提供「檢舉並封鎖／忽略此節點」。本機封鎖即使 API 失敗也能生效。
- 檢舉確認頁清楚告知「不會上傳訊息本文」。成功後顯示可複製的 ticket ID；網路失敗可手動重試，不建立持久離線檢舉佇列。

新增共用介面：

- `ReportRepository`、`ReportRequest`、`ReportReason`、`ReportResult`。
- Android Google/F-Droid 綁定 HTTPS repository；Desktop 綁定 unavailable implementation，不顯示檢舉入口。
- `BuildConfigProvider` 增加公開但環境化的 report API base URL；Google release 若仍為空值或 placeholder，建置直接失敗。

Cloud Run 公開介面固定為：

```text
POST /v1/reports
Content-Type: application/json
```

請求僅包含：

- `schemaVersion = 1`
- 隨機且單次的 `requestId`
- `type = MESSAGE | USER`
- 固定 reason enum
- `transport = MESHTASTIC`
- 被檢舉的 `targetNodeId`
- 訊息檢舉才帶 canonical `packetId`、`receivedAt`
- App versionName/versionCode

禁止傳送訊息本文、自由輸入說明、回報者節點 ID、頻道名稱、位置、PSK、目的地、BLE 位址、raw protobuf、packet bytes、日誌或 radio configuration。成功回應為 `202 { ticketId, acceptedAt }`；支援 `400/413/429/503`。

後端部署：

- 建立 `ntsocial-meshlink-prod` GCP 專案，Cloud Run 與 Firestore Native 均放在 `asia-east1`；使用獨立最小權限 service account。[Cloud Run 區域](https://docs.cloud.google.com/run/docs/locations)、[Firestore 區域](https://docs.cloud.google.com/firestore/native/docs/locations)
- Firestore 用 `reports`、`rateLimits` collections；client SDK rules 全部拒絕，只有 Cloud Run service account 能存取。
- 一般 report 的 `expiresAt` 為建立後 90 天，由 Firestore TTL 自動刪除；法律保全案件先清除 `expiresAt` 並標記 `LEGAL_HOLD`。
- 從請求 IP 產生每日輪替的 HMAC hash，不保存 raw IP；限制每 IP 每小時 5 件、每天 20 件，rate-limit records 48 小時後刪除。
- Cloud Run request logs 設 exclusion，應用程式 log 只能記 ticket ID、type、reason、status，不能記 IP 或完整 request body。
- 威脅、兒少安全與非法活動 reason 產生 Cloud Monitoring 即時 email alert；其他案件每天由具 MFA 與最小 IAM 權限的管理者在 Firestore queue 檢查。
- 支援信箱接受使用 ticket ID 的刪除申請；非法律保全案件人工刪除。

### 定位與 Foreground Service

- Onboarding 改成清楚區分：「位置權限可用於地圖／距離」與「分享位置預設關閉」。
- 使用者打開「提供位置給 Mesh」時，不論系統權限是否早已授予，都先顯示 prominent disclosure，說明：
  - 精確位置可能在 App 不可見時持續取得；
  - 會透過 Meshtastic radio 傳給 mesh 參與者，啟用 MQTT／TAK 時可能到相應服務；
  - 頻道設定不同時資料不保證端到端加密；
  - 可隨時由設定或持續通知停止。
- 使用者明確同意後才設定 preference、要求 Android 權限並開始更新；拒絕時保持關閉。
- `MeshLocationManager` 增加 `isActive: StateFlow<Boolean>`；`MeshService` 僅在實際 active 時加入 `FOREGROUND_SERVICE_TYPE_LOCATION`，其他時間只使用 `CONNECTED_DEVICE`。
- 移除因「權限存在」或 service/background restart 而自動恢復位置收集的行為。Process 重啟後，使用者需再次打開 App 才恢復既有 opt-in；通知與 UI 必須分別顯示「已選擇」及「目前正在分享」。
- 位置分享中的 foreground notification 加入「停止分享位置」action；停止時同步停止 location manager、移除 LOCATION FGS type 並清除 opt-in。
- 不新增 `ACCESS_BACKGROUND_LOCATION`。

### 發行設定、語言與公開文件

- 修改 release signing：缺少正式 `keystore.properties` 時 release task 必須失敗，永遠不得 fallback 至 debug signing。
- Google release 在 Maps key、report API URL 或其他正式設定仍為 dummy 時直接失敗。
- Android v1 packaging 只保留 `en`、`zh-rTW`；保留原翻譯原始檔，但不包進首發 Android artifacts。
- 更新 `docs/google-play` 全部上架文件：
  - 刪除 Firebase／Datadog 與 analytics consent 描述；
  - 加入 Maps SDK、檢舉 metadata、IP rate limiting、90 天 TTL、ticket 刪除方式；
  - 同步 UGC report/block、位置揭露、Foreground Service 與 Data safety 答案；
  - 保持 MeshCore 未啟用的真實說明。
- 從 GitHub Pages 的 `/docs` 發布穩定 HTML，設定 `/privacy/`、`/terms/`、`/community-guidelines/` permalink；頁面繁中優先並附英文，不需登入或地區限制。App 與 Play Console 使用同一 privacy URL。
- 重新製作一張 1024×500 feature graphic 與六張無真實位置、PSK、私人訊息的手機截圖：Connections、Messages、Nodes、Map、Gateway 狀態、Privacy/Location。使用 NTsocial 綠色蝴蝶，不使用 Meshtastic mountain logo。

## 2. 建立正式身分、簽章與雲端設定

1. 在 Play Console 建立 App：預設語言繁中、名稱 `NTsocial MeshLink`、App、Free；確認 package 永久為 `com.ntsocial.meshlink`。[建立 App](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en)
2. 在 App integrity 啟用 Play App Signing，選擇由 Google 產生 app-signing key；記錄 app-signing SHA-1/SHA-256。正式 API 與跨 App 信任必須使用 app-signing certificate，不是 upload certificate。[Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en-EN)
3. 在 repo 外產生專用 upload keystore：

```text
keytool -genkeypair -v -keystore <安全路徑>/ntsocial-meshlink-upload.jks \
  -alias meshlink-upload -keyalg RSA -keysize 4096 -validity 10000
```

4. 把 keystore 與密碼保存於密碼管理器及兩份加密備份；只在 gitignored `keystore.properties` 放本機路徑與 alias/password，絕不提交或輸出到 log。
5. 從父 App 的 Play Console 取得 `com.ntsocial.android` app-signing SHA-256，與 MeshLink 目前 pin 的 `29EF…E646` 比較；只有不一致時才同步 verifier、known-signer resource、package visibility 與雙方 build configuration。
6. 取得 MeshLink app-signing SHA-256 後，在父 App 加入對它的 release trust，發布父 App Internal build；不得使用 MeshLink upload key digest 或放寬成任意已安裝 App。
7. 在 GCP 啟用 billing、Maps SDK for Android、Cloud Run、Cloud Build、Artifact Registry、Firestore、Secret Manager、Monitoring；設定月預算和 50/80/100% 警示。
8. 建立 Maps production key，只允許 package `com.ntsocial.meshlink` 與 MeshLink Play app-signing SHA-1。若第一次 upload 後才看得到 app-signing fingerprint，先完成 Internal upload，再加入 fingerprint，測試者安裝前必須完成。
9. 部署 report API，將產生的 `.run.app` HTTPS base URL 注入 release config；production build 不允許 localhost、空值或 staging endpoint。

## 3. 產生並驗證第一個 AAB

先依 `.skills/project-overview/SKILL.md` 完成 JDK 21、Android SDK、submodule、properties bootstrap，再設定英文測試環境。實作完成後執行完整基線：

```text
.\gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests `
  kmpSmokeCompile :app:lintFdroidDebug :app:lintGoogleDebug `
  --continue --no-configuration-cache
```

接著：

1. 執行 report backend unit/integration tests及 Firestore emulator tests。
2. 在最後一個 release commit 後重新計算 versionCode，確認未曾上傳至 Play；第一版預期約為 `29320745`，實際值以最後 commit count 為準。
3. 建立 AAB：

```text
.\gradlew :app:bundleGoogleRelease `
  -Pmeshlink.disableAbiSplits=true `
  -PaboutLibraries.release=true `
  --no-configuration-cache
```

4. 驗證 `app/build/outputs/bundle/googleRelease/app-google-release.aab`：
   - `bundletool validate`
   - `jarsigner -verify -verbose -certs`
   - upload certificate、package、versionName、versionCode、targetSdk 37 正確；
   - merged manifest 無 ads、Crashlytics、Datadog、debuggable 或 background-location；
   - dependency report 不含 Firebase／Datadog；
   - Maps key 與 report URL 不是 dummy。
5. 產生 bundletool APK set，對所有 arm64 native libraries 執行 `zipalign -c -P 16` 與 ELF `PT_LOAD` 0x4000 對齊稽核；Google flavor 也必須通過，不能只引用既有 F-Droid 結果。[16 KB 要求](https://developer.android.com/guide/practices/page-sizes)
6. 保存 AAB、SHA-256 checksum、R8 `mapping.txt`、native symbols zip、dependency audit 與測試報告。移除 Crashlytics 後將 mapping/native symbols 從 Play Console App Bundle Explorer 手動上傳。
7. 任何已上傳 AAB 的 versionCode 都不能重用；若 Internal 測試後修正，提交新 commit 產生更高 versionCode。

## 4. Play Console 表單與測試軌操作

### Store listing

- 使用既有繁中標題、短描述、完整描述，完成英文翻譯。
- 上傳 512×512 icon、1024×500 feature graphic、六張 phone screenshots。
- Developer contact 使用 LiberaNt LLC 與 `huangct_2025@liber-ant.com`。
- 隱私政策填 GitHub Pages `/privacy/`。
- 所有文案只描述已實作功能，不宣稱 MeshCore transport、可靠投遞、遠端回執或未驗證功能。

### App content

- Ads：No。
- App access：No login；說明外接 radio 非啟動必要條件，提供連線步驟、無 radio 畫面行為及公開審查影片。
- Target audience：18 and over；Not designed for children。
- Content rating：如實回答 user communication、UGC、位置與公開 mesh content。
- UGC：填寫已存在的條款同意、訊息／使用者檢舉及封鎖流程。[UGC 政策](https://support.google.com/googleplay/android-developer/answer/9876937?hl=en-GB)
- Foreground Service：
  - `connectedDevice`：保持 BLE/USB/TCP radio 連線；中斷會停止收發。
  - `location`：只在使用者明確開啟位置分享時使用；中斷只停止位置傳送。
  - 提供無登入、可公開觀看的影片，完整錄下連線、揭露、權限、背景通知、持續運作及停止 action。[FGS 申報](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en)
- targetSdk 37 已超過 2026-08-31 起的新 App API 36 要求。[Target API 規則](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-GB_ALL)

Data safety 以實際 release artifact 為準，預設填「有收集、無廣告或販售、資料均為 optional」：

| 資料類型 | 來源與用途 |
|---|---|
| Approximate location | Maps SDK 的 IP、report API 的短期 IP rate limiting；analytics／security |
| Precise location | 使用者選擇透過 mesh/MQTT/TAK 分享；app functionality |
| Name | 節點／使用者名稱傳輸；app functionality |
| User IDs | Meshtastic node ID 與被檢舉 target ID；app functionality／security |
| Other in-app messages | 使用者主動傳送的 mesh 訊息；app functionality |
| App interactions | Maps SDK 的地圖 pan/zoom；analytics |
| Crash logs | Maps SDK 自身的 stack traces/crash metrics；analytics |
| Device or other IDs | Maps SDK pseudonymous identifier；analytics |

- Mesh／MQTT 接收者屬使用者預期並主動觸發的傳輸；Google Cloud 為代表發布者處理資料的 service provider，因此依政策例外填「not shared」，但隱私政策仍需完整解釋接收者。[Data safety 定義](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
- 「所有傳輸皆加密」填 No，因 LoRa/MQTT 的加密取決於使用者設定。
- 「可要求刪除」填 Yes，外部說明頁提供以 ticket ID 聯絡支援刪除 report 的方式；App 無帳號，因此不需 account deletion flow。

### Internal testing 與 Production

1. Internal testing 建立 5–10 人 email list，上傳已驗證 AAB，使用既有 release notes。
2. 所有測試者必須由 Play 安裝，不能用 sideload 代替；確認 Play app-signing、Maps key 與父 App release signer trust。
3. 測試 3–7 天並處理 Pre-launch report、Android Vitals、permission、layout、crash/ANR。
4. 組織帳號不套用新個人帳號的 12 人／14 天 production gate，但仍確認 Console 未顯示額外測試要求。[測試軌說明](https://support.google.com/googleplay/android-developer/answer/9859348/prepare-and-roll-out-a-release?hl=en-GB)
5. Internal 通過後直接 promote 同一 artifact 到 Production；不要重新建一個未測試 AAB。
6. Production countries/regions 僅勾選 Taiwan，開啟 Managed publishing，提交審查。
7. 核准後檢查 Store listing、Data safety、版本、國家與價格，才按 Publish。保留送審表單、影片、artifact checksum 與核准畫面的證據。

## 5. 發布驗收與上線後操作

正式送審前必須全部成立：

- 兩台 Meshtastic 節點或本地節點加遠端協作者完成文字訊息、位置與 canonical NTsocial `PRIVATE_APP / port 256` envelope 的實際 RF 收發；不能只驗證本機 queue acceptance。
- Play 安裝的 MeshLink 與 Play 安裝的父 App 完成 Provider snapshot、capability、explicit command/event、signer trust 與回傳訊息測試。
- 定位分享只有在 disclosure、同意與實際 active 時才使用 LOCATION FGS；停止 action、權限撤銷、service restart、radio disconnect 都有測試。
- Report API 測試有效、重複、格式錯誤、超大 body、429、503、90 天 TTL、法律保全、log 無敏感資料；Android 測試證明 request 不含訊息本文。
- 條款 gate 覆蓋訊息、反應、通知回覆、名稱更新；拒絕時絕不傳送，Gateway IPC 不受錯誤阻擋。
- 英文與繁中 UI、商店素材、公開政策、Data safety、實際 SDK/permissions 完全一致。
- Google release arm64 16 KB、R8、lint、tests、KMP smoke、release signing、Maps 與 backend production config 全部通過。

上線後：

- 前 72 小時每天檢查 Android Vitals、policy status、reviews、Cloud Run errors、Firestore report queue 與 GCP 費用；之後至少每週檢查。
- P1 檢舉 alert 目標 24 小時內人工檢視，一般案件 3 個工作日內 triage；90 天 TTL 每月抽查。
- 發布滿 7 天且無重大 crash/ANR、信任失敗、隱私或 RF 問題後，再先擴香港／澳門，最後評估全球市場。
- 嚴重問題時立即停止新國家供應並建立更高 versionCode hotfix；已發布 artifact 不能覆寫。
- 首次手動流程穩定後才恢復 CI/Fastlane 自動化；GitHub Secrets 只保留 upload keystore、Maps key、report URL、Play service-account key 與 Gradle cache secrets，不再加入 Firebase／Datadog／GSERVICES。

## 明確假設

- 未回覆的三項採建議預設：保留 Google Maps、可安排雙節點 RF 測試、可建立已啟用 billing 的 GCP 專案；任一不成立都視為首發 blocker。
- 檢舉不上傳原訊息或自由文字，只保存 metadata 90 天，防濫用只採短期 IP HMAC rate limiting。
- 首發不新增帳號、廣告、訂閱、Play Integrity 或中央內容歷史。
- 正式 release 使用 Google 產生的 Play app-signing key及獨立 upload key；debug APK、fallback debug signature、dummy Firebase/Maps 設定均不得送審。
