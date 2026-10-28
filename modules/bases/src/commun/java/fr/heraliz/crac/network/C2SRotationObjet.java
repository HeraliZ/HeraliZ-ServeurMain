package fr.heraliz.crac.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * Quarts de tour (0 à 3) choisis avec R pour l'objet collé aux murs que le joueur tient (HeraliZ, 28/10, protocole 28) :
 * le serveur le garde pour la pose ({@code fr.heraliz.crac.block.OrientationObjet}).
 */
public record C2SRotationObjet(int quarts) {
    public static void encode(C2SRotationObjet msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.quarts & 3);
    }

    public static C2SRotationObjet decode(FriendlyByteBuf buf) {
        return new C2SRotationObjet(buf.readByte() & 3);
    }

    public static void handle(C2SRotationObjet msg, Supplier<Context> ctx) {
        NetworkHandler.recevoirServeur(msg, ctx);
    }
}
