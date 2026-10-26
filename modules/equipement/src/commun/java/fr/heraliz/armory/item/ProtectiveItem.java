package fr.heraliz.armory.item;

import fr.heraliz.armory.armor.Guard;
import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

/**
 * Objet de l'Armurerie qui protege quand il est porte (0.9.0) : pieces et combinaisons (PieceItem), objets repris de
 * HeralisZ Armor (GearItem). Lu par le calcul commun des protections (armor.Protection).
 */
public interface ProtectiveItem {
    Guard guard();

    /** Slot d'armure vanilla ou l'objet protege, null pour un objet Curios. */
    @Nullable
    EquipmentSlot wornArmorSlot();

    /** Slot Curios ou l'objet protege, null pour une piece d'armure vanilla. */
    @Nullable
    String wornCurioSlot();
}
