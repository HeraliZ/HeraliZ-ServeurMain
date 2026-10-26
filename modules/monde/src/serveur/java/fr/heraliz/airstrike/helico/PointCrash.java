package fr.heraliz.airstrike.helico;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.airstrike.AirstrikeMod;
import fr.heraliz.airstrike.api.HelicoApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Où l'hélicoptère abattu peut s'écraser (règle du propriétaire : là où il est abattu ou tout près, jamais dans l'eau,
 * toujours à un endroit où l'on va à pied) et où il ne patrouille pas. Le point visé est la position au moment de la
 * destruction, un peu en avant ; s'il ne passe pas, on cherche autour, du plus près au plus loin (tous les 4 blocs jusqu'à
 * {@code recherche_proche}, puis tous les 8 jusqu'à {@code recherche_loin}). Un point passe si, sur 9 × 9 blocs (un relevé
 * tous les 2 blocs) : tronçons déjà chargés (la recherche ne charge jamais rien), sol naturel (étiquette
 * {@code airstrike:sol_crash} : terre, sable, pierre, gravier, neige… jamais de matériau de toit), aucun liquide, aucun
 * feuillage au-dessus, au plus 3 blocs d'écart de hauteur ; du plein sous la surface (3 blocs, au centre et aux 4 coins :
 * pas un toit de terre au-dessus d'une pièce) ; la surface à ±3 blocs de la hauteur médiane d'un anneau à 12 blocs (pas
 * un toit ni un pic) ; dans la bordure ; loin du point d'apparition de la carte, des zones VIP (HeraliZ Grades) et des
 * bases des clans (CRac). Si les zones VIP ou les bases ne peuvent pas être lues, le point est REFUSÉ (jamais accepté
 * par défaut) : une ligne au journal, nouvel essai de lecture plus tard. Une seule recherche par hélicoptère abattu
 * (3 000 essais au plus, la plupart refusés dès le premier relevé). Serveur seulement.
 */
public final class PointCrash {
    private PointCrash() {
    }

    /** Sols où l'épave peut tomber : data/airstrike/tags/blocks/sol_crash.json. */
    public static final TagKey<Block> SOL = TagKey.create(Registries.BLOCK, new ResourceLocation(AirstrikeMod.MODID, "sol_crash"));
    private static final int DEMI = 4;
    private static final int ESSAIS_MAX = 3000;
    /** Marge autour des zones VIP (blocs). */
    private static final int MARGE_VIP = 8;
    /** Blocs pleins exigés sous la surface, au centre et aux coins. */
    private static final int PLEIN = 3;
    /** Anneau de contrôle : rayon, écart permis à sa hauteur médiane, relevés lisibles au moins. */
    private static final int ANNEAU = 12, ECART_ANNEAU = 3, ANNEAU_MIN = 5;
    private static final int[][] COINS = {{0, 0}, {-DEMI, -DEMI}, {DEMI, -DEMI}, {-DEMI, DEMI}, {DEMI, DEMI}};

    /**
     * Point de crash le plus proche de (x, z) qui passe les contrôles : position du sol (premier bloc libre au-dessus), ou
     * null si aucun dans le rayon de recherche.
     */
    @Nullable
    public static BlockPos chercher(ServerLevel level, double x, double z, HelicoApi.Reglages r, RandomSource rnd) {
        lireVip(level.getServer());
        int essais = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int rayon = 0; rayon <= r.rechercheLoin; rayon += rayon < r.rechercheProche ? 4 : 8) {
            int pas = rayon < r.rechercheProche ? 4 : 8;
            int n = rayon == 0 ? 1 : Math.max(6, (int) Math.round(2 * Math.PI * rayon / pas));
            double a0 = rnd.nextDouble() * Math.PI * 2;
            for (int k = 0; k < n; k++) {
                if (++essais > ESSAIS_MAX) return null;
                double a = a0 + 2 * Math.PI * k / n;
                int px = Mth.floor(x + Math.cos(a) * rayon), pz = Mth.floor(z + Math.sin(a) * rayon);
                int y = sol(level, px, pz, r, m);
                if (y != Integer.MIN_VALUE) return new BlockPos(px, y, pz);
            }
        }
        return null;
    }

    /** Le sol sous (x, z) passe tous les contrôles d'un point de crash : sa position, sinon null (rien n'est chargé). */
    @Nullable
    public static BlockPos valide(ServerLevel level, double x, double z, HelicoApi.Reglages r) {
        lireVip(level.getServer());
        int px = Mth.floor(x), pz = Mth.floor(z);
        int y = sol(level, px, pz, r, new BlockPos.MutableBlockPos());
        return y == Integer.MIN_VALUE ? null : new BlockPos(px, y, pz);
    }

    /**
     * Contrôles relâchés (dernier recours pour poser un objet au sol) : le sol le plus proche de (x, z), dans la bordure,
     * dans un tronçon chargé, qui n'est pas un liquide ; jusqu'à {@code rayon} blocs. Null s'il n'y en a pas.
     */
    @Nullable
    public static BlockPos solRelache(ServerLevel level, double x, double z, int rayon) {
        WorldBorder b = level.getWorldBorder();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int d = 0; d <= rayon; d += 4) {
            int n = d == 0 ? 1 : Math.max(6, (int) Math.round(2 * Math.PI * d / 4));
            for (int k = 0; k < n; k++) {
                double a = 2 * Math.PI * k / n;
                int px = Mth.floor(x + Math.cos(a) * d), pz = Mth.floor(z + Math.sin(a) * d);
                if (!b.isWithinBounds(px, pz) || !charge(level, px, pz)) continue;
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, px, pz);
                if (h <= level.getMinBuildHeight()) continue;
                if (!level.getBlockState(m.set(px, h - 1, pz)).getFluidState().isEmpty()) continue;
                return new BlockPos(px, h, pz);
            }
        }
        return null;
    }

    /** Hauteur du sol au centre si la zone passe, sinon {@code Integer.MIN_VALUE}. */
    private static int sol(ServerLevel level, int x, int z, HelicoApi.Reglages r, BlockPos.MutableBlockPos m) {
        WorldBorder b = level.getWorldBorder();
        if (!b.isWithinBounds(x - ANNEAU, z - ANNEAU) || !b.isWithinBounds(x + ANNEAU, z + ANNEAU)) return Integer.MIN_VALUE;
        if (!charge(level, x - ANNEAU, z - ANNEAU) || !charge(level, x + ANNEAU, z + ANNEAU)
                || !charge(level, x - ANNEAU, z + ANNEAU) || !charge(level, x + ANNEAU, z - ANNEAU)) return Integer.MIN_VALUE;
        if (interdit(level, x, z, r)) return Integer.MIN_VALUE;
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, centre = 0;
        // le centre d'abord : la plupart des points sont refusés dès ce relevé
        for (int i = -1; i < 25; i++) {
            int dx = 0, dz = 0;
            if (i >= 0) {
                dx = (i % 5) * 2 - DEMI;
                dz = (i / 5) * 2 - DEMI;
                if (dx == 0 && dz == 0) continue;
            }
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
            // feuillage au-dessus : un arbre, pas un sol dégagé
            if (h != level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + dx, z + dz)) return Integer.MIN_VALUE;
            BlockState s = level.getBlockState(m.set(x + dx, h - 1, z + dz));
            if (!s.getFluidState().isEmpty() || !s.is(SOL)) return Integer.MIN_VALUE;
            min = Math.min(min, h);
            max = Math.max(max, h);
            if (max - min > 3) return Integer.MIN_VALUE;
            if (i < 0) centre = h;
        }
        // du plein sous la surface, au centre et aux coins : pas un toit de terre ou de pierre posé sur du vide
        for (int[] c : COINS) {
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + c[0], z + c[1]);
            for (int k = 2; k <= PLEIN + 1; k++) {
                BlockState s = level.getBlockState(m.set(x + c[0], h - k, z + c[1]));
                if (s.isAir() || !s.getFluidState().isEmpty()) return Integer.MIN_VALUE;
            }
        }
        // la surface à la hauteur du terrain autour (médiane d'un anneau à 12 blocs) : pas un toit, pas un pic
        int[] anneau = new int[8];
        int n = 0;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            int ax = x + (int) Math.round(Math.cos(a) * ANNEAU), az = z + (int) Math.round(Math.sin(a) * ANNEAU);
            if (!charge(level, ax, az)) continue;
            anneau[n++] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ax, az);
        }
        if (n < ANNEAU_MIN) return Integer.MIN_VALUE;
        Arrays.sort(anneau, 0, n);
        int mediane = anneau[n / 2];
        if (Math.abs(centre - mediane) > ECART_ANNEAU) return Integer.MIN_VALUE;
        if (procheDUneBase(level, m, x, centre, z, r.loinDesBases)) return Integer.MIN_VALUE;
        return centre;
    }

    private static boolean charge(ServerLevel level, int x, int z) {
        return level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null;
    }

    /**
     * Trop près du point d'apparition de la carte, ou dans une zone VIP (marge comprise), ou zones VIP illisibles (refusé
     * par prudence). Contrôle léger, sans bloc lu.
     */
    public static boolean interdit(ServerLevel level, double x, double z, HelicoApi.Reglages r) {
        BlockPos sp = level.getSharedSpawnPos();
        double dx = x - sp.getX(), dz = z - sp.getZ();
        if (r.loinDuSpawn > 0 && dx * dx + dz * dz < (double) r.loinDuSpawn * r.loinDuSpawn) return true;
        if (!vipLues) return true;
        for (int[] v : vip) {
            if (x >= v[0] && x < v[2] && z >= v[1] && z < v[3]) return true;
        }
        return false;
    }

    /**
     * Dans une zone VIP de la carte (marge comprise), ou zones VIP pas encore lues sans erreur (refusé par prudence) :
     * frappes aériennes des joueurs (26/10, {@code strike.DesignatorLogic}). Appeler {@link #lireVip} avant.
     */
    public static boolean dansZoneVip(double x, double z) {
        if (!vipLues) return true;
        for (int[] v : vip) {
            if (x >= v[0] && x < v[2] && z >= v[1] && z < v[3]) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ zones VIP (HeraliZ Grades, fichier lu tel quel)
    private static List<int[]> vip = List.of();
    /** Zones lues sans erreur (fichier absent : aucune zone, c'est lu). Sinon tout point est refusé. */
    private static boolean vipLues, vipErreurDite;
    private static long dateVip = -1, vipProchain;

    /** Relit {@code heraliz_grades/zones_vip.json} s'il a changé, au plus toutes les 30 s ; illisible : nouvel essai dans 30 s. */
    public static void lireVip(@Nullable MinecraftServer srv) {
        long now = System.currentTimeMillis();
        if (srv == null || now < vipProchain) return;
        vipProchain = now + 30_000L;
        Path f = srv.getServerDirectory().toPath().resolve("heraliz_grades").resolve("zones_vip.json");
        try {
            long d = Files.exists(f) ? Files.getLastModifiedTime(f).toMillis() : 0;
            if (vipLues && d == dateVip) return;
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
            dateVip = d;
            vipLues = true;
            vipErreurDite = false;
        } catch (Exception ex) {
            vipLues = false;
            if (!vipErreurDite) {
                vipErreurDite = true;
                AirstrikeMod.LOGGER.error("[helico] zones VIP illisibles ({}) : aucun point de crash ni de patrouille accepte tant qu'elles ne"
                        + " sont pas lues ({})", f, ex.toString());
            }
        }
    }

    // ------------------------------------------------------------------ bases (CRac, par réflexion)
    @Nullable
    private static Method claimAt;
    private static long claimProchainEssai;
    private static boolean claimErreurDite;

    /**
     * Un claim de base (CRac) à moins de {@code loin} blocs : le point et 8 autour (contrôle léger, comme le largage). Vrai
     * aussi si les bases ne peuvent pas être lues (CRac absent ou en erreur) : le point est refusé, jamais accepté par
     * défaut ; une ligne au journal, nouvel essai de la lecture une minute plus tard.
     */
    public static boolean procheDUneBase(ServerLevel level, BlockPos.MutableBlockPos m, int x, int y, int z, int loin) {
        if (claimAt == null) {
            long now = System.currentTimeMillis();
            if (now < claimProchainEssai) return true;
            claimProchainEssai = now + 60_000L;
            try {
                claimAt = Class.forName("fr.heraliz.crac.claim.ClaimManager").getMethod("getAt", ServerLevel.class, BlockPos.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                erreurClaims(e);
                return true;
            }
        }
        try {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (claimAt.invoke(null, level, m.set(x + dx * loin, y, z + dz * loin).immutable()) != null) return true;
                }
            }
            claimErreurDite = false;
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            erreurClaims(e);
            claimAt = null;
            claimProchainEssai = System.currentTimeMillis() + 60_000L;
            return true;
        }
    }

    private static void erreurClaims(Throwable e) {
        if (claimErreurDite) return;
        claimErreurDite = true;
        AirstrikeMod.LOGGER.error("[helico] bases des clans (CRac) illisibles : aucun point de crash ni de patrouille accepte tant"
                + " qu'elles ne sont pas lues ({})", e.toString());
    }
}
