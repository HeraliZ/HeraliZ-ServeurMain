package fr.heraliz.grades.serveur;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /aide <message>} : appeler le staff (03/11, réponse du propriétaire : pendant le tutoriel, aucune commande sauf
 * appeler le staff ; aucune commande de ce genre n'existait). Le message part au staff connecté (Guide et plus, ou op 4,
 * connecté sur AuthMe) et sur le salon Discord du staff, avec le pseudo, le monde et la position du joueur. Une fois par
 * minute au plus par joueur, 200 caractères au plus, joueur connecté sur AuthMe. Permise pendant le tutoriel des bases
 * (seule commande que la fenêtre « Appeler le staff » des Quêtes peut envoyer), en combat et sur la carte (listes du
 * noyau). Fil serveur.
 */
final class AideStaff {
    private static final Logger LOG = LogUtils.getLogger();
    private static final long ECART_MS = 60_000L;
    private static final int MAX = 200;
    private static final Map<UUID, Long> DERNIERE = new HashMap<>();

    private AideStaff() {
    }

    static void brancher() {
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> DERNIERE.remove(e.getEntity().getUUID()));
    }

    static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("aide")
                .executes(c -> {
                    if (c.getSource().getEntity() instanceof ServerPlayer p) p.sendSystemMessage(msg("usage", "Écris /aide puis ton problème : le staff le reçoit."));
                    return 0;
                })
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(c -> c.getSource().getEntity() instanceof ServerPlayer p ? appeler(p, StringArgumentType.getString(c, "message")) : 0)));
    }

    private static Component msg(String cle, String fr, Object... args) {
        return Component.translatableWithFallback("message.heraliz_grades.aide." + cle, fr, args);
    }

    private static int appeler(ServerPlayer p, String brut) {
        if (!PontBukkit.authentifie(p)) return 0;
        StringBuilder b = new StringBuilder();
        for (char ch : brut.toCharArray()) {
            if (ch < 32 || ch == '§') continue;
            if (b.length() >= MAX) break;
            b.append(ch);
        }
        String m = b.toString().strip();
        if (m.isEmpty()) return 0;
        long now = System.currentTimeMillis();
        Long der = DERNIERE.get(p.getUUID());
        if (der != null && now - der < ECART_MS) {
            p.sendSystemMessage(msg("attends", "Ton appel est déjà parti : attends %s s avant d'en envoyer un autre.",
                    String.valueOf((ECART_MS - (now - der) + 999) / 1000)).copy().withStyle(ChatFormatting.RED));
            return 0;
        }
        DERNIERE.put(p.getUUID(), now);
        String nom = p.getGameProfile().getName();
        String ou = p.level().dimension().location() + " " + p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ();
        Component pourStaff = msg("staff", "[Appel au staff] %1$s (%2$s) : %3$s", nom, ou, m).copy().withStyle(ChatFormatting.GOLD);
        int n = 0;
        RegistreGrades r = ServeurGrades.registre;
        for (ServerPlayer x : p.server.getPlayerList().getPlayers()) {
            boolean staff = x.hasPermissions(4) || r != null && r.staffAuMoins(x.getUUID(), fr.heraliz.grades.Grade.GUIDE);
            if (staff && x != p && PontBukkit.authentifie(x)) {
                x.sendSystemMessage(pourStaff);
                n++;
            }
        }
        LOG.info("[staff] appel de {} ({}) : {}", nom, ou, m);
        LienDiscord.evenement("🆘 Appel au staff : **" + nom + "** (" + ou + ") — « " + m + " »");
        p.sendSystemMessage(msg(n > 0 ? "envoye" : "envoye_discord", n > 0 ? "Ton appel est parti au staff (%s connecté(s))."
                : "Aucun staff en jeu : ton appel est parti sur le Discord du staff.", String.valueOf(n)).copy().withStyle(ChatFormatting.GREEN));
        return 1;
    }
}
