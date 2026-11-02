package fr.heraliz.grades.staff;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Onglet « Commandes du Fondateur » du panel du staff (28/10) : règles communes aux deux côtés. Le Fondateur peut donner
 * à un grade du staff (Guide, Helpeur, Modérateur, Admin) le droit d'utiliser UNE commande précise (sa racine : {@code tp},
 * {@code ban}, {@code eco}…), sans donner les autres. Le serveur décide de tout ({@code CommandesGrades}) ; le joueur n'y
 * lit que la liste des commandes interdites (cadenas dans l'onglet).
 * <p>
 * Jamais donnables : les commandes qui donnent TOUS les droits d'un coup ou arrêtent le serveur (op, execute, function,
 * stop, reload, gestion des permissions…), et celles qui ont déjà leurs propres règles (panel, grades : console).
 */
public final class CommandesStaff {
    private CommandesStaff() {
    }

    /**
     * Racine d'une commande : lettres, chiffres, « _ », « - », « . » (32 caractères au plus), avec un « / » devant pour les
     * commandes de WorldEdit ({@code //set} s'appelle {@code /set} dans l'arbre des commandes).
     */
    private static final Pattern RACINE = Pattern.compile("/?[a-z0-9_.\\-]{1,31}");

    /** Commandes jamais donnables à un grade (cadenas dans l'onglet ; refusées par le serveur). */
    public static final Set<String> INTERDITES = Set.of(
            // tous les droits d'un coup ou arrêt du serveur
            "op", "deop", "stop", "restart", "execute", "function", "reload", "rl", "datapack", "debug", "jfr", "perf",
            "save-off", "save-on", "forge", "mohist", "bukkit", "plugins", "pl", "version", "ver", "about", "icanhasbukkit",
            "whitelist", "ban-ip", "pardon-ip", "setidletimeout", "publish", "sudo",
            // gestion des permissions (LuckPerms) et des comptes (AuthMe)
            "lp", "luckperms", "perm", "perms", "permission", "permissions", "authme", "email", "changepassword", "unregister",
            // nos commandes qui ont leurs propres règles
            "staff", "grade", "grades", "wipe", "spartan", "recharger", "redemarrage", "diag", "litebans");

    /**
     * Parties réservées à l'admin (02/11, audit point 10, décision : une commande donnée à un grade ne donne PAS ses
     * sous-commandes d'admin) : jamais données avec leur commande, même si elles demandent le même niveau qu'elle. Ce sont
     * celles qui créent de l'argent, des clés, des objets ou des récompenses, ou qui effacent un clan. Le serveur relit
     * aussi la commande donnée au niveau 2 (ou au niveau de sa racine s'il est plus haut) : les sous-commandes d'un niveau
     * supérieur (op 4 : {@code /eco donner}...) restent fermées. Mots séparés par une espace, racine d'abord.
     */
    public static final Set<String> SOUS_COMMANDES_ADMIN = Set.of(
            "clan admin", "eco donner", "eco retirer", "eco definir", "heraliz givekey", "heraliz keyall", "heraliz cles",
            "heraliz givecrate", "casino jetons", "casino compte", "casino jackpot", "quete donner", "quete finir",
            "saison points", "emote debloquer");

    /** La première des {@link #SOUS_COMMANDES_ADMIN} par laquelle commence ce chemin de mots (racine d'abord), ou null. */
    public static String sousCommandeAdmin(java.util.List<String> mots) {
        StringBuilder b = new StringBuilder();
        for (String m : mots) {
            if (b.length() > 0) b.append(' ');
            b.append(racine(m).isEmpty() ? m.toLowerCase(Locale.ROOT) : racine(m));
            if (SOUS_COMMANDES_ADMIN.contains(b.toString())) return b.toString();
        }
        return null;
    }

    /**
     * Racine normalisée d'un nom de l'arbre des commandes ou d'une commande lue par le serveur (SANS le « / » du chat) :
     * premier mot, minuscules, sans espace de noms ({@code minecraft:tp} -> {@code tp}) ; chaîne vide si invalide.
     */
    public static String racine(String brut) {
        if (brut == null) return "";
        String b = brut.strip();
        int sp = b.indexOf(' ');
        String r = (sp < 0 ? b : b.substring(0, sp)).toLowerCase(Locale.ROOT);
        int deux = r.indexOf(':');
        if (deux >= 0) r = r.substring(deux + 1);
        return RACINE.matcher(r).matches() ? r : "";
    }

    /** Vrai si cette racine (déjà normalisée) peut être donnée à un grade. */
    public static boolean donnable(String racine) {
        return racine != null && RACINE.matcher(racine).matches() && !INTERDITES.contains(racine);
    }
}
