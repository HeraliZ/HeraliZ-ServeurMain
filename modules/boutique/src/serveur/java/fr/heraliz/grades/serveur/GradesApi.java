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
     * 9 cases, 0 = pas d'accès), {@code zones_vip} (0 ou 1) ; depuis le 22/10 : {@code tp_reduction} (pourcentage retiré
     * au délai du /home), {@code coffre_vip_cases} (cases du coffre VIP, 0 à 54, remplace {@code coffre_vip_lignes}),
     * {@code nick} et {@code chat_couleur} (0 ou 1). -1 pour une clé inconnue ou hors d'un serveur démarré.
     * Lue par réflexion par CRac (homes, recharges) et HeraliZ Utils (coffres personnels).
     */
    public static int perm(UUID joueur, String cle) {
        fr.heraliz.grades.PermsGrades.Perm p = fr.heraliz.grades.PermsGrades.parCle(cle);
        if (p == null || joueur == null || ServeurGrades.registre == null) return -1;
        return PermsServeur.valeur(joueur, p);
    }

    /**
     * Joueurs qui ont un grade du staff (05/11, wipe de la réouverture de HeraliZ Quêtes, par réflexion) : uuid → {identifiant
     * du grade du staff (« fondateur », « admin »...), dernier pseudo connu}, connectés ou non, tels qu'enregistrés dans
     * {@code heraliz_grades/grades.json}. null si les grades ne sont pas chargés ou si le fichier est illisible : on ne peut
     * alors pas savoir qui est du staff (l'appelant doit refuser ce qui en dépend).
     */
    public static java.util.Map<UUID, String[]> staff() {
        RegistreGrades r = ServeurGrades.registre;
        if (r == null || r.illisible()) return null;
        java.util.Map<UUID, String[]> m = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<UUID, RegistreGrades.Fiche> e : r.tous()) {
            Grade s = e.getValue().staff();
            if (s != null) m.put(e.getKey(), new String[]{s.id, e.getValue().nom()});
        }
        return m;
    }

    /** Identifiant du grade affiché du joueur (« traqueur », « helper »... ; « survivant » par défaut). */
    public static String gradeAffiche(UUID joueur) {
        RegistreGrades r = ServeurGrades.registre;
        return r == null || joueur == null ? Grade.SURVIVANT.id : r.affiche(joueur).id;
    }

    /**
     * Vrai si le joueur est caché par vanish du staff (2.7.0) : à ne pas montrer aux joueurs qui ne sont pas du staff (listes,
     * comptes de joueurs en ligne). Lisible par réflexion par nos autres mods.
     */
    public static boolean cache(UUID joueur) {
        return VanishServeur.cache(joueur);
    }

    /**
     * Vrai si le joueur est connecté sur AuthMe (serveur cracké ; toujours vrai sinon). AuthMe injoignable : faux. Sert aux
     * commandes réservées (par exemple /inspecter de HeraliZ Utils) : un pseudo d'op ne vaut rien avant le /login.
     */
    public static boolean authentifie(ServerPlayer joueur) {
        return joueur != null && PontBukkit.authentifie(joueur);
    }

    /**
     * Nom d'un objet tel que le joueur le voit, pour un message envoyé par le serveur (03/11, {@link fr.heraliz.noyau.api.NomsObjets}) : le vrai
     * nom d'une arme, d'une munition ou d'un accessoire de TaCZ (pas la clé « item.tacz.modern_kinetic_gun »), sinon le nom
     * donné ou le nom habituel. Lue par réflexion par HeraliZ Quêtes (messages des marchands, quêtes, échanges).
     */
    public static net.minecraft.network.chat.Component nomObjet(net.minecraft.world.item.ItemStack objet) {
        return fr.heraliz.noyau.api.NomsObjets.nom(objet);
    }
}
