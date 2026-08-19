# Meshtastic nRF52 XEdDSA 封包尺寸缺陷調查、修補與驗證報告

- 日期：2026-08-19
- 影響產品：NTsocial ↔ NTsocial MeshLink ↔ Meshtastic nRF52 LoRa 節點
- 硬體 target：`nrf52_promicro_diy_xtal`（nRF52840 + RFM95W/SX127x）
- 嚴重度：P0（合法封包在本機韌體端被確定性丟棄）
- MeshLink 專案基準：`dee786337e1583a8c8601afbb5153101626a345f`
- 韌體修補分支：`codex/xeddsa-size-gate`
- 韌體修補 commit：`f3fd3d4bf7550c83244a412fc034ca2d891c3a03`

## 1. 結論摘要

調查確認：目前使用的 Meshtastic 2.8.0 nRF52 韌體含有 XEdDSA 廣播封包尺寸缺陷。

舊韌體先用 `payload bytes + 64 < 233` 猜測簽章能否放入封包；通過後加入 64-byte XEdDSA 簽章，最後才用完整 protobuf 編碼大小加 16-byte Meshtastic header 檢查 255-byte RF 上限。這兩個判斷不是同一個尺寸模型，因此會產生「先簽章、再因 `TOO_LARGE(7)` 丟棄」的死區。

對 NTsocial 使用的 `PRIVATE_APP=256` 及目前一般 overlay `Data` 形狀，死區精確是 raw NTsocial envelope 166–168 bytes。先前 Round 2 報告記錄 166-byte 第三片在兩輪均固定 `res=7`，且持續觀察超過 217 秒；此 retained evidence 的索引是 `C:\Users\cth\Documents\GitHub\NTsocial_release\NTSOCIAL_LORA_RELIABILITY_ROUND2_REPORT_2026-08-19.md` lines 33–39。它與尺寸模型共同解釋較大 169/180-byte 封包反而成功的非單調現象；目前 retained Android log 未保存該次原始 `QueueStatus res=7` 行，因此不能把 Round 2 摘要提升為獨立 raw-log 證據。

Meshtastic 官方已在 [PR #10858](https://github.com/meshtastic/firmware/pull/10858)／[commit `0e84c1a82727218e340d8195eaea82473f50a3f8`](https://github.com/meshtastic/firmware/commit/0e84c1a82727218e340d8195eaea82473f50a3f8) 修正同一問題。官方提交說明明確指出舊 heuristic 造成廣播封包「signed then failed `TOO_LARGE`」，並改為以實際 protobuf encoded size 決定是否簽章。

本地韌體 fork 已完成最小、sender／receiver 尺寸模型對稱的 backport，production nRF52 target 亦可成功編譯出可追溯的新 UF2。行為安全與端到端修復仍待單元測試及 RF 實機驗收；本次未刷寫任何硬體，實機安裝與驗收須依本報告第 11 節執行。

## 2. 調查範圍與專案邊界

| 專案 | 角色 | 本次結果 |
| --- | --- | --- |
| `ntsocial-mesh-gateway-android` | NTsocial MeshLink Android/KMP gateway | 可調查 Android 尺寸檢查、QueueStatus 與韌體更新流程；沒有 Meshtastic firmware source，不能在此直接編譯 Router 韌體 |
| `faketec-RA-01SH-P` | 實際 Meshtastic firmware fork | 含完整 C++、PlatformIO、客製 nRF52 target；本次修補與 UF2 建置在此完成 |
| `NTsocial-with-Meshtastic-` | UF2／硬體資料發布目錄 | 發現既有 UF2 檔名與 binary 內嵌版本不一致 |
| `NTsocial_release` | NTsocial Android 與現場診斷證據 | 保存節點 metadata、`QueueStatus res=7` 與 LoRa 實測紀錄 |

MeshLink repo 的唯一 submodule 是 `core/proto/src/main/proto -> meshtastic/protobufs`；它是 wire schema，不是 firmware source。MeshLink 的 firmware feature 可下載與安裝 UF2／DFU 映像，但不會編譯 `Router.cpp`。

## 3. 目前 UF2 有檔名錯標問題

使用者指出的檔案為：

`C:\Users\cth\Documents\GitHub\NTsocial-with-Meshtastic-\firmware-nrf52_promicro_diy_xtal-2.8.0.7c6b85d.uf2`

實際檢查結果：

| 項目 | 發布目錄中的「7c6b85d」檔 | 真正由 7c6b85d 建出的舊檔 |
| --- | --- | --- |
| 檔名 suffix | `7c6b85d` | `7c6b85d` |
| binary 內嵌版本 | **`2.8.0.16831c5`** | `2.8.0.7c6b85d` |
| 長度 | 1,429,504 bytes | 1,427,968 bytes |
| SHA-256 | `4A7307D6C22F021EA147DB6F8742A1D3C7D006DF328CE86D4A97662FC44A30D5` | `3004FF90251B351A8F67840A777263E0F90C1E5CC23E77202B41F8DDCB62A89D` |

現場 A 節點也回報：

- `pio_env=nrf52_promicro_diy_xtal`
- `Local Metadata received: 2.8.0.16831c5`
- firmware check 再次讀到 `2.8.0.16831c5`

證據位於：

`C:\Users\cth\Documents\GitHub\NTsocial_release\diagnostics\lora_field_20260819\R5CX42P0SDH_offline_outbox.logcat.txt` lines 4611、4627、8902。

因此，若節點是由目前發布目錄的同名 UF2 刷入，實際版本應判定為 `16831c5`，不能依檔名判定為 `7c6b85d`。這是 release-blocking 的發布追溯缺陷；在檔名、內嵌版本、source commit 與 SHA-256 一致前，不應把該檔升格為可發布 artifact。

好消息是：`7c6b85d` 與 `16831c5` 的相關 `Router.cpp` 邏輯相同，兩者都沒有官方修正，所以缺陷判定不受錯標影響。

## 4. 版本與官方修正關係

本地 firmware fork 的版本關係如下：

- `7c6b85d775bad57e0915841bb08d205ab2e3d087` 是 `16831c57d02af8acfee9c3048a5af4b6b1862992` 的祖先。
- 官方修正 `0e84c1a82727218e340d8195eaea82473f50a3f8` 不是上述任一 commit 的祖先。
- 兩條分支的共同 merge-base 是 `b44ed4552f0072b2318e4e650289ff12d6b9a101`。
- `7c6b85d` 與 `16831c5` 均無 `signedDataFits()`，仍使用舊 payload heuristic。

官方比較：

- [`7c6b85d...0e84c1a`](https://github.com/meshtastic/firmware/compare/7c6b85d775bad57e0915841bb08d205ab2e3d087...0e84c1a82727218e340d8195eaea82473f50a3f8)
- [`0e84c1a...16831c5`](https://github.com/meshtastic/firmware/compare/0e84c1a82727218e340d8195eaea82473f50a3f8...16831c57d02af8acfee9c3048a5af4b6b1862992)
- [官方修正後的 `Router.cpp`](https://github.com/meshtastic/firmware/blob/0e84c1a82727218e340d8195eaea82473f50a3f8/src/mesh/Router.cpp#L661-L703)
- [官方 regression tests](https://github.com/meshtastic/firmware/blob/0e84c1a82727218e340d8195eaea82473f50a3f8/test/test_packet_signing/test_main.cpp)

## 5. nRF52 target 確實會走 XEdDSA 路徑

`nrf52_promicro_diy_xtal` 繼承 `nrf52840_base`。其 build flags 未定義 `MESHTASTIC_EXCLUDE_PKI` 或 `MESHTASTIC_EXCLUDE_XEDDSA`，客製 `variant.h` 也沒有啟用 minimize build。

因此，只要封包符合以下條件，就會進入有缺陷的舊簽章分支：

1. 本機產生的 decoded packet；
2. broadcast；
3. 非 PKI；
4. XEdDSA／PKI 未被 build flag 排除；
5. 舊 payload heuristic 判定「可簽」。

官方 target 來源：

- [`nrf52_promicro_diy_xtal/platformio.ini`](https://raw.githubusercontent.com/meshtastic/firmware/7c6b85d775bad57e0915841bb08d205ab2e3d087/variants/nrf52840/diy/nrf52_promicro_diy_xtal/platformio.ini)
- [`nrf52_promicro_diy_xtal/variant.h`](https://raw.githubusercontent.com/meshtastic/firmware/7c6b85d775bad57e0915841bb08d205ab2e3d087/variants/nrf52840/diy/nrf52_promicro_diy_xtal/variant.h)

## 6. 根因與精確尺寸計算

舊 sender 邏輯：

```cpp
if (payload.size + XEDDSA_SIGNATURE_SIZE < DATA_PAYLOAD_LEN) {
    sign();
}

numbytes = protobufEncode(Data);
if (numbytes + MESHTASTIC_HEADER_LENGTH > MAX_LORA_PAYLOAD_LEN) {
    return TOO_LARGE;
}
```

關鍵常數：

- `MAX_LORA_PAYLOAD_LEN = 255`
- `MESHTASTIC_HEADER_LENGTH = 16`
- `DATA_PAYLOAD_LEN = 233`
- `XEDDSA_SIGNATURE_SIZE = 64`
- signature protobuf field overhead = 1-byte tag + 1-byte length + 64 bytes = 66 bytes
- `PRIVATE_APP = 256`

對 raw NTsocial envelope `n >= 128` 且 firmware 已加入一般 bitfield 的目前形狀：

| 元件 | 編碼大小 |
| --- | ---: |
| portnum field，值 256 | 3 bytes |
| payload field | `n + 3` bytes |
| bitfield | 2 bytes |
| XEdDSA signature field | 66 bytes |
| Meshtastic RF header | 16 bytes |
| signed RF frame 總長 | **`n + 90` bytes** |
| unsigned RF frame 總長 | **`n + 24` bytes** |

舊 heuristic 在 `n + 64 < 233` 時簽章，也就是 `n <= 168`。

| raw NTsocial envelope | 舊邏輯 | signed RF 長度 | 結果 |
| ---: | --- | ---: | --- |
| 165 | 簽章 | 255 | 成功，剛好上限 |
| 166 | 簽章 | 256 | **`TOO_LARGE(7)`** |
| 167 | 簽章 | 257 | **`TOO_LARGE(7)`** |
| 168 | 簽章 | 258 | **`TOO_LARGE(7)`** |
| 169 | 不簽章 | unsigned 193 | 成功 |
| 180 | 不簽章 | unsigned 204 | 成功 |

這個結果完全吻合現場「較小尾片固定失敗、較大前片成功」的觀察，也排除 pacing、每三包規則、duty cycle 與 airtime rate-limit：

- `TOO_LARGE = 7`
- duty cycle error = 9
- airtime rate-limit = 38

官方修正說明中的一般 dead band 是 167–168 bytes；NTsocial 使用 `PRIVATE_APP=256`，port enum 多一個 varint byte，因此本路徑向下移成 166–168 bytes。

注意：166–168 不是所有 `Data` 形狀的永遠常數。若有非零 `reply_id`、`emoji` 或未來新增欄位，邊界會再移動；所以正確修法必須用實際 protobuf encoder，不能只在 App 或 firmware 硬編一段禁用尺寸。

## 7. 為何 MeshLink 的 Android 尺寸檢查抓不到

MeshLink 的 [`CommandSenderImpl.kt`](core/data/src/commonMain/kotlin/com/ntsocial/meshlink/core/data/manager/CommandSenderImpl.kt) 會在送入 radio 前，以 `Data.ADAPTER.isWithinSizeLimit(..., DATA_PAYLOAD_LEN)` 檢查 Android 當下看得到的 protobuf。

但 Android 建立的 `Data` 沒有 XEdDSA signature；簽章是 radio firmware 在 `ToRadio` 之後才決定並加入。因此 Android preflight 能證明「簽章前 Data 合法」，無法預知舊 firmware 的錯誤 sign-then-overflow 行為。

此外：

- Gateway `COMMAND_ACCEPTED` 只表示 Room、WorkManager 與 idempotency ledger 已完成本機 durable admission。
- firmware 的晚到 `QueueStatus res=7` 由 [`PacketHandlerImpl.kt`](core/data/src/commonMain/kotlin/com/ntsocial/meshlink/core/data/manager/PacketHandlerImpl.kt) 收到，但目前壓成 Boolean queue failure，沒有回傳成 parent 可觀察的 typed gateway event。
- 因此 `COMMAND_ACCEPTED` 不能當 RF enqueue、上空口或遠端收到的證據。

## 8. 已實作的最小 firmware backport

修補位置：

`C:\Users\cth\Documents\GitHub\faketec-RA-01SH-P`

分支／commit：

```text
codex/xeddsa-size-gate
f3fd3d4bf7550c83244a412fc034ca2d891c3a03
```

修改三個檔案：

1. `src/mesh/CryptoEngine.h`
   - 定義實際 signature protobuf field overhead：66 bytes。
2. `src/mesh/Router.cpp`
   - sender 在簽章前清除 client 預設 signature；
   - 新增 `signedDataFits()`，暫設 64-byte signature 後呼叫 `pb_get_encoded_size()`；
   - 只有完整 encoded `Data + 16-byte header <= 255` 才簽章；
   - receiver 對真正 unsigned 封包，用 `raw encoded size + 66 + 16` 套用同一條件；
   - receiver 明確排除 PKI broadcast policy；
   - 清除不可信任的 inbound `xeddsa_signed` flag；
   - 拒絕長度為 1–63 bytes 的畸形 signature，避免利用 padding 繞過 downgrade 保護。
3. `test/test_packet_signing/test_main.cpp`
   - 新增 partial-signature 拒絕測試；
   - 新增 NTsocial `PRIVATE_APP=256` 的 165、166、167、168、169、180-byte 邊界矩陣；
   - 明確 pin 165-byte signed frame = 255、166-byte signed frame = 256；
   - 同一 round-trip 同時驗證 sender 不再 `TOO_LARGE` 與 receiver 不誤判合法 unsigned 封包。

這個 backport 沒有關閉 XEdDSA、沒有改 wire schema、沒有降低一般 LoRa payload 上限，也沒有加入 FEC、複雜重送或新的 protocol。它只把錯誤的估算換成官方已合併的精確尺寸判定，符合本輪 P0 最小修復原則。

本輪未整包 cherry-pick 官方 19-file commit。PR #10858 另外包含 NodeInfo unicast、plaintext MQTT policy、randomized signing library pin 等獨立 security／interop hardening；這些可另立 P1 工作，不是修復本次 RF `TOO_LARGE` 的必要條件。

## 9. 新的可追溯 firmware artifact

本次實際建置命令（本機 `python` 解析為 Python 3.14.0；repo 建議的 Python 3.12 launcher 在此機不存在）：

```powershell
python -m platformio run -e nrf52_promicro_diy_xtal
```

建置結果：`SUCCESS`，耗時 124.42 秒。以下 RAM／Flash 與 guard 數值是本次終端即時觀察；本次沒有另外保存原始 build log，發布前應以同一 source commit 重跑並保留完整 log。

- RAM：108,156 / 248,832 bytes（43.5%）
- Flash：714,880 / 815,104 bytes（87.7%）
- warm-region guard：通過，image end `0xD4880`，保留約 85 KiB
- ISR-handler guard：5 個 critical handlers 均保留

UF2：

```text
C:\Users\cth\Documents\GitHub\faketec-RA-01SH-P\.pio\build\nrf52_promicro_diy_xtal\firmware-nrf52_promicro_diy_xtal-2.8.0.f3fd3d4.uf2
```

- 長度：1,430,016 bytes
- SHA-256：`F4C4352B49874FB314B5E6B473D87B188C51157E29B0B61443D22D3353844BA5`
- binary 內嵌版本：`2.8.0.f3fd3d4`
- 檔名、內嵌版本與 source commit suffix：一致

此 UF2 尚未加入 Git、未複製到發布 repo，也未刷入硬體。保留這個邊界可避免再次把未驗收 binary 當成正式發布物。

## 10. 驗證狀態與限制

| Gate | 結果 | 說明 |
| --- | --- | --- |
| Firmware source reviews | 未發現 P0 blocker | 兩次獨立唯讀審查均未發現會推翻修補方向的缺陷；sender／receiver 尺寸模型對稱，partial-signature bypass 已補 |
| `git diff --check` | 通過 | 無 whitespace error |
| nRF52 production target build | 通過 | `nrf52_promicro_diy_xtal` 成功產出 UF2 |
| `trunk fmt` | 未執行 | 本機未安裝 `trunk` executable |
| native `test_packet_signing` | 未執行案例 | Windows 缺 `pkg-config`／POSIX shell，native build 在測試前停止 |
| embedded test cross-compile | 未執行案例 | build 在 test-only nanopb aggregate-assignment 處停止；該 blocker 尚未完全釐清。這不阻斷本次 production target 成功建置，但不能據此宣稱測試案例或所有 production 行為已通過 |
| 實機刷寫與 RF boundary matrix | 尚未執行 | 本次未取得明確的 flash／裝置重啟授權，未更動節點 |

因此，目前可宣稱：缺陷已由 source 與官方修正交叉確認；最小 firmware 修補已提交；實際 nRF target 已成功編譯；修正版 UF2 已產生。尚不能宣稱新增測試案例已執行通過、兩個硬體節點已修復，或端到端 RF 已驗收。

## 11. 必做部署與實機驗收

### 11.1 刷寫前先逐台確認硬體與復原路徑

目前 retained metadata 只證明 A 節點是 `nrf52_promicro_diy_xtal`；尚無同等證據證明 B 節點也是相同 target。刷寫前必須逐台讀取並保存：

- `pio_env`、hardware model／board target；
- 目前 firmware 內嵌版本與節點識別；
- bootloader／DFU 能力及安裝路徑；
- radio/channel 設定備份與可回復的原始 UF2 SHA-256。

新 artifact 的 `.mt.json` 標示 `requiresDfu=true`。只有在兩台都確認為 `nrf52_promicro_diy_xtal`，且 nRF52 bootloader／DFU 流程、穩定供電與回復映像都已核准後，才能刷入本報告的 UF2；任一板型不符時必須停止，另建正確 target，不能共用此映像。

### 11.2 兩個相同 target 的節點必須一起升級

不能只升級 sender。新 sender 會把不能安全容納 signature 的邊界封包改成合法 unsigned；舊 receiver 若已記住 sender 是簽章節點，仍可能用舊 heuristic 把它誤判為 downgrade attack 而丟棄。

因此兩個 NTsocial MeshLink 綁定的 `nrf52_promicro_diy_xtal` 節點都必須刷入同一個 `2.8.0.f3fd3d4` artifact，然後重新讀取 metadata，逐台確認：

```text
pio_env=nrf52_promicro_diy_xtal
firmware=2.8.0.f3fd3d4
```

若任何節點仍是 `7c6b85d` 或 `16831c5`，不得宣稱 mixed-fleet 已修復。

### 11.3 精確 RF 邊界矩陣

這個矩陣必須繞過 NTsocial 目前會避開 legacy dead band 的 chunk planner；只用一般 App 發訊息，未必會產出 166–168-byte envelope，不能證明 firmware 邊界已修正。應使用受控的 debug／raw gateway 注入（或 test-only、不可進 production 的等價鉤子），固定以下 `Data` 形狀：broadcast、`PRIVATE_APP=256`、無 `reply_id`、無 `emoji`、一般 bitfield，並以 unique message／packet IDs 雙向逐筆送出 raw NTsocial envelope：

```text
165, 166, 167, 168, 169, 180 bytes
```

每筆之間等待上一筆 QueueStatus 與 receiver observation 完成。每筆至少觀察 120 秒。

修正後預期：

- 六種尺寸全部 `QueueStatus res=0`；
- `res=7` 次數為 0；
- receiver 每個 ID 恰好 commit 一次；
- 165 bytes 可簽並剛好 fit；
- 166–180 bytes 在不能容納 signature 時改走合法 unsigned，而不是被本機丟棄；
- serial log 不得出現同 packet ID 的 `Error=7, return NAK and drop packet`；
- 每個成功封包應有 RF enqueue／TX 與另一節點 RX 證據。

### 11.4 NTsocial 三機回歸

1. 保持 NTsocial App 自有 BLE mesh 暫停，MeshLink ↔ node Bluetooth 保持正常。
2. A→B、B→A 各送短文字、長文字與會跨 chunk 的訊息。
3. 每個 marker 在 A/B canonical store 各恰好一筆，無 LoRa→BLE→LoRa loop。
4. 恢復 NTsocial BLE 後，第三支未安裝 MeshLink 的手機須在 120 秒內收到全部測試訊息，且每筆 count=1。
5. 完成 Public BLE text + image 回歸，確認 firmware 更新沒有影響 NTsocial 成熟 BLE baseline。

## 12. 後續具體建議

### P0：本輪必做

1. 在取得操作授權後，將 `2.8.0.f3fd3d4` 刷入兩個 nRF52 節點並執行第 11 節。
2. 將錯標的 `...7c6b85d.uf2` 從發布流程隔離；不要覆寫或刪除到無法稽核，應先保留舊 SHA 證據，再以正確檔名與 SHA 發布已驗收 artifact。
3. 發布 manifest／README 必須同時記錄 source full commit、embedded version、target、file size、SHA-256 與實機 metadata readback。
4. 在兩個節點尚未同版前，保留 App 端避免已知 dead band 的臨時 workaround；硬編 166–168 只能當 exact legacy firmware workaround，不能取代 firmware 修補。

### P1：下一輪處理

1. MeshLink 保留 `COMMAND_ACCEPTED = local durable admission` 語意，但新增 metadata-only 的 radio queue result：至少包含 client request ID、Meshtastic packet ID、typed `Routing.Error` 與時間戳，讓 NTsocial 能看到 `TOO_LARGE` 等 terminal reject。
2. `PacketHandlerImpl.handleQueueStatus()` 不應只把所有非零 `res` 壓成 Boolean；至少記錄並向 gateway 暴露 `TOO_LARGE=7`。
3. 在 Linux CI 或具備 `pkg-config` 的原生環境執行 `test_packet_signing`，把新增邊界測試納入 release gate。
4. 另案評估完整 backport 官方 PR #10858 的其餘 security／interop 修正，不與本次 P0 尺寸修補混成未驗證的大型變更。

## 13. 最終判定

這不是推測性 App 調參，也不是 LoRa pacing 不足。它是 Meshtastic 2.8.0 fork 未包含官方修正所造成的確定性 firmware encode bug；官方已合併同根因修法，本地 nRF52 target 又能重現相同舊程式碼與精確尺寸死區。

本次已完成必要的最小 source 修補與可追溯 UF2 建置。真正完成條件是：兩個節點同時升級、165–180 邊界矩陣全部 `res=0`、雙向 receiver commit、120 秒無晚到失敗／重複，以及 NTsocial BLE baseline 完整恢復。
