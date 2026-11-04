#include "world.h"
#include "world_internal.h"
#include "assets.h"
#include "anim_data.h"
#include "fx.h"
#include "sky.h"
#include "snd.h"
#include "save.h"
#include "ui.h"
#include "j2me.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "fx_math.h"


int World_idx(World* w, int i, int j) {
    return (i < 0 || j < 0 || i >= w->tnx || j >= w->tnz) ? -1 : (j * w->tnx + i);
}

Tile* World_tileFor(World* w, int gi, int gj, int per) {
    if (gi < 0 || gj < 0) return NULL;
    int k = World_idx(w, gi / per, gj / per);
    return k < 0 ? NULL : w->tiles[k];
}

int World_hCell(World* w, int gi, int gj, int y) {
    Tile* t = World_tileFor(w, gi, gj, TILE_NH);
    if (!t || !t->top) return -640;
    int k = (gj % TILE_NH) * TILE_NH + (gi % TILE_NH);
    int a = t->top[k];
    if (a == -32768) return -640;
    int b = t->low ? t->low[k] : -32768;

    /* 1. Above water, the seabed (b < -70) is never a walkable floor.
          Bridges, piers, and docks over water always return the deck a. */
    if (b < -70 && y > -70) return a;

    /* 2. Two distinct layers: ceiling a and floor b. */
    if (b != -32768 && b != a && b > -70) {
        /* On the upper deck (driving on elevated highway, overpass, bridge, roof) */
        if (y > b + 90 && (a - y) < (y - b)) {
            return a;
        }
        /* Ceiling is overhead (inside underground parking, under archway/bridge) */
        if (a > y + 70) {
            return b;
        }
    }

    /* 3. Single-layer cell (palm tree crown, tree canopy, overhead prop):
          If a is far above the entity (> 2.5 m above y), the entity is
          underneath it on the ground. Never teleport or spawn onto treetops! */
    if (a > y + 160) {
        return y;
    }

    return a;
}

bool World_spawnClear(World* w, int xm, int zm, int radius) {
    static const int dir[9][2] = {
        { 0, 0 }, { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 },
        { 1, 1 }, { -1, 1 }, { 1, -1 }, { -1, -1 }
    };
    int py = w->player->y;
    for (int i = 0; i < 9; i++) {
        int x = xm + dir[i][0] * radius;
        int z = zm + dir[i][1] * radius;
        if (World_solidCell(w, World_scell(x, w->mox), World_scell(z, w->moz))) return false;
        /* Reject points under a building roof/overhang.  Bridge decks over
           water are valid; ordinary roofs are not. */
        if (!World_isBridgeAt(w, x, z)) {
            int fx = (x - w->mox - 1024) / 8;
            int fz = (z - w->moz - 1024) / 8;
            int gi = fx >> 8, gj = fz >> 8;
            Tile* t = World_tileFor(w, gi, gj, TILE_NH);
            if (t && t->top) {
                int k = (gj % TILE_NH) * TILE_NH + (gi % TILE_NH);
                if (t->top[k] > py + 160) return false;
            }
        }
    }
    return true;
}

bool World_isBridgeAt(World* w, int xm, int zm) {
    int fx = (xm - w->mox - 1024) / 8;
    int fz = (zm - w->moz - 1024) / 8;
    int gi = fx >> 8, gj = fz >> 8;
    Tile* t = World_tileFor(w, gi, gj, TILE_NH);
    if (!t || !t->top || !t->low || !t->surf) return false;
    int k = (gj % TILE_NH) * TILE_NH + (gi % TILE_NH);
    int top = t->top[k], low = t->low[k];
    return (t->surf[k] == WORLD_S_ROAD || t->surf[k] == WORLD_S_PAVE) && (top > low + 80);
}

/* A bridge deck over water can be several metres above the approach; do not
   treat it as an overhead roof. */
static bool bridgeStep(World* w, int xm, int zm, int y, int maxStep) {
    if (!World_isBridgeAt(w, xm, zm)) return false;
    int g = World_groundH(w, xm, zm, y);
    if (g - y > maxStep && g - y <= 2000) return true;
    return false;
}

bool World_stepAllowed(World* w, int xm, int zm, int y, int maxStep) {
    int d = World_groundH(w, xm, zm, y + 40) - y;
    if (d <= maxStep) return true;
    return bridgeStep(w, xm, zm, y, maxStep);
}

int World_groundH(World* w, int xm, int zm, int y) {
    /* ((a << 8) / 2048) == a / 8, with the same truncation toward zero */
    int fx = (xm - w->mox - 1024) / 8;
    int fz = (zm - w->moz - 1024) / 8;
    int i = fx >> 8, j = fz >> 8;
    int ax = fx & 255, az = fz & 255;
    int h00 = World_hCell(w, i, j, y);
    int h10 = World_hCell(w, i + 1, j, y);
    int h01 = World_hCell(w, i, j + 1, y);
    int h11 = World_hCell(w, i + 1, j + 1, y);

    if (h00 == -640 || h10 == -640 || h01 == -640 || h11 == -640) {
        int v = h00;
        if (h10 > v) v = h10;
        if (h01 > v) v = h01;
        if (h11 > v) v = h11;
        if (v == -640) return -640;
        if (h00 == -640) h00 = v;
        if (h10 == -640) h10 = v;
        if (h01 == -640) h01 = v;
        if (h11 == -640) h11 = v;
    }

    int mn = h00, mx = h00;
    if (h10 < mn) mn = h10;
    if (h10 > mx) mx = h10;
    if (h01 < mn) mn = h01;
    if (h01 > mx) mx = h01;
    if (h11 < mn) mn = h11;
    if (h11 > mx) mx = h11;

    if (mx - mn > 128) {
        return ax < 128 ? (az < 128 ? h00 : h01) : (az < 128 ? h10 : h11);
    }
    int a = h00 + (((h10 - h00) * ax) >> 8);
    int b = h01 + (((h11 - h01) * ax) >> 8);
    return a + (((b - a) * az) >> 8);
}

int World_groundH2(World* w, int xm, int zm) {
    return World_groundH(w, xm, zm, 4000);
}

int World_surfAt(World* w, int xm, int zm) {
    if (xm < w->mox || zm < w->moz) return WORLD_S_WATER;
    int gi = (xm - w->mox) / 2048;
    int gj = (zm - w->moz) / 2048;
    Tile* t = World_tileFor(w, gi, gj, TILE_NH);
    if (!t || !t->surf) return WORLD_S_WATER;
    return t->surf[(gj % TILE_NH) * TILE_NH + (gi % TILE_NH)];
}

bool World_solidCell(World* w, int gi, int gj) {
    Tile* t = World_tileFor(w, gi, gj, TILE_NS);
    if (!t || !t->solid) return false;
    int li = gi % TILE_NS, lj = gj % TILE_NS;
    if ((t->solid[lj * TILE_SROW + (li >> 3)] & (0x80 >> (li & 7))) == 0) return false;

    /* A roadway, bridge, ramp, or parking deck is for driving: ignore geometry
       artifacts (bridge trusses, railings, ramp side flanks) on driveable surfaces. */
    int hi = gi / 2, hj = gj / 2;
    Tile* th = World_tileFor(w, hi, hj, TILE_NH);
    if (th && th->surf && th->top && th->low) {
        int k = (hj % TILE_NH) * TILE_NH + (hi % TILE_NH);
        int s = th->surf[k];
        if (s == WORLD_S_ROAD) return false;
        if (s != WORLD_S_WATER) {
            int diff = th->top[k] - th->low[k];
            if (diff >= 50 && diff <= 280) return false; /* elevated ramp / parking floor */
            if (diff < 50) {
                /* check 4 cardinal neighbors for a ramp slope leading to a ramp/deck */
                static const int nbr[4][2] = { {-1,0}, {1,0}, {0,-1}, {0,1} };
                for (int n = 0; n < 4; n++) {
                    int ni = (hi % TILE_NH) + nbr[n][0], nj = (hj % TILE_NH) + nbr[n][1];
                    if (ni >= 0 && ni < TILE_NH && nj >= 0 && nj < TILE_NH) {
                        int nk = nj * TILE_NH + ni;
                        int ndiff = th->top[nk] - th->low[nk];
                        int d = th->top[nk] - th->top[k];
                        if (d > 35 && d <= 260 && ndiff <= 280) return false;
                    }
                }
            }
        }
    }
    return true;
}

int World_scell(int m, int o) {
    return m < o ? -1 : (m - o) / 1024;
}

bool World_deep(World* w, int xm, int zm, int y) {
    return World_groundH(w, xm, zm, y) < -70;
}

bool World_walkable(World* w, int xm, int zm, int y) {
    if (World_surfAt(w, xm, zm) == WORLD_S_WATER) return false;
    int g = World_groundH(w, xm, zm, y);
    if (g < -70 || g > y + 120) return false;
    return !World_solidCell(w, World_scell(xm, w->mox), World_scell(zm, w->moz));
}

bool World_resolve(World* w, int rad) {
    bool hit = false;
    int i0 = World_scell(w->rx - rad, w->mox), i1 = World_scell(w->rx + rad, w->mox);
    int j0 = World_scell(w->rz - rad, w->moz), j1 = World_scell(w->rz + rad, w->moz);

    for (int j = j0; j <= j1; j++) {
        for (int i = i0; i <= i1; i++) {
            if (!World_solidCell(w, i, j)) continue;
            int bx0 = w->mox + i * 1024, bz0 = w->moz + j * 1024;
            int bx1 = bx0 + 1024, bz1 = bz0 + 1024;
            int cx = w->rx < bx0 ? bx0 : (w->rx > bx1 ? bx1 : w->rx);
            int cz = w->rz < bz0 ? bz0 : (w->rz > bz1 ? bz1 : w->rz);
            int dx = w->rx - cx, dz = w->rz - cz;

            if (dx == 0 && dz == 0) {
                int l = w->rx - bx0, rr = bx1 - w->rx;
                int t = w->rz - bz0, bt = bz1 - w->rz;
                int m = l;
                if (rr < m) m = rr;
                if (t < m) m = t;
                if (bt < m) m = bt;
                if (m == l) w->rx = bx0 - rad;
                else if (m == rr) w->rx = bx1 + rad;
                else if (m == t) w->rz = bz0 - rad;
                else w->rz = bz1 + rad;
                hit = true;
            } else {
                int d2 = dx * dx + dz * dz;
                if (d2 < rad * rad) {
                    int d = (int)Fx_isqrtFast((uint64_t)d2);
                    if (d < 1) d = 1;
                    w->rx = cx + dx * rad / d;
                    w->rz = cz + dz * rad / d;
                    hit = true;
                }
            }
        }
    }
    return hit;
}

bool World_stepOk(World* w, int ox, int oz, int oy, int maxStep) {
    if (World_stepAllowed(w, w->rx, w->rz, oy, maxStep)) return true;
    int nx = w->rx, nz = w->rz;
    w->rx = nx; w->rz = oz;
    if (World_stepAllowed(w, w->rx, w->rz, oy, maxStep)) return true;
    w->rx = ox; w->rz = nz;
    if (World_stepAllowed(w, w->rx, w->rz, oy, maxStep)) return true;
    w->rx = ox; w->rz = oz;
    return false;
}

void World_buildFxMeshes(World* w) {
    int L = 70 * 64, bw = 7 * 64;
    w->beam = Mesh_new(5, 2);
    w->beam->x[0] = 0;   w->beam->y[0] = 0;   w->beam->z[0] = 0;
    w->beam->x[1] = -bw; w->beam->y[1] = 0;   w->beam->z[1] = L;
    w->beam->x[2] = bw;  w->beam->y[2] = 0;   w->beam->z[2] = L;
    w->beam->x[3] = 0;   w->beam->y[3] = -bw; w->beam->z[3] = L;
    w->beam->x[4] = 0;   w->beam->y[4] = bw;  w->beam->z[4] = L;

    int tiB[6] = { 0, 1, 2, 0, 3, 4 };
    memcpy(w->beam->ti, tiB, sizeof(tiB));
    w->beam->tex[0] = -1; w->beam->tex[1] = -1;
    w->beam->col[0] = 0x2A2618; w->beam->col[1] = 0x2A2618;
    w->beam->flag[0] = (int8_t)(MESH_F_DOUBLE | MESH_F_EMISSIVE);
    w->beam->flag[1] = (int8_t)(MESH_F_DOUBLE | MESH_F_EMISSIVE);
    Mesh_bounds(w->beam);

    int q = 64;
    w->blood = Mesh_new(4, 2);
    w->blood->x[0] = -q; w->blood->z[0] = -q;
    w->blood->x[1] = q;  w->blood->z[1] = -q;
    w->blood->x[2] = q;  w->blood->z[2] = q;
    w->blood->x[3] = -q; w->blood->z[3] = q;
    w->blood->u[0] = 0;   w->blood->v[0] = 0;
    w->blood->u[1] = 511; w->blood->v[1] = 0;
    w->blood->u[2] = 511; w->blood->v[2] = 511;
    w->blood->u[3] = 0;   w->blood->v[3] = 511;

    int tiBl[6] = { 0, 2, 1, 0, 3, 2 };
    memcpy(w->blood->ti, tiBl, sizeof(tiBl));
    int bt = Assets_acquirePath("/t_blood.png");
    w->blood->tex[0] = (int16_t)bt; w->blood->tex[1] = (int16_t)bt;
    w->blood->flag[0] = MESH_F_DOUBLE; w->blood->flag[1] = MESH_F_DOUBLE;
    w->blood->ny[0] = 127; w->blood->ny[1] = 127;
    Mesh_bounds(w->blood);
}

World* World_new(R3D* r) {
    World* w = (World*)calloc(1, sizeof(World));
    w->r = r;
    w->alive = true;
    w->player = &w->playerInst;
    Ped_init(w->player);
    w->camPitch = 55;
    w->camDistMm = 4300;
    w->minutes = 14 * 60 + 18;
    w->drawDist = 150;
    w->brightness = 10;
    w->speedMul = 100;
    w->npool = Assets_legacy ? 5 : WORLD_NPOOL;
    w->ncpool = Assets_legacy ? 4 : WORLD_NCPOOL;
    w->maxPed = Assets_legacy ? 10 : WORLD_MAXPED;
    w->maxTraffic = Assets_legacy ? 3 : WORLD_MAXTRAFFIC;
    w->ring = Assets_legacy ? 1 : 2;
    /* The original uses the high-detail tile mesh out to 200 m.  On the
       console the low-detail mesh saves a lot of triangles and is invisible
       at 160x120, so keep near detail around the player only. */
    w->nearLod = Assets_legacy ? 70 * 64 : (150 * 64 + 3200);
    const char* lod = getenv("GTAVC_LOD");
    if (lod) w->nearLod = atoi(lod) * 64;
    return w;
}

void World_loadIndex(World* w) {
    w->tileMm = 100 * 1024;
    w->tnx = 0;
    w->tnz = 0;
    int len = 0;
    uint8_t* b = Assets_readAll("/mapidx.bin", &len);
    if (!b) return;
    DataInputStream d;
    dis_init(&d, b, len);
    uint8_t mg[4];
    dis_readFully(&d, mg, 4);
    w->mox = dis_readInt(&d);
    w->moz = dis_readInt(&d);
    w->tileMm = dis_readUnsignedShort(&d);
    w->tnx = dis_readUnsignedShort(&d);
    w->tnz = dis_readUnsignedShort(&d);
    w->tileMm = 100 * 1024;

    int bitBytes = (w->tnx * w->tnz + 7) / 8;
    uint8_t* bits = (uint8_t*)malloc((size_t)bitBytes);
    dis_readFully(&d, bits, bitBytes);

    int totalTiles = w->tnx * w->tnz;
    w->tflag = (uint8_t*)malloc((size_t)totalTiles);
    for (int k = 0; k < totalTiles; k++) {
        w->tflag[k] = (bits[k >> 3] >> (7 - (k & 7))) & 1;
    }
    free(bits);

    dis_readUnsignedShort(&d);
    w->rx0 = dis_readInt(&d);
    w->rz0 = dis_readInt(&d);
    w->rcell = dis_readUnsignedShort(&d);
    w->rw = dis_readUnsignedShort(&d);
    w->rh = dis_readUnsignedShort(&d);

    int radBytes = w->rw * w->rh;
    w->rad = (uint8_t*)malloc((size_t)radBytes);
    dis_readFully(&d, w->rad, radBytes);

    w->spX = dis_readInt(&d);
    w->spZ = dis_readInt(&d);
    w->spY = dis_readInt(&d);
    w->spYaw = dis_readUnsignedShort(&d);

    w->lampX = dis_readInt(&d);
    w->lampY = dis_readInt(&d);
    w->lampZ = dis_readInt(&d);
    dis_close(&d);
    free(b);

    w->tiles = (Tile**)calloc((size_t)totalTiles, sizeof(Tile*));
    w->requested = (bool*)calloc((size_t)totalTiles, sizeof(bool));
    World_buildFxMeshes(w);
}

void World_preload(World* w, int x, int z) {
    if (!w->tiles || w->tileMm <= 0 || w->tnx <= 0 || w->tnz <= 0) return;
    /* Only the tile the player stands on is loaded synchronously.  The rest
       of the ring is streamed one tile per frame once gameplay starts; this
       keeps the loading screen short and the heap peak low on the 32 MB
       device.  The Java build used a loader thread for the whole ring. */
    int ci = (x - w->mox) / w->tileMm;
    int cj = (z - w->moz) / w->tileMm;
    /* centre + four cardinal neighbours: enough for spawning and the first
       camera frame; the corners stream in after gameplay starts. */
    static const int off[5][2] = { {0,0}, {1,0}, {-1,0}, {0,1}, {0,-1} };
    for (int n = 0; n < 5; n++) {
        int i = ci + off[n][0], j = cj + off[n][1];
        int k = World_idx(w, i, j);
        if (k < 0 || w->tiles[k] != NULL || w->tflag[k] == 0) continue;
        Tile* t = Tile_load(i, j, w->mox, w->moz, w->tileMm);
        if (t) {
            Tile_install(t);
            w->tiles[k] = t;
        } else {
            w->tflag[k] = 0;
        }
    }
}

void World_startLoader(World* w) {
    (void)w;
}

void World_tileJobCancel(World* w) {
    if (!w) return;
    if (w->tileJobFile) {
        fclose((FILE*)w->tileJobFile);
        w->tileJobFile = NULL;
    }
    free(w->tileJobBuf);
    w->tileJobBuf = NULL;
    w->tileJobActive = false;
    w->tileJobLen = w->tileJobGot = 0;
}

static bool tileJobStart(World* w, int i, int j, int k) {
    char path[600];
    const char* d = platform_assets_dir();
    snprintf(path, sizeof(path), "%s/tc/%d_%d.bin", (d && *d) ? d : "assets", i, j);
    FILE* f = fopen(path, "rb");
    if (!f) return false;
    fseek(f, 0, SEEK_END);
    long len = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (len <= 0 || len > 4 * 1024 * 1024) { fclose(f); return false; }
    uint8_t* buf = (uint8_t*)malloc((size_t)len);
    if (!buf) { fclose(f); return false; }
    w->tileJobFile = f;
    w->tileJobBuf = buf;
    w->tileJobLen = (int)len;
    w->tileJobGot = 0;
    w->tileJobI = i;
    w->tileJobJ = j;
    w->tileJobK = k;
    w->tileJobActive = true;
    return true;
}

/* Read 64 KiB per frame.  A dense cache is <1 MiB, so the old 200-300 ms
   blocking fread becomes a sequence of small I/O slices. */
static void tileJobStep(World* w) {
    if (!w->tileJobActive) return;
    int left = w->tileJobLen - w->tileJobGot;
    int chunk = left > 65536 ? 65536 : left;
    if (chunk <= 0) {
        World_tileJobCancel(w);
        return;
    }
    size_t n = fread(w->tileJobBuf + w->tileJobGot, 1, (size_t)chunk, (FILE*)w->tileJobFile);
    if (n == 0) {
        World_tileJobCancel(w);
        return;
    }
    w->tileJobGot += (int)n;
    if (w->tileJobGot < w->tileJobLen) return;

    Tile* t = Tile_loadCache(w->tileJobBuf, w->tileJobLen, w->tileJobI, w->tileJobJ,
                             w->mox, w->moz, w->tileMm);
    int k = w->tileJobK;
    World_tileJobCancel(w);
    if (t) {
        Tile_install(t);
        w->tiles[k] = t;
    } else {
        w->tflag[k] = 0;
    }
}

void World_streamTiles(World* w, int x, int z) {
    if (!w->tiles || w->tileMm <= 0 || w->tnx <= 0 || w->tnz <= 0) return;
    if (w->tileJobActive) {
        tileJobStep(w);
        return;
    }
    int ci = (x - w->mox) / w->tileMm;
    int cj = (z - w->moz) / w->tileMm;
    int ringLimit = w->ring;

    /* load at most one tile per frame: the Java build used a loader thread,
       but the console core is single-threaded and a dense tile can take a
       while to decode and split. */
    int loadedOne = 0;
    for (int ring = 0; ring <= ringLimit && !loadedOne; ring++) {
        for (int j = cj - ring; j <= cj + ring && !loadedOne; j++) {
            for (int i = ci - ring; i <= ci + ring && !loadedOne; i++) {
                int di = abs(i - ci), dj = abs(j - cj);
                int rMax = di > dj ? di : dj;
                if (rMax != ring) continue;
                int k = World_idx(w, i, j);
                if (k < 0 || w->tflag[k] == 0 || w->tiles[k] != NULL) continue;
                if (tileJobStart(w, i, j, k)) return;
                Tile* t = Tile_load(i, j, w->mox, w->moz, w->tileMm);
                if (t) {
                    Tile_install(t);
                    w->tiles[k] = t;
                } else {
                    w->tflag[k] = 0;
                }
                loadedOne = 1;
            }
        }
    }

    int totalTiles = w->tnx * w->tnz;
    for (int k = 0; k < totalTiles; k++) {
        Tile* t = w->tiles[k];
        if (!t) continue;
        if (abs(t->i - ci) > ringLimit + 1 || abs(t->j - cj) > ringLimit + 1) {
            Tile_unload(t);
            Tile_free(t);
            w->tiles[k] = NULL;
        }
    }
}

void World_free(World* w) {
    if (!w) return;
    World_tileJobCancel(w);
    int totalTiles = w->tnx * w->tnz;
    if (w->tiles) {
        for (int k = 0; k < totalTiles; k++) {
            if (w->tiles[k]) {
                Tile_unload(w->tiles[k]);
                Tile_free(w->tiles[k]);
            }
        }
        free(w->tiles);
    }
    free(w->tflag);
    free(w->requested);
    free(w->rad);
    if (w->pedNames) Assets_freeLines(w->pedNames, w->pedNamesCount);
    free(w->pedFlags);
    if (w->carNames) Assets_freeLines(w->carNames, w->carNamesCount);
    for (int i = 0; i < WORLD_NPOOL; i++) {
        if (w->pool[i]) PedModel_free(w->pool[i]);
        if (w->poolLo[i]) PedModel_free(w->poolLo[i]);
    }
    if (w->pm0) PedModel_free(w->pm0);
    for (int i = 0; i < WORLD_NCPOOL; i++) {
        if (w->cpool[i]) CarModel_free(w->cpool[i]);
    }
    for (int i = 1; i < 3; i++) if (w->weaponMesh[i]) Mesh_free(w->weaponMesh[i]);
    if (w->beam) Mesh_free(w->beam);
    if (w->blood) Mesh_free(w->blood);
    if (w->cashMesh) Mesh_free(w->cashMesh);
    if (w->missionMarker) Mesh_free(w->missionMarker);
    if (w->hdl) Assets_freeLines(w->hdl, w->hdlCount);
    free(w);
}
