package fr.heraliz.casino.economy;

import fr.heralisz.economie.api.EconomieApi;

import java.util.UUID;

/**
 * Seule classe du casino (cote serveur) qui reference l'API de HeraliZ Economie. Elle n'est chargee qu'au premier
 * appel, et {@link EconomieServeur} ne l'appelle que si le mod est charge : sans lui, le casino demarre normalement.
 */
final class PontEconomie {
    private PontEconomie() {
    }

    static boolean disponible() {
        return EconomieApi.disponible();
    }

    static long solde(UUID joueur) {
        return EconomieApi.solde(joueur);
    }

    static boolean retirer(UUID joueur, long montant, String motif) {
        return EconomieApi.retirer(joueur, montant, motif);
    }

    static boolean peutRecevoir(UUID joueur, long montant) {
        return EconomieApi.solde(joueur) + montant <= EconomieApi.maxSolde();
    }

    static void deposer(UUID joueur, long montant, String motif) {
        EconomieApi.deposer(joueur, montant, motif);
    }

    static String format(long v) {
        return EconomieApi.format(v);
    }
}
