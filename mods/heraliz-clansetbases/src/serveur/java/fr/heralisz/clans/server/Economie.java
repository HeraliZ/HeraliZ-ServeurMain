package fr.heralisz.clans.server;

import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.economie.api.EconomieApi;

import java.util.UUID;

/**
 * Economie du serveur vue par les clans : adaptateur fin vers HeraliZ Économie ({@link EconomieApi}, mod
 * {@code heralisz_economie}, dependance obligatoire). Toutes les operations sont en entiers (jetons), sur le fil
 * serveur. Les motifs passes a l'API sont prefixes « clan: » (banque, creation...) pour le journal et le HUD du joueur.
 */
public interface Economie {
    /** Solde du joueur (0 sans compte ou hors serveur). */
    long solde(UUID joueur);

    /** Retire le montant ; faux (et rien ne change) si le solde est insuffisant ou le montant <= 0. */
    boolean retirer(UUID joueur, long montant);

    /** Credite le montant (rien si <= 0 ; tronque au plafond de l'economie). */
    void deposer(UUID joueur, long montant);

    /** Variante avec motif precis (« clan:banque », « clan:creation », « clan:quete »...). */
    boolean retirer(UUID joueur, long montant, String motif);

    void deposer(UUID joueur, long montant, String motif);

    /** Unite affichee apres les montants : symbole de la config de l'economie, sinon le mot au pluriel (« jetons »). */
    String symbole();

    /** Nom du fournisseur (journal, /clan admin info). */
    String nom();

    /** Vrai si l'economie peut servir (serveur demarre, fil serveur). */
    boolean disponible();

    /** « 1 250 jetons » : texte deja formate par l'economie. */
    default String format(long montant) {
        return EconomieApi.format(montant);
    }

    /** Motif par defaut des operations des clans. */
    String MOTIF_DEFAUT = "clan:banque";

    // ------------------------------------------------------------------ adaptateur
    /** L'unique implementation : HeraliZ Économie. */
    final class Jetons implements Economie {
        static final Jetons INSTANCE = new Jetons();

        private Jetons() {
        }

        @Override
        public long solde(UUID joueur) {
            return EconomieApi.disponible() ? EconomieApi.solde(joueur) : 0;
        }

        @Override
        public boolean retirer(UUID joueur, long montant) {
            return retirer(joueur, montant, MOTIF_DEFAUT);
        }

        @Override
        public void deposer(UUID joueur, long montant) {
            deposer(joueur, montant, MOTIF_DEFAUT);
        }

        @Override
        public boolean retirer(UUID joueur, long montant, String motif) {
            return EconomieApi.disponible() && EconomieApi.retirer(joueur, montant, motif);
        }

        @Override
        public void deposer(UUID joueur, long montant, String motif) {
            if (EconomieApi.disponible()) EconomieApi.deposer(joueur, montant, motif);
        }

        @Override
        public String symbole() {
            String s = EconomieApi.symbole();
            return s == null || s.isEmpty() ? EconomieApi.nom(true) : s;
        }

        @Override
        public String nom() {
            return "heralisz_economie";
        }

        @Override
        public boolean disponible() {
            return EconomieApi.disponible();
        }
    }

    /** L'economie active (toujours HeraliZ Économie). */
    static Economie active() {
        return Jetons.INSTANCE;
    }

    /** Appele au demarrage du serveur par ClanStore : simple trace (plus de choix de fournisseur). */
    static void init(ClanStore store) {
        HeraliszClans.LOG.info("[clans] economie : HeraliZ Économie (unite « {} »)", Jetons.INSTANCE.symbole());
    }
}
