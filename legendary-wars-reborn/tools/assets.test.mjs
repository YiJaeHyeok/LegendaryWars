import test from 'node:test';
import assert from 'node:assert/strict';
import { parsePlist, atlasFrame, validatePng, safeName,validateFont,validateMp3 } from './asset-safety.mjs';

test('external entities cannot become parsed values',()=>{
  assert.throws(()=>parsePlist('<!DOCTYPE plist [<!ENTITY x SYSTEM "file:///private">]><plist><string>&x;</string></plist>'),/entity|doctype/i);
});
test('plist preserves typed data without evaluating embedded expressions',()=>{
  assert.deepEqual(parsePlist('<plist><dict><key>hp</key><integer>75</integer><key>enabled</key><true/><key>caption</key><string>&lt;script&gt;</string></dict></plist>'),{hp:75,enabled:true,caption:'<script>'});
});
test('rotated atlas uses physical swapped dimensions and logical trim',()=>{
  const f=atlasFrame({frame:'{{402,330},{95,99}}',rotated:true,sourceColorRect:'{{33,105},{95,99}}',sourceSize:'{187,207}'},1024,1024);
  assert.deepEqual(f.rect,[402,330,99,95]);
  assert.deepEqual(f.trim,[33,105,95,99]);
  assert.deepEqual(f.size,[187,207]);
});
test('out-of-bounds atlas is refused',()=>{
  assert.throws(()=>atlasFrame({frame:'{{1010,0},{100,100}}',sourceSize:'{100,100}'},1024,1024),/bounds/);
});
test('extension alone does not permit an executable disguised as PNG',()=>{
  assert.throws(()=>validatePng(Buffer.from('MZ pretend image')),/PNG/);
});
test('archive traversal and executable names cannot be selected',()=>{
  assert.equal(safeName('../evil.png'),false);
  assert.equal(safeName('assets/../../evil.png'),false);
  assert.equal(safeName('assets/evil.so'),false);
  assert.equal(safeName('assets/knighty-hd.png'),true);
});
test('font file with a table outside its bytes is refused',()=>{
  const b=Buffer.alloc(32);b.writeUInt32BE(0x00010000,0);b.writeUInt16BE(1,4);b.write('head',12);b.writeUInt32BE(999,20);b.writeUInt32BE(20,24);
  assert.throws(()=>validateFont(b),/font|table/i);
});
test('an executable renamed as music cannot be imported',()=>{
  assert.throws(()=>validateMp3(Buffer.from('MZ this is not MPEG audio')),/MP3|MPEG/i);
});
