package fr.heraliz.wonder.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Reglage du serveur utile au client : tir des Wonder Weapons autorise hors partie de zombies. */
public record Regle(boolean tirHorsZombies) {
    /** Copie cote client (le client refuse le tir comme le serveur, sans animation ni son pour rien). */
    public static volatile boolean clientTirHorsZombies;

    public static void ecrire(Regle m, FriendlyByteBuf b) {
        b.writeBoolean(m.tirHorsZombies);
    }

    public static Regle lire(FriendlyByteBuf b) {
        return new Regle(b.readBoolean());
    }

    public static void recevoir(Regle m, Supplier<NetworkEvent.Context> c) {
        clientTirHorsZombies = m.tirHorsZombies;
        c.get().setPacketHandled(true);
    }
}
