package fr.heralisz.clans.client.gui.tabs;

import fr.heralisz.clans.client.gui.kit.Icons;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.QueteEtat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Quetes : « QUOTIDIENNES · reinitialisation dans 7 h 12 » (3 cartes) puis « HEBDOMADAIRES · 3 j 4 h » (2 cartes).
 * Carte = titre, barre 3 px accent, « 37 / 100 », recompense attenuee ; terminee = coche + carte eteinte (50 %).
 */
public final class QuetesTab extends Tab {
    private static final int CARTE_H = 62;

    @Override
    protected void construire() {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cw = (w - 16) / 3;
        int y1 = y;
        entete(g, tr("quete.quotidiennes"), s.resetJourMs(), y1);
        cartes(g, s.quetesJour(), y1 + 14, cw, mouseX, mouseY);
        int y2 = y1 + 14 + CARTE_H + 18;
        entete(g, tr("quete.hebdomadaires"), s.resetSemaineMs(), y2);
        cartes(g, s.quetesSemaine(), y2 + 14, cw, mouseX, mouseY);
        int y3 = y2 + 14 + CARTE_H + 14;
        if (y3 + 10 <= y + h) {
            g.drawString(font, Ui.tr(Ui.SMALL, "gui.quetes.auto"), x, y3, Ui.argb(Ui.FAINT, 255), false);
        }
    }

    private void entete(GuiGraphics g, String titre, long resetMs, int yy) {
        Ui.spaced(g, font, titre, Ui.SMALL, x, yy, Ui.argb(Ui.MUTED, 255), 1.0f);
        int tw = (int) Ui.spacedWidth(font, titre.toUpperCase(java.util.Locale.ROOT), Ui.SMALL, 1.0f);
        String reset = "·  " + tr("quete.reset_dans", Ui.duree(resetMs));
        g.drawString(font, Ui.lit(reset, Ui.SMALL), x + tw + 8, yy, Ui.argb(Ui.FAINT, 255), false);
        int rw = font.width(Ui.lit(reset, Ui.SMALL));
        Ui.hairline(g, x + tw + 8 + rw + 8, x + w, yy + 4, 0xFFFFFF, 40, false, true);
    }

    private void cartes(GuiGraphics g, List<QueteEtat> quetes, int yy, int cw, int mouseX, int mouseY) {
        if (quetes.isEmpty()) {
            g.drawString(font, Ui.tr(Ui.SMALL, "gui.quetes.aucune"), x, yy + 4, Ui.argb(Ui.FAINT, 255), false);
            return;
        }
        for (int i = 0; i < Math.min(3, quetes.size()); i++) {
            carte(g, quetes.get(i), x + i * (cw + 8), yy, cw, mouseX, mouseY);
        }
    }

    private void carte(GuiGraphics g, QueteEtat q, int cx, int cy, int cw, int mouseX, int mouseY) {
        boolean fini = q.terminee;
        float a = fini ? 0.5f : 1f;
        boolean hover = !fini && dans(mouseX, mouseY, cx, cy, cw, CARTE_H);
        Ui.soft(g, cx, cy, cw, CARTE_H, Ui.argb(0xFFFFFF, (int) ((hover ? 12 : 8) * a)));
        Ui.softOutline(g, cx + 1, cy + 1, cw - 2, CARTE_H - 2, Ui.argb(0xFFFFFF, (int) (16 * a)));
        if (fini) g.fill(cx, cy, cx + 2, cy + CARTE_H, Ui.argb(Ui.OK, 150));
        String titre = q.titre(Ui.french());
        g.drawString(font, Ui.lit(Ui.ellipsis(font, titre, Ui.BOLD, cw - 16 - (fini ? 14 : 0)), Ui.BOLD), cx + 8, cy + 8, Ui.argb(Ui.TEXT, (int) (255 * a)), false);
        if (fini) Icons.draw(g, Icons.COCHE, cx + cw - 20, cy + 6, 12, Ui.OK, 255);
        Ui.spaced(g, font, tr(q.type.libelle()), Ui.SMALL, cx + 8, cy + 20, Ui.argb(Ui.FAINT, (int) (255 * a)), 0.6f);
        Ui.bar(g, cx + 8, cy + 31, cw - 16, 3, fini ? 1 : q.ratio(), fini ? Ui.OK : accent);
        String prog = Ui.nombre(Math.min(q.progression, q.objectif)) + " / " + Ui.nombre(q.objectif);
        g.drawString(font, Ui.lit(prog, Ui.BOLD), cx + 8, cy + 39, Ui.argb(Ui.TEXT, (int) (255 * a)), false);
        if (fini) {
            Ui.right(g, font, Ui.tr(Ui.SMALL, "quete.terminee"), cx + cw - 8, cy + 40, Ui.argb(Ui.OK, 200));
        } else {
            Ui.right(g, font, Ui.lit(Ui.pct(q.ratio() * 100), Ui.SMALL), cx + cw - 8, cy + 40, Ui.argb(Ui.MUTED, 255));
        }
        Component rec = Component.translatable("quete.recompense", Ui.nombre(q.xp), Ui.jetons(q.argent, Ui.SMALL));
        g.drawString(font, Ui.in(rec, Ui.SMALL), cx + 8, cy + 50, Ui.argb(Ui.MUTED, (int) (255 * a)), false);
    }
}
