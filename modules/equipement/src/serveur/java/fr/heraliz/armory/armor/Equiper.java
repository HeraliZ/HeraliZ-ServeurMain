package fr.heraliz.armory.armor;

import fr.heraliz.armory.suit.SuitRules;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/** Enfile une pièce de l'Armurerie dans son emplacement (armure de Minecraft ou Curios). Fil serveur. */
public final class Equiper {
    private Equiper() {}

    /**
     * Enfile la pièce dans son slot. Refuse (faux) si la règle des combinaisons l'interdit (une combinaison est portée, ou
     * c'est une combinaison et autre chose est porté) : voir suit.SuitRules.
     */
    public static boolean piece(LivingEntity entity, PieceDef def, ItemStack stack) {
        if (def.curioSlot() != null) {
            if (!SuitRules.mayEquipCurio(entity, def.curioSlot(), stack)) return false;
            CuriosApi.getCuriosInventory(entity).ifPresent(inv -> inv.setEquippedCurio(def.curioSlot(), 0, stack));
        } else {
            if (!SuitRules.mayEquipArmor(entity, def.equipmentSlot(), stack)) return false;
            entity.setItemSlot(def.equipmentSlot(), stack);
        }
        return true;
    }

    /**
     * Enfile un objet repris de HeralisZ Armor (11/11 : ghillie de la cible d'essai) dans son emplacement Curios, ou sur
     * la tête pour un objet event. Mêmes refus que {@link #piece} (règle des combinaisons).
     */
    public static boolean objet(LivingEntity entity, GearDef g, ItemStack stack) {
        if (g.curio() != null) {
            if (!SuitRules.mayEquipCurio(entity, g.curio(), stack)) return false;
            CuriosApi.getCuriosInventory(entity).ifPresent(inv -> inv.setEquippedCurio(g.curio(), 0, stack));
        } else {
            if (!SuitRules.mayEquipArmor(entity, EquipmentSlot.HEAD, stack)) return false;
            entity.setItemSlot(EquipmentSlot.HEAD, stack);
        }
        return true;
    }
}
