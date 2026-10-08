package fr.heralisz.economie.network;

import net.minecraft.network.FriendlyByteBuf;
import fr.heraliz.clansetbases.pont.Crochet;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Messages serveur -> client (records avec write/read/handle). Les handlers passent par {@link #RECEPTION_CLIENT}, branche
 * sur {@code client.ClientSolde} par la partie client. Les chaines sont bornees des la construction du record : un
 * motif ou un nom trop long est coupe, jamais d'EncoderException au moment de l'envoi.
 */
public final class Packets {
    private Packets() {
    }

    /**
     * Reception cote client (ClientSolde.recevoir / reglages) : branchee par la partie client du mod
     * ({@code economie.client.PartieClient}), neutre ailleurs (serveur dedie, jar -serveur).
     */
    public static final Crochet<Object> RECEPTION_CLIENT = new Crochet<>();

    /** Chaine non nulle coupee a max caracteres (limite de writeUtf). */
    static String borner(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    /**
     * Nouveau solde du joueur. delta = variation (0 a la connexion ou apres /eco recharger), motif = chaine du
     * serveur (« quete:zombies », « paiement »…, 64 caracteres max) que le client rend lisible.
     */
    public record SoldeS2C(long solde, long delta, String motif) {
        public static final int MOTIF_MAX = 64;

        public SoldeS2C {
            motif = borner(motif, MOTIF_MAX);
        }

        void write(FriendlyByteBuf b) {
            b.writeVarLong(solde);
            b.writeVarLong(delta);
            b.writeUtf(motif, MOTIF_MAX);
        }

        static SoldeS2C read(FriendlyByteBuf b) {
            return new SoldeS2C(b.readVarLong(), b.readVarLong(), b.readUtf(MOTIF_MAX));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    /** Reglages utiles au client (noms de l'unite 32 max, symbole 8 max, HUD permanent), envoyes a la connexion. */
    public record ReglagesS2C(String nomSingulier, String nomPluriel, String symbole, boolean hudPermanent) {
        public static final int NOM_MAX = 32, SYMBOLE_MAX = 8;

        public ReglagesS2C {
            nomSingulier = borner(nomSingulier, NOM_MAX);
            nomPluriel = borner(nomPluriel, NOM_MAX);
            symbole = borner(symbole, SYMBOLE_MAX);
        }

        void write(FriendlyByteBuf b) {
            b.writeUtf(nomSingulier, NOM_MAX);
            b.writeUtf(nomPluriel, NOM_MAX);
            b.writeUtf(symbole, SYMBOLE_MAX);
            b.writeBoolean(hudPermanent);
        }

        static ReglagesS2C read(FriendlyByteBuf b) {
            return new ReglagesS2C(b.readUtf(NOM_MAX), b.readUtf(NOM_MAX), b.readUtf(SYMBOLE_MAX), b.readBoolean());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }
}
