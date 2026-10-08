package fr.heraliz.crates.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import fr.heraliz.crates.HeralizCrates;
import fr.heraliz.crates.client.render.scene.Sprite;
import fr.heraliz.crates.crate.CrateType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Textures HD des caisses (atlas 1024 px + calque lumineux, sprites) lues et mipmappees pendant l'ecran de chargement du
 * jeu, en arriere-plan : en arrivant au spawn, rien n'est plus a decoder (sinon ~20 ms par texture au premier regard).
 */
public final class CrateTextures extends SimplePreparableReloadListener<Map<ResourceLocation, NativeImage[]>> {
    @Override
    protected Map<ResourceLocation, NativeImage[]> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, NativeImage[]> out = new LinkedHashMap<>();
        for (CrateType type : CrateType.values()) {
            load(resourceManager, SceneRenderer.texture(type.id), out);
            load(resourceManager, SceneRenderer.glowTexture(type.id), out);
        }
        for (Sprite s : Sprite.values()) load(resourceManager, HeralizCrates.id(s.path()), out);
        return out;
    }

    private static void load(ResourceManager rm, ResourceLocation loc, Map<ResourceLocation, NativeImage[]> out) {
        if (out.containsKey(loc)) return;
        try {
            out.put(loc, HDTexture.decode(rm, loc));
        } catch (Exception e) {
            HeralizCrates.LOGGER.warn("[HeralizCrates] Texture {} illisible", loc, e);
        }
    }

    @Override
    protected void apply(Map<ResourceLocation, NativeImage[]> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        for (Map.Entry<ResourceLocation, NativeImage[]> e : prepared.entrySet()) HDTexture.install(e.getKey(), e.getValue());
    }
}
