# NTsocial MeshLink：Google Play 首發文件

最後更新：2026 年 7 月 19 日

這個目錄是 `NTsocial MeshLink`（`com.ntsocial.meshlink`）第一版 Google Play 上架的唯一工作包。
請從 [最快上架流程](06-first-play-launch-plan-zh-TW.md) 開始，不必另外建立 Google Cloud、
Maps 或 Firebase 專案。

## 對外產品身分與權利陳述

所有 Play Console 欄位、審查備註、政策頁、截圖文字與支援回覆都使用同一套事實：

- **發布與維護主體：** NTsocial MeshLink 由 **LiberaNt LLC 與 NTsocial 團隊主導開發、
  整合、發布與持續維護**，是 Android NTsocial App 的核心開源 companion app；
- **公司原創與修改：** LiberaNt LLC 對 NTsocial MeshLink 特有的原創程式、可受著作權保護
  的修改、Gateway／母程式整合、文件與品牌視覺成果主張著作權；
- **開源承諾：** 合併作品仍依 GPL-3.0-or-later 提供原始碼與使用、研究、修改、再散布的
  自由。公司著作權與產品主導聲明不得被寫成 `All Rights Reserved` 或額外限制；
- **上游與貢獻者：** Meshtastic Android 衍生部分、MeshCore 參考材料及個別貢獻者的權利
  與 notice 繼續保留；LiberaNt 不主張擁有未修改的上游內容；
- **無官方關係暗示：** 本 App 不是 Meshtastic 或 MeshCore 官方發行版，也不得暗示其贊助、
  背書或商標授權。

權利與來源的完整依據是根目錄 `NOTICE.md`、`THIRD_PARTY_NOTICES.md` 與
`docs/copyright-and-attribution.md`。母程式的專有 EULA／`All Rights Reserved` 條款不適用
於這個 GPL repository。

## 現況一覽

| 項目 | 狀態 | 說明 |
|---|---|---|
| Google Cloud／Maps／Play Services runtime | 已移除 | 兩個 Android flavor 都不得重新加入 |
| Firebase／Crashlytics／Datadog／ML Kit | 已移除 | 不收集產品分析、意見或 App 內當機報告 |
| 地圖 UI | 已移除 | 商店文案與截圖不得再出現地圖 |
| QR／條碼 | 已完成 | 使用離線 ZXing，不上傳相機畫面 |
| Release 建置 | 已通過 | `bundleGoogleRelease` 可完成，但目前 AAB 未簽署 |
| Upload key／Play App Signing | 待人工完成 | 這是正式上傳必要項目，與 GCP billing 無關 |
| Location FGS 行為 | 送審前阻擋 | 必須只在使用者啟用手機位置提供時使用 location 類型，或移除該功能 |
| UGC 合規 | 送審前阻擋 | 必須有條款接受、App 內檢舉，以及一對一互動的封鎖／ignore 能力 |
| 政策發布與 App 內 URL | 送審前阻擋 | 草稿警告須移除，正式公開 HTTPS URL 須寫回 App 並重建 |
| 舊 `analytics_notice` 翻譯 | 待清理 | SDK 已移除，但最終封裝不得留下誤導性的舊收集聲明 |
| 商店素材與 Console 表單 | 待人工完成 | 依本目錄提供的稿件填寫 |
| 產品身分與開源來源文案 | 已完成 | LiberaNt 主導、GPL、上游權利與非官方關係已統一 |
| Play 配送實測 | 待人工完成 | 必須從 Internal track 安裝，驗證正式簽章與 NTsocial pairing |
| 新 personal 帳號測試門檻 | 依帳號判定 | 2023-11-13 後建立者通常需 12 位測試者連續 14 天 closed test |

`googleRelease` 只是 Gradle variant 名稱，不表示 App 依賴 Google SDK。選擇 Google Play
仍會使用 Play Console、Play App Signing、商店安裝／更新與 Android Vitals；若連這些平台
服務也要避免，就不能透過 Google Play 發布。

## 只照這個順序做

1. 先完成 [最快上架流程](06-first-play-launch-plan-zh-TW.md) 的兩個已確認政策／runtime 阻擋。
2. 建立 upload key，產生已簽署 AAB。
3. 準備 [商店文案與素材](01-store-listing-zh-TW.md)。
4. 填寫 [App content](02-app-content-zh-TW.md)、[Data safety](03-data-safety-zh-TW.md) 與
   [權限／前景服務](04-review-and-permissions-zh-TW.md)。
5. 上傳 Internal track，從 Play 安裝並完成實測。
6. 用 [最終檢查表](05-release-checklist-zh-TW.md) 做最後一次 Go／No-Go。
7. 將同一個已測 artifact promote 到 Production。

公開政策草稿：

- [隱私權政策](PRIVACY_POLICY.md)
- [使用條款](TERMS_OF_USE.md)
- [社群與通訊規範](COMMUNITY_GUIDELINES.md)

送審前要把政策發布成無需登入、無地區限制的公開 HTTPS 頁面，並確保 App 內可以開啟。
這些 Markdown 是提交稿，不會自動完成 App 內條款接受或檢舉功能。

## 首發完全不需要

- GCP 專案、Cloud billing、Maps SDK 或 Maps API key；
- `google-services.json`、Firebase、Crashlytics 或 mapping upload；
- Datadog token、RUM／Logs／Trace；
- ML Kit 或雲端 QR 解碼；
- 分析 installation ID、意見收集後端或廣告 SDK；
- Play service-account JSON（第一版採 Play Console 手動上傳即可）。

不要建立 dummy cloud secrets，也不要為了讓舊流程通過而重新加入已移除的 SDK。

## 官方參考

- [建立與設定 App](https://support.google.com/googleplay/android-developer/answer/9859152)
- [準備及推出版本](https://support.google.com/googleplay/android-developer/answer/9859348)
- [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)
- [User Data 政策](https://support.google.com/googleplay/android-developer/answer/10144311)
- [User Generated Content 政策](https://support.google.com/googleplay/android-developer/answer/9876937)
- [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)
- [新 personal 帳號測試規則](https://support.google.com/googleplay/android-developer/answer/14151465)
- [開發者帳號類型與組織驗證](https://support.google.com/googleplay/android-developer/answer/13634885)
- [Play Console 帳號與一次性註冊費](https://support.google.com/googleplay/android-developer/answer/6112435)
- [前景服務聲明](https://support.google.com/googleplay/android-developer/answer/13392821)
- [商店資訊最佳實務](https://support.google.com/googleplay/android-developer/answer/13393723)
- [Misrepresentation](https://support.google.com/googleplay/android-developer/answer/9888689)
- [Impersonation](https://support.google.com/googleplay/android-developer/answer/9888374)
- [Intellectual Property](https://support.google.com/googleplay/android-developer/answer/9888072)
