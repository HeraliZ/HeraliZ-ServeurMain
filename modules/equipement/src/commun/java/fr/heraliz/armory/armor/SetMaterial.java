package fr.heraliz.armory.armor;

import fr.heraliz.armory.HeralizArmory;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Materiau d'armure d'une piece (une instance par piece). 0.8.2 : aucun point d'armure ni robustesse vanilla et aucune
 * reparation a l'enclume, comme HeralisZ Armor : la protection est geree en % par type de degat (ProtectionEvents).
 */
public record SetMaterial(PieceDef def) implements ArmorMaterial {
    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return def.durability();
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return 0;
    }

    @Override
    public int getEnchantmentValue() {
        return 0;
    }

    @Override
    public SoundEvent getEquipSound() {
        // combinaisons (0.9.0) : toile et caoutchouc, son du cuir
        return def.tier() >= 3 && !def.isSuit() ? SoundEvents.ARMOR_EQUIP_IRON : SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.EMPTY;
    }

    @Override
    public String getName() {
        return HeralizArmory.MOD_ID + ":" + def.set();
    }

    @Override
    public float getToughness() {
        return 0;
    }

    @Override
    public float getKnockbackResistance() {
        return 0;
    }
}
