package fr.heralisz.clans.client.gui.tabs;

import fr.heralisz.clans.ClansConfig;
import fr.heralisz.clans.client.ClientClans;
import fr.heralisz.clans.client.gui.kit.HzButton;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.Arbre;
import fr.heralisz.clans.model.Avantages;
import fr.heralisz.clans.model.Niveaux;
import fr.heralisz.clans.network.Packets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Niveau du clan (maquette validee du 21/10, arbre de la saison des clans du 22/10) : a gauche « Prochain niveau » et
 * « Gagner de l'XP » ; a droite l'arbre d'ameliorations (une ligne par branche : palier, effet actuel et suivant, cout en
 * points et en herals, bouton Ameliorer pour le chef) et, en bas, les points et places de la saison. Chiffres :
 * {@link Arbre} (les memes que le serveur, qui verifie tout). Le prochain niveau est en gras 17 a sa vraie taille
 * ({@link Ui#GROS}), pose sur un point de l'ecran (regle du 23/10 : jamais de texte agrandi).
 */
public final class NiveauTab extends Tab {
    private static final int GAUCHE = 170, LIGNE = 14, RANG = 24;
    private final List<HzButton> boutons = new ArrayList<>();

    @Override
    protected void construire() {
        boutons.clear();
        int tx = x + GAUCHE + 8, tw = w - GAUCHE - 8;
        Arbre.Branche[] br = Arbre.Branche.values();
        for (int i = 0; i < br.length; i++) {
            Arbre.Branche b = br[i];
            HzButton bt = new HzButton(tx + tw - 74, y + 30 + i * RANG, 64, 14, Component.translatable("gui.arbre.ameliorer"), accent,
                    () -> envoyer(new Packets.AmeliorerC2S(b.id()))).look(HzButton.Look.LINK);
            ecran.ajouter(bt);
            boutons.add(bt);
        }
        majBoutons();
    }

    @Override
    public void tick() {
        majBoutons();
    }

    private void majBoutons() {
        Packets.ArbreSaisonS2C a = ClientClans.arbre();
        Arbre.Branche[] br = Arbre.Branche.values();
        for (int i = 0; i < boutons.size(); i++) {
            int p = palier(a, i);
            int suivant = p + 1;
            boutons.get(i).active = a != null && a.chef() && suivant <= br[i].max && a.pointsLibres() >= Arbre.coutPoints(br[i], suivant)
                    && s.banque() >= Arbre.coutHerals(br[i], suivant) && s.niveau() >= Arbre.niveauRequis(br[i], suivant);
            boutons.get(i).visible = suivant <= br[i].max;
        }
    }

    private static int palier(Packets.ArbreSaisonS2C a, int i) {
        return a == null || i >= a.paliers().length ? 0 : a.paliers()[i];
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int n = s.niveau();
        int hCarte = 112;
        carte(g, x, y, GAUCHE, hCarte);
        Ui.spaced(g, font, tr("gui.niveau.prochain"), Ui.SMALL, x + 10, y + 9, Ui.argb(Ui.MUTED, 255), 1.0f);
        // meme milieu des capitales que l'ancien texte 11 agrandi 1,5 fois depuis y + 22
        float yNiveau = y + 22 + 1.5f * Ui.milieu(11) - Ui.milieu(17);
        if (n >= Niveaux.MAX) {
            net(g, Ui.tr(Ui.GROS, "gui.niveau_max"), x + 10, yNiveau, Ui.argb(Ui.OR, 255));
        } else {
            net(g, Ui.tr(Ui.GROS, "gui.niveau.n", n + 1), x + 10, yNiveau, Ui.argb(Ui.OR, 255));
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
                {"gui.niveau.xp.don", null},
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
        arbre(g);
    }

    /** Carte de droite : l'arbre, puis la saison. */
    private void arbre(GuiGraphics g) {
        Packets.ArbreSaisonS2C a = ClientClans.arbre();
        int tx = x + GAUCHE + 8, tw = w - GAUCHE - 8;
        carte(g, tx, y, tw, h);
        Ui.spaced(g, font, tr("gui.arbre.titre"), Ui.SMALL, tx + 10, y + 9, Ui.argb(Ui.MUTED, 255), 1.0f);
        int libres = a == null ? 0 : a.pointsLibres();
        Ui.spacedRight(g, font, tr("gui.arbre.points", libres), Ui.SMALL, tx + tw - 10, y + 9, Ui.argb(libres > 0 ? Ui.OR : Ui.FAINT, 255), 0.6f);
        Arbre.Branche[] br = Arbre.Branche.values();
        // ligne 1 : nom puis effet (actuel → suivant) ; ligne 2 : pastilles du palier puis cout ; tout s'arrete avant le bouton
        int fin = tx + tw - 80, cx = tx + 44;
        for (int i = 0; i < br.length; i++) {
            Arbre.Branche b = br[i];
            int p = palier(a, i), ry = y + 26 + i * RANG;
            if (i % 2 == 0) Ui.soft(g, tx + 5, ry - 2, tw - 10, RANG - 2, Ui.argb(0xFFFFFF, 6));
            Component nom = Ui.lit(Ui.ellipsis(font, tr("arbre." + b.id()), Ui.BOLD, fin - tx - 10), Ui.BOLD);
            g.drawString(font, nom, tx + 10, ry, Ui.argb(Ui.TEXT, 255), false);
            // pastilles du palier
            for (int k = 0; k < b.max; k++) Ui.point(g, tx + 12 + k * 6, ry + 14, k < p ? Ui.OR : Ui.FAINT, 255);
            String effet = tr("arbre.effet." + b.id(), Arbre.effet(b, p));
            if (p < b.max) effet += "  →  " + tr("arbre.effet." + b.id(), Arbre.effet(b, p + 1));
            int ex = tx + 10 + font.width(nom) + 8;
            if (fin - ex > 12) {
                g.drawString(font, Ui.lit(Ui.ellipsis(font, effet, Ui.SMALL, fin - ex), Ui.SMALL), ex, ry + 1, Ui.argb(Ui.MUTED, 255), false);
            }
            if (p < b.max) {
                int requis = Arbre.niveauRequis(b, p + 1);
                String cout = requis > 0
                        ? tr("gui.arbre.cout_niveau", Arbre.coutPoints(b, p + 1), Ui.nombre(Arbre.coutHerals(b, p + 1)), requis)
                        : tr("gui.arbre.cout", Arbre.coutPoints(b, p + 1), Ui.nombre(Arbre.coutHerals(b, p + 1)));
                boolean manque = requis > s.niveau();
                g.drawString(font, Ui.lit(Ui.ellipsis(font, cout, Ui.SMALL, fin - cx), Ui.SMALL), cx, ry + 11,
                        Ui.argb(manque ? Ui.AMBRE : Ui.FAINT, 255), false);
            } else {
                g.drawString(font, Ui.lit(Ui.ellipsis(font, tr("gui.arbre.max"), Ui.SMALL, tx + tw - 10 - cx), Ui.SMALL), cx, ry + 11,
                        Ui.argb(Ui.OK, 255), false);
            }
        }
        // saison
        int sy = y + 30 + br.length * RANG;
        Ui.hairline(g, tx + 8, tx + tw - 8, sy - 4, 0xFFFFFF, 30, true, true);
        if (a == null || !a.saison()) {
            g.drawString(font, Ui.tr(Ui.SMALL, "gui.saison.aucune"), tx + 10, sy + 2, Ui.argb(Ui.FAINT, 255), false);
        } else if (!a.coeur()) {
            g.drawString(font, Ui.lit(Ui.ellipsis(font, tr("gui.saison.sans_coeur"), Ui.SMALL, tw - 20), Ui.SMALL), tx + 10, sy + 2,
                    Ui.argb(Ui.AMBRE, 255), false);
        } else {
            int[] r = a.rangs();
            String l1 = tr("gui.saison.points", Ui.nombre(a.pve()), place(r, 0), Ui.nombre(a.pvp()), place(r, 1), Ui.nombre(a.events()), place(r, 2));
            g.drawString(font, Ui.lit(Ui.ellipsis(font, l1, Ui.SMALL, tw - 20), Ui.SMALL), tx + 10, sy + 2, Ui.argb(Ui.TEXT, 230), false);
            g.drawString(font, Ui.tr(Ui.BOLD, "gui.saison.general", place(r, 3)), tx + 10, sy + 13, Ui.argb(Ui.OR, 255), false);
        }
    }

    private static String place(int[] r, int i) {
        return i < r.length && r[i] > 0 ? "#" + r[i] : "-";
    }

    private void carte(GuiGraphics g, int cx, int cy, int cw, int ch) {
        Ui.soft(g, cx, cy, cw, ch, Ui.argb(0xFFFFFF, 8));
        Ui.softOutline(g, cx + 1, cy + 1, cw - 2, ch - 2, Ui.argb(0xFFFFFF, 16));
    }

    /** Ce que le niveau n apporte : un point de competence, et la deco a certains paliers. */
    private List<String> gains(int n) {
        List<String> l = new ArrayList<>();
        l.add(tr("gui.niveau.gain.point"));
        for (int p : Avantages.PALIERS) if (p == n) l.add(tr("gui.niveau.court." + p));
        return l;
    }

    /** Texte dessine a sa vraie taille, pose sur un point entier de l'ecran ({@link Ui#placer}) ; (x, y) = point de dessin. */
    private void net(GuiGraphics g, Component c, float x, float y, int argb) {
        g.pose().pushPose();
        Ui.placer(g, x, y);
        g.drawString(font, c, 0, 0, argb, false);
        g.pose().popPose();
    }
}
