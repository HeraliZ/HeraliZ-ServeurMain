package fr.heraliz.crac.build.net;

import java.util.function.Supplier;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.item.BuildingHammerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Traitement serveur du paquet C2SPieceInfo (corps d'origine, docs/METHODE_CLIENT_SERVEUR.md §6) : le paquet commun ne garde
 * que son format ; ce traitement est appelé par serveur/PartieServeur.
 */
public final class C2SPieceInfoTraitement {
    private C2SPieceInfoTraitement() {
    }

    /** The client asks at most this often; faster requests are dropped. */
    public static void traiter(C2SPieceInfo msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> process(player, msg));
        ctx.get().setPacketHandled(true);
    }    private static void process(@Nullable ServerPlayer player, C2SPieceInfo msg) {
        if (!PacketGuard.canAct(player) || !(player.getMainHandItem().getItem() instanceof BuildingHammerItem)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!PacketGuard.near(player, level, msg.pos(), PacketGuard.reach() + 3.0)) {
            return;
        }
        if (!PacketGuard.allow(player, PacketGuard.Limit.INFO, C2SPieceInfo.COOLDOWN_MS)) {
            return;
        }
        Piece p = PacketGuard.pieceAt(level, msg.pos(), msg.pieceId());
        if (p != null && !PacketGuard.inSight(player, level, msg.pos(), p)) {
            // a piece behind a wall: its health is none of the player's business
            return;
        }
        S2CPieceInfo.send(player, p == null ? S2CPieceInfo.gone(msg.pieceId()) : S2CPieceInfoFabrique.of(level, player, p));
    }}
