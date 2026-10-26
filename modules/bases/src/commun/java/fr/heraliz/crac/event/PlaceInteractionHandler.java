package fr.heraliz.crac.event;

import fr.heraliz.crac.item.BuildingHammerItem;
import fr.heraliz.crac.item.BuildingPlanItem;
import fr.heraliz.crac.placable.PlacableSet;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
    modid = "crac"
)
public final class PlaceInteractionHandler {
    private PlaceInteractionHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof BuildingPlanItem
            || stack.getItem() instanceof BuildingHammerItem
            || stack.getItem() instanceof BlockItem pbi && PlacableSet.contains(event.getLevel(), pbi.getBlock())) {
            event.setUseBlock(Result.DENY);
        }
    }
}
