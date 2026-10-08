package com.hbm_m.client.render;

import com.hbm_m.client.render.shader.IrisDerivedMatrixUniforms;
import com.hbm_m.client.render.shader.IrisExtendedShaderAccess;
import com.hbm_m.client.render.shader.IrisPhaseGuard;
import com.hbm_m.client.render.shader.IrisRenderBatch;
import com.hbm_m.client.render.shader.IrisShaderApply;
import com.hbm_m.client.render.shader.ShaderCompatibilityDetector;
import com.hbm_m.main.MainRegistry;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
final class IrisInstancedBatchRenderer {
   private final InstancedStaticPartRenderer parent;
   @Nullable
   private IrisCompanionMesh irisCompanion;
   private boolean irisCompanionAttempted;
   private IrisDerivedMatrixUniforms.Locations cachedMatrixLocs = IrisDerivedMatrixUniforms.Locations.NONE;
   private long cachedMatrixPipelineGeneration = -1L;
   private int cachedMatrixProgramId = -1;
   private ShaderInstance cachedMatrixShader;
   private final Matrix4f tmpInstanceMat = new Matrix4f();
   private final Quaternionf irisQuatTmp = new Quaternionf();
   private static final Matrix4f IDENTITY = new Matrix4f();
   private final float[] irisMvFloats = new float[16];
   private final float[] irisMvInverseFloats = new float[16];
   private final float[] irisNormalMatFloats = new float[9];
   private final Matrix4f irisMvInverseTmp = new Matrix4f();
   private final Matrix3f irisNormalTmp = new Matrix3f();
   final float[] irisSingleUV = new float[2];
   private final int[] instanceLightmapSlot;
   private final short[] tmpCornerShort;

   IrisInstancedBatchRenderer(InstancedStaticPartRenderer parent) {
      this.parent = parent;
      this.instanceLightmapSlot = new int[parent.maxInstances];
      this.tmpCornerShort = new short[parent.lightFloatCount];
   }

   @Nullable
   IrisCompanionMesh getOrBuildIrisCompanion() {
      if (this.irisCompanion != null && this.irisCompanion.isBuilt()) {
         return this.irisCompanion;
      }

      if (this.irisCompanion != null && this.irisCompanion.isFailed()) {
         return null;
      }

      if (this.irisCompanionAttempted && this.irisCompanion == null) {
         return null;
      }

      if (this.parent.quadsForIris != null && !this.parent.quadsForIris.isEmpty()) {
         if (this.irisCompanion == null) {
            this.irisCompanion = new IrisCompanionMesh(this.parent.quadsForIris);
            this.irisCompanionAttempted = true;
         }

         return this.irisCompanion.ensureBuilt() ? this.irisCompanion : null;
      } else {
         this.irisCompanionAttempted = true;
         return null;
      }
   }

   @Nullable
   IrisCompanionMesh getCompanion() {
      return this.irisCompanion;
   }

   void invalidateIrisLocations() {
      this.cachedMatrixLocs = IrisDerivedMatrixUniforms.Locations.NONE;
      this.cachedMatrixPipelineGeneration = -1L;
      this.cachedMatrixProgramId = -1;
      this.cachedMatrixShader = null;
   }

   private IrisDerivedMatrixUniforms.Locations resolveMatrixLocs(ShaderInstance shader) {
      int programId = shader.getId();
      long gen = IrisExtendedShaderAccess.getPipelineGeneration();
      if (this.cachedMatrixProgramId == programId && this.cachedMatrixShader == shader && this.cachedMatrixPipelineGeneration == gen) {
         return this.cachedMatrixLocs;
      }

      this.cachedMatrixProgramId = programId;
      this.cachedMatrixShader = shader;
      this.cachedMatrixPipelineGeneration = gen;
      this.cachedMatrixLocs = IrisDerivedMatrixUniforms.resolve(shader);
      return this.cachedMatrixLocs;
   }

   void sampleCornersForSingleDraw(PoseStack poseStack, BlockPos blockPos, @Nullable BlockEntity blockEntity, int packedLight) {
      BlockPos anchor = blockEntity != null ? blockEntity.getBlockPos() : blockPos;
      if (anchor == null) {
         anchor = BlockPos.ZERO;
      }

      if (LightSampleCache.BASE_POSE_SET.get()) {
         this.parent.tmpLocalPose.set((Matrix4fc)LightSampleCache.BASE_POSE.get()).invert().mul(poseStack.last().pose());
      } else {
         Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
         this.parent.tmpInvViewRot.identity().set(RenderSystem.getInverseViewRotationMatrix());
         this.parent.tmpLocalPose.set(this.parent.tmpInvViewRot).mul(poseStack.last().pose());
         this.parent.tmpLocalPose.m30(this.parent.tmpLocalPose.m30() - (float)(anchor.getX() - cam.x));
         this.parent.tmpLocalPose.m31(this.parent.tmpLocalPose.m31() - (float)(anchor.getY() - cam.y));
         this.parent.tmpLocalPose.m32(this.parent.tmpLocalPose.m32() - (float)(anchor.getZ() - cam.z));
      }

      long partHash = System.identityHashCode(this.parent);
      if (this.parent.useSlicedLight) {
         LightSampleCache.getOrSample16(blockEntity, partHash, this.parent.objBbox, anchor, this.parent.tmpLocalPose, packedLight, this.parent.tmpCornerUV);
      } else {
         LightSampleCache.getOrSample8(blockEntity, partHash, this.parent.objBbox, anchor, this.parent.tmpLocalPose, packedLight, this.parent.tmpCornerUV);
      }
   }

   boolean drawSingleWithIrisExtended(PoseStack poseStack, int packedLight, BlockPos blockPos, @Nullable BlockEntity blockEntity) {
      IrisCompanionMesh companion = this.getOrBuildIrisCompanion();
      if (companion == null) {
         return false;
      }

      IrisRenderBatch activeBatch = IrisRenderBatch.active();
      boolean shadowPassEarly = activeBatch != null ? activeBatch.isShadowPass() : ShaderCompatibilityDetector.isRenderingShadowPass();
      boolean haveCorners = false;
      if (!shadowPassEarly) {
         this.sampleCornersForSingleDraw(poseStack, blockPos, blockEntity, packedLight);
         haveCorners = true;
      }

      IrisRenderBatch batch = IrisRenderBatch.active();
      if (batch != null) {
         LightSampleCache.getOrSample(blockEntity, packedLight, this.irisSingleUV, 0);
         int blockUInt = Math.max(0, Math.min(240, Math.round(this.irisSingleUV[0])));
         int skyVInt = Math.max(0, Math.min(240, Math.round(this.irisSingleUV[1])));
         int packedSmoothLight = skyVInt << 16 | blockUInt;
         if (haveCorners && this.parent.useSlicedLight && companion.supportsSlicedPerVertexLightmap()) {
            batch.drawCompanionWithSlicedPerVertexLight(companion, poseStack.last().pose(), this.parent.tmpCornerUV, packedSmoothLight);
         } else if (haveCorners) {
            batch.drawCompanionWithPerVertexLight(companion, poseStack.last().pose(), this.parent.tmpCornerUV, packedSmoothLight);
         } else {
            batch.drawCompanion(companion, poseStack.last().pose(), packedSmoothLight);
         }

         return true;
      } else {
         boolean shadowPass = ShaderCompatibilityDetector.isRenderingShadowPass();
         ShaderInstance shader = IrisExtendedShaderAccess.getBlockShader(shadowPass);
         if (shader == null) {
            return false;
         }

         int previousBlockEntityId = IrisExtendedShaderAccess.setCurrentRenderedBlockEntity(0);

         try (
            RenderStateGuard stateGuard = RenderStateGuard.snapshot();
            IrisPhaseGuard ignored = IrisPhaseGuard.pushBlockEntities();
         ) {
            RenderSystem.setShader(() -> shader);
            this.parent.vanillaHelper.updateUniformCache(shader);
            LightSampleCache.getOrSample(blockEntity, packedLight, this.irisSingleUV, 0);
            this.parent.vanillaHelper.applyCommonUniforms(shader, RenderSystem.getProjectionMatrix(), poseStack.last().pose());
            if (this.parent.vanillaHelper.uBrightness != null) {
               this.parent.vanillaHelper.uBrightness.set(this.parent.vanillaHelper.brightnessFromUV(this.irisSingleUV[0], this.irisSingleUV[1], Float.NaN));
            }

            SingleMeshVboRenderer.TextureBinder.bindForModelIfNeeded(shader);
            companion.bindVaoIfNeeded();
            if (!IrisShaderApply.tryApply(shader)) {
               return false;
            }

            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515);
            RenderSystem.depthMask(true);
            RenderSystem.disableCull();
            companion.prepareForShader(shader.getId());
            int uv2Loc = companion.getUv2Location();
            if (haveCorners && companion.supportsPerVertexLightmap()) {
               companion.ensureLightmapCapacity(1);
               companion.writeInstanceLightmap(0, this.parent.tmpCornerUV);
               companion.finishLightmapWrites();
               companion.activatePerVertexLightmap();
               companion.bindLightmapForInstance(0);
            } else if (uv2Loc != -1) {
               companion.restoreConstantLightmap();
               int blockUInt = Math.max(0, Math.min(240, Math.round(this.irisSingleUV[0])));
               int skyVInt = Math.max(0, Math.min(240, Math.round(this.irisSingleUV[1])));
               companion.bindVaoIfNeeded();
               GL30.glVertexAttribI2i(uv2Loc, blockUInt, skyVInt);
            }

            companion.bindVaoIfNeeded();
            GL11.glDrawElements(4, companion.getIndexCount(), 5125, 0L);
            shader.clear();
            return true;
         } catch (Exception e) {
            MainRegistry.LOGGER.error("IrisInstancedBatchRenderer.drawSingleWithIrisExtended failed", e);
            return false;
         } finally {
            IrisExtendedShaderAccess.restoreCurrentRenderedBlockEntity(previousBlockEntityId);
         }
      }
   }

   void flushBatchIris(Matrix4f projectionMatrix) {
      IrisCompanionMesh companion = this.getOrBuildIrisCompanion();
      boolean shadowPass = ShaderCompatibilityDetector.isRenderingShadowPass();
      ShaderInstance shader = IrisExtendedShaderAccess.getBlockShader(shadowPass);
      if (shader != null) {
         this.parent.instanceBuffer.flip();
         int floats = this.parent.instanceCount * this.parent.instanceDataSize;
         if (floats <= this.parent.instanceBuffer.remaining()) {
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
            int previousBlockEntityId = IrisExtendedShaderAccess.setCurrentRenderedBlockEntity(0);

            try (IrisPhaseGuard ignored = IrisPhaseGuard.pushBlockEntities()) {
               RenderSystem.setShader(() -> shader);
               this.parent.vanillaHelper.applyCommonUniforms(shader, projectionMatrix, IDENTITY);
               RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
               Minecraft.getInstance().gameRenderer.overlayTexture().setupOverlayColor();
               Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
               SingleMeshVboRenderer.TextureBinder.bindForModelIfNeeded(shader);
               int targetVao = companion != null ? companion.getVaoId() : this.parent.vaoId;
               int targetIndexCount = companion != null ? companion.getIndexCount() : this.parent.indexCount;
               if (companion != null) {
                  companion.bindVaoIfNeeded();
               } else {
                  GlStateManager._glBindVertexArray(targetVao);
               }

               if (IrisShaderApply.tryApply(shader)) {
                  RenderSystem.enableDepthTest();
                  RenderSystem.depthFunc(515);
                  RenderSystem.depthMask(true);
                  RenderSystem.disableCull();
                  if (companion != null) {
                     companion.prepareForShader(shader.getId());
                  }

                  int uv2Loc = companion != null ? companion.getUv2Location() : -1;
                  boolean perVertexLight = companion != null
                     && (this.parent.useSlicedLight ? companion.supportsSlicedPerVertexLightmap() : companion.supportsPerVertexLightmap());
                  if (perVertexLight) {
                     companion.ensureLightmapCapacity(Math.max(8, this.parent.instanceCount));

                     for (int i = 0; i < this.parent.instanceCount; i++) {
                        int cornerBase = i * this.parent.instanceDataSize + 14;
                        long key = 1469598103934665603L;

                        for (int k = 0; k < this.parent.lightFloatCount; k++) {
                           float f = this.parent.instanceBuffer.get(cornerBase + k);
                           int q = Math.round(f);
                           if (q < 0) {
                              q = 0;
                           } else if (q > 240) {
                              q = 240;
                           }

                           this.tmpCornerShort[k] = (short)q;
                           key ^= q & 65535;
                           key *= 1099511628211L;
                        }

                        long alloc = companion.allocLightmapSlot(key);
                        int slot = (int)(alloc & 4294967295L);
                        boolean reused = alloc >>> 32 != 0L;
                        this.instanceLightmapSlot[i] = slot;
                        if (!reused) {
                           for (int k = 0; k < this.parent.lightFloatCount; k++) {
                              this.parent.tmpCornerUV[k] = this.tmpCornerShort[k] & 65535;
                           }

                           companion.writeInstanceLightmap(slot, this.parent.tmpCornerUV);
                        }
                     }

                     companion.finishLightmapWrites();
                     companion.activatePerVertexLightmap();
                  }

                  IrisDerivedMatrixUniforms.Locations matrixLocs = this.resolveMatrixLocs(shader);
                  int locModelView = matrixLocs.modelView();
                  int locModelViewInverse = matrixLocs.modelViewInverse();
                  int locNormalMat = matrixLocs.normalMat();
                  float[] mvFloats = this.irisMvFloats;
                  float[] mvInverseFloats = this.irisMvInverseFloats;
                  float[] normalMatFloats = this.irisNormalMatFloats;
                  Matrix4f mvInverseTmp = this.irisMvInverseTmp;
                  Matrix3f normalTmp = this.irisNormalTmp;
                  float lastQx = Float.NaN;
                  float lastQy = Float.NaN;
                  float lastQz = Float.NaN;
                  float lastQw = Float.NaN;
                  float lastPx = Float.NaN;
                  float lastPy = Float.NaN;
                  float lastPz = Float.NaN;
                  int lastBlockU = Integer.MIN_VALUE;
                  int lastSkyV = Integer.MIN_VALUE;

                  for (int i = 0; i < this.parent.instanceCount; i++) {
                     int base = i * this.parent.instanceDataSize;
                     float px = this.parent.instanceBuffer.get(base);
                     float py = this.parent.instanceBuffer.get(base + 1);
                     float pz = this.parent.instanceBuffer.get(base + 2);
                     float qx = this.parent.instanceBuffer.get(base + 3);
                     float qy = this.parent.instanceBuffer.get(base + 4);
                     float qz = this.parent.instanceBuffer.get(base + 5);
                     float qw = this.parent.instanceBuffer.get(base + 6);
                     boolean rotChanged = qx != lastQx || qy != lastQy || qz != lastQz || qw != lastQw;
                     boolean posChanged = px != lastPx || py != lastPy || pz != lastPz;
                     this.tmpInstanceMat.translationRotate(px, py, pz, this.irisQuatTmp.set(qx, qy, qz, qw));
                     if (locModelView >= 0) {
                        this.tmpInstanceMat.get(mvFloats);
                        GL20.glUniformMatrix4fv(locModelView, false, mvFloats);
                     }

                     boolean haveInverseFresh = false;
                     if (locModelViewInverse >= 0 && (rotChanged || posChanged)) {
                        mvInverseTmp.set(this.tmpInstanceMat).invertAffine();
                        mvInverseTmp.get(mvInverseFloats);
                        GL20.glUniformMatrix4fv(locModelViewInverse, false, mvInverseFloats);
                        haveInverseFresh = true;
                     }

                     if (locNormalMat >= 0 && rotChanged) {
                        normalTmp.set(this.tmpInstanceMat);
                        normalTmp.get(normalMatFloats);
                        GL20.glUniformMatrix3fv(locNormalMat, false, normalMatFloats);
                     }

                     if (perVertexLight) {
                        companion.bindLightmapForInstance(this.instanceLightmapSlot[i]);
                     } else if (uv2Loc != -1) {
                        int uvBase = i * 2;
                        int blockUInt = Math.max(0, Math.min(240, Math.round(this.parent.instanceLightUV[uvBase])));
                        int skyVInt = Math.max(0, Math.min(240, Math.round(this.parent.instanceLightUV[uvBase + 1])));
                        if (blockUInt != lastBlockU || skyVInt != lastSkyV) {
                           companion.bindVaoIfNeeded();
                           GL30.glVertexAttribI2i(uv2Loc, blockUInt, skyVInt);
                           lastBlockU = blockUInt;
                           lastSkyV = skyVInt;
                        }
                     }

                     if (this.parent.vanillaHelper.uFadeAlpha != null) {
                        this.parent.vanillaHelper.uFadeAlpha.set(this.parent.instanceBuffer.get(base + this.parent.instanceFadeFloatOffset));
                     }

                     if (companion != null) {
                        companion.bindVaoIfNeeded();
                     }

                     GL11.glDrawElements(4, targetIndexCount, 5125, 0L);
                     lastQx = qx;
                     lastQy = qy;
                     lastQz = qz;
                     lastQw = qw;
                     lastPx = px;
                     lastPy = py;
                     lastPz = pz;
                  }

                  if (companion != null && perVertexLight) {
                     companion.restoreConstantLightmap();
                  }

                  shader.clear();
                  return;
               }
            } catch (Exception e) {
               MainRegistry.LOGGER.error("Error during instanced flush (Iris)", e);
               return;
            } finally {
               GlVaoSafety.bindVertexArray(previousVao);
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
               IrisExtendedShaderAccess.restoreCurrentRenderedBlockEntity(previousBlockEntityId);
            }
         }
      }
   }

   void cleanup() {
      IrisCompanionMesh companionToDestroy = this.irisCompanion;
      this.irisCompanion = null;
      this.irisCompanionAttempted = false;
      if (companionToDestroy != null) {
         RenderSystem.recordRenderCall(companionToDestroy::destroy);
      }
   }
}
