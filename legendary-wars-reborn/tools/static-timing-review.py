"""Bounded ARM instruction reading only; the original library is never loaded."""
import json, pathlib, re, struct, sys
from capstone import Cs, CS_ARCH_ARM, CS_MODE_ARM

ROOT = pathlib.Path(__file__).resolve().parents[1]
B = (ROOT.parent / 'outputs/legendary-wars-analysis/samples/libverde.static-only.so').read_bytes()
META = json.loads((ROOT / 'reference/native-objc-methods.json').read_text(encoding='utf8'))
SECTIONS = META['sections']
def off(a):
    for s in SECTIONS:
        if s['type'] != 8 and s['addr'] <= a < s['addr'] + s['size']:
            return s['off'] + a - s['addr']
    raise ValueError(hex(a))
def word(a): return struct.unpack_from('<I', B, off(a))[0]
def disasm(a, size):
    md = Cs(CS_ARCH_ARM, CS_MODE_ARM)
    md.skipdata = True
    lines = []
    for i in md.disasm(B[off(a):off(a)+size], a):
        note = ''
        m = re.search(r'\[pc, #(-?0x[0-9a-f]+|-?\d+)\]', i.op_str)
        if i.mnemonic == 'ldr' and m:
            try:
                v = word(i.address + 8 + int(m[1], 0))
                f = struct.unpack('<f', struct.pack('<I', v))[0]
                note = f' ; literal=0x{v:x} float={f:g}'
            except ValueError: pass
        lines.append(f'{i.address:08x} {i.mnemonic:10} {i.op_str}{note}')
    return lines
if __name__ == '__main__':
    for arg in sys.argv[1:]:
        address, _, size = arg.partition(':')
        a, n = int(address, 0), int(size, 0) if size else 512
        if n > 20000: raise ValueError('bounded window required')
        print('\n'.join(disasm(a, n)))
