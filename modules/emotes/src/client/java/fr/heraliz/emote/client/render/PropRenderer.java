package fr.heraliz.emote.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heraliz.emote.client.catalog.EmoteCatalog;
import fr.heraliz.emote.client.catalog.PropModel;
import fr.heraliz.emote.client.catalog.PropSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/** Draws one prop in the frame of the body part it is attached to. */
public final class PropRenderer {
    private static final ResourceLocation TEXTURE = new ResourceLocation("heraliz_emote", "textures/prop/base.png");
    private static final Map<String, ItemStack> ITEMS = new HashMap<>();
    private static final Map<String, BlockState> BLOCKS = new HashMap<>();
    private static final float[] SAMPLE = new float[7];
    /** Set while the icons are rendered (development only): props with {@code icon: false} are left out. */
    public static boolean iconRender;

    private PropRenderer() {
    }

    /** {@code body}: playerAnimator's whole-body transform (position x, y, z, rotation x, y, z). */
    public static void render(PropSpec prop, float tick, float[] body, AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> model,
                              PoseStack pose, MultiBufferSource buffers, int light) {
        if (!prop.visible(tick) || iconRender && !prop.inIcon) return;
        prop.sample(tick, SAMPLE);
        if (SAMPLE[6] <= 0.001F) return;
        int packedLight = prop.light >= 0 ? LightTexture.pack(prop.light, prop.light) : light;
        pose.pushPose();
        boolean hand = attach(prop.attach, body, model, pose);
        pose.translate(SAMPLE[0] / 16.0F, SAMPLE[1] / 16.0F, SAMPLE[2] / 16.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(SAMPLE[5]));
        pose.mulPose(Axis.YP.rotationDegrees(SAMPLE[4]));
        pose.mulPose(Axis.XP.rotationDegrees(SAMPLE[3]));
        pose.scale(SAMPLE[6], SAMPLE[6], SAMPLE[6]);
        // the body part frames are the model's (y down): turn items and blocks right side up there
        if (!hand && prop.model == null) pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        if (prop.item != null) {
            ItemStack stack = ITEMS.computeIfAbsent(prop.item, id -> new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(id))));
            boolean left = prop.attach.startsWith("left");
            ItemDisplayContext context = switch (prop.display) {
                case "fixed" -> ItemDisplayContext.FIXED;
                case "ground" -> ItemDisplayContext.GROUND;
                case "head" -> ItemDisplayContext.HEAD;
                default -> hand ? (left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                        : ItemDisplayContext.FIXED;
            };
            Minecraft.getInstance().getItemRenderer().renderStatic(player, stack, context, left, pose, buffers, player.level(),
                    packedLight, OverlayTexture.NO_OVERLAY, player.getId());
        } else if (prop.block != null) {
            BlockState state = BLOCKS.computeIfAbsent(prop.block, id -> {
                Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
                return block == null ? Blocks.AIR.defaultBlockState() : block.defaultBlockState();
            });
            pose.translate(-0.5F, -0.5F, -0.5F);
            BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
            blocks.renderSingleBlock(state, pose, buffers, packedLight, OverlayTexture.NO_OVERLAY);
            int stage = prop.crackStage(tick);
            if (stage >= 0) {
                // the mining cracks, laid over the block like vanilla does for the block being broken
                VertexConsumer cracks = new SheetedDecalTextureGenerator(buffers.getBuffer(ModelBakery.DESTROY_TYPES.get(stage)),
                        pose.last().pose(), pose.last().normal(), 1.0F);
                blocks.getModelRenderer().renderModel(pose.last(), cracks, state, blocks.getBlockModel(state), 1, 1, 1,
                        packedLight, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            }
        } else if (prop.model != null) {
            PropModel m = EmoteCatalog.model(prop.model);
            if (m != null) renderModel(m, pose, buffers, packedLight);
        }
        pose.popPose();
    }

    /** Moves {@code pose} to the attachment frame; true for the held-item frame. */
    private static boolean attach(String attach, float[] body, PlayerModel<AbstractClientPlayer> model, PoseStack pose) {
        switch (attach) {
            case "rightHand", "leftHand" -> {
                // the same frame vanilla uses for held items (ItemInHandLayer)
                HumanoidArm arm = attach.equals("leftHand") ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
                model.translateToHand(arm, pose);
                pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
                pose.mulPose(Axis.YP.rotationDegrees(180.0F));
                pose.translate((arm == HumanoidArm.LEFT ? -1 : 1) / 16.0F, 0.125F, -0.625F);
                return true;
            }
            case "rightArm" -> model.rightArm.translateAndRotate(pose);
            case "leftArm" -> model.leftArm.translateAndRotate(pose);
            case "rightLeg" -> model.rightLeg.translateAndRotate(pose);
            case "leftLeg" -> model.leftLeg.translateAndRotate(pose);
            case "head" -> model.head.translateAndRotate(pose);
            case "world" -> {
                // the model frame without the whole-body transform: stays put on the ground while the
                // body jumps, leans or spins (S^-1 B^-1 S, S being LivingEntityRenderer's flip, scale and offset)
                pose.translate(0.0F, 1.501F, 0.0F);
                pose.scale(1 / 0.9375F, 1 / 0.9375F, 1 / 0.9375F);
                pose.scale(-1.0F, -1.0F, 1.0F);
                pose.translate(0.0F, 0.7F, 0.0F);
                pose.mulPose(Axis.XP.rotation(-body[3]));
                pose.mulPose(Axis.YP.rotation(-body[4]));
                pose.mulPose(Axis.ZP.rotation(-body[5]));
                pose.translate(-body[0], -body[1] - 0.7F, -body[2]);
                pose.scale(-1.0F, -1.0F, 1.0F);
                pose.scale(0.9375F, 0.9375F, 0.9375F);
                pose.translate(0.0F, -1.501F, 0.0F);
            }
            case "body" -> model.body.translateAndRotate(pose);
            default -> {
                // root: the model frame itself
            }
        }
        return false;
    }

    private static void renderModel(PropModel model, PoseStack pose, MultiBufferSource buffers, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (PropModel.Box box : model.boxes()) {
            pose.pushPose();
            float[] r = box.rot();
            if (r[0] != 0 || r[1] != 0 || r[2] != 0) {
                float[] p = box.pivot();
                pose.translate(p[0] / 16.0F, p[1] / 16.0F, p[2] / 16.0F);
                pose.mulPose(Axis.ZP.rotationDegrees(r[2]));
                pose.mulPose(Axis.YP.rotationDegrees(r[1]));
                pose.mulPose(Axis.XP.rotationDegrees(r[0]));
                pose.translate(-p[0] / 16.0F, -p[1] / 16.0F, -p[2] / 16.0F);
            }
            box(vc, pose, box, box.glow() ? LightTexture.FULL_BRIGHT : light);
            pose.popPose();
        }
    }

    private static void box(VertexConsumer vc, PoseStack pose, PropModel.Box box, int light) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float x0 = box.from()[0] / 16.0F, y0 = box.from()[1] / 16.0F, z0 = box.from()[2] / 16.0F;
        float x1 = box.to()[0] / 16.0F, y1 = box.to()[1] / 16.0F, z1 = box.to()[2] / 16.0F;
        int c = box.color();
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        // uv: one texture tile per face, scaled to the face size so the grain keeps its size
        float su = Math.max(Math.abs(x1 - x0), 0.0625F), sv = Math.max(Math.abs(y1 - y0), 0.0625F), sw = Math.max(Math.abs(z1 - z0), 0.0625F);
        quad(vc, m, n, r, g, b, light, 0, 0, -1, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, su, sv);
        quad(vc, m, n, r, g, b, light, 0, 0, 1, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, su, sv);
        quad(vc, m, n, r, g, b, light, -1, 0, 0, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, sw, sv);
        quad(vc, m, n, r, g, b, light, 1, 0, 0, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, sw, sv);
        quad(vc, m, n, r, g, b, light, 0, -1, 0, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, su, sw);
        quad(vc, m, n, r, g, b, light, 0, 1, 0, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, su, sw);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, int r, int g, int b, int light, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                             float dx, float dy, float dz, float su, float sv) {
        float u = Math.min(1, su), v = Math.min(1, sv);
        vertex(vc, m, n, ax, ay, az, 0, 0, r, g, b, light, nx, ny, nz);
        vertex(vc, m, n, bx, by, bz, u, 0, r, g, b, light, nx, ny, nz);
        vertex(vc, m, n, cx, cy, cz, u, v, r, g, b, light, nx, ny, nz);
        vertex(vc, m, n, dx, dy, dz, 0, v, r, g, b, light, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                               int r, int g, int b, int light, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(r, g, b, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(n, nx, ny, nz).endVertex();
    }
}
