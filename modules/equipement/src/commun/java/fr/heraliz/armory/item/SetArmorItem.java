package fr.heraliz.armory.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.armor.PieceDef;
import fr.heraliz.armory.armor.SetMaterial;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Piece portee dans un slot d'armure vanilla (tete, torse, jambes, pieds), rendue avec son modele en relief.
 * 0.8.2 : aucun point d'armure vanilla (protection en % : ProtectionEvents) ; seul le poids (vitesse) reste un attribut.
 */
public class SetArmorItem extends ArmorItem implements PieceItem {
    private static final UUID[] SPEED_UUID = {
            UUID.fromString("6f3b2a10-3c1e-4d55-9a61-7b1f0e8a0001"), UUID.fromString("6f3b2a10-3c1e-4d55-9a61-7b1f0e8a0002"),
            UUID.fromString("6f3b2a10-3c1e-4d55-9a61-7b1f0e8a0003"), UUID.fromString("6f3b2a10-3c1e-4d55-9a61-7b1f0e8a0004")};

    private final PieceDef def;
    private final Multimap<Attribute, AttributeModifier> modifiers;

    public SetArmorItem(PieceDef def, Properties properties) {
        super(new SetMaterial(def), def.armorType(), properties);
        this.def = def;
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        if (def.speed() != 0) {
            b.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(SPEED_UUID[def.armorType().ordinal()], "Armor weight",
                    def.speed(), AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        this.modifiers = b.build();
    }

    @Override
    public PieceDef def() {
        return def;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == def.equipmentSlot() ? modifiers : ImmutableMultimap.of();
    }

    @Override
    public @Nullable String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return HeralizArmory.MOD_ID + ":" + def.texturePath();
    }

    /** Modele en relief (client.ClientArmor.extensions), branche par la partie client (PartieClient). */
    public static volatile BiConsumer<SetArmorItem, Consumer<IClientItemExtensions>> RENDU_CLIENT = (item, consumer) -> {};

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        // appele par le constructeur d'Item, avant l'affectation de def : la definition est lue au moment du rendu
        RENDU_CLIENT.accept(this, consumer);
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
