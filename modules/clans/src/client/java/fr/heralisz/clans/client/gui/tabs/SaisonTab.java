package fr.heralisz.clans.client.gui.tabs;

import fr.heralisz.clans.client.ClientClans;
import fr.heralisz.clans.client.gui.kit.BannerArt;
import fr.heralisz.clans.client.gui.kit.HzScroll;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.LigneBareme;
import fr.heralisz.clans.model.SaisonLigne;
import fr.heralisz.clans.network.Packets;
import fr.heralisz.clans.registry.Sons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Onglet Saison (25/10, maquette validee : maquettes/saison/, modules/clans/outils/apercu_saison.py) : a gauche le
 * classement de la saison (place au general, PVE / PVP / events avec la place dans chacune, somme des places) et l'etat
 * du clan du joueur (place, points, coeur, saison) ; a droite « Gagner des points » : le bareme reel envoye par le
 * serveur, par categorie (puces PVE / PVP / EVENEMENTS). Les donnees sont demandees a l'ouverture de l'onglet
 * (SimpleC2S CLASSEMENT) et arrivent par {@link Packets.SaisonS2C}.
 */
public final class SaisonTab extends Tab {
    private static final int GAUCHE = 300, LIGNE = 17, ENTREE = 21, BOITE = 34;
    /** Colonnes (bord droit, depuis le bord gauche de l'onglet) : PVE, PVP, EVENTS, total des places. */
    private static final int R_PVE = 196, R_PVP = 236, R_EV = 276;
    private static final int C_PVE = 0x5FD08A, C_PVP = 0xFF6B6B, C_EV = 0xB57BFF;
    private static final int[] COULEURS = {C_PVE, C_PVP, C_EV};
    private static final String[] CATEGORIES = {"gui.saison.cat.pve", "gui.saison.cat.pvp", "gui.saison.cat.events"};
    /** Categorie affichee a droite (gardee entre deux reconstructions de l'ecran). */
    private static int categorie;
    private static long derniereDemande;

    private int rx, rw;

    @Override
    protected void construire() {
        if (!ClientClans.estFaux() && Minecraft.getInstance().getConnection() != null && System.currentTimeMillis() - derniereDemande > 2000) {
            derniereDemande = System.currentTimeMillis();
            envoyer(new Packets.SimpleC2S(Packets.SimpleC2S.Genre.CLASSEMENT));
        }
        rx = x + GAUCHE + 14;
        rw = w - GAUCHE - 14;
        Packets.SaisonS2C sa = ClientClans.saison();
        Classement cl = new Classement(x, y + 26, GAUCHE, h - 26 - BOITE - 4);
        cl.setContentHeight(sa == null ? 0 : sa.top().size() * LIGNE);
        ecran.ajouter(cl);
        Bareme ba = new Bareme(rx + 10, y + 38, rw - 20, h - 38 - 24);
        ba.setContentHeight(lignes(sa).size() * ENTREE);
        ecran.ajouter(ba);
    }

    private static List<LigneBareme> lignes(Packets.SaisonS2C sa) {
        List<LigneBareme> l = new ArrayList<>();
        if (sa != null) for (LigneBareme b : sa.bareme()) if (b.categorie() == categorie) l.add(b);
        return l;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Packets.SaisonS2C sa = ClientClans.saison();
        // ---- titre
        String titre = sa == null || sa.numero() <= 0 ? tr("gui.saison.titre_seul") : tr("gui.saison.titre", sa.numero());
        Ui.spaced(g, font, titre, Ui.BOLD, x, y, Ui.argb(Ui.TEXT, 255), 1.2f);
        int tw = (int) Ui.spacedWidth(font, titre.toUpperCase(Locale.ROOT), Ui.BOLD, 1.2f);
        String sous = sa == null ? tr("gui.saison.chargement")
                : !sa.active() ? tr("gui.saison.arretee")
                : sa.finMs() > 0 ? tr("gui.saison.sous_titre", Ui.duree(Math.max(0, sa.finMs() - ClientClans.maintenantServeur())))
                : tr("gui.saison.sous_titre_sans_fin");
        g.drawString(font, Ui.lit(Ui.ellipsis(font, sous, Ui.SMALL, GAUCHE - tw - 12), Ui.SMALL), x + tw + 12, y + 1, Ui.argb(Ui.FAINT, 255), false);
        // ---- en-tetes du classement
        int hy = y + 14, c = Ui.argb(Ui.MUTED, 255);
        Ui.spaced(g, font, "#", Ui.SMALL, x + 2, hy, c, 0.8f);
        Ui.spaced(g, font, tr("gui.saison.col.clan"), Ui.SMALL, x + 34, hy, c, 0.8f);
        Ui.spacedRight(g, font, tr("gui.saison.col.pve"), Ui.SMALL, x + R_PVE, hy, Ui.argb(C_PVE, 255), 0.8f);
        Ui.spacedRight(g, font, tr("gui.saison.col.pvp"), Ui.SMALL, x + R_PVP, hy, Ui.argb(C_PVP, 255), 0.8f);
        Ui.spacedRight(g, font, tr("gui.saison.col.events"), Ui.SMALL, x + R_EV, hy, Ui.argb(C_EV, 255), 0.8f);
        Ui.spacedRight(g, font, tr("gui.saison.col.general"), Ui.SMALL, x + GAUCHE, hy, c, 0.8f);
        Ui.hairline(g, x, x + GAUCHE, hy + 10, 0xFFFFFF, 40, false, true);
        if (sa != null && sa.top().isEmpty()) {
            Ui.centered(g, font, Ui.tr(Ui.SMALL, "gui.saison.vide"), x + GAUCHE / 2f, y + 26 + (h - 26 - BOITE) / 2f - 4, Ui.argb(Ui.FAINT, 255));
        }
        boite(g, sa);
        droite(g, sa, mouseX, mouseY);
    }

    /** Bas gauche : place et points du clan, coeur, saison. */
    private void boite(GuiGraphics g, Packets.SaisonS2C sa) {
        int by = y + h - BOITE;
        Ui.soft(g, x, by, GAUCHE, BOITE, Ui.argb(0xFFFFFF, 8));
        Ui.softOutline(g, x + 1, by + 1, GAUCHE - 2, BOITE - 2, Ui.argb(0xFFFFFF, 18));
        Ui.spaced(g, font, tr("gui.saison.votre_clan"), Ui.SMALL, x + 8, by + 6, Ui.argb(Ui.MUTED, 255), 0.8f);
        String etat;
        if (sa == null) etat = tr("gui.saison.chargement");
        else if (!sa.active()) etat = tr("gui.saison.arretee");
        else if (sa.mesRangs()[3] <= 0) etat = tr("gui.saison.non_classe");
        else etat = tr("gui.saison.etat", sa.mesRangs()[3], Ui.nombre(sa.mesPoints()[0] + sa.mesPoints()[1] + sa.mesPoints()[2]));
        g.drawString(font, Ui.lit(Ui.ellipsis(font, etat, Ui.BOLD, GAUCHE - 116), Ui.BOLD), x + 8, by + 17, Ui.argb(Ui.TEXT, 255), false);
        if (sa == null) return;
        int ix = x + GAUCHE - 100;
        boolean coeur = sa.coeur();
        Ui.point(g, ix, by + 10, coeur ? Ui.OK : Ui.DANGER, 255);
        String c1 = tr(coeur ? "gui.saison.coeur_ok" : "gui.saison.coeur_non");
        g.drawString(font, Ui.lit(Ui.ellipsis(font, c1, Ui.SMALL, 90), Ui.SMALL), ix + 8, by + 8, Ui.argb(coeur ? Ui.OK : Ui.DANGER, 255), false);
        String c2 = tr(sa.active() ? "gui.saison.en_cours" : "gui.saison.pas_en_cours");
        g.drawString(font, Ui.lit(Ui.ellipsis(font, c2, Ui.SMALL, 90), Ui.SMALL), ix + 8, by + 19, Ui.argb(Ui.MUTED, 255), false);
    }

    /** A droite : « Gagner des points », puces des categories, note du coeur. */
    private void droite(GuiGraphics g, Packets.SaisonS2C sa, int mouseX, int mouseY) {
        int top = y - 2, hh = h + 2;
        Ui.soft(g, rx, top, rw, hh, Ui.argb(0x0D0F14, 120));
        Ui.softOutline(g, rx + 1, top + 1, rw - 2, hh - 2, Ui.argb(0xFFFFFF, 26));
        Ui.spaced(g, font, Ui.spacedEllipsis(font, tr("gui.saison.gagner"), Ui.BOLD, 1.2f, rw - 20), Ui.BOLD, rx + 10, y + 5, Ui.argb(Ui.TEXT, 255), 1.2f);
        int px = rx + 10;
        for (int i = 0; i < 3; i++) {
            String nom = tr(CATEGORIES[i]);
            int pw = puce(nom);
            boolean actif = i == categorie, survol = !actif && dans(mouseX, mouseY, px, y + 18, pw, 12);
            Ui.soft(g, px, y + 18, pw, 12, Ui.argb(COULEURS[i], actif ? 60 : survol ? 30 : 12));
            Ui.softOutline(g, px, y + 18, pw, 12, Ui.argb(COULEURS[i], actif ? 200 : 50));
            Ui.spacedCentered(g, font, nom, Ui.SMALL, px + pw / 2f, y + 20, Ui.argb(actif ? Ui.TEXT : Ui.MUTED, 255), 0.8f);
            px += pw + 5;
        }
        Ui.hairline(g, rx + 10, rx + rw - 10, y + 34, 0xFFFFFF, 22, false, false);
        if (sa != null && lignes(sa).isEmpty()) {
            Ui.centered(g, font, Ui.tr(Ui.SMALL, "gui.saison.chargement"), rx + rw / 2f, y + 60, Ui.argb(Ui.FAINT, 255));
        }
        String note = tr("gui.saison.note");
        g.drawString(font, Ui.lit(Ui.ellipsis(font, note, Ui.SMALL, rw - 20), Ui.SMALL), rx + 10, y + h - 14, Ui.argb(Ui.AMBRE, 255), false);
    }

    private int puce(String nom) {
        return (int) Math.ceil(Ui.spacedWidth(font, nom.toUpperCase(Locale.ROOT), Ui.SMALL, 0.8f)) + 12;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int px = rx + 10;
        for (int i = 0; i < 3; i++) {
            int pw = puce(tr(CATEGORIES[i]));
            if (i != categorie && dans(mx, my, px, y + 18, pw, 12)) {
                categorie = i;
                Ui.son(Sons.UI_CLIC, 0.5f);
                ecran.reconstruireOnglet();
                return true;
            }
            px += pw + 5;
        }
        return false;
    }

    /** Nombre mis en forme : entier avec separateur de milliers, sinon deux decimales au plus (virgule hors anglais). */
    private static String nombre(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-6) return Ui.nombre(Math.round(v));
        String s = String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        return code != null && code.startsWith("en") ? s : s.replace('.', ',');
    }

    // ------------------------------------------------------------------ listes
    private final class Classement extends HzScroll {
        Classement(int x, int y, int w, int h) {
            super(x, y, w, h);
        }

        @Override
        protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Packets.SaisonS2C sa = ClientClans.saison();
            if (sa == null) return;
            List<SaisonLigne> l = sa.top();
            int premiere = Math.max(0, getScroll() / LIGNE), derniere = Math.min(l.size(), (getScroll() + getHeight()) / LIGNE + 1);
            int wl = getWidth() - 2;
            for (int i = premiere; i < derniere; i++) {
                SaisonLigne c = l.get(i);
                int ly = getY() + i * LIGNE, ty = ly + 5;
                boolean moi = c.id().equals(s.id());
                if (moi) {
                    Ui.soft(g, getX(), ly, wl, LIGNE, Ui.argb(accent, 28));
                    g.fill(getX(), ly + 1, getX() + 2, ly + LIGNE - 1, Ui.argb(accent, 220));
                } else if (dans(mouseX, mouseY, getX(), ly, wl, LIGNE)) {
                    Ui.soft(g, getX(), ly, wl, LIGNE, Ui.argb(0xFFFFFF, 10));
                }
                int rangC = c.rang() == 1 ? Ui.OR : c.rang() <= 3 ? Ui.TEXT : Ui.MUTED;
                Ui.right(g, font, Ui.lit(String.valueOf(c.rang()), Ui.BOLD), getX() + 16, ty, Ui.argb(rangC, 255));
                BannerArt.draw(g, c.banniere(), getX() + 21, ly + 3, 8, 11);
                String nom = Ui.ellipsis(font, c.nom(), Ui.BOLD, 100);
                g.drawString(font, Ui.lit(nom, Ui.BOLD), getX() + 34, ty, Ui.argb(Ui.TEXT, 255), false);
                int nw = font.width(Ui.lit(nom, Ui.BOLD));
                // le tag s'arrete avant les points PVE (au plus « 99 999 » + la place, environ 36 de large)
                String tag = Ui.ellipsis(font, "[" + c.tag() + "]", Ui.SMALL, Math.max(0, R_PVE - 36 - 4 - (34 + nw + 4)));
                g.drawString(font, Ui.lit(tag, Ui.SMALL), getX() + 34 + nw + 4, ty + 1, Ui.argb(Ui.MUTED, 255), false);
                colonne(g, c.pve(), c.rPve(), getX() + R_PVE, ty, C_PVE);
                colonne(g, c.pvp(), c.rPvp(), getX() + R_PVP, ty, C_PVP);
                colonne(g, c.events(), c.rEvents(), getX() + R_EV, ty, C_EV);
                Ui.right(g, font, Ui.lit(String.valueOf(c.sommePlaces()), Ui.BOLD), getX() + wl, ty, Ui.argb(c.rang() == 1 ? Ui.OR : Ui.TEXT, 255));
                if (i < l.size() - 1) Ui.hairline(g, getX(), getX() + wl, ly + LIGNE - 1, 0xFFFFFF, 14, false, true);
            }
        }

        /** Points (police normale) puis la place dans la categorie, en petit et en couleur, a droite. */
        private void colonne(GuiGraphics g, long points, int place, int xr, int ty, int couleur) {
            Component p = Ui.lit(String.valueOf(place), Ui.SMALL);
            int pw = font.width(p);
            Ui.right(g, font, p, xr, ty + 1, Ui.argb(couleur, 255));
            Ui.right(g, font, Ui.lit(Ui.nombre(points), Ui.FONT), xr - pw - 3, ty, Ui.argb(Ui.TEXT, 230));
        }
    }

    private final class Bareme extends HzScroll {
        Bareme(int x, int y, int w, int h) {
            super(x, y, w, h);
        }

        @Override
        protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            List<LigneBareme> l = lignes(ClientClans.saison());
            int premiere = Math.max(0, getScroll() / ENTREE), derniere = Math.min(l.size(), (getScroll() + getHeight()) / ENTREE + 1);
            int wl = getWidth() - 2;
            for (int i = premiere; i < derniere; i++) {
                LigneBareme b = l.get(i);
                int ly = getY() + i * ENTREE;
                Object[] v = new Object[b.valeurs().length];
                for (int k = 0; k < v.length; k++) v[k] = nombre(b.valeurs()[k]);
                Component pts = Ui.tr(Ui.BOLD, "gui.saison.pts." + b.forme(), v);
                int pw = font.width(pts);
                Ui.right(g, font, pts, getX() + wl, ly + 2, Ui.argb(COULEURS[Math.max(0, Math.min(2, b.categorie()))], 255));
                String lib = Ui.ellipsis(font, tr("gui.saison.l." + b.cle()), Ui.BOLD, wl - pw - 8);
                g.drawString(font, Ui.lit(lib, Ui.BOLD), getX(), ly + 2, Ui.argb(Ui.TEXT, 255), false);
                if (!b.condition().isEmpty()) {
                    String cond = Ui.ellipsis(font, tr("gui.saison.c." + b.condition(), (Object[]) b.args()), Ui.SMALL, wl);
                    g.drawString(font, Ui.lit(cond, Ui.SMALL), getX(), ly + 12, Ui.argb(Ui.FAINT, 255), false);
                }
                if (i < l.size() - 1) Ui.hairline(g, getX(), getX() + wl, ly + ENTREE - 1, 0xFFFFFF, 14, false, false);
            }
        }
    }
}
