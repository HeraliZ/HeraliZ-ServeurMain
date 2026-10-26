package fr.heraliz.crac.event;

import fr.heraliz.crac.item.BuildingHammerItem;
import fr.heraliz.crac.item.BuildingPlanItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
    modid = "crac"
)
public final class HammerAttackBlocker {
    private HammerAttackBlocker() {
    }

    @SubscribeEvent
    public static void onLeftClickBlock(LeftClickBlock event) {
        if (holdingHammer(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (holdingHammer(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Main hand only, like the plan and hammer controllers: a tool in the offhand must not disable melee. */
    private static boolean holdingHammer(Player player) {
        if (player == null) {
            return false;
        }
        ItemStack s = player.getMainHandItem();
        return s.getItem() instanceof BuildingHammerItem || s.getItem() instanceof BuildingPlanItem;
    }
}
