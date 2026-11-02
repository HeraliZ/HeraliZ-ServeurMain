package fr.heraliz.noyau.serveur.commandes;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.logging.LogUtils;
import fr.heraliz.noyau.NoyauConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Commandes retirées pour de bon (02/11, audit points 34 et 36) : liste {@code [commandes] retirees} de
 * {@code heraliz_noyau-server.toml} (mods des autres auteurs comme {@code /kiwi}, {@code /tacz}, et commandes de Minecraft
 * sans usage ici). Sans toucher au code des autres : la commande est enlevée de l'arbre des commandes du serveur (ni
 * proposée, ni utilisable, console comprise) et, sous Mohist, de la table des commandes de Bukkit (sinon Bukkit la
 * remettrait dans l'arbre à sa prochaine resynchronisation). Une commande de plugin n'est jamais touchée.
 * <p>Quand : au démarrage (les réglages du monde ne sont lus qu'après l'enregistrement des commandes), à chaque nouvel arbre
 * ({@code /reload} des datapacks : les mods réenregistrent tout) et quand le fichier des réglages change (liste renvoyée
 * aux joueurs connectés). Enlever un nom de la liste ne le fait revenir qu'au redémarrage. Fil serveur.</p>
 */
public final class CommandesRetirees {
    private static final Logger LOG = LogUtils.getLogger();

    private CommandesRetirees() {
    }

    private static volatile boolean demarre;

    public static void enregistrer() {
        // en dernier : après les commandes de Minecraft et de tous les mods
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (RegisterCommandsEvent e) -> {
            Set<String> l = liste();
            if (l != null) retirer(e.getDispatcher(), l);
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> {
            demarre = true;
            appliquer(e.getServer(), "démarrage");
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> demarre = false);
    }

    /** Réglages relus (fichier changé pendant que le serveur tourne) : appliqué sur le fil serveur, liste renvoyée aux joueurs. */
    public static void reglagesRelus() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (!demarre || s == null || !s.isRunning()) return;
        s.execute(() -> {
            if (appliquer(s, "réglage relu") > 0) {
                for (ServerPlayer p : s.getPlayerList().getPlayers()) s.getCommands().sendCommands(p);
            }
        });
    }

    /** La liste du réglage en minuscules, ou null si les réglages du monde ne sont pas encore lus. */
    private static Set<String> liste() {
        List<? extends String> brut;
        try {
            brut = NoyauConfig.COMMANDES_RETIREES.get();
        } catch (IllegalStateException e) {
            return null;
        }
        Set<String> s = new HashSet<>();
        for (String c : brut) {
            if (c == null) continue;
            String r = c.trim().toLowerCase(Locale.ROOT);
            if (r.startsWith("/") && !r.startsWith("//")) r = r.substring(1);
            if (!r.isEmpty()) s.add(r);
        }
        return s;
    }

    /** Retire la liste de l'arbre du serveur et de la table de Bukkit ; rend le nombre de noms retirés. */
    private static int appliquer(MinecraftServer s, String quand) {
        Set<String> l = liste();
        if (l == null || l.isEmpty()) return 0;
        Set<String> faits = new TreeSet<>(retirer(s.getCommands().getDispatcher(), l));
        faits.addAll(Bukkit.retirer(l));
        if (!faits.isEmpty()) LOG.info("[noyau] commandes retirées ({}) : {}", quand, String.join(", ", faits));
        return faits.size();
    }

    // ------------------------------------------------------------------ arbre des commandes (Brigadier)
    private static Field enfants, litteraux;
    private static Method retirerCommande;
    private static boolean resolu;

    /** Retire de la racine les nœuds nommés (et leurs variantes « espace:nom ») ; rend les noms retirés. */
    static List<String> retirer(CommandDispatcher<CommandSourceStack> d, Set<String> noms) {
        List<String> faits = new ArrayList<>();
        CommandNode<CommandSourceStack> racine = d.getRoot();
        List<String> cibles = new ArrayList<>();
        for (CommandNode<CommandSourceStack> n : racine.getChildren()) {
            String nom = n.getName().toLowerCase(Locale.ROOT);
            int deux = nom.indexOf(':');
            if (noms.contains(nom) || (deux > 0 && noms.contains(nom.substring(deux + 1)))) cibles.add(n.getName());
        }
        for (String nom : cibles) if (retirerNoeud(racine, nom)) faits.add(nom);
        return faits;
    }

    /**
     * Brigadier n'a pas de « retirer » : Mohist en ajoute un ({@code removeCommand}), sinon les deux tables de la racine
     * (tous les enfants, et les mots fixes que lit l'analyse de la commande) sont vidées de ce nom.
     */
    private static boolean retirerNoeud(CommandNode<CommandSourceStack> racine, String nom) {
        try {
            if (!resolu) {
                resolu = true;
                try {
                    retirerCommande = CommandNode.class.getMethod("removeCommand", String.class);
                } catch (NoSuchMethodException e) {
                    retirerCommande = null;
                }
                enfants = CommandNode.class.getDeclaredField("children");
                enfants.setAccessible(true);
                litteraux = CommandNode.class.getDeclaredField("literals");
                litteraux.setAccessible(true);
            }
            if (retirerCommande != null) retirerCommande.invoke(racine, nom);
            if (enfants != null) ((Map<?, ?>) enfants.get(racine)).remove(nom);
            if (litteraux != null) ((Map<?, ?>) litteraux.get(racine)).remove(nom);
            return racine.getChild(nom) == null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warn("[noyau] commande /{} impossible à retirer ({})", nom, e.toString());
            return false;
        }
    }

    // ------------------------------------------------------------------ table des commandes de Bukkit (Mohist)
    /** Par réflexion : aucune dépendance de compilation ; sans Bukkit (Forge seul), rien. */
    private static final class Bukkit {
        private Bukkit() {
        }

        static List<String> retirer(Set<String> noms) {
            List<String> faits = new ArrayList<>();
            try {
                ClassLoader cl = MinecraftServer.class.getClassLoader();
                Class<?> bukkit = Class.forName("org.bukkit.Bukkit", false, cl);
                Object serveur = bukkit.getMethod("getServer").invoke(null);
                if (serveur == null) return faits;
                Object carte = serveur.getClass().getMethod("getCommandMap").invoke(serveur);
                Map<?, ?> connues;
                try {
                    connues = (Map<?, ?>) carte.getClass().getMethod("getKnownCommands").invoke(carte);
                } catch (NoSuchMethodException e) {
                    Field f = Class.forName("org.bukkit.command.SimpleCommandMap", false, bukkit.getClassLoader()).getDeclaredField("knownCommands");
                    f.setAccessible(true);
                    connues = (Map<?, ?>) f.get(carte);
                }
                Class<?> dePlugin = Class.forName("org.bukkit.command.PluginIdentifiableCommand", false, bukkit.getClassLoader());
                List<Object> cles = new ArrayList<>();
                for (Map.Entry<?, ?> en : connues.entrySet()) {
                    if (!(en.getKey() instanceof String k) || dePlugin.isInstance(en.getValue())) continue;
                    String nom = k.toLowerCase(Locale.ROOT);
                    int deux = nom.indexOf(':');
                    if (noms.contains(nom) || (deux > 0 && noms.contains(nom.substring(deux + 1)))) cles.add(k);
                }
                for (Object k : cles) {
                    connues.remove(k);
                    faits.add(String.valueOf(k));
                }
            } catch (ClassNotFoundException | LinkageError e) {
                // pas de Bukkit : l'arbre suffit
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOG.warn("[noyau] commandes retirées : table de Bukkit illisible ({}), seul l'arbre du serveur est nettoyé", e.toString());
            }
            return faits;
        }
    }
}
