package fr.heraliz.casino.command;

import fr.heraliz.casino.HeralizCasino;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;

/**
 * Reponses des commandes /casino. Le monde du serveur HeraliZ garde la regle sendCommandFeedback=false, qui MASQUE
 * tous les sendSuccess : a un joueur, la reponse part donc directement ({@code sendSystemMessage}) ; console et blocs
 * de commande : sendSuccess / sendFailure classiques. Les reponses "diffusees" (actions d'operateur) vont aussi dans
 * le journal du serveur.
 */
final class Reponses {
    private Reponses() {
    }

    /** Reponse normale (succes, information). */
    static void ok(CommandSourceStack src, Component c) {
        ok(src, c, false);
    }

    /** Reponse normale ; diffuser : action d'operateur, notee aussi dans le journal du serveur. */
    static void ok(CommandSourceStack src, Component c, boolean diffuser) {
        if (src.getEntity() instanceof ServerPlayer p) {
            p.sendSystemMessage(c);
            if (diffuser) HeralizCasino.LOGGER.info("[HeralizCasino] commande de {} : {}", p.getGameProfile().getName(), texte(c));
        } else {
            src.sendSuccess(() -> c, diffuser);
        }
    }

    /** Erreur (rouge). */
    static void erreur(CommandSourceStack src, Component c) {
        if (src.getEntity() instanceof ServerPlayer p) {
            p.sendSystemMessage(c.copy().withStyle(ChatFormatting.RED));
        } else {
            src.sendFailure(c);
        }
    }

    /** Texte du journal : cle et arguments d'un texte traduit (le serveur n'a pas les fichiers de langue du mod). */
    private static String texte(Component c) {
        if (c.getContents() instanceof TranslatableContents t) return t.getKey() + " " + Arrays.toString(t.getArgs());
        return c.getString();
    }
}
