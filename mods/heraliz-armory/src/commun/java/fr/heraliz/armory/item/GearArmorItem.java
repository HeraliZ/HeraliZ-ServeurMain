package fr.heraliz.armory.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.armor.GearDef;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Objet event porte sur la tete (tete de citrouille, crane, sac sur la tete...), repris tel quel de HeralisZ Armor
 * (HzArmorItem) : rendu GeckoLib (client.GearRenderer), aucun point d'armure vanilla, protection par le calcul commun.
 */
public class GearArmorItem extends ArmorItem implements GeoItem, GearItem {
    /** Aucun point d'armure vanilla : la protection est geree par type de degat (ProtectionEvents). */
    private static final ArmorMaterial CLOTH = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(Type type) {
            return 100;
        }

        @Override
        public int getDefenseForType(Type type) {
            return 0;
        }

        @Override
        public int getEnchantmentValue() {
            return 10;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_LEATHER;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }

        @Override
        public String getName() {
            return HeralizArmory.MOD_ID + ":cloth";
        }

        @Override
        public float getToughness() {
            return 0;
        }

        @Override
        public float getKnockbackResistance() {
            return 0;
        }
    };

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final GearDef gear;

    public GearArmorItem(GearDef gear) {
        super(CLOTH, Type.HELMET, new Properties().durability(gear.durability()));
        this.gear = gear;
    }

    @Override
    public GearDef gear() {
        return gear;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return ImmutableMultimap.of();
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(GearItem.rarityStyle(gear.rarity()));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        GearItem.appendTooltip(gear, lines);
        if (gear.gas()) GearItem.appendUsure(stack, lines);
    }

    /**
     * Rendu GeckoLib d'origine (client.GearRenderer), branche par la partie client (PartieClient) : Forge appelle
     * initializeClient a la creation de l'objet, apres les constructeurs @Mod.
     */
    public static volatile BiConsumer<GearArmorItem, Consumer<IClientItemExtensions>> RENDU_CLIENT = (item, consumer) -> {};

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        RENDU_CLIENT.accept(this, consumer);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
