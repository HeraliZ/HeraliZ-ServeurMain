package fr.heraliz.armory.client;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.heraliz.armory.client.gui.Ui;
import fr.heraliz.armory.entity.Mannequin;
import fr.heraliz.armory.menu.MannequinMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;

import java.util.HashMap;
import java.util.Map;

/**
 * Ecran du mannequin, au style des interfaces HeraliZ (ecrans de clan) : verre, DM Sans, capitales espacees, cases
 * discretes. Armure et mains a gauche, apercu 3D au centre, equipement (slots Curios) a droite, inventaire en bas.
 * Les icones des cases vides sont redessinees en clair (les icones vanilla et Curios, sombres, se perdent sur le verre).
 */
public class MannequinScreen extends AbstractContainerScreen<MannequinMenu> {
    private static final int PREVIEW_X = 26, PREVIEW_W = 70, PREVIEW_H = 72;
    private static final ResourceLocation[] ARMOR_ICONS = {InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS, null, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD};
    private final Map<String, ResourceLocation> icons = new HashMap<>();

    public MannequinScreen(MannequinMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = MannequinMenu.WIDTH;
        imageHeight = MannequinMenu.HEIGHT;
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelX = 8;
        inventoryLabelY = MannequinMenu.INV_Y - 11;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        Ui.fond(g, width, height);
        super.render(g, mouseX, mouseY, partialTicks);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTicks, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        Ui.glass(g, x - 4, y - 4, imageWidth + 8, imageHeight + 8, 225);
        Ui.band(g, x - 2, y - 2, x + imageWidth + 2, y + 16, Ui.ACCENT, 26, 60);
        Ui.hairline(g, x + 4, x + imageWidth - 4, y + 16, 0xFFFFFF, 40, false, true);
        Ui.hairline(g, x + 6, x + imageWidth - 6, y + inventoryLabelY - 4, 0xFFFFFF, 30, true, true);
        int mannequinSlots = menu.curioStart() + menu.curioSlots.size();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            int sx = x + s.x - 1, sy = y + s.y - 1;
            boolean own = i < mannequinSlots;
            boolean hover = hoveredSlot == s;
            Ui.soft(g, sx, sy, 18, 18, Ui.argb(0x000000, own ? 120 : 95));
            Ui.softOutline(g, sx + 1, sy + 1, 16, 16, hover ? Ui.argb(Ui.ACCENT, 200) : Ui.argb(0xFFFFFF, own ? 26 : 18));
        }
        // icones des cases vides, en clair : armure et main gauche (atlas des blocs), puis slots d'equipement (Curios)
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(2.6F, 2.6F, 2.7F, 0.55F);
        for (int i = 0; i < ARMOR_ICONS.length && i < menu.curioStart(); i++) {
            Slot s = menu.slots.get(i);
            if (s.hasItem() || ARMOR_ICONS[i] == null) continue;
            g.blit(x + s.x, y + s.y, 0, 16, 16, minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ARMOR_ICONS[i]));
        }
        for (int k = 0; k < menu.curioSlots.size(); k++) {
            Slot s = menu.slots.get(menu.curioStart() + k);
            if (s.hasItem()) continue;
            ResourceLocation icon = icons.computeIfAbsent(menu.curioSlots.get(k).getFirst(), MannequinScreen::iconTexture);
            if (icon != null) g.blit(icon, x + s.x, y + s.y, 0, 0, 16, 16, 16, 16);
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        // apercu 3D du mannequin
        int px = x + PREVIEW_X, py = y + MannequinMenu.TOP - 1;
        Ui.soft(g, px, py, PREVIEW_W, PREVIEW_H, Ui.argb(0x000000, 110));
        Ui.softOutline(g, px + 1, py + 1, PREVIEW_W - 2, PREVIEW_H - 2, Ui.argb(0xFFFFFF, 20));
        if (minecraft != null && minecraft.level != null && minecraft.level.getEntity(menu.entityId()) instanceof Mannequin m) {
            int cx = px + PREVIEW_W / 2, cy = py + PREVIEW_H - 6;
            g.enableScissor(px + 1, py + 1, px + PREVIEW_W - 1, py + PREVIEW_H - 1);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, cy, 30, (float) cx - mouseX, (float) (cy - 50) - mouseY, m);
            g.disableScissor();
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, Ui.in(title, Ui.BOLD), titleLabelX, titleLabelY - 1, Ui.argb(Ui.TEXT, 255), false);
        if (!menu.curioSlots.isEmpty()) {
            Ui.spacedRight(g, font, Component.translatable("container.heraliz_armory.mannequin.curios").getString(), Ui.SMALL,
                    MannequinMenu.CURIO_X + 16, titleLabelY + 0.5F, Ui.argb(Ui.MUTED, 255), 1.0F);
        }
        Ui.spaced(g, font, playerInventoryTitle.getString(), Ui.SMALL, inventoryLabelX, inventoryLabelY + 0.5F, Ui.argb(Ui.MUTED, 255), 1.0F);
    }

    private static ResourceLocation iconTexture(String id) {
        ResourceLocation icon = CuriosApi.getSlot(id, true).map(ISlotType::getIcon).orElse(null);
        if (icon == null) icon = new ResourceLocation("curios", "slot/empty_curio_slot");
        return new ResourceLocation(icon.getNamespace(), "textures/" + icon.getPath() + ".png");
    }
}
