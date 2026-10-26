package fr.heraliz.armory.curios;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

/**
 * Partie serveur du Maj + clic vers les slots Curios ({@link MajClicCurios}) : range l'objet comme le clic droit
 * d'equipement de Curios (pile posee dans le slot, son d'equipement). L'objet est DEPLACE : la pile posee remplace
 * l'objet de la case, videe dans le meme appel (son numero anti-duplication de Spartan le suit, il n'existe qu'a un
 * endroit). Curios synchronise ensuite le slot chez le joueur et ceux qui le voient (bonus et poids de la piece
 * appliques par Curios au tick suivant, comme pour un objet pose a la main).
 */
public final class MajClicCuriosServeur {
    private MajClicCuriosServeur() {
    }

    public static boolean ranger(Slot depart, Player joueur, MajClicCurios.Cible cible) {
        ItemStack objet = depart.getItem();
        if (objet.isEmpty() || !cible.piles().getStackInSlot(cible.index()).isEmpty()) return false;
        int n = Math.min(objet.getCount(), Math.min(cible.piles().getSlotLimit(cible.index()), objet.getMaxStackSize()));
        if (n <= 0) return false;
        ItemStack pose = ItemHandlerHelper.copyStackWithSize(objet, n);
        cible.piles().setStackInSlot(cible.index(), pose);
        objet.shrink(n);
        if (objet.isEmpty()) depart.set(ItemStack.EMPTY);
        else depart.setChanged();
        SlotContext contexte = new SlotContext(cible.type(), joueur, cible.index(), false, cible.visible());
        CuriosApi.getCurio(pose).ifPresent(curio -> curio.onEquipFromUse(contexte));
        return true;
    }
}
