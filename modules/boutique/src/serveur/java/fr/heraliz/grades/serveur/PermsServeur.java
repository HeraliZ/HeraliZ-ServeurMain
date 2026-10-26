package fr.heraliz.grades.serveur;

import fr.heraliz.grades.Grade;
import fr.heraliz.grades.PermsGrades;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Permissions d'un joueur (2.0.0) : la valeur de Survivant, améliorée par chacun de ses grades qui règle cette permission
 * (staff, boutique, spéciaux, Soutien s'il porte le tag) ; le meilleur réglage l'emporte ({@link PermsGrades.Perm#meilleur}).
 */
final class PermsServeur {
    private PermsServeur() {
    }

    /** Tous les grades du joueur, Survivant compris. */
    static List<Grade> grades(UUID joueur) {
        List<Grade> l = new ArrayList<>();
        l.add(Grade.SURVIVANT);
        RegistreGrades r = ServeurGrades.registre;
        RegistreGrades.Fiche f = r == null ? null : r.fiche(joueur);
        if (f != null) l.addAll(f.grades());
        if (LienDiscord.estSoutien(joueur)) l.add(Grade.SOUTIEN);
        return l;
    }

    static int valeur(UUID joueur, PermsGrades.Perm p) {
        Integer v = null;
        for (Grade g : grades(joueur)) {
            Integer x = g.perms.get(p.cle());
            if (x != null) v = v == null ? x : p.meilleur(v, x);
        }
        return p.borner(v == null ? p.defaut() : v);
    }
}
