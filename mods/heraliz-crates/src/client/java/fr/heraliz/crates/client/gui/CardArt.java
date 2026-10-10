package fr.heraliz.crates.client.gui;

import com.mojang.math.Axis;
import fr.heraliz.crates.loot.LootRarity;
import fr.heraliz.crates.network.Packets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Cartes d'objets de la roue : fond sombre aux coins adoucis, lueur et filet a la couleur de la rarete.
 */
public final class CardArt {
    private CardArt() {
    }

    public static LootRarity rarity(int ordinal) {
        LootRarity[] all = LootRarity.values();
        return all[Math.max(0, Math.min(all.length - 1, ordinal))];
    }

    public static int color(int rarity) {
        return rarity(rarity).color & 0xFFFFFF;
    }

    /** Rectangle aux coins adoucis (un pixel retire a chaque coin). */
    public static void soft(GuiGraphics g, int x, int y, int w, int h, int argb) {
        g.fill(x + 1, y, x + w - 1, y + 1, argb);
        g.fill(x, y + 1, x + w, y + h - 1, argb);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, argb);
    }

    /** Contour d'un pixel aux coins adoucis. */
    public static void softOutline(GuiGraphics g, int x, int y, int w, int h, int argb) {
        g.fill(x + 1, y - 1, x + w - 1, y, argb);
        g.fill(x + 1, y + h, x + w - 1, y + h + 1, argb);
        g.fill(x - 1, y + 1, x, y + h - 1, argb);
        g.fill(x + w, y + 1, x + w + 1, y + h - 1, argb);
    }

    /** Petites capitales et textes fins des cartes (DM Sans 6,5). */
    public static final net.minecraft.resources.ResourceLocation MINI = fr.heraliz.crates.HeralizCrates.id("ui_mini"),
            MINI_GRAS = fr.heraliz.crates.HeralizCrates.id("ui_mini_bold");

    /**
     * Carte d'objet (maquette validée) : fond sombre avec un halo clair derrière l'objet, contour fin, dégradé et trait à la
     * couleur de la rareté en bas, nom de l'objet sous l'image. glow (0..1) : objet gagné (contour et halo de sa couleur) ;
     * dim (0..1) : carte éteinte (les autres après l'arrêt).
     */
    public static void card(GuiGraphics g, Font font, Packets.Card c, int x, int y, int w, int h, int itemPx, float glow, float dim) {
        int col = color(c.rarity());
        if (glow > 0) {
            softOutline(g, x - 2, y - 2, w + 4, h + 4, Ui.argb(col, (int) (50 * glow)));
            softOutline(g, x - 1, y - 1, w + 2, h + 2, Ui.argb(col, (int) (200 * glow)));
        }
        soft(g, x, y, w, h, 0xFF16181E);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h * 3 / 5, 0xFF262A33, 0x0016181E);
        // halo clair derrière l'objet
        int hx = x + w / 2, hy = y + h * 2 / 5;
        g.fillGradient(hx - w / 3, hy - w / 3, hx + w / 3, hy, 0x00FFFFFF, 0x14FFFFFF);
        g.fillGradient(hx - w / 3, hy, hx + w / 3, hy + w / 3, 0x14FFFFFF, 0x00FFFFFF);
        g.fillGradient(x + 1, y + h - h * 3 / 8, x + w - 1, y + h - 1, Ui.argb(col, 0), Ui.argb(col, glow > 0 ? 90 : 64));
        softOutline(g, x + 1, y + 1, w - 2, h - 2, Ui.argb(0xFFFFFF, 26));
        g.fill(x, y + h - 1, x + w, y + h, Ui.argb(col, 255));
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, Ui.argb(col, 200));
        item(g, c.stack(), x + w / 2f, y + itemPx / 2f + 5, itemPx);
        // nom, coupé avec « … » s'il est trop long ; quantité en haut à droite
        String nom = c.stack().getHoverName().getString();
        Component n = Ui.lit(nom, MINI);
        int max = w - 6;
        if (font.width(n) > max) {
            while (nom.length() > 1 && font.width(Ui.lit(nom + "…", MINI)) > max) nom = nom.substring(0, nom.length() - 1);
            n = Ui.lit(nom.trim() + "…", MINI);
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        g.drawString(font, n, x + (w - font.width(n)) / 2, y + h - 12, Ui.argb(0xD9DDE3, 255), false);
        if (c.count() > 1) {
            Component q = Ui.lit("×" + c.count(), MINI_GRAS);
            g.drawString(font, q, x + w - 3 - font.width(q), y + 3, Ui.argb(0xFFFFFF, 200), false);
        }
        g.pose().popPose();
        if (dim > 0) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 260);
            soft(g, x, y, w, h, Ui.argb(0x07080A, (int) (130 * dim)));
            g.pose().popPose();
        }
    }

    /** Objet centre en (cx, cy), de px pixels de cote. */
    public static void item(GuiGraphics g, ItemStack stack, float cx, float cy, float px) {
        if (stack.isEmpty()) return;
        float s = px / 16f;
        g.pose().pushPose();
        g.pose().translate(cx - px / 2f, cy - px / 2f, 0);
        g.pose().scale(s, s, 1);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    /** Rayons tournants (objet legendaire ou mythique). */
    public static void rays(GuiGraphics g, float cx, float cy, float len, int rgb, float time, float alpha) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        for (int k = 0; k < 12; k++) {
            g.pose().pushPose();
            g.pose().mulPose(Axis.ZP.rotationDegrees(k * 30 + time * 0.8f));
            g.fillGradient(-1, 0, 1, (int) len, Ui.argb(rgb, (int) (70 * alpha)), Ui.argb(rgb, 0));
            g.pose().popPose();
        }
        g.pose().popPose();
    }

    public static boolean french() {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        return code != null && code.startsWith("fr");
    }

    /** Infobulle d'une carte : nom, rarete et chance, quantite, puis les lignes propres a l'objet (enchantements...). */
    public static List<Component> tooltip(Packets.Card c) {
        LootRarity r = rarity(c.rarity());
        List<Component> vanilla = Screen.getTooltipFromItem(Minecraft.getInstance(), c.stack());
        List<Component> lines = new ArrayList<>();
        // nom brut : la couleur vient de la rarete de la caisse (un nom personnalise garde la sienne)
        Component name = c.stack().getHoverName();
        lines.add(Ui.in(Component.empty().append(name).withStyle(st -> st.withColor(r.color)), Ui.BOLD));
        lines.add(Ui.in(Component.empty()
                .append(r.displayName())
                .append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(Ui.pct(c.chance())).withStyle(st -> st.withColor(Ui.TEXT))), Ui.SMALL));
        if (c.count() > 1) {
            lines.add(Ui.in(Ui.tr(Ui.SMALL, "gui.heralizcrates.amount", c.count()).withStyle(st -> st.withColor(Ui.MUTED)), Ui.SMALL));
        }
        for (int i = 1; i < vanilla.size() && i < 8; i++) {
            lines.add(Ui.in(Component.empty().append(vanilla.get(i)).withStyle(st -> st.withColor(Ui.MUTED)), Ui.SMALL));
        }
        return lines;
    }
}
