package fr.heraliz.noyau.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * Bots de charge (module « banc », installé seulement pour les tests) : ils portent l'étiquette d'entité {@link #TAG},
 * lue par plusieurs mods (anticheat, saison, cinématique, peuplement des zombies) pour les traiter à part.
 * <p>Sécurité : cette étiquette exempte de l'anticheat. Seul un vrai bot la garde : le noyau la retire à tout autre joueur
 * à la connexion et chaque seconde ; les pseudos {@code HzBot001} à {@code HzBot200} sont réservés. Le module banc dit
 * qui est un vrai bot avec {@link #brancher}. Ne jamais renommer cette classe ni l'étiquette.
 */
public final class Bots {
    private Bots() {}

    public static final String TAG = "heraliz_bot";
    /** Pseudos réservés aux bots de charge. */
    public static final String PSEUDOS = "(?i)HzBot\\d{3}";

    private static volatile Predicate<ServerPlayer> vraiBot = p -> false;

    /** Réservé au module banc : reconnaît ses bots. */
    public static void brancher(Predicate<ServerPlayer> test) {
        vraiBot = test;
    }

    /** Vrai pour un bot de charge (porte l'étiquette). */
    public static boolean estBot(@Nullable Entity e) {
        return e != null && e.getTags().contains(TAG);
    }

    /** Vrai si ce joueur est un vrai bot du module banc (lui seul peut garder l'étiquette). */
    public static boolean vraiBot(ServerPlayer p) {
        return vraiBot.test(p);
    }
}
