package fr.heraliz.noyau.client.kit;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import org.joml.Matrix4f;

/**
 * Anneau de progression des HUD (refonte du 25/10, docs/HUD_REFONTE.md), le même pour les soins, les grenades et la
 * réanimation : anneau fin de {@link #RAYON} unités (24 de diamètre) et {@link #EPAISSEUR} d'épaisseur autour du
 * réticule, piste blanche discrète, remplissage de midi dans le sens horaire avec une lueur douce, bords adoucis d'un
 * pixel d'écran. Quadrilatères envoyés au tampon, un seul vidage par {@link #dessiner}.
 * <p>États :
 * <ul>
 *     <li>{@link Etat#NORMAL} : rempli de la progression donnée à chaque image ({@link #normal}), interpolée par
 *     l'appelant (fraction de tick) ;</li>
 *     <li>{@link Etat#ATTENTE} : action finie chez le joueur, le serveur n'a pas encore répondu : anneau plein atténué et
 *     petit arc clair qui tourne (jamais un anneau figé) ;</li>
 *     <li>{@link Etat#INTERROMPU} : rouge qui se vide en {@link #INTERRUPTION_MS} ms depuis la progression atteinte ;</li>
 *     <li>{@link Etat#REUSSI} : anneau plein et coche de la couleur de réussite pendant {@link #REUSSITE_MS} ms.</li>
 * </ul>
 * Les états INTERROMPU et REUSSI s'arrêtent seuls ({@link #actif}). Aucune allocation.
 */
public final class AnneauProgression {
    /** Rayon (milieu du trait) et épaisseur. */
    public static final float RAYON = 12f, EPAISSEUR = 1.5f;
    /** Opacité de la piste vide, largeur et opacité de la lueur autour de la partie pleine. */
    public static final float PISTE_ALPHA = 0.16f, LUEUR = 1.6f, LUEUR_ALPHA = 0.26f;
    /** Attente : opacité de l'anneau plein, longueur de l'arc qui tourne (fraction de tour), durée d'un tour (ms). */
    public static final float ATTENTE_ALPHA = 0.45f, ATTENTE_ARC = 0.16f;
    public static final int ATTENTE_TOUR_MS = 900;
    /** Coche de réussite : taille, épaisseur, durée de son tracé. */
    public static final float COCHE = 9f, COCHE_EPAISSEUR = 1.6f;
    public static final int COCHE_MS = 160;
    /** Durées des états de fin. */
    public static final int INTERRUPTION_MS = 300, REUSSITE_MS = 600;

    public enum Etat { RIEN, NORMAL, ATTENTE, INTERROMPU, REUSSI }

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

    /** Action finie chez le joueur, en attente de la réponse du serveur. */
    public void attente(int rgb) {
        if (etat != Etat.ATTENTE) depuis = Util.getMillis();
        etat = Etat.ATTENTE;
        progression = 1f;
        couleur = rgb;
    }

    /** Interrompu : rouge qui se vide depuis la progression atteinte. Sans effet si rien n'était en cours. */
    public void interrompre() {
        if (etat != Etat.NORMAL && etat != Etat.ATTENTE) return;
        etat = Etat.INTERROMPU;
        depuis = Util.getMillis();
    }

    /** Réussi : anneau plein et coche de la couleur donnée. */
    public void reussir(int rgb) {
        etat = Etat.REUSSI;
        progression = 1f;
        couleur = rgb;
        depuis = Util.getMillis();
    }

    public void arreter() {
        etat = Etat.RIEN;
    }

    public Etat etat() {
        actif();
        return etat;
    }

    public float progression() {
        return progression;
    }

    /** Vrai tant qu'il y a quelque chose à dessiner (les états de fin s'arrêtent seuls). */
    public boolean actif() {
        long t = Util.getMillis() - depuis;
        if (etat == Etat.INTERROMPU && t >= INTERRUPTION_MS) etat = Etat.RIEN;
        if (etat == Etat.REUSSI && t >= REUSSITE_MS) etat = Etat.RIEN;
        return etat != Etat.RIEN;
    }

    /** Vrai pendant les états de fin (interrompu, réussi). */
    public boolean enFin() {
        actif();
        return etat == Etat.INTERROMPU || etat == Etat.REUSSI;
    }

    /** Envoie l'anneau au tampon (sans vidage), centré en (cx, cy), opacité globale {@code alpha} (0..1). */
    public void ajouter(VertexConsumer vc, Matrix4f m, float cx, float cy, float alpha) {
        if (!actif() || alpha <= 0f) return;
        float px = Formes.pixel();
        float ri = RAYON - EPAISSEUR / 2f, re = RAYON + EPAISSEUR / 2f;
        long t = Util.getMillis() - depuis;
        switch (etat) {
            case NORMAL -> {
                piste(vc, m, cx, cy, ri, re, alpha, px);
                plein(vc, m, cx, cy, ri, re, 0f, progression, couleur, alpha, px);
            }
            case ATTENTE -> {
                piste(vc, m, cx, cy, ri, re, alpha, px);
                Formes.arc(vc, m, cx, cy, ri, re, 0f, 1f, Palette.argb(couleur, alpha * ATTENTE_ALPHA), px);
                float d = (t % ATTENTE_TOUR_MS) / (float) ATTENTE_TOUR_MS, f = d + ATTENTE_ARC;
                int clair = Palette.argb(Palette.clair(couleur, 0.6f), alpha * 0.9f);
                Formes.arc(vc, m, cx, cy, ri, re, d, Math.min(1f, f), clair, px);
                if (f > 1f) Formes.arc(vc, m, cx, cy, ri, re, 0f, f - 1f, clair, px);
            }
            case INTERROMPU -> {
                float k = Anim.borner(t / (float) INTERRUPTION_MS);
                float a = alpha * (1f - 0.6f * k * k);
                piste(vc, m, cx, cy, ri, re, a, px);
                plein(vc, m, cx, cy, ri, re, 0f, progression * (1f - Anim.sortie(k)), Palette.DANGER, a, px);
            }
            case REUSSI -> {
                float k = Anim.borner(t / (float) REUSSITE_MS);
                float a = alpha * (k < 0.65f ? 1f : 1f - Anim.douce((k - 0.65f) / 0.35f));
                plein(vc, m, cx, cy, ri, re, 0f, 1f, couleur, a, px);
                Formes.coche(vc, m, cx, cy, COCHE, COCHE_EPAISSEUR, Anim.sortie(t / (float) COCHE_MS), Palette.argb(couleur, a));
            }
            default -> {
            }
        }
    }

    /** Envoie l'anneau et vide le tampon une fois. */
    public void dessiner(net.minecraft.client.gui.GuiGraphics g, float cx, float cy, float alpha) {
        ajouter(Formes.tampon(g), g.pose().last().pose(), cx, cy, alpha);
        g.flush();
    }

    private static void piste(VertexConsumer vc, Matrix4f m, float cx, float cy, float ri, float re, float alpha, float px) {
        Formes.arc(vc, m, cx, cy, ri, re, 0f, 1f, Palette.argb(0xFFFFFF, alpha * PISTE_ALPHA), px);
    }

    private static void plein(VertexConsumer vc, Matrix4f m, float cx, float cy, float ri, float re, float de, float a, int rgb,
                              float alpha, float px) {
        if (a - de <= 0.0005f) return;
        Formes.arc(vc, m, cx, cy, ri - LUEUR, re + LUEUR, de, a, Palette.argb(rgb, alpha * LUEUR_ALPHA), LUEUR * 2f);
        Formes.arc(vc, m, cx, cy, ri, re, de, a, Palette.argb(rgb, alpha), px);
    }
}
