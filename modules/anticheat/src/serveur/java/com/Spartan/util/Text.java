package com.Spartan.util;

/**
 * Neutralisation du texte venant du client (pseudos, détails de signalement,
 * noms de packs…) avant de l'afficher dans la console, les logs, le chat ou
 * Discord.
 *
 * Sans ça, un joueur pourrait :
 * - injecter des codes ANSI dans la console web Pterodactyl ou latest.log ;
 * - fausser une ligne de log avec un retour à la ligne ;
 * - poser un faux lien Discord [texte](http://piège) ou un @everyone dans une
 *   alerte lue par le staff.
 */
public final class Text {
    private static final int MAX = 200;

    private Text() {
    }

    /** Pour la console, les logs et le chat en jeu : une seule ligne, sans caractères de contrôle. */
    public static String oneLine(String in) {
        if (in == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(Math.min(in.length(), MAX));
        in.codePoints().limit(MAX).forEach(cp -> {
            // Retire les caractères de contrôle (ESC, retours à la ligne, etc.). appendCodePoint :
            // un émoji (hors du plan de base) ne doit pas être coupé en deux.
            if (Character.getType(cp) == Character.CONTROL) {
                sb.append(' ');
            } else {
                sb.appendCodePoint(cp);
            }
        });
        return sb.toString().trim();
    }

    /** Pour Discord : une ligne, plus neutralisation du Markdown et des mentions. */
    public static String discord(String in) {
        String s = oneLine(in);
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // Échappe ce qui crée liens masqués, formatage ou mentions.
            if (c == '[' || c == ']' || c == '(' || c == ')' || c == '`'
                    || c == '*' || c == '_' || c == '~' || c == '>' || c == '|' || c == '\\') {
                sb.append('\\');
            }
            // Casse @everyone / @here / <@id> sans perdre l'information.
            sb.append(c == '@' ? "@​" : c);
        }
        return sb.toString();
    }
}
