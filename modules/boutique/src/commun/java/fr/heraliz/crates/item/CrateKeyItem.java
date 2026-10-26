package fr.heraliz.crates.item;

import fr.heraliz.crates.pont.Crochet;
import fr.heraliz.crates.crate.CrateType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Cle 3D qui ouvre un type de coffre precis.
 */
public class CrateKeyItem extends Item {
    public final CrateType type;

    public CrateKeyItem(CrateType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).withStyle(s -> s.withColor(type.glow).withBold(true));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.heralizcrates.key.opens", type.crateName()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.heralizcrates.key.use").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.heralizcrates.tier", type.tier.displayName()).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Rendu 3D dans l'inventaire : branché par la partie client (client/ClientRenderers.itemRenderer). */
    public static final Crochet<Consumer<IClientItemExtensions>> RENDU_CLIENT = new Crochet<>();

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        RENDU_CLIENT.accept(consumer);
    }
}
