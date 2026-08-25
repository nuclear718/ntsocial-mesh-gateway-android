# Android NTsocial MeshLink 多節點版本三機問題修正與硬體回歸報告

- 日期：2026-08-25（Asia/Taipei）
- 產品軌：Android `NTsocial MeshLink`
- 分支／基底：`multi_nodes_`，開始時 HEAD `2c62c6bc0`
- 被測模式：三支 Android 16/API 36 arm64 手機；其中兩支各自連接一個既有 Meshtastic BLE 節點，另一支沒有選定節點
- 參考基準：`ANDROID_MULTI_NODE_THREE_PHONE_HARDWARE_TEST_REPORT_2026-08-24.md`

## 1. 結論

參考報告中的四個已確認 P1 均已重現、找出根因、以最小範圍修正，並完成修改後的單元、跨平台編譯與三機硬體回歸：

1. `/channels` 等 nested deep link 不再把非 top-level graph 當成目前分頁，三機皆不再 crash。
2. `install -r` 後，原本有節點的兩支手機不需手動開啟 App 即可恢復 MeshService、FGS 與 Stage 2；無節點手機會在既有 grace period 後停止服務。
3. Gateway Provider 在對外可查詢前會同步完成一次 app-owned、thread-safe、idempotent Koin bootstrap；100 次 cold Provider 壓力測試沒有 Koin 或 fatal failure，ADB shell 仍 100/100 被安全層拒絕。
4. BLE auto scan 改為 12 秒有界 sweep，Connected 或 Activity pause 時停止，並移除永久 indeterminate animation。原問題手機的 Connections 70 秒平均從約 71.1% one-core／126 rendered frames/s 降為 4.69%／8.44 rendered frames/s。

依使用者 2026-08-25 的指示，原訂 185 分鐘的最終 soak 在所有明顯錯誤計數持續為 0 後提前停止。完成的共同範圍是每機 59 個一分鐘樣本（minutes 0–58）及 12 個自動 UI 事件，不可宣稱為三小時測試。

D3 的間歇性 CPU／能耗候選仍存在；本輪沒有找到 crash、timeout、重連、busy loop 或單一方法級根因，因此沒有推測性修改 Room、packet handler、BLE priority 或 RF scheduler。

## 2. 已確認根因與修改

| 問題 | 已確認根因 | 最小修改 |
|---|---|---|
| Nested deep link crash | `MultiBackstack.handleDeepLink()` 以 path root fallback 為目前 top-level tab；Channels/Firmware graph 沒有獨立 back stack | 只有真正 top-level route 才切 tab／replace；nested path append 到目前真實 tab；缺少目標 stack 時 no-op |
| Package replacement 不自癒 | Receiver manifest 有 `MY_PACKAGE_REPLACED`，程式卻只接受 `BOOT_COMPLETED`；cold DataStore `.value` 也可能暫時為空 | Receiver 只接受 boot／own-package-replaced，直接啟動既有 MeshService；由 service 的 authoritative hydration 與 bounded no-device grace 決定保留或停止 |
| Cold Provider/Koin race | ContentProvider 可早於 `Application.onCreate()` 接受 Binder query，lazy injection 當時沒有完整 Koin graph | Gateway Provider host wrapper 在 `onCreate()` 返回前呼叫單一 Android bootstrap；Application 重用相同 bootstrap，移除 double-start/load |
| 無限掃描與 120 Hz churn | `Duration.INFINITE`、偏好值在 Connections 每次進入都啟動掃描、Activity pause 不保證 dispose、indeterminate progress 永久逐 frame 動畫 | 12 秒 timeout；只在 resumed、偏好開啟、尚未 Connected、scan idle 時 auto-start；Connected/pause 停止；保留手動掃描按鈕但移除 indeterminate bar |

修改沒有擴張多節點架構、Gateway contract、Room schema、訊息內容、頻道內容、RF 傳送或 BLE transport ownership。

## 3. 建置與自動測試

### 3.1 Focused gate

下列測試通過：

- Navigation router／multi-backstack integration tests，包括 Channels、Firmware、Wi-Fi nested paths 與 missing-stack no-op。
- Scanner ViewModel 測試，包括精確的 12 秒 timeout 與 stop 行為。
- Android host BootCompleteReceiver allow-list 測試。
- Android app Provider/Koin bootstrap idempotency 與完整 root graph 測試。
- Changed-module Spotless 與 Detekt。

### 3.2 Full gate

JDK 21、`en-US` 下執行：

```text
spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile
:app:lintFdroidDebug :app:lintGoogleDebug :app:bundleGoogleRelease --continue
```

- 2,180 actionable tasks：428 executed、11 from cache、1,741 up-to-date。
- 通過：format、兩個 Android Debug assemblies、tests、`allTests`、Desktop/JVM、shared KMP、iOS Simulator compilation、F-Droid/Google Debug lint、Google Release R8、Lint Vital、AAB packaging、cloud-runtime guards。
- 整體 exit 1 僅因六個既有且未修改來源的 Detekt findings：BLE 3、domain 1、model 1、network 1。
- 本次修改模組沒有新增 Detekt finding。

### 3.3 最終產物

- Google arm64 Debug APK：version `1.0.6 (7)`，51,393,739 bytes。
- SHA-256：`8BC272A926D58AE811B63951707B0BF233D5BBC68F23A5300058607B9652C265`。
- 16 KiB zipalign：通過。
- Google Release AAB：24,572,387 bytes，SHA-256 `D37BF171A893DB77E0FCD0E6A5BF4855B8419E5A8AEDFD5152694BC0B0F8DE4`。
- AAB 是本機未配置 upload keystore 的 pipeline artifact，不可宣稱 Play-ready 或已被 Play 接受。

## 4. 三機安裝、自癒與基本功能

### 4.1 保留資料覆蓋安裝

- 三機 `adb install -r -t` 均成功。
- 三機 installed base APK SHA-256 都與最終 APK 相同。
- 三機 `firstInstallTime` 都保留既有 2026-08-21 時間，沒有清除 App 資料。
- 沒有 Room migration verification error。

### 4.2 不手動開啟的更新自癒

- D2：熄屏 package replacement 後在第一個觀察點即完成 Stage 2，FGS 持續正常。
- D3：OPPO 熄屏背景喚醒較慢；約 21 秒建立程序／服務，約 41 秒完成 FGS 與 Stage 2，之後四分鐘 PID 與服務保持穩定。
- D1：無選定節點時曾短暫啟動 FGS，約 15–21 秒後由既有 startup grace 停止服務。
- 三機 `ForegroundServiceStartNotAllowedException`、Koin、fatal、migration、liveness／Stage 2 timeout、forced reconnect 均為 0。

### 4.3 Cold Provider

在無節點手機執行 100 次 process force-stop → cold Provider query：

- Provider process 成功啟動：100/100。
- 完整 Koin bootstrap event：100/100。
- Koin output/log error：0。
- fatal：0。
- 未授權 ADB shell query 被拒絕：100/100。

這證明 Provider publish 前的 DI invariant 與 fail-closed shell 邊界；它不是授權 parent 的 status → channels → status 功能／內容驗證，也沒有改寫 Gateway 資料。

### 4.4 UI 與 deep links

- 修改後 Messages、Nodes、Settings：三機 9/9 頁面成功顯示，PID 不變，fatal 0。
- `channels`、`firmware`、`firmware/update`、`wifi-provision`：三機 12/12 成功顯示，PID 不變，`Stack for ... not found` 0，fatal 0。
- Settings → external `/channels` → system Back → Settings：三機兩輪均通過；最終 soak 中的 3/3 事件也維持同一 PID。
- D2/D3 Connections 顯示 Connected；D1 顯示無選定裝置。

這些是 read-only／navigation smoke checks。本輪沒有傳送訊息、寫入頻道、啟動韌體更新或執行 Wi-Fi provision。

## 5. 效率驗證

### 5.1 Connections A/B

同一支 D2、同一 PID、Connections 前景七個 10 秒窗：

| 指標 | 修正前重現 | 修正後 |
|---|---:|---:|
| 平均 one-core CPU | 約 71.1% | 4.69% |
| 平均 rendered frames/s | 約 126.2 | 8.44 |
| 70 秒 `setRequestedFrameRate frameRate=NaN` | 17,934 | 1,592 |
| fatal | 0 | 0 |

修改後的剩餘 frame/CPU 是間歇 UI／資料事件，不再是固定 120 Hz animation。

### 5.2 手動 scan 與 lifecycle

- 手動 scan 可在 Connected 狀態啟動，約 12 秒 timeout 後 UI 回到 idle。
- scan 中按 Home，返回 Connections 後 scan 為 inactive、PID 相同、Connected 保留、fatal 0。
- 返回 Connections 的 45 分鐘 checkpoint，D2/D3 都是 Connected 且 scanning=false。

### 5.3 D3 候選問題

三分鐘熄屏六窗對照：D2 平均 2.56% one-core，D3 平均 6.42%；兩機 PID／FGS 穩定，fatal、disconnect／forced reconnect、Stage 2 timeout 均為 0。

提前結束的 59 分鐘共同 soak 全窗（包含自動 UI 轉場）平均：

| 裝置 | 平均 CPU | 最大 CPU | RSS KiB 範圍 | PSS KiB 範圍 | Threads 範圍 |
|---|---:|---:|---:|---:|---:|
| D1，無 radio | 0.27% | 2.40% | 298,932–321,096 | 246,942–271,897 | 43–49 |
| D2，一個 radio | 7.95% | 16.79% | 345,184–398,116 | 254,788–299,814 | 60–66 |
| D3，一個 radio | 16.42% | 37.44% | 298,980–362,172 | 225,777–245,287 | 67–77 |

D3 高窗常在轉場或例行週期事件附近，之後會回落；沒有 runaway thread、OOM、crash、liveness 或 reconnect 證據。Debug build、不同 SoC/OEM、不同 radio workload 與 UI 擷取會影響百分比。沒有相同 payload 的 Profile/Perfetto 方法級控制，因此仍不能宣稱 packet/Room/GC 的唯一根因，也不能用推測修改核心路徑。

## 6. 提前結束的 soak

- 原訂：185 分鐘。
- 實際完成共同窗：minutes 0–58，共 59 samples/device、177 個完整三機 samples。
- 另在 minute 59 中途停止後留下 D1 一筆 partial sample；統計已排除。
- 自動 UI events：12/12 通過（minute 0 Connections、15 Messages、30 Settings → Channels → Back、45 Connections；每次三機）。
- 三機 online：177/177。
- distinct PID：每機 1。
- D2/D3 FGS：共同窗持續存在；Connected checkpoint 通過。
- crash、ANR、OOM、Koin、missing stack、liveness／Stage 2 timeout／forced reconnect、Room migration、background-start exception：全為 0。
- 使用者在 minute 58 後指示：若無明顯錯誤可提早結束。因此沒有執行原排程的 60–90、120–150、175–180 分鐘熄屏／喚醒階段。

在 soak 之前另有三分鐘熄屏 CPU 對照與約四分鐘最終 package-replacement 熄屏恢復觀察；它們不能合併成連續三小時證據。

## 7. 尚未證明與後續 gate

本輪不能宣稱：

- 三小時 soak、Doze、拔除 USB 後的電池壽命或永久背景存活。
- D3 候選能耗已修正；需要相同 radio workload 的 Profile/Release-like 10–30 分鐘方法級 Perfetto/CPU/GC A/B 才能定級。
- 同一手機同時連接二至四個 Meshtastic 節點的硬體隔離、獨立頻道 mutation、reconnect storm 或 scheduler 公平性。
- RF airtime、第二節點 remote receipt、Gateway Provider/caller 的 connected-radio command admission、訊息傳送或頻道寫入。
- Android 11–17 完整裝置矩陣、正式簽章、Play 上傳／接受、Windows 或 iOS 實機功能。

最準確的結論是：**四個已確認 P1 已有明確根因、最小修正及三機回歸證據；單節點基本 UI、更新自癒、cold Provider 安全與 Connections 效率均明確改善。D3 能耗仍是未定級候選，且長 soak 依使用者指示提前結束。**
