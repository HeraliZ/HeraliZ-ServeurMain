package fr.heraliz.loot;

import net.minecraft.core.BlockPos;
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
 * Menu d'un coffre T7 : 27 cases (3 x 9) + l'inventaire du joueur. Les cases pas encore identifiées montrent l'objet
 * « ? » (le vrai objet reste sur le serveur) et ne se prennent pas ; on ne peut rien déposer dans le coffre.
 * Données synchronisées : case en cours, progression, durée, cases identifiées (3 masques de 9 bits), restants.
 * Source (25/10) : un coffre T7 posé ({@link CoffreBlockEntity}) ou une caisse d'un autre mod, entité qui est un conteneur
 * de 27 cases (caisse de largage : {@code RevelationCaisses}) ; badge = le palier « T7 » à côté du nom.
 */
public class CoffreMenu extends AbstractContainerMenu {
    public static final int D_COURANT = 0, D_PROGRES = 1, D_DUREE = 2, D_MASQUE = 3, D_RESTANTS = 6, N_DONNEES = 7;
    final Source coffre;                                  // serveur seulement (null chez le client)
    public final BlockPos pos;
    public final boolean badge;
    public final ContainerData donnees;

    /** Ce que montre le menu, côté serveur : encore là et à portée du joueur, et fermeture par ce joueur. */
    public interface Source {
        boolean valide(Player p);

        void fermer(Player p);
    }

    /** Client : la position, puis (caisses) le badge du palier ; rien après la position = coffre T7, avec badge. */
    public CoffreMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, null, buf.readBlockPos(), !buf.isReadable() || buf.readBoolean(), new SimpleContainer(CoffreBlockEntity.TAILLE),
                new SimpleContainerData(N_DONNEES));
    }

    /** Serveur : CoffreMenuServeur ou CaisseMenu (vue « ? » des objets non identifiés, données de l'identification). */
    protected CoffreMenu(int id, Inventory inv, Source src, BlockPos pos, boolean badge, Container c, ContainerData d) {
        super(HeralizLoot.COFFRE_MENU.get(), id);
        this.coffre = src;
        this.pos = pos;
        this.badge = badge;
        this.donnees = d;
        for (int r = 0; r < 3; r++) {
            for (int k = 0; k < 9; k++) {
                addSlot(new Case(this, c, k + r * 9, 8 + k * 18, 22 + r * 18));
            }
        }
        for (int r = 0; r < 3; r++) {
            for (int k = 0; k < 9; k++) {
                addSlot(new Slot(inv, k + r * 9 + 9, 8 + k * 18, 94 + r * 18));
            }
        }
        for (int k = 0; k < 9; k++) {
            addSlot(new Slot(inv, k, 8 + k * 18, 152));
        }
        addDataSlots(d);
    }

    public boolean identifie(int i) {
        return (donnees.get(D_MASQUE + i / 9) >> (i % 9) & 1) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int i) {
        Slot s = slots.get(i);
        if (i >= CoffreBlockEntity.TAILLE || !s.hasItem() || !s.mayPickup(p)) {
            return ItemStack.EMPTY;
        }
        ItemStack st = s.getItem(), copie = st.copy();
        if (!moveItemStackTo(st, CoffreBlockEntity.TAILLE, slots.size(), true)) {
            return ItemStack.EMPTY;
        }
        s.setChanged();
        if (st.isEmpty()) {
            s.set(ItemStack.EMPTY);
        }
        return copie;
    }

    @Override
    public boolean stillValid(Player p) {
        return coffre == null || coffre.valide(p);
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        if (coffre != null) {
            coffre.fermer(p);
        }
    }

    /** Case du coffre : on ne dépose rien ; on ne prend qu'un objet identifié. */
    static final class Case extends Slot {
        private final CoffreMenu menu;

        Case(CoffreMenu menu, Container c, int i, int x, int y) {
            super(c, i, x, y);
            this.menu = menu;
        }

        @Override
        public boolean mayPlace(ItemStack st) {
            return false;
        }

        @Override
        public boolean mayPickup(Player p) {
            return container instanceof Vue v ? v.identifie(getContainerSlot()) : menu.identifie(getContainerSlot());
        }
    }

    /** Le coffre vu par le menu côté serveur (CoffreMenuServeur) : sait quelles cases sont identifiées. */
    interface Vue extends Container {
        boolean identifie(int i);
    }
}
