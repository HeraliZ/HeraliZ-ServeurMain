package fr.heraliz.recolte.client.rendu;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.recolte.HeralizRecolte;
import fr.heraliz.recolte.client.modeles.Maillage;
import fr.heraliz.recolte.client.modeles.ModeleCuit;
import fr.heraliz.recolte.client.modeles.ModelesRecolte;
import fr.heraliz.recolte.entites.Gisement;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.WeakHashMap;

/**
 * Gisement de pierre, de métal ou de métal rare ({@code heraliz_recolte:gisement}) : le modèle cuit de son étape
 * ({@link Gisement#ETAPE}), en un envoi, avec la lumière de l'endroit et le lacet de l'entité ; une petite ombre ronde.
 * <ul>
 *    <li>à chaque coup ({@link Gisement#SECOUSSE} : heure de jeu du dernier coup), 6 ticks de tremblement : petites
 *    rotations autour de la base et petits décalages, amortis ;</li>
 *    <li>quand il perd un morceau (étape suivante), petit « pop » d'échelle autour de sa base (le nouveau modèle
 *    apparaît un peu plus gros, rebondit et se pose).</li>
 * </ul>
 * Rien au-delà de 64 blocs ({@code Gisement.shouldRenderAtSqrDistance}). Aucune allocation par image (le suivi de
 * l'étape est créé une fois par gisement vu).
 */
public final class RenduGisement extends EntityRenderer<Gisement> {
    private static final ResourceLocation SECOURS = new ResourceLocation(HeralizRecolte.MODID, "textures/entity/gisement_pierre_1.png");
    private static final float DEG = (float) (Math.PI / 180.0);
    /** Tremblement après un coup, « pop » après un changement d'étape (ticks). */
    private static final float SECOUSSE = 6.0F, POP = 6.0F;
    /** Amplitudes du tremblement : décalage (m) et rotation (radians, environ 1,5 °). */
    private static final float SECOUSSE_DECALAGE = 0.022F, SECOUSSE_ANGLE = 0.028F;
    /** Le nouveau modèle apparaît 9 % plus gros, puis rebondit et se pose. */
    private static final float POP_ECHELLE = 0.09F;

    private final Matrix4f m = new Matrix4f();
    private final Matrix3f n = new Matrix3f();
    /** Par gisement vu : {étape vue, heure de jeu de son changement} (oublié avec l'entité). */
    private final WeakHashMap<Gisement, long[]> suivis = new WeakHashMap<>();

    public RenduGisement(EntityRendererProvider.Context contexte) {
        super(contexte);
        this.shadowRadius = 0.9F;
        this.shadowStrength = 0.6F;
    }

    @Override
    public ResourceLocation getTextureLocation(Gisement g) {
        ModeleCuit mc = ModelesRecolte.gisement(g.type(), g.etape());
        return mc != null ? mc.texture : SECOURS;
    }

    @Override
    public void render(Gisement g, float lacet, float partiel, PoseStack pose, MultiBufferSource tampons, int lumiere) {
        int etape = g.etape();
        ModeleCuit mc = ModelesRecolte.gisement(g.type(), etape);
        this.shadowRadius = Math.max(0.3F, g.getBbWidth() * 0.55F);
        if (mc == null) {
            return;
        }
        long heure = g.level().getGameTime();
        float echelle = pop(g, etape, heure, partiel);
        m.set(pose.last().pose());
        n.set(pose.last().normal());
        float y = -g.getYRot() * DEG;
        m.rotateY(y);
        n.rotateY(y);
        long coup = g.secousse();
        if (coup >= 0L) {
            float t = (float) (heure - coup) + partiel;
            if (t >= 0.0F && t < SECOUSSE) {
                float a = 1.0F - t / SECOUSSE;
                a *= a;
                float graine = (g.getId() & 7) * 0.7F;
                float dx = SECOUSSE_DECALAGE * a * Mth.sin(t * 3.1F + graine);
                float dz = SECOUSSE_DECALAGE * 0.8F * a * Mth.sin(t * 3.7F + 1.3F + graine);
                float rx = SECOUSSE_ANGLE * a * Mth.sin(t * 3.4F + 0.5F + graine);
                float rz = SECOUSSE_ANGLE * a * Mth.sin(t * 2.9F + 2.1F + graine);
                m.translate(dx, 0.0F, dz).rotateX(rx).rotateZ(rz);
                n.rotateX(rx).rotateZ(rz);
            }
        }
        if (echelle != 1.0F) {
            m.scale(echelle);
        }
        Maillage.dessiner(mc.tout, m, n, tampons.getBuffer(mc.decoupe()), 1.0F, lumiere, OverlayTexture.NO_OVERLAY);
    }

    /**
     * Échelle du « pop » : 1 sauf dans les 6 ticks qui suivent un changement d'étape vu par ce joueur (rien quand le
     * gisement apparaît : la première étape vue n'est pas un changement).
     */
    private float pop(Gisement g, int etape, long heure, float partiel) {
        long[] s = suivis.get(g);
        if (s == null) {
            suivis.put(g, new long[]{etape, Long.MIN_VALUE / 2L});
            return 1.0F;
        }
        if (s[0] != etape) {
            s[0] = etape;
            // le morceau part au coup qui l'arrache : heure du coup si elle est récente, sinon maintenant
            long coup = g.secousse();
            s[1] = coup >= 0L && heure - coup >= 0L && heure - coup < POP ? coup : heure;
        }
        float t = (float) (heure - s[1]) + partiel;
        if (t < 0.0F || t >= POP) {
            return 1.0F;
        }
        float a = 1.0F - t / POP;
        return 1.0F + POP_ECHELLE * a * a * Mth.cos(t * 1.9F);
    }
}
