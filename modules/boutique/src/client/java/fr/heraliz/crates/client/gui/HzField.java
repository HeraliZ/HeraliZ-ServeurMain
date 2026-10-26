package fr.heraliz.crates.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Petit champ de saisie numerique sobre : fond sombre, contour fin (couleur d'accent quand il a le focus). La premiere
 * frappe apres le clic remplace la valeur ; Entree, Tab ou un clic ailleurs valident.
 */
public class HzField extends AbstractWidget {
    private String value = "";
    private final int maxLength;
    private final Predicate<String> filter;
    private Consumer<String> responder = v -> {
    };
    private boolean replace;
    private int accent = 0xFFFFFF;
    private String suffix = "";
    private int blink;

    public HzField(int x, int y, int w, int h, int maxLength, Predicate<String> filter) {
        super(x, y, w, h, Component.empty());
        this.maxLength = maxLength;
        this.filter = filter;
    }

    public HzField accent(int rgb) {
        this.accent = rgb & 0xFFFFFF;
        return this;
    }

    public HzField suffix(String s) {
        this.suffix = s;
        return this;
    }

    public void setResponder(Consumer<String> responder) {
        this.responder = responder;
    }

    public String getValue() {
        return value;
    }

    /** Change la valeur affichee sans prevenir le repondeur. */
    public void setValue(String v) {
        this.value = v;
    }

    public void tick() {
        blink++;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        replace = focused;
        blink = 0;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (!isFocused() || !active) return false;
        String next = (replace ? "" : value) + c;
        if (next.length() <= maxLength && filter.test(next)) {
            value = next;
            replace = false;
            responder.accept(value);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!isFocused()) return false;
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                value = replace || value.isEmpty() ? "" : value.substring(0, value.length() - 1);
                replace = false;
                responder.accept(value);
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                value = "";
                replace = false;
                responder.accept(value);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB -> {
                setFocused(false);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public void playDownSound(SoundManager sounds) {
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean focus = isFocused();
        // fond sombre et contour fin (couleur d'accent quand il a le focus)
        g.fill(x, y, x + w, y + h, Ui.argb(0x000000, focus ? 120 : isHovered ? 105 : 90));
        int edge = focus ? Ui.argb(accent, 230) : Ui.argb(0xFFFFFF, isHovered ? 56 : 31);
        g.fill(x, y, x + w, y + 1, edge);
        g.fill(x, y + h - 1, x + w, y + h, edge);
        g.fill(x, y + 1, x + 1, y + h - 1, edge);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, edge);
        Font font = Minecraft.getInstance().font;
        Component text = Ui.lit(value, Ui.BOLD);
        int tx = x + 4, ty = y + (h - 8) / 2;
        if (focus && replace && !value.isEmpty()) {
            g.fill(tx - 1, ty - 1, tx + font.width(text) + 1, ty + 9, Ui.argb(accent, 70));
        }
        g.drawString(font, text, tx, ty, Ui.argb(Ui.TEXT, 255), false);
        if (!suffix.isEmpty()) {
            g.drawString(font, Ui.lit(suffix, Ui.SMALL), x + w - 3 - font.width(Ui.lit(suffix, Ui.SMALL)), ty + 1, Ui.argb(Ui.MUTED, 255), false);
        }
        if (focus && !replace && (blink / 6) % 2 == 0) {
            int cx = tx + font.width(text) + 1;
            g.fill(cx, ty, cx + 1, ty + 9, Ui.argb(accent, 230));
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, Component.literal(value));
    }
}
