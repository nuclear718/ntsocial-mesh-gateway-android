# Contributing to NTsocial MeshLink

感謝你參與 NTsocial MeshLink。本專案目前由 **LiberaNt LLC 與 NTsocial 團隊主導開發與維護**，
是 Android NTsocial App 的核心開源 companion app；它同時也是 Meshtastic Android 的 GPL
fork，而不是 Meshtastic LLC 或 MeshCore 專案的官方發行版。

在開始前請閱讀 [AGENTS.md](AGENTS.md)、[NOTICE.md](NOTICE.md) 與
[著作權與來源政策](docs/copyright-and-attribution.md)。技術變更必須保留既有
Meshtastic radio/service/database/settings 相容性與 NTsocial Gateway 邊界。

## License、著作權與來源

提交貢獻代表你確認有權依本專案的 GPL-3.0-or-later 條款提供該內容。

- NTsocial MeshLink 原創與 LiberaNt LLC 的修改使用標準 LiberaNt-first 檔頭。
- Meshtastic Android 衍生檔案必須保留 Meshtastic LLC 的適用 copyright、GPL 與免責。
- 不得刪除或弱化第三方 license、notice、作者或修改日期。
- 不得把相鄰 `NTsocial_release` 的專有程式、`All Rights Reserved` 限制、私有資產、
  production credentials 或秘密搬進本 GPL 倉庫。
- 除非另有書面 CLA／assignment，個別貢獻者保留其貢獻的著作權。
- 新增或直接翻譯實質第三方程式時，必須同步更新
  [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

不要手動發明另一套檔頭。Kotlin／KTS／XML 的標準來源位於 `config/spotless/`，並由
Spotless 與 Detekt 驗證。

## How to contribute

- 從本 repository 的 `main` 或指定 feature branch 建立範圍清楚的分支。
- 先搜尋既有 issue／PR，避免重複工作。
- 以小而完整的 commit 實作，保留上游相容行為。
- 新功能與 bug fix 必須附上相稱的測試。
- PR 描述應說明「改了什麼、為什麼、如何驗證」，並標示任何上游來源。
- 若問題只存在於官方 Meshtastic Android、且未涉及本 fork，請同時考慮回報 upstream。

## Code style and architecture

- Kotlin 遵循 [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)。
- 使用 Compose Multiplatform、Navigation 3、Koin annotations、Ktor、Room KMP 與 Okio 的
  專案既有模式。
- `commonMain` 不得引入 `android.*` 或 `java.*`。
- project-owned package 使用 `com.ntsocial.meshlink.*`；生成的 Meshtastic protobuf 維持
  `org.meshtastic.proto`。
- shared string 放在 `core:resources`；新增後執行 `python3 scripts/sort-strings.py`。
- 不新增 Google Cloud、Maps、Play services、Firebase、Crashlytics、Datadog、ML Kit 或廣告
  runtime；`google` flavor 只是既有 Play 發布名稱。
- 用 `safeCatching {}` 保護 coroutine cancellation，避免 suspend path 的 `runCatching {}`。
- 請使用清楚命名、聚焦函式與必要註解，不使用 placeholder code。

完整規則以 [AGENTS.md](AGENTS.md) 與 `.skills/` playbooks 為準。

## Formatting, lint and tests

建置前先完成專案 bootstrap：

```bash
git submodule update --init
```

JDK 21 與有效 `ANDROID_HOME` 是必要條件。一般 implementation 變更至少執行：

```bash
./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests
```

KMP、flavor、Navigation、dependency 或 host wiring 變更另執行：

```bash
./gradlew kmpSmokeCompile :app:lintFdroidDebug :app:lintGoogleDebug
```

若測試依賴英文資源，設定：

```text
JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"
```

新增 Android dependency、manifest、signing、store-facing resource 或 release path 時，還要依
[AGENTS.md](AGENTS.md) 執行 Google release cloud-runtime guard 與 bundle 驗證。成功產生本機
bundle 不代表已簽章或 Play-ready。

## Pull requests

- 使用 conventional commit 風格標題，例如 `feat(scope):`、`fix(scope):`、
  `refactor(scope):`、`chore(scope):`。
- 保持 PR 聚焦；修正／polish commit 在送審前整理成合理的邏輯單位。
- UI 變更附上 screenshot 或清楚的視覺驗證說明。
- 說明任何無法執行的測試與原因，不得把未驗證功能描述成已完成。
- 不提交 APK、AAB、keystore、token、裝置 log 中的私訊／位置／密鑰或其他敏感資料。

## Issue reporting

請提供可重現步驟、預期行為、實際行為、版本／flavor 與已去識別化的必要 log。切勿公開貼出
private message、精確位置、PSK、token、signer digest 或 pairing credential。

安全漏洞請依 [SECURITY.md](SECURITY.md) 私下回報。

## Community standards

本 fork 的協作規範見 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。尊重原作者、上游社群、
NTsocial 使用者與其他貢獻者，是技術品質的一部分。

感謝你協助 LiberaNt LLC／NTsocial 團隊把 NTsocial MeshLink 建成可信、可審查、可長期協作的
開源通訊平台。
