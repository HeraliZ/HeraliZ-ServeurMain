package fr.heraliz.clashwar.moteur;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Arrivées préchargées des téléportations du ClashWar (19/11, audit des lenteurs C1 et C3). Forge charge le tronçon
 * d'arrivée dès que la position d'un joueur change ; un tronçon absent était alors lu sur le disque et décodé avec ses
 * voisins par le fil du serveur (170 ms à l'entrée dans la salle, jusqu'à 1,5 s au retour d'un match, le 18/11 avec trois
 * joueurs). Ici, un ticket demande les tronçons à Minecraft, qui les lit en tâche de fond et les décode entre les ticks,
 * comme quand on marche ; la téléportation se fait quand ils sont prêts.
 * <ul>
 *     <li>{@link #precharger} : demande les tronçons (5 × 5 autour du point, ticket de 30 s), sans attendre ;</li>
 *     <li>{@link #pret} : les 3 × 3 tronçons autour du point sont-ils chargés (sans rien charger) ;</li>
 *     <li>{@link #puis} : l'action (la téléportation) dès que l'arrivée est prête, au plus 3 s après (passé ce délai,
 *     elle se fait quand même, comme avant).</li>
 * </ul>
 * Même méthode que {@code Prechargement} de HeraliZ Utils et de CRac. Réglage {@code precharger_arrivees} de
 * clashwar.json (faux : téléportation immédiate, comme avant). Fil serveur ; {@link #tick} ne fait rien sans attente.
 */
public final class Arrivees {
    private Arrivees() {
    }

    /** Rayon du ticket : 5 × 5 tronçons complets autour du point. */
    private static final int RAYON = 2;
    /** Au plus ce délai entre la demande et l'action (ticks). */
    private static final int ATTENTE_MAX = 60;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("heraliz_clashwar_arrivee", Comparator.comparingLong(ChunkPos::toLong), 20 * 30);

    private static final class Attente {
        final ServerPlayer joueur;
        final ServerLevel niveau;
        final int cx, cz, limite;
        final Runnable action;

        Attente(ServerPlayer joueur, ServerLevel niveau, int cx, int cz, int limite, Runnable action) {
            this.joueur = joueur;
            this.niveau = niveau;
            this.cx = cx;
            this.cz = cz;
            this.limite = limite;
            this.action = action;
        }
    }

    /** Une attente par joueur (une nouvelle demande remplace l'ancienne : la dernière destination l'emporte). */
    private static final Map<UUID, Attente> ATTENTES = new LinkedHashMap<>();
    private static final List<Attente> FAIRE = new ArrayList<>();
    private static boolean actif = true;
    private static int ticks;
    /** Depuis le démarrage : arrivées déjà prêtes, prêtes après attente, faites au bout du délai, abandonnées (départ). */
    private static long tout, attendues, delai, abandonnees;

    /** Réglage {@code precharger_arrivees} (faux : téléportations immédiates, comme avant le 19/11). */
    public static void regler(boolean precharger) {
        actif = precharger;
    }

    private static int troncon(double v) {
        return Mth.floor(v) >> 4;
    }

    /** Demande à Minecraft les tronçons autour de (x, z) de ce monde, en tâche de fond ; rend tout de suite. */
    public static void precharger(ServerLevel l, double x, double z) {
        if (!actif) return;
        ChunkPos cp = new ChunkPos(troncon(x), troncon(z));
        l.getChunkSource().addRegionTicket(TICKET, cp, RAYON, cp);
    }

    /** Les 3 × 3 tronçons autour de (x, z) sont-ils chargés (sans rien charger) ? Toujours vrai sans préchargement. */
    public static boolean pret(ServerLevel l, double x, double z) {
        return !actif || pret(l, troncon(x), troncon(z));
    }

    private static boolean pret(ServerLevel l, int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (l.getChunkSource().getChunkNow(cx + dx, cz + dz) == null) return false;
            }
        }
        return true;
    }

    /**
     * Fait {@code action} (la téléportation de {@code joueur} vers (x, z) de {@code l}) dès que l'arrivée est chargée : tout
     * de suite si elle l'est déjà (ou sans préchargement), sinon dans quelques ticks (au plus 3 s). Rien si le joueur se
     * déconnecte avant ; une nouvelle demande pour ce joueur remplace celle qui attendait.
     */
    public static void puis(ServerPlayer joueur, ServerLevel l, double x, double z, Runnable action) {
        int cx = troncon(x), cz = troncon(z);
        if (!actif || pret(l, cx, cz)) {
            ATTENTES.remove(joueur.getUUID());
            tout++;
            action.run();
            return;
        }
        ChunkPos cp = new ChunkPos(cx, cz);
        l.getChunkSource().addRegionTicket(TICKET, cp, RAYON, cp);
        ATTENTES.put(joueur.getUUID(), new Attente(joueur, l, cx, cz, ticks + ATTENTE_MAX, action));
    }

    /** Ce joueur attend-il une arrivée ? */
    public static boolean enAttente(UUID joueur) {
        return !ATTENTES.isEmpty() && ATTENTES.containsKey(joueur);
    }

    /** Déconnexion : son attente est oubliée (rien n'est fait). */
    public static void oublier(UUID joueur) {
        if (ATTENTES.remove(joueur) != null) abandonnees++;
    }

    /** Chaque tick (MoteurLogique). */
    static void tick() {
        ticks++;
        if (ATTENTES.isEmpty()) return;
        FAIRE.clear();
        for (Iterator<Attente> it = ATTENTES.values().iterator(); it.hasNext(); ) {
            Attente a = it.next();
            if (a.joueur.isRemoved()) {
                it.remove();
                abandonnees++;
                continue;
            }
            boolean prete = pret(a.niveau, a.cx, a.cz);
            if (prete || ticks >= a.limite) {
                it.remove();
                if (prete) attendues++;
                else delai++;
                FAIRE.add(a);
            }
        }
        // hors de la boucle : une action peut demander une autre arrivée
        for (int i = 0; i < FAIRE.size(); i++) FAIRE.get(i).action.run();
        FAIRE.clear();
    }

    /** Nombre d'attentes en cours (moniteur des listes du noyau). */
    public static int attentes() {
        return ATTENTES.size();
    }

    /** « 12 tout de suite, 30 après attente, 1 au délai, 0 abandonnée » (staff). */
    public static long[] compteurs() {
        return new long[]{tout, attendues, delai, abandonnees};
    }

    static void vider() {
        ATTENTES.clear();
        FAIRE.clear();
    }
}
