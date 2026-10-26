package fr.heraliz.crates.loot;

import fr.heraliz.crates.HeralizCrates;
import fr.heraliz.crates.crate.CrateType;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Caisse des emotes : chaque recompense debloque une emote de HeraliZ Emote ({@code emote debloquer {player} <id>}). Le
 * tirage ne garde que les emotes que le joueur n'a pas encore ; s'il les a toutes, la cle est refusee (pas consommee).
 * HeraliZ Emote est lu par reflexion ({@code fr.heraliz.emote.serveur.EmotesApi}) : sans lui, rien n'est filtre.
 */
public final class EmotesCaisse {
    /** Debut de la commande d'une recompense de la caisse des emotes. */
    public static final String COMMANDE = "emote debloquer {player} ";

    private static boolean cherche;
    @Nullable
    private static Method a, liste;

    private EmotesCaisse() {
    }

    /**
     * Recompenses permises pour ce joueur : jamais une emote qu'il a deja, dans toutes les caisses (26/10 : la caisse
     * Evenement donne aussi des emotes) ; les autres recompenses restent toutes permises.
     */
    public static Predicate<LootEntry> permis(ServerPlayer p, CrateType type) {
        if (!api()) return e -> true;
        return e -> {
            String id = emoteDe(e);
            if (id == null) return true;
            try {
                return !Boolean.TRUE.equals(a.invoke(null, p, id));
            } catch (ReflectiveOperationException | RuntimeException ex) {
                return true;
            }
        };
    }

    /** Id de l'emote que debloque cette recompense, ou null. */
    @Nullable
    public static String emoteDe(LootEntry e) {
        for (String c : e.commands) {
            String s = c.startsWith("/") ? c.substring(1) : c;
            if (s.startsWith(COMMANDE)) return s.substring(COMMANDE.length()).trim();
        }
        return null;
    }

    /** Toutes les emotes {id, nom} de HeraliZ Emote (vide sans lui) : butin par defaut de la caisse. */
    public static List<String[]> toutes() {
        if (!api() || liste == null) return List.of();
        try {
            @SuppressWarnings("unchecked")
            List<String[]> l = (List<String[]>) liste.invoke(null);
            return new ArrayList<>(l);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return List.of();
        }
    }

    private static boolean api() {
        if (!cherche) {
            cherche = true;
            try {
                Class<?> c = Class.forName("fr.heraliz.emote.serveur.EmotesApi");
                a = c.getMethod("a", ServerPlayer.class, String.class);
                liste = c.getMethod("liste");
            } catch (ReflectiveOperationException | LinkageError ex) {
                a = liste = null;
                HeralizCrates.LOGGER.warn("[HeralizCrates] HeraliZ Emote absent : la caisse des emotes ne filtre rien");
            }
        }
        return a != null;
    }
}
