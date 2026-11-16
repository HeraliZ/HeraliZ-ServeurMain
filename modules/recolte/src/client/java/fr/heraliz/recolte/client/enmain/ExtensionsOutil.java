package fr.heraliz.recolte.client.enmain;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Extensions client des trois outils ({@code OutilRecolte.RENDU_CLIENT}, branché par {@code PartieClient}) : le modèle
 * en cubes en main ({@link RenduOutilObjet} ; l'icône de l'inventaire vient du modèle d'objet,
 * {@code forge:separate_transforms}). Une seule instance pour les trois : elle ne garde rien de l'outil (Forge la
 * demande pendant la construction de l'objet, avant que ses champs soient remplis ; tout est lu sur la pile au moment
 * du rendu).
 */
public final class ExtensionsOutil implements IClientItemExtensions {
    public static final ExtensionsOutil INSTANCE = new ExtensionsOutil();

    private BlockEntityWithoutLevelRenderer rendu;

    private ExtensionsOutil() {
    }

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        BlockEntityWithoutLevelRenderer r = rendu;
        if (r == null) {
            rendu = r = new RenduOutilObjet();
        }
        return r;
    }
}
