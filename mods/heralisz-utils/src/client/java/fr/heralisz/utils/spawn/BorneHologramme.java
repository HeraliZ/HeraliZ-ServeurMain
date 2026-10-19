package fr.heralisz.utils.spawn;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Hologramme de la borne de départ (maquette « table tactique holographique » validée) : au-dessus du projecteur de la
 * table, un globe de la carte en fil de fer bleu qui tourne lentement (anneaux et méridiens) et une épingle rouge qui
 * saute toutes les 2,5 s vers un nouveau point de la carte de la table (le « départ au hasard »), avec son rayon et sa
 * cible au sol. Lumière pleine, mélange additif (RenderType.lightning) : brille la nuit, rien d'opaque. Au-dessus, le
 * panneau « TÉLÉPORTATION ALÉATOIRE » ({@link #panneau}). Joueurs seulement, rien ne vient du serveur. Coordonnées en
 * pixels de bloc (16 par bloc).
 */
public class BorneHologramme implements BlockEntityRenderer<BorneDepartEntite> {
    private static final float CX = 8f, CY = 23.5f, CZ = 8f;       // centre du globe
    private static final float RAYON = 6.2f, TRAIT = 0.16f;
    private static final float TABLE = 14.35f;                       // dessus de la carte de la table
    private static final int SEGMENTS = 28;
    private static final float[] BLEU = {0.30f, 0.80f, 1.00f}, ROUGE = {1.00f, 0.25f, 0.18f};
    /** Panneau « Téléportation aléatoire » : hauteur de son centre au-dessus du pied de la borne (blocs, au-dessus de
     *  l'épingle qui monte jusqu'à ~2,3), taille d'un pixel de police (blocs), portée (blocs), taille de la 2e ligne. */
    private static final double PANNEAU_Y = 2.75, PANNEAU_PORTEE = 24;
    private static final float PANNEAU_ECHELLE = 0.0115f, AIDE_ECHELLE = 0.62f;
    private static final int PLEINE_LUMIERE = 0xF000F0;

    public BorneHologramme(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(BorneDepartEntite be, float partiel, PoseStack pose, MultiBufferSource buffers, int lumiere, int ombre) {
        if (be.getLevel() == null) {
            return;
        }
        float t = (be.getLevel().getGameTime() % 24000L + partiel) / 20f;
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        pose.pushPose();
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        Matrix4f m = pose.last().pose();
        float pulse = 0.55f + 0.12f * Mth.sin(t * 2.2f);
        float tour = t * 0.35f;                                      // ~20° par seconde

        // faisceau du projecteur : cône fin du projecteur vers le bas du globe
        for (int i = 0; i < 6; i++) {
            float a = tour + i * Mth.TWO_PI / 6;
            segment(vc, m, CX, 16f, CZ, CX + Mth.cos(a) * 2.2f, CY - RAYON + 1.2f, CZ + Mth.sin(a) * 2.2f, 0.06f, BLEU, 0.25f);
        }
        // anneaux horizontaux (parallèles)
        for (float k : new float[]{-0.78f, -0.42f, 0f, 0.42f, 0.78f}) {
            float y = CY + k * RAYON, r = RAYON * Mth.sqrt(1 - k * k);
            cercleHorizontal(vc, m, y, r, tour, k == 0f ? pulse + 0.15f : pulse);
        }
        // méridiens
        for (int i = 0; i < 4; i++) {
            meridien(vc, m, tour + i * Mth.PI / 4, pulse);
        }
        // épingle : saute vers un point tiré d'après la position de la borne et la tranche de temps (même chose pour tous)
        long graine = be.getBlockPos().asLong();
        int tranche = (int) Math.floor(t / 2.5f);
        float avance = Mth.clamp((t - tranche * 2.5f) / 0.45f, 0f, 1f);
        avance = avance * avance * (3 - 2 * avance);
        float[] a0 = point(graine, tranche - 1), a1 = point(graine, tranche);
        float px = Mth.lerp(avance, a0[0], a1[0]), pz = Mth.lerp(avance, a0[1], a1[1]);
        float saut = Mth.sin(avance * Mth.PI) * 3.5f;                 // petit bond pendant le déplacement
        float tete = CY + RAYON + 2.0f + saut + 0.4f * Mth.sin(t * 3f);
        segment(vc, m, px, TABLE, pz, px, tete - 1.2f, pz, 0.07f, ROUGE, 0.55f);  // rayon
        cible(vc, m, px, pz, t);
        losange(vc, m, px, tete, pz, 1.1f, t * 1.5f);
        pose.popPose();
        panneau(be, pose, buffers);
    }

    /**
     * Panneau holographique au-dessus de l'épingle : « TÉLÉPORTATION ALÉATOIRE » et, plus petit, « Clic droit · départ au
     * hasard sur la carte » (clés de langue heralisz_utils.borne.*), toujours tourné vers le joueur, fond sombre
     * transparent bordé d'un fin trait bleu ; net jusqu'à 10 blocs, plus pâle ensuite, rien au-delà de 24.
     */
    private static void panneau(BorneDepartEntite be, PoseStack pose, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 oeil = mc.gameRenderer.getMainCamera().getPosition();
        double d = oeil.distanceTo(Vec3.atCenterOf(be.getBlockPos()).add(0, PANNEAU_Y - 0.5, 0));
        if (d > PANNEAU_PORTEE) {
            return;
        }
        float alpha = (float) (d <= 10 ? 1.0 : Mth.lerp((d - 10) / (PANNEAU_PORTEE - 10), 1.0, 0.35));
        Font police = mc.font;
        Component titre = Component.translatable("heralisz_utils.borne.titre");
        Component aide = Component.translatable("heralisz_utils.borne.aide");
        float lt = police.width(titre), la = police.width(aide) * AIDE_ECHELLE;
        float demi = Math.max(lt, la) / 2f + 6f;                      // demi-largeur du fond, en pixels de police
        float haut = -6f, bas = 9f + 2f + 9f * AIDE_ECHELLE + 4f;

        pose.pushPose();
        pose.translate(0.5, PANNEAU_Y, 0.5);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-PANNEAU_ECHELLE, -PANNEAU_ECHELLE, PANNEAU_ECHELLE);   // comme les noms : +x à droite, +y vers le bas
        Matrix4f m = pose.last().pose();
        VertexConsumer fond = buffers.getBuffer(RenderType.textBackground());
        rect(fond, m, -demi, haut, demi, bas, 0x0A141C, Math.round(150 * alpha));
        int bord = Math.round(210 * alpha);
        rect(fond, m, -demi, haut, demi, haut + 0.8f, 0x4CCCFF, bord);         // fin trait bleu tout autour
        rect(fond, m, -demi, bas - 0.8f, demi, bas, 0x4CCCFF, bord);
        rect(fond, m, -demi, haut, -demi + 0.8f, bas, 0x4CCCFF, bord);
        rect(fond, m, demi - 0.8f, haut, demi, bas, 0x4CCCFF, bord);
        int a = Math.max(8, Math.round(255 * alpha)) << 24;
        police.drawInBatch(titre, -lt / 2f, -2f, a | 0xEAF8FF, false, m, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, PLEINE_LUMIERE);
        pose.translate(0, 9f + 2f, 0);
        pose.scale(AIDE_ECHELLE, AIDE_ECHELLE, AIDE_ECHELLE);
        police.drawInBatch(aide, -police.width(aide) / 2f, 0, a | 0x8FD3FF, false, pose.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, PLEINE_LUMIERE);
        pose.popPose();
    }

    /** Rectangle plein (fond des textes), dessiné des deux côtés. */
    private static void rect(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int rgb, int alpha) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        float[][] p = {{x0, y0}, {x0, y1}, {x1, y1}, {x1, y0}};
        for (int i = 0; i < 4; i++) {
            vc.vertex(m, p[i][0], p[i][1], 0).color(r, g, b, alpha).uv2(PLEINE_LUMIERE).endVertex();
        }
        for (int i = 3; i >= 0; i--) {
            vc.vertex(m, p[i][0], p[i][1], 0).color(r, g, b, alpha).uv2(PLEINE_LUMIERE).endVertex();
        }
    }

    /** Point d'arrivée « tiré » sur la carte de la table (à 1,5 px des bords). */
    private static float[] point(long graine, int tranche) {
        long h = graine * 0x9E3779B97F4A7C15L + tranche * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        float u = ((h >>> 11) & 0xFFFF) / 65535f, v = ((h >>> 27) & 0xFFFF) / 65535f;
        return new float[]{1.5f + u * 13f, 1.5f + v * 13f};
    }

    private static void cercleHorizontal(VertexConsumer vc, Matrix4f m, float y, float r, float tour, float alpha) {
        for (int i = 0; i < SEGMENTS; i++) {
            float a = tour + i * Mth.TWO_PI / SEGMENTS, b = tour + (i + 1) * Mth.TWO_PI / SEGMENTS;
            segment(vc, m, CX + Mth.cos(a) * r, y, CZ + Mth.sin(a) * r, CX + Mth.cos(b) * r, y, CZ + Mth.sin(b) * r, TRAIT, BLEU, alpha);
        }
    }

    private static void meridien(VertexConsumer vc, Matrix4f m, float azimut, float alpha) {
        float dx = Mth.cos(azimut), dz = Mth.sin(azimut);
        for (int i = 0; i < SEGMENTS; i++) {
            float a = i * Mth.TWO_PI / SEGMENTS, b = (i + 1) * Mth.TWO_PI / SEGMENTS;
            segment(vc, m, CX + dx * Mth.cos(a) * RAYON, CY + Mth.sin(a) * RAYON, CZ + dz * Mth.cos(a) * RAYON,
                    CX + dx * Mth.cos(b) * RAYON, CY + Mth.sin(b) * RAYON, CZ + dz * Mth.cos(b) * RAYON, TRAIT, BLEU, alpha * 0.85f);
        }
    }

    /** Cible au sol sous l'épingle : anneau rouge qui pulse sur la carte. */
    private static void cible(VertexConsumer vc, Matrix4f m, float x, float z, float t) {
        float r = 1.0f + 0.35f * (0.5f + 0.5f * Mth.sin(t * 5f));
        for (int i = 0; i < 12; i++) {
            float a = i * Mth.TWO_PI / 12, b = (i + 1) * Mth.TWO_PI / 12;
            segment(vc, m, x + Mth.cos(a) * r, TABLE + 0.05f, z + Mth.sin(a) * r, x + Mth.cos(b) * r, TABLE + 0.05f, z + Mth.sin(b) * r,
                    0.1f, ROUGE, 0.8f);
        }
    }

    /** Tête de l'épingle : losange (octaèdre) rouge qui tourne. */
    private static void losange(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float tour) {
        Vector3f haut = new Vector3f(x, y + r * 1.4f, z), bas = new Vector3f(x, y - r * 1.4f, z);
        Vector3f[] tour4 = new Vector3f[4];
        for (int i = 0; i < 4; i++) {
            float a = tour + i * Mth.HALF_PI;
            tour4[i] = new Vector3f(x + Mth.cos(a) * r, y, z + Mth.sin(a) * r);
        }
        for (int i = 0; i < 4; i++) {
            Vector3f p = tour4[i], q = tour4[(i + 1) % 4];
            triangle(vc, m, haut, p, q, ROUGE, 0.9f);
            triangle(vc, m, bas, q, p, ROUGE, 0.7f);
        }
    }

    private static void triangle(VertexConsumer vc, Matrix4f m, Vector3f a, Vector3f b, Vector3f c, float[] col, float alpha) {
        quad(vc, m, a, b, c, c, col, alpha);
    }

    /** Trait lumineux de a à b : deux rubans croisés, dessinés des deux côtés. */
    private static void segment(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
                                float largeur, float[] col, float alpha) {
        Vector3f d = new Vector3f(bx - ax, by - ay, bz - az);
        if (d.lengthSquared() < 1e-6f) {
            return;
        }
        Vector3f u = new Vector3f(d).cross(0, 1, 0);
        if (u.lengthSquared() < 1e-6f) {
            u.set(1, 0, 0);
        }
        u.normalize(largeur);
        Vector3f v = new Vector3f(d).cross(u).normalize(largeur);
        for (Vector3f o : new Vector3f[]{u, v}) {
            Vector3f p1 = new Vector3f(ax - o.x, ay - o.y, az - o.z), p2 = new Vector3f(ax + o.x, ay + o.y, az + o.z);
            Vector3f p3 = new Vector3f(bx + o.x, by + o.y, bz + o.z), p4 = new Vector3f(bx - o.x, by - o.y, bz - o.z);
            quad(vc, m, p1, p2, p3, p4, col, alpha);
            quad(vc, m, p4, p3, p2, p1, col, alpha);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vector3f a, Vector3f b, Vector3f c, Vector3f d, float[] col, float alpha) {
        for (Vector3f p : new Vector3f[]{a, b, c, d}) {
            vc.vertex(m, p.x, p.y, p.z).color(col[0], col[1], col[2], alpha).endVertex();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(BorneDepartEntite be) {
        return true;
    }

    /** Branchement du rendu (bus MOD, joueurs seulement). */
    @Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Branchement {
        private Branchement() {}

        @SubscribeEvent
        public static void rendus(EntityRenderersEvent.RegisterRenderers e) {
            e.registerBlockEntityRenderer(Depart.BORNE_BE.get(), BorneHologramme::new);
        }
    }
}
