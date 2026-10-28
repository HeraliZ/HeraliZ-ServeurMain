package fr.heraliz.crac.build.net;

import fr.heraliz.crac.heraliz.Txt;
import java.util.function.Supplier;
import fr.heraliz.crac.build.BuildConfig;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.item.BuildingHammerItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Hammer action on the piece {@code pieceId}, which the client saw in block {@code pos}. {@code tier} is the target
 * tier of an upgrade (ordinal), ignored otherwise.
 */
public record C2SHammer(int action, BlockPos pos, long pieceId, int tier) {
    public static final int UPGRADE = 0;
    public static final int REPAIR = 1;
    public static final int DEMOLISH = 2;
    /** HeraliZ (28/10) : retirer la vitre ou les barreaux d'une fenêtre (rendus au joueur). */
    public static final int RETIRER_INSERT = 3;





    public static void encode(C2SHammer msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.action);
        buf.writeBlockPos(msg.pos);
        buf.writeLong(msg.pieceId);
        buf.writeByte(msg.tier);
    }

    public static C2SHammer decode(FriendlyByteBuf buf) {
        return new C2SHammer(buf.readUnsignedByte(), buf.readBlockPos(), buf.readLong(), buf.readUnsignedByte());
    }

    /** Smallest delay the server accepts between two repairs: the hold time, with some slack for network jitter. */
    public static long repairCooldownMs() {
        return Math.round(BuildConfig.repairSeconds.get() * 1000.0 * 0.75);
    }

    public static void handle(C2SHammer msg, Supplier<NetworkEvent.Context> ctx) {
        // traitement : C2SHammerTraitement.traiter (src/serveur), branché par la partie serveur
        fr.heraliz.crac.network.NetworkHandler.recevoirServeur(msg, ctx);
    }
}
