package fr.heraliz.crac.bases;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

/**
 * Tronçons d'arrivée chargés AVANT la téléportation (01/11, rapports de blocage du 28 et du 29/10) : un joueur posé dans
 * un tronçon pas encore chargé le faisait charger tout de suite par le fil du serveur (Forge charge le tronçon de
 * destination dès que la position change), lu sur le disque puis décodé avec ses voisins : 130 à 360 ms à chaque retour
 * d'extraction au point /spawn, jusqu'à 1,5 s pour /spawn. Un ticket demande les tronçons à Minecraft, qui les lit en
 * tâche de fond et les décode entre les ticks, comme quand on marche.
 * <ul>
 *   <li>{@link #precharger} : demande les tronçons (5 × 5 autour du point, ticket de 30 s), sans attendre ;</li>
 *   <li>{@link #puis} : l'action dès que les 3 × 3 tronçons autour du point sont chargés, au plus 3 s après (passé ce
 *   délai, elle se fait quand même, comme avant) ;</li>
 *   <li>{@link #garder} / {@link #lacher} : point d'arrivée fixe (point /spawn) gardé chargé.</li>
 * </ul>
 * Fil serveur seulement ; {@link #tick} ne fait rien sans attente en cours.
 */
final class Prechargement {
    private Prechargement() {
    }

    private static final int RAYON = 2;
    private static final int ATTENTE_MAX = 60;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("crac_arrivee", Comparator.comparingLong(ChunkPos::toLong), 20 * 30);
    private static final TicketType<ChunkPos> TICKET_GARDE = TicketType.create("crac_arrivee_fixe", Comparator.comparingLong(ChunkPos::toLong));

    private record Attente(ServerPlayer joueur, ServerLevel niveau, ChunkPos cp, int limite, Runnable action) {
    }

    private static final List<Attente> ATTENTES = new ArrayList<>();
    private static int ticks;

    private static ChunkPos troncon(double x, double z) {
        return new ChunkPos(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
    }

    static void precharger(ServerLevel l, double x, double z) {
        ChunkPos cp = troncon(x, z);
        l.getChunkSource().addRegionTicket(TICKET, cp, RAYON, cp);
    }

    /** Ticket sur une zone plus grande (parcelle du tutoriel), même durée. */
    static void precharger(ServerLevel l, ChunkPos cp, int rayon) {
        l.getChunkSource().addRegionTicket(TICKET, cp, rayon, cp);
    }

    static boolean pret(ServerLevel l, ChunkPos cp, int rayon) {
        for (int dx = -rayon; dx <= rayon; dx++) {
            for (int dz = -rayon; dz <= rayon; dz++) {
                if (l.getChunkSource().getChunkNow(cp.x + dx, cp.z + dz) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /** L'action (téléportation du joueur vers (x, z) de l) dès que l'arrivée est chargée ; rien s'il part ou meurt avant. */
    static void puis(ServerPlayer joueur, ServerLevel l, double x, double z, Runnable action) {
        ChunkPos cp = troncon(x, z);
        if (pret(l, cp, 1)) {
            action.run();
            return;
        }
        l.getChunkSource().addRegionTicket(TICKET, cp, RAYON, cp);
        ATTENTES.add(new Attente(joueur, l, cp, ticks + ATTENTE_MAX, action));
    }

    static void garder(ServerLevel l, double x, double z) {
        ChunkPos cp = troncon(x, z);
        l.getChunkSource().addRegionTicket(TICKET_GARDE, cp, RAYON, cp);
    }

    static void lacher(ServerLevel l, double x, double z) {
        ChunkPos cp = troncon(x, z);
        l.getChunkSource().removeRegionTicket(TICKET_GARDE, cp, RAYON, cp);
    }

    /** Chaque tick (Deplacements.tick). */
    static void tick() {
        ticks++;
        if (ATTENTES.isEmpty()) {
            return;
        }
        List<Runnable> faire = null;
        for (Iterator<Attente> it = ATTENTES.iterator(); it.hasNext(); ) {
            Attente a = it.next();
            if (a.joueur().isRemoved() || !a.joueur().isAlive()) {
                it.remove();
                continue;
            }
            if (ticks >= a.limite() || pret(a.niveau(), a.cp(), 1)) {
                it.remove();
                if (faire == null) {
                    faire = new ArrayList<>();
                }
                faire.add(a.action());
            }
        }
        if (faire != null) {
            faire.forEach(Runnable::run);
        }
    }

    static void vider() {
        ATTENTES.clear();
    }
}
