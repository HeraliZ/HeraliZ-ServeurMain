package fr.heraliz.crac.build.net;

import java.util.function.Supplier;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.item.BuildingPlanItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

/** Building Plan left click: place a twig piece in the given socket. {@code key} is null when the client sent garbage. */
public record C2SPlacePiece(@Nullable PieceKey key) {
    public static final long COOLDOWN_MS = 150L;


    public static void encode(C2SPlacePiece msg, FriendlyByteBuf buf) {
        msg.key.write(buf);
    }

    public static C2SPlacePiece decode(FriendlyByteBuf buf) {
        return new C2SPlacePiece(PieceKey.read(buf));   // = PacketGuard.readKey (serveur)
    }

    public static void handle(C2SPlacePiece msg, Supplier<NetworkEvent.Context> ctx) {
        // traitement : C2SPlacePieceTraitement.traiter (src/serveur), branché par la partie serveur
        fr.heraliz.crac.network.NetworkHandler.recevoirServeur(msg, ctx);
    }
}
