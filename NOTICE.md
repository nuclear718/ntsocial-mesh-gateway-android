# NTsocial MeshLink 著作權與來源聲明

版本：2026-07-19

## 專案主導與開源立場

NTsocial MeshLink 目前由 **LiberaNt LLC 與 NTsocial 團隊主導開發、整合與維護**。它是
Android NTsocial App 的核心 companion app，負責將 NTsocial 的通訊模型連接到
Meshtastic radio，並為獨立的 MeshCore 相容層保留擴充邊界。

NTsocial MeshLink 特有的原創程式、架構、整合、視覺、文件與修改：

Copyright (c) 2026 LiberaNt LLC.

除非另有書面權利移轉，個別貢獻者仍保有其貢獻的著作權。LiberaNt LLC 的主導維護身分是
治理與開發責任的說明，不是對既有上游作者或個別貢獻者著作權的排除。

開源不等於無作者、無著作權或公眾領域。本專案以 GPL 授予使用、研究、修改與散布的自由，
同時保留準確的作者、修改者與來源資訊。本聲明不增加 GPL 以外的限制。

## Meshtastic Android 上游

本專案是 [meshtastic/Meshtastic-Android](https://github.com/meshtastic/Meshtastic-Android)
的修改版本。可由 Git 歷史追溯的 fork 基準為
`c0d95d6ac4196fcbc705f2d3f174c7d9c46a77b2`；後續也可能選擇性整合其他上游變更。

Meshtastic Android 衍生部分：

Copyright (c) Meshtastic LLC and Meshtastic Android contributors.

這些部分依 GNU General Public License version 3（或原檔案允許的後續版本）提供。本倉庫保留
適用的上游著作權、GPL、免責與修改標示；根目錄 [LICENSE](LICENSE) 收錄 GPLv3 完整文字。

本 fork 不是 Meshtastic LLC 的官方發行版，LiberaNt LLC 對 fork 特有問題與修改負責。
「Meshtastic」名稱僅用於如實描述上游來源、協定與相容性，不表示贊助、背書或商標授權。

## NTsocial 原創與母程式邊界

下列工作是 LiberaNt LLC／NTsocial 在本 fork 中主導的主要範圍：

- `com.ntsocial.meshlink.*` 專案命名空間、NTsocial MeshLink 產品身分與品牌整合。
- 受保護的 Gateway Provider、capability、command/event IPC 與 parent-App 信任驗證。
- `PRIVATE_APP / port 256` NTsocial envelope、暫存、canonical NTsocial channel provisioning。
- NTsocial App companion 整合、雙 App 責任邊界、相關測試與文件。
- NTsocial 視覺語言、butterfly 品牌資產的整合，以及獨立 MeshCore KMP 相容層。

相鄰的 `NTsocial_release` 母程式是另一個產品與倉庫。它的公司身分、產品架構與設計語言可作
整合依據，但它的專有 EULA、`All Rights Reserved` 文字、封閉業務邏輯、私有資產、憑證與
秘密不適用於、也不得直接搬入這個 GPL 專案。這個界線確保本倉庫持續完整開源。

## MeshCore 相容性來源

本倉庫的 `core/meshcore` 與 `feature/meshcore` 是為 NTsocial MeshLink 建立的 Kotlin
Multiplatform 實作與 UI；它們不是 MeshCore 韌體或官方 MeshCore client 的鏡像。Companion
Protocol codec 的行為曾參考公開規格與下列可追溯版本：

- `meshcore-dev/MeshCore` commit `219812b9f136744c3478908e9487afd0d6031b53`
- `meshcore-dev/meshcore.js` commit `bbe1f9301b801cbd48a053687f16eea9634634cd`
- `meshcore-dev/meshcore_py` commit `5bac3573b51c4298062881885b6d15a994109076`
- 公開的 MeshCore Companion Radio Protocol 文件

MeshCore 專案及其 client 的權利仍屬各自作者與貢獻者；其公開資料標示 MIT License。為避免
把協定相容實作誤說成 LiberaNt 原創協定，相關 MIT 著作權與許可文字完整保存在
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。本專案自己的 Kotlin 實作作為整體仍依 GPL
散布。

「MeshCore」名稱僅表示相容性目標，不表示本 App 是 MeshCore 官方 client、受到 MeshCore
開發者背書，或已完成實機 transport。實際完成狀態以
[docs/meshcore-integration.md](docs/meshcore-integration.md) 為準。

## 其他第三方內容

- `core/proto` 是獨立的 Meshtastic protobufs Git submodule，保留其自身歷史與授權；不得由
  NTsocial 檔頭批次工具改寫。
- Gradle wrapper 保留原作者的 Apache-2.0 宣告。
- 依賴套件、字型、圖示與其他第三方資產仍受其各自授權約束；App 內的 acknowledgements
  由 AboutLibraries 產生，不能用本聲明取代。
- 任何新增的實質第三方程式或資產都必須保留其原始 notice，並同步更新第三方聲明。

## 標準檔頭的解讀

本倉庫的一般 Kotlin、Kotlin DSL 與 XML 檔頭會先列出 LiberaNt LLC 對 NTsocial MeshLink
原創與修改的權利，再以「where present」保留 Meshtastic Android 衍生部分的原宣告。這種
條件式寫法不會把 Meshtastic 著作權錯加到全新 NTsocial 程式，也不會把上游檔案冒稱為
LiberaNt 單獨創作。

詳細選用規則、例外與未來貢獻流程見
[docs/copyright-and-attribution.md](docs/copyright-and-attribution.md)。

## 授權

除個別檔案或第三方聲明明確另有標示外，NTsocial MeshLink 的合併作品依
**GNU GPL-3.0-or-later** 散布，並且不提供任何保證。著作權宣告用來記錄權利與來源，不是
`All Rights Reserved` 限制，也不得被解讀為削減 GPL 所授予的自由。
