package fr.heraliz.crates.client.render.scene;

import fr.heraliz.crates.block.CrateAnim;

/**
 * Effets communs a toutes les caisses : cercle runique HERALIZ au sol, halo, lumiere de la cavite,
 * anneau de la roue, revelation de l'objet gagne.
 */
public final class CommonFx {
    private CommonFx() {
    }

    /**
     * Presage : dans la derniere seconde et demie de la roue, si l'objet gagne est epique ou mieux, le cercle runique vire
     * a la couleur de sa rarete et bat comme un coeur (0 = rien, 1 = pleinement teinte).
     */
    public static float tease(CrateState s) {
        if (!s.active || ringRank(s, s.winIndex) < 3) return 0;
        float t = s.t;
        return Ease.smooth(Ease.range(t, CrateAnim.STOP - 34, CrateAnim.STOP - 4)) * (1 - Ease.range(t, CrateAnim.STOP + 20, CrateAnim.REWARD));
    }

    /** Cercle runique (texte HERALIZ + boussole) qui tourne au sol ; il s'emballe pendant l'ouverture. */
    public static void ground(CrateState s, Pose p, DrawList out, Design d) {
        if (!s.runes || !s.active) return;
        float ex = Seq.excite(s);
        float spin = s.time * 0.35f + (s.active ? s.t * s.t * 0.012f : 0);
        float tz = tease(s);
        float beat = tz > 0 && s.t < CrateAnim.STOP ? (float) Math.pow(Math.max(0, Math.sin(s.t * 0.55f)), 8) : 0;
        float boost = 1f + 0.9f * ex + 1.2f * Seq.stopFlash(s) + 0.8f * tz * beat;
        float a = 0.44f * (0.8f + 0.2f * s.pulse(0.075f)) * boost;
        int col = Ease.mix(d.glow, s.rarityRgb, tz);
        out.ground(p, Sprite.RUNE, 8, 0.35f, 8, d.runeRadius, spin, col, Math.min(1f, a));
        if (tz > 0.01f) out.ground(p, Sprite.SHOCK, 8, 0.4f, 8, d.runeRadius * (0.6f + 0.5f * beat), 0, s.rarityRgb, 0.5f * tz * beat);
        out.ground(p, Sprite.RUNE, 8, 0.45f, 8, d.runeRadius * 0.72f, -spin * 1.7f, col, 0.17f * boost);
        if (ex > 0.01f) {
            // deuxieme anneau exterieur pendant l'ouverture
            out.ground(p, Sprite.RING, 8, 0.4f, 8, d.runeRadius * (1.05f + 0.1f * ex), spin * 2f, d.accent, 0.35f * ex);
        }
    }

    public static void halo(CrateState s, Pose p, DrawList out, Design d) {
        float boost = 1f + 0.8f * Seq.excite(s);
        out.billboard(p, Sprite.HALO, 8, 14, 8, 34f, 0, d.glow, 0.09f * boost * (0.75f + 0.25f * s.pulse(0.075f)));
    }

    /** Lumiere qui jaillit de l'interieur (x,y,z = fond de la cavite). */
    public static void cavity(CrateState s, Pose p, DrawList out, Design d, float x, float y, float z, float width) {
        cavity(s, p, out, d, x, y, z, width, d.glow);
    }

    /** Lumiere de la cavite d'une autre couleur que la caisse (ame de feu du Chevalier...). */
    public static void cavity(CrateState s, Pose p, DrawList out, Design d, float x, float y, float z, float width, int rgb) {
        float c = Seq.cavity(s);
        if (c <= 0.01f) return;
        int col = rgb;
        if (s.active && s.t >= CrateAnim.STOP) {
            col = Ease.mix(rgb, s.rarityRgb, Ease.range(s.t, CrateAnim.STOP, CrateAnim.STOP + 6));
        }
        out.shaft(p, x, y, z, width, 42f, s.time * 0.8f, 3, col, 0.26f * c);
        out.billboard(p, Sprite.HALO, x, y + 12f, z, 22f, 0, col, 0.18f * c);
        if (s.beams) out.beam(p, x, y, z, 8.8f, 140f, col, 0.24f * c);
    }

    /** Couleur et rang (0 commun .. 5 mythique) de la case i de la roue. */
    public static int ringRgb(CrateState s, Design d, int i) {
        return s.ringRgb != null && i >= 0 && i < s.ringRgb.length ? s.ringRgb[i] : d.glow;
    }

    public static int ringRank(CrateState s, int i) {
        if (s.ringRank != null && i >= 0 && i < s.ringRank.length) return s.ringRank[i];
        return i == s.winIndex ? (s.rarityTier == 2 ? 4 : s.rarityTier == 1 ? 2 : 0) : 0;
    }

    /**
     * Roue d'objets : anneau lumineux, porte de selection face au joueur (prend la couleur de l'objet qui passe, flashe a
     * chaque cran, le chevron rebondit), socle et halo a la couleur de la rarete sous chaque objet (eclats pour les
     * epiques, rayons pour les legendaires et mythiques), trainees de vitesse en arc.
     */
    public static void ringFx(CrateState s, Pose p, DrawList out, Design d) {
        if (!s.active) return;
        float t = s.t;
        if (t < CrateAnim.RISE || t >= CrateAnim.STOP + 14) return;
        int n = Math.max(1, s.ringSize);
        float k = Ease.range(t, CrateAnim.RISE, CrateAnim.SPIN) * (1 - Ease.range(t, CrateAnim.STOP, CrateAnim.STOP + 14));
        float slot = 360f / n;
        float ang = CrateAnim.ringAngle(t, n, s.winIndex);
        float vel = Math.abs(ang - CrateAnim.ringAngle(t - 0.5f, n, s.winIndex)) * 2f;
        float speed = Seq.spinSpeed(s);
        // cran : juste apres le passage d'une frontiere entre deux cases (meme instant que le tic-tac)
        float ph = ((ang + slot / 2) / slot) % 1f;
        if (ph < 0) ph += 1;
        float flash = t < CrateAnim.STOP ? (float) Math.pow(1 - ph, 5) * (0.35f + 0.65f * (1 - speed)) : 0;
        int front = Math.floorMod(Math.round(-ang / slot), n);
        int frontRgb = Ease.mix(d.glow, ringRgb(s, d, front), 0.75f);
        float fx = 8, fy = d.ringY, fz = 8 + d.ringR;

        // anneau au sol sous la roue (bat au rythme des crans en fin de course)
        out.ground(p, Sprite.RING, 8, d.ringY - 7f, 8, d.ringR + 5.6f, s.time * 3, d.glow, (0.6f + 0.35f * flash) * k);
        // porte de selection
        float gw = d.ringR * (float) Math.sin(Math.toRadians(slot / 2)) * 0.82f;
        for (int sd = -1; sd <= 1; sd += 2) {
            out.beam(p, fx + sd * gw, fy - 6.5f, fz + 0.6f, 0.9f, 13f, frontRgb, (0.4f + 0.55f * flash) * k);
        }
        out.ground(p, Sprite.HALO, fx, fy - 6.4f, fz, gw * 1.3f, 0, frontRgb, (0.25f + 0.4f * flash) * k);
        float bob = (float) Math.sin(s.time * 0.4f) * 0.6f - 1.4f * flash;
        out.billboard(p, Sprite.CHEVRON, fx, fy + 13f + bob, fz, 3.2f + 0.8f * flash, 0, frontRgb, 0.95f * k);
        out.billboard(p, Sprite.HALO, fx, fy + 1f, fz, 12f + 3f * flash, 0, frontRgb, (0.4f + 0.3f * flash) * k);

        // sous chaque objet : socle et halo de sa rarete, trainee de vitesse
        float[] it = new float[6];
        for (int i = 0; i < n; i++) {
            if (!Roulette.item(s, d, i, it)) continue;
            if (t >= CrateAnim.STOP && i == s.winIndex) continue;
            int rgb = ringRgb(s, d, i), rank = ringRank(s, i);
            float f = it[5], a = k * Ease.clamp01(it[3] / 1.25f);
            out.ground(p, Sprite.RING, it[0], it[1] - 5.2f, it[2], 3.4f + 1.6f * f, s.time * 4 + i * 40, rgb, (0.55f + 0.4f * f) * a);
            out.ground(p, Sprite.HALO, it[0], it[1] - 5.3f, it[2], 4.5f + 1.5f * f, 0, rgb, (0.3f + 0.3f * f) * a);
            out.billboard(p, Sprite.HALO, it[0], it[1], it[2], 7.5f + 3f * f + 0.6f * rank, 0, rgb, (0.22f + 0.3f * f + 0.07f * rank) * a);
            if (rank >= 3) {
                float tw = 0.5f + 0.5f * (float) Math.sin(s.time * 0.3f + i * 2);
                out.billboard(p, Sprite.GLINT, it[0] + 1.2f, it[1] + 1.5f, it[2], 2.2f + f, s.time * 3 + i * 30, 0xFFFFFF, (0.35f + 0.4f * tw) * a);
            }
            if (rank >= 4) {
                out.rays(p, it[0], it[1], it[2], rank >= 5 ? 10 : 6, 7f + 3f * f, 1.2f, s.time * (rank >= 5 ? -2.4f : 1.6f) + i * 20, rgb, (0.22f + 0.2f * f) * a);
            }
            if (speed > 0.06f && t < CrateAnim.STOP) {
                float[][] pts = new float[6][];
                float a0 = ang + i * slot;
                for (int j = 0; j < pts.length; j++) {
                    double r = Math.toRadians(a0 - j * vel * 0.9f);
                    pts[j] = v(8 + (float) Math.sin(r) * d.ringR, it[1], 8 + (float) Math.cos(r) * d.ringR);
                }
                out.additive(p, Trail.ribbon(pts, 1.5f, 0.1f), rgb, 0.5f * Math.min(1f, speed * 1.6f) * a);
            }
        }
    }

    private static float[] v(float x, float y, float z) {
        return new float[]{x, y, z};
    }

    /** Revelation : eclair, eventail de rayons a la couleur de la rarete, onde de choc au sol. */
    public static void reveal(CrateState s, Pose p, DrawList out, Design d) {
        if (!s.active) return;
        float t = s.t;
        if (t < CrateAnim.STOP || t > CrateAnim.REWARD) return;
        float[] w = new float[6];
        Roulette.winner(s, d, w);
        float x = w[0], y = w[1] + 1f, z = w[2];
        int rc = s.rarityRgb;
        float sIn = Ease.range(t, CrateAnim.STOP, CrateAnim.STOP + 8);
        float fade = 1 - Ease.range(t, CrateAnim.FLY, CrateAnim.REWARD);
        float flash = Seq.stopFlash(s);
        out.billboard(p, Sprite.FLARE, x, y, z, 9.6f + 22f * flash, s.time * 2, rc, flash);
        out.billboard(p, Sprite.HALO, x, y, z, 29f * flash + 3f, 0, 0xFFFFFF, 0.8f * flash);
        int count = s.rarityTier == 2 ? 18 : (s.rarityTier == 1 ? 14 : 10);
        float len = s.rarityTier == 2 ? 35f : 24f;
        out.rays(p, x, y, z, count, len * sIn, 3.2f, s.time * 1.5f, rc, 0.55f * sIn * fade);
        out.rays(p, x, y, z, count / 2, len * 0.7f * sIn, 2.2f, -s.time * 2.3f, 0xFFFFFF, 0.25f * sIn * fade);
        out.billboard(p, Sprite.HALO, x, y, z, 16f * sIn, 0, rc, 0.55f * fade);
        float sw = Ease.range(t, CrateAnim.STOP, CrateAnim.STOP + 14);
        if (sw > 0 && sw < 1) {
            out.ground(p, Sprite.SHOCK, 8, 0.8f, 8, 8f + sw * 56f, 0, rc, (1 - sw) * 0.9f);
        }
        // socle lumineux sous l'objet gagne
        out.ground(p, Sprite.RING, x, y - 7.5f, z, (4f + 3.5f * sIn) * (1 + 0.1f * flash), s.time * 3, rc, 0.7f * sIn * fade);
        out.ground(p, Sprite.RUNE, x, y - 7.6f, z, 6.5f * sIn, -s.time * 1.5f, rc, 0.35f * sIn * fade);
        int rank = ringRank(s, s.winIndex);
        if (rank >= 4) {
            // legendaire et mythique : colonne de lumiere, deuxieme onde, eclats en couronne
            out.shaft(p, x, 0.8f, z, 3.2f, y + 20f, s.time * 1.2f, 3, rc, 0.3f * sIn * fade);
            float sw2 = Ease.range(t, CrateAnim.STOP + 5, CrateAnim.STOP + 20);
            if (sw2 > 0 && sw2 < 1) out.ground(p, Sprite.SHOCK, 8, 0.9f, 8, 6f + sw2 * 44f, 0, 0xFFFFFF, (1 - sw2) * 0.6f);
            for (int i = 0; i < 6; i++) {
                float a = i * 60 + s.time * 2.5f;
                double r = Math.toRadians(a);
                float cr = 9f + 1.5f * (float) Math.sin(s.time * 0.2f + i);
                out.billboard(p, Sprite.GLINT, x + (float) Math.sin(r) * cr, y + 2f * (float) Math.sin(s.time * 0.15f + i), z + (float) Math.cos(r) * cr,
                        2.4f, s.time * 4 + i * 60, i % 2 == 0 ? 0xFFFFFF : rc, 0.8f * sIn * fade);
            }
        }
        if (rank >= 5) {
            // mythique : double eventail contre-rotatif et pulsation
            float pu = 0.5f + 0.5f * (float) Math.sin(s.time * 0.5f);
            out.rays(p, x, y, z, 24, 44f * sIn, 1.6f, -s.time * 2.2f, 0xFFFFFF, 0.3f * sIn * fade * pu);
            out.billboard(p, Sprite.FLARE, x, y, z, 12f + 6f * pu, s.time * 3, rc, 0.4f * sIn * fade);
        }
    }
}
