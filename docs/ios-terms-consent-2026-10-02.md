# iOS 首次啟動使用條款 — 2026-10-02

本次變更範圍為 **iOS MeshLink 首次啟動條款與同意阻擋**。Android、Windows、共用訊息／資料庫／無線電協定與父 App 不變。
本文件不是 App Store UGC 全面合規、實機測試或審核通過證明。
來源為 `b0e3a9fe94` 加上本次未提交的工作目錄變更。

## 條款來源與行為

- 官方 [英文條款](https://www.liber-ant.com/terms/#english)、[繁體中文條款](https://www.liber-ant.com/terms/#traditional-chinese)、[日文條款](https://www.liber-ant.com/terms/#japanese) 的版本均為 `2026-10-02`。2026-10-02 透過 HTTPS 取得並核對版本、錨點及安全政策。
- 第一個互動畫面顯示使用者條款／EULA、零容忍政策、開發者收到檢舉後 24 小時內的處理承諾、完整條款／社群規範／隱私權／Apple 標準 EULA 連結。
- 可以在畫面中切換繁體中文、英文、日文；預設參考 App 的 `AppleLanguages`，再參考裝置語言。不支援的語言退回英文。此選擇是協議閱讀語言；同意後仍沿用既有 Compose 首次語言設定流程。
- 同意開關預設關閉；勾選後還須按「同意並繼續」。切換協議語言會清除尚未提交的勾選。拒絕、重新啟動或開啟深層連結均不會自動同意。
- 同意前不建立 Compose root、KMP runtime、BLE 或 Gateway bootstrap；Gateway／功能深層連結只暫存在畫面狀態，同意後才交給既有 runtime。未同意時終止程序會捨棄該連結，呼叫端可重新開啟。
- 以 App 私有 `UserDefaults` 的單一紀錄保存版本、語言、時間。不得存入 App Group 或由父 App 的同意狀態代替。已有安裝但沒有此紀錄的使用者也會看到條款。
- 重新啟動時，只有完整且版本相同的紀錄可略過條款；版本變更或紀錄無效會重新要求同意。更新官方條款時，必須一起更新 `TermsConsentStore.currentVersion`、畫面文案與測試，不可只改網址上的文件。
- 安全摘要可離線顯示；完整官網文件需要網路。社群行為條款補充 Apple Standard EULA，保留 GPL 與第三方授權權利。

## 仍需完成的 Apple UGC 項目

[Apple Review Guideline 1.2](https://developer.apple.com/app-store/review/guidelines/#user-generated-content) 與本次審查訊息要求的其他措施，不會因新增 EULA 自動完成：

| 項目 | 本次來源檢查／尚待證據 |
| --- | --- |
| 明確同意條款與零容忍政策 | 本次加入，尚待 Xcode 與實機驗證 |
| 不當內容過濾 | 未找到專用不當文字／UGC 過濾流程；既有封包過濾不等同內容審核 |
| App 內內容／使用者檢舉 | 訊息與節點畫面未找到完整檢舉路徑；聯絡信箱連結不能代替此功能 |
| 封鎖與立即移除內容、通知開發者 | 現有 `NodeContextMenu` → `NodeManagementActions` 有 Ignore 操作；必須另外驗證既有歷史立即隱藏、後續互動阻擋，並完成實際開發者通知。開啟郵件草稿不代表已送達 |
| 收到檢舉後 24 小時內移除／停權 | 官網已有承諾；需實際營運接收、審核、處置和時限追蹤。分散式第三方無線電網路不能宣稱可全網停權或遠端刪除所有副本 |

上述缺口需在發布前完成與測試；不得以本變更宣稱全部五項已符合，也不得以父 App 的功能取代獨立 MeshLink 的功能證據。

## 可重播驗證

2026-10-02 Windows 本機驗證：

- 四個 Swift 檔案通過語法解析；三語各 18 個文字鍵的語法與鍵集合一致；Xcode source/resource 參照與物件 ID 檢查通過；測試 shell 腳本通過 `bash -n`。這些不是 SwiftUI 型別檢查或 App 執行證明。
- `spotlessApply spotlessCheck detekt assembleDebug test allTests --max-workers=4` 通過，1,703 tasks（452 executed、9 from cache、1,242 up-to-date）。初次 baseline 的兩個 Android flavor 在既有 `SecondaryRadioEndpointScopeRuntimeTest` 遇到 SQLite `Error code: 14`；測試暫存資料庫路徑為 257 字元。設定 `JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US -Djava.io.tmpdir=C:/mlt"` 並使用已存在的短暫存目錄後重跑通過，沒有修改產品／測試程式以繞過例外。
- `kmpSmokeCompile :app:lintFdroidDebug :app:lintGoogleDebug :ios:runtime:jvmTest :ios:runtime:compileKotlinIosArm64 :ios:runtime:compileKotlinIosSimulatorArm64 :ios:runtime:compileTestKotlinIosSimulatorArm64 :ios:runtime:linkDebugFrameworkIosSimulatorArm64 --max-workers=4` 通過，1,122 tasks（296 executed、1 from cache、825 up-to-date）。**framework link 在 Windows 上是 SKIPPED**；不能將命令成功視為 framework 已連結或 Apple host 已建置。既有 convention 仍禁用 Native test executable link/run。
- 本機沒有 `xcodebuild`、`xcrun` 或 Swift compiler；下面的 Foundation 同意紀錄測試已加入，但尚未執行。SwiftUI 組建、啟動、實機 BLE／Gateway 與錄影仍待 macOS／Apple 裝置完成。

macOS 上執行 `bash iosApp/Tools/run_terms_consent_tests.sh`。它直接編譯 production Foundation consent store，檢查全新安裝、未勾選拒絕寫入、三語紀錄、重新建立 store、版本／欄位／時間錯誤、舊 Boolean 與語言回退。
它不啟動 SwiftUI、KMP 或實機。

依 `AGENTS.md` 執行 baseline、KMP/lint、`:ios:runtime:jvmTest` 與 iOS framework/source/test compilation。
另在 macOS 執行：

```sh
xcodebuild -project iosApp/NTsocialMeshLink.xcodeproj \
  -scheme NTsocialMeshLink -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

模擬器與實機檢查：

1. 使用測試裝置全新安裝，確認第一個畫面是條款，且未先跳出 Bluetooth／相機要求。
2. 分別切換三語，核對文案與官網對應錨點；確認切換後同意開關重設。檢查 VoiceOver、大字體、深色／淺色、橫向、小尺寸 iPhone 與 iPad 的捲動可用性。
3. 未勾選時不能繼續；拒絕後仍停留於條款。關閉並重啟仍需同意。離線時也可閱讀安全摘要。
4. 未同意時開啟 `ntsocial-meshlink://process` 及既有功能深層連結，仍停留在條款，沒有 radio/Gateway 操作。
5. 勾選並同意，確認進入原本語言選擇／主畫面，既有 BLE、主要 endpoint Gateway 與 lifecycle 運作。重啟同版本不重複要求；升級舊版本／缺少紀錄仍要求同意。
6. 測試版本變更重新同意，確認未自動替舊版紀錄升級。勿為錄影刪除使用者正式環境資料。

## 實體裝置錄影與 Review Notes

Apple 要求 **實體裝置** 錄影；模擬器、程式碼檢查及本文件不能代替。錄影需顯示全新啟動 EULA、拒絕阻擋、三語選擇及明確同意，並在其餘功能完成後接續示範：

1. 在 App 內檢舉一則測試不良內容，展示實際送達或明確的待送／失敗結果。
2. 在 App 內封鎖測試濫用者，展示舊內容立即從相關列表／對話消失，並驗證通知開發者與後續阻擋。
3. 將可供審查開啟的錄影連結放入 App Store Connect 的 App 審查資訊備註，並在回覆審查訊息時附上。須使用實際網址，不能填入範例／佔位連結。

目前沒有實機錄影或 App Store Connect 上傳結果，也沒有實際檢舉處理完成的證據。
