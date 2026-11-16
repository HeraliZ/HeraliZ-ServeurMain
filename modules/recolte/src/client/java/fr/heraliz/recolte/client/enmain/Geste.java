package fr.heraliz.recolte.client.enmain;

/**
 * Le geste du coup, environ 0,6 s (12 ticks), le même à la première personne et vu des autres (A_FAIRE §7, PROPOSITION
 * §6 bis) : élan (hache au-dessus de l'épaule droite, pioche au-dessus de la tête), frappe rapide, impact, petit recul,
 * retour en douceur au repos. Le coup est donné au serveur à l'impact ({@link #IMPACT}).
 * <pre>
 *    ticks   0 ─────── 4,2 ─── 5,4 ── 6,0 ── 7,0 ───────────── 12
 *          repos     élan    frappe impact recul            repos
 *          (doux)   (de plus en plus vite)  (rebond)       (doux)
 * </pre>
 * Poses : 0 repos, 1 élan, 2 frappe, 3 impact. {@link #calculer} donne les deux poses à mêler et le mélange ; aucune
 * allocation.
 */
final class Geste {
    /** Fin de l'élan, de la frappe, impact, fin du recul, fin du geste (ticks depuis le début du coup). */
    static final float ELAN = 4.2F, FRAPPE = 5.4F, IMPACT = 6.0F, RECUL = 7.0F, FIN = 12.0F;
    static final int REPOS = 0, POSE_ELAN = 1, POSE_FRAPPE = 2, POSE_IMPACT = 3;

    /** Résultat du dernier calcul (fil de rendu seulement) : poses a et b, mélange f (0 : a, 1 : b), recul (0 à 1). */
    int a, b;
    float f, recul;

    /** @param t ticks depuis le début du coup ; négatif ou au-delà de {@link #FIN} : au repos */
    Geste calculer(float t) {
        recul = 0.0F;
        if (t < 0.0F || t >= FIN) {
            a = b = REPOS;
            f = 0.0F;
        } else if (t < ELAN) {
            a = REPOS;
            b = POSE_ELAN;
            f = doux(t / ELAN);
        } else if (t < FRAPPE) {
            a = POSE_ELAN;
            b = POSE_FRAPPE;
            float u = (t - ELAN) / (FRAPPE - ELAN);
            f = u * u;
        } else if (t < IMPACT) {
            a = POSE_FRAPPE;
            b = POSE_IMPACT;
            f = (t - FRAPPE) / (IMPACT - FRAPPE);
        } else if (t < RECUL) {
            a = b = POSE_IMPACT;
            f = 0.0F;
            recul = (float) Math.sin(Math.PI * (t - IMPACT) / (RECUL - IMPACT));
        } else {
            a = POSE_IMPACT;
            b = REPOS;
            f = doux((t - RECUL) / (FIN - RECUL));
        }
        return this;
    }

    private static float doux(float u) {
        u = Math.max(0.0F, Math.min(1.0F, u));
        return u * u * (3.0F - 2.0F * u);
    }
}
