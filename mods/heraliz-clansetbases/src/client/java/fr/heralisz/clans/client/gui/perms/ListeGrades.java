package fr.heralisz.clans.client.gui.perms;

import fr.heralisz.clans.client.PermsLocales;
import fr.heralisz.clans.client.gui.kit.Hote;
import fr.heralisz.clans.client.gui.kit.HzScroll;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.ClanSnapshot;
import fr.heralisz.clans.model.Grade;
import fr.heralisz.clans.model.Perm;
import fr.heralisz.clans.registry.Sons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Onglet Permissions › PAR GRADE : liste defilante (barre fine, molette) groupee CLAN · BASE · CLASHWAR, une ligne par
 * permission, trois colonnes HAUT GRADE · MEMBRE · RECRUE a droite (pastilles, accent = active). Le chef bascule une
 * pastille d'un clic (affichage immediat, envoi groupe par {@link PermsLocales}) ; les autres la voient en lecture
 * seule. Les en-tetes de colonnes sont dessines par l'onglet, au-dessus de la liste ({@link #colX}).
 */
public final class ListeGrades extends HzScroll {
    /** Largeur d'une colonne de grade. */
    public static final int COL_W = 76;
    private final Hote hote;
    private final ClanSnapshot s;
    private final boolean modifiable;
    private final int couleur;
    private final Font font = Minecraft.getInstance().font;
    private final List<PermsUi.Ligne> lignes = PermsUi.lignes(false);
    private final Component[] libelles = new Component[Perm.values().length];
    private final int[] masques = new int[3];
    private final List<Component> infoLecture;

    public ListeGrades(Hote hote, ClanSnapshot s, int x, int y, int w, int h) {
        super(x, y, w, h);
        this.hote = hote;
        this.s = s;
        this.modifiable = s.jeSuisChef();
        this.couleur = s.accent();
        accent(couleur);
        for (Perm p : Perm.values()) libelles[p.ordinal()] = Ui.tr(Ui.FONT, p.libelle());
        infoLecture = List.of(Ui.tr(Ui.FONT, "gui.permissions.lecture_seule_grades"));
        setContentHeight(PermsUi.hauteur(lignes));
    }

    /** Centre (x absolu) de la colonne du grade i (0 haut grade, 1 membre, 2 recrue). */
    public int colX(int i) {
        return getX() + getWidth() - 8 - (2 - i) * COL_W - COL_W / 2;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Grade[] grades = Grade.modifiables();
        for (int i = 0; i < grades.length; i++) masques[i] = PermsLocales.masqueGrade(s, grades[i]);
        int haut = getY() + getScroll(), bas = haut + getHeight(), lw = getWidth() - 4;
        boolean survolPastille = false;
        for (PermsUi.Ligne l : lignes) {
            int ly = getY() + l.y();
            if (ly + l.h() < haut || ly > bas) continue;
            if (l.perm() == null) {
                PermsUi.titre(g, font, l.groupe(), getX() + 2, ly, lw - 4);
                continue;
            }
            Perm p = l.perm();
            if (PermsUi.dans(mouseX, mouseY, getX(), ly, lw, l.h())) Ui.soft(g, getX(), ly, lw, l.h(), Ui.argb(0xFFFFFF, 8));
            g.drawString(font, libelles[p.ordinal()], getX() + 6, ly + 3, Ui.argb(Ui.TEXT, 230), false);
            for (int i = 0; i < grades.length; i++) {
                int cx = colX(i);
                boolean on = (masques[i] & p.bit()) != 0;
                boolean hover = PermsUi.dans(mouseX, mouseY, cx - 10, ly, 20, l.h());
                Ui.pastille(g, cx - PermsUi.PASTILLE / 2, ly + 2, PermsUi.PASTILLE, couleur, on, hover && modifiable);
                survolPastille |= hover;
            }
        }
        if (survolPastille && !modifiable) hote.infobulle(infoLecture);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !dansZone(mx, my)) return false;
        double cy = my + getScroll();
        Grade[] grades = Grade.modifiables();
        for (PermsUi.Ligne l : lignes) {
            if (l.perm() == null) continue;
            int ly = getY() + l.y();
            if (cy < ly || cy >= ly + l.h()) continue;
            for (int i = 0; i < grades.length; i++) {
                int cx = colX(i);
                if (mx >= cx - 10 && mx < cx + 10) {
                    if (!modifiable) return true;
                    PermsLocales.basculerGrade(s, grades[i], l.perm());
                    Ui.son(Sons.UI_CLIC, 0.5f);
                    return true;
                }
            }
            return true;
        }
        return true;
    }

    // ------------------------------------------------------------------ banc d'essai
    /** Coordonnees d'interface du centre de la pastille (perm, grade i), apres defilement pour la rendre visible. */
    public double[] centrePastille(Perm p, int i) {
        for (PermsUi.Ligne l : lignes) {
            if (l.perm() != p) continue;
            montrer(l.y(), l.h());
            return new double[]{colX(i), getY() + l.y() - getScroll() + l.h() / 2.0};
        }
        return null;
    }

    private void montrer(int y, int h) {
        if (y < getScroll()) setScroll(y);
        else if (y + h > getScroll() + getHeight()) setScroll(y + h - getHeight());
    }
}
