#!/usr/bin/env python3
"""Packs build/cldc classes + res/ into the final JAR/JAD.

  python3 tools/package.py dist                 full map
  MAP_REGION=i0,j0,i1,j1 python3 tools/package.py dist   only tiles in that index box (smaller JAR);
                                                tiles outside are simply missing (sea/void in game)
  JAR_NAME=GTA_VC_J2ME_lite                     output name
"""
import os, sys, zipfile, struct

HERE = os.path.dirname(os.path.abspath(__file__))
P = os.path.join(HERE, '..')
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(P, 'dist')
os.makedirs(OUT, exist_ok=True)
NAME = os.environ.get('JAR_NAME', 'GTA_VC_J2ME')
VER = os.environ.get('MIDLET_VERSION', '0.3.0')
REGION = os.environ.get('MAP_REGION')
manifest = '\r\n'.join([
    'Manifest-Version: 1.0',
    'MIDlet-Name: GTA Vice City',
    'MIDlet-Version: ' + VER,
    'MIDlet-Vendor: AidenCantCode',
    'MIDlet-1: GTA Vice City, /icon.png, GtaMidlet',
    'MIDlet-Description: Vice City open world port',
    'Nokia-MIDlet-Category: Game',
    'Nokia-MIDlet-On-Screen-Keypad: no',
    'MicroEdition-Configuration: CLDC-1.1',
    'MicroEdition-Profile: MIDP-2.0',
    'Created-By: 1.3 (preverified)', '', ''])

res = os.path.join(P, 'res')
tiles = sorted(os.listdir(os.path.join(res, 't'))) if os.path.isdir(os.path.join(res, 't')) else []
if REGION:
    i0, j0, i1, j1 = [int(v) for v in REGION.split(',')]
    keep = []
    for f in tiles:
        i, j = [int(v) for v in f[:-4].split('_')]
        if i0 <= i <= i1 and j0 <= j <= j1: keep.append(f)
    tiles = keep
used = set()
for f in tiles:
    d = open(os.path.join(res, 't', f), 'rb').read()
    n = struct.unpack_from('>H', d, 2)[0]
    used.update(struct.unpack_from('>%dH' % n, d, 4))

jar = os.path.join(OUT, NAME + '.jar')
with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    z.writestr('META-INF/MANIFEST.MF', manifest)
    cl = os.path.join(P, 'build', 'cldc')
    for f in sorted(os.listdir(cl)):
        if f.endswith('.class'): z.write(os.path.join(cl, f), f)
    for f in sorted(os.listdir(res)):
        p = os.path.join(res, f)
        if os.path.isdir(p): continue
        if f.endswith('.3gp'): z.write(p, f, compress_type=zipfile.ZIP_STORED)
        else: z.write(p, f)
    for f in tiles: z.write(os.path.join(res, 't', f), 't/' + f)
    for t in sorted(used):
        f = os.path.join(res, 'x', '%d.png' % t)
        if os.path.exists(f): z.write(f, 'x/%d.png' % t, compress_type=zipfile.ZIP_STORED)
size = os.path.getsize(jar)
jad = manifest.rstrip('\r\n').split('\r\n')
jad += ['MIDlet-Jar-URL: ' + NAME + '.jar', 'MIDlet-Jar-Size: %d' % size]
open(os.path.join(OUT, NAME + '.jad'), 'w', newline='').write('\r\n'.join(jad) + '\r\n')
print('jar', jar, size, 'bytes,', len(tiles), 'tiles,', len(used), 'map textures')
