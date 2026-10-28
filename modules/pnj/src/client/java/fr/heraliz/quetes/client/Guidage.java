package fr.heraliz.quetes.client;

import fr.heraliz.quetes.entite.Pnj;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Guidage du nouveau joueur au spawn (28/10, demande du propriétaire) : vers le Guide tant que la visite n'est pas faite,
 * puis vers le Commandant Varga jusqu'à ce qu'il lui parle. Le PNJ à aller voir est celui que le serveur marque d'une
 * lueur ({@link Lueurs#cible}) ; ici, chez ce joueur seulement :
 * <ul>
 *    <li>surbrillance du PNJ (contour vert, {@link Pnj#SURBRILLANCE_CLIENT}) ;</li>
 *    <li>traînée de petites particules vertes au sol, en ligne droite du joueur vers le PNJ, qui « coule » vers lui.</li>
 * </ul>
 * Seulement dans le monde du spawn, PNJ suivi par le jeu, pas pendant une présentation. Coût : la cible est cherchée une
 * fois par seconde, au plus 14 particules toutes les 4 ticks.
 */
final class Guidage {
    private static final ResourceLocation SPAWN = new ResourceLocation("heralisz", "spawn");
    private static final DustParticleOptions VERT = new DustParticleOptions(new Vector3f(0.35F, 1F, 0.5F), 0.9F);
    /** Écart entre deux points de la traînée, longueur montrée au plus, rien de plus près que ça du PNJ. */
    private static final double PAS = 1.6, LONGUEUR = 24, PRES = 2.5;

    /** Identifiant d'entité du PNJ guidé (-1 : aucun). */
    private static int cible = -1;

    private Guidage() {
    }

    static void brancher() {
        Pnj.SURBRILLANCE_CLIENT = p -> p.getId() == cible;
    }

    static void vider() {
        cible = -1;
    }

    static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || !SPAWN.equals(mc.level.dimension().location()) || Presentation.actif()) {
            cible = -1;
            return;
        }
        if (mc.player.tickCount % 20 == 0) chercher(mc);
        if (cible < 0 || mc.isPaused() || mc.player.tickCount % 4 != 0) return;
        Entity e = mc.level.getEntity(cible);
        if (!(e instanceof Pnj) || !e.isAlive()) {
            cible = -1;
            return;
        }
        trainee(mc.level, mc.player.position(), e.position(), mc.player.tickCount);
    }

    /** Le PNJ (guide ou Varga) au-dessus duquel le serveur a posé la lueur d'accueil. */
    private static void chercher(Minecraft mc) {
        int trouve = -1;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Pnj p && ("guide".equals(p.pnjId()) || "varga".equals(p.pnjId())) && Lueurs.cible(e.blockPosition())) {
                trouve = e.getId();
                break;
            }
        }
        cible = trouve;
    }

    /** Points au sol de la traînée, décalés à chaque appel pour donner un mouvement vers le PNJ. */
    private static void trainee(Level level, Vec3 depuis, Vec3 vers, int tick) {
        double dx = vers.x - depuis.x, dz = vers.z - depuis.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < PRES + 0.5) return;
        double ux = dx / d, uz = dz / d;
        double fin = Math.min(d - PRES, LONGUEUR);
        double decalage = (tick / 4 % 4) * (PAS / 4);
        for (double t = 1.0 + decalage; t < fin; t += PAS) {
            double x = depuis.x + ux * t, z = depuis.z + uz * t;
            // hauteur : du joueur vers le PNJ, puis posée sur le premier sol trouvé (3 blocs au-dessus ou en dessous)
            double y = depuis.y + (vers.y - depuis.y) * (t / d);
            double sol = sol(level, x, y, z);
            if (Double.isNaN(sol)) continue;
            level.addParticle(VERT, x, sol + 0.08, z, 0, 0, 0);
        }
    }

    /** Ordre des hauteurs essayées : d'abord vers le bas (le sol sous les pieds), puis un peu au-dessus (marche). */
    private static final int[] HAUTEURS = {0, -1, -2, -3, 1, 2};

    /** Dessus du bloc plein qui porte ce point (de -3 à +2 blocs), NaN s'il n'y en a pas (vide, mur). */
    private static double sol(Level level, double x, double y, double z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), by = (int) Math.floor(y);
        for (int dy : HAUTEURS) {
            m.set(bx, by + dy, bz);
            if (!level.getBlockState(m).isAir() && level.getBlockState(m).isCollisionShapeFullBlock(level, m)) {
                m.move(0, 1, 0);
                if (level.getBlockState(m).isCollisionShapeFullBlock(level, m)) return Double.NaN;   // dans un mur
                return by + dy + 1;
            }
        }
        return Double.NaN;
    }
}
