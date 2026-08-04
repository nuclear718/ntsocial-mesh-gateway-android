# NTsocial MeshLink 節點私訊缺陷調查與修復施工計劃

> 調查日期：2026-08-04
>
> 影響產品：Android NTsocial MeshLink；共享 KMP 修改須回歸 Windows Desktop
>
> 文件性質：程式碼靜態比對、根因分層與施工計劃；尚未宣稱已完成雙節點 RF 驗證

## 1. 執行摘要

目前的問題不是單一的「Meshtastic 私訊加密被改壞」，而是三條不同的訊息路徑被混稱為 private message，且 fork 同時缺少數個官方後續修補。

核心結論如下：

1. **原生 Meshtastic 文字私訊的建包主幹仍在，而且欄位靜態上正確。**
   MeshLink UI 仍會建立 TEXT_MESSAGE_APP / port 1、指定目的節點、wantAck=true；雙方有公鑰時使用應用層 channel 8，CommandSender 再正確轉為 wire channel 0、pki_encrypted=true 並附目的節點公鑰。因此不應把原生私訊改成 port 256，也不應移除 channel 8 到 wire channel 0 的轉換。

2. **NTsocial Gateway 並沒有實作一條明確的節點私密傳送命令。**
   目前只接受 SEND_NTSOCIAL_ENVELOPE_TO_ROUTE 與 SEND_CHANNEL_TEXT。SEND_CHANNEL_TEXT 明確固定傳給 ^all；現有 port 256 routed overlay 雖可帶 to，卻把 RF transport channel 鎖在 route 的 0..7 頻道，沒有進入官方 PKI direct-message 路徑。若母程式把 SEND_CHANNEL_TEXT 加上 EXTRA_TO 當私訊，EXTRA_TO 會被忽略，結果反而是廣播，這是必須立即 fail closed 的隱私風險。

3. **fork 缺少官方已修復的私訊列表 contact_key race。**
   Room 已把私訊存入正確的 contact_key，但 paged repository 丟掉該 key，ContactsViewModel 又用可能尚未就緒的本機身分重算。冷啟、切換 radio 或重連時，兩個不同私訊可能被重算成同一個「自己的節點 ID」，造成對話歸錯、詳情空白或 LazyColumn duplicate key。頻道廣播不依賴這個本機身分判斷，所以看起來仍正常。

4. **Android 送出工作存在「封包實際未送，Worker 卻回報成功」的確定缺口。**
   Activity 尚未綁定或已 onStop 時，AndroidRadioControllerImpl 看到 meshService == null 會直接 return；SendMessageWorker 隨後仍把訊息標為 ENROUTE 並回傳 Result.success()，不會 retry。官方已移除這條內部 AIDL 依賴。

5. **韌體 2.8 的 QueueStatus 35 尚未相容。**
   PKI 私訊前置的 shared-contact 封包可能收到 ERRNO_SHOULD_RELEASE=35；官方把它視為本機 loopback 成功，fork 仍視為失敗或等待逾時。

因此最小而完整的修復不是重併一千多個 upstream commits，而是依序做四個小批次：

- 先保留 Room 已儲存的 contact_key，修復「已收但看不到」；
- 移除訊息送出對 nullable Activity binder 的依賴，修復偽成功；
- 移植 QueueStatus 35 與私訊流程的必要錯誤傳播；
- 新增 additive Gateway v2 的明確 PKI private-overlay 命令，並禁止 SEND_CHANNEL_TEXT 接受目的節點。

## 2. 比對基準

| 項目 | 調查時版本 |
|---|---|
| NTsocial fork main | fec591ec8e90fbfca2477c51b64f237b50f406e5，2026-07-31 |
| 官方 upstream/main | e51fdf8a5bd7bc59488949079f247a3868318676，2026-08-03 |
| 共同祖先 | c0d95d6ac4196fcbc705f2d3f174c7d9c46a77b2，2026-05-07 |
| 分歧量 | fork 約 46 個獨有 commits；upstream 約 1030 個獨有 commits |

此版本差距代表「整包 rebase」不是本缺陷的低風險修法。官方移除 AIDL 的 3e0d2d39 會牽動約 264 個檔案，容易碰撞 NTsocial Gateway、Room 43 與自訂 namespace；本計劃只移植已確認相關的語意與小型 patch。

主要官方參考修補：

- [93b24572：使用 DB stored contact_key，避免私訊 key 重算與列表 crash](https://github.com/meshtastic/Meshtastic-Android/commit/93b24572c8d6b03310be1963ad5d5c9d243d3de2)
- [3e0d2d39：移除內部 AIDL API，改為 in-process send 與可等待的 queue admission](https://github.com/meshtastic/Meshtastic-Android/commit/3e0d2d39c3549256d043bd4c03e14d1b5329da1c)
- [45939b30：SendMessageUseCase 不再吞掉送出錯誤](https://github.com/meshtastic/Meshtastic-Android/commit/45939b30e63f53148148d3b21d9f47abf8a4bd45)
- [c07c9141：韌體 2.8 QueueStatus res=35 視為成功](https://github.com/meshtastic/Meshtastic-Android/commit/c07c9141c0e037f6103ce9b878d5a7342533ad1f)
- [bc5719c8：韌體 2.8 節點重新編號後的 device identity 遷移](https://github.com/meshtastic/Meshtastic-Android/commit/bc5719c8a76c89e285b8d7bc5628f72ffb27a0b8)
- [09555a43：重新編號後 stale identity replay 的 reconciliation](https://github.com/meshtastic/Meshtastic-Android/commit/09555a43984e44f937e00c8a33c2104028599627)

fork 已包含較早的 cb89b111、b3be9e2c 與 60cc2f42 私訊相關修補，但未包含 93b24572；這正好解釋為何目前官方 App 較穩，而 fork 在冷啟／重連後仍可能出現私訊空白或歸錯。

## 3. 必須先分清楚的三種訊息

| 路徑 | Meshtastic port | 目的地 | RF 加密／頻道 | 本機歷史 | 正確用途 |
|---|---:|---|---|---|---|
| 原生 Meshtastic 私訊 | TEXT_MESSAGE_APP / 1 | 特定 !xxxxxxxx | 有雙方 key：app ch 8 → wire ch 0 + PKI；舊節點才 fallback heard-on channel | 正常 Room chat、Contacts、Message detail | MeshLink UI 與官方 App 可互通的文字私訊 |
| Gateway 頻道文字 | TEXT_MESSAGE_APP / 1 | 固定 ^all | route 所指 0..7 頻道 | 正常 Room broadcast history | 母程式在既有 Meshtastic 頻道發廣播文字 |
| NTsocial overlay | PRIVATE_APP / 256 | 現況可 null、^all 或特定 node | 現況固定 route 0..7，沒有自動 PKI | inbound 只進 bounded in-memory Gateway cache；不進一般 chat Room | NM binary envelope；母程式擁有 canonical history |

PRIVATE_APP 是 Meshtastic application port 名稱，不等於「已做端對端私訊」。是否真正是節點私密傳輸，取決於 MeshPacket 是否有：

- 精確的目的 node number；
- pki_encrypted=true；
- 目的節點 public_key；
- wire channel=0，而 domain DataPacket 保持 channel=8。

## 4. 目前原生私訊的正確部分

以下路徑不應被重寫：

1. **feature/node/.../NodeDetailViewModel.kt**
   getDirectMessageRoute() 在雙方都有 PKC 時選 DataPacket.PKC_CHANNEL_INDEX，也就是 8；否則才用目的節點 heard-on channel。

2. **core/repository/.../SendMessageUseCase.kt**
   contact key 例如 8!70fdde9b 會被拆成 channel=8、to=!70fdde9b，並建立文字 DataPacket。

3. **core/model/.../DataPacket.kt**
   文字 constructor 使用 TEXT_MESSAGE_APP，wantAck 預設為 true。

4. **core/data/.../CommandSenderImpl.kt**
   resolveNodeNum() 正確移除 ! 並解析 32-bit node number。buildMeshPacket() 對 channel 8 設 pki_encrypted、public_key，且將 wire channel 改為 0。

5. **core/model/.../MeshDataMapper.kt**
   收到 PKI MeshPacket 時，會在 app domain 還原成 channel 8。

6. **core/data/.../MeshDataHandlerImpl.kt**
   TEXT_MESSAGE_APP 會依 incoming/outgoing 計算遠端 contact，寫入 Room。

這些證據排除下列錯誤修法：

- 不要把原生文字私訊改成 port 256；
- 不要把 app channel 8 直接改成 wire channel 8；
- 不要移除 pki_encrypted 或目的 public key；
- 不要為「看起來像官方」而硬編 channel 0；
- 不要關閉 wantAck；
- 不要把 NM binary envelope 塞入 TEXT_MESSAGE_APP。

## 5. 已確認的差異與根因

### 5.1 P0：Gateway 沒有 private command，現有 direct overlay 又沒有走 PKI

**證據**

- **core/api/.../NtsocialGatewayContract.kt** 只有：
  - COMMAND_SEND_NTSOCIAL_ENVELOPE_TO_ROUTE
  - COMMAND_SEND_CHANNEL_TEXT
- **core/service/.../NtsocialGatewayCommandReceiver.kt** 的 classifyGatewayCommand() 只接受上述兩種 v2 命令；SEND_PRIVATE 或等價命令不存在。
- parseGatewayNativeTextCommand() 不讀 EXTRA_TO。
- **core/data/.../NtsocialGatewayRepositoryImpl.kt** 的 persistAndQueueNativeBroadcastText() 明確建立 to=^all。
- routed overlay 用 route.channelIndex 建 port 256 DataPacket。route 只會是 configured channel 0..7，因此 CommandSender 不會設 PKI。

**結果**

- 母程式若送 SEND_CHANNEL_TEXT + EXTRA_TO，實際上仍是廣播。
- 母程式若用 routed overlay + 特定 to，得到的是 legacy channel-encrypted directed packet，不是官方 PKI DM。
- 新版韌體只接受 PKI direct message 時，port 256 direct packet可能根本不會到達。

**可信度：高。** 這是程式碼與 Gateway contract 的直接缺口，不依賴現場推測。

### 5.2 P0：DB 已存正確私訊，Contacts paged path 卻丟掉 contact_key

**證據**

- **core/database/.../PacketDao.kt** 依真正的 contact_key 分組。
- **core/data/.../PacketRepositoryImpl.kt** 的 getContactsPaged() 只回 it.data，丟掉 Room row 的 it.contact_key。
- **feature/messaging/.../ContactsViewModel.kt** 再以 packet.from、packet.to 與可能為 null／stale 的 myId 重算 contactKey。
- 官方 93b24572 專門修正同一問題。

**具體失敗例**

- 本機 B 傳給 A 的 PKI row 正確儲存為 8!A。
- 冷啟時 myId 尚未 emit，latest packet 的 from=!B 無法被判斷為本機送出。
- ViewModel 把它當 incoming，重算成 8!B。
- 點擊後 detail DAO 查 8!B，但資料實際在 8!A，因此顯示空對話。
- 多個「最後一則為本機送出」的私訊都可能塌成 8!B，導致 duplicate LazyColumn keys。

頻道廣播永遠由 toBroadcast=true 得到 channel + ^all，不依賴 myId，所以表面上正常。

**可信度：高。** fork 明確缺少官方針對同一錯誤的修補。

### 5.3 P0：Android nullable binder 會靜默丟包，Worker 卻標示 ENROUTE

**證據**

- **core/service/.../AndroidRadioControllerImpl.kt**：
  - meshService == null 時記錄 dropping packet；
  - 隨後直接 return，不拋例外。
- **core/service/.../MeshServiceClient.kt**：
  - Activity onStart 綁定；
  - onStop 清掉 binder。
- **core/service/.../worker/SendMessageWorker.kt**：
  - 只檢查 app-level connectionState；
  - sendMessage() 正常 return 後即更新 ENROUTE 並 Result.success()。

app-level Connected 與 Activity binder 非同一狀態。背景 WorkManager、畫面切換或程序恢復時，可以出現 radio 仍 Connected、binder 卻為 null；封包未進 MeshService／CommandSender／ToRadio，但永久失去 retry。

**可信度：高，但是否為每一個現場失敗的直接觸發條件，仍需以 metadata log／測試確認。**

### 5.4 P0：私訊送出前多了一個不必要的 Gateway channel flow 依賴，且錯誤被吞掉

**core/repository/.../SendMessageUseCase.kt** 對所有訊息先執行 channelSetFlow.first()，但 channelSet 只在 broadcast gateway identity 產生時需要。私訊若遇到 channel flow 尚未 emit 或失敗，會在 save/enqueue 之前停住。

同一 try/catch 只 log 不 rethrow；UI 與呼叫端可能以為 use case 已完成。

**可信度：中高。** 這是 fork-specific 不必要耦合與錯誤遮蔽，應與主要修補一起移除。

### 5.5 P0：韌體 2.8 的 shared-contact QueueStatus 35 被當成失敗

PKI 私訊會先送 self-addressed shared-contact admin packet。韌體 2.8 對本機 loopback 可回 QueueStatus.res=35，也就是 ERRNO_SHOULD_RELEASE。官方 c07c9141 已把 res=35 視為成功；fork 的 **PacketHandlerImpl.handleQueueStatus()** 只接受 res==0。

結果是前置步驟假失敗或等待五秒；若 ViewModel coroutine 期間被取消，主私訊可能尚未 enqueue。

**可信度：高，且特別影響 PKI 私訊。**

### 5.6 P1：Queue admission 仍是 fire-and-forget

目前 CommandSender.sendData() 與 PacketHandler.sendToRadio(MeshPacket) 都不是 suspend。PacketHandler 只對 Channel.UNLIMITED 執行 trySend 後立即返回；Worker 在真正加入 queuedPackets 之前就可能回報成功。

官方 3e0d2d39 已改成 suspend，並在 queueMutex 內完成 admission 後才返回。這不是第一個 hotfix 必須整包移植的理由，但應在第二批中針對文字訊息與 durable Gateway 路徑補上可等待的 admission。

### 5.7 P1：節點 ID parser 與韌體 2.8 renumbering

- **DataPacket.idToDefaultNodeNum()** 直接 id.toLong(16)，不能解析標準 !a1b2c3d4。
- NodeRepository fallback 可能因此得到 num=0。
- fork 未含官方韌體 2.8 device identity migration／reconciliation；舊 DB 若出現 user.id 與 node.num 不一致，route 可能指向舊 node number。

主 CommandSender.resolveNodeNum() 對正常已載入節點仍是正確的，因此這是第二階段 hardening，不應先擴成大型 NodeDB migration。

### 5.8 設計邊界：port 256 收到後本來就不會出現在 MeshLink 一般私訊 UI

TEXT_MESSAGE_APP 會進 rememberDataPacket()、Room、Contacts 與 Message detail。PRIVATE_APP / 256 與 legacy 497 只進 NtsocialGatewayRepository.cacheInbound() 的 bounded in-memory cache；NtsocialTransport 註解也明訂 MVP 不把此 cache 寫入 Room。

因此：

- 若目標是「官方／MeshLink UI 文字私訊」，必須用 port 1；
- 若目標是「母程式 NTsocial 的 NM envelope 私訊」，應檢查 Gateway cache/event 與母程式 canonical store，不能拿 MeshLink chat Room 是否有 row 當成功判定；
- 第一批修復不應把 port 256 硬塞進一般聊天歷史，也不新增 Room schema。

## 6. 分層判定：先確認封包卡在哪裡

| 檢查層 | 必查 metadata | 判定 |
|---|---|---|
| Sender 建包／ToRadio | packetId、to、decoded port、domain channel、wire channel、pki flag、public_key 是否非空、wantAck | direct port 256 若仍是 ch 0..7 且 pki=false，鎖定 Gateway 建包缺陷 |
| Receiver FromRadio | 相同 packetId、from、to、port、pki flag | Sender 有 ToRadio 而 Receiver 無 FromRadio，才進入 RF、relay、key 或 firmware 排查 |
| 原生 port 1 保存 | Room row、contact_key、myNodeNum、filtered | row 存在但 UI 空，優先判定 stored contact_key regression |
| overlay port 256 接收 | Gateway cache 是否有 envelope、EVENT_ENVELOPE_AVAILABLE 是否發出 | 不應期待一般 chat Room row |
| 母程式消費 | cache poll/event、headerMsgId、母程式 canonical store | cache 有而母程式無，問題在 Gateway 消費／母程式整合，不是 Meshtastic chat DAO |

診斷只記 metadata。不得記錄私訊文字、NM payload、PSK、公鑰內容、authorization token 或 route token。

## 7. 施工計劃

每個批次獨立 commit／PR，先寫會失敗的 regression test，再改 production code。不要在同一批順手重構 UI、protobuf、Room schema 或 Windows IPC。

### 批次 A：修復「已收／已存但看不到」與接收端不必要耦合

**目標**

- 私訊列表、導航、未讀數與 detail 查詢一律使用 DB 已保存的 contact_key。
- 私訊 Room insert 不再等待 Gateway broadcast channel catalog。

**修改檔案**

1. **core/repository/src/commonMain/kotlin/com/ntsocial/meshlink/core/repository/PacketRepository.kt**

~~~kotlin
fun getContactsPaged(): Flow<PagingData<Pair<String, DataPacket>>>
~~~

2. **core/data/src/commonMain/kotlin/com/ntsocial/meshlink/core/data/repository/PacketRepositoryImpl.kt**

~~~kotlin
.map { pagingData ->
    pagingData.map { row -> row.contact_key to row.data }
}
~~~

3. **feature/messaging/src/commonMain/kotlin/com/ntsocial/meshlink/feature/messaging/ui/contact/ContactsViewModel.kt**

~~~kotlin
pagingData.map { (contactKey, packetData) ->
    // contactKey 只使用 Room stored key；不得由 from/to/myId 重算。
}
~~~

刪除 contactId 與 contactKey 的重算。myId 只可繼續用於顯示「誰送的」或 preview 格式，不得再決定對話 identity。getUnreadCount()、getMessageCount()、settings、mute、delete 與 navigation 全部使用 stored contactKey。

4. **core/data/src/commonMain/kotlin/com/ntsocial/meshlink/core/data/manager/MeshDataHandlerImpl.kt**

只有未過濾的 broadcast TEXT_MESSAGE_APP 才讀 channelSetFlow 並建立 NtsocialGatewayIdentity：

~~~kotlin
val shouldCaptureGatewayIdentity =
    captureGatewayIdentity &&
        !isFiltered &&
        dataPacket.to == DataPacket.ID_BROADCAST
~~~

私訊直接 insert，不得為 Gateway broadcast identity 等待 channelSetFlow.first()。

**測試**

- **ContactsViewModelTest.kt**：seed 8!remoteA 與 8!remoteB；兩筆 latest packet 的 from 都是本機，令 myId=null，輸出 keys 仍為兩個 stored keys 且互異。
- repository regression：paged output 必須保留 Room contact_key。
- round-trip：列表產出的 contactKey 直接餵 detail DAO，必須查到訊息。
- **MeshDataHandlerTest.kt**：
  - inbound PKI TEXT 產生 8!remote；
  - legacy channel 2 TEXT 產生 2!remote；
  - channelSetFlow 不 emit 或拋錯時，private TEXT 仍完成 insert；
  - broadcast 才嘗試建立 Gateway identity。

**完成條件**

- 冷啟、切 radio、重連時，不同私訊不會塌成 self key。
- Room schema 不變，不需要 migration。

### 批次 B：修復送出偽成功、私訊 flow 阻塞與韌體 2.8 QueueStatus

#### B1. 先移除 nullable binder 靜默丟包

**修改檔案**

- **core/service/src/androidMain/kotlin/com/ntsocial/meshlink/core/service/AndroidRadioControllerImpl.kt**
- 必要的 Android Koin wiring／generated binding test

最終做法是讓 sendMessage() 使用現有 in-process MeshRouter.actionHandler 與 NodeManager，而不是 AndroidServiceRepository.meshService：

~~~kotlin
override suspend fun sendMessage(packet: DataPacket) {
    val myNodeNum = nodeManager.myNodeNum.value
        ?: throw RadioNotConnectedException()
    meshRouter.actionHandler.handleSend(packet, myNodeNum)
}
~~~

這只切換一般訊息送出；其他尚未移植的設定／管理 AIDL call 暫時保持，避免擴大範圍。若必須分兩個 release，第一個止血 commit 至少要把 meshService==null 的 return 改成 throw RadioNotConnectedException()，使既有 Worker 能 retry；下一個 commit 再切 in-process path。

#### B2. 讓「送出成功」至少代表已進本地 packet queue

保留既有同步相容路徑，但對 WorkManager／一般訊息新增可等待 admission 的內部 API：

- **PacketHandler.kt / PacketHandlerImpl.kt**：新增 suspend enqueueToRadio(MeshPacket)，在 queueMutex 內檢查連線、加入 queuedPackets 並啟動 processor 後才返回。
- **CommandSender.kt / CommandSenderImpl.kt**：新增 suspend sendDataAndAwaitAdmission(DataPacket)，共用既有 validation 與 buildMeshPacket，最後呼叫 enqueueToRadio。
- **MeshActionHandler.kt / MeshActionHandlerImpl.kt**：使訊息送出使用 awaited path；完成 admission 後才 broadcast ENROUTE／remember。
- **DirectRadioControllerImpl.kt** 與 AndroidRadioControllerImpl 使用 awaited action。
- deprecated AIDL send() 保留 external signature；在 ServiceScope 中呼叫同一 suspend action，不再成為 Android App 內部的必要通路。

不要另建第二套 durable DB queue。Room + MessageQueue + WorkManager 仍是唯一 retry source；PacketHandler queue 只是 radio transport admission。

#### B3. 移除私訊對 broadcast channelSet 的依賴並傳播錯誤

**core/repository/.../SendMessageUseCase.kt**

- 把 channelSetFlow.first() 移到 dest == ^all 且 channel != null 的 Gateway identity 分支內。
- savePacket() 或 messageQueue.enqueue() 失敗時，記 metadata-only error 後 rethrow。
- CancellationException 必須原樣傳播。
- 保留私訊 shared-contact／favorite 的現有判斷，但主訊息失敗不可被吞掉。

#### B4. 相容 QueueStatus 35

**core/data/.../PacketHandlerImpl.kt**

~~~kotlin
val success = queueStatus.res == 0 || queueStatus.res == 35
if (queueStatus.res == 0 && queueStatus.free == 0) return
~~~

res=35 即使 free==0 仍要完成對應 deferred 為 true；其他非零值保持 false。

**測試**

- 新增 **AndroidRadioControllerImplTest.kt**：
  - node identity 未就緒時拋 RadioNotConnectedException；
  - Activity binder=null 仍透過 direct path admission 一次；
  - 不會同時走 binder 與 direct path。
- **SendMessageWorkerTest.kt**：
  - send exception → QUEUED + Result.retry；
  - 絕不可標 ENROUTE；
  - send 返回時 queue admission 已完成。
- **SendMessageUseCaseTest.kt**：
  - 8!70fdde9b 產生 port 1、to=!70fdde9b、channel=8、wantAck=true；
  - channelSetFlow 永不 emit 時，DM 仍 save/enqueue；
  - save/enqueue exception 會向呼叫端傳播；
  - replyId 原樣保存。
- **PacketHandlerImplTest.kt**：
  - res=35/free>0 → true；
  - res=35/free=0 → true；
  - 其他非零 → false；
  - enqueueToRadio 返回前 queuedPackets 已 admission。
- 新增 **CommandSenderImplTest.kt**：
  - 8!70fdde9b → MeshPacket.to=0x70fdde9b、port 1、pki=true、public_key 非空、wire channel=0、want_ack=true；
  - legacy channel 1 direct → pki=false、wire channel=1；
  - malformed／unknown destination fail closed。

**完成條件**

- Background WorkManager 不依賴 Activity binder。
- Result.success() 只會在本地 radio queue admission 後發生；它仍不代表 RF ACK 或遠端已讀。

### 批次 C：新增明確、fail-closed 的 NTsocial PKI private overlay

此批次只解決母程式 NTsocial 的 NM envelope 節點私訊，不改原生 Meshtastic 文字私訊。

#### C1. additive Gateway v2 contract

**core/api/.../NtsocialGatewayContract.kt**

新增：

~~~kotlin
const val COMMAND_SEND_NTSOCIAL_PRIVATE_ENVELOPE_TO_NODE =
    "SEND_NTSOCIAL_PRIVATE_ENVELOPE_TO_NODE"
const val CAPABILITY_PRIVATE_OVERLAY_SEND = 1L shl 5
~~~

沿用既有 request_id、authorization token、source_channel_id、route_token、client_message_id、payload、to 與 hop_limit。此命令的 wantAck 由 MeshLink 強制為 true，不接受 caller 降級。

Gateway v1 的欄位、命令與行為全部不變。

#### C2. parser 與 admission

**core/service/.../NtsocialGatewayCommandReceiver.kt**

- GatewayCommandKind 新增 PRIVATE_OVERLAY。
- 新 parser 必須要求：
  - payload 是完整、可 decode 的 NM envelope，且不超過既有限制；
  - to 嚴格符合 ! + 8 個 hex；
  - to 不可為 ^all、null 或本機；
  - client_message_id 為既有 canonical 32-hex；
  - route token／source channel／caller／radio generation 驗證全部沿用；
  - 目的 node number 必須存在 nodeRepository.nodeDBbyNum；
  - local 與 destination 都有 PKC；
  - user.id 與 node.num 若不一致，先以 canonical node.num 路由並記 metadata-only warning；不可送向 stale ID；
  - 任何不滿足條件都不 save、不 enqueue。

新增精確 rejection reasons，例如：

- invalid_destination
- destination_unknown
- destination_is_local
- pki_unavailable
- not_connected

不要 fallback 到 0..7 legacy directed packet。名為 private 的命令必須 fail closed。

#### C3. transport 建包

**NtsocialGatewayRepository.kt / NtsocialGatewayRepositoryImpl.kt**

新增 suspend persistAndQueuePrivateEnvelope()，重用既有：

- NM envelope decode；
- packet ID reservation；
- PacketRepository Room + MessageQueue durable admission；
- client_message_id ledger；
- outbound Gateway cache。

唯一關鍵 transport 差異：

~~~kotlin
DataPacket(
    to = destinationNodeId,
    bytes = rawEnvelope,
    dataType = NtsocialTransport.PRIVATE_APP_PORT_NUM,
    id = packetId,
    channel = DataPacket.PKC_CHANNEL_INDEX,
    hopLimit = hopLimit,
    wantAck = true,
)
~~~

CommandSenderImpl 會把 domain channel 8 轉為 wire channel 0、pki_encrypted=true 並加入目的 public key。不要在 Gateway repository 自己複製公鑰或 MeshPacket 加密邏輯。

route 的 channelIndex／sourceChannelId 仍用來授權母程式可使用的 NTsocial route 與綁定 idempotency；它不再冒充 RF transport channel。收到的 cached envelope 會反映 transport channel 8。若母程式還需要原 NTsocial semantic channel，應從 NM envelope 或既有 source route context 取得，不可期待 MeshPacket.channel 同時承擔兩種語意。

#### C4. 禁止 SEND_CHANNEL_TEXT 靜默變廣播

SEND_CHANNEL_TEXT 保持 broadcast-only，但只要 Intent 有 EXTRA_TO，無論內容為何，都在完成 caller authorization 後回傳 destination_not_allowed，且不得 save/enqueue。這個 guard 必須有 regression test。

#### C5. capability 與事件語意

- Provider capability bits 宣告 API 支援；實際 command 仍逐次檢查 Connected、known destination 與 PKC。
- EVENT_COMMAND_ACCEPTED 只代表 Room + WorkManager durable admission。
- firmware QueueStatus／routing ACK 或 NAK 更新 transport delivery state。
- 「遠端 NTsocial 已解密並入庫」需要 NTsocial application-level receipt；不得把本機 accepted 當成遠端收件。
- inbound port 256 繼續走 Gateway cache/event，由母程式寫入 canonical history；不加入 /v2/message-changes。

**測試**

- **NtsocialGatewayCommandParsingTest.kt**：
  - private command 所有必填欄位；
  - missing、broadcast、self、malformed destination；
  - SEND_CHANNEL_TEXT + EXTRA_TO 必須 reject。
- **NtsocialGatewayRepositoryImplTest.kt**：
  - known PKI destination → port 256、domain ch 8、wantAck=true；
  - unknown／self／PKI unavailable 不寫 Room、不 enqueue；
  - same client_message_id + same fingerprint idempotent；
  - same ID + different destination／payload conflict。
- **CommandSenderImplTest.kt**：
  - port 256 private → exact to、pki=true、public_key 非空、wire ch 0。
- **MeshDataHandlerTest.kt**：
  - inbound PKI port 256 進 Gateway cache；
  - 不進一般 TEXT chat Room；
  - envelope headerMsgId、from、to、packetId 保持可供母程式 correlation。
- Provider／capability contract tests更新 1L shl 5，不改 v1 snapshot。

**母程式配套**

- 母程式必須把 NTsocial peer 映射成 Provider /v1/nodes 或 /v2 status 所提供的 canonical Meshtastic node_id，也就是 !xxxxxxxx。
- 不得把 decimal node_num、NTsocial 使用者 ID、fingerprint 或公鑰字串塞進 EXTRA_TO。
- 母程式依 EVENT_ENVELOPE_AVAILABLE／cache poll 消費 inbound port 256，並以 headerMsgId 寫入自己的 canonical history。

### 批次 D：小型 address／firmware 2.8 identity hardening

此批次在 A～C 與雙節點測試完成後執行，不先移植完整 09555a43。

1. 在 **core/model** 移植小型 NodeAddress／ContactKey value parser：
   - 接受 !00000001 到 !ffffffff；
   - 支援無 ! 的內部相容輸入，但輸出一律 canonical ! + 8 lower-case hex；
   - 以 UInt 處理完整 32-bit 範圍；
   - empty、非 hex、超過 8 位一律失敗。
2. 替換 DataPacket.idToDefaultNodeNum()、NodeRepository fallback、SendMessageUseCase 與 CommandSender 中分散的字串切割。
3. 加 route invariant：
   - parsed user.id == node.num 才直接使用；
   - mismatch 時以 node.num 產生 canonical ID，並只記 node number／error code，不記公鑰。
4. 只有在實機確認韌體 2.8 renumber 後仍留下重複／stale DB row，才另開 NodeDB migration PR。

## 8. 硬體與端到端驗收矩陣

自動化測試不能替代兩台 radio。至少以 A、B 兩節點執行：

| 情境 | Sender 必見 | Receiver 必見 | UI／母程式結果 |
|---|---|---|---|
| 原生 PKI 文字 A→B | port 1、to=B、pki=true、wire ch 0 | 同 packetId、from=A、domain ch 8 | Room 8!A；B 對話可見 |
| 原生 PKI 文字 B→A | 同上反向 | 同上反向 | 回覆後冷啟／重連仍在正確對話 |
| Gateway private overlay A→B | port 256、to=B、pki=true、wire ch 0 | port 256、from=A、cache hit | 母程式 decode 同一 headerMsgId 並入 canonical history |
| foreground | admission、ToRadio | FromRadio | 成功 |
| Activity onStop／background Worker | direct in-process admission、無 binder drop | FromRadio | 成功或明確 retry，不得偽成功 |
| process restart／queued retry | 同 packetId idempotent admission | 最多一次有效訊息 | 不產生不同內容的 duplicate |
| firmware 2.8 shared contact | res=35 被視為成功 | 主 port 1 packet 隨後送出 | 不額外卡 5 秒 |
| 一跳 | 正常 | 正常 | 成功 |
| 兩跳、relay=ALL | 正常 | 正常 | port 1 與 port 256 分別驗證 |
| 兩跳、relay=CORE_PORTNUMS_ONLY | port 1 control 可測；port 256 可能被 relay policy 丟棄 | 依 policy | 不把 policy 限制誤判為 App 加密 bug |

每一輪再重測：

- 冷啟；
- 斷線／重連；
- 切換 active radio database；
- 最後一則訊息是 outgoing；
- ignored／word-filtered sender；
- my_node 與 Packet.myNodeNum scope 一致。

只有同時留存 Sender ToRadio、Receiver FromRadio、Room 或 Gateway cache、以及上層顯示／入庫證據，才可宣稱端到端完成。

## 9. 自動化驗證與品質門檻

每個 Kotlin 批次均執行：

~~~bash
./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests
./gradlew kmpSmokeCompile :app:lintFdroidDebug :app:lintGoogleDebug
~~~

環境要求：

- JDK 21；
- 正確 ANDROID_HOME；
- JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"；
- proto submodule 已初始化。

共享 KMP 修改另確認：

~~~bash
./gradlew :desktop:test
~~~

本計劃文件本身至少執行 git diff --check。既有與本次無關的 root Detekt baseline 必須如實記錄，不得以 suppression 隱藏。

## 10. 發佈、觀測與回滾

### 發佈順序

1. 先發批次 A；它無 schema migration，最容易單獨驗證與回滾。
2. 再發批次 B；以 background Worker 與 firmware 2.8 實機證據作 gate。
3. 批次 C 必須與母程式支援新 capability／command 同版整合，但 v1 與既有 routed overlay 不刪除。
4. 批次 D 僅在前面穩定後進行。

### Metadata-only 觀測點

- request/client correlation 的雜湊或既有非敏感 ID；
- packetId；
- port；
- direction；
- destination validation 結果代碼；
- pki flag；
- queue stage；
- ACK／NAK reason；
- contact_key 只在本機 debug/test 資料中使用，不送 telemetry。

不得記錄 message text、raw envelope、PSK、key bytes、authorization／route token。

### 回滾

- 批次 A 可直接回滾 Kotlin 型別與 ViewModel mapping，無 DB rollback。
- 批次 B 保留 deprecated AIDL adapter；若 direct path 發生新問題，可回到「binder null 必須 throw」的止血版本，不能回到 silent return。
- 批次 C 是 additive capability。母程式看不到 capability 時回到既有功能，但不得把 private 需求降級為 SEND_CHANNEL_TEXT 廣播或 legacy direct。

## 11. 明確不做的事

- 不整包 rebase upstream/main。
- 不修改 Meshtastic protobuf。
- 不新增或遷移 Room schema來解這一輪私訊。
- 不把 port 256 寫入一般 MeshLink chat history。
- 不把 NM binary 當作 TEXT_MESSAGE_APP。
- 不改 Gateway v1 contract。
- 不新增第二套 outbound retry queue。
- 不為 port 256 強制修改使用者節點的 rebroadcast mode。
- 不在沒有兩台 radio 證據時宣稱 RF 或遠端收件已修復。
- 不在本輪設計 Windows IPC；Windows 只做共享 KMP regression。

## 12. Definition of Done

本缺陷只有在以下條件全部滿足時才算完成：

- 原生 port 1 私訊 A↔B 可收發，冷啟／重連後仍落在正確對話。
- Contacts 使用 stored contact_key，沒有 duplicate key 或空 detail regression。
- background Worker 不再依賴 Activity binder，未 admission 的封包會 retry。
- firmware 2.8 res=35 不阻擋主私訊。
- SEND_CHANNEL_TEXT 帶任何 EXTRA_TO 都會 fail closed，絕不靜默廣播。
- 新 Gateway private overlay 產生 port 256、specific to、pki=true、public key、wire ch 0。
- unknown/self/broadcast/no-PKI destination 都不 save、不 enqueue。
- inbound port 256 能由母程式以同一 headerMsgId 消費並寫入 canonical history。
- 一跳與兩跳 relay policy 已分別驗證。
- 全套 Gradle gate、Android lints、Desktop shared regression 與兩節點實機矩陣都有留存結果。

---

最推薦的施工順序是 **A → B1/B3/B4 → B2 → C → D**。其中 A、B1、B3、B4 是最小 P0 修補；C 是 NTsocial 母程式真正要做節點私密 NM envelope 時不可省略的 Gateway 能力。這個順序能先修復使用者眼前的「訊息消失／偽成功」，再新增清楚且不會意外廣播的 private contract。
