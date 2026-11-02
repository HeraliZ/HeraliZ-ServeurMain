package fr.heraliz.noyau.api;

import net.minecraft.commands.CommandSourceStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Rechargements des modules, joués par {@code /recharger} (02/11, audit point 36). Chaque module y inscrit la relecture de
 * ses fichiers (quêtes, caisses, casino, raretés, tables de loot, saison, territoires...) au lieu d'avoir sa propre
 * sous-commande « recharger » : {@code /recharger} les joue toutes, {@code /recharger <nom>} une seule.
 * <p>Sans dépendance de compilation : un module qui ne compile pas avec le noyau l'appelle par réflexion avec un type de
 * Java ({@link ToIntFunction}), par exemple
 * {@code Class.forName("fr.heraliz.noyau.api.Rechargements").getMethod("ajouter", String.class, ToIntFunction.class)}.
 * Lu par réflexion : ne jamais renommer la classe ni {@link #ajouter}.</p>
 */
public final class Rechargements {
    private Rechargements() {
    }

    private static final Map<String, ToIntFunction<CommandSourceStack>> TOUS = new LinkedHashMap<>();

    /**
     * Inscrit (ou remplace, même nom) le rechargement d'un module. {@code nom} : un mot court, en minuscules (« quetes »,
     * « caisses »...), tapé après {@code /recharger}. {@code action} : relit les fichiers et répond à {@code source} ; rend
     * plus que 0 si le rechargement est fait. Appelé à l'enregistrement des commandes (fil du serveur).
     */
    public static synchronized void ajouter(String nom, ToIntFunction<CommandSourceStack> action) {
        if (nom == null || action == null) return;
        String n = nom.trim().toLowerCase(Locale.ROOT);
        if (!n.isEmpty()) TOUS.put(n, action);
    }

    /** Noms inscrits, dans l'ordre d'inscription. */
    public static synchronized List<String> noms() {
        return new ArrayList<>(TOUS.keySet());
    }

    /** Le rechargement inscrit sous ce nom, ou null. */
    public static synchronized ToIntFunction<CommandSourceStack> action(String nom) {
        return nom == null ? null : TOUS.get(nom.trim().toLowerCase(Locale.ROOT));
    }
}
