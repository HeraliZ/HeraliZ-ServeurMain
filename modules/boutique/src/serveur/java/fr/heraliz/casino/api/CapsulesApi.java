package fr.heraliz.casino.api;

import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.economy.CompteCapsules;

import java.util.UUID;

/**
 * Compte de capsules des joueurs (27/10), pour les autres mods. Lue par reflexion par HeraliZ Quetes (/wipe) : ne jamais
 * renommer cette classe ni changer ces signatures. Fil serveur seulement.
 */
public final class CapsulesApi {
    private CapsulesApi() {
    }

    /** Capsules sur le compte du joueur (0 si inconnu ou compte ferme). */
    public static long solde(UUID joueur) {
        return joueur == null ? 0 : CompteCapsules.solde(joueur);
    }

    /** Ajoute {@code n} capsules au compte (joueur en ligne ou non), ecrit tout de suite. Faux : rien n'a ete donne. */
    public static boolean crediter(UUID joueur, long n, String source) {
        return joueur != null && CompteCapsules.crediter(joueur, null, n, source == null ? "api" : source);
    }

    /** Remise a zero (/wipe, /wipe tout) : solde a 0, comme les herals. */
    public static boolean wipe(UUID joueur) {
        if (joueur == null) return false;
        boolean ok = CompteCapsules.remettreAZero(joueur, "wipe");
        if (!ok) HeralizCasino.LOGGER.error("[HeralizCasino] /wipe : compte de capsules de {} pas remis à zéro (bloqué ou illisible)", joueur);
        return ok;
    }
}
