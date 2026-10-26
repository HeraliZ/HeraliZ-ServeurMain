package fr.heralisz.clans.client.gui.atelier;

import fr.heralisz.clans.client.gui.kit.Ui;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.IntConsumer;

/**
 * Glissiere fine (taille, rotation) : piste de 2 px, remplissage a l'accent, curseur 4 x 10. Un appui sur la piste
 * place la valeur sous la souris puis la suit jusqu'au relachement ; {@code debut} est appele a l'appui (pour noter
 * l'etat d'avant), {@code valeur} a chaque changement.
 */
public final class Glissiere {
    public static final int HAUTEUR = 10;
    private int x, y, w;
    private final int min, max;
    private int valeur;
    private boolean prise;
    private final Runnable debut;
    private final IntConsumer change;

    public Glissiere(int min, int max, Runnable debut, IntConsumer change) {
        this.min = min;
        this.max = Math.max(min + 1, max);
        this.debut = debut;
        this.change = change;
    }

    public void placer(int x, int y, int w) {
        this.x = x;
        this.y = y;
        this.w = Math.max(8, w);
    }

    /** Valeur affichee (sans prevenir) ; ignoree pendant une prise (la souris fait foi). */
    public void valeur(int v) {
        if (!prise) valeur = Math.max(min, Math.min(max, v));
    }

    public boolean prise() {
        return prise;
    }

    public void render(GuiGraphics g, int mx, int my, int accent) {
        boolean survol = prise || Dessin.dans(mx, my, x, y, w, HAUTEUR);
        int py = y + HAUTEUR / 2 - 1;
        g.fill(x, py, x + w, py + 2, Ui.argb(0xFFFFFF, survol ? 34 : 22));
        int cx = x + Math.round((valeur - min) / (float) (max - min) * (w - 4));
        g.fill(x, py, cx + 2, py + 2, Ui.argb(accent, 230));
        Ui.soft(g, cx, y, 4, HAUTEUR, Ui.argb(survol ? 0xFFFFFF : Ui.mix(accent, 0xFFFFFF, 0.5f), 255));
    }

    public boolean clic(double mx, double my) {
        if (!Dessin.dans(mx, my, x - 2, y - 2, w + 4, HAUTEUR + 4)) return false;
        prise = true;
        debut.run();
        suivre(mx);
        return true;
    }

    public boolean glisser(double mx) {
        if (!prise) return false;
        suivre(mx);
        return true;
    }

    public void relacher() {
        prise = false;
    }

    private void suivre(double mx) {
        float t = (float) ((mx - x - 2) / Math.max(1, w - 4));
        int v = Math.max(min, Math.min(max, Math.round(min + t * (max - min))));
        if (v != valeur) {
            valeur = v;
            change.accept(v);
        }
    }
}
