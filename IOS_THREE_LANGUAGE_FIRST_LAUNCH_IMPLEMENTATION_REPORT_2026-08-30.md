# iOS Three-Language First-Launch Implementation Report — 2026-08-30

## Scope

This change affects the iOS `NTsocial MeshLink` track. The first-launch selector is now shared with Android from common Compose source, so Android retains its existing behavior while iOS receives the same layout, assets, language order, and immediate selection model. Desktop does not adopt the first-launch gate. The shared `SettingsViewModel` change is read-only state exposure and compiles for Android, Desktop/JVM, and iOS.

## Implemented behavior

- A fresh iOS install waits for the atomic `UiPrefs.appLaunchPreferences` snapshot before deciding whether to show the selector.
- When neither introduction completion nor a persisted locale exists, iOS renders the exact shared Android selector: NTsocial background, US/Taiwan/Japan flags, 30%-from-right panel placement, 360-dp limit, 24/16/12-dp spacing, 48-dp rows, 24-dp flags, blue radio states, and `English`, `繁體中文`, `日本語` ordering.
- Selecting `en`, `zh-TW`, or `ja` awaits the locale write before the shared product shell replaces the selector. A persisted locale bypasses the selector on subsequent launches.
- The iOS Compose root provides the persisted locale to Compose resources, so changing the language updates the running shared UI without an Android-only AppCompat dependency.
- iOS Settings now includes `App language` in the App section and uses the same three-option radio-dialog interaction as Android.
- The Xcode host declares `en`, `ja`, and `zh-Hant` localizations. `InfoPlist.strings` localizes the Bluetooth permission description in all three languages.
- Missing Japanese labels on the iOS Settings surface were added; unchanged resource keys continue to use the project's existing shared language catalogs and fallback behavior.

## Validation evidence

- `:feature:intro:jvmTest`: 9/9 passed.
- Focused `SettingsViewModelTest`: 14/14 passed, including live locale state after `setLocale`.
- `:ios:runtime:jvmTest`: 19/19 passed.
- Changed `feature:intro`, `feature:settings`, and `ios:runtime` module Spotless and Detekt checks passed. Android intro/settings assembly and iOS Simulator Arm64 compilation passed in the changed-module gate.
- Final iOS runtime Spotless, Detekt, and Simulator Arm64 compilation replay passed (173 actionable tasks).
- `plutil` accepted `Info.plist` and every localized `InfoPlist.strings`; `xcodebuild -list` parsed the project.
- A fresh signing-disabled Simulator Debug Xcode build succeeded. The app bundle contains `en.lproj`, `ja.lproj`, and `zh-Hant.lproj`, and reports the same three `CFBundleLocalizations` values.
- The final-source app installed and cold-launched on a newly created iOS 26.5 simulator as PID 42706, retained the same PID on a second launch check, and visibly rendered the expected first-launch selector.

The unfiltered `feature:settings:jvmTest` run remains non-green because existing `DebugSearchTest` clear/search/filter semantics cases reported 12 failures across its repeated test configurations. The language-focused Settings test and all changed-module compile/static-analysis gates are green; no production failure was attributed to the language change.

## Evidence boundary

This is source, JVM-test, iOS Simulator compile/build, and Simulator visual evidence. It is not a signed archive, physical-device accessibility/dynamic-type check, physical Bluetooth permission check, TestFlight result, or App Store-readiness claim. No Windows UI behavior was changed.
