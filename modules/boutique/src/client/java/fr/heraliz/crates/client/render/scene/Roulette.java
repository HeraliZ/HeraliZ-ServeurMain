package fr.heraliz.crates.client.render.scene;

import fr.heraliz.crates.block.CrateAnim;

/**
 * Positions des objets de la roue (px, repere du modele) : sortie du coffre, rotation, arret net,
 * objet gagnant au centre puis vol vers le joueur ; les autres retombent dans le coffre.
 */
public final class Roulette {
    private Roulette() {
    }

    public static final float CX = 8, CZ = 8;

    /**
     * @param out x, y, z, echelle, lacet (degres), mise en avant (0..1)
     * @return false si l'objet est invisible
     */
    public static boolean item(CrateState s, Design d, int i, float[] out) {
        int n = s.ringSize;
        if (!s.active || n <= 0) return false;
        float t = s.t;
        if (t < CrateAnim.RISE || t > CrateAnim.REWARD) return false;
        int win = s.winIndex;
        float slot = 360f / n;
        float angle = CrateAnim.ringAngle(t, n, win);
        float rise = Ease.clamp01((t - CrateAnim.RISE - i * 0.8f) / 10f);
        if (rise <= 0) return false;
        float e = Ease.outBack(rise);
        float ang = angle + i * slot;
        double rad = Math.toRadians(ang);
        float radius = d.ringR * Ease.clamp01(e);
        float x = CX + (float) Math.sin(rad) * radius;
        float z = CZ + (float) Math.cos(rad) * radius;
        float y = Ease.lerp(d.riseFromY * d.size, d.ringY, Ease.outCubic(rise)) + (float) Math.sin(s.time * 0.15f + i) * 0.6f;
        float scale = 1.25f * e;
        float face = (float) Math.cos(rad);
        float focus = (float) Math.pow(Math.max(0, face), 12);
        if (t < CrateAnim.STOP) scale *= 1f + 0.35f * focus;
        float yaw = ang;
        if (t >= CrateAnim.STOP) {
            if (i == win) {
                winner(s, d, out);
                return out[3] > 0.01f;
            }
            int dist = Math.abs(i - win);
            dist = Math.min(dist, n - dist);
            float u = Ease.clamp01((t - CrateAnim.STOP - 2 - dist * 1.2f) / 12f);
            float k = Ease.inCubic(u);
            x = Ease.lerp(x, CX, k);
            z = Ease.lerp(z, CZ, k);
            y = Ease.lerp(y, (d.riseFromY - 2) * d.size, k);
            scale *= 1 - k;
            focus = 0;
        }
        out[0] = x;
        out[1] = y;
        out[2] = z;
        out[3] = scale;
        out[4] = yaw;
        out[5] = focus;
        return scale > 0.01f;
    }

    /** Objet gagnant apres l'arret de la roue. */
    public static void winner(CrateState s, Design d, float[] out) {
        float t = s.t;
        float frontZ = CZ + d.ringR;
        float u = Ease.inOutCubic(Ease.range(t, CrateAnim.STOP + 2, CrateAnim.REVEAL + 12));
        float x = CX;
        float y = Ease.lerp(d.ringY, d.revealY, u) + (float) Math.sin(s.time * 0.12f) * 0.8f * u;
        float z = Ease.lerp(frontZ, CZ + 6f, u);
        float scale = 1.25f * (1f + 0.75f * u) * (1f + 0.12f * (float) Math.exp(-(t - CrateAnim.STOP) * 0.4f));
        float yaw = (t - CrateAnim.STOP) * 4.5f * u;
        if (t >= CrateAnim.FLY) {
            float v = Ease.inCubic(Ease.range(t, CrateAnim.FLY, CrateAnim.REWARD));
            float[] target = s.target != null ? s.target : new float[]{CX, d.revealY + 30f, CZ + 20f};
            x = Ease.lerp(x, target[0], v);
            y = Ease.lerp(y, target[1], v);
            z = Ease.lerp(z, target[2], v);
            scale *= 1f - 0.75f * v;
        }
        out[0] = x;
        out[1] = y;
        out[2] = z;
        out[3] = scale;
        out[4] = yaw;
        out[5] = 1;
    }
}
