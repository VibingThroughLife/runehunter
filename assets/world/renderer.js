import * as THREE from '../vendor/three.module.js';
import { createWorld } from './scene.js';

const clamp = (n, a = 0, b = 1) => Math.min(b, Math.max(a, n));
const smooth = (a, b, value) => { const t = clamp((value - a) / (b - a)); return t * t * (3 - 2 * t); };

/** Render authored scene poses. Scroll and encounter state are owned by the controller. */
export function createIntroRenderer(canvas, { onLost, onRestored } = {}) {
  let renderer;
  try {
    const context = canvas.getContext('webgl2', { alpha: false, antialias: true, powerPreference: 'low-power' });
    if (!context) return null;
    renderer = new THREE.WebGLRenderer({ canvas, context, alpha: false, antialias: true });
  } catch { return null; }
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.03;
  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0xb5c8c7);
  scene.fog = new THREE.Fog(0xb5c8c7, 115, 225);
  scene.add(new THREE.HemisphereLight(0xf2f4e4, 0x506446, 1.45));
  const sun = new THREE.DirectionalLight(0xffefce, 2.0);
  sun.position.set(-38, 64, 42); scene.add(sun);
  const world = createWorld(); scene.add(world.group);
  const camera = new THREE.PerspectiveCamera(38, 1, .1, 280);
  const orb = new THREE.Group();
  const orbMaterial = new THREE.MeshStandardMaterial({ color: 0xf0c97f, roughness: .38, metalness: .32, emissive: 0x6e3a0b, emissiveIntensity: .18 });
  orb.add(new THREE.Mesh(new THREE.IcosahedronGeometry(.20, 2), orbMaterial));
  const band = new THREE.Mesh(new THREE.TorusGeometry(.203, .027, 4, 16), new THREE.MeshLambertMaterial({color:0xffe9ae})); orb.add(band);
  orb.visible = false; scene.add(orb);
  const halo = new THREE.Mesh(new THREE.RingGeometry(.39,.43,32), new THREE.MeshBasicMaterial({color:0xf2d996,transparent:true,opacity:.65,depthWrite:false,side:THREE.DoubleSide}));
  halo.rotation.x = -Math.PI / 2; halo.visible = false; scene.add(halo);
  const sparkles = new THREE.InstancedMesh(new THREE.IcosahedronGeometry(.055,0),new THREE.MeshBasicMaterial({color:0xffe9b5,transparent:true,opacity:1}),16);
  sparkles.frustumCulled=false; sparkles.visible=false; scene.add(sparkles);
  const dummy=new THREE.Object3D(), hand=new THREE.Vector3(), release=new THREE.Vector3(), target=new THREE.Vector3();
  let width=1,height=1,low=innerWidth<760,lost=false,frames=0,lastNow=null,frameTimes=[],slowWindows=0;
  const mobile=()=>width<760;
  function resize() {
    const rect=canvas.parentElement.getBoundingClientRect();
    width=Math.max(1,Math.round(rect.width));height=Math.max(1,Math.round(rect.height));
    renderer.setPixelRatio(Math.min(devicePixelRatio||1,low?1:1.5));renderer.setSize(width,height,false);
    camera.aspect=width/height;camera.updateProjectionMatrix();world.setQuality?.(low);
  }
  function place(actor, pose, scale=1) {
    actor.group.visible=true; actor.group.position.set(pose.x,0,pose.z);actor.group.rotation.y=pose.rotation;
    actor.group.scale.setScalar((actor.baseScale??1)*scale);
  }
  function handPosition(actor, out) {
    actor.group.updateMatrixWorld(true);
    if(actor.handWorldPosition) return actor.handWorldPosition(out);
    return out.set(.43,.84,.11).applyMatrix4(actor.group.matrixWorld);
  }
  function render(frame, encounter, {time=0,paused=false,now=performance.now()}={}) {
    if(lost) return;
    world.animate(time);
    const player=world.actors.player, dharok=world.actors.dharok;
    for(const [id,actor] of Object.entries(world.actors)) if(id!=='player'&&id!=='dharok') actor.group.visible=false;
    const p=encounter.phase==='throwing'?clamp(encounter.elapsed/3):encounter.phase==='caught'?1:0;
    const throwing=encounter.phase==='throwing'&&frame.chapter==='encounter';
    const playerPose={time,walkPhase:frame.player.walkPhase,walkWeight:frame.player.walkWeight,throwProgress:throwing?p:null,noticed:frame.chapter==='encounter'};
    place(player,frame.player);player.pose?.(playerPose);
    const shrink=throwing?1-smooth(.53,.70,p):1;
    place(dharok,frame.dharok,Math.max(.001,shrink));
    dharok.group.visible=encounter.phase!=='caught'&&p<.70;
    dharok.pose?.({time,walkPhase:0,walkWeight:0,throwProgress:throwing?p:null,peek:frame.dharok.peek,noticed:frame.dharok.noticed,caught:encounter.phase==='caught'});
    orb.visible=throwing||(encounter.phase==='caught'&&frame.chapter==='encounter');
    halo.visible=orb.visible&&p>.68;
    if(orb.visible){
      target.set(frame.dharok.x,.25,frame.dharok.z);
      if(throwing&&p<.18){handPosition(player,hand);orb.position.copy(hand);}
      else if(throwing&&p<.57){
        player.pose?.({...playerPose,throwProgress:.18});handPosition(player,release);player.pose?.(playerPose);
        const t=clamp((p-.18)/.39);target.y=.9;orb.position.lerpVectors(release,target,t);orb.position.y+=Math.sin(t*Math.PI)*2.05;target.y=.25;
      } else {orb.position.copy(target);orb.position.y+=throwing?(1-smooth(.57,.70,p))*.65+Math.sin(smooth(.70,.77,p)*Math.PI)*.14:0;}
      orb.rotation.set(0,p*7,throwing&&p>.70?Math.sin((p-.70)*Math.PI*20)*Math.max(0,1-(p-.70)/.30)*.30:0);
      orb.scale.setScalar(throwing&&p<.57?1:1.13);
      halo.position.set(target.x,.045,target.z);halo.scale.setScalar(1+(throwing?smooth(.72,1,p)*.8:0));halo.material.opacity=encounter.phase==='caught'?.45:.7*(1-smooth(.80,1,p));
    }
    sparkles.visible=throwing&&!paused&&p>=.76&&p<1;
    if(sparkles.visible){
      const q=(p-.76)/.24;
      for(let i=0;i<16;i++){const a=i*2.39996,r=(.3+(i%4)*.14)*q;dummy.position.set(target.x+Math.cos(a)*r,.35+q*.9+Math.sin(i*3)*q*.3,target.z+Math.sin(a)*r);dummy.scale.setScalar(1-q*.8);dummy.rotation.set(q*4,i,q*2);dummy.updateMatrix();sparkles.setMatrixAt(i,dummy.matrix);}
      sparkles.material.opacity=1-q;sparkles.instanceMatrix.needsUpdate=true;
    }
    camera.clearViewOffset();camera.position.fromArray(frame.camera.position);camera.lookAt(...frame.camera.target);
    if(!mobile()){
      const shift=-.16*(1-smooth(.15,.29,frame.progress))+.15*smooth(.56,.65,frame.progress);
      if(shift)camera.setViewOffset(width,height,width*shift,0,width,height);
    }else{
      const shift=-.13*(1-smooth(.15,.30,frame.progress))+.18*smooth(.55,.65,frame.progress);
      camera.setViewOffset(width,height,0,height*shift,width,height);
    }
    camera.updateProjectionMatrix();camera.updateMatrixWorld();renderer.render(scene,camera);frames++;
    if(!paused&&lastNow!==null){const duration=now-lastNow;if(duration>0&&duration<250){frameTimes.push(duration);if(frameTimes.length>1800)frameTimes.shift();}}
    lastNow=paused?null:now;
    if(!low&&frames%120===0&&frameTimes.length>=120){const recent=frameTimes.slice(-120),mean=recent.reduce((a,b)=>a+b,0)/recent.length;slowWindows=mean>34?slowWindows+1:0;if(slowWindows>=2){low=true;resize();}}
  }
  function handleLost(event){event.preventDefault();lost=true;lastNow=null;onLost?.();}
  function handleRestored(){lost=false;lastNow=null;resize();onRestored?.();}
  canvas.addEventListener('webglcontextlost',handleLost);canvas.addEventListener('webglcontextrestored',handleRestored);
  resize();
  return {render,resize,stats:()=>({mode:lost?'fallback':'webgl',contextLost:lost,frames,triangles:renderer.info.render.triangles,drawCalls:renderer.info.render.calls,quality:low?'low':'high',frameTimes:[...frameTimes]}),
    projectPoint(x,y,z){const rect=canvas.getBoundingClientRect(),v=new THREE.Vector3(x,y,z).project(camera);return{x:rect.left+(v.x+1)*rect.width/2,y:rect.top+(1-v.y)*rect.height/2,visible:v.z>-1&&v.z<1&&Math.abs(v.x)<1&&Math.abs(v.y)<1};},
    dispose(){canvas.removeEventListener('webglcontextlost',handleLost);canvas.removeEventListener('webglcontextrestored',handleRestored);world.dispose();orb.traverse(o=>{o.geometry?.dispose();o.material?.dispose();});halo.geometry.dispose();halo.material.dispose();sparkles.geometry.dispose();sparkles.material.dispose();renderer.dispose();}
  };
}
