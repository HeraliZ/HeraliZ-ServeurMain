package com.heraliz.extraction.preview;

import com.heraliz.extraction.TerminalGeometry;
import com.heraliz.extraction.client.KneelLegs;
import com.heraliz.extraction.client.TerminalModel;
import com.heraliz.extraction.client.TerminalPoses;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;

/**
 * Génère des aperçus PNG du terminal en utilisant exactement le même code de géométrie
 * et de poses que le jeu. Usage : gradlew renderPreview → build/preview/*.png
 */
public final class PreviewRenderer {
    private static final int LIGHT = 0x00A000A0;
    private static final int OVERLAY = 0;
    private static final int W = 640;
    private static final int H = 480;

    private static BufferedImage atlas;
    private static BufferedImage skin;

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        File resources = new File(args[1]);
        out.mkdirs();
        atlas = ImageIO.read(new File(resources, "assets/heraliz_extraction/textures/entity/extraction_terminal.png"));
        skin = loadSkin();

        sheet(out, "sheet.png",
                new BufferedImage[]{
                        ground(new Vector3f(-0.9F, 1.1F, -1.4F)),
                        gui(),
                        thirdPerson(new Vector3f(1.3F, 1.5F, 2.3F), new Vector3f(0, 1.0F, 0), false, false),
                        thirdPerson(new Vector3f(1.3F, 1.3F, 2.3F), new Vector3f(0, 0.75F, 0), true, true),
                        thirdPerson(null, null, true, true),
                        thirdPerson(new Vector3f(-1.6F, 1.2F, 2.0F), new Vector3f(0, 0.7F, 0.2F), true, true)},
                new String[]{"Au sol (cote joueur)", "Inventaire", "3e personne : tient (debout)",
                        "3e personne : tape (a genoux)", "A genoux (profil exact)", "A genoux (autre angle)"});

        sheet(out, "first_person.png",
                new BufferedImage[]{firstPerson(0.0F), firstPerson(1.2F), firstPerson(2.4F), firstPerson(5.0F)},
                new String[]{"1re personne t=0", "t=1.2", "t=2.4", "t=5.0"});
        System.out.println("Apercus generes dans " + out.getAbsolutePath());
    }

    private static void sheet(File dir, String name, BufferedImage[] images, String[] labels) throws Exception {
        int rows = (images.length + 1) / 2;
        BufferedImage sheet = new BufferedImage(W * 2, H * rows, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        for (int i = 0; i < images.length; i++) {
            int x = (i % 2) * W, y = (i / 2) * H;
            g.drawImage(images[i], x, y, null);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            g.drawString(labels[i], x + 10, y + 26);
        }
        g.dispose();
        ImageIO.write(sheet, "png", new File(dir, name));
    }

    // ------------------------------------------------------------------ vues

    /** Terminal posé au sol, caisse ouverte, fumigène déployé. */
    private static BufferedImage ground(Vector3f eye) {
        SoftRaster r = new SoftRaster();
        groundGrid(r);
        PoseStack pose = new PoseStack();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        float s = TerminalGeometry.GROUND_SCALE / 16.0F;
        pose.scale(s, s, s);
        TerminalModel.renderGeometry(pose, r.consumer(atlas), LIGHT, OVERLAY, new TerminalModel.State(100, 1, 1, true));
        return r.render(camera(eye, new Vector3f(0.15F, 0.3F, 0.0F), 40), W, H, 0x8FB8E8, 0xD8E8F4);
    }

    /** Icône d'inventaire : mêmes transformations que le JSON (gui) + le renderer d'item. */
    private static BufferedImage gui() {
        SoftRaster r = new SoftRaster();
        PoseStack pose = new PoseStack();
        pose.mulPose(new Quaternionf().rotationXYZ((float) Math.toRadians(25), (float) Math.toRadians(-30), 0));
        pose.scale(0.95F, 0.95F, 0.95F);
        pose.translate(-0.5F, -0.5F, -0.5F);
        pose.translate(0.5F, 0.25F, 0.5F);
        float s = 0.92F / TerminalGeometry.MODEL_WIDTH;
        pose.scale(s, s, s);
        pose.translate(-TerminalGeometry.MODEL_CENTER_X, 0.0F, 1.0F);
        TerminalModel.renderGeometry(pose, r.consumer(atlas), LIGHT, OVERLAY, TerminalModel.State.HELD);
        Matrix4f ortho = new Matrix4f().ortho(-0.6F * W / H, 0.6F * W / H, -0.6F, 0.6F, -10, 10);
        return r.render(ortho, W, H, 0x8B8B8B, 0x8B8B8B);
    }

    /**
     * Joueur qui tient le PC (mêmes transformations que le rendu des joueurs).
     * {@code kneeling} reproduit l'accroupi vanilla (-0.125) + la descente et les jambes à genoux.
     */
    private static BufferedImage thirdPerson(Vector3f eye, Vector3f target, boolean typing, boolean kneeling) {
        SoftRaster r = new SoftRaster();
        groundGrid(r);

        ModelPart root = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot();
        PlayerModel<LivingEntity> model = new PlayerModel<>(root, false);
        model.young = false;
        float headPitch = 0.45F;
        model.head.xRot = headPitch;
        model.hat.copyFrom(model.head);
        TerminalPoses.applyArms(model.rightArm, model.leftArm, headPitch, 5.0F, typing);
        if (kneeling) {
            TerminalPoses.applyKneelLegs(model.rightLeg, model.leftLeg);
        }
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.visible = model.rightLeg.visible;
        model.leftPants.visible = model.leftLeg.visible;

        PoseStack pose = new PoseStack();
        if (kneeling) {
            pose.translate(0.0F, -KneelLegs.DROP, 0.0F);
        }
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.scale(0.9375F, 0.9375F, 0.9375F);
        pose.translate(0.0F, -1.501F, 0.0F);
        model.renderToBuffer(pose, r.consumer(skin), LIGHT, OVERLAY, 1, 1, 1, 1);
        if (kneeling) {
            KneelLegs.get().render(pose, r.consumer(skin), LIGHT, OVERLAY, true, true);
        }

        pose.pushPose();
        TerminalPoses.applyHeldTransform(pose, model.body);
        TerminalModel.renderGeometry(pose, r.consumer(atlas), LIGHT, OVERLAY, TerminalModel.State.HELD);
        pose.popPose();

        Matrix4f cam = eye == null
                ? new Matrix4f().ortho(-1.2F * W / H, 1.2F * W / H, -1.2F, 1.2F, -10, 10)
                        .mul(new Matrix4f().lookAt(new Vector3f(5, 1, 0), new Vector3f(0, 1, 0), new Vector3f(0, 1, 0)))
                : camera(eye, target, 45);
        return r.render(cam, W, H, 0x8FB8E8, 0xD8E8F4);
    }

    /** Vue à la 1re personne pendant la saisie, à l'instant {@code time} (ticks). */
    private static BufferedImage firstPerson(float time) {
        SoftRaster r = new SoftRaster();
        PoseStack pose = new PoseStack();
        pose.pushPose();
        TerminalPoses.applyFirstPersonTransform(pose, 0.0F);
        TerminalModel.renderGeometry(pose, r.consumer(atlas), LIGHT, OVERLAY, TerminalModel.State.HELD);
        pose.popPose();

        ModelPart root = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot();
        PlayerModel<LivingEntity> model = new PlayerModel<>(root, false);
        for (boolean right : new boolean[]{true, false}) {
            ModelPart arm = right ? model.rightArm : model.leftArm;
            ModelPart sleeve = right ? model.rightSleeve : model.leftSleeve;
            pose.pushPose();
            TerminalPoses.applyFirstPersonHand(pose, right, time, 0.0F);
            arm.x = arm.y = arm.z = 0;
            arm.xRot = arm.yRot = arm.zRot = 0;
            sleeve.copyFrom(arm);
            arm.render(pose, r.consumer(skin), LIGHT, OVERLAY);
            sleeve.render(pose, r.consumer(skin), LIGHT, OVERLAY);
            pose.popPose();
        }
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70), (float) W / H, 0.05F, 100);
        return r.render(proj, W, H, 0x6F8F5F, 0x4F6F3F);
    }

    // ------------------------------------------------------------------ utilitaires

    /** Sol en petites dalles (le rastériseur ne découpe pas les triangles derrière la caméra). */
    private static void groundGrid(SoftRaster r) {
        for (float x = -3; x < 3; x += 0.25F) {
            for (float z = -3; z < 3; z += 0.25F) {
                float shade = ((int) ((x + 3) * 4) + (int) ((z + 3) * 4)) % 2 == 0 ? 0.0F : 0.02F;
                r.solidQuad(new float[][]{{x, 0, z}, {x, 0, z + 0.25F}, {x + 0.25F, 0, z + 0.25F}, {x + 0.25F, 0, z}},
                        0.33F + shade, 0.48F + shade, 0.25F);
            }
        }
    }

    private static Matrix4f camera(Vector3f eye, Vector3f target, float fov) {
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(fov), (float) W / H, 0.05F, 200);
        Matrix4f view = new Matrix4f().lookAt(eye, target, new Vector3f(0, 1, 0));
        return proj.mul(view);
    }

    private static BufferedImage loadSkin() throws Exception {
        try (InputStream in = PreviewRenderer.class.getResourceAsStream("/assets/minecraft/textures/entity/player/wide/steve.png")) {
            if (in != null) {
                return ImageIO.read(in);
            }
        }
        System.out.println("Skin Steve introuvable, skin de remplacement.");
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x3A6EA5));
        g.fillRect(0, 0, 64, 64);
        g.setColor(new Color(0xC69C7B));
        g.fillRect(0, 0, 32, 16);
        g.fillRect(40, 16, 16, 16);
        g.fillRect(32, 48, 16, 16);
        g.dispose();
        return img;
    }
}
