package fr.heraliz.airstrike.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heraliz.airstrike.client.ClientStrikes;
import fr.heraliz.airstrike.client.fx.Flares;
import fr.heraliz.airstrike.client.model.GeoModel;
import fr.heraliz.airstrike.client.model.ModModels;
import fr.heraliz.airstrike.strike.Drop;
import fr.heraliz.airstrike.strike.FlightPath;
import fr.heraliz.airstrike.strike.OrdnanceKind;
import fr.heraliz.airstrike.strike.PlaneType;
import fr.heraliz.airstrike.strike.Strike;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import fr.heraliz.airstrike.client.model.GeoPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Renders aircraft, falling ordnance, tracers and the target marker (RenderLevelStageEvent.AFTER_ENTITIES). */
public final class StrikeRenderer {
    private static final int SKY_LIGHT = LightTexture.pack(0, 15);
    private static final double[] ORI = new double[3];
    private static final double[] PREV = new double[3];
    private static final String[] AILERONS_R = {"body/wing_r/outer_r/aileron_r0", "body/wing_r/outer_r/aileron_r1"};
    private static final String[] AILERONS_L = {"body/wing_l/outer_l/aileron_l0", "body/wing_l/outer_l/aileron_l1"};
    private static final String[] HARDPOINTS = {"hp_r0", "hp_r1", "hp_r2", "hp_r3", "hp_l0", "hp_l1", "hp_l2", "hp_l3"};
    /** Ground shadows: top view silhouettes (tools/modelgen/shadows.py), half size of the square in blocks. */
    private static final net.minecraft.resources.ResourceLocation JET_SHADOW = ModModels.texture("shadow_attack_jet");
    private static final net.minecraft.resources.ResourceLocation CARGO_SHADOW = ModModels.texture("shadow_cargo_plane");
    private static final float JET_SHADOW_HALF = 162.54f / 16f;
    private static final float CARGO_SHADOW_HALF = 178.20f / 16f * 2f;
    private static final int SHADOW_GRID = 6;
    private static final float[] SHADOW_X = new float[(SHADOW_GRID + 1) * (SHADOW_GRID + 1)],
            SHADOW_Y = new float[SHADOW_X.length], SHADOW_Z = new float[SHADOW_X.length];
    private static final net.minecraft.resources.ResourceLocation FLASH = ModModels.texture("designator_display");

    private StrikeRenderer() {
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ClientStrikes.all().isEmpty()) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack ps = event.getPoseStack();
        float pt = event.getPartialTick();
        Frustum frustum = event.getFrustum();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float sky = Mth.clamp((mc.level.getSkyDarken(pt) - 0.2f) / 0.8f, 0f, 1f) * 0.85f + 0.15f;
        try {
            for (ClientStrikes.ClientStrike cs : ClientStrikes.all()) {
                double t = cs.time(pt);
                Strike s = cs.strike;
                if (t >= 0 && t <= s.path.duration) {
                    Vec3 pos = s.path.position(t);
                    boolean cargo = s.type.plane == PlaneType.CARGO;
                    if (frustum.isVisible(new AABB(pos, pos).inflate(cargo ? 40 : 22))) {
                        if (cargo) renderCargo(cs, t, pos, ps, buffers, cam);
                        else renderJet(cs, t, pos, ps, buffers, cam);
                    }
                    renderShadow(cs, t, pos, cargo, ps, buffers, cam, frustum, sky);
                }
                Flares.render(cs, t, ps, buffers, cam, frustum, sky);
                renderDrops(cs, t, ps, buffers, cam, frustum);
                if (cs.isMine() && t < s.firstImpactTime() + 10) renderMarker(cs, t, ps, buffers, cam);
            }
        } finally {
            buffers.endBatch();
        }
    }

    // ------------------------------------------------------------------------------------------------------------

    private static void pushAircraft(PoseStack ps, Vec3 pos, Vec3 cam, double yaw, double pitch, double roll, float scale) {
        ps.pushPose();
        ps.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
        ps.mulPose(Axis.YP.rotationDegrees((float) (180.0 - yaw)));
        ps.mulPose(Axis.XP.rotationDegrees((float) -pitch));
        ps.mulPose(Axis.ZP.rotationDegrees((float) -roll));
        ps.scale(-scale, -scale, scale);
    }

    private static void setRot(GeoPart p, float x, float y, float z) {
        if (p == null) return;
        p.xRot = x;
        p.yRot = y;
        p.zRot = z;
    }

    private static void renderJet(ClientStrikes.ClientStrike cs, double t, Vec3 pos, PoseStack ps,
                                  MultiBufferSource buffers, Vec3 cam) {
        GeoModel m = ModModels.jet();
        FlightPath path = cs.strike.path;
        path.orientation(t, ORI);
        double yaw = ORI[0], pitch = ORI[1], roll = ORI[2];
        double[] prev = PREV;
        path.orientation(t - 1, prev);
        double rollRate = roll - prev[2];
        double pitchRate = pitch - prev[1];
        double yawRate = Mth.wrapDegrees(yaw - prev[0]);

        // --- animation
        float spin = (float) (t * 1.7);
        GeoPart fanR = m.part("body/engine_r/fan_r"), fanL = m.part("body/engine_l/fan_l");
        if (fanR != null) fanR.zRot = spin;
        if (fanL != null) fanL.zRot = -spin;
        float ail = (float) Math.toRadians(Mth.clamp(rollRate * 6.0, -18, 18));
        for (String a : AILERONS_R) setRot(m.part(a), ail, 0, 0);
        for (String a : AILERONS_L) setRot(m.part(a), -ail, 0, 0);
        setRot(m.part("body/tail/elevator"), (float) Math.toRadians(Mth.clamp(pitchRate * 8.0, -15, 15)), 0, 0);
        float rud = (float) Math.toRadians(Mth.clamp(yawRate * 4.0, -12, 12));
        setRot(m.part("body/tail/fin_r/rudder_r"), 0, rud, 0);
        setRot(m.part("body/tail/fin_l/rudder_l"), 0, rud, 0);
        // the pilot looks at the target
        Vec3 toTarget = cs.strike.target.subtract(pos);
        double rel = Mth.wrapDegrees(FlightPath.yawOf(toTarget.x, toTarget.z) - yaw);
        setRot(m.part("body/cockpit/pilot_head"), 0.25f, (float) Math.toRadians(Mth.clamp(rel, -75, 75)), 0);

        pushAircraft(ps, pos, cam, yaw, pitch, roll, 1.0f);
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutout(ModModels.texture("attack_jet")));
        m.renderList(m.solid, ps, solid, SKY_LIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);

        // stores under the wings
        GeoModel ord = ModModels.ordnance();
        OrdnanceKind[] loadout = cs.strike.type.loadout();
        VertexConsumer ordVc = buffers.getBuffer(RenderType.entityCutout(ModModels.texture("ordnance")));
        for (int i = 0; i < 8; i++) {
            if (loadout[i] == null) continue;
            if (t >= cs.releaseAt[i]) continue;
            Vec3 hp = m.point(HARDPOINTS[i]);
            ps.pushPose();
            ps.translate(-hp.x / 16.0, -hp.y / 16.0, hp.z / 16.0);
            ord.renderPart(loadout[i].part, ps, ordVc, SKY_LIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            ps.popPose();
        }

        VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucentCull(ModModels.texture("attack_jet")));
        m.renderList(m.glass, ps, glass, SKY_LIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);

        VertexConsumer emissive = buffers.getBuffer(RenderType.eyes(ModModels.texture("attack_jet")));
        long gt = (long) Math.floor(t);
        m.renderPart("emit_nav_l", ps, emissive, SKY_LIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        m.renderPart("emit_nav_r", ps, emissive, SKY_LIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        m.renderPart("emit_form", ps, emissive, SKY_LIGHT, OverlayTexture.NO_OVERLAY, 0.55f, 0.55f, 0.55f, 1);
        m.renderPart("emit_exhaust", ps, emissive, SKY_LIGHT, OverlayTexture.NO_OVERLAY, 0.7f, 0.7f, 0.7f, 1);
        if (gt % 24 < 2 || (gt + 5) % 24 < 1) m.renderPart("emit_strobe", ps, emissive, SKY_LIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        float beacon = (float) Math.max(0, Math.sin(t * 0.45));
        if (beacon > 0.05f) m.renderPart("emit_beacon", ps, emissive, SKY_LIGHT, OverlayTexture.NO_OVERLAY, beacon, beacon, beacon, 1);
        if (cs.isFiring(t) && (gt % 2 == 0)) {
            GeoPart flash = m.topLevel.get("emit_flash");
            if (flash != null) {
                flash.zRot = (float) (t * 2.3);
                flash.render(ps, emissive, SKY_LIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
                flash.zRot = 0;
            }
        }
        ps.popPose();
        if (cs.isFiring(t)) renderMuzzle(cs, t, pos, ps, buffers, cam);
    }

    /** Cannon fire: a flickering star of flame at the muzzle and a tongue of fire ahead of the nose. */
    private static void renderMuzzle(ClientStrikes.ClientStrike cs, double t, Vec3 pos, PoseStack ps,
                                     MultiBufferSource buffers, Vec3 cam) {
        FlightPath path = cs.strike.path;
        double[] g = fr.heraliz.airstrike.strike.AircraftData.JET_GUN;
        Vec3 muzzle = pos.add(path.frameOffset(t, g[0], g[1], g[2])).subtract(cam);
        Vec3 fwd = path.position(t + 0.5).subtract(path.position(t - 0.5)).normalize();
        // fast irregular flicker (the cannon fires ~65 rounds per second)
        double ph = t * 9.7;
        float k = 0.55f + 0.45f * (float) Math.abs(Math.sin(ph) * Math.sin(ph * 1.7 + 1.1));
        Matrix4f mat = ps.last().pose();
        VertexConsumer fire = buffers.getBuffer(ModRenderTypes.GLOW);
        Vec3 tip = muzzle.add(fwd.scale(3.0 + 3.0 * k));
        // ribbon(a, b): transparent at a, opaque at b
        ribbon(fire, mat, tip, muzzle, 1.1f * k, 1.0f, 0.55f, 0.15f, 0.9f);
        ribbon(fire, mat, muzzle.add(fwd.scale(1.8 + 1.6 * k)), muzzle, 0.45f * k, 1.0f, 0.92f, 0.65f, 1.0f);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(FLASH));
        org.joml.Vector3f c = new org.joml.Vector3f((float) muzzle.x, (float) muzzle.y, (float) muzzle.z);
        Flares.billboard(glow, mat, ps.last().normal(), c, camera.getLeftVector(), camera.getUpVector(), 10f * k, 1f * k, 0.62f * k, 0.22f * k);
        Flares.billboard(glow, mat, ps.last().normal(), c, camera.getLeftVector(), camera.getUpVector(), 4f * k, k, 0.95f * k, 0.75f * k);
    }

    /**
     * Soft shadow of the aircraft on the ground when it flies low: the silhouette texture laid on a small grid that
     * follows the terrain height, fainter and blurrier the higher the aircraft.
     */
    private static void renderShadow(ClientStrikes.ClientStrike cs, double t, Vec3 pos, boolean cargo, PoseStack ps,
                                     MultiBufferSource buffers, Vec3 cam, Frustum frustum, float sky) {
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(pos.x), Mth.floor(pos.z));
        double alt = pos.y - ground;
        if (alt < 0 || alt > 75) return;
        float a = (float) (0.55 * Mth.clamp((75 - alt) / 45.0, 0, 1)) * (sky - 0.15f) / 0.85f;
        if (a < 0.03f) return;
        float half = (cargo ? CARGO_SHADOW_HALF : JET_SHADOW_HALF) * (float) (1.0 + alt / 140.0);
        if (!frustum.isVisible(new AABB(pos.x - half, ground - 2, pos.z - half, pos.x + half, ground + 4, pos.z + half))) return;
        cs.strike.path.orientation(t, ORI);
        double yaw = Math.toRadians(180.0 - ORI[0]);
        float cos = (float) Math.cos(yaw), sin = (float) Math.sin(yaw);
        // a banked aircraft casts a narrower shadow
        float hx = half * (float) Math.max(0.3, Math.abs(Math.cos(Math.toRadians(ORI[2])))), hz = half;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityShadow(cargo ? CARGO_SHADOW : JET_SHADOW));
        Matrix4f mat = ps.last().pose();
        org.joml.Matrix3f nm = ps.last().normal();
        final int n = SHADOW_GRID;
        float[] hy = SHADOW_Y, wx = SHADOW_X, wz = SHADOW_Z;
        for (int j = 0; j <= n; j++) {
            for (int i = 0; i <= n; i++) {
                float lx = (-1f + 2f * i / n) * hx, lz = (-1f + 2f * j / n) * hz;
                // same yaw as the model (natural x right, z towards the tail)
                float x = (float) pos.x + lx * cos + lz * sin, z = (float) pos.z - lx * sin + lz * cos;
                int idx = j * (n + 1) + i;
                wx[idx] = x;
                wz[idx] = z;
                hy[idx] = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) + 0.02f;
            }
        }
        int light = LightTexture.FULL_BRIGHT, ov = OverlayTexture.NO_OVERLAY;
        // same winding as the vanilla shadows: the render type culls faces seen from below
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                for (int v = 0; v < 4; v++) {
                    int ci = i + (v >= 2 ? 1 : 0), cj = j + (v == 1 || v == 2 ? 1 : 0);
                    int id = cj * (n + 1) + ci;
                    vc.vertex(mat, (float) (wx[id] - cam.x), (float) (hy[id] - cam.y), (float) (wz[id] - cam.z))
                            .color(1f, 1f, 1f, a).uv(ci / (float) n, cj / (float) n).overlayCoords(ov).uv2(light)
                            .normal(nm, 0, 1, 0).endVertex();
                }
            }
        }
    }

    private static void renderCargo(ClientStrikes.ClientStrike cs, double t, Vec3 pos, PoseStack ps,
                                    MultiBufferSource buffers, Vec3 cam) {
        GeoModel m = ModModels.cargo();
        FlightPath path = cs.strike.path;
        path.orientation(t, ORI);
        double tDrop = 1e9;
        for (Drop d : cs.strike.drops) if (d.kind == OrdnanceKind.CRATE) tDrop = d.t0;

        float spin = (float) (t * 1.35);
        for (int i = 0; i < 4; i++) {
            GeoPart prop = m.part("prop_" + i);
            if (prop != null) prop.zRot = spin + i * 0.7f;
            GeoPart blur = m.part("blur_" + i);
            if (blur != null) blur.zRot = -spin * 0.37f;
        }
        // rear ramp: opens 5 s before the drop, closes 3 s after
        double open = Mth.clamp((t - (tDrop - 100)) / 30.0, 0, 1) * Mth.clamp(((tDrop + 60) - t) / 30.0, 0, 1);
        setRot(m.part("body/ramp_mount/ramp"), (float) Math.toRadians(-21 * open), 0, 0);

        float scale = CARGO_SCALE;
        pushAircraft(ps, pos, cam, ORI[0], ORI[1], ORI[2], scale);
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutout(ModModels.texture("cargo_plane")));
        m.renderList(m.solid, ps, solid, SKY_LIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);

        // the crate sliding out of the cargo bay
        if (t < tDrop) {
            Vec3 bay = m.point("cargo_bay");
            double slide = Mth.clamp((t - (tDrop - 16)) / 16.0, 0, 1);
            ps.pushPose();
            ps.translate(-bay.x / 16.0, -bay.y / 16.0, (bay.z + slide * slide * 60.0) / 16.0);
            ps.scale(1.0f / scale, 1.0f / scale, 1.0f / scale);
            GeoModel crate = ModModels.crate();
            SupplyCrateRenderer.pose(crate, -1f, fr.heraliz.airstrike.entity.SupplyCrateEntity.GEAR_PIECES);
            crate.renderList(crate.solid, ps, buffers.getBuffer(RenderType.entityCutout(ModModels.texture("supply_crate"))),
                    SKY_LIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            ps.popPose();
        }

        VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucentCull(ModModels.texture("cargo_plane")));
        m.renderList(m.glass, ps, glass, SKY_LIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        VertexConsumer emissive = buffers.getBuffer(RenderType.eyes(ModModels.texture("cargo_plane")));
        long gt = (long) Math.floor(t);
        m.renderPart("emit_nav_l", ps, emissive, SKY_LIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        m.renderPart("emit_nav_r", ps, emissive, SKY_LIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        if (gt % 30 < 2) m.renderPart("emit_strobe", ps, emissive, SKY_LIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        float beacon = (float) Math.max(0, Math.sin(t * 0.4));
        if (beacon > 0.05f) m.renderPart("emit_beacon", ps, emissive, SKY_LIGHT, OverlayTexture.NO_OVERLAY, beacon, beacon, beacon, 1);
        if (open > 0.5) m.renderPart("emit_bay", ps, emissive, SKY_LIGHT, OverlayTexture.NO_OVERLAY, 0.9f, 0.9f, 0.9f, 1);
        ps.popPose();
    }

    /** The cargo model is authored at half scale (8 texels per block) and rendered twice as big. */
    public static final float CARGO_SCALE = 2.0f;

    // ------------------------------------------------------------------------------------------------------------

    private static void renderDrops(ClientStrikes.ClientStrike cs, double t, PoseStack ps, MultiBufferSource buffers,
                                    Vec3 cam, Frustum frustum) {
        GeoModel ord = null;
        VertexConsumer vc = null;
        VertexConsumer glow = null;
        Matrix4f mat = null;
        for (Drop d : cs.strike.drops) {
            if (t < d.t0 || t > d.t1) continue;
            if (d.kind == OrdnanceKind.CRATE) continue;
            Vec3 p = d.position(t);
            if (!frustum.isVisible(new AABB(p, p).inflate(d.kind == OrdnanceKind.BULLET ? 8 : 3))) continue;
            if (d.kind == OrdnanceKind.BULLET) {
                if (glow == null) {
                    glow = buffers.getBuffer(ModRenderTypes.GLOW);
                    mat = ps.last().pose();
                }
                Vec3 dir = d.velocity(t).normalize();
                Vec3 tail = p.subtract(dir.scale(5.5));
                ribbon(glow, mat, tail.subtract(cam), p.subtract(cam), 0.12f, 1.0f, 0.78f, 0.35f, 0.9f);
                ribbon(glow, mat, tail.subtract(cam), p.subtract(cam), 0.35f, 1.0f, 0.45f, 0.1f, 0.35f);
                continue;
            }
            if (ord == null) {
                ord = ModModels.ordnance();
                vc = buffers.getBuffer(RenderType.entityCutout(ModModels.texture("ordnance")));
            }
            Vec3 v = d.velocity(t);
            double h = Math.sqrt(v.x * v.x + v.z * v.z);
            double yaw = FlightPath.yawOf(v.x, v.z);
            double pitch = Math.toDegrees(Math.atan2(-v.y, Math.max(1e-4, h)));
            double tau = t - d.t0;
            double roll = 0;
            if (d.kind.wobbles) {
                roll = Math.sin(tau * 0.5 + d.x0) * 8.0;
                pitch += Math.sin(tau * 0.37 + d.z0) * (d.kind == OrdnanceKind.NAPALM ? 25.0 : 4.0);
            }
            if (d.kind == OrdnanceKind.BOMBLET) {
                roll = tau * 45.0;
                pitch = tau * 31.0;
            }
            // leave the pylon gently: the first few ticks the store keeps the aircraft attitude
            float s = 1.0f;
            ps.pushPose();
            ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            ps.mulPose(Axis.YP.rotationDegrees((float) (180.0 - yaw)));
            ps.mulPose(Axis.XP.rotationDegrees((float) -pitch));
            ps.mulPose(Axis.ZP.rotationDegrees((float) -roll));
            ps.scale(-s, -s, s);
            ord.renderPart(d.kind.part, ps, vc, SKY_LIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            ps.popPose();
        }
    }

    /** Camera facing quad between two points (relative to the camera). */
    private static void ribbon(VertexConsumer vc, Matrix4f mat, Vec3 a, Vec3 b, float width, float r, float g, float bl, float alpha) {
        Vec3 dir = b.subtract(a);
        Vec3 side = dir.cross(a).normalize().scale(width);
        if (!Double.isFinite(side.x)) return;
        vc.vertex(mat, (float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z)).color(r, g, bl, 0f).endVertex();
        vc.vertex(mat, (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z)).color(r, g, bl, 0f).endVertex();
        vc.vertex(mat, (float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z)).color(r, g, bl, alpha).endVertex();
        vc.vertex(mat, (float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z)).color(r, g, bl, alpha).endVertex();
    }

    /** Pulsing red diamond on the designated point, visible through terrain, for the caller only. */
    private static void renderMarker(ClientStrikes.ClientStrike cs, double t, PoseStack ps, MultiBufferSource buffers, Vec3 cam) {
        Vec3 target = cs.strike.target;
        Vec3 rel = target.subtract(cam);
        double dist = rel.length();
        if (dist < 1.0) return;
        VertexConsumer vc = buffers.getBuffer(ModRenderTypes.MARKER);
        Matrix4f mat = ps.last().pose();
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        org.joml.Vector3f up = camera.getUpVector();
        org.joml.Vector3f left = camera.getLeftVector();
        float size = (float) (dist * 0.012 * (1.0 + 0.25 * Math.sin(t * 0.5)));
        float a = 0.85f;
        Vec3 u = new Vec3(up.x(), up.y(), up.z()).scale(size);
        Vec3 l = new Vec3(left.x(), left.y(), left.z()).scale(size);
        // diamond outline made of 4 thin quads
        Vec3[] pts = {rel.add(u), rel.add(l), rel.subtract(u), rel.subtract(l)};
        for (int i = 0; i < 4; i++) {
            Vec3 p0 = pts[i], p1 = pts[(i + 1) % 4];
            Vec3 inward = rel.subtract(p0.add(p1).scale(0.5)).normalize().scale(size * 0.18);
            quad(vc, mat, p0, p1, p1.add(inward), p0.add(inward), 1.0f, 0.12f, 0.08f, a);
        }
        Vec3 c0 = rel.add(u.scale(0.15)), c1 = rel.add(l.scale(0.15)), c2 = rel.subtract(u.scale(0.15)), c3 = rel.subtract(l.scale(0.15));
        quad(vc, mat, c0, c1, c2, c3, 1.0f, 0.2f, 0.1f, a);
    }

    private static void quad(VertexConsumer vc, Matrix4f mat, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float r, float g, float bl, float alpha) {
        vc.vertex(mat, (float) a.x, (float) a.y, (float) a.z).color(r, g, bl, alpha).endVertex();
        vc.vertex(mat, (float) b.x, (float) b.y, (float) b.z).color(r, g, bl, alpha).endVertex();
        vc.vertex(mat, (float) c.x, (float) c.y, (float) c.z).color(r, g, bl, alpha).endVertex();
        vc.vertex(mat, (float) d.x, (float) d.y, (float) d.z).color(r, g, bl, alpha).endVertex();
    }
}
