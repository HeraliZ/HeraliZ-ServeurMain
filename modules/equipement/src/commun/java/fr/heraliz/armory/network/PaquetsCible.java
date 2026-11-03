package fr.heraliz.armory.network;

import fr.heraliz.armory.cible.Tenues;
import fr.heraliz.armory.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Paquets de la cible d'essai (03/11, canal heraliz_armory:main, protocole 5 : tenues complètes seulement) :
 * <ul>
 *    <li>{@link Ouvrir} (serveur → joueur) : écran du choix de l'armure, avec les protections de chaque tenue calculées par
 *    le serveur (vrai plafond, vrai multiplicateur : jamais envoyés autrement) ;</li>
 *    <li>{@link Action} (joueur → serveur) : choisir une tenue, ou ramasser la cible ; le serveur vérifie tout ;</li>
 *    <li>{@link Touche} (serveur → tireur seulement) : un coup mesuré sur la cible.</li>
 * </ul>
 */
public final class PaquetsCible {
    private PaquetsCible() {}

    /**
     * @param corps protection (%) au corps de chaque tenue de {@link Tenues#TOUTES}, dans le même ordre
     * @param tete  protection (%) à la tête
     */
    public record Ouvrir(int entite, String tenue, boolean ramasser, float[] corps, float[] tete) {}

    public static final byte CHOISIR = 0, RAMASSER = 1;

    public record Action(int entite, byte action, String tenue) {}

    /** Zones d'un coup. */
    public static final byte CORPS = 0, TETE = 1, MELEE = 2, EXPLOSION = 3;

    /**
     * Un coup mesuré.
     *
     * @param arme     dégâts du coup avant toute armure (réglages des armes, tête, distance, accessoires compris)
     * @param recu     dégâts qu'aurait perdus un joueur (après armure)
     * @param distance du tireur (yeux) au point touché, en blocs
     * @param tir      départ du tir (tick) pour les armes à plombs : les plombs d'un même tir le partagent ; -1 sinon
     * @param plafond  plomb arrêté par le plafond d'un tir de fusil à pompe (armes.json) : il ne fait rien
     */
    public record Touche(int entite, double x, double y, double z, byte zone, float arme, float recu, float distance,
                         Component nomArme, long tir, boolean plafond, String tenue) {}

    /** Action reçue par le serveur, avec son joueur. */
    public record Recu(ServerPlayer joueur, Action action) {}

    /** Ouvrir et Touche reçus par le jeu du joueur : branché par la partie client (client.cible.CibleClient). */
    public static final Crochet<Object> RECEPTION_CLIENT = new Crochet<>();
    /** Action reçue par le serveur : branchée par la partie serveur (cible.CiblesServeur). */
    public static final Crochet<Recu> RECEPTION_SERVEUR = new Crochet<>();

    static void enregistrer(SimpleChannel canal) {
        canal.messageBuilder(Ouvrir.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((m, buf) -> {
                    buf.writeVarInt(m.entite());
                    buf.writeUtf(m.tenue(), Tenues.MAX_LETTRES);
                    buf.writeBoolean(m.ramasser());
                    ecrire(buf, m.corps());
                    ecrire(buf, m.tete());
                })
                .decoder(buf -> new Ouvrir(buf.readVarInt(), buf.readUtf(Tenues.MAX_LETTRES), buf.readBoolean(),
                        lire(buf), lire(buf)))
                .consumerMainThread((m, ctx) -> {
                    RECEPTION_CLIENT.accept(m);
                    ctx.get().setPacketHandled(true);
                })
                .add();
        canal.messageBuilder(Action.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, buf) -> {
                    buf.writeVarInt(m.entite());
                    buf.writeByte(m.action());
                    buf.writeUtf(m.tenue(), Tenues.MAX_LETTRES);
                })
                .decoder(buf -> new Action(buf.readVarInt(), buf.readByte(), buf.readUtf(Tenues.MAX_LETTRES)))
                .consumerMainThread((m, ctx) -> {
                    ServerPlayer joueur = ctx.get().getSender();
                    if (joueur != null) RECEPTION_SERVEUR.accept(new Recu(joueur, m));
                    ctx.get().setPacketHandled(true);
                })
                .add();
        canal.messageBuilder(Touche.class, 5, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((m, buf) -> {
                    buf.writeVarInt(m.entite());
                    buf.writeDouble(m.x());
                    buf.writeDouble(m.y());
                    buf.writeDouble(m.z());
                    buf.writeByte(m.zone());
                    buf.writeFloat(m.arme());
                    buf.writeFloat(m.recu());
                    buf.writeFloat(m.distance());
                    buf.writeComponent(m.nomArme());
                    buf.writeLong(m.tir());
                    buf.writeBoolean(m.plafond());
                    buf.writeUtf(m.tenue(), Tenues.MAX_LETTRES);
                })
                .decoder(buf -> new Touche(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readByte(),
                        buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readComponent(), buf.readLong(), buf.readBoolean(),
                        buf.readUtf(Tenues.MAX_LETTRES)))
                .consumerMainThread((m, ctx) -> {
                    RECEPTION_CLIENT.accept(m);
                    ctx.get().setPacketHandled(true);
                })
                .add();
    }

    private static void ecrire(FriendlyByteBuf buf, float[] v) {
        int n = Math.min(Tenues.MAX, v.length);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) buf.writeFloat(v[i]);
    }

    private static float[] lire(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > Tenues.MAX) throw new IllegalArgumentException("trop de tenues : " + n);
        float[] v = new float[n];
        for (int i = 0; i < n; i++) v[i] = buf.readFloat();
        return v;
    }
}
