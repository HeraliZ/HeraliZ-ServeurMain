package fr.heralisz.clans.client.gui.tabs;

import fr.heralisz.clans.ClansConfig;
import fr.heralisz.clans.client.gui.kit.Icons;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.Avantages;
import fr.heralisz.clans.model.Niveaux;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Niveau du clan (maquette validee du 21/10) : a gauche « Prochain niveau » (XP qui manque, ce qu'il debloque) et
 * « Gagner de l'XP » ; a droite les paliers (membres, zones, avantage), atteints coches, niveau actuel surligne.
 * Avantages : {@link Avantages} (memes chiffres que le serveur).
 */
public final class NiveauTab extends Tab {
    private static final int GAUCHE = 170, LIGNE = 14;

    @Override
    protected void construire() {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int n = s.niveau();
        int hCarte = 112;
        carte(g, x, y, GAUCHE, hCarte);
        Ui.spaced(g, font, tr("gui.niveau.prochain"), Ui.SMALL, x + 10, y + 9, Ui.argb(Ui.MUTED, 255), 1.0f);
        if (n >= Niveaux.MAX) {
            Ui.gros(g, font, Ui.tr(Ui.BOLD, "gui.niveau_max"), x + 10, y + 22, 1.5f, Ui.argb(Ui.OR, 255));
        } else {
            Ui.gros(g, font, Ui.tr(Ui.BOLD, "gui.niveau.n", n + 1), x + 10, y + 22, 1.5f, Ui.argb(Ui.OR, 255));
            long reste = Math.max(0, s.xpSuivant() - s.xp());
            g.drawString(font, Ui.tr(Ui.SMALL, "gui.niveau.reste", Ui.nombre(reste)), x + 10, y + 40, Ui.argb(Ui.MUTED, 255), false);
            int ly = y + 54;
            for (String l : gains(n + 1)) {
                Ui.point(g, x + 12, ly + 3, Ui.OK, 255);
                g.drawString(font, Ui.lit(Ui.ellipsis(font, l, Ui.BOLD, GAUCHE - 30), Ui.BOLD), x + 20, ly, Ui.argb(Ui.TEXT, 255), false);
                ly += 15;
                if (ly > y + hCarte - 10) break;
            }
        }
        int y2 = y + hCarte + 6, h2 = h - hCarte - 6;
        carte(g, x, y2, GAUCHE, h2);
        Ui.spaced(g, font, tr("gui.niveau.gagner"), Ui.SMALL, x + 10, y2 + 9, Ui.argb(Ui.MUTED, 255), 1.0f);
        String[][] xp = {
                {"gui.niveau.xp.capture", Ui.nombre(ClansConfig.XP_CAPTURE.get())},
                {"gui.niveau.xp.tenir", Ui.nombre(ClansConfig.XP_HEURE_VALEUR.get())},
                {"gui.niveau.xp.ravitaillement", Ui.nombre(ClansConfig.XP_RAVITAILLEMENT.get())},
                {"gui.niveau.xp.quetes", null},
                {"gui.niveau.xp.boss", Ui.nombre(ClansConfig.XP_BOSS.get())},
                {"gui.niveau.xp.joueur", Ui.nombre(ClansConfig.XP_KILL_JOUEUR.get())},
                {"gui.niveau.xp.zombie", Ui.nombre(ClansConfig.XP_KILL_ZOMBIE.get())}};
        int ly = y2 + 22;
        for (String[] l : xp) {
            if (ly > y2 + h2 - 10) break;
            g.drawString(font, Ui.lit(Ui.ellipsis(font, tr(l[0]), Ui.SMALL, GAUCHE - 60), Ui.SMALL), x + 10, ly, Ui.argb(Ui.TEXT, 230), false);
            String v = l[1] == null ? tr("gui.niveau.xp.variable") : tr("gui.niveau.xp.valeur", l[1]);
            Ui.right(g, font, Ui.lit(v, Ui.BOLD), x + GAUCHE - 10, ly, Ui.argb(Ui.OR, 255));
            ly += LIGNE;
        }
        // paliers
        int tx = x + GAUCHE + 8, tw = w - GAUCHE - 8;
        carte(g, tx, y, tw, h);
        Ui.spaced(g, font, tr("gui.niveau.paliers"), Ui.SMALL, tx + 10, y + 9, Ui.argb(Ui.MUTED, 255), 1.0f);
        int cN = tx + 12, cM = tx + 40, cZ = tx + 80, cA = tx + 112;
        int hy = y + 22;
        Ui.spaced(g, font, tr("gui.niveau.col.niveau"), Ui.SMALL, cN, hy, Ui.argb(Ui.FAINT, 255), 0.6f);
        Ui.spaced(g, font, tr("gui.niveau.col.membres"), Ui.SMALL, cM, hy, Ui.argb(Ui.FAINT, 255), 0.6f);
        Ui.spaced(g, font, tr("gui.niveau.col.zones"), Ui.SMALL, cZ, hy, Ui.argb(Ui.FAINT, 255), 0.6f);
        Ui.spaced(g, font, tr("gui.niveau.col.avantage"), Ui.SMALL, cA, hy, Ui.argb(Ui.FAINT, 255), 0.6f);
        int ry = hy + 12;
        int actuel = 1;
        for (int p : Avantages.PALIERS) if (p <= n) actuel = p;
        for (int p : Avantages.PALIERS) {
            if (ry > y + h - 22) break;
            boolean cur = p == actuel, fait = p < actuel;
            int c = cur ? Ui.OR : fait ? Ui.TEXT : Ui.FAINT;
            if (cur) {
                Ui.soft(g, tx + 5, ry - 3, tw - 10, LIGNE - 1, Ui.argb(Ui.OR, 34));
                Ui.softOutline(g, tx + 5, ry - 3, tw - 10, LIGNE - 1, Ui.argb(Ui.OR, 110));
            }
            g.drawString(font, Ui.lit(String.valueOf(p), Ui.BOLD), cN, ry, Ui.argb(c, 255), false);
            g.drawString(font, Ui.lit(String.valueOf(Niveaux.membresMax(p)), Ui.FONT), cM, ry, Ui.argb(c, 255), false);
            g.drawString(font, Ui.lit(String.valueOf(Niveaux.territoiresMax(p)), Ui.FONT), cZ, ry, Ui.argb(c, 255), false);
            String a = Ui.ellipsis(font, tr("gui.niveau.avantage." + p), Ui.FONT, tw - (cA - tx) - 26);
            g.drawString(font, Ui.lit(a, Ui.FONT), cA, ry, Ui.argb(cur ? Ui.TEXT : c, 255), false);
            if (fait || cur) Icons.draw(g, Icons.COCHE, tx + tw - 18, ry - 1, 9, Ui.OK, 255);
            ry += LIGNE;
        }
        g.drawString(font, Ui.tr(Ui.SMALL, "gui.niveau.pied"), tx + 10, y + h - 12, Ui.argb(Ui.FAINT, 255), false);
    }

    private void carte(GuiGraphics g, int cx, int cy, int cw, int ch) {
        Ui.soft(g, cx, cy, cw, ch, Ui.argb(0xFFFFFF, 8));
        Ui.softOutline(g, cx + 1, cy + 1, cw - 2, ch - 2, Ui.argb(0xFFFFFF, 16));
    }

    /** Ce que le niveau n apporte par rapport au precedent. */
    private List<String> gains(int n) {
        List<String> l = new ArrayList<>();
        int m = Niveaux.membresMax(n) - Niveaux.membresMax(n - 1);
        if (m > 0) l.add(tr("gui.niveau.gain.membre", Niveaux.membresMax(n)));
        if (Niveaux.territoiresMax(n) > Niveaux.territoiresMax(n - 1)) l.add(tr("gui.niveau.gain.territoire", Niveaux.territoiresMax(n)));
        for (int p : Avantages.PALIERS) {
            if (p == n && p != 3 && p != 1) l.add(tr("gui.niveau.court." + p));
        }
        if (l.isEmpty()) l.add(tr("gui.niveau.gain.rien"));
        return l;
    }
}
