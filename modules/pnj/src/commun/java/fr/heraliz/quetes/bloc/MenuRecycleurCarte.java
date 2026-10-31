package fr.heraliz.quetes.bloc;

import fr.heraliz.quetes.HeralizQuetes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * Menu du recycleur de la carte (31/10) : même disposition et même écran que {@link MenuRecycleur}, autre type de menu
 * ({@code heraliz_quetes:recycleur_carte}) pour que l'écran sache qu'il montre la machine partagée. Côté serveur : sa
 * version {@code serveur/MenuRecycleurCarteServeur} (cases de la machine, tri des entrées, sauvegarde de chaque clic).
 */
public class MenuRecycleurCarte extends MenuRecycleur {
    /** Côté joueur (type de menu, IForgeMenuType) : conteneurs vides, remplis par le serveur. */
    public MenuRecycleurCarte(int id, Inventory inv, FriendlyByteBuf donneesOuverture) {
        this(id, inv, new SimpleContainer(ENTREES), new SimpleContainer(SORTIES), new SimpleContainerData(NB_DONNEES));
    }

    protected MenuRecycleurCarte(int id, Inventory inv, Container entree, Container sortie, ContainerData donnees) {
        super(HeralizQuetes.RECYCLEUR_CARTE_MENU.get(), id, inv, entree, sortie, donnees);
    }
}
