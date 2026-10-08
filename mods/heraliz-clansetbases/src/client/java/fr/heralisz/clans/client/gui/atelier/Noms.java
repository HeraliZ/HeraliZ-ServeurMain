package fr.heralisz.clans.client.gui.atelier;

import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.Formes;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * Noms des formes pour l'atelier, calcules une fois (pas de traduction ni de chaine creee a chaque image) :
 * composant d'infobulle, texte complet et texte coupe a la largeur d'une ligne de la liste des calques.
 * {@link #vider()} a chaque ouverture de l'atelier (la langue a pu changer).
 */
public final class Noms {
    private Noms() {
    }

    private static final Component[] COMPOSANTS = new Component[Formes.NOMBRE];
    private static final String[] TEXTES = new String[Formes.NOMBRE];
    private static final Component[] COUPES = new Component[Formes.NOMBRE];
    private static int largeurCoupe = -1;

    public static void vider() {
        for (int i = 0; i < Formes.NOMBRE; i++) {
            COMPOSANTS[i] = null;
            TEXTES[i] = null;
            COUPES[i] = null;
        }
        largeurCoupe = -1;
    }

    private static int borne(int id) {
        return id < 0 || id >= Formes.NOMBRE ? Formes.EMBLEMES : id;
    }

    /** Nom traduit (police courante de l'interface), pour les infobulles. */
    public static Component composant(int id) {
        int i = borne(id);
        if (COMPOSANTS[i] == null) COMPOSANTS[i] = Ui.tr(Ui.FONT, Formes.libelle(i));
        return COMPOSANTS[i];
    }

    /** Nom traduit en texte. */
    public static String texte(int id) {
        int i = borne(id);
        if (TEXTES[i] == null) TEXTES[i] = Component.translatable(Formes.libelle(i)).getString();
        return TEXTES[i];
    }

    /** Nom coupe avec « … » a la largeur donnee, en composant pret a dessiner (police FONT). */
    public static Component coupe(Font font, int id, int largeur) {
        int i = borne(id);
        if (largeur != largeurCoupe) {
            for (int k = 0; k < Formes.NOMBRE; k++) COUPES[k] = null;
            largeurCoupe = largeur;
        }
        if (COUPES[i] == null) COUPES[i] = Ui.lit(Ui.ellipsis(font, texte(i), Ui.FONT, largeur), Ui.FONT);
        return COUPES[i];
    }
}
