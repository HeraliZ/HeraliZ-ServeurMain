package fr.heraliz.casino.client.render.scene.machines;

/**
 * Etat de la Fusee (Crash) lu par son modele 3D.
 */
public final class CrashView extends RoundView {
    public double growth = 0.075;
    /** Ticks de vol (phase RUNNING), fraction comprise. */
    public float flight;
    public boolean crashed;
    /** Ticks depuis l'explosion. */
    public float sinceCrash;
    /** Point d'explosion x100 (connu apres l'explosion). */
    public long crashShown;
    /** Derniers points d'explosion x100. */
    public long[] crashes = new long[0];
    /** Joueurs encore en jeu / total mise en jeu. */
    public int inPlay;
    public long totalInPlay;
    /** Encaissement du joueur local (x100), 0 sinon. */
    public long myCash;

    /** Plus grand multiplicateur affichable (garde-fou contre un etat incoherent). */
    public static final double MAX_SHOWN = 1_000_000;

    /**
     * Multiplicateur affiche (non arrondi). Hors vol (attente, mises), la fusee est au sol : x1. Avant, "flight" (temps
     * depuis le debut de la phase) servait aussi pendant l'attente et l'exponentielle donnait des nombres gigantesques.
     */
    public double multiplier() {
        if (crashed) return Math.min(MAX_SHOWN, crashShown / 100.0);
        if (phase != RUNNING) return 1.0;
        return Math.min(MAX_SHOWN, Math.exp(growth * Math.max(0, flight) / 20.0));
    }

    /** Secondes de vol affichees (0 hors vol). */
    public float flightSeconds() {
        if (crashed) return (float) (Math.log(Math.max(1.0, multiplier())) / growth);
        return phase == RUNNING ? Math.max(0, flight) / 20f : 0;
    }
}
