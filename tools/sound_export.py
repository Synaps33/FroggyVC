#!/usr/bin/env python3
"""Pulls the few sound effects the game uses out of Vice City's sfx.raw/sfx.SDT (indices from reVC's AudioSamples.h)
into small 8 kHz-16 kHz mono WAVs in res/ (s_<name>.wav).
  python3 tools/sound_export.py path/to/Audio
"""
import os, sys, struct, wave
import numpy as np
RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'res')
# name: (sample index, max seconds, out rate)
PICK = {'punch': (167, 0.4, 11025), 'gun': (50, 0.6, 11025), 'bat': (165, 0.5, 11025), 'swing': (168, 0.4, 11025),
        'crash': (136, 0.8, 11025), 'dooropen': (17, 0.6, 11025), 'doorclose': (18, 0.6, 11025), 'horn': (1, 0.5, 11025),
        'pickup': (366, 0.9, 11025), 'explode': (47, 1.6, 11025), 'splash': (324, 0.8, 11025), 'hitped': (115, 0.5, 11025),
        'siren': (27, 3.8, 11025), 'idle': (288, 0.5, 11025), 'rev': (268, 0.33, 11025)}

def main(audio):
    sdt = open(os.path.join(audio, 'sfx.SDT'), 'rb').read()
    raw = open(os.path.join(audio, 'sfx.RAW'), 'rb')
    for name, (idx, secs, rate) in PICK.items():
        off, size, freq, ls, le = struct.unpack_from('<5i', sdt, idx * 20)
        raw.seek(off); a = np.frombuffer(raw.read(size), '<i2').astype(np.float32)
        a = a[:int(secs * freq)]
        n = int(len(a) * rate / freq)
        a = np.interp(np.linspace(0, len(a) - 1, n), np.arange(len(a)), a)
        w = wave.open(os.path.join(RES, 's_%s.wav' % name), 'wb')
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(rate)
        w.writeframes(np.clip(a, -32768, 32767).astype('<i2').tobytes()); w.close()
    print('sounds:', ', '.join(PICK))

if __name__ == '__main__':
    main(sys.argv[1])
