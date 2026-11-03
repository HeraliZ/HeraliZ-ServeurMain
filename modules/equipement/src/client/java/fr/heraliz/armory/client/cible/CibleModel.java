package fr.heraliz.armory.client.cible;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import fr.heraliz.armory.cible.CibleEssai;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Cible d'essai (03/11) : le modèle du mannequin d'armurerie (même maillage, MannequinModel.LAYER, même texture), aucun
 * dessin nouveau. Pose fixe d'un joueur debout sans arme, bras le long du corps : exactement les morceaux visés par les
 * balles (serveur, cible.HitboxCible : mêmes pivots, corps relevé d'un pixel pour le socle).
 */
public class CibleModel extends HumanoidModel<CibleEssai> {
    private final ModelPart base;

    public CibleModel(ModelPart root) {
        super(root);
        this.base = root.getChild("base");
    }

    @Override
    public void setupAnim(CibleEssai e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        crouching = false;
        riding = false;
        swimAmount = 0.0F;
        attackTime = 0.0F;
        pose(head, 0.0F, -1.0F);
        pose(body, 0.0F, -1.0F);
        pose(rightArm, -5.0F, 1.0F);
        pose(leftArm, 5.0F, 1.0F);
        pose(rightLeg, -1.9F, 11.0F);
        pose(leftLeg, 1.9F, 11.0F);
        hat.copyFrom(head);
        base.visible = true;
    }

    private static void pose(ModelPart p, float x, float y) {
        p.x = x;
        p.y = y;
        p.z = 0.0F;
        p.xRot = 0.0F;
        p.yRot = 0.0F;
        p.zRot = 0.0F;
    }

    @Override
    protected Iterable<ModelPart> bodyParts() {
        return Iterables.concat(super.bodyParts(), ImmutableList.of(base));
    }
}
