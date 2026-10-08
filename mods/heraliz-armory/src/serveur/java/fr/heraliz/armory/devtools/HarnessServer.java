package fr.heraliz.armory.devtools;

import fr.heraliz.armory.HeralizArmory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Harnais (dev, -Dheraliz.armory.harness=true) : petite scene neutre (dalle + mur de fond), joueur op en creatif. */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID)
public final class HarnessServer {
    private HarnessServer() {}

    public static boolean enabled() {
        return Boolean.getBoolean("heraliz.armory.harness") || cinema();
    }

    /** Tournage video : decor plus riche (voir CinemaScene). */
    public static boolean cinema() {
        return !System.getProperty("heraliz.armory.cinema", "").isEmpty();
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        if (!enabled()) return;
        ServerLevel level = event.getServer().overworld();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
        level.setDayTime(6000);
        level.setWeatherParameters(24000 * 10, 0, false, false);
        level.setDefaultSpawnPos(new BlockPos(0, y, 0), 0);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, event.getServer());
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 40; z++) {
                Block b = ((x + z) & 1) == 0 ? Blocks.SMOOTH_STONE : Blocks.POLISHED_ANDESITE;
                level.setBlock(new BlockPos(x, y - 1, z), b.defaultBlockState(), 3);
                for (int h = 0; h < 6; h++) level.setBlock(new BlockPos(x, y + h, z), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        for (int x = -12; x <= 12; x++) {
            for (int h = 0; h < 6; h++) level.setBlock(new BlockPos(x, y + h, -13), Blocks.STONE_BRICKS.defaultBlockState(), 3);
        }
        if (cinema()) CinemaScene.build(level, y);
        HeralizArmory.LOGGER.info("[harness] scene construite a y={}", y);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        server.getPlayerList().op(player.getGameProfile());
        player.setGameMode(GameType.CREATIVE);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!enabled()) return;
        MinecraftServer server = event.getEntity().getServer();
        if (server != null) server.halt(false);
    }
}
