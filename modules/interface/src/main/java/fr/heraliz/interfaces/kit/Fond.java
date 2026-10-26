package fr.heraliz.interfaces.kit;

import fr.heraliz.interfaces.InterfaceConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Fond plein ecran de nos menus : monde floute ({@link Flou}), voile diagonal (jamais uni), vignette sur les bords et
 * lueur sombre douce derriere le logo. {@code t} = progression de l'ouverture (0..1) : le flou et le voile montent
 * ensemble.
 */
public final class Fond {
    private Fond() {
    }

    /** Dessine le fond ; a appeler en tout debut de render(). */
    public static void dessiner(GuiGraphics g, int w, int h, float t) {
        g.flush();
        if (InterfaceConfig.oui(InterfaceConfig.FLOU, true) && Flou.disponible()) {
            Flou.appliquer(InterfaceConfig.entier(InterfaceConfig.FLOU_FORCE, 10) * (float) Minecraft.getInstance().getWindow().getGuiScale() / 3f * t);
        }
        boolean flou = InterfaceConfig.oui(InterfaceConfig.FLOU, true) && Flou.disponible();
        // voile diagonal : plus leger avec le flou (le monde reste devinable), plus dense sans
        int a0 = flou ? 0x50 : 0x8C, a1 = flou ? 0x88 : 0xB4;
        int bandes = 24;
        for (int i = 0; i < bandes; i++) {
            int x0 = w * i / bandes, x1 = w * (i + 1) / bandes;
            float u = (i + 0.5f) / bandes;
            int haut = Ui.argb(0x05060A, (int) ((a0 + (a1 - a0) * u * 0.5f) * t));
            int bas = Ui.argb(0x05060A, (int) ((a0 + (a1 - a0) * (0.5f + u * 0.5f)) * t));
            g.fillGradient(x0, 0, x1, h, haut, bas);
        }
        vignette(g, w, h, (int) (110 * t));
    }

    /** Bords assombris (haut, bas, gauche, droite) qui s'effacent vers le centre. */
    public static void vignette(GuiGraphics g, int w, int h, int alpha) {
        if (alpha <= 0) return;
        int bh = h / 4;
        g.fillGradient(0, 0, w, bh, Ui.argb(0, alpha), Ui.argb(0, 0));
        g.fillGradient(0, h - bh, w, h, Ui.argb(0, 0), Ui.argb(0, alpha));
        int bw = w / 5, pas = 16;
        for (int i = 0; i < pas; i++) {
            float u = 1 - (i + 0.5f) / pas;
            int a = (int) (alpha * u * u);
            int x0 = bw * i / pas, x1 = bw * (i + 1) / pas;
            g.fill(x0, 0, x1, h, Ui.argb(0, a));
            g.fill(w - x1, 0, w - x0, h, Ui.argb(0, a));
        }
    }

    /**
     * Lueur douce (ellipse sans bord net) centree en (cx, cy) : disques concentriques peu opaques, etires en largeur.
     * Sert de « plaque » sombre derriere le logo (rgb 0) ou de halo colore discret.
     */
    public static void lueur(GuiGraphics g, float cx, float cy, float rayonX, float rayonY, int rgb, int alpha) {
        if (alpha <= 0 || rayonX <= 0 || rayonY <= 0) return;
        int n = 14;
        int parDisque = Math.max(1, alpha / n);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(rayonX / rayonY, 1f, 1f);
        for (int i = 0; i < n; i++) {
            float r = rayonY * (1f - i / (float) n);
            Ui.disque(g, 0, 0, r, Ui.argb(rgb, parDisque));
        }
        g.pose().popPose();
    }
}
