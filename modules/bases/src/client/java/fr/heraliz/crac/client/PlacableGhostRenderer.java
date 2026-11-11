package fr.heraliz.crac.client;

import fr.heraliz.crac.heraliz.Txt;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.build.client.BuildClient;
import fr.heraliz.crac.placable.MultiBlocks;
import fr.heraliz.crac.placable.PlacableSet;
import fr.heraliz.crac.placable.PlacableSupport;
import fr.heraliz.crac.placable.TableArmurier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
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
                    BlockHitResult hit = raytrace(mc, player);
                    if (hit != null && hit.getType() == Type.BLOCK) {
                        UseOnContext useCtx = new UseOnContext(player, activeHand, hit);
                        BlockPlaceContext ctx = new BlockPlaceContext(useCtx);
                        BlockItem blockItem = (BlockItem)stack.getItem();
                        // HeraliZ (03/11) : table d'armurier visée contre un mur de base : posée dans la rangée du mur,
                        // comme au serveur (placable/TablePose) ; sinon la pose habituelle
                        boolean table = TableArmurier.estTable(blockItem.getBlock());
                        TableArmurier.DansMur contreMur = table ? TableArmurier.dansMur(mc.level, player, hit) : null;
                        // HeraliZ (28/10) : même place que la pose (objet collé aux murs visé sur une case de mur : case
                        // libre voisine, poussé vers elle ; descente sur un plancher à mi-bloc ; sens du joueur)
                        fr.heraliz.crac.block.ObjetAdossable.Place place = contreMur != null ? null
                            : stack.getItem() instanceof fr.heraliz.crac.block.ObjetAdossable oa
                            ? oa.placeDePose(ctx) : ctx.canPlace() ? new fr.heraliz.crac.block.ObjetAdossable.Place(ctx, null) : null;
                        if (place != null || contreMur != null) {
                            // la case de pose : la case libre visée, ou la case du mur où la caisse se met (31/10)
                            BlockPos placePos = contreMur != null ? contreMur.pied() : place.pos();
                            BlockState state = contreMur != null ? TableArmurier.etat(blockItem.getBlock(), contreMur.facing())
                                : place.etat(blockItem.getBlock());
                            boolean blocked = state == null;
                            if (state == null) {
                                state = blockItem.getBlock().defaultBlockState();
                                if (table) {
                                    // la table de TaCZ se met de côté : sa tête à droite du joueur (GunSmithTableBlockB)
                                    state = TableArmurier.etat(blockItem.getBlock(), TableArmurier.facing(player));
                                } else if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
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
                                // dans la rangée du mur (31/10) : l'autre case d'un coffre est une case du mur, laissée à la pose
                                // (03/11 : de même pour la table d'armurier posée contre un mur)
                                if (contreMur == null && place.dansMur() == null && !mc.level.getBlockState(second).canBeReplaced()) {
                                    blocked = true;
                                }
                            }

                            PoseStack pose = e.getPoseStack();
                            Vec3 cam = e.getCamera().getPosition();
                            BufferSource buffers = mc.renderBuffers().bufferSource();
                            BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
                            ClientClaimCache.ClaimHere here = ClientClaimCache.claimAt(placePos);
                            if (here != ClientClaimCache.ClaimHere.NONE) {
                                // the server's reasons (BuildEventHandler.placeProblem), first failing one shown ; HeraliZ
                                // (11/11) : un coffre ou rangement de Minecraft ne se pose plus, nulle part
                                String why = PlacableSet.poseInterdite(state) ? PlacableSet.POSE_INTERDITE : null;
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
                                        // HeraliZ (03/11) : dessiné par le rendu du bloc posé (table d'armurier : même sens et même
                                        // place qu'une fois posée) ; sinon son rendu d'objet tourné comme le bloc
                                        if (!rendreCommePose(mc, parts.get(i), cellx, stack, pose, free, e.getPartialTick())) {
                                            applyFacing(pose, parts.get(i));
                                            dispatcher.renderSingleBlock(parts.get(i), pose, free, 15728880, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                                        }
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

    /** HeraliZ (03/11) : dernier refus du serveur pour un objet posé ({@code RefusServeur}), montré sous le viseur 2 s. */
    @Nullable
    private static net.minecraft.network.chat.Component refus;
    private static long refusA;
    private static final long REFUS_MS = 2000L;

    /** HeraliZ (03/11) : le joueur tient un objet que CRac pose (caisse, coffre, armoire...) dans une main. */
    public static boolean objetEnMain(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof BlockItem bi && PlacableSet.canPlaceClient(bi.getBlock())) {
                return true;
            }
        }
        return false;
    }

    /** HeraliZ (03/11) : refus de pose renvoyé par le serveur, dit sous le viseur à la place du message de Minecraft. */
    public static void refusServeur(net.minecraft.network.chat.Component message) {
        refus = net.minecraft.network.chat.Component.literal(message.getString());
        refusA = net.minecraft.Util.getMillis();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        String why = reason;
        net.minecraft.network.chat.Component serveur = refus != null && net.minecraft.Util.getMillis() - refusA < REFUS_MS ? refus : null;
        // the Building Plan draws its own verdict at the same place
        if (why == null && serveur == null || !e.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id()) || mc.player == null
            || mc.options.hideGui || BuildClient.holdingPlan(mc.player)) {
            return;
        }
        GuiGraphics g = e.getGuiGraphics();
        g.drawCenteredString(mc.font, serveur != null ? serveur : Txt.c(why), e.getWindow().getGuiScaledWidth() / 2,
            e.getWindow().getGuiScaledHeight() / 2 + 12, 0xFF6060);
    }

    /** Entité de bloc factice du fantôme (rendu du bloc posé), gardée tant que l'état et l'objet tenu ne changent pas. */
    @Nullable
    private static BlockEntity factice;
    @Nullable
    private static BlockState facticeEtat;
    private static String facticeId = "";

    /**
     * Dessine l'état comme le jeu le dessinera une fois posé, par le rendu de son entité de bloc (HeraliZ, 03/11). Pour un
     * bloc dessiné par une entité de bloc ({@link RenderShape#ENTITYBLOCK_ANIMATED} : la table d'armurier de TaCZ), le
     * fantôme prenait le rendu de l'objet en main, tourné selon {@code facing} : ni le sens (un quart de tour de travers),
     * ni la place (la table s'étendait du mauvais côté) de la table posée. Faux si l'état n'a pas un tel rendu (rendu
     * d'objet, comme avant). Les modèles de TaCZ prennent eux-mêmes leur tampon de dessin : la table garde ses couleurs,
     * le cadre des cases (bleu ou rouge) et la raison sous le viseur disent si la pose est possible, comme avant.
     */
    private static boolean rendreCommePose(Minecraft mc, BlockState state, BlockPos pos, ItemStack stack, PoseStack pose,
                                           MultiBufferSource buffers, float partial) {
        if (state.getRenderShape() != RenderShape.ENTITYBLOCK_ANIMATED || !(state.getBlock() instanceof EntityBlock eb)) {
            return false;
        }
        // modèle choisi par l'objet (TaCZ : « BlockId » de l'objet, gardé par l'entité de bloc à la pose)
        CompoundTag tag = stack.getTag();
        String id = tag != null && tag.contains("BlockId", Tag.TAG_STRING) ? tag.getString("BlockId") : "";
        if (factice == null || facticeEtat != state || !facticeId.equals(id) || factice.getLevel() != mc.level) {
            factice = null;
            BlockEntity be = eb.newBlockEntity(pos, state);
            if (be == null) {
                return false;
            }
            be.setLevel(mc.level);
            CompoundTag donnees = new CompoundTag();
            if (!id.isEmpty()) {
                donnees.putString("BlockId", id);
            }
            try {
                be.load(donnees);
            } catch (RuntimeException ex) {
                return false;
            }
            factice = be;
            facticeEtat = state;
            facticeId = id;
        }
        BlockEntityRenderer<BlockEntity> r = mc.getBlockEntityRenderDispatcher().getRenderer(factice);
        if (r == null) {
            return false;
        }
        try {
            r.render(factice, partial, pose, buffers, 15728880, OverlayTexture.NO_OVERLAY);
            return true;
        } catch (RuntimeException ex) {
            factice = null;
            return false;
        }
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
        BlockState teteTable = MultiBlocks.teteTacz(state);
        if (teteTable != null) {
            return teteTable;   // table d'armurier de TaCZ (03/11) : sa tête, à droite du joueur
        }
        if (state.getBlock() instanceof fr.heraliz.crac.block.CoffreBlock c) {
            // coffre large : sa case de droite a son propre modèle ; coffre haut : la case du bas dessine tout
            return c.large() ? state.setValue(fr.heraliz.crac.block.CoffreBlock.ANNEXE, true) : null;
        }
        if (state.getBlock() instanceof BedBlock && state.hasProperty(BedBlock.PART)) {
            return state.setValue(BedBlock.PART, BedPart.HEAD);
        } else {
            return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                ? state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER)
                : null;
        }
    }

    private static BlockPos secondCellOf(BlockState state, BlockPos pos) {
        if (MultiBlocks.estTableTacz(state.getBlock())) {
            return MultiBlocks.partner(state, pos);
        }
        if (state.getBlock() instanceof fr.heraliz.crac.block.CoffreBlock c) {
            return c.annexeDe(pos, state.getValue(fr.heraliz.crac.block.CoffreBlock.FACING));
        }
        if (state.getBlock() instanceof BedBlock) {
            return pos.relative(state.getValue(BedBlock.FACING));
        } else {
            return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                ? pos.above()
                : null;
        }
    }

    /**
     * Ce que le clic droit visera (HeraliZ, 31/10) : en survie (le joueur peut construire), la pose est celle de Minecraft,
     * faite sur la cible de son viseur ({@code mc.hitResult}, portée du joueur : 4,5 blocs) ; le fantôme suit exactement
     * cette cible. Avant, il visait toujours à {@code adventureReach} (5 blocs) : entre 4,5 et 5 blocs, un fantôme vert sans
     * pose au clic, ou sur un autre bloc que celui du clic. En aventure, le serveur vise lui-même à {@code adventureReach}
     * (C2SPlacePlacable) : même lancer de rayon.
     */
    @org.jetbrains.annotations.Nullable
    private static BlockHitResult raytrace(Minecraft mc, Player player) {
        if (player.getAbilities().mayBuild) {
            return mc.hitResult instanceof BlockHitResult b ? b : null;
        }
        return raytrace(mc.level, player);
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
