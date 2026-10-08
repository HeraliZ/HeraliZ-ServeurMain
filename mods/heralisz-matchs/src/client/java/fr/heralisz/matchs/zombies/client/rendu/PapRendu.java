package fr.heralisz.matchs.zombies.client.rendu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocLarge;
import fr.heralisz.matchs.zombies.contenu.bloc.PapBE;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesDecor.EtatPap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Pack-a-Punch façon CoD Black Ops : le CORPS est un modèle de bloc cuit dans les tronçons (coque verte, cadre d'acier,
 * emblème, batterie, enseigne : {@code models/block/zb_pap_corps.json}, rien à dessiner ici) ; ce rendu ne dessine que les
 * pièces animées du modèle {@code zbmodeles/pap.json} ({@code tools/matchs/zombies/gen_pap.py}) et l'arme :
 * <ul>
 *   <li>sans courant : rouleaux gris immobiles, aucune lueur ;</li>
 *   <li>sous tension : rouleaux bleu-blanc lumineux qui tournent doucement, fond de la fenêtre et enseigne allumés (elle
 *   grésille 1,5 s à l'allumage), halos, diode verte de la batterie ;</li>
 *   <li>amélioration : l'arme entre dans la fenêtre, les rouleaux s'emballent, la lumière pulse, la diode clignote (les
 *   étincelles sont des particules, voir {@link PapBE#tickClient}) ;</li>
 *   <li>arme offerte : éclat, l'arme améliorée ressort et flotte devant la fenêtre dans un halo à la couleur du niveau.</li>
 * </ul>
 * Aucune allocation par image. Repère : origine au centre bas de la case maîtresse, +z vers la salle.
 */
public class PapRendu implements BlockEntityRenderer<PapBE> {
    private static final RenderType HALO = ZbTypesRendu.lueur(HeraliszMatchs.id("textures/entity/zombies/halo.png"));
    private static final float[] PLEIN = {0, 0, 1, 1};
    private static final int BLEU = 0x8CCBFF;
    /** Couleurs des niveaux du Pack-a-Punch (lavande, violet, magenta), comme le nom de l'arme. */
    private static final int[] NIVEAUX = {0xE59BFF, 0xC77DFF, 0xFF5FD2};
    /** Repères du modèle (pixels) : centre de la fenêtre, profondeur des rouleaux, face du cadre. */
    private static final float Y_FEN = 19.8F, Z_ROULEAUX = 1F, Z_CADRE = 6.8F;
    private static ModelesZb.Tex tex;

    public PapRendu(BlockEntityRendererProvider.Context ctx) {
    }

    private static float doux(float u) {
        u = Mth.clamp(u, 0, 1);
        return u * u * (3 - 2 * u);
    }

    /** Grésillement d'allumage (néon) : 1,5 s de clignotements irréguliers, puis allumé. */
    private static float gresille(float t) {
        if (t >= 30) return 1;
        int k = (int) (t * 0.8F);
        int h = (k * 1103515245 + 12345) >>> 16;
        float on = (h & 7) < 3 ? 0.12F : 0.95F;
        return on * (0.5F + 0.5F * t / 30F);
    }

    @Override
    public void render(PapBE be, float partiel, PoseStack ps, MultiBufferSource buf, int lumiere, int overlay) {
        ModeleZb m = ModelesZb.get("pap");
        if (m == null || be.getLevel() == null) return;
        if (tex == null) tex = ModelesZb.tex(m.texture);
        Direction f = be.getBlockState().hasProperty(BlocLarge.FACING) ? be.getBlockState().getValue(BlocLarge.FACING) : Direction.NORTH;
        EtatPap etat = be.etat();
        boolean on = be.alimente();
        float age = (be.getLevel().getGameTime() % 24000L) + partiel;
        float t = be.ecoule(partiel);
        float gr = on ? gresille(be.depuisAllumage(partiel)) : 0;

        // --- paramètres d'animation (degrés, intensités 0..1)
        float rouleaux = 0, lumRouleaux = gr, halo = 0.75F * gr, diode = gr, arme = 0, eclat = 0;
        switch (etat) {
            case PRETE -> rouleaux = age * 3F;
            case AMELIORATION -> {
                // accélération continue (t² puis vitesse de croisière de 45°/tick)
                rouleaux = age * 3F + (t < 25 ? 0.9F * t * t : 562.5F + (t - 25) * 45F);
                float p = 0.5F + 0.5F * Mth.sin(t * 0.85F);
                lumRouleaux = 0.72F + 0.28F * p;
                halo = 0.55F + 0.45F * p;
                diode = (int) (age / 3) % 2 == 0 ? 1F : 0.15F;
                arme = t < 14 ? 1 : 0;
            }
            case OFFERTE -> {
                rouleaux = age * 3F + (t < 30 ? 30F * t - 0.5F * t * t : 450F);
                eclat = t < 12 ? 1 - t / 12F : 0;
                lumRouleaux = Math.min(1, 0.85F + eclat);
                halo = Math.min(1, 0.75F + eclat);
                arme = 1;
            }
            default -> {
            }
        }

        ps.pushPose();
        ps.translate(0.5F, 0, 0.5F);
        ps.mulPose(Axis.YP.rotationDegrees(-f.toYRot()));
        int teinte = on ? 0xFFFFFFFF : 0xFF9A9A9A;
        // deux passes (base puis lueur) : un tampon non fixe ne doit jamais être entrelacé avec un autre
        for (int passe = 0; passe < (on ? 2 : 1); passe++) {
            VertexConsumer vc = buf.getBuffer(passe == 0 ? tex.base() : tex.lueur());
            for (ModeleZb.Groupe g : m.groupes()) {
                float k;
                switch (g.nom) {
                    case "rouleau_1", "rouleau_2", "rouleau_3" -> k = lumRouleaux;
                    case "lueur_fenetre" -> k = lumRouleaux;
                    case "halo_fenetre" -> k = halo;
                    case "lueur_enseigne" -> k = gr;
                    case "halo_enseigne" -> k = 0.8F * gr;
                    case "voyant" -> k = diode;
                    default -> k = 1;
                }
                boolean rouleau = g.nom.startsWith("rouleau_");
                if (passe == 0 && !rouleau) continue;
                if (passe == 1 && k <= 0.01F) continue;
                ps.pushPose();
                if (rouleau) {
                    // déphasage par rouleau : les rainures ne défilent pas en bloc
                    float phase = g.nom.charAt(8) * 37F;
                    ModeleZb.tourner(ps, g, Axis.XP, -(rouleaux + phase));
                }
                if (passe == 0) ModeleZb.dessiner(g, ModeleZb.BASE, ps, vc, lumiere, overlay, teinte);
                else ModeleZb.dessiner(g, ModeleZb.LUEUR, ps, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, Geo.additif(k, 0xFFFFFF));
                ps.popPose();
            }
        }
        // --- lumière bleue au sol devant la machine
        if (on) {
            VertexConsumer vh = buf.getBuffer(HALO);
            ps.pushPose();
            ps.translate(0, 0.02F, 0.95F);
            ps.mulPose(Axis.XP.rotationDegrees(-90));
            Geo.plan(ps, vh, -1.35F, -0.75F, 1.35F, 0.75F, PLEIN, 0xF000F0, OverlayTexture.NO_OVERLAY, Geo.additif(0.32F * halo, BLEU));
            ps.popPose();
        }
        // --- étincelles : traînées orangées face à la caméra (réserve de PapBE, mise à jour par son ticker client)
        if (be.nEtincelles > 0) etincelles(be, f, partiel, ps, buf.getBuffer(HALO));
        // --- l'arme : elle entre dans la fenêtre (amélioration), puis ressort et flotte (offerte)
        ItemStack a = be.arme();
        if (!a.isEmpty() && arme > 0.01F) {
            ps.pushPose();
            float z, y = Y_FEN, ech;
            boolean offerte = etat == EtatPap.OFFERTE;
            if (offerte) {
                float s = doux(t / 14F);
                z = Z_ROULEAUX + 2 + (11.5F - Z_ROULEAUX - 2) * s;
                ech = 0.5F + 0.16F * s;
                float flotte = doux((t - 12) / 10F);
                y += 0.6F * flotte * Mth.sin(age * 0.12F);
                int niveau = Math.max(1, Math.min(NIVEAUX.length, ZombiesApi.niveauPap(a)));
                VertexConsumer vh = buf.getBuffer(HALO);
                ps.pushPose();
                ps.translate(0, y / 16F, (z - 0.8F) / 16F);
                Geo.plan(ps, vh, -0.75F, -0.45F, 0.75F, 0.45F, PLEIN, 0xF000F0, OverlayTexture.NO_OVERLAY,
                        Geo.additif(0.5F + 0.5F * eclat, NIVEAUX[niveau - 1]));
                ps.popPose();
            } else {
                float s = doux(t / 14F);
                z = 13F - (13F - (Z_ROULEAUX + 2)) * s;
                ech = 0.62F - 0.12F * s;
            }
            ps.translate(0, y / 16F, z / 16F);
            if (offerte) ps.mulPose(Axis.YP.rotationDegrees(18 * Mth.sin(age * 0.05F) * doux((t - 12) / 10F)));
            ps.scale(ech, ech, ech);
            // dans la fenêtre l'arme passe derrière le cadre : elle reste éclairée par les rouleaux
            int l = offerte || z < Z_CADRE ? 0xF000F0 : lumiere;
            Minecraft.getInstance().getItemRenderer().renderStatic(a, ItemDisplayContext.FIXED, l, OverlayTexture.NO_OVERLAY, ps, buf,
                    be.getLevel(), (int) be.getBlockPos().asLong());
            ps.popPose();
        }
        ps.popPose();
    }

    /**
     * Étincelles en traînées : de la position un tick plus tôt (vitesse x 2,2) à la tête, largeur face à la caméra (position
     * de la caméra ramenée dans le repère de la machine), deux couches additives (halo orangé, cœur clair). Sans allocation.
     */
    private static void etincelles(PapBE be, Direction f, float partiel, PoseStack ps, VertexConsumer vc) {
        net.minecraft.world.phys.Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double rx = cam.x - (be.getBlockPos().getX() + 0.5), ry = cam.y - be.getBlockPos().getY(), rz = cam.z - (be.getBlockPos().getZ() + 0.5);
        double a = Math.toRadians(f.toYRot()), c = Math.cos(a), s = Math.sin(a);
        float camX = (float) ((rx * c + rz * s) * 16), camY = (float) (ry * 16), camZ = (float) ((-rx * s + rz * c) * 16);
        PoseStack.Pose pose = ps.last();
        org.joml.Matrix4f m = pose.pose();
        org.joml.Matrix3f nm = pose.normal();
        for (int i = 0; i < be.nEtincelles; i++) {
            float k = (be.eAge[i] + partiel) / be.eVie[i];
            if (k >= 1) continue;
            float hx = be.eX[i] + be.eVx[i] * partiel, hy = be.eY[i] + be.eVy[i] * partiel, hz = be.eZ[i] + be.eVz[i] * partiel;
            float tx = hx - be.eVx[i] * 2.2F, ty = hy - be.eVy[i] * 2.2F, tz = hz - be.eVz[i] * 2.2F;
            float dx = hx - tx, dy = hy - ty, dz = hz - tz;
            float vx = camX - hx, vy = camY - hy, vz = camZ - hz;
            float sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
            float n = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (n < 1.0E-5F) continue;
            float vie = 1 - k;
            for (int couche = 0; couche < 2; couche++) {
                float larg = (couche == 0 ? 0.55F : 0.18F) / n;
                int coul = couche == 0 ? Geo.additif(0.75F * vie, 0xFF9A30) : Geo.additif(vie, 0xFFF0C8);
                trainee(m, nm, vc, tx, ty, tz, hx, hy, hz, sx * larg, sy * larg, sz * larg, coul);
            }
        }
    }

    private static void trainee(org.joml.Matrix4f m, org.joml.Matrix3f nm, VertexConsumer vc, float tx, float ty, float tz, float hx, float hy,
                                float hz, float ox, float oy, float oz, int argb) {
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        final float u = 1 / 16F;
        sommet(m, nm, vc, (tx + ox) * u, (ty + oy) * u, (tz + oz) * u, 0, 0, r, g, b);
        sommet(m, nm, vc, (tx - ox) * u, (ty - oy) * u, (tz - oz) * u, 0, 1, r, g, b);
        sommet(m, nm, vc, (hx - ox) * u, (hy - oy) * u, (hz - oz) * u, 1, 1, r, g, b);
        sommet(m, nm, vc, (hx + ox) * u, (hy + oy) * u, (hz + oz) * u, 1, 0, r, g, b);
    }

    private static void sommet(org.joml.Matrix4f m, org.joml.Matrix3f nm, VertexConsumer vc, float x, float y, float z, float uu, float vv, int r, int g,
                               int b) {
        vc.vertex(m, x, y, z).color(r, g, b, 255).uv(uu, vv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nm, 0, 1, 0).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(PapBE be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 80;
    }
}
