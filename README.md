# 黑白像素桌面鯊魚 (Android)

原生 Kotlin Android Studio 專案。可選一般鯊魚 / 錘頭鯊、俯視 / 側視偏俯視，透明懸浮視窗在其他 App 上方游動。

## 安裝
1. 用 Android Studio 開啟此資料夾，等待 Gradle 同步（需要網路下載 Android Gradle Plugin）。
2. 在 Android Studio 選 Build > Build APK(s)。
3. 將 `app/build/outputs/apk/debug/app-debug.apk` 安裝至 Android 手機。
4. 啟動 App，按「啟動 / 更新鯊魚」，授予「顯示在其他應用程式上層」權限後再次按啟動。
5. 透過通知「停止」按鈕或 App 的停止按鈕結束服務。

## 說明
- 這是第一版可編譯原型，圖形由程式繪製，並非直接使用網路付費素材。
- 懸浮視窗設為 `FLAG_NOT_TOUCHABLE`，因此可穿透觸控；尚未實作拖曳或點擊鯊魚。
- Android 版本、廠商省電策略可能限制長時間背景運作。某些系統安全畫面不允許覆蓋。
- 本環境未實際執行 Android Gradle 編譯，需在 Android Studio 驗證。
