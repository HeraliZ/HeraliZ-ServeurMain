package fr.heralisz.matchs.duels.client;

import fr.heralisz.matchs.client.kit.SonsUi;
import fr.heralisz.matchs.client.kit.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * Carte d'option a cocher (format, mode, carte) : titre en capitales espacees, une ou deux lignes de description
 * attenuees, liseré et fond a l'accent quand elle est choisie, grisee si indisponible (raison en infobulle par l'ecran).
 * Textes prepares a la construction.
 */
class Option extends AbstractButton {
    private final String titre;
    private final List<Component> lignes;
    private final Runnable action;
    private int accent;
    private boolean choisie;

    Option(int x, int y, int w, int h, Component titre, List<Component> lignes, int accent, Runnable action) {
        super(x, y, w, h, titre);
        this.titre = titre.getString().toUpperCase(Locale.ROOT);
        this.lignes = List.copyOf(lignes);
        this.accent = accent;
        this.action = action;
    }

    Option choisie(boolean c) {
        this.choisie = c;
        return this;
    }

    boolean choisie() {
        return choisie;
    }

    Option accent(int rgb) {
        this.accent = rgb;
        return this;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    public void playDownSound(SoundManager s) {
        SonsUi.clic(0.6f);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = active && isHovered;
        Font font = Minecraft.getInstance().font;
        int fond, bord, texte;
        if (!active) {
            fond = Ui.argb(0xFFFFFF, 3);
            bord = Ui.argb(0xFFFFFF, 16);
            texte = Ui.argb(0xFFFFFF, 70);
        } else if (choisie) {
            fond = Ui.argb(accent, hover ? 52 : 38);
            bord = Ui.argb(Ui.mix(accent, 0xFFFFFF, 0.2f), 230);
            texte = 0xFFFFFFFF;
        } else {
            fond = Ui.argb(0xFFFFFF, hover ? 16 : 7);
            bord = hover ? Ui.argb(accent, 200) : Ui.argb(0xFFFFFF, 36);
            texte = Ui.argb(Ui.TEXT, 235);
        }
        Ui.soft(g, x, y, w, h, fond);
        Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, bord);
        if (lignes.isEmpty()) {
            Ui.spacedCentered(g, font, titre, Ui.BOLD, x + w / 2f, y + (h - 8) / 2f, texte, 1.1f);
            return;
        }
        Ui.spaced(g, font, titre, Ui.BOLD, x + 8, y + 7, texte, 0.9f);
        int ly = y + 20;
        int c = active ? Ui.argb(choisie ? Ui.mix(Ui.MUTED, 0xFFFFFF, 0.4f) : Ui.MUTED, 255) : Ui.argb(Ui.FAINT, 200);
        for (Component l : lignes) {
            g.drawString(font, l, x + 8, ly, c, false);
            ly += 10;
        }
        if (choisie && active) Ui.point(g, x + w - 11, y + 7, accent, 255);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
