package fr.heraliz.noyau.serveur;

import fr.heraliz.noyau.api.Bots;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * L'étiquette des bots de charge exempte de l'anticheat et d'autres règles : un vrai joueur qui la porterait ({@code /tag}
 * d'un staff, sauvegarde d'un bot restée après un plantage) la perd à la connexion et chaque seconde. Les pseudos des bots
 * sont réservés (un vrai compte à ce nom serait écrasé par {@code /bots}). Fil serveur.
 */
final class EtiquetteBots {
    private EtiquetteBots() {}

    static void enregistrer() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, EtiquetteBots::connexion);
        MinecraftForge.EVENT_BUS.addListener(EtiquetteBots::tick);
    }

    private static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || Bots.vraiBot(p)) return;
        p.removeTag(Bots.TAG);
        if (p.getGameProfile().getName().matches(Bots.PSEUDOS)) {
            p.connection.disconnect(Component.translatableWithFallback("noyau.bots.pseudo_reserve", "Ce pseudo est réservé au serveur."));
        }
    }

    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 20 != 3) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            if (p.getTags().contains(Bots.TAG) && !Bots.vraiBot(p)) p.removeTag(Bots.TAG);
        }
    }
}
