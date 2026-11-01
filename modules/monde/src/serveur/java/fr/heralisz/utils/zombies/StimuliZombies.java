package fr.heralisz.utils.zombies;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.custom.SilenceModifier;
import fr.heralisz.utils.HeraliszUtils;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Bruit et sang (25/10) : un tir (moins loin avec un silencieux), une explosion ou un joueur blessé attirent les zombies
 * HeraliZ proches qui n'ont pas encore de cible : ils vont voir d'où ça vient (plus le zombie est près, plus il y a de
 * chances qu'il y aille), et un groupe qui va au même endroit partage le même chemin ({@link CheminsZombies}). Un seul
 * calcul AU MOMENT du bruit (recherche des zombies dans la boîte autour, par tronçons), jamais de balayage à chaque tick ;
 * tirs d'un même tireur une fois par seconde au plus ; un zombie qui vient de partir vers un bruit n'est pas relancé avant
 * 3 s. Seulement sur la carte (overworld). Serveur seulement.
 * <p>
 * 01/11 (journal du Ptero : 49 bruits de plus de 10 ms) : les zombies choisis partent quelques-uns par tick
 * ({@code par_tick}, file d'attente de 2 s au plus) au lieu de calculer jusqu'à 16 chemins dans le tick du tir ; et le
 * chemin vers un bruit est cherché en petit ({@code recherche}), vers un point à la portée de suivi du zombie dans la
 * direction du bruit quand celui-ci est plus loin (Minecraft ne l'emmenait de toute façon pas plus loin : un tel calcul
 * parcourait toutes ses cases pour un chemin incomplet).
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID)
public final class StimuliZombies {
    private StimuliZombies() {
    }

    private static final int DELAI_TIREUR = 20, DELAI_SANG = 60, DELAI_ZOMBIE = 60;
    /** Dernier bruit compté par tireur ou par blessé (tick de jeu). */
    private static final Map<UUID, Long> TIREURS = new HashMap<>(), BLESSES = new HashMap<>();
    /** Dernier départ de chaque zombie vers un bruit (tick de jeu). */
    private static final Map<ZombieSurvie, Long> PARTIS = new WeakHashMap<>();
    private static long bruits, attires;

    /** Zombie choisi par un bruit, en attente de son chemin (tick de jeu du bruit). */
    private record Envoi(ZombieSurvie zombie, double x, double y, double z, long tick) {
    }

    private static final ArrayDeque<Envoi> FILE = new ArrayDeque<>();
    /** File : 256 zombies au plus, 2 s d'attente au plus (après, le bruit est passé). */
    private static final int FILE_MAX = 256, FILE_ATTENTE = 40;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tir(GunShootEvent e) {
        if (e.getLogicalSide() != LogicalSide.SERVER || !ZombiesConfig.STIMULI.get()) return;
        LivingEntity tireur = e.getShooter();
        if (tireur == null || !(tireur.level() instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD) return;
        long t = sl.getGameTime();
        Long avant = TIREURS.get(tireur.getUUID());
        if (avant != null && t - avant < DELAI_TIREUR) return;
        TIREURS.put(tireur.getUUID(), t);
        int r = tireur instanceof ServerPlayer p && silencieux(p) ? ZombiesConfig.STIMULUS_TIR_SILENCIEUX.get() : ZombiesConfig.STIMULUS_TIR.get();
        bruit(sl, tireur.position(), r);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void explosion(ExplosionEvent.Detonate e) {
        if (!ZombiesConfig.STIMULI.get() || !(e.getLevel() instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD) return;
        bruit(sl, e.getExplosion().getPosition(), ZombiesConfig.STIMULUS_EXPLOSION.get());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void sang(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getAmount() < 1f || !ZombiesConfig.STIMULI.get()) return;
        if (!(p.level() instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD || p.isCreative() || p.isSpectator()) return;
        long t = sl.getGameTime();
        Long avant = BLESSES.get(p.getUUID());
        if (avant != null && t - avant < DELAI_SANG) return;
        BLESSES.put(p.getUUID(), t);
        bruit(sl, p.position(), ZombiesConfig.STIMULUS_SANG.get());
    }

    @SubscribeEvent
    public static void depart(PlayerEvent.PlayerLoggedOutEvent e) {
        TIREURS.remove(e.getEntity().getUUID());
        BLESSES.remove(e.getEntity().getUUID());
    }

    /**
     * 27/10 (blocage de 111 ms quand une bombe de frappe aérienne a touché un joueur) : un zombie qui marche déjà vers un
     * point à moins de tant de blocs du bruit n'est pas relancé. Une frappe envoie elle-même les infectés vers ses impacts
     * (Airstrike, {@code StrikeEffects.attractMonsters}) ; l'explosion de la même bombe, puis le sang du joueur touché,
     * recalculaient aussitôt un chemin (A*, la part chère d'un bruit) vers presque le même point pour ces mêmes zombies.
     */
    private static final double DEJA_EN_ROUTE = 8.0;
    /** Mesure (27/10) : un bruit plus long que ça est écrit au journal, une ligne toutes les 5 s au plus. */
    private static final long SEUIL_MESURE_NS = 10_000_000L;
    private static long prochaineMesureMs;

    /** Les zombies sans cible dans le rayon vont voir (au plus {@code max_zombies}). */
    static void bruit(ServerLevel sl, Vec3 ou, int rayon) {
        if (rayon <= 0) return;
        long debut = System.nanoTime();
        bruits++;
        long t = sl.getGameTime();
        double r2 = (double) rayon * rayon;
        List<ZombieSurvie> l = sl.getEntitiesOfClass(ZombieSurvie.class, new AABB(ou, ou).inflate(rayon, Math.min(rayon, 24), rayon),
                z -> z.isAlive() && z.getTarget() == null);
        int max = ZombiesConfig.STIMULUS_MAX.get(), n = 0;
        for (ZombieSurvie z : l) {
            if (n >= max) break;
            double d2 = z.distanceToSqr(ou);
            if (d2 > r2) continue;
            Long parti = PARTIS.get(z);
            if (parti != null && t - parti < DELAI_ZOMBIE) continue;
            if (dejaEnRoute(z, ou)) continue;
            // plus près, plus sûr : chance 1 au contact, 0,2 au bord du rayon
            double chance = 1.0 - 0.8 * Math.sqrt(d2 / r2);
            if (z.getRandom().nextDouble() >= chance) continue;
            PARTIS.put(z, t);
            if (FILE.size() >= FILE_MAX) FILE.pollFirst();
            FILE.addLast(new Envoi(z, ou.x, ou.y, ou.z, t));
            n++;
        }
        long ns = System.nanoTime() - debut;
        if (ns >= SEUIL_MESURE_NS) {
            long ms = System.currentTimeMillis();
            if (ms >= prochaineMesureMs) {
                prochaineMesureMs = ms + 5000L;
                HeraliszUtils.LOG.warn("[zombies] bruit : {} zombie(s) mis en route sur {} trouve(s) dans un rayon de {} blocs en {} ms",
                        n, l.size(), rayon, String.format(java.util.Locale.ROOT, "%.1f", ns / 1_000_000.0));
            }
        }
    }

    /** Chaque tick : quelques zombies de la file partent vers leur bruit (tant qu'il reste du budget de chemins). */
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || FILE.isEmpty()) return;
        int max = ZombiesConfig.STIMULUS_PAR_TICK.get();
        float facteur = ZombiesConfig.STIMULUS_RECHERCHE.get().floatValue();
        int lances = 0;
        while (lances < max && !FILE.isEmpty() && CheminsZombies.peutCalculer()) {
            Envoi en = FILE.pollFirst();
            ZombieSurvie z = en.zombie();
            if (z.isRemoved() || !z.isAlive() || z.getTarget() != null || z.level().getGameTime() - en.tick() > FILE_ATTENTE) continue;
            lances++;
            if (envoyer(z, en.x(), en.y(), en.z(), facteur)) attires++;
        }
    }

    /** Vers le bruit, ou vers le point à sa portée de suivi dans la direction du bruit s'il est plus loin. */
    private static boolean envoyer(ZombieSurvie z, double x, double y, double zz, float facteur) {
        double dx = x - z.getX(), dy = y - z.getY(), dz = zz - z.getZ();
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz), portee = z.getAttributeValue(Attributes.FOLLOW_RANGE) - 2.0;
        if (portee > 4.0 && d > portee) {
            double f = portee / d;
            x = z.getX() + dx * f;
            y = z.getY() + dy * f;
            zz = z.getZ() + dz * f;
        }
        if (z.getNavigation() instanceof NavigationZombie nav) return nav.allerVoir(x, y, zz, 1.0, facteur);
        return z.getNavigation().moveTo(x, y, zz, 1.0);
    }

    @SubscribeEvent
    public static void arret(ServerStoppedEvent e) {
        FILE.clear();
        PARTIS.clear();
    }

    /** Le zombie marche déjà (chemin en cours) vers un point à moins de {@value #DEJA_EN_ROUTE} blocs du bruit. */
    private static boolean dejaEnRoute(ZombieSurvie z, Vec3 ou) {
        if (z.getNavigation().isDone()) return false;
        net.minecraft.core.BlockPos vise = z.getNavigation().getTargetPos();
        return vise != null && vise.distToCenterSqr(ou) <= DEJA_EN_ROUTE * DEJA_EN_ROUTE;
    }

    /** Silencieux monté (propriété « silence » des accessoires TaCZ, déjà calculée pour l'arme en main). */
    private static boolean silencieux(ServerPlayer p) {
        AttachmentCacheProperty cache = IGunOperator.fromLivingEntity(p).getCacheProperty();
        if (cache == null) return false;
        Object o = cache.getCache(SilenceModifier.ID);
        return o instanceof Pair<?, ?> pair && Boolean.TRUE.equals(pair.right());
    }

    /** {bruits, zombies attirés} depuis le démarrage (pour /zombies stats). */
    static long[] compteurs() {
        return new long[]{bruits, attires};
    }
}
