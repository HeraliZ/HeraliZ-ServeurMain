package fr.heraliz.crates.client.gui;

import fr.heraliz.crates.loot.LootRarity;
import fr.heraliz.crates.network.Packets;
import fr.heraliz.noyau.client.BadgeRarete;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Outils de dessin des caisses : rarete d'une carte, formes aux coins adoucis, objet centre, infobulle d'une carte.
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

    public static boolean french() {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        return code != null && code.startsWith("fr");
    }

    /**
     * Infobulle d'une carte : nom, badge de rarete et chance, quantite, puis les lignes propres a l'objet (enchantements...).
     * 14/11 (demande du proprietaire : le meme badge de rarete partout) : la rarete de la carte est le badge du noyau
     * (BadgeRarete, rang de la caisse : Commun ... Mythique = Unique), suivi de sa chance ; avant, le mot (« Épique ·
     * 2,5 % »). Le badge de rarete de l'objet lui-meme (HeraliZ Loot, camos...) n'est pas repris : un seul par carte.
     */
    public static List<Component> tooltip(Packets.Card c) {
        LootRarity r = rarity(c.rarity());
        List<Component> vanilla = Screen.getTooltipFromItem(Minecraft.getInstance(), c.stack());
        List<Component> lines = new ArrayList<>();
        // nom brut : la couleur vient de la rarete de la caisse (un nom personnalise garde la sienne)
        Component name = c.stack().getHoverName();
        lines.add(Ui.in(Component.empty().append(name).withStyle(st -> st.withColor(r.color)), Ui.BOLD));
        lines.add(Component.empty().append(BadgeRarete.ligne(r.ordinal()))
                .append(Ui.in(Component.literal(Ui.pct(c.chance())).withStyle(st -> st.withColor(Ui.TEXT)), Ui.SMALL)));
        if (c.count() > 1) {
            lines.add(Ui.in(Ui.tr(Ui.SMALL, "gui.heralizcrates.amount", c.count()).withStyle(st -> st.withColor(Ui.MUTED)), Ui.SMALL));
        }
        for (int i = 1; i < vanilla.size() && i < 8; i++) {
            if (BadgeRarete.rangSeul(vanilla.get(i)) >= 0) continue;
            lines.add(Ui.in(Component.empty().append(vanilla.get(i)).withStyle(st -> st.withColor(Ui.MUTED)), Ui.SMALL));
        }
        return lines;
    }
}
