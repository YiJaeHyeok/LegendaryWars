import fs from 'node:fs';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const d=JSON.parse(fs.readFileSync(path.join(root,'app/src/main/assets/game-data.json')));
const lines=d.units.map(u=>[u.id,u.hp,u.attack,u.defense,u.speed,u.cost,u.cooldown,u.range,u.interval,u.hero,u.unlock].join('\t'));
fs.mkdirSync(path.join(root,'build'),{recursive:true});fs.writeFileSync(path.join(root,'build/unit-specs.tsv'),lines.join('\n'));
