# 05｜正式送審檢查表

## A. 明日是否具備公開資格

- [ ] 確認開發者帳號是組織帳號，或不是 2023-11-13 後建立的新個人帳號。
- [ ] 若屬新個人帳號，已完成 12 位測試者連續 14 天封閉測試並取得 Production access。
- [ ] 需要時已在 Play Console Android App 完成 Android 10+ 非 Root 實機驗證。
- [ ] 已預留審查可能 7 天以上的時間；沒有對外承諾「送出後明天一定上線」。

若第一或第二項不成立，文件再完整也無法明日進 Production。

## B. 發布者與公開聯絡資料

- [ ] Play Console 開發者名稱確認為 `LiberaNt LLC`；若不同，已同步修改隱私政策。
- [ ] `huangct_2025@liber-ant.com` 確實有人收信並可處理支援／隱私要求。
- [ ] Play 開發者帳號地址、電話、D-U-N-S／組織驗證資料皆已完成且與公司資料一致。
- [ ] GitHub repository 是公開狀態。
- [ ] `PRIVACY_POLICY.md` 已 push 到 `main`，無痕視窗可直接開啟。
- [ ] App 內隱私政策連結已改成 MeshLink 政策，不再連到 Meshtastic 官方政策。

## C. 產品與政策紅線

- [ ] 已決定分析的合法基礎與預設值；若仍預設開啟，政策與 Data safety 沒有寫成 opt-in。
- [ ] 已評估把分析預設改為 false，並提供真正的事前 opt-in。
- [ ] 所有實際打包語系的 `analytics_notice` 均已改成真實的「假名化」資料說明；目前只有
  英文與繁中已修正，其餘語系仍含上游的「匿名」宣稱，不能原樣發布。
- [ ] Release 遠端日誌已移除或遮罩 BLE address、IP、host、port 等不必要識別資訊。
- [ ] 已建立 UGC／通訊規範、使用者接受流程及適用的檢舉處理方式，或已取得 Play 對
  去中心化 radio messaging 的明確政策判定。
- [ ] `TERMS_OF_USE.md` 與 `COMMUNITY_GUIDELINES.md` 已公開，App 內可到達且使用者在
  建立／傳送 UGC 前完成適當接受；App 內另有可操作的內容／使用者檢舉入口。
- [ ] 未宣稱「完全匿名」、「所有通訊都加密」、「保證送達」或「無任何網路服務」。
- [ ] 未把 MeshCore transport、RF scheduler、可靠持久傳輸或遠端 RF 接收寫成已完成。

## D. 正式簽章與跨 App 信任

- [ ] 已建立並安全備份真正的 upload keystore、alias、store password 與 key password。
- [ ] `keystore.properties` 指向正式 upload key；release 不再退回 Debug signing。
- [ ] 已啟用 Play App Signing，並保存 upload certificate 與 app signing certificate。
- [ ] 已從 Play Console 複製 **App signing certificate SHA-256**，不是 upload key 指紋。
- [ ] 正式 app signing SHA-256 已同步加入 MeshLink 與 NTsocial 母程式的 release 簽章信任規則。
- [ ] 正式 `com.ntsocial.android` 與 `com.ntsocial.meshlink` 的 package visibility、known signer
  resource 與 build configuration 一致。
- [ ] 從 Play Internal track 安裝兩個由 Play 重新簽署／配送的版本，實測 Provider、
  capability、explicit command 與 event/re-query 配對。

只用本機 upload key 簽 APK 的測試，不能證明 Play 配送簽章下的跨 App IPC 會成功。

## E. Production 服務設定

- [ ] 授權的 production `google-services.json` 已就位，Firebase package 與 SHA 憑證正確。
- [ ] Google Maps API key 已設定 package／certificate 限制，不是 `dummy`。
- [ ] Datadog application ID、client token、environment 與站點正確，不是 defaults。
- [ ] Firebase Crashlytics mapping upload 使用有權限的正式設定並成功。
- [ ] 已核對 Firebase Crashlytics retention、Datadog retention／GeoIP 與刪除流程，並與
  隱私政策一致。
- [ ] 沒有把 production credential、keystore 或 token 加入 Git。

## F. Manifest 與 Play 權限聲明

- [x] 已移除 Firebase Analytics、Play Services Measurement、Ads Identifier、Privacy
  Sandbox Ads 與 AppMeasurement 程式碼／依賴。
- [x] Google release 合併 Manifest 已驗證不含 `AD_ID`、`ACCESS_ADSERVICES_ATTRIBUTION`、
  `ACCESS_ADSERVICES_AD_ID`、Install Referrer、`android.ext.adservices` 或 AppMeasurement。
- [ ] 最終正式 AAB 上傳後，在 App Bundle Explorer 再驗證上述項目不存在，然後於
  Console 回答「不使用 Advertising ID」。
- [ ] 確認沒有意外帶入 `ACCESS_BACKGROUND_LOCATION`、SMS／Call Log、
  `MANAGE_EXTERNAL_STORAGE`、`QUERY_ALL_PACKAGES`、VPN、Accessibility、exact alarm、
  install packages 或 full-screen intent。
- [ ] Connected-device 與 location foreground service 說明已填寫。
- [ ] `MeshService` 只在使用者實際開啟位置提供時加入 location FGS type；目前只要已有
  位置權限就加入的行為已修正並以正式 AAB 驗證。
- [ ] 前景服務示範影片已上傳為可直接觀看的 YouTube 公開／不公開影片並貼入 URL。
- [ ] 位置權限前的顯著揭露已在正式 App 中實際顯示，包含背景持續使用、接收者與
  未加密風險，且文字與影片一致；目前首次位置畫面尚未完整涵蓋這些資訊。

## G. AAB 建置與技術驗證

- [ ] 先依 `.skills/project-overview/SKILL.md` 完成 JDK 21、Android SDK、submodule 與 secrets bootstrap。
- [ ] 完整基線通過：

  ```powershell
  $env:JAVA_TOOL_OPTIONS='-Duser.language=en -Duser.country=US'
  .\gradlew.bat spotlessApply spotlessCheck detekt assembleDebug test allTests --no-configuration-cache
  ```

- [ ] 額外通過 `kmpSmokeCompile :app:lintFdroidDebug :app:lintGoogleDebug`。
- [ ] 使用正式設定執行 `:app:bundleGoogleRelease`，且 R8／mapping upload 全部成功。
- [ ] AAB package 是 `com.ntsocial.meshlink`，versionCode 未使用過，versionName 正確。
- [ ] AAB 由 upload key 正式簽署，沒有 Debug certificate。
- [ ] 使用 `bundletool validate` 與 App Bundle Explorer 檢查 AAB。
- [ ] 上傳 R8 mapping 與可用的 native debug symbols。
- [ ] Google flavor 的所有 native `.so` 已驗證 16 KB page-size 相容；不能只引用 F-Droid
  arm64 Debug APK 的既有驗證。
- [ ] Internal track 安裝檔完成乾淨安裝、升級、啟動、權限、radio 連線與
  背景服務 smoke test。

目前專案 targetSdk 37，已高於 2026-07-17 新 App 的 API 35 要求，也高於
2026-08-31 起的 API 36 要求；但 target 合規不代表 AAB 已可上傳。

## H. 商店頁與圖像

- [ ] 預設語言設為中文（繁體）`zh-TW`。
- [ ] 標題、短描述、完整描述與 release notes 已依本包貼上並檢查字數。
- [ ] 沒有上傳仍屬上游 Meshtastic 文案的其他 Fastlane 語系；首發只啟用已稽核的
  `zh-TW` 商店資訊，或已逐語系完成品牌與功能校對。
- [ ] 類別選「通訊」，標籤只選 Console 內實際存在且與功能直接相關者。
- [ ] 客服信箱、網站、隱私政策 URL 均可開啟。
- [ ] 512 × 512 icon 在 Play 遮罩預覽中正常。
- [ ] 已建立不是純黑的 1024 × 500 Feature graphic。
- [ ] 至少 2 張、建議 4 張以上正式手機截圖；沒有真實座標、通知、私人訊息、PSK、
  token、裝置序號或測試者個資。
- [ ] 截圖顯示 `NTsocial MeshLink`，不是舊的 `Meshtastic` 上游品牌畫面。
- [ ] 已填每張圖的繁中替代文字。
- [ ] 商店文案與審查說明沒有宣稱 Release 使用者可使用 Demo Mode；mock transport 只在
  Debug 或 Firebase Test Lab 環境可用。

## I. App content 與 Data safety

- [ ] Privacy policy、Ads、App access、Target audience、IARC 全部完成。
- [ ] Health、Financial、Government、News、COVID、Account deletion 等聲明全部完成。
- [ ] IARC 如實回答使用者通訊、UGC、未審核內容與位置分享為「是」。
- [ ] Data safety 依實際正式 AAB 與 production SDK 設定複核，不是照抄母程式舊政策。
- [ ] Data safety 與隱私權政策對分析預設、資料類型、接收者、保存與刪除說法一致。
- [ ] 沒有把 service-provider／user-initiated transfer 例外誤寫成「實際上完全不傳資料」。

## J. 發布與發布後

- [ ] 先上 Internal track，從 Play 安裝並驗證簽章與 Gateway pairing。
- [ ] 視帳號資格完成 Closed testing／Production access。
- [ ] 選擇發行國家／地區與 rollout 比例。
- [ ] 開啟 Managed publishing，避免審查通過後在未確認時自動公開。
- [ ] 發布前再檢查 Policy status、Pre-launch report、Android vitals 與 App Bundle Explorer。
- [ ] Production rollout 後監看 crash／ANR、權限拒絕、Gateway trust failure 與使用者回報。
- [ ] 對外狀態只寫「已送審」或「已公開」的真實狀態，不把 Debug 三機測試稱為 Play
  release 驗證，也不把本機 radio queue accepted 稱為遠端 RF 收到。

## 官方時程與技術來源

- [新個人帳號封閉測試](https://support.google.com/googleplay/android-developer/answer/14151465)
- [App 審查與目標對象](https://support.google.com/googleplay/android-developer/answer/9867159)
- [Target API](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)
- [準備與推出版本](https://support.google.com/googleplay/android-developer/answer/9859348)
- [16 KB page size](https://developer.android.com/guide/practices/page-sizes)
