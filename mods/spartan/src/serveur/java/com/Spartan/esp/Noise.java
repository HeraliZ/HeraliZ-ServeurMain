package com.Spartan.esp;

import com.Spartan.Spartan;
import com.Spartan.config.SpartanConfig;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModList;

/**
 * Bruits qui révèlent un joueur caché par l'anti-ESP : sans eux, son tir (TaCZ) ou
 * sa voix (Simple Voice Chat) ne serait pas joué chez les joueurs qui ne le
 * reçoivent pas. Comme dans tout jeu : qui fait du bruit se fait repérer, mais
 * seulement par ceux qui sont à portée d'oreille.
 */
public final class Noise {
    private static boolean taczFallback;
    private static double taczFallbackRadius = 64;

    private Noise() {
    }

    /** TaCZ envoie un son lié à {@code source} aux joueurs à moins de {@code distance} blocs. */
    public static void taczSound(Entity source, int distance) {
        if (SpartanConfig.ESP_REVEAL_SHOTS.get()) {
            VisibilityEngine.reveal(source, distance + 2.0, SpartanConfig.ESP_SHOT_REVEAL_TICKS.get());
        }
    }

    /** Simple Voice Chat : {@code speaker} parle, entendu jusqu'à {@code range} blocs (thread de la voix). */
    public static void voice(UUID speaker, double range) {
        VisibilityEngine.noteVoice(speaker, range > 0 ? range : 48);
    }

    /**
     * Démarrage du serveur : le hook exact sur les sons de TaCZ (Mixin) est-il en place ?
     * Sinon (version de TaCZ différente), repli sur l'événement de tir de TaCZ avec la
     * portée de son par défaut de TaCZ.
     */
    @SuppressWarnings("unchecked")
    public static void checkTacz() {
        if (!ModList.get().isLoaded("tacz") || taczFallback) {
            return;
        }
        try {
            Class<?> sm = Class.forName("com.tacz.guns.sound.SoundManager");
            for (Method m : sm.getDeclaredMethods()) {
                if (m.getName().contains("spartan$")) {
                    Spartan.LOGGER.info("[Spartan] Anti-ESP : tirs TaCZ reliés (un tireur est montré aux joueurs qui l'entendent).");
                    return;
                }
            }
            Class<? extends Event> ev = (Class<? extends Event>) Class.forName("com.tacz.guns.api.event.common.GunShootEvent");
            Method shooter = ev.getMethod("getShooter");
            Method side = ev.getMethod("getLogicalSide");
            try {
                Object v = Class.forName("com.tacz.guns.config.common.GunConfig")
                        .getField("DEFAULT_GUN_FIRE_SOUND_DISTANCE").get(null);
                if (v instanceof ForgeConfigSpec.IntValue iv) {
                    taczFallbackRadius = iv.get();
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // portée par défaut de TaCZ : 64
            }
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, (Class<Event>) ev, e -> {
                try {
                    if (side.invoke(e) == LogicalSide.SERVER && shooter.invoke(e) instanceof Entity s
                            && SpartanConfig.ESP_REVEAL_SHOTS.get()) {
                        VisibilityEngine.reveal(s, taczFallbackRadius * 1.5, SpartanConfig.ESP_SHOT_REVEAL_TICKS.get());
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // jamais casser un tir
                }
            });
            taczFallback = true;
            Spartan.LOGGER.warn("[Spartan] Anti-ESP : hook des sons TaCZ absent (version de TaCZ différente ?), "
                    + "repli sur l'événement de tir ({} blocs).", (int) (taczFallbackRadius * 1.5));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Spartan.LOGGER.warn("[Spartan] Anti-ESP : TaCZ non reconnu ({}). Les tirs d'un joueur caché pourraient "
                    + "être silencieux : désactivez l'anti-ESP si c'est le cas.", e.toString());
        }
    }
}
