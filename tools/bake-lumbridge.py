#!/usr/bin/env python3
"""
bake-lumbridge.py :: the cache-to-site terrain tool

Reads the OSRS cache (flatcache format, as published by abextm/osrs-cache),
decodes the real terrain around Lumbridge castle, and bakes it into the
compact LPATCH block that runehunter-index.html draws with its own rasterizer.

Setup (once):
    git clone --filter=blob:none --no-checkout https://github.com/abextm/osrs-cache
    cd osrs-cache && git sparse-checkout init && git sparse-checkout set 2.flatcache 5.flatcache
    git checkout HEAD -- 2.flatcache 5.flatcache

Run:
    python3 bake-lumbridge.py path/to/osrs-cache
    -> writes lum-patch.js (paste over the LPATCH line in runehunter-index.html)
    -> writes patch-preview.png (what got baked, in rail orientation)

Formats decoded here, all per RuneLite's cache module:
  - .flatcache: text lines; per group "id=", "contents=<base64 JS5 container>"
  - JS5 container: u8 compression (0 raw / 1 bzip2 sans header / 2 gzip), i32 len
  - config group unpack: chunk-size table at the tail (ArchiveFiles.java)
  - index 2 group 1: underlays, opcode 1 = rgb24
  - index 2 group 4: overlays, opcodes 1 rgb / 2 texture / 5 hideUnderlay / 7 rgb2
  - index 5, group id == regionId (x<<8|y): m-file terrain, u16 attribute opcodes
Rail mapping (rotA): page-forward = real west, page-right = real north, so the
camera arrives from Al Kharid, over the bridge, at the castle's east face.
The client blends underlay colours between tiles; overlays stay hard. Same here:
soft tiles get a 5x5 soft-only blur, water and floors stay crisp, and any land
tile touching water is pre-mixed toward bank mud.
Output byte per tile: palette index (6 bits) | hard flag (bit 6) | water (bit 7).
"""
import base64, bz2, gzip, io, struct, json, sys, os
from collections import Counter
try:
    from PIL import Image
except ImportError:
    Image = None

OC = sys.argv[1] if len(sys.argv) > 1 else "osrs-cache"
XC, YC = 3213, 3218          # castle courtyard centre, real coords
YOFF = 16                    # window slid north: less swamp, more farmland
N = 100
UC = VC = N // 2
BRIDGE = (3242, 3226)        # the real bridge over the Lum
MUD = (118, 96, 66)

def parse_flatcache(path):
    groups, cur = {}, None
    with open(path) as f:
        for line in f:
            line = line.rstrip("\n")
            i = line.index("=")
            k, v = line[:i], line[i+1:]
            if k == "id":
                cur = int(v); groups[cur] = {"files": []}
            elif k == "file":
                groups[cur]["files"].append(int(v.split("=")[0]))
            elif k == "contents":
                groups[cur]["contents"] = base64.b64decode(v)
    return groups

def decomp(b):
    c, ln = b[0], struct.unpack(">i", b[1:5])[0]
    if c == 0: return b[5:5+ln]
    payload = b[9:9+ln]
    if c == 1: return bz2.decompress(b"BZh1" + payload)
    if c == 2: return gzip.decompress(payload)
    raise ValueError(f"compression {c}")

def unpack_files(data, n):
    if n == 1: return [data]
    chunks = data[-1]
    p = len(data) - 1 - chunks * n * 4
    sizes = [0]*n
    for _ in range(chunks):
        acc = 0
        for i in range(n):
            acc += struct.unpack(">i", data[p:p+4])[0]; p += 4
            sizes[i] += acc
    out, p = [], 0
    for i in range(n):
        out.append(data[p:p+sizes[i]]); p += sizes[i]
    return out

def parse_configs(cfg):
    und, ovl = {}, {}
    g = cfg[1]
    for fid, d in zip(g["files"], unpack_files(decomp(g["contents"]), len(g["files"]))):
        s, rgb = io.BytesIO(d), None
        while (op := s.read(1)) and op[0]:
            if op[0] == 1: rgb = struct.unpack(">I", b"\0"+s.read(3))[0]
        und[fid] = rgb
    g = cfg[4]
    for fid, d in zip(g["files"], unpack_files(decomp(g["contents"]), len(g["files"]))):
        s, o = io.BytesIO(d), {"rgb": None, "tex": None}
        while (op := s.read(1)) and op[0]:
            if op[0] == 1: o["rgb"] = struct.unpack(">I", b"\0"+s.read(3))[0]
            elif op[0] == 2: o["tex"] = s.read(1)[0]
            elif op[0] == 7: s.read(3)
        ovl[fid] = o
    return und, ovl

def parse_m(data):
    s = io.BytesIO(data)
    u16 = lambda: struct.unpack(">H", s.read(2))[0]
    s16 = lambda: struct.unpack(">h", s.read(2))[0]
    tiles = [[[None]*64 for _ in range(64)] for _ in range(4)]
    for z in range(4):
        for x in range(64):
            for y in range(64):
                t = {"ov": 0, "un": 0}
                while True:
                    a = u16()
                    if a == 0: break
                    if a == 1: s.read(1); break
                    if a <= 49: t["ov"] = s16()
                    elif a <= 81: pass
                    else: t["un"] = a - 81
                tiles[z][x][y] = t
    return tiles

def main():
    cfg = parse_flatcache(os.path.join(OC, "2.flatcache"))
    UND, OVL = parse_configs(cfg)
    maps = parse_flatcache(os.path.join(OC, "5.flatcache"))
    regions = {}
    def tile_at(x, y):
        rx, ry = x // 64, y // 64
        if (rx, ry) not in regions:
            rid = (rx << 8) | ry
            regions[(rx, ry)] = parse_m(decomp(maps[rid]["contents"])) if rid in maps else None
        R = regions[(rx, ry)]
        return R[0][x % 64][y % 64] if R else None

    def classify(t):
        if t is None: return (76, 104, 48), False, False
        base = UND.get(t["un"]-1) if t["un"] else None
        base = base if base is not None else 0x4C6B2F
        hard = False
        if t["ov"]:
            o = OVL.get(t["ov"]-1)
            if o:
                if o["tex"] == 1 or (o["rgb"] is None and o["tex"] is not None):
                    return (52, 88, 140), True, False
                if o["rgb"] is not None and o["rgb"] != 0xFF00FF:
                    base = o["rgb"]; hard = True
        return ((base >> 16) & 255, (base >> 8) & 255, base & 255), False, hard

    M = 4
    grid, water, hard = {}, {}, {}
    for zr in range(-M, N+M):
        for xr in range(-M, N+M):
            rgb, w, h = classify(tile_at(XC + (zr - VC), YC + YOFF + (xr - UC)))
            grid[(zr,xr)], water[(zr,xr)], hard[(zr,xr)] = rgb, w, h

    out = [[None]*N for _ in range(N)]
    for z in range(N):
        for x in range(N):
            if water[(z,x)] or hard[(z,x)]:
                out[z][x] = grid[(z,x)]; continue
            rs = gs = bs = n = 0
            for dz in range(-2, 3):
                for dx in range(-2, 3):
                    k = (z+dz, x+dx)
                    if water.get(k) or hard.get(k): continue
                    c = grid.get(k)
                    if c: rs += c[0]; gs += c[1]; bs += c[2]; n += 1
            out[z][x] = (rs//n, gs//n, bs//n) if n else grid[(z,x)]

    wat = [[water[(z,x)] for x in range(N)] for z in range(N)]
    hrd = [[hard[(z,x)] for x in range(N)] for z in range(N)]
    for z in range(N):
        for x in range(N):
            if wat[z][x]: continue
            if any(0 <= z+dz < N and 0 <= x+dx < N and wat[z+dz][x+dx]
                   for dz, dx in ((0,1),(0,-1),(1,0),(-1,0))):
                r, g, b = out[z][x]
                out[z][x] = ((r+2*MUD[0])//3, (g+2*MUD[1])//3, (b+2*MUD[2])//3)
                hrd[z][x] = True

    cnt = Counter(out[z][x] for z in range(N) for x in range(N))
    pal = [c for c, _ in cnt.most_common(63)]
    pidx = {c: i for i, c in enumerate(pal)}
    nearest = lambda c: pidx.get(c) if c in pidx else min(
        range(len(pal)), key=lambda i: sum((pal[i][k]-c[k])**2 for k in range(3)))
    data = bytearray(nearest(out[z][x]) | (0x40 if hrd[z][x] else 0) | (0x80 if wat[z][x] else 0)
                     for z in range(N) for x in range(N))

    js = ("var LPATCH={n:%d,uc:%d,vc:%d,ub:%d,vb:%d,pal:%s,d:'%s'};" %
          (N, UC, VC, BRIDGE[1]-YC-YOFF+UC, BRIDGE[0]-XC+VC,
           json.dumps([v for c in pal for v in c], separators=(',', ':')),
           base64.b64encode(bytes(data)).decode()))
    open("lum-patch.js", "w").write(js)
    print(f"lum-patch.js written: {len(pal)} colours, {len(js)} bytes")

    if Image:
        img = Image.new("RGB", (N, N))
        px = img.load()
        for z in range(N):
            for x in range(N):
                px[x, z] = tuple(pal[data[z*N+x] & 0x3F])
        img.resize((N*5, N*5), Image.NEAREST).save("patch-preview.png")
        print("patch-preview.png written")

if __name__ == "__main__":
    main()
