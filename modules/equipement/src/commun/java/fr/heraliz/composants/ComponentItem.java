package fr.heraliz.composants;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ComponentItem extends Item {
    private final String[] categories;

    public ComponentItem(Properties properties, ComponentDefs.Def def) {
        super(properties);
        this.categories = def.categories().split(",");
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String prefix = "tooltip." + HeralizComposants.MODID + ".";
        if (categories[0].equals("vente")) {
            tooltip.add(Component.translatable(prefix + "sell").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            MutableComponent cats = Component.empty();
            for (int i = 0; i < categories.length; i++) {
                if (i > 0) cats.append(", ");
                cats.append(Component.translatable(prefix + "cat." + categories[i]));
            }
            tooltip.add(Component.translatable(prefix + "used_for", cats).withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltip.add(Component.translatable(getDescriptionId() + ".desc")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
