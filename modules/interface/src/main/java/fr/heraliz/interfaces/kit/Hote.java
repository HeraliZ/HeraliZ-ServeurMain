package fr.heraliz.interfaces.kit;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Ecran qui heberge un composant reutilisable (editeur de permissions dans l'onglet du clan ou dans l'ecran
 * autonome) : le composant y ajoute ses widgets et y pose son infobulle, dessinee a la fin de l'image.
 */
public interface Hote {
    /** Ajoute un widget a l'ecran (rendu et evenements). */
    <T extends AbstractWidget> T ajouter(T w);

    /** Infobulle a dessiner a la fin de l'image courante. */
    void infobulle(List<Component> lignes);
}
