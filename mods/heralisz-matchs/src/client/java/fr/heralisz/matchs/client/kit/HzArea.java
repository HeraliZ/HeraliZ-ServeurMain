package fr.heralisz.matchs.client.kit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

/**
 * Zone de texte sur 3 lignes (description du clan) : retour a la ligne automatique, Entree = nouvelle ligne (3 au
 * plus), 140 caracteres au plus, curseur en fin de texte, compteur discret. Meme habillage que HzField.
 */
public class HzArea extends AbstractWidget {
    private final int maxLength, maxLines;
    private String value = "";
    private String placeholder = "", erreur = "";
    private int accent = 0xFFFFFF;
    private int blink;
    private Consumer<String> responder = v -> {
    };

    public HzArea(int x, int y, int w, int h, int maxLength, int maxLines) {
        super(x, y, w, h, Component.empty());
        this.maxLength = maxLength;
        this.maxLines = maxLines;
    }

    public HzArea accent(int rgb) {
        this.accent = rgb & 0xFFFFFF;
        return this;
    }

    public HzArea placeholder(String s) {
        this.placeholder = s == null ? "" : s;
        return this;
    }

    public void setResponder(Consumer<String> r) {
        this.responder = r;
    }

    public void setErreur(String e) {
        this.erreur = e == null ? "" : e;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String v) {
        this.value = v == null ? "" : v;
    }

    public void tick() {
        blink++;
    }

    @Override
    public void setFocused(boolean f) {
        super.setFocused(f);
        blink = 0;
    }

    private List<FormattedCharSequence> lignes(Font font, String s) {
        return font.split(Ui.lit(s.isEmpty() ? " " : s, Ui.FONT), getWidth() - 8);
    }

    /** Accepte le texte candidat s'il tient (longueur, lignes explicites et lignes apres repli). */
    private void accepter(String next) {
        if (next.length() > maxLength) return;
        if (next.split("\n", -1).length > maxLines) return;
        if (lignes(Minecraft.getInstance().font, next).size() > maxLines) return;
        value = next;
        responder.accept(value);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (!isFocused() || !active || c < 32) return false;
        accepter(value + c);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!isFocused()) return false;
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!value.isEmpty()) {
                    value = value.substring(0, value.offsetByCodePoints(value.length(), -1));
                    responder.accept(value);
                }
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                accepter(value + "\n");
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                setFocused(false);
                return true;
            }
            case GLFW.GLFW_KEY_V -> {
                if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) {
                    String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                    if (clip != null) accepter(value + clip.replace("\r", ""));
                    return true;
                }
                return false;
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
        boolean err = !erreur.isEmpty();
        g.fill(x, y, x + w, y + h, Ui.argb(0x000000, !active ? 50 : focus ? 150 : isHovered ? 110 : 80));
        g.fill(x, y + h - 1, x + w, y + h, err ? Ui.argb(Ui.DANGER, 220) : focus ? Ui.argb(accent, 230) : Ui.argb(0xFFFFFF, isHovered ? 70 : 34));
        Font font = Minecraft.getInstance().font;
        int tx = x + 4, ty = y + 4;
        if (value.isEmpty()) {
            if (!placeholder.isEmpty()) g.drawString(font, Ui.lit(placeholder, Ui.FONT), tx, ty, Ui.argb(Ui.FAINT, 255), false);
            if (focus && (blink / 6) % 2 == 0) g.fill(tx, ty, tx + 1, ty + 9, Ui.argb(accent, 230));
        } else {
            List<FormattedCharSequence> l = lignes(font, value);
            int ly = ty, lastW = 0;
            for (FormattedCharSequence seq : l) {
                g.drawString(font, seq, tx, ly, Ui.argb(active ? Ui.TEXT : Ui.FAINT, 255), false);
                lastW = font.width(seq);
                ly += 10;
            }
            boolean finLigne = value.endsWith("\n");
            if (focus && (blink / 6) % 2 == 0) {
                int cx = finLigne ? tx : tx + lastW + 1, cy = finLigne ? ly : ly - 10;
                g.fill(cx, cy, cx + 1, cy + 9, Ui.argb(accent, 230));
            }
        }
        String compteur = value.length() + "/" + maxLength;
        g.drawString(font, Ui.lit(compteur, Ui.SMALL), x + w - 3 - font.width(Ui.lit(compteur, Ui.SMALL)), y + h - 9, Ui.argb(Ui.FAINT, 255), false);
        if (err) g.drawString(font, Ui.lit(erreur, Ui.SMALL), x, y + h + 2, Ui.argb(Ui.DANGER, 255), false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, Component.literal(value));
    }
}
