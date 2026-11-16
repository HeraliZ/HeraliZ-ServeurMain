package fr.heraliz.recolte.client.enmain;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nullable;

/**
 * Extensions client des trois outils ({@code OutilRecolte.RENDU_CLIENT}, branché par {@code PartieClient}) : le modèle
 * en cubes en main ({@link RenduOutilObjet} ; l'icône de l'inventaire vient du modèle d'objet,
 * {@code forge:separate_transforms}) et la pose à deux bras vue des autres ({@link VueDesAutres}). Une seule instance
 * pour les trois : elle ne garde rien de l'outil (Forge la demande pendant la construction de l'objet, avant que ses
 * champs soient remplis ; tout est lu sur la pile au moment du rendu).
 */
public final class ExtensionsOutil implements IClientItemExtensions {
    public static final ExtensionsOutil INSTANCE = new ExtensionsOutil();
    /**
     * Pose des bras, créée ici (à la construction des objets, avant tout rendu), comme celles de CRac et d'Extraction :
     * une valeur ajoutée à {@code ArmPose} après que Minecraft a fait un {@code switch} dessus ne serait pas connue.
     */
    private static final HumanoidModel.ArmPose POSE = VueDesAutres.POSE;

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

    /** Tenu en main principale : les deux bras sur le manche ; en main gauche seule (rare) : la pose de Minecraft. */
    @Nullable
    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entite, InteractionHand main, ItemStack pile) {
        return main == InteractionHand.MAIN_HAND ? POSE : null;
    }
}
