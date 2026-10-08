package fr.heralisz.bases.network;

import fr.heraliz.clansetbases.pont.Crochet;
import fr.heralisz.bases.model.BaseVue;
import fr.heralisz.bases.model.FenetreVue;
import fr.heralisz.bases.model.PillableVue;
import fr.heralisz.bases.model.Plan;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Messages reseau du mod (records avec write/read/handle). S2C = serveur vers client, C2S = client vers serveur.
 * Les S2C delèguent a {@code client.ClientHandlers} (classe client seulement), les C2S a {@code server.ServerHandlers}
 * qui verifie tout (le client n'est jamais cru).
 */
public final class Packets {
    private Packets() {
    }

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

    private static <E extends Enum<E>> E enumLu(FriendlyByteBuf b, E[] valeurs, E defaut) {
        int i = b.readByte();
        return i >= 0 && i < valeurs.length ? valeurs[i] : defaut;
    }

    /** Coupe une chaine a max caracteres (jamais d'EncoderException a l'envoi). */
    public static String coupe(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    // =================================================================== serveur -> client
    /** Etat de la base de mon clan (onglet / ecran BASE). */
    public record EtatBaseS2C(BaseVue vue) {
        void write(FriendlyByteBuf b) {
            vue.write(b);
        }

        static EtatBaseS2C read(FriendlyByteBuf b) {
            return new EtatBaseS2C(BaseVue.read(b));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    /** Plan du bunker (metadonnees de bunker.json) ; null = pas de modele charge sur le serveur. */
    public record PlanS2C(@Nullable Plan plan) {
        void write(FriendlyByteBuf b) {
            b.writeBoolean(plan != null);
            if (plan != null) plan.write(b);
        }

        static PlanS2C read(FriendlyByteBuf b) {
            return new PlanS2C(b.readBoolean() ? Plan.read(b) : null);
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    /** Liste de l'ecran PILLER + fenetre de pillage. */
    public record PillablesS2C(List<PillableVue> bases, FenetreVue fenetre) {
        void write(FriendlyByteBuf b) {
            b.writeVarInt(bases.size());
            for (PillableVue v : bases) v.write(b);
            fenetre.write(b);
        }

        static PillablesS2C read(FriendlyByteBuf b) {
            int n = Math.min(512, b.readVarInt());
            List<PillableVue> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(PillableVue.read(b));
            return new PillablesS2C(l, FenetreVue.read(b));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    /** Ouvre un ecran du mod (envoye apres l'etat qu'il affiche). */
    public record OuvrirS2C(Ecran ecran) {
        public enum Ecran {BASE, PILLER}

        void write(FriendlyByteBuf b) {
            b.writeByte(ecran.ordinal());
        }

        static OuvrirS2C read(FriendlyByteBuf b) {
            return new OuvrirS2C(enumLu(b, Ecran.values(), Ecran.BASE));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    /**
     * Notification (toast cote client) : genre, cle de traduction + arguments (96 caracteres chacun), son du mod
     * (chemin dans heralisz_bases, « » = aucun).
     */
    public record NoticeS2C(Genre genre, String cle, List<String> args, String son) {
        public enum Genre {
            /** Information neutre. */
            INFO,
            /** Erreur / action impossible (bandeau rouge). */
            ERREUR,
            /** Refus discret d'une action dans la base (casser, ouvrir...) : petit texte, pas de toast. */
            REFUS,
            BASE_CREEE,
            SALLE_DEBLOQUEE,
            /** « Votre base est attaquee » (membres de la base pillee). */
            ATTAQUEE,
            /** « Pillage termine ». */
            PILLAGE_TERMINE,
            /** Rappel « fin du pillage dans 2 min ». */
            PILLAGE_FIN_PROCHE
        }

        void write(FriendlyByteBuf b) {
            b.writeByte(genre.ordinal());
            b.writeUtf(coupe(cle, 96), 96);
            b.writeVarInt(Math.min(6, args.size()));
            for (int i = 0; i < Math.min(6, args.size()); i++) b.writeUtf(coupe(args.get(i), 96), 96);
            b.writeUtf(coupe(son, 32), 32);
        }

        static NoticeS2C read(FriendlyByteBuf b) {
            Genre g = enumLu(b, Genre.values(), Genre.INFO);
            String cle = b.readUtf(96);
            int n = Math.min(6, b.readVarInt());
            List<String> a = new ArrayList<>(n);
            for (int i = 0; i < n; i++) a.add(b.readUtf(96));
            return new NoticeS2C(g, cle, a, b.readUtf(32));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    /**
     * HUD dans la dimension des bases : mode (AUCUN hors base), tag et couleur du clan proprietaire, zone (id de salle,
     * « hall », « couloir », « sas_nord »... noms dans le plan), fin du pillage (epoch ms serveur, 0 = sans objet).
     * Envoye seulement quand quelque chose change.
     */
    public record HudS2C(Mode mode, String tag, int couleur, String zone, long finMs) {
        public enum Mode {AUCUN, BASE, PILLAGE}

        public static final HudS2C AUCUN = new HudS2C(Mode.AUCUN, "", 0, "", 0);

        void write(FriendlyByteBuf b) {
            b.writeByte(mode.ordinal());
            b.writeUtf(coupe(tag, 8), 8);
            b.writeInt(couleur);
            b.writeUtf(coupe(zone, 32), 32);
            b.writeLong(finMs);
        }

        static HudS2C read(FriendlyByteBuf b) {
            return new HudS2C(enumLu(b, Mode.values(), Mode.AUCUN), b.readUtf(8), b.readInt(), b.readUtf(32), b.readLong());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    /**
     * Teleportation differee (10 s immobile) : DEBUT (compte a rebours de {@code totalTicks}, le client l'anime seul),
     * ANNULEE (mouvement, degat, combat : {@code raison} = cle de traduction), FAITE.
     */
    public record TeleportS2C(Genre genre, Etat etat, int totalTicks, String raison) {
        public enum Genre {ENTREE, SORTIE, PILLAGE}

        public enum Etat {DEBUT, ANNULEE, FAITE}

        void write(FriendlyByteBuf b) {
            b.writeByte(genre.ordinal());
            b.writeByte(etat.ordinal());
            b.writeVarInt(totalTicks);
            b.writeUtf(coupe(raison, 96), 96);
        }

        static TeleportS2C read(FriendlyByteBuf b) {
            return new TeleportS2C(enumLu(b, Genre.values(), Genre.ENTREE), enumLu(b, Etat.values(), Etat.FAITE), b.readVarInt(),
                    b.readUtf(96));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    // =================================================================== client -> serveur
    /**
     * Action d'un ecran. {@code arg} : ENTRER « nord » / « sud », DEBLOQUER id de salle, PILLER tag, OUVRIR « » (BASE)
     * ou « piller » ; ignore sinon. Tout est verifie par {@link ServerHandlers#action}.
     */
    public record ActionC2S(Genre genre, String arg) {
        public enum Genre {
            /** Ouvre l'ecran BASE (ou PILLER si arg = « piller ») : le serveur repond etat (+ plan) puis OuvrirS2C. */
            OUVRIR,
            /** L'ecran est ferme (plus d'envois lents). */
            FERMER,
            /** Redemande l'etat de ma base. */
            ETAT,
            /** Redemande le plan du bunker. */
            PLAN,
            /** Redemande la liste des bases pillables. */
            PILLABLES,
            CREER,
            ENTRER,
            SORTIR,
            DEBLOQUER,
            PILLER,
            /** Annule ma teleportation en attente. */
            ANNULER
        }

        void write(FriendlyByteBuf b) {
            b.writeByte(genre.ordinal());
            b.writeUtf(coupe(arg, 32), 32);
        }

        static ActionC2S read(FriendlyByteBuf b) {
            return new ActionC2S(enumLu(b, Genre.values(), Genre.ETAT), b.readUtf(32));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) RECEPTION_SERVEUR.accept(new Recu(p, this));
        }
    }
}
