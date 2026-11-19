package fr.heraliz.noyau.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import javax.annotation.Nullable;

/**
 * Tronçons « vraiment prêts » (19/11, audit des lenteurs, point C4). {@code Level.isLoaded} et {@code hasChunkAt} disent
 * seulement qu'un tronçon est DEMANDÉ (son ticket permet qu'il soit complet), pas qu'il l'est : juste après une
 * téléportation, au bord de la vue ou quand un joueur va vite, le tronçon est encore en lecture. Le {@code getBlockState},
 * {@code getBlockEntity} ou la recherche de collisions qui suit ATTEND alors sa fin sur le fil du serveur, et fait pendant
 * ce temps tout le travail en file (des centaines de tronçons) : 50 à 700 ms de gel, vus dans les rapports du 18 et du 19/11
 * ({@code Territoires.rafraichirDrapeau}, Spartan {@code supportNear}).
 * <p>Ici, la seule question posée est celle de {@code getChunkNow} : le tronçon est-il déjà complet ? Jamais d'attente,
 * jamais de chargement. Réponse « pas prêt » : faire comme pour un tronçon pas chargé (passer, réessayer plus tard).
 * Fil du serveur seulement (ailleurs, Minecraft répond toujours « pas prêt »). Côté joueur (monde du jeu), la même
 * question est posée à son cache de tronçons.
 * <p>Classe lue par d'autres mods : ne pas la renommer, ni ses méthodes.
 */
public final class Troncons {
    private Troncons() {
    }

    /**
     * Attentes évitées depuis le démarrage : réponses « pas prêt » pour un tronçon que {@code isLoaded} aurait dit chargé
     * (le fil du serveur l'aurait attendu). Fil du serveur seulement, pour {@code /diag etat}.
     */
    public static long attentesEvitees;

    /** Le tronçon (cx, cz) s'il est complet, sinon null ; ne charge rien, n'attend rien. */
    @Nullable
    public static LevelChunk pret(Level niveau, int cx, int cz) {
        LevelChunk c = niveau.getChunkSource().getChunkNow(cx, cz);
        if (c == null && niveau instanceof ServerLevel s && s.getChunkSource().hasChunk(cx, cz)) {
            attentesEvitees++;
        }
        return c;
    }

    /** Le tronçon du bloc s'il est complet, sinon null. */
    @Nullable
    public static LevelChunk pret(Level niveau, BlockPos pos) {
        return pret(niveau, pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** À la place de {@code isLoaded(pos)} : vrai seulement si le tronçon du bloc est complet. */
    public static boolean charge(Level niveau, BlockPos pos) {
        return pret(niveau, pos) != null;
    }

    /**
     * Tous les tronçons qui couvrent les blocs de (x0, z0) à (x1, z1) (bornes comprises, en blocs) sont complets. Sert aux
     * boîtes qui débordent sur le tronçon voisin (collisions, recherches autour d'un joueur).
     */
    public static boolean zone(Level niveau, int x0, int z0, int x1, int z1) {
        int cx1 = Math.max(x0, x1) >> 4, cz1 = Math.max(z0, z1) >> 4;
        for (int cx = Math.min(x0, x1) >> 4; cx <= cx1; cx++) {
            for (int cz = Math.min(z0, z1) >> 4; cz <= cz1; cz++) {
                if (pret(niveau, cx, cz) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Les (2r + 1) × (2r + 1) tronçons autour de (cx, cz) sont complets. */
    public static boolean autour(Level niveau, int cx, int cz, int rayon) {
        for (int dx = -rayon; dx <= rayon; dx++) {
            for (int dz = -rayon; dz <= rayon; dz++) {
                if (pret(niveau, cx + dx, cz + dz) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /** L'état du bloc si son tronçon est complet (lu par le monde, comme {@code getBlockState}), sinon null. */
    @Nullable
    public static BlockState etat(Level niveau, BlockPos pos) {
        return pret(niveau, pos) == null ? null : niveau.getBlockState(pos);
    }

    /** L'entité de bloc si son tronçon est complet (lue par le monde, comme {@code getBlockEntity}), sinon null. */
    @Nullable
    public static BlockEntity entite(Level niveau, BlockPos pos) {
        return pret(niveau, pos) == null ? null : niveau.getBlockEntity(pos);
    }
}
