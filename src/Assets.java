import java.io.*;
import javax.microedition.lcdui.*;

/** Resource loading: textures (renderer ids), UI images. */
final class Assets {
    /** Tommy's textures are fixed ids 0..5, everything else comes from the .lst files */
    static final int T_PED0 = 0, T_COUNT = 6;
    static final String[] NAME = { "ped0", "ped1", "ped2", "ped3", "ped4", "ped5" };
    static final String[] FIXED_EXTRA = { "npc_cop", "npc_construction", "npc_hispanic", "npc_skater", "npc_street", "blood" };

    static InputStream open(String res) { return new Assets().getClass().getResourceAsStream(res); }

    static int[] tw = new int[256], th = new int[256];
    static String[] extra = new String[0];

    static int[] load(String name, int[] dim) throws IOException {
        Image im = Image.createImage("/t_" + name + ".png");
        int w = im.getWidth(), h = im.getHeight();
        int[] p = new int[w * h];
        im.getRGB(p, 0, w, 0, 0, w, h);
        for (int i = 0; i < p.length; i++) {
            int c = p[i];
            p[i] = ((c >>> 24) < 128) ? 0 : (c | 0xFF000000);
            if (p[i] == 0xFF000000) p[i] = 0xFF010101;          // pure black must not read as "transparent"
        }
        dim[0] = w; dim[1] = h;
        return p;
    }

    private static final int[] dim = new int[2];

    static void loadTexture(R3D r, int id) throws IOException {
        int[] p = load(NAME[id], dim);
        tw[id] = dim[0]; th[id] = dim[1];
        r.setTexture(id, p, tw[id], th[id]);
        if (id > T_PED0 && tw[id] == 64) r.uvs[id] = 1;
    }

    private static void readList(String res, String[] tmp, int[] n) {
        try {
            InputStream in = open(res);
            if (in == null) return;
            StringBuffer sb = new StringBuffer();
            int ch;
            while ((ch = in.read()) >= 0) sb.append((char) ch);
            in.close();
            String s = sb.toString();
            int p = 0;
            while (p < s.length()) {
                int q = s.indexOf('\n', p);
                if (q < 0) q = s.length();
                String line = s.substring(p, q).trim();
                if (line.length() > 0 && n[0] < tmp.length) tmp[n[0]++] = line;
                p = q + 1;
            }
        } catch (Exception e) { }
    }

    /** extra textures: npc + blood, car textures (cartex.lst) and map textures (maptex.lst) get ids T_COUNT.. */
    static int initExtra() {
        String[] tmp = new String[240];
        int[] n = { 0 };
        for (int i = 0; i < FIXED_EXTRA.length; i++) tmp[n[0]++] = FIXED_EXTRA[i];
        readList("/cartex.lst", tmp, n);
        readList("/maptex.lst", tmp, n);
        extra = new String[n[0]];
        System.arraycopy(tmp, 0, extra, 0, n[0]);
        return extra.length;
    }
    static int idOf(String name) {
        for (int i = 0; i < extra.length; i++) if (extra[i].equals(name)) return T_COUNT + i;
        for (int i = 0; i < T_COUNT; i++) if (NAME[i].equals(name)) return i;
        return -1;
    }
    static void loadExtra(R3D r, int k) throws IOException {
        int id = T_COUNT + k;
        int[] p = load(extra[k], dim);
        tw[id] = dim[0]; th[id] = dim[1];
        r.setTexture(id, p, tw[id], th[id]);
    }

    static int[] imgPix(String res, int[] dm) throws IOException {
        Image im = Image.createImage(res);
        int w = im.getWidth(), h = im.getHeight();
        int[] p = new int[w * h];
        im.getRGB(p, 0, w, 0, 0, w, h);
        dm[0] = w; dm[1] = h;
        return p;
    }

    /** greyscale sprite -> intensity 0..255 per pixel */
    static int[] intensity(String res, int[] dm) throws IOException {
        int[] p = imgPix(res, dm);
        for (int i = 0; i < p.length; i++) p[i] = (p[i] >> 16) & 255;
        return p;
    }

    static byte[] readAll(String res) throws IOException {
        InputStream in = open(res);
        if (in == null) throw new IOException("missing " + res);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        in.close();
        return bo.toByteArray();
    }
}
