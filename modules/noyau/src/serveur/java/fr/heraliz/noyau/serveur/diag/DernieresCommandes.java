package fr.heraliz.noyau.serveur.diag;

import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.List;
import java.util.Locale;

/**
 * Les dernières commandes jouées, pour les rapports de blocage (01/11, « grosse montée de ms quand je faisais des
 * commandes ») : heure et mots de la commande seulement (« /clan info »), jamais les arguments ni qui l'a tapée (ni mot de
 * passe, ni message privé, ni pseudo dans les rapports). 16 au plus, en mémoire, fil serveur seulement (l'événement de
 * Forge passe sur le fil serveur) ; ne coûte qu'une ligne par commande.
 */
final class DernieresCommandes {
    private DernieresCommandes() {
    }

    private static final int GARDEES = 16;
    private static final long[] QUAND = new long[GARDEES];
    private static final String[] QUOI = new String[GARDEES];
    private static int pos, nb;

    static void enregistrer() {
        // en dernier, refusées comprises : on note ce qui est vraiment parti (ou refusé par une règle)
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, DernieresCommandes::noter);
    }

    private static void noter(CommandEvent e) {
        CommandContextBuilder<CommandSourceStack> b = e.getParseResults().getContext();
        StringBuilder s = new StringBuilder("/");
        List<ParsedCommandNode<CommandSourceStack>> noeuds = b.getNodes();
        // les mots fixes du début (racine et sous-commandes), jusqu'au premier argument
        for (int i = 0; i < noeuds.size() && i < 3 && noeuds.get(i).getNode() instanceof LiteralCommandNode<?>; i++) {
            if (i > 0) s.append(' ');
            s.append(noeuds.get(i).getNode().getName());
        }
        if (s.length() == 1) {
            // commande inconnue de Brigadier : seulement son premier mot
            String brut = e.getParseResults().getReader().getString().trim();
            if (brut.startsWith("/")) brut = brut.substring(1);
            int sp = brut.indexOf(' ');
            String racine = sp < 0 ? brut : brut.substring(0, sp);
            s.append(racine.length() > 32 ? racine.substring(0, 32) : racine).append(" (inconnue de Minecraft)");
        }
        if (b.getSource().getEntity() == null) s.append(" (console)");
        if (e.isCanceled()) s.append(" (refusée)");
        synchronized (QUAND) {
            QUAND[pos] = System.nanoTime();
            QUOI[pos] = s.toString();
            pos = (pos + 1) % GARDEES;
            if (nb < GARDEES) nb++;
        }
    }

    /** La plus récente commande jouée depuis {@code depuisNanos} (horloge System.nanoTime), ou null. */
    static String depuis(long depuisNanos) {
        synchronized (QUAND) {
            if (nb == 0) return null;
            int i = (pos - 1 + GARDEES) % GARDEES;
            return QUAND[i] - depuisNanos >= 0 ? QUOI[i] : null;
        }
    }

    /** Section du rapport : les commandes des 60 dernières secondes, de la plus récente à la plus ancienne. */
    static void ecrire(StringBuilder t, long maintenant) {
        t.append("== Dernières commandes (60 s ; mots de la commande seulement) ==\n");
        int ecrites = 0;
        synchronized (QUAND) {
            for (int k = 1; k <= nb; k++) {
                int i = (pos - k + GARDEES) % GARDEES;
                long ms = (maintenant - QUAND[i]) / 1_000_000L;
                if (ms > 60_000) break;
                t.append(String.format(Locale.ROOT, "  il y a %6d ms : %s%n", ms, QUOI[i]));
                ecrites++;
            }
        }
        if (ecrites == 0) t.append("  aucune\n");
        t.append('\n');
    }
}
