package fr.heraliz.grades.staff;

import java.util.List;

/**
 * Onglet « Économie » du panel du staff (03/11, demande du propriétaire : « donner / retirer des sous, des clés, des points
 * de clan, des PB ») : ce que vise chaque action ({@link ActionStaff} de l'onglet {@link ActionStaff.Onglet#ECONOMIE}), son
 * plus gros montant par opération et les montants proposés en un clic. Commun aux deux côtés : le joueur n'y lit que la
 * mise en page ; le serveur ({@code PanelEconomie}) revérifie tout (Fondateur, bornes, cible) et fait l'opération.
 * <p>
 * Ajouter une opération (le « 5/ » du propriétaire) : une action {@link ActionStaff} à la suite de l'onglet Économie (verrou
 * Fondateur), une ligne ici, son cas dans {@code PanelEconomie} (voir, donner, retirer) et ses textes
 * ({@code heraliz_grades.staff.action.<id>}, {@code .aide}, {@code gui.heraliz_grades.staff.eco.unite.<id>}) en français,
 * anglais et russe.
 */
public enum OperationEco {
    /** Hérals du joueur : comme {@code /eco donner|retirer} (plafond du solde, journal_admin.log). */
    HERALS(ActionStaff.ECO_HERALS, false, 10_000_000L, List.of(1_000L, 10_000L, 100_000L, 1_000_000L)),
    /** Clés de caisse sur le compte de clés du joueur (une sorte de clé par opération). */
    CLES(ActionStaff.ECO_CLES, false, 1_000L, List.of(1L, 5L, 10L, 50L)),
    /** Points Boutique du compte du site : 20 000 au plus par opération (plafond de la clé des PB du site). */
    PB(ActionStaff.ECO_PB, false, 20_000L, List.of(100L, 1_000L, 5_000L, 20_000L)),
    /** Points de saison du clan, dans une catégorie (PVE, PVP, événements), notés au journal de la saison. */
    SAISON(ActionStaff.ECO_SAISON, true, 100_000L, List.of(10L, 50L, 100L, 500L)),
    /** XP du clan (donnée : le clan peut monter de niveau ; retirée : dans le niveau en cours seulement). */
    XP_CLAN(ActionStaff.ECO_XP_CLAN, true, 10_000_000L, List.of(1_000L, 10_000L, 50_000L, 100_000L));

    public final ActionStaff action;
    /** Vise un clan (tag) ; sinon un joueur (pseudo). */
    public final boolean clan;
    /** Plus gros montant d'une opération. */
    public final long max;
    /** Montants proposés en un clic. */
    public final List<Long> rapides;

    OperationEco(ActionStaff action, boolean clan, long max, List<Long> rapides) {
        this.action = action;
        this.clan = clan;
        this.max = max;
        this.rapides = rapides;
    }

    /** L'opération de cette action, ou null (pas une action de l'onglet Économie). */
    public static OperationEco de(ActionStaff a) {
        for (OperationEco o : values()) if (o.action == a) return o;
        return null;
    }

    /** Clé du texte de l'unité ({@code gui.heraliz_grades.staff.eco.unite.<…>}) pour cette sorte ({@code option}). */
    public String cleUnite(String option) {
        String base = "gui.heraliz_grades.staff.eco.unite.";
        return switch (this) {
            case HERALS -> base + "herals";
            case CLES -> base + "cles." + option;
            case PB -> base + "pb";
            case SAISON -> base + "saison." + option;
            case XP_CLAN -> base + "xp";
        };
    }

    /** Sens de l'opération demandée au serveur. */
    public static final int VOIR = 0, DONNER = 1, RETIRER = 2;
}
