package fr.heraliz.crac.heraliz;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * HeraliZ: in the dimensions of {@code heraliz.protectedDimensions} (the map while it is being built), no explosion
 * breaks a block, whatever caused it. Runs last, after CRac's own handling (C4 damage to pieces) and the other mods,
 * so nothing can add blocks back to the list. Blasts still hurt entities.
 */
@EventBusSubscriber(modid = "crac")
public final class MapProtection {
    private MapProtection() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level && Heraliz.protectedMap(level)) {
            event.getAffectedBlocks().clear();
        }
    }
}
