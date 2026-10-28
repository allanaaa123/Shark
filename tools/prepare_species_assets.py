"""Package approved transparent concept WebPs as Android 8-compatible atlases.
Usage: python tools/prepare_species_assets.py /directory/containing/concept_webps
"""
from pathlib import Path
import json
import sys
from PIL import Image

root=Path(__file__).resolve().parents[1]/'app/src/main/assets/shark'
for name in ('whale_shark','orca'):
    original=Path(sys.argv[1])/(name+'_concept.webp')
    dest=root/name
    dest.mkdir(parents=True,exist_ok=True)
    (dest/'swim.webp').write_bytes(original.read_bytes())
    animation=Image.open(original)
    atlas=Image.new('RGBA',(192*8,192*((animation.n_frames+7)//8)))
    durations=[]
    for i in range(animation.n_frames):
        animation.seek(i)
        frame=animation.convert('RGBA')
        durations.append(animation.info['duration'])
        # Normalize body length exactly as the hammerhead atlas (170px).
        bbox=frame.getchannel('A').getbbox()
        sprite=frame.crop(bbox)
        sprite=sprite.resize((170,round(sprite.height*170/sprite.width)),Image.Resampling.LANCZOS)
        tile=Image.new('RGBA',(192,192))
        tile.alpha_composite(sprite,(11,(192-sprite.height)//2))
        atlas.alpha_composite(tile,((i%8)*192,(i//8)*192))
    atlas.save(dest/'swim_atlas.webp',lossless=True,method=6)
    metadata={'swim':dict(frames=animation.n_frames,durationMs=sum(durations),frameDurationsMs=durations,frameSize=192,columns=8)}
    (dest/'animations.json').write_text(json.dumps(metadata,indent=2)+'\n')
    print(name,animation.n_frames,'poses',sum(durations),'ms')
