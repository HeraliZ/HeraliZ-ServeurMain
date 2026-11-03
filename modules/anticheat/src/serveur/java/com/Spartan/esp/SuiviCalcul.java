package com.Spartan.esp;

/**
 * Mesure d'un « suivi à travers les murs » (03/11, demande du propriétaire) : un observateur dont la visée suit un joueur
 * qu'il ne peut PAS voir. Java pur, sans Minecraft (testé hors du jeu : SuiviCalculTest). {@link SuiviMurs} décide quand
 * un échantillon compte (cible cachée, ni entendue ni vue récemment…) ; ici on ne fait que mesurer un épisode.
 *
 * <p>Un épisode suit UNE paire (observateur → cible cachée), échantillon par échantillon (10 par seconde par défaut) :
 * <ul>
 *   <li><b>angle</b> entre la visée et la direction de la cible (centre de sa hitbox) ; « sur la cible » si l'angle est sous
 *   la taille apparente de la cible plus une marge ;</li>
 *   <li><b>corrélation</b> entre les mouvements de la visée (en lacet) et ceux de la direction de la cible : la visée suit
 *   les déplacements, pas seulement un point fixe ;</li>
 *   <li><b>balayage dû à la cible</b> : de combien de degrés la cible s'est déplacée vue de l'observateur, observateur
 *   supposé immobile (tenir un angle sur un ennemi qui ne bouge pas, ou tourner autour d'un point en se déplaçant soi-même,
 *   ne compte pas) ;</li>
 *   <li><b>virages suivis</b> : la cible change de sens (vue de l'observateur) et la visée reste sur elle. Suivre une cible
 *   qui court tout droit derrière un mur se devine (on prolonge son mouvement) ; suivre ses changements de direction, non.</li>
 * </ul>
 * Un épisode est suspect seulement si TOUT est réuni (durée, part du temps sur la cible, balayage, corrélation, virages) :
 * seuils prudents, mieux vaut rater un tricheur que d'accuser un innocent.
 */
public final class SuiviCalcul {
    private SuiviCalcul() {
    }

    /**
     * @param margeDegres         marge ajoutée à la taille apparente de la cible (degrés)
     * @param rayonCible          demi-taille de la cible prise pour sa taille apparente (blocs)
     * @param echantillonsMin     durée minimale d'un épisode suspect (échantillons)
     * @param partSurCible        part minimale des échantillons « sur la cible »
     * @param balayageMinDegres   balayage minimal dû à la cible (degrés cumulés)
     * @param correlationMin      corrélation minimale visée / direction de la cible
     * @param viragesMin          virages de la cible suivis, au minimum
     * @param horsCibleMax        échantillons hors de la cible de suite avant de clore l'épisode
     * @param seuilVirage         vitesse (degrés par échantillon) au-dessus de laquelle la cible « va » dans un sens
     */
    public record Reglages(double margeDegres, double rayonCible, int echantillonsMin, double partSurCible,
                           double balayageMinDegres, double correlationMin, int viragesMin, int horsCibleMax,
                           double seuilVirage) {
    }

    /** Chiffres d'un épisode (pour l'alerte et le journal). */
    public record Bilan(int echantillons, double angleMoyen, double partSurCible, double balayage, double correlation,
                        int virages, double distanceMoyenne) {
    }

    /** Un épisode en cours (une paire observateur → cible). Un seul fil (celui du serveur). */
    /** Échantillons par fenêtre de la corrélation. */
    static final int FENETRE = 3;

    public static final class Episode {
        boolean premier = true;
        double pEx;
        double pEz;
        double pTx;
        double pTz;
        double pYaw;
        int n;
        int nSur;
        int horsDeSuite;
        double sommeAngle;
        double sommeDistance;
        // corrélation (Pearson) entre les variations de la visée et de la direction de la cible, mesurées par fenêtres de
        // FENETRE échantillons (0,3 s) : le tremblement de la main d'un échantillon à l'autre ne compte pas
        int k;
        double fYaw;
        double fLacet;
        int m;
        double sv;
        double sb;
        double svv;
        double sbb;
        double svb;
        double balayage;
        double lisse;
        int signe;
        int virages;
        /** Déjà compté comme suspect (un épisode ne compte qu'une fois). */
        public boolean compte;
        /** Tick du serveur du premier et du dernier échantillon. */
        public long debut;
        public long dernier;
        /** Échantillons depuis le dernier contrôle « un allié la voit » (SuiviMurs). */
        public int depuisAllie;

        public int echantillons() {
            return n;
        }
    }

    /** Ramène un angle (degrés) dans ]-180, 180]. */
    static double ramener(double a) {
        a %= 360.0;
        if (a > 180.0) {
            a -= 360.0;
        } else if (a <= -180.0) {
            a += 360.0;
        }
        return a;
    }

    /** Lacet (convention de Minecraft) pour regarder de (ex, ez) vers (tx, tz). */
    static double lacetVers(double ex, double ez, double tx, double tz) {
        return Math.toDegrees(Math.atan2(-(tx - ex), tz - ez));
    }

    /**
     * Angle (degrés) entre la visée (lacet, tangage de Minecraft) et la direction œil → point visé.
     */
    public static double ecart(double ex, double ey, double ez, double lacet, double tangage, double tx, double ty, double tz) {
        double y = Math.toRadians(lacet);
        double p = Math.toRadians(tangage);
        double lx = -Math.sin(y) * Math.cos(p);
        double ly = -Math.sin(p);
        double lz = Math.cos(y) * Math.cos(p);
        double dx = tx - ex;
        double dy = ty - ey;
        double dz = tz - ez;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1e-6) {
            return 0;
        }
        double c = (lx * dx + ly * dy + lz * dz) / d;
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, c))));
    }

    /** Tolérance « sur la cible » à cette distance : taille apparente de la cible plus la marge (degrés). */
    public static double tolerance(Reglages r, double distance) {
        return Math.toDegrees(Math.atan2(r.rayonCible(), Math.max(0.5, distance))) + r.margeDegres();
    }

    /** La visée est-elle sur la cible ? (pour démarrer un épisode) */
    public static boolean surCible(Reglages r, double ex, double ey, double ez, double lacet, double tangage,
                                   double tx, double ty, double tz) {
        double dx = tx - ex;
        double dy = ty - ey;
        double dz = tz - ez;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return ecart(ex, ey, ez, lacet, tangage, tx, ty, tz) <= tolerance(r, d);
    }

    /**
     * Ajoute un échantillon. @return false si l'épisode est fini (visée restée trop longtemps hors de la cible).
     */
    public static boolean ajouter(Episode e, Reglages r, double ex, double ey, double ez, double lacet, double tangage,
                                  double tx, double ty, double tz) {
        double dx = tx - ex;
        double dy = ty - ey;
        double dz = tz - ez;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double angle = ecart(ex, ey, ez, lacet, tangage, tx, ty, tz);
        boolean sur = angle <= tolerance(r, d);
        if (!e.premier) {
            double avant = lacetVers(e.pEx, e.pEz, e.pTx, e.pTz);
            double dV = ramener(lacet - e.pYaw);
            double dB = ramener(lacetVers(ex, ez, tx, tz) - avant);
            // part due à la cible seule : observateur gardé à sa place d'avant
            double vT = ramener(lacetVers(e.pEx, e.pEz, tx, tz) - avant);
            if (Math.abs(dV) <= 60 && Math.abs(dB) <= 60) { // au-delà : téléportation, demi-tour d'un coup (pas un suivi)
                if (++e.k >= FENETRE) {
                    double wV = ramener(lacet - e.fYaw);
                    double wB = ramener(lacetVers(ex, ez, tx, tz) - e.fLacet);
                    e.k = 0;
                    e.fYaw = lacet;
                    e.fLacet = lacetVers(ex, ez, tx, tz);
                    e.m++;
                    e.sv += wV;
                    e.sb += wB;
                    e.svv += wV * wV;
                    e.sbb += wB * wB;
                    e.svb += wV * wB;
                }
                e.balayage += Math.abs(vT);
                e.lisse = 0.6 * e.lisse + 0.4 * vT;
                int s = e.lisse >= r.seuilVirage() ? 1 : e.lisse <= -r.seuilVirage() ? -1 : 0;
                if (s != 0) {
                    if (e.signe != 0 && s != e.signe && sur) {
                        e.virages++;
                    }
                    e.signe = s;
                }
            } else {
                e.k = 0; // fenêtre reprise de zéro
                e.fYaw = lacet;
                e.fLacet = lacetVers(ex, ez, tx, tz);
            }
        } else {
            e.fYaw = lacet;
            e.fLacet = lacetVers(ex, ez, tx, tz);
        }
        e.premier = false;
        e.pEx = ex;
        e.pEz = ez;
        e.pTx = tx;
        e.pTz = tz;
        e.pYaw = lacet;
        e.n++;
        e.sommeAngle += angle;
        e.sommeDistance += d;
        if (sur) {
            e.nSur++;
            e.horsDeSuite = 0;
        } else {
            e.horsDeSuite++;
        }
        return e.horsDeSuite <= r.horsCibleMax();
    }

    /** Corrélation (Pearson) des variations de la visée et de la direction de la cible ; 0 si l'une ne bouge pas. */
    static double correlation(Episode e) {
        if (e.m < 3) {
            return 0;
        }
        double mv = e.sv / e.m;
        double mb = e.sb / e.m;
        double varV = e.svv / e.m - mv * mv;
        double varB = e.sbb / e.m - mb * mb;
        // moins de ~0,5° d'écart type par fenêtre : rien à corréler (visée ou cible quasi immobiles)
        if (varV < 0.25 || varB < 0.25) {
            return 0;
        }
        return (e.svb / e.m - mv * mb) / Math.sqrt(varV * varB);
    }

    public static Bilan bilan(Episode e) {
        int n = Math.max(1, e.n);
        return new Bilan(e.n, e.sommeAngle / n, (double) e.nSur / n, e.balayage, correlation(e), e.virages,
                e.sommeDistance / n);
    }

    /** L'épisode réunit-il TOUS les critères d'un suivi à travers les murs ? */
    public static boolean suspect(Episode e, Reglages r) {
        if (e.n < r.echantillonsMin() || (double) e.nSur / e.n < r.partSurCible()) {
            return false;
        }
        return e.balayage >= r.balayageMinDegres() && e.virages >= r.viragesMin() && correlation(e) >= r.correlationMin();
    }
}
