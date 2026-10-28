package fr.heraliz.airstrike.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heraliz.airstrike.client.DesignatorClient;
import fr.heraliz.airstrike.item.DesignatorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderHandEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The binoculars held in both hands like the TaCZ guns: first person, the player's real arms (PlayerRenderer
 * renderRightHand / renderLeftHand, so sleeves and gloves of other mods follow) reach for the two tubes; third person,
 * the binoculars sit against the eyes (aiming) or in front of the chest (rest, reload) and both arms are turned
 * towards their grips. The left hand does the reload work. Settings validated in mods/airstrike/outils/apercu
 * (apres.js): keep both in sync.
 * <p>
 * Item space: pixels of the item model (x right, y up, z back towards the eyepieces).
 */
public final class DesignatorHold {
    /** Middle of the binoculars, between the tubes, on their axis. */
    static final Vector3f CENTRE = new Vector3f(8f, 6.4f, 8f);
    /** Grips: outer side of each tube, under the axis. */
    static final Vector3f GRIP_R = new Vector3f(14.8f, 4.2f, 6.5f), GRIP_L = new Vector3f(1.2f, 4.4f, 6.0f);

    // first person: where the centre of the binoculars goes (blocks, camera space), angles (degrees), size
    private static final Hold FP_REST = new Hold(0.28f, -0.27f, -0.95f, 24f, -18f, -5f, 0.7f);
    private static final Hold FP_AIM = new Hold(0f, -0.04f, -0.33f, 0f, 0f, 0f, 0.7f);
    private static final Hold FP_RELOAD = new Hold(0.22f, -0.28f, -1.0f, 46f, 20f, 4f, 0.7f);
    /** Virtual shoulders (blocks, camera space): the arms go from the hands towards them, stretched like TaCZ arms. */
    private static final Vector3f SHOULDER_R = new Vector3f(0.6f, -1.05f, 0.2f), SHOULDER_L = new Vector3f(-0.45f, -1.05f, 0.2f),
            SHOULDER_L_WORK = new Vector3f(-0.6f, -1.05f, 0.3f);
    private static final float STRETCH = 1.5f;
    /** Where the left hand pockets and fetches the cartridges (blocks, camera space). */
    private static final Vector3f POCKET = new Vector3f(-0.6f, -0.8f, -0.35f);
    /** Palm side (item space directions): towards the tube axis. While reloading the left palm faces the bay. */
    private static final Vector3f PALM_R = new Vector3f(-1f, 0.6f, 0f), PALM_L = new Vector3f(1f, 0.6f, 0f);
    private static final Vector3f BAY_TOP = new Vector3f(8f, 11f, 8f);
    /** Ticks to lower the binoculars from the eyes once the right click is released. */
    private static final float LOWER_TICKS = 4f;

    // third person: item pixel E goes to point C of the head or body part (model pixels, y down, front = -z)
    private static final Place TP_AIM = new Place(true, new Vector3f(8f, 6.4f, 15.4f), new Vector3f(0f, -4.0f, -4.3f),
            new Vector3f(0f, 0f, 0f), 0.55f);
    private static final Place TP_REST = new Place(false, new Vector3f(8f, 6.4f, 8f), new Vector3f(0f, 7.0f, -6.0f),
            new Vector3f(-20f, 0f, 0f), 0.55f);
    private static final Place TP_RELOAD = new Place(false, new Vector3f(8f, 6.4f, 8f), new Vector3f(-0.5f, 4.5f, -6.5f),
            new Vector3f(-55f, -15f, 0f), 0.55f);
    static final Vector3f TP_GRIP_R = new Vector3f(14.9f, 4.2f, 9.5f), TP_GRIP_L = new Vector3f(1.1f, 4.2f, 9.5f);

    /** Set while the first person arms are drawn: the binoculars then give the plain arm pose (see getArmPose). */
    public static boolean drawingFirstPersonArms;

    /** Third person: model posed by the arm pose of the player being rendered, and where its binoculars go. */
    private static HumanoidModel<?> capturedModel;
    private static Frame capturedFrame;
    private static boolean capturedSlim;

    private static final Map<Integer, Raise> RAISES = new HashMap<>();
    private static int raiseCalls;

    private DesignatorHold() {
    }

    // ------------------------------------------------------------------------------------------------------------
    // Settings

    /** First person hold: centre position (blocks), angles (degrees), size. */
    private record Hold(float x, float y, float z, float rx, float ry, float rz, float s) {
        Hold mirror(int side) {
            return side > 0 ? this : new Hold(-x, y, z, rx, -ry, -rz, s);
        }

        Hold lerp(Hold o, float t) {
            if (t <= 0f) return this;
            if (t >= 1f) return o;
            return new Hold(Mth.lerp(t, x, o.x), Mth.lerp(t, y, o.y), Mth.lerp(t, z, o.z), Mth.lerp(t, rx, o.rx),
                    Mth.lerp(t, ry, o.ry), Mth.lerp(t, rz, o.rz), Mth.lerp(t, s, o.s));
        }
    }

    /** Third person place: item pixel {@code e} at point {@code c} of the head or body, angles (degrees), size. */
    private record Place(boolean head, Vector3f e, Vector3f c, Vector3f rot, float s) {
        Place lerp(Place o, float t) {
            if (t <= 0f) return this;
            if (t >= 1f) return o;
            return new Place(head, e, new Vector3f(c).lerp(o.c, t), new Vector3f(rot).lerp(o.rot, t), s);
        }

        /** Item space (blocks) to the frame of the part. */
        Matrix4f matrix() {
            return new Matrix4f().translation(c.x / 16f, c.y / 16f, c.z / 16f)
                    .rotateXYZ(rad(rot.x), rad(rot.y), rad(rot.z))
                    .rotateZ(Mth.PI)
                    .scale(s)
                    .translate(-e.x / 16f, -e.y / 16f, -e.z / 16f);
        }
    }

    /**
     * Item space (blocks) to the model space of the player (blocks): the centre of the binoculars goes to {@code c},
     * turned by {@code q}, scaled by {@code s}.
     */
    private record Frame(Vector3f c, Quaternionf q, float s) {
        static Frame of(Matrix4f m) {
            return new Frame(m.transformPosition(new Vector3f(CENTRE).div(16f)), m.getUnnormalizedRotation(new Quaternionf()),
                    m.getScale(new Vector3f()).x);
        }

        Frame lerp(Frame o, float t) {
            if (t <= 0f) return this;
            if (t >= 1f) return o;
            return new Frame(new Vector3f(c).lerp(o.c, t), new Quaternionf(q).slerp(o.q, t), Mth.lerp(t, s, o.s));
        }

        Matrix4f matrix() {
            return new Matrix4f().translation(c).rotate(q).scale(s).translate(-CENTRE.x / 16f, -CENTRE.y / 16f, -CENTRE.z / 16f);
        }

        void apply(PoseStack ps) {
            ps.translate(c.x, c.y, c.z);
            ps.mulPose(q);
            ps.scale(s, s, s);
            ps.translate(-CENTRE.x / 16f, -CENTRE.y / 16f, -CENTRE.z / 16f);
        }
    }

    private static float rad(float deg) {
        return (float) Math.toRadians(deg);
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Raising to the eyes (shared by both views): up with the right click, eased back down once it is released

    private static final class Raise {
        float shown;
        long at;
    }

    /** 0 = rest, 1 = against the eyes (eased). */
    static float raise(LivingEntity entity, InteractionHand hand, float partial) {
        float raw = 0f;
        if (entity.isUsingItem() && entity.getUsedItemHand() == hand && entity.getUseItem().getItem() instanceof DesignatorItem) {
            raw = Mth.clamp((entity.getTicksUsingItem() + partial) / DesignatorClient.SCOPE_DELAY, 0f, 1f);
        }
        long now = System.nanoTime();
        if (++raiseCalls >= 512) {
            raiseCalls = 0;
            for (Iterator<Raise> it = RAISES.values().iterator(); it.hasNext(); ) {
                if (now - it.next().at > 10_000_000_000L) it.remove();
            }
        }
        Raise r = RAISES.get(entity.getId());
        if (r == null) {
            r = new Raise();
            r.shown = raw;
            RAISES.put(entity.getId(), r);
        } else if (raw >= r.shown) {
            r.shown = raw;
        } else {
            float ticks = Math.min(1f, (now - r.at) / 5.0e7f);
            r.shown = Math.max(raw, r.shown - ticks / LOWER_TICKS);
        }
        r.at = now;
        return smooth(r.shown);
    }

    /** Hand holding the binoculars with both hands, or null (one hand only: plain item). */
    static InteractionHand holdingHand(LivingEntity entity) {
        if (entity.getMainHandItem().getItem() instanceof DesignatorItem) return InteractionHand.MAIN_HAND;
        if (entity.getOffhandItem().getItem() instanceof DesignatorItem && entity.getMainHandItem().isEmpty()) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------------------
    // First person

    /** RenderHandEvent: binoculars and both arms (looking through them, the HUD replaces the hands). */
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (DesignatorClient.isScoped()) {
            event.setCanceled(true);
            return;
        }
        InteractionHand hand = holdingHand(player);
        if (hand == null) return;
        // both hands are on the binoculars: nothing else is drawn
        event.setCanceled(true);
        if (event.getHand() != hand) return;
        ItemStack stack = player.getItemInHand(hand);
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        renderFirstPerson(mc, player, stack, hand, arm == HumanoidArm.RIGHT ? 1 : -1, ps, event.getMultiBufferSource(),
                event.getPackedLight(), event.getPartialTick(), event.getEquipProgress(), event.getSwingProgress());
        ps.popPose();
    }

    private static void renderFirstPerson(Minecraft mc, LocalPlayer player, ItemStack stack, InteractionHand hand, int side,
                                          PoseStack ps, MultiBufferSource buffers, int light, float partial, float equip,
                                          float swing) {
        float r = raise(player, hand, partial);
        // horloge régulière du joueur, pas le temps du monde que le serveur recale chaque seconde (HorlogeJumelles)
        float p = HorlogeJumelles.recharge(stack);
        float w = DesignatorRenderer.presentation(p);
        Hold d = FP_REST.mirror(side).lerp(FP_AIM, r).lerp(FP_RELOAD.mirror(side), w);
        // B: item space (blocks) to the frame of the hands given by the event
        PoseStack local = new PoseStack();
        placeFirstPerson(local, d, p, w, r, side, equip, swing);
        Matrix4f b = new Matrix4f(local.last().pose());
        Vector3f off = new Matrix4f(b).invert().transformPosition(new Vector3f(POCKET)).mul(16f);

        ps.pushPose();
        placeFirstPerson(ps, d, p, w, r, side, equip, swing);
        DesignatorRenderer.Anim a = DesignatorRenderer.draw(stack, ps, buffers, light, OverlayTexture.NO_OVERLAY, off, GRIP_L, true);
        ps.popPose();

        if (player.isInvisible()) return;
        EntityRenderer<? super LocalPlayer> renderer = mc.getEntityRenderDispatcher().getRenderer(player);
        if (!(renderer instanceof PlayerRenderer pr)) return;
        boolean slim = "slim".equals(player.getModelName());
        Vector3f gR = b.transformPosition(new Vector3f(GRIP_R).div(16f));
        Vector3f gL = b.transformPosition(new Vector3f(a != null ? a.hand : GRIP_L).div(16f));
        Vector3f inR = b.transformDirection(new Vector3f(PALM_R)).normalize();
        arm(pr, player, ps, buffers, light, true, slim, gR, new Vector3f(SHOULDER_R).sub(gR), inR);
        Vector3f shoulderL = new Vector3f(SHOULDER_L).lerp(SHOULDER_L_WORK, a != null ? w : 0f);
        Vector3f inL = a != null ? b.transformPosition(new Vector3f(BAY_TOP).div(16f)).sub(gL).normalize()
                : b.transformDirection(new Vector3f(PALM_L)).normalize();
        arm(pr, player, ps, buffers, light, false, slim, gL, shoulderL.sub(gL), inL);
    }

    /** Hand frame to item space: equip, hold, reload moves around the centre, swing. */
    private static void placeFirstPerson(PoseStack ps, Hold d, float p, float w, float r, int side, float equip, float swing) {
        ps.translate(0f, -0.6f * equip, 0f);
        ps.translate(d.x, d.y, d.z);
        ps.mulPose(new Quaternionf().rotationXYZ(rad(d.rx), rad(d.ry), rad(d.rz)));
        ps.scale(d.s, d.s, d.s);
        ps.translate(-CENTRE.x / 16f, -CENTRE.y / 16f, -CENTRE.z / 16f);
        if (w > 0f) {
            // the moves of the reload (latch, flip, click, slam) around the centre of the binoculars
            float[] sw = DesignatorRenderer.sway(p, side);
            ps.translate(CENTRE.x / 16f, CENTRE.y / 16f, CENTRE.z / 16f);
            ps.translate(sw[0] / d.s, sw[1] / d.s, sw[2] / d.s);
            ps.mulPose(Axis.XP.rotationDegrees(sw[3]));
            ps.mulPose(Axis.YP.rotationDegrees(sw[4]));
            ps.mulPose(Axis.ZP.rotationDegrees(sw[5]));
            ps.translate(-CENTRE.x / 16f, -CENTRE.y / 16f, -CENTRE.z / 16f);
        }
        if (swing > 0f && r <= 0.001f && w <= 0.001f) {
            // left click without looking through them: a short nod
            float s = Mth.sin(swing * Mth.PI);
            ps.translate(CENTRE.x / 16f, CENTRE.y / 16f, CENTRE.z / 16f);
            ps.translate(0f, 0f, -0.06f * s / d.s);
            ps.mulPose(Axis.XP.rotationDegrees(-12f * s));
            ps.translate(-CENTRE.x / 16f, -CENTRE.y / 16f, -CENTRE.z / 16f);
        }
    }

    /**
     * One arm drawn like the TaCZ arms: the middle of the end of the hand at {@code hand}, the arm going towards
     * {@code toShoulder} (stretched by half so that the shoulder stays off screen), the palm turned towards
     * {@code palm}. All in the frame of {@code ps} (blocks).
     */
    private static void arm(PlayerRenderer pr, AbstractClientPlayer player, PoseStack ps, MultiBufferSource buffers, int light,
                            boolean right, boolean slim, Vector3f hand, Vector3f toShoulder, Vector3f palm) {
        Vector3f d = new Vector3f(toShoulder);
        if (d.lengthSquared() < 1e-8f) return;
        d.normalize();
        Vector3f in = new Vector3f(palm).sub(new Vector3f(d).mul(palm.dot(d)));
        if (in.lengthSquared() < 1e-8f) return;
        in.normalize();
        Vector3f z = new Vector3f(in).cross(d);
        ps.pushPose();
        ps.translate(hand.x, hand.y, hand.z);
        ps.mulPose(new Quaternionf().setFromNormalized(new Matrix3f(in.x, in.y, in.z, d.x, d.y, d.z, z.x, z.y, z.z)));
        ps.scale(1f, STRETCH, 1f);
        // back to the arm part: the end of the hand (palm towards the body, arm towards the shoulder)...
        ps.mulPose(right ? Axis.XP.rotationDegrees(180f) : Axis.ZP.rotationDegrees(180f));
        float cx = right ? (slim ? -0.5f : -1f) : (slim ? 0.5f : 1f);
        ps.translate(-cx / 16f, -10f / 16f, 0f);
        // ...then undo the pose PlayerRenderer.renderHand gives the arm (pivot (-/+5, 2, 0), tilted 0.1 rad)
        ps.mulPose(Axis.ZP.rotation(right ? -0.1f : 0.1f));
        ps.translate((right ? 5f : -5f) / 16f, -2f / 16f, 0f);
        drawingFirstPersonArms = true;
        try {
            if (right) pr.renderRightHand(ps, buffers, light, player);
            else pr.renderLeftHand(ps, buffers, light, player);
        } finally {
            drawingFirstPersonArms = false;
        }
        ps.popPose();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Third person

    /**
     * Arm pose (HumanoidModel.setupAnim, before the crouch and breathing moves it adds afterwards): turns both arms
     * towards their grips on the binoculars, and remembers where the binoculars go for the item renderer.
     */
    public static void poseArms(HumanoidModel<?> model, LivingEntity entity, HumanoidArm unused) {
        InteractionHand hand = holdingHand(entity);
        if (hand == null) return;
        ItemStack stack = entity.getItemInHand(hand);
        Minecraft mc = Minecraft.getInstance();
        float partial = mc.getFrameTime();
        float p = HorlogeJumelles.recharge(stack);
        boolean crouch = model.crouching;
        // head and body as setupAnim leaves them (it moves them for crouching after the arm poses)
        Matrix4f head = new Matrix4f().translation(0f, (crouch ? 4.2f : 0f) / 16f, 0f)
                .rotateZYX(model.head.zRot, model.head.yRot, model.head.xRot);
        Matrix4f body = new Matrix4f().translation(0f, (crouch ? 3.2f : 0f) / 16f, 0f).rotateX(crouch ? 0.5f : 0f);
        Frame f = thirdPersonFrame(head, body, raise(entity, hand, partial), DesignatorRenderer.presentation(p));
        Matrix4f m = f.matrix();
        Vector3f gR = m.transformPosition(new Vector3f(TP_GRIP_R).div(16f)).mul(16f);
        Vector3f handL = p < 1f
                ? DesignatorRenderer.anim(p, DesignatorItem.ejected(stack) != null, DesignatorRenderer.TP_OFF, TP_GRIP_L).hand
                : TP_GRIP_L;
        Vector3f gL = m.transformPosition(new Vector3f(handL).div(16f)).mul(16f);
        float py = crouch ? 5.2f : 2f;
        aim(model.rightArm, -5f, py, gR);
        aim(model.leftArm, 5f, py, gL);
        // what setupAnim adds afterwards: arms raised when crouching, breathing (bobModelPart)
        if (crouch) {
            model.rightArm.xRot -= 0.4f;
            model.leftArm.xRot -= 0.4f;
        }
        float age = entity.tickCount + partial;
        float bz = Mth.cos(age * 0.09f) * 0.05f + 0.05f, bx = Mth.sin(age * 0.067f) * 0.05f;
        model.rightArm.zRot -= bz;
        model.rightArm.xRot -= bx;
        model.leftArm.zRot += bz;
        model.leftArm.xRot += bx;
        capturedModel = model;
        capturedFrame = f;
        capturedSlim = model instanceof PlayerModel<?> && entity instanceof AbstractClientPlayer acp
                && "slim".equals(acp.getModelName());
    }

    /** Rest or reload in front of the chest, blended towards the eyes while raised. */
    private static Frame thirdPersonFrame(Matrix4f head, Matrix4f body, float r, float k) {
        Frame rest = Frame.of(new Matrix4f(body).mul(TP_REST.lerp(TP_RELOAD, k).matrix()));
        if (r <= 0f) return rest;
        return rest.lerp(Frame.of(new Matrix4f(head).mul(TP_AIM.matrix())), r);
    }

    /** Turns the arm (pivot px, py, 0 in model pixels) so that it points at g: xRot, yRot, zRot = 0. */
    private static void aim(ModelPart arm, float px, float py, Vector3f g) {
        Vector3f d = new Vector3f(g).sub(px, py, 0f);
        if (d.lengthSquared() < 1e-6f) return;
        d.normalize();
        float xr = -(float) Math.acos(Mth.clamp(d.y, -1f, 1f));
        float s = Mth.sin(xr);
        arm.xRot = xr;
        arm.yRot = Math.abs(s) < 1e-4f ? 0f : (float) Mth.atan2(d.x / s, d.z / s);
        arm.zRot = 0f;
    }

    /**
     * Item renderer, third person: from the item space of the hand (ItemInHandLayer, display transform left out) to
     * the place chosen by {@link #poseArms}. False when the binoculars are not held by a player posed this frame
     * (mob, one hand only): the item is then drawn in the hand.
     */
    static boolean placeThirdPerson(ItemDisplayContext ctx, PoseStack ps) {
        HumanoidModel<?> model = capturedModel;
        Frame f = capturedFrame;
        boolean slim = capturedSlim;
        capturedModel = null;
        capturedFrame = null;
        if (model == null || f == null) return false;
        boolean left = ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        ModelPart arm = left ? model.leftArm : model.rightArm;
        float slimOffset = slim ? (left ? -0.5f : 0.5f) : 0f;
        ps.translate(0.5f, 0.5f, 0.5f);
        // ItemInHandLayer.renderArmWithItem, undone
        ps.translate((left ? 1f : -1f) / 16f, -0.125f, 0.625f);
        ps.mulPose(Axis.YP.rotationDegrees(-180f));
        ps.mulPose(Axis.XP.rotationDegrees(90f));
        // HumanoidModel.translateToHand, undone
        ps.mulPose(new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot).conjugate());
        ps.translate(-(arm.x + slimOffset) / 16f, -arm.y / 16f, -arm.z / 16f);
        f.apply(ps);
        return true;
    }

    /** Start and end of the render of a player: forgets a pose that no item render used. */
    public static void endPlayer() {
        capturedModel = null;
        capturedFrame = null;
    }
}
