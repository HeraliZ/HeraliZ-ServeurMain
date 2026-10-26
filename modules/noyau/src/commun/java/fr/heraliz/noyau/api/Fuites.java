package fr.heraliz.noyau.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

/**
 * Listes surveillées par le moniteur du noyau (24/10, « alerte quand une liste grossit sans jamais redescendre ») : un
 * mod y déclare la taille de ses grosses listes en mémoire (joueurs suivis, files d'attente, caches…). Le moniteur relève
 * chaque taille une fois par minute (fil serveur) et prévient le staff si l'une grossit pendant 20 minutes sans jamais
 * redescendre : signe d'une fuite (une entrée ajoutée et jamais retirée).
 * <pre>
 *    Fuites.surveiller("quetes.sessions", SESSIONS::size);
 * </pre>
 * La taille est lue sur le fil serveur : elle doit être rapide (taille d'une collection, pas un parcours).
 */
public final class Fuites {
    private Fuites() {
    }

    private static final Map<String, IntSupplier> SERIES = new ConcurrentHashMap<>();

    public static void surveiller(String nom, IntSupplier taille) {
        SERIES.put(nom, taille);
    }

    public static void oublier(String nom) {
        SERIES.remove(nom);
    }

    /** Copie des listes déclarées (nom -> taille). */
    public static Map<String, IntSupplier> series() {
        return Map.copyOf(SERIES);
    }
}
