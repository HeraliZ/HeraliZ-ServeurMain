package fr.heralisz.utils;

import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * Brume de bordure : en approchant du bord de la map (bordure du monde), le brouillard se resserre jusqu'à ~20 blocs,
 * la mer et les crêtes se perdent dans la brume et on ne voit jamais le « bout du monde ». Calcul côté client, gratuit.
 * Appliquée par le brouillard d'ambiance ({@link fr.heralisz.utils.ambiance.Brouillard}), en dernier, par-dessus tout
 * autre réglage (désert, boss) : pas de saut de brouillard en entrant dans la zone de bordure.
 */
public final class BorderFog {
    private static final double RANGE = 256.0;

    private BorderFog() {}

    public static void appliquer(ViewportEvent.RenderFog e) {
        Player p = Minecraft.getInstance().player;
        if (p == null) {
            return;
        }
        Vec3 c = e.getCamera().getPosition();
        if (c.y < 0) {                                              // refuge souterrain du spawn et décor de la cinématique
            return;
        }
        WorldBorder wb = p.level().getWorldBorder();
        double d = wb.getDistanceToBorder(c.x, c.z);
        if (d >= RANGE) {
            return;
        }
        float k = (float) Mth.clamp(1.0 - d / RANGE, 0.0, 1.0);
        k = k * k * (3 - 2 * k);
        float far = Mth.lerp(k, e.getFarPlaneDistance(), 20.0f);
        e.setFarPlaneDistance(Math.min(e.getFarPlaneDistance(), far));
        e.setNearPlaneDistance(Math.min(e.getNearPlaneDistance(), far * 0.1f));
        e.setFogShape(FogShape.SPHERE);
        e.setCanceled(true);
    }
}
