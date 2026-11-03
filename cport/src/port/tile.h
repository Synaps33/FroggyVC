#ifndef GTAVC_TILE_H
#define GTAVC_TILE_H

#include <stdint.h>
#include <stdbool.h>
#include "mesh.h"
#include "j2me.h"

#define TILE_NH 50
#define TILE_NS 100
#define TILE_SROW 13

typedef struct Tile {
    int i, j;
    int cx, cz;
    Mesh *nearMesh, *farMesh;
    Mesh **nearChunks; int nearChunkCount;
    Mesh **farChunks;  int farChunkCount;
    int16_t *top, *low;
    uint8_t *surf, *solid;
    char** texPath;
    int** texPix;
    int* texW;
    int* texH;
    int* slot;
    int texCount;
    bool installed;
    int lastUse;
} Tile;

Tile* Tile_load(int i, int j, int ox, int oz, int tileMm);
Tile* Tile_loadCache(const uint8_t* data, int len, int i, int j, int ox, int oz, int tileMm);
bool  Tile_saveCache(Tile* t, const char* path);
Mesh* Tile_readMesh(DataInputStream* d);
void  Tile_install(Tile* t);
void  Tile_unload(Tile* t);
void  Tile_free(Tile* t);

#endif /* GTAVC_TILE_H */
