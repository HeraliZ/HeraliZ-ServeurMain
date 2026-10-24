package fr.heraliz.crates.network;

import fr.heraliz.crates.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Messages reseau. Les piles d'objets sont envoyees avec une quantite d'affichage (64 au plus) ; la vraie quantite
 * voyage a part.
 */
public final class Packets {
    private Packets() {
    }

    private static final int MAX_LIST = 512;

    /** Case d'objet : pile affichee, rarete (ordinal de LootRarity), quantite reelle, chance (%). */
    public record Card(ItemStack stack, int rarity, int count, float chance) {
        static void write(FriendlyByteBuf b, Card c) {
            b.writeItem(c.stack);
            b.writeVarInt(c.rarity);
            b.writeVarInt(c.count);
            b.writeFloat(c.chance);
        }

        static Card read(FriendlyByteBuf b) {
            return new Card(b.readItem(), b.readVarInt(), b.readVarInt(), b.readFloat());
        }
    }

    private static void writeCards(FriendlyByteBuf b, List<Card> list) {
        b.writeVarInt(list.size());
        for (Card c : list) Card.write(b, c);
    }

    private static List<Card> readCards(FriendlyByteBuf b) {
        int n = Math.min(MAX_LIST, b.readVarInt());
        List<Card> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(Card.read(b));
        return out;
    }

    /** Paquet reçu par le serveur, avec son expéditeur. */
    public record Recu(ServerPlayer joueur, Object paquet) {
    }

    /** Réception côté client (écrans d'ouverture et d'édition) : branchée par la partie client. */
    public static final Crochet<Object> RECEPTION_CLIENT = new Crochet<>();
    /** Réception côté serveur (ouverture, passage, fermeture, sauvegarde du butin) : branchée par la partie serveur. */
    public static final Crochet<Recu> RECEPTION_SERVEUR = new Crochet<>();

    private static void serveur(Supplier<NetworkEvent.Context> ctx, Object paquet) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) RECEPTION_SERVEUR.accept(new Recu(p, paquet));
    }

    // =================================================================== interface d'ouverture
    /**
     * Serveur -> client : ouvre l'interface d'une caisse (contenu et chances). session : numero de l'interface (les
     * demandes d'une interface deja remplacee sont ignorees) ; free = cles non consommees (creatif) ; boutique = la
     * boutique de HeraliZ Grades vend la cle de cette caisse (boutons « Acheter », Caisses 2.22.0).
     */
    public record CaseScreen(String crate, int session, boolean free, boolean boutique, List<Card> contents) {
        void write(FriendlyByteBuf b) {
            b.writeUtf(crate);
            b.writeVarInt(session);
            b.writeBoolean(free);
            b.writeBoolean(boutique);
            writeCards(b, contents);
        }

        static CaseScreen read(FriendlyByteBuf b) {
            return new CaseScreen(b.readUtf(64), b.readVarInt(), b.readBoolean(), b.readBoolean(), readCards(b));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    /** Client -> serveur : ouvrir une caisse (une a la fois). */
    public record CaseOpen(int session) {
        void write(FriendlyByteBuf b) {
            b.writeVarInt(session);
        }

        static CaseOpen read(FriendlyByteBuf b) {
            return new CaseOpen(b.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            serveur(ctx, this);
        }
    }

    /** Serveur -> client : resultat. error : cle de traduction (vide = succes) ; la bande de la roue et l'index gagnant. */
    public record CaseResult(String error, int winIndex, List<Card> strip) {
        void write(FriendlyByteBuf b) {
            b.writeUtf(error);
            b.writeVarInt(winIndex);
            writeCards(b, strip);
        }

        static CaseResult read(FriendlyByteBuf b) {
            return new CaseResult(b.readUtf(128), b.readVarInt(), readCards(b));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    /** Client -> serveur : le joueur a passe l'animation (ses objets sont donnes tout de suite). */
    public record CaseSkip() {
        void write(FriendlyByteBuf b) {
        }

        static CaseSkip read(FriendlyByteBuf b) {
            return new CaseSkip();
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            serveur(ctx, this);
        }
    }

    /** Client -> serveur : interface fermee. */
    public record CaseClose(int session) {
        void write(FriendlyByteBuf b) {
            b.writeVarInt(session);
        }

        static CaseClose read(FriendlyByteBuf b) {
            return new CaseClose(b.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            serveur(ctx, this);
        }
    }

    // =================================================================== editeur de butin
    /** Ligne de l'editeur : pile affichee, quantite, poids, rarete, recompense en commande seule. */
    public record Row(ItemStack stack, int count, int weight, int rarity, boolean commandOnly, String itemId) {
        static void write(FriendlyByteBuf b, Row r) {
            b.writeItem(r.stack);
            b.writeVarInt(r.count);
            b.writeVarInt(r.weight);
            b.writeVarInt(r.rarity);
            b.writeBoolean(r.commandOnly);
            b.writeUtf(r.itemId);
        }

        static Row read(FriendlyByteBuf b) {
            return new Row(b.readItem(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readUtf(256));
        }
    }

    /** Serveur -> client : ouvre l'editeur (version = empreinte du butin, pour refuser une sauvegarde perimee). */
    public record EditorOpen(String crate, int version, List<Row> rows) {
        void write(FriendlyByteBuf b) {
            b.writeUtf(crate);
            b.writeInt(version);
            b.writeVarInt(rows.size());
            for (Row r : rows) Row.write(b, r);
        }

        static EditorOpen read(FriendlyByteBuf b) {
            String c = b.readUtf(64);
            int v = b.readInt();
            int n = Math.min(MAX_LIST, b.readVarInt());
            List<Row> rows = new ArrayList<>(n);
            for (int i = 0; i < n; i++) rows.add(Row.read(b));
            return new EditorOpen(c, v, rows);
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    /** Ligne sauvegardee : orig = index de l'entree d'origine (-1 = nouvel objet, pris dans stack). */
    public record SaveRow(int orig, ItemStack stack, int count, int weight, int rarity) {
        static void write(FriendlyByteBuf b, SaveRow r) {
            b.writeVarInt(r.orig + 1);
            b.writeItem(r.stack);
            b.writeVarInt(r.count);
            b.writeVarInt(r.weight);
            b.writeVarInt(r.rarity);
        }

        static SaveRow read(FriendlyByteBuf b) {
            return new SaveRow(b.readVarInt() - 1, b.readItem(), b.readVarInt(), b.readVarInt(), b.readVarInt());
        }
    }

    /** Client -> serveur : nouvelle table de butin d'une caisse. */
    public record EditorSave(String crate, int version, List<SaveRow> rows) {
        void write(FriendlyByteBuf b) {
            b.writeUtf(crate);
            b.writeInt(version);
            b.writeVarInt(rows.size());
            for (SaveRow r : rows) SaveRow.write(b, r);
        }

        static EditorSave read(FriendlyByteBuf b) {
            String c = b.readUtf(64);
            int v = b.readInt();
            int n = Math.min(MAX_LIST, b.readVarInt());
            List<SaveRow> rows = new ArrayList<>(n);
            for (int i = 0; i < n; i++) rows.add(SaveRow.read(b));
            return new EditorSave(c, v, rows);
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            serveur(ctx, this);
        }
    }
}
