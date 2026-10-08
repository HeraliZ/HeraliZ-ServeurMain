package fr.heralisz.economie.api;

import fr.heralisz.economie.EconomieConfig;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * API publique de l'economie HeraliZ pour les autres mods. Statique, <b>fil serveur uniquement</b> (toute methode qui
 * touche aux comptes leve IllegalStateException hors du fil serveur ou avant le demarrage du serveur ; verifier
 * {@link #disponible()} si besoin). Montants en entiers (unite = 1 héral). Motifs = chaines libres prefixees
 * (« clan:banque », « quete:zombies », « admin », « paiement », « ravitaillement »…), coupees a 64 caracteres.
 * Apres chaque changement : journal du compte, {@link SoldeChangeEvent} sur le bus Forge, paquet SoldeS2C au joueur
 * s'il est en ligne, sauvegarde (groupee sous 1,5 s si le montant atteint {@code sauvegarde_immediate}, sinon
 * autosave 60 s). Les ecritures se font sur un fil IO du mod ; {@link #sauvegarderMaintenant()} attend l'ecriture et
 * {@link #sauvegarderBientot()} la demande sans attendre.
 */
public final class EconomieApi {
    private EconomieApi() {
    }

    /** Separateur de milliers : espace fine insecable (U+202F). */
    public static final char SEPARATEUR_MILLIERS = ' ';
    /** Espace insecable entre le nombre et l'unite (U+00A0). */
    public static final char ESPACE_UNITE = ' ';
    /** Glyphe « héral » (U+E000, piece de laiton frappee d'un H) des polices ui*.json du mod, pour nos ecrans (dans le chat : le mot). */
    public static final String GLYPHE_HERAL = "";
    /** Ancien nom du glyphe (garde pour les mods compiles contre la 0.1.0). */
    public static final String GLYPHE_JETON = GLYPHE_HERAL;

    // ------------------------------------------------------------------ partie serveur (les comptes)
    /**
     * Les comptes vivent dans la partie serveur du mod ({@code EconomieApiServeur}, jars -serveur et -complet), branchee
     * au chargement par {@code economie.serveur.PartieServeur}. Sans elle (jar -client), l'API repond comme hors serveur :
     * {@link #disponible()} faux, les methodes des comptes levent IllegalStateException (« serveur non demarre »).
     * Les noms et signatures publics ci-dessous ne changent pas (API lue par d'autres mods : casino, matchs...).
     */
    interface Moteur {
        boolean disponible();

        long solde(UUID joueur);

        @Nullable
        Compte compte(UUID joueur);

        List<Transaction> journal(UUID joueur, int n);

        List<Compte> classement(int n);

        boolean retirer(UUID joueur, long montant, String motif);

        void deposer(UUID joueur, long montant, String motif);

        boolean transferer(UUID de, UUID vers, long montant, String motif);

        boolean definir(UUID joueur, long solde, String motif);

        boolean sauvegarderMaintenant();

        void sauvegarderBientot();
    }

    /** Branche par la partie serveur ({@code EconomieApiServeur.installer()}) ; null dans le jar -client. */
    static volatile Moteur moteur;

    private static Moteur moteur() {
        Moteur m = moteur;
        if (m == null) throw new IllegalStateException("HeraliZ Économie : serveur non démarré (économie indisponible)");
        return m;
    }

    // ------------------------------------------------------------------ etat
    /** Vrai si le serveur tourne et que l'on est sur son fil : les autres methodes peuvent etre appelees. */
    public static boolean disponible() {
        Moteur m = moteur;
        return m != null && m.disponible();
    }

    /** Plafond d'un compte (config max_solde). */
    public static long maxSolde() {
        return EconomieConfig.MAX_SOLDE.get();
    }

    // ------------------------------------------------------------------ lecture
    /** Solde du joueur (0 s'il n'a pas de compte). */
    public static long solde(UUID joueur) {
        return moteur().solde(joueur);
    }

    /** Le compte (null si le joueur n'en a jamais eu). Lecture seule pour les autres mods. */
    @Nullable
    public static Compte compte(UUID joueur) {
        return moteur().compte(joueur);
    }

    /** Les n dernieres transactions du joueur, la plus recente en premier (liste vide sans compte). */
    public static List<Transaction> journal(UUID joueur, int n) {
        return moteur().journal(joueur, n);
    }

    /** Les n comptes les plus riches (solde decroissant). */
    public static List<Compte> classement(int n) {
        return moteur().classement(n);
    }

    // ------------------------------------------------------------------ ecriture
    /** Retire le montant ; faux (et rien ne change) si montant <= 0 ou solde insuffisant. */
    public static boolean retirer(UUID joueur, long montant, String motif) {
        return moteur().retirer(joueur, montant, motif);
    }

    /** Crédite le montant (rien si <= 0) ; cree le compte au besoin ; tronque au plafond max_solde. */
    public static void deposer(UUID joueur, long montant, String motif) {
        moteur().deposer(joueur, montant, motif);
    }

    /**
     * Transfert atomique de « de » vers « vers » : faux (rien ne change) si montant <= 0, memes comptes, solde
     * insuffisant ou plafond du destinataire depasse. Les deux comptes sont mutes avant tout effet de bord
     * (evenement, paquet, sauvegarde) : une erreur en aval ne peut pas laisser un debit sans credit.
     */
    public static boolean transferer(UUID de, UUID vers, long montant, String motif) {
        return moteur().transferer(de, vers, montant, motif);
    }

    /** Fixe le solde (0 <= solde <= max_solde) ; faux si hors bornes. Journalise la difference. */
    public static boolean definir(UUID joueur, long solde, String motif) {
        return moteur().definir(joueur, solde, motif);
    }

    // ------------------------------------------------------------------ persistance
    /**
     * Ecrit comptes.json tout de suite et attend la fin de l'ecriture (de quelques ms a quelques centaines de ms
     * selon le nombre de comptes) : pour les operations critiques des autres mods (banque de clan, gros achats) qui
     * veulent le solde sur le disque avant de continuer. Fil serveur uniquement. Faux si rien n'a ete ecrit :
     * sauvegarde bloquee parce que le fichier etait illisible au demarrage, ou erreur disque (le journal du serveur
     * dit pourquoi) ; les soldes en memoire restent corrects dans tous les cas.
     */
    public static boolean sauvegarderMaintenant() {
        return moteur().sauvegarderMaintenant();
    }

    /**
     * Demande une sauvegarde groupee : au plus une ecriture par 1,5 s, serialisation sur le fil serveur et ecriture
     * sur le fil IO du mod, sans bloquer le tick. Pour marquer un changement important sans attendre (c'est ce que
     * fait l'API elle-meme pour les montants >= sauvegarde_immediate). Fil serveur uniquement.
     */
    public static void sauvegarderBientot() {
        moteur().sauvegarderBientot();
    }

    // ------------------------------------------------------------------ formats (utilisables partout, sans serveur)
    /** Nom de l'unite (config nom_singulier / nom_pluriel). */
    public static String nom(boolean pluriel) {
        return pluriel ? EconomieConfig.NOM_PLURIEL.get() : EconomieConfig.NOM_SINGULIER.get();
    }

    /** Symbole (config, vide = le mot est utilise). */
    public static String symbole() {
        return EconomieConfig.SYMBOLE.get();
    }

    /** « 1 250 hérals », « 1 héral », « 0 héral » (espace fine insecable comme separateur de milliers ; symbole a la place du mot s'il est defini). */
    public static String format(long v) {
        return format(v, nom(false), nom(true), symbole());
    }

    /** « +250 hérals » / « -1 250 hérals ». */
    public static String formatSigne(long v) {
        return (v >= 0 ? "+" : "-") + format(Math.abs(v));
    }

    /** Le texte formate, en composant (pour les messages de chat). */
    public static Component composant(long v) {
        return Component.literal(format(v));
    }

    /** Vrai si le montant prend le pluriel : a partir de 2 (regle francaise : « 0 héral », « 1 héral », « 2 hérals »). */
    public static boolean pluriel(long v) {
        return Math.abs(v) >= 2;
    }

    /** Format avec des noms donnes (le client s'en sert avec les noms recus du serveur). */
    public static String format(long v, String singulier, String pluriel, String symbole) {
        String unite = symbole != null && !symbole.isEmpty() ? symbole : (pluriel(v) ? pluriel : singulier);
        return nombre(v) + ESPACE_UNITE + unite;
    }

    /** Nombre avec l'espace fine insecable tous les trois chiffres : 1 250 000. */
    public static String nombre(long v) {
        String s = Long.toString(Math.abs(v));
        StringBuilder sb = new StringBuilder(s.length() + s.length() / 3 + 1);
        if (v < 0) sb.append('-');
        int premier = s.length() % 3;
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (i - premier) % 3 == 0) sb.append(SEPARATEUR_MILLIERS);
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }
}
