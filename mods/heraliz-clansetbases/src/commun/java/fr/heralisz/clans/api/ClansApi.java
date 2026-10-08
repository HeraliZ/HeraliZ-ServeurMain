package fr.heralisz.clans.api;

import fr.heralisz.clans.model.Grade;
import fr.heralisz.clans.model.Perm;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * API publique du mod clans pour les autres mods HeraliZ (Bases, Matchs). Statique, a appeler <b>sur le fil serveur</b>
 * uniquement (ticks, evenements, commandes, handlers reseau mis en file) ; hors serveur (client, avant le demarrage)
 * tout renvoie vide / faux / 0 sans planter.
 *
 * <p>Persistance : toute ecriture marque le clan a synchroniser (snapshot renvoye aux membres) et le store a sauvegarder
 * (autosave 60 s) ; les operations d'argent sauvegardent tout de suite clans.json (le cote debite est ecrit avant de
 * rendre la main). Les montants sont en jetons (HeraliZ Économie). Evenements postes sur {@code MinecraftForge.EVENT_BUS} :
 * {@link fr.heralisz.clans.api.events.ClanDissousEvent}, {@link fr.heralisz.clans.api.events.MembreQuitteEvent}.
 *
 * <p>Drapeau « en match » : {@link #EN_MATCH} dans {@code persistentData} d'un joueur (pose et retire par HeraliZ
 * Matchs) ; les stats et quetes de clan l'ignorent tant qu'il est present.
 */
public final class ClansApi {
    private ClansApi() {
    }

    /** Cle de persistentData posee par HeraliZ Matchs sur un joueur en match (valeur indifferente : seule la presence compte). */
    public static final String EN_MATCH = "heralisz:en_match";
    /** Nom affiche dans le journal de banque pour une operation sans joueur. */
    public static final String NOM_SYSTEME = "HeraliZ";
    /** Arguments au plus d'une ligne de journal (la cle comprise : limite reseau de Evenement). */
    static final int JOURNAL_ARGS_MAX = 7;


    // ------------------------------------------------------------------ partie serveur (les clans du serveur)
    /**
     * Les clans vivent dans la partie serveur du mod ({@code ClansApiServeur}, jars -serveur et -complet), branchee au
     * chargement par {@code clans.serveur.PartieServeur}. Sans elle (jar -client), l'API repond comme hors serveur dans
     * le mod d'origine : vide / faux / 0, sans planter. Noms et signatures publics inchanges (API lue par matchs, crac,
     * corps, ping, bases...).
     */
    interface Moteur {
        boolean disponible();

        Optional<UUID> clanDe(UUID joueur);

        Optional<ClanInfo> infos(UUID clanId);

        Optional<ClanInfo> parTag(String tag);

        List<ClanInfo> tous();

        Optional<Grade> grade(UUID joueur);

        boolean aPerm(UUID joueur, Perm perm);

        boolean aPerm(UUID joueur, UUID clanId, Perm perm);

        Perm.Etat etatPermJoueur(UUID clanId, UUID membre, Perm perm);

        boolean definirPermJoueur(@Nullable UUID acteur, UUID clanId, UUID membre, Perm perm, Perm.Etat etat);

        String nomJoueur(UUID joueur);

        long soldeBanque(UUID clanId);

        boolean debiterBanque(UUID clanId, long montant, String motif, @Nullable UUID auteur);

        long crediterBanque(UUID clanId, long montant, String motif, @Nullable UUID auteur);

        boolean ajouterClashWar(UUID clanId, boolean gagne);

        boolean journal(UUID clanId, String cle, String... args);
    }

    /** Branche par la partie serveur ({@code ClansApiServeur.installer()}) ; null dans le jar -client. */
    static volatile Moteur moteur;

    // ------------------------------------------------------------------ lecture
    /** Vrai si les donnees des clans sont chargees (serveur demarre). */
    public static boolean disponible() {
        Moteur m = moteur;
        return m == null ? false : m.disponible();
    }

    /** Clan du joueur. */
    public static Optional<UUID> clanDe(UUID joueur) {
        Moteur m = moteur;
        return m == null ? Optional.empty() : m.clanDe(joueur);
    }

    /** Photo d'un clan (vide s'il n'existe pas, ou plus). */
    public static Optional<ClanInfo> infos(UUID clanId) {
        Moteur m = moteur;
        return m == null ? Optional.empty() : m.infos(clanId);
    }

    /** Clan par tag (insensible a la casse). */
    public static Optional<ClanInfo> parTag(String tag) {
        Moteur m = moteur;
        return m == null ? Optional.empty() : m.parTag(tag);
    }

    /** Photos de tous les clans (ordre quelconque). */
    public static List<ClanInfo> tous() {
        Moteur m = moteur;
        return m == null ? List.of() : m.tous();
    }

    /** Grade du joueur dans son clan. */
    public static Optional<Grade> grade(UUID joueur) {
        Moteur m = moteur;
        return m == null ? Optional.empty() : m.grade(joueur);
    }

    /**
     * Vrai si le joueur a la permission dans SON clan (le chef les a toutes ; faux sans clan). Tient compte des
     * reglages individuels : un reglage AUTORISE / REFUSE du joueur l'emporte sur la permission de son grade.
     */
    public static boolean aPerm(UUID joueur, Perm perm) {
        Moteur m = moteur;
        return m == null ? false : m.aPerm(joueur, perm);
    }

    /**
     * Vrai si le joueur est membre du clan donne et y a la permission (ex. : agir dans la base de ce clan), reglages
     * individuels compris (voir {@link #aPerm(UUID, Perm)}).
     */
    public static boolean aPerm(UUID joueur, UUID clanId, Perm perm) {
        Moteur m = moteur;
        return m == null ? false : m.aPerm(joueur, clanId, perm);
    }

    // ------------------------------------------------------------------ permissions par joueur
    /**
     * Reglage individuel d'une permission pour un membre : {@link Perm.Etat#HERITE} (comme son grade, aussi pour un
     * clan ou un joueur inconnu, un non-membre et le chef), {@link Perm.Etat#AUTORISE} ou {@link Perm.Etat#REFUSE}.
     * Pour savoir si le joueur PEUT, utiliser {@link #aPerm(UUID, UUID, Perm)}.
     */
    public static Perm.Etat etatPermJoueur(UUID clanId, UUID membre, Perm perm) {
        Moteur m = moteur;
        return m == null ? Perm.Etat.HERITE : m.etatPermJoueur(clanId, membre, perm);
    }

    /**
     * Regle une permission pour un membre (HERITE = comme son grade). {@code acteur} = le joueur qui fait le reglage,
     * soumis aux memes regles que l'editeur du clan : il a GERER_PERMISSIONS (defaut chef et haut grade), son grade
     * est strictement superieur a celui du membre, et il n'accorde pas une permission qu'il n'a pas lui-meme ;
     * {@code acteur} null = systeme (commande admin, mod appelant) : seules restent les regles « membre du clan » et
     * « le chef n'est pas reglable ». Journal du clan, snapshot aux membres, clans.json sauvegarde a l'autosave. Vrai
     * si le reglage est permis (applique, ou deja dans cet etat) ; faux sinon (rien n'est fait).
     */
    public static boolean definirPermJoueur(@Nullable UUID acteur, UUID clanId, UUID membre, Perm perm, Perm.Etat etat) {
        Moteur m = moteur;
        return m == null ? false : m.definirPermJoueur(acteur, clanId, membre, perm, etat);
    }

    /** Nom affiche d'un joueur : en ligne, sinon dernier pseudo connu par les clans, sinon debut de l'UUID. */
    public static String nomJoueur(UUID joueur) {
        Moteur m = moteur;
        return m == null ? joueur.toString().substring(0, 8) : m.nomJoueur(joueur);
    }

    /** Vrai si l'entite est un joueur marque « en match » (null : faux). */
    public static boolean enMatch(@Nullable Entity e) {
        return e != null && e.getPersistentData().contains(EN_MATCH);
    }

    // ------------------------------------------------------------------ banque
    /** Solde de la banque du clan (0 s'il n'existe pas). */
    public static long soldeBanque(UUID clanId) {
        Moteur m = moteur;
        return m == null ? 0 : m.soldeBanque(clanId);
    }

    /** {@link #debiterBanque(UUID, long, String, UUID)} sans auteur (journal : « HeraliZ »). */
    public static boolean debiterBanque(UUID clanId, long montant, String motif) {
        return debiterBanque(clanId, montant, motif, null);
    }

    /**
     * Retire {@code montant} (> 0) de la banque du clan si elle le contient : ligne de journal de banque (motif = cle
     * courte, affichee via « banque.motif.&lt;motif&gt; » si la traduction existe, sinon telle quelle ; auteur = joueur qui a
     * declenche l'achat, ou null), snapshot aux membres, clans.json ecrit tout de suite. Faux (rien n'est fait) si le
     * clan n'existe pas, si le montant est invalide ou si la banque ne suffit pas. Les jetons debites disparaissent
     * (achat) : a l'appelant de les crediter ailleurs s'il le faut, APRES ce debit.
     */
    public static boolean debiterBanque(UUID clanId, long montant, String motif, @Nullable UUID auteur) {
        Moteur m = moteur;
        return m == null ? false : m.debiterBanque(clanId, montant, motif, auteur);
    }

    /** {@link #crediterBanque(UUID, long, String, UUID)} sans auteur. */
    public static long crediterBanque(UUID clanId, long montant, String motif) {
        return crediterBanque(clanId, montant, motif, null);
    }

    /**
     * Ajoute {@code montant} (> 0) a la banque du clan, tronque au plafond {@code ClanService#BANQUE_MAX} (la perte est
     * journalisee dans le log serveur) ; journal de banque, snapshot, clans.json ecrit tout de suite. Renvoie les jetons
     * reellement credites (0 si le clan n'existe pas ou si le montant est invalide). Si les jetons viennent d'un
     * compte joueur, le debiter (et le sauvegarder) AVANT d'appeler cette methode.
     */
    public static long crediterBanque(UUID clanId, long montant, String motif, @Nullable UUID auteur) {
        Moteur m = moteur;
        return m == null ? 0 : m.crediterBanque(clanId, montant, motif, auteur);
    }

    // ------------------------------------------------------------------ stats, journal
    /**
     * Compte un ClashWar dispute par le clan : clashwarJoues +1, et clashwarGagnes +1 si {@code gagne}. A appeler une
     * seule fois par clan participant et par tournoi (le vainqueur avec {@code gagne} vrai). Faux si le clan n'existe pas.
     */
    public static boolean ajouterClashWar(UUID clanId, boolean gagne) {
        Moteur m = moteur;
        return m == null ? false : m.ajouterClashWar(clanId, gagne);
    }

    /**
     * Ajoute une ligne au journal d'activite du clan (onglet Apercu, 20 dernieres). {@code cle} = cle de traduction
     * fournie par le mod appelant dans ses propres fichiers lang (ex. « heralisz_bases.journal.salle_debloquee ») ;
     * {@code args} = chaines inserees (%s), 6 au plus, 96 caracteres chacune. Faux si le clan n'existe pas.
     */
    public static boolean journal(UUID clanId, String cle, String... args) {
        Moteur m = moteur;
        return m == null ? false : m.journal(clanId, cle, args);
    }
}
