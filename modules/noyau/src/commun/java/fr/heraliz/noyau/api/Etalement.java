package fr.heraliz.noyau.api;

/**
 * Tâches périodiques décalées (19/11, audit des lenteurs, point C2) : la sauvegarde automatique de Minecraft tombe sur les
 * ticks multiples de 6 000 (toutes les 5 min, {@code ticks-per.autosave} de bukkit.yml). Nos sauvegardes toutes les minutes
 * ou toutes les 30 s (clans, banque, saison, quêtes, butin des zombies…) partaient d'un compteur lancé au démarrage : une
 * fois sur cinq (ou dix), elles s'ajoutaient au même tick que celle de Minecraft (rapports du 18 et du 19/11).
 * <p>{@link #decalage} donne à chaque tâche un décalage fixe tiré de son nom, jamais 0 ni proche de 0 : une tâche dont la
 * période divise 6 000 (20, 100, 600, 1 200, 6 000…) ne tombe JAMAIS sur le tick de la sauvegarde automatique, et deux
 * tâches de même période tombent le plus souvent sur des ticks différents.
 * <pre>
 *    private static final int DECALAGE = Etalement.decalage("clans.sauvegarde", 1200);
 *    if (Etalement.tour(server.getTickCount(), 1200, DECALAGE)) sauvegarder();
 *    // ou, avec un compteur à soi qui part de 0 : int ticks = DECALAGE;  if (++ticks >= 1200) { ticks = 0; ... }
 * </pre>
 * Classe lue par d'autres mods : ne pas la renommer, ni ses méthodes.
 */
public final class Etalement {
    private Etalement() {
    }

    /** Période de la sauvegarde automatique de Minecraft (ticks). */
    public static final int SAUVEGARDE_AUTO = 6000;

    /**
     * Décalage (ticks) de la tâche {@code nom} de période {@code periode} : toujours le même pour un même nom, entre un
     * vingtième de la période et la période moins un vingtième (au moins 1 tick de chaque côté). 0 pour une période de
     * moins de 4 ticks.
     */
    public static int decalage(String nom, int periode) {
        if (periode < 4) return 0;
        int marge = Math.max(1, periode / 20);
        int plage = periode - 2 * marge + 1;
        int h = nom.hashCode() * 0x9E3779B9;
        h ^= h >>> 15;
        return marge + Math.floorMod(h, plage);
    }

    /** Vrai une fois par période : quand {@code tick} vaut {@code decalage} modulo {@code periode}. */
    public static boolean tour(long tick, int periode, int decalage) {
        return periode > 0 && Math.floorMod(tick - decalage, (long) periode) == 0;
    }
}
