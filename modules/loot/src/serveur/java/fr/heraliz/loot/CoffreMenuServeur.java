package fr.heraliz.loot;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Menu d'un coffre T7, côté serveur : les cases pas encore identifiées montrent l'objet « ? » (le vrai objet ne quitte
 * jamais le serveur) ; données synchronisées de l'identification (case en cours, progression, durée, masques, restants).
 */
public class CoffreMenuServeur extends CoffreMenu {

    public CoffreMenuServeur(int id, Inventory inv, CoffreBlockEntityServeur be) {
        super(id, inv, be, be.getBlockPos(), true, new VueServeur(be), new Donnees(be));
    }

    /** Le coffre vu par le menu (serveur) : « ? » tant que la case n'est pas identifiée. */
    static final class VueServeur implements Vue {
        final CoffreBlockEntityServeur be;

        VueServeur(CoffreBlockEntityServeur be) {
            this.be = be;
        }

        @Override
        public boolean identifie(int i) {
            return be.identifie[i];
        }

        @Override
        public int getContainerSize() {
            return CoffreBlockEntity.TAILLE;
        }

        @Override
        public boolean isEmpty() {
            return be.objets.stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getItem(int i) {
            ItemStack st = be.objets.get(i);
            if (st.isEmpty() || be.identifie[i]) {
                return st;
            }
            return new ItemStack(HeralizLoot.OBJET_INCONNU.get());
        }

        @Override
        public ItemStack removeItem(int i, int n) {
            if (!be.identifie[i]) {
                return ItemStack.EMPTY;
            }
            ItemStack r = be.objets.get(i).split(n);
            be.setChanged();
            return r;
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            if (!be.identifie[i]) {
                return ItemStack.EMPTY;
            }
            ItemStack r = be.objets.get(i);
            be.objets.set(i, ItemStack.EMPTY);
            return r;
        }

        @Override
        public void setItem(int i, ItemStack st) {
            if (be.identifie[i]) {
                be.objets.set(i, st);
                be.setChanged();
            }
        }

        @Override
        public void setChanged() {
            be.setChanged();
        }

        @Override
        public boolean stillValid(Player p) {
            return true;
        }

        @Override
        public void clearContent() {
        }
    }

    static final class Donnees implements ContainerData {
        final CoffreBlockEntityServeur be;

        Donnees(CoffreBlockEntityServeur be) {
            this.be = be;
        }

        @Override
        public int get(int i) {
            return switch (i) {
                case D_COURANT -> be.courant;
                case D_PROGRES -> be.progres;
                case D_DUREE -> be.duree(be.courant);
                case D_RESTANTS -> be.restants();
                default -> {
                    int m = 0, b0 = (i - D_MASQUE) * 9;
                    for (int k = 0; k < 9; k++) {
                        if (be.identifie[b0 + k]) {
                            m |= 1 << k;
                        }
                    }
                    yield m;
                }
            };
        }

        @Override
        public void set(int i, int v) {
        }

        @Override
        public int getCount() {
            return N_DONNEES;
        }
    }
}
