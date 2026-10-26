package fr.heraliz.quetes.bloc;

import fr.heraliz.quetes.HeralizQuetes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu du recycleur (25/10, maquette validée : maquettes/recycleur/) : 6 cases « à recycler », 6 cases « récupéré » (on y
 * prend seulement), l'inventaire du joueur, et l'état du recyclage synchronisé pour l'écran ({@link #donnees}). Toute la
 * décision (quoi recycler, tirage, hérals) est dans la partie serveur ({@code serveur/MenuRecycleurServeur}) ; ici,
 * seulement la disposition des cases (mêmes mesures que l'écran : panneau 560 x 324, cases de 20 points, objet à +2) et
 * les déplacements Maj + clic.
 */
public class MenuRecycleur extends AbstractContainerMenu {
    public static final int ENTREES = 6, SORTIES = 6;
    /** Données synchronisées (valeurs sur 16 bits, comme toutes les données de menu). */
    public static final int D_PROGRES = 0, D_ATTENTE = 1, D_ETAT = 2, D_HERALS = 3, D_CASE = 4, D_DIXIEMES = 5, NB_DONNEES = 6;
    /** États de la machine : rien déposé, en cours, sortie pleine, rien de recyclable dans les cases. */
    public static final int VIDE = 0, EN_COURS = 1, PLEIN = 2, RIEN = 3;
    /** Bouton « Tout prendre » (clickMenuButton). */
    public static final int BOUTON_TOUT_PRENDRE = 0;

    /** Disposition (points d'interface, depuis le coin du panneau) : grilles de 3 x 2 cases, inventaire 9 x 4. */
    public static final int CASE = 20, ENTREE_X = 16, SORTIE_X = 310, MACHINE_Y = 66, INVENTAIRE_X = 16, INVENTAIRE_Y = 162;

    protected final Container entree, sortie;
    protected final ContainerData donnees;

    /** Côté joueur (type de menu, IForgeMenuType) : conteneurs vides, remplis par le serveur. */
    public MenuRecycleur(int id, Inventory inv, FriendlyByteBuf donneesOuverture) {
        this(id, inv, new SimpleContainer(ENTREES), new SimpleContainer(SORTIES), new SimpleContainerData(NB_DONNEES));
    }

    protected MenuRecycleur(int id, Inventory inv, Container entree, Container sortie, ContainerData donnees) {
        super(HeralizQuetes.RECYCLEUR_MENU.get(), id);
        this.entree = entree;
        this.sortie = sortie;
        this.donnees = donnees;
        for (int i = 0; i < ENTREES; i++) {
            addSlot(new Slot(entree, i, ENTREE_X + (i % 3) * (CASE + 2) + 2, MACHINE_Y + (i / 3) * (CASE + 2) + 2));
        }
        for (int i = 0; i < SORTIES; i++) {
            addSlot(new Slot(sortie, i, SORTIE_X + (i % 3) * (CASE + 2) + 2, MACHINE_Y + (i / 3) * (CASE + 2) + 2) {
                @Override
                public boolean mayPlace(ItemStack s) {
                    return false;
                }
            });
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inv, 9 + r * 9 + c, INVENTAIRE_X + c * (CASE + 2) + 2, INVENTAIRE_Y + r * (CASE + 2) + 2));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inv, c, INVENTAIRE_X + c * (CASE + 2) + 2, INVENTAIRE_Y + 3 * (CASE + 2) + 4 + 2));
        }
        addDataSlots(donnees);
    }

    public int donnee(int i) {
        return donnees.get(i);
    }

    /** Index des cases du menu : entrée 0-5, sortie 6-11, inventaire 12-38, barre 39-47. */
    public static boolean estEntree(int index) {
        return index >= 0 && index < ENTREES;
    }

    public static boolean estSortie(int index) {
        return index >= ENTREES && index < ENTREES + SORTIES;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack s = slot.getItem(), avant = s.copy();
        int inv = ENTREES + SORTIES, fin = slots.size();
        if (index < inv) {
            // entrée ou sortie -> inventaire (la barre d'abord)
            if (!moveItemStackTo(s, inv, fin, true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(s, 0, ENTREES, false)) {
            return ItemStack.EMPTY;
        }
        if (s.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (s.getCount() == avant.getCount()) return ItemStack.EMPTY;
        slot.onTake(p, s);
        return avant;
    }

    @Override
    public boolean clickMenuButton(Player p, int bouton) {
        if (bouton != BOUTON_TOUT_PRENDRE) return false;
        // tout ce qui est récupéré -> inventaire (ce qui ne tient pas reste dans la sortie)
        int inv = ENTREES + SORTIES;
        for (int i = ENTREES; i < inv; i++) {
            Slot slot = slots.get(i);
            if (!slot.hasItem()) continue;
            ItemStack s = slot.getItem();
            moveItemStackTo(s, inv, slots.size(), true);
            if (s.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return true;
    }

    @Override
    public boolean stillValid(Player p) {
        return true;
    }
}
