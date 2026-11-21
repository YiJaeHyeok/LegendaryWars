import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {Archive,parsePlist,validatePng,atlasFrame,hash,validateFont,validateMp3,validateVorbis,validateWav} from './asset-safety.mjs';
import {compileOriginalCampaign} from './original-campaign.mjs';
import {compileOriginalLayout} from './original-layout.mjs';
import {compileScenes} from './compile-scenes.mjs';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const workspace=path.dirname(root),input=path.join(workspace,'outputs/legendary-wars-analysis/samples');
const output=path.join(root,'app/src/main/assets');
fs.mkdirSync(path.join(output,'art'),{recursive:true});fs.mkdirSync(path.join(output,'audio'),{recursive:true});fs.mkdirSync(path.join(output,'fonts'),{recursive:true});
const apkHash='062e16b02595cc577527cd0f15c0a9f4d6cb36b6575bedf7cd226e5e0ab28b48';
const obbHash='844d16cc494000fa0efe6d0938c68becd7db227559e381b9edc964dd33159d7a';
const apk=new Archive(path.join(input,'original.apk'),apkHash),obb=new Archive(path.join(input,'original.obb'),obbHash);
const provenance=[],warnings=[];
function readPlist(name){const namePath='assets/'+name+'.plist';const b=apk.read(namePath);provenance.push({source:'apk',path:namePath,sha256:hash(b),use:'parsed data only'});return parsePlist(b.toString('utf8'));}
const original=readPlist('Units');
const layout=compileOriginalLayout(apk),campaign=compileOriginalCampaign(apk);
const data={schemaVersion:2,atlases:{},units:[],originalLayout:layout,campaign:{regions:['Grasslands','Frostburn','Desert','Swamp','Volcano','Netherworld','Skyhaven'],reconstructed:true,levels:campaign.levels.filter(l=>l.mode!=='intro'),coverage:campaign.coverage},rules:{mining:layout.battleHud.mining,castleHp:original.goodcastle.properties.hitPoints,heroRoster:layout.battleHud.unitSelection.roster}};
fs.writeFileSync(path.join(root,'reference/campaign-recovery.json'),JSON.stringify(campaign,null,2));
const world=readPlist('WorldMap');data.worldMap={width:Number(world.mapWidth)||1924,height:Number(world.mapHeight)||1262,points:(world.levels??[]).map(l=>({id:l.levelFile?.replace('.plist',''),position:(String(l.position).match(/-?\d+(?:\.\d+)?/g)??[]).map(Number),completed:l.completedImage,pending:l.notCompletedImage}))};
function atlas(name){
  if(data.atlases[name])return;
  const pp='assets/'+name+'.plist',png='assets/'+name+'.png';
  if(!apk.entries.has(pp)||!obb.entries.has(png)){warnings.push('Absent atlas: '+name);return;}
  const b=obb.read(png),dims=validatePng(b),meta=readPlist(name),frames={};
  for(const [key,value] of Object.entries(meta.frames??{})){
    if(!/^[A-Za-z0-9_ .-]+\.png$/.test(key)){warnings.push('Unused/nonstandard frame omitted: '+name+'/'+key);continue;}
    try{frames[key]=atlasFrame(value,dims.width,dims.height);}catch(e){warnings.push('Rejected invalid frame: '+name+'/'+key+' ('+e.message+')');}
  }
  fs.writeFileSync(path.join(output,'art',name+'.png'),b);
  provenance.push({source:'obb',path:png,sha256:hash(b),use:'unchanged PNG',...dims});
  data.atlases[name]={file:'art/'+name+'.png',...dims,frames};
}
const heroes=[['knighty','Knighty',0],['elfy','Elfy',2],['dwarfy','Dwarfy',9],['wizard','Wizard',30],['golem','Golem',20],['unicorn','Unicorn',13],['phoenix','Phoenix',42],['faun','Faun',5],['rogue','Rogue',0],['sprite','Sprite',0],['centaur','Centaur',0],['healer','Healer',0]];
const enemies=['skully','zombie','wolfy','vampy','troll','gargoyle','skullyarcher','reaper'];
const ordered=[...heroes.slice(0,7),['miney','Miney',0],...enemies.map(x=>[x,x,0]),...heroes.slice(7)];
const known=new Set(ordered.map(x=>x[0]));for(const id of Object.keys(original))if(!known.has(id))ordered.push([id,id,0]);
const MAGIC_UNITS=new Set(['wizard','unicorn','phoenix','phoenix-s','healer','sprite','faun','vampy','vampybat','vampyboss','reaper','reaperboss','lich','necro','medusa','fairy','dragon','icedragon','magmadragon','crystaldragon','sanddragon','bonedragon']);
const RANGED_UNITS=new Set(['elfy','skullyarcher','centaur','spidey','spideyboss','necrospiderpet']);
function parseTypeCode(name,fallback){
  if(name==='melee')return 0;if(name==='ranged')return 1;if(name==='magic')return 2;if(name==='siege')return 3;if(name==='neutral')return 4;
  return fallback;
}
function inferRoleType(id,hasProjectile){
  if(MAGIC_UNITS.has(id))return 2;
  if(RANGED_UNITS.has(id)||hasProjectile)return 1;
  return 0;
}
function parseChance(raw){
  if(raw===undefined||raw===null||raw==='')return 1;
  const n=Number(raw);if(!Number.isFinite(n)||n<=0)return 1;
  return n>1?Math.min(1,n/100):n;
}
function normalizeBuff(b,selfBuff=false){
  if(!b||typeof b!=='object')return null;
  const unitMod=String(b.unitModifier??''),attr=b.unitModifierAttributes??{};
  const aura=unitMod==='FaunAura'&&attr.auraBuff?attr.auraBuff:null;
  const src=aura??b;
  const duration=Math.max(0,Number(b.duration??src.duration)||0);
  const dps=Math.max(0,Number(src.dps)||0),hps=Math.max(0,Number(src.hps)||0);
  const stun=Math.max(0,Number(src.stun)||0),root=src.root===true;
  const speedScalor=Number(src.speedScalor)||0,hasteScalor=Number(src.hasteScalor)||0,attackScalor=Number(src.attackScalor)||0,defenseScalor=Number(src.defenseScalor)||0,rangeScalor=Number(src.rangeScalor)||0;
  const lifeDrainPercent=Number(src.lifeDrainPercent)||0;
  const status=String(src.status??b.status??(unitMod==='FearBuff'?'fear':unitMod==='CharmBuff'?'confuse':stun>0?'stun':dps>0?'poison':''));
  const chance=parseChance(b.percentChanceToApply??src.percentChanceToApply);
  const name=String(src.name??b.name??(status||'buff'));
  if(!duration&&!stun&&!dps&&!hps&&!speedScalor&&!hasteScalor&&!attackScalor&&!defenseScalor&&!rangeScalor&&!lifeDrainPercent&&!root&&status!=='fear'&&status!=='confuse')return null;
  return {name,status,duration:Math.max(duration,stun),chance,dps,hps,stun,root,speedScalor,hasteScalor,attackScalor,defenseScalor,rangeScalor,lifeDrainPercent,selfBuff,friendlyAura:aura?attr.friendly!==false:false,auraRadius:aura?Math.max(0,Number(attr.radius)||120):0};
}
function combat(def={}){
  const fps=Math.max(1,Math.min(120,Number(def.fps)||15)),count=Math.max(1,Number(def.frameNumber)||1),power=def.powerData??{};
  const hits=Object.entries(def.events??{}).filter(([name,e])=>/^(hit|fire|areaEffect)\d*$/.test(name)&&e&&Number.isFinite(Number(e.frame))).map(([name,e])=>({time:Math.max(0,Number(e.frame)-1)/fps,projectileSpeed:Number(e.projectileSpeed)||0,heal:e.heal===true,area:name.startsWith('areaEffect'),radius:Number(e.explosionRadius)||0})).sort((a,b)=>a.time-b.time);
  const buffs=[];
  for(const raw of (power.buffs??def.buffs??[])){const nb=normalizeBuff(raw,false);if(nb)buffs.push(nb);}
  if(def.events?.custom?.arguments?.targetBuff){const nb=normalizeBuff(def.events.custom.arguments.targetBuff,false);if(nb)buffs.push(nb);}
  if(power.hitHandler?.arguments?.buff){const nb=normalizeBuff(power.hitHandler.arguments.buff,false);if(nb)buffs.push(nb);}
  let selfBuff=null;
  if(def.events?.buff?.buff)selfBuff=normalizeBuff(def.events.buff.buff,true);
  if(!selfBuff&&Array.isArray(def.endEvents)){for(const ev of def.endEvents){if(ev?.data?.buff){selfBuff=normalizeBuff(ev.data.buff,true);break;}}}
  const stallStun=Number(def.events?.stall?.stunLength)||0;
  return {fps,frames:count,interval:count/fps,range:Number(power.range)||30,type:String(def.type??'attack'),hitAllLanes:power.hitAllLanes===true,hits,activationCost:Number(def.activationCost)||0,speedScalor:Number(power.speedScalor)||0,damageType:power.damageType??null,buffs,selfBuff,stallStun};
}
for(const [id,label,unlock] of ordered){
  const u=original[id];if(!u)throw Error('Missing unit '+id);
  const p=u.properties??{},animations=u.unitAnimations??{},variants=[];
  for(let tier=1;tier<=6;tier++){
    const stem=id+(tier===1?'':tier),sheet=stem+'-hd';
    if(!apk.entries.has('assets/'+sheet+'.plist'))continue;atlas(sheet);
    const sheets=[sheet];if(apk.entries.has('assets/'+stem+'-s-hd.plist')){atlas(stem+'-s-hd');sheets.push(stem+'-s-hd');}
    let anim=animations;
    if(tier>1&&apk.entries.has('assets/'+id+'Animations_'+tier+'.plist'))anim=readPlist(id+'Animations_'+tier);
    const states={};
    for(const [state,def] of Object.entries(anim)){
      if(typeof def.frames!=='string')continue;
      const names=[];
      let stateAtlas=sheet;
      for(let i=1;i<=Math.min(100,Number(def.frameNumber)||1);i++){
        const name=def.frames.replace(/%0?(\d*)d/,(_,n)=>String(i).padStart(Number(n)||1,'0'));
        const found=sheets.find(s=>data.atlases[s]?.frames[name]);if(found){stateAtlas=found;names.push(name);}
      }
      if(names.length){const hit=def.events?.hit??def.events?.fire??def.events?.areaEffect;states[state]={atlas:stateAtlas,frames:names,fps:Math.max(1,Math.min(120,Number(def.fps)||15)),hitFrame:Number(hit?.frame)||1};}
    }
    if(!states.ready)states.ready=states.walk??states.primary;
    if(!states.walk)states.walk=states.ready;
    if(!states.walk)continue;
    variants.push({tier,atlas:sheet,states,primaryCombat:combat(anim.primary),specialCombat:combat(anim.special)});
  }
  if(!variants.length){
    const sourceSheet=(u.spriteSheets?.[0]??'GrassScene')+'-hd';atlas(sourceSheet);
    let frame=animations.ready?.frames??'grass-scrolling-crate.png';
    if(!data.atlases[sourceSheet]?.frames[frame])frame='grass-scrolling-crate.png';
    const fallbackAtlas=data.atlases[sourceSheet]?.frames[frame]?sourceSheet:'GrassScene-hd';atlas(fallbackAtlas);
    const state={atlas:fallbackAtlas,frames:[frame],fps:1,hitFrame:1};variants.push({tier:1,atlas:fallbackAtlas,states:{ready:state,walk:state,primary:state,death:state}});
  }
  const range=Number(animations.primary?.powerData?.range)||30;
  const roster=layout.battleHud.unitSelection.roster.find(h=>h.id===id),primary=variants[0].states.primary;
  const primaryCombat=combat(animations.primary);
  const hasProj=primaryCombat.hits.some(h=>h.projectileSpeed>0);
  const inferred=inferRoleType(id,hasProj);
  const damageType=parseTypeCode(p.damageType,inferred),armorType=parseTypeCode(p.armorType,inferred);
  data.units.push({id,label,unlock,hero:!!roster,moonstonePrice:roster?.initialUpgrade?Number(roster.initialUpgradePrice)||1:0,hp:Number.isFinite(Number(p.hitPoints))?Number(p.hitPoints):100,attack:Number(p.attackPoints)||0,defense:Number(p.defensePoints)||0,critChance:Number(p.critChance)||0,speed:Number(p.speed)||0,cost:Number(p.production?.cost)||0,cooldown:Number(p.production?.cooldown)||0,range,interval:primaryCombat.interval,hitDelay:primary?(primary.hitFrame-1)/primary.fps:.3,specialCost:Number.isFinite(Number(animations.special?.activationCost))?Number(animations.special.activationCost):10,specialDamage:Number(p.specialPoints)||p.attackPoints||10,reward:Number(p.rewardPoints)||0,gearCost:Number(p.moonStone?.dUpgradeCost)||1,healer:id==='healer',damageType,armorType,buffImmune:p.buffImmune===true,charmImmune:p.charmImmune===true,huntLimit:Number(p.huntLimit)||0,upgrades:p.upgrades??{},variants});
}
for(const name of ['mainmenu-background-hd','mainmenu-foreground-hd','worldmapsheet1','worldmapsheet2','worldmapsheet3-hd','armory-shopdesk-hd','armory-units-spritesheet1-hd','unit-selection-spritesheet-hd','hud-spritesheet-hd'])atlas(name);
for(const region of ['Grass','Snow','Desert','Swamp','Volcano','Netherworld']){atlas(region+'Scene-hd');if(apk.entries.has('assets/'+region+'Scene2-hd.plist'))atlas(region+'Scene2-hd');if(apk.entries.has('assets/'+region+'SceneScrolling-hd.plist'))atlas(region+'SceneScrolling-hd');if(apk.entries.has('assets/'+region+'SceneScrolling2-hd.plist'))atlas(region+'SceneScrolling2-hd');atlas('castle-'+region.toLowerCase()+'-spritesheet-hd');}
for(const extra of ['SkyhavenSceneScrolling-hd','SkyhavenSceneScrolling2-hd','armory-spritesheet-hd','armory-units-spritesheet2-hd','armory-units-spritesheet3-hd','scrollnode-vertical-spritesheet-hd','scrollnode-horizontal-spritesheet-hd','fx-spritesheet-hd'])atlas(extra);
atlas('castle-default-spritesheet-hd');
for(const existing of fs.readdirSync(path.join(output,'audio')))if(existing.endsWith('.wav'))fs.unlinkSync(path.join(output,'audio',existing));
const sfxMap={
  melee:'mono_Knight_1st_attack.wav',
  ranged:'mono_Elf_1st_attack.wav',
  arrowHit:'mono_Arrowhit.wav',
  magic:'mono_Wizard_attack_fire.wav',
  heroDeath:'mono_Knight_Death.wav',
  enemyDeath:'mono_Skeleton_Death.wav',
  special:'mono_Knight_special.wav',
  specialReady:'mono_Special_Ready.wav',
  spellArrows:'mono_Rainofarrows.wav',
  spellFire:'mono_Firestorm.wav',
  spellIce:'mono_Icestorm.wav',
  gem:'mono_Gem_touch1.wav',
  spawn:'mono_Create.wav',
  miner:'mono_Minertouch.wav',
  click:'mono_Buttonclick.wav',
  upgrade:'mono_Upgrade_tier.wav',
  victory:'mono_Victory.wav',
  defeat:'mono_defeat_laugh.wav'
};
data.sfx={};
for(const [key,name] of Object.entries(sfxMap)){
  if(!obb.entries.has('assets/'+name))throw Error('Missing curated SFX '+name);
  const b=obb.read('assets/'+name),info=validateWav(b);
  fs.writeFileSync(path.join(output,'audio',name),b);
  provenance.push({source:'obb',path:'assets/'+name,sha256:hash(b),use:'validated WAV',...info});
  data.sfx[key]='audio/'+name;
}
data.music={};
for(const name of ['TitleScreen.mp3','Overworld.mp3','Armory.mp3','GrassLevel.mp3','SnowLevel.mp3','DesertLevel.mp3','SwampLevel.mp3','VolcanoLevel.mp3','Netherworld_Level.mp3','SkyHaven.mp3']){
  const b=obb.read('assets/'+name),isOgg=b.toString('ascii',0,4)==='OggS';const info=isOgg?validateVorbis(b):validateMp3(b),dest=name.replace('.mp3',isOgg?'.ogg':'.mp3');fs.writeFileSync(path.join(output,'audio',dest),b);provenance.push({source:'obb',path:'assets/'+name,outputPath:'audio/'+dest,sha256:hash(b),use:isOgg?'validated Ogg Vorbis':'validated MP3',...info});data.music[name.replace('.mp3','')]='audio/'+dest;
}
for(const name of ['Livingst.ttf','Museo-700.ttf']){const b=apk.read('assets/'+name);const info=validateFont(b);fs.writeFileSync(path.join(output,'fonts',name),b);provenance.push({source:'apk',path:'assets/'+name,outputPath:'fonts/'+name,sha256:hash(b),use:'validated TrueType',...info});}
data.heroTypes=layout.battleHud.unitSelection.roster.map(h=>data.units.findIndex(u=>u.id===h.id));
data.scenes=compileScenes(layout,data);
data.sourceLevelFiles=[...apk.entries.keys()].filter(x=>/^assets\/level-0[1-7]-\d+\.plist$/.test(x)).sort();
fs.writeFileSync(path.join(output,'game-data.json'),JSON.stringify(data));
const report={sourceApkSha256:apkHash,sourceObbSha256:obbHash,originalExecutablesIncluded:false,validation:'ZIP CRC, exact source SHA-256, PNG chunk CRC/dimension/bounds, plist safe subset, WAV chunk bounds, Ogg page CRC/bounds and Vorbis ID, TrueType table bounds',files:provenance,warnings};
fs.writeFileSync(path.join(root,'asset-provenance.json'),JSON.stringify(report,null,2));
console.log(JSON.stringify({atlases:Object.keys(data.atlases).length,units:data.units.length,variants:data.units.reduce((n,u)=>n+u.variants.length,0),frames:Object.values(data.atlases).reduce((n,a)=>n+Object.keys(a.frames).length,0),sourceLevels:data.sourceLevelFiles.length,warnings}));