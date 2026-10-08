package fr.heraliz.armory.item;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import fr.heraliz.armory.armor.PieceDef;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.UUID;

/**
 * Piece portee dans un slot Curios : visage (slot « mask »), gilet (« belt »), gants (« hands »), genouilleres
 * (« knees »). 0.8.2 : aucun point d'armure vanilla (protection en % : ProtectionEvents) ; seul le poids reste.
 */
public class SetCurioItem extends Item implements ICurioItem, PieceItem {
    private final PieceDef def;

    public SetCurioItem(PieceDef def, Properties properties) {
        super(properties.stacksTo(1).durability(def.durability()));
        this.def = def;
    }

    @Override
    public PieceDef def() {
        return def;
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return slotContext.identifier().equals(def.curioSlot());
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slotContext, UUID uuid, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> map = HashMultimap.create();
        if (def.speed() != 0) {
            map.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(uuid, "Armor weight", def.speed(), AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        return map;
    }

    @Override
    public ICurio.SoundInfo getEquipSound(SlotContext slotContext, ItemStack stack) {
        return new ICurio.SoundInfo(def.tier() >= 3 ? SoundEvents.ARMOR_EQUIP_IRON : SoundEvents.ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(PieceItem.nameStyle(def.tier()));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        PieceItem.appendTooltip(def, tooltip);
    }
}
