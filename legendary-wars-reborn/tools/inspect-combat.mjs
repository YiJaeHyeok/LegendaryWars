import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {Archive,parsePlist,hash} from './asset-safety.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const input=path.join(path.dirname(root),'outputs/legendary-wars-analysis/samples/original.apk');
const zip=new Archive(input,'062e16b02595cc577527cd0f15c0a9f4d6cb36b6575bedf7cd226e5e0ab28b48');
const units=parsePlist(zip.read('assets/Units.plist').toString('utf8'));
const selected=['knighty','elfy','dwarfy','faun','rogue','unicorn','sprite','golem','centaur','healer','wizard','phoenix','skully','zombie','wolfy','vampy','troll','gargoyle','skullyarcher','reaper','miney'];
const report={source:hash(fs.readFileSync(input)),units:Object.fromEntries(selected.map(id=>[id,units[id]])),relatedFiles:[...zip.entries.keys()].filter(n=>/AI|[Cc]ombat|[Cc]ontroller|[Ss]peed|[Dd]amage|[Pp]ower/.test(n)&&n.endsWith('.plist'))};
fs.writeFileSync(path.join(root,'reference/combat-source.json'),JSON.stringify(report,null,2));
console.log(JSON.stringify({units:selected.length,relatedFiles:report.relatedFiles,native:[...zip.entries.keys()].filter(n=>n.endsWith('.so'))}));
const defaults=[];
for(const n of zip.entries.keys())if(n.endsWith('.plist')){const text=zip.read(n).toString('utf8');if(/default.*fps|fps.*default|[Aa]nimation[Ff]ps|frameRate|[Aa]ttack[Ss]peed|haste|[Aa]nimation[Ss]peed/.test(text))defaults.push({file:n,matches:text.split(/\r?\n/).filter(l=>/default.*fps|fps.*default|[Aa]nimation[Ff]ps|frameRate|[Aa]ttack[Ss]peed|haste|[Aa]nimation[Ss]peed/.test(l)).slice(0,8)});}
console.log(JSON.stringify(defaults));
