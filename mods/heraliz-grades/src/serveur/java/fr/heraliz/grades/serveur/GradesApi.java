package fr.heraliz.grades.serveur;

import fr.heraliz.grades.Grade;

import java.util.UUID;

/**
 * API des grades pour nos autres mods (côté serveur seulement ; appelée par réflexion, par exemple par la zone VIP du
 * casino). Ne jamais renommer cette classe ni changer ses signatures. Fil serveur seulement.
 */
public final class GradesApi {
    private GradesApi() {
    }

    /**
     * Rang du grade de la boutique du joueur : 0 aucun (Survivant), 1 Rescapé, 2 Traqueur, 3 Roi de la Zone. 0 aussi
     * hors d'un serveur démarré ou si le fichier des grades est illisible.
     */
    public static int rangBoutique(UUID joueur) {
        RegistreGrades r = ServeurGrades.registre;
        if (r == null || joueur == null) return 0;
        RegistreGrades.Fiche f = r.fiche(joueur);
        return f == null || f.boutique() == null ? 0 : f.boutique().rang;
    }

    /** Identifiant du grade affiché du joueur (« traqueur », « helper »... ; « survivant » par défaut). */
    public static String gradeAffiche(UUID joueur) {
        RegistreGrades r = ServeurGrades.registre;
        return r == null || joueur == null ? Grade.SURVIVANT.id : r.affiche(joueur).id;
    }
}
