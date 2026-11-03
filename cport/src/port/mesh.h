#ifndef GTAVC_MESH_H
#define GTAVC_MESH_H

#include <stdint.h>
#include <stdbool.h>

#define MESH_BODY 0x1000000
#define MESH_F_DOUBLE 1
#define MESH_F_EMISSIVE 2
#define MESH_F_WATER 4
#define MESH_F_TINT 16
#define MESH_F_GLASS 32
#define MESH_F_TINT2 64

typedef struct Mesh {
    int nv, nt;
    int *x, *y, *z, *u, *v;
    int *ti;
    int16_t *tex;
    int *col;
    int8_t *flag;
    int8_t *nx, *ny, *nz;
    int bx, by, bz, br;
    void* block;      /* single allocation for all arrays (NULL for manual meshes) */
} Mesh;

Mesh* Mesh_new(int nv, int nt);
void  Mesh_bounds(Mesh* m);
void  Mesh_free(Mesh* m);

#endif /* GTAVC_MESH_H */
