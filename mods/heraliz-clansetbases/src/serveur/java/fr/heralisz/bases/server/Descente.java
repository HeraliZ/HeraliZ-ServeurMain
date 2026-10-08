package fr.heralisz.bases.server;

import fr.heralisz.bases.HeraliszBases;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Descente forcee de tout vehicule avant une teleportation des bases (RETOURS du 01/10, §0 et §2) : le joueur resté
 * « passager » d'un vehicule (le train de HeraliZ Utils refuse toute descente hors d'une porte ouverte) gardait une
 * position serveur figee (ou recopiee depuis le vehicule, meme apres un changement de dimension) et tous ses clics sur
 * les blocs etaient annules par le mod du train : impossible de poser ou de casser dans sa base, compte a rebours qui
 * ne s'annulait jamais, retour toujours au meme point, chute dans le vide a l'arrivee.
 * <ul>
 *   <li>vehicule vivant dans le meme monde : descente normale ({@code stopRiding}, point de descente du vehicule) ;</li>
 *   <li>vehicule retire ou reste dans une autre dimension (« vehicule fantome ») : le lien est simplement coupe, le joueur
 *   garde sa position serveur ;</li>
 *   <li>dans les deux cas le client est recale sur la position du serveur (c'est elle qui fait foi ensuite).</li>
 * </ul>
 * Une descente refusee par un autre mod ({@code EntityMountEvent} annule) est acceptee quand c'est nous qui la
 * demandons : ecouteur au plus bas, qui recoit les evenements annules, et ne touche qu'au joueur en cours de descente.
 * Fil serveur uniquement.
 */
public final class Descente {
    private Descente() {
    }

    /** Joueur dont on force la descente en ce moment (null sinon). */
    @Nullable
    private static ServerPlayer force;

    /** Vrai si le vehicule du joueur est un « fantome » : retire, ou dans une autre dimension que lui. */
    public static boolean vehiculeFantome(ServerPlayer p) {
        Entity v = p.getVehicle();
        return v != null && (v.isRemoved() || v.level() != p.level());
    }

    /**
     * Fait descendre le joueur de son vehicule, meme si un autre mod refuse la descente.
     *
     * @param pourquoi motif pour le journal (« entree », « sortie », « presence »...)
     * @return vrai si le joueur n'est plus passager de rien
     */
    public static boolean faireDescendre(ServerPlayer p, String pourquoi) {
        Entity v = p.getVehicle();
        if (v == null) return true;
        boolean fantome = vehiculeFantome(p);
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(v.getType());
        force = p;
        try {
            if (fantome) p.removeVehicle();
            else p.stopRiding();
        } finally {
            force = null;
        }
        if (p.getVehicle() != null) {
            HeraliszBases.LOG.warn("[bases] {} : descente de {} impossible ({})", p.getGameProfile().getName(), type, pourquoi);
            return false;
        }
        // la position du serveur fait foi : le client y est recale (il pouvait se croire ailleurs)
        p.fallDistance = 0;
        p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
        HeraliszBases.LOG.info("[bases] {} descendu de {} ({}{}) en {} {} {}", p.getGameProfile().getName(), type, pourquoi,
                fantome ? ", vehicule retire ou dans une autre dimension" : "", Math.round(p.getX()), Math.round(p.getY()), Math.round(p.getZ()));
        return true;
    }

    /** Descente refusee par un autre mod (ex. le train) : acceptee si c'est la notre. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onMonture(EntityMountEvent e) {
        if (force != null && e.isDismounting() && e.getEntityMounting() == force && e.isCanceled()) {
            e.setCanceled(false);
        }
    }
}
