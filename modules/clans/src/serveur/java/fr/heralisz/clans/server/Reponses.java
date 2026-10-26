package fr.heralisz.clans.server;

import fr.heralisz.clans.HeraliszClans;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Reponses des commandes /clan (meme principe et memes couleurs que /territoire) : un {@code sendSuccess} ne s'affiche
 * PAS quand la regle {@code sendCommandFeedback} est a false (cas du monde de l'utilisateur, regles de l'ancien
 * serveur : /clan admin argent, forcer, niveau, info, top... semblaient muets). Une reussite part donc directement au
 * joueur ({@code sendSystemMessage}) et dans le journal du serveur ; {@code sendSuccess} ne sert que si la source n'est
 * pas un joueur (console, bloc de commande). Les erreurs passent par {@code sendFailure}, toujours affiche (rouge).
 */
public final class Reponses {
    private Reponses() {
    }

    /** Couleurs : accent neutre (prefixe), avertissement ambre, texte attenue. */
    public static final int ACCENT = 0x6FA8FF, AVERT = 0xE8B34A, ATTENUE = 0x8F95A0;

    /**
     * « Clans · » en couleur d'accent suivi du message. La racine est vide : le message n'herite pas de la couleur du
     * prefixe (les freres heritent du style de leur parent).
     */
    public static MutableComponent prefixe(Component c) {
        return Component.empty().append(Component.translatableWithFallback("clan.cmd.prefixe", "Clans · ").withStyle(s -> s.withColor(ACCENT))).append(c);
    }

    /** Reussite (avec le prefixe), visible meme avec sendCommandFeedback a false ; ops prevenus depuis la console. */
    public static void ok(CommandSourceStack src, Component c) {
        envoyer(src, prefixe(c), true);
    }

    /** Avertissement (ambre) : la commande a reussi mais quelque chose merite l'attention. */
    public static void avert(CommandSourceStack src, Component c) {
        envoyer(src, prefixe(c.copy().withStyle(s -> s.withColor(AVERT))), true);
    }

    /** Ligne de detail (fiche, liste) : sans prefixe, en retrait. */
    public static void ligne(CommandSourceStack src, Component c) {
        envoyer(src, Component.literal("  ").append(c), false);
    }

    /** Erreur (rouge, toujours affichee) ; renvoie 0 pour la commande. */
    public static int echec(CommandSourceStack src, Component c) {
        src.sendFailure(prefixe(c));
        return 0;
    }

    private static void envoyer(CommandSourceStack src, Component c, boolean ops) {
        if (src.getEntity() instanceof ServerPlayer p) {
            p.sendSystemMessage(c);
            HeraliszClans.LOG.info("[clans] /clan ({}) : {}", p.getGameProfile().getName(), c.getString());
        } else {
            src.sendSuccess(() -> c, ops);
        }
    }
}
