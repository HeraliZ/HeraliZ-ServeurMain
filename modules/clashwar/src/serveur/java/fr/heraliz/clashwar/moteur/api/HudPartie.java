package fr.heraliz.clashwar.moteur.api;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Bandeau standard d'une partie (pastilles de ses equipes), cote serveur seulement : {@link Partie} et {@link Equipe}
 * ne partent plus chez les joueurs (audit du 02/11, point 37), ces deux aides ont donc quitte {@link Hud} (partage).
 * Fil serveur.
 */
public final class HudPartie {
    private HudPartie() {
    }

    /** Bandeau standard d'une partie envoye a ses joueurs connectes. */
    public static void envoyer(Partie partie, Component titre, int secondes, Component score) {
        Hud.envoyer(partie.joueursEnLigne(), bandeau(partie, titre, secondes, score));
    }

    /** Construit le bandeau standard d'une partie. */
    public static Hud.Bandeau bandeau(Partie partie, Component titre, int secondes, Component score) {
        List<Hud.Pastille> l = new ArrayList<>(partie.equipes().size());
        for (Equipe e : partie.equipes()) l.add(new Hud.Pastille(e.nom(), e.couleur(), e.nbVivants(), e.taille()));
        return new Hud.Bandeau(titre, secondes, l, score);
    }
}
