package fr.heraliz.armory.client;

import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.armor.Protection;
import fr.heraliz.armory.client.gui.Ui;
import fr.heraliz.armory.item.PieceItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.client.gui.CuriosScreen;
import top.theillusivec4.curios.client.gui.CuriosScreenV2;

/**
 * Bandeau « Protection » sous la fenetre d'inventaire (et du panneau Curios) : cinq cases (balles, explosion, corps a
 * corps, morsure, radiation) avec le % TOTAL du stuff porte (0.9.0 : pieces, combinaisons et objets repris de HeralisZ
 * Armor, un seul calcul), plafonne comme en jeu (75 %, radiation 100 %). Meme emplacement que l'ancien bandeau de
 * HeralisZ Armor (fond opaque). Style des interfaces HeraliZ.
 */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID, value = Dist.CLIENT)
public final class ProtectionPanel {
    private ProtectionPanel() {}

    private static final String[] GLYPHS = {PieceItem.BULLET, PieceItem.EXPLOSION, PieceItem.MELEE, PieceItem.BITE, PieceItem.RADIATION};
    private static final int H = 14, GAP = 2;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || !(screen instanceof InventoryScreen || screen instanceof CuriosScreen || screen instanceof CuriosScreenV2)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Protection.Totals t = Protection.totals(Protection.worn(mc.player));
        float[] values = {t.bullet(), t.explosion(), t.melee(), t.bite(), t.radiation()};
        int n = values.length;
        // meme geometrie que l'ancien bandeau de HeralisZ Armor
        int cell = (screen.getXSize() - GAP * (n - 1)) / n;
        int x0 = screen.getGuiLeft() + (screen.getXSize() - (cell * n + GAP * (n - 1))) / 2;
        int y0 = screen.getGuiTop() + screen.getYSize() + 3;
        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        g.pose().pushPose();
        g.pose().translate(0, 0, 320);
        for (int k = 0; k < n; k++) {
            int cx = x0 + k * (cell + GAP);
            int v = Math.round(values[k]);
            // fond opaque, puis verre : contour, lisere a l'accent si la valeur est non nulle
            g.fill(cx, y0, cx + cell, y0 + H, 0xFF0D0F14);
            Ui.softOutline(g, cx + 1, y0 + 1, cell - 2, H - 2, Ui.argb(0xFFFFFF, 26));
            g.fill(cx + 2, y0 + H - 2, cx + cell - 2, y0 + H - 1, v > 0 ? Ui.argb(Ui.OR, 200) : Ui.argb(0xFFFFFF, 18));
            Component icon = Component.literal(GLYPHS[k]).withStyle(PieceItem.ICON_FONT);
            Component text = Ui.lit(v + " %", Ui.FONT);
            int iw = font.width(icon);
            int w = iw + 3 + font.width(text);
            int ix = cx + (cell - w) / 2;
            g.drawString(font, icon, ix, y0 + 3, 0xFFFFFF, false);
            g.drawString(font, text, ix + iw + 3, y0 + 3, v > 0 ? Ui.argb(Ui.TEXT, 255) : Ui.argb(Ui.FAINT, 255), false);
        }
        g.pose().popPose();
    }
}
