# Android NTsocial MeshLink 多節點版本三機單節點硬體測試與問題調查報告

- 測試日期：2026-08-24（Asia/Taipei）
- 受影響產品軌：Android `NTsocial MeshLink`
- 測試型態：三支 Android 16 實機、USB ADB 控制、兩支手機各連一個既有 Meshtastic BLE 節點
- 結論狀態：測試完成；單節點基本功能與兩小時連線存活通過，但因四個已確認 P1 與一個 P1 candidate，不能宣稱高效或 release-ready
- 程式碼修改：無。這次只建置、安裝、測試、蒐證與撰寫報告

> 隱私處理：本報告刻意省略完整手機序號、BLE MAC、節點名稱、頻道名稱、訊息內容與位置。手機只以 D1、D2、D3 及序號末四碼識別。原始 UI dump、截圖及 logcat 不會提交進 Git。

## 1. 執行摘要

目前版本可以在三支實機完成保留資料升級安裝。手動開啟 App 後，D2、D3 都能各自重新連回原本的單一 Meshtastic 節點、完成 Stage 1/Stage 2，同時顯示新的 `1 / 4` radio fleet UI；D1 沒有既有節點時正確顯示未選擇裝置。Messages、頻道歷史、未送出的 composer 輸入／清除、Nodes、Settings 與 Channels 唯讀檢查均可操作。活躍 radio database 為 Room schema 43，沒有觀察到 migration 錯誤。

但是，目前不能宣稱這個版本「保持高效」或已適合正式發布。測試已確認下列高優先問題：

| 優先級 | 問題 | 目前判定 |
|---|---|---|
| P1 / Release blocker | 外部 `/channels` deep link 破壞 Navigation 3 multi-backstack 狀態，造成可重現 App crash | 根因確定；D2、D3 同時發生，D2 單一 intent 再現 |
| P1 / High | `install -r` 後 `MY_PACKAGE_REPLACED` 被 receiver 程式碼直接忽略，既有 BLE/FGS 不會自行恢復 | 根因確定；D2、D3 等待約 90 秒仍無服務，手動開啟才恢復 |
| P1 / High | 冷行程的 Gateway `ContentProvider` 可在 Koin 啟動前被 parent 查詢 | 真機已命中一次 `KoinApplication has not been started`；初始化順序根因確定 |
| P1 / High performance | D2 的持續自動 BLE 掃描讓 Connections 以約 120 rendered frames/s 永久產生 frame，長期占用約 0.65–0.70 個 CPU core | 同 PID、超過兩分鐘觀察及 Messages ↔ Connections A/B 已確認 |
| P1 candidate / High energy risk | D3 熄屏後在已觀察 GATT 事件數相同時仍平均占用 8.91% one-core，CPU 與 HeapTaskDaemon 活動同向 | 225 秒、六窗確認；payload/handler path 未受控，需 Profile build 決定 production severity |
| P2 candidate / Debug-device UX risk | OPPO D3 在一般 top-level 頁面切換出現較高 frame latency | 同 PID 重測仍存在；D2 掃描動畫會偏移跨機統計，尚不能判定 source regression |

正面結果是：導覽 crash 後 Android sticky service 能重新建立，D2、D3 分別約 13.3 秒與 17.6 秒重新完成 Stage 2，之後至少觀察一分鐘沒有 liveness timeout 或重連風暴。這證明 recovery path 有效，但不能抵銷原本不應發生的 crash。

## 2. 被測來源與建置產物

### 2.1 Source snapshot

- Branch：`multi_nodes_`
- HEAD：`d3aa2eebf16cf5e5f7f44803b17e7a68594c2b59`
- HEAD subject：`docs: record multi-radio architecture audit`
- Source 狀態：dirty worktree，包含目前尚未提交的 multi-node 實作

因此 APK 是「本次工作目錄完整快照」的產物，不是只 checkout 上述 commit 就能得到的純淨 commit artifact。報告中的結論也只適用於這個工作目錄快照。

### 2.2 Build

使用 JDK 21、Android SDK 與 `en-US` JVM locale，強制重跑 Google Debug 組裝：

```text
.\gradlew.bat :app:assembleGoogleDebug --rerun-tasks --no-configuration-cache --console=plain
```

結果：

- `BUILD SUCCESSFUL`，5 分 28 秒
- 450 actionable tasks，全部實際執行
- `verifyGoogleDebugNoCloudRuntimeComponents` 隨組裝通過
- 本次沒有執行完整 root `test/allTests/lint/detekt/kmpSmokeCompile` gate，不能把這次硬體測試報告當成完整 source gate 證明

APK：

```text
app/build/outputs/apk/google/debug/app-google-arm64-v8a-debug.apk
```

| 項目 | 值 |
|---|---|
| Package | `com.ntsocial.meshlink.google.debug` |
| Version | `1.0.6 (7)` |
| minSdk / targetSdk / compileSdk | 26 / 37 / 37 |
| ABI | arm64-v8a |
| Size | 51,377,431 bytes |
| SHA-256 | `402FFFA26535344AD6B404753000E73CAE6DE41656715ABBDFF07E1E9B7BAB92` |
| `zipalign -c -P 16 4` | Pass |
| Signature | 與三機既有 Debug 安裝相同，因此可保留資料升級 |

這是 Debug APK，不是簽署的 Play Release artifact，也不適合用來聲稱 Release/R8 最終效能。

## 3. 實機矩陣

三機皆為 Android 16 / API 36、arm64，透過 USB 連接並持續供電。USB 只用於 ADB；第一版 Connections UI 仍是 Bluetooth-only，不能把 USB 手機連線當成 USB radio transport 證據。

| ID | Model / 序號遮罩 | SoC | 畫面 | 升級前 radio 狀態 | 本次角色 |
|---|---|---|---|---|---|
| D1 | SM-S9080 / `…MRTY` | SM8450 | 1080×2316，最高 120 Hz | 無已選節點 | 無 radio 對照組 |
| D2 | SM-S9280 / `…0SDH` | SM8650 | 1080×2340，120 Hz active | 已連一個 Meshtastic BLE 節點 | 單節點、掃描與高效能對照 |
| D3 | OPPO CPH2695 / `…GU55` | MT6835 | 720×1604，測試時 60 Hz active、最高 120 Hz | App 開啟後可連一個 Meshtastic BLE 節點 | 單節點、中階硬體對照 |

所有裝置 thermal status 都是 0，low-power mode 關閉。由於 USB 供電，熄屏時 `deviceidle` 仍為 `ACTIVE`，本次背景測試不是 Doze 驗證。

## 4. 安裝與資料保留

三機皆以同一 APK 執行 `adb install -r -t`，沒有 uninstall、`pm clear`、`-g` 或 downgrade：

| Device | 安裝結果 | ADB install elapsed | firstInstallTime | lastUpdateTime | 安裝後 base APK SHA-256 |
|---|---|---:|---|---|---|
| D1 | Success | 2,262 ms | 2026-08-21 13:06:55 | 2026-08-24 19:44:14 | `402FFFA…BAB92` |
| D2 | Success | 1,774 ms | 2026-08-21 13:06:52 | 2026-08-24 19:44:14 | `402FFFA…BAB92` |
| D3 | Success | 2,477 ms | 2026-08-21 13:06:57 | 2026-08-24 19:44:14 | `402FFFA…BAB92` |

三機的 `firstInstallTime` 均保留，安裝後 package/version/hash 一致。D2、D3 的既有設定、radio catalog、頻道與歷史 database 仍可讀；active per-radio database header 為 schema 43。沒有觀察到資料清空或 migration exception。

## 5. 基本單節點功能結果

### 5.1 啟動與 BLE session

升級後手動打開 Activity 的 `am start -W` 時間：

- D1：838 ms
- D2：854 ms
- D3：2,482 ms

這些行程先前已可能被 parent Provider 建立，所以不是可比較的純 cold-start benchmark。

手動開啟後：

- D1 正確顯示「未選擇裝置」，不建立不必要的 radio FGS。
- D2、D3 在約 30 秒內完成 BLE Ready、Stage 1、Stage 2 並顯示已連接。
- 兩支連線手機皆顯示 `Meshtastic 節點 1 / 4`、legacy primary、已連接與 Disconnect action。
- BLE negotiated max write length 為 244 bytes。
- 測試期間沒有新增第二個 endpoint；本報告證明的是 multi-node 版本的單節點相容性，不是兩／四 radio 同機證明。

### 5.2 Messages 與 composer

- D2、D3 的 Messages 均顯示選中 endpoint 標記與五個已設定頻道列。
- 開啟 channel 0 後，composer 可編輯且 Send action 存在。
- D2 以固定 sentinel 輸入後清除，沒有按 Send。
- D3 的中文 Gboard 會轉換 ADB `input text`，所以只能確認欄位接受輸入與清除，不能把轉換後字串當成 App 內容錯誤。
- 沒有送出訊息，因此沒有 RF airtime、另一節點收件或 remote receipt 證據。

### 5.3 Settings、Channels、Nodes

- D2、D3 Settings 可打開並顯示 endpoint 標記。
- 經 Settings 內正常導覽可打開 Channels；Add action 與 slot 0–4 都可見，Primary/Secondary 角色正確。沒有修改、套用或重啟 radio。
- Nodes 的 title、搜尋與摘要正常；D2 約 825 筆、D3 約 756 筆保留 node 資料可渲染。不同資料量會影響跨機效能比較。
- 直接外部 `/channels` deep link 會 crash；這是第 8.1 節的獨立 P1 問題，不代表 Settings 內部導覽也失敗。

## 6. 兩小時持續測試設計與結果

正式 soak 從 2026-08-24 20:14:46 +08:00 開始，22:14:53 結束。採樣本身涵蓋 20:14:49–22:14:53；每分鐘三機各一筆，實得 363/363 rows，每機 minute 0–120 各 121 個唯一分鐘，ADB state 全部為 `device`。每五分鐘另取 PSS/RSS、Java/native heap、thread count、current-PID crash/liveness 關鍵字與最新 process exit info。

同時從正式測試開始前持續保存三條 App UID full log 與三條 system critical log，直到最終檢查後才停止；六個 stderr 都是 0 byte。正式視窗的錯誤判定以這些連續串流、每五分鐘 current-PID 快照、PID/FGS、process exit-info 與最後 UI state 交叉驗證，不只依賴可能被 D2 framework frame log 快速覆寫的 ring buffer。

執行事件：

| 分鐘 | 動作 | 目的 |
|---:|---|---|
| 0–29 | Connections 前景 | 單節點穩定、D2 掃描高負荷 |
| 30–59 | HOME + screen off | 背景/熄屏服務存活；USB charging 下非 Doze |
| 60 | 喚醒、Connections | 驗證連線與 UI 回復 |
| 90–93 | Messages → Nodes → Settings → Connections | 低頻率真實頁面切換 |
| 105–114 | 第二段 HOME + screen off | 短背景重複驗證 |
| 115–120 | 喚醒、Connections | 最終連線、PID、服務與 UI 驗證 |

最終結果：

- D1/D2/D3 分別全程維持 PID 23317/30039/5433，各自只有一個唯一 PID；沒有 process restart。
- D2、D3 的 MeshService 與 connected-device FGS 都是 121/121 samples。兩次 HOME + screen off 都沒有中斷，最後仍顯示已連線與 `1 / 4`。
- D1 沒有 radio；MeshService 在 App 前景存在 81/121 samples，在兩段 HOME + screen off 停止。第 60、115 分鐘喚醒時各有一筆短暫 FGS，下一分鐘已 demote；最終 UI 仍正確顯示未選擇裝置。
- 正式視窗內，三機的 App UID log 都是 0 priority-E、0 priority-W；App/system 串流中沒有 FATAL EXCEPTION、ANR、OOM、fatal signal 或 MeshLink process death。
- `Stack for … not found`、`KoinApplication has not been started`、liveness/Stage-2 timeout/forced-reconnect 關鍵字均為 0。D2、D3 沒有 radio disconnect；D1 的兩行 `connectionState=Disconnected` 是無 radio 狀態，D3 的兩行 `BufferQueueConsumer disconnect` 是兩次 HOME 的 surface 釋放。
- 最新 process exit-info 全程未改變：D1 仍是 19:44:14 的 package update；D2、D3 仍是正式 soak 前刻意重現的 20:01:36／19:53:18 navigation crash。正式視窗沒有新增 exit row。
- 第 90–93 分鐘的 Messages → Nodes → Settings → Connections 全部通過，PID 不變；最後 D2/D3 仍已連線，D1 仍未選擇。
- USB 供電讓三機 device-idle 維持 ACTIVE；本結果證明 screen-off/background 存活，不是 Doze 存活證明。

## 7. 效能觀察

### 7.1 D2 Connections 的永久 120 Hz 重繪

D2 保留了舊版 `bleAutoScan=true` 偏好。UI 明確顯示「正在搜尋」及 indeterminate progress bar；D1、D3 沒有此狀態。

至少 129.6 秒、七個有效 16 秒窗的唯讀觀察：

- PID 全程不變。
- App CPU：65.6–68.2%（以一個 core 為 100%）。
- main thread：36.1–39.3%。
- RenderThread：21.9–24.1%。
- `gfxinfo` rendered-frame counter rate：118.8–121.3 frames/s，貼近 120 Hz active display mode。
- 專用 10 秒視窗：`gfxinfo` rendered-frame counter 增加 1,261，約 126.1 frames/s；D3 靜態 Connections 同時只有 1 frame。這不是 panel-presented fps；多 surface、統計邊界或視窗誤差可使 counter rate 略高於 120 Hz。最後一個同步 reset 無效的 gfx 樣本已丟棄，CPU tick 樣本不受影響。
- jank 約 0.08–0.09%、p50 6 ms：畫面不是卡頓，而是很順地永久產生不必要 frame。
- D2 兩分鐘 logcat 有約 29,188 筆 `View: setRequestedFrameRate frameRate=NaN`，約 243 筆/秒；頻率約為 rendered-frame rate 的兩倍，符合每 frame 多次 framework frame-rate request。它會快速擠掉 ring buffer 中真正有用的 App log，但其 CPU 占比沒有獨立量測。

正式兩小時連續 App UID log 再次確認規模：D2 有 1,132,234 行相同 framework 訊息，D1 189 行、D3 0 行。D2 的捕獲檔因此達 616,717,979 bytes，而 D1/D3 約 3.65/2.92 MB。以整個 120 分鐘計約 157 lines/s；若只用 Connections 可見的約 77 分鐘估算，約 245 lines/s，與短測一致。這些不是 App 主動寫出的 Kermit error，但會污染裝置 log buffer 與調查資料量。

同一 D2、同一 PID、每段先等待五秒後的 A/B：

| 畫面 | 掃描進度 | 30 秒 CPU | Frames | 約略 rendered frames/s |
|---|---|---:|---:|---:|
| Messages | 不可見，Connections dispose 後 stop scan | 8.23% | 365 | 12.2 |
| Connections | 可見，auto scan 重啟 | 69.08% | 3,667 | 122.2 |

回到 Connections 後增加 60.85 個 CPU 百分點與約 110 rendered frames/s；PID 不變，排除了冷啟動、不同 process 或硬體差異。Messages 本身仍有 8.23% CPU 與 12.2 frames/s，所以這是證明 Connections 額外成本的 A/B，不是絕對 idle baseline。

熄屏自然對照：

- D2 screen-off 的早期單一 30 秒窗 CPU 降至 1.50%，支持前景主要負荷是 UI frame production；後續六窗平均為 3.23%，不能把 1.50% 當成穩定背景基線。
- Bluetooth manager `LE Scanner Map` 的 `com.ntsocial.meshlink.google.debug` package entry 明確顯示 33 starts / 32 stops、PID 30039、Meshtastic service UUID filter，因此可歸屬為 MeshLink 尚有一個 active scan；它不是只看全域 counter 的推論。第 90 分鐘離開 Connections 後 stop、93 分鐘回來後 start，計數變為 34/33；第 105 分鐘再次 HOME + screen off 後仍維持 34/33，至第 110 分鐘 active time 又增加 248,471 ms，最終也是 34/33、6,580,410 ms cumulative active time。

程式碼因果鏈：

1. `feature/connections/.../ConnectionsScreen.kt:132-138`：`bleAutoScan=true` 時進畫面啟動，只有 composable dispose 才 stop。
2. `feature/connections/.../ScannerViewModel.kt:227-257`：使用 `Duration.INFINITE`。
3. `feature/connections/.../DeviceList.kt:96-104`：掃描狀態傳給 `showProgress`。
4. `feature/connections/.../DeviceSectionHeader.kt:70-71`：整段掃描顯示 indeterminate `LinearProgressIndicator`。
5. `ScannerViewModel.kt:239-248`：每次 advertisement 的 RSSI/name 改變都重建 map，是可能的次要 recomposition 放大因素。

### 7.2 一般頁面切換

先前包含 crash/restart 的 D3 `64.63% jank / p50 109 ms` 統計已作廢：它只涵蓋新 PID 的少量冷啟動、Room/Koin/Compose/BLE 重連 frame，不能代表 steady state。

固定 PID、五輪 top-level、每次約一秒的受控重跑：

| Device | Frames | Jank | p50 | p95 | p99 |
|---|---:|---:|---:|---:|---:|
| D1 | 775 | 9.42% | 未單獨保存 | 23 ms | 109 ms |
| D2 | 1,826 | 6.30% | 未單獨保存 | 27 ms | 89 ms |
| D3 | 507 | 20.91% | 12 ms | 150 ms | 300 ms |

各自從 Connections 往單一目標畫面五輪、1.5 秒間隔：

| Device / route | Frames | Jank | p95 | p99 |
|---|---:|---:|---:|---:|
| D2 Messages | 1,282 | 3.51% | 12 ms | 46 ms |
| D2 Nodes | 1,219 | 3.77% | 14 ms | 61 ms |
| D2 Settings | 1,297 | 2.78% | 10 ms | 48 ms |
| D3 Messages | 334 | 16.17% | 125 ms | 150 ms |
| D3 Nodes | 253 | 22.53% | 150 ms | 250 ms |
| D3 Settings | 257 | 18.29% | 150 ms | 300 ms |

D3 的中階 SoC、60 Hz active display、OEM scheduler、約 756 個 node、Debug logging 與未最佳化 build 都與 D2 不同；D2 在 Connections dwell 期間的永久掃描動畫又會增加 frame 分母、降低 jank 比例並把 percentile 往短 frame 偏移。因此 D2/D3 不能作直接控制組，也不能把差異解讀成 source regression。D3 同 PID 的 p95 125–150 ms 仍是實際可感知的 Debug-device UX 風險，正式比較必須關閉掃描並固定 Profile build、refresh rate 與 workload。

### 7.3 熄屏後的 D3 allocation/GC 背景負荷

在第 30 分鐘三機 HOME + screen off 後，另以 `/proc` CPU tick 與 thread tick 做 225 秒、六個視窗的 D2/D3 對照。兩個 PID 全程不變：

| 視窗 | D2 CPU | D3 CPU |
|---:|---:|---:|
| 1 | 3.18% | 12.03% |
| 2 | 3.63% | 11.69% |
| 3 | 2.66% | 3.92% |
| 4 | 2.44% | 7.53% |
| 5 | 3.40% | 11.94% |
| 6 | 4.09% | 6.33% |
| 平均 | **3.23%** | **8.91%** |

D3 的 main 只有約 0.58–0.79%，RenderThread 不在熱點；高窗中 `HeapTaskDaemon` 約 2.93–3.26%，其餘分散在 Binder 與 DefaultDispatcher。六個小樣本的描述性相關中，process CPU 與 HeapTaskDaemon CPU 的相關係數為 0.985（n=6）；這符合 heap/GC activity，但不能證明因果或大量短命物件。34.7 秒 RSS 只增 804 KiB，沒有短期 runaway leak 證據。

最近 120 秒兩機可從 log 觀察到的 GATT/packet 事件數相同：

| 指標 | D2 | D3 |
|---|---:|---:|
| `fromRadio` packets | 13 | 13 |
| `logRadio` packets | 0 | 0 |
| GATT characteristic change | 9 | 9 |
| Characteristic read log | 30 | 30 |
| Writes / heartbeats | 4 / 4 | 4 / 4 |
| Reconnect / disconnect / error | 0 | 0 |

App log rate也近似（D2 1.35/s、D3 1.51/s）。因此沒有 D3 具有更多已觀察 RF/GATT 事件、radio debug stream、reconnect storm、log flood、location registration、FROMRADIO drain busy-loop 或單一熱 thread 的證據。packet byte size、protobuf variant、handler path、Node/Room mutation 與 Gateway query/dispatch 並未受控；目前只能把原因範圍縮到正常 packet pipeline 的 allocation/GC 或裝置 runtime 放大，而不能宣稱唯一根因。沒有方法級 profiler 與 GC allocated/freed/time delta，不能把百分比精確分配給單一函式。

另發現 `BleRadioTransport.kt:376-384` 每次普通連線都要求 Android `Priority.High`，Stage 2/config drain 後沒有切回 Balanced。這不能解釋兩機在相同 callback 數下的 App CPU 差異，但可能造成額外 controller/system energy，且 OEM 行為不同。

### 7.4 記憶體、threads 與 thermal

以 `dumpsys meminfo` 每五分鐘取得 25 組樣本；數值為 KiB：

| Device | PSS start / min / max / end | RSS start / min / max / end | Java heap min / max / end | Threads |
|---|---|---|---|---|
| D1 | 265,303 / 217,233 / 277,081 / 224,439 | 390,896 / 302,404 / 400,372 / 302,404 | 22,160 / 32,572 / 22,744 | 41–41 |
| D2 | 295,925 / 242,616 / 317,786 / 313,312 | 440,528 / 389,520 / 467,676 / 463,400 | 30,440 / 73,452 / 73,452 | 41–41 |
| D3 | 260,424 / 226,671 / 263,247 / 243,434 | 421,984 / 277,600 / 424,924 / 314,416 | 20,852 / 52,088 / 34,812 | 47–47 |

沒有 thread growth，D1/D3 end 都低於 start。D2 並非單調上升：第 110 分鐘背景樣本曾降到 PSS 242,616 / Java 46,252，喚醒並在 Connections 掃描五分鐘後 end 回到 PSS 313,312 / Java 73,452（Java 為本次樣本高點）。因此兩小時內沒有 runaway/native leak 證據，但 D2 前景掃描路徑有明顯的 allocation/retained-heap churn；不能把最後高點忽略，也不能只憑它宣稱 leak。修正 scan 後應以對齊 Activity state 的 Profile heap/GC A/B 重測。

最終三機皆 USB 供電、battery 100%，battery temperature 為 D1 33.5°C、D2 33.8°C、D3 31.2°C，thermal status 都是 0。這代表本次供電環境沒有 thermal throttling 警訊，但不是拔線電池耗用測試。

## 8. 詳細問題與修改辦法

### 8.1 P1：nested deep link 破壞 `MultiBackstack` 不變量並 crash

#### 真機證據

- 2026-08-24 19:53:18，D2、D3 幾乎同時出現：

```text
java.lang.IllegalStateException: Stack for ChannelsGraph not found
at MultiBackstack.getActiveBackStack(MultiBackstack.kt:45)
at EndpointAwareNavigation(Main.kt:126)
```

- D2 在 20:01:35 經正常 `/settings` 後，只送一次 manifest 已宣告的 `meshtastic://meshtastic/channels` intent 即再次 deterministic crash；舊 PID 立即消失，新 PID 約一秒出現。
- `dumpsys activity exit-info` 將 D2 的兩次退出及 D3 的一次退出記為 `reason=4 (APP CRASH(EXCEPTION))`。

#### 根因

`DeepLinkRouter.kt:80` 將 `/channels` 解析成只有一個 nested graph key 的 `[ChannelsRoute.ChannelsGraph]`。`MultiBackstack.handleDeepLink()` 目前：

1. 把第一個 key 當成 top-level fallback。
2. 先將 `currentTabRoute` 寫成 `ChannelsGraph`。
3. 才發現 `backStacks` 只有五個 top-level tab，找不到 `ChannelsGraph` 並 return。
4. 下一次 Compose 讀 `activeBackStack` 時必然拋例外。

相同缺陷也涵蓋 `/firmware`、`/firmware/update`、`/wifi-provision`。Manifest 對外宣告 `/channels` 與 `/firmware`，所以不是只可能由測試工具觸發的私有 route。

這不是 BLE、Room 或 endpoint Koin scope 的根因；新的 `EndpointAwareNavigation` 只是第一個讀取已被污染狀態的位置。

#### 具體修正

本機 `upstream/main` commit `00ad90afdf352e7fddf1d28f3e61a95a763a48dc` 已有對應 navigation hunk：

- 如果 first key 確實屬於 `TopLevelDestination`，才切換 tab 並 replace 該 stack。
- 如果是 nested route，保持 `currentTabRoute` 不變，把 path `addAll()` 到目前 tab 的 stack。
- 必須先解析可用 stack，再改變目前 route；不要在 getter 裡加入無聲 fallback 掩蓋 invariant corruption。

只應人工移植 `MultiBackstack` 與相應測試，不應為此 cherry-pick 整個 firmware commit。

必要測試：

1. 真實 `/channels` router 輸出交給 `MultiBackstack` 的 integration test。
2. Settings stack 收到 `[ChannelsGraph]` 後仍以 Settings 為 current tab，且可安全讀取 active stack。
3. Firmware、WifiProvision nested route 同類測試。
4. current stack 缺失時 no-op，不 crash、不污染 route。
5. Nodes 等真正 top-level deep link 仍可正確切換並 replace。
6. Android instrumentation 直接對 manifest advertised URI 發 intent，連線與未連線狀態都要覆蓋。

### 8.2 P1：App 更新後既有 BLE service 不會自行恢復

#### 真機證據

- D2、D3 在升級前均有既有 BLE session/FGS。
- 19:44:14 package update 結束後，Android 如預期終止舊 process。
- 19:44:28 至至少 19:45:42，多次檢查都有由 parent Provider 建立的 process，但沒有 MeshService、沒有 FGS、沒有 BLE reconnect；從安裝完成起約 90 秒都沒有自癒。
- 19:46:01 手動開啟 Activity 後，Activity 的 service client 才啟動 MeshService，兩機隨後恢復。

#### 根因

`app/src/main/AndroidManifest.xml:289-306` 對 `BootCompleteReceiver` 宣告：

- `BOOT_COMPLETED`
- 多個 quick-boot / SIM test action
- `MY_PACKAGE_REPLACED`

但 `core/service/.../BootCompleteReceiver.kt:42-45` 只接受 `Intent.ACTION_BOOT_COMPLETED`，其他 manifest 合法 action 一律直接 return。因此 `MY_PACKAGE_REPLACED` 必然失效。

另有第二個 latent race：receiver 在 `BootCompleteReceiver.kt:46` 同步讀 `meshPrefs.deviceAddress.value`；`MeshPrefsImpl.kt:57-60` 的 eager StateFlow 在 DataStore 完成前固定以 `"n"` 起始。即使補上 action allow-list，冷 process 仍可能把尚未 hydrated 誤認為沒有節點。

#### 具體修正

1. 先對已證實且有平台語意的 `BOOT_COMPLETED`、`MY_PACKAGE_REPLACED` 建立精確 allow-list；未知 action 繼續 fail closed。OEM quick-boot 必須逐項驗證來源可信度、Release 必要性與背景 FGS 合法性，`SIM_BOOT` 應移到 Debug/test manifest，不能因 manifest 已宣告就全部放行。
2. 不要用尚未 hydrated 的 `StateFlow.value` 判斷持久狀態。
3. 可靠性優先方案：合法 boot/package-replaced action 直接啟動 MeshService，讓現有 `awaitHydratedDeviceAddress()`/15 秒 grace 決定有無節點；無節點時自行停止。
4. 若要避免無節點短暫 FGS，使用 `goAsync()` + bounded IO 直接讀 authoritative DataStore snapshot；timeout 時 package replacement 應 fail-safe 啟動，而不是再次漏接。
5. Log 只記 action、單調時間、hydration elapsed、結果與匿名 endpoint ID；不能輸出完整 BLE MAC。

必要真機回歸：已連線時反覆 `install -r`，不要開 Activity；在明確 SLA 內確認新 PID、FGS notification、BLE reconnect、Stage 2，並穩定至少兩分鐘。另對每個 allow-list action、未知 action、`ForegroundServiceStartNotAllowedException` 與 DataStore timeout 做 receiver 測試。

### 8.3 P1：冷 Gateway Provider 與 Koin 啟動順序競態

#### 真機證據

D3 在 19:53:18 navigation crash 後建立新行程；19:53:21.031，已授權 parent 立即查詢 Gateway Provider 時得到：

```text
KoinApplication has not been started
```

此事件發生在 `MeshUtilApplication.onCreate()` / MeshService 建立前。之後 Application 完成初始化，radio recovery 正常。沒有資料洩漏，但 parent 的合法查詢失敗。

#### 根因

- `MeshUtilApplication.kt:73-81` 到 `Application.onCreate()` 才 `startKoin`，再動態 `loadKoinModules(radioEndpointKoinModule)`。
- Android 先建立並 publish `ContentProvider`，之後才呼叫 `Application.onCreate()`。
- `NtsocialGatewayProvider.onCreate()` 雖刻意不解析 DI，但 Provider 一 publish，Binder thread 就能進 `query()`。
- `query():89` 的 `enforceAccess()` 會解析 `callerVerifier by inject()`；此時 Global Koin 尚不存在。

Navigation crash 只負責創造冷行程時窗；它不是 Koin 錯誤本身的根因。

#### 具體修正

AndroidX Startup／`KoinStartup` 是候選機制，但單獨不足以證明 Gateway Provider publish 前已完成 DI：目前 manifest 的 Gateway Provider 與 `InitializationProvider` 沒有一個已驗證的先後保證。所需的不變量是：**Gateway Provider 的 `onCreate()` 返回並可被 Binder 呼叫前，完整 DI definitions 與必要 application context 已以 app-owned、idempotent bootstrap 註冊。**

1. 建立單一 thread-safe、idempotent 的 bootstrap，原子註冊 Android context、WorkManager factory、Gateway verifier/repository、database definitions 與 `radioEndpointKoinModule`；移除 `Application.onCreate()` 的第二次 start/load。
2. 可由已驗證高順位的 bootstrap Provider 執行，或讓 Gateway Provider 自身先呼叫相同 bootstrap，再返回 `onCreate()`；若使用 `KoinStartup`，必須以真機證明實際 Provider 順序。
3. `ContextServices.app` 必須在同一 bootstrap 中可用，否則只會把 Koin exception 轉成 database/context 未初始化。
4. definitions 早期註冊，Room、BLE 與其他重物仍保持 lazy，避免把 cold Provider query 變成同步 I/O。
5. `android:initOrder` 只能作為排序手段，不能取代 idempotent bootstrap 與壓力測試。

不要在 Provider 捕捉全部 `IllegalStateException` 後回傳空資料；這會掩蓋真錯誤，也不能犧牲 caller certificate/UID 驗證。

必要測試：冷 process kill/restart 100 次，由授權 parent 立即並行查 `/v2/status → /v2/channels → /v2/status`；不得有 Koin/RemoteException，也要驗證未授權 shell 仍被拒絕、完整 Koin graph 無 double-start。

### 8.4 P1：無限 auto scan 的前景 CPU 與背景生命週期問題

#### 問題拆分

1. **前景畫面**：indeterminate progress 以 display refresh rate 永久重繪，D2 約 69% CPU。
2. **背景**：`DisposableEffect.onDispose` 不是 Activity `ON_STOP`；HOME + screen off 後 scanner 仍 active，雖然 Compose 不再產 frame。
3. **資料更新**：每次 RSSI 波動可能發布新 map/list，增加 recomposition。
4. **記錄污染**：Samsung framework frame-rate request log 約 243 lines/s，會快速覆寫真正錯誤；其 App CPU 成本未單獨量測。

#### 具體修正順序

1. 把 auto scan 改為 10–15 秒有界 sweep；成功連線或 fleet 已滿時停止，並保留明確的手動重新搜尋。
2. 若產品必須持續探索，採低 duty-cycle，且不要用永久 indeterminate animation。
3. 用 lifecycle-aware effect 在 `ON_STOP` 停掃描；回 `ON_START/RESUME` 時只有偏好仍開啟才重新做一次有界 sweep。
4. 進度顯示改成靜態「正在搜尋」或短暫低頻動畫。第一個低風險修正就是讓靜止頁面不再維持約 120 rendered frames/s。
5. 以 address 在 ViewModel 合併 advertisement；RSSI 採 1–2 秒 sample/debounce 或最小變化門檻，再發布 UI snapshot。
6. `AnimatedConnectionsNavIcon` 的 mesh activity pulse 是 Messages 仍有週期 frame 的次要候選；應先 trace，若確認才限頻或在持續流量時改為靜態 activity 狀態。

修正後驗收：同一 D2、同 PID、Connections → Messages → Connections A/B/A，每段丟棄五秒轉場後量 60 秒；靜止 Connections 不得貼 120 rendered frames/s，main/RenderThread 應接近 idle；HOME/screen off 後以 App session log 或 package/UID-specific scanner record 證明 scan 已停止，不能只看全域 starts/stops 相等。

### 8.5 P1 candidate：D3 例行 packet pipeline 的背景 allocation/GC 成本

#### 待 A/B 驗證的候選熱路徑

1. `KableMeshtasticRadioProfile.kt:78-110`：FROMNUM callback 觸發 drain，逐筆 GATT read 建立 `ByteArray`。
2. `BleRadioTransport.kt:343-363` 及 dispatch path：每筆 packet 產生 verbose log lambda/counter/Flow dispatch，再 callback 到 data layer。
3. `MeshMessageProcessorImpl.kt:113` 起：protobuf decode、variant dispatch；mesh packet path 會建立 UUID、文字 representation、`FromRadio`、`MeshLog` 與 database work。
4. `NodeManagerImpl.kt:203` 附近：node 更新可能建立 immutable-map 新版本並另啟 Room upsert。
5. Debug build 的 BLE event logging 增加每個例行事件的 allocation，雖然沒有形成 log flood。

#### 具體調查與候選修改

1. 將 per-packet verbose log 改成每 30–60 秒的 aggregate；Profile/Release benchmark 停用 BLE event logging。
2. 先用 trace/A-B 判斷完整 `packet.toString()` 是否為顯著成本；若是，再於不改變歷史、診斷與 schema 語意的前提下延遲建立或改成精簡 representation。
3. 相同 node 的 lastHeard/RSSI/SNR/model update 以時間窗 coalesce；transform 後無變化則不建立新 map、不 upsert。
4. MeshLog/Node write 使用有界佇列與 batch transaction，避免每 packet 建立多個 coroutine/job。
5. Stage 2 完成後要求 Balanced connection priority；只在 config、DFU 或有界 bulk transfer 暫時 High。
6. 增加 `MeshPerf` 每 60 秒聚合：drain trigger、empty/nonempty read、decode count/elapsed、MeshLog/Node insert、GC count/allocated/freed/time delta、目前 priority。不可逐 packet 寫診斷，否則會改變量測本身。

應以相同 radio workload 的 Profile/Release-like build，在 D2、D3 各測 10–30 分鐘 Perfetto/CPU/GC。若 D3 仍長期接近 9% one-core，列為正式 P1 energy blocker；若大部分是 Debug-only allocation，再調整 production severity。

### 8.6 P2 candidate：OPPO 中階裝置導覽延遲

目前沒有足夠 method-level trace 指向單一函式，也沒有排除 D2 掃描動畫造成的比較偏差；下列只是需要 Profile trace 驗證的 source 候選：

- `Main.kt:115-146`：只要 endpoint list 非空，即使只有一個 endpoint，也在 Connections/MeshCore 與其他 tab 間插入／移除 `RadioEndpointTabs`，改變 NavDisplay 高度及 layout constraints。
- `MeshtasticNavDisplay.kt`：top-level 與 nested 都使用 350 ms fade，快速輸入會重疊 outgoing/incoming composition、layout、draw。
- `Main.kt:107-166`：host 同時 collect fleet snapshots、selected endpoint、scope map；以 composable lambda identity 作 `movableContentOf` remember key，需確認沒有 host/scope churn。
- Nodes 畫面分別 collect 多個 flow、對完整 list 重算 ignored count、row 結構及 sensor lambda 偏重；D3 保留約 756 個 node。

建議：

1. 單 endpoint 時不要畫切換 tab row，或保留固定、低成本 label；多 endpoint 時保持 host 幾何高度穩定。
2. top-level tab 使用較短獨立 transition，並 conflate 快速輸入到最後目標。
3. 讓 host、endpoint chrome、Koin scope 使用穩定 key；加入 ViewModel create/clear、collector count 的測試診斷。
4. Nodes ViewModel 合併為 immutable UI state，`distinctUntilChanged`；上游算 ignored/sensor presentation；LazyColumn 加 `contentType` 並確認 model stability。
5. 新增 Macrobenchmark/Profile build：single-node warm navigation、Nodes first render/scroll、process restart→Stage 2、1/2/4 endpoint scope switch；至少五至十輪，報 p50/p90/p95/p99 與 PID invariant。

### 8.7 P2/P3：診斷、隱私與自動化可觀測性不足

這次無需修改 logcat 才能找到上述三個主要根因，但調查成本被下列問題放大：

- Kermit 多數 App log 沒有穩定 subsystem tag，必須以 UID/PID 擷取。
- `RadioFleetManager` 將 exception 轉成 `Failed(error.message)`，沒有保留 stack、stage、endpoint generation 與 elapsed time。
- 90 秒 setup timeout、Active BLE ownership、DB open/migrate、endpoint scope create/close 缺乏一筆可關聯的結構化摘要。
- 部分 BLE/service log 仍可能輸出完整 address；正式 log 應改為 process/session-local ordinal 或 keyed ephemeral hash，不能記持久 address 片段、名稱、PSK、payload、位置。
- phone bottom navigation 隱藏 label；`AnimatedConnectionsNavIcon` 沒傳 `contentDescription`，Connections tab 對 accessibility/UI automation 沒有與其他 icon 等價的描述。
- fleet card、endpoint tab、top-level nav 缺少穩定 test semantics/resource IDs；只顯示 address 末四碼也可能在四台設備中碰撞。
- `Failed/Degraded` UI 只顯示一般「Error」，使用者與測試無法知道是等待資源、DB、BLE、Stage 2 還是 timeout。

建議每 30 秒只輸出一筆隱私安全聚合 telemetry：匿名 endpoint、state/generation、scan start/stop reason、advertisement count、UI snapshot count、packet RX/TX count、DB/Stage elapsed、frame/slow-frame、main/Render CPU delta。不可逐 frame、逐 advertisement、逐 packet payload 寫 log。

UI 應補：Connections icon description、`testTagsAsResourceId`（限 Debug/test 亦可）、top-level/tab/fleet card 的穩定 selector，以及 Failed/Degraded 的安全錯誤分類。

## 9. 建議修正順序與 release gate

### 第一批：正確性與自癒

1. 移植 nested deep-link `MultiBackstack` 修正及 router↔backstack integration tests。
2. 修正已證實的 boot/package-replaced receiver allow-list 與 DataStore hydration race；OEM action 逐項審核。
3. 建立 Provider publish 前可證明完成的 idempotent 完整 DI bootstrap，完成 cold Provider 壓力／安全測試。

### 第二批：高效與中階裝置

4. 有界 BLE scan、lifecycle stop、靜態/低頻進度與 RSSI snapshot 節流。
5. 合併 packet/Node/Room 工作、移除無條件 per-packet allocation，Stage 2 後 BLE priority 回 Balanced。
6. 單 endpoint chrome/layout、top-level transition、Nodes UI state 優化。
7. 建立 release-like Macrobenchmark/Baseline Profile/Perfetto gate，不再用混合 crash/restart 的單次 `gfxinfo` 宣稱效率。

### 必須補的真機 gate

- 三機 `install -r` 不開 Activity，自動回 Stage 2。
- 所有 manifest deep links 在 connected/unconnected 狀態零 crash。
- 授權 parent 冷 Provider 100 輪；未知 signer/shell 仍 fail closed。
- Android 11–17/API 30–37 的 background/Doze/permission/FGS/location matrix；本次 USB charging screen-off 不涵蓋 Doze。
- 同一台中階手機對修正前後做 Profile build A/B。
- 兩／四 radio 同一手機的獨立 DB、頻道 mutation、reconnect storm、RF delivery 與 remote receipt；本報告沒有驗證。

## 10. 測試邊界與不能宣稱的事項

本次沒有：

- 按 Send 或證明 RF airtime、遠端接收、QueueStatus delivery。
- 修改頻道、LoRa config、GPS preference、radio firmware 或 node policy。
- 同一手機同時連兩／四 radio。
- camera QR、Gateway caller admission 到 radio queue、parent canonical-history end-to-end。
- Doze、拔除 USB、長期電池耗用、Android 11–15 或 API 37 device。
- Google/F-Droid Release、R8、簽署 AAB、Play 上架或正式效能。
- Windows/iOS 行為驗證；本次問題與證據都是 Android track。

另外，已授權的 NTsocial Android parent 在三機上維持安裝並會查詢 Gateway Provider，兩個 Meshtastic 節點也處在真實 RF 環境；本次不是關閉 parent、固定 packet replay 的實驗室純 idle baseline。D2 的同 PID 頁面 A/B 與 D2/D3 已觀察 GATT 事件數對照降低了部分干擾，但 payload/handler path 未固定，正式效能 gate 仍應使用可重現 workload。

因此最準確的結論是：**目前 multi-node Android source 的單節點核心 UI、保留資料與 BLE Stage 2 基本可用，但存在三個 P1 正確性／啟動問題及一個 P1 效能問題；修正與指定回歸 gate 完成前，不能宣稱高效或 release-ready。**

## 11. 最終持續測試結果

| 驗收項目 | 結果 | 可宣稱範圍 |
|---|---|---|
| Google Debug build、cloud guard、16 KiB zipalign | Pass | 本次 dirty-worktree Debug artifact |
| 三機保留資料升級安裝 | Pass | `install -r -t`，first-install time 保留 |
| 最終 installed APK 完整性 | Pass | 三機皆為 `1.0.6 (7)`，base APK SHA-256 都是 `402FFFA26535344AD6B404753000E73CAE6DE41656715ABBDFF07E1E9B7BAB92` |
| D2/D3 各一個既有 Meshtastic BLE 節點 | Pass | Stage 2、Messages/Nodes/Settings/Channels 唯讀基本功能 |
| 120 分鐘 process / radio service 存活 | Pass | 363/363 rows，同 PID，D2/D3 FGS 121/121 |
| 正常 top-level 導覽 | Pass | Messages → Nodes → Settings → Connections；nested `/channels` 另有已確認 crash |
| 正式視窗 crash / ANR / OOM / liveness | Pass | 持續 log、exit-info、PID/FGS 與 UI 交叉驗證為 0 新事件 |
| 高效運作 | **Fail** | D2 Connections 約 0.65–0.70 core、120 Hz rendered-frame churn、背景 scan 不停止；D3 背景能耗為 P1 candidate |
| 同機 2/4 radio、RF/remote receipt、Doze | 未測 | 不得由本報告延伸宣稱 |

測試結束後，三機仍在線，D2/D3 顯示已連線及 `1 / 4`，D1 顯示未選擇；三機 thermal status 為 0，且安裝 APK hash 與建置產物完全一致。

原始蒐證包含完整序號、BLE/節點 metadata、UI XML、截圖、363-row CSV 與約 629 MB 連續 log；暫存目錄共 83 個檔案、685,054,491 bytes。完成統計與本報告後，已驗證並刪除精確暫存目錄 `ntsocial-multinode-test-20260824`；這些未提交檔案不可復原。本報告只保留匿名化聚合數據。

最終判定：**單一 Meshtastic 節點下的核心功能與兩小時連線存活正常，但目前版本沒有保持高效。nested deep-link、package-update 自癒、cold Provider bootstrap、無限 scan/120 Hz churn 四個 P1 必須先修正；D3 背景 CPU 與 OPPO 導覽延遲需以 Profile build 定級。產品 source code 本次未修改。**
