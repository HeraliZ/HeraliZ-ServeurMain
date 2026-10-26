package fr.heraliz.corps.entite;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Ce que porte un corps, case par case, a la place exacte qu'il avait sur le joueur :
 * <ul>
 *     <li>{@code 0..35} : inventaire (memes indices que {@code Inventory.items} : 0-8 barre rapide, 9-35 le reste) ;</li>
 *     <li>{@code 36..39} : armure (pieds, jambes, torse, tete, comme {@code Inventory.armor}) ; {@code 40} : main gauche ;</li>
 *     <li>{@code 41} : le sac equipe (emplacement Curios « back »), objet entier avec son contenu dans son NBT ;</li>
 *     <li>{@code 42..} : les accessoires Curios portes (masque, gilet, gants, genouilleres...), seulement ceux qui etaient
 *     pleins, avec l'identifiant de leur emplacement ;</li>
 *     <li>puis les « autres » : objets laches par un autre mod a la mort, places dans la section inventaire.</li>
 * </ul>
 * Le conteneur ne prend jamais rien ({@link #canPlaceItem} faux) : on ne fait qu'y prendre. Sauvegarde : seules les cases
 * pleines.
 */
public final class CorpsContenu implements Container {
    public static final int INVENTAIRE = 0, NB_INVENTAIRE = 36;
    public static final int ARMURE = 36;
    public static final int MAIN_GAUCHE = 40;
    public static final int SAC = 41;
    public static final int CURIOS = 42;

    private final NonNullList<ItemStack> objets;
    private final String[] curiosIds;
    private final int nbAutres;
    @Nullable
    private Runnable surChangement;

    public CorpsContenu(int nbCurios, int nbAutres) {
        this.curiosIds = new String[Math.max(0, nbCurios)];
        java.util.Arrays.fill(curiosIds, "");
        this.nbAutres = Math.max(0, nbAutres);
        this.objets = NonNullList.withSize(CURIOS + curiosIds.length + this.nbAutres, ItemStack.EMPTY);
    }

    public void surChangement(@Nullable Runnable r) {
        this.surChangement = r;
    }

    public int nbCurios() {
        return curiosIds.length;
    }

    public int nbAutres() {
        return nbAutres;
    }

    /** Premiere case des « autres » objets. */
    public int debutAutres() {
        return CURIOS + curiosIds.length;
    }

    public String idCurios(int k) {
        return k >= 0 && k < curiosIds.length ? curiosIds[k] : "";
    }

    public void definirIdCurios(int k, String id) {
        if (k >= 0 && k < curiosIds.length) curiosIds[k] = id == null ? "" : id;
    }

    /** Remplissage a la creation (pas de rappel de changement). */
    public void poser(int index, ItemStack stack) {
        objets.set(index, stack);
    }

    /** Nombre de cases pleines. */
    public int nbObjets() {
        int n = 0;
        for (ItemStack s : objets) if (!s.isEmpty()) n++;
        return n;
    }

    // ------------------------------------------------------------------ Container
    @Override
    public int getContainerSize() {
        return objets.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : objets) if (!s.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return index >= 0 && index < objets.size() ? objets.get(index) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack s = ContainerHelper.removeItem(objets, index, count);
        if (!s.isEmpty()) setChanged();
        return s;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        return ContainerHelper.takeItem(objets, index);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        if (index < 0 || index >= objets.size()) return;
        objets.set(index, stack);
        setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public void setChanged() {
        if (surChangement != null) surChangement.run();
    }

    /** La fouille elle-meme verifie la portee (menu) ; le conteneur seul ne sert a personne d'autre. */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return false;
    }

    @Override
    public void clearContent() {
        objets.clear();
        setChanged();
    }

    // ------------------------------------------------------------------ sauvegarde
    public CompoundTag sauver() {
        CompoundTag t = new CompoundTag();
        t.putInt("NbCurios", curiosIds.length);
        t.putInt("NbAutres", nbAutres);
        ListTag ids = new ListTag();
        for (String id : curiosIds) ids.add(StringTag.valueOf(id));
        t.put("CuriosIds", ids);
        ListTag l = new ListTag();
        for (int i = 0; i < objets.size(); i++) {
            ItemStack s = objets.get(i);
            if (s.isEmpty()) continue;
            CompoundTag o = new CompoundTag();
            o.putShort("Case", (short) i);
            s.save(o);
            l.add(o);
        }
        t.put("Objets", l);
        return t;
    }

    public static CorpsContenu charger(CompoundTag t) {
        CorpsContenu c = new CorpsContenu(t.getInt("NbCurios"), t.getInt("NbAutres"));
        ListTag ids = t.getList("CuriosIds", Tag.TAG_STRING);
        for (int k = 0; k < ids.size() && k < c.curiosIds.length; k++) c.curiosIds[k] = ids.getString(k);
        ListTag l = t.getList("Objets", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag o = l.getCompound(i);
            int index = o.getShort("Case");
            if (index >= 0 && index < c.objets.size()) c.objets.set(index, ItemStack.of(o));
        }
        return c;
    }
}
