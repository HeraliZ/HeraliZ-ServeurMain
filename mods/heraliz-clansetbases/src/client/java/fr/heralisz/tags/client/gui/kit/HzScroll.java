package fr.heralisz.tags.client.gui.kit;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Zone defilante sobre : contenu decoupe (scissor), decalage lisse, barre de 2 px a droite (visible seulement si le
 * contenu depasse). L'ecran appelle begin() avant de dessiner le contenu (translate par -offset) et end() apres.
 */
public final class HzScroll {
    private int x, y, w, h;
    private int contentH;
    private float offset, cible;
    private boolean drag;
    private int accent = Ui.accent;

    public HzScroll(int x, int y, int w, int h) {
        setBounds(x, y, w, h);
    }

    public void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        clamp();
    }

    public HzScroll accent(int rgb) {
        this.accent = rgb & 0xFFFFFF;
        return this;
    }

    /** Hauteur totale du contenu (a tenir a jour par l'ecran). */
    public void setContentHeight(int contentH) {
        this.contentH = Math.max(0, contentH);
        clamp();
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return w;
    }

    public int height() {
        return h;
    }

    /** Decalage courant (pixels), a soustraire aux y du contenu. */
    public int offset() {
        return Math.round(offset);
    }

    public int max() {
        return Math.max(0, contentH - h);
    }

    public boolean deborde() {
        return contentH > h;
    }

    public boolean contient(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void clamp() {
        cible = Math.max(0, Math.min(max(), cible));
        offset = Math.max(0, Math.min(max(), offset));
    }

    /** A appeler a chaque image : rapproche le decalage de sa cible (defilement lisse). */
    public void tick(float partial) {
        clamp();
        offset += (cible - offset) * Math.min(1f, 0.35f + partial * 0.1f);
        if (Math.abs(cible - offset) < 0.5f) offset = cible;
    }

    /** Molette : delta positif = vers le haut. Retourne vrai si consomme. */
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!contient(mx, my) || !deborde()) return false;
        cible -= (float) (delta * 24);
        clamp();
        return true;
    }

    public boolean mouseClicked(double mx, double my, int bouton) {
        if (bouton != 0 || !deborde()) return false;
        if (mx >= x + w - 6 && mx < x + w && my >= y && my < y + h) {
            drag = true;
            glisser(my);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mx, double my) {
        if (!drag) return false;
        glisser(my);
        return true;
    }

    public void mouseReleased() {
        drag = false;
    }

    private void glisser(double my) {
        float t = (float) ((my - y) / h);
        cible = Math.max(0, Math.min(max(), t * contentH - h / 2f));
        offset = cible;
    }

    /** Active la decoupe ; le contenu se dessine ensuite avec y - offset(). */
    public void begin(GuiGraphics g) {
        g.enableScissor(x, y, x + w, y + h);
    }

    /** Fin de la decoupe + barre. */
    public void end(GuiGraphics g) {
        g.disableScissor();
        if (!deborde()) return;
        int bx = x + w - 2;
        g.fill(bx, y, bx + 2, y + h, Ui.argb(0xFFFFFF, 14));
        int bh = Math.max(12, Math.round(h * (float) h / contentH));
        int by = y + Math.round((h - bh) * (offset / Math.max(1, max())));
        g.fill(bx, by, bx + 2, by + bh, Ui.argb(drag ? accent : 0xFFFFFF, drag ? 220 : 90));
    }
}
