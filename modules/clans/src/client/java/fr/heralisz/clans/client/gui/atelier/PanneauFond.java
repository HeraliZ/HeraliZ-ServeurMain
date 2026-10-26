package fr.heralisz.clans.client.gui.atelier;

import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.registry.Sons;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Panneau du fond (aucun calque selectionne) : FOND (cibles COULEUR A / COULEUR B, palette ; le champ hexa est un
 * widget de l'ecran), STYLE (5 pastilles avec apercu : vertical, diagonal, radial, partage, uni), puis l'aide.
 * La couleur B est grisee en style UNI.
 */
public final class PanneauFond {
    public static final int Y_CIBLES = 14, Y_PALETTE = 32, Y_STYLE = 52, Y_PASTILLES = 64, Y_NOM_STYLE = 92, Y_AIDE = 160;
    public static final int CIBLE_W = 64, PUCE_H = 12, STYLE_W = 40, STYLE_H = 24, STYLE_PAS = 44;
    private final Atelier at;
    private int x, y, w;
    private String fond = "", cibleA = "", cibleB = "", style = "";
    private final String[] styles = new String[Banniere.Degrade.values().length];
    private Component aide1, aide2;

    public PanneauFond(Atelier at) {
        this.at = at;
    }

    public void placer(int x, int y, int w) {
        this.x = x;
        this.y = y;
        this.w = w;
        fond = tr("gui.atelier.fond");
        cibleA = tr("gui.atelier.couleur_a");
        cibleB = tr("gui.atelier.couleur_b");
        style = tr("gui.atelier.style");
        for (Banniere.Degrade d : Banniere.Degrade.values()) styles[d.ordinal()] = tr(d.libelle()).toUpperCase(Locale.ROOT);
        aide1 = Ui.tr(Ui.SMALL, "gui.atelier.aide_fond");
        aide2 = Ui.tr(Ui.SMALL, "gui.atelier.aide_ajouter");
    }

    private static String tr(String cle) {
        return Component.translatable(cle).getString();
    }

    public int hexaX() {
        return x + w - 56;
    }

    public int hexaY() {
        return y + Y_CIBLES - 1;
    }

    /** Centre de la pastille de style d (banc d'essai). */
    public int styleX(Banniere.Degrade d) {
        return x + d.ordinal() * STYLE_PAS + STYLE_W / 2;
    }

    public int styleY() {
        return y + Y_PASTILLES + STYLE_H / 2;
    }

    public void render(GuiGraphics g, int mx, int my) {
        Banniere b = at.banniere();
        Font font = at.police();
        int accent = at.accent();
        boolean uni = b.degrade == Banniere.Degrade.UNI;
        int cible = uni ? 0 : at.cible();
        Ui.section(g, font, fond, x, y, w);
        Dessin.puce(g, font, cibleA, x, y + Y_CIBLES, CIBLE_W, PUCE_H, cible == 0, Dessin.dans(mx, my, x, y + Y_CIBLES, CIBLE_W, PUCE_H), false, accent);
        Dessin.puce(g, font, cibleB, x + CIBLE_W + 4, y + Y_CIBLES, CIBLE_W, PUCE_H, cible == 1,
                !uni && Dessin.dans(mx, my, x + CIBLE_W + 4, y + Y_CIBLES, CIBLE_W, PUCE_H), uni, accent);
        Ui.soft(g, x + 2 * CIBLE_W + 12, y + Y_CIBLES + 1, 10, 10, Ui.argb(cible == 0 ? b.couleurA : b.couleurB, 255));
        g.drawString(font, Dessin.DIESE, hexaX() - 7, hexaY() + 3, Ui.argb(Ui.FAINT, 255), false);
        Dessin.palette(g, x, y + Y_PALETTE, cible == 0 ? b.couleurA : b.couleurB, mx, my);
        Ui.section(g, font, style, x, y + Y_STYLE, w);
        int survole = -1;
        for (Banniere.Degrade d : Banniere.Degrade.values()) {
            int px = x + d.ordinal() * STYLE_PAS, py = y + Y_PASTILLES;
            boolean sel = b.degrade == d, survol = Dessin.dans(mx, my, px, py, STYLE_W, STYLE_H);
            if (survol) survole = d.ordinal();
            Dessin.apercuDegrade(g, d, b.couleurA, b.couleurB, px + 1, py + 1, STYLE_W - 2, STYLE_H - 2);
            Ui.softOutline(g, px + 1, py + 1, STYLE_W - 2, STYLE_H - 2, sel ? Ui.argb(accent, 255) : Ui.argb(0xFFFFFF, survol ? 120 : 40));
            if (sel) Ui.softOutline(g, px, py, STYLE_W, STYLE_H, Ui.argb(accent, 90));
        }
        String nom = styles[survole >= 0 ? survole : b.degrade.ordinal()];
        Ui.spaced(g, font, nom, Ui.SMALL, x, y + Y_NOM_STYLE, Ui.argb(survole >= 0 ? Ui.TEXT : Ui.MUTED, 255), 0.8f);
        g.drawString(font, aide1, x, y + Y_AIDE, Ui.argb(Ui.FAINT, 255), false);
        g.drawString(font, aide2, x, y + Y_AIDE + 10, Ui.argb(Ui.FAINT, 255), false);
    }

    public boolean clic(double mx, double my, int bouton) {
        if (bouton != 0 || !Dessin.dans(mx, my, x, y, w, Y_AIDE)) return false;
        Banniere b = at.banniere();
        boolean uni = b.degrade == Banniere.Degrade.UNI;
        if (Dessin.dans(mx, my, x, y + Y_CIBLES, CIBLE_W, PUCE_H)) {
            Ui.son(Sons.UI_CLIC, 0.5f);
            at.cible(0);
            return true;
        }
        if (!uni && Dessin.dans(mx, my, x + CIBLE_W + 4, y + Y_CIBLES, CIBLE_W, PUCE_H)) {
            Ui.son(Sons.UI_CLIC, 0.5f);
            at.cible(1);
            return true;
        }
        int p = Dessin.paletteSous(x, y + Y_PALETTE, mx, my);
        if (p >= 0) {
            Ui.son(Sons.UI_CLIC, 0.5f);
            at.appliquerCouleur(Dessin.PALETTE[p], Historique.AUCUN);
            return true;
        }
        for (Banniere.Degrade d : Banniere.Degrade.values()) {
            if (Dessin.dans(mx, my, x + d.ordinal() * STYLE_PAS, y + Y_PASTILLES, STYLE_W, STYLE_H)) {
                Ui.son(Sons.UI_CLIC, 0.5f);
                if (d != b.degrade) at.changer(b.avecDegrade(d), Historique.AUCUN);
                if (d == Banniere.Degrade.UNI) at.cible(0);
                return true;
            }
        }
        return true;
    }
}
