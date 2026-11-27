package fr.heraliz.vehicules;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import java.util.ArrayList;
import java.util.List;

/**
 * Contenu du coffre HeraliZ d'un véhicule ({@link CoffresVehicules}) : {@value #CASES_MAX} cases gardées quel que soit le
 * véhicule (la taille montrée vient du réglage {@code [coffre] tailles}), sauvegardées avec l'entité (données « ForgeCaps »
 * du véhicule, par {@link Fournisseur}) : le contenu suit le véhicule partout (tronçon déchargé, changement de monde,
 * {@code /ywzj_vehicle reload}), jamais copié à part.
 * <p>Jamais exposé aux entonnoirs ni aux autres mods (aucune capacité rendue) ; seul le menu du coffre y touche.
 */
public final class CoffreVehicule extends SimpleContainer {
    /** Cases gardées : 24 colonnes x 9 lignes au plus. */
    public static final int CASES_MAX = 216;

    /** Écrans ouverts sur ce coffre (pour ne chercher les joueurs à fermer que s'il y en a). */
    private int ouverts;

    CoffreVehicule() {
        super(CASES_MAX);
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return !CoffresVehicules.interdit(stack);
    }

    @Override
    public void startOpen(Player joueur) {
        ouverts++;
    }

    @Override
    public void stopOpen(Player joueur) {
        if (ouverts > 0) ouverts--;
    }

    int ouverts() {
        return ouverts;
    }

    boolean vide() {
        for (int i = 0; i < getContainerSize(); i++) {
            if (!getItem(i).isEmpty()) return false;
        }
        return true;
    }

    /** Sort tout : chaque case est vidée et ses objets sont rendus (les mêmes, jamais des copies). */
    List<ItemStack> sortir() {
        List<ItemStack> objets = new ArrayList<>();
        for (int i = 0; i < getContainerSize(); i++) {
            ItemStack s = removeItemNoUpdate(i);
            if (!s.isEmpty()) objets.add(s);
        }
        if (!objets.isEmpty()) setChanged();
        return objets;
    }

    CompoundTag ecrire() {
        CompoundTag racine = new CompoundTag();
        ListTag l = new ListTag();
        for (int i = 0; i < getContainerSize(); i++) {
            ItemStack s = getItem(i);
            if (s.isEmpty()) continue;
            CompoundTag t = s.save(new CompoundTag());
            t.putInt("Case", i);
            l.add(t);
        }
        racine.put("Objets", l);
        return racine;
    }

    void lire(CompoundTag racine) {
        for (int i = 0; i < getContainerSize(); i++) setItem(i, ItemStack.EMPTY);
        ListTag l = racine.getList("Objets", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            int k = t.getInt("Case");
            if (k < 0 || k >= getContainerSize()) {
                HeralizVehicules.LOG.warn("[vehicules] coffre : case {} hors du coffre ignoree ({})", k, t);
                continue;
            }
            setItem(k, ItemStack.of(t));
        }
    }

    /** Attaché à chaque véhicule (serveur) : sauvegarde seulement, aucune capacité rendue aux autres mods. */
    static final class Fournisseur implements ICapabilitySerializable<CompoundTag> {
        final CoffreVehicule coffre = new CoffreVehicule();

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction cote) {
            return LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return coffre.ecrire();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            coffre.lire(tag);
        }
    }
}
