import fs from 'node:fs';
import { pathToFileURL } from 'node:url';
import { Archive, parsePlist } from './asset-safety.mjs';

export const ORIGINAL_APK_SHA256='062e16b02595cc577527cd0f15c0a9f4d6cb36b6575bedf7cd226e5e0ab28b48';
const tuple=s=>typeof s==='string'&&(s.match(/-?\d+(?:\.\d+)?/g)??[]).map(Number);
const pair=s=>{const n=tuple(s);return n?.length===2?n:null;};
const object=o=>o&&typeof o==='object'&&!Array.isArray(o);
const round=n=>Math.round(n*10000)/10000;

// This parses data expressions only. It never interprets original class names or actions.
export function parseAlignment(s){
  if(typeof s!=='string')return null;
  const m=/^\{\s*([^,{}]+),\s*([^,{}]+),\s*([^,{}]+),\s*([^,{}]+),\s*\{([^{}]*)\}\s*\}$/.exec(s);
  if(!m)return {source:s,resolved:false};
  const axis=(v,vertical=false)=>{v=v.trim().toLowerCase();if(/^-?\d+(?:\.\d+)?%$/.test(v))return parseFloat(v)/100;return (vertical?{bot:0,bottom:0,mid:.5,center:.5,top:1}:{left:0,center:.5,mid:.5,right:1})[v]??null;};
  return {source:s,reference:m[1].trim(),horizontal:axis(m[2]),vertical:axis(m[3],true),side:m[4].trim().toLowerCase(),insets:tuple(m[5])??[],resolved:false};
}

export function mapOriginalPoint(point,{width=1280,height=720}={}){
  const scale=Math.min(width/480,height/320);
  return [round((width-480*scale)/2+point[0]*scale),round((height-320*scale)/2+(320-point[1])*scale)];
}

export function compileOriginalLayout(archive){
  const sources=new Set();
  const read=name=>{sources.add(`assets/${name}.plist`);return parsePlist(archive.read(`assets/${name}.plist`).toString('utf8'));};
  const main=read('MainMenuStack'),hud=read('HUDS'),production=read('ProductionDock'),selection=read('UnitSelectionDock'),units=read('Units'),gem=read('LWGemCounter'),special=read('HudActionSpecial');
  const atlasNames=['mainmenu-background','mainmenu-foreground','hud-spritesheet','unit-selection-spritesheet'];
  const frameIndex=new Map();
  for(const atlas of atlasNames){const parsed=read(atlas);for(const [name,f]of Object.entries(parsed.frames??{}))frameIndex.set(name,{atlas,sourceSize:pair(f.sourceSize),sourceColorRect:tuple(f.sourceColorRect),rotated:f.rotated===true});}
  const describe=(node,path)=>{
    const sprite=node.sprite??node, frame=sprite.spriteFrameName;
    const record={path};
    if(typeof frame==='string')record.frame=frame;
    if(frame){const metadata=frameIndex.get(frame);record.frameAvailable=!!metadata;if(metadata)Object.assign(record,metadata);}
    for(const [key,target]of [['position','position'],['position-hd','positionHd'],['position-ipad','positionIpad'],['anchorPoint','anchor'],['contentSize','contentSize']]){const val=pair(node[key]);if(val)record[target]=val;}
    for(const key of ['alignOptions','align'])if(node[key])record.alignment=parseAlignment(node[key]);
    if(node['alignOptions-ipad']??node['align-ipad'])record.alignmentIpad=parseAlignment(node['alignOptions-ipad']??node['align-ipad']);
    for(const key of ['scale','scaleX','scaleY','opacity'])if(typeof node[key]==='number')record[key]=node[key];
    const z=Number(node.z??node.zOrder??0);record.z=Number.isFinite(z)?z:0;
    if(typeof node.string==='string')record.label=node.string;
    return record;
  };
  const visuals=(root,path)=>{
    const nodes=[];
    function visit(o,p,parent){
      if(!object(o))return;
      let activeParent=parent;
      if(o.spriteFrameName||o.sprite?.spriteFrameName||typeof o.string==='string'){
        const d=describe(o,p);if(parent)d.parent=parent;nodes.push(d);activeParent=p;
      }
      if(o.children)for(const [key,v]of Object.entries(o.children))visit(v,`${p}.children.${key}`,activeParent);
      // Menu wrappers can be arbitrarily deep, but only the normal state is drawn.
      if(o.menuItem)visit(o.menuItem,`${p}.menuItem`,activeParent);
      if(o.normal)visit(o.normal,`${p}.normal`,activeParent);
    }
    visit(root,path,null);return nodes;
  };
  const firstFrame=(o,state='normal')=>{
    if(!object(o))return null;
    if(o[state]?.spriteFrameName)return o[state].spriteFrameName;
    if(o.spriteFrameName)return o.spriteFrameName;
    if(o.menuItem)return firstFrame(o.menuItem,state);
    return null;
  };
  const control=(o,path)=>{
    const out=describe(o,path);out.normalFrame=firstFrame(o);out.selectedFrame=firstFrame(o,'selected');
    const selectedMetadata=frameIndex.get(out.selectedFrame),normalMetadata=frameIndex.get(out.normalFrame);
    if(normalMetadata)out.normalSourceSize=normalMetadata.sourceSize;
    if(selectedMetadata)out.selectedSourceSize=selectedMetadata.sourceSize;
    if(o.polyPoints)out.polygon=o.polyPoints.map(pair);
    if(o.relativePolyPoints)out.relativePolygon=o.relativePolyPoints.map(pair);
    if(o.parsable===false)out.sourceDisabled=true;
    if(out.polygon){const xs=out.polygon.map(p=>p[0]),ys=out.polygon.map(p=>p[1]);out.localBounds={x:Math.min(...xs),y:Math.min(...ys),width:Math.max(...xs)-Math.min(...xs),height:Math.max(...ys)-Math.min(...ys),evidence:'derived-bounds-of-source-polygon'};}
    return out;
  };
  const title=main.stack[2],pushed=title.children[4].children[0].layerToPush,props=pushed.children.props,options=pushed.children.options;
  const titleVisuals=visuals(title,'stack.2');
  // The pushed layer is a different screen: do not bake its controls into the title scene.
  const propsControls=Object.entries(props.children.menu.children).map(([id,node])=>({id,...control(node,`props.menu.${id}`)}));
  const optionControls=Object.entries(options.children.menu.children).map(([id,node])=>({id,...control(node,`options.menu.${id}`)}));
  const titleControls=Object.entries(title.children[5].children).map(([id,node])=>({id,...control(node,`title.menu.${id}`)}));
  const propsLeft=round(240-905*pair(props.anchorPoint)[0]);
  function estimateBounds(controls,xOffset){for(const c of controls){if(!c.position||!c.localBounds)continue;const b=c.localBounds,x=round(c.position[0]+b.x+xOffset),y=round(c.position[1]+b.y);c.recommendedBounds={source:[x,y,b.width,b.height],canvas:[...mapOriginalPoint([x,y+b.height]),round(b.width*2.25),round(b.height*2.25)],evidence:'derived from source polygon bounds plus recommended parent placement; visually verify before use'};}}
  estimateBounds(titleControls,-240);estimateBounds(propsControls,propsLeft);
  const command=(id,node,path,placement)=>({id,...control(node,path),visuals:visuals(node,path),placement});
  const controls=[
    command('pause',hud.buttons.pause.children.item,'HUDS.buttons.pause.children.item',{kind:'source-alignment',region:'top-right'}),
    command('spellBook',hud.buttons.spellBook.children.item,'HUDS.buttons.spellBook.children.item',{kind:'source-alignment',region:'top-right',inset:[40,3]}),
    command('productionStance',hud.buttons.productionAIToggle,'HUDS.buttons.productionAIToggle',{kind:'source-position',point:pair(hud.buttons.productionAIToggle.position)}),
    command('group',hud.buttons.group,'HUDS.buttons.group',{kind:'runtime-layout',sourcePosition:pair(hud.groupControl.position),note:'GroupControl implementation supplies command positions; buttons.group references older missing icon-group frames.'}),
    command('special',special,'HudActionSpecial',{kind:'runtime-layout',region:'bottom-right',note:'LWSpecialsDock / LWScrollingDock calculates position; SunStar is a layered circular button.'})
  ];
  controls[2].states=hud.buttons.productionAIToggle.subItems.map(item=>({value:item.itemData,frame:firstFrame(item),frameAvailable:frameIndex.has(firstFrame(item))}));
  for(const [id,node]of Object.entries(hud.docks.dpad.children))controls.push(command(id,node,`HUDS.docks.dpad.children.${id}`,{kind:'runtime-layout',region:'bottom-left'}));
  const roster=Object.keys(selection.menu.children).map(id=>{
    const u=units[id],p=u.properties,s=u.hud.selectionItem;
    return {id,order:s.zOrder,selectionCost:s.cost,unlockRequired:selection.units[id].unlockRequired??null,initialUpgrade:p.initialUpgrade??null,initialUpgradePrice:p.initialUpgradePrice??null,stats:{hp:p.hitPoints,attack:p.attackPoints,defense:p.defensePoints??null,speed:p.speed,cost:p.production.cost,cooldown:p.production.cooldown,range:u.unitAnimations.primary.powerData?.range??null},icon:`icon-${id}.png`,animationPatterns:Object.fromEntries(Object.entries(u.unitAnimations).map(([state,v])=>[state,{pattern:v.frames,count:v.frameNumber,fps:typeof v.fps==='string'?Number(v.fps):v.fps??null}]))};
  }).sort((a,b)=>a.order-b.order);
  const mining=units.miney.properties.mining;
  return {
    schemaVersion:1,source:{sha256:ORIGINAL_APK_SHA256,files:[...sources],method:'Static allowlisted plist parsing only; no original application code, classes, actions, or scripts are instantiated.'},
    coordinateSystem:{designSize:[480,320],orientation:'landscape',origin:'bottom-left',atlasDensity:'SD sourceSize is logical pixels; game-data HD frame.size is approximately twice SD and needs a 0.5 logical-size factor.',canvas:{size:[1280,720],scale:2.25,offset:[100,0],formula:'canvasX=100+sourceX*2.25; canvasY=720-sourceY*2.25',note:'Aspect-preserving 1080x720 content with100px horizontal margins. Extend scenery into margins; anchor HUD to safe viewport.'}},
    menu:{
      sceneOrder:['sky','hills','title','props'],
      backgrounds:[...visuals(main.stack[0],'stack.0'),...visuals(main.stack[1],'stack.1')],
      scenes:{
        title:{visuals:titleVisuals,controls:titleControls,compositionWidth:960,compositionWidthEvidence:'derived from right-anchored hill position960 and offscreen composition positions; not declared contentSize',recommendedCompositionOffset:[-240,0],offsetEvidence:'derived centering of960-wide scene in480-wide viewport; screen-aligned title logo/pole bypass this shift'},
        props:{visuals:[...visuals(props,'props'),...visuals(options,'options')],controls:propsControls,optionControls,baseFrame:'mainmenu-props.png',anchor:pair(props.anchorPoint),recommendedBasePosition:[240,0],recommendedBaseLeft:propsLeft,placementEvidence:'derived: put the props anchor at screen center, then interpret child positions from sprite lower-left. Runtime StackTransitionLayer default position is unavailable.',labels:{campaign:'Campaign',mode:'Modes',monsterWars:'Monster Wars',toOptions:'Options'}}
      },
      uncertainties:['Cloth force simulation and transition code are not reproduced; cloth textures can be drawn statically.','Source menu positions include SD, HD and iPad overrides. This recovery retains all explicit variants and recommends SD for the480x320 view.','Props and options occupy horizontally sliding screens. Their parent placement is runtime behavior, so recommended base placement and clickable rectangles must be visually verified.','Original button titles are baked into atlas art; semantic labels here are renderer labels, not extracted text strings.']
    },
    battleHud:{
      controls,gemCounter:{...describe(gem,'LWGemCounter'),visuals:visuals(gem,'LWGemCounter'),iconPattern:gem.icon.gemFrameFormat},
      meters:{friendlyCastle:{...describe(hud.meters['team0-castleMeters'],'HUDS.meters.team0-castleMeters'),visuals:visuals(hud.meters['team0-castleMeters'],'HUDS.meters.team0-castleMeters')},mana:{...describe(hud.meters.manaMeter,'HUDS.meters.manaMeter'),visuals:visuals(hud.meters.manaMeter,'HUDS.meters.manaMeter')},enemyCastle:{...describe(hud.meters['team1-castleMeters'],'HUDS.meters.team1-castleMeters'),note:'Template position320,160 is not proof of a global screen HUD; level may override it.'}},
      productionDock:{horizontalAlign:production.horizontalAlign,onBottom:production.onBottom,z:production.z,slotGeometry:'runtime-calculated, not present in ProductionDock.plist',iconLogicalSize:[50,50],iconSizeEvidence:'derived from100x100 HD unit icons',miner:control(units.miney.hud.miningItem,'Units.miney.hud.miningItem')},
      grouping:{sourcePosition:pair(hud.groupControl.position),availableFrames:['icon-group-defense.png','icon-group-formup-off.png','icon-group-formup.png','icon-group-left.png','icon-group-right.png'],note:'Source group template names icon-group.png/icon-group-attack-glow.png which are absent in the supplied atlas. Use available group directional/defense art with reconstructed placement.'},
      unitSelection:{maxSlots:selection.maxSlots,maxColumns:selection.menu.maxColumns,childrenStartPosition:pair(selection.menu.childrenStartPosition),label:selection.scrollNode.children.label.string,startLabel:selection.startButton.menuItem.children.label.string,roster},
      mining:{hp:units.miney.properties.hitPoints,speed:units.miney.properties.speed,production:units.miney.properties.production,duration:mining.duration,gemsPerTrip:mining.gemsPerTrip,maxMiners:mining.maxMiners,idleWithGemDuration:mining.idleWithGemDuration,cooldown:mining.cooldown,cost:mining.cost,note:'Timing fields are static source values; original runtime scheduling is not recovered.'},
      notes:['HUDS states it is a reusable template store; individual levels determine which controls appear.','Attack/defend/group docking and production slots are native runtime layout and cannot be recovered exactly from these plists alone.','Some historical templates reference absent frames, including icon-production-defend.png and icon-group.png. Do not invent a claim of exact restoration.']
    }
  };
}

if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
  const apk=process.argv[2]??'../outputs/legendary-wars-analysis/samples/original.apk';
  const result=compileOriginalLayout(new Archive(apk,ORIGINAL_APK_SHA256));
  fs.writeFileSync('reference/layout-recovery.json',JSON.stringify(result,null,2)+'\n');
  console.log(`Recovered ${result.menu.scenes.title.visuals.length} title visuals, ${result.battleHud.controls.length} HUD controls and ${result.battleHud.unitSelection.roster.length} selectable units.`);
}
