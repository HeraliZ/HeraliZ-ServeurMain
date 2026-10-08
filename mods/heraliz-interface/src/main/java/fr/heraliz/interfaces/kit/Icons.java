package fr.heraliz.interfaces.kit;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.heraliz.interfaces.HeralizInterface;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Planche d'icones textures/gui/icones.png (produite par src/heraliz-interface/tools/gen_icones.py) : cases de 64 px
 * sur UNE ligne (4x pour rester nettes aux echelles 3 et 4, filtrage lineaire), blanches sur fond transparent,
 * teintees au dessin. Index = ordre des constantes (= liste ICONES du script ; ajouter a la fin, jamais reordonner).
 */
public final class Icons {
    private Icons() {
    }

    public static final ResourceLocation PLANCHE = HeralizInterface.id("textures/gui/icones.png");
    public static final int CASE = 64, COLONNES = 20;
    public static final int LARGEUR = CASE * COLONNES, HAUTEUR = CASE;

    public static final int CROIX = 0, REPRENDRE = 1, DISCUSSION = 2, BOUTIQUE = 3, INFO = 4, OPTIONS = 5, SUCCES = 6,
            STATS = 7, QUITTER = 8, LAN = 9, MODS = 10, JOUEURS = 11, EXTERNE = 12, CHEVRON = 13, RETOUR = 14, LIVRE = 15,
            TERMINAL = 16, BOUCLIER = 17, HORLOGE = 18, PIECE = 19;

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
