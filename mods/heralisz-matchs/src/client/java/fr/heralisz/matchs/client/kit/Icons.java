package fr.heralisz.matchs.client.kit;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Planche d'icones textures/gui/icons.png (produite par tools/clans/emblem_art.py) : 16 cases de 16 px sur UNE ligne
 * (256 x 16), blanches sur fond transparent, teintees au dessin. Index = ordre des constantes (= icons.json).
 */
public final class Icons {
    private Icons() {
    }

    /** Planche du mod clans (dependance obligatoire) : memes icones partout. */
    public static final ResourceLocation PLANCHE = new ResourceLocation("heralisz_clans", "textures/gui/icons.png");
    public static final int TAILLE = 16, COLONNES = 16, LIGNES = 1;
    public static final int LARGEUR = TAILLE * COLONNES, HAUTEUR = TAILLE * LIGNES;

    public static final int EN_LIGNE = 0, CHEF = 1, HAUT = 2, BAS = 3, CROIX = 4, PIECE = 5, CRANE = 6, HORLOGE = 7,
            DRAPEAU = 8, CADENAS = 9, FLECHE = 10, COCHE = 11, PLUS = 12, MOINS = 13, ETOILE = 14, INFO = 15;

    /** Dessine l'icone a 16 px, teintee (rgb) avec une opacite (0..255). */
    public static void draw(GuiGraphics g, int index, int x, int y, int rgb, int alpha) {
        draw(g, index, x, y, TAILLE, rgb, alpha);
    }

    /** Dessine l'icone a la taille voulue (8 ou 10 pour les listes, 16 normal). */
    public static void draw(GuiGraphics g, int index, int x, int y, int taille, int rgb, int alpha) {
        int u = (index % COLONNES) * TAILLE, v = (index / COLONNES) * TAILLE;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, Math.max(0, Math.min(255, alpha)) / 255f);
        g.blit(PLANCHE, x, y, taille, taille, u, v, TAILLE, TAILLE, LARGEUR, HAUTEUR);
        g.setColor(1, 1, 1, 1);
    }
}
