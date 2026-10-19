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
        // d'abord le point /spawn posé par le staff (/spawn definir de CRac, par réflexion : facultatif), puis le point de
        // réveil du monde du spawn de HeraliZ (Utils, par réflexion : facultatif), s'il est en place
        if (ExtractionConfig.USE_HERALIZ_SPAWN.get() && (versSpawnDefini(player) || versSpawnHeraliz(player))) {
            return;
        }
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

    private static java.lang.reflect.Method spawnCrac;
    private static boolean spawnCracCherche;

    /**
     * CRac : fr.heraliz.crac.api.SpawnCracApi.allerAuSpawnDefini(ServerPlayer) (point posé par /spawn definir, vraie
     * téléportation). Faux si CRac est absent, trop ancien, ou si aucun point n'est défini.
     */
    private static boolean versSpawnDefini(ServerPlayer player) {
        if (!spawnCracCherche) {
            spawnCracCherche = true;
            try {
                spawnCrac = Class.forName("fr.heraliz.crac.api.SpawnCracApi").getMethod("allerAuSpawnDefini", ServerPlayer.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                spawnCrac = null;
            }
        }
        if (spawnCrac == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(spawnCrac.invoke(null, player));
        } catch (ReflectiveOperationException | RuntimeException e) {
            com.mojang.logging.LogUtils.getLogger().warn("[extraction] retour au point /spawn de CRac impossible : {}", e.toString());
            return false;
        }
    }

    private static java.lang.reflect.Method spawnApi;
    private static boolean spawnApiCherchee;

    /**
     * HeraliZ Utils : fr.heralisz.utils.api.SpawnApi.allerAuSpawn(ServerPlayer) (point de réveil du monde du spawn, vraie
     * téléportation). Faux si Utils est absent, trop ancien, ou si le monde du spawn n'est pas en place.
     */
    private static boolean versSpawnHeraliz(ServerPlayer player) {
        if (!spawnApiCherchee) {
            spawnApiCherchee = true;
            try {
                spawnApi = Class.forName("fr.heralisz.utils.api.SpawnApi").getMethod("allerAuSpawn", ServerPlayer.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                spawnApi = null;
            }
        }
        if (spawnApi == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(spawnApi.invoke(null, player));
        } catch (ReflectiveOperationException | RuntimeException e) {
            com.mojang.logging.LogUtils.getLogger().warn("[extraction] retour au spawn de HeraliZ impossible : {}", e.toString());
            return false;
        }
    }
}
