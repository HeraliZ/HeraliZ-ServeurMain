package fr.heralisz.tags.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import fr.heralisz.tags.HeraliszTags;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Shader de decalque « peinture » ({@code heralisz_tags:peinture}, TAGS_V2_SPEC §3) : meme format de sommets que
 * {@code rendertype_text} (POSITION_COLOR_TEX_LIGHTMAP), lightmap et brouillard, plus la porosite alignee sur les
 * pixels des blocs (uniforme {@code Origine}), un assombrissement leger par endroits (une lecture de texture par fragment, rejet immediat du vide).
 * <p>Enregistre a chaque chargement des shaders (demarrage, F3+T) ; s'il ne compile pas, {@link #obtenir()} rend
 * null et le {@link DecalRenderer} se replie sur {@code rendertype_text} (rendu v1, sans effet de peinture).
 */
@Mod.EventBusSubscriber(modid = HeraliszTags.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PeintureShader {
    private PeintureShader() {
    }

    public static final ResourceLocation ID = new ResourceLocation(HeraliszTags.MODID, "peinture");
    /** Nom de l'uniforme de l'origine monde (vec3) des sommets dessines. */
    public static final String ORIGINE = "Origine";

    @Nullable
    private static volatile ShaderInstance shader;

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        // l'instance precedente est fermee par le GameRenderer au rechargement : ne jamais la garder si la nouvelle echoue
        shader = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), ID, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
                    s -> {
                        shader = s;
                        HeraliszTags.LOGGER.info("[HeraliZ Tags] shader de peinture charge");
                    });
        } catch (Exception e) {
            HeraliszTags.LOGGER.error("[HeraliZ Tags] shader de peinture indisponible : repli sur rendertype_text", e);
        }
    }

    /** Le shader de peinture, ou null s'il n'a pas pu etre charge. */
    @Nullable
    public static ShaderInstance obtenir() {
        return shader;
    }
}
