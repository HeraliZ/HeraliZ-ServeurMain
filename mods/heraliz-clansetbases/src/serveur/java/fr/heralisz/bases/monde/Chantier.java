package fr.heralisz.bases.monde;

import fr.heralisz.bases.BasesConfig;
import fr.heralisz.bases.HeraliszBases;
import fr.heralisz.bases.model.Boite;
import fr.heralisz.bases.model.Plan;
import fr.heralisz.bases.registry.BasesRegistry;
import fr.heralisz.bases.registry.Sons;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Travaux de blocs par tranches dans la dimension des bases (BASES_SPEC §2 : budget de {@code budget_blocs_tick} blocs
 * par tick, 20 000 par defaut, partage entre tous les travaux). Trois sortes de travaux :
 * <ul>
 *   <li>{@link Pose} : le bunker du modele, puis les blocs de sortie, puis les cloisons des salles deja achetees ;</li>
 *   <li>{@link Effacement} : tout bloc non vide de la parcelle redevient de l'air (entites retirees au debut) ;</li>
 *   <li>{@link Cloison} : deblocage d'une salle, la cloison se desagrege en 3 s (particules maison, son) — hors budget.</li>
 * </ul>
 * Pendant un travail, un ticket garde les chunks de la parcelle charges. Drapeaux de pose : envoi aux clients, pas de
 * mise a jour des voisins (rien ne tombe ni ne casse pendant la pose). Fil serveur uniquement.
 */
public final class Chantier {
    private Chantier() {
    }

    private static final TicketType<ChunkPos> TICKET = TicketType.create("heralisz_bases_chantier", Comparator.comparingLong(ChunkPos::toLong));
    private static final int DRAPEAUX = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Marge autour de la structure effacee (blocs). */
    public static final int MARGE = 4;

    private static final Deque<Travail> FILE = new ArrayDeque<>();
    private static final List<Cloison> CLOISONS = new ArrayList<>();
    /**
     * Travaux finis dont le ticket est garde encore un peu : le moteur de lumiere traite les blocs poses en differe ; si
     * les chunks se dechargent avant, ils sont sauvegardes avec une lumiere incomplete (salles noires malgre leurs
     * lampes, constate au banc de validation). Ticket rendu apres {@link #GARDE_MIN} ticks et sans travail de lumiere en
     * attente, au plus tard apres {@link #GARDE_MAX} ticks.
     */
    private static final List<Travail> LIBERATIONS = new ArrayList<>();
    private static final int GARDE_MIN = 60, GARDE_MAX = 600;

    /** Un travail par tranches sur une parcelle. {@code fini} est appele une fois, a la fin. */
    public abstract static class Travail {
        final ServerLevel level;
        final int parcelle;
        final BlockPos origine;
        final Consumer<Travail> fini;
        final ChunkPos centre;
        final int rayon;
        boolean demarre;
        long faits;
        int garde;

        Travail(ServerLevel level, int parcelle, BlockPos origine, int tx, int tz, Consumer<Travail> fini) {
            this.level = level;
            this.parcelle = parcelle;
            this.origine = origine;
            this.fini = fini;
            this.centre = new ChunkPos(new BlockPos(origine.getX() + tx / 2, 0, origine.getZ() + tz / 2));
            this.rayon = (Math.max(tx, tz) / 2 + MARGE) / 16 + 2;
        }

        public int parcelle() {
            return parcelle;
        }

        /** Avancement 0..1. */
        public abstract float progression();

        /** Avance d'au plus {@code budget} blocs ; renvoie le nombre de blocs traites, -1 si termine. */
        abstract int avancer(int budget);

        void demarrer() {
            level.getChunkSource().addRegionTicket(TICKET, centre, rayon, centre);
        }

        void terminer() {
            level.getChunkSource().removeRegionTicket(TICKET, centre, rayon, centre);
        }
    }

    // ------------------------------------------------------------------ pose
    /** Pose du modele dans une parcelle (idempotente : peut etre rejouee apres un arret). */
    public static final class Pose extends Travail {
        private final Modele m;
        private final List<Plan.Salle> cloisonsOuvertes;
        private int i, phase, cloison;
        private final List<BlockPos> aOuvrir = new ArrayList<>();

        public Pose(ServerLevel level, int parcelle, Modele m, List<Plan.Salle> sallesOuvertes, Consumer<Travail> fini) {
            super(level, parcelle, Grille.origine(parcelle, m.plan.tx, m.plan.tz), m.plan.tx, m.plan.tz, fini);
            this.m = m;
            this.cloisonsOuvertes = sallesOuvertes;
        }

        @Override
        public float progression() {
            return m.nombre() == 0 ? 1f : Math.min(1f, i / (float) m.nombre());
        }

        @Override
        int avancer(int budget) {
            int n = 0;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            if (phase == 0) {
                while (i < m.nombre() && n < budget) {
                    int q = m.positions[i];
                    p.set(origine.getX() + Modele.x(q), origine.getY() + Modele.y(q), origine.getZ() + Modele.z(q));
                    BlockState s = m.palette[m.etats[i]];
                    level.setBlock(p, s, DRAPEAUX);
                    CompoundTag t = m.nbt.get(i);
                    if (t != null) {
                        BlockEntity be = level.getBlockEntity(p);
                        if (be != null) {
                            CompoundTag c = t.copy();
                            c.putInt("x", p.getX());
                            c.putInt("y", p.getY());
                            c.putInt("z", p.getZ());
                            try {
                                be.load(c);
                                be.setChanged();
                            } catch (RuntimeException ex) {
                                HeraliszBases.LOG.warn("[bases] BlockEntity illisible en {} : {}", p, ex.toString());
                            }
                        }
                    }
                    i++;
                    n++;
                }
                if (i >= m.nombre()) phase = 1;
                return n;
            }
            if (phase == 1) {
                BlockState sortie = BasesRegistry.SORTIE.get().defaultBlockState();
                for (int[] s : m.sortiesAjoutees) {
                    p.set(origine.getX() + s[0], origine.getY() + s[1], origine.getZ() + s[2]);
                    level.setBlock(p, sortie, DRAPEAUX);
                    n++;
                }
                // cloisons des salles deja achetees (repose apres un wipe ou une reinitialisation)
                for (Plan.Salle salle : cloisonsOuvertes) {
                    for (Boite b : salle.cloison()) {
                        for (int x = b.x0(); x <= b.x1(); x++)
                            for (int y = b.y0(); y <= b.y1(); y++)
                                for (int z = b.z0(); z <= b.z1(); z++)
                                    aOuvrir.add(new BlockPos(origine.getX() + x, origine.getY() + y, origine.getZ() + z));
                    }
                }
                phase = 2;
                return n;
            }
            if (phase == 2) {
                while (cloison < aOuvrir.size() && n < budget) {
                    level.setBlock(aOuvrir.get(cloison++), Blocks.AIR.defaultBlockState(), DRAPEAUX);
                    n++;
                }
                if (cloison >= aOuvrir.size()) return -1;
                return n;
            }
            return -1;
        }
    }

    // ------------------------------------------------------------------ effacement
    /** Effacement d'une parcelle : volume de la structure + {@link #MARGE}, de haut en bas. */
    public static final class Effacement extends Travail {
        private final int x0, y0, z0, x1, y1, z1;
        private int x, y, z;
        private final long total;
        private long vus;

        public Effacement(ServerLevel level, int parcelle, int tx, int ty, int tz, Consumer<Travail> fini) {
            super(level, parcelle, Grille.origine(parcelle, tx, tz), tx, tz, fini);
            this.x0 = origine.getX() - MARGE;
            this.z0 = origine.getZ() - MARGE;
            this.x1 = origine.getX() + tx - 1 + MARGE;
            this.z1 = origine.getZ() + tz - 1 + MARGE;
            this.y0 = Math.max(level.getMinBuildHeight(), origine.getY() - MARGE);
            this.y1 = Math.min(level.getMaxBuildHeight() - 1, origine.getY() + ty - 1 + MARGE);
            this.x = x0;
            this.y = y1;
            this.z = z0;
            this.total = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
        }

        @Override
        void demarrer() {
            super.demarrer();
            // objets au sol, animaux, corps... (les joueurs ont deja ete sortis)
            AABB zone = new AABB(x0, y0, z0, x1 + 1, y1 + 1, z1 + 1);
            for (Entity e : level.getEntities((Entity) null, zone, e -> !(e instanceof Player))) e.discard();
        }

        @Override
        public float progression() {
            return total == 0 ? 1f : Math.min(1f, vus / (float) total);
        }

        @Override
        int avancer(int budget) {
            int n = 0;
            // on compte les blocs examines (lire l'air coute peu) : 4 lectures = 1 bloc du budget
            int lectures = 0;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            BlockState air = Blocks.AIR.defaultBlockState();
            while (y >= y0 && n < budget && lectures < budget * 4) {
                p.set(x, y, z);
                lectures++;
                vus++;
                if (!level.getBlockState(p).isAir()) {
                    // un conteneur se vide sans rien lacher : on retire le BlockEntity avant de poser l'air
                    level.removeBlockEntity(p);
                    level.setBlock(p, air, DRAPEAUX);
                    n++;
                }
                if (++x > x1) {
                    x = x0;
                    if (++z > z1) {
                        z = z0;
                        y--;
                    }
                }
            }
            return y < y0 ? -1 : Math.max(1, n);
        }
    }

    // ------------------------------------------------------------------ cloison (animation)
    /** Cloison qui se desagrege en {@link #DUREE} ticks : blocs retires dans un ordre aleatoire, poussiere et son. */
    public static final class Cloison {
        static final int DUREE = 60;
        private final ServerLevel level;
        private final List<BlockPos> blocs;
        private int tick, fait;
        private final Runnable fini;

        Cloison(ServerLevel level, List<BlockPos> blocs, Runnable fini) {
            this.level = level;
            this.blocs = blocs;
            this.fini = fini;
        }

        /** Vrai quand c'est termine. */
        boolean avancer() {
            tick++;
            int cible = (int) Math.ceil(blocs.size() * Math.min(1.0, tick / (double) DUREE));
            BlockState air = Blocks.AIR.defaultBlockState();
            while (fait < cible) {
                BlockPos p = blocs.get(fait++);
                if (!level.getBlockState(p).isAir()) {
                    level.setBlock(p, air, Block.UPDATE_ALL);
                    level.sendParticles(BasesRegistry.POUSSIERE_COQUE.get(), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6,
                            0.3, 0.3, 0.3, 0.02);
                }
            }
            if (fait >= blocs.size()) {
                fini.run();
                return true;
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ file
    public static void ajouter(Travail t) {
        FILE.addLast(t);
    }

    /** Lance la desagregation d'une cloison (positions absolues) ; {@code fini} est appele a la fin. */
    public static void desagreger(ServerLevel level, BlockPos origine, List<Boite> cloison, Runnable fini) {
        List<BlockPos> l = new ArrayList<>();
        double cx = 0, cy = 0, cz = 0;
        for (Boite b : cloison) {
            for (int x = b.x0(); x <= b.x1(); x++)
                for (int y = b.y0(); y <= b.y1(); y++)
                    for (int z = b.z0(); z <= b.z1(); z++) {
                        l.add(origine.offset(x, y, z));
                        cx += origine.getX() + x;
                        cy += origine.getY() + y;
                        cz += origine.getZ() + z;
                    }
        }
        if (l.isEmpty()) {
            fini.run();
            return;
        }
        // haut d'abord, avec du hasard : la cloison s'effrite plutot qu'elle ne disparait d'un bloc
        RandomSource r = RandomSource.create(l.size());
        l.sort(Comparator.comparingDouble(p -> -p.getY() * 1.5 + r.nextDouble() * 4));
        level.playSound(null, cx / l.size(), cy / l.size(), cz / l.size(), Sons.CLOISON.get(), SoundSource.BLOCKS, 2.0f, 1.0f);
        CLOISONS.add(new Cloison(level, l, fini));
    }

    /** Travail en cours ou en attente pour cette parcelle (null sinon). */
    @Nullable
    public static Travail travail(int parcelle) {
        for (Travail t : FILE) if (t.parcelle == parcelle) return t;
        return null;
    }

    public static boolean occupe(int parcelle) {
        return travail(parcelle) != null;
    }

    /** A appeler a chaque fin de tick serveur. */
    public static void tick() {
        for (Iterator<Cloison> it = CLOISONS.iterator(); it.hasNext(); ) {
            if (it.next().avancer()) it.remove();
        }
        for (Iterator<Travail> it = LIBERATIONS.iterator(); it.hasNext(); ) {
            Travail t = it.next();
            if (++t.garde < GARDE_MIN) continue;
            if (t.garde < GARDE_MAX && t.level.getChunkSource().getLightEngine().hasLightWork()) continue;
            t.terminer();
            it.remove();
        }
        int budget = BasesConfig.BUDGET_BLOCS_TICK.get();
        while (budget > 0 && !FILE.isEmpty()) {
            Travail t = FILE.peekFirst();
            if (!t.demarre) {
                t.demarre = true;
                t.demarrer();
            }
            int n = t.avancer(budget);
            if (n < 0) {
                FILE.pollFirst();
                t.garde = 0;
                LIBERATIONS.add(t); // ticket rendu une fois la lumiere calculee
                try {
                    t.fini.accept(t);
                } catch (RuntimeException ex) {
                    HeraliszBases.LOG.error("[bases] fin de travail en erreur (parcelle {})", t.parcelle, ex);
                }
            } else {
                t.faits += n;
                budget -= Math.max(1, n);
            }
        }
    }

    /** Arret du serveur : tout est abandonne (les bases en POSE / EFFACEMENT reprennent au prochain demarrage). */
    public static void vider() {
        for (Travail t : FILE) {
            if (t.demarre) t.terminer();
        }
        for (Travail t : LIBERATIONS) t.terminer();
        LIBERATIONS.clear();
        FILE.clear();
        CLOISONS.clear();
    }
}
