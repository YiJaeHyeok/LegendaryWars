import { parsePlist, safeName } from './asset-safety.mjs';

// This is a bounded data compiler, never an interpreter for native actions or
// expression strings. Times on triggered records are relative to that trigger.
const object = v => v !== null && typeof v === 'object' && !Array.isArray(v);
const num = (v, fallback = 0) => (typeof v === 'number' || typeof v === 'string' && /^-?\d+(?:\.\d+)?$/.test(v)) && Number.isFinite(Number(v)) ? Number(v) : fallback;
const ref = v => typeof v === 'string' ? /^\{\s*"([A-Za-z0-9_. -]+\.plist)"(?:\s+"([A-Za-z0-9_. -]+)")?\s*\}$/.exec(v) : null;
const round = x => Math.round(x * 1e6) / 1e6;
const digest = s => { let h = 2166136261; for (const c of s) h = Math.imul(h ^ c.charCodeAt(0), 16777619); return (h >>> 0) / 4294967296; };
const parseUnit = s => { const m = /^(.+?)(?:\{(\d+)\})?(?:-(\d+(?:\.\d+)?))?$/.exec(String(s)); return { type: m?.[1] ?? String(s), tier: num(m?.[2], 1), chance: num(m?.[3], 1) }; };
const pair = (v, fallback = null) => {
  if (v === undefined) return fallback;
  if (typeof v !== 'string') return null;
  const m = /^\s*\{?\s*(-?(?:\d+(?:\.\d+)?|\.\d+))\s*,\s*(-?(?:\d+(?:\.\d+)?|\.\d+))\s*\}?\s*$/.exec(v);
  return m ? [Number(m[1]), Number(m[2])] : null;
};
const resourceText = v => typeof v === 'string' && /^[A-Za-z0-9_. -]{1,200}$/.test(v) ? v : null;
function landscapeLayers(data) {
  return Object.entries(data.layers ?? {}).filter(([, n]) => object(n)).map(([id, n]) => {
    const p = object(n.parallax) ? n.parallax : {};
    return { id, frame: resourceText(n.spriteFrameName ?? n.sprite?.spriteFrameName), sheet: resourceText(n.spriteSheet ?? n.spriteSheets?.[0]), anchor: pair(n.anchorPoint), position: pair(p.position ?? p.offset ?? n.position, [0, 0]), ratio: pair(p.ratio, [1, 1]), onScreen: p.onScreen === true, align: typeof (p.alignOptions ?? p.align ?? n.alignOptions ?? n.align) === 'string' ? (p.alignOptions ?? p.align ?? n.alignOptions ?? n.align).slice(0, 256) : null, z: num(n.z, null) };
  }).filter(n => n.frame !== null);
}

function merge(base, next) {
  const out = { ...base };
  for (const [raw, v] of Object.entries(next ?? {})) {
    if (raw === 'extern-ref') continue;
    const key = raw.replace(/\s*\((?:override|remove)\)$/, '');
    if (/\(remove\)$/.test(raw)) delete out[key];
    else out[key] = /\(override\)$/.test(raw) ? v : object(v) && object(out[key]) ? merge(out[key], v) : v;
  }
  return out;
}

/** Recover the original first-play campaign without running APK code.
 * Numeric scale fields preserve original factors; raw customizers retain all
 * other values. Missing numeric defaults remain null instead of invented stats.
 * Repeat streams contain ONE finite cycle and an explicit repeatForever flag.
 */
export function compileOriginalCampaign(archive) {
  const cache = new Map();
  const read = name => {
    const path = 'assets/' + name;
    if (!safeName(path) || !name.endsWith('.plist')) throw Error('Unsafe campaign reference');
    if (!cache.has(name)) cache.set(name, parsePlist(archive.read(path).toString('utf8')));
    return cache.get(name);
  };
  const names = [...archive.entries.keys()].filter(n => /^assets\/level-\d\d-\d+\.plist$/.test(n)).map(n => n.slice(7));
  names.sort((a, b) => a.localeCompare(b, 'en', { numeric: true }));
  const worldLevels = [];
  const mapSources = new Map();
  const loadMap = (filename, seen = new Set()) => {
    if (seen.has(filename) || seen.size >= 8 || !archive.entries.has('assets/' + filename)) return;
    seen.add(filename);
    const map = read(filename);
    for (const l of map.levels ?? []) { worldLevels.push(l); mapSources.set(l.levelFile, filename); }
    if (typeof map.skyHavenData === 'string' && /^[A-Za-z0-9_. -]+\.plist$/.test(map.skyHavenData)) loadMap(map.skyHavenData, seen);
  };
  loadMap('WorldMap.plist');
  let allUnits = {};
  if (archive.entries.has('assets/Units.plist')) allUnits = read('Units.plist');
  const levels = names.map(name => {
    const [, regionString, stageString] = /level-(\d+)-(\d+)/.exec(name);
    const region = Number(regionString), stage = Number(stageString);
    const coverage = { unsupportedActions: {}, conditionalEvents: [], triggeredEvents: [], issues: [], skippedScripts: [] };
    const issue = (reason, source, extra = {}) => coverage.issues.push({ reason, source, ...extra });
    const load = (filename, stack = []) => {
      if (stack.length >= 24 || stack.includes(filename)) { issue('import-cycle', filename); return {}; }
      let data;
      try { data = read(filename); } catch (e) { issue('missing-or-invalid-resource', filename, { message: e.message }); return {}; }
      const r = ref(data['extern-ref']);
      if (!r) return merge({}, data);
      if (r[2]) { issue('unsupported-level-keypath', filename); return merge({}, data); }
      return merge(load(r[1], [...stack, filename]), data);
    };
    const raw = read(name), data = load(name);
    const map = worldLevels.find(l => l.levelFile === name) ?? {};
    const widthMatch = /\{\{[^}]*\},\s*\{([\d.]+)\s*,/.exec(data.boundary?.window ?? '');
    const dock = data.hud?.children?.unitSelectionDock ?? {};
    const allowedUnits = Object.keys(data.units ?? {}).filter(u => !['goodcastle', 'evilcastle', 'obstacle'].includes(u)).map(u => u.replace(/\{\d+\}/, ''));
    const level = {
      sourceId: name.slice(0, -6), region, stage, title: map.name ?? raw.name ?? null,
      scene: data.sceneName ?? /^level-(grass|snow|desert|swamp|volcano|netherworld2?)(?:-s|-hackslash)?\.plist$/.exec(ref(raw['extern-ref'])?.[1] ?? '')?.[1] ?? null,
      mode: stage === 0 ? 'intro' : region === 1 && stage === 1 ? 'tutorial' : region === 7 && stage === 12 ? 'ending' : data.constants?.type ?? (data.spawnFromMapEdge || data.scrolling ? 'scrolling' : 'campaign'),
      initialGem: num(data.initialGem, null), maxUnitSlots: dock.parsable === false ? 0 : num(dock.maxSlots, null),
      allowedUnits: [...new Set(allowedUnits)], worldWidth: widthMatch ? Number(widthMatch[1]) : null,
      lanes: data.lanes ?? [], layers: landscapeLayers(data), mining: { enabled: Boolean(data.layers?.mineLayer?.createUnitType), unitId: data.layers?.mineLayer?.createUnitType ?? null },
      castleHp: { player: null, enemy: null }, waves: [], spawns: [], initialPlayerUnits: [], text: [], outcomes: [], commands: [], coverage,
    };
    let actionsVisited = 0;
    const unsupported = type => coverage.unsupportedActions[type] = (coverage.unsupportedActions[type] ?? 0) + 1;
    const commandTypes = new Set(['setSwitch', 'clearSwitch', 'setGems', 'flag', 'selectUnit', 'setMaxMana', 'setScrollSpeed', 'matchScrollSpeed', 'die']);
    const command = (node, ctx, source) => {
      const numeric = (v, field) => { const n = num(v, null); if (n === null) issue('nonliteral-command-value', source, { type: node.type, field }); return n; };
      const id = v => typeof v === 'string' && v.length <= 128 ? v : null;
      let fields;
      switch (node.type) {
        case 'setSwitch': case 'clearSwitch': fields = { id: numeric(node.switch, 'switch'), value: node.type === 'setSwitch' }; break;
        case 'setGems': fields = { amount: numeric(node.count, 'count') }; break;
        case 'flag': fields = { id: id(node.flag), value: node.value === undefined ? null : numeric(node.value, 'value'), increment: node.increment === true }; break;
        case 'selectUnit': fields = { id: id(node.scriptID) }; break;
        case 'setMaxMana': fields = { max: numeric(node.max, 'max') }; break;
        case 'setScrollSpeed': fields = { speed: numeric(node.speed, 'speed') }; break;
        case 'matchScrollSpeed': fields = { target: id(node.target), value: node.val === true }; break;
        case 'die': fields = { target: id(node.target) }; break;
      }
      level.commands.push({ time: round(ctx.time), trigger: ctx.trigger ?? null, source, type: node.type, ...fields });
    };
    const triggerFor = (e, ctx, source) => {
      const conditions = Array.isArray(e.conditions) ? e.conditions.map(String) : [];
      const cycles = conditions.map(c => /^LevelCycle\(\)\s*==\s*(\d+)$/.exec(c));
      if (conditions.length) {
        const excluded = cycles.some(c => c && Number(c[1]) !== 1);
        const conditional = cycles.some(c => !c);
        coverage.conditionalEvents.push({ source, type: e.type ?? e.eventType, conditions, excluded, resolved: !conditional });
        if (excluded) return null;
        if (conditional) e = { ...e, conditional: true };
      }
      const type = e.eventType ?? e.type;
      const startup = type === 'gameTime' || type === 'levelStateChanged' && e.states?.every(s => ['onEnter', 'initHUD', 'normal'].includes(s));
      if (startup && !e.conditional) return { ...ctx, time: ctx.time + num(e.delay), derived: [...ctx.derived, ...(e.delayRandomRange ? ['event-delay-variance-omitted'] : []), ...(e.repeat ? ['event-one-cycle-preview'] : [])] };
      // An end-* script and its root levelStateChanged event describe the same
      // transition, rather than two successive victory/defeat transitions.
      if (type === 'levelStateChanged' && !e.conditional && ctx.time === 0 && ctx.trigger?.type === type && !ctx.trigger.parent && JSON.stringify(ctx.trigger.states) === JSON.stringify(e.states)) return { ...ctx, time: num(e.delay) };
      const trigger = { type: type ?? 'unknown', ...(e.target === undefined ? {} : { target: e.target }), ...(e.states ? { states: e.states } : {}), ...(conditions.length ? { conditions } : {}), ...(e.conditional ? { conditional: true } : {}), ...(ctx.trigger ? { parent: ctx.trigger, parentTime: ctx.time } : {}) };
      coverage.triggeredEvents.push({ source, trigger });
      return { ...ctx, time: num(e.delay), trigger };
    };
    const spawn = (unit, ctx, source, extra = {}) => {
      if (level.spawns.length >= 10000) { issue('spawn-limit', source); return; }
      const parsed = parseUnit(unit.unitType ?? unit.type ?? 'unknown');
      const customizer = unit.customizer ?? {};
      const point = /^\{?\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)/.exec(unit.position ?? '');
      const s = { time: round(ctx.time), unitId: parsed.type, tier: num(customizer.tierOverride, parsed.tier), team: num(unit.team, 1), lane: num(unit.lane, 1), x: point ? Number(point[1]) : null, y: point ? Number(point[2]) : null, fromLeft: unit.fromLeft ?? false, hpFactor: num(customizer.scaleHp, 1), attackFactor: num(customizer.scaleAttack, 1), speedFactor: num(customizer.scaleSpeed, 1), customizer, scriptId: unit.id ?? customizer.id ?? null, source, trigger: ctx.trigger ?? null, derived: [...ctx.derived], ...(unit.positionAlignment ? { positionAlignment: unit.positionAlignment } : {}), ...(unit.ai ? { ai: unit.ai } : {}), ...(unit.plot === undefined ? {} : { plot: unit.plot }), ...(unit.spriteFrame ? { spriteFrame: unit.spriteFrame } : {}), ...(unit.imageOffset ? { imageOffset: unit.imageOffset } : {}), ...extra };
      level.spawns.push(s);
      if (s.unitId === 'goodcastle' || s.unitId === 'evilcastle') {
        const def = merge(allUnits[s.unitId] ?? {}, data.units?.[s.unitId] ?? {});
        level.castleHp[s.team === 0 ? 'player' : 'enemy'] = num(def.properties?.hitPoints, null);
      }
    };
    const visitFile = (filename, ctx, stack) => {
      if (stack.length >= 32 || stack.includes(filename)) { issue('import-cycle', filename); return; }
      let doc;
      try { doc = read(filename); } catch (e) { issue('missing-or-invalid-resource', filename, { message: e.message }); return; }
      visit(doc, ctx, filename, [...stack, filename]);
    };
    const compileWaves = (action, ctx, source) => {
      for (const [waveIndex, w] of (action.waves ?? []).entries()) {
        let stream;
        try { stream = read(w.waveFile); } catch (e) { issue('missing-or-invalid-resource', w.waveFile, { message: e.message }); continue; }
        const count = Math.max(0, Math.min(2000, Math.floor(num(stream.totalSpawns))));
        const derived = [...ctx.derived, ...(stream.repeatForever ? ['one-cycle-preview'] : []), ...(stream.delayVariance ? ['delay-variance-midpoint'] : [])];
        const wave = { source: w.waveFile, actionSource: source, title: w.title ?? null, startTime: num(w.startTime), monsterStartTime: num(w.monsterStartTime), eventTime: round(ctx.time), totalSpawns: count, repeatForever: Boolean(stream.repeatForever), delayBetweenSpawns: num(stream.delayBetweenSpawns), delayVariance: num(stream.delayVariance), lanePosition: stream.lanePosition ?? {}, fromLeft: stream.fromLeft ?? false, team: num(stream.team, 1), trigger: ctx.trigger ?? null, derived, globalCustomizer: action.globalCustomizer ?? {}, customizer: stream.customizer ?? {} };
        level.waves.push(wave);
        const choices = (stream.units ?? []).map(u => typeof u === 'string' ? parseUnit(u) : { ...u, tier: num(u.customizer?.tierOverride, 1), chance: num(u.chance, 1) });
        if (!choices.length) { issue('empty-wave', w.waveFile); continue; }
        const normal = choices.filter(u => !u.last), last = choices.filter(u => u.last);
        for (let i = 0; i < count; i++) {
          const pool = i === count - 1 && last.length ? last : normal.length ? normal : choices;
          const seed = `${name}/${source}/${waveIndex}/${w.waveFile}/${i}`;
          const total = pool.reduce((n, u) => n + Math.max(0, u.chance), 0);
          let choice = pool[0], roll = digest(seed) * total;
          for (const u of pool) { roll -= Math.max(0, u.chance); if (roll < 0) { choice = u; break; } }
          const customizer = merge(stream.customizer ?? {}, choice.customizer ?? {});
          for (const [k, v] of Object.entries(action.globalCustomizer ?? {})) {
            // Independent global and per-unit scale factors combine multiplicatively.
            customizer[k] = k.startsWith('scale') && num(v, null) !== null ? round(num(customizer[k], 1) * num(v)) : v;
          }
          const laneConfig = stream.lanePosition ?? {};
          const lane = laneConfig.laneChoice === 'random' ? Math.floor(digest(seed + '/lane') * 3) : (num(laneConfig.startLane) + i) % 3;
          const reasons = [...derived, ...(pool.length > 1 ? ['unit-choice'] : []), ...(laneConfig.laneChoice === 'random' ? ['random-lane'] : []), 'wave-time-composition'];
          const time = ctx.time + num(w.startTime) + num(w.monsterStartTime) + i * num(stream.delayBetweenSpawns);
          spawn({ ...choice, unitType: choice.type, team: stream.team ?? 1, lane, fromLeft: stream.fromLeft ?? false, customizer: { ...customizer, tierOverride: num(customizer.tierOverride, choice.tier) } }, { ...ctx, time, derived: reasons }, w.waveFile, { waveIndex: level.waves.length - 1 });
        }
        if (stream.repeatForever) issue('repeat-stream-preview-only', w.waveFile, { cycleSpawns: count });
      }
    };
    const visit = (node, ctx, source, stack = []) => {
      if (++actionsVisited > 30000 || stack.length > 32) { if (actionsVisited === 30001) issue('action-limit', source); return; }
      if (Array.isArray(node)) { for (const n of node) visit(n, ctx, source, stack); return; }
      if (!object(node)) return;
      const imported = ref(node['extern-ref']);
      if (imported) { visitFile(imported[1], ctx, stack); return; }
      const events = node.events ?? node['events(override)'];
      if (Array.isArray(events)) {
        for (const event of events) { const next = triggerFor(event, ctx, source); if (next) {
          const actions = event.actions ?? [];
          if (event.randomAction && actions.length) visit(actions[Math.floor(digest(source + '/' + next.time) * actions.length)], { ...next, derived: [...next.derived, 'event-action-choice'] }, source, stack);
          else visit(actions, next, source, stack);
        } }
        return;
      }
      if (node.type === 'actionFile') { visitFile(node.filename, ctx, stack); return; }
      if (node.type === 'createEvents') { const next = triggerFor(node, ctx, source); if (next) visit(node.actions, next, source, stack); return; }
      if (node.type === 'createWaves') { compileWaves(node, ctx, source); return; }
      if (node.type === 'createUnit') { spawn(node, ctx, source); return; }
      if (commandTypes.has(node.type)) { command(node, ctx, source); return; }
      if (node.type === 'showTutorialText' || node.type === 'chat') {
        level.text.push({ time: round(ctx.time), kind: node.type === 'chat' ? 'chat' : 'tutorial', text: String(node.message ?? node.text ?? ''), title: node.title ?? null, trigger: ctx.trigger ?? null, source }); return;
      }
      if (node.type === 'endLevel') { level.outcomes.push({ time: round(ctx.time), victory: node.victory === true && !node.defeat, trigger: ctx.trigger ?? null, source }); return; }
      if (node.type) unsupported(node.type);
      else if (node.actions) visit(node.actions, ctx, source, stack);
    };
    const skipped = new Set(['clouds', 'sun', 'moon', 'quake', 'upgradeUnits', 'create-hud-gemcounter', 'create-groupControl', 'particles', 'fog', 'credits']);
    for (const [key, script] of Object.entries(data.scripts ?? {})) {
      if (skipped.has(key)) { coverage.skippedScripts.push(key); continue; }
      const r = ref(script);
      const ctx = { time: 0, trigger: key.startsWith('end-') ? { type: 'levelStateChanged', states: [key.slice(4)] } : null, derived: [] };
      if (r) visitFile(r[1], ctx, []); else if (object(script)) visit(script, ctx, name, []);
    }
    for (const chat of map.chats ?? []) visit(chat, { time: 0, trigger: { type: 'worldMapChat' }, derived: [] }, mapSources.get(name) ?? 'WorldMap.plist');
    level.spawns.sort((a, b) => a.time - b.time);
    level.initialPlayerUnits = level.spawns.filter(s => s.team === 0 && !s.trigger && s.time <= 2 && !s.unitId.includes('castle'));
    return level;
  });
  const unsupportedActions = {};
  for (const l of levels) for (const [type, count] of Object.entries(l.coverage.unsupportedActions)) unsupportedActions[type] = (unsupportedActions[type] ?? 0) + count;
  return { levels, coverage: { sourceLevels: levels.length, regions: [...new Set(levels.map(l => l.region))], levelsWithWaves: levels.filter(l => l.waves.length).length, waves: levels.reduce((n, l) => n + l.waves.length, 0), spawns: levels.reduce((n, l) => n + l.spawns.length, 0), commands: levels.reduce((n, l) => n + l.commands.length, 0), unsupportedActions, conditionalEvents: levels.reduce((n, l) => n + l.coverage.conditionalEvents.length, 0), triggeredEvents: levels.reduce((n, l) => n + l.coverage.triggeredEvents.length, 0), issues: levels.reduce((n, l) => n + l.coverage.issues.length, 0), fidelity: 'static-first-play-partial', selectionPolicy: 'seeded-weighted-unit-choice; seeded random lane; midpoint spawn delay; one finite repeat cycle; no condition evaluation except LevelCycle()==1', filesRead: [...cache.keys()].sort() } };
}
