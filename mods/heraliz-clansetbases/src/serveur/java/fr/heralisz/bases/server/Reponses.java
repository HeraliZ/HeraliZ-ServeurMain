package fr.heralisz.bases.server;

import fr.heralisz.bases.HeraliszBases;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;

/**
 * Reponses des commandes {@code /base} (joueur, admin, pillage). Le monde de l'utilisateur garde la regle
 * {@code sendCommandFeedback=false} (regles de l'ancien serveur), qui MASQUE tous les {@code sendSuccess} : une reponse
 * part donc directement au joueur ({@code sendSystemMessage}) et dans le journal du serveur ; {@code sendSuccess} /
 * {@code sendFailure} seulement si la source n'est pas un joueur (console, bloc de commande). Erreurs en rouge,
 * avertissements en ambre, le reste sans couleur imposee. Fil serveur uniquement.
 */
public final class Reponses {
    private Reponses() {
    }

    public static final int ROUGE = 0xFF6B6B, AMBRE = 0xE8B34A;

    /** Reponse normale (succes, information). */
    public static void ok(CommandSourceStack src, Component c) {
        envoyer(src, c, false);
    }

    /** Erreur (rouge). */
    public static void erreur(CommandSourceStack src, Component c) {
        envoyer(src, c.copy().withStyle(s -> s.withColor(ROUGE)), true);
    }

    /** Avertissement (ambre). */
    public static void avertissement(CommandSourceStack src, Component c) {
        envoyer(src, c.copy().withStyle(s -> s.withColor(AMBRE)), false);
    }

    private static void envoyer(CommandSourceStack src, Component c, boolean erreur) {
        HeraliszBases.LOG.info("[bases] commande de {} : {}{}", src.getTextName(), erreur ? "ERREUR " : "", texte(c));
        if (src.getEntity() instanceof ServerPlayer p) {
            p.sendSystemMessage(c);
        } else if (erreur) {
            src.sendFailure(c);
        } else {
            src.sendSuccess(() -> c, false);
        }
    }

    /** Texte du journal : cle et arguments d'un texte traduit (le serveur n'a pas les fichiers de langue du mod). */
    private static String texte(Component c) {
        if (c.getContents() instanceof TranslatableContents t) return t.getKey() + " " + Arrays.toString(t.getArgs());
        return c.getString();
    }
}
