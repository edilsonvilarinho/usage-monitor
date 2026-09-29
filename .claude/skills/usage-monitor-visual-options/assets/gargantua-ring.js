/*
 * Biblioteca do anel Gargantua para protótipos HTML (skill usage-monitor-visual-options).
 * Mesmo desenho do app (AppGargantuaRing / GargantuaDrawing.kt), em Canvas 2D:
 *   drawRing(ctx, {arcs:[{f:0..1, t:'ok'|'warn'|'crit'}], mark:'claude'|'openai'}, phase, overrides)
 *   ctx já transladado por PAD; o anel ocupa S×S (64 px = anel do app a 100%).
 * Efeitos: flashAt, ringAt (onda), rrPath (contorno do balão), perimeter (pontos do contorno).
 * Easing: seg(t,a,b), easeOut, easeIn, easeInOut. Nada aqui tem rebote: dado não passa do lugar.
 */
const TONE={ok:'#4CAF50',warn:'#FFA726',crit:'#EF5350'}, INFO='#42A5F5';
const S=64, STROKE=2.5, GAP=1.5, PAD=28, W=S+PAD*2;
const LIGHT=-135*Math.PI/180;
const clamp=(v,a=0,b=1)=>Math.max(a,Math.min(b,v));
const seg=(t,a,b)=>clamp((t-a)/(b-a));
const easeOut=t=>1-Math.pow(1-t,3), easeIn=t=>t*t*t, easeInOut=t=>t<.5?4*t*t*t:1-Math.pow(-2*t+2,3)/2;
function hex(c){return [1,3,5].map(i=>parseInt(c.slice(i,i+2),16))}
function rgba(c,a){const [r,g,b]=hex(c);return `rgba(${r},${g},${b},${a})`}
function mix(c,w,k){const a=hex(c),b=hex(w);return '#'+a.map((v,i)=>Math.round(v+(b[i]-v)*k).toString(16).padStart(2,'0')).join('')}
function drawCore(ctx,room,ph,o){
  const c=S/2,h=room*.62*o.core;
  ctx.globalAlpha=o.disk;
  let g=ctx.createRadialGradient(c,c,Math.max(0,h*.9),c,c,room);
  g.addColorStop(0,'rgba(232,174,99,.16)');g.addColorStop(1,'rgba(232,174,99,0)');
  ctx.fillStyle=g;ctx.beginPath();ctx.arc(c,c,room,0,7);ctx.fill();
  ctx.lineWidth=room*.05;ctx.strokeStyle='rgba(255,230,190,.35)';ctx.beginPath();ctx.arc(c,c,h+room*.1,Math.PI*1.08,Math.PI*1.92);ctx.stroke();
  ctx.lineWidth=room*.025;ctx.strokeStyle='rgba(255,230,190,.18)';ctx.beginPath();ctx.arc(c,c,h+room*.08,Math.PI*.15,Math.PI*.85);ctx.stroke();
  const disk=front=>{ctx.save();ctx.translate(c,c+room*.08);ctx.rotate(-12*Math.PI/180);
    const rx=room*.95*o.diskScale,ry=rx*.16;const lg=ctx.createLinearGradient(-rx,0,rx,0);
    lg.addColorStop(0,'rgba(180,80,30,.25)');lg.addColorStop(.5,'rgba(232,174,99,.55)');lg.addColorStop(1,'rgba(255,240,203,.8)');
    ctx.strokeStyle=lg;ctx.lineWidth=room*.07;ctx.beginPath();ctx.ellipse(0,0,rx,ry,0,front?0:Math.PI,front?Math.PI:Math.PI*2);ctx.stroke();
    for(let k=0;k<3;k++){const a=((ph*360*(1+k%2))+k*120)%360;if(front!==(a<180))continue;const r=a*Math.PI/180,e=Math.abs(Math.sin(r));
      ctx.strokeStyle=`rgba(255,248,230,${.45*e})`;ctx.lineWidth=room*.035;ctx.lineCap='round';ctx.beginPath();ctx.ellipse(0,0,rx,ry,0,r,r+Math.min(.35,(front?Math.PI:Math.PI*2)-r));ctx.stroke();}
    ctx.restore();};
  disk(false);ctx.globalAlpha=1;
  ctx.fillStyle='#030508';ctx.beginPath();ctx.arc(c,c,h,0,7);ctx.fill();
  ctx.globalAlpha=o.disk;ctx.strokeStyle='rgba(255,240,203,.8)';ctx.lineWidth=room*.022;ctx.beginPath();ctx.arc(c,c,h+room*.01,0,7);ctx.stroke();
  disk(true);ctx.globalAlpha=1;
}
function glassArc(ctx,r,tone,f,ph,o){
  const c=S/2,a0=-Math.PI/2,end=a0+f*Math.PI*2,step=Math.PI/60;
  ctx.globalAlpha=o.glass;ctx.lineCap='butt';
  ctx.strokeStyle='rgba(255,255,255,.06)';ctx.lineWidth=STROKE*1.4;ctx.beginPath();ctx.arc(c,c,r,0,7);ctx.stroke();
  for(let a=0;a<Math.PI*2;a+=step){const lit=.5+.5*Math.cos(a-LIGHT);
    ctx.strokeStyle=`rgba(255,255,255,${.28*lit*lit*lit})`;ctx.lineWidth=.45;ctx.beginPath();ctx.arc(c,c,r-STROKE*.5,a,a+step+.01);ctx.stroke();
    ctx.strokeStyle=`rgba(255,255,255,${.12*lit})`;ctx.lineWidth=.35;ctx.beginPath();ctx.arc(c,c,r+STROKE*.5,a,a+step+.01);ctx.stroke();}
  ctx.globalAlpha=1;
  if(f<=.001)return;
  ctx.lineCap='round';
  ctx.strokeStyle=rgba(tone,.25);ctx.lineWidth=STROKE*2.4;ctx.beginPath();ctx.arc(c,c,r,a0,end);ctx.stroke();
  ctx.strokeStyle=tone;ctx.lineWidth=STROKE*.9;ctx.beginPath();ctx.arc(c,c,r,a0,end);ctx.stroke();
  ctx.strokeStyle=mix(tone,'#ffffff',.6);ctx.lineWidth=STROKE*.35;ctx.beginPath();ctx.arc(c,c,r,a0,end);ctx.stroke();
  for(let k=0;k<3;k++){const p=(ph+k/3)%1,fade=Math.sin(p*Math.PI);if(fade<.02)continue;
    const x=c+r*Math.cos(a0+p*f*Math.PI*2),y=c+r*Math.sin(a0+p*f*Math.PI*2);const g=ctx.createRadialGradient(x,y,0,x,y,2.4);
    g.addColorStop(0,`rgba(255,255,255,${.9*fade})`);g.addColorStop(1,'rgba(255,255,255,0)');ctx.fillStyle=g;ctx.beginPath();ctx.arc(x,y,2.4,0,7);ctx.fill();}
}
function mark(ctx,kind,alpha,scale){
  const c=S/2;ctx.save();ctx.globalAlpha=alpha;ctx.translate(c,c);ctx.scale(scale,scale);ctx.strokeStyle='#F2EDED';ctx.fillStyle='#F2EDED';
  if(kind==='claude'){ctx.lineWidth=1.6;ctx.lineCap='round';for(let i=0;i<12;i++){const a=i*Math.PI/6+.13;const l=i%2?6.5:8.5;ctx.beginPath();ctx.moveTo(Math.cos(a)*1.5,Math.sin(a)*1.5);ctx.lineTo(Math.cos(a)*l,Math.sin(a)*l);ctx.stroke();}}
  else{ctx.lineWidth=1.3;for(let i=0;i<6;i++){ctx.save();ctx.rotate(i*Math.PI/3);ctx.beginPath();ctx.ellipse(0,-3.2,2.6,5.4,0,0,Math.PI*2);ctx.stroke();ctx.restore();}}
  ctx.restore();
}
const BASE={scale:1,core:1,disk:1,diskScale:1,glass:1,arcs:1,markAlpha:1,markScale:1,rot:0,stretch:1,alpha:1};
function drawRing(ctx,acc,ph,o){
  o={...BASE,...o};
  const room=S/2-acc.arcs.length*(STROKE+GAP);
  ctx.save();ctx.globalAlpha=o.alpha;
  drawCore(ctx,room,ph/5,o);
  acc.arcs.forEach((a,i)=>glassArc(ctx,S/2-STROKE/2-i*(STROKE+GAP),TONE[a.t],a.f*o.arcs,ph,o));
  mark(ctx,acc.mark,o.markAlpha,o.markScale);
  ctx.restore();ctx.globalAlpha=1;
}
const ACC=[{arcs:[{f:.06,t:'ok'},{f:.44,t:'crit'}],mark:'claude'},{arcs:[{f:.25,t:'ok'},{f:0,t:'ok'}],mark:'openai'}];

/* ---------- efeitos de cena (coordenadas do palco) ---------- */
function flashAt(x,cx,cy,a,r){if(a<=0)return;const g=x.createRadialGradient(cx,cy,0,cx,cy,r);g.addColorStop(0,`rgba(255,255,255,${a})`);g.addColorStop(.3,`rgba(255,240,203,${a*.7})`);g.addColorStop(1,'rgba(232,174,99,0)');x.fillStyle=g;x.beginPath();x.arc(cx,cy,r,0,7);x.fill();}
function ringAt(x,cx,cy,r,color,alpha,width){if(alpha<=0||r<=0)return;x.strokeStyle=rgba(color,alpha);x.lineWidth=width;x.beginPath();x.arc(cx,cy,r,0,7);x.stroke();}
function rrPath(x,r){const k=10;x.beginPath();x.roundRect(r.x,r.y,r.w,r.h,k);}
function perimeter(r,N){ // contorno arredondado em sentido horário, começando no ponto da cauda (borda direita)
  const k=10,pts=[];const L=2*(r.w+r.h-4*k)+2*Math.PI*k;
  const segs=[[r.x+k,r.y,r.x+r.w-k,r.y],['c',r.x+r.w-k,r.y+k,-Math.PI/2,0],[r.x+r.w,r.y+k,r.x+r.w,r.y+r.h-k],['c',r.x+r.w-k,r.y+r.h-k,0,Math.PI/2],
    [r.x+r.w-k,r.y+r.h,r.x+k,r.y+r.h],['c',r.x+k,r.y+r.h-k,Math.PI/2,Math.PI],[r.x,r.y+r.h-k,r.x,r.y+k],['c',r.x+k,r.y+k,Math.PI,Math.PI*1.5]];
  for(let i=0;i<N;i++){let d=i/N*L;for(const s of segs){const len=s[0]==='c'?Math.PI/2*k:Math.hypot(s[2]-s[0],s[3]-s[1]);
      if(d<=len){if(s[0]==='c'){const a=s[3]+(d/len)*(s[4]-s[3]);pts.push([s[1]+Math.cos(a)*k,s[2]+Math.sin(a)*k]);}else{const u=d/len;pts.push([s[0]+(s[2]-s[0])*u,s[1]+(s[3]-s[1])*u]);}break;}d-=len;}}
  return pts;
}

