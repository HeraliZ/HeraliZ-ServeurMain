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

/** Hammer HUD: asks for the health and stability of the piece under the crosshair. Answered by {@link S2CPieceInfo}. */
public record C2SPieceInfo(BlockPos pos, long pieceId) {
    public static final long COOLDOWN_MS = 100L;


    public static void encode(C2SPieceInfo msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeLong(msg.pieceId);
    }

    public static C2SPieceInfo decode(FriendlyByteBuf buf) {
        return new C2SPieceInfo(buf.readBlockPos(), buf.readLong());
    }

    public static void handle(C2SPieceInfo msg, Supplier<NetworkEvent.Context> ctx) {
        // traitement : C2SPieceInfoTraitement.traiter (src/serveur), branché par la partie serveur
        fr.heraliz.crac.network.NetworkHandler.recevoirServeur(msg, ctx);
    }
}
