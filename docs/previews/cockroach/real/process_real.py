"""Direct tracking, crop and background removal from the original real video.
No generated poses, synthetic limbs, motion interpolation or recoloring.
"""
from pathlib import Path
from PIL import Image
import numpy as np
from scipy.ndimage import gaussian_filter, grey_closing, label, binary_fill_holes, binary_opening, binary_dilation, binary_propagation
import json
root=Path(__file__).parent
(root/'cutout_frames').mkdir(exist_ok=True)
(root/'crop_frames').mkdir(exist_ok=True)
paths=sorted((root/'full_frames').glob('*.png'))
previous=np.array([335.,118.]); velocity=np.array([-4.,10.])
records=[]
for out,n in enumerate(range(28,44)):
 im=Image.open(paths[n-1]).convert('RGB'); gray=np.array(im.convert('L'),dtype=np.float32)
 smooth=gaussian_filter(gray,0.55)
 bg=grey_closing(smooth,size=(61,61))
 diff=bg-smooth
 mask=diff>18
 mask[:35]=False;mask[440:]=False;mask[:,:90]=False;mask[:,410:]=False
 lab,count=label(mask);sizes=np.bincount(lab.ravel())
 candidates=[]
 for k in range(1,count+1):
  if 600<sizes[k]<13000:
   yy,xx=np.where(lab==k);c=np.array([xx.mean(),yy.mean()]);candidates.append((np.linalg.norm(c-previous-velocity),k,c))
 assert candidates,(n,'body not found')
 _,key,center=min(candidates,key=lambda p:p[0])
 body=binary_fill_holes(lab==key)
 core=binary_opening(body,structure=np.ones((7,7)))
 if core.sum()<200:core=body
 yy,xx=np.where(core);center=np.array([xx.mean(),yy.mean()])
 if out:velocity=0.65*velocity+0.35*(center-previous)
 previous=center
 cov=np.cov(np.stack((xx,yy)));e,v=np.linalg.eigh(cov);axis=v[:,-1]
 if axis[1]<0:axis=-axis
 heading=np.degrees(np.arctan2(axis[1],axis[0]))
 yy,xx=np.indices(gray.shape)
 local=(xx-center[0])**2+(yy-center[1])**2<145**2
 allowed=binary_dilation((diff>7)&local,iterations=1)
 allowed[:35]=False;allowed[423:]=False;allowed[:,:70]=False;allowed[:,395:]=False
 grown=binary_propagation(body,mask=allowed)
 alpha=np.clip((diff-6)/15,0,1)*grown
 alpha[body]=1
 # Decontaminate the original floor color at partially covered edge pixels.
 rgb=np.array(im,dtype=float)
 a=np.clip(alpha,0.001,1)
 foreground=(rgb-(1-a[:,:,None])*bg[:,:,None])/a[:,:,None]
 foreground=np.clip(foreground,0,255).astype('uint8')
 rgba=np.dstack((foreground,(alpha*255).astype('uint8')))
 frame=Image.fromarray(rgba,'RGBA')
 crop=(round(center[0])-144,round(center[1])-144,round(center[0])+144,round(center[1])+144)
 frame=frame.crop(crop).rotate(float(heading),resample=Image.Resampling.BICUBIC).resize((432,432),Image.Resampling.LANCZOS)
 frame.save(root/'cutout_frames'/f'{out:03d}.png')
 # Preserve actual surrounding source pixels for an unambiguous motion check.
 raw=im.crop(crop).rotate(float(heading),resample=Image.Resampling.BICUBIC,fillcolor=(110,110,110)).resize((432,432),Image.Resampling.LANCZOS)
 raw.save(root/'crop_frames'/f'{out:03d}.png')
 records.append({'sourceFrame':n,'sourceTimeSeconds':(n-1)/25,'center':center.tolist(),'heading':float(heading)})
print(json.dumps(records[::6]))
(root/'frame-tracking.json').write_text(json.dumps(records,indent=2)+'\n')
