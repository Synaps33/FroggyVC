# FroggyVC — GTA Vice City for GB300 V2 / SF2000

**FroggyVC** is a from-scratch C port of the open-source J2ME demake
[`aidencantcode/GTAVCJ2ME`](https://github.com/aidencantcode/GTAVCJ2ME) for the
**GB300 V2** and **SF2000** retro handhelds.  It is the same Vice City map,
vehicles, pedestrians, weapons and game logic as the J2ME version, translated
1:1 to C and adapted to a 87 MHz MIPS32 CPU **without an FPU**.

The original MIDP 2.0 / CLDC 1.1 implementation is kept in this repository as
reference and for other devices (`src/`, `res/`, `res_legacy/`, `tools/`).
The console port lives in **[`cport/`](cport/)**.

---

## What is in the console port?

- **Full Vice City map** — both main islands, Starfish Island, Prawn Island and
  the small keys.  The complete map is streamed from the SD card.
- **Software 3D renderer** — textured, depth-buffered, fixed-point rasteriser;
  no FPU and no libm in the frame loop.
- **Streaming with no multi-second stalls** — pre-split tile chunks (`tc/`) and
  raw/atlas map textures are generated on the PC and read from the SD card.
- **Audio** — effects, voice lines, engine and siren loops mixed at 44.1 kHz
  and sent through the libretro audio callback.
- **First story mission** — *An Old Friend*: meet Ken, drive to the Ocean View
  Hotel, three-minute timer, $100 reward.
- **Controls** — Switch-style ABXY, always-on autorun, L/R handbrake /
  look-behind while driving.
- **Settings** — saved to `te_gtavc_opts.rms`: brightness, draw distance,
  limiter, PS2 filter, subtitles, wide screen, radar/HUD, traffic density and
  screen resolution.  Resolution modes: 320×240, 240×180, 160×120, 106×80.
- **Save/continue** — progress is stored in the platform data directory;
  settings and mission state survive a reboot.

---

## Controls

| GB300 / SF2000 | On foot | In a vehicle |
|---|---|---|
| D-pad | move / turn | steer |
| **A** | sprint / run | accelerate |
| **B** | attack | brake / reverse |
| **X** | jump | handbrake |
| **Y** | enter / exit vehicle | exit vehicle |
| **L** | camera left | handbrake |
| **R** | camera right | look behind |
| **Select** | switch weapon | switch weapon |
| **Start** | pause | pause |

Autorun is always enabled on the console.  Weapon switch cycles
**fists → baseball bat → Colt 45** (only owned weapons with ammo are selected).

---

## Repository layout

```
cport/                 C port, assets and build files for GB300 V2 / SF2000
  src/                 C translation of the J2ME classes
  libretro/            libretro core wrapper
  runner/              host/MIPS replay runner and tests
  assets/              full game assets (map tiles, textures, models, audio)
    tc/                pre-split tile chunks
    x/                 map textures, raw caches and xatlas.bin
  tools/               texture cache / tile cache generators
  Makefile
  install_to_sd.sh
  README.md            detailed port notes
  PORTING_GUIDE.md
  VERIFICATION.md
src/                   original J2ME Java sources
res/, res_legacy/      original J2ME resources
assets/                original J2ME source assets
tools/                 original J2ME asset/build tools
.github/               original J2ME CI workflow
```

---

## Build

### Requirements

- Linux host with `gcc`, `make`, Python 3 and Pillow (only for regenerating
  asset caches).
- A MIPS32 soft-float toolchain; the port was built with the MTI GCC 7.4
  toolchain (`mipsel-mti-elf-*`).
- The froggy multicore frontend for the final device link.
- For the host/MIPS tests: `make`, `qemu-mipsel` and a musl MIPS soft-float
  cross compiler (the repository's `tools/test_mips.sh` documents the paths).

### Host build

```bash
cd cport
make host
./build/gtavc_runner assets screenshots
```

The runner replays the original J2ME test scenario.  Directories and command
line options are intentionally simple so it can run on a PC or under qemu.

### MIPS soft-float test

```bash
cd cport
make mips-test
```

This builds the same runner with `mipsel-linux-muslsf-gcc`, links it statically
and runs it under `qemu-mipsel`.  It proves that all float use goes through
soft-float helpers.

### Device build

```bash
cd cport
make device FROGGY_TYPE=GB300V2 \
  MULTICORE=/path/to/sf2000_multicore
```

`MIPS_ELF` and `MULTICORE` can be overridden at the command line.  For an
SF2000 build use `FROGGY_TYPE=SF2000`; the output is
`dist/gtavc_SF2000.sf2k` instead of `dist/gtavc_GB300V2.gb3k`.

The resulting core is linked with `-msoft-float`; a useful sanity check is:

```bash
mipsel-mti-elf-readelf -A build/core.elf | grep FP
mipsel-mti-elf-objdump -d build/core.elf | grep -E 'cop1|mtc1|mfc1'
```

The first line should say `Tag_GNU_MIPS_ABI_FP: Soft float`; the second should
find no instructions.

---

## Install on the SD card

Build the device core first, then run:

```bash
cd cport
./install_to_sd.sh /media/$USER/GB300 GB300V2
```

The installer copies:

- the core to `system/Deimos/cores`, `cores/gtavc` and `ROMS/gtavc`,
- the full `assets/` tree, including the `tc/` tile cache and `x/*.raw` /
  `x/xatlas.bin` texture caches,
- the launcher stubs and artwork.

Allow a minute or two: the game assets are about 200 MB and many small files on
FAT32 are slow.  Start the game from `ROMS/gtavc/GTAVC.gb3k`.

If you use a different mount point, pass it as the first argument.  The second
argument selects `GB300V2` or `SF2000`.

---

## Asset caches

The assets in `cport/assets` already contain the generated caches, but they can
be rebuilt from the source PNG/tile files:

```bash
cd cport
python3 tools/predecode_textures.py
python3 tools/pack_texture_atlas.py
GTAVC_TILECACHE=1 ./build/gtavc_runner assets /tmp/tile_shots
```

- `tools/predecode_textures.py` — converts all 32×32 PNGs in `assets/x` to raw
  ARGB files.
- `tools/pack_texture_atlas.py` — packs them into `assets/x/xatlas.bin`.
- `GTAVC_TILECACHE=1` — writes `assets/tc/<i>_<j>.bin` (pre-split chunks with
  top/low/surface/solid data).

If a cache is missing, the game falls back to the original tile file and PNG
decoding.  The fallback is correct but slower and may hitch while driving, so
keep the caches on the card.

---

## Tests

Use `GTAVC_SEED=1234567` for a reproducible run.

```bash
cd cport
GTAVC_SEED=1234567 ./build/gtavc_runner assets /tmp/shots
GTAVC_SEED=1234567 GTAVC_MISSIONTEST=1 ./build/gtavc_runner assets /tmp/mission
GTAVC_AUDIOTEST=1 ./build/gtavc_runner assets /tmp/audio
GTAVC_SPAWNCHECK=1 ./build/gtavc_runner assets /tmp/spawn
```

Extra diagnostics:

- `GTAVC_TILELOG=1` — print tile read/split timings.
- `GTAVC_TILEBENCH=1` — load a dense tile and print its breakdown.
- `GTAVC_STRESS=N` — run N simulated frames with random driving/input.
- `GTAVC_DENSITYTEST=1` — drive the real options menu through low/medium/high
  and print live pedestrian/car counts.

The host and MIPS runners have been compared screenshot-for-screenshot; with
the same seed the images are pixel-identical.

---

## Notes and limitations

- The target CPU has no FPU.  All gameplay and rendering code uses integers;
  soft-float is only used during asset loading for normal/sqrt calculations.
- The console hardware is mono; the libretro frontend averages the stereo
  channels.
- The J2ME **intro video** is not decoded by the console port; `intro.3gp` is
  present but the port treats it as unavailable/skipped.
- Audio is limited to the original J2ME effect/voice set.  There is no music
  player in the port.
- The first mission is a compact console adaptation; there are no full
  cutscenes.
- Assets are from the GTA Vice City J2ME demake and remain the property of
  their respective copyright holders.  This repository is a non-commercial
  fan port and is not affiliated with Rockstar Games or Take-Two Interactive.

---

## Credits

- Original J2ME demake, map/tools and assets:
  [`aidencantcode/GTAVCJ2ME`](https://github.com/aidencantcode/GTAVCJ2ME)
- C port, console adaptation, caches and tests: Synaps33 / FroggyVC.
- Froggy multicore frontend: the SF2000/GB300 community project used to build
  and run the core.

## License

See [LICENSE](LICENSE).  The original J2ME source is included under its
original terms; the C port is provided for personal, non-commercial use.
