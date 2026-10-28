package fr.heraliz.crac.menu;

import fr.heraliz.crac.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Coffre de plus de 9 colonnes (caisse d'armes 11 × 6, coffre-fort 13 × 6) : comme le coffre de Minecraft, plus large ;
 * l'inventaire du joueur reste centré dessous. Mêmes placements que ChestMenu (cases de 18 px, 18 px de marge en haut).
 */
public class CoffreMenu extends AbstractContainerMenu {
    private final Container conteneur;
    private final int colonnes, lignes;

    /** Joueur : taille lue dans le paquet d'ouverture. */
    public CoffreMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readVarInt(), buf.readVarInt());
    }

    private CoffreMenu(int id, Inventory inv, int colonnes, int lignes) {
        this(ModMenus.COFFRE.get(), id, inv, new SimpleContainer(colonnes * lignes), colonnes, lignes);
    }

    public CoffreMenu(MenuType<?> type, int id, Inventory inv, Container conteneur, int colonnes, int lignes) {
        super(type, id);
        checkContainerSize(conteneur, colonnes * lignes);
        this.conteneur = conteneur;
        this.colonnes = colonnes;
        this.lignes = lignes;
        conteneur.startOpen(inv.player);
        int larg = largeur(colonnes);
        int x0 = (larg - colonnes * 18) / 2 + 1;
        for (int r = 0; r < lignes; r++) {
            for (int c = 0; c < colonnes; c++) {
                this.addSlot(new Slot(conteneur, c + r * colonnes, x0 + c * 18, 18 + r * 18));
            }
        }
        int xi = (larg - 9 * 18) / 2 + 1;
        int yi = 18 + lignes * 18 + 13;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, c + r * 9 + 9, xi + c * 18, yi + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            this.addSlot(new Slot(inv, c, xi + c * 18, yi + 58));
        }
    }

    /** Largeur de l'écran (px d'interface) : la grille et ses bords, jamais moins que l'inventaire du joueur. */
    public static int largeur(int colonnes) {
        return Math.max(176, 14 + colonnes * 18);
    }

    public Container conteneur() {
        return this.conteneur;
    }

    public int colonnes() {
        return this.colonnes;
    }

    public int lignes() {
        return this.lignes;
    }

    @NotNull
    @Override
    public ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack res = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack st = slot.getItem();
            res = st.copy();
            int n = this.colonnes * this.lignes;
            if (index < n) {
                if (!this.moveItemStackTo(st, n, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(st, 0, n, false)) {
                return ItemStack.EMPTY;
            }
            if (st.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return res;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return this.conteneur.stillValid(player);
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        this.conteneur.stopOpen(player);
    }
}
