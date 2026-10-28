package fr.heraliz.crac.heraliz;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import fr.heraliz.crac.bases.MondeBases;
import fr.heraliz.crac.item.BuildingPlanItem;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2CZonesInterdites;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.PacketDistributor;

/**
 * Zones où l'on ne construit pas, envoyées au joueur qui tient le plan (HeraliZ, 27/10, demande du propriétaire : les voir
 * en rouge avant de poser). Deux fois par seconde, seulement pour les joueurs plan en main sur la carte : la fenêtre du
 * masque autour d'eux ({@link ZonesConstruction#fenetre}, 25 x 25 cases de 4 blocs, soit 100 blocs de côté), renvoyée
 * seulement quand le joueur change de morceau ou que le masque change. Ailleurs (autre monde, créatif, plan rangé) :
 * une fenêtre vide, une fois. Fil serveur.
 */
@EventBusSubscriber(modid = "crac")
public final class ZonesInterditesEnvoi {
    /** Cases de chaque côté du centre, et pas (en cases) dont le centre avance : la fenêtre déborde toujours de 24 blocs. */
    private static final int RAYON = 12, ARRONDI = 4;
    /** Dernière fenêtre envoyée à chaque joueur : « vide », ou centre et version du masque. */
    private static final Map<UUID, String> ENVOYE = new HashMap<>();

    private ZonesInterditesEnvoi() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 0) {
            return;
        }
        boolean actif = p.getMainHandItem().getItem() instanceof BuildingPlanItem && !p.getAbilities().instabuild
            && !p.isSpectator() && MondeBases.estBases(p.level());
        String avant = ENVOYE.get(p.getUUID());
        if (!actif) {
            if (avant != null && !avant.equals("vide")) {
                envoyer(p, S2CZonesInterdites.vide(), "vide");
            }
            return;
        }
        S2CZonesInterdites f = ZonesConstruction.fenetre(p.getBlockX(), p.getBlockZ(), RAYON, ARRONDI);
        String cle = f.largeur() == 0 ? "vide" : f.x0() + "," + f.z0() + "," + ZonesConstruction.version();
        if (!cle.equals(avant)) {
            envoyer(p, f, cle);
        }
    }

    private static void envoyer(ServerPlayer p, S2CZonesInterdites f, String cle) {
        ENVOYE.put(p.getUUID(), cle);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), f);
    }

    @SubscribeEvent
    public static void depart(PlayerEvent.PlayerLoggedOutEvent e) {
        ENVOYE.remove(e.getEntity().getUUID());
    }

    /** Changement de monde : le jeu du joueur oublie ses zones (nouveau monde), on renverra. */
    @SubscribeEvent
    public static void monde(PlayerEvent.PlayerChangedDimensionEvent e) {
        ENVOYE.remove(e.getEntity().getUUID());
    }
}
