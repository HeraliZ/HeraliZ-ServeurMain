package fr.heraliz.crac.event;

import fr.heraliz.crac.Config;
import fr.heraliz.crac.build.world.StructureBlock;
import fr.heraliz.crac.network.C2SPickup;
import fr.heraliz.crac.network.C2SPlacePlacable;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.placable.PlacableSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
    modid = "crac",
    value = {Dist.CLIENT}
)
public final class AdventureBuildHandler {
    private static long lastAction = 0L;

    private AdventureBuildHandler() {
    }

    private static long cooldownMs() {
        return Config.adventureCooldownMs.get().intValue();
    }

    @SubscribeEvent
    public static void onRightClick(RightClickBlock event) {
        Player player = event.getEntity();
        // a Dist.CLIENT subscriber also sees the integrated server's events: only the local player's own clicks count,
        // or a LAN guest's click would be sent again as the host's
        if (!event.getLevel().isClientSide() || player != Minecraft.getInstance().player) {
            return;
        }
        if (!player.getAbilities().instabuild) {
            Level level = event.getLevel();
            BlockPos pos = event.getPos();
            long now = System.currentTimeMillis();
            if (!player.isSecondaryUseActive()) {
                if (!player.getAbilities().mayBuild) {
                    ItemStack held = event.getItemStack();
                    if (held.getItem() instanceof BlockItem abi && PlacableSet.canPlaceClient(abi.getBlock())) {
                        event.setCanceled(true);
                        if (now - lastAction >= cooldownMs()) {
                            lastAction = now;
                            NetworkHandler.CHANNEL.sendToServer(new C2SPlacePlacable(event.getHand() == InteractionHand.OFF_HAND ? 1 : 0));
                        }
                    }
                }
            } else {
                BlockState target = level.getBlockState(pos);
                boolean buildPanel = target.getBlock() instanceof StructureBlock;
                if (PlacableSet.containsClient(target.getBlock()) && !buildPanel) {
                    event.setCanceled(true);
                    if (now - lastAction >= cooldownMs()) {
                        lastAction = now;
                        NetworkHandler.CHANNEL.sendToServer(new C2SPickup(pos));
                    }
                }
            }
        }
    }
}
