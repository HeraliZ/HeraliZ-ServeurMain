package fr.heraliz.quetes;

import java.util.function.Supplier;

/**
 * Langue du jeu qui affiche (code Minecraft : « fr_fr », « en_us », « ru_ru »). Le code commun (nom des objets de quête,
 * nom des PNJ) la lit ici ; la partie client y branche la langue choisie par le joueur. Sur le serveur : français.
 */
public final class Langue {
    private Langue() {
    }

    private static volatile Supplier<String> courante = () -> "fr_fr";

    public static void brancher(Supplier<String> s) {
        courante = s;
    }

    public static String code() {
        try {
            String c = courante.get();
            return c == null ? "fr_fr" : c;
        } catch (RuntimeException ex) {
            return "fr_fr";
        }
    }
}
