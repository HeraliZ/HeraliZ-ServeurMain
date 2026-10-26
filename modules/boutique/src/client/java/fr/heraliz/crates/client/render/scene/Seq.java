package fr.heraliz.crates.client.render.scene;

import static fr.heraliz.crates.block.CrateAnim.CLOSE;
import static fr.heraliz.crates.block.CrateAnim.END;
import static fr.heraliz.crates.block.CrateAnim.OPEN;
import static fr.heraliz.crates.block.CrateAnim.OPEN_END;
import static fr.heraliz.crates.block.CrateAnim.RISE;
import static fr.heraliz.crates.block.CrateAnim.SLAM;
import static fr.heraliz.crates.block.CrateAnim.SPIN;
import static fr.heraliz.crates.block.CrateAnim.STOP;
import static fr.heraliz.crates.block.CrateAnim.UNLOCK;

/**
 * Valeurs derivees de la chronologie d'ouverture (communes a toutes les caisses).
 */
public final class Seq {
    private Seq() {
    }

    /** Eveil : du declic jusqu'a l'ouverture complete, retombe a la fermeture (0..1). */
    public static float awaken(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        return Ease.smooth(Ease.range(t, UNLOCK, OPEN_END)) * (1 - Ease.smooth(Ease.range(t, CLOSE, END)));
    }

    /** Ouverture du couvercle (0 ferme, 1 ouvert ; depasse legerement 1 au rebond). */
    public static float open(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        if (t < OPEN) return 0.035f * Ease.bell(t, UNLOCK + 2, OPEN);
        if (t < OPEN_END) return Ease.outBack(Ease.range(t, OPEN, OPEN_END), 1.3f);
        if (t < CLOSE) return 1f + 0.012f * (float) Math.sin((t - OPEN_END) * 0.12f);
        if (t < SLAM) return 1f - Ease.inCubic(Ease.range(t, CLOSE, SLAM));
        float u = (t - SLAM) / 8f;
        if (u < 1) return 0.05f * Math.abs((float) Math.sin(u * Math.PI * 2)) * (1 - u);
        return 0;
    }

    /** Ouverture lineaire sans rebond (0..1). */
    public static float openLinear(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        return Ease.range(t, OPEN, OPEN_END) * (1 - Ease.range(t, CLOSE, SLAM));
    }

    /** Lumiere qui sort de la cavite (0..1). */
    public static float cavity(CrateState s) {
        return Ease.clamp01(openLinear(s) * 1.6f);
    }

    /** Energie de la roue : monte a la sortie des objets, maximale pendant la rotation, retombe apres l'arret. */
    public static float spin(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        return Ease.smooth(Ease.range(t, RISE - 6, SPIN)) * (1 - Ease.smooth(Ease.range(t, STOP, STOP + 26)));
    }

    /** Vitesse relative de la roue (pour accelerer les decors en orbite) : 0..1. */
    public static float spinSpeed(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        if (t <= SPIN || t >= STOP) return 0;
        return fr.heraliz.crates.block.CrateAnim.spinVelocity((t - SPIN) / (float) (STOP - SPIN));
    }

    /** Eclair a l'arret de la roue (1 -> 0). */
    public static float stopFlash(CrateState s) {
        if (!s.active || s.t < STOP) return 0;
        return (float) Math.exp(-(s.t - STOP) * 0.35f);
    }

    /** Choc de fermeture (1 -> 0). */
    public static float slam(CrateState s) {
        if (!s.active || s.t < SLAM) return 0;
        return (float) Math.exp(-(s.t - SLAM) * 0.3f);
    }

    /** Excitation globale : 0 au repos, 1 pendant l'ouverture et la roue. */
    public static float excite(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        return Ease.smooth(Ease.range(t, 0, UNLOCK + 6)) * (1 - Ease.smooth(Ease.range(t, CLOSE, END)));
    }

    /**
     * Arret sur image (hit-stop) : autour de l'instant "at", le temps local de la choregraphie se fige presque pendant
     * "hold" ticks puis rattrape le temps reel en "recover" ticks. A appliquer aux mouvements d'une caisse, pas a la roue.
     */
    public static float hitStop(float t, float at, float hold, float recover) {
        if (t <= at) return t;
        float u = t - at;
        if (u <= hold) return at + u * 0.12f;
        float lag = hold * 0.88f * (1 - Ease.smooth(Ease.clamp01((u - hold) / recover)));
        return t - lag;
    }

    /** Tremblement du clic sans cle (decalage lateral en px). */
    public static float shake(CrateState s) {
        if (s.shake < 0 || s.shake >= 10) return 0;
        float u = s.shake / 10f;
        return (float) Math.sin(u * Math.PI * 9) * 0.45f * (1 - u);
    }
}
