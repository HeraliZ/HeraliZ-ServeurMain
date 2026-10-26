package fr.heraliz.airstrike.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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
import org.joml.Vector3f;

/**
 * Hélicoptère de l'événement (modèle {@code helicopter}) : même façon de tourner le modèle que les avions de
 * {@link StrikeRenderer} (lacet, puis tangage et roulis autour du centre de la cabine, repère retourné). Le rotor principal
 * tourne autour du moyeu ({@code rotor}, lacet de la pièce), le rotor de queue autour de x ({@code tail_rotor}) ; à pleine
 * vitesse, les disques flous ({@code rotor_blur}, {@code tail_blur}) apparaissent et les pales pâlissent (dessinées en
 * transparence), comme les hélices de l'avion cargo. Miniguns de porte : canons qui tournent ({@code gun_l/barrels}) et
 * éclair à la bouche pendant le tir. Feux de position, flash anticollision, cockpit. Éclairé par la lumière du monde à sa
 * position. Modèle pas encore livré : rien n'est dessiné.
 */
public class HelicopterRenderer extends EntityRenderer<Helicopter> {
    private static final ResourceLocation TEXTURE = ModModels.texture("helicopter");
    private static final ResourceLocation FLASH = ModModels.texture("designator_display");
    /** Au-dessus de ce régime, disque flou plein et pales pâles. */
    private static final float FLOU_DEBUT = 0.35f, FLOU_PLEIN = 0.8f;
    private static final String[] BOUCHES = {"muzzle_l", "muzzle_r"};
    /** Vitesse des canons qui tournent pendant le tir (radians par tick). */
    private static final float CANONS = 0.9f;
    private final Vector3f centreFlash = new Vector3f();

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
        float lacet = h.lacet(pt), tangage = h.tangage(pt), roulis = h.roulis(pt);
        Vec3 pivot = m.meta.points.containsKey("cabin_center") ? m.point("cabin_center").scale(1.0 / 16.0)
                : new Vec3(Helicopter.CENTRE[0], Helicopter.CENTRE[1], Helicopter.CENTRE[2]);

        // --- animation
        float regime = h.rotor();
        float flou = lisse(FLOU_DEBUT, FLOU_PLEIN, regime);
        GeoPart rotor = m.topLevel.get("rotor"), queue = m.topLevel.get("tail_rotor");
        if (rotor != null) rotor.yRot = rotor.initialYRot() + h.pales(pt);
        if (queue != null) queue.xRot = queue.initialXRot() + h.rotorQueue(pt);
        GeoPart flouRotor = m.topLevel.get("rotor_blur"), flouQueue = m.topLevel.get("tail_blur");
        if (flouRotor != null) flouRotor.yRot = flouRotor.initialYRot() - h.pales(pt) * 0.37f;
        if (flouQueue != null) flouQueue.xRot = flouQueue.initialXRot() - h.rotorQueue(pt) * 0.37f;
        long tirG = HelicoClient.depuisTir(h.getId(), false), tirD = HelicoClient.depuisTir(h.getId(), true);
        float t = h.tickCount + pt;
        GeoPart bg = m.part("gun_l/barrels"), bd = m.part("gun_r/barrels");
        if (bg != null) bg.zRot = bg.initialZRot() + (tirG < 8 ? t * CANONS : 0f);
        if (bd != null) bd.zRot = bd.initialZRot() + (tirD < 8 ? t * CANONS : 0f);

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180f - lacet));
        ps.translate(pivot.x, pivot.y, pivot.z);
        ps.mulPose(Axis.XP.rotationDegrees(-tangage));
        ps.mulPose(Axis.ZP.rotationDegrees(-roulis));
        ps.translate(-pivot.x, -pivot.y, -pivot.z);
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
        long gt = h.tickCount;
        m.renderPart("emit_nav_l", ps, lumiere, full, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        m.renderPart("emit_nav_r", ps, lumiere, full, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        m.renderPart("emit_cockpit", ps, lumiere, full, OverlayTexture.NO_OVERLAY, 0.6f, 0.6f, 0.6f, 1);
        if (gt % 24 < 2 || (gt + 5) % 24 < 1) m.renderPart("emit_strobe", ps, lumiere, full, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        ps.popPose();

        // éclair à la bouche du minigun qui tire (scintillement rapide)
        if (tirG < 3 || tirD < 3) {
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            VertexConsumer flash = buffers.getBuffer(RenderType.eyes(FLASH));
            Matrix4f mat = ps.last().pose();
            for (int c = 0; c < 2; c++) {
                if ((c == 0 ? tirG : tirD) >= 3) continue;
                Vec3 b = m.meta.points.containsKey(BOUCHES[c]) ? m.point(BOUCHES[c]).scale(1.0 / 16.0)
                        : c == 0 ? new Vec3(Helicopter.CANON_G[0], Helicopter.CANON_G[1], Helicopter.CANON_G[2])
                        : new Vec3(Helicopter.CANON_D[0], Helicopter.CANON_D[1], Helicopter.CANON_D[2]);
                Vec3 o = Helicopter.versMonde(b.x, b.y, b.z, lacet, tangage, roulis);
                float k = 0.6f + 0.4f * Mth.abs(Mth.sin(t * 7.3f + c));
                centreFlash.set((float) o.x, (float) o.y, (float) o.z);
                Flares.billboard(flash, mat, ps.last().normal(), centreFlash, camera.getLeftVector(), camera.getUpVector(),
                        2.2f * k, k, 0.75f * k, 0.3f * k);
            }
        }
        super.render(h, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(Helicopter h) {
        return TEXTURE;
    }
}
