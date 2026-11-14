package fr.heraliz.crac.client.ui;

import fr.heraliz.crac.menu.CoffreMenu;
import fr.heraliz.noyau.client.kit.EcranConteneurVerre;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Écran des coffres de plus de 9 colonnes (caisse d'armes 11 x 6, coffre-fort 13 x 6). 14/11 (aperçu validé par le
 * propriétaire) : en verre comme l'inventaire ({@link EcranConteneurVerre} du noyau) : le nom en haut, le contenu en
 * grille, « Sur toi » et « Accès rapide » en bas ; l'échelle d'interface se règle pour que le panneau tienne dans la
 * fenêtre. Même menu (CoffreMenu) : côté serveur rien ne change (règles des coffres de base, objets à vie…). Les
 * coffres de 9 colonnes ou moins passent par l'écran de coffre du jeu, en verre aussi (HeraliZ Loot, EcranConteneur).
 */
public class CoffreScreen extends EcranConteneurVerre<CoffreMenu> {
    public CoffreScreen(CoffreMenu menu, Inventory inv, Component titre) {
        super(menu, inv, titre, menu.colonnes(), menu.lignes());
    }
}
