package com.heraliz.extraction.serveur;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Liens facultatifs avec nos autres mods, par réflexion (rien ne casse s'ils sont absents). */
final class Compat {
    private static final Logger LOG = LogUtils.getLogger();
    private static boolean looked;
    private static MethodHandle downed;

    private Compat() {
    }

    /**
     * Joueur à terre (Réanimation, {@code fr.heraliz.reanimation.core.Downed.isDowned(Entity)}) : il ne peut pas s'extraire
     * (sinon il échapperait à son exécution en restant dans la zone).
     */
    static boolean downed(ServerPlayer player) {
        if (!looked) {
            looked = true;
            if (ModList.get().isLoaded("reanimation")) {
                try {
                    Class<?> api = Class.forName("fr.heraliz.reanimation.core.Downed");
                    downed = MethodHandles.publicLookup().findStatic(api, "isDowned", MethodType.methodType(boolean.class, Entity.class));
                } catch (ReflectiveOperationException | LinkageError e) {
                    LOG.warn("[Extraction] Réanimation présente mais sans son API Downed : les joueurs à terre peuvent s'extraire ({})", e.toString());
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
