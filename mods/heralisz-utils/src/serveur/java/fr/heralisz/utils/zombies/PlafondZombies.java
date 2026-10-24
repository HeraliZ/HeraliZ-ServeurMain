package fr.heralisz.utils.zombies;

import fr.heralisz.utils.HeraliszUtils;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
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
 * <p>
 * 24/10 : les mêmes plafonds sont vérifiés dès les règles d'apparition ({@link #regles}), AVANT que Minecraft crée le
 * monstre. Le bukkit.yml (250 monstres par joueur, un essai toutes les 3 ticks) fait que Minecraft essaie d'en faire
 * apparaître dans chaque tronçon proche d'un joueur sans jamais atteindre sa propre limite ; au plafond, chaque essai
 * créait un zombie complet (objectifs, navigation, attributs, passage par Zombie Awareness) aussitôt jeté.
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID)
public final class PlafondZombies {
    private PlafondZombies() {
    }

    private static int total;
    private static Long2IntOpenHashMap parTroncon = new Long2IntOpenHashMap();
    /** Zombies HeraliZ par tronçon (même relevé), lus par le peuplement ({@link Peuplement}). */
    private static Long2IntOpenHashMap zombiesParTroncon = new Long2IntOpenHashMap();
    private static boolean signale;

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD
                || sl.getGameTime() % 20 != 7) return;
        if (ZombiesConfig.PLAFOND_CARTE.get() <= 0 && ZombiesConfig.PLAFOND_LOCAL.get() <= 0 && !ZombiesConfig.PEUPLEMENT.get()) return;
        int n = 0;
        Long2IntOpenHashMap carte = new Long2IntOpenHashMap(), zombies = new Long2IntOpenHashMap();
        for (Entity en : sl.getAllEntities()) {
            if (en.getType().getCategory() != MobCategory.MONSTER || !en.isAlive()) continue;
            n++;
            long k = ChunkPos.asLong(en.getBlockX() >> 4, en.getBlockZ() >> 4);
            carte.addTo(k, 1);
            if (en instanceof ZombieSurvie) zombies.addTo(k, 1);
        }
        total = n;
        parTroncon = carte;
        zombiesParTroncon = zombies;
        int plafond = ZombiesConfig.PLAFOND_CARTE.get();
        if (plafond > 0 && n >= plafond && !signale) {
            signale = true;
            HeraliszUtils.LOG.info("[zombies] plafond des monstres de la carte atteint ({} / {}) : plus d'apparition naturelle tant que ça dure",
                    n, plafond);
        } else if (plafond > 0 && n < plafond * 9 / 10) {
            signale = false;
        }
    }

    /** Monstres de la carte au dernier relevé (plus les apparitions comptées depuis). */
    static int total() {
        return total;
    }

    /** Zombies HeraliZ autour de (x, z), à rayon blocs environ (tronçons dont le centre est à moins de rayon + 11). */
    static int zombiesAutour(double x, double z, double rayon) {
        Long2IntOpenHashMap carte = zombiesParTroncon;
        int cx = Mth.floor(x) >> 4, cz = Mth.floor(z) >> 4, r = (int) Math.ceil(rayon / 16.0), c = 0;
        double lim = (rayon + 11.4) * (rayon + 11.4);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double ox = ((cx + dx) << 4) + 8 - x, oz = ((cz + dz) << 4) + 8 - z;
                if (ox * ox + oz * oz <= lim) c += carte.get(ChunkPos.asLong(cx + dx, cz + dz));
            }
        }
        return c;
    }

    /** Un zombie HeraliZ vient d'apparaître ici (peuplement) : compté tout de suite, sans attendre le relevé suivant. */
    static void compter(double x, double z) {
        total++;
        long k = ChunkPos.asLong(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
        parTroncon.addTo(k, 1);
        zombiesParTroncon.addTo(k, 1);
    }

    /** Règles d'apparition (avant la création du monstre) : refus au plafond, même règle que {@link #apparition}. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void regles(MobSpawnEvent.SpawnPlacementCheck e) {
        if (e.getSpawnType() != MobSpawnType.NATURAL || e.getResult() == Event.Result.DENY
                || e.getEntityType().getCategory() != MobCategory.MONSTER) return;
        if (e.getLevel().getLevel().dimension() != Level.OVERWORLD || !ZombiesConfig.PLAFOND_AVANT.get()) return;
        if (auPlafond(e.getPos().getX(), e.getPos().getZ())) e.setResult(Event.Result.DENY);
    }

    /** Plafond de la carte atteint, ou plafond local autour de (x, z) (5 x 5 tronçons). */
    private static boolean auPlafond(double x, double z) {
        int plafond = ZombiesConfig.PLAFOND_CARTE.get();
        if (plafond > 0 && total >= plafond) return true;
        int local = ZombiesConfig.PLAFOND_LOCAL.get();
        if (local <= 0) return false;
        int cx = ((int) Math.floor(x)) >> 4, cz = ((int) Math.floor(z)) >> 4, c = 0;
        Long2IntOpenHashMap carte = parTroncon;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) c += carte.get(ChunkPos.asLong(cx + dx, cz + dz));
        }
        return c >= local;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void apparition(MobSpawnEvent.FinalizeSpawn e) {
        if (e.getSpawnType() != MobSpawnType.NATURAL || e.isSpawnCancelled()
                || e.getEntity().getType().getCategory() != MobCategory.MONSTER) return;
        if (e.getLevel().getLevel().dimension() != Level.OVERWORLD) return;
        if (auPlafond(e.getX(), e.getZ())) {
            e.setSpawnCancelled(true);
            return;
        }
        total++;   // les apparitions de la même seconde comptent aussi
    }
}
