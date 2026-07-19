# NTsocial MeshLink 使用條款

最後更新：2026 年 7 月 19 日

> **送審前草稿：** App 內條款接受、檢舉與 block／ignore 流程尚待完成及實測。完成後請
> 移除本段，再把此條款發布成正式公開 HTTPS 頁面；目前不可直接拿本檔 URL 送審。

本條款適用於 LiberaNt LLC 發布的 **NTsocial MeshLink** Android App（套件名稱
`com.ntsocial.meshlink`，以下稱「本 App」）。使用本 App 前，請閱讀本條款、
[隱私權政策](PRIVACY_POLICY.md)及[社群與通訊規範](COMMUNITY_GUIDELINES.md)。使用者在
App 顯示的接受流程中同意後，才可建立或傳送使用者產生內容。

## 1. 產品主導、著作權與開放原始碼

本 App 由 LiberaNt LLC 與 NTsocial 團隊主導開發、整合、發布及持續維護，是 Android
NTsocial App 的核心開源 companion／radio gateway。

LiberaNt LLC 對 NTsocial MeshLink 特有的原創程式、可受著作權保護的修改、Gateway／
母程式整合、文件與品牌視覺成果主張著作權。除非另有書面權利移轉，個別貢獻者仍保有其貢獻
的著作權；公司主導與維護責任不表示 LiberaNt 擁有未修改的上游內容。

本 App 是以 Meshtastic Android 為基礎的 GPL-3.0-or-later 開源分支。GPL 授予使用者取得
原始碼、使用、研究、修改及再散布的自由；LiberaNt 的著作權聲明不增加 `All Rights
Reserved`、EULA 或其他專有程式限制。本條款只規範已發布 App 與發布者可控制功能的使用，
不取代或縮減 GPL、MIT、上游或其他第三方開源授權所授予的權利。

Meshtastic Android 衍生部分、MeshCore 參考材料與第三方內容仍屬各自作者並保留原授權。
本 App 不是 Meshtastic 或 MeshCore 官方發行版，也不表示其贊助、背書或商標授權。完整
來源、著作權與授權文字見原始碼 repository 的 `LICENSE`、`NOTICE.md` 與
`THIRD_PARTY_NOTICES.md`。

## 2. 功能與使用者責任

本 App 用於連接 Meshtastic 相容無線電，並在 NTsocial App 與 LoRa mesh 間提供傳輸
閘道。部分功能需要相容硬體、韌體、Bluetooth／USB／TCP、位置權限或使用者自行設定的
MQTT／TAK 端點。

使用者必須：

- 遵守所在地的無線電、頻率、功率、執照、隱私與內容法規；
- 對自己設定的頻道、金鑰、伺服器、節點名稱、訊息、位置與其他內容負責；
- 在分享他人位置、個資或機密資訊前取得必要同意；
- 妥善保管 PSK、憑證、token 與裝置存取權。

## 3. 訊息與其他內容

使用者保留自己內容的權利。當使用者要求本 App 傳送訊息、位置、節點名稱、遙測或其他
資料時，即授權 App 依其設定處理、暫存、顯示、編碼與轉送，以完成 mesh、NTsocial
Gateway、MQTT、TAK 或 TCP 功能。

公開頻道、Ham mode、共享 PSK、未加密 MQTT 或第三方橋接可能讓其他參與者讀取、保存
或再次轉送內容。請勿把公開 mesh 視為私密、保證加密或可以撤回的通訊管道。

## 4. 禁止行為

不得使用本 App：

- 從事違法、詐欺、威脅、仇恨、騷擾、跟蹤、霸凌、人口販運或鼓勵暴力的行為；
- 製作或散布兒童性剝削、未經同意的私密影像或其他依法禁止內容；
- 未經同意揭露住址、精確位置、身分、憑證、PSK、token 或其他敏感資料；
- 冒充他人、政府、救援機關或緊急服務，或發送明知不實的緊急訊息；
- 發送垃圾訊息、惡意程式、釣魚內容，或蓄意耗盡 mesh airtime、裝置或服務；
- 繞過權限、簽章、套件驗證、single-use capability 或其他安全控制；
- 使用未經授權的頻率、功率、加密或識別設定；
- 侵害著作權、商標、隱私、資料保護或其他第三方權利。

## 5. 封鎖與檢舉

使用者可使用 App 內的 block／ignore 停止顯示特定節點的後續內容，並使用 App 內檢舉
入口回報內容或使用者。若 App 內流程無法使用，可寄信至
`huangct_2025@liber-ant.com`，提供時間、節點或頻道識別、問題說明及已遮蔽無關個資的
必要證據；不要提供 PSK、token 或私密金鑰。

因 mesh 是去中心化網路，發布者通常無法刪除其他 radio、MQTT 或參與者裝置上的副本，
也不保證能阻止對方換用其他節點。發布者仍可在可控制的範圍內提供封鎖指引、修正 App、
限制受控服務、保存必要證據或依法配合。

立即人身危險請直接聯絡所在地緊急服務；本 App 不是報案或救援服務。

## 6. 無保證送達

LoRa 與 mesh 受硬體、韌體、法規、距離、地形、干擾、節點密度及 airtime 影響。
`COMMAND_ACCEPTED` 或本機 queue accepted 只表示本機佇列接受命令，不表示遠端收到。
在法律允許範圍內，本 App 依現況提供，不保證不中斷、無錯誤、即時或適合特定目的。

## 7. 第三方與責任限制

Meshtastic 韌體、radio 硬體、使用者自選 MQTT／TAK／TCP、外部網站及 Google Play／
Android 平台由各自提供者負責。本版本不內嵌第三方地圖、分析或當機回報 SDK。

在適用法律允許的最大範圍內，發布者不對通訊遺失、延遲、誤傳、第三方保存或轉送、
無線電違規、裝置損壞或資料遺失所生的間接、附帶、特殊或衍生損害負責。本條不排除法律
不得排除的責任或消費者權利。

## 8. 變更與聯絡

功能、政策或法規重大改變時，我們可更新本條款與日期並提供適當通知。

- 發布者：LiberaNt LLC
- 開發與維護：LiberaNt LLC 與 NTsocial 團隊
- 電子郵件：huangct_2025@liber-ant.com
- 原始碼：https://github.com/nuclear718/ntsocial-mesh-gateway-android
- 問題回報：https://github.com/nuclear718/ntsocial-mesh-gateway-android/issues
