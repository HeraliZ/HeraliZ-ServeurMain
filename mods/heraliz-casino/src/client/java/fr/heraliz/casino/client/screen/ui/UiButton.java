package fr.heraliz.casino.client.screen.ui;

import fr.heraliz.casino.client.render.scene.UiAtlas;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Bouton du casino (atlas HD) : arrondi ou rond, couleur, libelle, icone d'objet, etat "choisi".
 */
public class UiButton extends AbstractButton {
    private final Consumer<UiButton> action;
    public int style;
    public boolean round;
    public boolean selected;
    public float textScale = 1f;
    public boolean bold = true;
    public Supplier<Component> label;
    public ItemStack icon = ItemStack.EMPTY;
    public BooleanSupplier enabledWhen;
    public Supplier<Component> tooltip;
    /** Couleur du texte imposee (sinon celle du style). */
    public int textColor = -1;
    /** Pastille de couleur a gauche du libelle (-1 = aucune). */
    public int swatch = -1;

    public UiButton(int w, int h, Component label, int style, Consumer<UiButton> action) {
        super(0, 0, w, h, label);
        this.style = style;
        this.action = action;
        this.label = () -> label;
    }

    public UiButton label(Supplier<Component> l) {
        this.label = l;
        return this;
    }

    public UiButton when(BooleanSupplier c) {
        this.enabledWhen = c;
        return this;
    }

    public UiButton tip(Supplier<Component> t) {
        this.tooltip = t;
        return this;
    }

    public UiButton scale(float k) {
        this.textScale = k;
        return this;
    }

    public UiButton icon(ItemStack s) {
        this.icon = s;
        return this;
    }

    public UiButton round() {
        this.round = true;
        return this;
    }

    public UiButton color(int rgb) {
        this.textColor = rgb;
        return this;
    }

    public UiButton swatch(int rgb) {
        this.swatch = rgb;
        return this;
    }

    public void refresh() {
        if (enabledWhen != null) this.active = enabledWhen.getAsBoolean();
    }

    @Override
    public void onPress() {
        action.accept(this);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        if (UiKit.clan) {
            renderClan(g);
            return;
        }
        int x = getX(), y = getY(), w = width, h = height;
        // bouton choisi : fond "survol" (plus clair) et petit trait dore sous le libelle, sans contour
        boolean hover = (isHoveredOrFocused() || selected) && active;
        int st = active || selected ? style : UiAtlas.GREY;
        if (round) {
            UiKit.round(g, st, hover, x, y, Math.min(w, h));
        } else {
            UiKit.button(g, st, hover, x, y, w, h);
        }
        if (selected && !round) g.fill(x + w / 2 - 8, y + h - 3, x + w / 2 + 8, y + h - 2, 0xE0FFD36A);
        Component text = label.get();
        int color = !active && !selected ? UiAtlas.STYLE_TEXT[UiAtlas.GREY] : textColor >= 0 ? textColor : UiAtlas.STYLE_TEXT[style];
        float k = textScale;
        if (bold && text != null) text = text.copy().withStyle(s -> s.withBold(true));
        int iconW = icon.isEmpty() ? 0 : 13;
        int swW = swatch >= 0 ? 9 : 0;
        float tw = text == null ? 0 : UiKit.font().width(text) * k;
        float maxText = w - 6 - iconW - swW;
        if (tw > maxText && tw > 0) {
            k *= maxText / tw;
            tw = maxText;
        }
        float total = tw + iconW + swW;
        float sx = x + (w - total) / 2f;
        if (swW > 0) {
            g.fill((int) sx, y + h / 2 - 3, (int) sx + 6, y + h / 2 + 3, 0xFF000000 | swatch);
            g.fill((int) sx, y + h / 2 - 3, (int) sx + 6, y + h / 2 - 2, 0x60FFFFFF);
            sx += swW;
        }
        if (iconW > 0) {
            g.pose().pushPose();
            g.pose().translate(sx - 1, y + (h - 12) / 2f, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
            sx += iconW;
        }
        if (text != null) {
            g.pose().pushPose();
            g.pose().translate(sx, y + (h - 8 * k) / 2f + 0.5f, 100);
            g.pose().scale(k, k, 1);
            boolean shadow = true;
            g.drawString(UiKit.font(), text, 0, 0, 0xFF000000 | color, shadow);
            g.pose().popPose();
        }
    }

    /**
     * Style des interfaces HeraliZ (ecrans d'administration) : contour fin, survol a l'accent, choisi = teinte d'accent
     * et filet dessous, bouton or = bouton principal ; la couleur propre d'une entree devient une petite pastille.
     */
    private void renderClan(GuiGraphics g) {
        int x = getX(), y = getY(), w = width, h = height;
        boolean hover = isHoveredOrFocused() && active;
        int accent = fr.heraliz.casino.client.screen.kit.Ui.accent();
        boolean primary = style == UiAtlas.GOLD;
        int fill, edge;
        if (!active && !selected) {
            fill = 0x0AFFFFFF;
            edge = 0x14FFFFFF;
        } else if (selected) {
            fill = fr.heraliz.casino.client.screen.kit.Ui.argb(accent, hover ? 50 : 34);
            edge = fr.heraliz.casino.client.screen.kit.Ui.argb(accent, 140);
        } else if (primary) {
            fill = fr.heraliz.casino.client.screen.kit.Ui.argb(accent, hover ? 70 : 34);
            edge = fr.heraliz.casino.client.screen.kit.Ui.argb(fr.heraliz.casino.client.screen.kit.Ui.mix(accent, 0xFFFFFF, hover ? 0.3f : 0f), hover ? 255 : 170);
        } else {
            fill = hover ? 0x16FFFFFF : 0x08FFFFFF;
            edge = hover ? fr.heraliz.casino.client.screen.kit.Ui.argb(accent, 220) : 0x24FFFFFF;
        }
        Component text = label.get();
        String s = text == null ? "" : text.getString();
        if (round || s.equals("✕")) {
            // croix de fermeture dessinee au pixel
            int c = hover ? 0xFFECEEF2 : 0xFF8F95A0, cx = x + (w - 7) / 2, cy = y + (h - 7) / 2;
            if (!s.equals("✕")) {
                fr.heraliz.casino.client.screen.kit.Ui.soft(g, x, y, w, h, fill);
                UiKit.text(g, text, x + w / 2f, y + (h - 8) / 2f + 0.5f, 0xECEEF2, textScale, 0, false);
                return;
            }
            for (int i = 0; i < 7; i++) {
                g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, c);
                g.fill(cx + 6 - i, cy + i, cx + 7 - i, cy + i + 1, c);
            }
            return;
        }
        // entree de liste (couleur propre = machine) : ligne sans contour, barre d'accent a gauche quand elle est choisie
        boolean row = textColor >= 0 && !primary;
        if (row) {
            fr.heraliz.casino.client.screen.kit.Ui.soft(g, x, y, w, h, selected ? fr.heraliz.casino.client.screen.kit.Ui.argb(accent, hover ? 44 : 30)
                    : hover ? 0x14FFFFFF : 0x06FFFFFF);
            if (selected) g.fill(x, y + 2, x + 2, y + h - 2, fr.heraliz.casino.client.screen.kit.Ui.argb(accent, 255));
        } else {
            fr.heraliz.casino.client.screen.kit.Ui.soft(g, x, y, w, h, fill);
            fr.heraliz.casino.client.screen.kit.Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, edge);
            if (selected) g.fill(x + 3, y + h - 2, x + w - 3, y + h - 1, fr.heraliz.casino.client.screen.kit.Ui.argb(accent, 230));
        }
        if (text == null) return;
        int color = !active && !selected ? 0x5A5F69 : selected || hover || primary ? 0xFFFFFF : 0xC9CDD6;
        Component t2 = text.copy().withStyle(st -> st.withBold(false).withFont(bold || primary
                ? fr.heraliz.casino.client.screen.kit.Ui.BOLD : fr.heraliz.casino.client.screen.kit.Ui.FONT));
        float k = textScale;
        int iconW = icon.isEmpty() ? 0 : 13;
        int swW = textColor >= 0 || swatch >= 0 ? 8 : 0;
        float tw = UiKit.font().width(t2) * k;
        float maxText = w - 8 - iconW - swW;
        if (tw > maxText && tw > 0) {
            k *= maxText / tw;
            tw = maxText;
        }
        float sx = row ? x + 8 : x + (w - (tw + iconW + swW)) / 2f;
        if (swW > 0) {
            int rgb = swatch >= 0 ? swatch : textColor;
            int dy = y + h / 2 - 2;
            g.fill((int) sx + 1, dy, (int) sx + 4, dy + 5, 0xFF000000 | rgb);
            g.fill((int) sx, dy + 1, (int) sx + 5, dy + 4, 0xFF000000 | rgb);
            sx += swW;
        }
        if (iconW > 0) {
            g.pose().pushPose();
            g.pose().translate(sx - 1, y + (h - 12) / 2f, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
            sx += iconW;
        }
        g.pose().pushPose();
        g.pose().translate(sx, y + (h - 8 * k) / 2f + 0.5f, 100);
        g.pose().scale(k, k, 1);
        g.drawString(UiKit.font(), t2, 0, 0, 0xFF000000 | color, false);
        g.pose().popPose();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
