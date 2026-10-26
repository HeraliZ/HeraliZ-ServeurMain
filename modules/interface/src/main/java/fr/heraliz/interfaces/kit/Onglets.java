package fr.heraliz.interfaces.kit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Barre d'onglets au style des clans : libelles en petites capitales espacees (icone optionnelle devant), l'onglet
 * actif en blanc souligne d'un filet d'accent qui glisse d'un onglet a l'autre, les autres attenues (survol : un peu
 * plus clairs, filet blanc discret). Clic ou fleches gauche / droite au clavier.
 */
public class Onglets extends AbstractWidget {
    private final List<String> libelles = new ArrayList<>();
    private final List<Integer> icones = new ArrayList<>();
    private final int[] xs, ws;
    private final IntConsumer choix;
    private int actif;
    private final int accent;
    private float filetX = -1, filetW;
    private final Anim.Horloge horloge = new Anim.Horloge();
    private static final int ECART = 18, ICONE = 8;

    public Onglets(int x, int y, List<Component> libelles, List<Integer> icones, int actif, int accent, IntConsumer choix) {
        super(x, y, 0, 16, Component.empty());
        Font font = Minecraft.getInstance().font;
        this.xs = new int[libelles.size()];
        this.ws = new int[libelles.size()];
        int cx = x;
        for (int i = 0; i < libelles.size(); i++) {
            String l = libelles.get(i).getString();
            this.libelles.add(l);
            int ic = icones != null && i < icones.size() ? icones.get(i) : -1;
            this.icones.add(ic);
            int w = (int) Math.ceil(Ui.spacedWidth(font, l, Ui.SMALL, 1.0f)) + (ic >= 0 ? ICONE + 5 : 0);
            xs[i] = cx;
            ws[i] = w;
            cx += w + ECART;
        }
        setWidth(Math.max(0, cx - ECART - x));
        this.actif = actif;
        this.accent = accent & 0xFFFFFF;
        this.choix = choix;
    }

    public int actif() {
        return actif;
    }

    private int sous(double mx, double my) {
        if (my < getY() - 3 || my >= getY() + getHeight()) return -1;
        for (int i = 0; i < xs.length; i++) if (mx >= xs[i] - 6 && mx < xs[i] + ws[i] + 6) return i;
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        int i = sous(mx, my);
        if (i < 0) return false;
        if (i != actif) {
            Sons.jouer(Sons.CLIC, 0.5f);
            actif = i;
            choix.accept(i);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        int d = key == 263 ? -1 : key == 262 ? 1 : 0; // fleches gauche / droite
        if (d == 0 || xs.length == 0) return false;
        actif = (actif + d + xs.length) % xs.length;
        Sons.jouer(Sons.CLIC, 0.5f);
        choix.accept(actif);
        return true;
    }

    @Override
    public boolean isMouseOver(double mx, double my) {
        return sous(mx, my) >= 0;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int y = getY();
        int survole = sous(mouseX, mouseY);
        for (int i = 0; i < xs.length; i++) {
            boolean estActif = i == actif;
            boolean hover = !estActif && i == survole;
            int c = Ui.argb(estActif ? Ui.TEXT : hover ? Ui.mix(Ui.MUTED, Ui.TEXT, 0.55f) : Ui.MUTED, 255);
            int tx = xs[i];
            int ic = icones.get(i);
            if (ic >= 0) {
                int ci = estActif ? Ui.mix(accent, 0xFFFFFF, 0.3f) : c & 0xFFFFFF;
                Icons.draw(g, ic, tx, y - 1, ICONE, ci, 255);
                tx += ICONE + 5;
            }
            Ui.spaced(g, font, libelles.get(i), Ui.SMALL, tx, y, c, 1.0f);
            if (hover) g.fill(xs[i], y + 11, xs[i] + ws[i], y + 12, Ui.argb(0xFFFFFF, 40));
        }
        // filet d'accent qui glisse vers l'onglet actif
        if (actif >= 0 && actif < xs.length) {
            float dt = horloge.dt();
            if (filetX < 0) {
                filetX = xs[actif];
                filetW = ws[actif];
            }
            filetX = Anim.vers(filetX, xs[actif], dt, 18f);
            filetW = Anim.vers(filetW, ws[actif], dt, 18f);
            g.fill(Math.round(filetX), y + 11, Math.round(filetX + filetW), y + 12, Ui.argb(accent, 235));
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        if (actif >= 0 && actif < libelles.size()) out.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, libelles.get(actif));
    }
}
