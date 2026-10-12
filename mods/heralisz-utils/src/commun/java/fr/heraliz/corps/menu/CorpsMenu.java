package fr.heraliz.corps.menu;

import fr.heraliz.corps.entite.CorpsContenu;
import fr.heraliz.corps.entite.CorpsEntity;
import fr.heraliz.corps.registre.Registres;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Fouille d'un corps. Les cases du corps se prennent seulement (on n'y pose rien) ; tout passe par la mecanique des menus
 * vanilla, cote serveur : le client n'envoie que des clics, le serveur verifie a chaque clic que la fouille est encore
 * valide (corps present, a portee, joueur vivant) puis renvoie l'etat exact des cases. Deux joueurs peuvent fouiller le
 * meme corps : ils partagent le meme contenu, un objet pris par l'un disparait chez l'autre.
 * <p>Actions : clic (prendre un objet), Maj + clic (dans l'inventaire), bouton « Tout prendre » ({@link #clickMenuButton}).
 */
public class CorpsMenu extends AbstractContainerMenu {
    public static final int BOUTON_TOUT_PRENDRE = 0;
    private static final ResourceLocation[] ICONES_ARMURE = {InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
            InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE, InventoryMenu.EMPTY_ARMOR_SLOT_HELMET};

    private final Container contenu;
    private final int nbCorps;
    private final int nbCurios, nbAutres;
    private final String[] curiosIds;
    private final Disposition disposition;
    private final int idEntite;
    @Nullable
    private final CorpsEntity corps;

    /** Cote client : plan recu du serveur (nombre d'accessoires et d'autres objets, emplacements Curios). */
    public static CorpsMenu client(int id, Inventory inv, FriendlyByteBuf b) {
        int idEntite = b.readVarInt();
        int nbCurios = b.readVarInt();
        int nbAutres = b.readVarInt();
        String[] ids = new String[nbCurios];
        for (int k = 0; k < nbCurios; k++) ids[k] = b.readUtf(64);
        Entity e = inv.player.level().getEntity(idEntite);
        return new CorpsMenu(id, inv, new SimpleContainer(CorpsContenu.CURIOS + nbCurios + nbAutres), nbCurios, nbAutres, ids, idEntite,
                e instanceof CorpsEntity c ? c : null);
    }

    /** Cote serveur. */
    public static CorpsMenu serveur(int id, Inventory inv, CorpsEntity corps) {
        CorpsContenu c = corps.contenu();
        String[] ids = new String[c.nbCurios()];
        for (int k = 0; k < ids.length; k++) ids[k] = c.idCurios(k);
        return new CorpsMenu(id, inv, c, c.nbCurios(), c.nbAutres(), ids, corps.getId(), corps);
    }

    /** Donnees d'ouverture envoyees au client (meme plan des deux cotes). */
    public static void ecrire(FriendlyByteBuf b, CorpsEntity corps) {
        CorpsContenu c = corps.contenu();
        b.writeVarInt(corps.getId());
        b.writeVarInt(c.nbCurios());
        b.writeVarInt(c.nbAutres());
        for (int k = 0; k < c.nbCurios(); k++) b.writeUtf(c.idCurios(k), 64);
    }

    private CorpsMenu(int id, Inventory inv, Container contenu, int nbCurios, int nbAutres, String[] curiosIds, int idEntite,
                      @Nullable CorpsEntity corps) {
        super(Registres.MENU.get(), id);
        this.contenu = contenu;
        this.nbCurios = nbCurios;
        this.nbAutres = nbAutres;
        this.curiosIds = curiosIds;
        this.idEntite = idEntite;
        this.corps = corps;
        this.disposition = new Disposition(nbCurios, nbAutres);
        this.nbCorps = contenu.getContainerSize();
        for (int i = 0; i < nbCorps; i++) {
            CaseCorps s = new CaseCorps(contenu, i, disposition.x(i, nbCurios) + 1, disposition.y(i, nbCurios) + 1);
            if (i >= CorpsContenu.ARMURE && i < CorpsContenu.ARMURE + 4) s.setBackground(InventoryMenu.BLOCK_ATLAS, ICONES_ARMURE[i - CorpsContenu.ARMURE]);
            if (i == CorpsContenu.MAIN_GAUCHE) s.setBackground(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
            addSlot(s);
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inv, 9 + r * 9 + c, Disposition.DROITE + c * Disposition.CASE + 1, disposition.yJoueur + r * Disposition.CASE + 1));
            }
        }
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, Disposition.DROITE + c * Disposition.CASE + 1, disposition.yJoueurBarre + 1));
    }

    // ------------------------------------------------------------------ acces (ecran)
    public Disposition disposition() {
        return disposition;
    }

    public int nbCorps() {
        return nbCorps;
    }

    public int nbCurios() {
        return nbCurios;
    }

    public int nbAutres() {
        return nbAutres;
    }

    /** Emplacement Curios d'une case d'accessoire (« hands », « mask »...), vide sinon. */
    public String idCurios(int index) {
        int k = index - CorpsContenu.CURIOS;
        return k >= 0 && k < curiosIds.length ? curiosIds[k] : "";
    }

    public int idEntite() {
        return idEntite;
    }

    /** Le corps fouille (cote client : peut etre null si l'entite n'est pas connue). */
    @Nullable
    public CorpsEntity corps() {
        return corps;
    }

    // ------------------------------------------------------------------ regles
    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide) return true;
        return corps != null && corps.contenu() == contenu && corps.resteAPortee(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= nbCorps) return ItemStack.EMPTY; // rien ne se pose sur un corps
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack avant = stack.copy();
        if (!moveItemStackTo(stack, nbCorps, slots.size(), false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == avant.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return avant;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return true;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BOUTON_TOUT_PRENDRE || player.level().isClientSide) return false;
        int pris = 0, restes = 0;
        // ordre : equipement, sac, accessoires, barre rapide, reste de l'inventaire, autres
        int[] ordre = ordrePrise();
        for (int index : ordre) {
            Slot slot = slots.get(index);
            if (!slot.hasItem()) continue;
            ItemStack stack = slot.getItem();
            int avant = stack.getCount();
            moveItemStackTo(stack, nbCorps, slots.size(), false);
            if (stack.getCount() != avant) pris++;
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else {
                slot.setChanged();
                restes++;
            }
        }
        if (player instanceof ServerPlayer sp) {
            if (pris > 0) sp.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.0F);
            if (restes > 0) sp.displayClientMessage(Component.translatable("message.heraliz_corps.inventaire_plein", restes), true);
        }
        return true;
    }

    private int[] ordrePrise() {
        int[] o = new int[nbCorps];
        int n = 0;
        for (int i = CorpsContenu.ARMURE; i <= CorpsContenu.SAC; i++) o[n++] = i;
        for (int i = CorpsContenu.CURIOS; i < CorpsContenu.CURIOS + nbCurios; i++) o[n++] = i;
        for (int i = 0; i < CorpsContenu.NB_INVENTAIRE; i++) o[n++] = i;
        for (int i = CorpsContenu.CURIOS + nbCurios; i < nbCorps; i++) o[n++] = i;
        return o;
    }

    /** Case du corps : on ne fait qu'y prendre. */
    static final class CaseCorps extends Slot {
        CaseCorps(Container c, int index, int x, int y) {
            super(c, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
