package fr.heraliz.banc;

/**
 * Ce que coûte le banc lui-même, mis à part de ce que coûteraient de vrais joueurs (01/11, banc réaliste ; temps du fil
 * serveur, en nanosecondes, depuis le démarrage).
 * <ul>
 *     <li>{@link #banc} : le travail propre au banc, qu'aucun vrai joueur ne demanderait au serveur : le « cerveau » des
 *     bots (chercher une cible, regarder si elle est en vue, choisir où marcher : chez un vrai joueur, c'est son jeu qui
 *     le fait), leur préparation, les rythmes, les relevés de la mesure ;</li>
 *     <li>{@link #JOUEURS} : ce que le serveur fait aussi pour de vrais joueurs, appelé par le banc à la place de leur
 *     connexion : tick de la connexion ({@code doTick}), paquets de position ({@code ChunkMap.move}), gestes (tirs,
 *     lancers, soins, fouilles, frappes, constructions), réapparitions, téléportations, connexions et déconnexions.</li>
 * </ul>
 * Le reste du tick (Minecraft, nos mods, les plugins) est le serveur lui-même : « ms par tick hors banc » = ms par tick
 * moins {@link #banc}. Fil serveur seulement.
 */
final class Couts {
    private Couts() {
    }

    static final int TICK_CONNEXION = 0, DEPLACEMENTS = 1, GESTES = 2, REAPPARITIONS = 3, TELEPORTATIONS = 4, CONNEXIONS = 5;
    /** Clés du rapport (ligne MESURE), dans l'ordre des indices. */
    static final String[] CLES = {"tick_connexion", "deplacements", "gestes", "reapparitions", "teleportations", "connexions"};
    static final String[] NOMS = {"tick de la connexion (doTick)", "paquets de position (ChunkMap.move)",
            "gestes (tirs, lancers, soins, fouilles, frappes, constructions)", "réapparitions", "téléportations",
            "connexions et déconnexions"};

    static final long[] JOUEURS = new long[CLES.length];
    static long banc;

    /** Ajoute le temps écoulé depuis {@code depuis} (nanoTime) à la part {@code i} ; rend l'heure d'arrêt. */
    static long ajouter(int i, long depuis) {
        long t = System.nanoTime();
        JOUEURS[i] += t - depuis;
        return t;
    }

    static long joueurs() {
        long s = 0;
        for (long v : JOUEURS) s += v;
        return s;
    }

    /** Relevé : {banc, JOUEURS...} (copie, pour une différence en fin de mesure). */
    static long[] releve() {
        long[] r = new long[JOUEURS.length + 1];
        r[0] = banc;
        System.arraycopy(JOUEURS, 0, r, 1, JOUEURS.length);
        return r;
    }
}
