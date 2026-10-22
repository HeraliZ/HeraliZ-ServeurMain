package fr.heraliz.grades.serveur;

import fr.heraliz.grades.Grade;
import net.minecraft.server.level.ServerPlayer;

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

    /**
     * Valeur d'une permission de grade pour ce joueur (2.0.0, réglée dans l'éditeur des grades) : le meilleur réglage de
     * ses grades, sinon celui de Survivant. Clés : {@code tpa_recharge}, {@code tpa_delai}, {@code home_recharge},
     * {@code rtp_recharge} (secondes), {@code homes} (nombre), {@code planque_lignes}, {@code coffre_vip_lignes} (lignes de
     * 9 cases, 0 = pas d'accès), {@code zones_vip} (0 ou 1). -1 pour une clé inconnue ou hors d'un serveur démarré.
     * Lue par réflexion par CRac (homes, recharges) et HeraliZ Utils (coffres personnels).
     */
    public static int perm(UUID joueur, String cle) {
        fr.heraliz.grades.PermsGrades.Perm p = fr.heraliz.grades.PermsGrades.parCle(cle);
        if (p == null || joueur == null || ServeurGrades.registre == null) return -1;
        return PermsServeur.valeur(joueur, p);
    }

    /** Identifiant du grade affiché du joueur (« traqueur », « helper »... ; « survivant » par défaut). */
    public static String gradeAffiche(UUID joueur) {
        RegistreGrades r = ServeurGrades.registre;
        return r == null || joueur == null ? Grade.SURVIVANT.id : r.affiche(joueur).id;
    }

    /**
     * Vrai si le joueur est connecté sur AuthMe (serveur cracké ; toujours vrai sinon). AuthMe injoignable : faux. Sert aux
     * commandes réservées (par exemple /inspecter de HeraliZ Utils) : un pseudo d'op ne vaut rien avant le /login.
     */
    public static boolean authentifie(ServerPlayer joueur) {
        return joueur != null && PontBukkit.authentifie(joueur);
    }
}
