package fr.heraliz.crac.bases;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Carrières du monde des bases, des deux côtés : un joueur en survie peut y casser les blocs naturels du type de la
 * carrière (exception à la règle « aucune casse en survie » de {@code event.CasseBlocs}).
 * <ul>
 *   <li>chez le joueur : la liste reçue du serveur ({@code S2CCarrieres}) pour que le clic gauche démarre la casse ;</li>
 *   <li>au serveur : la décision ({@code serveur} branché par la partie serveur, données gardées avec le monde des bases).
 *   Sans partie serveur, rien n'est cassable.</li>
 * </ul>
 */
public final class Carrieres {
    /** Une carrière : cylindre vertical de rayon {@code rayon} autour du centre, de {@code rayon} blocs vers le haut et le bas. */
    public record Zone(String nom, BlockPos centre, int rayon, CarriereType type) {
        public boolean contient(BlockPos p) {
            long dx = p.getX() - this.centre.getX();
            long dz = p.getZ() - this.centre.getZ();
            return dx * dx + dz * dz <= (long) this.rayon * this.rayon && Math.abs(p.getY() - this.centre.getY()) <= this.rayon;
        }

        /** Le bloc est-il un bloc que cette carrière laisse casser (dans la zone et du bon type) ? */
        public boolean permet(BlockPos p, BlockState s) {
            return this.contient(p) && s.is(this.type.tag);
        }
    }

    /** Décision du serveur. */
    public interface Test {
        boolean permet(Level level, BlockPos pos, BlockState state);
    }

    private static volatile Test serveur = (l, p, s) -> false;
    private static volatile List<Zone> vues = List.of();

    private Carrieres() {
    }

    public static void brancherServeur(Test t) {
        serveur = t == null ? (l, p, s) -> false : t;
    }

    /** Carrières du monde des bases reçues du serveur (chez le joueur). */
    public static void vues(List<Zone> zones) {
        vues = List.copyOf(zones);
    }

    public static List<Zone> vues() {
        return vues;
    }

    /** Un joueur en survie peut-il casser ce bloc (monde des bases, dans une carrière, bloc du bon type) ? */
    public static boolean permet(@Nullable Level level, @Nullable BlockPos pos, @Nullable BlockState state) {
        if (level == null || pos == null || state == null || !MondeBases.estBases(level)) {
            return false;
        }
        if (level.isClientSide()) {
            for (Zone z : vues) {
                if (z.permet(pos, state)) {
                    return true;
                }
            }
            return false;
        }
        return serveur.permet(level, pos, state);
    }
}
