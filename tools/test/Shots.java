import java.awt.image.BufferedImage;
import java.io.*;

/** Headless session: boots the canvas, plays a scripted run and writes screenshots to build/shots. */
public class Shots {
    static Game g; static World w;
    static void shot(String n) throws Exception {
        new File("build/shots").mkdirs();
        BufferedImage bi = new BufferedImage(g.W, g.H, BufferedImage.TYPE_INT_RGB);
        bi.setRGB(0, 0, g.W, g.H, g.scr, 0, g.W);
        javax.imageio.ImageIO.write(bi, "png", new File("build/shots/" + n + ".png"));
    }
    static void frames(int n) { for (int i = 0; i < n; i++) { g.frame(33); try { Thread.sleep(2); } catch (Exception e) { } } }
    static void key(int code, int n) { g.keyPressed(code); frames(n); g.keyReleased(code); }
    public static void main(String[] a) throws Exception {
        g = new Game(null);
        frames(5); shot("00_splash");
        int guard = 0; while (g.state != Game.S_MENU && guard++ < 9000) g.frame(33);
        System.out.println("menu after " + guard + " frames");
        frames(10); shot("01_menu");
        g.press(Game.A_FIRE); g.frame(33); g.down = 0;
        w = g.world;
        frames(40); shot("02_spawn");
        System.out.println("spawn ground " + w.groundH(w.player.x, w.player.z, w.player.y) + " y " + w.player.y + " surf " + w.surfAt(w.player.x, w.player.z) + " peds " + w.nped + " cars " + w.ncars);
        for (int k = 1; k < 4; k++) { w.camYawOff = k * 256; frames(25); shot("03_look" + k); }
        w.camYawOff = 0;
        // walk to the pickups
        for (int i = 0; i < w.npick; i++) { w.player.x = w.pkX[i]; w.player.z = w.pkZ[i]; frames(3); }
        System.out.println("weapons: bat " + w.hasBat + " pistol " + w.hasPistol + " ammo " + w.ammo + " current " + w.weapon);
        w.weapon = 2; g.keyPressed(53); frames(8); shot("04_aim"); frames(20); g.keyReleased(53);
        System.out.println("after shooting: ammo " + w.ammo + " wanted " + w.wanted);
        w.weapon = 1; frames(5); shot("04_bat");
        Ped v = null;
        for (int i = 0; i < w.nped; i++) if (!w.peds[i].cop && w.peds[i].seat == null && w.peds[i].state != Ped.DEAD) { v = w.peds[i]; break; }
        if (v != null) { w.damagePed(v, 500, true, w.player.x, w.player.z); w.player.x = v.x + 2600; w.player.z = v.z; w.player.y = w.groundH(w.player.x, w.player.z, v.y); w.player.yaw = Fx.yawOf(v.x - w.player.x, v.z - w.player.z); w.camYaw = w.player.yaw; frames(70); shot("04_dead"); }
        int[] times = { 9 * 60, 18 * 60 + 40, 23 * 60 };
        for (int i = 0; i < times.length; i++) { w.minutes = times[i]; frames(3); shot("05_time_" + times[i] / 60); }
        w.minutes = 13 * 60;
        // drive: take the nearest car
        Car c = null; int bd = 1 << 30;
        for (int i = 0; i < w.ncars; i++) { if (w.cm(w.cars[i]).bike) continue; int d = Fx.dist(w.cars[i].x - w.player.x, w.cars[i].z - w.player.z); if (d < bd) { bd = d; c = w.cars[i]; } }
        if (c != null) { c.mode = Car.PARKED; c.vx = c.vz = 0; c.speed = 0; }
        if (c != null) {
            w.player.x = c.x - 2500; w.player.z = c.z; w.player.y = c.y; c.vx = c.vz = 0; c.speed = 0;
            key(42, 1); frames(150);
            System.out.println("in car: " + (w.inCar != null) + " model " + w.cm(c).name);
            g.keyPressed(-1); frames(120); shot("06_drive"); 
            System.out.println("speed " + c.speed / 1024 + " m/s, vx " + c.vx + " vz " + c.vz + " hp " + c.hp);
            g.keyPressed(-3); frames(40); shot("07_turn"); g.keyReleased(-3);
            g.keyPressed(48); g.keyPressed(-4); frames(30); shot("08_handbrake"); g.keyReleased(-4); g.keyReleased(48); g.keyReleased(-1);
            System.out.println("slide " + c.slide + " yawVel " + c.yawVel);
        }
        long t0 = System.currentTimeMillis(); frames(60); System.out.println("desktop ms/frame " + (System.currentTimeMillis() - t0) / 60.0 + " tris " + g.r.tris);
        int loaded = 0; for (int k = 0; k < w.tiles.length; k++) if (w.tiles[k] != null) loaded++;
        System.out.println("tiles loaded " + loaded);
        w.alive = false;
        System.exit(0);
    }
}
