package fr.heraliz.crac.build.client;

import fr.heraliz.crac.heraliz.Heraliz;
import fr.heraliz.crac.heraliz.Txt;
import java.util.List;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.build.client.render.GhostMesh;
import fr.heraliz.crac.build.geom.RoofField;
import fr.heraliz.crac.build.logic.PlacementRules;
import fr.heraliz.crac.build.logic.StructureView;
import fr.heraliz.crac.build.logic.Supports;
import fr.heraliz.crac.build.logic.Variants;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.net.C2SPlacePiece;
import fr.heraliz.crac.build.world.BuildCost;
import fr.heraliz.crac.build.world.ClientMirror;
import fr.heraliz.crac.build.world.LevelEnv;
import fr.heraliz.crac.build.logic.Assises;
import fr.heraliz.crac.client.ClientClaimCache;
import fr.heraliz.crac.client.RaidBlockOverlay;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2CNearbyClaims;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Building Plan in hand: R rotates, holding right click (or the menu key) opens the piece wheel, left click places
 * the ghost. The ghost is recomputed every frame from the camera ray and checked with the same rules as the server.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "crac", value = Dist.CLIENT)
public final class PlanController {
    public static final int VALID_ARGB = 0x8040A0FF;
    public static final int INVALID_ARGB = 0x80FF4040;
    private static final long PLACE_COOLDOWN_MS = C2SPlacePiece.COOLDOWN_MS + 50L;

    public enum Severity { OK, ERROR, HINT }

    /** {@code sendable}: a click still goes to the server (the client may be wrong, e.g. about stability). */
    public record Status(Severity severity, Component message, boolean sendable) {
        public boolean ok() {
            return this.severity == Severity.OK;
        }

        /** {@code msg}: a translation key or an encoded message ({@link Txt#k}). */
        static Status error(String msg) {
            return new Status(Severity.ERROR, Txt.c(msg), false);
        }

        static Status error(Component msg) {
            return new Status(Severity.ERROR, msg, false);
        }
    }

    @Nullable
    private static PieceKey ghost;
    @Nullable
    private static Status status;
    @Nullable
    private static PieceKey checkedKey;
    private static long checkedTick = Long.MIN_VALUE;
    private static Status checked;
    /** Building the checked key would join (for the ghost's roof variant), or 0. */
    private static long checkedBuilding;
    private static long lastPlace;
    private static PlanState.Note rotationNote = PlanState.Note.NONE;

    private PlanController() {
    }

    /** The socket the plan aims at this frame, or null. */
    @Nullable
    public static PieceKey ghost() {
        return ghost;
    }

    /** Placement verdict for the ghost (or a hint when there is none); null when not holding the plan. */
    @Nullable
    public static Status status() {
        return status;
    }

    /** What the ghost does with the rotation counter beyond {@link PlanState#rotationText}, for the HUD. */
    public static PlanState.Note rotationNote() {
        return rotationNote;
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && BuildClient.holdingPlan(p) && (event.isAttack() || event.isUseItem())) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** The vanilla block outline only shows one block of a piece: the ghost and the hammer panel replace it. */
    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && (BuildClient.holdingPlan(p) || BuildClient.holdingHammer(p))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !BuildClient.holdingPlan(mc.player)) {
            return;
        }
        if (mc.screen != null) {
            return;
        }
        // take the clicks before vanilla turns them into attacks or item use
        boolean place = false;
        while (mc.options.keyAttack.consumeClick()) {
            place = true;
        }
        while (mc.options.keyUse.consumeClick()) {
            // the wheel opens below while the button is held
        }
        while (BuildKeys.ROTATE.consumeClick()) {
            if (Screen.hasShiftDown()) {
                PlanState.rotateBack();
            } else {
                PlanState.rotate();
            }
            checkedKey = null;
        }
        if (mc.player.isSpectator()) {
            return;
        }
        if (mc.options.keyUse.isDown() && RadialMenuScreen.mayOpen()) {
            mc.setScreen(new PlanRadialScreen(mc.options.keyUse.getKey()));
            return;
        }
        if (BuildKeys.MENU.isDown() && RadialMenuScreen.mayOpen()) {
            mc.setScreen(new PlanRadialScreen(BuildKeys.MENU.getKey()));
            return;
        }
        if (place) {
            tryPlace(mc);
        }
    }

    private static void tryPlace(Minecraft mc) {
        long now = Util.getMillis();
        if (now - lastPlace < PLACE_COOLDOWN_MS || ghost == null || status == null) {
            return;
        }
        if (!status.sendable()) {
            mc.player.displayClientMessage(status.message().copy().withStyle(ChatFormatting.RED), true);
            return;
        }
        lastPlace = now;
        NetworkHandler.CHANNEL.sendToServer(new C2SPlacePiece(ghost));
        mc.player.swing(InteractionHand.MAIN_HAND);
        checkedKey = null;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        update(mc, event.getPartialTick());
        if (ghost == null || status == null) {
            return;
        }
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        GhostMesh.render(event.getPoseStack(), buffers, event.getCamera().getPosition(), ghost, Tier.TWIG, 0, ghostVariant(ghost),
            status.ok() ? VALID_ARGB : INVALID_ARGB);
        buffers.endBatch();
    }

    private static void update(Minecraft mc, float partialTick) {
        ghost = null;
        rotationNote = PlanState.Note.NONE;
        if (mc.player == null || mc.level == null || mc.player.isSpectator() || !BuildClient.holdingPlan(mc.player)) {
            status = null;
            return;
        }
        PieceKind kind = PlanState.selected();
        int rot = PlanState.rotation();
        Sockets.Aim aim = BuildClient.aim(mc, partialTick);
        PieceKey key = Sockets.candidate(ClientMirror.view(), kind, rot, aim);
        // tutoriel des bases : viser la pièce verte elle-même suffit (pas seulement le pied de la fondation)
        PieceKey tuto = fr.heraliz.crac.bases.TutoClient.vise(kind, aim.eye(), aim.look());
        if (tuto != null) {
            key = tuto;
        }
        if (key == null) {
            status = new Status(Severity.HINT, Component.translatable(hint(kind)), false);
            return;
        }
        rotationNote = PlanState.rotationNote(ClientMirror.view(), kind, rot, key, aim.eye());
        long tick = mc.level.getGameTime();
        if (!key.equals(checkedKey) || tick != checkedTick) {
            checked = validate(mc, key);
            checkedKey = key;
            checkedTick = tick;
        }
        ghost = key;
        status = checked;
    }

    private static String hint(PieceKind kind) {
        if (kind.isFoundation()) {
            return "crac.plan.hint_foundation";
        }
        return switch (kind.family) {
            case WALL -> "crac.plan.hint_wall";
            case VOLUME -> "crac.plan.hint_volume";
            default -> "crac.plan.hint_surface";
        };
    }

    /** Roofs merge with the roofs of the building they would join; walls draw every post of their bands. */
    private static long ghostVariant(PieceKey key) {
        if (key.kind().isRoof()) {
            return checkedBuilding > 0 ? Variants.roofVariant(ClientMirror.view(), key, checkedBuilding) : RoofField.ownVariant(key.kind().shape);
        }
        if (key.kind().isWall()) {
            return key.topUnits() >= 2
                ? Variants.POST_A_LOW | Variants.POST_A_HIGH | Variants.POST_B_LOW | Variants.POST_B_HIGH
                : Variants.POST_A_LOW | Variants.POST_B_LOW;
        }
        return 0L;
    }

    // ------------------------------------------------------------------ client-side verdict

    /** Grid rules, then what the client can tell of reach, claims and materials (the server decides). */
    private static Status validate(Minecraft mc, PieceKey key) {
        LocalPlayer player = mc.player;
        if (Heraliz.blocked(mc.level)) {
            return Status.error(Heraliz.blockedMessage());
        }
        if (player != null && Heraliz.horsMondeBases(player) != null) {
            return Status.error(Heraliz.horsMondeBases(player));
        }
        StructureView view = ClientMirror.view();
        MirrorStability.refresh(BuildClient.stability());
        PlacementRules.Plan plan = PlacementRules.check(view, new LevelEnv(mc.level), key, BuildClient.stability(), BuildClient.maxSkirt());
        checkedBuilding = plan.ok() && plan.buildingId() != PlacementRules.NEW_BUILDING ? plan.buildingId() : supportBuilding(view, key);
        if (plan.result() == PlacementRules.Result.UNSTABLE) {
            // the mirror may miss supports in unloaded chunks: let the server decide
            return new Status(Severity.ERROR, Txt.c(plan.result().message), true);
        }
        if (!plan.ok()) {
            return Status.error(plan.result().message);
        }
        // HeraliZ : pendant le tutoriel des bases, seulement la pièce verte qui clignote
        String tuto = player.getAbilities().instabuild ? null : fr.heraliz.crac.bases.TutoClient.refus(key);
        if (tuto != null) {
            return Status.error(tuto);
        }
        // HeraliZ (27/10) : zone où l'on ne construit pas (masque envoyé par le serveur, dessiné en rouge)
        if (!player.getAbilities().instabuild && fr.heraliz.crac.client.ZonesInterdites.touche(key, plan.blocks())) {
            return Status.error("crac.zone.interdite_plan");
        }
        if (!player.getAbilities().instabuild) {
            if (!inReach(player.getEyePosition(), key, plan.blocks())) {
                return Status.error("crac.build.too_far");
            }
            String claim = claimProblem(view, key, plan);
            if (claim != null) {
                return Status.error(claim);
            }
            List<BuildCost> cost = BuildClient.placeCost(key.kind());
            if (!BuildClient.has(player, cost)) {
                return Status.error(Txt.k("crac.build.need", BuildClient.describe(cost)));
            }
        }
        return new Status(Severity.OK, Txt.c(PlacementRules.Result.OK.message), true);
    }

    /** The building of the first support of a key (for the ghost of a refused roof), or 0. */
    private static long supportBuilding(StructureView view, PieceKey key) {
        if (key.kind().isFoundation() || !key.isWellFormed()) {
            return 0L;
        }
        List<Supports.Support> s = Supports.of(view, key);
        return s.isEmpty() ? 0L : s.get(0).piece().buildingId;
    }

    private static boolean inReach(Vec3 eye, PieceKey key, List<BlockPos> blocks) {
        double reach = BuildClient.reach() + 1.0;
        for (AABB box : PlacementRules.worldBoxes(key, blocks)) {
            if (box.inflate(0.5).distanceToSqr(eye) <= reach * reach) {
                return true;
            }
        }
        return false;
    }

    /** Mirrors the server's claim rules with the claims the client knows about; null when nothing is wrong. */
    @Nullable
    private static String claimProblem(StructureView view, PieceKey key, PlacementRules.Plan plan) {
        List<S2CNearbyClaims.ClaimEntry> claims = ClientClaimCache.get();
        List<BlockPos> blocks = plan.blocks();
        if (plan.buildingId() == PlacementRules.NEW_BUILDING) {
            for (BlockPos pos : blocks) {
                if (S2CNearbyClaims.claimAt(claims, pos) != null) {
                    return "crac.build.no_new_in_claim";
                }
            }
            return null;
        }
        Building b = view.building(plan.buildingId());
        S2CNearbyClaims.ClaimEntry own = b == null ? null : claimOf(view, b, claims);
        if (own != null) {
            if (!own.ownedByMe()) {
                return "crac.build.not_yours";
            }
            for (BlockPos pos : blocks) {
                if (!own.contains(pos)) {
                    return "crac.build.outside_zone";
                }
            }
            for (BlockPos pos : blocks) {
                // where zones overlap, a block belongs to the nearest Claim Block
                if (!own.equals(S2CNearbyClaims.claimAt(claims, pos))) {
                    return "crac.build.too_close";
                }
            }
            if (Config.SPEC.isLoaded() && Config.raidBlockBuilding.get() && RaidBlockOverlay.ownBaseBlocked()) {
                return "crac.build.raid_blocked";
            }
            return null;
        }
        for (BlockPos pos : blocks) {
            if (S2CNearbyClaims.claimAt(claims, pos) != null) {
                return "crac.build.foreign_claim";
            }
        }
        // HeraliZ : la cabane du tutoriel des bases se bâtit avant son Claim Block (le serveur le vérifie)
        if (b != null && b.pieces.size() >= BuildClient.maxPiecesWithoutClaim() && !fr.heraliz.crac.bases.TutoClient.actif()) {
            return "crac.build.claim_first";
        }
        return null;
    }

    /** The claim whose Claim Block stands on a surface of building {@code b} (the seat rule, §7.5). */
    @Nullable
    private static S2CNearbyClaims.ClaimEntry claimOf(StructureView view, Building b, List<S2CNearbyClaims.ClaimEntry> claims) {
        for (S2CNearbyClaims.ClaimEntry e : claims) {
            Piece seat = Assises.seatUnder(view, e.center());
            if (seat != null && seat.buildingId == b.id) {
                return e;
            }
        }
        return null;
    }
}
