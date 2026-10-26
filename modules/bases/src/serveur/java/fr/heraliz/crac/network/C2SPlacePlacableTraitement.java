package fr.heraliz.crac.network;

import fr.heraliz.crac.heraliz.Heraliz;
import fr.heraliz.crac.heraliz.Txt;
import fr.heraliz.crac.heraliz.ClanAccess;
import java.util.function.Supplier;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.placable.PlacableHpManager;
import fr.heraliz.crac.placable.PlacableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.network.NetworkEvent.Context;
import org.jetbrains.annotations.Nullable;

/**
 * Traitement serveur du paquet C2SPlacePlacable (corps d'origine, docs/METHODE_CLIENT_SERVEUR.md §6) : le paquet commun ne garde
 * que son format ; ce traitement est appelé par serveur/PartieServeur.
 */
public final class C2SPlacePlacableTraitement {
    private C2SPlacePlacableTraitement() {
    }

    public static void traiter(C2SPlacePlacable msg, Supplier<Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> process(player, msg.hand()));
        ctx.get().setPacketHandled(true);
    }    /** Server side of the packet, on the server thread (public for the GameTests). */
    public static void process(@Nullable ServerPlayer player, int hand) {
        if (player == null || !player.isAlive() || player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.getAbilities().mayBuild || Heraliz.blocked(level) || !ActionCooldown.allow(player)) {
            return;
        }
        InteractionHand h = hand == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack held = player.getItemInHand(h);
        if (!(held.getItem() instanceof BlockItem bi) || !PlacableSet.canPlace(level, bi.getBlock())) {
            return;
        }
        boolean claimAnchor = PlacableSet.isClaimAnchor(bi.getBlock());
        HitResult hit = player.pick(Config.adventureReach.get(), 1.0F, false);
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() == Type.MISS) {
            return;
        }
        BlockPos placePos = bhr.getBlockPos().relative(bhr.getDirection());
        // spawn protection and the world border, which the vanilla use path would have checked;
        // the claim, raid block and support rules run in BuildEventHandler.onBlockPlace
        if (!level.mayInteract(player, placePos) || !level.mayInteract(player, bhr.getBlockPos())) {
            return;
        }
        if (!claimAnchor) {
            Claim claim = ClaimManager.getAt(level, placePos);
            if (claim == null) {
                player.displayClientMessage(Txt.red("crac.placable.only_in_claim"), true);
                return;
            }

            Group g = GroupData.get(level.getServer()).getById(claim.groupId());
            if (!ClanAccess.allows(g, player.getUUID(), ClanAccess.Action.BUILD)) {
                player.displayClientMessage(Txt.red("crac.claim.not_yours"), true);
                return;
            }
        }

        boolean prev = player.getAbilities().mayBuild;
        player.getAbilities().mayBuild = true;
        // a damaged item placed from this hand keeps its HP (no block use event is fired here)
        PlacableHpManager.notePlacingHand(player, h);

        try {
            InteractionResult result = ForgeHooks.onPlaceItemIntoWorld(new UseOnContext(player, h, bhr));
            if (result.consumesAction()) {
                BlockState placed = level.getBlockState(placePos);
                SoundType st = placed.getSoundType(level, placePos, player);
                player.playNotifySound(st.getPlaceSound(), SoundSource.BLOCKS, (st.getVolume() + 1.0F) / 2.0F, st.getPitch() * 0.8F);
            }
        } finally {
            player.getAbilities().mayBuild = prev;
        }
    }}
