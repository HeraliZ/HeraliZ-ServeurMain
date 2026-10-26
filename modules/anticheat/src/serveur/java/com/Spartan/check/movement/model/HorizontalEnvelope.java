package com.Spartan.check.movement.model;

/**
 * Enveloppe de vitesse horizontale d'un joueur vanilla 1.20.1 (Java pur, testé).
 *
 * À chaque tick, vanilla fait : déplacement = vitesse + accélération (+0,2 au
 * tick d'un saut en sprint), puis vitesse suivante = déplacement × frottement
 * (bloc × 0,91 au sol, 0,91 en l'air). L'enveloppe calcule la distance MAXIMALE
 * possible (entrée maximale, sprint) et fait repartir la vitesse suivante de la
 * distance BORNÉE, jamais de la distance revendiquée si elle dépasse : on ne peut
 * pas accumuler de l'élan en trichant un peu à chaque tick (bunny-hop progressif).
 *
 * Vitesses imposées par le serveur (recul, explosion, propulsion de mod) : leur
 * valeur exacte est connue (paquets envoyés au joueur) et intégrée, au lieu
 * d'exempter le joueur pendant plusieurs secondes.
 */
public final class HorizontalEnvelope {
    /** Accélération en l'air en sprint (Player.getFlyingSpeed). */
    public static final double AIR_ACCEL = 0.026;
    /** Ajout horizontal du saut en sprint (LivingEntity.jumpFromGround). */
    public static final double SPRINT_JUMP = 0.2;
    public static final double AIR_FRICTION = 0.91;
    /** Entrée du joueur quand il utilise un objet (LocalPlayer.aiStep). */
    public static final double USE_ITEM_INPUT = 0.2;
    /** Facteur d'accélération au sol : vitesse × 0,216 / frottement³ (LivingEntity.getFrictionInfluencedSpeed). */
    private static final double GROUND_FACTOR = 0.21600002;

    /**
     * Ce que l'on sait d'un tick.
     *
     * @param ground          au sol au début du tick (frottement et accélération du sol)
     * @param uncertainGround sol incertain : on prend le plus permissif des deux
     * @param blockFriction   frottement du bloc sous les pieds (0,6 normal, 0,98 glace)
     * @param speed           vitesse de déplacement du joueur (attribut, sprint et effets compris)
     * @param jump            tick d'un saut (impulsion du sprint possible)
     * @param slowed          le joueur utilise un objet (manger, boire, se soigner, bander un arc…)
     * @param impulse         vitesse horizontale imposée par le serveur (recul : remplace la vitesse ; 0 = aucune)
     * @param push            vitesse horizontale ajoutée par le serveur (explosion ; 0 = aucune)
     * @param extra           marge supplémentaire (poussée d'entités voisines…)
     */
    public record Tick(boolean ground, boolean uncertainGround, double blockFriction, double speed, boolean jump,
                       boolean slowed, double impulse, double push, double extra) {
    }

    private double v;

    /** Vitesse horizontale maximale possible au début du prochain tick (blocs/tick). */
    public double velocity() {
        return v;
    }

    public void reset(double velocity) {
        v = Math.max(0, velocity);
    }

    /** Distance horizontale maximale possible pendant ce tick. */
    public double allowed(Tick t) {
        return allowedFrom(v, t);
    }

    private static double allowedFrom(double velocity, Tick t) {
        double base = Math.max(velocity, t.impulse()) + t.push();
        double input = t.slowed() ? USE_ITEM_INPUT : 1.0;
        double groundAccel = t.speed() * GROUND_FACTOR / cube(Math.max(0.05, t.blockFriction()));
        double accel;
        if (t.uncertainGround()) {
            accel = Math.max(groundAccel, AIR_ACCEL);
        } else {
            accel = t.ground() ? groundAccel : AIR_ACCEL;
        }
        return base + accel * input + (t.jump() ? SPRINT_JUMP : 0) + t.extra();
    }

    private static double friction(Tick t) {
        double groundFriction = t.blockFriction() * AIR_FRICTION;
        if (t.uncertainGround()) {
            return Math.max(groundFriction, AIR_FRICTION);
        }
        return t.ground() ? groundFriction : AIR_FRICTION;
    }

    /**
     * Fin du tick : la vitesse suivante part de la distance bornée.
     * @return la distance autorisée pour ce tick
     */
    public double advance(Tick t, double claimed) {
        return advance(t, 1, claimed);
    }

    /**
     * Fin d'une position qui couvre peut-être plusieurs ticks. Un client vanilla envoie
     * une position par tick où il bouge : une position arrivée en retard (réseau) ne
     * couvre en réalité qu'un tick, mais un client qui n'a rien envoyé pendant quelques
     * ticks (caméra détachée par un mod…) peut avoir bougé plusieurs fois. On accepte le
     * chemin maximal sur {@code ticks} ticks (le premier depuis l'état connu, les suivants
     * au sol ou en l'air, sans saut ni impulsion), et la vitesse suivante part de la
     * distance revendiquée bornée par le plus grand pas de ce chemin : pas d'élan perdu
     * pour un paquet retardé, pas d'élan accumulé pour un tricheur.
     *
     * @return la distance autorisée pour ces ticks
     */
    public double advance(Tick t, int ticks, double claimed) {
        int n = Math.max(1, ticks);
        double velocity = v;
        double total = 0;
        double maxStep = 0;
        Tick cur = t;
        for (int i = 0; i < n; i++) {
            double step = allowedFrom(velocity, cur);
            total += step;
            maxStep = Math.max(maxStep, step);
            velocity = step * friction(cur);
            if (i == 0 && n > 1) {
                cur = new Tick(t.ground(), true, t.blockFriction(), t.speed(), false, t.slowed(), 0, 0, t.extra());
            }
        }
        double d = Math.min(Math.max(0, claimed), maxStep);
        v = d * friction(cur);
        return total;
    }

    private static double cube(double f) {
        return f * f * f;
    }
}
