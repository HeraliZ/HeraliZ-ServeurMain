package fr.heraliz.crac.client.ui;

import fr.heraliz.crac.menu.CoffreMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Écran des coffres de plus de 9 colonnes (caisse d'armes, coffre-fort) : la texture de coffre de Minecraft (celle du pack
 * du serveur, style DayZ des coffres), élargie case par case ; l'inventaire du joueur centré dessous. Maquette validée le
 * 27/10 (maquettes/coffres_rarete/ecrans_grands_coffres.png, mêmes découpes).
 */
public class CoffreScreen extends AbstractContainerScreen<CoffreMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");
    private final int colonnes, lignes;

    public CoffreScreen(CoffreMenu menu, Inventory inv, Component titre) {
        super(menu, inv, titre);
        this.colonnes = menu.colonnes();
        this.lignes = menu.lignes();
        this.imageWidth = CoffreMenu.largeur(this.colonnes);
        this.imageHeight = 114 + this.lignes * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        int panneau = 14 + this.colonnes * 18;
        this.titleLabelX = (this.imageWidth - panneau) / 2 + 8;
        this.inventoryLabelX = (this.imageWidth - 176) / 2 + 8;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        this.renderBackground(g);
        super.render(g, mx, my, partial);
        this.renderTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int panneau = 14 + this.colonnes * 18;
        int x = this.leftPos + (this.imageWidth - panneau) / 2;
        int y = this.topPos;
        int droite = x + 7 + this.colonnes * 18;
        // haut du panneau (titre)
        g.blit(TEXTURE, x, y, 0, 0, 7, 17);
        for (int c = 0; c < this.colonnes; c++) {
            g.blit(TEXTURE, x + 7 + c * 18, y, 7, 0, 18, 17);
        }
        g.blit(TEXTURE, droite, y, 169, 0, 7, 17);
        // lignes de cases
        for (int r = 0; r < this.lignes; r++) {
            int yy = y + 17 + r * 18;
            g.blit(TEXTURE, x, yy, 0, 17, 7, 18);
            for (int c = 0; c < this.colonnes; c++) {
                g.blit(TEXTURE, x + 7 + c * 18, yy, 7, 17, 18, 18);
            }
            g.blit(TEXTURE, droite, yy, 169, 17, 7, 18);
        }
        // bas du panneau, puis l'inventaire du joueur (176 px de la texture), centré
        int yb = y + 17 + this.lignes * 18;
        g.blit(TEXTURE, x, yb, 0, 126, 7, 13);
        for (int c = 0; c < this.colonnes; c++) {
            g.blit(TEXTURE, x + 7 + c * 18, yb, 7, 126, 18, 13);
        }
        g.blit(TEXTURE, droite, yb, 169, 126, 7, 13);
        g.blit(TEXTURE, this.leftPos + (this.imageWidth - 176) / 2, yb + 13, 0, 139, 176, 83);
    }
}
