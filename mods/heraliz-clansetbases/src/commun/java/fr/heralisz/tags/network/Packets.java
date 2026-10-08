package fr.heralisz.tags.network;

import fr.heraliz.clansetbases.pont.Crochet;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Messages reseau du mod. Ids de tags = chaines courtes ([a-z0-9_], 32 au plus) ; la roue = 4 emplacements,
 * chaine vide = emplacement libre. Les handlers delèguent a ServerHandlers (serveur) et ClientHandlers (client).
 */
public final class Packets {
    private Packets() {
    }

    public static final int MAX_ID = 32, MAX_LISTE = 256, ROUE = 4;

    /**
     * Paquets recus par le client : branche par la partie client du mod (aiguillage vers les traitements d'origine),
     * neutre ailleurs (serveur dedie, jar -serveur).
     */
    public static final Crochet<Object> RECEPTION_CLIENT = new Crochet<>();
    /**
     * Paquets recus par le serveur, avec le joueur qui les envoie : branche par la partie serveur du mod (traitements
     * d'origine, qui reverifient tout), neutre sans elle (jar -client).
     */
    public static final Crochet<Recu> RECEPTION_SERVEUR = new Crochet<>();

    /** Paquet recu par le serveur et son expediteur. */
    public record Recu(ServerPlayer joueur, Object paquet) {
    }

    private static void writeIds(FriendlyByteBuf b, List<String> ids) {
        b.writeVarInt(ids.size());
        for (String s : ids) b.writeUtf(s == null ? "" : s, MAX_ID);
    }

    private static List<String> readIds(FriendlyByteBuf b) {
        int n = Math.min(MAX_LISTE, b.readVarInt());
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(b.readUtf(MAX_ID));
        return out;
    }

    private static void writeVec(FriendlyByteBuf b, Vec3 v) {
        b.writeDouble(v.x);
        b.writeDouble(v.y);
        b.writeDouble(v.z);
    }

    private static Vec3 readVec(FriendlyByteBuf b) {
        return new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
    }

    /** Roue lue depuis le reseau : exactement 4 entrees (completee par des vides, tronquee sinon). */
    private static List<String> roue4(List<String> in) {
        List<String> out = new ArrayList<>(ROUE);
        for (int i = 0; i < ROUE; i++) out.add(i < in.size() ? in.get(i) : "");
        return out;
    }

    // =================================================================== serveur -> client
    /** Etat du joueur : tags debloques (ids) + roue (4 ids, "" = vide) + portee de pose du serveur (blocs). Envoye a la
     *  connexion et a tout changement. */
    public record EtatS2C(List<String> debloques, List<String> roue, float portee) {
        void write(FriendlyByteBuf b) {
            writeIds(b, debloques);
            writeIds(b, roue4(roue));
            b.writeFloat(portee);
        }

        static EtatS2C read(FriendlyByteBuf b) {
            return new EtatS2C(readIds(b), roue4(readIds(b)), b.readFloat());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    /**
     * Un tag pose : id unique serveur, joueur, tag, point d'impact (celui du rayon rejoue par le serveur), face touchee,
     * roulis (degres, choisi par le serveur), cap horizontal du tagueur au moment de la pose (degres : oriente les tags
     * au sol / plafond de la meme facon chez tous les spectateurs et a la recuisson), date de creation
     * (System.currentTimeMillis serveur ; le client se cale sur sa propre horloge a la reception).
     */
    public record TagS2C(long id, UUID joueur, String tagId, Vec3 point, Direction face, float angle, float cap, long creeLe) {
        void write(FriendlyByteBuf b) {
            b.writeVarLong(id);
            b.writeUUID(joueur);
            b.writeUtf(tagId, MAX_ID);
            writeVec(b, point);
            b.writeEnum(face);
            b.writeFloat(angle);
            b.writeFloat(cap);
            b.writeLong(creeLe);
        }

        static TagS2C read(FriendlyByteBuf b) {
            return new TagS2C(b.readVarLong(), b.readUUID(), b.readUtf(MAX_ID), readVec(b), b.readEnum(Direction.class), b.readFloat(), b.readFloat(), b.readLong());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    /** Un tag retire avant son terme (plafond atteint, ou nettoyage). */
    public record TagRetireS2C(long id) {
        void write(FriendlyByteBuf b) {
            b.writeVarLong(id);
        }

        static TagRetireS2C read(FriendlyByteBuf b) {
            return new TagRetireS2C(b.readVarLong());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    /** Ouvre la collection (apres /tags). */
    public record OuvrirIndexS2C() {
        void write(FriendlyByteBuf b) {
        }

        static OuvrirIndexS2C read(FriendlyByteBuf b) {
            return new OuvrirIndexS2C();
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    // =================================================================== client -> serveur
    /** Demande de pose : tag, point d'impact du rayon (yeux, portee), face touchee. Tout est reverifie au serveur. */
    public record TagC2S(String tagId, Vec3 point, Direction face) {
        void write(FriendlyByteBuf b) {
            b.writeUtf(tagId, MAX_ID);
            writeVec(b, point);
            b.writeEnum(face);
        }

        static TagC2S read(FriendlyByteBuf b) {
            return new TagC2S(b.readUtf(MAX_ID), readVec(b), b.readEnum(Direction.class));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) RECEPTION_SERVEUR.accept(new Recu(p, this));
        }
    }

    /** Nouvelle roue (4 ids, "" = vide). */
    public record RoueC2S(List<String> ids) {
        void write(FriendlyByteBuf b) {
            writeIds(b, roue4(ids));
        }

        static RoueC2S read(FriendlyByteBuf b) {
            return new RoueC2S(roue4(readIds(b)));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) RECEPTION_SERVEUR.accept(new Recu(p, this));
        }
    }

    /** Le client demande l'ouverture de la collection (le serveur renvoie l'etat a jour puis OuvrirIndexS2C). */
    public record OuvrirIndexC2S() {
        void write(FriendlyByteBuf b) {
        }

        static OuvrirIndexC2S read(FriendlyByteBuf b) {
            return new OuvrirIndexC2S();
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) RECEPTION_SERVEUR.accept(new Recu(p, this));
        }
    }
}
