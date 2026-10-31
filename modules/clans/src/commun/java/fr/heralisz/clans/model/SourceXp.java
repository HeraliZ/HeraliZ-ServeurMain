package fr.heralisz.clans.model;

import java.util.Locale;

/**
 * Sources de l'XP de clan (31/10, demande du proprietaire : « niveaux beaucoup trop faciles, des le debut »). Chaque source
 * a son plafond par clan et par jour (heure de Paris), regle dans {@code config/heralisz_clans/niveaux.json}
 * ({@code plafonds_jour}) : on ne monte plus de niveau en repetant une seule activite. Lu des deux cotes : le serveur
 * applique les plafonds, l'onglet Niveau du menu K montre le gain, le plafond et ce que le clan a deja gagne aujourd'hui
 * (tableaux envoyes dans l'ordre de cette enum : ne jamais retirer ni reordonner, ajouter en dernier).
 */
public enum SourceXp {
    /** Quetes de clan (deja limitees par le tirage : 3 par jour, 2 par semaine). */
    QUETE("quetes"),
    /** Capture d'une zone (ClansConfig [xp] capture). */
    CAPTURE("capture"),
    /** Zones tenues : XP par heure et par point de valeur de la zone (ClansConfig xp_heure_valeur). */
    TENUE("tenir"),
    /** Signal de ravitaillement lance. */
    RAVITAILLEMENT("ravitaillement"),
    /** Joueur tue (anti-boost des clans). */
    JOUEUR("joueur"),
    /** Zombie ou autre monstre tue. */
    ZOMBIE("zombie"),
    /** Boss tue. */
    BOSS("boss"),
    /** Objet de valeur donne au clan (/clan don). */
    DON("don"),
    /** Commande du staff (/clan admin xp) : jamais plafonnee. */
    ADMIN("admin");

    /** Fin de la cle de traduction du libelle : {@code gui.niveau.xp.<cle>}. */
    public final String cle;

    SourceXp(String cle) {
        this.cle = cle;
    }

    /** Identifiant dans niveaux.json (« zombie », « capture »...). */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String libelle() {
        return "gui.niveau.xp." + cle;
    }

    public static SourceXp parId(String s) {
        for (SourceXp x : values()) if (x.id().equalsIgnoreCase(s)) return x;
        return null;
    }
}
