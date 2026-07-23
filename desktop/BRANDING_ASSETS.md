# NTsocial MeshLink Windows 品牌素材來源

本檔記錄 Windows 品牌化所複製的二進位素材。來源倉庫僅作唯讀參考，沒有修改其程式碼或資產。

- 來源倉庫：`C:\Users\cth\Documents\GitHub\NTsocial_Windows`
- 複製時來源 HEAD：`84c6f8c4349eaecff741a09d4e77a7c3e9d04b68`
- 授權依據：使用者明確授權將該專案的 NTsocial 藍色蝴蝶品牌素材複製至本 GPL 專案。
- 資產性質：NTsocial／LiberaNt LLC 專案自有品牌素材；不變更 Meshtastic 上游程式碼歸屬。

| 本專案檔案 | `NTsocial_Windows` 原始檔案 | 原始引入 commit | SHA-256 |
|---|---|---|---|
| `desktop/src/main/resources/icon.ico` | `src/NTSocial.App/Assets/AppIcon.ico` | `ff65b6bd82acd8349516adc616379b22bd7fb56f` | `B622196BADECED33CC37B6FE166979395A1AF41D6C421326BE5F2671CE38260A` |
| `desktop/src/main/resources/ntsocial_windows_butterfly_24.png` | `src/NTSocial.App/Assets/Square44x44Logo.targetsize-24_altform-unplated.png` | `bf1d01296fe22ac3867f6cc691a096908960c788` | `A82A6AA035F0C7CCB3877A2E7D6EDB385BC6B4603F87473C95EB836C00026D00` |
| `desktop/src/main/resources/ntsocial_windows_butterfly_48.png` | `src/NTSocial.App/Assets/Square44x44Logo.targetsize-48_altform-lightunplated.png` | `bf1d01296fe22ac3867f6cc691a096908960c788` | `3F3D70599314F6A031A2AC7B19D02598DF8DB9F8054B854E0C532EB905A42E7B` |
| `desktop/src/main/resources/ntsocial_windows_butterfly_512.png` | `src/NTSocial.App/Assets/Brand/ntsocial_launcher_512.png` | `bf1d01296fe22ac3867f6cc691a096908960c788` | `366C80EF5EA8E1522C212B623E7CC2278AC14E5B88B08F07666F281C9DDF2A10` |

## 已存在且直接重用的背景

`core/resources/src/commonMain/composeResources/drawable/img_ntsocial_background_butterfly.png`
與來源的 `src/NTSocial.App/Assets/Brand/background_butterfly_a.png` SHA-256 均為
`B21284D533FC0ECDF766CF0A4848559B09694631406805C868127785771210E2`。

因內容完全相同，Windows host 直接使用共用 Compose resource，不另外複製。
