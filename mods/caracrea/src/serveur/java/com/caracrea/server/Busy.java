package com.caracrea.server;

import com.caracrea.CaraCrea;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Whether a player is busy with something the creator must never get in the way of: dead, spectator,
 * in a HeraliZ match (flag {@code heralisz:en_match} of the matchs engine), downed (Reanimation) or not
 * logged in on AuthMe yet (the first-join creator waits for /register or /login, {@link LoginCheck}). These
 * mods are optional: read through their public flag / API, without a compile dependency. Server thread.
 */
final class Busy {
    /** Persistent-data flag set by heralisz_matchs while a player is in a match (MatchApi.DRAPEAU_EN_MATCH). */
    private static final String IN_MATCH = "heralisz:en_match";
    private static MethodHandle downed;
    private static boolean looked;

    private Busy() {
    }

    static boolean busy(ServerPlayer player) {
        return !player.isAlive() || player.isSpectator() || inMatch(player) || downed(player) || !LoginCheck.loggedIn(player);
    }

    static boolean inMatch(ServerPlayer player) {
        return player.getPersistentData().getBoolean(IN_MATCH);
    }

    /** Reanimation's {@code fr.heraliz.reanimation.core.Downed.isDowned(Entity)}, false without the mod. */
    static boolean downed(ServerPlayer player) {
        if (!looked) {
            looked = true;
            if (ModList.get().isLoaded("reanimation")) {
                try {
                    Class<?> api = Class.forName("fr.heraliz.reanimation.core.Downed");
                    downed = MethodHandles.publicLookup().findStatic(api, "isDowned", MethodType.methodType(boolean.class, Entity.class));
                } catch (ReflectiveOperationException | LinkageError e) {
                    CaraCrea.LOGGER.warn("Reanimation found but its Downed API is not: downed players are not detected ({})", e.toString());
                }
            }
        }
        if (downed == null) {
            return false;
        }
        try {
            return (boolean) downed.invokeExact((Entity) player);
        } catch (Throwable e) {
            return false;
        }
    }
}
