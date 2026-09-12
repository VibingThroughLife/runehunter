import { evaluateIntro, createIntroState } from './intro.js';
import { createIntroRenderer } from './renderer.js';

const $=id=>document.getElementById(id);
const section=$('intro'),sticky=$('intro-sticky'),stage=$('world-stage'),canvas=$('world-canvas');
const encounter=createIntroState(),motion=matchMedia('(prefers-reduced-motion: reduce)');
const panels={arrival:$('intro-arrival'),journey:$('intro-journey'),encounter:$('intro-encounter')};
let reducedMotion=motion.matches,paused=motion.matches,graphics=null,contextLost=false,initializationError=null;
let progress=0,rawProgress=0,visible=true,sceneFrame=evaluateIntro(0),pending=0,dirty=true,lastNow=null,time=0,uiStamp='';
let wasEncounter=false;
document.body.classList.add('intro-live');
function requestFrame(){dirty=true;if(!pending)pending=requestAnimationFrame(frame);}
function measure(){
  const rect=section.getBoundingClientRect(),range=Math.max(1,section.offsetHeight-sticky.offsetHeight);
  rawProgress=-rect.top/range;progress=Math.min(1,Math.max(0,rawProgress));
  visible=rect.top<innerHeight&&rect.bottom>0&&rawProgress<=1.001;
  const inEncounter=progress>=.65&&rawProgress<=1.001&&visible;
  if(wasEncounter&&!inEncounter)encounter.leaveEncounter();
  wasEncounter=inEncounter;
  sceneFrame=evaluateIntro(progress,{mobile:innerWidth<760,reducedMotion,caught:encounter.state.phase==='caught'});
  syncUI();
}
function syncUI(){
  const chapter=sceneFrame.chapter,phase=encounter.state.phase,stamp=[chapter,phase,paused,reducedMotion,contextLost,Boolean(graphics)].join('|');
  section.style.setProperty('--intro-progress',`${progress*100}%`);
  if(stamp===uiStamp)return;uiStamp=stamp;
  sticky.dataset.chapter=chapter;
  for(const [name,panel]of Object.entries(panels)){panel.hidden=name!==chapter;panel.inert=name!==chapter;}
  $('intro-chapter').textContent=chapter==='arrival'?'01 / Lumbridge':chapter==='journey'?'02 / Around the castle':'03 / A small discovery';
  const caught=phase==='caught',throwing=phase==='throwing',button=$('intro-catch');
  const transferFocus=caught&&document.activeElement===button;
  button.hidden=caught;button.disabled=throwing;
  button.innerHTML=throwing?'A little hope…':'<span class="orb" aria-hidden="true"></span> Throw an orb';
  $('intro-replay').hidden=!caught;
  $('intro-catch-title').textContent=caught?'Mini Dharok caught':'Wait. Is that Dharok?';
  $('intro-catch-copy').innerHTML=caught?'Your first small discovery.<br>A whole world of them awaits.':'All of the axe. Less of the height.<br>He looks like he could use a friend.';
  $('intro-status').textContent=caught?'Added to your demo collection.':throwing?'One orb. One very small legend.':'';
  if(transferFocus)$('intro-replay').focus({preventScroll:true});
  $('motion-toggle').textContent=paused?'Resume motion':'Pause motion';$('motion-toggle').setAttribute('aria-pressed',String(paused));
  document.body.classList.toggle('motion-paused',paused);
  const fallback=!graphics||contextLost;stage.classList.toggle('ready',!fallback);
  $('intro-graphics-status').hidden=!fallback;
  $('intro-graphics-status').textContent=chapter==='encounter'?'Scene still · the catch works in this view too.':'Scene still · scroll to discover mini Dharok.';
}
function fallback(lost){contextLost=lost;if(lost)encounter.leaveEncounter();uiStamp='';measure();requestFrame();}
try{graphics=createIntroRenderer(canvas,{onLost:()=>fallback(true),onRestored:()=>fallback(false)});}
catch(error){initializationError=error.message;}
function frame(now){
  pending=0;const dt=lastNow===null?0:Math.min(.05,Math.max(0,(now-lastNow)/1000));lastNow=now;
  measure();
  if(document.hidden||!visible){lastNow=null;return;}
  const animated=!paused&&!reducedMotion&&Boolean(graphics)&&!contextLost;
  if(animated){time+=dt;encounter.update(dt);}
  sceneFrame=evaluateIntro(progress,{mobile:innerWidth<760,reducedMotion,caught:encounter.state.phase==='caught'});
  syncUI();
  if(graphics&&!contextLost&&(dirty||animated))graphics.render(sceneFrame,encounter.state,{time,paused:!animated,now});
  dirty=false;
  if(animated)pending=requestAnimationFrame(frame);
}
$('intro-catch').addEventListener('click',()=>{
  if(sceneFrame.chapter!=='encounter')return;
  encounter.throwOrb({discrete:paused||reducedMotion||!graphics||contextLost});uiStamp='';requestFrame();syncUI();
});
$('intro-replay').addEventListener('click',()=>{encounter.replay();uiStamp='';syncUI();$('intro-catch').focus({preventScroll:true});requestFrame();});
$('motion-toggle').addEventListener('click',()=>{
  // An explicit Resume can opt into motion after the initial OS preference.
  if(paused&&reducedMotion)reducedMotion=false;
  paused=!paused;lastNow=null;uiStamp='';measure();requestFrame();
});
motion.addEventListener('change',event=>{reducedMotion=event.matches;paused=event.matches;if(reducedMotion)encounter.leaveEncounter();lastNow=null;uiStamp='';measure();requestFrame();});
addEventListener('scroll',()=>{measure();requestFrame();},{passive:true});
addEventListener('resize',()=>{graphics?.resize();measure();lastNow=null;requestFrame();},{passive:true});
addEventListener('pageshow',()=>{measure();lastNow=null;requestFrame();});
document.addEventListener('visibilitychange',()=>{lastNow=null;if(!document.hidden)requestFrame();});
// Native fragment links retain their normal navigation and keyboard semantics.
new ResizeObserver(()=>{graphics?.resize();measure();requestFrame();}).observe(sticky);
measure();requestFrame();
if(new URLSearchParams(location.search).has('debug')){
  window.__runehunterIntro={getState:()=>({progress,rawProgress,chapter:sceneFrame.chapter,...encounter.state,paused,reducedMotion,visible,time,player:{...sceneFrame.player},camera:structuredClone(sceneFrame.camera),renderer:{...(graphics?.stats()??{mode:'fallback',frames:0,triangles:0,drawCalls:0,frameTimes:[],quality:'none'}),contextLost,initializationError}}),projectPoint:(x,y,z)=>graphics?.projectPoint(x,y,z)};
}
