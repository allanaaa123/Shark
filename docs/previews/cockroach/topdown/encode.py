from pathlib import Path
from PIL import Image
import json,subprocess
root=Path(__file__).parent
for mode,name in [('sprite','topdown-crawl'),('preview','topdown-preview'),('slow','topdown-slow')]:
    frames=[Image.open(p).convert('RGBA') for p in sorted((root/(mode+'_frames')).glob('*.png'))]
    assert len(frames)==(96 if mode=='slow' else 48)
    if mode=='sprite':
        assert len({f.tobytes() for f in frames})==48
        for f in frames:
            a=f.getchannel('A'); b=a.getbbox()
            assert a.getextrema()[0]==0 and b and 0<b[0]<b[2]<640 and 0<b[1]<b[3]<480
        frames[0].save(root/(name+'.webp'),save_all=True,append_images=frames[1:],duration=[42,42,41]*16,loop=0,lossless=True,method=6)
    else:
        frames=[f.convert('RGB') for f in frames]
        frames[0].save(root/(name+'.gif'),save_all=True,append_images=frames[1:],duration=[50 if i%6==5 else 40 for i in range(len(frames))],loop=0)
for mode,name in [('preview','topdown-crawl'),('moving','topdown-forward'),('slow','topdown-slow')]:
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-framerate','24','-i',str(root/(mode+'_frames')/'%03d.png'),'-c:v','libx264','-crf','18','-pix_fmt','yuv420p','-movflags','+faststart',str(root/(name+'.mp4'))],check=True)
meta={'fps':24,'frames':48,'durationMs':2000,'view':'orthographic_top_down','method':'textured_joint_rig_with_inverse_kinematics','sourceMotion':'qualitative reference to Mongeau et al. 2012 top-view recording; not motion capture','gait':json.loads((root/'gait-validation.json').read_text())}
(root/'animation.json').write_text(json.dumps(meta,indent=2)+'\n')
print(json.dumps(meta))
