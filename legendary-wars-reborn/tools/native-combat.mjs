// Static bytes only. The extracted ARM ELF is never loaded, linked, or run.
import fs from 'node:fs';
import zlib from 'node:zlib';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {Archive,hash,crc32} from './asset-safety.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const input=path.join(path.dirname(root),'outputs/legendary-wars-analysis/samples/original.apk');
const zip=new Archive(input,'062e16b02595cc577527cd0f15c0a9f4d6cb36b6575bedf7cd226e5e0ab28b48'),bytes=fs.readFileSync(input);
const e=zip.entries.get('lib/armeabi/libverde.so'),p=e.local;
if(e.size>12_000_000||bytes.readUInt32LE(p)!==0x04034b50)throw Error('ELF entry bounds');
const start=p+30+bytes.readUInt16LE(p+26)+bytes.readUInt16LE(p+28),raw=bytes.subarray(start,start+e.compressed);
const b=e.method===8?zlib.inflateRawSync(raw,{maxOutputLength:12_000_000}):raw;
if(b.length!==e.size||crc32(b)!==e.crc||b.toString('hex',0,6)!=='7f454c460101')throw Error('ELF integrity');
const dest=path.join(path.dirname(root),'outputs/legendary-wars-analysis/samples/libverde.static-only.so');fs.writeFileSync(dest,b);
const shoff=b.readUInt32LE(32),shsize=b.readUInt16LE(46),count=b.readUInt16LE(48),sections=[];
if(count>200||shoff+count*shsize>b.length)throw Error('ELF section bounds');
for(let i=0;i<count;i++){const p=shoff+i*shsize;sections.push({type:b.readUInt32LE(p+4),addr:b.readUInt32LE(p+12),off:b.readUInt32LE(p+16),size:b.readUInt32LE(p+20),link:b.readUInt32LE(p+24),entry:b.readUInt32LE(p+36)});}
const symbols=[];for(const s of sections)if(s.type===2||s.type===11){const str=sections[s.link];if(!str||!s.entry||s.size/s.entry>200_000)throw Error('ELF symbol bounds');for(let p=s.off;p<s.off+s.size;p+=s.entry){const n=b.readUInt32LE(p),end=b.indexOf(0,str.off+n);if(end<0||end>str.off+str.size)continue;const name=b.toString('utf8',str.off+n,end);if(/(UnitCustomizer|UnitNode|UnitAnimation|UnitPower|Controller|Hunt|Guard|Damage|Attack|AnimationRenderer)/i.test(name))symbols.push({name,address:b.readUInt32LE(p+4),size:b.readUInt32LE(p+8)});}}
const strings=[...b.toString('latin1').matchAll(/[ -~]{5,}/g)].filter(m=>/scaleSpeed|fps|[Ff]rame[Rr]ate|[Hh]aste|attackSpeed|damage|refreshTime/i.test(m[0])).map(m=>({offset:m.index,value:m[0].slice(0,300)}));
const report={path:dest,sha256:hash(b),sections,symbols,strings};fs.writeFileSync(path.join(root,'reference/native-combat.json'),JSON.stringify(report,null,2));console.log(JSON.stringify({path:dest,sha256:hash(b),symbolCount:symbols.length,strings}));
