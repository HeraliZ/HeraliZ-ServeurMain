package fr.heraliz.crac.build.world;

import java.nio.file.Files;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.data.SafeSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge bus hooks of the building system. */
@Mod.EventBusSubscriber(modid = "crac", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StructureEvents {
    /** Saved data of the old building system, which is not converted (v2 needs a fresh world). */
    private static final String V1_DATA = "crac_structures";

    private StructureEvents() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel sl) {
            StructureService.tick(sl);
        }
    }

    /** Store-side integrity check of the pieces in a chunk that just loaded (see {@link StructureIntegrity}). */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel sl) {
            StructureIntegrity.queueChunk(sl, event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        if (Files.exists(SafeSavedData.dataFile(overworld, V1_DATA))) {
            Crac.LOGGER.warn("[CRac buildings] This world holds bases of the old building system ({}.dat). They are not converted:"
                + " the new building system needs a fresh world.", V1_DATA);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        StructureIntegrity.clear();
        // la dernière sauvegarde des bases est écrite en fond (25/10) : elle finit avant que le serveur ne s'arrête
        SafeSavedData.attendreEcritures();
    }

    /** Left click on a twig piece damages it, Rust style. Other tiers need explosives. */
    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide() || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(level.getBlockState(event.getPos()).getBlock() instanceof StructureBlock)) {
            return;
        }
        float strength = player.getAttackStrengthScale(0.5F);
        StructureService.meleeHit(level, player, event.getPos(), player.pick(6.0, 1.0F, false).getLocation(), strength);
        player.resetAttackStrengthTicker();
    }
}
