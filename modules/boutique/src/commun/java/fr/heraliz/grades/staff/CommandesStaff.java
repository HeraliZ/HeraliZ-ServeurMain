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
