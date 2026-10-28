"""Validate species manifests, frame alignment and total shared memory."""
from pathlib import Path
import json
from PIL import Image
root=Path(__file__).resolve().parents[1]/'app/src/main/assets/shark'
total=0
for species,frames,duration in [('hammerhead',72,3024),('whale_shark',8,1200),('orca',8,1000)]:
    manifest=json.loads((root/species/'animations.json').read_text())
    assert manifest['swim']['frames']==frames
    assert manifest['swim']['durationMs']==duration
    for name,spec in manifest.items():
        atlas=Image.open(root/species/(name+'_atlas.webp')).convert('RGBA')
        assert atlas.size==(spec['columns']*spec['frameSize'],((spec['frames']+7)//8)*spec['frameSize'])
        assert sum(spec['frameDurationsMs'])==spec['durationMs']
        total+=atlas.width*atlas.height*4
        for i in range(spec['frames']):
            x,y=i%8*192,i//8*192
            a=atlas.crop((x,y,x+192,y+192)).getchannel('A')
            box=a.getbbox()
            assert a.getextrema()==(0,255)
            assert box and 0<box[0]<box[2]<192 and 0<box[1]<box[3]<192
    print(species,frames,'swim frames OK')
assert total<28*1024*1024
print('All-species shared ARGB memory:',total/1024**2,'MiB')
