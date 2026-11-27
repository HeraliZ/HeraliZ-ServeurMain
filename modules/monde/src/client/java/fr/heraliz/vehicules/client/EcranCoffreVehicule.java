package fr.heraliz.vehicules.client;

import fr.heraliz.noyau.client.kit.EcranConteneurVerre;
import fr.heraliz.vehicules.menu.CoffreVehiculeMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Écran du coffre d'un véhicule (27/11) : en verre comme la fouille du loot et les coffres des bases
 * ({@link EcranConteneurVerre} du noyau) : le nom du coffre en haut (« Coffre du camion »...), son contenu en grille
 * ({@code colonnes x lignes} reçues du serveur), l'inventaire du joueur en bas. L'échelle d'interface se règle pour que
 * le panneau tienne dans la fenêtre (camion 24 x 6 : 550 x 300 unités, tient à l'échelle 2 en 1366 x 768). Rien ne se
 * décide ici : même menu que le serveur, mêmes cases.
 */
public class EcranCoffreVehicule extends EcranConteneurVerre<CoffreVehiculeMenu> {
    public EcranCoffreVehicule(CoffreVehiculeMenu menu, Inventory inv, Component titre) {
        super(menu, inv, titre, menu.colonnes(), menu.lignes());
    }
}
