package fr.heraliz.corps.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.corps.CorpsConfig;
import fr.heraliz.corps.entite.CorpsEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Rendu d'un corps : le modele du joueur allonge (maillage cuit, voir {@link Maillage}), avec sa peau. Pas d'ombre, pas
 * de nom, pas d'armure dessinee. Distance de rendu et plafonds (par chunk, au total) : {@link ClientCorps}. Aucune
 * allocation par image : matrices et vecteurs reutilises, pose sauvee et rendue a la main (pas de push/pop).
 * <p>Membres du clan du mort : petit losange a la couleur du clan au-dessus du corps (moins de 32 blocs), dessine avec
 * les autres losanges en un seul lot apres les entites ({@link Marqueurs}).
 */
public final class CorpsRenderer extends EntityRenderer<CorpsEntity> {
    private static final double MARQUEUR_DISTANCE_CARRE = 32 * 32;

    private final Maillage large, fin;
    private final Matrix4f poseSauvee = new Matrix4f();
    private final Matrix3f normaleSauvee = new Matrix3f();
    private final Quaternionf rotation = new Quaternionf();
    private final Vector4f v4 = new Vector4f();
    private final Vector3f v3 = new Vector3f();

    /** Mesures du banc d'essai : corps dessines, temps total cumule et temps des seuls sommets (ns). */
    public static long mesureNs, mesureCorps, mesureSommetsNs;
    /** Mesures actives seulement dans le banc d'essai (constante : le JIT retire le code en jeu). */
    private static final boolean MESURER = System.getProperty("heraliz.corps.harness") != null;

    public CorpsRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
        this.large = Maillage.cuire(new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false));
        this.fin = Maillage.cuire(new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true));
    }

    @Override
    public boolean shouldRender(CorpsEntity e, Frustum frustum, double x, double y, double z) {
        return e.rendu && super.shouldRender(e, frustum, x, y, z);
    }

    @Override
    public void render(CorpsEntity e, float lacet, float pt, PoseStack ps, MultiBufferSource buffers, int lumiere) {
        if (e.level().getGameTime() < e.visibleDes()) return; // scene d'execution encore en cours
        long debut = MESURER ? System.nanoTime() : 0L;
        Peaux.Peau peau = Peaux.de(e);
        Maillage m = peau.fin ? fin : large;
        PoseStack.Pose pose = ps.last();
        Matrix4f mp = pose.pose();
        Matrix3f mn = pose.normal();
        poseSauvee.set(mp);
        normaleSauvee.set(mn);
        rotation.rotationY((180.0F - e.getYRot()) * Mth.DEG_TO_RAD);
        mp.rotate(rotation);
        mn.rotate(rotation);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(peau.texture));
        long t1 = MESURER ? System.nanoTime() : 0L;
        m.emettre(mp, mn, vc, lumiere, 1.0F, 1.0F, 1.0F, 1.0F, v4, v3);
        if (MESURER) mesureSommetsNs += System.nanoTime() - t1;
        mp.set(poseSauvee);
        mn.set(normaleSauvee);
        if (e.clanAmi && CorpsConfig.marqueurClan && entityRenderDispatcher.distanceToSqr(e) < MARQUEUR_DISTANCE_CARRE) {
            Marqueurs.ajouter(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        }
        if (MESURER) {
            mesureNs += System.nanoTime() - debut;
            mesureCorps++;
        }
    }

    @Override
    public ResourceLocation getTextureLocation(CorpsEntity e) {
        return Peaux.de(e).texture;
    }
}
