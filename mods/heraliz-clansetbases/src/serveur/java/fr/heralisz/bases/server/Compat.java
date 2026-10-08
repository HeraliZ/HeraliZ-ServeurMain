package fr.heralisz.bases.server;

import fr.heralisz.bases.HeraliszBases;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;

/**
 * Compatibilites tolerantes (reflexion : mod absent ou en erreur = fonction ignoree, jamais de plantage).
 * <ul>
 *   <li>Reanimation (mod de l'utilisateur, remplace PlayerRevive le 02/10) :
 *   {@code fr.heraliz.reanimation.core.Downed.isDowned(Entity)} = joueur « a terre » : il ne peut ni entrer ni piller ;</li>
 *   <li>PlayerRevive (CreativeMD, 2.0.31, ancien, garde pour la transition) :
 *   {@code team.creative.playerrevive.server.PlayerReviveServer.isBleeding(Player)}.</li>
 * </ul>
 */
public final class Compat {
    private Compat() {
    }

    private static boolean essaye, essayeR;
    private static Method bleeding, downed;

    /** Vrai si le joueur est a terre (Reanimation, ou PlayerRevive s'il est encore installe). */
    public static boolean aTerre(Player p) {
        if (p == null) return false;
        return aTerreReanimation(p) || aTerrePlayerRevive(p);
    }

    private static boolean aTerreReanimation(Player p) {
        if (!essayeR) {
            essayeR = true;
            try {
                downed = Class.forName("fr.heraliz.reanimation.core.Downed").getMethod("isDowned", Entity.class);
                HeraliszBases.LOG.info("[bases] Reanimation detecte : un joueur a terre ne peut ni entrer ni piller");
            } catch (ReflectiveOperationException | LinkageError ex) {
                downed = null;
            }
        }
        if (downed == null) return false;
        try {
            return Boolean.TRUE.equals(downed.invoke(null, p));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            HeraliszBases.LOG.warn("[bases] Reanimation.isDowned en erreur, ignore desormais : {}", ex.toString());
            downed = null;
            return false;
        }
    }

    private static boolean aTerrePlayerRevive(Player p) {
        if (!essaye) {
            essaye = true;
            try {
                bleeding = Class.forName("team.creative.playerrevive.server.PlayerReviveServer").getMethod("isBleeding", Player.class);
            } catch (ReflectiveOperationException | LinkageError ex) {
                bleeding = null;
            }
        }
        if (bleeding == null) return false;
        try {
            return Boolean.TRUE.equals(bleeding.invoke(null, p));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            HeraliszBases.LOG.warn("[bases] PlayerRevive.isBleeding en erreur, ignore desormais : {}", ex.toString());
            bleeding = null;
            return false;
        }
    }
}
