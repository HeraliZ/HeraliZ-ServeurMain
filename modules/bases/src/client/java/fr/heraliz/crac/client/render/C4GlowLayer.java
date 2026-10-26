package fr.heraliz.crac.client.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.texture.AutoGlowingTexture;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Emissive pass for the explosives' {@code <texture>_glowmask.png} (LED, timer digits, fuse ember).
 * GeckoLib still builds the processed glow texture (glowing texels are cut out of the base texture), but it is drawn
 * with vanilla's full-bright additive "eyes" render type instead of GeckoLib's own glow render type: that one uses the
 * translucent-emissive entity shader without the overlay texture state (glowing texels can come out black, depending
 * on what was rendered before) and that shader also applies directional entity shading (dim, angle-dependent glow).
 */
public class C4GlowLayer<T extends GeoAnimatable> extends AutoGlowingGeoLayer<T> {
    public C4GlowLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    @Override
    protected RenderType getRenderType(T animatable) {
        ResourceLocation base = this.getTextureResource(animatable);
        AutoGlowingTexture.getRenderType(base); // registers / builds the processed <base>_glowmask texture
        String path = base.getPath();
        int dot = path.lastIndexOf('.');
        String glow = (dot < 0 ? path : path.substring(0, dot)) + "_glowmask" + (dot < 0 ? "" : path.substring(dot));
        return RenderType.eyes(new ResourceLocation(base.getNamespace(), glow));
    }
}
