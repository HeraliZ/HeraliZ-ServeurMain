package fr.heralisz.utils.spawn;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Tronçons d'arrivée chargés AVANT la téléportation (01/11, rapports de blocage du 28 au 29/10) : un joueur posé dans un
 * tronçon pas encore chargé le faisait charger tout de suite par le fil du serveur (Forge charge le tronçon de destination
 * dès que la position change), lu sur le disque puis décodé avec ses voisins : 220 à 330 ms à chaque départ du spawn vers
 * la carte, 120 à 260 ms au point de réveil de la cinématique. Ici, un ticket demande les tronçons à Minecraft, qui les
 * lit en tâche de fond et les décode morceau par morceau entre les ticks, comme quand on marche ; la téléportation se fait
 * quand ils sont prêts.
 * <ul>
 *     <li>{@link #precharger} : demande les tronçons (5 × 5 autour du point, ticket de 30 s), sans attendre ;</li>
 *     <li>{@link #puis} : fait l'action dès que les 3 × 3 tronçons autour du point sont chargés, au plus 3 s après (passé
 *     ce délai, l'action se fait quand même, comme avant) ;</li>
 *     <li>{@link #garder} : point d'arrivée fixe (réveil de la cinématique) gardé chargé tant que le serveur tourne.</li>
 * </ul>
 * Fil serveur seulement. Rien ne tourne sans attente en cours.
 */
public final class Prechargement {
    private Prechargement() {
    }

    /** Rayon du ticket : 5 × 5 tronçons complets autour du point. */
    private static final int RAYON = 2;
    /** Au plus ce délai entre la demande et l'action (ticks). */
    private static final int ATTENTE_MAX = 60;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("heraliz_arrivee", Comparator.comparingLong(ChunkPos::toLong), 20 * 30);
    /** Sans fin : retiré à la main ({@link #lacher}). */
    private static final TicketType<ChunkPos> TICKET_GARDE = TicketType.create("heraliz_arrivee_fixe", Comparator.comparingLong(ChunkPos::toLong));

    private record Attente(ServerPlayer joueur, ServerLevel niveau, ChunkPos cp, int limite, Runnable action) {
    }

    private static final List<Attente> ATTENTES = new ArrayList<>();
    private static int ticks;

    /** Une fois, par la partie serveur. */
    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(Prechargement::tick);
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> ATTENTES.clear());
    }

    private static ChunkPos troncon(double x, double z) {
        return new ChunkPos(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
    }

    /** Demande à Minecraft de charger les tronçons autour de (x, z), en tâche de fond ; rend tout de suite. */
    public static void precharger(ServerLevel l, double x, double z) {
        ChunkPos cp = troncon(x, z);
        l.getChunkSource().addRegionTicket(TICKET, cp, RAYON, cp);
    }

    /** Les 3 × 3 tronçons autour de (x, z) sont-ils chargés (sans rien charger) ? */
    public static boolean pret(ServerLevel l, double x, double z) {
        return pret(l, troncon(x, z));
    }

    private static boolean pret(ServerLevel l, ChunkPos cp) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (l.getChunkSource().getChunkNow(cp.x + dx, cp.z + dz) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Fait {@code action} (la téléportation de {@code joueur} vers (x, z) de {@code l}) dès que les tronçons d'arrivée sont
     * chargés : tout de suite s'ils le sont déjà, sinon dans quelques ticks (au plus 3 s). Rien si le joueur part ou meurt
     * entre-temps.
     */
    public static void puis(ServerPlayer joueur, ServerLevel l, double x, double z, Runnable action) {
        ChunkPos cp = troncon(x, z);
        if (pret(l, cp)) {
            action.run();
            return;
        }
        l.getChunkSource().addRegionTicket(TICKET, cp, RAYON, cp);
        ATTENTES.add(new Attente(joueur, l, cp, ticks + ATTENTE_MAX, action));
    }

    /** Point d'arrivée fixe gardé chargé (même point rendu par {@link #lacher}). */
    public static void garder(ServerLevel l, double x, double z) {
        ChunkPos cp = troncon(x, z);
        l.getChunkSource().addRegionTicket(TICKET_GARDE, cp, RAYON, cp);
    }

    public static void lacher(ServerLevel l, double x, double z) {
        ChunkPos cp = troncon(x, z);
        l.getChunkSource().removeRegionTicket(TICKET_GARDE, cp, RAYON, cp);
    }

    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
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
            if (ticks >= a.limite() || pret(a.niveau(), a.cp())) {
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
}
