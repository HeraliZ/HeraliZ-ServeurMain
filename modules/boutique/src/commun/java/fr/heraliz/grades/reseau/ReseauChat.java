package fr.heraliz.grades.reseau;

import fr.heraliz.grades.HeralizGrades;
import fr.heraliz.grades.pont.Crochet;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;

/**
 * Canal du chat à canaux (25/10, maquettes {@code maquettes/staff_chat/4_} et {@code 5_} validées) : le joueur dit
 * seulement sur quel canal partent ses messages (Proximité, Global, Clan) ; le serveur décide qui les reçoit (distance,
 * clan, droits, mutes) et envoie chaque ligne avec son canal. Jars joueurs et serveur de la même fabrication.
 */
public final class ReseauChat {
    private ReseauChat() {
    }

    private static final String VERSION = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HeralizGrades.MODID, "chat"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    /** Canaux d'écriture (dans l'ordre des onglets du chat). */
    public static final int PROXIMITE = 0, GLOBAL = 1, CLAN = 2, NOMBRE = 3;
    private static final int MAX_MESSAGE = 256;

    /** Une ligne de chat (serveur -> joueur) : canal, auteur, nom affiché (tag de clan, grade, pseudo), message, couleur (0 : blanc). */
    public record Ligne(int canal, UUID auteur, Component nom, String message, int couleur) {
    }

    /** Canal choisi (joueur -> serveur). */
    public record Choisir(int canal) {
    }

    public record Recu(ServerPlayer joueur, int canal) {
    }

    public static final Crochet<Ligne> RECEPTION_LIGNE = new Crochet<>();
    public static final Crochet<Recu> RECEPTION_CHOISIR = new Crochet<>();

    public static void enregistrer() {
        CANAL.messageBuilder(Ligne.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((m, b) -> {
                    b.writeVarInt(m.canal());
                    b.writeUUID(m.auteur());
                    b.writeComponent(m.nom());
                    b.writeUtf(m.message(), MAX_MESSAGE);
                    b.writeInt(m.couleur());
                })
                .decoder(b -> new Ligne(b.readVarInt(), b.readUUID(), b.readComponent(), b.readUtf(MAX_MESSAGE), b.readInt()))
                .consumerMainThread((m, ctx) -> {
                    RECEPTION_LIGNE.accept(m);
                    ctx.get().setPacketHandled(true);
                }).add();
        CANAL.messageBuilder(Choisir.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, b) -> b.writeVarInt(m.canal()))
                .decoder(b -> new Choisir(b.readVarInt()))
                .consumerMainThread((m, ctx) -> {
                    ServerPlayer p = ctx.get().getSender();
                    if (p != null) RECEPTION_CHOISIR.accept(new Recu(p, m.canal()));
                    ctx.get().setPacketHandled(true);
                }).add();
    }
}
