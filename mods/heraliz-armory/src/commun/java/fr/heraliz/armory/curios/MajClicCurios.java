package fr.heraliz.armory.curios;

import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * Maj + clic d'un objet Curios dans l'inventaire normal (touche E) : il va dans le premier slot Curios libre qui
 * l'accepte (gilet, gants, masque, genouilleres, ghillie, brassard, sac a dos...), sans ouvrir le panneau Curios
 * (demande du proprietaire, 12/10). Appele au debut de {@code InventoryMenu.quickMoveStack} par le mixin commun
 * {@code InventoryCuriosQuickMoveMixin}.
 * <ul>
 *     <li>Meme ordre que le panneau Curios : une armure va d'abord dans son slot d'armure s'il est libre ; ensuite un
 *     objet Curios va dans un slot Curios ; le reste ne change pas.</li>
 *     <li>Memes regles que si on posait l'objet a la main dans le panneau Curios : slots visibles (pas cosmetiques),
 *     validateurs et tags Curios, {@code canEquip} de l'objet, CurioEquipEvent (regle des combinaisons, SuitEvents).</li>
 *     <li>Aucun slot libre ou objet refuse : le Maj + clic normal de Minecraft (inventaire &lt;-&gt; barre d'objets).</li>
 *     <li>C'est le SERVEUR qui range ({@link #SERVEUR}, branche par MajClicCuriosServeur) : l'objet est deplace, pas
 *     copie (anti-duplication de Spartan). Le jeu du joueur ne predit rien : il laisse l'objet en place et le serveur
 *     renvoie l'etat exact (case videe, slot Curios synchronise par Curios). Sans partie serveur : comportement normal.</li>
 *     <li>Creatif : comportement normal (l'inventaire creatif est decide par le jeu du joueur).</li>
 * </ul>
 */
public final class MajClicCurios {
    private MajClicCurios() {
    }

    /** Un slot Curios libre : ses piles, son type (identifiant Curios), son numero, son rendu affiche ou non. */
    public record Cible(IDynamicStackHandler piles, String type, int index, boolean visible) {
    }

    /** Rangement reel de l'objet de la case {@code depart} dans {@code cible} ; faux s'il n'a pas pu se faire. */
    @FunctionalInterface
    public interface Rangement {
        boolean ranger(Slot depart, Player joueur, Cible cible);
    }

    /** Branche par la partie serveur (MajClicCuriosServeur::ranger) ; neutre sans elle. */
    public static volatile Rangement SERVEUR = (depart, joueur, cible) -> false;

    /** Cases de l'inventaire du joueur dans InventoryMenu : 9 a 35 (inventaire), 36 a 44 (barre d'objets). */
    private static final int DEBUT = 9, FIN = 45;

    /**
     * Maj + clic sur la case {@code index} : vrai si l'objet part (ou va partir, cote joueur) dans un slot Curios, le
     * quickMoveStack de Minecraft ne doit alors rien faire ; faux pour le comportement normal.
     */
    public static boolean majClic(InventoryMenu menu, Player joueur, int index) {
        if (index < DEBUT || index >= FIN || index >= menu.slots.size()) return false;
        if (joueur.getAbilities().instabuild) return false;
        Slot slot = menu.slots.get(index);
        if (!slot.hasItem()) return false;
        ItemStack objet = slot.getItem();
        // une armure va d'abord dans son slot d'armure s'il est libre (Minecraft, panneau Curios)
        EquipmentSlot equipement = Mob.getEquipmentSlotForItem(objet);
        if (equipement.getType() == EquipmentSlot.Type.ARMOR && !menu.slots.get(8 - equipement.getIndex()).hasItem()) return false;
        Cible cible = cible(joueur, objet);
        if (cible == null) return false;
        if (joueur.level().isClientSide) return true;
        return SERVEUR.ranger(slot, joueur, cible);
    }

    /** Premier slot Curios libre, visible et non cosmetique, qui accepte l'objet (null : aucun, ou pas un objet Curios). */
    @Nullable
    public static Cible cible(Player joueur, ItemStack objet) {
        // types de slots prevus pour cet objet (validateurs, tags) : les autres ne sont pas interroges, pour que la
        // regle des combinaisons n'explique pas un refus sans rapport (CurioEquipEvent de SuitEvents)
        Map<String, ISlotType> types = CuriosApi.getItemStackSlots(objet, joueur);
        if (types.isEmpty()) return null;
        ICuriosItemHandler inventaire = CuriosApi.getCuriosInventory(joueur).resolve().orElse(null);
        if (inventaire == null) return null;
        for (Map.Entry<String, ICurioStacksHandler> e : inventaire.getCurios().entrySet()) {
            ICurioStacksHandler slots = e.getValue();
            if (!types.containsKey(e.getKey()) || !slots.isVisible()) continue;
            IDynamicStackHandler piles = slots.getStacks();
            NonNullList<Boolean> rendus = slots.getRenders();
            for (int i = 0; i < piles.getSlots(); i++) {
                if (!piles.getStackInSlot(i).isEmpty() || !piles.isItemValid(i, objet)) continue;
                return new Cible(piles, e.getKey(), i, rendus.size() > i && rendus.get(i));
            }
        }
        return null;
    }
}
