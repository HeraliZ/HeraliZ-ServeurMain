package com.heraliz.extraction.serveur;

import com.heraliz.extraction.ExtractionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Point d'arrivée d'un joueur extrait (réglages « destination » de config/heraliz_extraction-common.toml). */
public final class Destination {
    private Destination() {
    }

    /** Téléporte le joueur au point d'extraction configuré (vraie téléportation : suivie par l'anticheat). */
    public static void teleport(ServerPlayer player) {
        MinecraftServer server = player.server;
        ServerLevel target = server.overworld();
        double x;
        double y;
        double z;

        if (ExtractionConfig.USE_WORLD_SPAWN.get()) {
            BlockPos spawn = target.getSharedSpawnPos();
            x = spawn.getX() + 0.5D;
            y = spawn.getY();
            z = spawn.getZ() + 0.5D;
        } else {
            ResourceLocation id = ResourceLocation.tryParse(ExtractionConfig.SPAWN_DIMENSION.get());
            if (id != null) {
                ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
                if (level != null) {
                    target = level;
                }
            }
            x = ExtractionConfig.SPAWN_X.get();
            y = ExtractionConfig.SPAWN_Y.get();
            z = ExtractionConfig.SPAWN_Z.get();
        }

        player.stopRiding();
        player.teleportTo(target, x, y, z, player.getYRot(), player.getXRot());
        player.resetFallDistance();
    }
}
