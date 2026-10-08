package fr.heralisz.clans.territory;

import fr.heralisz.clans.ClansConfig;
import fr.heralisz.clans.HeraliszClans;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bonus de loot PhatLoots des membres presents dans une zone que leur clan tient : noeud LuckPerms
 * {@code phatloots.bonus.<n>} pose par commande console {@code lp user <nom> permission settemp <noeud> true 90s
 * replace} toutes les 60 s tant qu'ils y sont (expire tout seul : robuste aux deconnexions et plantages), retire par
 * {@code unsettemp} a la sortie. Si la commande {@code lp} n'existe pas (client de dev) : une ligne de journal, rien
 * d'autre. Cote serveur, {@code <n>} doit figurer dans LootBonusPermissions de PhatLoots (README).
 */
final class LootBonus {
    private static final long RENOUVELLEMENT_MS = 60_000;

    private final MinecraftServer server;
    /** Joueur -> dernier settemp (ms). */
    private final Map<UUID, Long> derniers = new HashMap<>();
    private Boolean lpDisponible;

    LootBonus(MinecraftServer server) {
        this.server = server;
    }

    private String noeud() {
        return "phatloots.bonus." + ClansConfig.LOOT_BONUS.get();
    }

    /** Le joueur est dans une zone de son clan : pose (ou renouvelle) le noeud. */
    void present(ServerPlayer p, long now) {
        Long last = derniers.get(p.getUUID());
        if (last != null && now - last < RENOUVELLEMENT_MS) return;
        derniers.put(p.getUUID(), now);
        commande("lp user " + p.getGameProfile().getName() + " permission settemp " + noeud() + " true 90s replace");
    }

    /** Le joueur n'est plus dans une zone de son clan : retire le noeud s'il l'avait. */
    void absent(ServerPlayer p) {
        if (derniers.remove(p.getUUID()) != null) {
            commande("lp user " + p.getGameProfile().getName() + " permission unsettemp " + noeud());
        }
    }

    /** Deconnexion : on oublie (le noeud temporaire expire seul). */
    void oublier(UUID joueur) {
        derniers.remove(joueur);
    }

    boolean aLeBonus(UUID joueur) {
        return derniers.containsKey(joueur);
    }

    private void commande(String cmd) {
        if (lpDisponible == null) {
            var racine = server.getCommands().getDispatcher().getRoot();
            lpDisponible = racine.getChild("lp") != null || racine.getChild("luckperms") != null;
            if (!lpDisponible) {
                HeraliszClans.LOG.info("[clans] commande lp absente (pas de LuckPerms) : bonus de loot des territoires inactif");
            }
        }
        if (!lpDisponible) return;
        try {
            CommandSourceStack src = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
            server.getCommands().performPrefixedCommand(src, cmd);
        } catch (RuntimeException ex) {
            HeraliszClans.LOG.warn("[clans] commande LuckPerms en echec : {}", cmd, ex);
        }
    }
}
