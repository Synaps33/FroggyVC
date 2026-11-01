import java.awt.image.BufferedImage;
import java.io.*;

/** Headless run: boots the MIDlet canvas, plays a scripted session and writes screenshots to build/shots. */
public class Shots {
    static Game g; static World w;
    static void shot(String n) throws Exception {
        new File("build/shots").mkdirs();
        BufferedImage bi = new BufferedImage(g.W, g.H, BufferedImage.TYPE_INT_RGB);
        bi.setRGB(0, 0, g.W, g.H, g.scr, 0, g.W);
        javax.imageio.ImageIO.write(bi, "png", new File("build/shots/" + n + ".png"));
    }
    static void frames(int n) { for (int i = 0; i < n; i++) g.frame(33); }
    static void key(int code, int n) { g.keyPressed(code); frames(n); g.keyReleased(code); }
    public static void main(String[] a) throws Exception {
        g = new Game(null);
        frames(5); shot("00_splash");
        int guard = 0; while (g.state != Game.S_MENU && guard++ < 6000) g.frame(33);
        System.out.println("menu after " + guard + " frames, state " + g.state);
        frames(2); shot("01_menu");
        g.press(Game.A_FIRE); g.frame(33); g.down = 0;
        w = g.world;
        frames(10); shot("02_spawn");
        // controls: 2/4/6/8 must move the camera, not Tommy
        int px = w.player.x, pz = w.player.z, cy = w.camYawOff, cp = w.camPitch;
        key(Canvas_KEY(52), 20); key(Canvas_KEY(50), 10);
        System.out.println("num4/num2: player moved " + Fx.dist(w.player.x - px, w.player.z - pz) + "mm, camYawOff " + cy + "->" + w.camYawOff + " pitch " + cp + "->" + w.camPitch);
        // walk / run toggle / backwards
        px = w.player.x; pz = w.player.z;
        g.keyPressed(-1); frames(30); g.keyReleased(-1);
        System.out.println("walk 1s: " + Fx.dist(w.player.x - px, w.player.z - pz) + " mm");
        key(48, 1);
        px = w.player.x; pz = w.player.z;
        g.keyPressed(-1); frames(30); g.keyReleased(-1);
        System.out.println("run 1s: " + Fx.dist(w.player.x - px, w.player.z - pz) + " mm  runMode=" + w.runMode);
        px = w.player.x; pz = w.player.z;
        g.keyPressed(-2); frames(30); shot("03_back"); g.keyReleased(-2);
        System.out.println("back 1s: " + Fx.dist(w.player.x - px, w.player.z - pz) + " mm, loco " + AnimData.name[w.player.loco]);
        key(48, 1);
        // the combo: capture the kick
        w.camYawOff = 300; frames(20);
        for (int k = 0; k < 3; k++) { g.press(Game.A_FIRE); g.frame(33); g.down = 0; System.out.println("attack " + k + " clip " + (w.player.clip >= 0 ? AnimData.name[w.player.clip] : "none")); for (int f = 0; f < 40 && w.player.clip >= 0; f++) { g.frame(33); if (f % 5 == 0) shot("04_atk" + k + "_" + f); } }
        // time of day
        int[] times = { 9 * 60, 12 * 60 + 30, 18 * 60 + 40, 19 * 60 + 30, 23 * 60 };
        for (int i = 0; i < times.length; i++) {
            w.minutes = times[i]; w.camYawOff = 0;
            // look towards the sun / lighthouse
            Sky.sunDir(w);
            w.player.yaw = i >= 3 ? Fx.yawOf(w.lampX - w.player.x, w.lampZ - w.player.z) : Fx.yawOf(Sky.sunX, Sky.sunZ);
            w.camYaw = w.player.yaw; w.camPitch = i == 2 ? 10 : 30;
            frames(3); shot("05_time_" + times[i] / 60);
        }
        // damage: punch a ped, check blood + wanted
        w.minutes = 13 * 60;
        Ped v = null;
        for (int i = 0; i < w.nped; i++) if (!w.peds[i].cop && w.peds[i].seat == null && w.peds[i].state != Ped.DEAD) { v = w.peds[i]; break; }
        int wantedBefore = w.wanted;
        for (int k = 0; k < 5 && v != null; k++) w.damagePed(v, 30, true, w.player.x, w.player.z);
        if (v != null) { w.player.x = v.x + 2500; w.player.z = v.z; w.player.y = w.groundH(w.player.x, w.player.z); w.player.yaw = Fx.yawOf(v.x - w.player.x, v.z - w.player.z); w.camYaw = w.player.yaw; w.camYawOff = 0; }
        frames(90); shot("06_blood");
        System.out.println("kill without cops: wanted " + wantedBefore + " -> " + w.wanted + " quietKills " + w.quietKills + "/" + w.quietLimit);
        // cop hits player
        w.raiseWanted(2); int h0 = w.health;
        Ped cop = w.spawnCopAt(w.player.x + 1200, w.player.z);
        frames(150);
        System.out.println("2 stars, cop next to Tommy for 5 s: health " + h0 + " -> " + w.health);
        w.clearCops(); w.wanted = 0; w.health = 100;
        // drive the admiral into something
        Car c = null; for (int i = 0; i < w.ncars; i++) if (w.cars[i].type == Car.ADMIRAL) c = w.cars[i];
        w.player.x = c.x - 2000; w.player.z = c.z; key(42, 1); frames(120);
        System.out.println("in car: " + (w.inCar != null));
        g.keyPressed(-1); frames(150); shot("07_drive"); g.keyReleased(-1);
        System.out.println("speed " + c.speed / 1024 + " m/s hp " + c.hp + " dam " + Integer.toBinaryString(c.damBits) + " lost " + Integer.toBinaryString(c.lostBits));
        for (int i = 0; i < 6; i++) w.damageCar(c, i % 4, 260);
        w.camYawOff = 400; frames(15); shot("08_damaged");
        System.out.println("tris last frame " + g.r.tris + "  traffic/cars " + w.ncars + " peds " + w.nped);
        long t0 = System.currentTimeMillis(); frames(100); System.out.println("desktop ms/frame " + (System.currentTimeMillis() - t0) / 100.0);
    }
    static int Canvas_KEY(int k) { return k; }
}
