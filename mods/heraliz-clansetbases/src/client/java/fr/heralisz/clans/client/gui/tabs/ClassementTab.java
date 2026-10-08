package fr.heralisz.clans.client.gui.tabs;

import fr.heralisz.clans.client.ClientClans;
import fr.heralisz.clans.client.gui.kit.BannerArt;
import fr.heralisz.clans.client.gui.kit.HzScroll;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.ClanLigne;
import fr.heralisz.clans.network.Packets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Classement : top 20 (rang, banniere mini, nom + tag, niveau, membres, kills, zones) ; mon clan surligne. La liste est
 * demandee au serveur a l'ouverture de l'onglet (SimpleC2S CLASSEMENT) et arrive par ClassementS2C.
 */
public final class ClassementTab extends Tab {
    private static final int LIGNE = 16;
    private static final int X_RANG = 0, X_BAN = 24, X_NOM = 40, R_NIVEAU = 330, R_MEMBRES = 390, R_KILLS = 450, R_ZONES = 510;
    private static long derniereDemande;

    @Override
    protected void construire() {
        if (!ClientClans.estFaux() && Minecraft.getInstance().getConnection() != null && System.currentTimeMillis() - derniereDemande > 2000) {
            derniereDemande = System.currentTimeMillis();
            envoyer(new Packets.SimpleC2S(Packets.SimpleC2S.Genre.CLASSEMENT));
        }
        Liste liste = new Liste(x, y + 14, w, h - 14);
        liste.accent(accent);
        liste.setContentHeight(ClientClans.classement().size() * LIGNE);
        ecran.ajouter(liste);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int c = Ui.argb(Ui.MUTED, 255);
        float sp = 0.8f;
        Ui.spaced(g, font, tr("gui.classement.rang"), Ui.SMALL, x + X_RANG, y, c, sp);
        Ui.spaced(g, font, tr("gui.titre"), Ui.SMALL, x + X_NOM, y, c, sp);
        Ui.spacedRight(g, font, tr("gui.classement.niveau"), Ui.SMALL, x + R_NIVEAU, y, c, sp);
        Ui.spacedRight(g, font, tr("gui.classement.membres"), Ui.SMALL, x + R_MEMBRES, y, c, sp);
        Ui.spacedRight(g, font, tr("gui.classement.kills"), Ui.SMALL, x + R_KILLS, y, c, sp);
        Ui.spacedRight(g, font, tr("gui.classement.territoires"), Ui.SMALL, x + R_ZONES, y, c, sp);
        Ui.hairline(g, x, x + w, y + 10, 0xFFFFFF, 40, false, true);
        if (ClientClans.classement().isEmpty()) {
            String cle = ClientClans.classementRecu() ? "gui.classement.vide" : "gui.classement.chargement";
            Ui.centered(g, font, Ui.tr(Ui.SMALL, cle), x + w / 2f, y + h / 2f, Ui.argb(Ui.FAINT, 255));
        }
    }

    private final class Liste extends HzScroll {
        Liste(int x, int y, int w, int h) {
            super(x, y, w, h);
        }

        @Override
        protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            List<ClanLigne> l = ClientClans.classement();
            int premiere = Math.max(0, getScroll() / LIGNE), derniere = Math.min(l.size(), (getScroll() + getHeight()) / LIGNE + 1);
            int wl = getWidth() - 2;
            for (int i = premiere; i < derniere; i++) {
                ClanLigne c = l.get(i);
                int ly = getY() + i * LIGNE, ty = ly + 4;
                boolean moi = c.id().equals(s.id());
                boolean hover = dans(mouseX, mouseY, getX(), ly, wl, LIGNE);
                if (moi) {
                    Ui.soft(g, getX(), ly, wl, LIGNE, Ui.argb(accent, 28));
                    g.fill(getX(), ly + 1, getX() + 2, ly + LIGNE - 1, Ui.argb(accent, 220));
                } else if (hover) {
                    Ui.soft(g, getX(), ly, wl, LIGNE, Ui.argb(0xFFFFFF, 10));
                }
                int rangC = c.rang() == 1 ? Ui.OR : c.rang() <= 3 ? Ui.TEXT : Ui.MUTED;
                Ui.right(g, font, Ui.lit(String.valueOf(c.rang()), Ui.BOLD), getX() + X_RANG + 16, ty, Ui.argb(rangC, 255));
                BannerArt.draw(g, c.banniere(), getX() + X_BAN, ly + 2, 9, 12);
                g.drawString(font, Ui.lit(Ui.ellipsis(font, c.nom(), Ui.BOLD, 160), Ui.BOLD), getX() + X_NOM, ty, Ui.argb(Ui.TEXT, 255), false);
                int nw = font.width(Ui.lit(Ui.ellipsis(font, c.nom(), Ui.BOLD, 160), Ui.BOLD));
                g.drawString(font, Ui.lit("[" + c.tag() + "]", Ui.FONT), getX() + X_NOM + nw + 6, ty, Ui.argb(Ui.MUTED, 255), false);
                int num = Ui.argb(Ui.TEXT, 220);
                Ui.right(g, font, Ui.lit(String.valueOf(c.niveau()), Ui.BOLD), getX() + R_NIVEAU, ty, num);
                Ui.right(g, font, Ui.lit(String.valueOf(c.membres()), Ui.FONT), getX() + R_MEMBRES, ty, num);
                Ui.right(g, font, Ui.lit(Ui.nombre(c.kills()), Ui.FONT), getX() + R_KILLS, ty, num);
                Ui.right(g, font, Ui.lit(String.valueOf(c.territoires()), Ui.FONT), getX() + R_ZONES, ty, num);
                if (i < l.size() - 1) Ui.hairline(g, getX(), getX() + wl, ly + LIGNE - 1, 0xFFFFFF, 14, false, true);
            }
        }
    }
}
