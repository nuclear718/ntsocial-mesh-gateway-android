# Play Console App content 填寫稿

最後更新：2026 年 7 月 18 日

Console 欄位會隨帳號與政策調整；若畫面文字不同，以實際 AAB、App 行為與 Console
最新問題為準。不要用這份稿件覆蓋已知的 runtime 差異。

## 建議答案總表

| 項目 | 建議答案／動作 |
|---|---|
| Privacy policy | 填穩定、公開、無需登入、非 PDF、使用者不可編輯的 HTTPS URL |
| Ads | **No** |
| Advertising ID | **No** |
| App access | 不需帳號或特殊資格；貼入下方審查說明 |
| Target audience | 建議只選 18+；發布者須確認這是實際產品決策 |
| Designed for Families | **No** |
| Content rating | 如實回答通訊、UGC、陌生人內容與選用位置分享，讓 IARC 計算 |
| Data safety | 依 [Data safety 填寫稿](03-data-safety-zh-TW.md)，目前不建議回答「完全不收集」 |
| Foreground service | 宣告 `connectedDevice`；保留手機位置功能時另宣告 `location` |
| Account deletion | App 不建立帳號，選不適用／沒有帳號功能 |
| News／Magazine | **No** |
| Health／Financial／Government／COVID／VPN | **No** |
| Payments | 無 Play Billing、訂閱、IAP 或付費數位內容 |

## App access

Play 的 App access 是詢問登入、會員、付費牆或其他受限內容，不是詢問是否需要 radio
硬體。選擇不需特殊存取，並貼入：

```text
NTsocial MeshLink 不需要帳號、密碼、OTP、會員資格、訂閱或付費。審查人員可略過非必要權限並直接進入連線、訊息、節點、MeshCore 與設定頁；本版本沒有地圖頁。

Bluetooth、USB、TCP radio session 與真實 LoRa 收發需要相容的 Meshtastic 無線電，但不需測試帳密。無硬體時仍可檢查主要 UI、權限流程與設定。若審查需要硬體流程，請聯絡 huangct_2025@liber-ant.com 取得可重現的測試安排。

受保護的 NTsocial Gateway 只允許通過套件與簽章驗證的 NTsocial App 存取；這不影響審查人員直接操作 MeshLink 主介面。
```

## 目標對象與內容分級

首發建議只選 18+，因為本 App 涉及無線電設定、公開 mesh、陌生節點、位置與未預先審核
內容。這不是規避 UGC 政策；18+ App 仍必須具備條款接受、App 內檢舉與一對一封鎖。

IARC 至少如實表達：

- App 可以傳送、接收及顯示 User Generated Content；
- 內容可能來自未知 mesh／MQTT 參與者，發布者不會逐則預先審核；
- 使用者可以傳送文字、節點名稱、反應及選用位置；
- App 沒有購買、賭博、廣告、性內容或暴力內容功能，但外部 UGC 可能出現不當內容；
- block／ignore 與 App 內檢舉必須在送審版本中實際可用。

目前程式搜尋尚未證明「首次發送前接受條款」或 App 內內容／使用者檢舉已實作。只有
email、GitHub issue 或公開政策頁不滿足 UGC safeguard；這是 Production 阻擋。

## 其他聲明

- 本 App 不是 News／Magazine、Health、Financial、Government、COVID、VPN、Dating、
  Gambling、教育兒童或購物 App；
- 本 App 不含廣告、Advertising ID、Play Billing 或訂閱；
- 本 App 不允許建立發布者帳號，因此沒有帳號刪除 URL；本機資料可在 App／Android
  設定刪除，mesh 或第三方端點上的副本不受發布者控制；
- 本 App 是 GPL-3.0 Meshtastic Android 分支，不是 Meshtastic 官方發行版。

## Target API 與 Android 17 位置決策

目前專案 `targetSdk` 為 37。這已高於 Google Play 自 2026 年 8 月 31 日起對新 App／更新
要求的 API 36，但也會使 App 進入 Android 17 的精確位置 minimum-scope／location button
政策範圍。Google 預計在 2026 年 10 月下旬開始執法，時程仍應在送審當天重新確認。

目前 onboarding 會要求 Fine＋Coarse location，且服務只要已有位置權限就加入 location
FGS。送審前必須做明確產品決策：

1. 保留手機位置提供：改成使用者啟動、最小權限、符合 location button／精確位置理由，
   並完成揭露、同意與 FGS 示範；或
2. 首發移除手機位置提供與 location FGS，只保留舊 Android Bluetooth 掃描真正需要的
   最小權限範圍。

不能把目前行為直接標示為已符合政策。詳見
[審查與權限文件](04-review-and-permissions-zh-TW.md)。

## 官方參考

- [Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
- [User Generated Content](https://support.google.com/googleplay/android-developer/answer/9876937)
- [User Data](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Content ratings](https://support.google.com/googleplay/android-developer/answer/9859655)
- [準備 App 審查](https://support.google.com/googleplay/android-developer/answer/9859455)
