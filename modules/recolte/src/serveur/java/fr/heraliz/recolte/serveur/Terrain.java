package fr.heraliz.recolte.serveur;

import fr.heraliz.recolte.HeralizRecolte;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Contrôles d'une place avant d'y poser une ressource (A_FAIRE §6, « Apparition ») ; une place refusée est tirée de
 * nouveau ailleurs. Deux temps :
 * <ul>
 *    <li>{@link #raisonSansBloc} : sans lire un bloc (au tirage, puis à la pose) : dans la bordure du monde (ou la zone du
 *    réglage), loin du spawn, hors des villes et de leurs abords, hors des zones VIP, assez loin des autres places ;</li>
 *    <li>{@link #controler} : sur les tronçons DÉJÀ chargés ({@link Lecteur} : jamais un tronçon chargé ici) : aucune
 *    entité de bloc de CRac (base de clan) dans les tronçons voisins ; posée SUR un sol naturel ; emprise au sol libre
 *    (air seulement au-dessus du sol, pas même une fleur : on ne remplace aucun bloc) et à plat (1 bloc d'écart au plus) ;
 *    ciel visible (rien au-dessus : jamais dans un bâtiment ni une grotte) ; pour un arbre, rien de haut dans sa
 *    couronne ; pas d'eau à 3 blocs ; aucun bloc non naturel (route, mur, bâtiment) à 6 blocs.</li>
 * </ul>
 * « Rien au-dessus » se lit dans la carte des hauteurs de Minecraft (WORLD_SURFACE : le plus haut bloc qui n'est pas de
 * l'air, colonne par colonne) : une seule lecture par colonne, quelques centaines de blocs lus par place contrôlée.
 */
final class Terrain {
    private Terrain() {
    }

    /** Raisons d'un refus (clés {@code heraliz_recolte.raison.<code>} de /recolte info), et leur texte français. */
    static final String[] RAISONS = {"bordure", "spawn", "ville", "vip", "ecart", "base", "hauteur", "sol", "emprise", "pente", "eau", "construit"};
    static final String[] RAISONS_FR = {"bord de la carte", "près du spawn", "ville", "zone VIP", "trop près d'une autre ressource",
            "base de clan", "hauteur", "sol pas naturel", "place pas libre", "en pente", "eau", "construction proche"};
    static final int BORDURE = 0, SPAWN = 1, VILLE = 2, VIP = 3, ECART = 4, BASE = 5, HAUTEUR = 6, SOL = 7, EMPRISE = 8, PENTE = 9,
            EAU = 10, CONSTRUIT = 11;
    /** Demi-côté (blocs) de l'emprise au sol autour du bloc de la place : 3 × 3 colonnes (gisement, souche). */
    static final int PIED = 1;
    /** Demi-côté de la couronne d'un arbre : 7 × 7 colonnes. */
    static final int COURONNE = 3;
    /** Hauteur libre demandée au-dessus du sol (le plus grand modèle, l'épicéa : 12,7 m). */
    static final int HAUTEUR_LIBRE = 14;
    /** Bordure du monde trop grande pour être la carte (bordure jamais réglée) : tirage autour du spawn à la place. */
    private static final double BORDURE_MAX = 20_000.0, AUTOUR_DU_SPAWN = 2_000.0;
    private static boolean bordureDite;

    /** Rectangle des tirages {minX, minZ, maxX, maxZ} : bordure du monde moins la marge, réduite à la zone du réglage. */
    static double[] bornes(ServerLevel l, Reglages r) {
        WorldBorder b = l.getWorldBorder();
        double x0 = b.getMinX() + r.margeBordure, z0 = b.getMinZ() + r.margeBordure;
        double x1 = b.getMaxX() - r.margeBordure, z1 = b.getMaxZ() - r.margeBordure;
        if (r.zone != null) {
            x0 = Math.max(x0, r.zone[0]);
            z0 = Math.max(z0, r.zone[1]);
            x1 = Math.min(x1, r.zone[2]);
            z1 = Math.min(z1, r.zone[3]);
        } else if (x1 - x0 > BORDURE_MAX || z1 - z0 > BORDURE_MAX) {
            BlockPos sp = l.getSharedSpawnPos();
            x0 = Math.max(x0, sp.getX() - AUTOUR_DU_SPAWN);
            z0 = Math.max(z0, sp.getZ() - AUTOUR_DU_SPAWN);
            x1 = Math.min(x1, sp.getX() + AUTOUR_DU_SPAWN);
            z1 = Math.min(z1, sp.getZ() + AUTOUR_DU_SPAWN);
            if (!bordureDite) {
                bordureDite = true;
                HeralizRecolte.LOG.warn("[recolte] bordure du monde pas reglee (plus de {} blocs) : ressources tirees a {} blocs du spawn au plus"
                        + " (reglage apparition.zone de recolte.json pour choisir)", (int) BORDURE_MAX, (int) AUTOUR_DU_SPAWN);
            }
        }
        if (x1 < x0) {
            x1 = x0;
        }
        if (z1 < z0) {
            z1 = z0;
        }
        return new double[]{x0, z0, x1, z1};
    }

    /** Raison d'un refus qui ne demande aucun bloc, -1 si le point (x, z) convient pour la place {@code p}. */
    static int raisonSansBloc(ServerLevel l, Places pl, Places.Place p, int x, int z, Reglages r, double[] bornes) {
        if (x < bornes[0] || x > bornes[2] || z < bornes[1] || z > bornes[3]) {
            return BORDURE;
        }
        BlockPos sp = l.getSharedSpawnPos();
        long dx = x - sp.getX(), dz = z - sp.getZ();
        if (r.loinDuSpawn > 0 && dx * dx + dz * dz < (long) r.loinDuSpawn * r.loinDuSpawn) {
            return SPAWN;
        }
        Zones.rafraichir(l.getServer(), r);
        if (Zones.dansUneVille(x + 0.5, z + 0.5, r.abordsVilles)) {
            return VILLE;
        }
        if (Zones.dansUneZoneVip(x, z, r.margeVip)) {
            return VIP;
        }
        if (!pl.loinDesAutres(p, x, z, r)) {
            return ECART;
        }
        return -1;
    }

    /** Tronçons à avoir chargés autour d'une place : ceux des bases à chercher, et assez pour lire tous les contrôles. */
    static int rayonTroncons(Reglages r) {
        int blocs = Math.max(COURONNE, PIED + Math.max(r.eau, r.nonNaturels));
        return Math.max(1, Math.max(r.troncsBases, (blocs + 15) / 16));
    }

    /**
     * Contrôle complet de la place {@code p} sur les tronçons déjà chargés de {@code lc}. Rend la raison du refus, ou -1 :
     * la hauteur des pieds de l'entité est alors dans {@code sortieY[0]}.
     */
    static int controler(ServerLevel l, Lecteur lc, Places pl, Places.Place p, Reglages r, double[] sortieY) {
        int x = p.x, z = p.z;
        int raison = raisonSansBloc(l, pl, p, x, z, r, bornes(l, r));
        if (raison >= 0) {
            return raison;
        }
        if (lc.baseProche(r.troncsBases)) {
            return BASE;
        }
        int sol = lc.dessus(x, z);
        if (sol <= l.getMinBuildHeight() || sol + 1 + HAUTEUR_LIBRE >= l.getMaxBuildHeight()) {
            return HAUTEUR;
        }
        BlockState dessous = lc.bloc(x, sol, z);
        if (!r.sols.contient(dessous)) {
            return SOL;
        }
        // emprise au sol : sur un sol naturel, à plat, rien au-dessus (pas même une fleur), ciel visible
        for (int i = -PIED; i <= PIED; i++) {
            for (int k = -PIED; k <= PIED; k++) {
                int h = lc.dessus(x + i, z + k);
                if (h > sol) {
                    return EMPRISE;
                }
                if (h < sol - r.ecartSol) {
                    return PENTE;
                }
                if (!r.sols.contient(lc.bloc(x + i, h, z + k))) {
                    return SOL;
                }
            }
        }
        // couronne d'un arbre : rien de plus haut qu'un bloc au-dessus du sol (ni mur, ni autre arbre, ni toit)
        if (p.sorte == Reglages.ARBRE) {
            for (int i = -COURONNE; i <= COURONNE; i++) {
                for (int k = -COURONNE; k <= COURONNE; k++) {
                    if (lc.dessus(x + i, z + k) > sol + 1) {
                        return EMPRISE;
                    }
                }
            }
        }
        // pas d'eau à « eau » blocs (surface de chaque colonne et le bloc dessous : eau sous un nénuphar)
        int e = PIED + r.eau;
        for (int i = -e; i <= e; i++) {
            for (int k = -e; k <= e; k++) {
                int h = lc.dessus(x + i, z + k);
                if (fluide(lc.bloc(x + i, h, z + k)) || fluide(lc.bloc(x + i, h - 1, z + k))) {
                    return EAU;
                }
            }
        }
        // aucun bloc non naturel (route, mur, bâtiment, clôture) à « non_naturels » blocs
        int n = PIED + r.nonNaturels;
        for (int i = -n; i <= n; i++) {
            for (int k = -n; k <= n; k++) {
                int h = lc.dessus(x + i, z + k);
                if (!naturel(r, lc.bloc(x + i, h, z + k)) || !naturel(r, lc.bloc(x + i, h - 1, z + k))) {
                    return CONSTRUIT;
                }
            }
        }
        sortieY[0] = sol + hauteurDuDessus(dessous);
        return -1;
    }

    private static boolean fluide(BlockState s) {
        return !s.getFluidState().isEmpty();
    }

    private static boolean naturel(Reglages r, BlockState s) {
        return s.isAir() || r.sols.contient(s) || r.naturels.contient(s);
    }

    /** Hauteur du dessus du bloc de sol (1 pour un bloc plein, moins pour une couche de neige). */
    private static double hauteurDuDessus(BlockState s) {
        VoxelShape forme = s.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        return forme.isEmpty() ? 1.0 : Mth.clamp(forme.max(Direction.Axis.Y), 0.0, 1.0);
    }

    /**
     * Lecture des blocs autour d'une place, seulement sur des tronçons DÉJÀ chargés (pris une fois par
     * {@code getChunkNow}, qui ne charge jamais rien) : hors de ces tronçons, de l'air.
     */
    static final class Lecteur {
        private final LevelChunk[] troncons;
        private final int cx0, cz0, cote, rayon;
        private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        private Lecteur(LevelChunk[] troncons, int cx0, int cz0, int cote, int rayon) {
            this.troncons = troncons;
            this.cx0 = cx0;
            this.cz0 = cz0;
            this.cote = cote;
            this.rayon = rayon;
        }

        /** Le tronçon du point (x, z) est-il déjà chargé ? (rien n'est chargé ici) */
        static boolean charge(ServerLevel l, int x, int z) {
            return l.getChunkSource().getChunkNow(x >> 4, z >> 4) != null;
        }

        /** Les tronçons autour du point (x, z), rayon {@code rayon}, s'ils sont TOUS déjà chargés ; sinon null. */
        @Nullable
        static Lecteur prendre(ServerLevel l, int x, int z, int rayon) {
            int cote = 2 * rayon + 1, cx0 = (x >> 4) - rayon, cz0 = (z >> 4) - rayon;
            LevelChunk[] t = new LevelChunk[cote * cote];
            for (int i = 0; i < cote; i++) {
                for (int k = 0; k < cote; k++) {
                    LevelChunk c = l.getChunkSource().getChunkNow(cx0 + i, cz0 + k);
                    if (c == null) {
                        return null;
                    }
                    t[i * cote + k] = c;
                }
            }
            return new Lecteur(t, cx0, cz0, cote, rayon);
        }

        @Nullable
        private LevelChunk troncon(int x, int z) {
            int i = (x >> 4) - cx0, k = (z >> 4) - cz0;
            return i < 0 || k < 0 || i >= cote || k >= cote ? null : troncons[i * cote + k];
        }

        /** Hauteur du plus haut bloc qui n'est pas de l'air dans la colonne (x, z) ; Integer.MIN_VALUE hors des tronçons. */
        int dessus(int x, int z) {
            LevelChunk c = troncon(x, z);
            return c == null ? Integer.MIN_VALUE : c.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        }

        BlockState bloc(int x, int y, int z) {
            LevelChunk c = troncon(x, z);
            return c == null ? Blocks.AIR.defaultBlockState() : c.getBlockState(m.set(x, y, z));
        }

        /** Une entité de bloc de CRac (pièce de base, armoire, coffre…) dans les tronçons à {@code r} du centre. */
        boolean baseProche(int r) {
            int d = Math.min(r, rayon);
            for (int i = rayon - d; i <= rayon + d; i++) {
                for (int k = rayon - d; k <= rayon + d; k++) {
                    for (BlockEntity be : troncons[i * cote + k].getBlockEntities().values()) {
                        ResourceLocation id = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(be.getType());
                        if (id != null && "crac".equals(id.getNamespace())) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }
}
