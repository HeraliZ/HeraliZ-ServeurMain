package fr.heraliz.armory.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.entity.Mannequin;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Mannequin : silhouette humaine standard (memes proportions que le joueur, donc toutes les armures tombent juste),
 * pose pilotee par les rotations du porte-armure, sur un socle. Le corps est releve d'un pixel pour poser sur le socle.
 */
public class MannequinModel extends HumanoidModel<Mannequin> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(HeralizArmory.id("mannequin"), "main");
    private static final float DEG = (float) Math.PI / 180.0F;
    private final ModelPart base;

    public MannequinModel(ModelPart root) {
        super(root);
        this.base = root.getChild("base");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 32).addBox(-6.0F, 0.0F, -6.0F, 12.0F, 1.0F, 12.0F),
                PartPose.offset(0.0F, 23.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(Mannequin e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        crouching = false;
        riding = false;
        swimAmount = 0.0F;
        attackTime = 0.0F;
        pose(head, 0.0F, -1.0F, e.getHeadPose().getX(), e.getHeadPose().getY(), e.getHeadPose().getZ());
        pose(body, 0.0F, -1.0F, e.getBodyPose().getX(), e.getBodyPose().getY(), e.getBodyPose().getZ());
        pose(rightArm, -5.0F, 1.0F, e.getRightArmPose().getX(), e.getRightArmPose().getY(), e.getRightArmPose().getZ());
        pose(leftArm, 5.0F, 1.0F, e.getLeftArmPose().getX(), e.getLeftArmPose().getY(), e.getLeftArmPose().getZ());
        pose(rightLeg, -1.9F, 11.0F, e.getRightLegPose().getX(), e.getRightLegPose().getY(), e.getRightLegPose().getZ());
        pose(leftLeg, 1.9F, 11.0F, e.getLeftLegPose().getX(), e.getLeftLegPose().getY(), e.getLeftLegPose().getZ());
        hat.copyFrom(head);
        base.visible = !e.isNoBasePlate();
    }

    private static void pose(ModelPart p, float x, float y, float rx, float ry, float rz) {
        p.x = x;
        p.y = y;
        p.z = 0.0F;
        p.xRot = DEG * rx;
        p.yRot = DEG * ry;
        p.zRot = DEG * rz;
    }

    @Override
    protected Iterable<ModelPart> bodyParts() {
        return Iterables.concat(super.bodyParts(), ImmutableList.of(base));
    }
}
