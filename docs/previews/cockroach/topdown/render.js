const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { createCanvas, loadImage } = require('@napi-rs/canvas');
const root = __dirname;
const textures = Object.fromEntries(['body','femur','tibia','tarsus'].map(n=>[n,'data:image/png;base64,'+fs.readFileSync(path.join(root,'assets',n+'.png')).toString('base64')]));
const html = `<!doctype html><meta charset="utf-8"><style>html,body{margin:0;background:transparent}canvas{display:block}</style><canvas id="c"></canvas><script>
const sources=${JSON.stringify(textures)};
const images={};
window.ready=Promise.all(Object.entries(sources).map(async([name,url])=>{const im=new Image();im.src=url;await im.decode();images[name]=im;}));
const canvas=document.getElementById('c'),ctx=canvas.getContext('2d');
const L=240, frequency=4, duty=0.62, speed=352;
const stride=speed*duty/frequency;
const frac=x=>x-Math.floor(x);
function rotate(x,y,yaw){return {x:x*Math.cos(yaw)-y*Math.sin(yaw),y:x*Math.sin(yaw)+y*Math.cos(yaw)}}
function limbState(t,side,index,worldX=0,worldY=0){
 const p=frac(t*frequency+((side===-1?0:1)+index)%2*0.5);
 const stance=p<duty;
 let dx,z=0,lateral=0;
 if(stance) dx=stride/2-stride*p/duty;
 else{
  const u=(p-duty)/(1-duty),u2=u*u,u3=u2*u;
  const h00=2*u3-3*u2+1,h10=u3-2*u2+u,h01=-2*u3+3*u2,h11=u3-u2;
  const m=-speed*(1-duty)/frequency;
  dx=h00*(-stride/2)+h10*m+h01*(stride/2)+h11*m;
  z=Math.sin(Math.PI*u)*L*0.035;
  lateral=Math.sin(Math.PI*u)*L*0.018;
 }
 const planX=[0.48,0.02,-0.44][index]*L;
 const planY=[0.32,0.43,0.35][index]*L;
 return {stance,x:worldX+planX+dx,y:worldY+side*(planY+lateral),z,p};
}
function segment(im,a,b,widthScale,sourceA,sourceB){
 const ax=sourceA[0]*im.width,ay=sourceA[1]*im.height,bx=sourceB[0]*im.width,by=sourceB[1]*im.height;
 const sourceLength=Math.hypot(bx-ax,by-ay),destinationLength=Math.hypot(b.x-a.x,b.y-a.y);
 const sx=(bx-ax)/sourceLength,sy=(by-ay)/sourceLength,dx=(b.x-a.x)/destinationLength,dy=(b.y-a.y)/destinationLength;
 const u=destinationLength/sourceLength,v=u*widthScale;
 const m00=u*dx*sx+v*dy*sy,m01=u*dx*sy-v*dy*sx;
 const m10=u*dy*sx-v*dx*sy,m11=u*dy*sy+v*dx*sx;
 ctx.save();ctx.transform(m00,m10,m01,m11,a.x-m00*ax-m01*ay,a.y-m10*ax-m11*ay);ctx.drawImage(im,0,0);ctx.restore();
}
function leg(t,side,index,center,yaw){
 const hip=rotate([0.30,0.18,0.06][index]*L,side*[0.11,0.15,0.15][index]*L,yaw);
 hip.x+=center.x;hip.y+=center.y;
 const foot=limbState(t,side,index,center.x,300);
 const toeDirs=[[0.7,0.714],[-0.4,0.9165],[-0.91,0.4146]][index];
 const tarsus=[0.075,0.10,0.125][index]*L;
 const ankle={x:foot.x-toeDirs[0]*tarsus,y:foot.y-side*toeDirs[1]*tarsus};
 const a=Math.sqrt(Math.pow([0.14,0.17,0.20][index]*L,2)-foot.z*foot.z*0.25);
 const b=Math.sqrt(Math.pow([0.19,0.26,0.37][index]*L,2)-foot.z*foot.z*0.75);
 const vx=ankle.x-hip.x,vy=ankle.y-hip.y,dist=Math.hypot(vx,vy),d=Math.min(a+b-0.001,Math.max(Math.abs(a-b)+0.001,dist));
 const ux=vx/dist,uy=vy/dist,along=(a*a-b*b+d*d)/(2*d),h=Math.sqrt(Math.max(0,a*a-along*along));
 const k1={x:hip.x+along*ux-h*uy,y:hip.y+along*uy+h*ux};
 const k2={x:hip.x+along*ux+h*uy,y:hip.y+along*uy-h*ux};
 const knee=side*k1.y>side*k2.y?k1:k2;
 segment(images.femur,hip,knee,0.64,[0.075,0.49],[0.96,0.65]);
 segment(images.tibia,knee,ankle,0.72,[0.055,0.58],[0.97,0.63]);
 segment(images.tarsus,ankle,foot,0.64,[0.035,0.50],[0.965,0.44]);
 return {hip,knee,ankle,foot,a,b};
}
function antenna(t,side,center,yaw){
 const base=rotate(L*0.475,side*L*0.028,yaw);
 const angle=side*(0.47+0.055*Math.sin(2*Math.PI*t+side)+0.035*Math.sin(Math.PI*t+side*0.8));
 const points=[{x:0,y:0},{x:L*0.22,y:side*L*0.04},{x:L*0.68,y:Math.sin(angle)*L*0.6},{x:Math.cos(angle)*L*0.94,y:Math.sin(angle)*L*0.94}];
 ctx.save();ctx.translate(center.x+base.x,center.y+base.y);ctx.rotate(yaw);
 let previous=points[0];
 for(let i=1;i<=40;i++){
  const u=i/40,v=1-u;const p={x:3*v*v*u*points[1].x+3*v*u*u*points[2].x+u*u*u*points[3].x,y:3*v*v*u*points[1].y+3*v*u*u*points[2].y+u*u*u*points[3].y};
  ctx.beginPath();ctx.moveTo(previous.x,previous.y);ctx.lineTo(p.x,p.y);ctx.strokeStyle=i%4===0?'#4d2314':'#612d19';ctx.lineWidth=Math.max(0.35,1.7*(1-u));ctx.lineCap='round';ctx.stroke();previous=p;
 }
 ctx.restore();
}
window.renderAt=(t,mode='sprite')=>{
 const moving=mode==='moving';canvas.width=moving?1440:640;canvas.height=moving?600:480;
 if(mode!=='sprite'){
  ctx.fillStyle='#c8c4bb';ctx.fillRect(0,0,canvas.width,canvas.height);
  const offset=moving?0:((-speed*t)%32+32)%32;
  ctx.fillStyle='#b3afa6';for(let y=16;y<canvas.height;y+=32)for(let x=-16;x<canvas.width+32;x+=32){ctx.beginPath();ctx.arc(x+offset,y,0.55,0,Math.PI*2);ctx.fill()}
 }
 const worldX=moving?210+speed*t:230;
 const center={x:worldX,y:300+0.5*Math.sin(t*frequency*4*Math.PI)};
 const yaw=0.006*Math.sin(t*frequency*2*Math.PI);
 // Rendering coordinates share the same baseline for the contact model.
 ctx.save();if(!moving)ctx.translate(0,-60);
 const data=[];
 for(const side of [-1,1])for(let i=0;i<3;i++)data.push(leg(t,side,i,center,yaw));
 ctx.save();ctx.translate(center.x,center.y);ctx.rotate(yaw);
 const im=images.body;ctx.drawImage(im,-L/2,-L*im.height/im.width/2,L,L*im.height/im.width);ctx.restore();
 antenna(t,-1,center,yaw);antenna(t,1,center,yaw);
 ctx.restore();return data;
};
window.diagnostics=()=>{
 let maxSlip=0,maxBoneError=0,minStance=6,maxStance=0;
 const eps=0.00001;
 for(let i=0;i<1000;i++){
  const t=i/1000;let planted=0;
  for(const side of [-1,1])for(let leg=0;leg<3;leg++){
   const a=limbState(t,side,leg,speed*t,300),b=limbState(t+eps,side,leg,speed*(t+eps),300);
   if(a.stance){planted++;if(b.stance)maxSlip=Math.max(maxSlip,Math.hypot(b.x-a.x,b.y-a.y)/eps);}
  }
  minStance=Math.min(minStance,planted);maxStance=Math.max(maxStance,planted);
  for(const d of window.renderAt(t,'moving')){
   maxBoneError=Math.max(maxBoneError,Math.abs(Math.hypot(d.knee.x-d.hip.x,d.knee.y-d.hip.y)-d.a),Math.abs(Math.hypot(d.ankle.x-d.knee.x,d.ankle.y-d.knee.y)-d.b));
  }
 }
 return {maxSlipPxPerSecond:maxSlip,maxBoneLengthErrorPx:maxBoneError,minPlantedFeet:minStance,maxPlantedFeet:maxStance,frequencyHz:frequency};
};
</script>`;
fs.writeFileSync(path.join(root,'rig-preview.html'),html);
(async()=>{
 const canvas=createCanvas(640,480);
 const images={};for(const [name,source] of Object.entries(textures))images[name]=await loadImage(source);
 const sandbox={window:{},document:{getElementById:()=>canvas},images,console,Math};
 vm.createContext(sandbox);
 const core=html.slice(html.indexOf('const canvas=document'),html.lastIndexOf('</script>'));
 vm.runInContext(core,sandbox);
 const diagnostic=sandbox.window.diagnostics();console.log(JSON.stringify(diagnostic));
 if(diagnostic.maxSlipPxPerSecond>0.01||diagnostic.maxBoneLengthErrorPx>0.1||diagnostic.minPlantedFeet<3)throw Error('Gait validation failed');
 for(const mode of ['sprite','preview','moving','slow']){
  const output=path.join(root,mode+'_frames');fs.mkdirSync(output,{recursive:true});
  const count=mode==='slow'?96:48;
  for(let frame=0;frame<count;frame++){
   sandbox.window.renderAt(frame/(mode==='slow'?48:24),mode==='slow'?'preview':mode);
   fs.writeFileSync(path.join(output,String(frame).padStart(3,'0')+'.png'),canvas.toBuffer('image/png'));
  }
  console.log(mode+' rendered '+count+' frames');
 }
 fs.writeFileSync(path.join(root,'gait-validation.json'),JSON.stringify(diagnostic,null,2));
})().catch(e=>{console.error(e);process.exit(1)});
