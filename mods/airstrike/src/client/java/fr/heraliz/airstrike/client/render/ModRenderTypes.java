package fr.heraliz.airstrike.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Extra render types (subclass only to reach the protected render state shards). */
public final class ModRenderTypes extends RenderType {
    /** Additive, always on top: the designator's target marker. */
    public static final RenderType MARKER = create("airstrike_marker", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 256, false, true, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /** Additive glowing ribbons (tracers, laser), depth tested. */
    public static final RenderType GLOW = create("airstrike_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, true, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /** Soft translucent ribbons (flare smoke trails), depth tested, no depth write. */
    public static final RenderType SMOKE = create("airstrike_smoke", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 8192, false, false, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private ModRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean affectsCrumbling,
                           boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, size, affectsCrumbling, sortOnUpload, setup, clear);
    }
}
