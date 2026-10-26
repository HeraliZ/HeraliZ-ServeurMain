package fr.heralisz.clans.client.gui.atelier;

import fr.heralisz.clans.model.Banniere;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * Ce que les morceaux de l'atelier (toile, liste des calques, selecteur, panneaux) voient de l'ecran : la banniere en
 * cours, la selection, l'historique et quelques actions. Implemente par {@code AtelierScreen}.
 */
public interface Atelier {
    /** Banniere en cours d'edition. */
    Banniere banniere();

    /** Index du calque selectionne (du fond vers le dessus), -1 si rien n'est selectionne. */
    int selection();

    /** Selectionne le calque i (-1 = rien) ; ferme le selecteur si un calque est choisi. */
    void selectionner(int i);

    /**
     * Remplace la banniere en notant l'etat precedent dans l'historique. Groupe {@link Historique#AUCUN} = un etat
     * par appel ; un meme groupe rapproche (frappe hexa, glissiere, geste a la souris) ne note qu'un etat.
     */
    void changer(Banniere n, int groupe);

    /** Deplace le calque « de » a l'index « vers » de la pile et le garde selectionne. */
    void deplacer(int de, int vers);

    /** Nouveau groupe d'historique pour un geste (glisser sur la toile, glissiere) : un seul etat note par geste. */
    int nouveauGeste();

    /** Grille 8 x 12 affichee (et aimantation active). */
    boolean grille();

    /** Couleur d'accent de l'ecran. */
    int accent();

    Font police();

    /** Infobulle a dessiner a la fin de l'image (sous peut etre null). */
    void infobulle(Component titre, @Nullable Component sous);

    /** Ajoute un calque de cette forme au centre et le selectionne. */
    void ajouterForme(int forme);

    /** Remplace toute la banniere par un modele (annulable). */
    void appliquerModele(Modeles.Modele m);

    /** Cible de couleur : 0 = forme (calque) ou couleur A (fond), 1 = contour (calque) ou couleur B (fond). */
    int cible();

    void cible(int c);

    /** Applique une couleur a la cible courante (palette ou champ hexa). */
    void appliquerCouleur(int rgb, int groupe);
}
