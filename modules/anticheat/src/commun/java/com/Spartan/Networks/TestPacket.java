package com.Spartan.Networks;

import com.Spartan.Spartan;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * C->S : identifiant matériel du client (UUID carte mère ou HWID).
 *
 * - longueur et caractères validés (UUID / hexadécimal uniquement) ;
 * - "unknown" (lecture matérielle impossible) n'est JAMAIS enregistré ni banni :
 *   sinon bannir une seule de ces valeurs bannirait tous les joueurs concernés ;
 * - chaque serial n'est traité qu'une fois par connexion, avec un plafond ;
 * - PC banni → expulsion ; si le compte est différent du compte banni, c'est
 *   probablement un compte secondaire : alerte au staff.
 */
public class TestPacket {
    private static final int MAX_LEN = 128;
    private static final Pattern SERIAL = Pattern.compile("^[A-Za-z0-9-]{4,128}$");

    private final String message;

    public TestPacket(String message) {
        this.message = message;
    }

    public static TestPacket decode(FriendlyByteBuf buf) {
        return new TestPacket(buf.readUtf(MAX_LEN));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.message.length() > MAX_LEN ? this.message.substring(0, MAX_LEN) : this.message, MAX_LEN);
    }

    public String message() {
        return this.message;
    }

    /** Traité par la partie serveur (ReceptionServeur), sur le thread du serveur. */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ModMessages.RECEPTION_SERVEUR.accept(new ModMessages.Recu(ctx.get().getSender(), this)));
        ctx.get().setPacketHandled(true);
    }
}
