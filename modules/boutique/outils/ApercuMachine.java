import fr.heraliz.casino.client.render.mesh.Mat;
import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.MachineDesign;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Apercu d'une machine du casino hors du jeu : son vrai modele (MachineDesign), ses vraies textures (atlas, feuilles,
 * calques lumineux) et ses lueurs, dessines par un petit moteur logiciel (comme ApercuCaisse pour les caisses). Lance par
 * outils/apercu_atm.py. Ce n'est pas la lumiere du jeu : de quoi juger formes, couleurs, proportions.
 * <p>Arguments : ressources sortie.png classeDuModele temps(ticks, -1 = repos) yaw pitch distance visX visY visZ largeur hauteur
 * silhouette(0/1) decor(0/1)
 */
public final class ApercuMachine {
    static int W, H;
    static float[] col, zb;
    static Path res;
    static String atlasName;
    static final Map<String, BufferedImage> IMAGES = new HashMap<>();
    static Matrix4f view = new Matrix4f(), proj = new Matrix4f();
    static Vector3f camPos = new Vector3f();
    static final Vector3f LIGHT = new Vector3f(0.45f, 0.8f, 0.7f).normalize(), FILL = new Vector3f(-0.7f, 0.3f, 0.4f).normalize();

    public static void main(String[] a) throws Exception {
        res = Path.of(a[0]);
        String out = a[1];
        MachineDesign<?> d = (MachineDesign<?>) Class.forName(a[2]).getConstructor().newInstance();
        float t = Float.parseFloat(a[3]);
        float yaw = Float.parseFloat(a[4]), pitch = Float.parseFloat(a[5]), dist = Float.parseFloat(a[6]);
        float lx = Float.parseFloat(a[7]), ly = Float.parseFloat(a[8]), lz = Float.parseFloat(a[9]);
        int ss = 2, ow = Integer.parseInt(a[10]), oh = Integer.parseInt(a[11]);
        boolean silhouette = a.length > 12 && a[12].equals("1"), decor = a.length > 13 && a[13].equals("1");
        W = ow * ss;
        H = oh * ss;
        atlasName = d.atlas;
        Scene s = t < 0 ? Scene.rest(false) : new Scene().set(MachineDesign.DEMO_START + (long) t, t - (long) t);
        Object v = t < 0 ? d.newView() : d.demoView(t);
        DrawList list = new DrawList();
        if (decor) decor(list);
        if (silhouette) silhouette(list);
        d.renderBody(list);
        d.renderAnimatedRaw(s, v, list);
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        camPos.set(lx + (float) (Math.sin(yr) * Math.cos(pr) * dist), ly + (float) (Math.sin(pr) * dist), lz + (float) (Math.cos(yr) * Math.cos(pr) * dist));
        view.setLookAt(camPos.x, camPos.y, camPos.z, lx, ly, lz, 0, 1, 0);
        proj.setPerspective((float) Math.toRadians(40), W / (float) H, 0.5f, 1000f);
        col = new float[W * H * 3];
        zb = new float[W * H];
        java.util.Arrays.fill(zb, Float.MAX_VALUE);
        for (int y = 0; y < H; y++) {
            float k = y / (float) H;
            for (int x = 0; x < W; x++) {
                int i = (y * W + x) * 3;
                col[i] = 0.05f - 0.03f * k;
                col[i + 1] = 0.045f - 0.03f * k;
                col[i + 2] = 0.07f - 0.04f * k;
            }
        }
        // opaques d'abord, puis le verre et les lueurs
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

    /** Sol de beton et mur de beton derriere la machine (feuille /recup), comme dans la salle du casino. */
    static void decor(DrawList list) {
        Mat beton = Mat.tri(384, 256, 128, 128, 8);
        MeshBuilder b = new MeshBuilder();
        // en dalles de 8 px : un grand quad qui passe derriere la camera serait jete en entier
        for (int x = -96; x < 96; x += 8) {
            for (int z = -40; z < 72; z += 8) b.box(x, -1, z, x + 8, 0, z + 8, beton);
            for (int y = 0; y < 72; y += 8) b.box(x, y, -2, x + 8, y + 8, -1, beton);
        }
        list.sheet("/recup");
        list.solidTinted(new Pose(), b.build(), 0, 0.27f, 0.27f, 0.3f);
        list.sheet(null);
    }

    /** Silhouette d'un joueur (1,8 bloc) a gauche de la machine, pour l'echelle. */
    static void silhouette(DrawList list) {
        Mat m = Mat.tri(256, 512, 256, 256, 16);
        MeshBuilder b = new MeshBuilder();
        float x = -12, z = 10, k = 28.8f / 32f;
        b.push().translate(x, 0, z).scale(k);
        b.bevelBox(-4, 0, -2, 0, 12, 2, 0.2f, m);
        b.bevelBox(0, 0, -2, 4, 12, 2, 0.2f, m);
        b.bevelBox(-4, 12, -2, 4, 24, 2, 0.2f, m);
        b.bevelBox(-8, 12, -2, -4, 24, 2, 0.2f, m);
        b.bevelBox(4, 12, -2, 8, 24, 2, 0.2f, m);
        b.bevelBox(-4, 24, -4, 4, 32, 4, 0.3f, m);
        b.pop();
        list.solidTinted(new Pose(), b.build(), 0, 0.42f, 0.44f, 0.48f);
    }

    static int tone(float v) {
        v = v / (1 + 0.15f * v);
        return Math.max(0, Math.min(255, (int) (Math.pow(Math.max(0, v), 1 / 1.1) * 255)));
    }

    /** Atlas de la machine (null), feuille commune ("/recup") ou feuille de la machine ("nom"), et son calque _glow. */
    static BufferedImage image(String sheet, boolean glow) {
        String base = sheet == null ? atlasName : sheet.startsWith("/") ? sheet.substring(1) : atlasName + "_" + sheet;
        String nom = "textures/machine/" + base + (glow ? "_glow" : "") + ".png";
        return IMAGES.computeIfAbsent(nom, f -> {
            try {
                Path p = res.resolve(f);
                return Files.exists(p) ? ImageIO.read(p.toFile()) : null;
            } catch (IOException e) {
                return null;
            }
        });
    }

    static int sample(BufferedImage im, float u, float v) {
        if (im == null) return 0;
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
        if (mesh == null) return;
        BufferedImage atlas = image(o.sheet, false), glow = image(o.sheet, true);
        float[] v = mesh.data();
        Matrix3f nm = new Matrix3f();
        o.m.normal(nm);
        for (int k = 0; k + 3 < mesh.vertexCount(); k += 4) {
            float[][] sv = new float[4][];
            for (int j = 0; j < 4; j++) {
                int b = (k + j) * Mesh.STRIDE;
                Vector4f p = clip(o.m, v[b], v[b + 1], v[b + 2]);
                Vector3f n = new Vector3f(v[b + 5], v[b + 6], v[b + 7]);
                nm.transform(n);
                if (n.lengthSquared() > 0) n.normalize();
                float sh = mode == 0 ? 0.32f + 0.6f * Math.max(0, n.dot(LIGHT)) + 0.22f * Math.max(0, n.dot(FILL)) : 1;
                sv[j] = new float[]{p.x, p.y, p.z, p.w, v[b + 3], v[b + 4], v[b + 8] * sh, v[b + 9] * sh, v[b + 10] * sh};
            }
            tri(sv[0], sv[1], sv[2], o, mode, atlas, glow);
            tri(sv[0], sv[2], sv[3], o, mode, atlas, glow);
        }
    }

    static void tri(float[] a, float[] b, float[] c, DrawList.Op o, int mode, BufferedImage atlas, BufferedImage glow) {
        if (a[3] < 0.5f || b[3] < 0.5f || c[3] < 0.5f) return;
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
                if (z >= zb[i] - (mode == 0 ? 0 : 0.02f)) continue;
                float u = q0 * a[4] + q1 * b[4] + q2 * c[4], vv = q0 * a[5] + q1 * b[5] + q2 * c[5];
                int i3 = i * 3;
                if (mode == 2) {
                    int g = sample(glow, u, vv);
                    col[i3] += ((g >> 16) & 255) / 255f * o.r * o.a;
                    col[i3 + 1] += ((g >> 8) & 255) / 255f * o.g * o.a;
                    col[i3 + 2] += (g & 255) / 255f * o.b * o.a;
                    continue;
                }
                int t = sample(atlas, u, vv);
                float r = ((t >> 16) & 255) / 255f, g = ((t >> 8) & 255) / 255f, bl = (t & 255) / 255f;
                if (mode == 1) {
                    float al = ((t >>> 24) & 255) / 255f * o.a;
                    if (al <= 0) continue;
                    col[i3] = col[i3] * (1 - al) + r * o.r * al;
                    col[i3 + 1] = col[i3 + 1] * (1 - al) + g * o.g * al;
                    col[i3 + 2] = col[i3 + 2] * (1 - al) + bl * o.b * al;
                    continue;
                }
                float cr = q0 * a[6] + q1 * b[6] + q2 * c[6], cg = q0 * a[7] + q1 * b[7] + q2 * c[7], cb = q0 * a[8] + q1 * b[8] + q2 * c[8];
                int gl = sample(glow, u, vv);
                float ge = o.glow * 1.3f;
                col[i3] = r * cr * o.r + ((gl >> 16) & 255) / 255f * ge;
                col[i3 + 1] = g * cg * o.g + ((gl >> 8) & 255) / 255f * ge;
                col[i3 + 2] = bl * cb * o.b + (gl & 255) / 255f * ge;
                zb[i] = z;
            }
        }
    }

    /** Lueur (sprite additif face a la camera). */
    static void billboard(DrawList.Op o) {
        BufferedImage sp = IMAGES.computeIfAbsent("fx/" + o.sprite.file, f -> {
            try {
                return ImageIO.read(res.resolve(o.sprite.path()).toFile());
            } catch (IOException e) {
                return null;
            }
        });
        if (sp == null) return;
        Vector3f c = o.m.transformPosition(new Vector3f(o.x, o.y, o.z));
        Vector3f f = new Vector3f(camPos).sub(c).normalize();
        Vector3f r = new Vector3f(0, 1, 0).cross(f).normalize(), u = new Vector3f(f).cross(r).normalize();
        float h = o.size;
        Vector3f[] q = {new Vector3f(c).sub(new Vector3f(r).mul(h)).sub(new Vector3f(u).mul(h)), new Vector3f(c).add(new Vector3f(r).mul(h)).sub(new Vector3f(u).mul(h)),
                new Vector3f(c).add(new Vector3f(r).mul(h)).add(new Vector3f(u).mul(h)), new Vector3f(c).sub(new Vector3f(r).mul(h)).add(new Vector3f(u).mul(h))};
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        float[][] pts = new float[4][];
        Matrix4f id = new Matrix4f();
        for (int j = 0; j < 4; j++) {
            Vector4f p = clip(id, q[j].x, q[j].y, q[j].z);
            pts[j] = new float[]{p.x, p.y, p.z, p.w, uv[j][0], uv[j][1]};
        }
        spriteTri(sp, pts[0], pts[1], pts[2], o);
        spriteTri(sp, pts[0], pts[2], pts[3], o);
    }

    static void spriteTri(BufferedImage sp, float[] a, float[] b, float[] c, DrawList.Op o) {
        if (a[3] < 0.5f || b[3] < 0.5f || c[3] < 0.5f) return;
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
}
