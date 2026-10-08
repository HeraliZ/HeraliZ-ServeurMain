package fr.heralisz.clans.client.gui.atelier;

import fr.heralisz.clans.client.gui.kit.BannerArt;
import fr.heralisz.clans.client.gui.kit.FormeArt;
import fr.heralisz.clans.client.gui.kit.HzScroll;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.Formes;
import fr.heralisz.clans.registry.Sons;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * Selecteur d'ajout (panneau de droite) : onglets FORMES · EMBLEMES · MODELES, grille de cases de {@value #CASE} px
 * (6 colonnes, defilante a la molette). Une forme ou un embleme ajoute un calque au centre et le selectionne ; un
 * modele remplace toute la banniere (annulable). Les cases de forme sont grisees quand la banniere a deja 12 calques.
 */
public final class Selecteur {
    public enum Onglet { FORMES, EMBLEMES, MODELES }

    public static final int CASE = 36, COLONNES = 6, MODELE_H = 54, ONGLETS_H = 16;
    private final Atelier at;
    private int x, y, w, h;
    private Onglet onglet = Onglet.FORMES;
    private final int[] ongletX = new int[3], ongletW = new int[3];
    private final String[] libelles = new String[3];
    private Component plein;
    private Grille grille;

    public Selecteur(Atelier at) {
        this.at = at;
    }

    public void placer(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        Font font = at.police();
        String[] cles = {"gui.atelier.onglet.formes", "gui.atelier.onglet.emblemes", "gui.atelier.onglet.modeles"};
        int ox = x;
        for (int i = 0; i < 3; i++) {
            libelles[i] = Component.translatable(cles[i]).getString().toUpperCase(Locale.ROOT);
            ongletX[i] = ox;
            ongletW[i] = (int) Ui.spacedWidth(font, libelles[i], Ui.SMALL, 1.0f);
            ox += ongletW[i] + 16;
        }
        plein = Ui.tr(Ui.SMALL, "gui.atelier.plein", Banniere.MAX_CALQUES);
        int defilement = grille != null ? grille.getScroll() : 0;
        grille = new Grille(x, y + ONGLETS_H + 4, COLONNES * CASE + 6, h - ONGLETS_H - 4);
        reglerContenu();
        grille.setScroll(defilement);
    }

    public Onglet onglet() {
        return onglet;
    }

    public void onglet(Onglet o) {
        if (o == onglet) return;
        onglet = o;
        reglerContenu();
        grille.setScroll(0);
    }

    private void reglerContenu() {
        int n = nombre();
        int lignes = (n + COLONNES - 1) / COLONNES;
        grille.setContentHeight(lignes * hauteurCase());
        grille.accent(at.accent());
    }

    private int nombre() {
        return switch (onglet) {
            case FORMES -> Formes.NOMBRE - Formes.EMBLEMES;
            case EMBLEMES -> Formes.EMBLEMES;
            case MODELES -> Modeles.tous().size();
        };
    }

    private int hauteurCase() {
        return onglet == Onglet.MODELES ? MODELE_H : CASE;
    }

    /** Forme de la case k de l'onglet courant (formes : 32..63, emblemes : 0..31). */
    private int forme(int k) {
        return onglet == Onglet.FORMES ? Formes.EMBLEMES + k : k;
    }

    /** Rectangle d'une case (coordonnees d'interface, sans defilement) : x, y, w, h — pour le banc d'essai. */
    public int caseX(int k) {
        return x + (k % COLONNES) * CASE;
    }

    public int caseY(int k) {
        return grille.getY() + (k / COLONNES) * hauteurCase() - grille.getScroll();
    }

    public int ongletX(Onglet o) {
        return ongletX[o.ordinal()];
    }

    public int ongletW(Onglet o) {
        return ongletW[o.ordinal()];
    }

    // ------------------------------------------------------------------ rendu
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Font font = at.police();
        int accent = at.accent();
        for (int i = 0; i < 3; i++) {
            boolean actif = onglet.ordinal() == i, survol = Dessin.dans(mx, my, ongletX[i] - 4, y - 3, ongletW[i] + 8, ONGLETS_H);
            int c = actif ? Ui.argb(Ui.TEXT, 255) : survol ? Ui.argb(Ui.TEXT, 200) : Ui.argb(Ui.MUTED, 255);
            Ui.spaced(g, font, libelles[i], Ui.SMALL, ongletX[i], y + 2, c, 1.0f);
            if (actif) g.fill(ongletX[i], y + 11, ongletX[i] + ongletW[i], y + 12, Ui.argb(accent, 255));
        }
        Ui.hairline(g, x, x + w, y + ONGLETS_H, 0xFFFFFF, 30, false, true);
        grille.accent(accent);
        grille.render(g, mx, my, pt);
        if (onglet != Onglet.MODELES && at.banniere().pleine()) {
            int py = y + h - 14;
            Ui.soft(g, x, py, w, 14, Ui.argb(0x0B0C0F, 230));
            g.drawString(font, plein, x + 6, py + 4, Ui.argb(Ui.AMBRE, 255), false);
        }
    }

    // ------------------------------------------------------------------ souris
    public boolean dans(double mx, double my) {
        return Dessin.dans(mx, my, x, y - 3, w, h + 3);
    }

    public boolean clic(double mx, double my, int bouton) {
        if (bouton != 0 || !dans(mx, my)) return false;
        if (my < y + ONGLETS_H) {
            // zones contigues : chaque onglet prend jusqu'a mi-chemin du suivant
            for (int i = 0; i < 3; i++) {
                int debut = i == 0 ? x : ongletX[i] - 8, fin = i == 2 ? x + w : ongletX[i + 1] - 8;
                if (mx >= debut && mx < fin) {
                    Ui.son(Sons.UI_CLIC, 0.5f);
                    onglet(Onglet.values()[i]);
                    return true;
                }
            }
            return true;
        }
        grille.mouseClicked(mx, my, bouton);
        return true;
    }

    public boolean molette(double mx, double my, double delta) {
        return grille.mouseScrolled(mx, my, delta);
    }

    // ------------------------------------------------------------------ grille
    private final class Grille extends HzScroll {
        Grille(int x, int y, int w, int h) {
            super(x, y, w, h);
        }

        @Override
        protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int n = nombre(), hc = hauteurCase(), accent = at.accent();
            Banniere b = at.banniere();
            boolean grise = onglet != Onglet.MODELES && b.pleine();
            List<Modeles.Modele> modeles = Modeles.tous();
            int haut = getY() + getScroll(), bas = haut + getHeight();
            for (int k = 0; k < n; k++) {
                int cx = getX() + (k % COLONNES) * CASE, cy = getY() + (k / COLONNES) * hc;
                if (cy + hc < haut || cy > bas) continue;
                boolean survol = !grise && Dessin.dans(mouseX, mouseY, cx, cy, CASE, hc);
                if (onglet == Onglet.MODELES) {
                    Modeles.Modele m = modeles.get(k);
                    boolean actuel = m.banniere().equals(b);
                    if (actuel) Ui.soft(g, cx + 1, cy + 1, CASE - 2, hc - 2, Ui.argb(accent, 40));
                    else if (survol) Ui.soft(g, cx + 1, cy + 1, CASE - 2, hc - 2, Ui.argb(0xFFFFFF, 14));
                    BannerArt.draw(g, m.banniere(), cx + 4, cy + 5, 28, 42);
                    if (actuel) Ui.softOutline(g, cx + 2, cy + 2, CASE - 4, hc - 4, Ui.argb(accent, 220));
                    if (survol) at.infobulle(m.nom(), null);
                } else {
                    int f = forme(k);
                    if (survol) Ui.soft(g, cx + 1, cy + 1, CASE - 2, hc - 2, Ui.argb(0xFFFFFF, 16));
                    int rgb = grise ? Ui.FAINT : survol ? 0xFFFFFF : Ui.mix(Ui.TEXT, Ui.MUTED, 0.35f);
                    FormeArt.draw(g, f, cx + 6, cy + 6, CASE - 12, rgb, grise ? 120 : 255);
                    if (survol) at.infobulle(Noms.composant(f), null);
                }
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || !dansZone(mx, my)) return false;
            int col = (int) ((mx - getX()) / CASE), ligne = (int) ((my + getScroll() - getY()) / hauteurCase());
            if (col < 0 || col >= COLONNES || ligne < 0) return false;
            int k = ligne * COLONNES + col;
            if (k >= nombre()) return false;
            if (onglet == Onglet.MODELES) {
                Ui.son(Sons.UI_CLIC, 0.5f);
                at.appliquerModele(Modeles.tous().get(k));
            } else if (!at.banniere().pleine()) {
                Ui.son(Sons.UI_CLIC, 0.5f);
                at.ajouterForme(forme(k));
            }
            return true;
        }
    }
}
