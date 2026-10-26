package fr.heraliz.casino.client.zone;

import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.entity.GuardEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Modele du videur : une carrure de joueur, dont les bras prennent la pose de son attitude (bras croises a son poste,
 * main levee pour arreter un resquilleur, bras tendus pour pousser un fauche, elan, lancer, mains epoussetees).
 * Quelques details en relief cassent le cube : nez, oreilles, et selon le videur un bonnet de laine ou des cheveux ras ;
 * poches et radio sur le gilet, genouillere.
 */
public class GuardModel extends PlayerModel<GuardEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(HeralizCasino.id("videur"), "main");

    private final ModelPart[] beanie, hair;

    public GuardModel(ModelPart root) {
        super(root, false);
        ModelPart h = root.getChild("head");
        beanie = new ModelPart[]{h.getChild("beanie_brim"), h.getChild("beanie_crown"), h.getChild("beanie_top"), h.getChild("beanie_tip")};
        hair = new ModelPart[]{h.getChild("hair_cap"), h.getChild("hair_back")};
    }

    /** Joueur (bras larges) + details ; texture 128 x 128 (l'apparence de joueur occupe le quart haut-gauche). */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
        PartDefinition root = mesh.getRoot(), head = root.getChild("head"), body = root.getChild("body");
        // nez (arete et bout) et oreilles
        head.addOrReplaceChild("nose_bridge", CubeListBuilder.create().texOffs(64, 0).addBox(-0.5f, -4.3f, -4.6f, 1f, 1.1f, 0.6f), PartPose.ZERO);
        head.addOrReplaceChild("nose_tip", CubeListBuilder.create().texOffs(69, 0).addBox(-0.8f, -3.3f, -4.95f, 1.6f, 1.0f, 1.0f), PartPose.ZERO);
        head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(76, 0).addBox(-4.7f, -4.9f, -0.6f, 0.7f, 2.0f, 1.2f), PartPose.ZERO);
        head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(82, 0).addBox(4.0f, -4.9f, -0.6f, 0.7f, 2.0f, 1.2f), PartPose.ZERO);
        // bonnet de laine : revers, calotte, dessus, pointe (arrondi en gradins)
        head.addOrReplaceChild("beanie_brim", CubeListBuilder.create().texOffs(64, 8).addBox(-4.5f, -7.2f, -4.5f, 9f, 1.8f, 9f), PartPose.ZERO);
        head.addOrReplaceChild("beanie_crown", CubeListBuilder.create().texOffs(64, 20).addBox(-4.3f, -8.8f, -4.3f, 8.6f, 1.7f, 8.6f), PartPose.ZERO);
        head.addOrReplaceChild("beanie_top", CubeListBuilder.create().texOffs(64, 32).addBox(-3.6f, -9.5f, -3.6f, 7.2f, 0.8f, 7.2f), PartPose.ZERO);
        head.addOrReplaceChild("beanie_tip", CubeListBuilder.create().texOffs(94, 32).addBox(-2.2f, -10.0f, -2.2f, 4.4f, 0.6f, 4.4f), PartPose.ZERO);
        // cheveux ras en relief : calotte et nuque (derriere les oreilles)
        head.addOrReplaceChild("hair_cap", CubeListBuilder.create().texOffs(64, 42).addBox(-4.25f, -8.45f, -4.25f, 8.5f, 1.9f, 8.5f), PartPose.ZERO);
        head.addOrReplaceChild("hair_back", CubeListBuilder.create().texOffs(64, 54).addBox(-4.25f, -6.6f, 0.7f, 8.5f, 3.2f, 3.55f), PartPose.ZERO);
        // gilet : trois poches, vieille radio et son antenne
        for (int i = 0; i < 3; i++) {
            float x = -2.6f + 2.6f * i;
            body.addOrReplaceChild("pouch_" + i, CubeListBuilder.create().texOffs(i * 8, 64).addBox(x - 1.1f, 7.0f, -3.2f, 2.2f, 2.9f, 0.95f), PartPose.ZERO);
        }
        body.addOrReplaceChild("radio", CubeListBuilder.create().texOffs(24, 64).addBox(-3.6f, 4.3f, -3.1f, 2.0f, 2.3f, 0.85f), PartPose.ZERO);
        body.addOrReplaceChild("antenna", CubeListBuilder.create().texOffs(32, 64).addBox(-2.3f, 1.2f, -2.8f, 0.3f, 3.1f, 0.3f), PartPose.ZERO);
        // genouillere
        root.getChild("right_leg").addOrReplaceChild("knee_pad", CubeListBuilder.create().texOffs(36, 64).addBox(-1.9f, 4.2f, -2.95f, 3.8f, 3.0f, 0.75f),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(GuardEntity e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
        super.setupAnim(e, limbSwing, limbSwingAmount, age, headYaw, headPitch);
        // l'un porte un bonnet, l'autre montre ses cheveux ras
        boolean cap = e.side() > 0;
        for (ModelPart p : beanie) p.visible = cap;
        for (ModelPart p : hair) p.visible = !cap;
        // ticks depuis le dernier changement d'attitude (fondu des gestes), cote dans la paire
        float since = e.stanceAge + Minecraft.getInstance().getFrameTime();
        int side = e.side();
        switch (e.stance()) {
            case GuardEntity.STOP -> {
                // "halte" : le bras droit tendu, paume en avant ; l'autre poing sur la hanche
                float k = ease(since / 5f);
                pose(rightArm, Mth.lerp(k, -0.95f, -1.5f) + 0.05f * Mth.sin(age * 0.35f), Mth.lerp(k, -0.72f, -0.1f), 0);
                pose(leftArm, 0.12f, 0, -0.38f);
            }
            case GuardEntity.RUN -> {
                // course : les bras pompent plus fort que ceux d'un marcheur
                rightArm.xRot *= 1.35f;
                leftArm.xRot *= 1.35f;
                rightArm.zRot = 0.18f;
                leftArm.zRot = -0.18f;
            }
            case GuardEntity.PUSH -> {
                // il tient le joueur : le bras du cote du joueur l'agrippe, l'autre le pousse par a-coups
                float shove = 0.2f * Mth.sin(age * 0.95f + (side > 0 ? 0 : 2f));
                ModelPart inner = side > 0 ? leftArm : rightArm, outer = side > 0 ? rightArm : leftArm;
                float in = side > 0 ? 1 : -1;
                pose(inner, -1.38f, in * 0.5f, 0);
                pose(outer, -1.18f + shove, in * -0.22f, 0);
            }
            case GuardEntity.WINDUP -> {
                // elan : les deux bras tires en arriere
                float k = ease(since / 8f);
                pose(rightArm, Mth.lerp(k, -1.3f, 0.75f), -0.1f, 0.3f);
                pose(leftArm, Mth.lerp(k, -1.3f, 0.75f), 0.1f, -0.3f);
            }
            case GuardEntity.THROW -> {
                // lancer : les bras partent d'un coup vers l'avant, et y restent
                float k = ease(since / 3f);
                pose(rightArm, Mth.lerp(k, 0.75f, -1.65f), -0.05f, 0.12f);
                pose(leftArm, Mth.lerp(k, 0.75f, -1.65f), 0.05f, -0.12f);
            }
            case GuardEntity.DUST -> {
                // il s'epoussette les mains, l'une contre l'autre
                float clap = 0.22f * Mth.sin(age * 1.3f);
                pose(rightArm, -1.05f + clap * 0.4f, -0.62f + clap, 0);
                pose(leftArm, -1.05f - clap * 0.4f, 0.62f - clap, 0);
            }
            case GuardEntity.POINT -> {
                // "entre" : le bras du cote de la porte la montre, l'autre poing sur la hanche
                float k = ease(since / 5f);
                if (e.pointsLeft()) {
                    pose(leftArm, Mth.lerp(k, -0.98f, -1.35f), Mth.lerp(k, 0.72f, -0.95f), 0);
                    pose(rightArm, 0.12f, 0, 0.38f);
                } else {
                    pose(rightArm, Mth.lerp(k, -0.95f, -1.35f), Mth.lerp(k, -0.72f, 0.95f), 0);
                    pose(leftArm, 0.12f, 0, -0.38f);
                }
            }
            default -> {
                // a son poste : bras croises, respiration lente
                float breath = 0.03f * Mth.sin(age * 0.07f);
                pose(rightArm, -0.95f + breath, -0.72f, 0.08f);
                pose(leftArm, -0.98f + breath, 0.72f, -0.08f);
            }
        }
        // les couches du costume (manches, veste) suivent les membres
        rightSleeve.copyFrom(rightArm);
        leftSleeve.copyFrom(leftArm);
        jacket.copyFrom(body);
        hat.copyFrom(head);
    }

    private static void pose(ModelPart part, float x, float y, float z) {
        part.xRot = x;
        part.yRot = y;
        part.zRot = z;
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
