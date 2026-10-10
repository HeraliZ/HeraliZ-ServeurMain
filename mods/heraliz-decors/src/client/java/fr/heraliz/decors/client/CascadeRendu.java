package fr.heraliz.decors.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heraliz.decors.HeralizDecors;
import fr.heraliz.decors.effet.CascadeBE;
import fr.heraliz.decors.effet.CascadeBloc;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * La grande cascade, dessinée d'un seul tenant (aucun raccord entre blocs) : l'eau file dans la fissure de la roche,
 * déborde et tombe en trois nappes continues (arrière lente, principale, voile d'embruns rapide devant), qui
 * s'écartent de la paroi en tombant (courbe de chute) et ondulent ; bords irréguliers qui s'effacent ; au pied, gerbes
 * qui montent et écume qui s'étale en anneau. La texture défile (rien n'est recalculé sur le serveur) ; la lumière est
 * prise le long de la chute pour suivre les lampes de la grotte. La nappe tombe collée à la paroi (courbe K). Environ
 * 2 300 faces pour une cascade de 12 x 40.
 */
public final class CascadeRendu implements BlockEntityRenderer<CascadeBE> {
    private static final ResourceLocation CHUTE = new ResourceLocation(HeralizDecors.MODID, "textures/entity/chute.png");
    private static final ResourceLocation VOILE = new ResourceLocation(HeralizDecors.MODID, "textures/entity/chute_voile.png");
    private static final ResourceLocation ECUME = new ResourceLocation(HeralizDecors.MODID, "textures/entity/chute_ecume.png");
    /** Distances de chute des premières rangées : serrées en haut, là où la nappe se courbe. */
    private static final float[] PAS = {0, 0.2f, 0.5f, 0.9f, 1.4f, 2.0f, 2.8f, 3.8f, 5.0f};
    private static final float Y0 = 0.18f;
    /** Courbe de chute : la nappe s'écarte de la paroi de K * racine(chute) ; petit, elle tombe collée à la roche. */
    private static final float K = 0.22f;

    @Override
    public void render(CascadeBE be, float pt, PoseStack ps, MultiBufferSource buf, int lumiere, int overlay) {
        Level lv = be.getLevel();
        if (lv == null) {
            return;
        }
        double temps = (lv.getGameTime() + (double) pt) / 20.0;
        float w = be.largeur, h = be.hauteur, rec = be.recul;
        Direction f = be.getBlockState().getValue(CascadeBloc.FACING);
        float[] chutes = rangees(h);
        int[] lum = lumieres(lv, be.getBlockPos(), f, chutes);
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-f.toYRot()));
        PoseStack.Pose pose = ps.last();
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(CHUTE));
        levre(vc, pose, w, rec, temps, lum[0]);
        nappe(vc, pose, chutes, lum, w, 1f, -0.18f, rec, temps, 1.1, 0.8f, 0.13f, false);
        nappe(vc, pose, chutes, lum, w, 1f, 0f, rec, temps, 1.6, 0.96f, 0f, false);
        VertexConsumer vv = buf.getBuffer(RenderType.entityTranslucent(VOILE));
        nappe(vv, pose, chutes, lum, w, 1.08f, 0.3f, rec, temps, 2.6, 0.72f, 0.41f, true);
        gerbes(vv, pose, w, h, temps, lum[lum.length - 1]);
        VertexConsumer ve = buf.getBuffer(RenderType.entityTranslucent(ECUME));
        anneau(ve, pose, w, h, temps, lum[lum.length - 1]);
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(CascadeBE be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }

    private static float[] rangees(float h) {
        int n = 0;
        while (n < PAS.length && PAS[n] < h) {
            n++;
        }
        int extra = (int) Math.ceil(Math.max(0, h - PAS[n - 1]) / 2.0);
        float[] out = new float[n + extra];
        System.arraycopy(PAS, 0, out, 0, n);
        for (int k = 0; k < extra; k++) {
            out[n + k] = Math.min(h, PAS[n - 1] + 2 * (k + 1));
        }
        out[out.length - 1] = h;
        return out;
    }

    /** Lumière à chaque rangée (au moins 7 de lumière de bloc : l'eau reste visible dans le noir). */
    private static int[] lumieres(Level lv, BlockPos p, Direction f, float[] chutes) {
        int[] out = new int[chutes.length];
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int k = 0; k < chutes.length; k++) {
            int avance = 1 + (int) (K * Math.sqrt(chutes[k]));
            mp.set(p).move(f, avance).move(Direction.DOWN, (int) chutes[k]);
            int l = LevelRenderer.getLightColor(lv, mp);
            out[k] = LightTexture.pack(Math.max(7, LightTexture.block(l)), LightTexture.sky(l));
        }
        return out;
    }

    private static float lisse(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static float bord(float u, float d) {
        float s = Mth.sin(u * 37 + d * 0.3f);
        return lisse(0, 0.12f, u) * lisse(1, 0.88f, u) * (0.75f + 0.25f * s * s);
    }

    private static float frac(double v) {
        return (float) (v - Math.floor(v));
    }

    private static void sommet(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float a, float u, float v, int lum,
                               float nx, float ny, float nz) {
        vc.vertex(p.pose(), x, y, z).color(1f, 1f, 1f, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(lum)
                .normal(p.normal(), nx, ny, nz).endVertex();
    }

    /** L'eau qui file à plat dans la fissure jusqu'au bord de la roche (texture continue avec la nappe). */
    private static void levre(VertexConsumer vc, PoseStack.Pose p, float w, float rec, double temps, int lum) {
        int cols = Math.max(6, Math.round(w * 2));
        float x0 = -w / 2, off = frac(temps * 1.6), za = -rec + 0.2f, zb = 0.5f;
        for (int i = 0; i < cols; i++) {
            float u0 = (float) i / cols, u1 = (float) (i + 1) / cols;
            float xa = x0 + u0 * w, xb = x0 + u1 * w;
            float ea = 0.96f * bord(u0, 0), eb = 0.96f * bord(u1, 0);
            sommet(vc, p, xa, Y0, za, ea, xa / 4, (za + rec) / 4 - off, lum, 0, 1, 0);
            sommet(vc, p, xb, Y0, za, eb, xb / 4, (za + rec) / 4 - off, lum, 0, 1, 0);
            sommet(vc, p, xb, Y0, zb, eb, xb / 4, (zb + rec) / 4 - off, lum, 0, 1, 0);
            sommet(vc, p, xa, Y0, zb, ea, xa / 4, (zb + rec) / 4 - off, lum, 0, 1, 0);
        }
    }

    private static void nappe(VertexConsumer vc, PoseStack.Pose p, float[] chutes, int[] lum, float w, float ampl, float dz, float rec,
                              double temps, double vit, float alpha, float du, boolean voile) {
        int cols = Math.max(6, Math.round(w * 2 * ampl));
        float x0 = -w * ampl / 2, larg = w * ampl, off = frac(temps * vit);
        float ph = (float) (temps % 7.853981633974483) * 0.8f;
        float lev = rec + 0.5f, h = chutes[chutes.length - 1];
        for (int j = 0; j + 1 < chutes.length; j++) {
            float da = chutes[j], db = chutes[j + 1];
            for (int i = 0; i < cols; i++) {
                float ua = (float) i / cols, ub = (float) (i + 1) / cols;
                float xa = x0 + ua * larg, xb = x0 + ub * larg;
                pt(vc, p, xa, da, ua, dz, alpha, voile, du, lev, off, ph, lum[j], h);
                pt(vc, p, xb, da, ub, dz, alpha, voile, du, lev, off, ph, lum[j], h);
                pt(vc, p, xb, db, ub, dz, alpha, voile, du, lev, off, ph, lum[j + 1], h);
                pt(vc, p, xa, db, ua, dz, alpha, voile, du, lev, off, ph, lum[j + 1], h);
            }
        }
    }

    private static void pt(VertexConsumer vc, PoseStack.Pose p, float x, float d, float u, float dz, float alpha, boolean voile, float du,
                           float lev, float off, float ph, int lum, float h) {
        float z = 0.5f + K * Mth.sqrt(d) + dz + 0.08f * Mth.sin(x * 0.9f + d * 0.12f + ph);
        float bas = Mth.clamp(1 - (h - d) / 3f, 0, 1);                  // les 3 derniers blocs : eau blanche, plus dense
        float a = Math.min(1f, alpha * bord(u, d) * (voile ? Math.min(1f, d / 1.5f) : 1f) * (1 + 0.6f * bas));
        sommet(vc, p, x, Y0 - d, z, a, x / 4 + du, (lev + d) / 4 - off, lum, 0, 0, 1);
    }

    /** Gerbes au pied : deux rangées de voiles qui montent et retombent. */
    private static void gerbes(VertexConsumer vc, PoseStack.Pose p, float w, float h, double temps, int lum) {
        float zi = 0.5f + K * Mth.sqrt(h), y = Y0 - h, off = frac(temps * 1.4);
        float ph = (float) (temps % 2.731819) * 2.3f;
        int n = Math.max(4, Math.round(w * 1.1f));
        for (int rang = 0; rang < 2; rang++) {
            float z = zi + 0.5f + rang * 0.7f;
            for (int k = 0; k < n; k++) {
                float x = -w / 2 + (k + 0.5f) * w / n, hh = 2.5f + 1.5f * Mth.sin(k * 2.1f + rang + ph), u = x / 4 + k * 0.37f;
                sommet(vc, p, x - 1.2f, y, z, 0.75f, u, 0.8f + off, lum, 0, 0, 1);
                sommet(vc, p, x + 1.2f, y, z, 0.75f, u + 0.6f, 0.8f + off, lum, 0, 0, 1);
                sommet(vc, p, x + 1.2f, y + hh, z - 0.2f, 0f, u + 0.6f, off, lum, 0, 0, 1);
                sommet(vc, p, x - 1.2f, y + hh, z - 0.2f, 0f, u, off, lum, 0, 0, 1);
            }
        }
    }

    /** Écume au point d'impact : deux couches qui bouillonnent (dense au centre, presque opaque : on ne voit plus le fond
     * à travers), et qui s'étalent en anneau ovale sur l'eau. */
    private static void anneau(VertexConsumer vc, PoseStack.Pose p, float w, float h, double temps, int lum) {
        float zc = 0.5f + K * Mth.sqrt(h) + 1.2f, rx = w / 2 + 2.5f, rz = 3.8f;
        int seg = 28, anneaux = 5;
        for (int couche = 0; couche < 2; couche++) {
            float y = Y0 - h + 0.03f + couche * 0.05f, off = frac(temps * (couche == 0 ? 0.35 : 0.55)), du = couche * 0.37f;
            for (int k = 0; k < anneaux; k++) {
                float ra = 0.1f + 0.9f * k / anneaux, rb = 0.1f + 0.9f * (k + 1) / anneaux;
                float aa = (float) Math.pow(1 - ra, 0.55), ab = (float) Math.pow(1 - rb, 0.55);
                for (int i = 0; i < seg; i++) {
                    float a0 = (float) (i * Math.PI * 2 / seg), a1 = (float) ((i + 1) * Math.PI * 2 / seg);
                    float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                    float u0 = i * 6f / seg + du, u1 = (i + 1) * 6f / seg + du;
                    sommet(vc, p, c0 * rx * ra, y, zc + s0 * rz * ra, aa, u0, ra * 1.5f - off, lum, 0, 1, 0);
                    sommet(vc, p, c1 * rx * ra, y, zc + s1 * rz * ra, aa, u1, ra * 1.5f - off, lum, 0, 1, 0);
                    sommet(vc, p, c1 * rx * rb, y, zc + s1 * rz * rb, ab, u1, rb * 1.5f - off, lum, 0, 1, 0);
                    sommet(vc, p, c0 * rx * rb, y, zc + s0 * rz * rb, ab, u0, rb * 1.5f - off, lum, 0, 1, 0);
                }
            }
        }
    }
}
