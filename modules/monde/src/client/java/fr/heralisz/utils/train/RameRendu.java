package fr.heralisz.utils.train;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * La rame : chaque véhicule posé sur ses deux bogies (Horaire.placer), ses bogies orientés chacun sur la voie, les
 * essieux qui tournent d'autant que le train avance, les portes du côté du quai qui coulissent à l'arrêt, les
 * portes d'intercirculation qui s'ouvrent quand quelqu'un s'en approche (Voyageurs). Tout est dans des tampons du GPU
 * (ModeleTrain) : un appel de dessin par groupe, la lumière du monde passée au shader (uniforme « Lumiere »).
 */
public final class RameRendu {
    static final float R_ROUE = 0.46f;
    private static final Horaire.Placement PL = new Horaire.Placement();
    private static final Matrix4f M = new Matrix4f();

    private RameRendu() {}

    static void vider() {
        ModeleTrain.vider();
    }

    /** Ouverture (0..1) de chaque porte pour cet instant : portes latérales à quai (mouvement adouci au départ et à
     *  l'arrivée), portes intérieures animées dès qu'un voyageur approche (Voyageurs.animPorte). */
    static float porte(int vehicule, int porte, Horaire.Etat e, int coteQuai) {
        if (porte >= 100) return Voyageurs.animPorte(vehicule, porte - 100);
        int cote = porte % 2 == 0 ? -1 : 1;                 // portes paires à gauche, impaires à droite
        return cote == coteQuai ? (float) Horaire.portes(e) : 0f;
    }

    /** Vitre k (0..13, ModeleTrain.Groupe.fenetres) de la voiture veh cassée ? */
    private static boolean cassee(int veh, int k) {
        return TrainClient.vitresCassees.contains(Blindage.cle(veh, k >= 7 ? 1 : -1, k % 7));
    }

    private static final net.minecraft.resources.ResourceLocation TEX_SOUFFLET =
            new net.minecraft.resources.ResourceLocation(fr.heralisz.utils.HeraliszUtils.MODID, "textures/train/soufflet.png");
    private static final Horaire.Placement PA = new Horaire.Placement(), PB = new Horaire.Placement();
    private static com.mojang.blaze3d.vertex.BufferBuilder bbSoufflet;
    private static VertexBuffer vboSoufflet;
    /** Gabarit du soufflet (tools/gen_train.py : SOUF_W, SOUF_Y0, SOUF_Y1) et bout du cadre fixé à chaque caisse. */
    private static final float SW = 1.12f, SY0 = Reseau.PLANCHER - 0.16f, SY1 = Reseau.PLANCHER + 2.6f + 0.34f, SX = Voyageurs.VL + 0.14f;

    private static void sommet(com.mojang.blaze3d.vertex.BufferBuilder bb, Vec3 p, Vec3 cam, float u, float v, float nx, float ny, float nz) {
        bb.vertex((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z)).uv(u, v).color(255, 255, 255, 255)
                .normal(nx, ny, nz).endVertex();
    }

    /** Soufflets entre deux voitures voisines : un tube de caoutchouc plissé (parois, toit) et la passerelle, tendus
     *  entre les cadres des deux caisses, recalculés à chaque image : fermés même en courbe, on ne voit plus dehors. */
    private static void soufflets(PoseStack ps, Matrix4f proj, Vec3 cam, Horaire h, Horaire.Etat e, ClientLevel lvl,
                                  net.minecraft.client.renderer.ShaderInstance sh, int rame) {
        if (bbSoufflet == null) bbSoufflet = new com.mojang.blaze3d.vertex.BufferBuilder(8192);
        var bb = bbSoufflet;
        bb.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        int n = 0, bloc = 0, ciel = 0;
        for (int i = 1; i + 1 < Horaire.N; i++) {
            int veh = rame * Horaire.N + i;
            if (!Voyageurs.voiture(veh) || !Voyageurs.voiture(veh + 1)) continue;
            h.placer(veh, e.s(), PA);
            h.placer(veh + 1, e.s(), PB);
            if (Math.abs(PA.x - cam.x) + Math.abs(PA.z - cam.z) > 160) continue;
            BlockPos lp = BlockPos.containing(Voyageurs.versMonde(PA, -SX, 2.0, 0));
            bloc = Math.max(bloc, lvl.getBrightness(LightLayer.BLOCK, lp));
            ciel = Math.max(ciel, lvl.getBrightness(LightLayer.SKY, lp));
            Vec3 a00 = Voyageurs.versMonde(PA, -SX, SY0, -SW), a01 = Voyageurs.versMonde(PA, -SX, SY1, -SW);
            Vec3 a10 = Voyageurs.versMonde(PA, -SX, SY0, SW), a11 = Voyageurs.versMonde(PA, -SX, SY1, SW);
            Vec3 b00 = Voyageurs.versMonde(PB, SX, SY0, -SW), b01 = Voyageurs.versMonde(PB, SX, SY1, -SW);
            Vec3 b10 = Voyageurs.versMonde(PB, SX, SY0, SW), b11 = Voyageurs.versMonde(PB, SX, SY1, SW);
            float L = (float) a00.distanceTo(b00) * 2f;                              // plis : 2 par bloc
            // paroi gauche, paroi droite, toit (dessinés des deux côtés : face au voyageur et vus de dehors)
            sommet(bb, a00, cam, 0, 1, 0, 0, -1); sommet(bb, a01, cam, 0, 0, 0, 0, -1);
            sommet(bb, b01, cam, L, 0, 0, 0, -1); sommet(bb, b00, cam, L, 1, 0, 0, -1);
            sommet(bb, a10, cam, 0, 1, 0, 0, 1); sommet(bb, b10, cam, L, 1, 0, 0, 1);
            sommet(bb, b11, cam, L, 0, 0, 0, 1); sommet(bb, a11, cam, 0, 0, 0, 0, 1);
            sommet(bb, a01, cam, 0, 0, 0, 1, 0); sommet(bb, a11, cam, 0, 1, 0, 1, 0);
            sommet(bb, b11, cam, L, 1, 0, 1, 0); sommet(bb, b01, cam, L, 0, 0, 1, 0);
            // passerelle (tôle striée) au niveau du plancher, sous le tube
            Vec3 p00 = Voyageurs.versMonde(PA, -SX, Reseau.PLANCHER - 0.01, -SW), p10 = Voyageurs.versMonde(PA, -SX, Reseau.PLANCHER - 0.01, SW);
            Vec3 q00 = Voyageurs.versMonde(PB, SX, Reseau.PLANCHER - 0.01, -SW), q10 = Voyageurs.versMonde(PB, SX, Reseau.PLANCHER - 0.01, SW);
            sommet(bb, p00, cam, 0, 1, 0, 1, 0); sommet(bb, p10, cam, 0, 0.98f, 0, 1, 0);
            sommet(bb, q10, cam, L, 0.98f, 0, 1, 0); sommet(bb, q00, cam, L, 1, 0, 1, 0);
            sommet(bb, a00, cam, 0, 1, 0, -1, 0); sommet(bb, b00, cam, L, 1, 0, -1, 0);
            sommet(bb, b10, cam, L, 1, 0, -1, 0); sommet(bb, a10, cam, 0, 1, 0, -1, 0);
            n++;
        }
        var rb = bb.end();
        if (n == 0) {
            rb.release();
            return;
        }
        if (vboSoufflet == null) vboSoufflet = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        sh.safeGetUniform("Lumiere").set((float) Math.max(bloc, 9), (float) ciel);
        RenderSystem.disableCull();
        ModeleTrain.lier(TEX_SOUFFLET);
        vboSoufflet.bind();
        vboSoufflet.upload(rb);
        vboSoufflet.drawWithShader(ps.last().pose(), proj, sh);
        RenderSystem.enableCull();
    }

    /** Faisceau des phares : additif, d'autant plus visible qu'il fait sombre (nuit, tunnel). */
    private static void faisceau(ModeleTrain.Groupe g, Matrix4f m, Matrix4f proj, net.minecraft.client.renderer.ShaderInstance sh,
                                 ClientLevel lvl, int ciel, float partial) {
        float jour = lvl.getSkyDarken(partial);                              // 1 en plein jour, ~0,2 la nuit
        float a = Math.max(0f, Math.min(1f, Math.max((1f - jour) * 1.25f, (15 - ciel) / 15f)));
        if (a < 0.05f) return;
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderColor(1f, 1f, 1f, a);
        sh.safeGetUniform("Lumiere").set(15f, 15f);
        ModeleTrain.lier(g.texture);
        g.vbo.bind();
        g.vbo.drawWithShader(m, proj, sh);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
    }

    static void dessiner(PoseStack ps, Matrix4f proj, Vec3 cam, Frustum frustum, float partial, boolean vitres) {
        Horaire h = Horaire.get();
        ClientLevel lvl = Minecraft.getInstance().level;
        if (h == null || lvl == null || TrainClient.shTrain == null) return;
        double portee = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0 + 32;
        var sh = TrainClient.shTrain;
        RenderSystem.setShader(() -> sh);
        Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
        RenderSystem.enableDepthTest();
        if (vitres) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
        } else {
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
        }
        var lum = sh.safeGetUniform("Lumiere");
        if (!vitres) Voyageurs.animer();
        for (int rame = 0; rame < Horaire.RAMES; rame++) {
        Horaire.Etat e = h.etatRame(TrainClient.temps(partial), rame);
        int coteQuai = e.aQuai() >= 0 ? h.reseau.gares[e.aQuai()].cote() : 0;
        for (int i = 0; i < Horaire.N; i++) {
            int veh = rame * Horaire.N + i;
            h.placer(veh, e.s(), PL);
            Horaire.Vehicule v = Horaire.RAME[i];
            double dx = PL.x - cam.x, dy = PL.y - cam.y, dz = PL.z - cam.z;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > portee) continue;
            AABB boite = new AABB(PL.x - 13, PL.y - 1, PL.z - 13, PL.x + 13, PL.y + 6, PL.z + 13);
            if (!frustum.isVisible(boite)) continue;
            BlockPos lp = BlockPos.containing(PL.x, PL.y + 2.2, PL.z);
            int bloc = lvl.getBrightness(LightLayer.BLOCK, lp), ciel = lvl.getBrightness(LightLayer.SKY, lp);
            // caisse
            M.set(ps.last().pose()).translate((float) dx, (float) dy, (float) dz).rotateY((float) -PL.cap).rotateZ((float) PL.pente);
            ModeleTrain caisse = ModeleTrain.get(Horaire.MODELES[i]);
            for (ModeleTrain.Groupe g : caisse.groupes) {
                if (g.nature == ModeleTrain.FAISCEAU) {                              // phares : cônes additifs, la nuit
                    if (vitres && i == 0) faisceau(g, M, proj, sh, lvl, ciel, partial);
                    continue;
                }
                boolean verre = g.nature == ModeleTrain.VERRE;
                if (verre != vitres) continue;
                if (g.nature == ModeleTrain.INTERIEUR && d > 72) continue;
                if (g.nom.equals("feux") && i != Horaire.RAME.length - 1) continue;      // feux de fin de convoi
                if (g.nom.equals("soufflet_av") && !Voyageurs.voiture(veh - 1)) continue;   // pas de soufflet contre la loco
                if (g.nom.equals("soufflet_ar") && !Voyageurs.voiture(veh + 1)) continue;   // ni au bout du train
                switch (g.nature) {
                    case ModeleTrain.INTERIEUR -> lum.set((float) Math.max(bloc, 12), (float) ciel);
                    case ModeleTrain.LUMIERE -> lum.set(15f, 15f);
                    default -> lum.set((float) bloc, (float) ciel);
                }
                Matrix4f mg = M;
                if (g.porte >= 0) {
                    float o = porte(veh, g.porte, e, coteQuai);
                    if (o > 0 && g.porte < 100) {                            // porte latérale louvoyante : sort de
                        float sort = Voyageurs.lisse(o / 0.22f), glisse = Voyageurs.lisse((o - 0.22f) / 0.78f);   // 7 cm puis glisse
                        float cote = g.porte % 2 == 0 ? -1f : 1f;
                        mg = new Matrix4f(M).translate(g.gx * glisse, g.gy * glisse, g.gz * glisse + cote * 0.07f * sort);
                    } else if (o > 0) {
                        mg = new Matrix4f(M).translate(g.gx * o, g.gy * o, g.gz * o);
                    }
                }
                ModeleTrain.lier(g.texture);
                if (g.fenetres != null && TrainClient.vitresCassees(veh)) {      // vitres cassées par des balles : pas dessinées
                    for (int k = 0; k < g.fenetres.length; k++) {
                        if (g.fenetres[k] == null || cassee(veh, k)) continue;
                        g.fenetres[k].bind();
                        g.fenetres[k].drawWithShader(mg, proj, sh);
                    }
                    continue;
                }
                g.vbo.bind();
                g.vbo.drawWithShader(mg, proj, sh);
            }
            if (vitres) continue;
            // bogies et essieux
            lum.set((float) bloc, (float) ciel);
            ModeleTrain bogie = ModeleTrain.get("bogie_" + v.modele), essieu = ModeleTrain.get("essieu");
            float angle = (float) (PL.s / R_ROUE);
            double[][] bs = {PL.bogieAvant, PL.bogieArriere};
            double[] ecart = v == Horaire.Vehicule.LOCO ? new double[]{-1.9, 0, 1.9} : new double[]{-1.25, 1.25};
            for (double[] b : bs) {
                Matrix4f mb = new Matrix4f(ps.last().pose()).translate((float) (b[0] - cam.x), (float) (b[1] - cam.y), (float) (b[2] - cam.z))
                        .rotateY((float) -b[3]).rotateZ((float) PL.pente);
                for (ModeleTrain.Groupe g : bogie.groupes) {
                    ModeleTrain.lier(g.texture);
                    g.vbo.bind();
                    g.vbo.drawWithShader(mb, proj, sh);
                }
                if (d > 64) continue;
                for (double ex : ecart) {
                    Matrix4f me = new Matrix4f(mb).translate((float) ex, R_ROUE, 0f).rotateZ(-angle);
                    for (ModeleTrain.Groupe g : essieu.groupes) {
                        ModeleTrain.lier(g.texture);
                        g.vbo.bind();
                        g.vbo.drawWithShader(me, proj, sh);
                    }
                }
            }
        }
        if (!vitres) soufflets(ps, proj, cam, h, e, lvl, sh, rame);
        }
        VertexBuffer.unbind();
        Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer();
        if (vitres) {
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
        }
    }
}
