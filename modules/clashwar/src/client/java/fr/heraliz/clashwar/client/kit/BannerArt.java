package fr.heraliz.clashwar.client.kit;

import fr.heralisz.clans.model.Banniere;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Bannieres de clan (v2, a calques) dans les ecrans des matchs : facade du rendu du mod clans
 * ({@code fr.heralisz.clans.client.gui.kit.BannerArt}, cache LRU et texture vivante), dependance obligatoire. Pas de
 * copie du moteur de rendu : une seule implementation, mise a jour par le chantier « banniere v2 ».
 * Les bannieres arrivent du serveur par {@code Banniere.write/read} (mod clans).
 */
public final class BannerArt {
    private BannerArt() {
    }

    /** Banniere nette a la taille physique (w x h en unites d'interface, proportions 2:3 conseillees). */
    public static void draw(GuiGraphics g, Banniere b, int x, int y, int w, int h) {
        fr.heralisz.clans.client.gui.kit.BannerArt.draw(g, b, x, y, w, h);
    }

    /** Idem avec une opacite 0..1. */
    public static void draw(GuiGraphics g, Banniere b, int x, int y, int w, int h, float alpha) {
        fr.heralisz.clans.client.gui.kit.BannerArt.draw(g, b, x, y, w, h, alpha);
    }

    /** Banniere animee (tissu qui ondule), pour les grands apercus. */
    public static void drawVivante(GuiGraphics g, Banniere b, int x, int y, int w, int h) {
        fr.heralisz.clans.client.gui.kit.BannerArt.drawVivante(g, b, x, y, w, h);
    }
}
