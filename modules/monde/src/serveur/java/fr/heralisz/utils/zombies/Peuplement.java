package fr.heralisz.utils.zombies;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.heraliz.extraction.serveur.ExtractionZones;
import fr.heralisz.utils.HeraliszUtils;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
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
 * Peuplement de la carte (24/10, refait le 25/10 : « pas obligatoirement N zombies autour de chaque joueur qui apparaissent
 * sans arrêt ; une règle plus aléatoire, moins de lag, et jamais un zombie qui apparaît sur un joueur de nulle part »).
 * <p>
 * La population suit les LIEUX ({@link ReservesZombies}) : chaque secteur de la carte a une capacité (nature, ville selon
 * sa valeur, zones posées par le staff : {@link ZonesZombies}) et une réserve. Toutes les {@code intervalle_secondes}, chaque
 * joueur en survie ou en aventure sur la carte (ni spectateur, ni créatif, ni à bord d'un véhicule ou du train, ni bot de
 * charge) a seulement une CHANCE ({@code chance_essai}, un peu plus la nuit, beaucoup moins près d'une extraction ouverte)
 * de faire apparaître UN zombie, et rien s'il y en a déjà beaucoup autour de lui. Le point est tiré à 32-72 blocs, derrière
 * ou sur les côtés de préférence, à 28 blocs au moins de TOUT joueur, dans un secteur qui a encore de la place, jamais à un
 * endroit qu'un joueur voit (rayon tiré de ses yeux), jamais à l'intérieur d'un bâtiment si un joueur est à moins de
 * 40 blocs, jamais à moins de 24 blocs du centre d'une extraction ouverte, ni dans un claim de CRac, une zone VIP, l'eau
 * ou près de la bordure. Tenue de l'endroit, ou celle de la zone (police, militaire).
 * <p>
 * Un zombie tué ne revient pas tout de suite : sa place revient dans la réserve du secteur au bout de quelques minutes.
 * Ces zombies portent l'étiquette {@link #TAG} et rôdent ; quand plus aucun joueur n'est à moins de
 * {@code distance_retrait} blocs, ils sont retirés sans butin et rendent leur place à leur secteur d'origine.
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID)
public final class Peuplement {
    private Peuplement() {
    }

    /** Étiquette d'entité des zombies du peuplement. */
    public static final String TAG = "heraliz_peuple";
    /** Secteur d'origine du zombie (données de l'entité) : sa place y revient quand il est retiré. */
    static final String SECTEUR = "HeralizSecteur";

    private static final int ESSAIS = 3, MONTEE = 10, DESCENTE = 12, PROFONDEUR_MAX = 12, TOIT_MAX = 8, MARGE_VIP = 8;
    /** Cosinus du demi-angle de vue retenu pour le test de vue (≈ 72° : large, pour les champs de vision élevés). */
    private static final double COS_VUE = 0.3;

    private static final ReferenceOpenHashSet<ZombieSurvie> PEUPLES = new ReferenceOpenHashSet<>();
    private static int poses, retires, refusVue, refusReserve;
    private static long prochainJournal;

    /** Zones VIP de la carte (HeraliZ Grades, heraliz_grades/zones_vip.json) : {minX, minZ, maxX, maxZ} avec la marge. */
    private static List<int[]> vip = List.of();
    private static long dateVip = -1;
    /** Centres des zones d'extraction ouvertes (relevés à chaque passage) : {x, z}. */
    private static final List<double[]> EXTRACTIONS = new ArrayList<>();

    @SubscribeEvent
    public static void demarrage(ServerStartedEvent e) {
        PEUPLES.clear();
        ReservesZombies.vider();
        ZonesZombies.charger(e.getServer());
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
        long now = System.currentTimeMillis();
        retirerLoin(sl, tous, now);
        if (!ZombiesConfig.PEUPLEMENT.get() || tous.isEmpty() || sl.getDifficulty() == Difficulty.PEACEFUL) return;
        if (ZombiesConfig.PEUPLEMENT_REGLE.get() && !sl.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
        double msMax = ZombiesConfig.PEUPLEMENT_TICK_MAX.get();
        if (msMax > 0 && srv.getAverageTickTime() > msMax) return;

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
        extractionsOuvertes(srv);
        double chance = ZombiesConfig.CHANCE_ESSAI.get() * (sl.isNight() ? ZombiesConfig.PEUPLEMENT_NUIT.get() : 1.0);
        int plafond = ZombiesConfig.PLAFOND_CARTE.get(), max = ZombiesConfig.PEUPLEMENT_MAX.get();
        int maxAutour = ZombiesConfig.MAX_AUTOUR.get();
        double rayonAutour = ZombiesConfig.MAX_AUTOUR_RAYON.get();
        for (ServerPlayer p : peuplants) {
            if (PEUPLES.size() >= max || (plafond > 0 && PlafondZombies.total() >= plafond)) break;
            double c = chance;
            if (pres(p.getX(), p.getZ(), ZombiesConfig.EXTRACTION_RAYON.get())) c *= ZombiesConfig.EXTRACTION_FACTEUR.get();
            if (rnd.nextDouble() >= c) continue;
            if (maxAutour > 0 && PlafondZombies.zombiesAutour(p.getX(), p.getZ(), rayonAutour) >= maxAutour) continue;
            essayer(sl, p, tous, rnd, now);
        }
        if (now >= prochainJournal) {
            prochainJournal = now + 300_000L;
            HeraliszUtils.LOG.info("[zombies] peuplement : {} zombies suivis, {} joueurs servis, {} monstres sur la carte, {} poses et {} retires en 5 min"
                            + " ({} points refuses car vus, {} faute de place dans le secteur, {} secteurs entames)",
                    PEUPLES.size(), peuplants.size(), PlafondZombies.total(), poses, retires, refusVue, refusReserve, ReservesZombies.entames());
            poses = retires = refusVue = refusReserve = 0;
        }
    }

    /** Joueur autour duquel la carte se peuple. */
    private static boolean peuple(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && p.gameMode.isSurvival() && !p.isPassenger()
                && (ZombiesConfig.PEUPLEMENT_BOTS.get() || !p.getTags().contains("heraliz_bot"));
    }

    /** Zombies suivis (pour /zombies stats). */
    static int suivis() {
        return PEUPLES.size();
    }

    // ------------------------------------------------------------------ extractions (registre du serveur)

    private static void extractionsOuvertes(MinecraftServer srv) {
        EXTRACTIONS.clear();
        try {
            ExtractionZones zs = ExtractionZones.get(srv);
            long heure = srv.overworld().getGameTime();
            for (ExtractionZones.Zone z : zs.all()) {
                if (Level.OVERWORLD.location().equals(z.dimension()) && zs.isOpen(z, heure)) EXTRACTIONS.add(new double[]{z.pos().x, z.pos().z});
            }
        } catch (RuntimeException ex) {
            // registre des extractions illisible : peuplement sans règle d'extraction
        }
    }

    /** Un centre d'extraction ouverte à moins de r blocs. */
    private static boolean pres(double x, double z, double r) {
        if (r <= 0) return false;
        double r2 = r * r;
        for (double[] c : EXTRACTIONS) {
            double dx = c[0] - x, dz = c[1] - z;
            if (dx * dx + dz * dz < r2) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ retrait

    /** Retire (sans butin) les zombies du peuplement loin de tout joueur, qui rendent leur place ; oublie les morts. */
    private static void retirerLoin(ServerLevel sl, List<ServerPlayer> tous, long now) {
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
                if (z.getPersistentData().contains(SECTEUR)) {
                    long cle = z.getPersistentData().getLong(SECTEUR);
                    ReservesZombies.rendre(ReservesZombies.centreX(cle), ReservesZombies.centreZ(cle), now);
                }
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

    // ------------------------------------------------------------------ apparition

    /** Un seul essai d'apparition pour ce joueur (au plus {@link #ESSAIS} colonnes regardées). */
    private static void essayer(ServerLevel sl, ServerPlayer p, List<ServerPlayer> tous, RandomSource rnd, long now) {
        int dMin = ZombiesConfig.APPARITION_MIN.get(), dMax = Math.max(dMin, ZombiesConfig.APPARITION_MAX.get());
        double dj = ZombiesConfig.ECART_JOUEUR.get(), dj2 = dj * dj, horsVue = ZombiesConfig.PEUPLEMENT_HORS_VUE.get();
        double di = ZombiesConfig.ECART_INTERIEUR.get(), di2 = di * di;
        Vec3 vue = p.getLookAngle();
        double cap = Math.atan2(vue.z, vue.x);
        int py = Mth.floor(p.getY());
        for (int i = 0; i < ESSAIS; i++) {
            // derrière et sur les côtés (60° à 300° du regard) de préférence ; sinon n'importe où
            double a = rnd.nextDouble() < horsVue ? cap + Math.toRadians(60 + rnd.nextDouble() * 240) : rnd.nextDouble() * Math.PI * 2;
            double dist = dMin + rnd.nextDouble() * (dMax - dMin);
            int bx = Mth.floor(p.getX() + Math.cos(a) * dist), bz = Mth.floor(p.getZ() + Math.sin(a) * dist);
            if (proche(bx + 0.5, bz + 0.5, tous, dj2)) continue;
            if (pres(bx + 0.5, bz + 0.5, ZombiesConfig.EXTRACTION_VIDE.get())) continue;
            long cle = ReservesZombies.cle(bx + 0.5, bz + 0.5);
            if (ReservesZombies.disponible(cle, now) <= 0) {
                refusReserve++;
                continue;
            }
            Vec3 place = sol(sl, bx, bz, py, rnd, !proche(bx + 0.5, bz + 0.5, tous, di2));
            if (place == null) continue;
            BlockPos bp = BlockPos.containing(place);
            if (dansVip(bx, bz) || claimCrac(sl, bp)) continue;
            if (ZombiesConfig.TEST_VUE.get() && vu(sl, place, tous)) {
                refusVue++;
                continue;
            }
            ZombieSurvie z = ZombiesSurvie.ZOMBIE.get().create(sl);
            if (z == null || !ReservesZombies.prendre(cle, now)) return;
            ZonesZombies.Zone zone = ZonesZombies.ici(place.x, place.z);
            ZombiesSurvie.Tenue tenue = ZonesZombies.tenue(zone);
            z.tenue(tenue != null && rnd.nextDouble() < 0.85 ? tenue : SpawnZombiesServeur.tenueIci(place.x, place.z, rnd));
            if (zone != null && zone.type().costauds > 0) z.getPersistentData().putDouble(IaZombies.BONUS_COSTAUDS, zone.type().costauds);
            z.getPersistentData().putLong(SECTEUR, cle);
            z.moveTo(place.x, place.y, place.z, rnd.nextFloat() * 360f, 0f);
            z.finalizeSpawn(sl, sl.getCurrentDifficultyAt(bp), MobSpawnType.NATURAL, null, null);
            z.setPersistenceRequired();   // ne disparaît plus à 32 blocs : c'est le retrait ci-dessus qui décide
            z.addTag(TAG);
            if (sl.addFreshEntity(z)) {   // refusé par un plugin (région WorldGuard...) : la place revient
                PlafondZombies.compter(place.x, place.z);
                poses++;
            } else {
                ReservesZombies.rendre(place.x, place.z, now);
            }
            return;
        }
    }

    /**
     * Un joueur voit cet endroit : dans son champ de vision (large), à moins de {@code vue_portee} blocs, et un rayon tiré
     * de ses yeux atteint la tête ou les pieds du futur zombie sans heurter de bloc. Les tronçons traversés sont ceux entre
     * un joueur et un point d'un tronçon actif : déjà chargés.
     */
    private static boolean vu(ServerLevel sl, Vec3 pied, List<ServerPlayer> tous) {
        double portee = ZombiesConfig.VUE_PORTEE.get(), p2 = portee * portee;
        Vec3 tete = pied.add(0, 1.7, 0), bas = pied.add(0, 0.4, 0);
        for (ServerPlayer p : tous) {
            if (p.isSpectator() || p.level() != sl) continue;
            Vec3 oeil = p.getEyePosition();
            double d2 = oeil.distanceToSqr(tete);
            if (d2 > p2) continue;
            Vec3 dir = tete.subtract(oeil);
            double n = Math.sqrt(d2);
            if (n > 1e-3 && p.getLookAngle().dot(dir) / n < COS_VUE) continue;
            if (libre(sl, oeil, tete, p) || libre(sl, oeil, bas, p)) return true;
        }
        return false;
    }

    private static boolean libre(ServerLevel sl, Vec3 a, Vec3 b, ServerPlayer p) {
        return sl.clip(new ClipContext(a, b, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
    }

    /**
     * Où poser un zombie dans la colonne (x, z), près de la hauteur du joueur : un étage à l'abri (bâtiment, sous les arbres),
     * pris au hasard, passe devant le sol à ciel ouvert, mais seulement si {@code interieur} (aucun joueur proche) ; à ciel
     * ouvert, seulement à {@link #TOIT_MAX} blocs au plus au-dessus du joueur. null : rien de bon, ou tronçon qui ne tourne pas.
     */
    private static Vec3 sol(ServerLevel sl, int x, int z, int py, RandomSource rnd, boolean interieur) {
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
            } else if (interieur && dessus - y <= PROFONDEUR_MAX && rnd.nextInt(++abris) == 0) {
                abri = v;   // un étage pris au hasard (pas les caves profondes ni les bunkers)
            }
        }
        if (abri != null) return abri;
        return ciel != null && ciel.y <= py + TOIT_MAX ? ciel : null;
    }

    /** Pieds en (x, y, z) : posé sur un sol qui porte, sans eau, place libre pour un zombie. */
    static Vec3 debout(ServerLevel sl, BlockPos.MutableBlockPos m, int x, int y, int z) {
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
    static boolean claimCrac(ServerLevel sl, BlockPos pos) {
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
