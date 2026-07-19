# NTsocial MeshLink 著作權與來源政策

本文件是未來維護者、協作者與自動化工具處理檔頭、第三方程式及來源聲明時的操作規則。
專案治理與來源總表見根目錄 [NOTICE.md](../NOTICE.md)。

## 原則

1. **主導權要清楚。** LiberaNt LLC 與 NTsocial 團隊自 2026 年起主導這個 fork 的產品、
   架構、整合與維護；README、NOTICE 與檔頭都應明確呈現。
2. **權利宣告只涵蓋實際工作。** LiberaNt LLC 的 copyright 僅涵蓋 NTsocial MeshLink
   原創內容與可受著作權保護的修改，不冒稱擁有未修改的 Meshtastic／MeshCore 內容。
3. **上游 notice 不可消失。** 衍生自 Meshtastic Android 的檔案保留 Meshtastic LLC 宣告、
   GPL、免責與修改日期。實質複製第三方內容時保留其完整 license/notice。
4. **開源不等於放棄著作權。** 本專案使用 GPL 授權他人自由使用、研究、修改與散布；
   不使用會造成額外限制或與 GPL 混淆的 `All Rights Reserved` 專有文字。
5. **來源必須可追溯。** Git 歷史、fork point、第三方 commit 與集中聲明共同構成稽核鏈。

## 標準檔頭

一般 Kotlin、Kotlin DSL 與適用的 XML 檔案使用以下語意：

```text
NTsocial MeshLink original work and modifications:
Copyright (c) 2026 LiberaNt LLC

Meshtastic Android-derived portions, where present:
Copyright (c) 2026 Meshtastic LLC

Developed and/or modified for NTsocial MeshLink in 2026.
SPDX-License-Identifier: GPL-3.0-or-later
```

「where present」是必要限定：它讓同一個自動格式化範本可以安全用在大量 fork 檔案與全新
NTsocial 檔案。前者仍保留 Meshtastic 權利；後者不會因此被宣稱含有 Meshtastic 程式。

完整 GPL 免責文字繼續留在檔頭。格式化與靜態檢查的來源檔為：

- `config/spotless/copyright.kt`
- `config/spotless/copyright.kts`
- `config/spotless/copyright.xml`
- `config/spotless/copyright.txt`
- `config/detekt/detekt.yml`
- `config/detekt/license.template`

任何修改檔頭格式的 PR 必須同步更新這些檔案，並執行 `spotlessCheck` 與 `detekt`。

## 檔案分類

### 1. NTsocial MeshLink 原創檔案

包括 Gateway IPC、NTsocial envelope/channel provisioning、parent-App integration、
NTsocial 品牌整合、專案文件，以及本倉庫建立的 MeshCore KMP codec/model/UI。

- 必須有 LiberaNt LLC／NTsocial MeshLink 宣告。
- 使用 GPL-3.0-or-later。
- 若只是依公開協定建立相容實作，應在文件中列出規格來源；不要把協定名稱冒稱為自有發明。

### 2. 已修改的 Meshtastic Android 檔案

包括 package migration、KMP/Navigation/DI 接線、功能修改、刪除雲端或 map runtime、
NTsocial 視覺與 Gateway 整合所觸及的上游檔案。

- 使用標準雙層檔頭。
- 保留 Meshtastic LLC 宣告。
- 以 2026 年修改標示與 Git 歷史滿足 GPL 的 modified-version traceability。
- 不把 fork 的問題歸責於上游。

### 3. 原樣或近乎原樣的上游／第三方檔案

- 保留原始檔頭，不為了「統一」而覆寫。
- Gradle wrapper 的 Apache-2.0 notice 不得改成 GPL 或 LiberaNt。
- `core/proto` submodule 不得由本倉庫格式化器改寫。
- 原始 license 文件不得修改內容。

### 4. 生成檔、二進位資產與外部素材

- 不對 generated code、Room schema、build output、APK/AAB、PNG、字型或第三方素材盲目加
  程式檔頭。
- 以來源 metadata、LICENSE、NOTICE、資產清單或 DEP5 類型清單記錄權利。
- 不得把相鄰 `NTsocial_release` 的專有程式、私有資產、秘密或 production credentials
  搬進本 GPL 倉庫。

## MeshCore 相容層

`core/meshcore` 與 `feature/meshcore` 是 LiberaNt LLC／NTsocial 在本倉庫建立的 Kotlin
實作；但其中的 command、frame 與資料語意來自公開 MeshCore 生態。維護者必須：

- 保留 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) 中的 MIT notice。
- 在協定文件記錄精確上游 commit。
- 若未來直接翻譯或複製更多 upstream source，先保存該檔案原始 header，再更新第三方聲明。
- 不使用「官方 MeshCore client」或已完成 transport 的字樣，除非事實與授權均已驗證。

## 貢獻與權利

除非另有書面貢獻者協議或權利移轉：

- 貢獻者保留其貢獻的著作權。
- 提交貢獻即表示有權依本專案 GPL 條款提供該內容。
- 公司主導與 maintainer 決策權不等於自動取得每位外部貢獻者的著作權。
- 若 LiberaNt LLC 未來需要集中權利，應另行建立清楚、可選擇且可稽核的 CLA／assignment
  流程，不得只靠檔頭文字推定。

## PR 檢查表

- [ ] 新檔案使用正確檔頭與 SPDX identifier。
- [ ] 修改上游檔案時沒有刪除上游 copyright、GPL 或免責。
- [ ] 引入第三方程式／資產時已保存完整 license/notice。
- [ ] 沒有把專有母程式限制文字加到 GPL 本倉庫。
- [ ] README、NOTICE、THIRD_PARTY_NOTICES 與實際程式來源一致。
- [ ] `spotlessCheck`、`detekt` 與 `git diff --check` 通過。

## 稽核依據（2026-07-19）

- Meshtastic Android fork point：
  `c0d95d6ac4196fcbc705f2d3f174c7d9c46a77b2`
- 目前 2,090 個 tracked files 中，有 1,557 個自 fork point 後變更，135 個為新增。
- 稽核前有 1,252 個檔案只顯示 `Copyright (c) 2026 Meshtastic LLC`；其中包含
  NTsocial Gateway 與全新 MeshCore 模組，因此需要本次雙層檔頭修正。
- MeshCore 參考版本與著作權文字記錄在根目錄 NOTICE 與第三方聲明。
