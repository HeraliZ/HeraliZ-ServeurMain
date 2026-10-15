package fr.heralisz.matchs.duels.client.bombe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.duels.bombe.BombeDuel;
import fr.heralisz.matchs.duels.bombe.OrdinateurDuel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Rendu de l'ordinateur d'amorçage posé (modèles GeckoLib {@code geo/duel_ordinateur[_ferme].geo.json}, une texture
 * d'écran par étape et par site, outils/bombe/modeles.py) : fermé au sol, ouvert pendant l'amorçage, allumé une fois la
 * bombe amorcée, dans les mains du défenseur pendant le piratage ({@link PoseOrdinateur}), écran vert après le
 * désamorçage. Par-dessus : l'écran en direct ({@link EcranVif}) et le câble jusqu'à la prise de la bombe ({@link Cable}).
 */
public final class OrdinateurRendu extends GeoEntityRenderer<OrdinateurDuel> {
    private final PoseOrdinateur pose = PoseOrdinateur.nouvelle();

    public OrdinateurRendu(EntityRendererProvider.Context ctx) {
        super(ctx, new Modele());
        this.shadowRadius = 0.35f;
        addRenderLayer(new CalqueLumineux<>(this));
    }

    /** Dans les mains du pirate, l'ordinateur est loin de sa position (aux pieds) : toujours dessiné. */
    @Override
    public boolean shouldRender(OrdinateurDuel o, Frustum frustum, double camX, double camY, double camZ) {
        return o.etat() == OrdinateurDuel.PIRATAGE || super.shouldRender(o, frustum, camX, camY, camZ);
    }

    @Override
    public void render(OrdinateurDuel o, float lacet, float partial, PoseStack ps, MultiBufferSource tampons, int lumiere) {
        this.pose.calculer(o, partial);
        double ex = Mth.lerp(partial, o.xo, o.getX()), ey = Mth.lerp(partial, o.yo, o.getY()), ez = Mth.lerp(partial, o.zo, o.getZ());
        this.shadowRadius = this.pose.tenu ? 0f : 0.35f;
        ps.pushPose();
        ps.translate(this.pose.x - ex, this.pose.y - ey, this.pose.z - ez);
        super.render(o, lacet, partial, ps, tampons, lumiere);
        if (this.entityRenderDispatcher.distanceToSqr(o) < EcranVif.DISTANCE * EcranVif.DISTANCE) {
            ps.pushPose();
            orienter(ps);
            ps.translate(0, 0.01f, 0);
            EcranVif.dessiner(o, ps, tampons, partial);
            ps.popPose();
        }
        ps.popPose();
        cable(o, partial, ps, tampons, lumiere, ex, ey, ez);
    }

    @Override
    protected void applyRotations(OrdinateurDuel o, PoseStack ps, float age, float lacet, float partial) {
        orienter(ps);
    }

    /** Orientation de la pose calculée pour cette image (la même pour le modèle, l'écran en direct et le câble). */
    private void orienter(PoseStack ps) {
        ps.mulPose(Axis.YP.rotationDegrees(180f - this.pose.lacet));
        if (this.pose.inclinaison != 0f) ps.mulPose(Axis.XP.rotationDegrees(this.pose.inclinaison));
    }

    /**
     * Câble de la pointe de l'ordinateur à la prise de la bombe reliée (positions relatives à l'entité dessinée) : il
     * sort de la pointe sur le côté, descend au sol, passe derrière l'ordinateur (jamais dessous), longe le sol jusqu'au
     * pied du module et remonte à la prise ; tenu en main, il pend des mains jusqu'au sol.
     */
    private void cable(OrdinateurDuel o, float partial, PoseStack ps, MultiBufferSource tampons, int lumiere, double ex, double ey, double ez) {
        if (o.etat() == OrdinateurDuel.FERME) return;
        Entity e = o.level().getEntity(o.bombeId());
        if (!(e instanceof BombeDuel b) || b.etat() == BombeDuel.EXPLOSEE) return;
        PoseOrdinateur p = this.pose;
        Vec3 a = p.point(OrdinateurDuel.PRISE_X, OrdinateurDuel.PRISE_Y, OrdinateurDuel.PRISE_Z);
        // prise de la bombe (même rotation que son rendu) et direction vers laquelle regarde son module
        double bx = Mth.lerp(partial, b.xo, b.getX()), by = Mth.lerp(partial, b.yo, b.getY()), bz = Mth.lerp(partial, b.zo, b.getZ());
        double t = Math.toRadians(180.0 - b.getYRot());
        double k = BombeDuel.ECHELLE / 16.0;
        double px = BombeDuel.PRISE_X * k, py = BombeDuel.PRISE_Y * k + 0.01, pz = BombeDuel.PRISE_Z * k;
        double qx = bx + px * Math.cos(t) + pz * Math.sin(t), qy = by + py, qz = bz - px * Math.sin(t) + pz * Math.cos(t);
        double psi = Math.toRadians(b.getYRot());
        double fx = -Math.sin(psi), fz = Math.cos(psi);
        double sol = p.sol + 0.02;
        double pied1x = qx + fx * 0.14 * BombeDuel.ECHELLE, pied1z = qz + fz * 0.14 * BombeDuel.ECHELLE;
        double[] pts;
        if (p.tenu) {
            // des mains au sol, un peu vers la bombe, puis au pied du module
            double dx = pied1x - a.x, dz = pied1z - a.z, d = Math.max(1e-3, Math.sqrt(dx * dx + dz * dz));
            pts = new double[]{a.x, a.y, a.z, a.x + dx / d * 0.2, sol, a.z + dz / d * 0.2, pied1x, sol, pied1z, qx, qy, qz};
        } else {
            // côté de la pointe (axe X du modèle) et arrière de l'ordinateur (axe Z), à plat
            Vec3 o0 = p.point(0, 0, 0), cote = p.point(16, 0, 0).subtract(o0), dos = p.point(0, 0, 16).subtract(o0);
            double cl = Math.max(1e-3, Math.sqrt(cote.x * cote.x + cote.z * cote.z)), dl = Math.max(1e-3, Math.sqrt(dos.x * dos.x + dos.z * dos.z));
            double s1x = a.x + cote.x / cl * 0.14, s1z = a.z + cote.z / cl * 0.14;
            double rx = pied1x - s1x, rz = pied1z - s1z, recul = Math.min(0.45, Math.sqrt(rx * rx + rz * rz) * 0.5);
            double s2x = s1x + dos.x / dl * recul, s2z = s1z + dos.z / dl * recul;
            pts = new double[]{a.x, a.y, a.z, s1x, sol, s1z, s2x, sol, s2z, pied1x, sol, pied1z, qx, qy, qz};
        }
        for (int i = 0; i < pts.length; i += 3) {
            pts[i] -= ex;
            pts[i + 1] -= ey;
            pts[i + 2] -= ez;
        }
        Cable.dessiner(ps, tampons, pts, p.sol - ey, lumiere);
    }

    @Override
    public RenderType getRenderType(OrdinateurDuel o, ResourceLocation texture, MultiBufferSource tampons, float partial) {
        return TypesRendu.solide(texture);
    }

    @Override
    public boolean shouldShowName(OrdinateurDuel o) {
        return false;
    }

    /** Modèle : ouvert ou fermé, texture de l'écran selon l'étape et le site. */
    static final class Modele extends GeoModel<OrdinateurDuel> {
        private static final ResourceLocation GEO_OUVERT = HeraliszMatchs.id("geo/duel_ordinateur.geo.json"),
                GEO_FERME = HeraliszMatchs.id("geo/duel_ordinateur_ferme.geo.json");
        private static final ResourceLocation ANIMATIONS = HeraliszMatchs.id("animations/duel_bombe.animation.json");
        private static final ResourceLocation FERME = HeraliszMatchs.id("textures/entity/duel_ordinateur_ferme.png");
        /** [étape][site] : amorcage, amorcee, piratage, desamorcee × A, B. */
        private static final ResourceLocation[][] ECRANS = new ResourceLocation[5][2];

        static {
            String[] noms = {null, "amorcage", "amorcee", "piratage", "desamorcee"};
            for (int e = 1; e < noms.length; e++) {
                ECRANS[e][0] = HeraliszMatchs.id("textures/entity/duel_ordinateur_" + noms[e] + "_a.png");
                ECRANS[e][1] = HeraliszMatchs.id("textures/entity/duel_ordinateur_" + noms[e] + "_b.png");
            }
        }

        @Override
        public ResourceLocation getModelResource(OrdinateurDuel o) {
            return o.etat() == OrdinateurDuel.FERME ? GEO_FERME : GEO_OUVERT;
        }

        @Override
        public ResourceLocation getTextureResource(OrdinateurDuel o) {
            int e = o.etat();
            if (e <= OrdinateurDuel.FERME || e >= ECRANS.length) return TextureHD.get(FERME);
            return TextureHD.get(ECRANS[e]["B".equals(o.site()) ? 1 : 0]);
        }

        @Override
        public ResourceLocation getAnimationResource(OrdinateurDuel o) {
            return ANIMATIONS;
        }
    }
}
