package fr.heralisz.utils.inspecter;

import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Les 54 cases d'une vue de /inspecter : chacune renvoie à une vraie case (inventaire du joueur, Curios, sac, coffre…),
 * ou est un décor ou un bouton (rien ne s'y prend ni ne s'y pose). Les objets sont toujours ceux d'origine : prendre
 * dans une case retire l'objet de son vrai emplacement.
 */
final class Vue implements Container {
    static final int TAILLE = 54;

    /** Une case de la vue. */
    interface Case {
        ItemStack get();

        default void set(ItemStack s) {
        }

        default boolean modifiable() {
            return true;
        }

        default boolean accepte(ItemStack s) {
            return modifiable();
        }

        default Runnable action() {
            return null;
        }
    }

    /** Case qui renvoie à la case {@code i} d'un conteneur (tant que {@code vivant} est vrai). */
    static Case de(Container c, int i, Predicate<ItemStack> accepte, Supplier<Boolean> vivant) {
        return new Case() {
            @Override
            public ItemStack get() {
                return vivant.get() ? c.getItem(i) : ItemStack.EMPTY;
            }

            @Override
            public void set(ItemStack s) {
                if (vivant.get()) {
                    c.setItem(i, s);
                    c.setChanged();
                }
            }

            @Override
            public boolean modifiable() {
                return vivant.get();
            }

            @Override
            public boolean accepte(ItemStack s) {
                return vivant.get() && accepte.test(s);
            }
        };
    }

    static Case de(Container c, int i) {
        return de(c, i, s -> c.canPlaceItem(i, s), () -> true);
    }

    static Case decor(ItemStack affiche) {
        return new Case() {
            @Override
            public ItemStack get() {
                return affiche;
            }

            @Override
            public boolean modifiable() {
                return false;
            }
        };
    }

    static Case bouton(ItemStack affiche, Runnable action) {
        return new Case() {
            @Override
            public ItemStack get() {
                return affiche;
            }

            @Override
            public boolean modifiable() {
                return false;
            }

            @Override
            public Runnable action() {
                return action;
            }
        };
    }

    final Case[] cases = new Case[TAILLE];
    private Runnable change = () -> {
    };

    void surChangement(Runnable r) {
        this.change = r;
    }

    Case caseA(int i) {
        return i >= 0 && i < TAILLE ? this.cases[i] : null;
    }

    boolean modifiable(int i) {
        Case c = caseA(i);
        return c != null && c.modifiable();
    }

    @Override
    public int getContainerSize() {
        return TAILLE;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public ItemStack getItem(int i) {
        Case c = caseA(i);
        return c == null ? ItemStack.EMPTY : c.get();
    }

    @Override
    public ItemStack removeItem(int i, int n) {
        Case c = caseA(i);
        if (c == null || !c.modifiable()) {
            return ItemStack.EMPTY;
        }
        ItemStack s = c.get();
        if (s.isEmpty() || n <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack pris = s.split(n);
        c.set(s.isEmpty() ? ItemStack.EMPTY : s);
        setChanged();
        return pris;
    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        Case c = caseA(i);
        if (c == null || !c.modifiable()) {
            return ItemStack.EMPTY;
        }
        ItemStack s = c.get();
        c.set(ItemStack.EMPTY);
        return s;
    }

    @Override
    public void setItem(int i, ItemStack s) {
        Case c = caseA(i);
        if (c != null && c.modifiable()) {
            c.set(s);
            setChanged();
        }
    }

    @Override
    public boolean canPlaceItem(int i, ItemStack s) {
        Case c = caseA(i);
        return c != null && c.accepte(s);
    }

    @Override
    public void setChanged() {
        this.change.run();
    }

    @Override
    public boolean stillValid(Player p) {
        return true;
    }

    @Override
    public void clearContent() {
    }
}
