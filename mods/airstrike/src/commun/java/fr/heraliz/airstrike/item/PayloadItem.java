package fr.heraliz.airstrike.item;

import fr.heraliz.airstrike.strike.StrikeType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Strike authorisation module ("recharge"). One is consumed each time the designator calls a strike of its type.
 */
public class PayloadItem extends Item {
    private final StrikeType type;

    public PayloadItem(StrikeType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public StrikeType getStrikeType() {
        return type;
    }

    /**
     * Counts the payload items of a type carried by the player (read by the client wheel and by the server). Moved
     * here from {@code DesignatorLogic} (server part), which keeps a method of the same name calling this one.
     */
    public static int countPayload(Inventory inv, StrikeType type) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof PayloadItem p && p.getStrikeType() == type) n += s.getCount();
        }
        return n;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(type.uiColor)));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(type.descriptionKey()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.airstrike.payload.usage").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
