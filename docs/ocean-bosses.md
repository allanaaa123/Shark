# 海洋霸主：三選一（1.3）

使用者已確認鄧氏魚、巨型龍膽石斑、大王魷魚全部製作，但同時只能選一種加入。巨齒鯊未加入此版本。

海洋頁的「海洋霸主」開啟單選面板，提供不加入、鄧氏魚、巨型龍膽石斑、大王魷魚。套用才變更；取消保持原狀。切換替換共用名額並保留原 ID／位置，最多一隻，計入 50 隻總上限；滿額但已有霸主仍能切換。一般新增與常態隨機抽樣只使用原 14 種。霸主也出現在生物頁「霸主」分類，點擊可回到三選一面板。

霸主固定 2.8 倍繪製比例、40% 基準游速，不受一般批次大小／速度滑桿影響；使用較慢的轉向上限，搖動維持沉穩。模型、保存載入及復原都保護共用唯一名額；存檔中多餘霸主被移除並保留第一隻，衝突復原不局部執行。

三種使用已確認的俯視設計與獨立連續網格。鄧氏魚頭甲穩定、石斑胸鰭緩慢收放、大王魷魚以外套膜帶領且腕與觸手落後。魷魚特效從頭部附近的噴水位置發出。全部 17 種按物種共用透明 512px WebP，解碼像素合計 17 MiB。

BossAvoidance 使用共同的移動前快照，讓多隻生物同時提早預測交會並平滑減速轉向；沒有位置推開或瞬移，不要求一對一配對。它獨立於可關閉的一般社交模式。霸主不參與普通跟隨／群游配對；離屏回游與驚嚇的既有導航優先順序保留。

已打包的正式素材直接提交至 app/src/main/assets/shark，正常 Gradle 建置不需 Python。原始透明來源保留於 tools/boss_sources。若要重建：

```sh
python -m pip install Pillow numpy scipy
python tools/prepare_boss_poses.py tools/boss_sources/dunk.png tools/boss_sources/grouper.png tools/boss_sources/squid.png
python tools/check_species_assets.py
```

驗證包含三選一替換、50 隻滿額、取消／移除／復原／Activity 重建、保存資料正規化、隨機排除霸主、多隻提早避讓、三種網格循環與極限轉彎不翻折，以及全部 17 種素材解碼。首次功能提交的資產檢查、testDebugUnitTest、lintDebug、assembleDebug 已通過：
https://github.com/allanaaa123/Shark/actions/runs/40880362690

實機幀率、耗電與小米背景行為仍需手機確認。鄧氏魚的體色與軟組織屬藝術復原。
