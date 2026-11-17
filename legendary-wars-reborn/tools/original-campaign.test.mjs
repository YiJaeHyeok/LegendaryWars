import test from 'node:test';
import assert from 'node:assert/strict';
import { Archive } from './asset-safety.mjs';
import { compileOriginalCampaign } from './original-campaign.mjs';

const archive = new Archive(new URL('../../outputs/legendary-wars-analysis/samples/original.apk', import.meta.url), '062e16b02595cc577527cd0f15c0a9f4d6cb36b6575bedf7cd226e5e0ab28b48');
let result;
const campaign = () => result ??= compileOriginalCampaign(archive);

test('recovers every numbered original source including the later worlds', () => {
  const { levels } = campaign();
  assert.equal(levels.length, 74);
  assert.equal(levels[0].sourceId, 'level-01-0');
  assert.equal(levels.at(-1).sourceId, 'level-07-12');
  assert.ok(levels.find(l => l.sourceId === 'level-06-7').spawns.length > 20);
  assert.equal(levels.find(l => l.sourceId === 'level-06-1').title, 'Level 6-1 Netherworld Cells');
});

test('Level 1-2 retains its original final wave at 90 seconds and HP customization', () => {
  const level = campaign().levels.find(l => l.sourceId === 'level-01-2');
  const final = level.waves.find(w => w.source === 'Level1-2-final.plist');
  assert.equal(final.startTime, 90);
  assert.equal(final.totalSpawns, 6);
  assert.ok(final.trigger, 'first-play tutorial starts the waves through HUD events');
  const boss = level.spawns.find(s => s.scriptId === 'finalskully');
  assert.equal(boss.unitId, 'skully');
  assert.equal(boss.tier, 2);
  assert.equal(boss.hpFactor, 1.4);
  assert.equal(level.castleHp.player, 500);
  assert.match(level.title, /1-2/);
});

test('first stage is the original hero tutorial with a scout and target victory', () => {
  const level = campaign().levels.find(l => l.sourceId === 'level-01-1');
  assert.equal(level.mode, 'tutorial');
  assert.ok(level.initialPlayerUnits.some(u => u.unitId === 'knighty' && u.x === 720 && u.lane === 1));
  assert.ok(level.spawns.some(u => u.unitId === 'skullyarcher' && u.x === 1200 && u.lane === 0));
  assert.ok(level.waves.some(w => w.source === 'Level1-1-stream.plist' && w.trigger?.target === 'skully'));
  assert.ok(level.outcomes.some(o => o.victory && JSON.stringify(o.trigger).includes('finalskully')));
  assert.ok(level.text.some(t => /Control the knight/.test(t.text)));
  assert.ok(!level.outcomes.some(o => o.victory && o.trigger?.target === 'castle-team1'));
});

test('source state commands retain tutorial switches, selection, and exact gem reset', () => {
  const level = campaign().levels.find(l => l.sourceId === 'level-01-2');
  const switches = level.commands.filter(c => c.type === 'setSwitch');
  assert.deepEqual(switches.map(c => c.id), [0, 1, 2]);
  assert.equal(switches[0].time, 1.4);
  assert.equal(switches[0].value, true);
  assert.equal(switches[1].trigger.target, 'mining');
  assert.deepEqual(level.commands.filter(c => c.type === 'clearSwitch').map(c => [c.id, c.value]), [[0, false], [1, false], [2, false]]);
  const reset = level.commands.find(c => c.type === 'setGems');
  assert.equal(reset.amount, 0);
  assert.equal(reset.source, 'WinCreateBackingAction.plist');
  assert.equal(reset.trigger.type, 'realTime');
  assert.match(JSON.stringify(reset.trigger), /victory/);
  const tutorial = campaign().levels.find(l => l.sourceId === 'level-01-1');
  assert.ok(tutorial.commands.some(c => c.type === 'selectUnit' && c.id === 'knighty'));
  assert.equal(level.coverage.unsupportedActions.setSwitch, undefined);
});

test('root landscape layers retain original grass anchors and parallax placement', () => {
  const level = campaign().levels.find(l => l.sourceId === 'level-01-2');
  assert.equal(level.scene, 'grass');
  const layers = Object.fromEntries(level.layers.map(l => [l.id, l]));
  assert.equal(layers.midgroundL.frame, 'Level1-L4L.png');
  assert.deepEqual(layers.midgroundL.anchor, [0, 0]);
  assert.deepEqual(layers.midgroundL.ratio, [1, 1]);
  assert.deepEqual(layers.midgroundL.position, [0, 0]);
  assert.deepEqual(layers.midgroundR.position, [849, 0]);
  assert.equal(layers.mineLayer.frame, 'Level1-Layer3.png');
  assert.deepEqual(layers.mineLayer.position, [0, 136]);
  assert.equal(layers.mineLayer.onScreen, true);
  assert.deepEqual(layers.background2.anchor, [0, 1]);
  assert.equal(layers.background2.frame, 'Level1-Layer1.png');
  assert.match(layers.background2.align, /top/);
});

test('static random choices are deterministic and repeatForever remains explicit', () => {
  assert.deepEqual(compileOriginalCampaign(archive), campaign());
  const level = campaign().levels.find(l => l.sourceId === 'level-01-5');
  assert.ok(level.waves.some(w => w.repeatForever && w.derived.includes('one-cycle-preview')));
  assert.ok(level.spawns.some(s => s.derived.includes('unit-choice') || s.derived.includes('random-lane')));
  assert.equal(level.worldWidth, null, 'a percentage viewport is not a 100-unit battlefield');
  assert.equal(level.mode, 'scrolling');
});

const xml = obj => '<?xml version="1.0"?><plist version="1.0">' + value(obj) + '</plist>';
function value(v) {
  if (Array.isArray(v)) return '<array>' + v.map(value).join('') + '</array>';
  if (v && typeof v === 'object') return '<dict>' + Object.entries(v).map(([k, x]) => '<key>' + k + '</key>' + value(x)).join('') + '</dict>';
  if (typeof v === 'number') return '<real>' + v + '</real>';
  return '<string>' + String(v).replaceAll('&', '&amp;').replaceAll('<', '&lt;') + '</string>';
}
const fixture = files => ({ entries: new Map(Object.keys(files).map(k => ['assets/' + k, {}])), read: name => Buffer.from(xml(files[name.slice(7)])) });

test('unknown actions and conditions remain reported; imported action cycles stop safely', () => {
  const a = fixture({
    'level-01-1.plist': { scripts: { normalEvent: '{"start.plist"}' } },
    'start.plist': { events: [
      { type: 'gameTime', delay: 1, actions: [{ type: 'nativeMystery', payload: 'do not execute' }, { type: 'actionFile', filename: 'loop.plist' }] },
      { type: 'gameTime', conditions: ['LevelCycle()==2'], actions: [{ type: 'createUnit', unitType: 'wrong-cycle' }] },
      { type: 'gameTime', conditions: ['NeverRunThis()'], actions: [{ type: 'createUnit', unitType: 'conditional' }] },
    ] },
    'loop.plist': { type: 'actionFile', filename: 'loop.plist' },
  });
  const { levels: [level] } = compileOriginalCampaign(a);
  assert.equal(level.coverage.unsupportedActions.nativeMystery, 1);
  assert.ok(level.coverage.issues.some(i => i.reason === 'import-cycle'));
  assert.ok(!level.spawns.some(s => s.unitId === 'wrong-cycle'));
  assert.equal(level.spawns.find(s => s.unitId === 'conditional').trigger.conditional, true);
});
