package fr.heraliz.casino.net;

import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.config.CasinoConfigServeur;
import fr.heraliz.casino.data.CasinoStats;
import fr.heraliz.casino.event.CommonEventsServeur;
import fr.heraliz.casino.machine.MachineType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * Paquets recus par le serveur (client -> serveur), branches sur {@link CasinoNet#RECEPTION_SERVEUR} par la partie
 * serveur : traitements d'origine des paquets, tels quels. (ActionPacket appelle directement la methode neutre
 * handleAction de la machine, redefinie par sa version serveur.)
 */
public final class ReceptionServeur {
    private ReceptionServeur() {
    }

    public static void recevoir(CasinoNet.Recu r) {
        Object m = r.paquet();
        ServerPlayer p = r.joueur();
        if (m instanceof AdminConfigPacket x) adminConfig(p, x);
        else if (m instanceof AdminStatsPacket.Request x) adminStats(p, x);
        else if (m instanceof ZonePayPacket x) zonePay(p, x);
    }

    /** Tablette du Directeur : nouveaux reglages d'une machine. Reserve aux operateurs (niveau 2), verifie ici. */
    static void adminConfig(ServerPlayer player, AdminConfigPacket p) {
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("message.heralizcasino.admin_only").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (CasinoConfig.LIGHTS.equals(p.machine())) {
            // page "Lumieres" : niveau de chaque machine (les machines posees suivent en une seconde)
            try {
                CasinoConfigServeur.applyLights(p.json());
            } catch (Exception e) {
                HeralizCasino.LOGGER.warn("[HeralizCasino] Lumieres refusees ({})", player.getGameProfile().getName(), e);
                player.displayClientMessage(Component.translatable("message.heralizcasino.admin_error").withStyle(ChatFormatting.RED), false);
                return;
            }
            CommonEventsServeur.broadcastConfig();
            HeralizCasino.LOGGER.info("[HeralizCasino] {} a modifie la lumiere des machines : {}", player.getGameProfile().getName(), p.json().replace('\n', ' '));
            player.displayClientMessage(Component.translatable("message.heralizcasino.admin_lights_saved").withStyle(ChatFormatting.GREEN), false);
            return;
        }
        MachineType t = MachineType.byId(p.machine());
        if (t == null) return;
        try {
            CasinoConfigServeur.applySection(t, p.json());
        } catch (Exception e) {
            HeralizCasino.LOGGER.warn("[HeralizCasino] Reglages refuses ({} par {})", t.id, player.getGameProfile().getName(), e);
            player.displayClientMessage(Component.translatable("message.heralizcasino.admin_error").withStyle(ChatFormatting.RED), false);
            return;
        }
        CommonEventsServeur.broadcastConfig();
        double[] rtp = CasinoConfig.rtp(t, CasinoConfig.get());
        HeralizCasino.LOGGER.info("[HeralizCasino] {} a modifie les reglages de {} : {}", player.getGameProfile().getName(), t.id, p.json().replace('\n', ' '));
        // machine a sous : la simulation de ces nouveaux reglages commence a peine, son chiffre ne veut encore rien dire
        boolean rough = rtp != null && rtp.length > 2 && rtp[2] > 0.03;
        Component msg = rtp == null || rough
                ? Component.translatable("message.heralizcasino.admin_saved", t.displayName())
                : Component.translatable("message.heralizcasino.admin_saved_rtp", t.displayName(), String.format(Locale.ROOT, "%.1f", rtp[1] * 100));
        player.displayClientMessage(msg.copy().withStyle(ChatFormatting.GREEN), false);
    }

    /** Un operateur demande les statistiques. */
    static void adminStats(ServerPlayer player, AdminStatsPacket.Request p) {
        if (!player.hasPermissions(2) || player.getServer() == null) return;
        String json = CasinoStats.get(player.getServer()).export(player.getServer()).toString();
        CasinoNet.sendTo(player, new AdminStatsPacket(json));
    }

    /** Le joueur paie son entree au casino. Le serveur verifie tout : zone, prix, jetons ou objets. */
    static void zonePay(ServerPlayer player, ZonePayPacket p) {
        if (player.isSpectator()) return;
        fr.heraliz.casino.zone.ZoneManager.pay(player);
    }
}
