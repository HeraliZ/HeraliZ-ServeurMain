package fr.heraliz.casino.item;

import fr.heraliz.casino.pont.Crochet;
import fr.heraliz.casino.decor.DecorType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Objet servant a poser un element de decor (rendu 3D dans l'inventaire).
 */
public class DecorItem extends BlockItem {
    public final DecorType type;

    public DecorItem(Block block, DecorType type, Properties properties) {
        super(block, properties);
        this.type = type;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).withStyle(s -> s.withColor(type.glow).withBold(true));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(type.description().withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.heralizcasino.decor." + type.place.name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.DARK_GRAY));
        if (type.door()) {
            tooltip.add(Component.translatable("tooltip.heralizcasino.decor.door").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.heralizcasino.decor.door_wall").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (type.seated()) {
            tooltip.add(Component.translatable("tooltip.heralizcasino.decor.seat").withStyle(ChatFormatting.GOLD));
            if (type.modes > 1) tooltip.add(Component.translatable("tooltip.heralizcasino.decor.seat_modes", type.modes).withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (type.flicker()) tooltip.add(Component.translatable("tooltip.heralizcasino.decor.flicker").withStyle(ChatFormatting.GOLD));
        if (!type.interactive()) return;
        String use = type.linked() ? "chain" : type.modes > 1 ? (type.switchable() ? "modes" : "pages") : "switch";
        tooltip.add(Component.translatable("tooltip.heralizcasino.decor." + use, type.modes).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Rendu 3D dans l'inventaire : branche par la partie client (Forge l'appelle a la creation de l'objet). */
    public static final Crochet<Consumer<IClientItemExtensions>> RENDU_CLIENT = new Crochet<>();

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        RENDU_CLIENT.accept(consumer);
    }
}
