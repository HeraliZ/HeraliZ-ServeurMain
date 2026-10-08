package fr.heraliz.casino.economy;

import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.config.CasinoConfig;
import net.minecraftforge.fml.ModList;
import java.util.UUID;

/**
 * Les jetons du serveur (mod HeraliZ Economie, FACULTATIF) vus par le casino. Cette classe ne touche jamais au mod
 * economie directement : tout passe par le pont du serveur (PontEconomie, src/serveur), qui n'est charge que si le mod
 * est la. Sans lui (ou en
 * cas de version incompatible), {@link #present()} est faux et le casino fonctionne comme avant (objets seulement).
 * <p>Ecritures : fil serveur uniquement (comme l'API de l'economie). Montants : jetons du serveur.
 */
public final class EconomieServeur {
    private EconomieServeur() {
    }

    public static final String MODID = "heralisz_economie";
    /** Motifs du journal de l'economie (HUD du joueur : « Achat de jetons de casino »...). */
    public static final String MOTIF_ACHAT = "casino:achat de jetons de casino", MOTIF_VENTE = "casino:revente de jetons de casino",
            MOTIF_ENTREE = "casino:entr" + (char) 0xE9 + "e du casino";

    private static Boolean present;

    /**
     * Les seuls appels a l'API du mod economie, cote serveur (src/serveur : PontEconomie), branches par la partie
     * serveur. Sans elle (jar -client), le change en herals est indisponible, comme sans le mod economie.
     */
    interface Pont {
        boolean disponible();

        long solde(UUID joueur);

        boolean retirer(UUID joueur, long montant, String motif);

        boolean peutRecevoir(UUID joueur, long montant);

        void deposer(UUID joueur, long montant, String motif);

        String format(long v);
    }

    static volatile Pont pont;

    /** Le mod economie est charge (des deux cotes : il est installe sur le serveur et chez chaque joueur). */
    public static boolean present() {
        if (present == null) {
            try {
                present = ModList.get() != null && ModList.get().isLoaded(MODID);
            } catch (Exception ex) {
                present = false;
            }
        }
        return present;
    }

    /** Apres une erreur de liaison (mod economie d'une version incompatible) : on fait comme s'il etait absent. */
    static void incompatible(Throwable t) {
        if (Boolean.FALSE.equals(present)) return;
        present = false;
        HeralizCasino.LOGGER.error("[HeralizCasino] HeraliZ Economie incompatible : change en jetons du serveur desactive", t);
    }

    /** Change en jetons du serveur ouvert : mod present et section caisse.economie active. */
    public static boolean ouvert() {
        return present() && CasinoConfig.get().caisse.economie.actif;
    }

    /** Comptes utilisables maintenant (serveur demarre, fil serveur). */
    public static boolean disponible() {
        Pont pe = pont;
        if (!present() || pe == null) return false;
        try {
            return pe.disponible();
        } catch (LinkageError e) {
            incompatible(e);
            return false;
        }
    }

    /** Solde du joueur (0 sans economie). */
    public static long solde(UUID joueur) {
        if (!disponible()) return 0;
        try {
            return pont.solde(joueur);
        } catch (LinkageError e) {
            incompatible(e);
            return 0;
        }
    }

    /** Retire le montant du compte ; faux (et rien ne change) si le solde ne suffit pas ou sans economie. */
    public static boolean retirer(UUID joueur, long montant, String motif) {
        if (montant <= 0 || !disponible()) return false;
        try {
            return pont.retirer(joueur, montant, motif);
        } catch (LinkageError e) {
            incompatible(e);
            return false;
        }
    }

    /** Le compte peut recevoir ce montant sans depasser son plafond (sinon le depot serait tronque). */
    public static boolean peutRecevoir(UUID joueur, long montant) {
        if (montant <= 0 || !disponible()) return false;
        try {
            return pont.peutRecevoir(joueur, montant);
        } catch (LinkageError e) {
            incompatible(e);
            return false;
        }
    }

    /** Verse le montant sur le compte ; faux sans economie (appeler {@link #peutRecevoir} avant). */
    public static boolean deposer(UUID joueur, long montant, String motif) {
        if (montant <= 0 || !disponible()) return false;
        try {
            pont.deposer(joueur, montant, motif);
            return true;
        } catch (LinkageError e) {
            incompatible(e);
            return false;
        }
    }

    /** « 1 250 jetons » (noms et symbole de la config de l'economie) ; sans economie : le nombre seul. */
    public static String format(long v) {
        Pont pe = pont;
        if (present() && pe != null) {
            try {
                return pe.format(v);
            } catch (LinkageError e) {
                incompatible(e);
            }
        }
        return Chips.format(v);
    }

    // =================================================================== taux (section caisse.economie)
    /** Jetons du serveur pour acheter "casino" jetons de casino (arrondi au-dessus : la caisse ne perd jamais). */
    public static long prixServeur(long casino) {
        CasinoConfig.JetonsServeur e = CasinoConfig.get().caisse.economie;
        long unites = (casino + e.jetonsCasino - 1) / e.jetonsCasino;
        return unites * e.jetonsServeur;
    }
}
