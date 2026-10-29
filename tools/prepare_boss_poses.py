"""Pack approved transparent boss poses; squid mantle leads and arms trail."""
from pathlib import Path
import argparse
import json
import numpy as np
from PIL import Image, ImageDraw, ImageOps
from scipy import ndimage

NAMES = [('dunkleosteus',3600),('giant_grouper',4200),('giant_squid',4000)]

def pack(paths, output, preview):
    contact=Image.new('RGB',(768,280),(231,241,247))
    for index,(path,(name,duration)) in enumerate(zip(paths,NAMES)):
        image=Image.open(path).convert('RGBA')
        if name=='giant_squid': image=ImageOps.mirror(image)
        pixels=np.array(image)
        assert pixels[:,:,3].min()==0
        labels,_=ndimage.label(pixels[:,:,3]>96)
        sizes=np.bincount(labels.ravel())
        component=np.argmax(sizes[1:])+1
        region=labels==component
        assert region.sum()>50000
        ys,xs=np.nonzero(region)
        bounds=(max(0,int(xs.min())-4),max(0,int(ys.min())-4),
                min(image.width,int(xs.max())+5),min(image.height,int(ys.max())+5))
        pixels[:,:,3]=np.where(ndimage.binary_dilation(region,iterations=3),pixels[:,:,3],0)
        pose=Image.fromarray(pixels).crop(bounds)
        ratio=448/pose.width
        center=float(np.median(ys[xs>xs.max()-(xs.max()-xs.min())*.025]))
        pose=pose.resize((448,round(pose.height*ratio)),Image.Resampling.LANCZOS)
        tile=Image.new('RGBA',(512,512))
        tile.alpha_composite(pose,(32,round(256-(center-bounds[1])*ratio)))
        box=tile.getchannel('A').getbbox()
        assert box and 0<box[0]<box[2]<512 and 0<box[1]<box[3]<512
        folder=output/name; folder.mkdir(parents=True,exist_ok=True)
        tile.save(folder/'swim_atlas.webp',lossless=True,method=6)
        (folder/'animations.json').write_text(json.dumps({'swim':dict(frames=1,durationMs=duration,
            frameDurationsMs=[duration],frameSize=512,columns=1,animation='boss_continuous_mesh',
            appearance='realistic_color_v1')},indent=2)+'\n')
        thumb=tile.resize((256,256),Image.Resampling.LANCZOS)
        contact.paste(thumb,(index*256,0),thumb)
        ImageDraw.Draw(contact).text((index*256+16,256),name,fill=(15,42,64))
        print(name,box,(folder/'swim_atlas.webp').stat().st_size,'bytes')
    preview.parent.mkdir(parents=True,exist_ok=True); contact.save(preview)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('poses',nargs=3,type=Path)
    p.add_argument('--output',type=Path,default=Path(__file__).resolve().parents[1]/'app/src/main/assets/shark')
    p.add_argument('--preview',type=Path,default=Path('/tmp/boss-poses.png'))
    a=p.parse_args(); pack(a.poses,a.output,a.preview)
