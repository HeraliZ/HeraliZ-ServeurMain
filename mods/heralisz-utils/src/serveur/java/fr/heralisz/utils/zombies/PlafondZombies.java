package fr.heralisz.utils.zombies;

import fr.heralisz.utils.HeraliszUtils;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Plafond des monstres de la carte (24/10, pour 50 joueurs) : le bukkit.yml du serveur permet 250 monstres autour de
 * CHAQUE joueur ; 50 joueurs éparpillés, c'est des milliers de zombies qui cherchent leur chemin à chaque tick (TPS en
 * chute). Une fois par seconde, les monstres de la carte sont comptés (en tout et par tronçon) ; une apparition
 * naturelle est refusée au-delà de {@code plafond_carte} (et de {@code plafond_local} autour du point, si réglé). Avec
 * peu de joueurs, rien ne change. Les apparitions voulues (vagues des drapeaux, commandes, oeufs) ne sont jamais
 * refusées. Passe avant le remplacement des zombies ({@link SpawnZombiesServeur}, qui ne remplace pas un refusé).
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID)
public final class PlafondZombies {
    private PlafondZombies() {
    }

    private static int total;
    private static Long2IntOpenHashMap parTroncon = new Long2IntOpenHashMap();
    private static boolean signale;

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD
                || sl.getGameTime() % 20 != 7) return;
        if (ZombiesConfig.PLAFOND_CARTE.get() <= 0 && ZombiesConfig.PLAFOND_LOCAL.get() <= 0) return;
        int n = 0;
        Long2IntOpenHashMap carte = new Long2IntOpenHashMap();
        for (Entity en : sl.getAllEntities()) {
            if (en.getType().getCategory() != MobCategory.MONSTER || !en.isAlive()) continue;
            n++;
            carte.addTo(ChunkPos.asLong(en.getBlockX() >> 4, en.getBlockZ() >> 4), 1);
        }
        total = n;
        parTroncon = carte;
        int plafond = ZombiesConfig.PLAFOND_CARTE.get();
        if (plafond > 0 && n >= plafond && !signale) {
            signale = true;
            HeraliszUtils.LOG.info("[zombies] plafond des monstres de la carte atteint ({} / {}) : plus d'apparition naturelle tant que ça dure",
                    n, plafond);
        } else if (plafond > 0 && n < plafond * 9 / 10) {
            signale = false;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void apparition(MobSpawnEvent.FinalizeSpawn e) {
        if (e.getSpawnType() != MobSpawnType.NATURAL || e.isSpawnCancelled()
                || e.getEntity().getType().getCategory() != MobCategory.MONSTER) return;
        if (e.getLevel().getLevel().dimension() != Level.OVERWORLD) return;
        int plafond = ZombiesConfig.PLAFOND_CARTE.get();
        if (plafond > 0 && total >= plafond) {
            e.setSpawnCancelled(true);
            return;
        }
        int local = ZombiesConfig.PLAFOND_LOCAL.get();
        if (local > 0) {
            int cx = ((int) Math.floor(e.getX())) >> 4, cz = ((int) Math.floor(e.getZ())) >> 4, c = 0;
            Long2IntOpenHashMap carte = parTroncon;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) c += carte.get(ChunkPos.asLong(cx + dx, cz + dz));
            }
            if (c >= local) {
                e.setSpawnCancelled(true);
                return;
            }
        }
        total++;   // les apparitions de la même seconde comptent aussi
    }
}
