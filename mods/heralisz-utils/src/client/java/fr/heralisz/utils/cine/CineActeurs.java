package fr.heralisz.utils.cine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.utils.HeraliszUtils;
import fr.heralisz.utils.train.PlateauRendu;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Les acteurs de la cinématique, dessinés par le mod sur le plateau (TrainClient les appelle avec la rame) : les goules
 * (Goule), le corps du joueur (son skin, sur le modèle du joueur : ses jambes et ses mains qu'il voit en tombant, puis
 * tout son corps éjecté du train) et les décors posés sur les vitres et le plancher (Trace : éclaboussure du choc,
 * traînée de sang qui s'allonge, fissures, lambeaux restés collés, mares). Rien n'existe hors de la cinématique.
 */
@OnlyIn(Dist.CLIENT)
public final class CineActeurs {
    static final List<Goule> GOULES = new ArrayList<>();
    static final List<Trace> TRACES = new ArrayList<>();
    static Corps corps;

    private CineActeurs() {}

    static ResourceLocation tex(String nom) {
        return new ResourceLocation(HeraliszUtils.MODID, "textures/cine/" + nom + ".png");
    }

    static void vider() {
        GOULES.clear();
        TRACES.clear();
        corps = null;
    }

    /** Fin de tick : chacun retient son état pour l'interpolation de l'image suivante. */
    static void tick() {
        for (Goule g : GOULES) {
            g.tick();
        }
        for (Trace t : TRACES) {
            t.tick();
        }
        if (corps != null) {
            corps.tick();
        }
    }

    // ---------------------------------------------------------------------------------------------- traces
    /** Un décor plat : de p0 à p1 (le long de la largeur de l'image), demi-hauteur dv ; zone (u0, v0, u1, v1) de la texture ;
     *  opacité ; p1 peut avancer (la traînée qui s'allonge derrière le visage) : interpolé à l'image. */
    static final class Trace {
        final ResourceLocation tex;
        Vec3 p0, p1, p1o, dv;
        float u0, v0, u1, v1, alpha = 1f, alphaO = 1f;
        int lumiere = -1;

        Trace(String tex, Vec3 p0, Vec3 p1, Vec3 dv, float u0, float v0, float u1, float v1) {
            this.tex = CineActeurs.tex(tex);
            this.p0 = p0;
            this.p1 = this.p1o = p1;
            this.dv = dv;
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
        }

        void tick() {
            p1o = p1;
            alphaO = alpha;
        }
    }

    static Trace trace(String tex, Vec3 p0, Vec3 p1, Vec3 dv) {
        Trace t = new Trace(tex, p0, p1, dv, 0, 0, 1, 1);
        TRACES.add(t);
        return t;
    }

    // ---------------------------------------------------------------------------------------------- corps du joueur
    /** Le corps du joueur (repère du plateau) : position des pieds… ou de n'importe quel point, orientation, angles des
     *  membres (radians, repère du modèle du joueur), tête cachée quand on voit par ses yeux. */
    static final class Corps {
        double x, y, z, xo, yo, zo;
        float lacet, tangage, roulis, lacetO, tangageO, roulisO;
        final Map<String, float[]> ang = new HashMap<>(), angO = new HashMap<>();
        boolean tete = true, visible = true, bras = true;
        int lumiere = -1;
        /** Hauteur (au-dessus des pieds) du pivot du tangage et du roulis : 0,7 = les hanches (on bascule assis). */
        float pivot = 0.703f;
        private PlayerModel<?> modele;
        private boolean fin;

        void tick() {
            xo = x;
            yo = y;
            zo = z;
            lacetO = lacet;
            tangageO = tangage;
            roulisO = roulis;
            ang.forEach((k, v) -> System.arraycopy(v, 0, angO.computeIfAbsent(k, kk -> new float[3]), 0, 3));
        }

        void a(String m, float rx, float ry, float rz) {
            float[] v = ang.computeIfAbsent(m, k -> new float[3]);
            v[0] = rx;
            v[1] = ry;
            v[2] = rz;
        }

        private float[] i(String k, float p) {
            float[] c = ang.get(k);
            if (c == null) {
                return new float[3];
            }
            float[] o = angO.getOrDefault(k, c);
            return new float[]{Mth.lerp(p, o[0], c[0]), Mth.lerp(p, o[1], c[1]), Mth.lerp(p, o[2], c[2])};
        }

        private static void mettre(ModelPart m, float[] a) {
            m.xRot = a[0];
            m.yRot = a[1];
            m.zRot = a[2];
        }

        /** Repère du modèle (unités du modèle du joueur, y vers le bas) dans celui du plateau. */
        private void repere(PoseStack ps, float p) {
            ps.translate(Mth.lerp(p, xo, x), Mth.lerp(p, yo, y), Mth.lerp(p, zo, z));
            ps.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(p, lacetO, lacet)));
            ps.translate(0, pivot, 0);
            ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(p, tangageO, tangage)));
            ps.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(p, roulisO, roulis)));
            ps.translate(0, -pivot, 0);
            ps.scale(-0.9375f, -0.9375f, 0.9375f);                                // comme le rendu des joueurs
            ps.translate(0, -1.501, 0);
        }

        /** Les yeux (repère du plateau), pour poser la caméra quand on voit par eux. */
        Vec3 oeil(float p) {
            PoseStack ps = new PoseStack();
            repere(ps, p);
            float[] a = i("tete", p);
            ps.mulPose(Axis.ZP.rotation(a[2]));
            ps.mulPose(Axis.YP.rotation(a[1]));
            ps.mulPose(Axis.XP.rotation(a[0]));
            org.joml.Vector3f v = ps.last().pose().transformPosition(new org.joml.Vector3f(0f, -4.2f / 16f, -1.2f / 16f));
            return new Vec3(v.x, v.y, v.z);
        }

        void dessiner(PoseStack ps, MultiBufferSource src, double ox, double oy, double oz, Vec3 cam, float p, int lum) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !visible) {
                return;
            }
            boolean fin2 = "slim".equals(mc.player.getModelName());
            if (modele == null || fin2 != fin) {
                fin = fin2;
                modele = new PlayerModel<>(mc.getEntityModels().bakeLayer(fin ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), fin);
            }
            PlayerModel<?> m = modele;
            m.young = false;
            m.crouching = false;
            mettre(m.head, i("tete", p));
            mettre(m.body, i("corps", p));
            mettre(m.rightArm, i("bras_d", p));
            mettre(m.leftArm, i("bras_g", p));
            mettre(m.rightLeg, i("jambe_d", p));
            mettre(m.leftLeg, i("jambe_g", p));
            m.rightArm.x = -5f;
            m.leftArm.x = 5f;
            m.rightArm.y = m.leftArm.y = 2f;
            m.rightLeg.x = -1.9f;
            m.leftLeg.x = 1.9f;
            m.rightLeg.y = m.leftLeg.y = 12f;
            m.rightLeg.z = m.leftLeg.z = 0.1f;
            m.head.y = 0;
            m.hat.copyFrom(m.head);
            m.jacket.copyFrom(m.body);
            m.rightSleeve.copyFrom(m.rightArm);
            m.leftSleeve.copyFrom(m.leftArm);
            m.rightPants.copyFrom(m.rightLeg);
            m.leftPants.copyFrom(m.leftLeg);
            m.head.visible = m.hat.visible = tete;
            m.body.visible = m.jacket.visible = tete;                             // par ses yeux : ni tête ni torse
            m.rightArm.visible = m.leftArm.visible = m.rightSleeve.visible = m.leftSleeve.visible = bras;
            ps.pushPose();
            ps.translate(ox - cam.x, oy - cam.y, oz - cam.z);
            repere(ps, p);
            int light = LightTexture.pack(lumiere >= 0 ? lumiere : lum, 0);
            m.renderToBuffer(ps, src.getBuffer(RenderType.entityTranslucent(mc.player.getSkinTextureLocation())), light,
                    OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
            ps.popPose();
            m.head.visible = m.hat.visible = true;
            m.body.visible = m.jacket.visible = true;
            m.rightArm.visible = m.leftArm.visible = m.rightSleeve.visible = m.leftSleeve.visible = true;
        }
    }

    // ---------------------------------------------------------------------------------------------- rendu
    private static int lumiereScene() {
        return Math.max(0, Cinematique.lumiereActeurs);
    }

    /** Opaques (après la rame) : goules et corps du joueur. */
    public static void opaque(PoseStack ps, Vec3 cam, float p) {
        if (!Cinematique.actif() || (GOULES.isEmpty() && corps == null)) {
            return;
        }
        MultiBufferSource.BufferSource src = Minecraft.getInstance().renderBuffers().bufferSource();
        double ox = PlateauRendu.ox, oy = PlateauRendu.oy, oz = PlateauRendu.oz;
        int lum = lumiereScene();
        // hors de vue : au-delà de la fin du brouillard, ou nettement derrière la caméra -> corps non dessiné (la horde dans
        // la brume de la course : on n'en voit que les yeux)
        float fin = com.mojang.blaze3d.systems.RenderSystem.getShaderFogEnd();
        org.joml.Vector3f vue = Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector();
        for (Goule g : GOULES) {
            double dx = ox + g.x - cam.x, dy = oy + g.y - cam.y, dz = oz + g.z - cam.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            boolean loin = fin > 0 && fin < 200 && d2 > (fin + 3.0) * (fin + 3.0);
            boolean derriere = d2 > 9 && dx * vue.x() + dy * vue.y() + dz * vue.z() < -2.5;
            g.dessiner(ps, src, ox, oy, oz, cam, p, lum, !loin && !derriere);
        }
        if (corps != null) {
            corps.dessiner(ps, src, ox, oy, oz, cam, p, lum);
        }
        src.endBatch();
    }

    /** Les yeux rouges de la horde : deux lueurs additives par goule, sans brume (on ne voit qu'elles dans le brouillard),
     *  cachées par ce qui est devant (test de profondeur), plus grosses de loin pour rester visibles. */
    private static void lueurs(PoseStack ps, Vec3 cam) {
        boolean aucune = true;                                      // rien à dessiner : on n'ouvre pas le tampon (un tampon
        for (Goule g : GOULES) {                                    // terminé sans être dessiné = « Clearing BufferBuilder
            if (g.rouges && g.yeuxOk && g.visible) {                // with unused batches » à chaque image dans le journal)
                aucune = false;
                break;
            }
        }
        if (aucune) {
            return;
        }
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        org.joml.Vector3f gauche = camera.getLeftVector(), haut = camera.getUpVector();
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        var b = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
        b.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        Matrix4f m = ps.last().pose();
        double ox = PlateauRendu.ox - cam.x, oy = PlateauRendu.oy - cam.y, oz = PlateauRendu.oz - cam.z;
        int n = 0;
        for (Goule g : GOULES) {
            if (!g.rouges || !g.yeuxOk || !g.visible) {
                continue;
            }
            for (int i = 0; i < 2; i++) {
                float x = (float) (g.yeuxPos[i * 3] + ox), y = (float) (g.yeuxPos[i * 3 + 1] + oy), z = (float) (g.yeuxPos[i * 3 + 2] + oz);
                float d = (float) Math.sqrt(x * x + y * y + z * z);
                float k = Math.max(1f, d / 10f);
                quadLueur(b, m, x, y, z, gauche, haut, 0.022f * k, 1f, 0.08f, 0.04f, 1f);   // un point rouge, rien autour
                n++;
            }
        }
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(b.end());
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    private static void quadLueur(com.mojang.blaze3d.vertex.BufferBuilder b, Matrix4f m, float x, float y, float z, org.joml.Vector3f g,
                                  org.joml.Vector3f h, float r, float cr, float cg, float cb, float a) {
        float gx = g.x() * r, gy = g.y() * r, gz = g.z() * r, hx = h.x() * r, hy = h.y() * r, hz = h.z() * r;
        b.vertex(m, x - gx - hx, y - gy - hy, z - gz - hz).color(cr, cg, cb, a).endVertex();
        b.vertex(m, x + gx - hx, y + gy - hy, z + gz - hz).color(cr, cg, cb, a).endVertex();
        b.vertex(m, x + gx + hx, y + gy + hy, z + gz + hz).color(cr, cg, cb, a).endVertex();
        b.vertex(m, x - gx + hx, y - gy + hy, z - gz + hz).color(cr, cg, cb, a).endVertex();
    }

    /** Décors plats (sang, fissures, lambeaux), avant les vitres de la rame (ils sont collés dehors, sur le verre). */
    public static void decors(PoseStack ps, Vec3 cam, float p) {
        if (Cinematique.actif() && !GOULES.isEmpty()) {
            lueurs(ps, cam);
        }
        if (!Cinematique.actif() || TRACES.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource src = Minecraft.getInstance().renderBuffers().bufferSource();
        double ox = PlateauRendu.ox, oy = PlateauRendu.oy, oz = PlateauRendu.oz;
        int lum = lumiereScene();
        ps.pushPose();
        ps.translate(ox - cam.x, oy - cam.y, oz - cam.z);
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        for (Trace t : TRACES) {
            float a = Mth.lerp(p, t.alphaO, t.alpha);
            if (a <= 0.01f) {
                continue;
            }
            Vec3 p1 = t.p1o.lerp(t.p1, p);
            Vec3 haut = t.dv, bas = t.dv.scale(-1);
            Vec3 c00 = t.p0.add(bas), c10 = p1.add(bas), c11 = p1.add(haut), c01 = t.p0.add(haut);
            Vec3 nor = p1.subtract(t.p0).cross(t.dv).normalize();
            int light = LightTexture.pack(t.lumiere >= 0 ? t.lumiere : lum, 0);
            VertexConsumer vc = src.getBuffer(RenderType.entityTranslucent(t.tex));
            sommet(vc, m, n, c00, t.u0, t.v1, a, light, nor);
            sommet(vc, m, n, c10, t.u1, t.v1, a, light, nor);
            sommet(vc, m, n, c11, t.u1, t.v0, a, light, nor);
            sommet(vc, m, n, c01, t.u0, t.v0, a, light, nor);
        }
        ps.popPose();
        src.endBatch();
    }

    private static void sommet(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p, float u, float v, float a, int light, Vec3 nor) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(1f, 1f, 1f, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(n, (float) nor.x, (float) nor.y, (float) nor.z).endVertex();
    }
}
