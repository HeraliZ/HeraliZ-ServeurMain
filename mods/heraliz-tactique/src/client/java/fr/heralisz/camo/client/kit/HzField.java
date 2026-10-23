package fr.heralisz.camo.client.kit;

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
import java.util.function.UnaryOperator;

/**
 * Champ de saisie sobre (texte libre ou nombre) : fond sombre, filet en bas (couleur d'accent quand il a le focus),
 * texte d'invite grise quand il est vide, longueur max, filtre sur la valeur candidate, transformation (ex. majuscules),
 * ligne d'erreur optionnelle en rouge dessous. Ctrl+V colle. Entree, Tab ou un clic ailleurs valident.
 */
public class HzField extends AbstractWidget {
    /** Filtres usuels. */
    public static final Predicate<String> NOMBRE = s -> s.matches("[0-9]*");
    public static final Predicate<String> TAG = s -> s.matches("[A-Za-z0-9]*");
    public static final Predicate<String> HEXA = s -> s.matches("[0-9A-Fa-f]*");
    public static final Predicate<String> TOUT = s -> true;

    private String value = "";
    private final int maxLength;
    private final Predicate<String> filter;
    private UnaryOperator<String> transform = s -> s;
    private Consumer<String> responder = v -> {
    };
    private boolean replace, replaceOnFocus = true, erreurVisible = true;
    private int accent = 0xFFFFFF;
    private String suffix = "", placeholder = "", erreur = "";
    private int blink;

    public HzField(int x, int y, int w, int h, int maxLength, Predicate<String> filter) {
        super(x, y, w, h, Component.empty());
        this.maxLength = maxLength;
        this.filter = filter == null ? TOUT : filter;
    }

    public HzField accent(int rgb) {
        this.accent = rgb & 0xFFFFFF;
        return this;
    }

    public HzField suffix(String s) {
        this.suffix = s;
        return this;
    }

    public HzField placeholder(String s) {
        this.placeholder = s == null ? "" : s;
        return this;
    }

    /** Transformation appliquee a chaque frappe (ex. String::toUpperCase pour le tag). */
    public HzField transform(UnaryOperator<String> t) {
        this.transform = t;
        return this;
    }

    /** « Premiere frappe remplace la valeur » (defaut vrai, utile pour les champs numeriques pre-remplis). */
    public HzField remplacerAuFocus(boolean b) {
        this.replaceOnFocus = b;
        return this;
    }

    /** Erreur en ligne affichee sous le champ ("" = aucune). */
    public void setErreur(String e) {
        this.erreur = e == null ? "" : e;
    }

    /** Faux : le filet passe au rouge mais le texte d'erreur n'est pas dessine (l'ecran l'affiche lui-meme, ex. champs cote a cote). */
    public HzField erreurVisible(boolean b) {
        this.erreurVisible = b;
        return this;
    }

    /** Apres un focus restaure par l'ecran : la prochaine frappe complete la valeur au lieu de la remplacer. */
    public void annulerRemplacement() {
        replace = false;
    }

    public String getErreur() {
        return erreur;
    }

    public void setResponder(Consumer<String> responder) {
        this.responder = responder;
    }

    public String getValue() {
        return value;
    }

    /** Change la valeur affichee sans prevenir le repondeur. */
    public void setValue(String v) {
        this.value = v == null ? "" : v;
    }

    public void tick() {
        blink++;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        replace = focused && replaceOnFocus;
        blink = 0;
    }

    private void inserer(String s) {
        String next = transform.apply((replace ? "" : value) + s);
        if (next.length() <= maxLength && filter.test(next)) {
            value = next;
            replace = false;
            responder.accept(value);
        }
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (!isFocused() || !active || c < 32) return false;
        inserer(String.valueOf(c));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!isFocused()) return false;
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                value = replace || value.isEmpty() ? "" : value.substring(0, value.offsetByCodePoints(value.length(), -1));
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
            case GLFW.GLFW_KEY_V -> {
                if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) {
                    String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                    if (clip != null) inserer(clip.replace("\n", " ").replace("\r", ""));
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
        int ligne = err ? Ui.argb(Ui.DANGER, 220) : focus ? Ui.argb(accent, 230) : Ui.argb(0xFFFFFF, isHovered ? 70 : 34);
        g.fill(x, y + h - 1, x + w, y + h, ligne);
        Font font = Minecraft.getInstance().font;
        int tx = x + 4, ty = y + (h - 8) / 2;
        // place du texte dans le champ (rien ne déborde : invite coupée avec « … », saisie trop longue montrée par sa fin)
        int place = w - 8 - (suffix.isEmpty() ? 0 : font.width(Ui.lit(suffix, Ui.SMALL)) + 4);
        if (value.isEmpty() && !placeholder.isEmpty()) {
            g.drawString(font, Ui.lit(Ui.ellipsis(font, placeholder, Ui.FONT, place), Ui.FONT), tx, ty, Ui.argb(Ui.FAINT, 255), false);
        }
        String vu = value;
        while (vu.length() > 1 && font.width(Ui.lit(vu, Ui.FONT)) > place - 2) vu = vu.substring(vu.offsetByCodePoints(0, 1));
        Component text = Ui.lit(vu, Ui.FONT);
        if (focus && replace && !value.isEmpty()) {
            g.fill(tx - 1, ty - 1, tx + font.width(text) + 1, ty + 9, Ui.argb(accent, 70));
        }
        g.drawString(font, text, tx, ty, Ui.argb(active ? Ui.TEXT : Ui.FAINT, 255), false);
        if (!suffix.isEmpty()) {
            g.drawString(font, Ui.lit(suffix, Ui.SMALL), x + w - 3 - font.width(Ui.lit(suffix, Ui.SMALL)), ty + 1, Ui.argb(Ui.MUTED, 255), false);
        }
        if (focus && !replace && (blink / 6) % 2 == 0) {
            int cx = tx + font.width(text) + 1;
            g.fill(cx, ty, cx + 1, ty + 9, Ui.argb(accent, 230));
        }
        if (err && erreurVisible) g.drawString(font, Ui.lit(erreur, Ui.SMALL), x, y + h + 2, Ui.argb(Ui.DANGER, 255), false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, Component.literal(value));
    }
}
