package com.heraliz.extraction.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Jambes "à genoux" : les jambes Minecraft n'ont pas de genou, on les redécoupe donc en
 * cuisse (moitié haute de la texture) + tibia (moitié basse), avec la texture du skin.
 * Cuisses verticales, tibias à plat au sol vers l'arrière.
 * <p>
 * Le corps doit être descendu de {@link #DROP} pour que les tibias touchent le sol.
 */
public final class KneelLegs {
    /** Descente totale du joueur (en blocs) : 4 pixels de modèle * 0.9375 (échelle du joueur). */
    public static final float DROP = 4.0F * 0.9375F / 16.0F;

    private static KneelLegs instance;

    private final ModelPart rightThigh;
    private final ModelPart leftThigh;
    private final ModelPart rightThighPants;
    private final ModelPart leftThighPants;

    private KneelLegs(ModelPart root) {
        this.rightThigh = root.getChild("right_thigh");
        this.leftThigh = root.getChild("left_thigh");
        this.rightThighPants = root.getChild("right_thigh_pants");
        this.leftThighPants = root.getChild("left_thigh_pants");
    }

    public static KneelLegs get() {
        if (instance == null) {
            instance = new KneelLegs(createLayer().bakeRoot());
        }
        return instance;
    }

    /** Mêmes zones de texture que les jambes du skin (64x64), coupées en deux à la hauteur du genou. */
    private static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation pants = new CubeDeformation(0.25F);
        addLeg(root, "right_thigh", -1.9F, 0, 16, CubeDeformation.NONE);
        addLeg(root, "left_thigh", 1.9F, 16, 48, CubeDeformation.NONE);
        addLeg(root, "right_thigh_pants", -1.9F, 0, 32, pants);
        addLeg(root, "left_thigh_pants", 1.9F, 0, 48, pants);
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void addLeg(PartDefinition root, String name, float x, int u, int v, CubeDeformation deformation) {
        // cuisse : du bassin au genou (6 px), moitié haute de la jambe du skin
        PartDefinition thigh = root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v).addBox(-2.0F, 0.0F, -2.0F, 4, 6, 4, deformation),
                PartPose.offset(x, 12.0F, 0.1F));
        // tibia : part du genou et se couche vers l'arrière, moitié basse de la jambe du skin
        thigh.addOrReplaceChild("shin",
                CubeListBuilder.create().texOffs(u, v + 6).addBox(-2.0F, -2.0F, -2.0F, 4, 6, 4, deformation),
                PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, Mth.HALF_PI, 0.0F, 0.0F));
    }

    /** Dessine les deux jambes pliées dans l'espace du modèle du joueur. */
    public void render(PoseStack pose, VertexConsumer skin, int light, int overlay, boolean rightPants, boolean leftPants) {
        this.rightThigh.xRot = 0.05F;
        this.rightThigh.zRot = 0.05F;
        this.leftThigh.xRot = 0.05F;
        this.leftThigh.zRot = -0.05F;
        this.rightThighPants.copyFrom(this.rightThigh);
        this.leftThighPants.copyFrom(this.leftThigh);

        this.rightThigh.render(pose, skin, light, overlay);
        this.leftThigh.render(pose, skin, light, overlay);
        if (rightPants) {
            this.rightThighPants.render(pose, skin, light, overlay);
        }
        if (leftPants) {
            this.leftThighPants.render(pose, skin, light, overlay);
        }
    }
}
