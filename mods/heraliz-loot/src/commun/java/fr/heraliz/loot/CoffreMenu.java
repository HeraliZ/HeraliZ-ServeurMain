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
 */
public class CoffreMenu extends AbstractContainerMenu {
    public static final int D_COURANT = 0, D_PROGRES = 1, D_DUREE = 2, D_MASQUE = 3, D_RESTANTS = 6, N_DONNEES = 7;
    final CoffreBlockEntity coffre;                       // serveur seulement (null chez le client)
    public final BlockPos pos;
    public final ContainerData donnees;

    /** Client. */
    public CoffreMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, null, buf.readBlockPos(), new SimpleContainer(CoffreBlockEntity.TAILLE), new SimpleContainerData(N_DONNEES));
    }

    /** Serveur : CoffreMenuServeur (vue « ? » des objets non identifiés, données de l'identification). */
    protected CoffreMenu(int id, Inventory inv, CoffreBlockEntity be, BlockPos pos, Container c, ContainerData d) {
        super(HeralizLoot.COFFRE_MENU.get(), id);
        this.coffre = be;
        this.pos = pos;
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
        return coffre == null || (!coffre.isRemoved() && p.distanceToSqr(pos.getCenter()) <= 36); // 6 blocs, comme les conteneurs
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
