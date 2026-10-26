package fr.heraliz.casino.client;

import fr.heraliz.casino.client.render.MachineItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;

/**
 * Point d'acces aux renderers d'objets (cree a la demande, cote client uniquement).
 */
public final class ClientRenderers {
    private ClientRenderers() {
    }

    private static MachineItemRenderer itemRenderer;

    public static BlockEntityWithoutLevelRenderer itemRenderer() {
        if (itemRenderer == null) itemRenderer = new MachineItemRenderer();
        return itemRenderer;
    }
}
