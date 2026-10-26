package com.hbm_m.client.render.implementations;

import com.hbm_m.block.machines.TransitionSealBlock;
import com.hbm_m.blockentity.machines.TransitionSealBlockEntity;
import com.hbm_m.client.loader.dae.DaeAnimation;
import com.hbm_m.client.loader.dae.DaeModel;
import com.hbm_m.client.loader.dae.DaeNode;
import com.hbm_m.client.loader.dae.DaeQuadBaker;
import com.hbm_m.client.render.MeshRenderCache;
import com.hbm_m.client.render.SingleMeshVboRenderer;
import com.hbm_m.client.render.shader.IrisRenderBatch;
import com.hbm_m.client.render.shader.ShaderCompatibilityDetector;
import com.hbm_m.lib.RefStrings;
import com.hbm_m.main.MainRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public class TransitionSealRenderer implements BlockEntityRenderer<TransitionSealBlockEntity> {
   private static final String CLIP_NAME = "animation";
   private static final ResourceLocation MODEL_ID = RefStrings.resourceLocation("models/block/doors/transition_seal");
   private static final ResourceLocation SEAL_TEX = RefStrings.resourceLocation("block/doors/transition_seal");
   private static DaeModel model;
   private static boolean modelFailed;
   private static ResourceLocation resolvedModelFile;
   private static ResourceLocation resolvedTexture;

   public TransitionSealRenderer(Context context) {
   }

   public boolean shouldRenderOffScreen(TransitionSealBlockEntity be) {
      return true;
   }

   public void render(TransitionSealBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
      init();
      if (model != null) {
         DaeAnimation clip = getClip();
         if (clip != null) {
            Direction facing = (Direction)be.getBlockState().getValue(TransitionSealBlock.FACING);
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.0F, 0.5F);
            switch (facing) {
               case NORTH:
                  poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
                  break;
               case SOUTH:
                  poseStack.mulPose(Axis.YP.rotationDegrees(270.0F));
                  break;
               case WEST:
                  poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            }

            poseStack.translate(0.0F, 0.0F, 0.5F);
            float time = be.getAnimationTime(partialTick);
            boolean iris = ShaderCompatibilityDetector.isExternalShaderActive();
            if (iris) {
               try (IrisRenderBatch batch = IrisRenderBatch.begin(ShaderCompatibilityDetector.isRenderingShadowPass(), RenderSystem.getProjectionMatrix())) {
                  renderNodes(model.sceneRoots, clip, time, poseStack, packedLight, be, bufferSource);
               }
            } else {
               renderNodes(model.sceneRoots, clip, time, poseStack, packedLight, be, bufferSource);
            }

            poseStack.popPose();
         }
      }
   }

   private static void renderNodes(
      List<DaeNode> nodes, DaeAnimation clip, float time, PoseStack poseStack, int packedLight, TransitionSealBlockEntity be, MultiBufferSource bufferSource
   ) {
      for (DaeNode node : nodes) {
         poseStack.pushPose();
         poseStack.mulPoseMatrix(node.localMatrix(time, clip));
         if (node.mesh != null) {
            SingleMeshVboRenderer renderer = getRendererForNode(node);
            if (renderer != null) {
               renderer.render(poseStack, packedLight, be.getBlockPos(), be, bufferSource);
            }
         }

         renderNodes(node.children, clip, time, poseStack, packedLight, be, bufferSource);
         poseStack.popPose();
      }
   }

   private static SingleMeshVboRenderer getRendererForNode(DaeNode node) {
      String key = "transition_seal:" + node.name;

      try {
         ResourceLocation texture = node.texture != null ? node.texture : SEAL_TEX;
         if (texture != null) {
            resolvedTexture = texture;
         }

         TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(texture);
         List<BakedQuad> quads = bakeNodeQuads(node, sprite);
         return quads.isEmpty() ? null : MeshRenderCache.getOrCreateRendererFromQuadList(key, quads);
      } catch (Exception e) {
         MainRegistry.LOGGER.error("TransitionSealRenderer: failed to bake node '{}'", node.name, e);
         return null;
      }
   }

   private static List<BakedQuad> bakeNodeQuads(DaeNode node, TextureAtlasSprite sprite) {
      return DaeQuadBaker.bakeNodeQuads(node.mesh, new Matrix4f(), sprite);
   }

   private static void init() {
      if (model == null && !modelFailed) {
         try {
            model = DaeModel.load(MODEL_ID);
            resolvedModelFile = new ResourceLocation(model.resource.getNamespace(), model.resource.getPath() + ".dae");
            resolvedTexture = findFirstTexture(model.sceneRoots);
            if (resolvedTexture == null && !model.textures.isEmpty()) {
               resolvedTexture = model.textures.values().iterator().next();
            }

            if (resolvedTexture == null) {
               resolvedTexture = SEAL_TEX;
            }
         } catch (Exception e) {
            modelFailed = true;
            MainRegistry.LOGGER.error("TransitionSealRenderer: failed to load DAE model", e);
         }
      }
   }

   public static String getDebugInfo() {
      init();
      StringBuilder sb = new StringBuilder("Transition seal debug\n");
      sb.append("Model file: ").append(resolvedModelFile != null ? resolvedModelFile : "<not loaded>");
      sb.append("\nTexture: ").append(resolvedTexture != null ? resolvedTexture : "<not loaded>");
      if (Minecraft.getInstance() != null && resolvedTexture != null) {
         try {
            TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(resolvedTexture);
            sb.append("\nSprite present: ").append(sprite != null);
            if (sprite != null) {
               SpriteContents contents = sprite.contents();
               sb.append("\nSprite size: ").append(contents.width()).append("x").append(contents.height());
               sb.append("\nSprite UV: ")
                  .append(sprite.getU0())
                  .append("..")
                  .append(sprite.getU1())
                  .append(" / ")
                  .append(sprite.getV0())
                  .append("..")
                  .append(sprite.getV1());
            }
         } catch (Exception e) {
            sb.append("\nSprite lookup failed: ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
         }
      }

      if (model != null) {
         sb.append("\nDAE resource: ").append(model.resource);
      }

      if (modelFailed) {
         sb.append("\nStatus: load failed");
      }

      return sb.toString();
   }

   private static ResourceLocation findFirstTexture(List<DaeNode> nodes) {
      for (DaeNode node : nodes) {
         if (node.texture != null) {
            return node.texture;
         }

         ResourceLocation found = findFirstTexture(node.children);
         if (found != null) {
            return found;
         }
      }

      return null;
   }

   private static DaeAnimation getClip() {
      DaeAnimation clip = model.animations.get("animation");
      if (clip == null && !model.animations.isEmpty()) {
         clip = model.animations.values().iterator().next();
      }

      return clip;
   }
}
