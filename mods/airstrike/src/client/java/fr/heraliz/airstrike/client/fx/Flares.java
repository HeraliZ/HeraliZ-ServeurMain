package fr.heraliz.airstrike.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.airstrike.client.ClientStrikes;
import fr.heraliz.airstrike.client.model.ModModels;
import fr.heraliz.airstrike.client.render.ModRenderTypes;
import fr.heraliz.airstrike.strike.FlightPath;
import fr.heraliz.airstrike.strike.PlaneType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Decoy flares fired by the aircraft on their way out: two salvos of eight, ejected left and right, burning white hot
 * while they fall and leaving curved smoke trails ("angel wings"). Positions come from a closed form (drag and
 * gravity), so nothing is simulated or spawned: every frame draws a glow sprite per flare and one soft ribbon per
 * trail.
 */
public final class Flares {
    public static final int PER_SALVO = 8;
    private static final double DRAG = 0.075, GRAVITY = 0.028;
    private static final int BURN = 70, TRAIL = 95;
    private static final int SAMPLES = 24;
    private static final ResourceLocation GLOW = ModModels.texture("designator_display");
    /** Flare sprite: left square of the display atlas cell 12. */
    private static final float U0 = 0f, U1 = 0.125f, V0 = 0.75f, V1 = 1f;
    private static final double[] ORI = new double[3];
    private static final Vector3f[] PTS = new Vector3f[SAMPLES + 1];
    private static final float[] WIDTH = new float[SAMPLES + 1], ALPHA = new float[SAMPLES + 1];
    private static final Vector3f SIDE = new Vector3f(), DIR = new Vector3f(), SA = new Vector3f(), SB = new Vector3f(),
            HEAD = new Vector3f();

    static {
        for (int i = 0; i < PTS.length; i++) PTS[i] = new Vector3f();
    }

    private Flares() {
    }

    private static float rnd(long seed, int i, int k) {
        long h = seed * 0x9E3779B97F4A7C15L + i * 0xBF58476D1CE4E5B9L + k * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        h *= 0x7FB5D329728EA185L;
        h ^= h >>> 27;
        return (h >>> 40) / (float) (1L << 24);
    }

    /** One flare of a salvo: where it starts, how it leaves the aircraft. */
    private record Launch(Vec3 p0, Vec3 v0, double delay, double burn) {
    }

    private static Launch launch(ClientStrikes.ClientStrike cs, int salvo, int i, double ts) {
        FlightPath path = cs.strike.path;
        boolean cargo = cs.strike.type.plane == PlaneType.CARGO;
        long seed = cs.strike.seed * 31 + salvo;
        path.orientation(ts, ORI);
        int side = i < PER_SALVO / 2 ? -1 : 1;
        int j = i % (PER_SALVO / 2);
        Vec3 pos = path.position(ts);
        Vec3 belly = FlightPath.rotate(side * (cargo ? 3.0 : 0.8), cargo ? -3.0 : -1.0, cargo ? 8.0 : 1.5, ORI[0], ORI[1], ORI[2]);
        Vec3 eject = FlightPath.rotate(side * (0.30 + 0.07 * j + 0.06 * rnd(seed, i, 0)), -0.08 - 0.06 * rnd(seed, i, 1) + 0.03 * j,
                0.04, ORI[0], ORI[1], ORI[2]);
        Vec3 vPlane = path.position(ts + 1).subtract(pos);
        return new Launch(pos.add(belly), eject.add(vPlane.scale(0.55)), j * 2.5, BURN * (0.85 + 0.3 * rnd(seed, i, 2)));
    }

    /** Position tau ticks after the flare left the aircraft (drag + gravity, closed form). */
    private static void position(Launch l, double tau, Vector3f out, Vec3 cam) {
        double e = Math.exp(-DRAG * tau);
        double a = (1 - e) / DRAG;
        out.set((float) (l.p0.x + l.v0.x * a - cam.x),
                (float) (l.p0.y + l.v0.y * a - GRAVITY / DRAG * (tau - a) - cam.y),
                (float) (l.p0.z + l.v0.z * a - cam.z));
    }

    public static void render(ClientStrikes.ClientStrike cs, double t, PoseStack ps, MultiBufferSource buffers, Vec3 cam,
                              Frustum frustum, float sky) {
        if (cs.flareSalvos.isEmpty()) return;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f up = camera.getUpVector(), left = camera.getLeftVector();
        Matrix4f mat = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        VertexConsumer smoke = null;
        for (int s = 0; s < cs.flareSalvos.size(); s++) {
            double ts = cs.flareSalvos.get(s);
            double age = t - ts;
            if (age < 0 || age > BURN * 1.2 + TRAIL + 10) continue;
            Vec3 at = cs.strike.path.position(ts);
            if (at.distanceToSqr(cam) > 450 * 450) continue;
            if (!frustum.isVisible(new AABB(at, at).inflate(40, 60, 40).move(0, -25, 0))) continue;
            if (smoke == null) smoke = buffers.getBuffer(ModRenderTypes.SMOKE);
            for (int i = 0; i < PER_SALVO; i++) {
                Launch l = launch(cs, s, i, ts);
                double tau = age - l.delay;
                if (tau < 0) continue;
                trail(smoke, mat, l, tau, cam, sky);
            }
        }
        if (smoke == null) return;
        // the burning heads, additive, after the smoke
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW));
        Vector3f head = HEAD;
        for (int s = 0; s < cs.flareSalvos.size(); s++) {
            double ts = cs.flareSalvos.get(s);
            double age = t - ts;
            if (age < 0 || age > BURN * 1.2 + 2) continue;
            for (int i = 0; i < PER_SALVO; i++) {
                Launch l = launch(cs, s, i, ts);
                double tau = age - l.delay;
                if (tau < 0 || tau > l.burn) continue;
                position(l, tau, head, cam);
                float fade = (float) Mth.clamp((l.burn - tau) / 12.0, 0, 1);
                float flicker = 0.8f + 0.2f * Mth.sin((float) (t * 2.7 + i * 1.3)) * Mth.sin((float) (t * 4.1 + s));
                // the sprite's hot core is about a quarter of the quad, the rest is the halo
                float size = 6.5f * flicker * (0.5f + 0.5f * fade);
                billboard(glow, mat, nm, head, left, up, size, 1f * fade, 0.78f * fade, 0.5f * fade);
                billboard(glow, mat, nm, head, left, up, size * 0.45f, fade, fade, fade);
            }
        }
    }

    /** Soft ribbon along the smoke left behind while the flare burned (older smoke wider and fainter). */
    private static void trail(VertexConsumer vc, Matrix4f mat, Launch l, double tau, Vec3 cam, float sky) {
        double newest = Math.min(tau, l.burn);
        double oldest = Math.max(0, tau - TRAIL);
        if (newest - oldest < 0.5) return;
        int n = 0;
        for (int k = 0; k <= SAMPLES; k++) {
            double s = oldest + (newest - oldest) * k / SAMPLES;
            double age = tau - s;
            position(l, s, PTS[n], cam);
            // the smoke drifts with the wind and rises a little as it ages
            PTS[n].add((float) (0.012 * age), (float) (0.016 * age), (float) (0.007 * age));
            WIDTH[n] = (float) Math.min(3.8, 0.35 + 0.045 * age);
            ALPHA[n] = (float) (0.62 * Math.exp(-age / 48.0) * Mth.clamp(s / 2.0, 0.3, 1.0));
            n++;
        }
        float c = 0.92f * sky;
        Vector3f side = SIDE, dir = DIR, sa = SA, sb = SB;
        for (int k = 0; k < n - 1; k++) {
            Vector3f a = PTS[k], b = PTS[k + 1];
            b.sub(a, dir);
            // side vector perpendicular to the trail and to the view (camera at the origin)
            dir.cross(a, side);
            if (side.lengthSquared() < 1e-8f) continue;
            side.normalize();
            side.mul(WIDTH[k] * 0.5f, sa);
            side.mul(WIDTH[k + 1] * 0.5f, sb);
            float aa = ALPHA[k], ab = ALPHA[k + 1];
            if (aa < 0.01f && ab < 0.01f) continue;
            vert(vc, mat, a.x - sa.x, a.y - sa.y, a.z - sa.z, c, 0f);
            vert(vc, mat, b.x - sb.x, b.y - sb.y, b.z - sb.z, c, 0f);
            vert(vc, mat, b.x, b.y, b.z, c, ab);
            vert(vc, mat, a.x, a.y, a.z, c, aa);
            vert(vc, mat, a.x, a.y, a.z, c, aa);
            vert(vc, mat, b.x, b.y, b.z, c, ab);
            vert(vc, mat, b.x + sb.x, b.y + sb.y, b.z + sb.z, c, 0f);
            vert(vc, mat, a.x + sa.x, a.y + sa.y, a.z + sa.z, c, 0f);
        }
    }

    private static void vert(VertexConsumer vc, Matrix4f mat, float x, float y, float z, float c, float a) {
        vc.vertex(mat, x, y, z).color(c, c, c * 1.02f, a).endVertex();
    }

    /** Camera facing sprite of the flare glow (eyes render type: additive, full bright). */
    public static void billboard(VertexConsumer vc, Matrix4f mat, Matrix3f nm, Vector3f c, Vector3f left, Vector3f up,
                                 float size, float r, float g, float b) {
        float h = size * 0.5f;
        float lx = left.x() * h, ly = left.y() * h, lz = left.z() * h;
        float ux = up.x() * h, uy = up.y() * h, uz = up.z() * h;
        int light = LightTexture.FULL_BRIGHT, ov = OverlayTexture.NO_OVERLAY;
        vc.vertex(mat, c.x + lx - ux, c.y + ly - uy, c.z + lz - uz).color(r, g, b, 1f).uv(U0, V1).overlayCoords(ov).uv2(light).normal(nm, 0, 1, 0).endVertex();
        vc.vertex(mat, c.x - lx - ux, c.y - ly - uy, c.z - lz - uz).color(r, g, b, 1f).uv(U1, V1).overlayCoords(ov).uv2(light).normal(nm, 0, 1, 0).endVertex();
        vc.vertex(mat, c.x - lx + ux, c.y - ly + uy, c.z - lz + uz).color(r, g, b, 1f).uv(U1, V0).overlayCoords(ov).uv2(light).normal(nm, 0, 1, 0).endVertex();
        vc.vertex(mat, c.x + lx + ux, c.y + ly + uy, c.z + lz + uz).color(r, g, b, 1f).uv(U0, V0).overlayCoords(ov).uv2(light).normal(nm, 0, 1, 0).endVertex();
    }
}
