package fr.heralisz.economie.client;

import fr.heralisz.economie.client.gui.kit.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * HUD epure du solde. Depuis le 18/11, chaque variation s'affiche dans un bandeau commun a droite de l'ecran
 * ({@link ClientSolde#recevoir}, maquette validee) ; ici ne reste que le total permanent : si hud_permanent, il est
 * affiche en petit (capitales espacees) dans le coin. Au repos sans HUD permanent, le rendu sort tout de suite.
 */
public final class SoldeHud {
    private SoldeHud() {
    }

    private static final int MARGE = 8;

    public static void render(GuiGraphics g, int largeur, int hauteur) {
        if (!ClientSolde.connu()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.options.renderDebug || mc.player == null) return;
        if (ClientSolde.hudPermanent()) permanent(g, mc.font, largeur);
    }

    /** Solde permanent : « 1 250 HÉRALS » en petites capitales espacees, attenue, dans le coin haut droit. */
    private static void permanent(GuiGraphics g, Font font, int largeur) {
        long solde = ClientSolde.solde();
        String chiffres = fr.heralisz.economie.api.EconomieApi.nombre(solde);
        String unite = ClientSolde.unite(solde);
        float wu = Ui.spacedWidth(font, unite.toUpperCase(java.util.Locale.ROOT), Ui.SMALL, 0.8f);
        Component c = Ui.lit(chiffres, Ui.BOLD);
        int wc = font.width(c);
        int xRight = largeur - MARGE;
        float y = MARGE + fr.heraliz.noyau.client.effets.EffetsHud.decalageHautDroite();
        Ui.spacedRight(g, font, unite, Ui.SMALL, xRight, y + 1.5f, Ui.argb(Ui.MUTED, 200), 0.8f);
        Ui.texte(g, font, c, xRight - wu - 4 - wc, y, Ui.TEXT, 220);
    }
}
