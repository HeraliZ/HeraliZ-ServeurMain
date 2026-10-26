package com.hbm_m.client.render.shader;

import com.hbm_m.client.render.GlVaoSafety;
import com.hbm_m.client.render.IrisCompanionMesh;
import com.hbm_m.main.MainRegistry;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
public final class IrisRenderBatch implements AutoCloseable {
   private static final IrisRenderBatch INSTANCE = new IrisRenderBatch();
   private static IrisRenderBatch ACTIVE;
   private static final IrisRenderBatch NOOP_NESTED = new IrisRenderBatch();
   private boolean isOuter;
   private boolean isPersistent;
   private boolean isShadowPass;
   private ShaderInstance shader;
   private int previousBlockEntityId;
   private int previousVao;
   private int previousArrayBuffer;
   private boolean previousCullEnabled;
   private IrisPhaseGuard phaseGuard;
   private int cachedShaderProgram = -1;
   private ShaderInstance cachedShaderInstance;
   private long cachedPipelineGeneration = -1L;
   private IrisDerivedMatrixUniforms.Locations matrixLocs = IrisDerivedMatrixUniforms.Locations.NONE;
   private final Matrix4f mvInverseTmp = new Matrix4f();
   private final Matrix3f normalTmp = new Matrix3f();
   private final float[] mvFloats = new float[16];
   private final float[] mvInverseFloats = new float[16];
   private final float[] normalMatFloats = new float[9];
   private final short[] cornerShort16 = new short[16];
   private final float[] cornerFloat16 = new float[16];
   private final float[] quantProbe32 = new float[32];
   private int lastBoundVao = -1;
   private int lastBlockU = Integer.MIN_VALUE;
   private int lastSkyV = Integer.MIN_VALUE;
   private static final Matrix4f IDENTITY = new Matrix4f();

   private IrisRenderBatch() {
   }

   public static IrisRenderBatch active() {
      return ACTIVE;
   }

   public static boolean isActive() {
      return ACTIVE != null;
   }

   public static IrisRenderBatch begin(boolean shadowPass, Matrix4f projectionMatrix) {
      if (ACTIVE != null && ACTIVE.isPersistent && ACTIVE.isShadowPass != shadowPass) {
         ACTIVE.actuallyClose();
      }

      if (ACTIVE != null && ACTIVE.isPersistent) {
         return NOOP_NESTED;
      }

      if (ACTIVE != null) {
         return NOOP_NESTED;
      }

      ShaderInstance shader = IrisExtendedShaderAccess.getBlockShader(shadowPass);
      if (shader == null) {
         return null;
      }

      try {
         INSTANCE.setupOuter(shader, projectionMatrix);
         INSTANCE.isPersistent = true;
         INSTANCE.isShadowPass = shadowPass;
         ACTIVE = INSTANCE;
         return NOOP_NESTED;
      } catch (Throwable t) {
         MainRegistry.LOGGER.warn("IrisRenderBatch.begin ({}) failed ({}), falling back to per-call path", shadowPass ? "shadow" : "main", t.toString());
         INSTANCE.tryRestoreState();
         INSTANCE.isPersistent = false;
         INSTANCE.isShadowPass = false;
         return null;
      }
   }

   public static void closePersistentIfActive() {
      if (ACTIVE != null && ACTIVE.isPersistent) {
         ACTIVE.actuallyClose();
      }
   }

   public boolean isShadowPass() {
      return this.isShadowPass;
   }

   private void setupOuter(ShaderInstance shader, Matrix4f projectionMatrix) {
      this.isOuter = true;
      this.shader = shader;
      this.previousVao = GL11.glGetInteger(34229);
      this.previousArrayBuffer = GL11.glGetInteger(34964);
      this.previousCullEnabled = GL11.glIsEnabled(2884);
      this.previousBlockEntityId = IrisExtendedShaderAccess.setCurrentRenderedBlockEntity(0);
      this.phaseGuard = IrisPhaseGuard.pushBlockEntities();
      RenderSystem.setShader(() -> shader);
      if (shader.PROJECTION_MATRIX != null) {
         shader.PROJECTION_MATRIX.set(projectionMatrix);
      }

      if (shader.MODEL_VIEW_MATRIX != null) {
         shader.MODEL_VIEW_MATRIX.set(IDENTITY);
      }

      Uniform fogStart = shader.getUniform("FogStart");
      if (fogStart != null) {
         fogStart.set(RenderSystem.getShaderFogStart());
      }

      Uniform fogEnd = shader.getUniform("FogEnd");
      if (fogEnd != null) {
         fogEnd.set(RenderSystem.getShaderFogEnd());
      }

      Uniform fogColor = shader.getUniform("FogColor");
      if (fogColor != null) {
         float[] fc = RenderSystem.getShaderFogColor();
         fogColor.set(fc[0], fc[1], fc[2], fc[3]);
      }

      Uniform sampler0 = shader.getUniform("Sampler0");
      if (sampler0 != null) {
         sampler0.set(0);
      }

      RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
      Minecraft.getInstance().gameRenderer.overlayTexture().setupOverlayColor();
      Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
      AbstractTexture blockAtlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
      RenderSystem.activeTexture(33984);
      RenderSystem.bindTexture(blockAtlas.getId());
      if (!IrisShaderApply.tryApply(shader)) {
         throw new IllegalStateException("ExtendedShader.apply() failed (destroyed GlResource or invalid pipeline phase)");
      }

      RenderSystem.enableDepthTest();
      RenderSystem.depthFunc(515);
      RenderSystem.depthMask(true);
      RenderSystem.disableCull();
      int programId = shader.getId();
      long currentGen = IrisExtendedShaderAccess.getPipelineGeneration();
      if (this.cachedShaderProgram != programId || this.cachedShaderInstance != shader || this.cachedPipelineGeneration != currentGen) {
         this.cachedShaderProgram = programId;
         this.cachedShaderInstance = shader;
         this.cachedPipelineGeneration = currentGen;
         this.matrixLocs = IrisDerivedMatrixUniforms.resolve(shader);
      }

      this.lastBoundVao = -1;
      this.lastBlockU = Integer.MIN_VALUE;
      this.lastSkyV = Integer.MIN_VALUE;
   }

   private void bindCompanionVao(IrisCompanionMesh companion, int targetVao) {
      if (this.shader != null && companion.isBuilt()) {
         GlStateManager._glBindVertexArray(targetVao);
         companion.prepareForShader(this.shader.getId());
         if (this.lastBoundVao != targetVao) {
            this.lastBlockU = Integer.MIN_VALUE;
            this.lastSkyV = Integer.MIN_VALUE;
         }

         this.lastBoundVao = targetVao;
      }
   }

   private void releaseCompanionVaoAfterDraw(IrisCompanionMesh companion) {
      if (companion != null) {
         companion.restoreConstantLightmap();
      }

      GlVaoSafety.bindVertexArray(this.previousVao);
      this.lastBoundVao = -1;
   }

   public static void runVanillaOverlay(Runnable draw) {
      IrisRenderBatch batch = ACTIVE;
      if (batch != null && batch.isOuter && batch.shader != null) {
         batch.runVanillaOverlayInner(draw);
      } else {
         draw.run();
      }
   }

   private void runVanillaOverlayInner(Runnable draw) {
      int vaoBeforeOverlay = GlVaoSafety.currentBinding();

      try {
         GlVaoSafety.bindVertexArray(0);
         RenderSystem.setShader(GameRenderer::getRendertypeSolidShader);
         draw.run();
      } finally {
         RenderSystem.setShader(() -> this.shader);
         if (!IrisShaderApply.tryApply(this.shader)) {
            MainRegistry.LOGGER.warn("IrisRenderBatch.runVanillaOverlay: ExtendedShader.apply() failed");
         }

         GlVaoSafety.bindVertexArray(vaoBeforeOverlay);
         this.lastBoundVao = -1;
      }
   }

   public void drawCompanion(IrisCompanionMesh companion, Matrix4f modelView, int packedLight) {
      if (this.isOuter && this.shader != null) {
         if (companion != null && companion.isBuilt()) {
            int targetVao = companion.getVaoId();
            int targetIndexCount = companion.getIndexCount();
            if (targetVao > 0 && targetIndexCount > 0) {
               if (this.isPersistent) {
                  IrisExtendedShaderAccess.setCurrentRenderedBlockEntity(0);
               }

               modelView.get(this.mvFloats);
               this.bindCompanionVao(companion, targetVao);
               companion.restoreConstantLightmap();
               float[] mvSrc = this.mvFloats;
               if (this.matrixLocs.modelView() >= 0) {
                  GL20.glUniformMatrix4fv(this.matrixLocs.modelView(), false, mvSrc);
               }

               boolean haveInverse = false;
               if (this.matrixLocs.modelViewInverse() >= 0) {
                  this.mvInverseTmp.set(mvSrc).invert();
                  this.mvInverseTmp.get(this.mvInverseFloats);
                  GL20.glUniformMatrix4fv(this.matrixLocs.modelViewInverse(), false, this.mvInverseFloats);
                  haveInverse = true;
               }

               if (this.matrixLocs.normalMat() >= 0) {
                  if (haveInverse) {
                     this.normalTmp.set(this.mvInverseTmp).transpose();
                  } else {
                     this.normalTmp.set(mvSrc[0], mvSrc[1], mvSrc[2], mvSrc[4], mvSrc[5], mvSrc[6], mvSrc[8], mvSrc[9], mvSrc[10]).invert().transpose();
                  }

                  this.normalTmp.get(this.normalMatFloats);
                  GL20.glUniformMatrix3fv(this.matrixLocs.normalMat(), false, this.normalMatFloats);
               }

               int uv2Loc = companion.getUv2Location();
               if (uv2Loc != -1) {
                  int blockU = Math.max(0, Math.min(240, packedLight & 65535));
                  int skyV = Math.max(0, Math.min(240, packedLight >>> 16 & 65535));
                  if (blockU != this.lastBlockU || skyV != this.lastSkyV) {
                     companion.bindVaoIfNeeded();
                     GL30.glVertexAttribI2i(uv2Loc, blockU, skyV);
                     this.lastBlockU = blockU;
                     this.lastSkyV = skyV;
                  }
               }

               companion.bindVaoIfNeeded();
               GL11.glDrawElements(4, targetIndexCount, 5125, 0L);
               this.releaseCompanionVaoAfterDraw(companion);
            }
         }
      }
   }

   public void drawCompanionWithPerVertexLight(IrisCompanionMesh companion, Matrix4f modelView, float[] cornerUV16, int packedLightFallback) {
      if (this.isOuter && this.shader != null) {
         if (companion != null && companion.isBuilt()) {
            int targetVao = companion.getVaoId();
            int targetIndexCount = companion.getIndexCount();
            if (targetVao > 0 && targetIndexCount > 0) {
               if (companion.supportsPerVertexLightmap() && cornerUV16 != null && cornerUV16.length >= 16) {
                  if (this.isShadowPass) {
                     this.drawCompanion(companion, modelView, packedLightFallback);
                  } else {
                     if (this.isPersistent) {
                        IrisExtendedShaderAccess.setCurrentRenderedBlockEntity(0);
                     }

                     modelView.get(this.mvFloats);
                     this.bindCompanionVao(companion, targetVao);
                     float[] mvSrc = this.mvFloats;
                     if (this.matrixLocs.modelView() >= 0) {
                        GL20.glUniformMatrix4fv(this.matrixLocs.modelView(), false, mvSrc);
                     }

                     boolean haveInverse = false;
                     if (this.matrixLocs.modelViewInverse() >= 0) {
                        this.mvInverseTmp.set(mvSrc).invert();
                        this.mvInverseTmp.get(this.mvInverseFloats);
                        GL20.glUniformMatrix4fv(this.matrixLocs.modelViewInverse(), false, this.mvInverseFloats);
                        haveInverse = true;
                     }

                     if (this.matrixLocs.normalMat() >= 0) {
                        if (haveInverse) {
                           this.normalTmp.set(this.mvInverseTmp).transpose();
                        } else {
                           this.normalTmp.set(mvSrc[0], mvSrc[1], mvSrc[2], mvSrc[4], mvSrc[5], mvSrc[6], mvSrc[8], mvSrc[9], mvSrc[10]).invert().transpose();
                        }

                        this.normalTmp.get(this.normalMatFloats);
                        GL20.glUniformMatrix3fv(this.matrixLocs.normalMat(), false, this.normalMatFloats);
                     }

                     long key = 1469598103934665603L;

                     for (int k = 0; k < 16; k++) {
                        int q = Math.round(cornerUV16[k]);
                        if (q < 0) {
                           q = 0;
                        } else if (q > 240) {
                           q = 240;
                        }

                        this.cornerShort16[k] = (short)q;
                        this.cornerFloat16[k] = q;
                        key ^= q & 65535;
                        key *= 1099511628211L;
                     }

                     companion.ensureLightmapCapacity(32);
                     long alloc = companion.allocLightmapSlot(key);
                     int cachedSlot = (int)(alloc & 4294967295L);
                     boolean reused = alloc >>> 32 != 0L;
                     if (!reused) {
                        companion.writeInstanceLightmap(cachedSlot, this.cornerFloat16);
                     }

                     companion.finishLightmapWrites();
                     companion.activatePerVertexLightmap();
                     companion.bindLightmapForInstance(cachedSlot);
                     this.lastBlockU = Integer.MIN_VALUE;
                     this.lastSkyV = Integer.MIN_VALUE;
                     GL11.glDrawElements(4, targetIndexCount, 5125, 0L);
                     this.releaseCompanionVaoAfterDraw(companion);
                  }
               } else {
                  this.drawCompanion(companion, modelView, packedLightFallback);
               }
            }
         }
      }
   }

   public void drawCompanionWithSlicedPerVertexLight(IrisCompanionMesh companion, Matrix4f modelView, float[] probeUV32, int packedLightFallback) {
      if (this.isOuter && this.shader != null) {
         if (companion != null && companion.isBuilt()) {
            int targetVao = companion.getVaoId();
            int targetIndexCount = companion.getIndexCount();
            if (targetVao > 0 && targetIndexCount > 0) {
               if (companion.supportsSlicedPerVertexLightmap() && probeUV32 != null && probeUV32.length >= 32) {
                  if (this.isShadowPass) {
                     this.drawCompanion(companion, modelView, packedLightFallback);
                  } else {
                     if (this.isPersistent) {
                        IrisExtendedShaderAccess.setCurrentRenderedBlockEntity(0);
                     }

                     modelView.get(this.mvFloats);
                     this.bindCompanionVao(companion, targetVao);
                     float[] mvSrc = this.mvFloats;
                     if (this.matrixLocs.modelView() >= 0) {
                        GL20.glUniformMatrix4fv(this.matrixLocs.modelView(), false, mvSrc);
                     }

                     boolean haveInverse = false;
                     if (this.matrixLocs.modelViewInverse() >= 0) {
                        this.mvInverseTmp.set(mvSrc).invert();
                        this.mvInverseTmp.get(this.mvInverseFloats);
                        GL20.glUniformMatrix4fv(this.matrixLocs.modelViewInverse(), false, this.mvInverseFloats);
                        haveInverse = true;
                     }

                     if (this.matrixLocs.normalMat() >= 0) {
                        if (haveInverse) {
                           this.normalTmp.set(this.mvInverseTmp).transpose();
                        } else {
                           this.normalTmp.set(mvSrc[0], mvSrc[1], mvSrc[2], mvSrc[4], mvSrc[5], mvSrc[6], mvSrc[8], mvSrc[9], mvSrc[10]).invert().transpose();
                        }

                        this.normalTmp.get(this.normalMatFloats);
                        GL20.glUniformMatrix3fv(this.matrixLocs.normalMat(), false, this.normalMatFloats);
                     }

                     long key = 1469598103934665603L;

                     for (int k = 0; k < 32; k++) {
                        int q = Math.round(probeUV32[k]);
                        if (q < 0) {
                           q = 0;
                        } else if (q > 240) {
                           q = 240;
                        }

                        this.quantProbe32[k] = q;
                        key ^= q & 65535;
                        key *= 1099511628211L;
                     }

                     companion.ensureLightmapCapacity(32);
                     long alloc = companion.allocLightmapSlot(key);
                     int cachedSlot = (int)(alloc & 4294967295L);
                     boolean reused = alloc >>> 32 != 0L;
                     if (!reused) {
                        companion.writeInstanceLightmap(cachedSlot, this.quantProbe32);
                     }

                     companion.finishLightmapWrites();
                     companion.activatePerVertexLightmap();
                     companion.bindLightmapForInstance(cachedSlot);
                     this.lastBlockU = Integer.MIN_VALUE;
                     this.lastSkyV = Integer.MIN_VALUE;
                     GL11.glDrawElements(4, targetIndexCount, 5125, 0L);
                     this.releaseCompanionVaoAfterDraw(companion);
                  }
               } else {
                  this.drawCompanion(companion, modelView, packedLightFallback);
               }
            }
         }
      }
   }

   @Override
   public void close() {
      if (this.isOuter) {
         if (!this.isPersistent) {
            this.actuallyClose();
         }
      }
   }

   private void actuallyClose() {
      try {
         if (this.shader != null) {
            this.shader.clear();
         }
      } catch (Throwable t) {
         MainRegistry.LOGGER.warn("IrisRenderBatch.close: shader.clear() threw {}", t.toString());
      } finally {
         this.tryRestoreState();
         this.isOuter = false;
         this.isPersistent = false;
         this.isShadowPass = false;
         this.shader = null;
         ACTIVE = null;
      }
   }

   private void tryRestoreState() {
      try {
         RenderSystem.setShader(GameRenderer::getRendertypeSolidShader);
         GlVaoSafety.bindVertexArray(this.previousVao);
         GL15.glBindBuffer(34962, this.previousArrayBuffer);
         this.lastBoundVao = -1;
         if (this.previousCullEnabled) {
            RenderSystem.enableCull();
         } else {
            RenderSystem.disableCull();
         }

         RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
         IrisExtendedShaderAccess.restoreCurrentRenderedBlockEntity(this.previousBlockEntityId);
         if (this.phaseGuard != null) {
            this.phaseGuard.close();
            this.phaseGuard = null;
         }
      } catch (Throwable t) {
         MainRegistry.LOGGER.error("IrisRenderBatch.tryRestoreState failed", t);
      }
   }

   public static void invalidateCaches() {
      if (ACTIVE != null && ACTIVE.isPersistent) {
         ACTIVE.actuallyClose();
      }

      INSTANCE.cachedShaderProgram = -1;
      INSTANCE.cachedShaderInstance = null;
      INSTANCE.cachedPipelineGeneration = -1L;
      INSTANCE.matrixLocs = IrisDerivedMatrixUniforms.Locations.NONE;
      INSTANCE.lastBoundVao = -1;
      INSTANCE.lastBlockU = Integer.MIN_VALUE;
      INSTANCE.lastSkyV = Integer.MIN_VALUE;
   }
}
