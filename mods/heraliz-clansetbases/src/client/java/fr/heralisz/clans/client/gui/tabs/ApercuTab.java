package fr.heralisz.clans.client.gui.tabs;

import fr.heralisz.clans.client.ClientClans;
import fr.heralisz.clans.client.gui.kit.BannerArt;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.Evenement;
import fr.heralisz.clans.model.Stats;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Apercu : grande banniere + description a gauche ; six tuiles (membres, kills, K/D, territoires, banque, anciennete)
 * et les quatre dernieres lignes d'activite a droite.
 */
public final class ApercuTab extends Tab {
    @Override
    protected void construire() {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // banniere et description
        BannerArt.draw(g, s.banniere(), x, y, 96, 128);
        int dy = y + 136;
        if (!s.description().isBlank()) {
            for (String ligne : s.description().split("\n")) {
                for (FormattedCharSequence seq : font.split(Ui.lit(ligne, Ui.FONT), 108)) {
                    g.drawString(font, seq, x, dy, Ui.argb(Ui.MUTED, 255), false);
                    dy += 10;
                }
            }
        } else {
            g.drawString(font, Ui.tr(Ui.SMALL, "gui.apercu.sans_description"), x, dy, Ui.argb(Ui.FAINT, 255), false);
        }
        // tuiles 3 x 2
        int tx = x + 124, tw = (w - 124 - 16) / 3, th = 44;
        Stats t = s.totaux();
        long jours = (ClientClans.maintenantServeur() - s.creeLe()) / 86400_000L;
        String anciennete = jours >= 1 ? jours + tr("gui.duree.j") : Ui.duree(ClientClans.maintenantServeur() - s.creeLe());
        Ui.tile(g, font, tx, y, tw, th, s.membres().size() + " / " + s.membresMax(), tr("gui.tuile.membres"));
        Ui.tile(g, font, tx + tw + 8, y, tw, th, Ui.nombre(t.kills), tr("gui.tuile.kills"));
        Ui.tile(g, font, tx + 2 * (tw + 8), y, tw, th, Ui.ratio(t.kd()), tr("gui.tuile.kd"));
        Ui.tile(g, font, tx, y + th + 8, tw, th, s.territoires().size() + " / " + s.territoiresMax(), tr("gui.tuile.territoires"));
        Ui.tile(g, font, tx + tw + 8, y + th + 8, tw, th, Ui.jetons(s.banque(), Ui.BOLD), tr("gui.tuile.banque"));
        Ui.tile(g, font, tx + 2 * (tw + 8), y + th + 8, tw, th, anciennete, tr("gui.tuile.anciennete"));
        // activite
        int ay = y + 2 * th + 8 + 16;
        Ui.section(g, font, tr("gui.activite"), tx, ay, w - 124);
        List<Evenement> j = s.journal();
        if (j.isEmpty()) {
            g.drawString(font, Ui.tr(Ui.SMALL, "gui.apercu.sans_activite"), tx, ay + 14, Ui.argb(Ui.FAINT, 255), false);
        }
        int ly = ay + 14;
        for (int i = 0; i < Math.min(4, j.size()) && ly + 10 <= y + h; i++) {
            Evenement e = j.get(i);
            String quand = Ui.ilYA(Math.max(0, ClientClans.maintenantServeur() - e.quand()));
            g.drawString(font, Ui.lit(quand, Ui.SMALL), tx, ly + 1, Ui.argb(Ui.FAINT, 255), false);
            Component texte = e.texte(); // PERSO (autres mods) et PERMISSION ont leur propre mise en forme
            String tx2 = Ui.ellipsis(font, texte.getString(), Ui.FONT, w - 124 - 70);
            g.drawString(font, Ui.lit("·", Ui.FONT), tx + 60, ly, Ui.argb(Ui.FAINT, 255), false);
            g.drawString(font, Ui.lit(tx2, Ui.FONT), tx + 68, ly, Ui.argb(Ui.TEXT, 230), false);
            ly += 12;
        }
        // territoires tenus, en bas a droite s'il reste de la place
        if (!s.territoires().isEmpty() && ly + 24 <= y + h) {
            int zy = y + h - 22;
            Ui.section(g, font, tr("gui.tuile.territoires"), tx, zy, w - 124);
            int zx = tx;
            for (var z : s.territoires()) {
                zx += Ui.chip(g, font, z.nom(), zx, zy + 10, accent) + 6;
            }
        }
    }
}
