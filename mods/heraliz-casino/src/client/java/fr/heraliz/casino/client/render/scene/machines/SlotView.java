package fr.heraliz.casino.client.render.scene.machines;

import fr.heraliz.casino.game.slot.SlotGame;
import fr.heraliz.casino.game.slot.SlotRules;

/**
 * Etat d'une machine a sous lu par son modele 3D.
 */
public final class SlotView {
    public long spinStart = Long.MIN_VALUE / 4;
    public final int[] stops = {0, 0, 0};
    public final int[] prev = {0, 0, 0};
    public SlotRules rules;
    public long win, bet;
    public double multiplier;
    public int lineMask;
    public boolean jackpot;
    public long jackpotValue = 5000;

    public SlotView(SlotRules rules) {
        this.rules = rules;
    }

    /** Angle (degres) du rouleau i (0 = arret "stop" 0 face au joueur) au temps t depuis le lancement. */
    public float reelAngle(int i, float t) {
        int n = rules.strips[i].length;
        float step = 360f / n;
        float end = stops[i] * step;
        if (t < 0 || t > SlotGame.STOP[i] + 30) return end;
        float start = prev[i] * step;
        float t0 = SlotGame.LEVER - 3 + i;
        float t1 = SlotGame.STOP[i];
        if (t < t0) return start;
        // descente : l'angle diminue (les symboles defilent vers le bas)
        float total = 3 * 360 + i * 360 + (float) (((start - end) % 360 + 360) % 360);
        if (t >= t1) {
            // rebond d'arret
            float k = t - t1;
            return end - 5f * (float) (Math.sin(k * 0.9) * Math.exp(-k * 0.35));
        }
        float u = (t - t0) / (t1 - t0);
        return start - total * travel(u);
    }

    /** Profil de rotation : demarrage rapide, vitesse constante, freinage bref (0..1). */
    static float travel(float u) {
        final float a = 0.08f, b = 0.85f, p = 2f;
        float total = a / 2 + (b - a) + (1 - b) / (p + 1);
        float e;
        if (u < a) {
            e = u * u / (2 * a);
        } else if (u < b) {
            e = a / 2 + (u - a);
        } else {
            float r = (1 - u) / (1 - b);
            e = a / 2 + (b - a) + (1 - b) / (p + 1) * (1 - (float) Math.pow(r, p + 1));
        }
        return Math.min(1f, e / total);
    }

    /** Vitesse du rouleau (degres par tick) pour le flou de mouvement. */
    public float reelSpeed(int i, float t) {
        return Math.abs(reelAngle(i, t + 0.5f) - reelAngle(i, t - 0.5f));
    }

    public boolean spinning(float t) {
        return t >= 0 && t < SlotGame.STOP[2];
    }
}
