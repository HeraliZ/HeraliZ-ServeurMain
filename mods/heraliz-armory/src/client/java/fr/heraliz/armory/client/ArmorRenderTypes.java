package fr.heraliz.armory.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.heraliz.armory.HeralizArmory;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Types de rendu des pieces (slots vanilla, Curios, bras a la premiere personne). Pieces avec verre : celui des entites translucides
 * (RenderType.entityTranslucent : alpha < 25 invisible, 25..250 semi-transparent comme un verre teinte, 255 opaque, tri des
 * faces a l'envoi), plus le leger rapprochement vers la camera des armures vanilla (VIEW_OFFSET_Z_LAYERING, comme
 * armorCutoutNoCull) pour que les faces proches de la peau ne scintillent pas.
 */
public final class ArmorRenderTypes extends RenderType {
    private static final Function<ResourceLocation, RenderType> TRANSLUCENT = Util.memoize(texture -> create(
            HeralizArmory.MOD_ID + "_armor_translucent", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(texture, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .createCompositeState(true)));

    private ArmorRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                             Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    /** Pieces avec verre (visieres) : translucide, faces triees. */
    public static RenderType armorTranslucent(ResourceLocation texture) {
        return TRANSLUCENT.apply(texture);
    }

    /** Pieces sans verre : decoupe comme les armures vanilla (pas de tri des faces, plus leger). */
    public static RenderType armorCutout(ResourceLocation texture) {
        return RenderType.armorCutoutNoCull(texture);
    }
}
