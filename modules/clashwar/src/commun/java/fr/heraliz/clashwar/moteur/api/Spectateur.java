package fr.heraliz.clashwar.moteur.api;

import fr.heraliz.clashwar.pont.Services;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Spectateur borne (MATCHS_SPEC §1.9) : un elimine regarde son equipe (camera sur un coequipier vivant, fleches
 * gauche/droite pour changer, sinon un autre joueur vivant, sinon le point {@code spectateur} de la carte) sans
 * pouvoir sortir des bornes de l'instance. Le mode decide ensuite (reapparition, retour en salle d'attente...).
 * Fil serveur.
 */
public final class Spectateur {
    private Spectateur() {
    }

    /**
     * Fait du joueur un spectateur de la partie (mode spectateur, ajoute a {@link Partie#spectateurs()}). Le mode a
     * deja appele {@link Equipe#eliminer} s'il le faut.
     */
    public static void suivre(ServerPlayer joueur, Partie partie) {
        Services.Moteur m = Services.MOTEUR;   // moteur/spectateur/Spectateurs (serveur)
        if (m != null) m.spectateurSuivre(joueur, partie);
    }

    /**
     * Fin du mode spectateur (retire de {@link Partie#spectateurs()}). {@code restaurerMode} : rend le mode de jeu qu'il
     * avait avant (sinon le mode le fixe lui-meme, par exemple Survie a la reapparition).
     */
    public static void arreter(ServerPlayer joueur, boolean restaurerMode) {
        Services.Moteur m = Services.MOTEUR;
        if (m != null) m.spectateurArreter(joueur, restaurerMode);
    }

    /** Vrai si le joueur est spectateur d'une partie. */
    public static boolean est(UUID joueur) {
        Services.Moteur m = Services.MOTEUR;
        return m != null && m.spectateurEst(joueur);
    }
}
