package fr.heralisz.matchs.moteur.coffre;

import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Descente forcee d'un vehicule avant le Coffre (RETOURS du 01/10 §0) : un joueur passager (train de heralisz_utils,
 * cheval, bateau...) n'est plus refuse, il descend. Un mod qui interdit la descente ({@code EntityMountEvent} annule :
 * le train en marche le fait) est outrepasse pour ce seul joueur et ce seul appel : l'evenement est re-autorise en
 * priorite LOWEST. La position lue ensuite (instantane du Coffre) est celle du joueur a pied, jamais celle du vehicule.
 * Fil serveur.
 */
public final class Descente {
    private Descente() {
    }

    /** Joueur en cours de descente forcee (un seul a la fois, fil serveur). */
    @Nullable
    private static ServerPlayer force;

    public static void enregistrer() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, Descente::monture);
    }

    private static void monture(EntityMountEvent e) {
        if (force != null && e.isDismounting() && e.getEntityMounting() == force && e.isCanceled()) e.setCanceled(false);
    }

    /**
     * Fait descendre le joueur de son vehicule, meme si un mod l'interdit ; rend vrai s'il est a pied ensuite (ou
     * l'etait deja). Journalise le vehicule quitte.
     */
    public static boolean forcer(ServerPlayer p) {
        if (!p.isPassenger()) return true;
        Entity v = p.getVehicle();
        force = p;
        try {
            p.stopRiding();
        } finally {
            force = null;
        }
        String type = v == null ? "?" : String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(v.getType()));
        if (p.isPassenger()) {
            HeraliszMatchs.LOG.warn("[matchs] {} : descente du vehicule {} impossible", p.getGameProfile().getName(), type);
            return false;
        }
        HeraliszMatchs.LOG.info("[matchs] {} descendu du vehicule {} (position du joueur {} {} {})", p.getGameProfile().getName(), type,
                (int) Math.floor(p.getX()), (int) Math.floor(p.getY()), (int) Math.floor(p.getZ()));
        return true;
    }
}
