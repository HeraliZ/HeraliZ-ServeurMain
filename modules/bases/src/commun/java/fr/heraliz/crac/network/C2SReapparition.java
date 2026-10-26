package fr.heraliz.crac.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * Choix du point de réapparition (25/10) : à la base de son clan (vrai) ou au camp. Envoyé juste avant la demande de
 * réapparition ; le serveur ne le garde que pour un joueur mort et revérifie tout ({@code bases/Reapparition}).
 */
public record C2SReapparition(boolean base) {
    public static void encode(C2SReapparition msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.base);
    }

    public static C2SReapparition decode(FriendlyByteBuf buf) {
        return new C2SReapparition(buf.readBoolean());
    }

    public static void handle(C2SReapparition msg, Supplier<Context> ctx) {
        NetworkHandler.recevoirServeur(msg, ctx);
    }
}
