package fr.heraliz.airstrike.client.helico;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heraliz.airstrike.client.model.GeoModel;
import fr.heraliz.airstrike.client.model.GeoPart;
import fr.heraliz.airstrike.client.model.ModModels;
import fr.heraliz.airstrike.helico.HelicoEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Dessin de l'hélicoptère de patrouille (modèle geo/helico.geo, outils/modele/helico.py) : rotors qui tournent (flou
 * transparent), tourelle du menton tournée vers la cible, barillet qui tourne pendant les rafales, feux de position,
 * gyrophare qui clignote, phare. Le même dessin sert à l'hélicoptère lointain (HelicoClient, au-delà de la distance de
 * vue). Orientation : YP(180 - lacet), puis tangage et roulis autour du milieu de la cabine ({@link #PIVOT}).
 */
public class HelicoRendu extends EntityRenderer<HelicoEntity> {
    public static final ResourceLocation TEXTURE = ModModels.texture("helico");
    /** Hauteur (blocs) du point autour duquel il penche. */
    public static final float PIVOT = 1.9f;

    public HelicoRendu(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(HelicoEntity e) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(HelicoEntity e, Frustum f, double x, double y, double z) {
        return e.shouldRenderAtSqrDistance(e.distanceToSqr(x, y, z)) && f.isVisible(e.getBoundingBoxForCulling());
    }

    @Override
    public void render(HelicoEntity e, float lacetE, float pt, PoseStack ps, MultiBufferSource buf, int lumiere) {
        float lacet = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float tangage = Mth.lerp(pt, e.tangageO, e.tangage);
        float roulis = Mth.lerp(pt, e.roulisO, e.roulis);
        float tl = Mth.rotLerp(pt, e.tourelleLacetO, e.tourelleLacet);
        float ct = Mth.lerp(pt, e.canonTangageO, e.canonTangage);
        float ba = Mth.lerp(pt, e.barilletO, e.barillet);
        dessiner(ps, buf, lumiere, lacet, tangage, roulis, e.tickCount + pt, tl, ct, ba, e.enChute());
        super.render(e, lacetE, pt, ps, buf, lumiere);
    }

    /** Orientation du modèle (ps à la position de l'hélicoptère). */
    public static void orienter(PoseStack ps, float lacet, float tangage, float roulis) {
        ps.mulPose(Axis.YP.rotationDegrees(180f - lacet));
        ps.translate(0, PIVOT, 0);
        ps.mulPose(Axis.XP.rotationDegrees(-tangage));
        ps.mulPose(Axis.ZP.rotationDegrees(-roulis));
        ps.translate(0, -PIVOT, 0);
    }

    public static void dessiner(PoseStack ps, MultiBufferSource buf, int lumiere, float lacet, float tangage, float roulis,
                                float temps, float tourelleLacet, float canonTangage, float barillet, boolean chute) {
        GeoModel m = ModModels.helico();
        ps.pushPose();
        orienter(ps, lacet, tangage, roulis);
        ps.scale(-1f, -1f, 1f);
        // rotors : rotation dans le repère du jeu (x et y retournés : yRot = -angle naturel)
        float vit = chute ? 0.55f : 0.95f;
        float rot = temps * vit;
        pose(m.part("corps/rotor"), 0, -rot, 0);
        pose(m.part("flou/rotor_flou"), 0, -rot * 0.31f, 0);
        pose(m.part("corps/queue_rotor"), -temps * (chute ? 0.6f : 2.4f), 0, 0);
        pose(m.part("flou/queue_flou"), -temps * 0.7f, 0, 0);
        pose(m.part("corps/tourelle"), 0, (float) -Math.toRadians(tourelleLacet), 0);
        pose(m.part("corps/tourelle/canon"), (float) -Math.toRadians(canonTangage), 0, 0);
        pose(m.part("corps/tourelle/canon/barillet"), 0, 0, barillet);
        int ov = OverlayTexture.NO_OVERLAY;
        m.renderList(m.solid, ps, buf.getBuffer(RenderType.entityCutout(TEXTURE)), lumiere, ov, 1, 1, 1, 1);
        m.renderList(m.glass, ps, buf.getBuffer(RenderType.entityTranslucentCull(TEXTURE)), lumiere, ov, 1, 1, 1, 1);
        var lueur = buf.getBuffer(RenderType.eyes(TEXTURE));
        long t = (long) temps;
        boolean allume = !chute || (t % 6 < 3);
        if (allume) {
            m.renderPart("feu_rouge", ps, lueur, lumiere, ov, 1, 1, 1, 1);
            m.renderPart("feu_vert", ps, lueur, lumiere, ov, 1, 1, 1, 1);
            m.renderPart("feu_blanc", ps, lueur, lumiere, ov, 0.8f, 0.8f, 0.8f, 1);
            m.renderPart("phare", ps, lueur, lumiere, ov, 1, 1, 1, 1);
        }
        if (t % 20 < 2 || chute && t % 4 < 2) m.renderPart("gyrophare", ps, lueur, lumiere, ov, 1, 1, 1, 1);
        ps.popPose();
    }

    private static void pose(GeoPart p, float x, float y, float z) {
        if (p == null) return;
        p.xRot = x;
        p.yRot = y;
        p.zRot = z;
    }

    private static final Matrix4f MAT = new Matrix4f();
    private static final Vector4f VEC = new Vector4f();

    /**
     * Point du modèle (pixels, repère naturel : x à droite, y en haut, nez vers -z) dans le monde, avec l'orientation
     * complète (mêmes transformations que {@link #orienter}). Fil du rendu ou du tick client seulement.
     */
    public static Vec3 pointMonde(Vec3 p, double x, double y, double z, float lacet, float tangage, float roulis) {
        MAT.identity()
                .rotateY((float) Math.toRadians(180f - lacet))
                .translate(0, PIVOT, 0)
                .rotateX((float) Math.toRadians(-tangage))
                .rotateZ((float) Math.toRadians(-roulis))
                .translate(0, -PIVOT, 0);
        MAT.transform(VEC.set((float) (p.x / 16.0), (float) (p.y / 16.0), (float) (p.z / 16.0), 1f));
        return new Vec3(x + VEC.x, y + VEC.y, z + VEC.z);
    }

    /** Sortie des balles : bout du barillet, la tourelle tournée (lacet, tangage du canon, degrés). */
    public static Vec3 bouche(double x, double y, double z, float lacet, float tangage, float roulis, float tLacet, float cTangage) {
        double th = Math.toRadians(tLacet), ph = Math.toRadians(cTangage);
        double lx = -31 * Math.cos(ph) * Math.sin(th), ly = -4.5 + 31 * Math.sin(ph), lz = -31 * Math.cos(ph) * Math.cos(th);
        Vec3 t = HelicoEntity.TOURELLE;
        return pointMonde(new Vec3(t.x + lx, t.y + ly, t.z + lz), x, y, z, lacet, tangage, roulis);
    }
}
