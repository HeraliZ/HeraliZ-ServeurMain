package fr.heraliz.noyau.client.kit;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import org.joml.Matrix4f;

/**
 * Petite barre de progression penchée des HUD (refonte du 25/10, retours du propriétaire : « une petite barre bien
 * propre, un peu en italique », docs/HUD_REFONTE.md) : barre fine et courte ({@link #LARGEUR} x {@link #HAUTEUR}),
 * penchée de {@link #INCLINAISON}° comme les cases de la barre d'objets DayZ, ombre douce d'un pixel d'écran, piste
 * discrète, remplissage net de la couleur donnée. Utilisée sous le réticule pour les soins et les grenades.
 * <p>États : {@link Etat#NORMAL} (progression donnée à chaque image, interpolée par l'appelant) et
 * {@link Etat#INTERROMPU} (rouge qui se vide en {@value #INTERRUPTION_MS} ms, puis s'arrête seul). Formes envoyées au
 * tampon sans vidage ; aucune allocation.
 */
public final class BarreProgression {
    /** Largeur, hauteur, inclinaison (degrés, comme la barre DayZ). */
    public static final float LARGEUR = 46f, HAUTEUR = 2.5f, INCLINAISON = 12f;
    /** Opacités de la piste vide et de l'ombre. */
    public static final float PISTE_ALPHA = 0.18f, OMBRE_ALPHA = 0.35f;
    public static final int INTERRUPTION_MS = 300;
    private static final float PENTE = Formes.pente(INCLINAISON);

    public enum Etat { RIEN, NORMAL, INTERROMPU }

    private Etat etat = Etat.RIEN;
    private float progression;
    private int couleur = Palette.OK;
    private long depuis;

    /** Progression 0..1 de la couleur donnée (RGB). */
    public void normal(float p, int rgb) {
        etat = Etat.NORMAL;
        progression = Math.max(0f, Math.min(1f, p));
        couleur = rgb;
    }

    /** Interrompu : rouge qui se vide depuis la progression atteinte. Sans effet si rien n'était en cours. */
    public void interrompre() {
        if (etat != Etat.NORMAL) return;
        etat = Etat.INTERROMPU;
        depuis = Util.getMillis();
    }

    public void arreter() {
        etat = Etat.RIEN;
    }

    public Etat etat() {
        actif();
        return etat;
    }

    /** Vrai tant qu'il y a quelque chose à dessiner (l'interruption s'arrête seule). */
    public boolean actif() {
        if (etat == Etat.INTERROMPU && Util.getMillis() - depuis >= INTERRUPTION_MS) etat = Etat.RIEN;
        return etat != Etat.RIEN;
    }

    public boolean enFin() {
        return actif() && etat == Etat.INTERROMPU;
    }

    /** Envoie la barre au tampon (sans vidage), centrée sur {@code cx}, haut en {@code y}, opacité {@code alpha} (0..1). */
    public void ajouter(VertexConsumer vc, Matrix4f m, float cx, float y, float alpha) {
        if (!actif() || alpha <= 0f) return;
        float px = Formes.pixel();
        float x0 = Formes.caler(cx - LARGEUR / 2f), y0 = Formes.caler(y), x1 = x0 + LARGEUR, y1 = y0 + HAUTEUR, r = (y0 + y1) / 2f;
        float p = progression, a = alpha;
        int c = couleur;
        if (etat == Etat.INTERROMPU) {
            float k = Anim.borner((Util.getMillis() - depuis) / (float) INTERRUPTION_MS);
            p = progression * (1f - Anim.sortie(k));
            c = Palette.DANGER;
            a = alpha * (1f - 0.6f * k * k);
        }
        float xp = x0 + LARGEUR * p;
        Formes.parallelogramme(vc, m, x0 - px, y0 - px, x1 + px, y1 + px, PENTE, r, Palette.argb(0x000000, OMBRE_ALPHA * a), px);
        Formes.parallelogramme(vc, m, xp, y0, x1, y1, PENTE, r, Palette.argb(0xFFFFFF, PISTE_ALPHA * a), px);
        Formes.parallelogramme(vc, m, x0, y0, xp, y1, PENTE, r, Palette.argb(c, a), px);
    }
}
