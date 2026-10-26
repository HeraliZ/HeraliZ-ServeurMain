package fr.heralisz.clans.client.gui.atelier;

import fr.heralisz.clans.client.gui.kit.FormeArt;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.Calque;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Liste des calques (colonne du milieu) : une ligne de {@value #LIGNE} px par calque, le calque du dessus en haut
 * (comme dans les logiciels de dessin) ; vignette {@link FormeArt} teintee sur une pastille, nom de la forme, pastille de
 * couleur a droite. Clic = selection ; glisser une ligne = la deplacer dans la pile (ligne d'insertion a l'accent).
 */
public final class ListeCalques {
    public static final int LIGNE = 18;
    /** Hauteur de la liste pleine (12 lignes). */
    public static final int HAUTEUR = Banniere.MAX_CALQUES * LIGNE;
    private final Atelier at;
    private int x, y, w;
    /** Calque presse (index dans la pile), -1 sinon. */
    private int presse = -1;
    private double yPresse;
    private boolean glisse;
    /** Ecart d'insertion vise (0 = au-dessus de la premiere ligne, n = sous la derniere). */
    private int ecart;
    private Component vide1, vide2;

    public ListeCalques(Atelier at) {
        this.at = at;
    }

    public void placer(int x, int y, int w) {
        this.x = x;
        this.y = y;
        this.w = w;
        vide1 = Ui.tr(Ui.SMALL, "gui.atelier.aucun_calque");
        vide2 = Ui.tr(Ui.SMALL, "gui.atelier.aucun_calque_aide");
    }

    private int ligneDe(int index, int n) {
        return n - 1 - index;
    }

    public void render(GuiGraphics g, int mx, int my) {
        Banniere b = at.banniere();
        Font font = at.police();
        int n = b.nombreCalques(), accent = at.accent();
        if (n == 0) {
            g.drawString(font, vide1, x + 2, y + 6, Ui.argb(Ui.MUTED, 255), false);
            g.drawString(font, vide2, x + 2, y + 17, Ui.argb(Ui.FAINT, 255), false);
            return;
        }
        for (int i = n - 1; i >= 0; i--) {
            Calque c = b.calque(i);
            int ly = y + ligneDe(i, n) * LIGNE;
            boolean sel = i == at.selection(), survol = !glisse && Dessin.dans(mx, my, x, ly, w, LIGNE);
            if (glisse && i == presse) Ui.soft(g, x, ly, w, LIGNE - 1, Ui.argb(0xFFFFFF, 6));
            else if (sel) {
                Ui.soft(g, x, ly, w, LIGNE - 1, Ui.argb(accent, 40));
                g.fill(x, ly + 3, x + 2, ly + LIGNE - 4, Ui.argb(accent, 255));
            } else if (survol) Ui.soft(g, x, ly, w, LIGNE - 1, Ui.argb(0xFFFFFF, 10));
            int alpha = glisse && i == presse ? 90 : 255;
            ligne(g, font, c, x, ly, alpha, sel);
        }
        if (glisse && presse >= 0) {
            // ligne d'insertion + fantome de la ligne tiree
            int iy = y + ecart * LIGNE - 1;
            g.fill(x, iy, x + w, iy + 2, Ui.argb(accent, 255));
            Calque c = b.calque(presse);
            if (c != null) {
                int gy = Math.max(y - LIGNE / 2, Math.min(y + n * LIGNE - LIGNE / 2, my - LIGNE / 2));
                Ui.soft(g, x + 4, gy, w - 4, LIGNE - 1, Ui.argb(0x0B0C0F, 220));
                Ui.softOutline(g, x + 5, gy + 1, w - 6, LIGNE - 3, Ui.argb(accent, 160));
                ligne(g, font, c, x + 4, gy, 255, true);
            }
        }
    }

    /** Contenu d'une ligne : vignette, nom, pastille de couleur. */
    private void ligne(GuiGraphics g, Font font, Calque c, int lx, int ly, int alpha, boolean sel) {
        Banniere b = at.banniere();
        // pastille de fond aux couleurs de la banniere ; si la forme s'y confond, fond neutre contraste
        int fond = b.couleurA;
        if (Math.abs(Dessin.luminance(fond) - Dessin.luminance(c.couleur())) < 0.18f) fond = Dessin.luminance(c.couleur()) > 0.5f ? 0x1B1F2A : 0x8F95A0;
        Ui.soft(g, lx + 5, ly + 1, 15, 15, Ui.argb(fond, alpha));
        // vignette en retrait de 2 px dans la pastille : une forme pleine (carre) reste lisible comme une forme posee sur le fond
        FormeArt.draw(g, c.forme(), lx + 7, ly + 3, 11, c.couleur(), alpha);
        g.drawString(font, Noms.coupe(font, c.forme(), w - 44), lx + 25, ly + 5, Ui.argb(sel ? Ui.TEXT : Ui.MUTED, alpha), false);
        Ui.soft(g, lx + w - 12, ly + 5, 7, 7, Ui.argb(c.couleur(), alpha));
        Ui.softOutline(g, lx + w - 12, ly + 5, 7, 7, Ui.argb(0xFFFFFF, Math.min(alpha, 40)));
    }

    // ------------------------------------------------------------------ souris
    public boolean dans(double mx, double my) {
        return Dessin.dans(mx, my, x, y, w, HAUTEUR);
    }

    public boolean clic(double mx, double my, int bouton) {
        if (bouton != 0 || !dans(mx, my)) return false;
        int n = at.banniere().nombreCalques();
        int ligne = (int) ((my - y) / LIGNE);
        if (ligne < 0 || ligne >= n) return true;                     // vide de la liste : rien, mais le clic est pris
        int i = n - 1 - ligne;
        presse = i;
        yPresse = my;
        glisse = false;
        at.selectionner(i);
        return true;
    }

    public boolean glisser(double mx, double my) {
        if (presse < 0) return false;
        if (!glisse && Math.abs(my - yPresse) > 3) glisse = true;
        if (glisse) {
            int n = at.banniere().nombreCalques();
            ecart = Math.max(0, Math.min(n, (int) Math.floor((my - y) / LIGNE + 0.5)));
        }
        return true;
    }

    /** Oublie le glisser en cours sans deplacer (la pile a change au clavier pendant la prise). */
    public void abandonner() {
        presse = -1;
        glisse = false;
    }

    public void relacher() {
        if (presse >= 0 && glisse) {
            int n = at.banniere().nombreCalques();
            int source = ligneDe(presse, n);
            if (ecart != source && ecart != source + 1) {
                int cible = ecart > source ? ecart - 1 : ecart;
                at.deplacer(presse, n - 1 - cible);
            }
        }
        presse = -1;
        glisse = false;
    }
}
