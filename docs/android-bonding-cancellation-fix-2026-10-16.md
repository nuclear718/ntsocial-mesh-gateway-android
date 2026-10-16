# Android 配對取消誤報修正 — 2026-10-16

基準版本：`883c0e8f29` 加上本次工作目錄修正。時間使用 Asia/Taipei。

## 原因與修改範圍

`AndroidScannerViewModel.requestBonding()` 在 `viewModelScope` 等待 Android 配對，卻以一般
`catch (Exception)` 捕捉 `CancellationException`，再將例外內容寫入共用錯誤提示。因此，畫面／端點
ViewModel 結束所造成的正常工作取消，也會顯示 **Bonding failed: Job was cancelled**。

取消畫面的等待工作不等於 Android 系統配對失敗，也不等於已交給 fleet 管理的連線失敗。
`AndroidBluetoothRepository.bond()` 的取消處理會移除等待用的廣播接收器；它沒有撤銷系統配對。
fleet 的已啟動連線工作則由自己的 scope 管理。這解釋了警告與成功連線可以同時出現的機制。
現存 S24 logcat 沒有保留使用者先前那次警告，不能據此指定那次操作究竟由哪個畫面切換觸發。

修正是在一般錯誤處理之前重新拋出 `CancellationException`，保留協程取消語意。
真正的配對拒絕與 Bluetooth 權限錯誤仍顯示警告；沒有跳過配對或改寫 Bluetooth／fleet 架構。

- **Android**：修改 Android 配對 ViewModel，新增 Android host 回歸測試。
- **iOS**：對應 ViewModel 使用 `safeCatching`，已重新拋出取消例外；共享 BLE transport 也已有此處理。
  未發現同一段誤報路徑，未修改或安裝 iOS artifact。
- **Windows**：使用自己的 ViewModel；無產品程式變更。

## 可重現驗證

新增五個 Android host 測試：配對中清除 ViewModel、repository 取消、成功配對交給 fleet、實際拒絕、權限不足。
未修改產品程式時，前兩個案例都重現完全相同的錯誤文字，另三個通過；修正後五個全數通過。
測試使用真實 `ViewModelStore.clear()` 觸發生命週期取消，並檢查等待工作的清理與未發生連線登錄。

JDK 21、Android SDK 及 en-US 測試環境下，以下完整檢查成功，耗時 56 秒：

```sh
./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests \
  kmpSmokeCompile :app:lintFdroidDebug :app:lintGoogleDebug \
  :feature:connections:testAndroidHostTest :desktop:test --max-workers=2
```

共 2,018 tasks：122 executed、1,896 up-to-date。Kotlin/Native 測試執行仍受既有 convention 停用；
KMP 編譯不代表 iOS 實機或 native test 執行。此修正沒有改動 commonMain 或 Apple host。

## S24 安裝與實機範圍

在使用者授權的 S24 Ultra 上確認固定序號後，以 `adb install -r -t` 保留資料覆蓋安裝，沒有解除配對、
清除 App 資料、傳送測試訊息或修改無線電設定。套件維持 `com.ntsocial.meshlink.google.debug`、版本 `1.0.9 (10)`。

- APK：`app/build/outputs/apk/google/debug/app-google-arm64-v8a-debug.apk`。
- SHA-256：`4e866287d58990666d8fdb180c5299f4db561dca670971ed663f0b73bfeda0e2`。
- Android Debug 憑證與原安裝相符；安裝時間約 15:59，原始安裝時間維持 2026-10-05 09:29:11。
- 既有 1407 節點：連線 1/4 → 明確斷線 0/4 → 重連並顯示已連線 1/4。
- 該次程序 logcat 沒有 `Bonding failed:`、`FATAL EXCEPTION` 或 `Fatal signal`。
- 安裝前後四個既有資料庫、19 筆既有封包的完整列資料、history epochs 與 fleet 登錄保持不變。
- 最後實機測試後再次比對，以上資料仍相同；三個既有 channel/local/module config 檔案位元組相同。
  拉回的已安裝 APK 雜湊與交付 APK 相同，最後冷啟動後仍為 1407 已連線 1/4。

這是既有單一節點的連線驗證，不是全新節點首次配對、並行多節點或 RF 傳送驗證。
首次配對等待被取消的錯誤由上述回歸測試驗證；沒有宣稱實機重播了使用者先前的首次配對操作。

私有快照、UI 與 logcat 存在 Git 忽略的 `.agent_artifacts/bonding-cancellation-20261016/`。
`test-before.log` / `tests-before.xml` 保留修正前失敗，`gate.log` 保留修正後檢查結果。
