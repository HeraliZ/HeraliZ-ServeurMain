package fr.heraliz.crac.bases;

import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Points d'arrivée dans le monde des bases : près de sa base, ou au hasard sur un sol plat loin des autres bases. */
final class Points {
    private static final int ESSAIS = 48;

    private Points() {
    }

    /** Une place pour un joueur près du Claim Block de sa base (à côté du bloc, dans la base), ou null. */
    @Nullable
    static Vec3 presDe(ServerLevel level, ServerPlayer p, BlockPos ancre) {
        for (int r = 1; r <= 4; r++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int sens = -1; sens <= 1; sens += 2) {
                    int y = ancre.getY() + sens * dy;
                    for (int dx = -r; dx <= r; dx++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                                continue;
                            }
                            BlockPos pieds = new BlockPos(ancre.getX() + dx, y, ancre.getZ() + dz);
                            if (libre(level, p, pieds)) {
                                return Vec3.atBottomCenterOf(pieds);
                            }
                        }
                    }
                    if (dy == 0) {
                        break;
                    }
                }
            }
        }
        return null;
    }

    /**
     * HeraliZ (03/11) : un home sûr aux pieds {@code pieds} (position exacte : un plancher à mi-bloc compte) : le corps du
     * joueur debout n'entre dans aucun bloc ni liquide, et un sol le porte juste dessous (jamais au-dessus du vide).
     */
    static boolean homeSur(ServerLevel level, ServerPlayer p, Vec3 pieds) {
        BlockPos b = BlockPos.containing(pieds);
        if (!level.isLoaded(b) || !level.getWorldBorder().isWithinBounds(b)) {
            return false;
        }
        AABB corps = p.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(pieds);
        if (!level.noCollision(p, corps) || level.containsAnyLiquid(corps)) {
            return false;
        }
        AABB dessous = new AABB(corps.minX, corps.minY - 0.25, corps.minZ, corps.maxX, corps.minY, corps.maxZ);
        return level.getBlockCollisions(p, dessous).iterator().hasNext() && !level.containsAnyLiquid(dessous);
    }

    /** Pieds libres (corps du joueur sans collision ni liquide), un sol dessous. */
    static boolean libre(ServerLevel level, ServerPlayer p, BlockPos pieds) {
        if (!level.isLoaded(pieds) || !level.getWorldBorder().isWithinBounds(pieds)) {
            return false;
        }
        // debout, quelle que soit sa pose du moment (accroupi, allongé) : jamais posé dans un trou où il ne se relève pas
        AABB corps = p.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(Vec3.atBottomCenterOf(pieds));
        if (!level.noCollision(p, corps) || level.containsAnyLiquid(corps)) {
            return false;
        }
        BlockState sol = level.getBlockState(pieds.below());
        return !sol.getCollisionShape(level, pieds.below()).isEmpty() && sol.getFluidState().isEmpty();
    }

    /**
     * Un point au hasard dans le monde des bases : sol naturel plat (5 × 5 à un bloc près), pas d'eau, dans la bordure,
     * à {@code [bases] ecart_bases} blocs au moins de tout Claim Block, hors des zones interdites. 01/11 : seulement dans
     * des tronçons déjà chargés (avant : jusqu'à 6 tronçons chargés sur le fil du serveur) ; secours de /rtp quand aucun des
     * points préparés ({@link #preparer}) ne convient. Null si rien n'a été trouvé en {@value #ESSAIS} essais.
     */
    @Nullable
    static Vec3 auHasardCharge(ServerLevel level, ServerPlayer p) {
        RandomSource rnd = level.getRandom();
        WorldBorder b = level.getWorldBorder();
        double demi = Math.max(32.0, b.getSize() / 2.0 - 100.0);
        double rayon = Math.min(BasesReglages.i(BasesReglages.RAYON_DEPART, 1150), demi);
        int ecart = BasesReglages.i(BasesReglages.ECART_BASES, 120);
        for (int i = 0; i < ESSAIS; i++) {
            int x = (int) Math.floor(b.getCenterX() + (rnd.nextDouble() * 2 - 1) * rayon);
            int z = (int) Math.floor(b.getCenterZ() + (rnd.nextDouble() * 2 - 1) * rayon);
            // carte principale (1.16.0) : jamais dans une zone où l'on ne construit pas (villes, loot, rails, neige…)
            if (presDUneBase(level, x, z, ecart) || MondeBases.estBases(level) && fr.heraliz.crac.heraliz.ZonesConstruction.interdit(x, z)) {
                continue;
            }
            // jamais de chargement ici (ni terrain neuf) : seulement des tronçons déjà là, voisins lus par plat() compris
            if (!charges(level, x, z)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (!plat(level, x, y, z)) {
                continue;
            }
            BlockPos pieds = new BlockPos(x, y, z);
            if (libre(level, p, pieds)) {
                return Vec3.atBottomCenterOf(pieds);
            }
        }
        return null;
    }

    /** Points tirés d'avance par {@link #preparer} (au plus), et tronçons demandés pour eux en tâche de fond (au plus). */
    private static final int CANDIDATS = 16, TICKETS_MAX = 6;
    /**
     * Autour d'un point tiré dont le sol n'est pas plat, quelques places voisines essayées aussi, toujours les mêmes (le test
     * « prêt » du départ et le départ trouvent la même place), dans les tronçons déjà demandés.
     */
    private static final int[][] VOISINS = {{0, 0}, {6, 0}, {-6, 0}, {0, 6}, {0, -6}, {6, 6}, {-6, -6}, {6, -6}, {-6, 6}};

    /**
     * /rtp, 01/11 : des points tirés au hasard avec les règles de {@link #auHasardCharge} (bordure, écart aux bases, zones
     * interdites, jamais de terrain neuf), dès la commande ; les tronçons de ceux qui ne sont pas chargés (au plus
     * {@value #TICKETS_MAX}) sont demandés à Minecraft, qui les lit en tâche de fond pendant le compte à rebours. Avant, le
     * départ en chargeait jusqu'à 6, plus leurs voisins, sur le fil du serveur. Rien n'est chargé ici.
     */
    static long[] preparer(ServerLevel level) {
        RandomSource rnd = level.getRandom();
        WorldBorder b = level.getWorldBorder();
        double demi = Math.max(32.0, b.getSize() / 2.0 - 100.0);
        double rayon = Math.min(BasesReglages.i(BasesReglages.RAYON_DEPART, 1150), demi);
        int ecart = BasesReglages.i(BasesReglages.ECART_BASES, 120);
        long[] out = new long[CANDIDATS];
        int n = 0, tickets = 0;
        for (int i = 0; i < ESSAIS && n < CANDIDATS; i++) {
            int x = (int) Math.floor(b.getCenterX() + (rnd.nextDouble() * 2 - 1) * rayon);
            int z = (int) Math.floor(b.getCenterZ() + (rnd.nextDouble() * 2 - 1) * rayon);
            if (presDUneBase(level, x, z, ecart) || MondeBases.estBases(level) && fr.heraliz.crac.heraliz.ZonesConstruction.interdit(x, z)) {
                continue;
            }
            net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(x >> 4, z >> 4);
            if (!Prechargement.pret(level, cp, 1)) {
                if (tickets >= TICKETS_MAX || level.dimension() == net.minecraft.world.level.Level.OVERWORLD && !regionPresente(level, x, z)) {
                    continue;
                }
                tickets++;
                // le tronçon du point et ses voisins (plat() et les places voisines lisent jusqu'à 8 blocs autour), 30 s
                Prechargement.precharger(level, cp, 1);
            }
            out[n++] = BlockPos.asLong(x, 0, z);
        }
        return java.util.Arrays.copyOf(out, n);
    }

    /**
     * Le premier des points tirés par {@link #preparer} (ou une place voisine, {@link #VOISINS}) où le joueur peut arriver,
     * avec les tests de {@link #auHasardCharge} (sol plat, place libre, loin des bases, hors des zones interdites), en ne
     * lisant que des tronçons déjà chargés ; null si aucun.
     */
    @Nullable
    static Vec3 parmi(ServerLevel level, ServerPlayer p, long[] points) {
        int ecart = BasesReglages.i(BasesReglages.ECART_BASES, 120);
        for (long k : points) {
            for (int[] d : VOISINS) {
                int x = BlockPos.getX(k) + d[0], z = BlockPos.getZ(k) + d[1];
                if (!charges(level, x, z) || presDUneBase(level, x, z, ecart)
                    || d != VOISINS[0] && MondeBases.estBases(level) && fr.heraliz.crac.heraliz.ZonesConstruction.interdit(x, z)) {
                    continue;   // pas encore lu, une base posée depuis la commande, ou une place voisine interdite
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (!plat(level, x, y, z)) {
                    continue;
                }
                BlockPos pieds = new BlockPos(x, y, z);
                if (libre(level, p, pieds)) {
                    return Vec3.atBottomCenterOf(pieds);
                }
            }
        }
        return null;
    }

    /** Tous les points tirés et leurs places voisines sont lus (rien de plus à attendre pour eux). */
    static boolean tousCharges(ServerLevel level, long[] points) {
        for (long k : points) {
            for (int[] d : VOISINS) {
                if (!charges(level, BlockPos.getX(k) + d[0], BlockPos.getZ(k) + d[1])) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Les tronçons que {@link #plat} et {@link #libre} lisent autour de (x, z) sont tous chargés (aucun chargement). */
    private static boolean charges(ServerLevel level, int x, int z) {
        for (int cx = (x - 2) >> 4; cx <= (x + 2) >> 4; cx++) {
            for (int cz = (z - 2) >> 4; cz <= (z + 2) >> 4; cz++) {
                if (level.getChunkSource().getChunkNow(cx, cz) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Le fichier de région du point existe (partie de la carte déjà construite) : rien ne sera généré. */
    private static boolean regionPresente(ServerLevel level, int x, int z) {
        java.nio.file.Path dossier = net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),
            level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT));
        return java.nio.file.Files.isRegularFile(dossier.resolve("region").resolve("r." + (x >> 9) + "." + (z >> 9) + ".mca"));
    }

    private static boolean presDUneBase(ServerLevel level, int x, int z, int ecart) {
        long e2 = (long) ecart * ecart;
        for (Claim c : ClaimData.get(level).all()) {
            long dx = c.anchor().getX() - x;
            long dz = c.anchor().getZ() - z;
            if (dx * dx + dz * dz < e2) {
                return true;
            }
        }
        return false;
    }

    /** Les coins et les milieux d'un carré de 5 × 5 sont à un bloc près de la même hauteur (dans le même tronçon ou à côté). */
    private static boolean plat(ServerLevel level, int x, int y, int z) {
        for (int dx = -2; dx <= 2; dx += 2) {
            for (int dz = -2; dz <= 2; dz += 2) {
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
                if (Math.abs(h - y) > 1) {
                    return false;
                }
            }
        }
        return true;
    }
}
