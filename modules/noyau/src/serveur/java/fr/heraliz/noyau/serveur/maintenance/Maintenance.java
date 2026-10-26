package fr.heraliz.noyau.serveur.maintenance;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import fr.heraliz.noyau.NoyauConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Mises à jour sans tout couper (24/10, ARCHITECTURE.md §3). Opérateurs niveau 4 et console.
 * <ul>
 *     <li>{@code /recharger} : relit les données de tous les modules sans redémarrer ni déconnecter personne, en jouant
 *     leurs commandes de rechargement (liste {@code [recharger] commandes} de {@code heraliz_noyau-server.toml}). Les
 *     réglages en .toml se relisent seuls quand leur fichier change.</li>
 *     <li>{@code /redemarrage <minutes> [raison]} (ou {@code annuler}) : pour du code Java, Forge oblige à redémarrer.
 *     Compte à rebours annoncé à tous (titre et chat à 5, 3, 2 et 1 min, 30 s puis chaque seconde des 10 dernières),
 *     sauvegarde complète, joueurs déconnectés avec le message {@value #CLE_DECONNEXION} (leur jeu se reconnecte tout seul
 *     quand le serveur revient, HeraliZ Interface), puis arrêt du serveur (code de sortie {@code [redemarrage]
 *     code_sortie}).</li>
 * </ul>
 */
public final class Maintenance {
    private Maintenance() {
    }

    private static final Logger LOG = LogUtils.getLogger();
    /** Clé du message de déconnexion d'un redémarrage : lue par le jeu des joueurs (ne jamais la changer). */
    public static final String CLE_DECONNEXION = "heraliz.redemarrage.deconnexion";
    private static final int[] ANNONCES = {300, 180, 120, 60, 30, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1};

    private static long fin = -1;
    private static int derniereAnnonce = Integer.MAX_VALUE;
    private static String raison = "";
    private static int codeSortie;

    public static void enregistrer() {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END && fin > 0 && e.getServer().getTickCount() % 5 == 0) suivre(e.getServer());
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            if (codeSortie == 0) return;
            int code = codeSortie;
            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignore) {
                    // on sort quand même
                }
                Runtime.getRuntime().halt(code);
            }, "HeraliZ redémarrage");
            t.setDaemon(true);
            t.start();
        });
    }

    public static void commandes(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("recharger").requires(s -> s.hasPermission(4)).executes(Maintenance::recharger));
        d.register(Commands.literal("redemarrage").requires(s -> s.hasPermission(4))
                .then(Commands.literal("annuler").executes(Maintenance::annuler))
                .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 60))
                        .executes(c -> programmer(c, IntegerArgumentType.getInteger(c, "minutes"), ""))
                        .then(Commands.argument("raison", StringArgumentType.greedyString())
                                .executes(c -> programmer(c, IntegerArgumentType.getInteger(c, "minutes"), StringArgumentType.getString(c, "raison"))))));
    }

    // ------------------------------------------------------------------ /recharger
    private static int recharger(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        List<String> rates = new ArrayList<>();
        int faites = 0;
        for (String cmd : NoyauConfig.RECHARGER_COMMANDES.get()) {
            String commande = cmd.startsWith("/") ? cmd.substring(1) : cmd;
            int r = s.getServer().getCommands().performPrefixedCommand(s, commande);
            if (r > 0) faites++;
            else rates.add(commande);
        }
        int n = faites;
        s.sendSuccess(() -> Component.translatableWithFallback("noyau.recharger.fait", "[Recharger] %s rechargement(s) faits, sans redémarrer.",
                String.valueOf(n)).withStyle(ChatFormatting.GREEN), true);
        if (!rates.isEmpty()) {
            s.sendSuccess(() -> Component.translatableWithFallback("noyau.recharger.rates", "[Recharger] Sans effet ou refusés (voir au-dessus) : %s",
                    String.join(", ", rates)).withStyle(ChatFormatting.GOLD), false);
        }
        LOG.info("[recharger] {} rechargement(s), sans effet : {}", n, rates);
        return n;
    }

    // ------------------------------------------------------------------ /redemarrage
    private static int programmer(CommandContext<CommandSourceStack> c, int minutes, String pourquoi) {
        fin = System.currentTimeMillis() + Math.max(10, minutes * 60) * 1000L;
        raison = pourquoi == null ? "" : pourquoi.replaceAll("[\\p{Cntrl}§]", " ").trim();
        derniereAnnonce = Integer.MAX_VALUE;
        codeSortie = NoyauConfig.REDEMARRAGE_CODE.get();
        LOG.info("[redemarrage] programmé dans {} min ({})", minutes, raison);
        suivre(c.getSource().getServer());
        return 1;
    }

    private static int annuler(CommandContext<CommandSourceStack> c) {
        if (fin < 0) return 0;
        fin = -1;
        Component t = Component.translatableWithFallback("noyau.redemarrage.annule", "Le redémarrage du serveur est annulé.").withStyle(ChatFormatting.GREEN);
        c.getSource().getServer().getPlayerList().broadcastSystemMessage(t, false);
        LOG.info("[redemarrage] annulé");
        return 1;
    }

    private static void suivre(MinecraftServer srv) {
        int reste = (int) Math.ceil((fin - System.currentTimeMillis()) / 1000.0);
        if (reste <= 0) {
            redemarrer(srv);
            return;
        }
        for (int a : ANNONCES) {
            if (reste <= a && a < derniereAnnonce) {
                derniereAnnonce = a;
                annoncer(srv, a);
                break;
            }
        }
    }

    private static void annoncer(MinecraftServer srv, int secondes) {
        String duree = secondes >= 60 ? (secondes / 60) + " min" : secondes + " s";
        Component chat = Component.translatableWithFallback("noyau.redemarrage.annonce",
                        "Redémarrage du serveur dans %s%s. Ton jeu se reconnectera tout seul.", duree, raison.isEmpty() ? "" : " (" + raison + ")")
                .withStyle(ChatFormatting.GOLD);
        if (secondes >= 30 || secondes == 10) srv.getPlayerList().broadcastSystemMessage(chat, false);
        Component titre = Component.translatableWithFallback("noyau.redemarrage.titre", "Redémarrage").withStyle(ChatFormatting.GOLD);
        Component sous = Component.translatableWithFallback("noyau.redemarrage.sous_titre", "dans %s", duree);
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(5, secondes <= 10 ? 20 : 50, 10));
            p.connection.send(new ClientboundSetTitleTextPacket(titre));
            p.connection.send(new ClientboundSetSubtitleTextPacket(sous));
        }
    }

    private static void redemarrer(MinecraftServer srv) {
        fin = -1;
        LOG.info("[redemarrage] sauvegarde, déconnexion des joueurs, arrêt");
        srv.saveEverything(false, true, true);
        // 25/10 (audit) : arrêt annoncé AVANT de déconnecter (comme /stop) : sinon un joueur en combat ou à terre au
        // moment du redémarrage était tué (« déconnexion en combat ») et perdait son équipement sur un corps.
        srv.halt(false);
        Component m = Component.translatableWithFallback(CLE_DECONNEXION, "Redémarrage du serveur : ton jeu se reconnecte tout seul dans un instant.");
        for (ServerPlayer p : new ArrayList<>(srv.getPlayerList().getPlayers())) p.connection.disconnect(m);
    }
}
