package fr.heraliz.loot.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;

/**
 * Fouille d'un conteneur de loot T1-T6 (et des salles à carte) : l'écran de coffre de base (style façon DayZ du pack de
 * textures), avec le badge du palier juste après le nom ({@link BadgePalier}). Depuis le 25/10, aussi tous les autres
 * coffres de base (planque, coffre VIP, coffre du clan, inspection…), palier 0 : sans badge. Ouvert à la place de
 * l'écran de coffre vanilla par {@link ClientLoot.Jeu#titreConteneur}.
 */
public class EcranConteneur extends ContainerScreen {
    private final int palier;

    public EcranConteneur(ChestMenu menu, Inventory inv, Component titre, int palier) {
        super(menu, inv, titre);
        this.palier = palier;
    }

    /** Titre clair sur le panneau gris du pack (25/10, audit des écrans : le gris foncé de base se lisait à peine). */
    private static final int TITRE = 0xFFE6E6E6;

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        // mêmes places que l'écran de base, nom coupé pour que le badge reste dans le panneau ; plus d'étiquette
        // « Inventaire » : avec le pack, elle tombait sur le bord entre les deux panneaux (coupée), et l'inventaire du
        // joueur n'en a pas non plus
        int place = palier >= 1 && palier <= 7 ? BadgePalier.largeur(font, palier) + 4 : 0;
        String nom = BadgePalier.couper(font, title.getString(), imageWidth - titleLabelX - 8 - place);
        g.drawString(font, nom, titleLabelX, titleLabelY, TITRE, false);
        if (palier >= 1 && palier <= 7) BadgePalier.dessiner(g, font, palier, titleLabelX + font.width(nom) + 4, titleLabelY - 1);
    }
}
