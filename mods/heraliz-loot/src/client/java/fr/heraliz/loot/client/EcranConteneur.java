package fr.heraliz.loot.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;

/**
 * Fouille d'un conteneur de loot T1-T6 (et des salles à carte) : l'écran de coffre de base, inchangé (style façon DayZ du
 * pack de textures), avec le badge du palier juste après le nom ({@link BadgePalier}). Ouvert à la place de l'écran de
 * coffre vanilla par {@link ClientLoot.Jeu#titreConteneur}.
 */
public class EcranConteneur extends ContainerScreen {
    private final int palier;

    public EcranConteneur(ChestMenu menu, Inventory inv, Component titre, int palier) {
        super(menu, inv, titre);
        this.palier = palier;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        super.renderLabels(g, mx, my);
        BadgePalier.dessiner(g, font, palier, titleLabelX + font.width(title) + 4, titleLabelY - 1);
    }
}
