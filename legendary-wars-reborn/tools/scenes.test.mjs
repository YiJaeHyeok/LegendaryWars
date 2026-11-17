import test from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';import {compileScenes} from './compile-scenes.mjs';
const data=JSON.parse(fs.readFileSync(new URL('../app/src/main/assets/game-data.json',import.meta.url)));
test('source centered title banner maps to original 480x320 composition',()=>{
  const scene=compileScenes(data.originalLayout,data);const banner=scene.title.draw.find(v=>v.frame==='mainmenu-title-logobanner.png');assert.deepEqual(banner.rect,[174.25,112.5,931.5,279]);
});
test('campaign hit area comes from original source polygon and remains on viewport',()=>{
 const b=compileScenes(data.originalLayout,data).props.controls.find(v=>v.id==='campaign').rect;assert.ok(b[0]>700&&b[0]<800&&b[1]<40);assert.ok(b[2]>400&&b[3]>350);
});
