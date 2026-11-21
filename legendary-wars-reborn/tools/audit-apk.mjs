import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import {fileURLToPath} from 'node:url';
import {Archive,hash,crc32,validatePng,validateFont,validateVorbis,validateMp3,validateWav} from './asset-safety.mjs';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const apkPath=path.join(path.dirname(root),'dist/legendary-wars-offline.apk');
const bytes=fs.readFileSync(apkPath),apkSha=hash(bytes),zip=new Archive(apkPath,apkSha);
const original=JSON.parse(fs.readFileSync(path.join(root,'asset-provenance.json')));
const expected=new Map();
for(const f of original.files){if(f.use==='unchanged PNG')expected.set('assets/art/'+path.posix.basename(f.path),f.sha256);if(f.use==='validated WAV')expected.set('assets/audio/'+path.posix.basename(f.path),f.sha256);if(['validated Ogg Vorbis','validated MP3','validated TrueType'].includes(f.use)){if(!/^(audio|fonts)\/[A-Za-z0-9_.-]+\.(ogg|mp3|ttf)$/.test(f.outputPath))throw Error('Provenance output path');expected.set('assets/'+f.outputPath,f.sha256);}}
expected.set('assets/game-data.json',hash(fs.readFileSync(path.join(root,'app/src/main/assets/game-data.json'))));
function read(name){
  const e=zip.entries.get(name);if(!e||e.size>16_000_000)throw Error('Entry limit '+name);
  const p=e.local;if(bytes.readUInt32LE(p)!==0x04034b50)throw Error('Entry header');
  const start=p+30+bytes.readUInt16LE(p+26)+bytes.readUInt16LE(p+28),raw=bytes.subarray(start,start+e.compressed);
  const b=e.method===0?raw:e.method===8?zlib.inflateRawSync(raw,{maxOutputLength:16_000_000}):null;
  if(!b||b.length!==e.size||crc32(b)!==e.crc)throw Error('Entry integrity '+name);return b;
}
let assetCount=0;
for(const name of zip.entries.keys()){
  if(/(?:^|\/)\.\.(?:\/|$)|\\|^\//.test(name))throw Error('Unsafe package path');
  if(/\.(so|jar|js|html|exe|sh|plist)$/i.test(name))throw Error('Forbidden original/executable payload '+name);
  if(name.endsWith('.dex')&&name!=='classes.dex')throw Error('Unexpected code file');
  if(name.startsWith('assets/')){
    const b=read(name);if(!expected.has(name)||hash(b)!==expected.get(name))throw Error('Asset provenance mismatch '+name);
    if(name.endsWith('.png'))validatePng(b);if(name.endsWith('.ogg'))validateVorbis(b);if(name.endsWith('.mp3'))validateMp3(b);if(name.endsWith('.wav'))validateWav(b);if(name.endsWith('.ttf'))validateFont(b);assetCount++;
  }
}
if(assetCount!==expected.size)throw Error('Missing approved asset');
const dex=read('classes.dex');
if(dex.toString('ascii',0,4)!=='dex\n')throw Error('DEX format');
const strings=[];
for(let i=0;i<dex.readUInt32LE(56);i++){
  let p=dex.readUInt32LE(dex.readUInt32LE(60)+i*4),n;do{n=dex[p++];}while(n&128);
  strings.push(dex.toString('utf8',p,dex.indexOf(0,p)));
}
const types=Array.from({length:dex.readUInt32LE(64)},(_,i)=>strings[dex.readUInt32LE(dex.readUInt32LE(68)+i*4)]);
const classes=Array.from({length:dex.readUInt32LE(96)},(_,i)=>types[dex.readUInt32LE(dex.readUInt32LE(100)+i*32)]);
if(classes.some(c=>!c.startsWith('Lcom/legendary/offline/')))throw Error('Unexpected compiled class');
const forbiddenTypes=types.filter(t=>/^L(?:java\/net\/|javax\/net\/|java\/lang\/reflect\/|java\/lang\/ProcessBuilder;|dalvik\/system\/|android\/webkit\/|android\/telephony\/)/.test(t));
if(forbiddenTypes.length)throw Error('Forbidden API types '+forbiddenTypes.join(','));
const methodNames=Array.from({length:dex.readUInt32LE(88)},(_,i)=>strings[dex.readUInt32LE(dex.readUInt32LE(92)+i*8+4)]);
if(methodNames.some(n=>['exec','loadLibrary','loadClass','getRuntimePermission'].includes(n)))throw Error('Unexpected process/dynamic-code API');
const urls=strings.filter(s=>/^https?:\/\//i.test(s));if(urls.length)throw Error('Network URL in application code');
const report={apkPath,apkSha256:apkSha,bytes:bytes.length,approvedAssets:assetCount,declaredClasses:classes,forbiddenApiTypeReferences:forbiddenTypes,applicationCodeHttpUrls:urls,nativeLibraries:[...zip.entries.keys()].filter(n=>n.endsWith('.so')),originalExecutableCodeIncluded:false,scope:'Packaged code/type inventory and exact source asset hashes; not an antivirus certification or runtime proof.'};
fs.mkdirSync(path.join(root,'build'),{recursive:true});fs.writeFileSync(path.join(root,'build/apk-security-audit.json'),JSON.stringify(report,null,2));
console.log(JSON.stringify(report,null,2));
