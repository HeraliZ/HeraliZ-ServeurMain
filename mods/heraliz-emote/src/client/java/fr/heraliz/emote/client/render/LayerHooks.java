package fr.heraliz.emote.client.render;

import fr.heraliz.emote.HeralizEmote;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.client.event.EntityRenderersEvent;

import java.lang.reflect.Field;
import java.util.List;

public final class LayerHooks {
    private LayerHooks() {
    }

    @SuppressWarnings("unchecked")
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer == null) continue;
            renderer.addLayer(new PropLayer(renderer));
            try {
                List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> layers = layers(renderer);
                for (int i = 0; i < layers.size(); i++) {
                    if (layers.get(i) instanceof ItemInHandLayer<?, ?>) {
                        layers.set(i, new HiddenItemsLayer(renderer, layers.get(i)));
                    }
                }
            } catch (Exception e) {
                HeralizEmote.LOGGER.warn("Held items will stay visible during emotes with props", e);
            }
        }
    }

    /** LivingEntityRenderer#layers, found by type so it does not depend on the mappings. */
    @SuppressWarnings("rawtypes")
    private static List layers(PlayerRenderer renderer) throws IllegalAccessException {
        for (Field f : LivingEntityRenderer.class.getDeclaredFields()) {
            if (List.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                return (List) f.get(renderer);
            }
        }
        throw new IllegalStateException("no layer list in LivingEntityRenderer");
    }
}
