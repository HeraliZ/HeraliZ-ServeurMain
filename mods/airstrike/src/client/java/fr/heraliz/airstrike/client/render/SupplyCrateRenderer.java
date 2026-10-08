package fr.heraliz.airstrike.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heraliz.airstrike.client.model.GeoModel;
import fr.heraliz.airstrike.client.model.GeoPart;
import fr.heraliz.airstrike.client.model.ModModels;
import fr.heraliz.airstrike.entity.SupplyCrateEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Airdrop crate: under its canopies while falling, then on the ground. Breaking it open plays the reveal: the straps
 * snap, the latches flip, the lid springs up and the front and side panels fall flat on the ground.
 */
public class SupplyCrateRenderer extends EntityRenderer<SupplyCrateEntity> {
    /** Height of the canopies above the crate: the crate swings under them. */
    private static final float SWING_PIVOT = 16.0f;
    private static final int COLLAPSE_TICKS = 70;
    private static final int CHUTE_STAYS = 400;
    private static final int CHUTE_FADE = 60;
    /** Panels hinge at the pallet and lie on the ground: slightly more than 90 degrees. */
    private static final float PANEL_DOWN = 97.9f;

    public SupplyCrateRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 2.0f;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    private static float easeOutBack(float x) {
        x = Mth.clamp(x, 0f, 1f) - 1f;
        return 1f + 2.6f * x * x * x + 1.6f * x * x;
    }

    /** Panel falling like a plank (accelerating), a small rebound when it slaps the ground. */
    private static float panelFall(float t) {
        if (t <= 0f) return 0f;
        float fall = 12f;
        if (t < fall) {
            float u = t / fall;
            return PANEL_DOWN * u * u;
        }
        float b = (t - fall) / 7f;
        if (b < 1f) return PANEL_DOWN - 8f * Mth.sin(Mth.PI * b) * (1f - b * 0.5f);
        return PANEL_DOWN;
    }

    private static void rot(GeoPart p, float x, float z) {
        if (p == null) return;
        p.xRot = p.initialXRot() + (float) Math.toRadians(x);
        p.zRot = p.initialZRot() + (float) Math.toRadians(z);
    }

    /**
     * Poses the crate. {@code reveal} = ticks since it was broken open (negative = sealed), {@code gear} = number of
     * gear pieces still inside (0..6).
     */
    public static void pose(GeoModel m, float reveal, int gear) {
        float t = reveal;
        GeoPart straps = m.part("straps");
        if (straps != null) straps.visible = t < 5f;
        float latch = t < 0 ? 0f : 125f * smooth((t - 3f) / 6f);
        rot(m.part("body/panel_f/latch_l"), latch, 0);
        rot(m.part("body/panel_f/latch_r"), latch, 0);
        rot(m.part("body/lid"), t < 0 ? 0f : -118f * easeOutBack((t - 7f) / 13f), 0);
        rot(m.part("body/panel_f"), t < 0 ? 0f : panelFall(t - 11f), 0);
        rot(m.part("body/panel_l"), 0, t < 0 ? 0f : panelFall(t - 14f));
        rot(m.part("body/panel_r"), 0, t < 0 ? 0f : -panelFall(t - 16f));
        for (int i = 0; i < SupplyCrateEntity.GEAR_PIECES; i++) {
            GeoPart g = m.part("gear/gear_" + i);
            if (g != null) g.visible = i < gear;
        }
    }

    @Override
    public void render(SupplyCrateEntity entity, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        boolean landed = entity.isLanded();
        float age = entity.getFallTicks() + pt;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.lerp(pt, entity.yRotO, entity.getYRot())));
        float inflate = Mth.clamp((age - SupplyCrateEntity.CHUTE_DELAY) / 26f, 0f, 1f);
        if (!landed) {
            // the crate swings under the canopies, tumbling before they open
            float open = smooth(inflate);
            float swing = open * (Mth.sin(age * 0.11f) * 5f + Mth.sin(age * 0.047f) * 3f);
            float tumble = (1f - open) * Mth.sin(age * 0.5f) * 12f;
            ps.translate(0, SWING_PIVOT * open, 0);
            ps.mulPose(Axis.ZP.rotationDegrees(swing + tumble));
            ps.mulPose(Axis.XP.rotationDegrees(swing * 0.6f + tumble * 0.4f));
            ps.translate(0, -SWING_PIVOT * open, 0);
        }

        GeoModel crate = ModModels.crate();
        float reveal = -1f;
        if (landed && entity.getOpenAt() >= 0 && entity.level() != null) {
            reveal = (float) (entity.level().getGameTime() - entity.getOpenAt()) + pt;
        }
        pose(crate, reveal, entity.gearPieces());
        ResourceLocation tex = ModModels.texture("supply_crate");
        ps.pushPose();
        ps.scale(-1f, -1f, 1f);
        crate.renderList(crate.solid, ps, buffers.getBuffer(RenderType.entityCutout(tex)), light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        long t = entity.tickCount;
        if (t % 30 < 2 || t % 30 == 4) {
            crate.renderPart("emit_strobe", ps, buffers.getBuffer(RenderType.eyes(tex)), LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        }
        ps.popPose();

        // canopies: streamers right after the exit, inflated a second later, collapsing downwind once landed
        if (age > 6f) {
            float since = landed ? entity.getLandedTicks() + pt : 0f;
            float collapse = landed ? Mth.clamp(since / COLLAPSE_TICKS, 0f, 1f) : 0f;
            float alpha = landed ? 1f - Mth.clamp((since - COLLAPSE_TICKS - CHUTE_STAYS) / CHUTE_FADE, 0f, 1f) : 1f;
            ParachuteRenderer.render(ps, buffers, light, new ParachuteRenderer.State(inflate, age, collapse, alpha));
        }
        ps.popPose();
        super.render(entity, yaw, pt, ps, buffers, light);
    }

    /** The canopies are gone for good after this many ticks on the ground. */
    public static int chuteLifetime() {
        return COLLAPSE_TICKS + CHUTE_STAYS + CHUTE_FADE;
    }

    @Override
    public ResourceLocation getTextureLocation(SupplyCrateEntity entity) {
        return ModModels.texture("supply_crate");
    }
}
