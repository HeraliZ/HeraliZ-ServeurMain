package fr.heraliz.banc;

/**
 * Réglages des bots de charge, changés en jeu par {@code /bots action|joueurs|frappes} (en mémoire seulement : revenus
 * aux valeurs par défaut au redémarrage). Délais en ticks (20 par seconde).
 */
final class Reglages {
    boolean tir = true, grenades = true, fumigenes = true, soins = true, frappes = true, largages = true, fouille = true,
            course = true, reapparition = true, construction = true;
    /** Les bots tirent aussi sur les vrais joueurs (survie) : non par défaut, pour ne pas gêner le staff qui regarde. */
    boolean joueurs;
    /** Délai entre deux frappes aériennes d'un même bot : 4 à 9 min ; largages : 6 à 12 min. */
    int frappeMin = 4 * 1200, frappeMax = 9 * 1200, largageMin = 6 * 1200, largageMax = 12 * 1200;
    /** Frappes et largages en cours sur tout le serveur au plus (ceux des bots s'arrêtent au-delà). */
    int frappesMax = 6;
    /** Petite base de CRac bâtie par un bot : toutes les 2 à 5 min, 2 au plus par bot (tout est effacé à leur départ). */
    int constructionMin = 2 * 1200, constructionMax = 5 * 1200, constructionsParBot = 2;

    /** Rythme d'un scénario de charge ({@code /bots scenario}) : tout arrive dans les premières minutes. */
    void intense() {
        frappeMin = 30 * 20;
        frappeMax = 90 * 20;
        largageMin = 60 * 20;
        largageMax = 150 * 20;
        constructionMin = 10 * 20;
        constructionMax = 60 * 20;
        tir = grenades = fumigenes = soins = frappes = largages = fouille = course = reapparition = construction = true;
    }

    /** Valeurs par défaut (fin d'un scénario). */
    void normal() {
        Reglages d = new Reglages();
        frappeMin = d.frappeMin;
        frappeMax = d.frappeMax;
        largageMin = d.largageMin;
        largageMax = d.largageMax;
        constructionMin = d.constructionMin;
        constructionMax = d.constructionMax;
    }

    /** Statistiques depuis le dernier {@code /bots etat} (remises à zéro à chaque lecture). */
    static final class Stats {
        long tirs, lancers, soins, fouilles, frappes, largages, morts, constructions, depuis = System.currentTimeMillis();

        void zero() {
            tirs = lancers = soins = fouilles = frappes = largages = morts = constructions = 0;
            depuis = System.currentTimeMillis();
        }
    }
}
