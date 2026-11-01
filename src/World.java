import java.io.*;

/** The streamed Vice City map piece: geometry chunks, ground/solid grids, entities, simulation and rendering. */
final class World {
    R3D r;

    // ------------------------------------------------------------------ map data
    Mesh[] chunk;
    int[] chunkX, chunkZ;               // chunk origin, 1/64 m
    int nchunk;
    int hx0, hz0, hcell, hw, hh;        // height + surface grid (mm)
    short[] hgt;                        // 1/64 m, -32768 = open sea
    byte[] surf;                        // 0 water, 1 sand, 2 grass, 3 pavement, 4 road, 5 rock
    int sx0, sz0, scell, sw, sh, srow;  // solid grid
    byte[] solid;
    int rx0, rz0, rcell, rw, rh;        // radar
    byte[] rad;
    int spX, spZ, spY, spYaw;
    int nslot;
    int[] slotX, slotZ, slotYaw;
    int lampX, lampY, lampZ;
    Mesh beam, blood;
    static final int S_WATER = 0, S_SAND = 1, S_GRASS = 2, S_PAVE = 3, S_ROAD = 4, S_ROCK = 5;

    World(R3D r) { this.r = r; }

    void loadMap() throws IOException {
        DataInputStream d = new DataInputStream(new ByteArrayInputStream(Assets.readAll("/map.bin")));
        byte[] mg = new byte[4]; d.readFully(mg);
        int[] texId = CarModel.readTex(d);
        nchunk = d.readUnsignedShort();
        chunk = new Mesh[nchunk]; chunkX = new int[nchunk]; chunkZ = new int[nchunk];
        for (int c = 0; c < nchunk; c++) {
            chunkX[c] = d.readInt() >> 4; chunkZ[c] = d.readInt() >> 4;
            int nv = d.readUnsignedShort();
            Mesh m = new Mesh();
            m.nv = nv;
            m.x = new int[nv]; m.y = new int[nv]; m.z = new int[nv]; m.u = new int[nv]; m.v = new int[nv];
            for (int i = 0; i < nv; i++) { m.x[i] = d.readShort(); m.y[i] = d.readShort(); m.z[i] = d.readShort(); m.u[i] = d.readShort(); m.v[i] = d.readShort(); }
            int nt = d.readUnsignedShort();
            m.nt = nt; m.ti = new int[nt * 3]; m.tex = new short[nt]; m.col = new int[nt]; m.flag = new byte[nt];
            m.nx = new byte[nt]; m.ny = new byte[nt]; m.nz = new byte[nt];
            for (int i = 0; i < nt; i++) {
                int a = d.readUnsignedShort(), b = d.readUnsignedShort(), cc = d.readUnsignedShort();
                m.ti[i * 3] = a; m.ti[i * 3 + 1] = b; m.ti[i * 3 + 2] = cc;
                int t = d.readUnsignedByte();
                m.tex[i] = (short) (t == 255 || t >= texId.length ? -1 : texId[t]);
                m.flag[i] = (byte) d.readUnsignedByte();
                m.col[i] = (d.readUnsignedByte() << 16) | (d.readUnsignedByte() << 8) | d.readUnsignedByte();
                CarModel.normal(m, i, a, b, cc);
            }
            m.bounds();
            chunk[c] = m;
        }
        hx0 = d.readInt(); hz0 = d.readInt(); hcell = d.readUnsignedShort(); hw = d.readUnsignedShort(); hh = d.readUnsignedShort();
        hgt = new short[hw * hh];
        for (int i = 0; i < hgt.length; i++) hgt[i] = d.readShort();
        surf = new byte[hw * hh]; d.readFully(surf);
        sx0 = d.readInt(); sz0 = d.readInt(); scell = d.readUnsignedShort(); sw = d.readUnsignedShort(); sh = d.readUnsignedShort();
        srow = d.readUnsignedShort();
        solid = new byte[srow * sh]; d.readFully(solid);
        rx0 = d.readInt(); rz0 = d.readInt(); rcell = d.readUnsignedShort(); rw = d.readUnsignedShort(); rh = d.readUnsignedShort();
        rad = new byte[rw * rh]; d.readFully(rad);
        spX = d.readInt(); spZ = d.readInt(); spY = d.readInt(); spYaw = d.readUnsignedShort();
        nslot = d.readUnsignedByte();
        slotX = new int[nslot]; slotZ = new int[nslot]; slotYaw = new int[nslot];
        for (int i = 0; i < nslot; i++) { slotX[i] = d.readInt(); slotZ[i] = d.readInt(); slotYaw[i] = d.readUnsignedShort(); }
        lampX = d.readInt(); lampY = d.readInt(); lampZ = d.readInt();
        d.close();
        buildFxMeshes();
    }

    /** lighthouse beam (two crossed tapered planes) + the blood decal quad */
    void buildFxMeshes() {
        int L = 70 * 64, w = 7 * 64;
        int[] x = { 0, -w, w, 0, 0 }, y = { 0, 0, 0, -w, w }, z = { 0, L, L, L, L };
        beam = new Mesh();
        beam.nv = 5; beam.x = x; beam.y = y; beam.z = z; beam.u = new int[5]; beam.v = new int[5];
        beam.nt = 2; beam.ti = new int[] { 0, 1, 2, 0, 3, 4 };
        beam.tex = new short[] { -1, -1 }; beam.col = new int[] { 0x2A2618, 0x2A2618 };
        beam.flag = new byte[] { (byte) (Mesh.F_DOUBLE | Mesh.F_EMISSIVE), (byte) (Mesh.F_DOUBLE | Mesh.F_EMISSIVE) };
        beam.nx = new byte[2]; beam.ny = new byte[2]; beam.nz = new byte[2];
        beam.bounds();
        blood = new Mesh();
        int q = 64;
        blood.nv = 4; blood.x = new int[] { -q, q, q, -q }; blood.y = new int[4]; blood.z = new int[] { -q, -q, q, q };
        blood.u = new int[] { 0, 511, 511, 0 }; blood.v = new int[] { 0, 0, 511, 511 };
        blood.nt = 2; blood.ti = new int[] { 0, 2, 1, 0, 3, 2 };
        int bt = Assets.idOf("blood");
        blood.tex = new short[] { (short) bt, (short) bt }; blood.col = new int[2];
        blood.flag = new byte[] { Mesh.F_DOUBLE, Mesh.F_DOUBLE };
        blood.nx = new byte[2]; blood.ny = new byte[] { 127, 127 }; blood.nz = new byte[2];
        blood.bounds();
    }

    // ------------------------------------------------------------------ grids
    /** ground height (1/64 m) at mm position, bilinear */
    int groundH(int xm, int zm) {
        int fx = (int) (((long) (xm - hx0) << 8) / hcell), fz = (int) (((long) (zm - hz0) << 8) / hcell);
        int i = fx >> 8, j = fz >> 8, ax = fx & 255, az = fz & 255;
        int h00 = hAt(i, j), h10 = hAt(i + 1, j), h01 = hAt(i, j + 1), h11 = hAt(i + 1, j + 1);
        int a = h00 + (((h10 - h00) * ax) >> 8), b = h01 + (((h11 - h01) * ax) >> 8);
        return a + (((b - a) * az) >> 8);
    }
    private int hAt(int i, int j) {
        if (i < 0 || j < 0 || i >= hw || j >= hh) return -640;
        int v = hgt[j * hw + i];
        return v == -32768 ? -640 : v;
    }
    int surfAt(int xm, int zm) {
        int i = (xm - hx0) / hcell, j = (zm - hz0) / hcell;
        if (xm < hx0 || zm < hz0 || i >= hw || j >= hh) return S_WATER;
        return surf[j * hw + i];
    }
    boolean solidCell(int i, int j) {
        if (i < 0 || j < 0 || i >= sw || j >= sh) return false;
        return (solid[j * srow + (i >> 3)] & (0x80 >> (i & 7))) != 0;
    }
    boolean deep(int xm, int zm) { return groundH(xm, zm) < -70; }
    boolean walkable(int xm, int zm) {
        int s = surfAt(xm, zm);
        if (s == S_WATER || deep(xm, zm)) return false;
        return !solidCell((xm - sx0) / scell, (zm - sz0) / scell);
    }

    /** pushes (rx, rz) out of solid cells, radius r (mm) */
    boolean resolve(int rad) {
        boolean hit = false;
        int i0 = (rx - rad - sx0) / scell, i1 = (rx + rad - sx0) / scell, j0 = (rz - rad - sz0) / scell, j1 = (rz + rad - sz0) / scell;
        if (rx - rad < sx0) i0 = -1;
        if (rz - rad < sz0) j0 = -1;
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                if (!solidCell(i, j)) continue;
                int bx0 = sx0 + i * scell, bz0 = sz0 + j * scell, bx1 = bx0 + scell, bz1 = bz0 + scell;
                int cx = rx < bx0 ? bx0 : (rx > bx1 ? bx1 : rx);
                int cz = rz < bz0 ? bz0 : (rz > bz1 ? bz1 : rz);
                int dx = rx - cx, dz = rz - cz;
                if (dx == 0 && dz == 0) {
                    int l = rx - bx0, rr = bx1 - rx, t = rz - bz0, bt = bz1 - rz;
                    int m = Math.min(Math.min(l, rr), Math.min(t, bt));
                    if (m == l) rx = bx0 - rad; else if (m == rr) rx = bx1 + rad; else if (m == t) rz = bz0 - rad; else rz = bz1 + rad;
                    hit = true;
                } else {
                    int d2 = dx * dx + dz * dz;
                    if (d2 < rad * rad) {
                        int d = (int) Math.sqrt((double) d2);
                        if (d < 1) d = 1;
                        rx = cx + dx * rad / d; rz = cz + dz * rad / d;
                        hit = true;
                    }
                }
            }
        }
        return hit;
    }

    /** moves an actor from (ox,oz,oy) towards (rx,rz): solid cells + step height; result left in rx/rz */
    boolean stepOk(int ox, int oz, int oy, int maxStep) {
        int g = groundH(rx, rz);
        if (g - oy <= maxStep) return true;
        int nx = rx, nz = rz;
        rx = nx; rz = oz;                                  // slide along x
        if (groundH(rx, rz) - oy <= maxStep) return true;
        rx = ox; rz = nz;                                  // slide along z
        if (groundH(rx, rz) - oy <= maxStep) return true;
        rx = ox; rz = oz;
        return false;
    }

    // ================================================================== simulation
    static final int K_UP = 1, K_DOWN = 2, K_LEFT = 4, K_RIGHT = 8, K_FIRE = 16, K_CAML = 32, K_CAMR = 64, K_CAMU = 128,
            K_CAMD = 256, K_SPRINT = 512, K_HAND = 1024;
    static final int PA_NONE = 0, PA_WALKTO = 1, PA_GETIN = 2, PA_GETOUT = 3;
    static final int NCAR_TYPES = 5;
    static final String[] CAR_NAMES = { "admiral", "oceanic", "taxi", "pcj600", "police" };

    CarModel[] carModels = new CarModel[NCAR_TYPES];
    PedModel pm0;
    PedModel[] npc = new PedModel[5], npcLo = new PedModel[5];
    int[] npcTex = new int[5];
    static final String[] NPC_NAMES = { "cop", "construction", "hispanic", "skater", "street" };
    byte[] tc;
    int cIdle, cWalk, cWalkB, cRun, cCiviWalk, cCiviRun, cWomanWalk, cWomanRun, cWomanIdle, cPunch, cJab, cKick,
            cHitF, cHitB, cKoF, cKoB, cGetup, cCower, cOpen, cGetin, cGetout, cPull, cJacked, cSit, cFight;

    Ped player;
    Car inCar, pActCar;
    int pAct, combo;
    Car[] cars = new Car[16];
    int ncars;
    Ped[] peds = new Ped[40];
    int nped;
    int money, health = 100, armor, wanted, pedsHit, carsStolen, kills;
    int quietKills, quietLimit = 4;     // kills nobody (no cop) saw; at 3..5 you get a star anyway
    int wantedCalmT;                     // ms without a cop near you
    int minutes = 14 * 60 + 18, clockMs;
    int camYawOff, camYaw, camPitch = 55, camIdle, camXm, camYm, camZm;
    int camDistMm = 4300;
    String help; int helpT, helpShown;
    int wastedT, dayCount, bustedT, drownT;
    boolean busted;
    int drawDist = 110;
    int brightness = 10;
    int time;
    int rx, rz;
    int shake;
    boolean runMode;
    private final int[] pose = new int[93], pose2 = new int[93];

    void initClips() {
        cIdle = AnimData.find("IDLE_stance"); cWalk = AnimData.find("WALK_player"); cWalkB = AnimData.find("walk_back");
        cRun = AnimData.find("run_player"); cCiviWalk = AnimData.find("WALK_civi"); cCiviRun = AnimData.find("run_civi");
        cWomanWalk = AnimData.find("WOMAN_walknorm"); cWomanRun = AnimData.find("woman_run"); cWomanIdle = AnimData.find("woman_idlestance");
        cPunch = AnimData.find("FIGHTppunch"); cJab = AnimData.find("FIGHTjab");
        cKick = AnimData.find("FIGHTpunch");   // 3rd hit: the heavy punch (FIGHTkick does not survive the 9-part rig retarget)
        cHitF = AnimData.find("HIT_front"); cHitB = AnimData.find("HIT_back"); cKoF = AnimData.find("KO_shot_front"); cKoB = AnimData.find("KO_skid_back");
        cGetup = AnimData.find("getup"); cCower = AnimData.find("cower"); cOpen = AnimData.find("CAR_open_LHS");
        cGetin = AnimData.find("CAR_getin_LHS"); cGetout = AnimData.find("CAR_getout_LHS"); cPull = AnimData.find("CAR_pullout_LHS");
        for (int i = 0; i < 5; i++) npcTex[i] = Assets.idOf("npc_" + NPC_NAMES[i]);
        cJacked = AnimData.find("CAR_jackedLHS"); cSit = AnimData.find("CAR_sit"); cFight = AnimData.find("FIGHTIDLE");
        // handling.cfg (km/h, accel), scaled for the small map
        int[] mv = { 165, 150, 160, 190, 190 }, ac = { 22, 20, 22, 32, 26 }, lk = { 30, 30, 34, 38, 32 };
        for (int i = 0; i < NCAR_TYPES; i++) {
            CarModel m = carModels[i];
            if (m == null) continue;
            m.vmax = mv[i] * 6 / 36; m.accel = ac[i] * 14; m.brake = 900; m.lockDeg = lk[i];
            m.wheelbase = m.dimZ * 16 * 6 / 10;
        }
    }

    void assignKind(Ped p, int k) { p.kind = k; p.tex = npcTex[k]; p.female = k >= 3; }

    void reset() {
        Fx.seed = 99991;
        initClips();
        player = new Ped();
        placePlayerAtSpawn();
        player.tex = Assets.T_PED0; player.loco = cIdle;
        inCar = null; pActCar = null; pAct = 0;
        ncars = 0; nped = 0;
        health = 100; armor = 0; money = 0; wanted = 0; pedsHit = 0; carsStolen = 0; kills = 0; quietKills = 0; quietLimit = 3 + Fx.rnd(3);
        minutes = 14 * 60 + 18; wastedT = 0; helpShown = 0; helpT = 0; busted = false; bustedT = 0; drownT = 0; runMode = false;
        camYawOff = 0; camYaw = player.yaw; camPitch = 55;
        int[] types = { Car.ADMIRAL, Car.OCEANIC, Car.TAXI, Car.PCJ600 };
        for (int i = 0; i < nslot && i < 4; i++) {
            Car c = addCar(Car.PARKED, types[i], slotX[i], slotZ[i], slotYaw[i], i == 0 ? 4 : i + 1);
            c.y = groundH(c.x, c.z);
        }
        for (int i = 0; i < 3; i++) spawnTraffic(true);
        for (int i = 0; i < 14; i++) spawnPed(true);
    }

    void placePlayerAtSpawn() {
        player.x = spX; player.z = spZ; player.yaw = spYaw; player.y = groundH(spX, spZ);
        player.state = Ped.WALK; player.hp = 100; player.clip = -1; player.seat = null; player.speed = 0;
    }

    Car addCar(int mode, int type, int xm, int zm, int yaw, int colorIdx) {
        if (ncars >= cars.length) return null;
        Car c = new Car();
        c.mode = mode; c.type = type; c.x = xm; c.z = zm; c.yaw = yaw & 1023; c.y = groundH(xm, zm);
        c.color = Car.COLORS[colorIdx % Car.COLORS.length];
        if (type == Car.TAXI) { c.color = 0xF0C820; c.color2 = 0xF0C820; }
        if (type == Car.POLICE_CAR) { c.color = 0xF4F4F4; c.color2 = 0x202430; }
        if (type == Car.PCJ600) c.color2 = 0x303030;
        cars[ncars++] = c;
        return c;
    }

    void removeCar(int i) {
        Car c = cars[i];
        if (c.driver != null) { c.driver.seat = null; c.driver.state = Ped.DEAD; c.driver.timer = 0; c.driver.hp = 0; c.driver.deadT = -1; c.driver = null; }
        cars[i] = cars[ncars - 1]; ncars--;
    }

    void addDriver(Car c, boolean cop) {
        Ped d = null;
        for (int i = 0; i < nped; i++) if (peds[i].state == Ped.DEAD && peds[i].deadT < 0 && peds[i].seat == null && !peds[i].cop) { d = peds[i]; break; }
        if (d == null) { if (nped >= peds.length) return; d = new Ped(); peds[nped++] = d; }
        d.seat = c; c.driver = d; d.cop = cop; assignKind(d, cop ? 0 : 1 + Fx.rnd(4)); d.state = Ped.IDLE; d.loco = cIdle; d.hp = 100; d.clip = -1; d.deadT = 0;
    }

    int vmaxOf(Car c) { CarModel m = carModels[c.type]; return m != null ? m.vmax * 1024 : 24 * 1024; }

    /** random position around the player at distance [d0,d1] metres satisfying a surface rule; returns false if none */
    final int[] spot = new int[2];
    boolean findSpot(int d0, int d1, int want) {
        for (int k = 0; k < 24; k++) {
            int a = Fx.rnd(1024), d = (d0 + Fx.rnd(d1 - d0 + 1)) * 1024;
            int x = player.x + ((Fx.sin(a) * (d >> 4)) >> 10), z = player.z + ((Fx.cos(a) * (d >> 4)) >> 10);
            int s = surfAt(x, z);
            boolean ok = want == S_ROAD ? s == S_ROAD : (s != S_WATER && s != S_ROAD && walkable(x, z));
            if (ok) { spot[0] = x; spot[1] = z; return true; }
        }
        return false;
    }

    /** direction (yaw) with the longest road run from a point */
    int roadYaw(int x, int z) {
        int best = 0, bl = -1;
        for (int a = 0; a < 1024; a += 64) {
            int l = 0;
            for (int s = 2; s <= 24; s += 2) {
                if (surfAt(x + ((Fx.sin(a) * s) >> 4), z + ((Fx.cos(a) * s) >> 4)) != S_ROAD) break;
                l++;
            }
            if (l > bl) { bl = l; best = a; }
        }
        return best;
    }

    void spawnTraffic(boolean initial) {
        if (!findSpot(initial ? 20 : 70, initial ? 90 : 110, S_ROAD)) return;
        int type = Fx.rnd(10) < 3 ? Car.TAXI : (Fx.rnd(2) == 0 ? Car.ADMIRAL : Car.OCEANIC);
        if (Fx.rnd(8) == 0) type = Car.PCJ600;
        Car c = addCar(Car.TRAFFIC, type, spot[0], spot[1], roadYaw(spot[0], spot[1]), Fx.rnd(10));
        if (c == null) return;
        c.speed = 4 * 1024;
        addDriver(c, false);
    }

    void spawnPed(boolean initial) {
        if (!findSpot(initial ? 8 : 45, initial ? 70 : 80, S_PAVE)) return;
        Ped p = null;
        for (int i = 0; i < nped; i++) if (peds[i].state == Ped.DEAD && peds[i].timer <= 0 && !peds[i].cop && peds[i].seat == null) { p = peds[i]; break; }
        if (p == null) {
            if (nped >= peds.length) return;
            p = new Ped();
            peds[nped++] = p;
        }
        p.x = spot[0]; p.z = spot[1];
        p.state = Ped.WALK; p.hp = 100; p.cash = 0; p.cop = false; p.clip = -1; p.clipNext = -1; p.seat = null; p.deadT = 0;
        p.yaw = Fx.rnd(1024); p.targetYaw = p.yaw;
        p.speed = 1150 + Fx.rnd(350);
        assignKind(p, 1 + Fx.rnd(4));
        p.turnTimer = 1500 + Fx.rnd(4000);
        p.locoT = Fx.rnd(1000);
        p.y = groundH(p.x, p.z);
        p.timer = 0;
    }

    Ped spawnCopAt(int x, int z) {
        Ped p = null;
        for (int i = 0; i < nped; i++) if (peds[i].state == Ped.DEAD && peds[i].timer <= 0 && peds[i].seat == null) { p = peds[i]; break; }
        if (p == null) { if (nped >= peds.length) return null; p = new Ped(); peds[nped++] = p; }
        p.x = x; p.z = z; p.cop = true; assignKind(p, 0); p.hp = 160; p.state = Ped.WALK; p.speed = 0; p.clip = -1; p.seat = null;
        p.deadT = 0; p.cash = 0; p.closeT = 0; p.attackCd = 1500; p.y = groundH(x, z); p.yaw = Fx.yawOf(player.x - x, player.z - z);
        return p;
    }

    void spawnCop() {
        if (findSpot(28, 42, S_PAVE)) spawnCopAt(spot[0], spot[1]);
    }

    void spawnPoliceCar() {
        if (!findSpot(60, 90, S_ROAD)) return;
        Car c = addCar(Car.POLICE, Car.POLICE_CAR, spot[0], spot[1], Fx.yawOf(player.x - spot[0], player.z - spot[1]), 0);
        if (c == null) return;
        c.speed = 6 * 1024;
        addDriver(c, true);
    }

    int copsNear(int rangeMm) {
        int n = 0;
        for (int i = 0; i < nped; i++) {
            Ped p = peds[i];
            if (!p.cop || p.state == Ped.DEAD) continue;
            int x = p.seat != null ? p.seat.x : p.x, z = p.seat != null ? p.seat.z : p.z;
            if (Fx.dist(x - player.x, z - player.z) < rangeMm) n++;
        }
        return n;
    }
    void raiseWanted(int lvl) {
        if (wanted < lvl) { wanted = lvl; wantedCalmT = 0; }
    }

    void say(String s) { help = s; helpT = 4500; }

    // ------------------------------------------------------------------ clips
    void startClip(Ped p, int clip, int next, int endMs) {
        p.clip = clip; p.clipT = 0; p.clipNext = next; p.clipEnd = endMs; p.hitDone = false;
    }
    int clipLen(Ped p) {
        int l = AnimData.len(p.clip);
        return p.clipEnd > 0 && p.clipEnd < l ? p.clipEnd : l;
    }
    /** advances the one-shot clip; returns true at the moment it finishes */
    boolean stepClip(Ped p, int dt) {
        if (p.clip < 0) return false;
        p.clipT += dt;
        int len = clipLen(p);
        if (p.clipT < len) return false;
        if (p.clipNext == -2) { p.clipT = len; return false; }
        if (p.clip == cGetout || p.clip == cGetin || p.clip == cPull || p.clip == cJacked || p.clip == cOpen) {
            AnimData.sample(p.clip, len, pose2);           // car clips move the root: keep the final offset
            int offR = pose2[90] * 16, offF = pose2[92] * 16, c = Fx.cos(p.yaw), s = Fx.sin(p.yaw);
            p.x += (offR * c + offF * s) >> 14; p.z += (-offR * s + offF * c) >> 14;
        }
        p.clip = p.clipNext; p.clipNext = -1; p.clipT = 0; p.clipEnd = 0; p.hitDone = false;
        return true;
    }
    void selectLoco(Ped p, int dt, int speed) {
        int sp = Fx.abs(speed), c;
        boolean f = p.female;
        if (sp < 150) { c = f ? cWomanIdle : cIdle; p.locoT += dt; }
        else {
            if (speed < 0) c = cWalkB;
            else if (sp < 3000) c = p == player ? cWalk : (f ? cWomanWalk : cCiviWalk);
            else c = p == player || p.cop ? cRun : (f ? cWomanRun : cCiviRun);
            int ratio = sp * 256 / AnimData.speed(c);
            if (ratio < 90) ratio = 90; if (ratio > 560) ratio = 560;
            p.locoT += dt * ratio >> 8;
        }
        p.loco = c;
    }

    // ------------------------------------------------------------------ update
    void update(int dt, int keys, boolean fire, boolean enter, boolean toggleRun) {
        if (dt > 100) dt = 100;
        time += dt;
        clockMs += dt;
        while (clockMs >= 1000) { clockMs -= 1000; minutes++; if (minutes >= 1440) { minutes = 0; dayCount++; } }
        if (helpT > 0) helpT -= dt;
        if (shake > 0) shake -= dt;
        if (busted) {
            bustedT += dt;
            updateWorld(dt); updateCamera(dt, keys);
            if (bustedT > 3600) { busted = false; bustedT = 0; arrestRespawn(); }
            return;
        }
        if (health <= 0) {
            if (wastedT == 0) { player.seat = null; if (inCar != null) { inCar.mode = Car.PARKED; inCar = null; } pAct = 0; startClip(player, cKoF, -2, 0); }
            wastedT += dt;
            stepClip(player, dt);
            if (wastedT > 3600) respawn();
            updateWorld(dt);
            updateCamera(dt, keys);
            return;
        }
        if (toggleRun && inCar == null) { runMode = !runMode; say(runMode ? "Run: ON" : "Run: OFF  (walking)"); helpT = 1600; }
        if (pAct != 0) updateAct(dt);
        else {
            if (enter) handleEnter();
            if (pAct == 0) { if (inCar != null) updateDriving(dt, keys); else updateOnFoot(dt, keys, fire); }
        }
        // drowning: Tommy can't swim
        boolean inWater = inCar != null ? deep(inCar.x, inCar.z) : (pAct == 0 && deep(player.x, player.z));
        if (inWater) { drownT += dt; if (inCar != null) inCar.speed = inCar.speed * 9 / 10; if (drownT > 1500) { health = 0; wastedT = 0; say("Tommy can't swim!"); } }
        else drownT = 0;
        updateWorld(dt);
        updateCamera(dt, keys);
        updateWanted(dt);
    }

    void updateWanted(int dt) {
        if (wanted == 0) return;
        if (copsNear(40 * 1024) == 0) {
            wantedCalmT += dt;
            if (wantedCalmT > 22000) { wanted--; wantedCalmT = 0; if (wanted == 0) say("You lost the cops."); }
        } else wantedCalmT = 0;
        int foot = 0, carsP = 0;
        for (int i = 0; i < nped; i++) if (peds[i].cop && peds[i].state != Ped.DEAD && peds[i].seat == null) foot++;
        for (int i = 0; i < ncars; i++) if (cars[i].mode == Car.POLICE) carsP++;
        int maxFoot = wanted == 1 ? 1 : (wanted == 2 ? 2 : 3);
        if (foot < maxFoot && (time % 4000) < dt) spawnCop();
        if (wanted >= 2 && carsP < (wanted >= 4 ? 2 : 1) && (time % 9000) < dt) spawnPoliceCar();
    }

    void respawn() {
        wastedT = 0; health = 100; armor = 0;
        money = money > 100 ? money - 100 : 0;
        wanted = 0; quietKills = 0;
        placePlayerAtSpawn();
        clearCops();
        say("You have been treated at the Ocean Beach clinic. $100 fee.");
    }
    void arrestRespawn() {
        money = money > 100 ? money - 100 : 0;
        wanted = 0; quietKills = 0;
        placePlayerAtSpawn();
        clearCops();
        say("Busted! $100 bail.");
    }
    void clearCops() {
        for (int i = nped - 1; i >= 0; i--) if (peds[i].cop && peds[i].seat == null) { peds[i] = peds[nped - 1]; nped--; }
        for (int i = ncars - 1; i >= 0; i--) if (cars[i].mode == Car.POLICE) removeCar(i);
    }

    // ---- car <-> world helpers (local units 1/64 m, x right, z forward)
    int localX(Car c, int lx, int lz) { return c.x + (((Fx.cos(c.yaw) * lx + Fx.sin(c.yaw) * lz) >> 14) * 16); }
    int localZ(Car c, int lx, int lz) { return c.z + (((-Fx.sin(c.yaw) * lx + Fx.cos(c.yaw) * lz) >> 14) * 16); }
    int seatLX(Car c) { CarModel m = carModels[c.type]; return m.bike ? 0 : -Fx.abs(m.seatX); }
    int seatLZ(Car c) { return carModels[c.type].seatZ; }

    void handleEnter() {
        if (inCar != null) {
            Car c = inCar;
            Ped p = player;
            p.x = localX(c, seatLX(c), seatLZ(c)); p.z = localZ(c, seatLX(c), seatLZ(c)); p.yaw = c.yaw; p.y = c.y;
            p.seat = null;
            c.mode = Car.PARKED;
            inCar = null;
            if (carModels[c.type].bike) { p.x = localX(c, -60, 0); p.z = localZ(c, -60, 0); p.clip = -1; p.y = groundH(p.x, p.z); return; }
            startClip(p, cGetout, -1, 0);
            pAct = PA_GETOUT;
            return;
        }
        Car best = null; int bd = 4600;
        for (int i = 0; i < ncars; i++) {
            Car c = cars[i];
            if (c.wrecked) continue;
            int d = Fx.dist(c.x - player.x, c.z - player.z);
            if (d < bd && Fx.abs(c.speed) < 3500) { bd = d; best = c; }
        }
        if (best != null) { pActCar = best; pAct = PA_WALKTO; player.clip = -1; camYawOff = 0; }
    }

    void updateAct(int dt) {
        Car c = pActCar; Ped p = player;
        boolean bike = carModels[c.type].bike;
        if (pAct == PA_WALKTO) {
            int tx = bike ? localX(c, -60, 0) : localX(c, seatLX(c) - 59, seatLZ(c) - 34), tz = bike ? localZ(c, -60, 0) : localZ(c, seatLX(c) - 59, seatLZ(c) - 34);
            int dx = tx - p.x, dz = tz - p.z, d = Fx.dist(dx, dz);
            if (d < 450) {
                p.x = tx; p.z = tz; p.yaw = c.yaw; p.speed = 0;
                if (c.driver != null && c.driver.seat == c) {          // carjack: pull the driver out
                    Ped v = c.driver;
                    v.seat = null; c.driver = null;
                    v.x = localX(c, seatLX(c), seatLZ(c)); v.z = localZ(c, seatLX(c), seatLZ(c)); v.yaw = c.yaw; v.y = c.y;
                    v.state = Ped.JACKED; v.hp = 100;
                    startClip(v, cJacked, cGetup, 0);
                    if (v.cop) raiseWanted(2); else if (copsNear(30 * 1024) > 0) raiseWanted(1);
                    pedsHit++;
                    if (bike) { pAct = PA_GETIN; p.clip = -1; }
                    else startClip(p, cPull, cGetin, 0);
                } else if (bike) p.clip = -1;
                else startClip(p, cOpen, cGetin, 0);
                pAct = PA_GETIN;
            } else {
                int want = Fx.yawOf(dx, dz);
                p.yaw = (p.yaw + Fx.clamp(Fx.angDiff(p.yaw, want), -700 * dt / 1000, 700 * dt / 1000)) & 1023;
                int sp = 4200;
                p.x += ((Fx.sin(p.yaw) * sp) >> 14) * dt / 1000; p.z += ((Fx.cos(p.yaw) * sp) >> 14) * dt / 1000;
                p.y = groundH(p.x, p.z);
                p.speed = sp; selectLoco(p, dt, sp);
            }
        } else if (pAct == PA_GETIN) {
            stepClip(p, dt);
            if (p.clip < 0) {
                inCar = c; c.mode = Car.PLAYER; p.seat = c; p.speed = 0; pAct = 0; carsStolen++;
                p.x = c.x; p.z = c.z;
                if (c.type == Car.POLICE_CAR) raiseWanted(1);
            }
        } else if (pAct == PA_GETOUT) {
            stepClip(p, dt);
            if (p.clip < 0) { pAct = 0; p.y = groundH(p.x, p.z); }
        }
    }

    void updateOnFoot(int dt, int keys, boolean fire) {
        Ped p = player;
        if (p.clip >= 0) {                                   // fighting animation in progress
            stepClip(p, dt);
            if (p.clip >= 0 && !p.hitDone && p.clipT >= (p.clip == cKick ? 330 : 170)) { p.hitDone = true; doPunch(p.clip == cKick ? 45 : 28); }
            p.speed = 0;
            return;
        }
        int turn = 0, move = 0;
        if ((keys & K_LEFT) != 0) turn -= 1;
        if ((keys & K_RIGHT) != 0) turn += 1;
        if ((keys & K_UP) != 0) move += 1;
        if ((keys & K_DOWN) != 0) move -= 1;
        p.yaw = (p.yaw + turn * 820 * dt / 1000) & 1023;
        int sp = 0;
        boolean sprint = (keys & K_SPRINT) != 0;
        if (move > 0) sp = sprint ? 6600 : (runMode ? 4800 : 1700);
        else if (move < 0) sp = runMode ? -2600 : -1700;
        p.speed = sp;
        if (fire) {
            // 1-2-3 combo: jab, punch, kick (each one-shot, played in place)
            combo = combo % 3 + 1;
            if (combo == 1) startClip(p, cJab, -1, 560); else if (combo == 2) startClip(p, cPunch, -1, 0); else startClip(p, cKick, -1, 0);
            return;
        }
        rx = p.x + ((Fx.sin(p.yaw) * sp) >> 14) * dt / 1000;
        rz = p.z + ((Fx.cos(p.yaw) * sp) >> 14) * dt / 1000;
        resolve(300);
        stepOk(p.x, p.z, p.y, 60);
        for (int i = 0; i < ncars; i++) {
            Car c = cars[i];
            int half = carModels[c.type].bike ? 600 : 1300, rr = carModels[c.type].bike ? 300 + 450 : 300 + 900;
            for (int k = -1; k <= 1; k++) {
                int cx = c.x + ((Fx.sin(c.yaw) * k * half) >> 14), cz = c.z + ((Fx.cos(c.yaw) * k * half) >> 14);
                int dx = rx - cx, dz = rz - cz;
                if (dx > rr || dx < -rr || dz > rr || dz < -rr) continue;
                int d = Fx.dist(dx, dz);
                if (d < rr && d > 0) { rx = cx + dx * rr / d; rz = cz + dz * rr / d; }
            }
        }
        int mv = Fx.dist(rx - p.x, rz - p.z);
        p.x = rx; p.z = rz;
        selectLoco(p, dt, mv == 0 && sp != 0 ? sp / 6 : sp);
        int gy = groundH(p.x, p.z);
        if (gy < p.y - 4) p.y += Math.max(gy - p.y, -(4 + 600 * dt / 1000));    // fall
        else p.y = gy;
    }

    void doPunch(int dmgBase) {
        Ped p = player;
        int fx = Fx.sin(p.yaw), fz = Fx.cos(p.yaw);
        for (int i = 0; i < nped; i++) {
            Ped o = peds[i];
            if (o.state == Ped.DEAD || o.seat != null || o.state == Ped.JACKED) continue;
            int dx = o.x - p.x, dz = o.z - p.z;
            int d = Fx.dist(dx, dz);
            if (d > 1700 || d == 0) continue;
            if ((dx * fx + dz * fz) / 16384 < d * 5 / 10) continue;
            damagePed(o, dmgBase + Fx.rnd(14), true, p.x, p.z);
            int push = o.state == Ped.DEAD ? 0 : 250;
            rx = o.x + dx * push / d; rz = o.z + dz * push / d;
            resolve(280);
            if (walkable(rx, rz)) { o.x = rx; o.z = rz; }
            break;                                            // one victim per blow
        }
    }

    void damagePed(Ped o, int dmg, boolean byPlayer, int ax, int az) {
        if (o.state == Ped.DEAD) return;
        o.hp -= dmg;
        pedsHit++;
        boolean front = ((ax - o.x) >> 4) * (Fx.sin(o.yaw) >> 4) + ((az - o.z) >> 4) * (Fx.cos(o.yaw) >> 4) > 0;
        boolean witnessed = byPlayer && copsNear(30 * 1024) > 0;
        if (o.hp <= 0) {
            o.state = Ped.DEAD; o.timer = 14000; o.deadT = 0;
            int back = front ? -700 : 700;
            o.bloodX = o.x + ((Fx.sin(o.yaw) * back) >> 14); o.bloodZ = o.z + ((Fx.cos(o.yaw) * back) >> 14);
            o.bloodY = groundH(o.bloodX, o.bloodZ);
            startClip(o, front ? cKoF : cKoB, -2, 0);
            if (!o.cop) o.cash = 10 + Fx.rnd(90);
            if (byPlayer) {
                kills++;
                if (o.cop) raiseWanted(wanted >= 1 ? 2 : 1);
                else if (witnessed) raiseWanted(1);
                else if (wanted == 0 && ++quietKills >= quietLimit) { raiseWanted(1); quietKills = 0; quietLimit = 3 + Fx.rnd(3); }
            }
        } else {
            o.state = Ped.HIT;
            startClip(o, front ? cHitF : cHitB, -1, 0);
            if (byPlayer) { if (o.cop) raiseWanted(1); else if (witnessed) raiseWanted(1); }
        }
    }

    void hurt(int dmg) {
        if (health <= 0 || dmg <= 0) return;
        if (armor > 0) { armor -= dmg; if (armor < 0) { health += armor; armor = 0; } }
        else health -= dmg;
        if (health <= 0) { health = 0; wastedT = 0; }
        shake = 250;
    }

    void updateDriving(int dt, int keys) {
        Car c = inCar;
        int acc = 0, st = 0;
        if ((keys & (K_UP | K_FIRE)) != 0) acc += 1;
        if ((keys & K_DOWN) != 0) acc -= 1;
        if ((keys & K_LEFT) != 0) st -= 1;
        if ((keys & K_RIGHT) != 0) st += 1;
        if (c.wrecked) acc = 0;
        driveCar(c, dt, acc, st, (keys & K_HAND) != 0);
        player.x = c.x; player.z = c.z; player.yaw = c.yaw; player.y = c.y;
    }

    /** bicycle model with a smoothly moving steering angle and speed-sensitive lock */
    void driveCar(Car c, int dt, int acc, int st, boolean hand) {
        CarModel cm = carModels[c.type];
        int vmax = cm.vmax * 1024, ac = cm.accel * 1024 / 100, br = cm.brake * 1024 / 100;
        int sp = c.speed;
        c.braking = false;
        if (acc > 0) {
            if (sp < -200) { sp += br * dt / 1000; c.braking = true; }
            else if (sp < vmax) sp += ac * dt / 1000 * (vmax - sp + vmax / 4) / vmax;
        } else if (acc < 0) {
            if (sp > 300) { sp -= br * dt / 1000; c.braking = true; }
            else if (sp > -vmax / 4) sp -= ac * dt / 1000 / 2;
        } else {
            int drag = (400 + Fx.abs(sp) / 12) * dt / 1000 * 3;
            if (sp > drag) sp -= drag; else if (sp < -drag) sp += drag; else sp = 0;
        }
        if (hand) { int bk = br * 12 / 10 * dt / 1000; if (sp > bk) sp -= bk; else if (sp < -bk) sp += bk; else sp = 0; }
        // off-road drag (sand / grass)
        int s = surfAt(c.x, c.z);
        if (s == S_SAND && Fx.abs(sp) > 5000) sp -= sp * dt / 1400;
        c.speed = sp;
        int asp = Fx.abs(sp);
        int lock = cm.lockDeg * 1024 / 360;
        int k = Math.min(asp, 40000) >> 6, kv = 14000 >> 6;
        int lockEff = lock * 256 / (256 + k * k * 256 / (kv * kv));
        int target = st * lockEff;
        int diff = target - c.steer;
        int rate = (st == 0 ? 820 : 420) * dt / 1000 + 1;
        c.steer += Fx.clamp(diff, -rate, rate);
        int cs = Fx.cos(c.steer);
        int tan14 = (Fx.sin(c.steer) << 14) / (cs < 1000 ? 1000 : cs);
        int omega = sp * tan14 / cm.wheelbase;
        int yawRate = (omega * 163) >> 14;
        if (hand && asp > 3000) yawRate = yawRate * 15 / 10;
        c.yaw = (c.yaw + yawRate * dt / 1000) & 1023;
        moveCar(c, dt);
    }

    /** damage a car region: 0 front, 1 rear, 2 left, 3 right */
    void damageCar(Car c, int region, int amount) {
        if (amount <= 0) return;
        c.hp -= amount;
        int[] parts;
        switch (region) {
            case 0: parts = new int[] { CarModel.P_BUMP_F, CarModel.P_BONNET, CarModel.P_WING_LF, CarModel.P_WING_RF, CarModel.P_WINDSCREEN }; break;
            case 1: parts = new int[] { CarModel.P_BUMP_R, CarModel.P_BOOT }; break;
            case 2: parts = new int[] { CarModel.P_DOOR_LF, CarModel.P_DOOR_LR, CarModel.P_WING_LF }; break;
            default: parts = new int[] { CarModel.P_DOOR_RF, CarModel.P_DOOR_RR, CarModel.P_WING_RF }; break;
        }
        for (int i = 0; i < parts.length; i++) {
            int p = parts[i];
            int a = i == 0 ? amount : amount * 2 / 3;
            if (p == CarModel.P_WINDSCREEN) a = amount / 2;
            c.partDmg[p] += a;
            if (c.partDmg[p] > 150) c.damBits |= 1 << p;
            boolean canFall = p == CarModel.P_BUMP_F || p == CarModel.P_BUMP_R || p == CarModel.P_BONNET || p == CarModel.P_BOOT || (p >= CarModel.P_DOOR_LF && p <= CarModel.P_DOOR_RR);
            if (canFall && c.partDmg[p] > 700) c.lostBits |= 1 << p;
        }
        if (c.hp <= 0 && !c.wrecked) {
            c.wrecked = true; c.color = 0x2A2622; c.color2 = 0x2A2622; c.speed = 0;
            if (c == inCar) say("This vehicle is wrecked.");
        }
    }

    void moveCar(Car c, int dt) {
        int dx = ((Fx.sin(c.yaw) * c.speed) >> 14) * dt / 1000, dz = ((Fx.cos(c.yaw) * c.speed) >> 14) * dt / 1000;
        int ox = c.x, oz = c.z;
        c.x += dx; c.z += dz;
        CarModel cm = carModels[c.type];
        int half = cm.dimZ * 16 / 2 - 800, rad = cm.bike ? 380 : cm.dimX * 16 / 2 + 60;
        if (half < 200) half = 200;
        boolean hit = false;
        for (int k = -1; k <= 1; k++) {
            int ox2 = (Fx.sin(c.yaw) * k * half) >> 14, oz2 = (Fx.cos(c.yaw) * k * half) >> 14;
            rx = c.x + ox2; rz = c.z + oz2;
            if (resolve(rad)) { hit = true; c.x = rx - ox2; c.z = rz - oz2; }
            int g = groundH(rx, rz);
            if (g - c.y > 50 && g > -60) { hit = true; c.x = ox; c.z = oz; }      // kerb wall / sea wall
        }
        if (hit) {
            int asp = Fx.abs(c.speed);
            if (asp > 3000) {
                if (c.mode == Car.PLAYER) { shake = 300; hurt(asp / 5000); }
                damageCar(c, c.speed > 0 ? 0 : 1, asp / 40);
            }
            c.speed = c.speed / 4 * -1 / 2;
        }
        for (int i = 0; i < ncars; i++) {
            Car o = cars[i];
            if (o == c) continue;
            int ddx = o.x - c.x, ddz = o.z - c.z;
            if (ddx > 6500 || ddx < -6500 || ddz > 6500 || ddz < -6500) continue;
            int d = Fx.dist(ddx, ddz);
            if (d < 2500 && d > 0) {
                int push = (2500 - d) / 2;
                o.x += ddx * push / d; o.z += ddz * push / d;
                c.x -= ddx * push / d; c.z -= ddz * push / d;
                int rel = Fx.abs(c.speed - o.speed);
                if (rel > 3000) {
                    if (c.mode == Car.PLAYER || o.mode == Car.PLAYER) shake = 250;
                    damageCar(c, regionOf(c, o.x, o.z), rel / 50);
                    damageCar(o, regionOf(o, c.x, c.z), rel / 50);
                    if ((o.mode == Car.POLICE || o.driver != null && o.driver.cop) && c.mode == Car.PLAYER) raiseWanted(1);
                }
                o.speed = (o.speed + c.speed) / 2;
                c.speed = c.speed * 6 / 10;
            }
        }
        int g = groundH(c.x, c.z);
        c.y += (g - c.y) * Math.min(dt, 100) / 90;
        c.wheelRot = (c.wheelRot + c.speed * dt / 1000 * 1024 / 2200) & 1023;
        if (Fx.abs(c.speed) > 2500) {
            for (int i = 0; i < nped; i++) {
                Ped p = peds[i];
                if (p.state == Ped.DEAD || p.seat != null || p.state == Ped.JACKED) continue;
                int ddx = p.x - c.x, ddz = p.z - c.z;
                if (ddx > 2800 || ddx < -2800 || ddz > 2800 || ddz < -2800) continue;
                if (Fx.dist(ddx, ddz) < (cm.bike ? 1300 : 2000)) {
                    damagePed(p, Fx.abs(c.speed) / 140, c.mode == Car.PLAYER, c.x, c.z);
                    p.x += ((Fx.sin(c.yaw) * 1500) >> 14); p.z += ((Fx.cos(c.yaw) * 1500) >> 14);
                    if (c.mode == Car.PLAYER) shake = 150;
                }
            }
            if (c.mode != Car.PLAYER && inCar == null && pAct == 0) {
                int ddx = player.x - c.x, ddz = player.z - c.z;
                if (Fx.dist(ddx, ddz) < 1900) { hurt(Fx.abs(c.speed) / 1024 * 3); c.speed = c.speed / 3; }
            }
        }
    }

    int regionOf(Car c, int x, int z) {
        int dx = x - c.x, dz = z - c.z;
        int lx = (Fx.cos(c.yaw) * dx - Fx.sin(c.yaw) * dz) >> 14, lz = (Fx.sin(c.yaw) * dx + Fx.cos(c.yaw) * dz) >> 14;
        if (Fx.abs(lz) > Fx.abs(lx) * 2) return lz > 0 ? 0 : 1;
        return lx < 0 ? 2 : 3;
    }

    void updateWorld(int dt) {
        for (int i = 0; i < ncars; i++) {
            Car c = cars[i];
            if (c.mode == Car.TRAFFIC) trafficAI(c, dt);
            else if (c.mode == Car.POLICE) policeAI(c, dt);
            else if (c.mode == Car.PARKED && c.speed != 0) {
                int drag = 9 * 1024 * dt / 1000;
                if (c.speed > drag) c.speed -= drag; else if (c.speed < -drag) c.speed += drag; else c.speed = 0;
                c.steer = c.steer * 3 / 4;
                moveCar(c, dt);
            }
        }
        // recycle far traffic
        int traffic = 0;
        for (int i = ncars - 1; i >= 0; i--) {
            Car c = cars[i];
            if (c.mode != Car.TRAFFIC && c.mode != Car.POLICE) continue;
            if (Fx.dist(c.x - player.x, c.z - player.z) > 140 * 1024) { removeCar(i); continue; }
            if (c.mode == Car.TRAFFIC) traffic++;
        }
        if (traffic < 3 && (time % 1500) < dt) spawnTraffic(false);
        for (int i = 0; i < nped; i++) updatePed(peds[i], dt);
        int alive = 0;
        for (int i = 0; i < nped; i++) {
            Ped p = peds[i];
            if (p.seat != null) continue;
            int d = Fx.dist(p.x - player.x, p.z - player.z);
            if (p.cop) {
                if ((wanted == 0 && d > 50000) || d > 120000) { if (p.state != Ped.DEAD) { p.state = Ped.DEAD; p.timer = 0; p.deadT = -1; } p.cop = p.state != Ped.DEAD; }
                continue;
            }
            if (p.state != Ped.DEAD && p.state != Ped.JACKED && p.state != Ped.GETUP && d > 110000) { p.state = Ped.DEAD; p.timer = 0; p.deadT = -1; }
            if (p.state != Ped.DEAD) alive++;
        }
        if (alive < 14 && (time % 700) < dt) spawnPed(false);
    }

    /** right-hand lane follower on the road mask */
    void trafficAI(Car c, int dt) {
        if (c.driver == null || c.driver.seat != c) { c.mode = Car.PARKED; return; }
        int fx = Fx.sin(c.yaw), fz = Fx.cos(c.yaw), rxv = fz, rzv = -fx;           // forward, right (2.14)
        int look = 6000 + Fx.abs(c.speed) / 2;
        int ax = c.x + ((fx * (look >> 4)) >> 10), az = c.z + ((fz * (look >> 4)) >> 10);
        // lateral position at the look-ahead point: distance to the right and left edge
        int eR = 0, eL = 0;
        for (int d = 1; d <= 12; d++) { if (surfAt(ax + rxv * d / 16, az + rzv * d / 16) != S_ROAD) break; eR = d; }
        for (int d = 1; d <= 12; d++) { if (surfAt(ax - rxv * d / 16, az - rzv * d / 16) != S_ROAD) break; eL = d; }
        int st = 0;
        boolean aheadRoad = surfAt(ax, az) == S_ROAD;
        if (aheadRoad) {
            int width = eL + eR + 1, target = width / 4;
            if (eR > target + 1) st = 1; else if (eR < target - 1 || eR == 0) st = -1;
        } else {
            int bestA = 0, bestScore = -1;
            for (int a = -192; a <= 192; a += 64) {
                if (a == 0) continue;
                int yy = (c.yaw + a) & 1023, sc = 0;
                for (int s = 4; s <= 16; s += 4) if (surfAt(c.x + ((Fx.sin(yy) * s) >> 4), c.z + ((Fx.cos(yy) * s) >> 4)) == S_ROAD) sc++;
                if (a > 0) sc = sc * 2 + 1; else sc *= 2;                     // prefer right turns
                if (sc > bestScore) { bestScore = sc; bestA = a; }
            }
            st = bestScore <= 1 ? 1 : (bestA > 0 ? 1 : -1);
        }
        // obstacles
        boolean block = false;
        for (int i = 0; i < ncars && !block; i++) {
            Car o = cars[i];
            if (o == c) continue;
            int dx = o.x - c.x, dz = o.z - c.z;
            int fwd = (dx * fx + dz * fz) >> 14, lat = (dx * rxv + dz * rzv) >> 14;
            if (fwd > 0 && fwd < look && lat < 2200 && lat > -2200) block = true;
        }
        if (!block) {
            int dx = player.x - c.x, dz = player.z - c.z;
            int fwd = (dx * fx + dz * fz) >> 14, lat = (dx * rxv + dz * rzv) >> 14;
            if (inCar == null && fwd > 0 && fwd < look && lat < 2000 && lat > -2000) block = true;
            for (int i = 0; i < nped && !block; i++) {
                Ped p = peds[i];
                if (p.seat != null || p.state == Ped.DEAD) continue;
                dx = p.x - c.x; dz = p.z - c.z;
                fwd = (dx * fx + dz * fz) >> 14; lat = (dx * rxv + dz * rzv) >> 14;
                if (fwd > 0 && fwd < look && lat < 1600 && lat > -1600) block = true;
            }
        }
        int target = aheadRoad ? Math.min(vmaxOf(c), 10 * 1024) : 4 * 1024;
        int acc = block ? -1 : (c.speed < target ? 1 : 0);
        if (block && c.speed < 600) { acc = 0; c.speed = 0; c.blocked += dt; } else c.blocked = 0;
        if (c.blocked > 6000) { c.blocked = 0; c.yaw = (c.yaw + 512) & 1023; }
        driveCar(c, dt, acc, st, false);
    }

    void policeAI(Car c, int dt) {
        if (c.driver == null || c.driver.seat != c) { c.mode = Car.PARKED; return; }
        int dx = player.x - c.x, dz = player.z - c.z, d = Fx.dist(dx, dz);
        int diff = Fx.angDiff(c.yaw, Fx.yawOf(dx, dz));
        int st = diff > 20 ? 1 : (diff < -20 ? -1 : 0);
        if (d < 10000 && inCar == null) {
            driveCar(c, dt, -1, 0, true);
            if (Fx.abs(c.speed) < 800) {           // stop and let the cops out
                Ped dr = c.driver; dr.seat = null; c.driver = null; c.mode = Car.PARKED;
                dr.x = localX(c, -90, 0); dr.z = localZ(c, -90, 0); dr.state = Ped.WALK; dr.y = groundH(dr.x, dr.z); dr.hp = 160;
                spawnCopAt(localX(c, 90, 0), localZ(c, 90, 0));
            }
            return;
        }
        driveCar(c, dt, Fx.abs(diff) > 300 && c.speed > 6000 ? 0 : 1, st, false);
    }

    void updatePed(Ped p, int dt) {
        if (p.seat != null) {
            Car c = p.seat;
            p.x = localX(c, seatLX(c), seatLZ(c)); p.z = localZ(c, seatLX(c), seatLZ(c)); p.yaw = c.yaw; p.y = c.y;
            return;
        }
        if (p.state == Ped.DEAD && p.deadT >= 0) p.deadT += dt;
        boolean finished = stepClip(p, dt);
        if (p.clip >= 0) {                                        // one-shot clip playing: no walking
            if (p.state == Ped.DEAD) { if (p.timer > 0) p.timer -= dt; }
            if (p.cop && p.clip == cPunch && p.clipT > 200 && !p.hitDone) {    // the cop's punch connects mid-swing
                p.hitDone = true;
                if (inCar == null && pAct == 0 && Fx.dist(player.x - p.x, player.z - p.z) < 1800) hurt(5 + Fx.rnd(4));
            }
            return;
        }
        if (finished || p.state == Ped.HIT || p.state == Ped.JACKED || p.state == Ped.GETUP) {
            if (p.state == Ped.JACKED) { p.state = Ped.GETUP; startClip(p, cGetup, -1, 0); return; }
            if (p.state == Ped.HIT || p.state == Ped.GETUP) { p.state = p.cop ? Ped.WALK : Ped.FLEE; p.timer = 3500; p.yaw = Fx.yawOf(p.x - player.x, p.z - player.z); }
        }
        if (p.state == Ped.DEAD) {
            if (p.timer > 0) p.timer -= dt;
            if (p.cash > 0 && inCar == null && Fx.dist(p.x - player.x, p.z - player.z) < 1300) { money += p.cash; p.cash = 0; }
            return;
        }
        int sp;
        if (p.cop && wanted > 0 && health > 0 && !busted) {
            int dx = player.x - p.x, dz = player.z - p.z;
            int d = Fx.dist(dx, dz);
            int want = Fx.yawOf(dx, dz);
            p.yaw = (p.yaw + Fx.clamp(Fx.angDiff(p.yaw, want), -500 * dt / 1000, 500 * dt / 1000)) & 1023;
            sp = d > 1500 ? (wanted == 1 ? 3600 : 4600) : 0;
            if (p.attackCd > 0) p.attackCd -= dt;
            if (wanted == 1) {
                // one star: they want to arrest you, not beat you up
                if (d < 1500 && inCar == null && pAct == 0) {
                    p.closeT += dt;
                    if (p.closeT > 2200) { busted = true; bustedT = 0; p.closeT = 0; }
                } else p.closeT = 0;
                if (player.clip == cPunch || player.clip == cJab || player.clip == cKick) {
                    if (d < 1700 && p.attackCd <= 0) { startClip(p, cPunch, -1, 0); p.attackCd = 2200; }
                }
            } else if (d <= 1600 && p.attackCd <= 0 && inCar == null) { startClip(p, cPunch, -1, 0); p.attackCd = 1800 + Fx.rnd(700); }
            p.speed = sp;
        } else {
            if (p.state == Ped.FLEE) { p.timer -= dt; sp = 4200; if (p.timer <= 0) p.state = Ped.WALK; }
            else {
                p.turnTimer -= dt;
                sp = p.cop ? 1300 : p.speed;
                if (p.state == Ped.IDLE) { sp = 0; if (p.turnTimer <= 0) { p.state = Ped.WALK; p.turnTimer = 2500; } }
                else if (p.turnTimer <= 0) {
                    p.targetYaw = (p.yaw + Fx.rnd(300) - 150) & 1023;
                    p.turnTimer = 2500 + Fx.rnd(5000);
                    if (Fx.rnd(5) == 0) { p.state = Ped.IDLE; p.turnTimer = 1500 + Fx.rnd(2500); }
                }
                p.yaw = (p.yaw + Fx.clamp(Fx.angDiff(p.yaw, p.targetYaw), -300 * dt / 1000, 300 * dt / 1000)) & 1023;
            }
            for (int i = 0; i < ncars; i++) {
                Car c = cars[i];
                if (Fx.abs(c.speed) < 2000) continue;
                int dx = p.x - c.x, dz = p.z - c.z;
                if (dx > 5000 || dx < -5000 || dz > 5000 || dz < -5000) continue;
                if (p.state != Ped.FLEE && Fx.dist(dx, dz) < 4000) { p.state = Ped.FLEE; p.timer = 2200; p.yaw = Fx.yawOf(dx, dz); }
            }
        }
        // look ahead: keep off water, solid cells and (when walking calmly) the road
        int ax = p.x + ((Fx.sin(p.yaw) * 1500) >> 14), az = p.z + ((Fx.cos(p.yaw) * 1500) >> 14);
        boolean calm = p.state == Ped.WALK && !p.cop;
        if (sp > 0 && (!walkable(ax, az) || (calm && surfAt(ax, az) == S_ROAD && surfAt(p.x, p.z) != S_ROAD))) {
            p.targetYaw = (p.yaw + 384 + Fx.rnd(256)) & 1023; p.yaw = (p.yaw + 60) & 1023;
            if (!p.cop || wanted == 0) sp = 0;
        }
        rx = p.x + ((Fx.sin(p.yaw) * sp) >> 14) * dt / 1000;
        rz = p.z + ((Fx.cos(p.yaw) * sp) >> 14) * dt / 1000;
        resolve(280);
        stepOk(p.x, p.z, p.y, 60);
        if (deep(rx, rz)) { rx = p.x; rz = p.z; }
        p.x = rx; p.z = rz;
        selectLoco(p, dt, sp);
        p.y = groundH(p.x, p.z);
    }

    // ------------------------------------------------------------------ camera
    void updateCamera(int dt, int keys) {
        int turnCam = 0;
        if ((keys & K_CAML) != 0) turnCam -= 1;
        if ((keys & K_CAMR) != 0) turnCam += 1;
        if (turnCam != 0) { camYawOff += turnCam * 520 * dt / 1000; camIdle = 0; }
        else {
            camIdle += dt;
            boolean moving = inCar != null ? Fx.abs(inCar.speed) > 1500 : player.speed > 0;
            if (moving && camIdle > 1500) camYawOff -= camYawOff * 3 * dt / 1000;
        }
        camYawOff = Fx.clamp(camYawOff, -512, 512);
        if ((keys & K_CAMU) != 0) { camPitch += 80 * dt / 1000 + 1; camIdle = 0; }
        if ((keys & K_CAMD) != 0) { camPitch -= 80 * dt / 1000 + 1; camIdle = 0; }
        camPitch = Fx.clamp(camPitch, 4, 150);
        int hy = inCar != null ? (inCar.speed < -1500 ? (inCar.yaw + 512) & 1023 : inCar.yaw) : player.yaw;
        int want = (hy + camYawOff) & 1023;
        int df = Fx.angDiff(camYaw, want);
        int rate = inCar != null ? 4 : 7;
        camYaw = (camYaw + df * rate * dt / 1000 + (df > 0 ? 1 : (df < 0 ? -1 : 0))) & 1023;
        if (Fx.abs(Fx.angDiff(camYaw, want)) < 2) camYaw = want;
        int tx = player.x, tz = player.z, ty = player.y + 100;
        int dist = inCar != null ? (carModels[inCar.type].bike ? 5200 : 7200) : camDistMm;
        int cp = Fx.cos(camPitch), sp = Fx.sin(camPitch);
        int hd = (dist * cp) >> 14, vd = (dist * sp) >> 14;
        int bestD = 120;
        for (int s = 8; s >= 1; s--) {
            int f = s * 1000 / 8;
            int cx = tx - ((Fx.sin(camYaw) * (hd * f / 1000)) >> 14), cz = tz - ((Fx.cos(camYaw) * (hd * f / 1000)) >> 14);
            int cyy = ty + (vd * f / 1000 >> 4);
            if (cyy > groundH(cx, cz) + 20 && !solidCell((cx - sx0) / scell, (cz - sz0) / scell)) { bestD = f; break; }
        }
        int hdf = hd * bestD / 1000, vdf = vd * bestD / 1000;
        camXm = tx - ((Fx.sin(camYaw) * hdf) >> 14);
        camZm = tz - ((Fx.cos(camYaw) * hdf) >> 14);
        camYm = ty + (vdf >> 4);
        int g = groundH(camXm, camZm);
        if (camYm < g + 24) camYm = g + 24;
        if (camYm < 24) camYm = 24;
    }

    // ================================================================== rendering
    void loadTimecyc() throws IOException {
        DataInputStream d = new DataInputStream(Assets.open("/timecyc.bin"));
        tc = new byte[360];
        d.readFully(tc);
        d.close();
    }

    String clockStr() {
        int h = minutes / 60, m = minutes % 60;
        return (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
    }

    final int[] lv = new int[15];
    boolean night;

    void lighting() {
        int h = minutes / 60, n = (h + 1) % 24, f = (minutes % 60) * 256 / 60;
        for (int k = 0; k < 15; k++) {
            int a = tc[h * 15 + k] & 255, b = tc[n * 15 + k] & 255;
            lv[k] = a + (((b - a) * f) >> 8);
        }
        r.skyTop = (lv[0] << 16) | (lv[1] << 8) | lv[2];
        r.skyBot = (lv[3] << 16) | (lv[4] << 8) | lv[5];
        r.fogR = lv[3]; r.fogG = lv[4]; r.fogB = lv[5];
        r.groundCol = Sky.mix(r.skyBot, 0x5C5A50, 150);   // seams between map pieces show this instead of bright sky
        int ambObj = (lv[9] + lv[10] + lv[11]) / 3, dir = (lv[12] + lv[13] + lv[14]) / 3;
        int fb = 200 + brightness * 4;
        int a = (ambObj * 256 / 255 + 28) * fb >> 8, d = (dir * 140 / 255) * fb >> 8;
        r.amb = a; r.dif = d;
        r.emis = 256;
        night = minutes < 6 * 60 || minutes >= 20 * 60;
        Sky.sunDir(this);
        if (Sky.sunY > 1500) { r.lx = Sky.sunX; r.ly = Sky.sunY; r.lz = Sky.sunZ; }
        else { r.lx = 5000; r.ly = 12000; r.lz = -6000; }
        r.farZ = drawDist * 64;
        r.fogFar = r.farZ; r.fogNear = r.farZ * 28 / 100;
    }

    private final int[] rYaw = new int[9], mmA = new int[9], wA = new int[9], wB = new int[9], wC = new int[9], sc = new int[9];

    void render() {
        lighting();
        int sx = 0, sy = 0;
        if (shake > 0) { sx = (Fx.rnd(9) - 4) * 2; sy = (Fx.rnd(7) - 3); }
        r.animU = time / 22; r.animV = time / 31;
        r.setCamera((camXm >> 4) + sx, camYm + sy, camZm >> 4, camYaw, camPitch);
        r.clear();
        Sky.drawSky(this, r);
        int[] mv = r.mv;
        int lim = r.farZ + 3000;
        int fx = Fx.sin(camYaw), fz = Fx.cos(camYaw);
        for (int i = 0; i < nchunk; i++) {
            int dx = chunkX[i] - r.camX, dz = chunkZ[i] - r.camZ;
            if (dx > lim || dx < -lim || dz > lim || dz < -lim) continue;
            if (((dx * fx + dz * fz) >> 14) < -3000) continue;          // behind the camera
            int dy = -r.camY;
            int tx = (mv[0] * dx + mv[1] * dy + mv[2] * dz) >> 14, ty = (mv[3] * dx + mv[4] * dy + mv[5] * dz) >> 14, tz = (mv[6] * dx + mv[7] * dy + mv[8] * dz) >> 14;
            r.drawMesh(chunk[i], mv, tx, ty, tz, r.lx, r.ly, r.lz, 0);
        }
        for (int i = 0; i < nped; i++) drawBlood(peds[i]);
        for (int i = 0; i < ncars; i++) drawCar(cars[i], false);
        for (int i = 0; i < nped; i++) {
            Ped p = peds[i];
            if (p.state == Ped.DEAD && p.deadT < 0) continue;
            if (Fx.abs(p.x - camXm) > 90000 || Fx.abs(p.z - camZm) > 90000) continue;
            drawPed(p);
        }
        drawPed(player);
        for (int i = 0; i < ncars; i++) drawCar(cars[i], true);
        if (night) drawBeam();
        Sky.drawPost(this, r);
    }

    void drawBlood(Ped p) {
        if (p.state != Ped.DEAD || p.deadT <= 400 || p.seat != null) return;
        int dx = (p.bloodX >> 4) - r.camX, dz = (p.bloodZ >> 4) - r.camZ, dy = p.bloodY + 2 - r.camY;
        if (dx > 4000 || dx < -4000 || dz > 4000 || dz < -4000) return;
        int grow = p.deadT - 400; if (grow > 3000) grow = 3000;
        int s = 4000 + grow * 9 / 2;                     // scale 2.14: 0.25 .. 1.07 (pool up to ~2 m)
        Fx.rotY(rYaw, p.yaw);
        for (int k = 0; k < 9; k++) sc[k] = 0;
        sc[0] = s; sc[4] = 16384; sc[8] = s;
        Fx.mul(rYaw, sc, wA);
        Fx.mul(r.mv, wA, mmA);
        int[] mv = r.mv;
        int tx = (mv[0] * dx + mv[1] * dy + mv[2] * dz) >> 14, ty = (mv[3] * dx + mv[4] * dy + mv[5] * dz) >> 14, tz = (mv[6] * dx + mv[7] * dy + mv[8] * dz) >> 14;
        r.drawMesh(blood, mmA, tx, ty, tz, 0, 16384, 0, 0);
    }

    void drawBeam() {
        int dx = (lampX >> 4) - r.camX, dy = lampY - r.camY, dz = (lampZ >> 4) - r.camZ;
        if (dx > 48000 || dx < -48000 || dz > 48000 || dz < -48000) return;
        Fx.rotY(rYaw, (time / 8) & 1023);                 // one turn every ~8 s
        Fx.mul(r.mv, rYaw, mmA);
        int[] mv = r.mv;
        int tx = (mv[0] * dx + mv[1] * dy + mv[2] * dz) >> 14, ty = (mv[3] * dx + mv[4] * dy + mv[5] * dz) >> 14, tz = (mv[6] * dx + mv[7] * dy + mv[8] * dz) >> 14;
        int fn = r.fogNear, ff = r.fogFar;
        r.fogNear = 1 << 28; r.fogFar = (1 << 28) + 1;     // no fog tint on light
        int fz0 = r.farZ;
        r.farZ = 60000;
        r.additive = true;
        r.drawMesh(beam, mmA, tx, ty, tz, 0, 16384, 0, 0);
        r.additive = false;
        r.farZ = fz0;
        r.fogNear = fn; r.fogFar = ff;
    }

    void drawCar(Car c, boolean glassPass) {
        int px = c.x >> 4, pz = c.z >> 4;
        int dx = px - r.camX, dz = pz - r.camZ, dy = c.y - r.camY;
        if (dx > r.farZ || dx < -r.farZ || dz > r.farZ || dz < -r.farZ) return;
        int dist = Math.max(Fx.abs(dx), Fx.abs(dz));
        boolean near = dist < 1500;
        int[] mv = r.mv;
        int cs = Fx.cos(c.yaw), sn = Fx.sin(c.yaw);
        rYaw[0] = cs; rYaw[1] = 0; rYaw[2] = sn; rYaw[3] = 0; rYaw[4] = 16384; rYaw[5] = 0; rYaw[6] = -sn; rYaw[7] = 0; rYaw[8] = cs;
        Fx.mul(mv, rYaw, mmA);
        int tx = (mv[0] * dx + mv[1] * dy + mv[2] * dz) >> 14, ty = (mv[3] * dx + mv[4] * dy + mv[5] * dz) >> 14, tz = (mv[6] * dx + mv[7] * dy + mv[8] * dz) >> 14;
        int lmx = (cs * r.lx - sn * r.lz) >> 14, lmy = r.ly, lmz = (sn * r.lx + cs * r.lz) >> 14;
        CarModel m = carModels[c.type];
        r.tint2 = c.color2;
        if (!near) {
            if (glassPass) { if (m.farGlass != null && dist < 2600) { r.blend = true; r.drawMesh(m.farGlass, mmA, tx, ty, tz, lmx, lmy, lmz, 0); r.blend = false; } }
            else r.drawMesh(m.far, mmA, tx, ty, tz, lmx, lmy, lmz, c.color);
            return;
        }
        for (int p = 0; p < m.np; p++) {
            int id = m.partId[p];
            if ((c.lostBits & (1 << id)) != 0) continue;
            Mesh[] v = (c.damBits & (1 << id)) != 0 ? m.dam[p] : m.ok[p];
            if (glassPass) {
                if (v[1] != null) { r.blend = true; r.drawMesh(v[1], mmA, tx, ty, tz, lmx, lmy, lmz, 0); r.blend = false; }
            } else r.drawMesh(v[0], mmA, tx, ty, tz, lmx, lmy, lmz, c.color);
        }
        if (glassPass || m.bike) return;
        for (int k = 0; k < m.nw; k++) {
            int wxl = m.wx[k], wzl = m.wz[k];
            int wpx = px + ((cs * wxl + sn * wzl) >> 14), wpz = pz + ((-sn * wxl + cs * wzl) >> 14), wpy = c.y + m.wy[k];
            Fx.rotY(wA, wzl > 0 ? c.steer : 0);
            Fx.mul(rYaw, wA, wB);
            Fx.rotX(wA, c.wheelRot);
            Fx.mul(wB, wA, wC);
            if (wxl < 0) { Fx.rotY(wA, 512); Fx.mul(wC, wA, wB); System.arraycopy(wB, 0, wC, 0, 9); }
            int ex = wpx - r.camX, ey = wpy - r.camY, ez = wpz - r.camZ;
            int wtx = (mv[0] * ex + mv[1] * ey + mv[2] * ez) >> 14, wty = (mv[3] * ex + mv[4] * ey + mv[5] * ez) >> 14, wtz = (mv[6] * ex + mv[7] * ey + mv[8] * ez) >> 14;
            Fx.mul(mv, wC, wA);
            int wlx = (wC[0] * r.lx + wC[3] * r.ly + wC[6] * r.lz) >> 14, wly = (wC[1] * r.lx + wC[4] * r.ly + wC[7] * r.lz) >> 14, wlz = (wC[2] * r.lx + wC[5] * r.ly + wC[8] * r.lz) >> 14;
            r.drawMesh(CarModel.wheel, wA, wtx, wty, wtz, wlx, wly, wlz, 0);
        }
    }

    void drawPed(Ped p) {
        int clip, t, fx = p.x >> 4, fz = p.z >> 4, fy = p.y;
        if (p.seat != null) { clip = cSit; t = 0; fy = p.seat.y - 18; if (carModels[p.seat.type].bike) fy = p.seat.y + 10; }
        else if (p.clip >= 0) { clip = p.clip; t = p.clipT; }
        else { clip = p.loco; t = p.locoT; }
        if (clip < 0) clip = cIdle;
        int dx = fx - r.camX, dz = fz - r.camZ;
        if (dx > r.farZ || dx < -r.farZ || dz > r.farZ || dz < -r.farZ) return;
        AnimData.sample(clip, t, pose);
        PedModel m = p == player ? pm0 : (Math.max(Fx.abs(dx), Fx.abs(dz)) < 1150 ? npc[p.kind] : npcLo[p.kind]);
        m.drawPose(r, fx, fy, fz, p.yaw, pose, p.tex);
    }
}
