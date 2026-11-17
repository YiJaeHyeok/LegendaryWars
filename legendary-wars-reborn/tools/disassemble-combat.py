"""Read ARM ELF / Objective-C metadata as bytes; never import the sample."""
import struct,json,pathlib
from capstone import Cs,CS_ARCH_ARM,CS_MODE_ARM,CS_MODE_THUMB
root=pathlib.Path(__file__).resolve().parents[1]
b=(root.parent/'outputs/legendary-wars-analysis/samples/libverde.static-only.so').read_bytes()
u32=lambda p:struct.unpack_from('<I',b,p)[0]
u16=lambda p:struct.unpack_from('<H',b,p)[0]
def cstr(p):
    if not 0<=p<len(b):return ''
    end=b.find(b'\0',p,min(len(b),p+600));return b[p:end].decode('utf8','replace') if end>=p else ''
shoff=u32(32);entsize=u16(46);count=u16(48);namesHeader=shoff+u16(50)*entsize;names=u32(namesHeader+16)
sections=[]
for i in range(count):
    p=shoff+i*entsize;sections.append(dict(name=cstr(names+u32(p)),type=u32(p+4),addr=u32(p+12),off=u32(p+16),size=u32(p+20)))
def off(addr):
    for s in sections:
        if s['type']!=8 and s['addr']<=addr<s['addr']+s['size']:return s['off']+addr-s['addr']
    raise ValueError(hex(addr))
def ptr(addr):return u32(off(addr))
def string(addr):
    try:return cstr(off(addr))
    except ValueError:return ''
classes=[]
for s in sections:
    if 'classlist' not in s['name']:continue
    for p in range(s['off'],s['off']+s['size'],4):
        addr=u32(p)
        try:
            ro=ptr(addr+16)&~3;name=string(ptr(ro+16));ml=ptr(ro+20)
            if not ml:continue
            mo=off(ml);entry=u32(mo)&0xffff;num=u32(mo+4)
            if entry<12 or num>3000:continue
            methods=[]
            for j in range(num):
                q=mo+8+j*entry;method=string(u32(q));imp=u32(q+8)
                methods.append(dict(name=method,imp=imp))
            ivars=[];iv=ptr(ro+28)
            if iv:
                io=off(iv);step=u32(io);n=u32(io+4)
                if 20<=step<100 and n<1000:
                    for j in range(n):
                        q=io+8+j*step
                        ivars.append(dict(name=string(u32(q+4)),offset=ptr(u32(q))))
            classes.append(dict(name=name,address=addr,methods=methods,ivars=ivars))
        except (ValueError,struct.error):pass
(root/'reference/native-objc-methods.json').write_text(json.dumps(dict(sections=sections,classes=classes),indent=2),encoding='utf8')
wanted={'WaveCustomizer','UnitRenderer','UnitNode','UnitPower','UnitAnimation','Damage','AttackPower','UnitController','UnitAI','HuntAI','GuardAI','LWUnit','UnitEntity','UnitAction','AnimatedUnit','LVAnimationInfo'}
results=[];allimps=sorted(set(m['imp']&~1 for c in classes for m in c['methods'] if m['imp']))
for cl in classes:
    if cl['name'] in wanted or 'ustomizer' in cl['name'] or 'Unit' in cl['name'] and ('Action' in cl['name'] or 'AI' in cl['name']):
        print(cl['name'],[(m['name'],hex(m['imp'])) for m in cl['methods']])
        for m in cl['methods']:
            if any(k in m['name'].lower() for k in ['custom','animation','damage','speed','frame','init','attack','update','guard','hunt','apply']):
                a=m['imp']&~1
                try:
                    end=next((v for v in allimps if v>a),a+8000);code=b[off(a):off(a)+min(end-a,12000)]
                    md=Cs(CS_ARCH_ARM,CS_MODE_THUMB if m['imp']&1 else CS_MODE_ARM)
                    md.skipdata=True
                    lines=[]
                    for ins in md.disasm(code,a):
                        lines.append(f'{ins.address:08x}  {ins.mnemonic:10} {ins.op_str}')
                    results.append(dict(classname=cl['name'],method=m['name'],address=hex(m['imp']),assembly=lines))
                except ValueError:pass
(root/'reference/native-combat-assembly.json').write_text(json.dumps(results,indent=2),encoding='utf8')
print('classes',len(classes),'functions',len(results))
addresses=[0x480808,0x480890,0x480a20,0x480aa4,0x480a4c,0x480ad8,0x480d58,0x582344,0x582588,0x480900,0x480944,0x480970,0x4809c0]
for a in addresses:
    md=Cs(CS_ARCH_ARM,CS_MODE_ARM);lines=[]
    for ins in md.disasm(b[off(a):off(a)+350],a):
        lines.append(f'{ins.address:08x}  {ins.mnemonic:10} {ins.op_str}')
        if ins.mnemonic in ('bx','pop') and ('lr' in ins.op_str or 'pc' in ins.op_str):break
    results.append(dict(classname='C model helpers',method=hex(a),assembly=lines))
(root/'reference/native-combat-assembly.json').write_text(json.dumps(results,indent=2),encoding='utf8')
# Bounded constant propagation across static loads; this does not emulate code.
import re
regs={};notes=[]
for ins in Cs(CS_ARCH_ARM,CS_MODE_ARM).disasm(b[off(0x4f6eb8):off(0x4f70e0)],0x4f6eb8):
    text=ins.op_str;value=None
    m=re.fullmatch(r'(r\d+|sl|fp|ip), \[pc, #(0x[0-9a-f]+|\d+)\]',text)
    if ins.mnemonic=='ldr' and m:value=ptr(ins.address+8+int(m[2],0))
    m2=re.fullmatch(r'(r\d+|sl|fp|ip), (pc|r\d+|sl|fp|ip), (r\d+|sl|fp|ip)',text)
    if ins.mnemonic=='add' and m2:
        x=ins.address+8 if m2[2]=='pc' else regs.get(m2[2]);y=regs.get(m2[3]);value=(x+y)&0xffffffff if x is not None and y is not None else None
    m3=re.fullmatch(r'(r\d+|sl|fp|ip), \[(r\d+|sl|fp|ip), (r\d+|sl|fp|ip)\]',text)
    if ins.mnemonic=='ldr' and m3:
        x=regs.get(m3[2]);y=regs.get(m3[3])
        try:value=ptr((x+y)&0xffffffff) if x is not None and y is not None else None
        except ValueError:value=None
    dest=text.split(',')[0]
    if ins.mnemonic in ('ldr','add','mov'):
        regs[dest]=value
        if value is not None:notes.append(dict(address=hex(ins.address),instruction=ins.mnemonic+' '+text,value=hex(value)))
    if ins.mnemonic=='bl':
        notes.append(dict(address=hex(ins.address),call=text,regs=dict(regs)))
        for reg in ['r0','r1','r2','r3','ip']:regs.pop(reg,None)
(root/'reference/native-customizer-loads.json').write_text(json.dumps(notes,indent=2),encoding='utf8')
md=Cs(CS_ARCH_ARM,CS_MODE_ARM);md.skipdata=True;inst=list(md.disasm(b[off(0x47f000):off(0x489000)],0x47f000));windows=[]
for i,ins in enumerate(inst):
    if ins.mnemonic=='ldr' and any(k in ins.op_str for k in ['#0x94]','#0x98]','#0x9c]','#0xa0]']):
        windows.append([f'{x.address:08x} {x.mnemonic:10} {x.op_str}' for x in inst[max(0,i-8):i+24]])
(root/'reference/native-speed-windows.json').write_text(json.dumps(windows,indent=2),encoding='utf8')
