package com.caracrea.api;

import com.caracrea.CaraCreaConfig;
import com.caracrea.server.AppearanceStore;
import net.minecraft.server.level.ServerPlayer;

/**
 * First join, for the other HeraliZ mods (read by reflection by HeraliZ Utils: the arrival cinematic waits for the
 * character). Never rename this class or change its signatures. Server side only.
 */
public final class Arrivee {
    private Arrivee() {
    }

    /** True once the player has saved their character, or when the first-join creator is off (nothing to wait for). */
    public static boolean personnageFait(ServerPlayer player) {
        return AppearanceStore.hasChosen(player) || !CaraCreaConfig.SERVER.openOnFirstJoin.get();
    }
}
