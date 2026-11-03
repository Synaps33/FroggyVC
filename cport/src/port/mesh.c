#include "mesh.h"
#include <stdlib.h>
#include "fx_math.h"

Mesh* Mesh_new(int nv, int nt) {
    Mesh* m = (Mesh*)calloc(1, sizeof(Mesh));
    if (!m) return NULL;
    m->nv = nv;
    m->nt = nt;
    /* One allocation per mesh: this matters on the 32 MB device, where a
       dense tile is split into hundreds of small chunk meshes. */
    size_t bytes = (size_t)nv * 5 * sizeof(int) + (size_t)nt * 3 * sizeof(int) +
                   (size_t)nt * sizeof(int) + (size_t)nt * sizeof(int16_t) +
                   (size_t)nt * 4 * sizeof(int8_t) + 8;
    uint8_t* block = (uint8_t*)calloc(1, bytes);
    if (!block) { free(m); return NULL; }
    m->block = block;
    uint8_t* p = block;
    m->x = (int*)p; p += (size_t)nv * sizeof(int);
    m->y = (int*)p; p += (size_t)nv * sizeof(int);
    m->z = (int*)p; p += (size_t)nv * sizeof(int);
    m->u = (int*)p; p += (size_t)nv * sizeof(int);
    m->v = (int*)p; p += (size_t)nv * sizeof(int);
    m->ti = (int*)p; p += (size_t)nt * 3 * sizeof(int);
    m->col = (int*)p; p += (size_t)nt * sizeof(int);
    m->tex = (int16_t*)p; p += (size_t)nt * sizeof(int16_t);
    p = (uint8_t*)(((uintptr_t)p + 3u) & ~(uintptr_t)3u);
    m->flag = (int8_t*)p; p += (size_t)nt * sizeof(int8_t);
    m->nx = (int8_t*)p; p += (size_t)nt * sizeof(int8_t);
    m->ny = (int8_t*)p; p += (size_t)nt * sizeof(int8_t);
    m->nz = (int8_t*)p;
    return m;
}

void Mesh_bounds(Mesh* m) {
    if (!m || m->nv == 0) return;
    int x0 = m->x[0], x1 = m->x[0];
    int y0 = m->y[0], y1 = m->y[0];
    int z0 = m->z[0], z1 = m->z[0];
    for (int i = 1; i < m->nv; i++) {
        if (m->x[i] < x0) x0 = m->x[i];
        if (m->x[i] > x1) x1 = m->x[i];
        if (m->y[i] < y0) y0 = m->y[i];
        if (m->y[i] > y1) y1 = m->y[i];
        if (m->z[i] < z0) z0 = m->z[i];
        if (m->z[i] > z1) z1 = m->z[i];
    }
    m->bx = (x0 + x1) >> 1;
    m->by = (y0 + y1) >> 1;
    m->bz = (z0 + z1) >> 1;
    int dx = (x1 - x0) >> 1;
    int dy = (y1 - y0) >> 1;
    int dz = (z1 - z0) >> 1;
    uint64_t d2 = (uint64_t)((int64_t)dx * dx) + (uint64_t)((int64_t)dy * dy) + (uint64_t)((int64_t)dz * dz);
    m->br = (int)Fx_isqrt(d2) + 2;
}

void Mesh_free(Mesh* m) {
    if (!m) return;
    if (m->block) {
        free(m->block);
    } else {
        free(m->x); free(m->y); free(m->z);
        free(m->u); free(m->v);
        free(m->ti); free(m->tex); free(m->col);
        free(m->flag); free(m->nx); free(m->ny); free(m->nz);
    }
    free(m);
}
