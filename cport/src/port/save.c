#include "save.h"
#include "world.h"
#include "game.h"
#include "car.h"
#include "ped.h"
#include "j2me.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define SAVE_NAME "gtavc_save"
#define OPT_NAME  "gtavc_opts"

bool Save_exists(void) {
    RecordStore* rs = RecordStore_openRecordStore(SAVE_NAME, false);
    if (!rs) return false;
    bool e = RecordStore_getNumRecords(rs) > 0;
    RecordStore_closeRecordStore(rs);
    return e;
}

void Save_write(World* w) {
    if (!w || !w->player || w->health <= 0 || w->busted) return;
    DataOutputStream d;
    dos_init(&d);
    dos_writeInt(&d, 2);
    dos_writeInt(&d, w->money);
    int x = (w->inCar != NULL) ? w->inCar->x : w->player->x;
    int z = (w->inCar != NULL) ? w->inCar->z : w->player->z;
    int y = (w->inCar != NULL) ? w->inCar->y : w->player->y;
    dos_writeInt(&d, x);
    dos_writeInt(&d, z);
    dos_writeInt(&d, y);
    dos_writeInt(&d, w->player->yaw);
    dos_writeInt(&d, w->minutes);
    dos_writeBoolean(&d, w->hasBat);
    dos_writeBoolean(&d, w->hasPistol);
    dos_writeInt(&d, w->ammo);
    dos_writeInt(&d, w->missionDone ? 1 : 0);

    RecordStore* rs = RecordStore_openRecordStore(SAVE_NAME, true);
    if (rs) {
        if (RecordStore_getNumRecords(rs) > 0) {
            RecordStore_setRecord(rs, 1, d.data, 0, d.len);
        } else {
            RecordStore_addRecord(rs, d.data, 0, d.len);
        }
        RecordStore_closeRecordStore(rs);
    }
    dos_free(&d);
}

bool Save_load(World* w) {
    if (!w) return false;
    RecordStore* rs = RecordStore_openRecordStore(SAVE_NAME, false);
    if (!rs) return false;
    int len = 0;
    const uint8_t* b = RecordStore_getRecord(rs, 1, &len);
    if (!b || len <= 0) {
        RecordStore_closeRecordStore(rs);
        return false;
    }

    DataInputStream d;
    dis_init(&d, b, len);
    int ver = dis_readInt(&d);
    w->money = dis_readInt(&d);
    int x = dis_readInt(&d);
    int z = dis_readInt(&d);
    int y = dis_readInt(&d);
    int yaw = dis_readInt(&d);
    w->minutes = dis_readInt(&d);
    w->hasBat = dis_readBoolean(&d);
    w->hasPistol = dis_readBoolean(&d);
    w->ammo = dis_readInt(&d);
    if (ver >= 2 && dis_available(&d) >= 4) w->missionDone = dis_readInt(&d) != 0;
    dis_close(&d);
    RecordStore_closeRecordStore(rs);

    World_continueAt(w, x, z, y, yaw);
    return true;
}


/* ------------------------------------------------------------------ */
/* Display options                                                     */
/* ------------------------------------------------------------------ */
void Save_writeOptions(struct Game* g) {
    if (!g) return;
    DataOutputStream d;
    dos_init(&d);
    dos_writeInt(&d, 2);              /* version */
    dos_writeInt(&d, g->oBright);
    dos_writeInt(&d, g->oDraw);
    dos_writeBoolean(&d, g->oLimiter);
    dos_writeBoolean(&d, g->oTrails);
    dos_writeBoolean(&d, g->oSubs);
    dos_writeBoolean(&d, g->oWide);
    dos_writeBoolean(&d, g->oLegend);
    dos_writeInt(&d, g->oRadar);
    dos_writeBoolean(&d, g->oHud);
    dos_writeInt(&d, g->oDensity);
    dos_writeInt(&d, g->oRes);
    dos_writeBoolean(&d, g->oRadio);

    RecordStore* rs = RecordStore_openRecordStore(OPT_NAME, true);
    if (rs) {
        if (RecordStore_getNumRecords(rs) > 0) {
            RecordStore_setRecord(rs, 1, d.data, 0, d.len);
        } else {
            RecordStore_addRecord(rs, d.data, 0, d.len);
        }
        RecordStore_closeRecordStore(rs);
    }
    dos_free(&d);
}

bool Save_loadOptions(struct Game* g) {
    if (!g) return false;
    RecordStore* rs = RecordStore_openRecordStore(OPT_NAME, false);
    if (!rs) return false;
    int len = 0;
    const uint8_t* b = RecordStore_getRecord(rs, 1, &len);
    /* v1 = 30 bytes, v2 adds the radio flag (31 bytes). */
    if (!b || len < 30) {
        RecordStore_closeRecordStore(rs);
        return false;
    }

    DataInputStream d;
    dis_init(&d, b, len);
    int ver = dis_readInt(&d);
    bool ok = (ver == 1 || ver == 2);
    int bright = dis_readInt(&d);
    int draw = dis_readInt(&d);
    bool limiter = dis_readBoolean(&d);
    bool trails = dis_readBoolean(&d);
    bool subs = dis_readBoolean(&d);
    bool wide = dis_readBoolean(&d);
    bool legend = dis_readBoolean(&d);
    int radar = dis_readInt(&d);
    bool hud = dis_readBoolean(&d);
    int density = dis_readInt(&d);
    int res = dis_readInt(&d);
    bool radio = true;
    if (ver >= 2 && dis_available(&d) >= 1) radio = dis_readBoolean(&d);
    dis_close(&d);
    RecordStore_closeRecordStore(rs);
    if (!ok) return false;

    if (bright >= 0 && bright <= 15) g->oBright = bright;
    if (draw >= 0 && draw <= 15) g->oDraw = draw;
    if (radar >= 0 && radar <= 2) g->oRadar = radar;
    if (density >= 0 && density <= 3) g->oDensity = density;
    if (res >= 0 && res <= 3) g->oRes = res;
    g->oLimiter = limiter;
    g->oTrails = trails;
    g->oSubs = subs;
    g->oWide = wide;
    g->oLegend = legend;
    g->oHud = hud;
    g->oRadio = radio;
    g->needResize = true;
    return true;
}
