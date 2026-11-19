package com.Spartan.integrity;

/**
 * Bibliothèques de Java vues DANS le jeu par le launcher (launcher 1.17.0, 19/11). Le Gardien lit, depuis l'extérieur du
 * jeu, les bibliothèques chargées dans le processus du jeu et ne renvoie que celles d'une courte liste fixe (jamais la
 * liste du joueur), sous la forme « dll.nom » dans le champ des outils de sa ligne signée. Un jeu modifié ne peut ni les
 * cacher ni les inventer : la ligne est signée par le launcher, avec la clé liée au jeton du site.
 *
 * <p>Un jeu lancé par le launcher HeraliZ ne charge jamais ces bibliothèques (aucun agent ni débogueur permis, attache
 * coupée) : chargées quand même, un programme les a injectées en cours de partie. Aucun overlay ni antivirus ne porte
 * ces noms. Java pur (testé hors du jeu).
 */
public final class GardienBibliotheques {
    /** Préfixe des bibliothèques dans le champ des outils de la ligne du Gardien. */
    public static final String PREFIXE = "dll.";

    /**
     * @param forte agent Java ou débogueur : trace certaine d'une injection (expulsion, règle en rodage) ; sinon alerte
     * @param role  ce que fait la bibliothèque, pour le staff
     */
    public record Bibliotheque(String nom, boolean forte, String role) {
    }

    private GardienBibliotheques() {
    }

    /** Bibliothèque décrite par un outil de la ligne du Gardien (« dll.instrument »), ou null si c'est un programme. */
    public static Bibliotheque lire(String outil) {
        if (outil == null || !outil.startsWith(PREFIXE) || outil.length() == PREFIXE.length()) {
            return null;
        }
        String nom = outil.substring(PREFIXE.length());
        return switch (nom) {
            case "instrument" -> new Bibliotheque(nom, true, "agent Java");
            case "jdwp" -> new Bibliotheque(nom, true, "débogueur Java");
            case "dt_socket", "dt_shmem" -> new Bibliotheque(nom, true, "liaison du débogueur Java");
            case "attach" -> new Bibliotheque(nom, false, "attache à un autre programme Java");
            case "management_agent" -> new Bibliotheque(nom, false, "contrôle à distance de Java (JMX)");
            case "sawindbg", "saproc" -> new Bibliotheque(nom, false, "lecture de la mémoire de Java (outil de débogage)");
            default -> new Bibliotheque(nom, false, "bibliothèque signalée par le launcher");
        };
    }
}
