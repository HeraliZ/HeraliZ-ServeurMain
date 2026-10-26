package fr.heralisz.utils.deco;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Pont de corde dessiné d'un bloc depuis sa tête : planches (3 par bloc, quelques-unes cassées ou manquantes, posées
 * de travers), cordes porteuses sous les planches, mains courantes, suspentes, et les cordes qui remontent aux poteaux
 * d'ancrage (posés par le générateur à un bloc des bords, un bloc avant et après le tablier). Le tablier dérive, roule
 * et rebondit selon le balancement de PontBlockEntity ; sous les pieds d'un marcheur il reste à la hauteur du plancher
 * invisible. Trois niveaux de détail selon la distance (au-delà de 160 blocs, rien).
 * Texture (tools/gen_anim_meshes.py, pont_texture) : 128 x 128, planche k en 0..96 x 12k..12k+12, corde en 96..104.
 */
@OnlyIn(Dist.CLIENT)
public class PontRenderer implements BlockEntityRenderer<PontBlockEntity> {
    private static final ResourceLocation TEX = new ResourceLocation(HeraliszUtils.MODID, "textures/entity/anim/pont_corde.png");
    private static final float PX = 1f / 128f;

    private VertexConsumer vc;
    private Matrix4f m;
    private Matrix3f nm;
    private float dx, dz, rx, rz, ox, oz;
    private PontBlockEntity be;
    private float t, e;
    private int[] lum = new int[0];
    private final float[] c = new float[3], a = new float[3], tg = new float[3], nr = new float[3];

    public PontRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(PontBlockEntity b, float partial, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        Level level = b.getLevel();
        if (!b.lu || b.n < 2 || level == null) {
            return;
        }
        be = b;
        Direction d = b.sens(), r = d.getClockWise();
        dx = d.getStepX();
        dz = d.getStepZ();
        rx = r.getStepX();
        rz = r.getStepZ();
        ox = 0.5f - dx * 0.5f;
        oz = 0.5f - dz * 0.5f;
        BlockPos p = b.getBlockPos();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        int n = b.n;
        float sc = Mth.clamp(b.abscisse(cam.x, cam.z), 0, n);
        double dist = Math.sqrt(cam.distanceToSqr(p.getX() + ox + dx * sc, p.getY() + b.hauteur(sc), p.getZ() + oz + dz * sc));
        if (dist > 160) {
            return;
        }
        int lod = dist < 24 ? 0 : dist < 64 ? 1 : 2;
        t = (level.getGameTime() % 72000L) + partial;
        e = b.energie(partial);
        vc = buf.getBuffer(RenderType.entityCutout(TEX));
        m = ps.last().pose();
        nm = ps.last().normal();
        if (lum.length < n + 2) {
            lum = new int[n + 2];
        }
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int k = -1; k <= n; k++) {
            float s = k + 0.5f;
            mp.set(p.getX() + Mth.floor(ox + dx * s), p.getY() + Mth.floor(b.hauteur(s) + 0.3f), p.getZ() + Mth.floor(oz + dz * s));
            lum[k + 1] = LevelRenderer.getLightColor(level, mp);
        }
        float W = b.demi + 0.3f;
        // planches
        float step = lod == 0 ? 1f / 3f : lod == 1 ? 1f : 2f;
        int j = 0;
        for (float s = step / 2; s < n; s += step, j++) {
            int hsh = (int) (Mth.getSeed(p.getX() + j * 31, p.getY(), p.getZ() - j * 17) & 0x7FFFFFFF);
            boolean bout = s < 1 || s > n - 1;
            if (lod == 0 && !bout && hsh % 41 == 0) {
                continue;                                                        // planche manquante
            }
            float w0 = -W, w1 = W;
            if (lod == 0 && !bout && hsh % 23 == 0) {                            // planche cassée
                if ((hsh >> 5) % 2 == 0) {
                    w0 = -W * 0.4f;
                } else {
                    w1 = W * 0.4f;
                }
            }
            float shift = lod == 0 ? ((hsh >> 8) % 17 - 8) * 0.01f : 0;
            float yaw = lod == 0 ? ((hsh >> 13) % 11 - 5) * 0.007f : 0;
            float len = lod == 0 ? 0.27f : step * 0.97f;
            planche(s, w0 + shift, w1 + shift, len, yaw, (hsh >> 3) % 6, lod == 2, W);
        }
        // cordes porteuses, mains courantes, suspentes, remontées aux poteaux
        float ec = W + 0.04f;
        float cs = lod == 0 ? 1f : lod == 1 ? 2f : 4f;
        for (int side = -1; side <= 1; side += 2) {
            corde(side * ec, 1.02f, cs, n, 0.035f);
            if (lod < 2) {
                corde(side * (W + 0.02f), -0.09f, cs * 2, n, 0.03f);
            }
            float post = side * (b.demi + 1);
            ancrage(-0.5f, post, b.hauteur(0) + 1.3f, 0f, side * ec, 1.02f);
            ancrage(n + 0.5f, post, b.hauteur(n) + 1.3f, n, side * ec, 1.02f);
            if (lod < 2) {
                ancrage(-0.5f, post, b.hauteur(0) + 0.1f, 0f, side * (W + 0.02f), -0.09f);
                ancrage(n + 0.5f, post, b.hauteur(n) + 0.1f, n, side * (W + 0.02f), -0.09f);
            }
            if (lod < 2) {
                float hs = lod == 0 ? 1.5f : 4.5f;
                for (float s = hs / 2; s < n; s += hs) {
                    point(s, side * ec, 1.02f, c);
                    float x0 = c[0], y0 = c[1], z0 = c[2];
                    point(s, side * (W + 0.02f), -0.09f, c);
                    tube(x0, y0, z0, c[0], c[1], c[2], 0.02f, lumiere(s));
                }
            }
        }
    }

    private int lumiere(float s) {
        int k = Mth.clamp(Mth.floor(s) + 1, 0, be.n + 1);
        return lum[k];
    }

    /** Point du tablier à l'abscisse s, à `l` du milieu (latéral) et `v` au-dessus du plancher, avec le balancement. */
    private void point(float s, float l, float v, float[] out) {
        float y = be.hauteur(s) + be.rebond(s, t, e);
        float dr = be.derive(s, t, e);
        float ro = be.roulis(s, t, e);
        float co = Mth.cos(ro), si = Mth.sin(ro);
        float lat = dr + l * co - v * si;
        out[0] = ox + dx * s + rx * lat;
        out[1] = y + l * si + v * co;
        out[2] = oz + dz * s + rz * lat;
    }

    /** Repère local du tablier en s : c = milieu, a = en travers, tg = le long (pente), nr = normale. */
    private void repere(float s) {
        point(s, 0, 0, c);
        float ro = be.roulis(s, t, e);
        float co = Mth.cos(ro), si = Mth.sin(ro);
        a[0] = rx * co;
        a[1] = si;
        a[2] = rz * co;
        float pente = (be.hauteur(s + 0.15f) - be.hauteur(s - 0.15f)) / 0.3f;
        float il = 1f / Mth.sqrt(1 + pente * pente);
        tg[0] = dx * il;
        tg[1] = pente * il;
        tg[2] = dz * il;
        nr[0] = a[1] * tg[2] - a[2] * tg[1];                                   // a x tg (vers le haut)
        nr[1] = a[2] * tg[0] - a[0] * tg[2];
        nr[2] = a[0] * tg[1] - a[1] * tg[0];
    }

    private void planche(float s, float w0, float w1, float len, float yaw, int var, boolean simple, float W) {
        repere(s);
        float cy = Mth.cos(yaw), sy = Mth.sin(yaw);
        float[] A = {a[0] * cy - tg[0] * sy, a[1] * cy - tg[1] * sy, a[2] * cy - tg[2] * sy};
        float[] T = {tg[0] * cy + a[0] * sy, tg[1] * cy + a[1] * sy, tg[2] * cy + a[2] * sy};
        float wm = (w0 + w1) / 2, wh = (w1 - w0) / 2, lh = len / 2, ep = 0.07f;
        float u0 = (w0 + W) / (2 * W) * 96 * PX, u1 = (w1 + W) / (2 * W) * 96 * PX;
        float v0 = (var * 12 + 1) * PX, v1 = (var * 12 + 10) * PX, vs0 = (var * 12 + 10) * PX, vs1 = (var * 12 + 12) * PX;
        int li = lumiere(s);
        float cx = c[0] + A[0] * wm, cyy = c[1] + A[1] * wm, cz = c[2] + A[2] * wm;
        // dessus (normale nr), dessous
        face(cx, cyy, cz, A, wh, T, lh, nr, u0, u1, v0, v1, li, false);
        float bx = cx - nr[0] * ep, by = cyy - nr[1] * ep, bz = cz - nr[2] * ep;
        float[] nn = {-nr[0], -nr[1], -nr[2]};
        face(bx, by, bz, T, lh, A, wh, nn, u0, u1, v0, v1, li, true);
        if (simple) {
            return;
        }
        // chants avant et arrière
        float mx = cx - nr[0] * ep / 2, my = cyy - nr[1] * ep / 2, mz = cz - nr[2] * ep / 2;
        float[] fT = {T[0], T[1], T[2]}, bT = {-T[0], -T[1], -T[2]};
        face(mx + T[0] * lh, my + T[1] * lh, mz + T[2] * lh, nr, ep / 2, A, wh, fT, u0, u1, vs0, vs1, li, true);
        face(mx - T[0] * lh, my - T[1] * lh, mz - T[2] * lh, A, wh, nr, ep / 2, bT, u0, u1, vs0, vs1, li, false);
    }

    /**
     * Quad centré en (x, y, z), côtés A (demi-longueur ha) et B (demi-longueur hb) avec A x B = normale (sens
     * antihoraire vu de devant). u suit A (ou B si uSurB), v suit l'autre.
     */
    private void face(float x, float y, float z, float[] A, float ha, float[] B, float hb, float[] n,
                      float u0, float u1, float v0, float v1, int li, boolean uSurB) {
        float[][] sg = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (float[] g : sg) {
            float px = x + A[0] * ha * g[0] + B[0] * hb * g[1];
            float py = y + A[1] * ha * g[0] + B[1] * hb * g[1];
            float pz = z + A[2] * ha * g[0] + B[2] * hb * g[1];
            float fa = (g[0] + 1) / 2, fb = (g[1] + 1) / 2;
            float u = uSurB ? u0 + (u1 - u0) * fb : u0 + (u1 - u0) * fa;
            float v = uSurB ? v0 + (v1 - v0) * fa : v0 + (v1 - v0) * fb;
            vc.vertex(m, px, py, pz).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(li)
                    .normal(nm, n[0], n[1], n[2]).endVertex();
        }
    }

    /** Corde qui suit le tablier de s = 0 à n, à `l` du milieu, `v` au-dessus du plancher, par pas de `pas`. */
    private void corde(float l, float v, float pas, int n, float rayon) {
        point(0, l, v, c);
        float px = c[0], py = c[1], pz = c[2];
        for (float s = pas; s < n + pas * 0.5f; s += pas) {
            float ss = Math.min(s, n);
            point(ss, l, v, c);
            tube(px, py, pz, c[0], c[1], c[2], rayon, lumiere(ss - pas / 2));
            px = c[0];
            py = c[1];
            pz = c[2];
            if (ss >= n) {
                break;
            }
        }
    }

    /** Corde du tablier (s0, l, v) jusqu'au haut ou au pied d'un poteau d'ancrage (sp, lp, hp) : sans balancement là-bas. */
    private void ancrage(float sp, float lp, float hp, float s0, float l, float v) {
        point(s0, l, v, c);
        tube(ox + dx * sp + rx * lp, hp, oz + dz * sp + rz * lp, c[0], c[1], c[2], 0.035f, lumiere(sp));
    }

    private void tube(float x0, float y0, float z0, float x1, float y1, float z1, float r, int li) {
        float tx = x1 - x0, ty = y1 - y0, tz = z1 - z0;
        float L = Mth.sqrt(tx * tx + ty * ty + tz * tz);
        if (L < 1e-4f) {
            return;
        }
        tx /= L;
        ty /= L;
        tz /= L;
        float sx = -tz, sy = 0, sz = tx;                                         // t x haut
        float sl = Mth.sqrt(sx * sx + sz * sz);
        if (sl < 1e-3f) {
            sx = rx;
            sz = rz;
            sl = 1;
        }
        sx /= sl;
        sz /= sl;
        float ux = sy * tz - sz * ty, uy = sz * tx - sx * tz, uz = sx * ty - sy * tx;   // s x t
        float mx = (x0 + x1) / 2, my = (y0 + y1) / 2, mz = (z0 + z1) / 2;
        float[] T = {tx, ty, tz};
        float[][] ns = {{sx, sy, sz}, {ux, uy, uz}, {-sx, -sy, -sz}, {-ux, -uy, -uz}};
        float u0 = 96 * PX, u1 = 104 * PX, v1 = Math.min(1f, L * 0.25f);
        for (float[] nn : ns) {
            float[] B = {nn[1] * tz - nn[2] * ty, nn[2] * tx - nn[0] * tz, nn[0] * ty - nn[1] * tx};   // n x t
            face(mx + nn[0] * r, my + nn[1] * r, mz + nn[2] * r, T, L / 2, B, r, nn, u0, u1, 0, v1, li, true);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(PontBlockEntity b) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 192;
    }
}
