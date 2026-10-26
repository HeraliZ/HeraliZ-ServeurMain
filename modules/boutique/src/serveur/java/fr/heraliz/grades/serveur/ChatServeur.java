package fr.heraliz.grades.serveur;

import com.mojang.logging.LogUtils;
import fr.heraliz.grades.reseau.ReseauChat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat à canaux (25/10, maquettes validées) : chaque joueur écrit sur un canal (Tab dans le chat) :
 * <ul>
 *     <li><b>Proximité</b> : lu par ceux du même monde à {@link #RAYON} blocs au plus ;</li>
 *     <li><b>Global</b> : lu par tous ;</li>
 *     <li><b>Clan</b> : le chat de clan de HeraliZ Clans ({@code ClanService.chat} : droits du clan, cadence, mutes).</li>
 * </ul>
 * Sur Mohist, le message passe d'abord par l'événement de chat de Bukkit : AuthMe, LiteBans (mutes) et les autres plugins
 * l'annulent avant nous ; s'il arrive intact à la fin (priorité MONITOR, {@code PontBukkit}), on le prend et on le répartit
 * nous-mêmes. Sans Bukkit (solo), par l'événement de chat de Forge. Le joueur ne choisit que le canal : destinataires,
 * droits et mutes sont décidés ici.
 */
final class ChatServeur {
    private static final Logger LOG = LogUtils.getLogger();
    /** Portée du canal Proximité (blocs). */
    static final double RAYON = 48;
    private static final int MAX = 256;
    private static final Map<UUID, Integer> CANAUX = new ConcurrentHashMap<>();
    private static Method chatClan;
    private static boolean chatClanCherche;

    private ChatServeur() {
    }

    static void brancher() {
        ReseauChat.RECEPTION_CHOISIR.brancher(r -> choisir(r.joueur(), r.canal()));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ChatServeur::chatForge);
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> CANAUX.remove(e.getEntity().getUUID()));
    }

    private static void choisir(ServerPlayer p, int canal) {
        if (canal < 0 || canal >= ReseauChat.NOMBRE) return;
        CANAUX.put(p.getUUID(), canal);
    }

    /** Message pris à la fin de l'événement de chat de Bukkit (autre fil) : réparti sur le fil serveur ; vrai = pris en charge. */
    static boolean prendre(UUID joueur, String message) {
        MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
        if (srv == null || joueur == null || message == null) return false;
        srv.execute(() -> {
            ServerPlayer p = srv.getPlayerList().getPlayer(joueur);
            if (p != null) diffuser(p, message);
        });
        return true;
    }

    /** Sans Bukkit (ou si son écouteur n'a pas pu être branché) : l'événement de chat de Forge. */
    private static void chatForge(ServerChatEvent e) {
        if (PontBukkit.chatCanaux()) return;
        e.setCanceled(true);
        diffuser(e.getPlayer(), e.getRawText());
    }

    private static String nettoyer(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < MAX; i++) {
            char c = s.charAt(i);
            if (c == '§') {
                i++;
                continue;
            }
            if (c >= 32) b.append(c);
        }
        return b.toString().strip();
    }

    private static void diffuser(ServerPlayer p, String brut) {
        String m = nettoyer(brut);
        if (m.isEmpty()) return;
        int canal = CANAUX.getOrDefault(p.getUUID(), ReseauChat.GLOBAL);
        if (canal == ReseauChat.CLAN) {
            clan(p, m);
            return;
        }
        List<ServerPlayer> lecteurs = new ArrayList<>();
        for (ServerPlayer x : p.server.getPlayerList().getPlayers()) {
            if (canal == ReseauChat.GLOBAL || x == p || (x.level() == p.level() && x.distanceToSqr(p) <= RAYON * RAYON)) lecteurs.add(x);
        }
        String code = PontBukkit.CODE_CHAT.get(p.getUUID());
        ChatFormatting f = code == null || code.length() < 2 ? null : ChatFormatting.getByCode(code.charAt(1));
        int couleur = f != null && f.getColor() != null ? f.getColor() : 0;
        ReseauChat.Ligne l = new ReseauChat.Ligne(canal, p.getUUID(), p.getDisplayName(), m, couleur);
        for (ServerPlayer x : lecteurs) ReseauChat.CANAL.send(PacketDistributor.PLAYER.with(() -> x), l);
        LOG.info("[chat] [{}] {} : {}", canal == ReseauChat.GLOBAL ? "global" : "proximité", p.getGameProfile().getName(), m);
        if (canal == ReseauChat.PROXIMITE && lecteurs.size() == 1) {
            p.sendSystemMessage(Component.translatableWithFallback("message.heraliz_grades.chat.personne",
                    "Personne à moins de %s blocs ne t'a lu (canal Proximité ; Tab pour changer).", String.valueOf((int) RAYON))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** Canal Clan : le chat de clan de HeraliZ Clans (droits, cadence, mutes, message dans la couleur du clan). */
    private static void clan(ServerPlayer p, String m) {
        Optional<UUID> c;
        try {
            c = fr.heralisz.clans.api.ClansApi.clanDe(p.getUUID());
        } catch (LinkageError ex) {
            c = Optional.empty();
        }
        if (c.isEmpty()) {
            p.sendSystemMessage(Component.translatableWithFallback("message.heraliz_grades.chat.sans_clan",
                    "Tu n'as pas de clan : message non envoyé. Appuie sur Tab dans le chat pour changer de canal.").withStyle(ChatFormatting.RED));
            return;
        }
        if (!chatClanCherche) {
            chatClanCherche = true;
            try {
                chatClan = Class.forName("fr.heralisz.clans.server.ClanService").getMethod("chat", ServerPlayer.class, String.class);
            } catch (ReflectiveOperationException | LinkageError ex) {
                chatClan = null;
            }
        }
        try {
            if (chatClan != null) chatClan.invoke(null, p, m);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            LOG.warn("[grades] chat de clan injoignable : {}", ex.toString());
        }
    }
}
