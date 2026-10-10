package fr.heraliz.crac.client.kit;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Écran dessiné sur le canevas de 640 x 360 des écrans de clan (voir {@link Echelle}) sans changer l'échelle d'interface
 * du joueur : le contenu est agrandi ou réduit à l'affichage, la souris ramenée sur le canevas. Le HUD sous l'écran (roues
 * de construction) garde ainsi sa taille.
 */
public abstract class EcranCanevas extends Screen {
    /** Facteur canevas -> interface, taille du canevas, un pixel d'écran en unités du canevas. */
    protected float k = 1;
    protected int cw = 640, ch = 360;
    protected float px = 1 / 3F;

    protected EcranCanevas(Component titre) {
        super(titre);
    }

    @Override
    protected final void init() {
        Window w = this.minecraft.getWindow();
        int s = Echelle.voulue();
        this.k = (float) (s / w.getGuiScale());
        this.px = 1.0F / s;
        this.cw = Math.round(w.getWidth() / (float) s);
        this.ch = Math.round(w.getHeight() / (float) s);
        this.initCanevas();
    }

    /** init() sur le canevas : {@link #cw}, {@link #ch} et {@link #px} sont prêts. */
    protected void initCanevas() {
    }

    @Override
    public final void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        g.pose().pushPose();
        g.pose().scale(this.k, this.k, 1.0F);
        this.rendu(g, mouseX / this.k, mouseY / this.k, pt);
        g.pose().popPose();
    }

    /** Dessin, en unités du canevas. */
    protected abstract void rendu(GuiGraphics g, float mx, float my, float pt);

    /** Découpe en unités du canevas. */
    protected void decoupe(GuiGraphics g, float x0, float y0, float x1, float y1) {
        g.enableScissor((int) Math.floor(x0 * this.k), (int) Math.floor(y0 * this.k), (int) Math.ceil(x1 * this.k), (int) Math.ceil(y1 * this.k));
    }

    @Override
    public final boolean mouseClicked(double mx, double my, int bouton) {
        return this.clic(mx / this.k, my / this.k, bouton);
    }

    @Override
    public final boolean mouseReleased(double mx, double my, int bouton) {
        return this.relache(mx / this.k, my / this.k, bouton);
    }

    @Override
    public final boolean mouseDragged(double mx, double my, int bouton, double dx, double dy) {
        return this.glisse(mx / this.k, my / this.k, bouton, dx / this.k, dy / this.k);
    }

    @Override
    public final boolean mouseScrolled(double mx, double my, double delta) {
        return this.molette(mx / this.k, my / this.k, delta);
    }

    protected boolean clic(double mx, double my, int bouton) {
        return false;
    }

    protected boolean relache(double mx, double my, int bouton) {
        return false;
    }

    protected boolean glisse(double mx, double my, int bouton, double dx, double dy) {
        return false;
    }

    protected boolean molette(double mx, double my, double delta) {
        return false;
    }

    protected static boolean dans(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
