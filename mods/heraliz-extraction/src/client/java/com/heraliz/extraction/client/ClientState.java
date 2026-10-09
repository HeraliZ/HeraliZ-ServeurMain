package com.heraliz.extraction.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/** Petit état partagé côté client entre les différents renderers. */
public final class ClientState {
    /** Entité en cours de rendu (le rendu d'item ne reçoit pas l'entité qui le tient). */
    @Nullable
    public static LivingEntity renderingEntity;

    private ClientState() {
    }

    /** Temps en ticks (avec interpolation) pour les animations d'écran. */
    public static float time() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return (System.currentTimeMillis() % 1_000_000L) / 50.0F;
        }
        return (mc.level.getGameTime() % 1_000_000L) + mc.getFrameTime();
    }
}
