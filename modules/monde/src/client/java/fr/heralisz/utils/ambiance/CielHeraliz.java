package fr.heralisz.utils.ambiance;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Effets du monde principal (remplacent ceux de vanilla, enregistrés pour « minecraft:overworld ») :
 * <ul>
 * <li>éclairage : la lumière du ciel est fortement baissée la nuit — selon la lune (pleine lune : on y voit ; nouvelle
 * lune sous un ciel couvert : nuit noire) — et un peu le jour (plus encore par temps couvert). La lumière des blocs
 * (torches, lanternes, lampadaires) ne change pas : elle ressort dans le noir. Calculé AVANT la luminosité du joueur et
 * sans le voile gris de vanilla, donc le réglage « Lumineux » ne suffit plus à faire disparaître la nuit ;</li>
 * <li>ciel : dessiné par {@link Ciel} (étoiles, voie lactée, halo de lune, étoiles filantes) ; en cas d'erreur, retour
 * au ciel vanilla.</li>
 * </ul>
 */
public final class CielHeraliz extends DimensionSpecialEffects.OverworldEffects {

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera,
                             Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        return Ciel.dessiner(level, partialTick, poseStack, camera, projectionMatrix, isFoggy, setupFog);
    }

    /**
     * Nuages doux à la place des nuages cubiques vanilla (seulement si le joueur a laissé les nuages activés). Hors de la
     * carte (spawn, monde du tutoriel, arènes : mêmes effets « overworld »), aucun nuage : sans ce retour, les nuages de
     * Minecraft s'y dessineraient (le spawn est au-dessus de leur couche, 192) depuis que les nuages sont rallumés.
     */
    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY,
                                double camZ, Matrix4f projectionMatrix) {
        if (level.dimension() != Level.OVERWORLD) {
            return true;
        }
        return Ciel.nuages(level, partialTick, poseStack, camX, camY, camZ, projectionMatrix);
    }

    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker,
                                     float skyLight, int pixelX, int pixelY, Vector3f colors) {
        if (level.getSkyFlashTime() > 0) {
            return;                                                  // éclair d'orage : tout s'illumine (vanilla)
        }
        // nuit d'après l'assombrissement du ciel (1 de jour, 0,2 la nuit ; l'orage le baisse un peu)
        float n = Mth.clamp((0.62f - skyDarken) / 0.38f, 0f, 1f);
        n = n * n * (3 - 2 * n);
        float clair = 1f - Ambiance.nuages;
        float attNuit = Mth.lerp(Ambiance.lune, 0.08f, 0.50f) * Mth.lerp(clair, 0.6f, 1f);
        float attJour = Mth.lerp(Ambiance.nuages, 0.96f, 0.88f);
        float att = Mth.lerp(n, attJour, attNuit);

        // lumière des blocs : calcul vanilla
        float b = LightTexture.getBrightness(level.dimensionType(), pixelX) * blockLightRedFlicker;
        float br = b;
        float bg = b * ((b * 0.6f + 0.4f) * 0.6f + 0.4f);
        float bb = b * (b * b * 0.6f + 0.4f);
        // lumière du ciel : teinte vanilla (bleutée la nuit), atténuée ; clair de lune un peu plus froid
        float k = skyLight * att;
        float sr = (skyDarken + (1f - skyDarken) * 0.35f) * k * (1f - 0.12f * n);
        float sb = k * (1f + 0.08f * n);
        float r = br + sr;
        float g = bg + sr;
        float bl = bb + sb;
        // voile gris de vanilla (4 %) presque retiré la nuit : le noir reste noir
        float voile = 0.04f * (1f - 0.8f * n);
        r += (0.75f - r) * voile;
        g += (0.75f - g) * voile;
        bl += (0.75f - bl) * voile;
        float sombre = Minecraft.getInstance().gameRenderer.getDarkenWorldAmount(partialTicks);
        if (sombre > 0f) {                                           // ciel assombri par un boss (vanilla)
            r = Mth.lerp(sombre, r, r * 0.7f);
            g = Mth.lerp(sombre, g, g * 0.6f);
            bl = Mth.lerp(sombre, bl, bl * 0.6f);
        }
        colors.set(r, g, bl);
    }
}
