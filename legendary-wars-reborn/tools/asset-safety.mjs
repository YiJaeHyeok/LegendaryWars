import fs from 'node:fs';
import zlib from 'node:zlib';
import crypto from 'node:crypto';

export const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
const crcTable=Array.from({length:256},(_,n)=>{for(let i=0;i<8;i++)n=n&1?0xedb88320^(n>>>1):n>>>1;return n>>>0;});
export function crc32(b){let c=0xffffffff;for(const v of b)c=crcTable[(c^v)&255]^(c>>>8);return (c^0xffffffff)>>>0;}
export function safeName(name){return /^assets\/[A-Za-z0-9_. -]+\.(png|plist|wav|mp3|ttf)$/.test(name)&&!name.includes('..');}

export function validateFont(b){
  if(b.length<28||b.length>3_000_000||b.readUInt32BE(0)!==0x00010000)throw Error('Invalid TrueType font');
  const count=b.readUInt16BE(4);if(count<1||count>128||12+count*16>b.length)throw Error('Font table bounds');
  for(let i=0;i<count;i++){const p=12+i*16,start=b.readUInt32BE(p+8),length=b.readUInt32BE(p+12);if(start>b.length||length>b.length-start)throw Error('Font table outside file');}
  return {tables:count};
}
export function validateMp3(b){
  if(b.length<256||b.length>12_000_000)throw Error('Invalid MP3 size');
  let p=0,frames=0;
  if(b.toString('ascii',0,3)==='ID3'){
    if(b[6]&128||b[7]&128||b[8]&128||b[9]&128)throw Error('MP3 ID3 size');
    p=10+(b[6]<<21)+(b[7]<<14)+(b[8]<<7)+b[9]+((b[5]&16)?10:0);
  }
  const high=[0,32,40,48,56,64,80,96,112,128,160,192,224,256,320,0];
  const low=[0,8,16,24,32,40,48,56,64,80,96,112,128,144,160,0];
  while(p+4<=b.length){
    if(b.length-p===128&&b.toString('ascii',p,p+3)==='TAG'){p=b.length;break;}
    if(b.length-p<=2048&&b.subarray(p).every(x=>x===0)){p=b.length;break;}
    const h=b.readUInt32BE(p),version=(h>>>19)&3,layer=(h>>>17)&3,bi=(h>>>12)&15,fi=(h>>>10)&3;
    if((h>>>21)!==0x7ff||version===1||layer!==1||fi===3)throw Error('Invalid MPEG Layer3 frame');
    const rate=[44100,48000,32000][fi]/(version===3?1:version===2?2:4),br=(version===3?high:low)[bi];
    const length=Math.floor((version===3?144000:72000)*br/rate)+((h>>>9)&1);
    if(!br||length<24||p+length>b.length)throw Error('MP3 frame bounds');p+=length;frames++;
  }
  if(frames<2||p!==b.length)throw Error('Incomplete MP3 frames');return {frames};
}

export function validateWav(b){
  if(b.length<44||b.length>12_000_000||b.toString('ascii',0,4)!=='RIFF'||b.toString('ascii',8,12)!=='WAVE'||b.readUInt32LE(4)+8!==b.length)throw Error('Invalid WAV container');
  let p=12,fmt=false,samples=false,channels=0,sampleRate=0,bitsPerSample=0,dataBytes=0;
  while(p+8<=b.length){
    const n=b.readUInt32LE(p+4);if(p+8+n>b.length)throw Error('WAV chunk bounds');
    const id=b.toString('ascii',p,p+4);
    if(id==='fmt '){
      if(n<16)throw Error('WAV fmt too short');
      const formatTag=b.readUInt16LE(p+8);
      if(![1,3,17].includes(formatTag))throw Error('Unsupported WAV format');
      channels=b.readUInt16LE(p+10);sampleRate=b.readUInt32LE(p+12);bitsPerSample=b.readUInt16LE(p+22);
      if(channels<1||channels>2||sampleRate<8000||sampleRate>96000)throw Error('Invalid WAV audio params');
      fmt=true;
    }
    if(id==='data'){samples=true;dataBytes=n;}
    p+=8+n+(n&1);
  }
  if(!fmt||!samples||dataBytes<1)throw Error('Incomplete WAV');
  return {channels,sampleRate,bitsPerSample,dataBytes};
}

export function validateVorbis(b){
  if(b.length<60||b.length>12_000_000)throw Error('Invalid Ogg size');
  let p=0,pages=0,serial=null,sequence=0;
  while(p<b.length){
    if(p+27>b.length||b.toString('ascii',p,p+4)!=='OggS'||b[p+4]!==0)throw Error('Ogg page header');
    const n=b[p+26],head=27+n;if(p+head>b.length)throw Error('Ogg lacing bounds');
    let payload=0;for(let i=0;i<n;i++)payload+=b[p+27+i];const length=head+payload;
    if(p+length>b.length)throw Error('Ogg page bounds');
    const s=b.readUInt32LE(p+14),seq=b.readUInt32LE(p+18);if(serial===null)serial=s;if(s!==serial||seq!==sequence++)throw Error('Ogg stream order');
    let crc=0;for(let i=0;i<length;i++){const byte=i>=22&&i<=25?0:b[p+i];crc^=byte<<24;for(let k=0;k<8;k++)crc=(crc&0x80000000)?(crc<<1)^0x04c11db7:crc<<1;}
    if((crc>>>0)!==b.readUInt32LE(p+22))throw Error('Ogg CRC');
    if(pages===0&&(b[p+head]!==1||b.toString('ascii',p+head+1,p+head+7)!=='vorbis'))throw Error('Not Vorbis audio');
    p+=length;pages++;
  }
  if(pages<2)throw Error('Incomplete Ogg stream');return {pages,codec:'vorbis'};
}

export function parsePlist(text) {
  if(text.length>2_000_000||/<!ENTITY|<!\[CDATA|<!DOCTYPE[^>]*\[/i.test(text))throw Error('Unsafe entity/doctype');
  const declarations=text.match(/<!DOCTYPE[^>]*>/g)??[];
  if(declarations.some(d=>!/^<!DOCTYPE plist PUBLIC "-\/\/Apple(?: Computer)?\/\/DTD PLIST 1\.0\/\/EN" "http:\/\/www\.apple\.com\/DTDs\/PropertyList-1\.0\.dtd">$/.test(d)))throw Error('Unrecognized doctype');
  const clean=text.replace(/<\?[\s\S]*?\?>|<!DOCTYPE[^>]*>|<!--[\s\S]*?-->/g,'').trim();
  const tokens=clean.match(/<[^>]+>|[^<]+/g)??[];
  let i=0,nodes=0;
  function whitespace(){while(i<tokens.length&&!tokens[i].trim())i++;}
  function decode(s){return s.replace(/&([^;]+);/g,(_,e)=>{const map={amp:'&',lt:'<',gt:'>',quot:'"',apos:"'"};if(e in map)return map[e];if(/^#(?:[0-9]+|x[0-9a-f]+)$/i.test(e)){const n=e[1]==='x'?parseInt(e.slice(2),16):Number(e.slice(1));if(n>0&&n<=0x10ffff)return String.fromCodePoint(n);}throw Error('Unknown entity');});}
  function node(depth=0){
    whitespace();if(depth>64||++nodes>100000)throw Error('Plist limit');
    const token=tokens[i++];const m=/^<(dict|array|key|string|integer|real|true|false|date|data)(\s*\/)?>$/.exec(token??'');
    if(!m)throw Error('Invalid plist element');const tag=m[1];
    if(m[2])return tag==='true'?true:tag==='false'?false:tag==='dict'?{}:tag==='array'?[]:'';
    if(tag==='dict'||tag==='array'){
      const out=tag==='dict'?{}:[];
      while(true){whitespace();if(tokens[i]===`</${tag}>`){i++;return out;}
        if(tag==='array'){out.push(node(depth+1));continue;}
        if(tokens[i]!=='<key>')throw Error('Dictionary key expected');const key=node(depth+1);
        if(['__proto__','constructor','prototype'].includes(key)||Object.hasOwn(out,key))throw Error('Duplicate/unsafe key');
        out[key]=node(depth+1);
      }
    }
    let s='';while(i<tokens.length&&tokens[i]!==`</${tag}>`){if(tokens[i].startsWith('<'))throw Error('Nested scalar');s+=tokens[i++];}
    if(tokens[i++]!==`</${tag}>`)throw Error('Unclosed scalar');s=decode(s);
    if(tag==='integer'||tag==='real'){const v=Number(s);if(!Number.isFinite(v))throw Error('Invalid number');return v;}
    if(tag==='true'||tag==='false')return tag==='true';return s;
  }
  whitespace();if(!/^<plist(?: version="1\.0")?>$/.test(tokens[i++]??''))throw Error('Missing plist root');
  const value=node();whitespace();if(tokens[i++]!=='</plist>')throw Error('Missing plist end');whitespace();if(i!==tokens.length)throw Error('Trailing XML');return value;
}

export function atlasFrame(frame,width,height){
  const numbers=s=>(String(s).match(/-?\d+(?:\.\d+)?/g)??[]).map(Number);
  const f=numbers(frame.frame??frame.textureRect),sz=numbers(frame.sourceSize??frame.spriteSourceSize);
  if(f.length!==4||sz.length!==2)throw Error('Invalid atlas bounds');
  const rotated=frame.rotated===true||frame.textureRotated===true;
  const rect=[f[0],f[1],rotated?f[3]:f[2],rotated?f[2]:f[3]];
  if(rect.some(x=>!Number.isInteger(x)||x<0)||rect[2]<1||rect[3]<1||rect[0]+rect[2]>width||rect[1]+rect[3]>height||sz.some(x=>x<1||x>8192))throw Error('Atlas out of bounds');
  const trim=frame.sourceColorRect?numbers(frame.sourceColorRect):[(sz[0]-f[2])/2,(sz[1]-f[3])/2,f[2],f[3]];
  if(trim.length!==4||trim.some(x=>!Number.isFinite(x)))throw Error('Invalid trim');
  return {rect,trim,size:sz,rotated};
}

export function validatePng(b){
  if(b.length<45||b.length>12_000_000||b.toString('hex',0,8)!=='89504e470d0a1a0a')throw Error('Invalid PNG signature');
  let p=8,width=0,height=0,idat=false,end=false;
  while(p+12<=b.length){const n=b.readUInt32BE(p),type=b.toString('ascii',p+4,p+8);
    if(n>12_000_000||p+n+12>b.length)throw Error('PNG chunk bounds');
    if(crc32(b.subarray(p+4,p+8+n))!==b.readUInt32BE(p+8+n))throw Error('PNG CRC');
    if(p===8){if(type!=='IHDR'||n!==13)throw Error('PNG IHDR');width=b.readUInt32BE(p+8);height=b.readUInt32BE(p+12);if(!width||!height||width>4096||height>4096||width*height>8_388_608)throw Error('PNG dimensions');}
    if(type==='IDAT')idat=true;
    if(type==='IEND'){if(n!==0)throw Error('PNG IEND');end=true;p+=12;break;}
    p+=n+12;
  }
  if(!idat||!end||p!==b.length)throw Error('PNG incomplete/trailing data');
  return {width,height};
}

export class Archive {
  constructor(file,expectedHash){
    if(fs.statSync(file).size>260_000_000)throw Error('Archive too large');
    this.bytes=fs.readFileSync(file);if(hash(this.bytes)!==expectedHash)throw Error('Source hash changed');
    const b=this.bytes;let e=b.length-22;
    while(e>=Math.max(0,b.length-65557)&&b.readUInt32LE(e)!==0x06054b50)e--;
    if(e<Math.max(0,b.length-65557))throw Error('No ZIP directory');
    const count=b.readUInt16LE(e+10);let p=b.readUInt32LE(e+16);this.entries=new Map();
    for(let i=0;i<count;i++){
      if(p+46>b.length||b.readUInt32LE(p)!==0x02014b50)throw Error('ZIP directory bounds');
      const nl=b.readUInt16LE(p+28),name=b.toString('utf8',p+46,p+46+nl);
      if(this.entries.has(name))throw Error('Duplicate ZIP entry');
      this.entries.set(name,{size:b.readUInt32LE(p+24),compressed:b.readUInt32LE(p+20),method:b.readUInt16LE(p+10),flags:b.readUInt16LE(p+8),crc:b.readUInt32LE(p+16),local:b.readUInt32LE(p+42),attrs:b.readUInt32LE(p+38)});
      p+=46+nl+b.readUInt16LE(p+30)+b.readUInt16LE(p+32);
    }
  }
  read(name){
    if(!safeName(name))throw Error('Non-allowlisted path');const e=this.entries.get(name);if(!e)throw Error('Missing resource '+name);
    const b=this.bytes,p=e.local;if(e.size>12_000_000||e.flags&1||((e.attrs>>>16)&0xf000)===0xa000||p+30>b.length||b.readUInt32LE(p)!==0x04034b50)throw Error('Unsafe ZIP entry');
    const start=p+30+b.readUInt16LE(p+26)+b.readUInt16LE(p+28);if(start+e.compressed>b.length)throw Error('ZIP data bounds');
    const raw=b.subarray(start,start+e.compressed),data=e.method===0?raw:e.method===8?zlib.inflateRawSync(raw,{maxOutputLength:12_000_000}):null;
    if(!data||data.length!==e.size||crc32(data)!==e.crc)throw Error('ZIP integrity');return data;
  }
}
