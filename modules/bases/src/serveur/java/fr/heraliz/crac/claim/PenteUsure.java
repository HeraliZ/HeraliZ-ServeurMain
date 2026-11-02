package fr.heraliz.crac.claim;

/**
 * Pente de l'usure des bases (HeraliZ, 03/11, demande du propriétaire) : une pièce (ou un objet posé) qui s'use perd
 * {@link #haute} % de ses PV max par heure réelle tant qu'il lui reste plus de {@link #seuil} % de ses PV, puis
 * {@link #basse} % par heure (section {@code [decay]} de {@code crac-server.toml}, relue quand le fichier change). Avec 10,
 * 30 et 5 : 7 h de 100 % à 30 %, puis 6 h jusqu'à 0, soit 13 h pour une pièce intacte, quel que soit son matériau.
 * <p>Aucun reste gardé par pièce : une seule horloge pour toute l'usure (heures écoulées, avancée à chaque passe
 * d'entretien de la minute, {@link ClaimMaintenanceManager}) ; la perte d'une passe est la différence des parties entières
 * de {@code max × pente × heures} après et avant elle. Une pièce de 250 PV à 10 %/h perd ainsi 25 PV par heure, 1 PV une
 * passe sur deux ou trois, sans arrondi qui s'accumule. Java pur (essayé hors du jeu).
 */
public final class PenteUsure {
    /** Pourcentage des PV max perdu par heure au-dessus du seuil, au seuil et en dessous, et le seuil (pourcentage). */
    public final double haute, seuil, basse;

    public PenteUsure(double haute, double seuil, double basse) {
        this.haute = Math.max(0.0, haute);
        this.seuil = Math.max(0.0, Math.min(100.0, seuil));
        this.basse = Math.max(0.0, basse);
    }

    /**
     * PV perdus entre les heures {@code avant} et {@code apres} de l'horloge de l'usure par une pièce qui en a {@code pv}
     * sur {@code max} ; 0 si rien n'est dû pendant cette passe.
     */
    public int perte(int pv, int max, double avant, double apres) {
        if (max <= 0 || pv <= 0 || !(apres > avant)) {
            return 0;
        }
        double pente = pv * 100.0 > this.seuil * max ? this.haute : this.basse;
        double parHeure = max * pente / 100.0;
        long p = (long) Math.floor(parHeure * apres) - (long) Math.floor(parHeure * avant);
        return (int) Math.max(0L, Math.min(p, (long) pv));
    }
}
