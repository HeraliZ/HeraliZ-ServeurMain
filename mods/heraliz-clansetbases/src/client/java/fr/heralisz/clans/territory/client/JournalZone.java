package fr.heralisz.clans.territory.client;

import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.client.ClientClans;
import fr.heralisz.clans.network.Packets;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * Banc d'essai seulement (client de dev lance avec -Dheralisz.clans.harness) : journalise chaque changement de la
 * barre de zone recue du serveur avec la position du joueur a cet instant (« barre de zone : Zone de test NEUTRE
 * (joueur 12.5 -60.0 0.5) », puis « barre de zone : aucune (joueur 60.5 -60.0 0.5) ») pour verifier dans le journal
 * que la barre n'existe que dans la zone et disparait des la sortie. Inerte en jeu normal (un test de champ par tick).
 */
@Mod.EventBusSubscriber(modid = HeraliszClans.MODID, value = Dist.CLIENT)
public final class JournalZone {
    private static final boolean ACTIF = System.getProperty("heralisz.clans.harness") != null;
    private static String derniere = "";

    private JournalZone() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (!ACTIF || e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            derniere = "";
            return;
        }
        Packets.TerritoireHudS2C h = ClientClans.hudZone();
        // une ligne par changement de zone, d'etat ou de proprietaire (pas a chaque pas de progression)
        String cle = h == null ? "" : h.zone() + "|" + h.etat() + "|" + h.proprioTag() + "|" + h.capteurTag();
        if (cle.equals(derniere)) return;
        derniere = cle;
        String pos = String.format(Locale.ROOT, "%.1f %.1f %.1f%s", mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                mc.player.isPassenger() ? ", passager" : "");
        if (h == null) {
            HeraliszClans.LOG.info("[clans] barre de zone : aucune (joueur {})", pos);
        } else {
            HeraliszClans.LOG.info("[clans] barre de zone : {} {} proprio [{}] capteur [{}] {} % (joueur {})", h.zone(), h.etat(),
                    h.proprioTag(), h.capteurTag(), Math.round(h.progression() * 100), pos);
        }
    }
}
