package fr.heraliz.casino.economy;

import java.util.UUID;

/**
 * Branche le pont vers HeraliZ Economie dans {@link EconomieServeur} (PartieServeur.init). Cette classe ne cite que
 * {@link PontEconomie} : comme avant, PontEconomie (et l'API du mod economie) n'est chargee qu'au premier appel, et
 * EconomieServeur ne l'appelle que si le mod est la.
 */
public final class EconomieServeurPont {
    private EconomieServeurPont() {
    }

    public static void installer() {
        EconomieServeur.pont = new EconomieServeur.Pont() {
            @Override
            public boolean disponible() {
                return PontEconomie.disponible();
            }

            @Override
            public long solde(UUID joueur) {
                return PontEconomie.solde(joueur);
            }

            @Override
            public boolean retirer(UUID joueur, long montant, String motif) {
                return PontEconomie.retirer(joueur, montant, motif);
            }

            @Override
            public boolean peutRecevoir(UUID joueur, long montant) {
                return PontEconomie.peutRecevoir(joueur, montant);
            }

            @Override
            public void deposer(UUID joueur, long montant, String motif) {
                PontEconomie.deposer(joueur, montant, motif);
            }

            @Override
            public String format(long v) {
                return PontEconomie.format(v);
            }
        };
    }
}
