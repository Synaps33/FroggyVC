import javax.microedition.lcdui.*;

/** Canvas + main loop + state machine (intro, loading, menus, game). */
public final class Game extends Canvas implements Runnable {
    static final int S_LOAD = 0, S_MENU = 1, S_OPT = 2, S_PLAY = 3, S_PAUSE = 4, S_CTRL = 5, S_MAP = 6, S_STATS = 7, S_SPLASH = 8, S_VIDEO = 9;
    // logical key actions
    static final int A_UP = 1, A_DOWN = 2, A_LEFT = 4, A_RIGHT = 8, A_FIRE = 16, A_ENTER = 32, A_CAML = 64, A_CAMR = 128,
            A_CAMU = 256, A_CAMD = 512, A_SPRINT = 1024, A_HAND = 2048, A_PAUSE = 4096;

    final GtaMidlet midlet;
    int W, H, u = 1, rs = 1;
    int[] scr, trail;
    R3D r = new R3D();
    World world;
    int state = S_SPLASH, prevState;
    int splashMs;
    Intro intro;
    volatile boolean running, paused, needResize = true;
    Thread thread;

    // input
    volatile int down, edges;
    // loading
    int loadI, loadMs, loadErr;
    String loadMsg;
    int total;
    boolean uiReady;
    // menu
    int sel, menuSel;
    int optSel;
    // options
    int oBright = 10, oDraw = 8, oRadar = 0, oRes = 0;
    boolean oLimiter = true, oTrails = false, oSubs = true, oWide = false, oLegend = false, oHud = true;
    int fps, fpsCount, fpsMs;
    int blink;
    int titleMs;
    boolean started;

    public Game(GtaMidlet m) {
        midlet = m;
        setFullScreenMode(true);
        Fx.init();
        total = Ui.UI_STEPS + Assets.T_COUNT + Assets.initExtra() + 11;
        W = getWidth(); H = getHeight();
    }

    // ------------------------------------------------------------ lifecycle
    void start() {
        if (thread != null) return;
        running = true;
        thread = new Thread(this);
        thread.start();
    }
    void stop() { running = false; }

    protected void showNotify() { paused = false; }
    protected void hideNotify() {
        paused = true;
        if (state == S_PLAY) { prevState = S_PLAY; state = S_PAUSE; sel = 0; }
    }
    protected void sizeChanged(int w, int h) { W = w; H = h; needResize = true; }

    void resize() {
        needResize = false;
        if (W < 64 || H < 48) return;
        u = Math.min(W / 320, H / 240);
        if (u < 1) u = 1;
        rs = oRes == 0 ? 1 : 2;
        int rw = W / rs, rh = H / rs;
        int focal = oWide ? rw * 4 / 8 : rw * 5 / 8;
        r.resize(rw, rh, focal);
        if (rs == 1) scr = r.fb; else scr = new int[W * H];
        trail = null;
    }

    // ------------------------------------------------------------ input
    int mapKey(int k) {
        // number keys first: on Nokia getGameAction(KEY_NUM2/4/6/8) answers UP/LEFT/RIGHT/DOWN, which made 2-4-6-8 walk Tommy
        switch (k) {
            case KEY_NUM2: return A_CAMU;
            case KEY_NUM8: return A_CAMD;
            case KEY_NUM4: return A_CAML;
            case KEY_NUM6: return A_CAMR;
            case KEY_NUM5: case 'f': case 'F': return A_FIRE;
            case KEY_STAR: case 'e': case 'E': return A_ENTER;
            case KEY_NUM1: case 'q': case 'Q': return A_SPRINT;
            case KEY_NUM0: case ' ': case 'b': case 'B': return A_HAND;
            case KEY_POUND: case 'p': case 'P': case 27: return A_PAUSE;
            case KEY_NUM3: return A_SPRINT;
            case KEY_NUM7: return A_HAND;
            case KEY_NUM9: return A_ENTER;
            case 'w': case 'W': return A_UP;
            case 's': case 'S': return A_DOWN;
            case 'a': case 'A': return A_LEFT;
            case 'd': case 'D': return A_RIGHT;
            case 'i': case 'I': return A_CAMU;
            case 'k': case 'K': return A_CAMD;
            case 'j': case 'J': return A_CAML;
            case 'l': case 'L': return A_CAMR;
            case -6: case -7: case -21: case -22: return A_PAUSE;    // soft keys
            default: break;
        }
        int ga = 0;
        try { ga = getGameAction(k); } catch (Exception e) { }
        if (ga == UP) return A_UP;
        if (ga == DOWN) return A_DOWN;
        if (ga == LEFT) return A_LEFT;
        if (ga == RIGHT) return A_RIGHT;
        if (ga == FIRE) return A_FIRE;
        return 0;
    }
    int anyKey;
    protected void keyPressed(int k) { anyKey++; press(mapKey(k)); }
    protected void keyRepeated(int k) { int a = mapKey(k); edges |= a & (A_UP | A_DOWN | A_LEFT | A_RIGHT); }
    protected void keyReleased(int k) { down &= ~mapKey(k); }
    void press(int a) { down |= a; edges |= a; }

    // ------------------------------------------------------------ main loop
    public void run() {
        int last = (int) System.currentTimeMillis();
        while (running) {
            int now = (int) System.currentTimeMillis();
            int dt = now - last;
            last = now;
            if (dt < 0) dt = 1;
            if (paused) { try { Thread.sleep(100); } catch (InterruptedException e) { } continue; }
            frame(dt);
            repaint();
            serviceRepaints();
            int spent = (int) System.currentTimeMillis() - now;
            int wait = (state == S_PLAY && !oLimiter) ? 1 : 33 - spent;
            if (wait < 2) wait = 2;
            try { Thread.sleep(wait); } catch (InterruptedException e) { }
        }
        midlet.notifyDestroyed();
    }

    protected void paint(Graphics g) {
        if (state == S_VIDEO) { g.setColor(0); g.fillRect(0, 0, getWidth(), getHeight()); return; }
        if (scr != null && W == (rs == 1 ? r.W : r.W * rs)) g.drawRGB(scr, 0, W, 0, 0, W, H, false);
    }

    /** one tick of logic + draw into scr (also used by the desktop test harness) */
    void frame(int dt) {
        if (needResize) resize();
        if (r.fb == null) return;
        int e = edges; edges = 0;
        blink += dt;
        fpsMs += dt; fpsCount++;
        if (fpsMs >= 1000) { fps = fpsCount; fpsCount = 0; fpsMs = 0; }
        switch (state) {
            case S_SPLASH: doSplash(dt); break;
            case S_VIDEO: doVideo(); break;
            case S_LOAD: doLoad(dt, e); break;
            case S_MENU: doMenu(e); break;
            case S_OPT: doOptions(e); break;
            case S_CTRL: case S_MAP: case S_STATS: if ((e & (A_FIRE | A_PAUSE | A_ENTER)) != 0) state = prevState == S_PLAY || prevState == S_PAUSE ? S_PAUSE : S_MENU; draw2(); break;
            case S_PAUSE: doPause(e); break;
            case S_PLAY: doPlay(dt, e); break;
            default: break;
        }
        if (rs > 1 && state == S_PLAY) upscale();
        if (state == S_PLAY) overlay();
        if (oTrails && state == S_PLAY) doTrails();
    }

    // ------------------------------------------------------------ splash / intro movie
    static final String CREDIT = "A Port made by AidenCantCode";
    int splashKeys = -1;

    void doSplash(int dt) {
        if (splashMs == 0) { try { Ui.loadFont(); } catch (Exception ex) { } splashKeys = anyKey; }
        splashMs += dt;
        for (int i = 0; i < scr.length; i++) scr[i] = 0;
        int a = splashMs < 600 ? splashMs * 255 / 600 : (splashMs > 2600 ? (3200 - splashMs) * 255 / 600 : 255);
        if (a < 0) a = 0;
        if (Ui.faMain != null) {
            int c = (a << 16) | (a << 8) | a;
            Ui.textC(scr, W, H, W / 2, H / 2 - 8 * u, CREDIT, c, u);
        }
        if (splashMs > 3200 || (anyKey != splashKeys && splashMs > 300)) { state = S_LOAD; loadMs = 0; edges = 0; }
    }

    int videoKeys;
    void doVideo() {
        for (int i = 0; i < scr.length; i++) scr[i] = 0;
        if (intro == null) {
            videoKeys = anyKey;
            intro = new Intro();
            intro.start(this, W, H);
        }
        if (intro.done || anyKey != videoKeys) {
            intro.stop();
            intro = null;
            state = S_MENU; sel = 0; edges = 0; down = 0;
            System.gc();
        }
    }

    // ------------------------------------------------------------ loading
    void doLoad(int dt, int e) {
        loadMs += dt;
        if (loadI < total && loadErr == 0) {
            int t0 = (int) System.currentTimeMillis();
            int budget = loadMs < 30 ? 1 : 3;
            for (int k = 0; k < budget && loadI < total; k++) {
                try { loadOne(loadI); } catch (Throwable ex) { loadErr = 1; loadMsg = String.valueOf(ex); break; }
                loadI++;
                if ((int) System.currentTimeMillis() - t0 > 60) break;
            }
        }
        for (int i = 0; i < scr.length; i++) scr[i] = 0;
        boolean intro = loadMs < 2600;
        if (e != 0 && Ui.scr[1] != null && loadMs > 400) intro = false;
        if (intro && Ui.scr[0] != null) {
            int f = 256;
            if (loadMs < 500) f = loadMs * 256 / 500; else if (loadMs > 2100) f = (2600 - loadMs) * 256 / 500;
            Ui.stretch(Ui.scr[0], 320, 240, scr, W, H, f < 0 ? 0 : f);
        } else {
            int idx = 1 + ((loadMs / 2300) % 4);
            if (Ui.scr[idx] == null) idx = 1;
            if (Ui.scr[idx] != null) Ui.stretch(Ui.scr[idx], 320, 240, scr, W, H, 256);
            // loading bar
            int bw = W * 6 / 10, bx = (W - bw) / 2, by = H - 14 * u;
            Ui.fill(scr, W, H, bx - u, by - u, bw + 2 * u, 5 * u, 0x101018);
            Ui.fill(scr, W, H, bx, by, bw * loadI / total, 3 * u, 0xFF6EC7);
            if (loadErr != 0) Ui.text(scr, W, H, 6 * u, 6 * u, "error " + loadMsg, 0xFF4040, u);
            if (loadI >= total && loadMs > 3200) { state = S_VIDEO; sel = 0; edges = 0; for (int q = 0; q < Ui.scr.length; q++) Ui.scr[q] = null; System.gc(); }
        }
        present();
    }

    void present() { if (rs > 1) { /* UI states render at full size directly */ } }

    void loadOne(int i) throws java.io.IOException {
        if (i < Ui.UI_STEPS) { Ui.loadStep(i); if (i == Ui.UI_STEPS - 1) uiReady = true; return; }
        i -= Ui.UI_STEPS;
        if (i < Assets.T_COUNT) { Assets.loadTexture(r, i); return; }
        i -= Assets.T_COUNT;
        if (i < Assets.extra.length) { Assets.loadExtra(r, i); return; }
        i -= Assets.extra.length;
        if (i == 0) { world = new World(r); world.pm0 = PedModel.load("/ped0.bin");
            for (int k = 0; k < 5; k++) { world.npc[k] = PedModel.load("/npc_" + World.NPC_NAMES[k] + ".bin"); world.npcLo[k] = PedModel.load("/npc_" + World.NPC_NAMES[k] + "_lo.bin"); } }
        else if (i == 1) { AnimData.load(); world.loadTimecyc(); Sky.load(); }
        else if (i >= 2 && i < 2 + World.NCAR_TYPES) world.carModels[i - 2] = CarModel.load(World.CAR_NAMES[i - 2]);
        else if (i == 7) world.loadMap();
        else if (i == 8) { world.reset(); applyOptions(); }
        else System.gc();
    }

    // ------------------------------------------------------------ menus
    static final String[] MAIN = { "start game", "options", "controls", "quit game" };
    static final String[] PAUSE = { "resume", "start new game", "map", "stats", "brief", "options", "quit game" };

    void menuNav(int e, int n) {
        if ((e & A_UP) != 0) sel = (sel + n - 1) % n;
        if ((e & A_DOWN) != 0) sel = (sel + 1) % n;
    }

    void drawMenu(String title, String[] items) {
        scrClearFull();
        Ui.menuBg(scr, W, H, u);
        Ui.menuTitle(scr, W, H, u, title);
        int n = items.length;
        int y0 = H * 36 / 100 - (n > 5 ? 0 : 0);
        int step = 15 * u;
        for (int i = 0; i < n; i++) Ui.menuItem(scr, W, H, u, W / 2, y0 + i * step, items[i], i == sel);
    }

    void scrClearFull() { if (rs > 1 || scr == null || scr.length != W * H) { } }

    void doMenu(int e) {
        menuNav(e, MAIN.length);
        if ((e & A_FIRE) != 0) {
            if (sel == 0) { world.reset(); applyOptions(); state = S_PLAY; down = 0; titleMs = 0; started = true; }
            else if (sel == 1) { prevState = S_MENU; state = S_OPT; optSel = 0; }
            else if (sel == 2) { prevState = S_MENU; state = S_CTRL; }
            else { running = false; }
        }
        drawMenu("grand theft auto", MAIN);
        Ui.textC(scr, W, H, W / 2, H - 14 * u, "vice city  j2me demake", 0xB0B0C0, 1);
    }

    void doPause(int e) {
        menuNav(e, PAUSE.length);
        if ((e & A_PAUSE) != 0) { state = S_PLAY; down = 0; return; }
        if ((e & A_FIRE) != 0) {
            switch (sel) {
                case 0: state = S_PLAY; down = 0; break;
                case 1: world.reset(); applyOptions(); state = S_PLAY; down = 0; break;
                case 2: prevState = S_PAUSE; state = S_MAP; break;
                case 3: prevState = S_PAUSE; state = S_STATS; break;
                case 4: prevState = S_PAUSE; state = S_CTRL; break;
                case 5: prevState = S_PAUSE; state = S_OPT; optSel = 0; break;
                default: state = S_MENU; sel = 0; break;
            }
        }
        if (state == S_PAUSE) drawMenu("pause menu", PAUSE);
    }

    static final String[] OPT_NAMES = { "brightness", "draw distance", "frame limiter", "trails", "subtitles", "wide screen",
            "map legend", "radar mode", "hud mode", "screen resolution", "restore defaults", "back" };

    void doOptions(int e) {
        int n = OPT_NAMES.length;
        if ((e & A_UP) != 0) optSel = (optSel + n - 1) % n;
        if ((e & A_DOWN) != 0) optSel = (optSel + 1) % n;
        int dlt = ((e & A_RIGHT) != 0 ? 1 : 0) - ((e & A_LEFT) != 0 ? 1 : 0);
        if ((e & A_FIRE) != 0 && optSel < 10) dlt = 1;
        if (dlt != 0) {
            switch (optSel) {
                case 0: oBright = Fx.clamp(oBright + dlt, 0, 15); break;
                case 1: oDraw = Fx.clamp(oDraw + dlt, 0, 15); break;
                case 2: oLimiter = !oLimiter; break;
                case 3: oTrails = !oTrails; break;
                case 4: oSubs = !oSubs; break;
                case 5: oWide = !oWide; needResize = true; break;
                case 6: oLegend = !oLegend; break;
                case 7: oRadar = (oRadar + 3 + dlt) % 3; break;
                case 8: oHud = !oHud; break;
                case 9: oRes = 1 - oRes; needResize = true; break;
                default: break;
            }
        }
        if ((e & A_FIRE) != 0 && optSel == 10) { oBright = 10; oDraw = 8; oLimiter = true; oTrails = false; oSubs = true; oWide = false; oLegend = false; oRadar = 0; oHud = true; oRes = 0; needResize = true; }
        if ((e & A_FIRE) != 0 && optSel == 11 || (e & A_PAUSE) != 0) { state = prevState == S_PAUSE ? S_PAUSE : S_MENU; applyOptions(); }
        applyOptions();
        scrClearFull();
        Ui.menuBg(scr, W, H, u);
        Ui.menuTitle(scr, W, H, u, "display");
        int x0 = W * 15 / 100, xr = W * 87 / 100, y0 = H * 20 / 100, step = 11 * u;
        for (int i = 0; i < n; i++) {
            int y = y0 + i * step;
            if (i == optSel) Ui.fill(scr, W, H, x0 - 4 * u, y - u, xr - x0 + 8 * u, 11 * u, Ui.HILITE);
            if (i >= 10) { Ui.textC(scr, W, H, W / 2, y, OPT_NAMES[i], Ui.PINK, u); continue; }
            Ui.text(scr, W, H, x0, y, OPT_NAMES[i], Ui.PINK, u);
            switch (i) {
                case 0: Ui.bars(scr, W, H, u, xr, y, oBright, 15); break;
                case 1: Ui.bars(scr, W, H, u, xr, y, oDraw, 15); break;
                case 2: Ui.textR(scr, W, H, xr, y, oLimiter ? "on" : "off", Ui.PINK, u); break;
                case 3: Ui.textR(scr, W, H, xr, y, oTrails ? "on" : "off", Ui.PINK, u); break;
                case 4: Ui.textR(scr, W, H, xr, y, oSubs ? "on" : "off", Ui.PINK, u); break;
                case 5: Ui.textR(scr, W, H, xr, y, oWide ? "on" : "off", Ui.PINK, u); break;
                case 6: Ui.textR(scr, W, H, xr, y, oLegend ? "on" : "off", Ui.PINK, u); break;
                case 7: Ui.textR(scr, W, H, xr, y, oRadar == 0 ? "map & blips" : (oRadar == 1 ? "blips only" : "off"), Ui.PINK, u); break;
                case 8: Ui.textR(scr, W, H, xr, y, oHud ? "on" : "off", Ui.PINK, u); break;
                default: Ui.textR(scr, W, H, xr, y, W / (oRes == 0 ? 1 : 2) + "x" + H / (oRes == 0 ? 1 : 2) + "x32", Ui.PINK, u); break;
            }
        }
        Ui.textR(scr, W, H, W - 6 * u, H - 11 * u, "left, right to adjust", 0xE0E0E0, 1);
    }

    void applyOptions() {
        if (world == null) return;
        world.brightness = oBright * 14 / 15;
        world.drawDist = 50 + oDraw * 7;
    }

    void draw2() {
        scrClearFull();
        Ui.menuBg(scr, W, H, u);
        int x = W * 14 / 100, y = H * 24 / 100, st = 12 * u;
        if (state == S_CTRL) {
            Ui.menuTitle(scr, W, H, u, "controls");
            String[] t = { "d-pad: walk / turn / steer", "fire / 5: punch (3 hit combo) / gas", "*: enter / exit / steal car", "2 8: look up / down   4 6: look around",
                    "0: toggle run / walk  (in car: handbrake)", "1 (hold): sprint", "down: brake / reverse", "soft key or #: pause menu" };
            for (int i = 0; i < t.length; i++) Ui.text(scr, W, H, x, y + i * st, t[i], Ui.PINK, u);
        } else if (state == S_STATS) {
            Ui.menuTitle(scr, W, H, u, "stats");
            World w = world;
            String[] t = { "money: $" + w.money, "time: " + w.clockStr() + "  day " + (w.dayCount + 1), "people hurt: " + w.pedsHit,
                    "cars stolen: " + w.carsStolen, "wanted level: " + w.wanted, "health: " + w.health };
            for (int i = 0; i < t.length; i++) Ui.text(scr, W, H, x, y + i * st, t[i], Ui.PINK, u);
        } else {
            Ui.menuTitle(scr, W, H, u, "map");
            World w = world;
            int mh = H * 70 / 100, mw = mh * w.rw / w.rh;
            if (mw > W - 20 * u) { mw = W - 20 * u; mh = mw * w.rh / w.rw; }
            int ox = W / 2 - mw / 2, oy = H * 22 / 100;
            for (int j = 0; j < mh; j++) {
                int mj = (mh - 1 - j) * w.rh / mh;
                int yy = oy + j;
                if (yy >= H) break;
                for (int i = 0; i < mw; i++) scr[yy * W + ox + i] = Ui.MAPPAL[w.rad[mj * w.rw + i * w.rw / mw] & 7];
            }
            int pxm = (w.player.x - w.rx0) / w.rcell * mw / w.rw;
            int pym = mh - 1 - (w.player.z - w.rz0) / w.rcell * mh / w.rh;
            if ((blink / 300) % 2 == 0) Ui.fill(scr, W, H, ox + pxm - 2 * u, oy + pym - 2 * u, 4 * u, 4 * u, 0xFF40D0);
            if (oLegend) Ui.text(scr, W, H, 6 * u, H - 12 * u, "pink dot: you   black: roads", 0xE0E0E0, 1);
        }
    }

    // ------------------------------------------------------------ gameplay
    void doPlay(int dt, int e) {
        if ((e & A_PAUSE) != 0) { state = S_PAUSE; sel = 0; prevState = S_PLAY; return; }
        int k = 0, d = down;
        if ((d & A_UP) != 0) k |= World.K_UP;
        if ((d & A_DOWN) != 0) k |= World.K_DOWN;
        if ((d & A_LEFT) != 0) k |= World.K_LEFT;
        if ((d & A_RIGHT) != 0) k |= World.K_RIGHT;
        if ((d & A_FIRE) != 0) k |= World.K_FIRE;
        if ((d & A_CAML) != 0) k |= World.K_CAML;
        if ((d & A_CAMR) != 0) k |= World.K_CAMR;
        if ((d & A_CAMU) != 0) k |= World.K_CAMU;
        if ((d & A_CAMD) != 0) k |= World.K_CAMD;
        if ((d & A_SPRINT) != 0) k |= World.K_SPRINT;
        if ((d & A_HAND) != 0) k |= World.K_HAND;
        titleMs += dt;
        world.update(dt, k, (e & A_FIRE) != 0, (e & A_ENTER) != 0, (e & A_HAND) != 0 && world.inCar == null);
        // contextual help like the original
        World w = world;
        if (w.inCar == null && w.helpT <= 0 && w.helpShown == 0) {
            for (int i = 0; i < w.ncars; i++) {
                if (Fx.dist(w.cars[i].x - w.player.x, w.cars[i].z - w.player.z) < 3800) { w.say("Press * to enter or exit a vehicle."); w.helpShown = 1; break; }
            }
        }
        w.render();
    }

    void upscale() {
        int rw = r.W, rh = r.H, s = rs;
        int[] src = r.fb;
        for (int y = 0; y < H; y++) {
            int sy = y / s; if (sy >= rh) sy = rh - 1;
            int so = sy * rw, d0 = y * W;
            for (int x = 0; x < W; x++) { int sx = x / s; if (sx >= rw) sx = rw - 1; scr[d0 + x] = src[so + sx]; }
        }
    }

    void doTrails() {
        if (trail == null || trail.length != scr.length) { trail = new int[scr.length]; System.arraycopy(scr, 0, trail, 0, scr.length); return; }
        for (int i = 0; i < scr.length; i++) {
            int a = scr[i], b = trail[i];
            int m = (((a >> 1) & 0x7F7F7F) + ((b >> 1) & 0x7F7F7F));
            scr[i] = m; trail[i] = m;
        }
    }

    void overlay() {
        World w = world;
        if (oHud) Ui.hudDraw(scr, W, H, w, u, true, oRadar);
        if (w.helpT > 0 && oSubs && w.help != null) Ui.helpBox(scr, W, H, u, w.help);
        if (titleMs < 4200) {
            int a = titleMs < 600 ? titleMs * 255 / 600 : (titleMs > 3400 ? (4200 - titleMs) * 255 / 800 : 255);
            String z = "Ocean Beach";
            int tw = Ui.textW(z, u * 2);
            for (int i = 0; i < z.length(); i++) { }
            if (a > 120) Ui.text(scr, W, H, W - 8 * u - tw, H - 28 * u, z, 0x28D890, u * 2);
        }
        if (w.busted) {
            Ui.fillA(scr, W, H, 0, 0, W, H, 0x000000, Math.min(150, w.bustedT / 14));
            String s = "busted";
            int sc = u * 3;
            Ui.text(scr, W, H, W / 2 - Ui.textW(s, sc) / 2, H / 2 - 8 * sc, s, Ui.PINK, sc);
        }
        if (w.health <= 0) {
            Ui.fillA(scr, W, H, 0, 0, W, H, 0x000000, Math.min(150, w.wastedT / 14));
            String s = "wasted";
            int sc = u * 3;
            Ui.text(scr, W, H, W / 2 - Ui.textW(s, sc) / 2, H / 2 - 8 * sc, s, Ui.PINK, sc);
        }
        if (oLegend) Ui.text(scr, W, H, 4 * u, H - 11 * u, "fps " + fps, 0xFFFF80, 1);
    }
}
