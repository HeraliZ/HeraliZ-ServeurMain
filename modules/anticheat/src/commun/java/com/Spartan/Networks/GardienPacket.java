package com.Spartan.Networks;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * C->S : rapport du Gardien du launcher, transmis TEL QUEL par le jeu (audit du 02/11, point 2). Le jeu ne vérifie
 * rien : c'est le serveur qui contrôle la signature, le défi et le numéro (GardienPreuve, GardienServeur).
 *
 * <p>{@code etat} : « ok » (fichier lu : {@code ligne} et {@code signature} tels qu'écrits par le launcher),
 * « fichier » (pas de rapport), « lecture » (fichier illisible) ou « absent » (jeu lancé sans clé du Gardien).
 * {@code pid} : numéro du processus du jeu.
 */
public class GardienPacket {
    private static final int MAX_ETAT = 16;
    private static final int MAX_LIGNE = 512;
    private static final int MAX_SIGNATURE = 128;

    private final String etat;
    private final String ligne;
    private final String signature;
    private final long pid;

    public GardienPacket(String etat, String ligne, String signature, long pid) {
        this.etat = etat;
        this.ligne = ligne;
        this.signature = signature;
        this.pid = pid;
    }

    public static GardienPacket decode(FriendlyByteBuf buf) {
        return new GardienPacket(buf.readUtf(MAX_ETAT), buf.readUtf(MAX_LIGNE), buf.readUtf(MAX_SIGNATURE), buf.readLong());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cut(this.etat, MAX_ETAT), MAX_ETAT);
        buf.writeUtf(cut(this.ligne, MAX_LIGNE), MAX_LIGNE);
        buf.writeUtf(cut(this.signature, MAX_SIGNATURE), MAX_SIGNATURE);
        buf.writeLong(this.pid);
    }

    public String etat() {
        return this.etat;
    }

    public String ligne() {
        return this.ligne;
    }

    public String signature() {
        return this.signature;
    }

    public long pid() {
        return this.pid;
    }

    /** Traité par la partie serveur (ReceptionServeur), sur le thread du serveur. */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ModMessages.RECEPTION_SERVEUR.accept(new ModMessages.Recu(ctx.get().getSender(), this)));
        ctx.get().setPacketHandled(true);
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
