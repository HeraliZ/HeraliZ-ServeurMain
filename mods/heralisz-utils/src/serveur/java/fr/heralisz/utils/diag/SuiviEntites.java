package fr.heralisz.utils.diag;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.HashMap;
import java.util.Map;

/**
 * Tick de chaque entité, appelé par le crochet {@code DiagTickEntiteMixin} au début et à la fin de
 * {@code Level.guardEntityTick} (une entité et ses passagers).
 * <ul>
 * <li>Toujours : l'entité en cours est notée (une écriture « opaque », sans barrière : quelques nanosecondes) pour que
 * le chien de garde dise quelle entité tournait pendant un tick long.</li>
 * <li>Pendant {@code /diag profil} seulement : temps de chaque tick d'entité additionné par type (deux lectures de
 * l'horloge par entité).</li>
 * </ul>
 * Tout se passe sur le fil du serveur ({@link #serveur}) : un appel d'un autre fil (monde du client en solo) est ignoré.
 */
public final class SuiviEntites {
    private SuiviEntites() {
    }

    /** Fil du serveur (posé au premier tick). */
    static volatile Thread serveur;
    /** Mesure par type en cours (profil). */
    static volatile boolean mesure;
    /** Vrai dès qu'un appel est arrivé : le crochet est bien posé. */
    static volatile boolean vu;

    @SuppressWarnings("unused")
    private static Entity courante;
    private static final VarHandle COURANTE;

    static {
        try {
            COURANTE = MethodHandles.lookup().findStaticVarHandle(SuiviEntites.class, "courante", Entity.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // fil du serveur seulement
    private static long debutNs;
    private static Entity mesuree;
    /** type -> {nombre de ticks, nanosecondes, pire tick en ns}. */
    private static final Map<EntityType<?>, long[]> TEMPS = new HashMap<>();

    /** Début du tick d'une entité (crochet). Ne lève jamais. */
    public static void debut(Entity e) {
        if (Thread.currentThread() != serveur) return;
        COURANTE.setOpaque(e);
        if (mesure) {
            mesuree = e;
            debutNs = System.nanoTime();
        }
    }

    /** Fin du tick d'une entité (crochet). Ne lève jamais. */
    public static void fin(Entity e) {
        if (Thread.currentThread() != serveur) return;
        if (mesure && mesuree == e && e != null) {
            long d = System.nanoTime() - debutNs;
            long[] t = TEMPS.computeIfAbsent(e.getType(), k -> new long[3]);
            t[0]++;
            t[1] += d;
            if (d > t[2]) t[2] = d;
            mesuree = null;
        }
        COURANTE.setOpaque(null);
        if (!vu) vu = true;
    }

    /** Entité dont le tick est en cours (lu par les fils d'échantillonnage, valeur possiblement en retard d'un instant). */
    static Entity courante() {
        return (Entity) COURANTE.getOpaque();
    }

    /** Début d'un tick du serveur : plus aucune entité en cours (un tick d'entité interrompu par une erreur). */
    static void nouveauTick() {
        COURANTE.setOpaque(null);
        mesuree = null;
    }

    /** Fil du serveur : début de la mesure par type. */
    static void commencerMesure() {
        TEMPS.clear();
        mesuree = null;
        mesure = true;
    }

    /** Fil du serveur : fin de la mesure, copie des temps par type. */
    static Map<EntityType<?>, long[]> finirMesure() {
        mesure = false;
        mesuree = null;
        Map<EntityType<?>, long[]> copie = new HashMap<>();
        TEMPS.forEach((k, v) -> copie.put(k, v.clone()));
        TEMPS.clear();
        return copie;
    }
}
