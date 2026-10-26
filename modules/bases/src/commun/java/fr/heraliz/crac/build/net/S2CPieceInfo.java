package fr.heraliz.crac.build.net;

import java.util.UUID;
import java.util.function.Supplier;
import fr.heraliz.crac.build.BuildConfig;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * What the hammer HUD needs about one piece and is not in the block occupants: health, stability, and what this
 * player may do with it.
 *
 * @param demolishSeconds seconds left in the demolish window, -1 when demolishing is always allowed
 * @param canDemolish     this player may demolish it right now
 * @param privileged      this player has building privilege over it (upgrade, repair)
 */
public record S2CPieceInfo(long pieceId, boolean exists, int hp, int maxHp, int stability, int tier, int demolishSeconds,
                           boolean canDemolish, boolean privileged) {

    public static S2CPieceInfo gone(long pieceId) {
        return new S2CPieceInfo(pieceId, false, 0, 0, 0, 0, 0, false, false);
    }



    public static void send(ServerPlayer player, S2CPieceInfo info) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), info);
    }

    public static void encode(S2CPieceInfo msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.pieceId);
        buf.writeBoolean(msg.exists);
        buf.writeVarInt(msg.hp);
        buf.writeVarInt(msg.maxHp);
        buf.writeByte(msg.stability);
        buf.writeByte(msg.tier);
        buf.writeVarInt(msg.demolishSeconds);
        buf.writeBoolean(msg.canDemolish);
        buf.writeBoolean(msg.privileged);
    }

    public static S2CPieceInfo decode(FriendlyByteBuf buf) {
        return new S2CPieceInfo(buf.readLong(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readUnsignedByte(),
            buf.readUnsignedByte(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(S2CPieceInfo msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                NetworkHandler.RECEPTION_CLIENT.accept(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }

}
