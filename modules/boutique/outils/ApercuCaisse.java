import fr.heraliz.crates.client.render.mesh.Mesh;
import fr.heraliz.crates.client.render.scene.CrateState;
import fr.heraliz.crates.client.render.scene.Design;
import fr.heraliz.crates.client.render.scene.Designs;
import fr.heraliz.crates.client.render.scene.DrawList;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Apercu d'une caisse hors du jeu : son vrai modele (Design), ses vraies textures (atlas et calque lumineux) et ses effets
 * (lueurs, sprites, faisceaux), dessines par un petit moteur logiciel. Lance par outils/apercu_caisse.py. Ce n'est pas
 * le rendu de Minecraft (pas de lumiere du monde, pas de brouillard) : de quoi juger formes, couleurs et choregraphie.
 * <p>Arguments : sortie.png id temps(ticks, -1 = repos) [yaw pitch distance largeur hauteur]
 */
public final class ApercuCaisse {
    static int W, H;
    static float[] col, zb;
    static BufferedImage atlas, glow;
    static final Map<String, BufferedImage> SPRITES = new HashMap<>();
    static Path res;
    static Matrix4f view = new Matrix4f(), proj = new Matrix4f();
    static Vector3f camPos = new Vector3f();
    static final Vector3f LIGHT = new Vector3f(0.35f, 0.85f, 0.45f).normalize();

    public static void main(String[] a) throws IOException {
        res = Path.of(a[0]);
        String out = a[1], id = a[2];
        float t = Float.parseFloat(a[3]);
        float yaw = a.length > 4 ? Float.parseFloat(a[4]) : 25, pitch = a.length > 5 ? Float.parseFloat(a[5]) : 22;
        float dist = a.length > 6 ? Float.parseFloat(a[6]) : 80;
        int ss = 2;
        int ow = a.length > 7 ? Integer.parseInt(a[7]) : 900, oh = a.length > 8 ? Integer.parseInt(a[8]) : 700;
        W = ow * ss;
        H = oh * ss;
        float lookY = a.length > 9 ? Float.parseFloat(a[9]) : 16;
        atlas = ImageIO.read(res.resolve("textures/crate/" + id + ".png").toFile());
        glow = ImageIO.read(res.resolve("textures/crate/" + id + "_glow.png").toFile());
        Design d = Designs.create(id);
        CrateState s = new CrateState();
        s.time = t < 0 ? 40 : t + 40;
        s.clock = s.time;
        s.active = t >= 0;
        s.t = Math.max(0, t);
        s.ringSize = 8;
        s.ringRgb = new int[]{0xFF4FD8, 0x3FA9FF, 0xFFC43A, 0x9BE35A, 0xFF4FD8, 0x3FA9FF, 0xFFC43A, 0xFFFFFF};
        s.ringRank = new int[]{1, 2, 3, 1, 2, 4, 1, 3};
        DrawList list = new DrawList();
        d.render(s, list);
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        camPos.set(8 + (float) (Math.sin(yr) * Math.cos(pr) * dist), lookY + (float) (Math.sin(pr) * dist), 8 + (float) (Math.cos(yr) * Math.cos(pr) * dist));
        view.setLookAt(camPos.x, camPos.y, camPos.z, 8, lookY, 8, 0, 1, 0);
        proj.setPerspective((float) Math.toRadians(40), W / (float) H, 1f, 1000f);
        col = new float[W * H * 3];
        zb = new float[W * H];
        java.util.Arrays.fill(zb, Float.MAX_VALUE);
        for (int y = 0; y < H; y++) {
            float k = y / (float) H;
            for (int x = 0; x < W; x++) {
                int i = (y * W + x) * 3;
                col[i] = 0.05f - 0.03f * k;
                col[i + 1] = 0.045f - 0.03f * k;
                col[i + 2] = 0.08f - 0.05f * k;
            }
        }
        // opaques d'abord, puis ce qui s'ajoute
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < list.size(); i++) {
                DrawList.Op o = list.get(i);
                boolean opaque = o.type == DrawList.SOLID;
                if ((pass == 0) != opaque) continue;
                switch (o.type) {
                    case DrawList.SOLID -> mesh(o, 0);
                    case DrawList.TRANSLUCENT -> mesh(o, 1);
                    case DrawList.ADDITIVE -> mesh(o, 2);
                    case DrawList.BILLBOARD -> billboard(o);
                    case DrawList.QUAD -> quad(o);
                    case DrawList.BEAM -> beam(o);
                    default -> {
                    }
                }
            }
        }
        BufferedImage img = new BufferedImage(ow, oh, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < oh; y++) {
            for (int x = 0; x < ow; x++) {
                float r = 0, g = 0, b = 0;
                for (int dy = 0; dy < ss; dy++) {
                    for (int dx = 0; dx < ss; dx++) {
                        int i = ((y * ss + dy) * W + x * ss + dx) * 3;
                        r += col[i];
                        g += col[i + 1];
                        b += col[i + 2];
                    }
                }
                float n = ss * ss;
                img.setRGB(x, y, (tone(r / n) << 16) | (tone(g / n) << 8) | tone(b / n));
            }
        }
        ImageIO.write(img, "png", new File(out));
    }

    static int tone(float v) {
        v = v / (1 + 0.15f * v);
        return Math.max(0, Math.min(255, (int) (Math.pow(Math.max(0, v), 1 / 1.1) * 255)));
    }

    static int sample(BufferedImage im, float u, float v) {
        int x = Math.floorMod((int) (u * im.getWidth()), im.getWidth()), y = Math.floorMod((int) (v * im.getHeight()), im.getHeight());
        return im.getRGB(x, y);
    }

    static Vector4f clip(Matrix4f m, float x, float y, float z) {
        Vector4f p = new Vector4f(x, y, z, 1);
        m.transform(p);
        view.transform(p);
        proj.transform(p);
        return p;
    }

    /** mode 0 opaque, 1 translucide, 2 additif. */
    static void mesh(DrawList.Op o, int mode) {
        Mesh mesh = o.mesh;
        float[] v = mesh.data();
        Matrix3f nm = new Matrix3f();
        o.m.normal(nm);
        float tr = mode == 0 ? (o.r == 0 && o.g == 0 && o.b == 0 ? 1 : o.r) : o.r, tg = mode == 0 ? (o.r == 0 && o.g == 0 && o.b == 0 ? 1 : o.g) : o.g,
                tb = mode == 0 ? (o.r == 0 && o.g == 0 && o.b == 0 ? 1 : o.b) : o.b;
        for (int k = 0; k + 3 < mesh.vertexCount(); k += 4) {
            float[][] sv = new float[4][];
            for (int j = 0; j < 4; j++) {
                int b = (k + j) * Mesh.STRIDE;
                Vector4f p = clip(o.m, v[b], v[b + 1], v[b + 2]);
                Vector3f n = new Vector3f(v[b + 5], v[b + 6], v[b + 7]);
                nm.transform(n);
                if (n.lengthSquared() > 0) n.normalize();
                float sh = mode == 0 ? 0.42f + 0.58f * Math.max(0, n.dot(LIGHT)) + 0.12f * Math.max(0, -n.y) : 1;
                sv[j] = new float[]{p.x, p.y, p.z, p.w, v[b + 3], v[b + 4], v[b + 8] * sh, v[b + 9] * sh, v[b + 10] * sh};
            }
            tri(sv[0], sv[1], sv[2], o, mode, tr, tg, tb);
            tri(sv[0], sv[2], sv[3], o, mode, tr, tg, tb);
        }
    }

    static void tri(float[] a, float[] b, float[] c, DrawList.Op o, int mode, float tr, float tg, float tb) {
        if (a[3] < 1 || b[3] < 1 || c[3] < 1) return;
        float[][] P = {a, b, c};
        float[] sx = new float[3], sy = new float[3], sz = new float[3], iw = new float[3];
        for (int i = 0; i < 3; i++) {
            iw[i] = 1 / P[i][3];
            sx[i] = (P[i][0] * iw[i] * 0.5f + 0.5f) * W;
            sy[i] = (0.5f - P[i][1] * iw[i] * 0.5f) * H;
            sz[i] = P[i][3];
        }
        float area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0]);
        if (Math.abs(area) < 1e-6f) return;
        int x0 = Math.max(0, (int) Math.floor(Math.min(sx[0], Math.min(sx[1], sx[2])))), x1 = Math.min(W - 1, (int) Math.ceil(Math.max(sx[0], Math.max(sx[1], sx[2]))));
        int y0 = Math.max(0, (int) Math.floor(Math.min(sy[0], Math.min(sy[1], sy[2])))), y1 = Math.min(H - 1, (int) Math.ceil(Math.max(sy[0], Math.max(sy[1], sy[2]))));
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                float px = x + 0.5f, py = y + 0.5f;
                float w0 = ((sx[1] - px) * (sy[2] - py) - (sx[2] - px) * (sy[1] - py)) / area;
                float w1 = ((sx[2] - px) * (sy[0] - py) - (sx[0] - px) * (sy[2] - py)) / area;
                float w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                float q0 = w0 * iw[0], q1 = w1 * iw[1], q2 = w2 * iw[2], qs = q0 + q1 + q2;
                q0 /= qs;
                q1 /= qs;
                q2 /= qs;
                float z = q0 * sz[0] + q1 * sz[1] + q2 * sz[2];
                int i = y * W + x;
                if (z >= zb[i] - (mode == 0 ? 0 : 0.05f)) continue;
                float u = q0 * a[4] + q1 * b[4] + q2 * c[4], vv = q0 * a[5] + q1 * b[5] + q2 * c[5];
                int i3 = i * 3;
                if (mode == 2) {
                    int g = sample(glow, u, vv);
                    float al = o.a;
                    col[i3] += ((g >> 16) & 255) / 255f * o.r * al;
                    col[i3 + 1] += ((g >> 8) & 255) / 255f * o.g * al;
                    col[i3 + 2] += (g & 255) / 255f * o.b * al;
                    continue;
                }
                int t = sample(atlas, u, vv);
                float r = ((t >> 16) & 255) / 255f, g = ((t >> 8) & 255) / 255f, bl = (t & 255) / 255f;
                float cr = q0 * a[6] + q1 * b[6] + q2 * c[6], cg = q0 * a[7] + q1 * b[7] + q2 * c[7], cb = q0 * a[8] + q1 * b[8] + q2 * c[8];
                if (mode == 1) {
                    float al = ((t >>> 24) & 255) / 255f * o.a;
                    if (atlas.getColorModel().hasAlpha() && al <= 0) continue;
                    col[i3] = col[i3] * (1 - al) + r * o.r * al;
                    col[i3 + 1] = col[i3 + 1] * (1 - al) + g * o.g * al;
                    col[i3 + 2] = col[i3 + 2] * (1 - al) + bl * o.b * al;
                    continue;
                }
                int gl = sample(glow, u, vv);
                float ge = o.glow * 1.3f;
                col[i3] = r * cr * tr + ((gl >> 16) & 255) / 255f * ge;
                col[i3 + 1] = g * cg * tg + ((gl >> 8) & 255) / 255f * ge;
                col[i3 + 2] = bl * cb * tb + (gl & 255) / 255f * ge;
                zb[i] = z;
            }
        }
    }

    static BufferedImage sprite(String file) {
        return SPRITES.computeIfAbsent(file, f -> {
            try {
                return ImageIO.read(res.resolve("textures/fx/" + f + ".png").toFile());
            } catch (IOException e) {
                return null;
            }
        });
    }

    /** Quad additif texture d'un sprite (coins dans le repere monde). */
    static void spriteQuad(BufferedImage sp, Vector3f[] c, DrawList.Op o) {
        if (sp == null) return;
        Matrix4f id = new Matrix4f();
        float[][] q = new float[4][];
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (int j = 0; j < 4; j++) {
            Vector4f p = clip(id, c[j].x, c[j].y, c[j].z);
            q[j] = new float[]{p.x, p.y, p.z, p.w, uv[j][0], uv[j][1]};
        }
        addTri(sp, q[0], q[1], q[2], o);
        addTri(sp, q[0], q[2], q[3], o);
    }

    static void addTri(BufferedImage sp, float[] a, float[] b, float[] c, DrawList.Op o) {
        if (a[3] < 1 || b[3] < 1 || c[3] < 1) return;
        float[][] P = {a, b, c};
        float[] sx = new float[3], sy = new float[3], iw = new float[3];
        for (int i = 0; i < 3; i++) {
            iw[i] = 1 / P[i][3];
            sx[i] = (P[i][0] * iw[i] * 0.5f + 0.5f) * W;
            sy[i] = (0.5f - P[i][1] * iw[i] * 0.5f) * H;
        }
        float area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0]);
        if (Math.abs(area) < 1e-6f) return;
        int x0 = Math.max(0, (int) Math.floor(Math.min(sx[0], Math.min(sx[1], sx[2])))), x1 = Math.min(W - 1, (int) Math.ceil(Math.max(sx[0], Math.max(sx[1], sx[2]))));
        int y0 = Math.max(0, (int) Math.floor(Math.min(sy[0], Math.min(sy[1], sy[2])))), y1 = Math.min(H - 1, (int) Math.ceil(Math.max(sy[0], Math.max(sy[1], sy[2]))));
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                float px = x + 0.5f, py = y + 0.5f;
                float w0 = ((sx[1] - px) * (sy[2] - py) - (sx[2] - px) * (sy[1] - py)) / area;
                float w1 = ((sx[2] - px) * (sy[0] - py) - (sx[0] - px) * (sy[2] - py)) / area;
                float w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                float q0 = w0 * iw[0], q1 = w1 * iw[1], q2 = w2 * iw[2], qs = q0 + q1 + q2;
                q0 /= qs;
                q1 /= qs;
                q2 /= qs;
                float z = q0 * a[3] + q1 * b[3] + q2 * c[3];
                int i = y * W + x;
                if (z >= zb[i]) continue;
                int t = sample(sp, q0 * a[4] + q1 * b[4] + q2 * c[4], q0 * a[5] + q1 * b[5] + q2 * c[5]);
                float al = ((t >>> 24) & 255) / 255f * o.a;
                int i3 = i * 3;
                col[i3] += ((t >> 16) & 255) / 255f * o.r * al;
                col[i3 + 1] += ((t >> 8) & 255) / 255f * o.g * al;
                col[i3 + 2] += (t & 255) / 255f * o.b * al;
            }
        }
    }

    static void billboard(DrawList.Op o) {
        Vector3f c = o.m.transformPosition(new Vector3f(o.x, o.y, o.z));
        float sc = o.m.getScale(new Vector3f()).x;
        Vector3f f = new Vector3f(camPos).sub(c).normalize();
        Vector3f r = new Vector3f(0, 1, 0).cross(f).normalize(), u = new Vector3f(f).cross(r).normalize();
        double ang = Math.toRadians(o.rot);
        Vector3f rr = new Vector3f(r).mul((float) Math.cos(ang)).add(new Vector3f(u).mul((float) Math.sin(ang)));
        Vector3f uu = new Vector3f(u).mul((float) Math.cos(ang)).sub(new Vector3f(r).mul((float) Math.sin(ang)));
        float h = o.size * sc;
        spriteQuad(sprite(o.sprite.file), new Vector3f[]{
                new Vector3f(c).sub(new Vector3f(rr).mul(h)).sub(new Vector3f(uu).mul(h)), new Vector3f(c).add(new Vector3f(rr).mul(h)).sub(new Vector3f(uu).mul(h)),
                new Vector3f(c).add(new Vector3f(rr).mul(h)).add(new Vector3f(uu).mul(h)), new Vector3f(c).sub(new Vector3f(rr).mul(h)).add(new Vector3f(uu).mul(h))}, o);
    }

    static void quad(DrawList.Op o) {
        spriteQuad(sprite(o.sprite.file), new Vector3f[]{o.m.transformPosition(new Vector3f(o.x, o.y, 0)), o.m.transformPosition(new Vector3f(o.x1, o.y, 0)),
                o.m.transformPosition(new Vector3f(o.x1, o.y1, 0)), o.m.transformPosition(new Vector3f(o.x, o.y1, 0))}, o);
    }

    static void beam(DrawList.Op o) {
        Vector3f b = o.m.transformPosition(new Vector3f(o.x, o.y, o.z)), t = o.m.transformPosition(new Vector3f(o.x, o.y + o.len, o.z));
        Vector3f f = new Vector3f(camPos).sub(b);
        f.y = 0;
        f.normalize();
        Vector3f r = new Vector3f(0, 1, 0).cross(f).normalize().mul(o.width * o.m.getScale(new Vector3f()).x);
        spriteQuad(sprite(o.sprite.file), new Vector3f[]{new Vector3f(b).sub(r), new Vector3f(b).add(r), new Vector3f(t).add(r), new Vector3f(t).sub(r)}, o);
    }
}
