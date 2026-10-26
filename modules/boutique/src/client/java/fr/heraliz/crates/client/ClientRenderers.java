package fr.heraliz.crates.client;

import fr.heraliz.crates.client.render.CrateItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;

/**
 * Point d'acces aux renderers d'objets (cree a la demande, cote client uniquement).
 */
public final class ClientRenderers {
    private ClientRenderers() {
    }

    private static CrateItemRenderer itemRenderer;

    public static BlockEntityWithoutLevelRenderer itemRenderer() {
        if (itemRenderer == null) {
            itemRenderer = new CrateItemRenderer();
        }
        return itemRenderer;
    }
}
