from pathlib import Path
from PIL import Image
import json,subprocess
root=Path(__file__).parent
native=[Image.open(root/'cutout_frames'/f'{i:03d}.png').convert('RGBA') for i in range(9)]
assert len({f.tobytes() for f in native})==9
# A real near-matching stride, not a generated gait. Three (42,42,41) ms groups.
native[0].save(root/'real-crawl.webp',save_all=True,append_images=native[1:],duration=[42,42,41]*3,loop=0,lossless=True,method=6)
previews=[]
for f in native:
 bg=Image.new('RGBA',f.size,(190,186,178,255));bg.alpha_composite(f);previews.append(bg.convert('RGB'))
loop=previews*2
loop[0].save(root/'real-crawl.gif',save_all=True,append_images=loop[1:],duration=[40,40,40,40,40,50]*3,loop=0)
# Source-pixel comparison contains the actual floor; no synthetic animal parts.
raw=[Image.open(root/'crop_frames'/f'{i:03d}.png').convert('RGB') for i in range(16)]
raw[0].save(root/'real-source-comparison.gif',save_all=True,append_images=raw[1:],duration=[42]*16,loop=0)
video=root/'video_frames';video.mkdir(exist_ok=True)
for i in range(72):previews[i%9].save(video/f'{i:03d}.png')
subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-framerate','24','-i',str(video/'%03d.png'),'-c:v','libx264','-pix_fmt','yuv420p','-movflags','+faststart',str(root/'real-crawl.mp4')],check=True)
meta={'method':'direct_real_video_frames','fps':24,'nativeSourceFps':25,'sourceFrameNumbers':list(range(28,37)),
      'sourceSegment':'original clip already slowed 10x','uniqueFramesPerStride':9,'strideDurationMs':375,
      'sourceVideoSize':[432,748],'outputSize':[432,432],'changes':['tracking','whole-frame rotation to face right','cropping','background subtraction','1.5x spatial resize','25fps to 24fps playback'],
      'noGeneratedPoses':True,'noSyntheticLimbs':True,'limitations':'low resolution grayscale source, soft alpha edges, approximate loop seam',
      'attribution':'Mongeau et al. (2012), PLOS ONE 7(6):e38003, Video S1, via Wikimedia Commons',
      'source':'https://commons.wikimedia.org/wiki/File:Periplaneta_americana_performing_a_high-speed_inversion_on_a_ramp_(top_view)_-_Journal.pone.0038003.s001.ogv',
      'license':'https://creativecommons.org/licenses/by/2.5/'}
(root/'real-animation.json').write_text(json.dumps(meta,indent=2)+'\n')
print(json.dumps(meta))
