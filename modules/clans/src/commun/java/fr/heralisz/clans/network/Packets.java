package fr.heralisz.clans.network;

import fr.heraliz.clansetbases.pont.Crochet;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.ClanSnapshot;
import fr.heralisz.clans.model.EtatZone;
import fr.heralisz.clans.model.Grade;
import fr.heralisz.clans.model.InvitationVue;
import fr.heralisz.clans.model.LigneBareme;
import fr.heralisz.clans.model.SaisonLigne;
import fr.heralisz.clans.model.TagVue;
import fr.heralisz.clans.model.Texte;
import fr.heralisz.clans.model.ZoneVue;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Messages reseau du mod (records avec write/read/handle). S2C = serveur vers client, C2S = client vers serveur.
 * Les handlers S2C delèguent a {@code client.ClientHandlers} (classe client seulement), les C2S a
 * {@code server.ServerHandlers} qui verifie tout (le client n'est jamais cru).
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

    @Nullable
    private static ServerPlayer sender(Supplier<NetworkEvent.Context> ctx) {
        return ctx.get().getSender();
    }

    // =================================================================== serveur -> client
    /** Mon clan complet (null = je n'ai pas de clan). */
    public record ClanSnapshotS2C(@Nullable ClanSnapshot snapshot) {
        void write(FriendlyByteBuf b) {
            b.writeBoolean(snapshot != null);
            if (snapshot != null) snapshot.write(b);
        }

        static ClanSnapshotS2C read(FriendlyByteBuf b) {
            return new ClanSnapshotS2C(b.readBoolean() ? ClanSnapshot.read(b) : null);
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    /** Invitations en attente pour moi. */
    public record InvitesS2C(List<InvitationVue> invitations) {
        void write(FriendlyByteBuf b) {
            b.writeVarInt(invitations.size());
            for (InvitationVue i : invitations) i.write(b);
        }

        static InvitesS2C read(FriendlyByteBuf b) {
            int n = Math.min(32, b.readVarInt());
            List<InvitationVue> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(InvitationVue.read(b));
            return new InvitesS2C(l);
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    /** Tags de tous les joueurs en ligne (remplace la table ; un joueur absent n'a pas de clan). */
    public record TagsS2C(Map<UUID, TagVue> tags) {
        void write(FriendlyByteBuf b) {
            b.writeVarInt(tags.size());
            for (Map.Entry<UUID, TagVue> e : tags.entrySet()) {
                b.writeUUID(e.getKey());
                e.getValue().write(b);
            }
        }

        static TagsS2C read(FriendlyByteBuf b) {
            int n = Math.min(1024, b.readVarInt());
            Map<UUID, TagVue> m = new HashMap<>(n * 2);
            for (int i = 0; i < n; i++) m.put(b.readUUID(), TagVue.read(b));
            return new TagsS2C(m);
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    /**
     * Onglet Saison (25/10, maquette validee) : saison en cours ou non, son numero et sa fin, le coeur du clan du joueur,
     * ses places {pve, pvp, events, general} (0 = pas classe) et ses points {pve, pvp, events}, le top 20 des clans et
     * le bareme reel (« Gagner des points »). Envoye a la demande (SimpleC2S CLASSEMENT).
     */
    public record SaisonS2C(boolean active, int numero, long finMs, boolean coeur, int[] mesRangs, long[] mesPoints,
                            List<SaisonLigne> top, List<LigneBareme> bareme) {
        void write(FriendlyByteBuf b) {
            b.writeBoolean(active);
            b.writeVarInt(numero);
            b.writeLong(finMs);
            b.writeBoolean(coeur);
            for (int i = 0; i < 4; i++) b.writeVarInt(i < mesRangs.length ? mesRangs[i] : 0);
            for (int i = 0; i < 3; i++) b.writeVarLong(i < mesPoints.length ? mesPoints[i] : 0);
            b.writeVarInt(top.size());
            for (SaisonLigne l : top) l.write(b);
            b.writeVarInt(bareme.size());
            for (LigneBareme l : bareme) l.write(b);
        }

        static SaisonS2C read(FriendlyByteBuf b) {
            boolean active = b.readBoolean();
            int numero = b.readVarInt();
            long fin = b.readLong();
            boolean coeur = b.readBoolean();
            int[] rangs = new int[4];
            for (int i = 0; i < 4; i++) rangs[i] = b.readVarInt();
            long[] points = new long[3];
            for (int i = 0; i < 3; i++) points[i] = b.readVarLong();
            int n = Math.min(50, b.readVarInt());
            List<SaisonLigne> top = new ArrayList<>(n);
            for (int i = 0; i < n; i++) top.add(SaisonLigne.read(b));
            int m = Math.min(64, b.readVarInt());
            List<LigneBareme> bareme = new ArrayList<>(m);
            for (int i = 0; i < m; i++) bareme.add(LigneBareme.read(b));
            return new SaisonS2C(active, numero, fin, coeur, rangs, points, top, bareme);
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    /**
     * Notification (toast HUD + eventuellement son). genre : ce qui s'est passe ; args : textes a inserer (tag, nom,
     * zone, quete, niveau...) ; son : chemin du son dans notre namespace ("" = aucun) ; banniere : pour le toast
     * d'invitation/capture (null sinon).
     */
    public record NoticeS2C(Genre genre, List<String> args, String son, @Nullable Banniere banniere) {
        public enum Genre {
            INVITATION, QUETE_FINIE, NIVEAU, CAPTURE, ZONE_PERDUE, RAVITAILLEMENT, INFO, ERREUR;

            /** Cle de traduction du texte du toast (notice.<genre>). */
            public String libelle() {
                return "notice." + name().toLowerCase();
            }

            static Genre parOrdinal(int i) {
                Genre[] v = values();
                return v[Math.max(0, Math.min(v.length - 1, i))];
            }
        }

        void write(FriendlyByteBuf b) {
            b.writeVarInt(genre.ordinal());
            b.writeVarInt(args.size());
            for (String s : args) Texte.utf(b, s, 96);
            Texte.utf(b, son, 64);
            b.writeBoolean(banniere != null);
            if (banniere != null) banniere.write(b);
        }

        static NoticeS2C read(FriendlyByteBuf b) {
            Genre g = Genre.parOrdinal(b.readVarInt());
            int n = Math.min(8, b.readVarInt());
            List<String> args = new ArrayList<>(n);
            for (int i = 0; i < n; i++) args.add(b.readUtf(96));
            String son = b.readUtf(64);
            Banniere ban = b.readBoolean() ? Banniere.read(b) : null;
            return new NoticeS2C(g, args, son, ban);
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    /** Ouvre un ecran par nom : "clan" (principal / sans clan), "creation", "drapeau" (panneau du drapeau). */
    public record OuvrirS2C(String ecran) {
        void write(FriendlyByteBuf b) {
            Texte.utf(b, ecran, 32);
        }

        static OuvrirS2C read(FriendlyByteBuf b) {
            return new OuvrirS2C(b.readUtf(32));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    /**
     * Barre de capture du HUD pour le joueur present dans une zone. zone vide = le joueur est sorti (barre cachee).
     * progression 0..1 ; couleur = couleur du proprietaire (ou accent neutre) ; capteurTag = clan en train de capturer.
     */
    /**
     * @param drapeau -2 : zone sans drapeau (pas de capture) ; -1 : le joueur est dans le cercle de capture du drapeau ;
     *                sinon distance au drapeau en blocs (arrondie a 5)
     */
    public record TerritoireHudS2C(String zone, String proprioTag, int couleur, float progression, EtatZone etat, String capteurTag,
                                   long protegeeMs, int drapeau) {
        void write(FriendlyByteBuf b) {
            Texte.utf(b, zone, 48);
            Texte.utf(b, proprioTag, 8);
            b.writeInt(couleur);
            b.writeFloat(progression);
            b.writeVarInt(etat.ordinal());
            Texte.utf(b, capteurTag, 8);
            b.writeVarLong(protegeeMs);
            b.writeInt(drapeau);
        }

        static TerritoireHudS2C read(FriendlyByteBuf b) {
            return new TerritoireHudS2C(b.readUtf(48), b.readUtf(8), b.readInt(), b.readFloat(), EtatZone.parOrdinal(b.readVarInt()),
                    b.readUtf(8), b.readVarLong(), b.readInt());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }

        public boolean visible() {
            return !zone.isEmpty();
        }
    }

    /** Panneau du drapeau (clic droit sur le socle) : ouvre l'ecran avec cette vue. */
    public record PanneauDrapeauS2C(ZoneVue zone) {
        void write(FriendlyByteBuf b) {
            zone.write(b);
        }

        static PanneauDrapeauS2C read(FriendlyByteBuf b) {
            return new PanneauDrapeauS2C(ZoneVue.read(b));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    // =================================================================== client -> serveur
    /** Fonder un clan (banniere v2 ; calquesEnvoyes = nombre de calques annonce, refuse au-dela de 12). */
    public record CreerClanC2S(String tag, String nom, String description, Banniere banniere, int calquesEnvoyes) {
        /** Cote client : le nombre de calques annonce est celui de la banniere. */
        public CreerClanC2S(String tag, String nom, String description, Banniere banniere) {
            this(tag, nom, description, banniere, banniere.nombreCalques());
        }

        void write(FriendlyByteBuf b) {
            Texte.utf(b, tag, 16);
            Texte.utf(b, nom, 48);
            Texte.utf(b, description, 200);
            banniere.write(b); // toujours en dernier : la lecture ignore les calques au-dela de 12
        }

        static CreerClanC2S read(FriendlyByteBuf b) {
            String tag = b.readUtf(16), nom = b.readUtf(48), desc = b.readUtf(200);
            Banniere.Lecture l = Banniere.lire(b);
            return new CreerClanC2S(tag, nom, desc, l.banniere(), l.calquesEnvoyes());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = sender(ctx);
            if (p != null) RECEPTION_SERVEUR.accept(new Recu(p, this));
        }
    }

    /** Inviter un joueur en ligne par son nom. */
    public record InviterC2S(String nom) {
        void write(FriendlyByteBuf b) {
            Texte.utf(b, nom, 48);
        }

        static InviterC2S read(FriendlyByteBuf b) {
            return new InviterC2S(b.readUtf(48));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = sender(ctx);
            if (p != null) RECEPTION_SERVEUR.accept(new Recu(p, this));
        }
    }

    /** Accepter ou refuser une invitation. */
    public record ReponseInvitationC2S(UUID clanId, boolean accepte) {
        void write(FriendlyByteBuf b) {
            b.writeUUID(clanId);
            b.writeBoolean(accepte);
        }

        static ReponseInvitationC2S read(FriendlyByteBuf b) {
            return new ReponseInvitationC2S(b.readUUID(), b.readBoolean());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = sender(ctx);
            if (p != null) RECEPTION_SERVEUR.accept(new Recu(p, this));
        }
    }

    /** Action sur un membre. */
    public record ActionMembreC2S(Genre genre, UUID cible) {
        public enum Genre {
            EXCLURE, PROMOUVOIR, RETROGRADER, TRANSFERER;

            static Genre parOrdinal(int i) {
                Genre[] v = values();
                return v[Math.max(0, Math.min(v.length - 1, i))];
            }
        }

        void write(FriendlyByteBuf b) {
            b.writeVarInt(genre.ordinal());
            b.writeUUID(cible);
        }

        static ActionMembreC2S read(FriendlyByteBuf b) {
            return new ActionMembreC2S(Genre.parOrdinal(b.readVarInt()), b.readUUID());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = sender(ctx);
            if (p != null) RECEPTION_SERVEUR.accept(new Recu(p, this));
        }
    }

    /** Depot (depot = true) ou retrait d'un montant > 0. */
    public record BanqueC2S(boolean depot, long montant) {
        void write(FriendlyByteBuf b) {
            b.writeBoolean(depot);
            b.writeLong(montant);
        }

        static BanqueC2S read(FriendlyByteBuf b) {
            return new BanqueC2S(b.readBoolean(), b.readLong());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = sender(ctx);
            if (p != null) RECEPTION_SERVEUR.accept(new Recu(p, this));
        }
    }

    /** Modification d'un champ texte (Parametres). */
    public record ParametreC2S(Champ champ, String valeur) {
        public enum Champ {
            NOM, TAG, DESCRIPTION;

            static Champ parOrdinal(int i) {
                Champ[] v = values();
                return v[Math.max(0, Math.min(v.length - 1, i))];
            }
        }

        void write(FriendlyByteBuf b) {
            b.writeVarInt(champ.ordinal());
            Texte.utf(b, valeur, 200);
        }

        static ParametreC2S read(FriendlyByteBuf b) {
            return new ParametreC2S(Champ.parOrdinal(b.readVarInt()), b.readUtf(200));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = sender(ctx);
            if (p != null) RECEPTION_SERVEUR.accept(new Recu(p, this));
        }
    }

    /** Nouvelle banniere (atelier ; calquesEnvoyes = nombre de calques annonce, refuse au-dela de 12). */
    public record BanniereC2S(Banniere banniere, int calquesEnvoyes) {
        /** Cote client : le nombre de calques annonce est celui de la banniere. */
        public BanniereC2S(Banniere banniere) {
            this(banniere, banniere.nombreCalques());
        }

        void write(FriendlyByteBuf b) {
            banniere.write(b); // seul champ : la lecture ignore les calques au-dela de 12
        }

        static BanniereC2S read(FriendlyByteBuf b) {
            Banniere.Lecture l = Banniere.lire(b);
            return new BanniereC2S(l.banniere(), l.calquesEnvoyes());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = sender(ctx);
            if (p != null) RECEPTION_SERVEUR.accept(new Recu(p, this));
        }
    }

    /** Permissions d'un grade (masque de bits, voir Perm.masque) — chef seulement. */
    public record PermsC2S(Grade grade, int masque) {
        void write(FriendlyByteBuf b) {
            b.writeVarInt(grade.ordinal());
            b.writeVarInt(masque);
        }

        static PermsC2S read(FriendlyByteBuf b) {
            return new PermsC2S(Grade.values()[Math.min(3, b.readVarInt())], b.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = sender(ctx);
            if (p != null) RECEPTION_SERVEUR.accept(new Recu(p, this));
        }
    }

    /**
     * Actions simples. OUVRIR (arg = ecran demande ou "") : redemande le snapshot/les invitations et signale que
     * l'ecran du clan est ouvert (les mises a jour lentes ne vont qu'aux ecrans ouverts, voir {@code server.Sync}) ;
     * FERMER : l'ecran vient d'etre ferme (sinon le serveur le considere ferme 30 s apres le dernier OUVRIR) ; QUITTER ;
     * DISSOUDRE ; CLASSEMENT : demande l'onglet Saison (SaisonS2C) ; CHAT_BASCULE : bascule le mode /cc.
     */
    public record SimpleC2S(Genre genre, String arg) {
        public enum Genre {
            OUVRIR, QUITTER, DISSOUDRE, CLASSEMENT, CHAT_BASCULE, FERMER,
            /** Ouvrir le coffre du clan (3.5.0, ajouté en dernier). */
            COFFRE;

            static Genre parOrdinal(int i) {
                Genre[] v = values();
                return v[Math.max(0, Math.min(v.length - 1, i))];
            }
        }

        public SimpleC2S(Genre genre) {
            this(genre, "");
        }

        void write(FriendlyByteBuf b) {
            b.writeVarInt(genre.ordinal());
            Texte.utf(b, arg, 64);
        }

        static SimpleC2S read(FriendlyByteBuf b) {
            return new SimpleC2S(Genre.parOrdinal(b.readVarInt()), b.readUtf(64));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = sender(ctx);
            if (p != null) RECEPTION_SERVEUR.accept(new Recu(p, this));
        }
    }

    /** Acheter le palier suivant d'une branche de l'arbre du clan (chef ; verifie par le serveur). */
    public record AmeliorerC2S(String branche) {
        void write(FriendlyByteBuf b) {
            Texte.utf(b, branche, 24);
        }

        static AmeliorerC2S read(FriendlyByteBuf b) {
            return new AmeliorerC2S(b.readUtf(24));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = sender(ctx);
            if (p != null) RECEPTION_SERVEUR.accept(new Recu(p, this));
        }
    }

    /**
     * Arbre du clan et saison (envoye avec chaque snapshot) : paliers par branche (ordre de {@code Arbre.Branche}),
     * points libres, chef ou non ; saison en cours, fin (ms), coeur pose, points PVE / PVP / events, places {pve, pvp,
     * events, general} (0 = pas classe).
     */
    public record ArbreSaisonS2C(int[] paliers, int pointsLibres, boolean chef, boolean saison, long fin, boolean coeur,
                                 long pve, long pvp, long events, int[] rangs) {
        void write(FriendlyByteBuf b) {
            b.writeVarIntArray(paliers);
            b.writeVarInt(pointsLibres);
            b.writeBoolean(chef);
            b.writeBoolean(saison);
            b.writeLong(fin);
            b.writeBoolean(coeur);
            b.writeVarLong(pve);
            b.writeVarLong(pvp);
            b.writeVarLong(events);
            b.writeVarIntArray(rangs);
        }

        static ArbreSaisonS2C read(FriendlyByteBuf b) {
            return new ArbreSaisonS2C(b.readVarIntArray(16), b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readLong(), b.readBoolean(),
                    b.readVarLong(), b.readVarLong(), b.readVarLong(), b.readVarIntArray(8));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    /** Lancer le signal de ravitaillement depuis le panneau du drapeau. */
    public record RavitaillementC2S(String zoneId) {
        void write(FriendlyByteBuf b) {
            Texte.utf(b, zoneId, 32);
        }

        static RavitaillementC2S read(FriendlyByteBuf b) {
            return new RavitaillementC2S(b.readUtf(32));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = sender(ctx);
            if (p != null) RECEPTION_SERVEUR.accept(new Recu(p, this));
        }
    }

    /**
     * Reglages individuels de permissions d'un membre (editeur PAR JOUEUR), groupes : pour chaque permission du
     * {@code masque} (bit = ordinal), l'etat voulu est AUTORISE si elle est dans {@code autorise}, REFUSE si elle est
     * dans {@code refuse}, sinon HERITE. Le serveur verifie chaque reglage (voir ClanService.refusPermJoueur).
     */
    public record PermsJoueurC2S(UUID membre, int masque, int autorise, int refuse) {
        void write(FriendlyByteBuf b) {
            b.writeUUID(membre);
            b.writeVarInt(masque);
            b.writeVarInt(autorise);
            b.writeVarInt(refuse);
        }

        static PermsJoueurC2S read(FriendlyByteBuf b) {
            return new PermsJoueurC2S(b.readUUID(), b.readVarInt(), b.readVarInt(), b.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = sender(ctx);
            if (p != null) RECEPTION_SERVEUR.accept(new Recu(p, this));
        }
    }

    // =================================================================== carte du monde (CarteApi)
    /** La carte du monde est ouverte : le joueur demande l'etat des territoires (au plus 1 par seconde, sans reponse sinon). */
    public record CarteDemandeC2S() {
        void write(FriendlyByteBuf b) {
        }

        static CarteDemandeC2S read(FriendlyByteBuf b) {
            return new CarteDemandeC2S();
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = sender(ctx);
            if (p != null) RECEPTION_SERVEUR.accept(new Recu(p, this));
        }
    }

    /**
     * Reponse a {@link CarteDemandeC2S} : les zones (proprietaire et capteur ecrits une seule fois dans une table des
     * clans, banniere comprise), les membres de son clan dans le meme monde et son clan.
     */
    public record CarteS2C(List<fr.heralisz.clans.api.CarteApi.Zone> zones, List<fr.heralisz.clans.api.CarteApi.Allie> allies,
                           @Nullable fr.heralisz.clans.api.CarteApi.MonClan monClan) {
        static final int MAX_ZONES = 256, MAX_CLANS = 128, MAX_ALLIES = 64;

        private record ClanVu(String tag, String nom, int couleur, @Nullable Banniere banniere) {
        }

        void write(FriendlyByteBuf b) {
            // table des clans cites (proprietaires et capteurs)
            List<ClanVu> clans = new ArrayList<>();
            Map<String, Integer> index = new HashMap<>();
            List<fr.heralisz.clans.api.CarteApi.Zone> zs = zones.size() > MAX_ZONES ? zones.subList(0, MAX_ZONES) : zones;
            for (var z : zs) {
                if (!z.proprioTag().isEmpty() && !index.containsKey(z.proprioTag()) && clans.size() < MAX_CLANS) {
                    index.put(z.proprioTag(), clans.size());
                    clans.add(new ClanVu(z.proprioTag(), z.proprioNom(), z.proprioCouleur(), z.proprioBanniere()));
                }
                if (!z.capteurTag().isEmpty() && !index.containsKey(z.capteurTag()) && clans.size() < MAX_CLANS) {
                    index.put(z.capteurTag(), clans.size());
                    clans.add(new ClanVu(z.capteurTag(), z.capteurNom(), z.capteurCouleur(), z.capteurBanniere()));
                }
            }
            b.writeVarInt(clans.size());
            for (ClanVu c : clans) {
                Texte.utf(b, c.tag(), 8);
                Texte.utf(b, c.nom(), 32);
                b.writeInt(c.couleur());
                b.writeBoolean(c.banniere() != null);
                if (c.banniere() != null) c.banniere().write(b);
            }
            b.writeVarInt(zs.size());
            for (var z : zs) {
                Texte.utf(b, z.id(), 32);
                Texte.utf(b, z.nom(), 48);
                b.writeInt(z.x());
                b.writeInt(z.z());
                b.writeVarInt(Math.max(0, z.rayon()));
                b.writeBoolean(z.drapeau());
                if (z.drapeau()) {
                    b.writeInt(z.drapeauX());
                    b.writeInt(z.drapeauZ());
                }
                b.writeVarInt(index.getOrDefault(z.proprioTag(), -1) + 1);
                b.writeVarInt(z.etat().ordinal());
                b.writeVarInt(index.getOrDefault(z.capteurTag(), -1) + 1);
                b.writeFloat(z.progression());
                b.writeVarLong(Math.max(0, z.tenueDepuisMs()));
                b.writeVarLong(Math.max(0, z.protegeeMs()));
                b.writeVarInt(Math.max(0, z.valeur()));
                b.writeVarInt(Math.max(0, z.bonusLoot()));
                b.writeBoolean(z.monClan());
                b.writeVarLong(Math.max(-1, z.ravitaillementMs()) + 1);
                b.writeBoolean(z.peutRavitailler());
            }
            List<fr.heralisz.clans.api.CarteApi.Allie> as = allies.size() > MAX_ALLIES ? allies.subList(0, MAX_ALLIES) : allies;
            b.writeVarInt(as.size());
            for (var a : as) {
                Texte.utf(b, a.nom(), 16);
                b.writeDouble(a.x());
                b.writeDouble(a.z());
            }
            b.writeBoolean(monClan != null);
            if (monClan != null) {
                Texte.utf(b, monClan.tag(), 8);
                Texte.utf(b, monClan.nom(), 32);
                b.writeInt(monClan.couleur());
                b.writeBoolean(monClan.banniere() != null);
                if (monClan.banniere() != null) monClan.banniere().write(b);
                b.writeVarInt(Math.max(0, monClan.enLigne()));
                b.writeVarInt(Math.max(0, monClan.territoires()));
            }
        }

        static CarteS2C read(FriendlyByteBuf b) {
            int nc = Math.min(MAX_CLANS, b.readVarInt());
            List<ClanVu> clans = new ArrayList<>(nc);
            for (int i = 0; i < nc; i++) {
                String tag = b.readUtf(8), nom = b.readUtf(32);
                int couleur = b.readInt();
                Banniere ban = b.readBoolean() ? Banniere.read(b) : null;
                clans.add(new ClanVu(tag, nom, couleur, ban));
            }
            int nz = Math.min(MAX_ZONES, b.readVarInt());
            List<fr.heralisz.clans.api.CarteApi.Zone> zs = new ArrayList<>(nz);
            for (int i = 0; i < nz; i++) {
                String id = b.readUtf(32), nom = b.readUtf(48);
                int x = b.readInt(), z = b.readInt(), rayon = b.readVarInt();
                boolean drapeau = b.readBoolean();
                int dx = drapeau ? b.readInt() : 0, dz = drapeau ? b.readInt() : 0;
                ClanVu o = clan(clans, b.readVarInt());
                EtatZone etat = EtatZone.parOrdinal(b.readVarInt());
                ClanVu c = clan(clans, b.readVarInt());
                float prog = Math.max(0, Math.min(1, b.readFloat()));
                long tenue = b.readVarLong(), protegee = b.readVarLong();
                int valeur = b.readVarInt(), bonus = b.readVarInt();
                boolean mien = b.readBoolean();
                long ravit = b.readVarLong() - 1;
                boolean peut = b.readBoolean();
                zs.add(new fr.heralisz.clans.api.CarteApi.Zone(id, nom, x, z, rayon, drapeau, dx, dz,
                        o == null ? "" : o.tag(), o == null ? "" : o.nom(), o == null ? 0 : o.couleur(), o == null ? null : o.banniere(),
                        etat, c == null ? "" : c.tag(), c == null ? "" : c.nom(), c == null ? 0 : c.couleur(), c == null ? null : c.banniere(),
                        prog, tenue, protegee, valeur, bonus, mien, ravit, peut));
            }
            int na = Math.min(MAX_ALLIES, b.readVarInt());
            List<fr.heralisz.clans.api.CarteApi.Allie> as = new ArrayList<>(na);
            for (int i = 0; i < na; i++) as.add(new fr.heralisz.clans.api.CarteApi.Allie(b.readUtf(16), b.readDouble(), b.readDouble()));
            fr.heralisz.clans.api.CarteApi.MonClan mc = null;
            if (b.readBoolean()) {
                String tag = b.readUtf(8), nom = b.readUtf(32);
                int couleur = b.readInt();
                Banniere ban = b.readBoolean() ? Banniere.read(b) : null;
                mc = new fr.heralisz.clans.api.CarteApi.MonClan(tag, nom, couleur, ban, b.readVarInt(), b.readVarInt());
            }
            return new CarteS2C(zs, as, mc);
        }

        @Nullable
        private static ClanVu clan(List<ClanVu> clans, int i) {
            return i <= 0 || i > clans.size() ? null : clans.get(i - 1);
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }
}
