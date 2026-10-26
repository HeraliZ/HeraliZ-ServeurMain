package fr.heralisz.clans.client.gui.atelier;

import fr.heralisz.clans.model.Banniere;
import net.minecraft.Util;

import javax.annotation.Nullable;
import java.util.ArrayDeque;

/**
 * Historique de l'atelier : 50 etats au plus (Ctrl+Z / Ctrl+Y). Les bannieres etant immuables, un etat est la
 * banniere elle-meme. Les changements d'un meme groupe sont regroupes : une frappe au clavier rapprochee (moins de
 * 800 ms) ou tout un geste a la souris (groupes {@code >= GESTES}, sans limite de temps) ne notent qu'un etat.
 */
public final class Historique {
    public static final int MAX = 50;
    /** Groupes : AUCUN = un etat par changement ; les autres regroupent les changements rapproches. */
    public static final int AUCUN = 0, HEXA = 1, CLAVIER_DEPLACER = 2, CLAVIER_TOURNER = 3, CLAVIER_TAILLE = 4;
    /** Premier numero des gestes (glisser, glissiere) : un geste = un etat, quelle que soit sa duree. */
    public static final int GESTES = 1000;
    private static final long REGROUPEMENT_MS = 800;

    private final ArrayDeque<Banniere> passe = new ArrayDeque<>(MAX + 1), futur = new ArrayDeque<>(MAX + 1);
    private int dernierGroupe = AUCUN;
    private long dernierMs;

    /** Note l'etat d'avant un changement (sauf s'il prolonge le changement precedent du meme groupe). */
    public void noter(Banniere avant, int groupe) {
        long t = Util.getMillis();
        boolean meme = groupe != AUCUN && groupe == dernierGroupe && (groupe >= GESTES || t - dernierMs < REGROUPEMENT_MS);
        dernierGroupe = groupe;
        dernierMs = t;
        if (meme) return;
        passe.push(avant);
        while (passe.size() > MAX) passe.removeLast();
        futur.clear();
    }

    /** Etat precedent (et courant mis de cote pour Ctrl+Y), null s'il n'y en a pas. */
    @Nullable
    public Banniere annuler(Banniere courant) {
        if (passe.isEmpty()) return null;
        futur.push(courant);
        while (futur.size() > MAX) futur.removeLast();
        dernierGroupe = AUCUN;
        return passe.pop();
    }

    /** Etat suivant (apres un Ctrl+Z), null s'il n'y en a pas. */
    @Nullable
    public Banniere retablir(Banniere courant) {
        if (futur.isEmpty()) return null;
        passe.push(courant);
        while (passe.size() > MAX) passe.removeLast();
        dernierGroupe = AUCUN;
        return futur.pop();
    }

    public boolean peutAnnuler() {
        return !passe.isEmpty();
    }

    public boolean peutRetablir() {
        return !futur.isEmpty();
    }

    /** Coupe le regroupement en cours (le prochain changement note un nouvel etat). */
    public void couper() {
        dernierGroupe = AUCUN;
    }

    public int taille() {
        return passe.size();
    }
}
