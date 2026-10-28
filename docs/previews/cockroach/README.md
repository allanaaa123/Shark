# 蟑螂原地待機動畫預覽

![24 FPS 待機預覽](idle/idle-preview.gif)

- [直接開啟 GIF](idle/idle-preview.gif?raw=true)
- [MP4 影片](idle/idle-preview.mp4?raw=true)
- [透明 Animated WebP](idle/idle-transparent.webp?raw=true)

96 幀，4 秒無縫循環。MP4 與 WebP 為 24 FPS；GIF 受時間精度限制，平均 24 FPS。

保留選定靜態圖的身體、六隻腳與觸鬚紋理，只對觸鬚做獨立的小幅彎曲。這是圖像綁定動畫，不是實拍或動作捕捉。身體固定區域已經過影格像素比對，MP4 幀率與幀數已由 ffprobe 驗證。透明素材為 1152×864 無損 Animated WebP；GIF 為 960×720 灰底預覽。

本分支僅供素材預覽，尚未整合進 Android App。
