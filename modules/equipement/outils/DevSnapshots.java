package fr.heraliz.composants.dev;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import fr.heraliz.composants.HeralizComposants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Outil de dev (exclu du jar) : au premier affichage de l'ecran titre, rend chaque item
 * en premiere personne (main droite) comme en jeu et enregistre run/snapshots/<id>_fp.png,
 * puis ferme le jeu. Active uniquement avec -Dheraliz.snapshots=true.
 */
@Mod.EventBusSubscriber(modid = HeralizComposants.MODID, value = Dist.CLIENT)
public final class DevSnapshots {
    private static boolean done;

    private DevSnapshots() {}

    private static void marker(Minecraft mc, PoseStack ps, MultiBufferSource buf, Item item, float unused) {
        ps.pushPose();
        ps.scale(0.25f, 0.25f, 0.25f);
        mc.getItemRenderer().renderStatic(new ItemStack(item), ItemDisplayContext.FIXED,
                0xF000F0, OverlayTexture.NO_OVERLAY, ps, buf, null, 0);
        ps.popPose();
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (done || !Boolean.getBoolean("heraliz.snapshots") || mc.getOverlay() != null
                || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        done = true;
        try {
            Path out = mc.gameDirectory.toPath().resolve("snapshots");
            Files.createDirectories(out);
            int w = 960, h = 540;
            TextureTarget target = new TextureTarget(w, h, true, Minecraft.ON_OSX);
            target.setClearColor(0.42f, 0.55f, 0.68f, 1f);
            java.util.List<Item> items = new java.util.ArrayList<>();
            HeralizComposants.ALL.forEach(ro -> items.add(ro.get()));
            items.add(net.minecraft.world.item.Items.IRON_SWORD);
            items.add(net.minecraft.world.item.Items.APPLE);
            items.add(net.minecraft.world.item.Items.COMPASS);
            items.add(net.minecraft.world.item.Items.TORCH);
            items.add(net.minecraft.world.item.Items.STICK);
            for (Item item : items) {
                ItemStack stack = new ItemStack(item);
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                RenderSystem.backupProjectionMatrix();
                RenderSystem.setProjectionMatrix(new Matrix4f().setPerspective(
                        (float) Math.toRadians(70), (float) w / h, 0.05f, 100f), VertexSorting.DISTANCE_TO_ORIGIN);
                PoseStack mv = RenderSystem.getModelViewStack();
                mv.pushPose();
                mv.setIdentity();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.enableDepthTest();
                Lighting.setupFor3DItems();

                PoseStack ps = new PoseStack();
                // meme placement que ItemInHandRenderer.applyItemArmTransform (main droite, au repos)
                ps.translate(0.56f, -0.52f, -0.72f);
                MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
                mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                        0xF000F0, OverlayTexture.NO_OVERLAY, ps, buf, null, 0);
                buf.endBatch();

                NativeImage img = Screenshot.takeScreenshot(target);
                String name = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item).getPath();
                img.writeToFile(out.resolve(name + "_fp.png"));
                img.close();

                mv.popPose();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.restoreProjectionMatrix();
            }
            // vue exterieure : un joueur (skin par defaut) tient l'objet en main droite
            net.minecraft.client.model.PlayerModel<?> body = new net.minecraft.client.model.PlayerModel<>(
                    mc.getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER), false);
            body.young = false; // sinon le corps est dessine en taille enfant
            body.rightArm.xRot = (float) (-Math.PI / 10); // pose "tient un objet" au repos
            body.leftArm.xRot = (float) (-Math.PI / 10);
            for (Item item : items) {
                ItemStack stack = new ItemStack(item);
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                RenderSystem.backupProjectionMatrix();
                RenderSystem.setProjectionMatrix(new Matrix4f().setPerspective(
                        (float) Math.toRadians(50), (float) w / h, 0.05f, 100f), VertexSorting.DISTANCE_TO_ORIGIN);
                PoseStack mv = RenderSystem.getModelViewStack();
                mv.pushPose();
                mv.setIdentity();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.enableDepthTest();
                Lighting.setupFor3DItems();

                PoseStack ps = new PoseStack();
                ps.translate(0.0f, -1.0f, -3.6f);
                ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(150));
                ps.scale(-1f, -1f, 1f); // comme LivingEntityRenderer
                ps.translate(0f, -1.501f, 0f);
                MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
                body.renderToBuffer(ps, buf.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(
                                net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin())),
                        0xF000F0, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
                // comme ItemInHandLayer.renderArmWithItem
                ps.pushPose();
                body.translateToHand(net.minecraft.world.entity.HumanoidArm.RIGHT, ps);
                if (item == net.minecraft.world.item.Items.STICK) { // reperes : pomme = epaule, compas = main
                    marker(mc, ps, buf, net.minecraft.world.item.Items.APPLE, 0f);
                }
                ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90f));
                ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
                ps.translate(1f / 16f, 0.125f, -0.625f);
                if (item == net.minecraft.world.item.Items.STICK) {
                    marker(mc, ps, buf, net.minecraft.world.item.Items.COMPASS, 0f);
                }
                mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                        0xF000F0, OverlayTexture.NO_OVERLAY, ps, buf, null, 0);
                ps.popPose();
                // meme objet en main gauche
                body.translateToHand(net.minecraft.world.entity.HumanoidArm.LEFT, ps);
                ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90f));
                ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
                ps.translate(-1f / 16f, 0.125f, -0.625f);
                mc.getItemRenderer().renderStatic(null, stack, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, true,
                        ps, buf, null, 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
                buf.endBatch();

                NativeImage img = Screenshot.takeScreenshot(target);
                img.writeToFile(out.resolve(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item).getPath() + "_tp.png"));
                img.close();
                mv.popPose();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.restoreProjectionMatrix();
            }
            target.destroyBuffers();
            mc.getMainRenderTarget().bindWrite(true);
            // couleur du nom dans l'infobulle (code couleur des raretes)
            StringBuilder rar = new StringBuilder();
            for (Item item : items) {
                var line = new ItemStack(item).getTooltipLines(null, net.minecraft.world.item.TooltipFlag.Default.NORMAL).get(0);
                rar.append(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item).getPath()).append(' ')
                        .append(line.getString()).append(" -> ").append(line.getStyle().getColor()).append('\n');
            }
            Files.writeString(out.resolve("raretes.txt"), rar.toString());
            Files.writeString(out.resolve("done.txt"), "ok");
        } catch (Exception e) {
            HeralizComposants.LOGGER.error("Snapshots", e);
        }
        mc.stop();
    }
}
