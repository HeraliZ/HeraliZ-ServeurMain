package fr.heraliz.usure;

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
 * Atelier de réparation d'un kit (26/10, demande du propriétaire) : clic droit avec un kit en main, on pose l'arme ou la
 * pièce d'armure dans la case, l'écran montre de combien son état va monter, « Réparer » confirme, Échap annule. Le kit
 * utilisé est celui de la main : sa case de la barre est bloquée tant que l'atelier est ouvert. Ici, seulement la
 * disposition des cases et l'état synchronisé ({@link #donnees}) ; le calcul et la réparation sont dans la partie
 * serveur ({@code MenuReparationServeur}), qui rend l'objet posé à la fermeture.
 */
public class MenuReparation extends AbstractContainerMenu {
    /** Données synchronisées (16 bits) : états en millièmes, code du résultat, utilisations, gain du kit (dixièmes de %). */
    public static final int D_AVANT = 0, D_APRES = 1, D_MAX_AVANT = 2, D_MAX_APRES = 3, D_CODE = 4, D_RESTANTES = 5,
            D_TOTAL = 6, D_GAIN = 7, D_GENRE = 8, D_NIVEAU = 9, NB_DONNEES = 10;
    /** Codes : case vide, réparation possible, déjà en parfait état, ruinée (refusée), ne s'use pas, plus de kit en main,
     *  objet qui ne se répare pas. */
    public static final int VIDE = 0, OK = 1, PLAFOND = 2, RUINEE = 3, NE_S_USE_PAS = 4, SANS_KIT = 5, PAS_REPARABLE = 6;
    /** Genre de l'objet posé : rien, arme, pièce d'armure. */
    public static final int RIEN = 0, ARME = 1, ARMURE = 2;
    /** Bouton « Réparer » (clickMenuButton). */
    public static final int BOUTON_REPARER = 0;

    /** Disposition (points d'interface depuis le coin du panneau) : cases de 20, objet à +2, inventaire 9 x 4. */
    public static final int CASE = 20, CIBLE_X = 18, CIBLE_Y = 66, INVENTAIRE_X = 16, INVENTAIRE_Y = 148;

    protected final Container cible;
    protected final ContainerData donnees;
    /** Case de la barre qui tient le kit (bloquée). */
    protected final int caseKit;

    /** Côté joueur (IForgeMenuType) : la case du kit vient du serveur. */
    public MenuReparation(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, new SimpleContainer(1), new SimpleContainerData(NB_DONNEES), buf.readVarInt());
    }

    protected MenuReparation(int id, Inventory inv, Container cible, ContainerData donnees, int caseKit) {
        super(Contenu.MENU_REPARATION.get(), id);
        this.cible = cible;
        this.donnees = donnees;
        this.caseKit = caseKit;
        addSlot(new Slot(cible, 0, CIBLE_X + 2, CIBLE_Y + 2) {
            @Override
            public boolean mayPlace(ItemStack s) {
                return KitReparation.reparable(s);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inv, 9 + r * 9 + c, INVENTAIRE_X + c * (CASE + 2) + 2, INVENTAIRE_Y + r * (CASE + 2) + 2));
            }
        }
        for (int c = 0; c < 9; c++) {
            final boolean kit = c == caseKit;
            addSlot(new Slot(inv, c, INVENTAIRE_X + c * (CASE + 2) + 2, INVENTAIRE_Y + 3 * (CASE + 2) + 4 + 2) {
                @Override
                public boolean mayPickup(Player p) {
                    return !kit;
                }

                @Override
                public boolean mayPlace(ItemStack s) {
                    return !kit;
                }
            });
        }
        addDataSlots(donnees);
    }

    public int donnee(int i) {
        return donnees.get(i);
    }

    public int caseKit() {
        return caseKit;
    }

    /** Index du menu de la case qui tient le kit (bloquée, dessinée à part). */
    public int indexKit() {
        return 1 + 27 + caseKit;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || index == indexKit()) return ItemStack.EMPTY;
        ItemStack s = slot.getItem(), avant = s.copy();
        if (index == 0) {
            if (!moveItemStackTo(s, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!KitReparation.reparable(s) || slots.get(0).hasItem()) return ItemStack.EMPTY;
            slots.get(0).setByPlayer(s.split(1));
        }
        if (s.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (s.getCount() == avant.getCount()) return ItemStack.EMPTY;
        return avant;
    }

    @Override
    public boolean clickMenuButton(Player p, int bouton) {
        return false;   // réparation : partie serveur
    }

    @Override
    public boolean stillValid(Player p) {
        return true;
    }
}
