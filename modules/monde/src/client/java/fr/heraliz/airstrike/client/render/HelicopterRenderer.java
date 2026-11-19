package fr.heraliz.airstrike.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.airstrike.client.HelicoClient;
import fr.heraliz.airstrike.client.fx.Flares;
import fr.heraliz.airstrike.client.model.GeoModel;
import fr.heraliz.airstrike.client.model.GeoPart;
import fr.heraliz.airstrike.client.model.ModModels;
import fr.heraliz.airstrike.entity.Helicopter;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Hélicoptère de l'événement (modèle {@code helicopter}) : même façon de tourner le modèle que les avions de
 * {@link StrikeRenderer} (lacet, puis tangage et roulis autour du centre de la cabine, repère retourné). Le rotor principal
 * tourne autour du moyeu ({@code rotor}, lacet de la pièce), le rotor de queue autour de x ({@code tail_rotor}) ; à pleine
 * vitesse, les disques flous ({@code rotor_blur}, {@code tail_blur}) apparaissent et les pales pâlissent (dessinées en
 * transparence), comme les hélices de l'avion cargo. Miniguns de porte : canons qui tournent ({@code gun_l/barrels}) et
 * éclair à la bouche pendant le tir. Feux de position, flash anticollision, cockpit. Éclairé par la lumière du monde à sa
 * position. Modèle pas encore livré : rien n'est dessiné.
 * <p>19/11 : le dessin est dans {@link #dessiner}, partagé avec l'hélicoptère lointain (sans entité chez le joueur,
 * {@code HelicoClient}) ; rien n'y est alloué (points du modèle relevés une fois par modèle chargé, rotations dans des
 * objets réutilisés).</p>
 */
public class HelicopterRenderer extends EntityRenderer<Helicopter> {
    private static final ResourceLocation TEXTURE = ModModels.texture("helicopter");
    private static final ResourceLocation FLASH = ModModels.texture("designator_display");
    /** Au-dessus de ce régime, disque flou plein et pales pâles. */
    private static final float FLOU_DEBUT = 0.35f, FLOU_PLEIN = 0.8f;
    /** Vitesse des canons qui tournent pendant le tir (radians par tick). */
    private static final float CANONS = 0.9f;

    // objets réutilisés (rendu sur le fil du jeu seulement) : rien d'alloué à l'image
    private static final Quaternionf ROT = new Quaternionf();
    private static final Vector3f CENTRE_FLASH = new Vector3f();
    private static final double[] OU = new double[3];
    /** Points du modèle (blocs), relevés pour le modèle {@link #modeleLu} : pivot, bouches gauche et droite. */
    private static final double[] PIVOT = new double[3], BOUCHE_G = new double[3], BOUCHE_D = new double[3];
    private static GeoModel modeleLu;

    public HelicopterRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    private static float lisse(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    @Override
    public void render(Helicopter h, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        GeoModel m = ModModels.helicopter();
        if (m.topLevel.isEmpty()) return;
        long tirG = HelicoClient.depuisTir(h.getId(), false), tirD = HelicoClient.depuisTir(h.getId(), true);
        dessiner(m, ps, buffers, light, h.lacet(pt), h.tangage(pt), h.roulis(pt), h.pales(pt), h.rotorQueue(pt), h.rotor(),
                h.tickCount, tirG, tirD, h.tickCount + pt);
        super.render(h, yaw, pt, ps, buffers, light);
    }

    /** Relève les points du modèle (une fois par modèle chargé : rechargement des ressources compris). */
    private static void lire(GeoModel m) {
        if (m == modeleLu) return;
        modeleLu = m;
        point(m, "cabin_center", Helicopter.CENTRE, PIVOT);
        point(m, "muzzle_l", Helicopter.CANON_G, BOUCHE_G);
        point(m, "muzzle_r", Helicopter.CANON_D, BOUCHE_D);
    }

    private static void point(GeoModel m, String nom, double[] defaut, double[] sortie) {
        Vec3 p = m.meta.points.get(nom);
        if (p == null) {
            System.arraycopy(defaut, 0, sortie, 0, 3);
        } else {
            sortie[0] = p.x / 16.0;
            sortie[1] = p.y / 16.0;
            sortie[2] = p.z / 16.0;
        }
    }

    /**
     * Le modèle, la pose déjà placée à la position de l'hélicoptère (repère du monde, relatif à la caméra) : lacet, tangage
     * et roulis (degrés), angles des pales et du rotor de queue (radians), régime du rotor (0 à 1), {@code horloge} (ticks,
     * flash anticollision), ticks depuis le dernier coup de chaque minigun, {@code t} (ticks et fraction : canons, éclair).
     */
    public static void dessiner(GeoModel m, PoseStack ps, MultiBufferSource buffers, int light, float lacet, float tangage,
                                float roulis, float pales, float palesQueue, float regime, long horloge, long tirG, long tirD,
                                float t) {
        lire(m);
        // --- animation
        float flou = lisse(FLOU_DEBUT, FLOU_PLEIN, regime);
        GeoPart rotor = m.topLevel.get("rotor"), queue = m.topLevel.get("tail_rotor");
        if (rotor != null) rotor.yRot = rotor.initialYRot() + pales;
        if (queue != null) queue.xRot = queue.initialXRot() + palesQueue;
        GeoPart flouRotor = m.topLevel.get("rotor_blur"), flouQueue = m.topLevel.get("tail_blur");
        if (flouRotor != null) flouRotor.yRot = flouRotor.initialYRot() - pales * 0.37f;
        if (flouQueue != null) flouQueue.xRot = flouQueue.initialXRot() - palesQueue * 0.37f;
        GeoPart bg = m.part("gun_l/barrels"), bd = m.part("gun_r/barrels");
        if (bg != null) bg.zRot = bg.initialZRot() + (tirG < 8 ? t * CANONS : 0f);
        if (bd != null) bd.zRot = bd.initialZRot() + (tirD < 8 ? t * CANONS : 0f);

        ps.pushPose();
        ps.mulPose(ROT.rotationY((float) Math.toRadians(180f - lacet)));
        ps.translate(PIVOT[0], PIVOT[1], PIVOT[2]);
        ps.mulPose(ROT.rotationX((float) Math.toRadians(-tangage)));
        ps.mulPose(ROT.rotationZ((float) Math.toRadians(-roulis)));
        ps.translate(-PIVOT[0], -PIVOT[1], -PIVOT[2]);
        ps.scale(-1f, -1f, 1f);

        // pièces pleines ; à plein régime, les pales passent dans la passe transparente
        boolean palesPleines = flou < 0.95f;
        if (rotor != null) rotor.visible = palesPleines;
        VertexConsumer plein = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
        m.renderList(m.solid, ps, plein, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        if (rotor != null) rotor.visible = true;

        VertexConsumer verre = buffers.getBuffer(RenderType.entityTranslucentCull(TEXTURE));
        m.renderPart("glass", ps, verre, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        if (!palesPleines && rotor != null) rotor.render(ps, verre, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 0.35f);
        if (flou > 0.02f) {
            m.renderPart("rotor_blur", ps, verre, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 0.75f * flou);
            m.renderPart("tail_blur", ps, verre, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 0.75f * flou);
        }

        VertexConsumer lumiere = buffers.getBuffer(RenderType.eyes(TEXTURE));
        int full = LightTexture.FULL_BRIGHT;
        m.renderPart("emit_nav_l", ps, lumiere, full, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        m.renderPart("emit_nav_r", ps, lumiere, full, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        m.renderPart("emit_cockpit", ps, lumiere, full, OverlayTexture.NO_OVERLAY, 0.6f, 0.6f, 0.6f, 1);
        if (horloge % 24 < 2 || (horloge + 5) % 24 < 1) m.renderPart("emit_strobe", ps, lumiere, full, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        ps.popPose();

        // éclair à la bouche du minigun qui tire (scintillement rapide)
        if (tirG < 3 || tirD < 3) {
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            VertexConsumer flash = buffers.getBuffer(RenderType.eyes(FLASH));
            Matrix4f mat = ps.last().pose();
            for (int c = 0; c < 2; c++) {
                if ((c == 0 ? tirG : tirD) >= 3) continue;
                double[] b = c == 0 ? BOUCHE_G : BOUCHE_D;
                Helicopter.versMonde(b[0], b[1], b[2], lacet, tangage, roulis, PIVOT, OU);
                float k = 0.6f + 0.4f * Mth.abs(Mth.sin(t * 7.3f + c));
                CENTRE_FLASH.set((float) OU[0], (float) OU[1], (float) OU[2]);
                Flares.billboard(flash, mat, ps.last().normal(), CENTRE_FLASH, camera.getLeftVector(), camera.getUpVector(),
                        2.2f * k, k, 0.75f * k, 0.3f * k);
            }
        }
    }

    /**
     * Dessine tout de suite ce que {@link #dessiner} a laissé en attente (seulement ses propres passes) : l'hélicoptère
     * lointain est dessiné avec son propre brouillard, rendu juste après.
     */
    public static void finir(MultiBufferSource.BufferSource buffers) {
        buffers.endBatch(RenderType.entityCutout(TEXTURE));
        buffers.endBatch(RenderType.entityTranslucentCull(TEXTURE));
        buffers.endBatch(RenderType.eyes(TEXTURE));
        buffers.endBatch(RenderType.eyes(FLASH));
    }

    @Override
    public ResourceLocation getTextureLocation(Helicopter h) {
        return TEXTURE;
    }
}
