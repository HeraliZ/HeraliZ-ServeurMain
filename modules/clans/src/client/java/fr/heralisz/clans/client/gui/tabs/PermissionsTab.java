package fr.heralisz.clans.client.gui.tabs;

import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.client.gui.perms.EditeurJoueurs;
import fr.heralisz.clans.client.gui.perms.ListeGrades;
import fr.heralisz.clans.client.gui.perms.PermsUi;
import fr.heralisz.clans.model.Grade;
import fr.heralisz.clans.registry.Sons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * Permissions (chef, et quiconque peut GERER_PERMISSIONS) : bascule PAR GRADE / PAR JOUEUR en haut a gauche,
 * indication discrete a droite.
 * <ul>
 * <li>PAR GRADE : colonnes HAUT GRADE · MEMBRE · RECRUE, liste defilante groupee CLAN · BASE · CLASHWAR
 * ({@link ListeGrades}) ; modifiable par le chef seul, lecture seule pour les autres ;</li>
 * <li>PAR JOUEUR : editeur des reglages individuels ({@link EditeurJoueurs}).</li>
 * </ul>
 * Le mode choisi est garde pour la session (le chef arrive sur PAR GRADE, un haut grade sur PAR JOUEUR).
 */
public final class PermissionsTab extends Tab {
    public enum Mode { GRADE, JOUEUR }

    /** Mode courant (null = defaut selon le grade, a la premiere ouverture). */
    @Nullable
    private static Mode mode;
    private String[] libelles;
    private int[] largeurs;
    @Nullable
    private ListeGrades grades;
    @Nullable
    private EditeurJoueurs editeur;

    @Override
    protected void construire() {
        if (mode == null) mode = s.jeSuisChef() ? Mode.GRADE : Mode.JOUEUR;
        libelles = new String[]{tr("gui.permissions.par_grade"), tr("gui.permissions.par_joueur")};
        largeurs = PermsUi.largeurs(font, libelles);
        if (mode == Mode.GRADE) {
            grades = ecran.ajouter(new ListeGrades(ecran, s, x, y + 34, w, h - 34));
        } else {
            editeur = new EditeurJoueurs(ecran, s, x, y + 20, w, h - 20, false);
        }
    }

    public Mode mode() {
        return mode;
    }

    @Nullable
    public EditeurJoueurs editeur() {
        return editeur;
    }

    @Nullable
    public ListeGrades grades() {
        return grades;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int survol = PermsUi.segmentSous(mouseX, mouseY, x, y, largeurs);
        PermsUi.bascule(g, font, x, y, libelles, largeurs, mode.ordinal(), survol == mode.ordinal() ? -1 : survol, accent);
        // indication a droite de la bascule
        if (mode == Mode.GRADE) {
            if (s.jeSuisChef()) {
                Ui.right(g, font, Ui.tr(Ui.SMALL, "gui.permissions.pied"), x + w, y + 3, Ui.argb(Ui.FAINT, 255));
            } else {
                Component t = Ui.tr(Ui.SMALL, "gui.permissions.lecture_seule_grades");
                int tw = font.width(t);
                PermsUi.cadenas(g, x + w - tw - 11, y + 2, Ui.FAINT, 220);
                Ui.right(g, font, t, x + w, y + 3, Ui.argb(Ui.FAINT, 255));
            }
            // titres des colonnes, puis la liste (widget)
            Grade[] gs = Grade.modifiables();
            for (int i = 0; i < gs.length && grades != null; i++) {
                Ui.spacedCentered(g, font, tr(gs[i].libelle()), Ui.SMALL, grades.colX(i), y + 22, Ui.argb(couleurGrade(gs[i]), 255), 0.8f);
            }
            Ui.hairline(g, x, x + w, y + 31, 0xFFFFFF, 40, false, true);
        } else {
            Ui.right(g, font, Ui.tr(Ui.SMALL, "gui.permissions.astuce_joueur"), x + w, y + 3, Ui.argb(Ui.FAINT, 255));
            if (editeur != null) editeur.render(g, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        int seg = PermsUi.segmentSous(mx, my, x, y, largeurs);
        if (seg < 0) return false;
        if (seg != mode.ordinal()) {
            mode = Mode.values()[seg];
            Ui.son(Sons.UI_CLIC, 0.5f);
            ecran.reconstruireOnglet();
        }
        return true;
    }

    /** Banc d'essai : centre du segment de la bascule (coordonnees d'interface). */
    public double[] centreBascule(Mode m) {
        int sx = x;
        for (int i = 0; i < m.ordinal(); i++) sx += largeurs[i];
        return new double[]{sx + largeurs[m.ordinal()] / 2.0, y + PermsUi.H_BASCULE / 2.0};
    }
}
