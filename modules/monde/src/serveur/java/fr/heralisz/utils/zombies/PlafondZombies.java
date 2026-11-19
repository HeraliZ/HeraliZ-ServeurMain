package fr.heralisz.utils.zombies;

import fr.heralisz.utils.HeraliszUtils;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
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
 * <p>19/11 (audit des lenteurs, C14) : les monstres de la carte sont suivis par leurs arrivées et leurs départs
 * ({@code EntityJoinLevelEvent}, {@code EntityLeaveLevelEvent}) ; le relevé de chaque seconde ne parcourt plus que
 * ces monstres, sans tables neuves (avant : toutes les entités de la carte, objets au sol, odeurs de Zombie Awareness et
 * corps compris, et deux tables neuves chaque seconde). Une fois par minute, un recensement complet de la carte sert de
 * contrôle (monstre oublié ajouté, écart noté pour {@code /zombies stats}). Réglage {@code plafond_par_arrivees}
 * (false : le relevé complet de chaque seconde, comme avant).</p>
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID)
public final class PlafondZombies {
    private PlafondZombies() {
    }

    private static int total;
    private static final Long2IntOpenHashMap parTroncon = new Long2IntOpenHashMap();
    /** Zombies HeraliZ par tronçon (même relevé), lus par le peuplement ({@link Peuplement}). */
    private static final Long2IntOpenHashMap zombiesParTroncon = new Long2IntOpenHashMap();
    /** Monstres de la carte arrivés et pas encore partis (identité des entités ; fil serveur). */
    private static final ReferenceOpenHashSet<Entity> SUIVIS = new ReferenceOpenHashSet<>();
    private static boolean signale;
    /** Relevés depuis le dernier recensement complet ; écart trouvé par le dernier (monstres qui manquaient au suivi). */
    private static int releves, dernierEcart, recensements;
    /** Un recensement complet toutes les tant de secondes (contrôle du suivi par arrivées et départs). */
    private static final int RECENSEMENT_S = 60;

    static {
        // taille du suivi visible dans /diag fuites (moniteur des listes du noyau : alerte si elle ne fait que grossir)
        fr.heraliz.noyau.api.Fuites.surveiller("zombies.plafond_suivis", SUIVIS::size);
    }

    /** Arrivée d'une entité dans un monde : un monstre de la carte est suivi (après les autres écouteurs, s'il entre vraiment). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void arrivee(EntityJoinLevelEvent e) {
        if (e.isCanceled() || e.getLevel().isClientSide() || e.getLevel().dimension() != Level.OVERWORLD) return;
        Entity en = e.getEntity();
        if (en.getType().getCategory() == MobCategory.MONSTER) SUIVIS.add(en);
    }

    /** Départ (mort, retrait, tronçon déchargé, changement de monde) : plus suivi. */
    @SubscribeEvent
    public static void depart(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide() || SUIVIS.isEmpty()) return;
        SUIVIS.remove(e.getEntity());
    }

    @SubscribeEvent
    public static void arret(ServerStoppedEvent e) {
        SUIVIS.clear();
        parTroncon.clear();
        zombiesParTroncon.clear();
        total = 0;
        releves = 0;
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD
                || sl.getGameTime() % 20 != 7) return;
        if (ZombiesConfig.PLAFOND_CARTE.get() <= 0 && ZombiesConfig.PLAFOND_LOCAL.get() <= 0 && !ZombiesConfig.PEUPLEMENT.get()) return;
        boolean complet = !ZombiesConfig.PLAFOND_PAR_ARRIVEES.get() || ++releves >= RECENSEMENT_S;
        if (complet) {
            releves = 0;
            recenser(sl);
        } else {
            releverSuivis(sl);
        }
        int n = total;
        int plafond = ZombiesConfig.PLAFOND_CARTE.get();
        if (plafond > 0 && n >= plafond && !signale) {
            signale = true;
            HeraliszUtils.LOG.info("[zombies] plafond des monstres de la carte atteint ({} / {}) : plus d'apparition naturelle tant que ça dure",
                    n, plafond);
        } else if (plafond > 0 && n < plafond * 9 / 10) {
            signale = false;
        }
    }

    /** Relevé de chaque seconde : seulement les monstres suivis (ceux partis sans qu'on le sache sont oubliés ici). */
    private static void releverSuivis(ServerLevel sl) {
        parTroncon.clear();
        zombiesParTroncon.clear();
        int n = 0;
        for (ObjectIterator<Entity> it = SUIVIS.iterator(); it.hasNext(); ) {
            Entity en = it.next();
            if (en.isRemoved() || en.level() != sl) {
                it.remove();
                continue;
            }
            if (!en.isAlive()) continue;
            n++;
            compterEntite(en);
        }
        total = n;
    }

    /**
     * Recensement complet (une fois par minute, ou chaque seconde si {@code plafond_par_arrivees} est faux) : toutes les
     * entités de la carte, comme avant le 19/11 ; un monstre qui manquait au suivi y est ajouté (écart noté).
     */
    private static void recenser(ServerLevel sl) {
        parTroncon.clear();
        zombiesParTroncon.clear();
        int n = 0, manquants = 0;
        for (Entity en : sl.getAllEntities()) {
            if (en.getType().getCategory() != MobCategory.MONSTER || !en.isAlive()) continue;
            n++;
            compterEntite(en);
            if (SUIVIS.add(en)) manquants++;
        }
        // les suivis partis sans qu'on le sache (retirés, autre monde) sont oubliés
        for (ObjectIterator<Entity> it = SUIVIS.iterator(); it.hasNext(); ) {
            Entity en = it.next();
            if (en.isRemoved() || en.level() != sl) it.remove();
        }
        total = n;
        recensements++;
        if (ZombiesConfig.PLAFOND_PAR_ARRIVEES.get() && manquants > 0 && manquants != dernierEcart) {
            HeraliszUtils.LOG.debug("[zombies] recensement de la carte : {} monstre(s) manquaient au suivi (ajoutes)", manquants);
        }
        dernierEcart = manquants;
    }

    private static void compterEntite(Entity en) {
        long k = ChunkPos.asLong(en.getBlockX() >> 4, en.getBlockZ() >> 4);
        parTroncon.addTo(k, 1);
        if (en instanceof ZombieSurvie) zombiesParTroncon.addTo(k, 1);
    }

    /** Monstres de la carte au dernier relevé (plus les apparitions comptées depuis). */
    static int total() {
        return total;
    }

    /** Monstres suivis par leurs arrivées et départs, et monstres qui manquaient au suivi au dernier recensement complet. */
    static int[] suivi() {
        return new int[]{SUIVIS.size(), dernierEcart, recensements};
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
