package fr.heraliz.grades.staff;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogue des actions du panel du staff (touche P, 25/10, maquettes {@code maquettes/staff_chat/1_} à {@code 3_}
 * validées). Commun aux deux côtés : le joueur n'y lit que les noms et la mise en page (textes
 * {@code heraliz_grades.staff.action.<id>} et {@code .aide}) ; le serveur décide de tout (droits par grade réglés par le
 * Fondateur, {@code PermsStaff}, puis exécution, {@code PanelStaff}). Les identifiants sont gardés dans
 * {@code heraliz_grades/panel_staff.json} : ne jamais en changer un, en ajouter à la fin de leur onglet.
 * <p>
 * Rangs du staff : 1 Guide, 2 Helpeur, 3 Modérateur, 4 Admin, 5 Fondateur ({@code Grade}). {@link #rangDefaut} : plus bas
 * rang qui a l'action tant que le Fondateur n'a rien changé.
 */
public enum ActionStaff {
    // ---------- sur le joueur choisi (onglet Joueurs)
    AVERTIR("avertir", Onglet.JOUEURS, "moderation", true, 2, false, false, null, true),
    MUET_COURT("muet_court", Onglet.JOUEURS, "moderation", true, 2, false, false, List.of("15m", "1h"), true),
    MUET_LONG("muet_long", Onglet.JOUEURS, "moderation", true, 3, false, false, List.of("1d", "7d", "def"), true),
    EXPULSER("expulser", Onglet.JOUEURS, "moderation", true, 2, false, false, null, true),
    BAN_TEMP("ban_temp", Onglet.JOUEURS, "moderation", true, 3, true, false, List.of("1d", "7d", "30d"), true),
    BAN_PC("ban_pc", Onglet.JOUEURS, "moderation", true, 4, true, false, List.of("7d", "30d", "def"), true),
    MESSAGE("message", Onglet.JOUEURS, "aide", true, 1, false, false, null, true),
    ALLER_VOIR("aller_voir", Onglet.JOUEURS, "aide", true, 2, false, false, null, false),
    FAIRE_VENIR("faire_venir", Onglet.JOUEURS, "aide", true, 3, false, false, null, false),
    LIBERER("liberer", Onglet.JOUEURS, "aide", true, 2, false, false, null, false),
    INVENTAIRE_VOIR("inventaire_voir", Onglet.JOUEURS, "aide", true, 3, false, false, null, false),
    INVENTAIRE_MODIFIER("inventaire_modifier", Onglet.JOUEURS, "aide", true, 4, false, false, null, false),
    ANALYSER_JOUEUR("analyser_joueur", Onglet.JOUEURS, "aide", true, 4, false, false, null, false),
    /** Donner ou retirer un grade : Fondateur seulement, jamais changé dans les permissions (choix du propriétaire, 25/10). */
    DONNER_GRADE("donner_grade", Onglet.JOUEURS, "aide", true, 5, true, true, null, false),
    // ---------- modération
    VIDER_CHAT("vider_chat", Onglet.MODERATION, "chat", false, 3, false, false, null, false),
    SE_CACHER("se_cacher", Onglet.MODERATION, "moi", false, 2, false, false, null, false),
    CACHER_NOM("cacher_nom", Onglet.MODERATION, "moi", false, 4, false, false, null, false),
    SPECTATEUR("spectateur", Onglet.MODERATION, "moi", false, 3, false, false, null, false),
    // ---------- monde
    JOUR("jour", Onglet.MONDE, "heure", false, 4, false, false, null, false),
    NUIT("nuit", Onglet.MONDE, "heure", false, 4, false, false, null, false),
    METEO_CLAIRE("meteo_claire", Onglet.MONDE, "meteo", false, 4, false, false, null, false),
    METEO_PLUIE("meteo_pluie", Onglet.MONDE, "meteo", false, 4, false, false, null, false),
    EXTRACTION_VAGUE("extraction_vague", Onglet.MONDE, "extractions", false, 4, false, false, null, false),
    // ---------- événements
    LARGAGE("largage", Onglet.EVENEMENTS, "largages", false, 4, false, false, null, false),
    FRAPPE("frappe", Onglet.EVENEMENTS, "largages", false, 4, true, false, List.of("smoke", "bombing", "napalm", "gas"), false),
    BOSS_EVEIL("boss_eveil", Onglet.EVENEMENTS, "boss", false, 4, true, false, List.of("mammouth", "mere", "n07", "peste", "sylvorn"), false),
    BOSS_RESET("boss_reset", Onglet.EVENEMENTS, "boss", false, 4, true, false, List.of("mammouth", "mere", "n07", "peste", "sylvorn"), false),
    // ---------- serveur
    REDEMARRAGE("redemarrage", Onglet.SERVEUR, "serveur", false, 4, true, false, List.of("5", "10", "15"), false),
    RECHARGER("recharger", Onglet.SERVEUR, "serveur", false, 4, false, false, null, false),
    ANNONCE("annonce", Onglet.SERVEUR, "serveur", false, 4, false, false, null, true),
    ETAT("etat", Onglet.SERVEUR, "performances", false, 3, false, false, null, false),
    ANALYSER("analyser", Onglet.SERVEUR, "performances", false, 4, false, false, null, false),
    NETTOYER_SOL("nettoyer_sol", Onglet.SERVEUR, "nettoyage", false, 3, false, false, null, false),
    NETTOYER_CORPS("nettoyer_corps", Onglet.SERVEUR, "nettoyage", false, 4, true, false, null, false),
    NETTOYER_ZOMBIES("nettoyer_zombies", Onglet.SERVEUR, "nettoyage", false, 3, false, false, null, false);

    /** Onglets du panel, dans l'ordre (Historique et Permissions viennent après, sans actions). */
    public enum Onglet { JOUEURS, MODERATION, MONDE, EVENEMENTS, SERVEUR }

    /** Rangs du staff : 1 Guide … 5 Fondateur. */
    public static final int RANG_MAX = 5;

    public final String id;
    public final Onglet onglet;
    /** Section dans son onglet (texte {@code heraliz_grades.staff.section.<section>}). */
    public final String section;
    /** Faite sur le joueur choisi dans la liste. */
    public final boolean cible;
    public final int rangDefaut;
    /** Deux clics (le second confirme dans les 5 s). */
    public final boolean danger;
    /** Fondateur seulement, jamais réglable dans l'onglet Permissions. */
    public final boolean verrou;
    /** Choix proposés (durées, types, boss…), le premier par défaut ; null : aucun. */
    public final List<String> options;
    /** Demande un texte (raison, message, annonce). */
    public final boolean texte;

    ActionStaff(String id, Onglet onglet, String section, boolean cible, int rangDefaut, boolean danger, boolean verrou,
                List<String> options, boolean texte) {
        this.id = id;
        this.onglet = onglet;
        this.section = section;
        this.cible = cible;
        this.rangDefaut = rangDefaut;
        this.danger = danger;
        this.verrou = verrou;
        this.options = options;
        this.texte = texte;
    }

    private static final Map<String, ActionStaff> PAR_ID = new HashMap<>();

    static {
        for (ActionStaff a : values()) PAR_ID.put(a.id, a);
    }

    public static ActionStaff parId(String id) {
        return id == null ? null : PAR_ID.get(id);
    }

    /** Masque par défaut : un bit par rang (bit r = rang r), du rang par défaut jusqu'au Fondateur. */
    public int masqueDefaut() {
        int m = 0;
        for (int r = rangDefaut; r <= RANG_MAX; r++) m |= 1 << r;
        return m;
    }

    /** Sanction notée dans la fiche du joueur (nombre de sanctions). */
    public boolean sanction() {
        return this == AVERTIR || this == MUET_COURT || this == MUET_LONG || this == EXPULSER || this == BAN_TEMP || this == BAN_PC;
    }
}
