package fr.heraliz.vehicules.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * Coffre d'un véhicule (27/11, demande du propriétaire : Maj + clic droit sur un véhicule ; moto 9 x 3, buggy 12 x 6,
 * fourgon 15 x 6, camion 24 x 6). Un coffre de Minecraft de {@code colonnes x lignes} cases, puis l'inventaire du joueur ;
 * l'écran en verre du joueur (client.EcranCoffreVehicule) range les cases à sa façon.
 * <ul>
 *   <li>Côté serveur, le conteneur est CELUI du véhicule (le même objet pour tous les joueurs qui le regardent, gardé avec
 *   l'entité : fr.heraliz.vehicules.CoffresVehicules) ; les règles (portée, véhicule arrêté, ni détruit ni retiré, joueur ni
 *   à bord, ni à terre, ni en match) sont dans la {@code garde} donnée par le serveur, relue à chaque tick et avant
 *   chaque clic par Minecraft.</li>
 *   <li>Une case n'accepte que ce que le conteneur accepte ({@code canPlaceItem}) : objets prêtés par un match, objets à
 *   vie et sacs pleins refusés par le serveur, même par Maj + clic ou échange avec la barre.</li>
 *   <li>Côté joueur : un conteneur vide de la bonne taille, sans règle (le serveur corrige).</li>
 * </ul>
 */
public class CoffreVehiculeMenu extends AbstractContainerMenu {
    /** Bornes de la grille (le serveur ne propose jamais plus). */
    public static final int COLONNES_MAX = 24, LIGNES_MAX = 9;

    private final Container conteneur;
    private final int colonnes, lignes;
    @Nullable
    private final Predicate<Player> garde;

    /** Joueur : taille lue dans le paquet d'ouverture. */
    public static CoffreVehiculeMenu client(int id, Inventory inv, FriendlyByteBuf buf) {
        int c = Math.max(1, Math.min(COLONNES_MAX, buf.readVarInt()));
        int l = Math.max(1, Math.min(LIGNES_MAX, buf.readVarInt()));
        return new CoffreVehiculeMenu(id, inv, new SimpleContainer(c * l), c, l, null);
    }

    /**
     * @param conteneur le coffre (serveur : celui du véhicule, au moins {@code colonnes x lignes} cases)
     * @param garde     règles pour garder l'écran ouvert (serveur), null chez le joueur
     */
    public CoffreVehiculeMenu(int id, Inventory inv, Container conteneur, int colonnes, int lignes, @Nullable Predicate<Player> garde) {
        super(MenusVehicules.COFFRE.get(), id);
        checkContainerSize(conteneur, colonnes * lignes);
        this.conteneur = conteneur;
        this.colonnes = colonnes;
        this.lignes = lignes;
        this.garde = garde;
        conteneur.startOpen(inv.player);
        int larg = Math.max(176, 14 + colonnes * 18);
        int x0 = (larg - colonnes * 18) / 2 + 1;
        for (int r = 0; r < lignes; r++) {
            for (int c = 0; c < colonnes; c++) {
                this.addSlot(new Slot(conteneur, c + r * colonnes, x0 + c * 18, 18 + r * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return this.container.canPlaceItem(this.getContainerSlot(), stack);
                    }
                });
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

    public Container conteneur() {
        return this.conteneur;
    }

    public int colonnes() {
        return this.colonnes;
    }

    public int lignes() {
        return this.lignes;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) return ItemStack.EMPTY;
        ItemStack res = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack st = slot.getItem();
            res = st.copy();
            int n = this.colonnes * this.lignes;
            if (index < n) {
                if (!this.moveItemStackTo(st, n, this.slots.size(), true)) return ItemStack.EMPTY;
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
    public boolean stillValid(Player player) {
        return this.garde == null || this.garde.test(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.conteneur.stopOpen(player);
    }
}
