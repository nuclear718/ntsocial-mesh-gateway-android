# NTsocial MeshLink Desktop

`:desktop` 是可獨立運作的 Compose Desktop Meshtastic 用戶端，直接使用本專案的 KMP service、Room、
DataStore、Koin、Navigation 3 與共用功能畫面。

## Windows 產品

Windows 發行版本的正式產品名稱為 **NTsocial MeshLink**，品牌與安裝識別如下：

- 藍色 NTsocial 蝴蝶視窗、工作列、系統匣、通知與 installer 圖示。
- 深色纖維蝴蝶背景、主題感知遮罩及約 90–94% 不透明度的 Material 3 面板。
- `#5B63EB` 主色、`#3730A3` 強調色、`#10B981` 次色與 `#F59E0B` 狀態色。
- Segoe UI Variable／Segoe UI 文字與 Cascadia Mono／Consolas 技術資訊字型 fallback。
- 每次程序冷啟動播放一次三秒品牌動畫；從系統匣重新顯示視窗不會重播。
- installer vendor 為 `LiberaNt LLC`、開始選單群組為 `NTsocial`，穩定 upgrade UUID 為
  `6784A2DD-CE59-518B-AA15-C26302D6FA85`。

application ID 保持 `com.ntsocial.meshlink.desktop`。新的 Windows upgrade UUID 讓本產品可與舊
Meshtastic Desktop 並存，後續 NTsocial MeshLink Windows 版本則可原地升級。

這次品牌化不改變 Meshtastic 協定、radio/service/database/settings 行為，也不加入
`NTsocial_Windows` IPC、Windows Service、Authenticator 或簽章功能。共享 Connections UI 仍遵守
Bluetooth-only 首發契約；USB/TCP/Serial backend 仍保留但不重新暴露於共享畫面。

macOS 與 Linux 的名稱、vendor、圖示與 installer metadata 維持原 Meshtastic Desktop 行為。
Android 仍使用既定的 `#67EA94` 綠色 NTsocial 蝴蝶和原有主題流程。

## 品牌素材

Windows 專用圖示由使用者明確授權，從唯讀相鄰專案 `NTsocial_Windows` 複製。完整來源 commit、
原始檔名與 SHA-256 記錄於 [BRANDING_ASSETS.md](BRANDING_ASSETS.md)。

共用的纖維蝴蝶背景已與參考專案逐位元比對相同，因此直接重用
`core:resources` 的 `img_ntsocial_background_butterfly.png`，沒有加入重複檔案。

## 執行與測試

```bash
# 執行桌面程式
./gradlew :desktop:run

# 桌面測試
./gradlew :desktop:test

# 目前作業系統的 release installer
./gradlew :desktop:packageReleaseDistributionForCurrentOS
```

Windows 原生打包需要包含 `jpackage.exe` 的完整 JDK 21。Compose Desktop release 使用 ProGuard
tree-shaking，但不混淆開放原始碼；規則位於 `desktop/proguard-rules.pro`。

## 架構界線

- `Main.kt` 只負責 Koin/service lifecycle、locale/theme、視窗、系統匣與 host branding。
- `branding/` 集中管理 Windows 產品識別、色盤、字型、資源選擇與 splash phase。
- `ui/DesktopMainScreen.kt` 保留 `MeshtasticAppShell`、`MeshtasticNavigationSuite` 與
  `MeshtasticNavDisplay`。
- `navigation/DesktopNavigation.kt` 組裝現有共用 feature graph。
- `radio/DesktopRadioTransportFactory.kt` 與 service/repository 路徑不因品牌化改變。
- 共用 `AppTheme(darkTheme, dynamicColor, content)` API、Material Expressive、Dynamic Color 與
  theme preference flow 保持相容；Windows host 僅透過可選 CompositionLocal 注入 override。
- event firmware branding 永遠優先於 host 預設品牌圖示。

本模組仍受根目錄 `NOTICE.md`、`THIRD_PARTY_NOTICES.md` 與
`docs/copyright-and-attribution.md` 所定義的上游歸屬與 GPL-3.0-or-later 條款約束。
