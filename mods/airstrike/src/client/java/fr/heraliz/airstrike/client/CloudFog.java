package fr.heraliz.airstrike.client;

import com.mojang.blaze3d.shaders.FogShape;
import fr.heraliz.airstrike.entity.EffectCloudEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * Thick fog while the camera is inside a smoke screen or a gas cloud. Seen from inside, a cloud is rendered by the
 * fog instead of hundreds of screen-filling puffs: the puffs close to the camera fade out and the ones hidden by the
 * fog are skipped ({@link #cullDistance}).
 */
public final class CloudFog {
    private static final float SMOKE_FAR = 6.5f;
    private static final float GAS_FAR = 13f;

    private static float smoke, smokeO, gas, gasO;
    /** Puffs further than this from the camera are fully fogged and not drawn (+inf when not in a cloud). */
    public static float cullDistance = Float.POSITIVE_INFINITY;
    /** Opacity multiplier of the big puffs: deep in the smoke the fog alone draws the cloud. */
    public static float puffVisibility = 1f;
    /** Extra near fade of the puffs inside the gas (fraction of the puff size). */
    public static float nearFade = 0f;

    private CloudFog() {
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        smokeO = smoke;
        gasO = gas;
        float ts = 0f, tg = 0f;
        if (mc.level != null && EffectCloudEntity.anyOnClient()) {
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            for (EffectCloudEntity e : mc.level.getEntitiesOfClass(EffectCloudEntity.class, new AABB(cam, cam).inflate(24))) {
                float f = e.fogDensityAt(cam);
                if (e.getKind() == EffectCloudEntity.SMOKE) ts = Math.max(ts, f);
                else if (e.getKind() == EffectCloudEntity.GAS) tg = Math.max(tg, f);
            }
        }
        smoke += (ts - smoke) * 0.14f;
        gas += (tg - gas) * 0.10f;
        if (smoke < 0.003f) smoke = 0f;
        if (gas < 0.003f) gas = 0f;
    }

    private static float smoothstep(float a, float b, float x) {
        return ease((x - a) / (b - a));
    }

    private static float ease(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    /** Interpolates distances geometrically: the fog closes in smoothly instead of all at once near the end. */
    private static float towards(float from, float to, float k) {
        from = Math.max(from, 0.5f);
        return (float) Math.exp(Mth.lerp(k, Math.log(from), Math.log(to)));
    }

    public static void onRenderFog(ViewportEvent.RenderFog event) {
        float pt = (float) event.getPartialTick();
        float s = ease(Mth.lerp(pt, smokeO, smoke)), g = ease(Mth.lerp(pt, gasO, gas));
        boolean terrain = event.getMode() == FogRenderer.FogMode.FOG_TERRAIN;
        if (terrain) {
            puffVisibility = (1f - smoothstep(0.3f, 0.75f, s)) * (1f - 0.6f * smoothstep(0.3f, 1f, g));
            nearFade = 0.9f * smoothstep(0.2f, 0.8f, g);
        }
        if (s <= 0.01f && g <= 0.01f) {
            if (terrain) cullDistance = Float.POSITIVE_INFINITY;
            return;
        }
        float far = event.getFarPlaneDistance(), near = event.getNearPlaneDistance();
        if (g > 0.01f) {
            far = towards(far, GAS_FAR, g);
            near = Mth.lerp(g, near, 0.5f);
        }
        if (s > 0.01f) {
            far = towards(far, SMOKE_FAR, s);
            near = Mth.lerp(s, near, 0f);
        }
        near = Math.min(near, far * 0.5f);
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(near);
        if (Math.max(s, g) > 0.3f) event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
        if (terrain) cullDistance = far + 1.5f;
    }

    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float pt = (float) event.getPartialTick();
        float s = ease(Mth.lerp(pt, smokeO, smoke)), g = ease(Mth.lerp(pt, gasO, gas));
        if (s <= 0.01f && g <= 0.01f) return;
        Minecraft mc = Minecraft.getInstance();
        float light = mc.level == null ? 1f : Mth.clamp((mc.level.getSkyDarken(pt) - 0.2f) / 0.8f, 0f, 1f) * 0.85f + 0.15f;
        float r = event.getRed(), gr = event.getGreen(), b = event.getBlue();
        if (g > 0.01f) {
            r = Mth.lerp(g, r, 0.60f * light);
            gr = Mth.lerp(g, gr, 0.66f * light);
            b = Mth.lerp(g, b, 0.36f * light);
        }
        if (s > 0.01f) {
            r = Mth.lerp(s, r, 0.80f * light);
            gr = Mth.lerp(s, gr, 0.80f * light);
            b = Mth.lerp(s, b, 0.82f * light);
        }
        event.setRed(r);
        event.setGreen(gr);
        event.setBlue(b);
    }

    public static void reset() {
        smoke = smokeO = gas = gasO = 0f;
        cullDistance = Float.POSITIVE_INFINITY;
        puffVisibility = 1f;
        nearFade = 0f;
    }
}
