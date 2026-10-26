package fr.heraliz.crac.client;

import fr.heraliz.crac.placable.PlacableSet;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** A placable picked up while damaged keeps its HP: say so, since it no longer stacks with fresh ones. */
@EventBusSubscriber(
    modid = "crac",
    value = {Dist.CLIENT}
)
public final class PlacableTooltip {
    private PlacableTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        CompoundTag tag = event.getItemStack().getTag();
        if (tag != null && tag.contains(PlacableSet.ITEM_HP_TAG, Tag.TAG_INT)) {
            event.getToolTip().add(Component.translatable("crac.placable.damaged_hp", tag.getInt(PlacableSet.ITEM_HP_TAG)).withStyle(ChatFormatting.RED));
        }
    }
}
