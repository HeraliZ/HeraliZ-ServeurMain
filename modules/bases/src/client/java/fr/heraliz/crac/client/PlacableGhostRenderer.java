package fr.heraliz.crac.client;

import fr.heraliz.crac.heraliz.Txt;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.build.client.BuildClient;
import fr.heraliz.crac.placable.PlacableSet;
import fr.heraliz.crac.placable.PlacableSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(
    modid = "crac",
    value = {Dist.CLIENT}
)
public final class PlacableGhostRenderer {
    private static final RenderType GHOST_RT = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);
    private static final float[] VALID = new float[]{0.4F, 0.65F, 1.0F};
    private static final float[] INVALID = new float[]{1.0F, 0.1F, 0.1F};
    /** Fallback before the server config arrives; the server places with {@code adventureReach}. */
    private static final double DEFAULT_REACH = 5.0;
    /** Why the ghost drawn this frame is red, shown under the crosshair like the Building Plan's; null when it is not. */
    @Nullable
    private static String reason;

    private PlacableGhostRenderer() {
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent e) {
        if (e.getStage() == Stage.AFTER_BLOCK_ENTITIES) {
            reason = null;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                Player player = mc.player;
                ItemStack stack = ItemStack.EMPTY;
                InteractionHand activeHand = InteractionHand.MAIN_HAND;

                for (InteractionHand hand : InteractionHand.values()) {
                    ItemStack s = player.getItemInHand(hand);
                    if (!s.isEmpty() && s.getItem() instanceof BlockItem gbi && PlacableSet.containsClient(gbi.getBlock())) {
                        stack = s;
                        activeHand = hand;
                        break;
                    }
                }

                if (!stack.isEmpty()) {
                    BlockHitResult hit = raytrace(mc.level, player);
                    if (hit.getType() == Type.BLOCK) {
                        UseOnContext useCtx = new UseOnContext(player, activeHand, hit);
                        BlockPlaceContext ctx = new BlockPlaceContext(useCtx);
                        if (ctx.canPlace()) {
                            BlockPos placePos = ctx.getClickedPos();
                            BlockItem blockItem = (BlockItem)stack.getItem();
                            BlockState state = blockItem.getBlock().getStateForPlacement(ctx);
                            boolean blocked = state == null;
                            if (state == null) {
                                state = blockItem.getBlock().defaultBlockState();
                                if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
                                    state = state.setValue(HorizontalDirectionalBlock.FACING, player.getDirection().getOpposite());
                                }
                            }

                            List<BlockPos> cells = new ArrayList<>(2);
                            List<BlockState> parts = new ArrayList<>(2);
                            cells.add(placePos);
                            parts.add(state);
                            BlockPos second = secondCellOf(state, placePos);
                            BlockState secondPart = secondPartOf(state);
                            if (second != null && secondPart != null) {
                                cells.add(second);
                                parts.add(secondPart);
                                if (!mc.level.getBlockState(second).canBeReplaced()) {
                                    blocked = true;
                                }
                            }

                            PoseStack pose = e.getPoseStack();
                            Vec3 cam = e.getCamera().getPosition();
                            BufferSource buffers = mc.renderBuffers().bufferSource();
                            BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
                            ClientClaimCache.ClaimHere here = ClientClaimCache.claimAt(placePos);
                            if (here != ClientClaimCache.ClaimHere.NONE) {
                                // the server's reasons (BuildEventHandler.placeProblem), first failing one shown
                                String why = null;
                                for (BlockPos cell : cells) {
                                    ClientClaimCache.ClaimHere claim = ClientClaimCache.claimAt(cell);
                                    if (why == null && claim != ClientClaimCache.ClaimHere.OWN) {
                                        why = claim == ClientClaimCache.ClaimHere.FOREIGN ? "crac.claim.not_yours" : "crac.claim.must_fit";
                                    }
                                }
                                if (why == null && Config.SPEC.isLoaded() && Config.raidBlockBuilding.get() && RaidBlockOverlay.ownBaseBlocked()) {
                                    why = "crac.build.raid_blocked";
                                }
                                // a placable stands on a floor, the ground or another placable
                                if (why == null && PlacableSupport.enabled()) {
                                    why = PlacableSupport.supportProblem(mc.level, cells, state);
                                }
                                boolean ok = !blocked && why == null;
                                reason = why;

                                float[] col = ok ? VALID : INVALID;
                                TintedBufferSource tinted = new TintedBufferSource(buffers, col[0], col[1], col[2], 1.0F, GHOST_RT);
                                if (state.getRenderShape() == RenderShape.MODEL) {
                                    for (int i = 0; i < cells.size(); i++) {
                                        BlockPos cellx = cells.get(i);
                                        pose.pushPose();
                                        pose.translate(cellx.getX() - cam.x, cellx.getY() - cam.y, cellx.getZ() - cam.z);
                                        dispatcher.renderSingleBlock(parts.get(i), pose, tinted, 15728880, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                                        pose.popPose();
                                    }

                                    buffers.endBatch(GHOST_RT);
                                } else {
                                    TintedBufferSource free = new TintedBufferSource(buffers, col[0], col[1], col[2], 1.0F);

                                    for (int i = 0; i < cells.size(); i++) {
                                        BlockPos cellx = cells.get(i);
                                        pose.pushPose();
                                        pose.translate(cellx.getX() - cam.x, cellx.getY() - cam.y, cellx.getZ() - cam.z);
                                        applyFacing(pose, parts.get(i));
                                        dispatcher.renderSingleBlock(parts.get(i), pose, free, 15728880, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                                        pose.popPose();
                                    }

                                    buffers.endBatch();
                                    drawOutline(pose, buffers, cam, cells, col);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        String why = reason;
        // the Building Plan draws its own verdict at the same place
        if (why == null || !e.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id()) || mc.player == null || mc.options.hideGui
            || BuildClient.holdingPlan(mc.player)) {
            return;
        }
        GuiGraphics g = e.getGuiGraphics();
        g.drawCenteredString(mc.font, Txt.c(why), e.getWindow().getGuiScaledWidth() / 2, e.getWindow().getGuiScaledHeight() / 2 + 12, 0xFF6060);
    }

    private static void applyFacing(PoseStack pose, BlockState state) {
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            float yRot = state.getValue(HorizontalDirectionalBlock.FACING).toYRot();
            if (yRot != 0.0F) {
                pose.translate(0.5, 0.5, 0.5);
                pose.mulPose(Axis.YP.rotationDegrees(-yRot));
                pose.translate(-0.5, -0.5, -0.5);
            }
        }
    }

    private static void drawOutline(PoseStack pose, BufferSource buffers, Vec3 cam, List<BlockPos> cells, float[] col) {
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        for (BlockPos cell : cells) {
            LevelRenderer.renderLineBox(pose, lines, new AABB(cell).deflate(0.02), col[0], col[1], col[2], 0.9F);
        }

        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static BlockState secondPartOf(BlockState state) {
        if (state.getBlock() instanceof BedBlock && state.hasProperty(BedBlock.PART)) {
            return state.setValue(BedBlock.PART, BedPart.HEAD);
        } else {
            return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                ? state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER)
                : null;
        }
    }

    private static BlockPos secondCellOf(BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof BedBlock) {
            return pos.relative(state.getValue(BedBlock.FACING));
        } else {
            return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                ? pos.above()
                : null;
        }
    }

    /** Same reach as the server's C2SPlacePlacable (synced server config). */
    private static double reach() {
        try {
            return Config.adventureReach.get();
        } catch (IllegalStateException e) {
            return DEFAULT_REACH;
        }
    }

    private static BlockHitResult raytrace(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        double reach = reach();
        Vec3 end = eye.add(look.x * reach, look.y * reach, look.z * reach);
        return level.clip(new ClipContext(eye, end, Block.OUTLINE, Fluid.NONE, player));
    }
}
