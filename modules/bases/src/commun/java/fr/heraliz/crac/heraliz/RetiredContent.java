package fr.heraliz.crac.heraliz;

import java.util.Set;
import fr.heraliz.crac.Crac;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;

/**
 * Content removed in 1.0.2-beta (HeraliZ: no sleeping bags, the server keeps its base teleport and vanilla beds). A world
 * that knew them loads without the "missing registry entries" prompt: the bags vanish (air), their items and block
 * entities are dropped, the sound is forgotten.
 */
@Mod.EventBusSubscriber(modid = Crac.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RetiredContent {
    private static final Set<String> RETIRED = Set.of("straw_bed", "bag_place");

    private RetiredContent() {
    }

    @SubscribeEvent
    public static void onMissingMappings(MissingMappingsEvent event) {
        for (var mapping : event.getMappings(ForgeRegistries.Keys.BLOCKS, Crac.MODID)) {
            if (RETIRED.contains(mapping.getKey().getPath())) mapping.ignore();
        }
        for (var mapping : event.getMappings(ForgeRegistries.Keys.ITEMS, Crac.MODID)) {
            if (RETIRED.contains(mapping.getKey().getPath())) mapping.ignore();
        }
        for (var mapping : event.getMappings(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES, Crac.MODID)) {
            if (RETIRED.contains(mapping.getKey().getPath())) mapping.ignore();
        }
        for (var mapping : event.getMappings(ForgeRegistries.Keys.SOUND_EVENTS, Crac.MODID)) {
            if (RETIRED.contains(mapping.getKey().getPath())) mapping.ignore();
        }
    }
}
