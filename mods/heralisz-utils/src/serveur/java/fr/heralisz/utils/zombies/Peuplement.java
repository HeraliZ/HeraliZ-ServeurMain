package fr.heralisz.utils.zombies;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.utils.HeraliszUtils;
import fr.heralisz.utils.bots.Bots;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Peuplement de la carte (24/10, demande du propriétaire : « un DayZ, un minimum conséquent mais pas trop »). Les
 * apparitions naturelles de Minecraft ne donnent presque rien sur la carte : seulement dans le noir complet (rien de jour
 * à ciel ouvert, rien près d'une lampe), à une hauteur tirée au hasard entre le fond du monde et la surface (presque
 * toujours dans la pierre), un essai tous les 3 ticks (bukkit.yml), et rien du tout si la règle doMobSpawning du monde
 * est à false (elle l'était sur la carte de construction).
 * <p>
 * Ici, toutes les {@code intervalle_secondes} : pour chaque joueur en survie ou en aventure sur la carte (ni spectateur,
 * ni créatif, ni à bord d'un véhicule ou du train, ni bot de charge), les zombies HeraliZ à moins de
 * {@code rayon_compte} blocs sont comptés (relevé d'une seconde de {@link PlafondZombies}, aucun nouveau balayage
 * d'entités) ; sous la cible (nature, ou ville selon sa valeur, un peu plus la nuit, à peine plus en groupe), UN essai
 * d'apparition : point à 24-56 blocs, derrière ou sur les côtés de préférence, à 20 blocs au moins de tout joueur, sol
 * solide à ciel ouvert ou étage d'un bâtiment (au plus 3 colonnes, 23 cases de haut chacune), jamais dans l'eau, ni dans
 * un claim de CRac, ni dans une zone VIP, ni près de la bordure, seulement dans un tronçon qui tourne. Tenue de l'endroit
 * ({@link SpawnZombiesServeur}). Le plafond des monstres de la carte est respecté, et rien de neuf quand le serveur rame
 * ({@code tick_max_ms}).
 * <p>
 * Ces zombies portent l'étiquette {@link #TAG} et ne disparaissent pas tout seuls (Minecraft retire un zombie à 32 blocs au
 * bout d'une minute environ) : ils rôdent ; quand plus aucun joueur n'est à moins de {@code distance_retrait} blocs, ils
 * sont retirés sans butin. Repris au chargement de leur tronçon (après un redémarrage) et retirés de même s'ils sont loin.
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID)
public final class Peuplement {
    private Peuplement() {
    }

    /** Étiquette d'entité des zombies du peuplement. */
    public static final String TAG = "heraliz_peuple";

    private static final int ESSAIS = 6, MONTEE = 10, DESCENTE = 12, PROFONDEUR_MAX = 12, TOIT_MAX = 8, MARGE_VIP = 8;

    private static final ReferenceOpenHashSet<ZombieSurvie> PEUPLES = new ReferenceOpenHashSet<>();
    private static int poses, retires, coupesTick, rates;
    private static long prochainJournal;

    /** Zones VIP de la carte (HeraliZ Grades, heraliz_grades/zones_vip.json) : {minX, minZ, maxX, maxZ} avec la marge. */
    private static List<int[]> vip = List.of();
    private static long dateVip = -1;

    @SubscribeEvent
    public static void demarrage(ServerStartedEvent e) {
        PEUPLES.clear();
        dateVip = -1;
        prochainJournal = 0;
        lireVip(e.getServer());
    }

    /** Un zombie du peuplement arrive (posé ici, ou rechargé avec son tronçon) : suivi. LOWEST : jamais un refusé. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void arrivee(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() && e.getEntity() instanceof ZombieSurvie z && z.getTags().contains(TAG)) PEUPLES.add(z);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer srv = e.getServer();
        int t = srv.getTickCount();
        if (t % 200 == 3) lireVip(srv);
        int iv = ZombiesConfig.PEUPLEMENT_INTERVALLE.get() * 20;
        if ((t + 13) % iv != 0) return;
        ServerLevel sl = srv.overworld();
        List<ServerPlayer> tous = sl.players();
        if (tous.isEmpty() && PEUPLES.isEmpty()) return;
        retirerLoin(sl, tous);
        if (!ZombiesConfig.PEUPLEMENT.get() || tous.isEmpty() || sl.getDifficulty() == Difficulty.PEACEFUL) return;
        if (ZombiesConfig.PEUPLEMENT_REGLE.get() && !sl.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
        double msMax = ZombiesConfig.PEUPLEMENT_TICK_MAX.get();
        if (msMax > 0 && srv.getAverageTickTime() > msMax) {
            coupesTick++;
            journal(0);
            return;
        }

        List<ServerPlayer> peuplants = new ArrayList<>();
        for (ServerPlayer p : tous) if (peuple(p)) peuplants.add(p);
        if (peuplants.isEmpty()) return;
        RandomSource rnd = sl.getRandom();
        for (int i = peuplants.size() - 1; i > 0; i--) {   // ordre tiré au hasard : personne n'est toujours servi en premier
            int j = rnd.nextInt(i + 1);
            ServerPlayer x = peuplants.get(i);
            peuplants.set(i, peuplants.get(j));
            peuplants.set(j, x);
        }
        boolean nuit = sl.isNight();
        double rayon = ZombiesConfig.PEUPLEMENT_RAYON.get();
        int plafond = ZombiesConfig.PLAFOND_CARTE.get(), max = ZombiesConfig.PEUPLEMENT_MAX.get();
        for (ServerPlayer p : peuplants) {
            if (PEUPLES.size() >= max || (plafond > 0 && PlafondZombies.total() >= plafond)) break;
            int autour = PlafondZombies.zombiesAutour(p.getX(), p.getZ(), rayon), c = cible(p, peuplants, nuit, rayon);
            if (autour >= c) continue;
            // loin de la cible (moins de la moitié) : deux essais pour que le coin se remplisse vite
            for (int k = autour * 2 < c ? 2 : 1; k > 0; k--) {
                if (!essayer(sl, p, tous, rnd)) rates++;
            }
        }
        journal(peuplants.size());
    }

    /** Résumé dans le journal du serveur toutes les 5 min (pour régler les chiffres). */
    private static void journal(int servis) {
        if (System.currentTimeMillis() < prochainJournal) return;
        prochainJournal = System.currentTimeMillis() + 300_000L;
        HeraliszUtils.LOG.info("[zombies] peuplement : {} zombies suivis, {} joueurs servis, {} monstres sur la carte, {} poses, {} essais sans place, "
                        + "{} passages coupes (serveur au-dessus de tick_max_ms), {} retires en 5 min",
                PEUPLES.size(), servis, PlafondZombies.total(), poses, rates, coupesTick, retires);
        poses = retires = coupesTick = rates = 0;
    }

    /** Joueur autour duquel la carte se peuple. */
    private static boolean peuple(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && p.gameMode.isSurvival() && !p.isPassenger()
                && (ZombiesConfig.PEUPLEMENT_BOTS.get() || !p.getTags().contains(Bots.TAG));
    }

    private static int cible(ServerPlayer p, List<ServerPlayer> peuplants, boolean nuit, double rayon) {
        int valeur = SpawnZombiesServeur.valeurVille(p.getX(), p.getZ());
        double c = valeur > 0 ? ZombiesConfig.PEUPLEMENT_VILLE.get() + ZombiesConfig.PEUPLEMENT_PAR_VALEUR.get() * valeur
                : ZombiesConfig.PEUPLEMENT_NATURE.get();
        if (nuit) c *= ZombiesConfig.PEUPLEMENT_NUIT.get();
        int voisins = 0;
        double r2 = rayon * rayon;
        for (ServerPlayer o : peuplants) {
            if (o == p) continue;
            double dx = o.getX() - p.getX(), dz = o.getZ() - p.getZ();
            if (dx * dx + dz * dz < r2) voisins++;
        }
        c *= 1 + Math.min(ZombiesConfig.PEUPLEMENT_BONUS_MAX.get(), ZombiesConfig.PEUPLEMENT_BONUS_JOUEUR.get() * voisins);
        return (int) Math.min(ZombiesConfig.PEUPLEMENT_MAX_JOUEUR.get(), Math.round(c));
    }

    /** Retire (sans butin) les zombies du peuplement loin de tout joueur ; oublie ceux qui sont morts ou déchargés. */
    private static void retirerLoin(ServerLevel sl, List<ServerPlayer> tous) {
        if (PEUPLES.isEmpty()) return;
        double d = ZombiesConfig.PEUPLEMENT_RETRAIT.get(), d2 = d * d;
        Iterator<ZombieSurvie> it = PEUPLES.iterator();
        while (it.hasNext()) {
            ZombieSurvie z = it.next();
            if (z.isRemoved() || !z.isAlive() || z.level() != sl) {
                it.remove();
                continue;
            }
            if (!proche(z.getX(), z.getZ(), tous, d2)) {
                it.remove();
                z.discard();
                retires++;
            }
        }
    }

    /** Un joueur (sauf spectateur) à moins de √d2 blocs, à plat. */
    private static boolean proche(double x, double z, List<ServerPlayer> tous, double d2) {
        for (ServerPlayer p : tous) {
            if (p.isSpectator()) continue;
            double dx = p.getX() - x, dz = p.getZ() - z;
            if (dx * dx + dz * dz < d2) return true;
        }
        return false;
    }

    /** Un essai d'apparition pour ce joueur (au plus {@link #ESSAIS} colonnes regardées). -> un zombie posé. */
    private static boolean essayer(ServerLevel sl, ServerPlayer p, List<ServerPlayer> tous, RandomSource rnd) {
        int dMin = ZombiesConfig.PEUPLEMENT_DIST_MIN.get(), dMax = Math.max(dMin, ZombiesConfig.PEUPLEMENT_DIST_MAX.get());
        double dj = ZombiesConfig.PEUPLEMENT_DIST_JOUEUR.get(), dj2 = dj * dj, horsVue = ZombiesConfig.PEUPLEMENT_HORS_VUE.get();
        Vec3 vue = p.getLookAngle();
        double cap = Math.atan2(vue.z, vue.x);
        int py = Mth.floor(p.getY());
        for (int i = 0; i < ESSAIS; i++) {
            // hors de la vue : de 60° à 300° du regard (derrière et sur les côtés) ; sinon n'importe où
            double a = rnd.nextDouble() < horsVue ? cap + Math.toRadians(60 + rnd.nextDouble() * 240) : rnd.nextDouble() * Math.PI * 2;
            double dist = dMin + rnd.nextDouble() * (dMax - dMin);
            int bx = Mth.floor(p.getX() + Math.cos(a) * dist), bz = Mth.floor(p.getZ() + Math.sin(a) * dist);
            if (proche(bx + 0.5, bz + 0.5, tous, dj2)) continue;
            Vec3 place = sol(sl, bx, bz, py, rnd);
            if (place == null) continue;
            BlockPos bp = BlockPos.containing(place);
            if (dansVip(bx, bz) || claimCrac(sl, bp)) continue;
            ZombieSurvie z = ZombiesSurvie.ZOMBIE.get().create(sl);
            if (z == null) return false;
            z.tenue(SpawnZombiesServeur.tenueIci(place.x, place.z, rnd));
            z.moveTo(place.x, place.y, place.z, rnd.nextFloat() * 360f, 0f);
            z.finalizeSpawn(sl, sl.getCurrentDifficultyAt(bp), MobSpawnType.NATURAL, null, null);
            z.setPersistenceRequired();   // ne disparaît plus à 32 blocs : c'est le retrait ci-dessus qui décide
            z.addTag(TAG);
            if (sl.addFreshEntity(z)) {   // refusé par un plugin (région WorldGuard...) : rien
                PlafondZombies.compter(place.x, place.z);
                poses++;
                return true;
            }
            return false;
        }
        return false;
    }

    /**
     * Où poser un zombie dans la colonne (x, z), près de la hauteur du joueur : un étage à l'abri (bâtiment, sous les arbres),
     * pris au hasard, passe devant le sol à ciel ouvert (sinon on poserait sur les toits) ; à ciel ouvert, seulement à
     * {@link #TOIT_MAX} blocs au plus au-dessus du joueur. null : rien de bon, ou tronçon qui ne tourne pas.
     */
    private static Vec3 sol(ServerLevel sl, int x, int z, int py, RandomSource rnd) {
        if (sl.getWorldBorder().getDistanceToBorder(x + 0.5, z + 0.5) < 4) return null;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, py, z);
        if (!sl.isPositionEntityTicking(m)) return null;   // avant toute lecture : jamais de tronçon chargé pour ça
        int dessus = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int haut = Math.min(dessus, py + MONTEE), bas = Math.max(py - DESCENTE, sl.getMinBuildHeight() + 1);
        Vec3 ciel = null, abri = null;
        int abris = 0;
        for (int y = haut; y >= bas; y--) {
            Vec3 v = debout(sl, m, x, y, z);
            if (v == null) continue;
            if (sl.canSeeSky(m.set(x, y, z))) {
                if (ciel == null) ciel = v;
            } else if (dessus - y <= PROFONDEUR_MAX && rnd.nextInt(++abris) == 0) {
                abri = v;   // un étage pris au hasard (pas les caves profondes ni les bunkers)
            }
        }
        if (abri != null) return abri;
        return ciel != null && ciel.y <= py + TOIT_MAX ? ciel : null;
    }

    /** Pieds en (x, y, z) : posé sur un sol qui porte, sans eau, place libre pour un zombie. */
    private static Vec3 debout(ServerLevel sl, BlockPos.MutableBlockPos m, int x, int y, int z) {
        m.set(x, y - 1, z);
        BlockState s = sl.getBlockState(m);
        if (s.isAir() || !s.getFluidState().isEmpty() || s.getRenderShape() == RenderShape.INVISIBLE   // barrière, voie du train
                || s.is(BlockTags.LEAVES) || s.is(BlockTags.FIRE) || s.is(BlockTags.CAMPFIRES) || s.is(Blocks.MAGMA_BLOCK)
                || s.is(Blocks.CACTUS) || s.is(Blocks.SWEET_BERRY_BUSH)) return null;
        VoxelShape forme = s.getCollisionShape(sl, m);
        if (forme.isEmpty()) return null;
        double h = forme.max(Direction.Axis.Y);
        if (h <= 0 || h > 1.0) return null;   // clôtures, murets : non
        double fy = y - 1 + h;
        AABB bb = ZombiesSurvie.ZOMBIE.get().getAABB(x + 0.5, fy, z + 0.5);
        if (!sl.noCollision(bb) || sl.containsAnyLiquid(bb)) return null;
        return new Vec3(x + 0.5, fy, z + 0.5);
    }

    // ------------------------------------------------------------------ zones VIP (HeraliZ Grades, fichier lu tel quel)

    private static boolean dansVip(int x, int z) {
        for (int[] b : vip) if (x >= b[0] && x < b[2] && z >= b[1] && z < b[3]) return true;
        return false;
    }

    private static void lireVip(MinecraftServer srv) {
        Path f = srv.getServerDirectory().toPath().resolve("heraliz_grades").resolve("zones_vip.json");
        try {
            long d = Files.exists(f) ? Files.getLastModifiedTime(f).toMillis() : 0;
            if (d == dateVip) return;
            dateVip = d;
            List<int[]> l = new ArrayList<>();
            if (d != 0) {
                JsonObject racine = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement el : racine.getAsJsonArray("zones")) {
                    JsonObject o = el.getAsJsonObject();
                    if (!"minecraft:overworld".equals(o.get("monde").getAsString())) continue;
                    JsonArray a = o.getAsJsonArray("a"), b = o.getAsJsonArray("b");
                    int ax = a.get(0).getAsInt(), az = a.get(2).getAsInt(), bx = b.get(0).getAsInt(), bz = b.get(2).getAsInt();
                    l.add(new int[]{Math.min(ax, bx) - MARGE_VIP, Math.min(az, bz) - MARGE_VIP,
                            Math.max(ax, bx) + 1 + MARGE_VIP, Math.max(az, bz) + 1 + MARGE_VIP});
                }
            }
            vip = List.copyOf(l);
        } catch (Exception ex) {
            HeraliszUtils.LOG.warn("[zombies] peuplement : zones VIP illisibles ({}) : {}", f, ex.toString());
        }
    }

    // ------------------------------------------------------------------ CRac (facultatif, par réflexion)

    private static Method cracClaim;
    private static boolean cracCherche;

    /** Dans un claim de CRac (fr.heraliz.crac.claim.ClaimManager.getAt) ; CRac absent ou changé : non. */
    private static boolean claimCrac(ServerLevel sl, BlockPos pos) {
        if (!cracCherche) {
            cracCherche = true;
            if (ModList.get().isLoaded("crac")) {
                try {
                    cracClaim = Class.forName("fr.heraliz.crac.claim.ClaimManager").getMethod("getAt", ServerLevel.class, BlockPos.class);
                } catch (ReflectiveOperationException | LinkageError ex) {
                    HeraliszUtils.LOG.warn("[zombies] peuplement : claims de CRac introuvables, zombies possibles dans les bases");
                }
            }
        }
        if (cracClaim == null) return false;
        try {
            return cracClaim.invoke(null, sl, pos) != null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            HeraliszUtils.LOG.warn("[zombies] peuplement : claims de CRac illisibles : {}", ex.toString());
            cracClaim = null;
            return false;
        }
    }
}
