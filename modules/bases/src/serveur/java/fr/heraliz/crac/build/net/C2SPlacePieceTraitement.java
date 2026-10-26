package fr.heraliz.crac.build.net;

import java.util.function.Supplier;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.item.BuildingPlanItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Traitement serveur du paquet C2SPlacePiece (corps d'origine, docs/METHODE_CLIENT_SERVEUR.md §6) : le paquet commun ne garde
 * que son format ; ce traitement est appelé par serveur/PartieServeur.
 */
public final class C2SPlacePieceTraitement {
    private C2SPlacePieceTraitement() {
    }

    /** Minimum delay between two placements of one player; the client waits a little longer. */
    public static void traiter(C2SPlacePiece msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> process(player, msg.key()));
        ctx.get().setPacketHandled(true);
    }    private static void process(@Nullable ServerPlayer player, @Nullable PieceKey key) {
        if (key == null || !PacketGuard.canAct(player) || !(player.getMainHandItem().getItem() instanceof BuildingPlanItem)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        // the key's centre is at most ~3 blocks from any block of the piece; checked before the rules touch the world
        if (!PacketGuard.near(player, level, key, PacketGuard.reach() + PacketGuard.KEY_SLACK)) {
            return;
        }
        if (!PacketGuard.allow(player, PacketGuard.Limit.PLACE, C2SPlacePiece.COOLDOWN_MS)) {
            return;
        }
        StructureService.Outcome out = StructureService.place(level, player, key);
        if (!out.ok()) {
            player.displayClientMessage(out.message().copy().withStyle(ChatFormatting.RED), true);
        }
    }}
