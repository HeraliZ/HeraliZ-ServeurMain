package fr.heraliz.airstrike.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heraliz.airstrike.AirstrikeMod;
import fr.heraliz.airstrike.client.model.GeoModel;
import fr.heraliz.airstrike.client.model.GeoPart;
import fr.heraliz.airstrike.client.model.ModModels;
import fr.heraliz.airstrike.item.DesignatorItem;
import fr.heraliz.airstrike.registry.ModItems;
import fr.heraliz.airstrike.strike.StrikeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the binoculars (generated model, see mods/airstrike/outils/modele/jumelles.py), the loaded cartridge (the
 * payload item model laid flat in the bay) and the screen on the back of the module, which shows the logo of the loaded
 * cartridge. How they are held (both hands, first and third person) is in {@link DesignatorHold}.
 * <p>
 * Reload, done by the left hand while the right one holds the binoculars: it leaves its tube, presses the latch and the
 * hatch springs open, the old cartridge pops up, the hand catches it, flips it in the air, catches it again and pockets
 * it, comes back with the new one, shows it with a twirl, slides it in nose first, clicks it home, slams the hatch and
 * goes back to its tube; the screen then boots on the new cartridge. The hand stays on the left of the hatch so that
 * the arm never hides it.
 */
public final class DesignatorRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation TEXTURE = ModModels.texture("designator");
    private static final ResourceLocation SCREEN = ModModels.texture("designator_display");
    private static final ResourceLocation DUST = new ResourceLocation(AirstrikeMod.MODID, "textures/particle/smoke_1.png");
    // item space, 1/16 block - keep in sync with mods/airstrike/outils/modele/jumelles.py
    private static final Vector3f BAY = new Vector3f(8.0f, 11.31f, 7.45f);
    private static final Vector3f HINGE = new Vector3f(8.0f, 12.925f, 4.3f);
    private static final Vector3f LATCH = new Vector3f(8.0f, 13.55f, 11.15f);
    private static final float DISPLAY_X0 = 5.625f, DISPLAY_X1 = 9.875f, DISPLAY_Y0 = 9.875f, DISPLAY_Y1 = 12.0f;
    private static final float DISPLAY_Z = 11.2f;
    private static final float CART_SCALE = 0.37f;
    private static final float HATCH_OPEN_DEG = 112f;
    /** Payload item model centre (0..1 block space). */
    private static final float PAY_X = 8f / 16f, PAY_Y = 7.9f / 16f, PAY_Z = 8f / 16f;
    /** The hand grips the cartridge by its left side (cartridge frame), so that the cartridge stays visible. */
    private static final Vector3f GRIP = new Vector3f(-3.2f, 0.3f, 0.5f);
    /** Screen atlas cells (4 x 4 of 128 x 64): 0-7 cartridges, then empty, hatch open, loading, white. */
    private static final int CELL_EMPTY = 8, CELL_OPEN = 9, CELL_LOADING = 10, CELL_WHITE = 11, CELL_FLARE = 12;
    /** Where the left hand pockets and fetches the cartridges in third person (and for an item on its own). */
    static final Vector3f TP_OFF = new Vector3f(BAY).add(-14f, -8f, 6f);
    private static final Vector3f ITEM_OFF = new Vector3f(-12f, 2f, 15f);

    // reload timeline (progress 0..1)
    public static final float T_LATCH = 0.11f, T_OPEN = 0.14f, T_OPENED = 0.24f, T_POP = 0.20f, T_CATCH = 0.27f,
            T_RELEASE = 0.31f, T_CATCH2 = 0.38f, T_POCKET = 0.47f, T_SHOW = 0.55f, T_TWIRLED = 0.61f,
            T_NOSE = 0.66f, T_SEATED = 0.72f, T_CLICK = 0.76f, T_LID = 0.79f, T_SLAM = 0.84f, T_READY = 0.92f;

    private static final Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST};
    private static final RandomSource RANDOM = RandomSource.create();
    private static DesignatorRenderer instance;

    private DesignatorRenderer(Minecraft mc) {
        super(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
    }

    public static DesignatorRenderer get() {
        if (instance == null) instance = new DesignatorRenderer(Minecraft.getInstance());
        return instance;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Curves

    private static float clamp01(float x) {
        return Mth.clamp(x, 0f, 1f);
    }

    private static float smooth(float x) {
        x = clamp01(x);
        return x * x * (3f - 2f * x);
    }

    private static float easeOutBack(float x) {
        x = clamp01(x) - 1f;
        return 1f + 2.2f * x * x * x + 1.2f * x * x;
    }

    private static float u(float p, float a, float b) {
        return clamp01((p - a) / (b - a));
    }

    /** 0 outside [at, at + width], a sine bump inside. */
    public static float bump(float p, float at, float width) {
        float x = (p - at) / width;
        return x < 0f || x > 1f ? 0f : Mth.sin(x * Mth.PI);
    }

    private static Vector3f v(float x, float y, float z) {
        return new Vector3f(x, y, z);
    }

    private static Vector3f add(Vector3f a, float x, float y, float z) {
        return new Vector3f(a).add(x, y, z);
    }

    /** A key of a motion track: time, value, and whether the motion stops there (zero velocity). */
    private record Key(float t, Vector3f v, boolean stop) {
    }

    private static Key k(float t, Vector3f v) {
        return new Key(t, v, false);
    }

    private static Key stop(float t, Vector3f v) {
        return new Key(t, v, true);
    }

    /** Cubic Hermite spline through the keys (Catmull-Rom tangents, zero at the ends and at stops). */
    private static Vector3f track(float p, Key... keys) {
        int n = keys.length;
        if (p <= keys[0].t) return new Vector3f(keys[0].v);
        if (p >= keys[n - 1].t) return new Vector3f(keys[n - 1].v);
        int i = 0;
        while (p > keys[i + 1].t) i++;
        Key a = keys[i], b = keys[i + 1];
        float h = b.t - a.t, s = (p - a.t) / h;
        Vector3f m0 = tangent(keys, i), m1 = tangent(keys, i + 1);
        float s2 = s * s, s3 = s2 * s;
        float h00 = 2 * s3 - 3 * s2 + 1, h10 = s3 - 2 * s2 + s, h01 = -2 * s3 + 3 * s2, h11 = s3 - s2;
        return new Vector3f(a.v).mul(h00).add(new Vector3f(m0).mul(h10 * h))
                .add(new Vector3f(b.v).mul(h01)).add(new Vector3f(m1).mul(h11 * h));
    }

    private static Vector3f tangent(Key[] keys, int i) {
        if (i == 0 || i == keys.length - 1 || keys[i].stop) return new Vector3f();
        Key a = keys[i - 1], b = keys[i + 1];
        return new Vector3f(b.v).sub(a.v).div(b.t - a.t).mul(0.85f);
    }

    /** Hatch opening 0..1 (1 = HATCH_OPEN_DEG). */
    public static float hatchOpen(float p) {
        if (p >= 1f || p < T_OPEN) return 0f;
        if (p < T_OPENED) {
            // spring: fast rise, one overshoot, settles
            float s = u(p, T_OPEN, T_OPENED);
            return 1f - Mth.cos(5f * s) * (float) Math.exp(-4f * s);
        }
        if (p < T_LID) return 1f;
        if (p < T_SLAM) {
            float s = u(p, T_LID, T_SLAM);
            return 1f - s * s;
        }
        float s = u(p, T_SLAM, T_SLAM + 0.07f);
        return 0.05f * Mth.sin(s * Mth.PI) * (1f - s);
    }

    /** First person: how much the binoculars are brought up to show the bay. */
    public static float presentation(float p) {
        if (p >= 1f) return 0f;
        return smooth(p / 0.09f) * smooth((1f - p) / 0.10f);
    }

    /**
     * Extra motion of the binoculars during the reload, first person (translation in blocks, rotations in degrees):
     * {dx, dy, dz, pitch, yaw, roll}; side = 1 when held in the right hand.
     */
    public static float[] sway(float p, int side) {
        float[] o = new float[6];
        if (p >= 1f) return o;
        o[1] += -0.010f * bump(p, T_LATCH - 0.03f, 0.05f);                          // thumb on the latch
        o[3] += -4f * bump(p, T_OPEN, 0.07f);                                         // hatch springs
        o[1] += 0.006f * bump(p, T_OPEN, 0.07f);
        o[5] += side * 7f * bump(p, T_CATCH - 0.02f, 0.16f);                          // flip
        float show = bump(p, T_POCKET + 0.02f, 0.18f);                               // make room for the twirl
        o[1] += -0.035f * show;
        o[3] += -6f * show;
        o[5] += -side * 9f * show;
        float ins = bump(p, T_TWIRLED, 0.12f);                                        // nose in
        o[3] += 4f * ins;
        o[5] += side * 5f * ins;
        o[1] += -0.022f * bump(p, T_CLICK - 0.01f, 0.05f);                            // click
        o[3] += 3f * bump(p, T_CLICK - 0.01f, 0.05f);
        o[1] += -0.030f * bump(p, T_SLAM - 0.005f, 0.06f);                            // slam
        o[5] += -side * 3f * bump(p, T_SLAM - 0.005f, 0.06f);
        o[4] += -side * 14f * bump(p, T_SLAM + 0.02f, 0.14f);                         // flourish on the way back
        o[1] += 0.010f * bump(p, T_SLAM + 0.02f, 0.14f);
        return o;
    }

    /** Point of the top surface of the hatch, at distance d from the hinge (6.3: free edge), item units. */
    private static Vector3f lidPoint(float open, float d) {
        float a = (float) Math.toRadians(HATCH_OPEN_DEG * open);
        float y = 0.2f;
        return v(8.0f, HINGE.y + y * Mth.cos(a) + d * Mth.sin(a), HINGE.z - y * Mth.sin(a) + d * Mth.cos(a));
    }

    private static Quaternionf euler(Vector3f deg) {
        return new Quaternionf().rotationXYZ((float) Math.toRadians(deg.x), (float) Math.toRadians(deg.y),
                (float) Math.toRadians(deg.z));
    }

    /** Hand path and cartridges of the reload (item units). */
    public static final class Anim {
        public Vector3f hand;
        public Vector3f oldPos, newPos;
        public Quaternionf oldRot, newRot;
    }

    /** Hand position when holding a cartridge at pos with the (spin free) orientation rot. */
    private static Vector3f held(Vector3f pos, Quaternionf rot) {
        return new Vector3f(GRIP).rotate(rot).add(pos);
    }

    /**
     * Hand path and cartridges of the reload (item units). {@code off}: where cartridges are pocketed and fetched;
     * {@code start}: where the hand starts from and goes back to (its grip), or null to start from {@code off}.
     */
    public static Anim anim(float p, boolean hasOld, Vector3f off, Vector3f start) {
        Anim a = new Anim();
        Vector3f popTop = add(BAY, 0f, 3.0f, 0.3f);
        Vector3f release = add(BAY, -2.0f, 5.0f, 1.0f);
        Vector3f catchPos = add(BAY, -3.2f, 4.4f, 1.6f);
        Quaternionf popRot = euler(v(-14f, 0f, 0f));

        // ---- old cartridge: pops up, flipped in the air, pocketed
        if (hasOld && p < T_POCKET) {
            if (p < T_POP) {
                a.oldPos = new Vector3f(BAY);
                a.oldRot = new Quaternionf();
            } else if (p < T_CATCH) {
                float s = u(p, T_POP, T_CATCH);
                a.oldPos = new Vector3f(BAY).lerp(popTop, easeOutBack(s));
                a.oldRot = euler(v(-14f * smooth(s), 0f, 0f));
            } else if (p < T_RELEASE) {
                float s = smooth(u(p, T_CATCH, T_RELEASE));
                a.oldPos = new Vector3f(popTop).lerp(release, s);
                a.oldRot = euler(v(-14f - 6f * s, 0f, 0f));
            } else if (p < T_CATCH2) {
                float s = u(p, T_RELEASE, T_CATCH2);
                a.oldPos = new Vector3f(release).lerp(catchPos, s).add(0f, 3.0f * 4f * s * (1f - s), 0f);
                a.oldRot = euler(v(-20f - 360f * smooth(s), 30f * s, 0f));
            } else {
                float s = smooth(u(p, T_CATCH2, T_POCKET));
                Quaternionf r = euler(v(-20f - 40f * s, 30f, 0f));
                Vector3f offCart = new Vector3f(off).sub(new Vector3f(GRIP).rotate(r));
                a.oldPos = track(p, stop(T_CATCH2, catchPos), k(T_CATCH2 + 0.04f, add(catchPos, -2.0f, -1.0f, 0.8f)),
                        stop(T_POCKET, offCart));
                a.oldRot = r;
            }
        }

        // ---- new cartridge: shown with a twirl, slid in nose first, seated
        Quaternionf baseNew = null;
        if (p >= T_POCKET) {
            Vector3f show = add(BAY, -5.5f, 6.0f, 2.5f);
            Vector3f show2 = add(BAY, -4.0f, 5.2f, 2.0f);
            Vector3f nose = add(BAY, -0.6f, 3.0f, 1.2f);
            Vector3f slide = add(BAY, 0f, 0.9f, 0.4f);
            Vector3f rotV = track(p, stop(T_POCKET, v(-60f, 0f, 0f)), k(T_SHOW, v(30f, 0f, 0f)),
                    k(T_TWIRLED, v(30f, 0f, 0f)), k(T_NOSE, v(-32f, 0f, 0f)), k(T_SEATED - 0.03f, v(-8f, 0f, 0f)),
                    stop(T_SEATED, v(0f, 0f, 0f)));
            baseNew = euler(rotV);
            float spin = 360f * smooth(u(p, T_SHOW - 0.01f, T_TWIRLED));
            Vector3f offCart = new Vector3f(off).sub(new Vector3f(GRIP).rotate(euler(v(-60f, 0f, 0f))));
            if (p < T_SEATED) {
                a.newPos = track(p, stop(T_POCKET, offCart), k(T_SHOW, show), k(T_TWIRLED, show2), k(T_NOSE, nose),
                        k(T_SEATED - 0.03f, slide), stop(T_SEATED, new Vector3f(BAY)));
            } else {
                a.newPos = new Vector3f(BAY);
            }
            a.newRot = new Quaternionf(baseNew).rotateZ((float) Math.toRadians(spin));
        }

        // ---- hand (always on the left of the hatch)
        Vector3f from = start != null ? start : off;
        Vector3f latchUp = add(LATCH, -4.0f, 1.4f, 1.0f);
        Vector3f latchPress = add(LATCH, -3.0f, 0.2f, 0.2f);
        Vector3f latchBack = add(LATCH, -4.2f, 1.6f, 1.2f);
        Vector3f press = add(BAY, -1.2f, 1.5f, 0.3f);
        Vector3f edgeOpen = lidPoint(1f, 3.6f).add(-2.4f, 0.6f, 0.4f);
        Vector3f edgeClosed = lidPoint(0f, 3.6f).add(-2.4f, 0.9f, 0.2f);
        if (p < T_CATCH || (!hasOld && p < T_POCKET)) {
            if (hasOld) {
                a.hand = track(p, stop(0f, from), k(0.07f, latchUp), stop(T_LATCH, latchPress), k(T_OPEN + 0.01f, latchBack),
                        k(0.22f, add(BAY, -4.0f, 4.5f, 1.0f)), stop(T_CATCH, held(popTop, popRot)));
            } else {
                a.hand = track(p, stop(0f, from), k(0.07f, latchUp), stop(T_LATCH, latchPress), k(T_OPEN + 0.01f, latchBack),
                        stop(0.30f, off), stop(T_POCKET, off));
            }
        } else if (p < T_RELEASE) {
            a.hand = held(a.oldPos, euler(v(-14f - 6f * smooth(u(p, T_CATCH, T_RELEASE)), 0f, 0f)));
        } else if (p < T_CATCH2) {
            // the hand lets go, dips and opens while the cartridge flips over
            float s = u(p, T_RELEASE, T_CATCH2);
            Vector3f h0 = held(release, euler(v(-20f, 0f, 0f)));
            Vector3f h1 = held(catchPos, euler(v(-20f, 30f, 0f)));
            a.hand = h0.lerp(h1, smooth(s)).add(0f, -1.3f * Mth.sin(s * Mth.PI), 0.4f * Mth.sin(s * Mth.PI));
        } else if (p < T_POCKET) {
            a.hand = held(a.oldPos, euler(v(-20f - 40f * smooth(u(p, T_CATCH2, T_POCKET)), 30f, 0f)));
        } else if (p < T_SEATED) {
            a.hand = held(a.newPos, baseNew);
        } else if (p < T_LID) {
            // lets go of the side and pushes the cartridge home from the top
            a.hand = track(p, stop(T_SEATED, held(BAY, new Quaternionf())), k(T_SEATED + 0.02f, add(BAY, -2.0f, 3.4f, 0.9f)),
                    stop(T_CLICK, press), stop(T_LID, edgeOpen));
        } else if (p < T_SLAM) {
            float s = u(p, T_LID, T_SLAM);
            a.hand = lidPoint(hatchOpen(p), 3.6f).add(-2.4f, 0.6f + 0.3f * s, 0.4f - 0.2f * s);
        } else {
            a.hand = track(p, stop(T_SLAM, edgeClosed), k(0.90f, add(edgeClosed, -3.0f, 3.0f, 1.0f)), stop(0.97f, from),
                    stop(1f, from));
        }
        return a;
    }

    // ------------------------------------------------------------------------------------------------------------

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buffers, int light,
                             int overlay) {
        if (ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            if (DesignatorHold.placeThirdPerson(ctx, ps)) {
                draw(stack, ps, buffers, light, overlay, TP_OFF, DesignatorHold.TP_GRIP_L, true);
                return;
            }
            // not held by a posed player (mob, one hand only): in the hand, as an ordinary item
            ps.translate(0.5f, 0.5f, 0.5f);
            ps.translate(0f, 1.5f / 16f, -2.0f / 16f);
            ps.mulPose(Axis.XP.rotationDegrees(90f));
            ps.scale(0.55f, 0.55f, 0.55f);
            ps.translate(-0.5f, -0.5f, -0.5f);
            draw(stack, ps, buffers, light, overlay, TP_OFF, DesignatorHold.TP_GRIP_L, true);
            return;
        }
        // first person contexts only when DesignatorHold does not draw them (held in one hand)
        draw(stack, ps, buffers, light, overlay, ITEM_OFF, null, ctx != ItemDisplayContext.GUI);
    }

    /**
     * Draws the binoculars in item space: body, hatch, status LED, screen, cartridges and the sparks of the reload.
     * Returns the reload motion (for the hand that does it), or null when not reloading.
     */
    public static Anim draw(ItemStack stack, PoseStack ps, MultiBufferSource buffers, int light, int overlay, Vector3f off,
                            Vector3f start, boolean fx) {
        Minecraft mc = Minecraft.getInstance();
        double now = mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime();
        float p = DesignatorItem.reloadProgress(stack, now);
        boolean reloading = p < 1f;
        StrikeType loaded = DesignatorItem.cartridge(stack);
        StrikeType old = DesignatorItem.ejected(stack);
        Anim a = reloading ? anim(p, old != null, off, start) : null;

        GeoModel m = ModModels.designator();
        GeoPart hatch = m.part("hatch");
        if (hatch != null) hatch.xRot = hatch.initialXRot() + (float) Math.toRadians(HATCH_OPEN_DEG * hatchOpen(p));
        ps.pushPose();
        ps.scale(-1f, -1f, 1f);
        m.renderList(m.solid, ps, buffers.getBuffer(RenderType.entityCutout(TEXTURE)), light, overlay, 1, 1, 1, 1);
        float[] led = ledColor(reloading && p < T_OPEN ? old : loaded, reloading, p, now);
        if (led != null) {
            m.renderPart("emit_led", ps, buffers.getBuffer(RenderType.eyes(TEXTURE)), LightTexture.FULL_BRIGHT, overlay,
                    led[0], led[1], led[2], 1);
        }
        ps.popPose();
        renderScreen(ps, buffers.getBuffer(RenderType.eyes(SCREEN)), loaded, old, p, now,
                DesignatorItem.reprogramProgress(stack, now));
        if (reloading && fx) renderFx(ps, buffers, p, now, light);

        VertexConsumer vc = buffers.getBuffer(Sheets.cutoutBlockSheet());
        if (!reloading) {
            if (loaded != null) cartridge(loaded, BAY, null, ps, vc, light, overlay);
            return null;
        }
        if (a.oldPos != null) cartridge(old, a.oldPos, a.oldRot, ps, vc, light, overlay);
        if (a.newPos != null && loaded != null) cartridge(loaded, a.newPos, a.newRot, ps, vc, light, overlay);
        return a;
    }

    /** Status LED: cartridge colour, amber blink while empty or open, white flash when the cartridge clicks home. */
    private static float[] ledColor(StrikeType shown, boolean reloading, float p, double now) {
        if (reloading && p >= T_CLICK && p < T_CLICK + 0.04f) return new float[]{1f, 1f, 1f};
        if ((reloading && p >= T_OPEN && p < T_CLICK) || shown == null) {
            return ((long) now / (reloading ? 4 : 8)) % 2 == 0 ? new float[]{1f, 0.6f, 0.1f} : null;
        }
        int c = shown.uiColor;
        float k = 0.85f + 0.15f * Mth.sin((float) now * 0.15f);
        return new float[]{Math.min(1f, ((c >> 16) & 255) / 255f * k), Math.min(1f, ((c >> 8) & 255) / 255f * k),
                Math.min(1f, (c & 255) / 255f * k)};
    }

    // ------------------------------------------------------------------------------------------------------------
    // Screen

    private static void renderScreen(PoseStack ps, VertexConsumer vc, StrikeType loaded, StrikeType old, float p,
                                     double now, float reprog) {
        PoseStack.Pose pose = ps.last();
        float z = DISPLAY_Z / 16f;
        if (p >= 1f && reprog < 1f && loaded != null) {
            // cartridge reprogrammed for another strike of its level: short flash, then the new logo scans in
            if (reprog < 0.35f) {
                cell(pose, vc, CELL_WHITE, 0f, 1f, z, 0.8f * (1f - reprog / 0.35f), 0.55f, 1f, 0.6f);
            } else {
                float f = smooth(u(reprog, 0.35f, 0.95f));
                cell(pose, vc, loaded.ordinal(), 0f, f, z, 1f, 1f, 1f, 1f);
                strip(pose, vc, f, 0.05f, z + 0.0006f, 0.35f, 1f, 0.45f);
            }
            return;
        }
        if (p >= 1f) {
            if (loaded == null) {
                boolean on = ((long) now / 10) % 2 == 0;
                cell(pose, vc, CELL_EMPTY, 0f, 1f, z, on ? 1f : 0.3f, 1f, 1f, 1f);
                return;
            }
            float pulse = 0.93f + 0.07f * Mth.sin((float) now * 0.2f);
            cell(pose, vc, loaded.ordinal(), 0f, 1f, z, pulse, 1f, 1f, 1f);
            // slow scan line every 4 s
            float sweep = (float) ((now % 80) / 14.0);
            if (sweep < 1f) strip(pose, vc, 1f - sweep, 0.035f, z + 0.0006f, 0.10f, 0.30f, 0.12f);
            return;
        }
        if (p < T_OPEN) {
            cell(pose, vc, old != null ? old.ordinal() : CELL_EMPTY, 0f, 1f, z, 1f, 1f, 1f, 1f);
        } else if (p < T_CLICK) {
            boolean on = ((long) now / 5) % 2 == 0;
            cell(pose, vc, CELL_OPEN, 0f, 1f, z, on ? 1f : 0.35f, 1f, 1f, 1f);
        } else if (p < T_LID) {
            float f = 1f - u(p, T_CLICK, T_LID);
            cell(pose, vc, CELL_WHITE, 0f, 1f, z, 0.9f * f, 0.55f, 1f, 0.6f);
        } else if (p < T_READY) {
            cell(pose, vc, CELL_LOADING, 0f, 1f, z, 1f, 1f, 1f, 1f);
            // segmented progress bar inside the frame of the LOADING cell (cell pixels 16..111 x 38..45)
            float prog = u(p, T_LID, T_READY - 0.01f);
            int segs = 12;
            for (int i = 0; i < segs; i++) {
                if (i >= prog * segs) break;
                float px0 = 16f + i * 8f, px1 = px0 + 6f;
                rect(pose, vc, CELL_WHITE, px0 / 128f, 38f / 64f, px1 / 128f, 46f / 64f, z + 0.0006f,
                        0.36f, 1f, 0.42f);
            }
        } else if (p < 0.95f) {
            float f = 1f - u(p, T_READY, 0.95f);
            cell(pose, vc, CELL_WHITE, 0f, 1f, z, 0.7f * f, 0.55f, 1f, 0.6f);
        } else if (loaded != null) {
            // new cartridge revealed from the top with a bright scan line
            float f = smooth(u(p, 0.95f, 0.995f));
            cell(pose, vc, loaded.ordinal(), 0f, f, z, 1f, 1f, 1f, 1f);
            strip(pose, vc, f, 0.05f, z + 0.0006f, 0.35f, 1f, 0.45f);
        }
    }

    /** Draws the top part (v from top0 to top1, 0 = top of the cell) of an atlas cell over the screen. */
    private static void cell(PoseStack.Pose pose, VertexConsumer vc, int cell, float top0, float top1, float z,
                             float k, float r, float g, float b) {
        rect(pose, vc, cell, 0f, top0, 1f, top1, z, r * k, g * k, b * k);
    }

    /** Horizontal glowing line at fraction f from the top of the screen. */
    private static void strip(PoseStack.Pose pose, VertexConsumer vc, float f, float h, float z, float r, float g,
                              float b) {
        float t0 = Math.max(0f, f - h * 0.5f), t1 = Math.min(1f, f + h * 0.5f);
        if (t1 > t0) rect(pose, vc, CELL_WHITE, 0f, t0, 1f, t1, z, r, g, b);
    }

    /** Part of a cell (fractions of the cell, 0,0 = top left) mapped on the same part of the screen. */
    private static void rect(PoseStack.Pose pose, VertexConsumer vc, int cell, float fx0, float fy0, float fx1,
                             float fy1, float z, float r, float g, float b) {
        float x0 = (DISPLAY_X0 + (DISPLAY_X1 - DISPLAY_X0) * fx0) / 16f, x1 = (DISPLAY_X0 + (DISPLAY_X1 - DISPLAY_X0) * fx1) / 16f;
        float yTop = (DISPLAY_Y1 - (DISPLAY_Y1 - DISPLAY_Y0) * fy0) / 16f, yBot = (DISPLAY_Y1 - (DISPLAY_Y1 - DISPLAY_Y0) * fy1) / 16f;
        float cu = (cell % 4) * 0.25f, cv = (cell / 4) * 0.25f;
        float u0 = cu + fx0 * 0.25f, u1 = cu + fx1 * 0.25f, v0 = cv + fy0 * 0.25f, v1 = cv + fy1 * 0.25f;
        Matrix4f mat = pose.pose();
        Matrix3f nm = pose.normal();
        int light = LightTexture.FULL_BRIGHT;
        int ov = OverlayTexture.NO_OVERLAY;
        vc.vertex(mat, x0, yBot, z).color(r, g, b, 1f).uv(u0, v1).overlayCoords(ov).uv2(light).normal(nm, 0, 0, 1).endVertex();
        vc.vertex(mat, x1, yBot, z).color(r, g, b, 1f).uv(u1, v1).overlayCoords(ov).uv2(light).normal(nm, 0, 0, 1).endVertex();
        vc.vertex(mat, x1, yTop, z).color(r, g, b, 1f).uv(u1, v0).overlayCoords(ov).uv2(light).normal(nm, 0, 0, 1).endVertex();
        vc.vertex(mat, x0, yTop, z).color(r, g, b, 1f).uv(u0, v0).overlayCoords(ov).uv2(light).normal(nm, 0, 0, 1).endVertex();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Sparks and dust, drawn with the item (item units) so that they stay on it in first person

    /** Deterministic noise 0..1 (the same reload always throws the same sparks, frame after frame). */
    private static float rnd(long seed, int i, int k) {
        long h = seed * 0x9E3779B97F4A7C15L + i * 0xBF58476D1CE4E5B9L + k * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        h *= 0x7FB5D329728EA185L;
        h ^= h >>> 27;
        h *= 0x81DADEF4BC2DD44DL;
        h ^= h >>> 33;
        return (h >>> 40) / (float) (1L << 24);
    }

    private static void renderFx(PoseStack ps, MultiBufferSource buffers, float p, double now, int light) {
        float secs = DesignatorItem.RELOAD_TICKS / 20f;
        float tClick = (p - T_CLICK) * secs, tSlam = (p - T_SLAM) * secs;
        boolean click = tClick >= 0f && tClick < 0.4f, slam = tSlam >= 0f && tSlam < 0.8f;
        if (!click && !slam) return;
        long seed = Math.round(now - p * DesignatorItem.RELOAD_TICKS);
        PoseStack.Pose pose = ps.last();
        Matrix4f inv = new Matrix4f(pose.pose()).invert();
        Vector3f cam = inv.transformPosition(new Vector3f()).mul(16f);
        // view space is mirrored in GUIs (y down): gravity must still point to the bottom of the screen
        float down = pose.pose().determinant() < 0f ? 1f : -1f;
        Vector3f g = inv.transformDirection(v(0f, down, 0f)).normalize();
        Vector3f right = inv.transformDirection(v(1f, 0f, 0f)).normalize();
        Vector3f up = inv.transformDirection(v(0f, 1f, 0f)).normalize();
        Vector3f facing = inv.transformDirection(v(0f, 0f, 1f)).normalize();
        // additive sparks first, then the translucent dust (a new buffer ends the previous batch)
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(SCREEN));
        if (click) {
            // the contacts arc when the cartridge clicks home: a flash, then sparks spraying out of the bay front
            if (tClick < 0.08f) {
                float f = 1f - tClick / 0.08f;
                flare(pose, glow, v(8f, 12.2f, 4.7f), right, up, 3.6f * (0.6f + 0.4f * f), 0.8f * f, 0.9f * f, f);
            }
            sparks(pose, glow, cam, g, seed, 0, 22, tClick, v(8f, 12.3f, 4.6f), 2.6f, v(0f, 1f, -0.45f), 0.8f, 20f, 46f);
        }
        if (slam) {
            // the hatch slams shut: a few sparks from the latch, dust squeezed out of the seams
            if (tSlam < 0.08f) {
                float f = 1f - tSlam / 0.08f;
                flare(pose, glow, v(8f, 12.9f, 11.2f), right, up, 2.2f, f, 0.8f * f, 0.5f * f);
            }
            sparks(pose, glow, cam, g, seed, 100, 8, tSlam, v(8f, 12.9f, 11.2f), 1.0f, v(0f, 0.7f, 1f), 0.7f, 14f, 30f);
            dust(pose, buffers.getBuffer(RenderType.entityTranslucent(DUST)), right, up, facing, seed, tSlam, light);
        }
    }

    private static void sparks(PoseStack.Pose pose, VertexConsumer vc, Vector3f cam, Vector3f down, long seed, int base,
                               int n, float t, Vector3f origin, float width, Vector3f dir, float spread, float vMin,
                               float vMax) {
        Vector3f d0 = new Vector3f(dir).normalize();
        Vector3f g = new Vector3f(down).mul(170f);   // item px / s2
        for (int i = 0; i < n; i++) {
            int id = base + i;
            float life = 0.14f + 0.18f * rnd(seed, id, 0);
            float tt = t - 0.03f * rnd(seed, id, 1);
            if (tt < 0f || tt > life) continue;
            Vector3f vel0 = new Vector3f(d0).add((rnd(seed, id, 2) - 0.5f) * 2f * spread,
                    (rnd(seed, id, 3) - 0.5f) * 2f * spread, (rnd(seed, id, 4) - 0.5f) * 2f * spread)
                    .normalize().mul(Mth.lerp(rnd(seed, id, 5), vMin, vMax));
            Vector3f pos = new Vector3f(origin).add((rnd(seed, id, 6) - 0.5f) * width, 0f, 0f)
                    .add(new Vector3f(vel0).mul(tt)).add(new Vector3f(g).mul(0.5f * tt * tt));
            Vector3f vel = new Vector3f(vel0).add(new Vector3f(g).mul(tt));
            Vector3f tail = new Vector3f(pos).sub(vel.mul(0.03f));
            float k = tt / life, fade = 1f - k * k;
            streak(pose, vc, pos, tail, cam, 0.16f, fade, (0.95f - 0.45f * k) * fade, (0.75f - 0.7f * k) * fade);
        }
    }

    /** Thin quad from head to tail facing the camera (both sides). */
    private static void streak(PoseStack.Pose pose, VertexConsumer vc, Vector3f head, Vector3f tail, Vector3f cam,
                               float w, float r, float g, float b) {
        Vector3f axis = new Vector3f(head).sub(tail);
        Vector3f side = axis.cross(new Vector3f(cam).sub(head), new Vector3f());
        if (side.lengthSquared() < 1e-10f) return;
        side.normalize().mul(w * 0.5f);
        Vector3f a = new Vector3f(head).add(side), b2 = new Vector3f(head).sub(side);
        Vector3f c = new Vector3f(tail).sub(side), d = new Vector3f(tail).add(side);
        float u = 0.875f, vv = 0.625f;   // inside the white cell
        glowQuad(pose, vc, a, b2, c, d, u, vv, u, vv, r, g, b);
        glowQuad(pose, vc, d, c, b2, a, u, vv, u, vv, r, g, b);
    }

    /** Camera facing star flare (left square of atlas cell 12). */
    private static void flare(PoseStack.Pose pose, VertexConsumer vc, Vector3f c, Vector3f right, Vector3f up,
                              float size, float r, float g, float b) {
        Vector3f rx = new Vector3f(right).mul(size * 0.5f), uy = new Vector3f(up).mul(size * 0.5f);
        Vector3f p0 = new Vector3f(c).sub(rx).sub(uy), p1 = new Vector3f(c).add(rx).sub(uy);
        Vector3f p2 = new Vector3f(c).add(rx).add(uy), p3 = new Vector3f(c).sub(rx).add(uy);
        float u0 = (CELL_FLARE % 4) * 0.25f, u1 = u0 + 0.125f, v0 = (CELL_FLARE / 4) * 0.25f, v1 = v0 + 0.25f;
        glowQuad(pose, vc, p0, p1, p2, p3, u0, v1, u1, v0, r, g, b);
        glowQuad(pose, vc, p3, p2, p1, p0, u0, v0, u1, v1, r, g, b);
    }

    /** Additive quad a b c d (item units), uv from (u0, v0) at a to (u1, v1) at c. */
    private static void glowQuad(PoseStack.Pose pose, VertexConsumer vc, Vector3f a, Vector3f b, Vector3f c, Vector3f d,
                                 float u0, float v0, float u1, float v1, float r, float g, float bl) {
        Matrix4f mat = pose.pose();
        Matrix3f nm = pose.normal();
        int light = LightTexture.FULL_BRIGHT, ov = OverlayTexture.NO_OVERLAY;
        vc.vertex(mat, a.x / 16f, a.y / 16f, a.z / 16f).color(r, g, bl, 1f).uv(u0, v0).overlayCoords(ov).uv2(light).normal(nm, 0, 0, 1).endVertex();
        vc.vertex(mat, b.x / 16f, b.y / 16f, b.z / 16f).color(r, g, bl, 1f).uv(u1, v0).overlayCoords(ov).uv2(light).normal(nm, 0, 0, 1).endVertex();
        vc.vertex(mat, c.x / 16f, c.y / 16f, c.z / 16f).color(r, g, bl, 1f).uv(u1, v1).overlayCoords(ov).uv2(light).normal(nm, 0, 0, 1).endVertex();
        vc.vertex(mat, d.x / 16f, d.y / 16f, d.z / 16f).color(r, g, bl, 1f).uv(u0, v1).overlayCoords(ov).uv2(light).normal(nm, 0, 0, 1).endVertex();
    }

    /** Puffs of dust pushed out of the hatch seams (rear edge and both sides), spreading and fading. */
    private static void dust(PoseStack.Pose pose, VertexConsumer vc, Vector3f right, Vector3f up, Vector3f facing,
                             long seed, float t, int light) {
        Matrix4f mat = pose.pose();
        Matrix3f nm = pose.normal();
        for (int i = 0; i < 24; i++) {
            int id = 200 + i;
            float life = 0.5f + 0.35f * rnd(seed, id, 0);
            if (t > life) continue;
            float s = rnd(seed, id, 1);
            Vector3f o, dir;
            if (i < 10) {
                o = v(Mth.lerp(s, 5.0f, 11.0f), 12.75f, 10.95f);
                dir = v(0f, 0.3f, 1f);
            } else if (i < 17) {
                o = v(4.85f, 12.75f, Mth.lerp(s, 4.6f, 10.8f));
                dir = v(-1f, 0.3f, 0f);
            } else {
                o = v(11.15f, 12.75f, Mth.lerp(s, 4.6f, 10.8f));
                dir = v(1f, 0.3f, 0f);
            }
            float k = t / life;
            float travel = (1f - (1f - k) * (1f - k)) * (1.8f + 1.8f * rnd(seed, id, 2));
            Vector3f c = dir.normalize().mul(travel).add(o).add(0f, 0.8f * k, 0f);
            float size = (1.4f + 0.8f * rnd(seed, id, 3)) * (1f + 1.6f * k);
            float alpha = 0.7f * (1f - k) * (1f - k) * Math.min(1f, t / 0.015f);
            float ang = rnd(seed, id, 4) * Mth.TWO_PI + k * 0.8f;
            float cs = Mth.cos(ang) * size * 0.5f, sn = Mth.sin(ang) * size * 0.5f;
            Vector3f ax = new Vector3f(right).mul(cs).add(new Vector3f(up).mul(sn));
            Vector3f ay = new Vector3f(up).mul(cs).sub(new Vector3f(right).mul(sn));
            Vector3f[] q = {new Vector3f(c).sub(ax).sub(ay), new Vector3f(c).add(ax).sub(ay),
                    new Vector3f(c).add(ax).add(ay), new Vector3f(c).sub(ax).add(ay)};
            float[][] uv = {{0f, 1f}, {1f, 1f}, {1f, 0f}, {0f, 0f}};
            float shade = 0.9f + 0.1f * rnd(seed, id, 5);
            for (int j = 0; j < 4; j++) {
                vc.vertex(mat, q[j].x / 16f, q[j].y / 16f, q[j].z / 16f).color(0.80f * shade, 0.77f * shade, 0.70f * shade, alpha)
                        .uv(uv[j][0], uv[j][1]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                        .normal(nm, facing.x, facing.y, facing.z).endVertex();
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------

    private static void cartridge(StrikeType type, Vector3f pos, Quaternionf rot, PoseStack ps, VertexConsumer vc,
                                  int light, int overlay) {
        BakedModel model = Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(ModItems.cartridgeFor(type));
        ps.pushPose();
        ps.translate(pos.x / 16f, pos.y / 16f, pos.z / 16f);
        if (rot != null) ps.mulPose(rot);
        // payload module laid flat: its contacts face the front of the bay, its icon faces up
        ps.mulPose(Axis.XP.rotationDegrees(-90f));
        ps.scale(CART_SCALE, CART_SCALE, CART_SCALE);
        ps.translate(-PAY_X, -PAY_Y, -PAY_Z);
        PoseStack.Pose pose = ps.last();
        for (Direction side : SIDES) {
            RANDOM.setSeed(42L);
            for (BakedQuad quad : model.getQuads(null, side, RANDOM)) {
                vc.putBulkData(pose, quad, 1f, 1f, 1f, light, overlay);
            }
        }
        ps.popPose();
    }
}
