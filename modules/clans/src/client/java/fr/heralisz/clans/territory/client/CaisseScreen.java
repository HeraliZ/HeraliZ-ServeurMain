package fr.heralisz.clans.territory.client;

import fr.heralisz.clans.client.gui.kit.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Ecran de la caisse de ravitaillement (menu ChestMenu 3 rangees, type heralisz_clans:caisse_ravitaillement) au style
 * du kit : fond en degrade, panneau verre, titres en capitales espacees, cases discretes ; aucun element vanilla.
 */
@OnlyIn(Dist.CLIENT)
public class CaisseScreen extends AbstractContainerScreen<ChestMenu> {
    private static final int LARGEUR = 176, HAUTEUR = 168;

    public CaisseScreen(ChestMenu menu, Inventory inv, Component titre) {
        super(menu, inv, titre);
        this.imageWidth = LARGEUR;
        this.imageHeight = HAUTEUR;
        this.titleLabelY = 6;
        this.inventoryLabelY = HAUTEUR - 94;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    /** Le monde reste visible derriere un degrade (jamais un aplat). */
    @Override
    public void renderBackground(GuiGraphics g) {
        Ui.fond(g, width, height);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        Ui.glass(g, leftPos - 10, topPos - 8, imageWidth + 20, imageHeight + 14);
        int accent = Ui.accent();
        // filet sous le titre et entre la caisse et l'inventaire
        Ui.hairline(g, leftPos + 2, leftPos + imageWidth - 2, topPos + 16, accent, 90, true, true);
        Ui.hairline(g, leftPos + 2, leftPos + imageWidth - 2, topPos + inventoryLabelY - 3, 0xFFFFFF, 22, true, true);
        for (Slot s : menu.slots) {
            boolean caisse = s.container != minecraft.player.getInventory();
            Ui.soft(g, leftPos + s.x - 1, topPos + s.y - 1, 18, 18, Ui.argb(caisse ? accent : 0xFFFFFF, caisse ? 22 : 12));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // titres tenus dans la largeur de la caisse : espacement resserre, puis coupe avec « … »
        int large = imageWidth - 2;
        String t = title.getString();
        float esp = Ui.spacingFit(font, t, Ui.BOLD, 1.5f, 0.5f, large);
        Ui.spaced(g, font, Ui.spacedEllipsis(font, t, Ui.BOLD, esp, large), Ui.BOLD, 1, titleLabelY, Ui.argb(Ui.TEXT, 255), esp);
        String inv = playerInventoryTitle.getString();
        float espInv = Ui.spacingFit(font, inv, Ui.SMALL, 1.2f, 0.5f, large);
        Ui.spaced(g, font, Ui.spacedEllipsis(font, inv, Ui.SMALL, espInv, large), Ui.SMALL, 1, inventoryLabelY, Ui.argb(Ui.MUTED, 255), espInv);
    }
}
