# NTsocial MeshLink Google Play 上架包

更新日期：2026-07-17

這個目錄是 `NTsocial MeshLink`（`com.ntsocial.meshlink`）的繁體中文 Google Play
Console 貼上稿。內容以目前 Google release 原始碼、實際合併 Manifest、母程式
`NTsocial_release` 的可核對資訊，以及 2026-07-17 Google 官方規則為依據。

所有上架文件都集中在本目錄，不需要再回到專案根目錄尋找。內容包含五份 Play
Console 填寫稿、隱私權政策、使用條款、社群規範及本索引，共九份 Markdown 文件。

## 建議填寫順序

1. 先處理 [05-release-checklist-zh-TW.md](05-release-checklist-zh-TW.md) 的紅線阻擋。
2. 建立 App 時依 [01-store-listing-zh-TW.md](01-store-listing-zh-TW.md) 填商店資料。
3. 依 [02-app-content-zh-TW.md](02-app-content-zh-TW.md) 完成 App content 各聲明。
4. 逐項依 [03-data-safety-zh-TW.md](03-data-safety-zh-TW.md) 填 Data safety。
5. 依 [04-review-and-permissions-zh-TW.md](04-review-and-permissions-zh-TW.md) 填審查存取與
   前景服務聲明，並錄製所需影片。
6. 上傳已正式簽署且完成驗證的 AAB，貼上版本資訊，再送審。

公開隱私權政策原稿位於 [PRIVACY_POLICY.md](PRIVACY_POLICY.md)。提交 Play Console
前必須先推送到公開 GitHub，確認下列網址在無登入／無痕視窗可正常開啟：

`https://github.com/nuclear718/ntsocial-mesh-gateway-android/blob/main/docs/google-play/PRIVACY_POLICY.md`

另已準備公開的 [使用條款](TERMS_OF_USE.md) 與
[社群／通訊規範](COMMUNITY_GUIDELINES.md)。因本 App 可收發使用者產生內容，正式
發布前仍須在 App 內提供可到達的條款／規範、適當的接受流程及檢舉入口；只有建立 Markdown
文件並不等於完成 UGC 政策要求。

## 目前不能宣稱「只要複製貼上即可明日公開」的原因

文件貼上稿已準備完成，但目前仍有文件以外的發布阻擋：

- 尚無使用真實 upload key 與正式 Google/Firebase Crashlytics/Datadog 設定建置、簽署並驗證過的
  Play AAB；缺少 `keystore.properties` 時，release 目前會退回 Debug 簽章。
- Firebase Analytics／Play Services Measurement、Ads Identifier 與 Privacy Sandbox Ads
  依賴已從程式移除；Google release 合併 Manifest 的廣告識別、AdServices、安裝歸因
  權限及 AppMeasurement 元件已驗證不存在。最終上傳的 AAB 仍須在 App Bundle Explorer
  再核對一次。
- Google Play App Signing 的正式 App signing certificate SHA-256 必須同步加入 MeshLink
  與母程式的 release 信任設定，否則商店版跨 App Gateway 配對可能失敗。
- Location FGS type 目前在只要已有位置權限時便加入，尚未跟「提供手機位置」開關同步；
  App 內位置揭露也尚未完整說明背景使用、接收者與未加密風險。
- App 可收發 UGC，但目前只有 ignore／mute，尚未完成條款接受與 App 內檢舉入口；本包
  已準備公開條款與社群規範，不能取代產品實作。
- 除英文與繁中外，其他已打包語系的分析告知仍沿用上游「匿名」說法，必須更新或從
  首發 Google AAB 排除；公開 Release 版也沒有一般使用者可選的 Demo Mode。
- 既有 Feature graphic 是純黑圖，既有截圖仍顯示上游 Meshtastic／模擬節點與座標，不能
  當成 NTsocial MeshLink 正式商店素材。
- 若是 2023-11-13 後建立的個人開發者帳號，需先完成 12 位測試者連續 14 天封閉測試；
  一般審查也可能超過 7 天，無法保證隔日公開。

## 語言與既有 Fastlane 中繼資料

本專案目前含約 40 組 Compose 語系資源與 39 組沿自上游的 Fastlane 商店語系目錄，
因此 App 本身並非「只有繁體中文」。本包只把 `zh-TW` 當成首發預設商店語言；其他
Fastlane 語系多半仍是上游 Meshtastic 文案，不得直接上傳。現行 `Fastfile` 也設定
`skip_upload_metadata`、`skip_upload_images` 與 `skip_upload_screenshots`，因此首發請依
本包人工貼入繁中內容與新素材，除非先完成所有語系的品牌與事實稽核。

## 母程式資料的使用邊界

本包只沿用母程式的 NTsocial 品牌關係、LiberaNt LLC 聯絡資料候選及產品分工。以下內容
沒有沿用：

- 「無分析、無 GPS、無伺服器」等與 MeshLink Google 版本不符的絕對宣稱；
- 母程式的 Public／Channel／Private、附件、PTT、個人檔案及遊戲功能；
- 禁止修改、反編譯或衍生作品的閉源 EULA；該條款與 MeshLink 的 GPL-3.0 不相容；
- 只授權 `com.ntsocial.android` 的權利文件；它不等於 MeshLink 的發布授權。

## 官方參考

- [建立 App 與商店欄位](https://support.google.com/googleplay/android-developer/answer/9859152)
- [商店圖像規格](https://support.google.com/googleplay/android-developer/answer/9866151)
- [準備 App 審查](https://support.google.com/googleplay/android-developer/answer/9859455)
- [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)
- [隱私權與 User Data 政策](https://support.google.com/googleplay/android-developer/answer/10144311)
- [新個人帳號測試要求](https://support.google.com/googleplay/android-developer/answer/14151465)
- [前景服務聲明](https://support.google.com/googleplay/android-developer/answer/13392821)
- [16 KB page size](https://developer.android.com/guide/practices/page-sizes)
