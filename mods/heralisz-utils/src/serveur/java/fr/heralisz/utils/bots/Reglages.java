package fr.heralisz.utils.bots;

/**
 * Réglages des bots de charge, changés en jeu par {@code /bots action|joueurs|frappes} (en mémoire seulement : revenus
 * aux valeurs par défaut au redémarrage). Délais en ticks (20 par seconde).
 */
final class Reglages {
    boolean tir = true, grenades = true, fumigenes = true, soins = true, frappes = true, largages = true, fouille = true,
            course = true, reapparition = true;
    /** Les bots tirent aussi sur les vrais joueurs (survie) : non par défaut, pour ne pas gêner le staff qui regarde. */
    boolean joueurs;
    /** Délai entre deux frappes aériennes d'un même bot : 4 à 9 min ; largages : 6 à 12 min. */
    int frappeMin = 4 * 1200, frappeMax = 9 * 1200, largageMin = 6 * 1200, largageMax = 12 * 1200;
    /** Frappes et largages en cours sur tout le serveur au plus (ceux des bots s'arrêtent au-delà). */
    int frappesMax = 6;

    /** Statistiques depuis le dernier {@code /bots etat} (remises à zéro à chaque lecture). */
    static final class Stats {
        long tirs, lancers, soins, fouilles, frappes, largages, morts, depuis = System.currentTimeMillis();

        void zero() {
            tirs = lancers = soins = fouilles = frappes = largages = morts = 0;
            depuis = System.currentTimeMillis();
        }
    }
}
