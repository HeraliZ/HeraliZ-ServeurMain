package com.hbm_m.client.render;

import com.hbm_m.client.render.shader.IrisExtendedShaderAccess;
import com.hbm_m.client.render.shader.ShaderCompatibilityDetector;
import com.hbm_m.main.MainRegistry;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.nio.Buffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
final class VanillaInstancedBatchRenderer {
   private final InstancedStaticPartRenderer parent;
   private ShaderInstance cachedShader = null;
   private int cachedShaderProgramId = -1;
   private long cachedPipelineGeneration = -1L;
   private Uniform uProjMat;
   private Uniform uModelView;
   private Uniform uFogStart;
   private Uniform uFogEnd;
   private Uniform uFogColor;
   Uniform uBrightness;
   Uniform uFadeAlpha;

   VanillaInstancedBatchRenderer(InstancedStaticPartRenderer parent) {
      this.parent = parent;
   }

   void updateUniformCache(ShaderInstance shader) {
      int programId = shader != null ? shader.getId() : -1;
      long currentGen = IrisExtendedShaderAccess.getPipelineGeneration();
      if (this.cachedShaderProgramId != programId || this.cachedShader != shader || this.cachedPipelineGeneration != currentGen || this.cachedShader == null) {
         this.cachedShader = shader;
         this.cachedShaderProgramId = programId;
         this.cachedPipelineGeneration = currentGen;
         this.uProjMat = shader.getUniform("ProjMat");
         this.uModelView = shader.getUniform("ModelViewMat");
         this.uFogStart = shader.getUniform("FogStart");
         this.uFogEnd = shader.getUniform("FogEnd");
         this.uFogColor = shader.getUniform("FogColor");
         this.uBrightness = shader.getUniform("Brightness");
         this.uFadeAlpha = shader.getUniform("FadeAlpha");
      }
   }

   void applyCommonUniforms(ShaderInstance shader, Matrix4f projectionMatrix, Matrix4f modelView) {
      this.updateUniformCache(shader);
      if (this.uProjMat != null) {
         this.uProjMat.set(projectionMatrix);
      } else if (shader.PROJECTION_MATRIX != null) {
         shader.PROJECTION_MATRIX.set(projectionMatrix);
      }

      if (this.uModelView != null) {
         this.uModelView.set(modelView);
      } else if (shader.MODEL_VIEW_MATRIX != null) {
         shader.MODEL_VIEW_MATRIX.set(modelView);
      }

      if (this.uFogStart != null) {
         this.uFogStart.set(RenderSystem.getShaderFogStart());
      }

      if (this.uFogEnd != null) {
         this.uFogEnd.set(RenderSystem.getShaderFogEnd());
      }

      if (this.uFogColor != null) {
         float[] fogColor = RenderSystem.getShaderFogColor();
         this.uFogColor.set(fogColor[0], fogColor[1], fogColor[2], fogColor[3]);
      }

      if (this.uFadeAlpha != null) {
         this.uFadeAlpha.set(SingleMeshVboRenderer.getFadeAlpha());
      }
   }

   void enableVertexAttribsForDraw() {
      for (int i = 0; i <= this.parent.instanceAttribLast; i++) {
         GL20.glEnableVertexAttribArray(i);
      }
   }

   void invalidateShaderCache() {
      this.cachedShader = null;
      this.cachedShaderProgramId = -1;
      this.cachedPipelineGeneration = -1L;
      this.uProjMat = null;
      this.uModelView = null;
      this.uFogStart = null;
      this.uFogEnd = null;
      this.uFogColor = null;
      this.uBrightness = null;
      this.uFadeAlpha = null;
   }

   Uniform getModelViewUniform() {
      return this.uModelView;
   }

   float brightnessFromUV(float blockU, float skyV, float cachedSkyDarken) {
      float blockLight = blockU / 16.0F;
      float skyLight = skyV / 16.0F;
      float skyDarken;
      if (cachedSkyDarken >= 0.0F && cachedSkyDarken <= 1.0F) {
         skyDarken = cachedSkyDarken;
      } else {
         ClientLevel level = Minecraft.getInstance().level;
         if (level == null) {
            return Math.max(0.05F, Math.max(blockLight, skyLight) / 15.0F);
         }

         skyDarken = level.getSkyDarken(1.0F);
      }

      float skyBrightness = 0.05F + skyDarken * 0.95F;
      float effectiveSkyLight = skyLight * skyBrightness;
      float maxLight = Math.max(blockLight, effectiveSkyLight);
      return 0.05F + maxLight / 15.0F * 0.95F;
   }

   float calculateBrightness(int packedLight) {
      return this.calculateBrightness(packedLight, Float.NaN);
   }

   float calculateBrightness(int packedLight, float cachedSkyDarken) {
      int blockLight = LightTexture.block(packedLight);
      int skyLight = LightTexture.sky(packedLight);
      float skyDarken;
      if (cachedSkyDarken >= 0.0F && cachedSkyDarken <= 1.0F) {
         skyDarken = cachedSkyDarken;
      } else {
         ClientLevel level = Minecraft.getInstance().level;
         if (level == null) {
            return Math.max(0.05F, Math.max(blockLight, skyLight) / 15.0F);
         }

         skyDarken = level.getSkyDarken(1.0F);
      }

      float skyBrightness = 0.05F + skyDarken * 0.95F;
      float effectiveSkyLight = skyLight * skyBrightness;
      float maxLight = Math.max(blockLight, effectiveSkyLight);
      return 0.05F + maxLight / 15.0F * 0.95F;
   }

   void uploadSingleInstance(PoseStack poseStack, int packedLight, @Nullable BlockEntity blockEntity) {
      this.parent.instanceBuffer.clear();
      Matrix4f mat = poseStack.last().pose();
      mat.getTranslation(this.parent.posTmp);
      mat.getNormalizedRotation(this.parent.rotTmp);
      BlockPos blockPosForSample = blockEntity != null ? blockEntity.getBlockPos() : BlockPos.ZERO;
      if (LightSampleCache.BASE_POSE_SET.get()) {
         this.parent.tmpLocalPose.set((Matrix4fc)LightSampleCache.BASE_POSE.get()).invert().mul(mat);
      } else {
         Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
         this.parent.tmpInvViewRot.identity().set(RenderSystem.getInverseViewRotationMatrix());
         this.parent.tmpLocalPose.set(this.parent.tmpInvViewRot).mul(mat);
         this.parent.tmpLocalPose.m30(this.parent.tmpLocalPose.m30() - (float)(blockPosForSample.getX() - cam.x));
         this.parent.tmpLocalPose.m31(this.parent.tmpLocalPose.m31() - (float)(blockPosForSample.getY() - cam.y));
         this.parent.tmpLocalPose.m32(this.parent.tmpLocalPose.m32() - (float)(blockPosForSample.getZ() - cam.z));
      }

      long partHash = System.identityHashCode(this.parent);
      if (this.parent.useSlicedLight) {
         LightSampleCache.getOrSample16(
            blockEntity, partHash, this.parent.objBbox, blockPosForSample, this.parent.tmpLocalPose, packedLight, this.parent.tmpCornerUV
         );
      } else {
         LightSampleCache.getOrSample8(
            blockEntity, partHash, this.parent.objBbox, blockPosForSample, this.parent.tmpLocalPose, packedLight, this.parent.tmpCornerUV
         );
      }

      this.parent.memPutInstanceRecordAtBaseFloat(0);
      ((Buffer)this.parent.instanceBuffer).position(this.parent.instanceDataSize);
      this.parent.instanceBuffer.flip();
   }

   void renderSingleVanilla(
      PoseStack poseStack, int packedLight, BlockPos blockPos, @Nullable BlockEntity blockEntity, @Nullable MultiBufferSource bufferSource
   ) {
      ShaderInstance shader = this.parent.useSlicedLight ? ModShaders.getBlockLitInstancedSlicedShader() : ModShaders.getBlockLitInstancedShader();
      if (shader == null) {
         if (this.parent.quadsForIris != null && !this.parent.quadsForIris.isEmpty() && bufferSource != null) {
            float fade = SingleMeshVboRenderer.getFadeAlpha();
            VertexConsumer consumer = bufferSource.getBuffer(fade < 0.99F ? RenderType.translucent() : RenderType.solid());
            Pose pose = poseStack.last();

            for (BakedQuad quad : this.parent.quadsForIris) {
               consumer.putBulkData(pose, quad, 1.0F, 1.0F, 1.0F, fade, packedLight, OverlayTexture.NO_OVERLAY, false);
            }
         }
      } else {
         try (RenderStateGuard ignored = RenderStateGuard.snapshot()) {
            this.uploadSingleInstance(poseStack, packedLight, blockEntity);
            GL30.glBindVertexArray(this.parent.vaoId);
            GL15.glBindBuffer(34962, this.parent.instanceVboId);
            this.parent.uploadInstanceStreamToBoundVbo();
            this.enableVertexAttribsForDraw();
            RenderSystem.setShader(() -> shader);
            this.applyCommonUniforms(shader, RenderSystem.getProjectionMatrix(), new Matrix4f());
            SingleMeshVboRenderer.prepareBlockLitSamplers(shader);
            shader.apply();
            SingleMeshVboRenderer.bindBlockLitSamplerTextures(shader);
            float fade = this.parent.instanceBuffer.get(this.parent.instanceFadeFloatOffset);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515);
            RenderSystem.depthMask(true);
            GL11.glDisable(2884);
            if (fade < 0.99F) {
               RenderSystem.enableBlend();
               RenderSystem.defaultBlendFunc();
            }

            InstancedGlCompat.glDrawElementsInstancedCompat(4, this.parent.indexCount, 5125, 0L, 1);
         } catch (Exception e) {
            MainRegistry.LOGGER.error("VanillaInstancedBatchRenderer.renderSingleVanilla failed", e);
         } finally {
            this.parent.instanceBuffer.clear();
         }
      }
   }

   void flushBatchVanilla(Matrix4f projectionMatrix) {
      boolean alreadyFlipped = false;
      MdiBatchCoordinator coord = MdiBatchCoordinator.active();
      if (coord != null
         && !this.parent.storesPerInstancePartBone
         && !this.parent.useSlicedLight
         && this.parent.atlasVertexBytesRetained != null
         && this.parent.atlasIndicesRetained != null
         && this.parent.atlasIndexCountRetained > 0
         && !ShaderCompatibilityDetector.isExternalShaderActive()) {
         this.parent.instanceBuffer.flip();
         alreadyFlipped = true;
         boolean accepted = coord.submit(
            this.parent,
            this.parent.indexCount,
            this.parent.instanceCount,
            this.parent.instanceDataSize,
            this.parent.instanceBuffer,
            this.parent.instanceCullIndices,
            this.parent.instanceOcclusionKeys,
            this.parent.atlasVertexBytesRetained,
            this.parent.atlasIndicesRetained,
            this.parent.atlasIndexCountRetained
         );
         if (accepted) {
            return;
         }
      }

      ShaderInstance shader = this.parent.useSlicedLight ? ModShaders.getBlockLitInstancedSlicedShader() : ModShaders.getBlockLitInstancedShader();
      if (shader == null) {
         if (!InstancedStaticPartRenderer.warnedInstancedShaderNullFlush) {
            InstancedStaticPartRenderer.warnedInstancedShaderNullFlush = true;
            MainRegistry.LOGGER
               .warn(
                  "InstancedStaticPartRenderer: instanced shader is null, discarding flush of {} instances (Fabric: ClientSetup.registerFabricShaders)",
                  this.parent.instanceCount
               );
         }
      } else {
         if (!alreadyFlipped) {
            this.parent.instanceBuffer.flip();
         }

         int previousVao = GL11.glGetInteger(34229);
         int previousArrayBuffer = GL11.glGetInteger(34964);
         boolean cullWasEnabled = GL11.glIsEnabled(2884);
         boolean depthTestWasEnabled = GL11.glIsEnabled(2929);
         boolean depthMaskWasEnabled = GL11.glGetBoolean(2930);
         int previousDepthFunc = GL11.glGetInteger(2932);
         boolean blendWasEnabled = GL11.glIsEnabled(3042);
         int prevBlendSrcRgb = GL11.glGetInteger(32969);
         int prevBlendDstRgb = GL11.glGetInteger(32968);
         int prevBlendSrcAlpha = GL11.glGetInteger(32971);
         int prevBlendDstAlpha = GL11.glGetInteger(32970);

         try {
            float minFade = 1.0F;

            for (int i = 0; i < this.parent.instanceCount; i++) {
               float fa = this.parent.instanceBuffer.get(i * this.parent.instanceDataSize + this.parent.instanceFadeFloatOffset);
               if (fa < minFade) {
                  minFade = fa;
               }
            }

            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515);
            RenderSystem.depthMask(true);
            RenderSystem.disableCull();
            if (minFade < 0.99F) {
               RenderSystem.enableBlend();
               RenderSystem.defaultBlendFunc();
            }

            GL30.glBindVertexArray(this.parent.vaoId);
            GL15.glBindBuffer(34962, this.parent.instanceVboId);
            this.parent.uploadInstanceStreamToBoundVbo();
            this.enableVertexAttribsForDraw();
            RenderSystem.setShader(() -> shader);
            this.applyCommonUniforms(shader, projectionMatrix, new Matrix4f());
            SingleMeshVboRenderer.prepareBlockLitSamplers(shader);
            shader.apply();
            SingleMeshVboRenderer.bindBlockLitSamplerTextures(shader);
            InstancedGlCompat.glDrawElementsInstancedCompat(4, this.parent.indexCount, 5125, 0L, this.parent.instanceCount);
            if (minFade < 0.99F) {
               RenderSystem.disableBlend();
            }
         } catch (Exception e) {
            MainRegistry.LOGGER.error("Error during instanced flush (vanilla)", e);
         } finally {
            GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(34962, previousArrayBuffer);
            RenderSystem.depthMask(depthMaskWasEnabled);
            RenderSystem.depthFunc(previousDepthFunc);
            if (depthTestWasEnabled) {
               RenderSystem.enableDepthTest();
            } else {
               RenderSystem.disableDepthTest();
            }

            if (cullWasEnabled) {
               RenderSystem.enableCull();
            } else {
               RenderSystem.disableCull();
            }

            RenderSystem.blendFuncSeparate(prevBlendSrcRgb, prevBlendDstRgb, prevBlendSrcAlpha, prevBlendDstAlpha);
            if (blendWasEnabled) {
               RenderSystem.enableBlend();
            } else {
               RenderSystem.disableBlend();
            }

            RenderSystem.setShader(GameRenderer::getRendertypeSolidShader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
         }
      }
   }
}
