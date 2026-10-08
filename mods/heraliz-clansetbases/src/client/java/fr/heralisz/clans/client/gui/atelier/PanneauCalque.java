package fr.heralisz.clans.client.gui.atelier;

import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.Calque;
import fr.heralisz.clans.registry.Sons;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Panneau contextuel d'un calque selectionne (colonne de droite) : COULEUR (cibles FORME / CONTOUR, palette de 16
 * couleurs ; le champ hexa est un widget de l'ecran place a droite des cibles), CONTOUR (aucun · 1 · 2 · 3 · 4),
 * TAILLE (glissiere, proportions gardees), ROTATION (glissiere + 0° / 90°), puis un rappel des gestes.
 * Dimensions : voir les constantes Y_* (decalages depuis le haut du panneau).
 */
public final class PanneauCalque {
    /** Decalages verticaux des rangees depuis le haut du panneau. */
    public static final int Y_CIBLES = 14, Y_PALETTE = 32, Y_CONTOUR = 52, Y_EPAISSEURS = 64, Y_TAILLE = 88, Y_TAILLE_G = 102,
            Y_ROTATION = 120, Y_ROTATION_G = 134, Y_AIDE = 160;
    public static final int CIBLE_W = 60, PUCE_H = 12, EPAISSEUR_W = 40, EPAISSEUR_PAS = 44, ANGLE_W = 28;
    private final Atelier at;
    private int x, y, w;
    private final Glissiere taille, rotation;
    private Calque departTaille;
    /** Calque vise par la glissiere en cours (un raccourci clavier peut changer la selection pendant la prise). */
    private int indexGeste = -1;
    private int groupe;
    private String forme = "", cibleForme = "", cibleContour = "", contour = "", tailleTitre = "", rotationTitre = "", aucun = "";
    private Component aide1, aide2, aide3, tropPetit;
    /** Textes des valeurs, recalcules seulement quand elles changent. */
    private int tailleW = -1, tailleH = -1, rotationV = -1, formeV = -1;
    private Component tailleTxt = Component.empty(), rotationTxt = Component.empty();

    public PanneauCalque(Atelier at) {
        this.at = at;
        taille = new Glissiere(Calque.TAILLE_MIN, Calque.TAILLE_MAX, () -> {
            departTaille = calque();
            indexGeste = at.selection();
            groupe = at.nouveauGeste();
        }, this::changerTaille);
        rotation = new Glissiere(0, 359, () -> {
            indexGeste = at.selection();
            groupe = at.nouveauGeste();
        }, v -> {
            Calque c = calque();
            if (c != null && at.selection() == indexGeste) remplacer(c.avecRotation(v), groupe);
        });
    }

    public void placer(int x, int y, int w) {
        this.x = x;
        this.y = y;
        this.w = w;
        taille.placer(x, y + Y_TAILLE_G, w);
        rotation.placer(x, y + Y_ROTATION_G, w - 2 * (ANGLE_W + 4));
        cibleForme = tr("gui.atelier.cible_forme");
        cibleContour = tr("gui.atelier.cible_contour");
        contour = tr("gui.atelier.contour");
        tailleTitre = tr("gui.atelier.taille");
        rotationTitre = tr("gui.atelier.rotation");
        aucun = tr("gui.atelier.contour_aucun");
        aide1 = Ui.tr(Ui.SMALL, "gui.atelier.aide_toile");
        aide2 = Ui.tr(Ui.SMALL, "gui.atelier.aide_maj");
        aide3 = Ui.tr(Ui.SMALL, "gui.atelier.aide_clavier");
        tropPetit = Ui.tr(Ui.SMALL, "gui.atelier.contour_trop_petit");
        formeV = -1;
        tailleW = -1;
        rotationV = -1;
    }

    private static String tr(String cle) {
        return Component.translatable(cle).getString();
    }

    /** Position du champ hexa (widget de l'ecran) : a droite des cibles de couleur. */
    public int hexaX() {
        return x + w - 56;
    }

    public int hexaY() {
        return y + Y_CIBLES - 1;
    }

    private Calque calque() {
        return at.banniere().calque(at.selection());
    }

    private void remplacer(Calque n, int groupe) {
        Banniere b = at.banniere();
        int sel = at.selection();
        Calque c = b.calque(sel);
        if (c != null && !c.equals(n)) at.changer(b.avecCalque(sel, n), groupe);
    }

    private void changerTaille(int v) {
        // la selection a change pendant la prise (Tab, Suppr, Ctrl+Z...) : les proportions de depart ne valent plus rien
        if (at.selection() != indexGeste) return;
        Calque c0 = departTaille != null ? departTaille : calque();
        if (c0 == null) return;
        float k = v / (float) Math.max(c0.w(), c0.h());
        int nw = Math.max(Calque.TAILLE_MIN, Math.round(c0.w() * k)), nh = Math.max(Calque.TAILLE_MIN, Math.round(c0.h() * k));
        Calque c = calque();
        if (c != null) remplacer(c.avecTaille(nw, nh), groupe);
    }

    private void majTextes(Calque c) {
        if (c.forme() != formeV) {
            formeV = c.forme();
            forme = Noms.texte(c.forme());
        }
        if (c.w() != tailleW || c.h() != tailleH) {
            tailleW = c.w();
            tailleH = c.h();
            tailleTxt = Ui.lit(tailleW + " × " + tailleH, Ui.SMALL);
        }
        if (c.rot() != rotationV) {
            rotationV = c.rot();
            rotationTxt = Ui.lit(rotationV + "°", Ui.SMALL);
        }
    }

    // ------------------------------------------------------------------ rendu
    public void render(GuiGraphics g, int mx, int my) {
        Calque c = calque();
        if (c == null) return;
        Font font = at.police();
        int accent = at.accent();
        majTextes(c);
        Ui.section(g, font, forme, x, y, w);
        // couleur : cibles + palette (le champ hexa est dessine par l'ecran)
        int cible = at.cible();
        Dessin.puce(g, font, cibleForme, x, y + Y_CIBLES, CIBLE_W, PUCE_H, cible == 0, Dessin.dans(mx, my, x, y + Y_CIBLES, CIBLE_W, PUCE_H), false, accent);
        Dessin.puce(g, font, cibleContour, x + CIBLE_W + 4, y + Y_CIBLES, CIBLE_W, PUCE_H, cible == 1,
                Dessin.dans(mx, my, x + CIBLE_W + 4, y + Y_CIBLES, CIBLE_W, PUCE_H), false, accent);
        Ui.soft(g, x + 2 * CIBLE_W + 12, y + Y_CIBLES + 1, 10, 10, Ui.argb(cible == 0 ? c.couleur() : c.couleurContour(), 255));
        g.drawString(font, Dessin.DIESE, hexaX() - 7, hexaY() + 3, Ui.argb(Ui.FAINT, 255), false);
        Dessin.palette(g, x, y + Y_PALETTE, cible == 0 ? c.couleur() : c.couleurContour(), mx, my);
        // contour
        Ui.section(g, font, contour, x, y + Y_CONTOUR, w);
        // epaisseurs que le rendu ne sait pas dessiner a cette taille : grisees (voir Calque.contourMax)
        int max = Calque.contourMax(c.w(), c.h()), effectif = c.contourEffectif();
        for (int k = 0; k <= Calque.CONTOUR_MAX; k++) {
            int px = x + k * EPAISSEUR_PAS;
            boolean survol = Dessin.dans(mx, my, px, y + Y_EPAISSEURS, EPAISSEUR_W, 14);
            Dessin.puce(g, font, k == 0 ? aucun : CHIFFRES[k], px, y + Y_EPAISSEURS, EPAISSEUR_W, 14, effectif == k, survol, k > max, accent);
            if (survol && k > max) at.infobulle(tropPetit, null);
        }
        // taille
        int vw = font.width(tailleTxt);
        Ui.section(g, font, tailleTitre, x, y + Y_TAILLE, w - vw - 8);
        g.drawString(font, tailleTxt, x + w - vw, y + Y_TAILLE, Ui.argb(Ui.TEXT, 255), false);
        taille.valeur(Math.max(c.w(), c.h()));
        taille.render(g, mx, my, accent);
        // rotation
        int rw = font.width(rotationTxt);
        Ui.section(g, font, rotationTitre, x, y + Y_ROTATION, w - rw - 8);
        g.drawString(font, rotationTxt, x + w - rw, y + Y_ROTATION, Ui.argb(Ui.TEXT, 255), false);
        rotation.valeur(c.rot());
        rotation.render(g, mx, my, accent);
        int ax = x + w - 2 * ANGLE_W - 4, ay = y + Y_ROTATION_G - 2;
        Dessin.puce(g, font, ANGLES[0], ax, ay, ANGLE_W, 14, c.rot() == 0, Dessin.dans(mx, my, ax, ay, ANGLE_W, 14), false, accent);
        Dessin.puce(g, font, ANGLES[1], ax + ANGLE_W + 4, ay, ANGLE_W, 14, c.rot() == 90, Dessin.dans(mx, my, ax + ANGLE_W + 4, ay, ANGLE_W, 14), false,
                accent);
        // aide
        g.drawString(font, aide1, x, y + Y_AIDE, Ui.argb(Ui.FAINT, 255), false);
        g.drawString(font, aide2, x, y + Y_AIDE + 10, Ui.argb(Ui.FAINT, 255), false);
        g.drawString(font, aide3, x, y + Y_AIDE + 20, Ui.argb(Ui.FAINT, 255), false);
    }

    private static final String[] CHIFFRES = {"0", "1", "2", "3", "4"};
    private static final String[] ANGLES = {"0°", "90°"};

    // ------------------------------------------------------------------ souris
    public boolean clic(double mx, double my, int bouton) {
        Calque c = calque();
        if (bouton != 0 || c == null || !Dessin.dans(mx, my, x, y, w, Y_AIDE)) return false;
        if (Dessin.dans(mx, my, x, y + Y_CIBLES, CIBLE_W, PUCE_H)) return clicCible(0);
        if (Dessin.dans(mx, my, x + CIBLE_W + 4, y + Y_CIBLES, CIBLE_W, PUCE_H)) return clicCible(1);
        int p = Dessin.paletteSous(x, y + Y_PALETTE, mx, my);
        if (p >= 0) {
            Ui.son(Sons.UI_CLIC, 0.5f);
            at.appliquerCouleur(Dessin.PALETTE[p], Historique.AUCUN);
            return true;
        }
        for (int k = 0; k <= Calque.CONTOUR_MAX; k++) {
            if (Dessin.dans(mx, my, x + k * EPAISSEUR_PAS, y + Y_EPAISSEURS, EPAISSEUR_W, 14)) {
                if (k > Calque.contourMax(c.w(), c.h())) return true;            // grisee : calque trop petit
                Ui.son(Sons.UI_CLIC, 0.5f);
                remplacer(c.avecContour(k), Historique.AUCUN);
                if (k > 0 && at.cible() == 0 && c.contour() == 0) at.cible(1);     // premier contour : la palette passe au contour
                return true;
            }
        }
        int ax = x + w - 2 * ANGLE_W - 4, ay = y + Y_ROTATION_G - 2;
        if (Dessin.dans(mx, my, ax, ay, ANGLE_W, 14)) {
            Ui.son(Sons.UI_CLIC, 0.5f);
            remplacer(c.avecRotation(0), Historique.AUCUN);
            return true;
        }
        if (Dessin.dans(mx, my, ax + ANGLE_W + 4, ay, ANGLE_W, 14)) {
            Ui.son(Sons.UI_CLIC, 0.5f);
            remplacer(c.avecRotation(90), Historique.AUCUN);
            return true;
        }
        if (taille.clic(mx, my) || rotation.clic(mx, my)) return true;
        return true;
    }

    private boolean clicCible(int c) {
        Ui.son(Sons.UI_CLIC, 0.5f);
        at.cible(c);
        return true;
    }

    public boolean glisser(double mx) {
        return taille.glisser(mx) | rotation.glisser(mx);
    }

    public void relacher() {
        taille.relacher();
        rotation.relacher();
        departTaille = null;
        indexGeste = -1;
    }

    /** Position d'une puce d'epaisseur de contour (banc d'essai) : centre x. */
    public int epaisseurX(int k) {
        return x + k * EPAISSEUR_PAS + EPAISSEUR_W / 2;
    }

    public int epaisseurY() {
        return y + Y_EPAISSEURS + 7;
    }
}
