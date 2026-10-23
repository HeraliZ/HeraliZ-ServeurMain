package fr.heraliz.armory.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import fr.heraliz.armory.armor.ArmorConfig;
import fr.heraliz.armory.armor.PieceDef;
import fr.heraliz.armory.suit.SuitRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Combinaison integrale (0.9.0) : un seul objet porte au torse qui couvre tout le corps (modele armor/<ensemble>/suit.json,
 * rendu par SetArmorLayer avec toutes les parties visibles). Elle se porte SEULE : voir {@link SuitRules}.
 * Tenue de plongee : en plus, palmes (nage +80 % par defaut, config) ; le -6 % a terre est le poids de la piece
 * (PieceDef.speed) ; bouteille d'air : suit.Diving.
 */
public class SuitItem extends SetArmorItem {
    private static final UUID FINS_UUID = UUID.fromString("8c2b6a41-5f0d-4e7a-9b1c-3d4e5f6a0002");

    public SuitItem(PieceDef def, Properties properties) {
        super(def, properties);
    }

    /** Slot d'armure de l'inventaire (et mannequin) : seulement au torse, et seulement si rien d'autre n'est porte. */
    @Override
    public boolean canEquip(ItemStack stack, EquipmentSlot armorType, Entity entity) {
        return armorType == EquipmentSlot.CHEST && (!(entity instanceof LivingEntity living) || SuitRules.canWearSuit(living));
    }

    /** Clic droit : enfile la combinaison, sauf si autre chose est porte (message). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!SuitRules.canWearSuit(player)) {
            SuitRules.tell(player, false);
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> base = super.getAttributeModifiers(slot, stack);
        if (slot != EquipmentSlot.CHEST || !def().diving()) return base;
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.putAll(base);
        b.put(ForgeMod.SWIM_SPEED.get(), new AttributeModifier(FINS_UUID, "Palmes", ArmorConfig.SWIM_BOOST.get(), AttributeModifier.Operation.MULTIPLY_BASE));
        return b.build();
    }

    /** Infobulle au format de l'Armurerie : combinaison, « se porte seule », protections, bonus. */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        PieceDef def = def();
        tooltip.add(GearItem.rarityLine(PieceItem.rarity(def.tier())));
        tooltip.add(Component.translatable("tooltip.heraliz_armory.suit").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.heraliz_armory.suit.alone").withStyle(ChatFormatting.YELLOW));
        tooltip.add(PieceItem.protection(def.bullet()));
        tooltip.add(Component.translatable("tooltip.heraliz_armory.zone.all").withStyle(ChatFormatting.DARK_GRAY));
        if (def.gas()) tooltip.add(Component.translatable("tooltip.heraliz_armory.gas").withStyle(ChatFormatting.AQUA));
        if (def.radiation() >= 100) tooltip.add(Component.translatable("tooltip.heraliz_armory.radiation_proof").withStyle(ChatFormatting.AQUA));
        if (def.cold()) tooltip.add(Component.translatable("tooltip.heraliz_armory.cold").withStyle(ChatFormatting.AQUA));
        if (def.gas() || def.radiation() > 0 || def.cold()) GearItem.appendUsure(stack, tooltip);
        if (def.diving()) {
            tooltip.add(Component.translatable("tooltip.heraliz_armory.tank", ArmorConfig.TANK_SECONDS.get()).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.heraliz_armory.fins", Math.round(ArmorConfig.SWIM_BOOST.get() * 100.0))
                    .withStyle(ChatFormatting.BLUE));
        }
    }
}
