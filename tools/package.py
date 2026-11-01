#!/usr/bin/env python3
"""Packs build/cldc classes + res/ into the final JAR/JAD."""
import os, sys, zipfile
HERE = os.path.dirname(os.path.abspath(__file__))
P = os.path.join(HERE, '..')
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(P, 'dist')
os.makedirs(OUT, exist_ok=True)
NAME = 'GTA_VC_J2ME'
VER = os.environ.get('MIDLET_VERSION', '0.2.0')
manifest = '\r\n'.join([
    'Manifest-Version: 1.0',
    'MIDlet-Name: GTA Vice City',
    'MIDlet-Version: ' + VER,
    'MIDlet-Vendor: AidenCantCode',
    'MIDlet-1: GTA Vice City, /icon.png, GtaMidlet',
    'MIDlet-Description: Vice City open world port - Ocean Beach',
    'Nokia-MIDlet-Category: Game',
    'Nokia-MIDlet-On-Screen-Keypad: no',
    'MicroEdition-Configuration: CLDC-1.1',
    'MicroEdition-Profile: MIDP-2.0',
    'Created-By: 1.3 (preverified)', '', ''])
jar = os.path.join(OUT, NAME + '.jar')
with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    z.writestr('META-INF/MANIFEST.MF', manifest)
    cl = os.path.join(P, 'build', 'cldc')
    for f in sorted(os.listdir(cl)):
        if f.endswith('.class'):
            z.write(os.path.join(cl, f), f)
    res = os.path.join(P, 'res')
    for f in sorted(os.listdir(res)):
        if f.endswith('.3gp'): z.write(os.path.join(res, f), f, compress_type=zipfile.ZIP_STORED)   # already compressed
        else: z.write(os.path.join(res, f), f)
size = os.path.getsize(jar)
jad = manifest.rstrip('\r\n').split('\r\n')
jad += ['MIDlet-Jar-URL: ' + NAME + '.jar', 'MIDlet-Jar-Size: %d' % size]
open(os.path.join(OUT, NAME + '.jad'), 'w', newline='').write('\r\n'.join(jad) + '\r\n')
print('jar', jar, size, 'bytes')
