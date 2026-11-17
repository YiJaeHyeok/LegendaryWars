import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { Archive } from './asset-safety.mjs';
import { compileOriginalLayout, mapOriginalPoint, parseAlignment, ORIGINAL_APK_SHA256 } from './original-layout.mjs';

test('source alignment expressions retain reference, anchor fractions and offsets',()=>{
  assert.deepEqual(parseAlignment('{screen, center, mid, noside, {0, -15}}'),{source:'{screen, center, mid, noside, {0, -15}}',reference:'screen',horizontal:.5,vertical:.5,side:'noside',insets:[0,-15],resolved:false});
  assert.equal(parseAlignment('{parent1, 25.104%, 6.771%, noside, {0}}').horizontal,.25104);
  assert.equal(parseAlignment('{screen, RightTop , Inside, {0, 0}}').resolved,false);
});

test('480x320 bottom-left coordinates map to a centered1280x720 viewport',()=>{
  assert.deepEqual(mapOriginalPoint([0,0]),[100,720]);
  assert.deepEqual(mapOriginalPoint([480,320]),[1180,0]);
  assert.deepEqual(mapOriginalPoint([240,160]),[640,360]);
});

const source=new URL('../../outputs/legendary-wars-analysis/samples/original.apk',import.meta.url);
test('real source recovery retains title layers, original hit polygons and HUD evidence',{skip:!fs.existsSync(source)},()=>{
  const d=compileOriginalLayout(new Archive(source,ORIGINAL_APK_SHA256));
  const title=d.menu.scenes.title.visuals;
  const banner=title.find(v=>v.frame==='mainmenu-title-logobanner.png');
  assert.deepEqual(banner.sourceSize,[414,124]);
  assert.equal(banner.alignment.vertical,.5);
  assert.deepEqual(banner.alignment.insets,[0,48]);
  assert.equal(title.find(v=>v.frame==='mainmenu-title-pole.png').z,1);
  assert.ok(title.find(v=>v.frame==='mainmenu-title-righttopribbon.png').parent===banner.path);
  assert.ok(!title.some(v=>v.frame==='mainmenu-props.png'),'pushed props screen must stay separate from title');
  const campaign=d.menu.scenes.props.controls.find(c=>c.id==='campaign');
  assert.deepEqual(campaign.position,[547,25]);
  assert.deepEqual(campaign.localBounds,{x:0,y:115,width:190,height:170,evidence:'derived-bounds-of-source-polygon'});
  assert.equal(campaign.selectedFrame,'mainmenu-campaign-on.png');
  assert.equal(d.menu.scenes.props.optionControls.find(c=>c.id==='toOptions').alignment.horizontal,.25104);
  assert.deepEqual(d.battleHud.gemCounter.position,[125,295]);
  assert.deepEqual(d.battleHud.meters.friendlyCastle.position,[63,308]);
  assert.equal(d.battleHud.controls.find(c=>c.id==='spellBook').normalFrame,'icon-spellbook.png');
  assert.deepEqual(d.battleHud.controls.find(c=>c.id==='productionStance').states.map(s=>s.value),['advanceRight','formUpRight']);
  assert.equal(d.battleHud.controls.find(c=>c.id==='productionStance').states[1].frameAvailable,false,'missing defend frame is identified explicitly');
  const sun=d.battleHud.controls.find(c=>c.id==='special');
  assert.deepEqual(sun.visuals.filter(v=>v.frame).map(v=>v.frame),['icon-special-glowletters.png','icon-special-rays.png','icon-special-centerglow.png','icon-special-sunfront.png','icon-special-bg.png']);
  const roster=d.battleHud.unitSelection.roster;
  assert.equal(roster.length,12);assert.equal(d.battleHud.unitSelection.maxSlots,7);
  assert.deepEqual(roster.map(v=>v.id),['knighty','elfy','dwarfy','faun','rogue','unicorn','sprite','golem','centaur','healer','wizard','phoenix']);
  assert.equal(roster.find(v=>v.id==='phoenix').unlockRequired,43);
  assert.equal(roster.find(v=>v.id==='rogue').initialUpgradePrice,1);
  assert.equal(roster.find(v=>v.id==='centaur').initialUpgradePrice,2);
  assert.equal(d.battleHud.mining.hp,20);assert.equal(d.battleHud.mining.gemsPerTrip,5);
  const serialized=JSON.stringify(d);
  assert.ok(!serialized.includes('classname'));assert.ok(!serialized.includes('extern-ref'));
});
