package fr.heralisz.bases.client.gui.kit;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.heralisz.bases.HeraliszBases;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Planche d'icones textures/gui/icones.png (produite par tools/bases/gen_icones.py) : 16 cases de 64 px sur UNE ligne
 * (1024 x 64, 4x pour rester nettes aux echelles 3 et 4), blanches sur fond transparent, teintees au dessin.
 * Index = ordre des constantes (= liste ICONES du script).
 */
public final class Icons {
    private Icons() {
    }

    public static final ResourceLocation PLANCHE = HeraliszBases.id("textures/gui/icones.png");
    public static final int CASE = 64, COLONNES = 16;
    public static final int LARGEUR = CASE * COLONNES, HAUTEUR = CASE;

    public static final int CROIX = 0, CADENAS = 1, CADENAS_OUVERT = 2, ENTRER = 3, SORTIR = 4, BOUCLIER = 5, CRANE = 6,
            CIBLE = 7, HORLOGE = 8, ALERTE = 9, COCHE = 10, INFO = 11, MEMBRES = 12, PIECE = 13, BUNKER = 14, VIE = 15;

    /** Dessine l'icone a la taille voulue (px d'interface), teintee (rgb) avec une opacite (0..255). */
    public static void draw(GuiGraphics g, int index, int x, int y, int taille, int rgb, int alpha) {
        int u = (index % COLONNES) * CASE;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, Math.max(0, Math.min(255, alpha)) / 255f);
        g.blit(PLANCHE, x, y, taille, taille, u, 0, CASE, CASE, LARGEUR, HAUTEUR);
        g.setColor(1, 1, 1, 1);
    }
}
