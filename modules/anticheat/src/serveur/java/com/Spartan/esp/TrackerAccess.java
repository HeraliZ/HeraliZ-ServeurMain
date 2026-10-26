package com.Spartan.esp;

import com.Spartan.Spartan;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/**
 * Accès au suivi des entités de vanilla (ChunkMap.TrackedEntity) pour appliquer
 * une décision de l'anti-ESP sans attendre que vanilla réévalue la paire.
 *
 * Noms SRG (identiques en jeu ; ObfuscationReflectionHelper les traduit en dev).
 * Si un serveur hybride ou un mod a changé ces classes : repli sur
 * ServerChunkCache.move(observateur), public, qui réévalue toutes les entités
 * pour cet observateur (plus cher, mais toujours correct).
 */
final class TrackerAccess {
    private static Field entityMap;
    private static Method updatePlayer;
    private static Method effectiveRange;
    private static Field seenBy;
    private static boolean resolved;
    private static boolean warned;

    private TrackerAccess() {
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            entityMap = ObfuscationReflectionHelper.findField(ChunkMap.class, "f_140150_");
            Class<?> tracked = Class.forName("net.minecraft.server.level.ChunkMap$TrackedEntity");
            updatePlayer = ObfuscationReflectionHelper.findMethod(tracked, "m_140497_", ServerPlayer.class);
            effectiveRange = ObfuscationReflectionHelper.findMethod(tracked, "m_140496_");
            seenBy = ObfuscationReflectionHelper.findField(tracked, "f_140475_");
        } catch (Throwable t) {
            entityMap = null;
            updatePlayer = null;
            effectiveRange = null;
            seenBy = null;
            Spartan.LOGGER.warn("[Spartan] Anti-ESP : suivi des entités non accessible ({}), repli sur la "
                    + "réévaluation complète de l'observateur.", t.toString());
        }
    }

    private static Object tracked(Entity e) {
        resolve();
        if (entityMap == null || !(e.level() instanceof ServerLevel level)) {
            return null;
        }
        try {
            Object map = entityMap.get(level.getChunkSource().chunkMap);
            return map instanceof Int2ObjectMap<?> m ? m.get(e.getId()) : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    /** Fait réévaluer par vanilla la paire (observateur, cible) : envoi ou retrait immédiat de la cible. */
    static void refresh(ServerPlayer observer, ServerPlayer target) {
        if (observer.hasDisconnected() || observer.level() != target.level()) {
            return;
        }
        Object t = tracked(target);
        if (t != null && updatePlayer != null) {
            try {
                updatePlayer.invoke(t, observer);
                return;
            } catch (ReflectiveOperationException | RuntimeException ex) {
                if (!warned) {
                    warned = true;
                    Spartan.LOGGER.warn("[Spartan] Anti-ESP : mise à jour directe impossible ({}), repli.", ex.toString());
                }
            }
        }
        if (observer.level() instanceof ServerLevel level) {
            level.getChunkSource().move(observer);
        }
    }

    /**
     * Vanilla envoie-t-il réellement la cible à l'observateur en ce moment ? (diagnostic :
     * doit correspondre à la décision de l'anti-ESP). null si inconnu.
     */
    static Boolean isSent(ServerPlayer observer, ServerPlayer target) {
        Object t = tracked(target);
        if (t == null || seenBy == null) {
            return null;
        }
        try {
            return seenBy.get(t) instanceof java.util.Set<?> set ? set.contains(observer.connection) : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    /**
     * Portée de suivi réelle de la cible (blocs), comme vanilla la calcule (config du
     * serveur, hybrides Spigot compris), ou -1 si inconnue.
     */
    static int effectiveRange(ServerPlayer target) {
        Object t = tracked(target);
        if (t == null || effectiveRange == null) {
            return -1;
        }
        try {
            return (int) effectiveRange.invoke(t);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return -1;
        }
    }
}
