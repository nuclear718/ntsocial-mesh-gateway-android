# Google Play 首發 Go／No-Go 檢查表

最後更新：2026 年 7 月 18 日

`[x]` 是目前 repository 已驗證的狀態；`[ ]` 必須由發布者或後續程式修改完成。任何
「送審阻擋」未勾選時，都不要送 Production。

## 已完成的去雲端工作

- [x] 兩個 flavor 都已移除 Google Cloud、Maps、Google Play Services runtime、Firebase、
  Crashlytics、Datadog 與 ML Kit。
- [x] 地圖 UI 與外部開圖入口已移除；QR／條碼改成離線 ZXing。
- [x] 分析、意見收集、installation ID 與 App 內當機回報 UI／SDK 已移除。
- [x] `bundleGoogleRelease`、R8、Lint Vital、cloud-runtime guards 與未簽署 AAB 包裝已通過。
- [x] 目前 debug arm64 APK 的 16 KB zip／ELF 對齊已通過。

以上不需要 GCP billing、Maps key、`google-services.json`、Firebase 專案或 Datadog token。

## 送審阻擋

- [ ] location FGS 只在使用者明確啟用手機位置提供、且實際取得位置時啟動；或首發完全
  移除該功能與 location FGS。
- [ ] 已針對目前 `targetSdk = 37` 決定 Android 17 minimum-scope／location button 路徑；
  onboarding 不再無條件要求 Fine＋Coarse location。
- [ ] 系統位置權限前已有獨立、明確的 App 內揭露與肯定同意，文字與實際傳輸對象一致。
- [ ] 傳送 UGC 前要求使用者接受條款。
- [ ] App 內已有可操作的內容／使用者檢舉入口；客服 email 只能作為備援。
- [ ] 一對一互動可 block／ignore，並已實機驗證後續內容不再顯示。
- [ ] 目前 cloud-free 版本已在實機完成 smoke test；不能沿用移除地圖前的舊結果。

## 正式簽章與 AAB

- [ ] 專用 upload keystore、alias 與密碼已離線備份，且未提交 Git。
- [ ] `keystore.properties` 指向 upload key；release 沒有 Debug signing fallback。
- [ ] `app-google-release.aab` 已由 upload key 簽署，package／version 正確，
  `bundletool validate` 通過。
- [ ] 最終簽署 artifact 的 dependency、merged Manifest 與 App Bundle Explorer 仍通過
  cloud-free／Advertising ID 檢查。
- [ ] 未使用的多語 `analytics_notice` 已刪除或改成 cloud-free 事實；最終 UI 與封裝資源
  不含 Firebase／Crashlytics／Datadog 收集聲明。
- [ ] 由最終 AAB 產生的 arm64 APK 通過 16 KB page-size 與所有 ELF `PT_LOAD` 稽核。
- [ ] 保存 AAB SHA-256、R8 `mapping.txt`、必要 native symbols 與測試報告。

## Play Console 與跨 App 信任

- [ ] 開發者帳號、發布者資料、聯絡資訊與必要身分驗證已完成；以 `LiberaNt LLC` 發布時
  使用 Organization 帳號並完成 D-U-N-S／組織驗證。
- [ ] Play App Signing 已啟用，upload certificate 與 app-signing certificate 已保存。
- [ ] Play **App signing certificate SHA-256** 已同步到 MeshLink／NTsocial 的正式 signer trust。
- [ ] 2023-11-13 後建立的 personal 帳號已完成通常所需的 12 位 opt-in 測試者連續 14 天
  closed test 與 Production access；organization／舊帳號依 Console 實際要求。
- [ ] Internal track 由 Play 安裝的兩個 App 已通過 Provider、capability、command、event／
  re-query 與 signer pairing。

## 商店與政策

- [ ] 標題、短描述、完整描述與 release notes 使用目前無地圖、無遙測 SDK 的功能事實。
- [ ] 512 × 512 icon、1024 × 500 feature graphic 與至少兩張手機截圖已準備；沒有真實位置、
  私人訊息、PSK、token、裝置序號或地圖畫面。
- [ ] 沒有直接上傳仍含 map／analytics／Meshtastic 官方宣稱的舊 Fastlane 語系 metadata
  或 screenshots。
- [ ] 隱私權政策、使用條款、社群規範已發布成穩定公開 HTTPS 頁面，無需登入、無地區
  限制、非 PDF、一般使用者不可編輯，已移除草稿警告，且 App 內正式 URL 可到達。
- [ ] Ads＝No、Advertising ID＝No、App access／目標對象／IARC／其他 App content 已完成。
- [ ] Data safety 依最終 AAB 與受控網路測試填寫；mesh／位置傳出裝置的行為沒有被誤寫成
  純本機處理。
- [ ] connectedDevice FGS 的功能、延遲／中斷影響及示範影片已提交；只有首發保留 location
  FGS 時才另填 location 聲明與影片，若移除則確認 Manifest 與 Console 都不再宣告。
- [ ] 沒有宣稱「完全匿名」、「全部傳輸加密」、「保證送達」或「完全不接觸 Google」。

## Internal 測試

- [ ] 從 Play 乾淨安裝與升級成功，無 crash、ANR 或簽章錯誤。
- [ ] 首次啟動、拒絕／授予權限、離線 QR、Bluetooth／USB／TCP 連線與停止流程正常。
- [ ] 若保留手機位置分享，只有在使用者啟用後發生且關閉後停止；若移除，已證明 App
  不再取得／轉送手機位置或啟動 location FGS。影片不含真實位置或秘密。
- [ ] 無 radio 時仍可進入主要畫面；有 radio 時完成實際傳送佇列測試。
- [ ] Pre-launch report、Policy status 與 App Bundle Explorer 沒有未處理阻擋。

## Production

- [ ] promote 同一個已通過 Internal 測試的 artifact，未重新建置未測版本。
- [ ] 國家／地區、rollout 與 Managed publishing 已確認。
- [ ] 上線後有人監看 Android Vitals、支援信箱與 UGC 檢舉。
- [ ] 對外說明只陳述已驗證結果；queue accepted 不等於遠端 RF 收到。

全部勾選後才可送 Production。
