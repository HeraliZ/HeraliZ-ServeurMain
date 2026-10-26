package com.hbm_m.client.render;

import com.hbm_m.client.render.culling.OcclusionCullingHelper;
import com.hbm_m.client.render.shader.IrisExtendedShaderAccess;
import com.hbm_m.client.render.shader.IrisPhaseGuard;
import com.hbm_m.client.render.shader.IrisRenderBatch;
import com.hbm_m.client.render.shader.IrisShaderApply;
import com.hbm_m.client.render.shader.ShaderCompatibilityDetector;
import com.hbm_m.main.MainRegistry;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

@OnlyIn(Dist.CLIENT)
public abstract class SingleMeshVboRenderer extends AbstractGpuMesh {
   public static final int MACHINE_PART_VERTEX_STRIDE_BYTES = 36;
   @Nullable
   private IrisCompanionMesh irisCompanion;
   private boolean irisCompanionAttempted;
   private static final ThreadLocal<Float> currentFadeAlpha = ThreadLocal.withInitial(() -> 1.0F);
   private static final ThreadLocal<Boolean> worldMissileOverlayDraw = ThreadLocal.withInitial(() -> false);
   private static final ThreadLocal<Boolean> entityMissileDepthBias = ThreadLocal.withInitial(() -> false);
   private static final float ENTITY_MISSILE_DEPTH_FACTOR = -4.0F;
   private static final float ENTITY_MISSILE_DEPTH_UNITS = -4.0F;
   private final Matrix4f tmpLocalPose = new Matrix4f();
   private final Matrix4f tmpInvViewRot = new Matrix4f();
   private final float[] tmpCornerUV = new float[16];
   private final float[] tmpProbeUV = new float[32];
   protected boolean useSlicedLight = false;
   private ShaderInstance cachedBlockLitShader;
   private int cachedBlockLitProgramId = -1;
   private long cachedBlockLitPipelineGen = -1L;
   private Uniform cachedBboxMinU;
   private Uniform cachedBboxSizeU;
   private Uniform cachedLightC01;
   private Uniform cachedLightC23;
   private Uniform cachedLightC45;
   private Uniform cachedLightC67;
   private Uniform cachedLightS0C01;
   private Uniform cachedLightS0C23;
   private Uniform cachedLightS1C01;
   private Uniform cachedLightS1C23;
   private Uniform cachedLightS2C01;
   private Uniform cachedLightS2C23;
   private Uniform cachedLightS3C01;
   private Uniform cachedLightS3C23;
   private Uniform cachedFogStartU;
   private Uniform cachedFogEndU;
   private Uniform cachedFogColorU;
   private Uniform cachedFadeAlphaU;

   public static void setFadeAlpha(float alpha) {
      currentFadeAlpha.set(alpha);
   }

   public static float getFadeAlpha() {
      return currentFadeAlpha.get();
   }

   public static void setWorldMissileOverlayDraw(boolean enabled) {
      worldMissileOverlayDraw.set(enabled);
   }

   public static boolean isWorldMissileOverlayDraw() {
      return worldMissileOverlayDraw.get();
   }

   public static void setEntityMissileDepthBias(boolean enabled) {
      entityMissileDepthBias.set(enabled);
   }

   private static void beginEntityMissileDepthBias() {
      if (entityMissileDepthBias.get()) {
         RenderSystem.enablePolygonOffset();
         RenderSystem.polygonOffset(-4.0F, -4.0F);
      }
   }

   private static void endEntityMissileDepthBias() {
      if (entityMissileDepthBias.get()) {
         RenderSystem.disablePolygonOffset();
      }
   }

   public void setUseSlicedLight(boolean useSlicedLight) {
      this.useSlicedLight = useSlicedLight;
   }

   private void updateBlockLitUniformCache(ShaderInstance shader) {
      int programId = shader != null ? shader.getId() : -1;
      long pipelineGen = IrisExtendedShaderAccess.getPipelineGeneration();
      if (this.cachedBlockLitShader != shader
         || this.cachedBlockLitProgramId != programId
         || this.cachedBlockLitPipelineGen != pipelineGen
         || this.cachedBlockLitShader == null) {
         this.cachedBlockLitShader = shader;
         this.cachedBlockLitProgramId = programId;
         this.cachedBlockLitPipelineGen = pipelineGen;
         if (shader == null) {
            this.cachedBboxMinU = null;
            this.cachedBboxSizeU = null;
            this.cachedLightC01 = null;
            this.cachedLightC23 = null;
            this.cachedLightC45 = null;
            this.cachedLightC67 = null;
            this.cachedLightS0C01 = null;
            this.cachedLightS0C23 = null;
            this.cachedLightS1C01 = null;
            this.cachedLightS1C23 = null;
            this.cachedLightS2C01 = null;
            this.cachedLightS2C23 = null;
            this.cachedLightS3C01 = null;
            this.cachedLightS3C23 = null;
            this.cachedFogStartU = null;
            this.cachedFogEndU = null;
            this.cachedFogColorU = null;
            this.cachedFadeAlphaU = null;
         } else {
            this.cachedBboxMinU = shader.getUniform("BboxMin");
            this.cachedBboxSizeU = shader.getUniform("BboxSize");
            this.cachedLightC01 = shader.getUniform("LightC01");
            this.cachedLightC23 = shader.getUniform("LightC23");
            this.cachedLightC45 = shader.getUniform("LightC45");
            this.cachedLightC67 = shader.getUniform("LightC67");
            this.cachedLightS0C01 = shader.getUniform("LightS0C01");
            this.cachedLightS0C23 = shader.getUniform("LightS0C23");
            this.cachedLightS1C01 = shader.getUniform("LightS1C01");
            this.cachedLightS1C23 = shader.getUniform("LightS1C23");
            this.cachedLightS2C01 = shader.getUniform("LightS2C01");
            this.cachedLightS2C23 = shader.getUniform("LightS2C23");
            this.cachedLightS3C01 = shader.getUniform("LightS3C01");
            this.cachedLightS3C23 = shader.getUniform("LightS3C23");
            this.cachedFogStartU = shader.getUniform("FogStart");
            this.cachedFogEndU = shader.getUniform("FogEnd");
            this.cachedFogColorU = shader.getUniform("FogColor");
            this.cachedFadeAlphaU = shader.getUniform("FadeAlpha");
         }
      }
   }

   protected abstract SingleMeshVboRenderer.VboData buildVboData();

   protected List<BakedQuad> getQuadsForIrisPath() {
      return null;
   }

   public static void prepareBlockLitSamplers(ShaderInstance shader) {
      if (shader != null) {
         RenderFrameLight.ensureLightTextureUpdated();
         primeBlockLitSamplerMap(shader, Minecraft.getInstance());
      }
   }

   private static void primeBlockLitSamplerMap(ShaderInstance shader, Minecraft mc) {
      TextureManager textureManager = mc.getTextureManager();
      RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
      RenderSystem.activeTexture(33984);
      textureManager.bindForSetup(TextureAtlas.LOCATION_BLOCKS);
      RenderSystem.activeTexture(33985);
      mc.gameRenderer.lightTexture().turnOnLightLayer();
      RenderSystem.activeTexture(33984);
      textureManager.bindForSetup(TextureAtlas.LOCATION_BLOCKS);
      AbstractTexture atlasTex = textureManager.getTexture(TextureAtlas.LOCATION_BLOCKS);
      int lightmapGlId = resolveLightmapGlId(mc);
      if (atlasTex != null) {
         shader.setSampler("Sampler0", atlasTex);
      } else {
         int atlasGlId = resolveBlockAtlasGlId(mc);
         if (atlasGlId > 0) {
            shader.setSampler("Sampler0", atlasGlId);
         }
      }

      if (lightmapGlId > 0) {
         shader.setSampler("Sampler2", lightmapGlId);
      }
   }

   private static int resolveBlockAtlasGlId(Minecraft mc) {
      AbstractTexture atlas = mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
      if (atlas != null) {
         return atlas.getId();
      }

      int fromSlot = RenderSystem.getShaderTexture(0);
      return fromSlot > 0 ? fromSlot : -1;
   }

   private static int resolveLightmapGlId(Minecraft mc) {
      int fromSlot = RenderSystem.getShaderTexture(2);
      if (fromSlot > 0) {
         return fromSlot;
      }

      RenderSystem.activeTexture(33985);
      int bound = GL11.glGetInteger(32873);
      RenderSystem.activeTexture(33984);
      return bound > 0 ? bound : -1;
   }

   public static void bindBlockLitSamplerTextures(ShaderInstance shader) {
      if (shader != null) {
         RenderSystem.assertOnRenderThread();
         Minecraft mc = Minecraft.getInstance();
         RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
         RenderSystem.activeTexture(33984);
         mc.getTextureManager().bindForSetup(TextureAtlas.LOCATION_BLOCKS);
         RenderSystem.activeTexture(33985);
         mc.gameRenderer.lightTexture().turnOnLightLayer();
         RenderSystem.activeTexture(33984);
         mc.getTextureManager().bindForSetup(TextureAtlas.LOCATION_BLOCKS);
         int atlasGlId = resolveBlockAtlasGlId(mc);
         int lightmapGlId = resolveLightmapGlId(mc);
         if (atlasGlId > 0 && lightmapGlId > 0) {
            GL13.glActiveTexture(33984);
            GL11.glBindTexture(3553, atlasGlId);
            GL13.glActiveTexture(33985);
            GL11.glBindTexture(3553, lightmapGlId);
            GL13.glActiveTexture(33984);
            shader.setSampler("Sampler0", atlasGlId);
            shader.setSampler("Sampler2", lightmapGlId);
            Uniform uSampler0 = shader.getUniform("Sampler0");
            if (uSampler0 != null) {
               uSampler0.set(0);
            }

            Uniform uSampler2 = shader.getUniform("Sampler2");
            if (uSampler2 != null) {
               uSampler2.set(1);
            }
         } else {
            MainRegistry.LOGGER.warn("bindBlockLitSamplerTextures: invalid atlas/lightmap GL id (atlas={}, lightmap={})", atlasGlId, lightmapGlId);
         }
      }
   }

   private boolean shouldRenderWithCulling(BlockPos blockPos, @Nullable BlockEntity blockEntity) {
      if (blockEntity != null && blockEntity.getLevel() != null) {
         AABB renderBounds = this.worldBoundsFromMesh(blockEntity);
         return OcclusionCullingHelper.shouldRender(blockPos, blockEntity.getLevel(), renderBounds);
      } else {
         return true;
      }
   }

   private AABB worldBoundsFromMesh(BlockEntity blockEntity) {
      BlockPos pos = blockEntity.getBlockPos();
      return new AABB(
         pos.getX() + this.objBbox[0],
         pos.getY() + this.objBbox[1],
         pos.getZ() + this.objBbox[2],
         pos.getX() + this.objBbox[3],
         pos.getY() + this.objBbox[4],
         pos.getZ() + this.objBbox[5]
      );
   }

   @Nullable
   private IrisCompanionMesh getOrBuildIrisCompanion() {
      if (this.irisCompanion != null && this.irisCompanion.isBuilt()) {
         return this.irisCompanion;
      }

      if (this.irisCompanion != null && this.irisCompanion.isFailed()) {
         return null;
      }

      if (this.irisCompanionAttempted && this.irisCompanion == null) {
         return null;
      }

      List<BakedQuad> quads = this.getQuadsForIrisPath();
      if (quads != null && !quads.isEmpty()) {
         if (this.irisCompanion == null) {
            this.irisCompanion = new IrisCompanionMesh(quads);
            this.irisCompanionAttempted = true;
         }

         return this.irisCompanion.ensureBuilt() ? this.irisCompanion : null;
      } else {
         this.irisCompanionAttempted = true;
         return null;
      }
   }

   protected void initVbo() {
      if (!this.initialized) {
         int previousVao = GL11.glGetInteger(34229);
         int previousArrayBuffer = GL11.glGetInteger(34964);
         int previousElementArrayBuffer = GL11.glGetInteger(34965);
         SingleMeshVboRenderer.VboData data = null;

         try {
            this.vaoId = GL30.glGenVertexArrays();
            this.vboId = GL15.glGenBuffers();
            data = this.buildVboData();
            if (data == null) {
               MainRegistry.LOGGER.warn("VboData is null, cannot initialize VBO");
               throw new IllegalStateException("VboData is null");
            }

            this.indexCount = data.indices != null ? data.indices.remaining() : 0;
            this.setObjBboxFrom(data);
            int vs = data.bytesPerVertex;
            GL30.glBindVertexArray(this.vaoId);
            GL15.glBindBuffer(34962, this.vboId);
            GL15.glBufferData(34962, data.byteBuffer, 35044);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 3, 5126, false, vs, 0L);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 3, 5126, false, vs, 12L);
            GL20.glEnableVertexAttribArray(2);
            GL20.glVertexAttribPointer(2, 2, 5126, false, vs, 24L);
            if (data.indices != null && data.indices.remaining() > 0) {
               this.eboId = GL15.glGenBuffers();
               GL15.glBindBuffer(34963, this.eboId);
               GL15.glBufferData(34963, data.indices, 35044);
            }

            GL30.glBindVertexArray(0);
            data.close();
            this.initialized = true;
         } catch (Exception e) {
            if (data != null) {
               data.close();
            }

            if (this.vaoId != -1) {
               GL30.glDeleteVertexArrays(this.vaoId);
               this.vaoId = -1;
            }

            if (this.vboId != -1) {
               GL15.glDeleteBuffers(this.vboId);
               this.vboId = -1;
            }

            if (this.eboId != -1) {
               GL15.glDeleteBuffers(this.eboId);
               this.eboId = -1;
            }

            throw e;
         } finally {
            GL15.glBindBuffer(34962, previousArrayBuffer);
            GL30.glBindVertexArray(previousVao);
            if (previousVao == 0) {
               GL15.glBindBuffer(34963, previousElementArrayBuffer);
            }
         }
      }
   }

   private void renderToBufferSource(PoseStack poseStack, int packedLight, List<BakedQuad> quads, MultiBufferSource bufferSource) {
      if (quads != null && !quads.isEmpty() && bufferSource != null) {
         float fade = currentFadeAlpha.get();
         VertexConsumer consumer = bufferSource.getBuffer(fade < 0.99F ? RenderType.translucent() : RenderType.cutout());
         Pose pose = poseStack.last();

         for (BakedQuad quad : quads) {
            consumer.putBulkData(pose, quad, 1.0F, 1.0F, 1.0F, fade, packedLight, OverlayTexture.NO_OVERLAY, false);
         }
      }
   }

   public void render(PoseStack poseStack, int packedLight, BlockPos blockPos) {
      this.render(poseStack, packedLight, blockPos, null, null);
   }

   public void render(PoseStack poseStack, int packedLight, BlockPos blockPos, @Nullable BlockEntity blockEntity) {
      this.render(poseStack, packedLight, blockPos, blockEntity, null);
   }

   public void render(PoseStack poseStack, int packedLight, BlockPos blockPos, @Nullable BlockEntity blockEntity, @Nullable MultiBufferSource bufferSource) {
      if (ShaderCompatibilityDetector.isExternalShaderActive()) {
         if (!this.renderWithIrisExtended(poseStack, packedLight, blockPos, blockEntity)) {
            List<BakedQuad> irisQuads = this.getQuadsForIrisPath();
            if (irisQuads != null && bufferSource != null) {
               this.renderToBufferSource(poseStack, packedLight, irisQuads, bufferSource);
            }
         }
      } else {
         if (!this.initialized && !this.initFailed) {
            try {
               this.initVbo();
            } catch (Exception e) {
               this.initFailed = true;
               MainRegistry.LOGGER.debug("VBO init failed (part has no geometry or other error), skipping: {}", e.getMessage());
               this.vaoId = -1;
               this.vboId = -1;
               this.eboId = -1;
               return;
            }
         }

         if (!this.initFailed) {
            if (this.initialized && this.vaoId > 0 && this.vboId > 0) {
               if (this.eboId > 0 && this.indexCount > 0) {
                  ShaderInstance shader = this.useSlicedLight ? ModShaders.getBlockLitSimpleSlicedShader() : ModShaders.getBlockLitSimpleShader();
                  if (shader == null) {
                     List<BakedQuad> fallbackQuads = this.getQuadsForIrisPath();
                     if (fallbackQuads != null && bufferSource != null) {
                        this.renderToBufferSource(poseStack, packedLight, fallbackQuads, bufferSource);
                     }
                  } else {
                     int previousVao = GL11.glGetInteger(34229);
                     int previousArrayBuffer = GL11.glGetInteger(34964);
                     boolean previousCullFaceEnabled = GL11.glIsEnabled(2884);
                     int previousDepthFunc = GL11.glGetInteger(2932);
                     boolean previousDepthMask = GL11.glGetBoolean(2930);
                     boolean previousDepthTestEnabled = GL11.glIsEnabled(2929);

                     try {
                        RenderSystem.setShader(() -> shader);
                        if (shader.MODEL_VIEW_MATRIX != null) {
                           shader.MODEL_VIEW_MATRIX.set(poseStack.last().pose());
                        }

                        if (shader.PROJECTION_MATRIX != null) {
                           shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
                        }

                        long partHash = System.identityHashCode(this);
                        if (LightSampleCache.BASE_POSE_SET.get()) {
                           this.tmpLocalPose.set((Matrix4fc)LightSampleCache.BASE_POSE.get()).invert().mul(poseStack.last().pose());
                        } else {
                           Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                           this.tmpInvViewRot.identity().set(RenderSystem.getInverseViewRotationMatrix());
                           this.tmpLocalPose.set(this.tmpInvViewRot).mul(poseStack.last().pose());
                           this.tmpLocalPose.m30(this.tmpLocalPose.m30() - (float)(blockPos.getX() - cam.x));
                           this.tmpLocalPose.m31(this.tmpLocalPose.m31() - (float)(blockPos.getY() - cam.y));
                           this.tmpLocalPose.m32(this.tmpLocalPose.m32() - (float)(blockPos.getZ() - cam.z));
                        }

                        if (this.useSlicedLight) {
                           LightSampleCache.getOrSample16(blockEntity, partHash, this.objBbox, blockPos, this.tmpLocalPose, packedLight, this.tmpProbeUV);
                        } else {
                           LightSampleCache.getOrSample8(blockEntity, partHash, this.objBbox, blockPos, this.tmpLocalPose, packedLight, this.tmpCornerUV);
                        }

                        this.updateBlockLitUniformCache(shader);
                        if (this.cachedBboxMinU != null) {
                           this.cachedBboxMinU.set(this.objBbox[0], this.objBbox[1], this.objBbox[2]);
                        }

                        if (this.cachedBboxSizeU != null) {
                           this.cachedBboxSizeU
                              .set(
                                 Math.max(1.0E-4F, this.objBbox[3] - this.objBbox[0]),
                                 Math.max(1.0E-4F, this.objBbox[4] - this.objBbox[1]),
                                 Math.max(1.0E-4F, this.objBbox[5] - this.objBbox[2])
                              );
                        }

                        if (this.useSlicedLight) {
                           if (this.cachedLightS0C01 != null) {
                              this.cachedLightS0C01.set(this.tmpProbeUV[0], this.tmpProbeUV[1], this.tmpProbeUV[2], this.tmpProbeUV[3]);
                           }

                           if (this.cachedLightS0C23 != null) {
                              this.cachedLightS0C23.set(this.tmpProbeUV[4], this.tmpProbeUV[5], this.tmpProbeUV[6], this.tmpProbeUV[7]);
                           }

                           if (this.cachedLightS1C01 != null) {
                              this.cachedLightS1C01.set(this.tmpProbeUV[8], this.tmpProbeUV[9], this.tmpProbeUV[10], this.tmpProbeUV[11]);
                           }

                           if (this.cachedLightS1C23 != null) {
                              this.cachedLightS1C23.set(this.tmpProbeUV[12], this.tmpProbeUV[13], this.tmpProbeUV[14], this.tmpProbeUV[15]);
                           }

                           if (this.cachedLightS2C01 != null) {
                              this.cachedLightS2C01.set(this.tmpProbeUV[16], this.tmpProbeUV[17], this.tmpProbeUV[18], this.tmpProbeUV[19]);
                           }

                           if (this.cachedLightS2C23 != null) {
                              this.cachedLightS2C23.set(this.tmpProbeUV[20], this.tmpProbeUV[21], this.tmpProbeUV[22], this.tmpProbeUV[23]);
                           }

                           if (this.cachedLightS3C01 != null) {
                              this.cachedLightS3C01.set(this.tmpProbeUV[24], this.tmpProbeUV[25], this.tmpProbeUV[26], this.tmpProbeUV[27]);
                           }

                           if (this.cachedLightS3C23 != null) {
                              this.cachedLightS3C23.set(this.tmpProbeUV[28], this.tmpProbeUV[29], this.tmpProbeUV[30], this.tmpProbeUV[31]);
                           }
                        } else {
                           if (this.cachedLightC01 != null) {
                              this.cachedLightC01.set(this.tmpCornerUV[0], this.tmpCornerUV[1], this.tmpCornerUV[2], this.tmpCornerUV[3]);
                           }

                           if (this.cachedLightC23 != null) {
                              this.cachedLightC23.set(this.tmpCornerUV[4], this.tmpCornerUV[5], this.tmpCornerUV[6], this.tmpCornerUV[7]);
                           }

                           if (this.cachedLightC45 != null) {
                              this.cachedLightC45.set(this.tmpCornerUV[8], this.tmpCornerUV[9], this.tmpCornerUV[10], this.tmpCornerUV[11]);
                           }

                           if (this.cachedLightC67 != null) {
                              this.cachedLightC67.set(this.tmpCornerUV[12], this.tmpCornerUV[13], this.tmpCornerUV[14], this.tmpCornerUV[15]);
                           }
                        }

                        if (!worldMissileOverlayDraw.get() && !entityMissileDepthBias.get()) {
                           if (this.cachedFogStartU != null) {
                              this.cachedFogStartU.set(RenderSystem.getShaderFogStart());
                           }

                           if (this.cachedFogEndU != null) {
                              this.cachedFogEndU.set(RenderSystem.getShaderFogEnd());
                           }
                        } else {
                           if (this.cachedFogStartU != null) {
                              this.cachedFogStartU.set(1.0E8F);
                           }

                           if (this.cachedFogEndU != null) {
                              this.cachedFogEndU.set(1.0E8F);
                           }
                        }

                        if (this.cachedFogColorU != null) {
                           float[] fogColor = RenderSystem.getShaderFogColor();
                           this.cachedFogColorU.set(fogColor[0], fogColor[1], fogColor[2], fogColor[3]);
                        }

                        if (this.cachedFadeAlphaU != null) {
                           this.cachedFadeAlphaU.set(currentFadeAlpha.get());
                        }

                        prepareBlockLitSamplers(shader);
                        shader.apply();
                        bindBlockLitSamplerTextures(shader);
                        float fade = currentFadeAlpha.get();
                        boolean overlay = worldMissileOverlayDraw.get();
                        if (overlay) {
                           RenderSystem.disableDepthTest();
                           GL11.glDepthMask(false);
                        } else {
                           RenderSystem.enableDepthTest();
                           RenderSystem.depthFunc(515);
                           RenderSystem.depthMask(true);
                        }

                        RenderSystem.disableCull();
                        if (fade < 0.99F) {
                           RenderSystem.enableBlend();
                           RenderSystem.defaultBlendFunc();
                        }

                        GL30.glBindVertexArray(this.vaoId);
                        beginEntityMissileDepthBias();
                        GL11.glDrawElements(4, this.indexCount, 5125, 0L);
                        endEntityMissileDepthBias();
                        if (fade < 0.99F) {
                           RenderSystem.disableBlend();
                        }
                     } catch (Exception e) {
                        MainRegistry.LOGGER.error("Error during VBO render", e);
                     } finally {
                        GL30.glBindVertexArray(0);
                        RenderSystem.setShader(GameRenderer::getRendertypeSolidShader);
                        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
                        GlVaoSafety.bindVertexArray(previousVao);
                        GL15.glBindBuffer(34962, previousArrayBuffer);
                        if (previousCullFaceEnabled) {
                           RenderSystem.enableCull();
                        } else {
                           RenderSystem.disableCull();
                        }

                        RenderSystem.depthFunc(previousDepthFunc);
                        GL11.glDepthMask(previousDepthMask);
                        if (previousDepthTestEnabled) {
                           RenderSystem.enableDepthTest();
                        } else {
                           RenderSystem.disableDepthTest();
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean renderWithIrisExtended(PoseStack poseStack, int packedLight, BlockPos blockPos, @Nullable BlockEntity blockEntity) {
      IrisCompanionMesh companion = this.getOrBuildIrisCompanion();
      if (companion == null) {
         return false;
      }

      boolean shadowPassEarly = ShaderCompatibilityDetector.isRenderingShadowPass();
      IrisRenderBatch batchEarly = IrisRenderBatch.active();
      if (batchEarly != null) {
         shadowPassEarly = batchEarly.isShadowPass();
      }

      boolean haveCorners = false;
      boolean haveSlicedProbes = false;
      if (!shadowPassEarly && companion.supportsPerVertexLightmap()) {
         BlockPos anchor = blockEntity != null ? blockEntity.getBlockPos() : blockPos;
         if (anchor == null) {
            anchor = BlockPos.ZERO;
         }

         if (LightSampleCache.BASE_POSE_SET.get()) {
            this.tmpLocalPose.set((Matrix4fc)LightSampleCache.BASE_POSE.get()).invert().mul(poseStack.last().pose());
         } else {
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            this.tmpInvViewRot.identity().set(RenderSystem.getInverseViewRotationMatrix());
            this.tmpLocalPose.set(this.tmpInvViewRot).mul(poseStack.last().pose());
            this.tmpLocalPose.m30(this.tmpLocalPose.m30() - (float)(anchor.getX() - cam.x));
            this.tmpLocalPose.m31(this.tmpLocalPose.m31() - (float)(anchor.getY() - cam.y));
            this.tmpLocalPose.m32(this.tmpLocalPose.m32() - (float)(anchor.getZ() - cam.z));
         }

         long partHash = System.identityHashCode(this);
         if (this.useSlicedLight && companion.supportsSlicedPerVertexLightmap()) {
            LightSampleCache.getOrSample16(blockEntity, partHash, this.objBbox, anchor, this.tmpLocalPose, packedLight, this.tmpProbeUV);
            haveSlicedProbes = true;
         } else {
            LightSampleCache.getOrSample8(blockEntity, partHash, this.objBbox, anchor, this.tmpLocalPose, packedLight, this.tmpCornerUV);
         }

         haveCorners = true;
      }

      IrisRenderBatch batch = IrisRenderBatch.active();
      if (batch != null) {
         if (haveCorners && haveSlicedProbes) {
            batch.drawCompanionWithSlicedPerVertexLight(companion, poseStack.last().pose(), this.tmpProbeUV, packedLight);
         } else if (haveCorners) {
            batch.drawCompanionWithPerVertexLight(companion, poseStack.last().pose(), this.tmpCornerUV, packedLight);
         } else {
            batch.drawCompanion(companion, poseStack.last().pose(), packedLight);
         }

         return true;
      } else {
         boolean shadowPass = ShaderCompatibilityDetector.isRenderingShadowPass();
         ShaderInstance shader = IrisExtendedShaderAccess.getBlockShader(shadowPass);
         if (shader == null) {
            return false;
         }

         int previousVao = GL11.glGetInteger(34229);
         int previousArrayBuffer = GL11.glGetInteger(34964);
         boolean previousCullFaceEnabled = GL11.glIsEnabled(2884);
         int previousBlockEntityId = IrisExtendedShaderAccess.setCurrentRenderedBlockEntity(0);

         try (IrisPhaseGuard ignored = IrisPhaseGuard.pushBlockEntities()) {
            RenderSystem.setShader(() -> shader);
            if (shader.MODEL_VIEW_MATRIX != null) {
               shader.MODEL_VIEW_MATRIX.set(poseStack.last().pose());
            }

            if (shader.PROJECTION_MATRIX != null) {
               shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
            }

            Uniform brightnessUniform = shader.getUniform("Brightness");
            if (brightnessUniform != null) {
               brightnessUniform.set(this.calculateBrightness(packedLight));
            }

            if (worldMissileOverlayDraw.get()) {
               Uniform fogStart = shader.getUniform("FogStart");
               if (fogStart != null) {
                  fogStart.set(1.0E8F);
               }

               Uniform fogEnd = shader.getUniform("FogEnd");
               if (fogEnd != null) {
                  fogEnd.set(1.0E8F);
               }
            }

            Uniform sampler0 = shader.getUniform("Sampler0");
            if (sampler0 != null) {
               sampler0.set(0);
            }

            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
            Minecraft.getInstance().gameRenderer.overlayTexture().setupOverlayColor();
            Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
            SingleMeshVboRenderer.TextureBinder.bindForModelIfNeeded(shader);
            GlStateManager._glBindVertexArray(companion.getVaoId());
            if (!IrisShaderApply.tryApply(shader)) {
               return false;
            }

            boolean overlay = worldMissileOverlayDraw.get();
            if (overlay) {
               RenderSystem.disableDepthTest();
               GL11.glDepthMask(false);
            } else {
               RenderSystem.enableDepthTest();
               RenderSystem.depthFunc(515);
               RenderSystem.depthMask(true);
            }

            RenderSystem.disableCull();
            companion.prepareForShader(shader.getId());
            int uv2Loc = companion.getUv2Location();
            if (haveCorners && companion.supportsPerVertexLightmap()) {
               companion.ensureLightmapCapacity(1);
               if (haveSlicedProbes) {
                  companion.writeInstanceLightmap(0, this.tmpProbeUV);
               } else {
                  companion.writeInstanceLightmap(0, this.tmpCornerUV);
               }

               companion.finishLightmapWrites();
               companion.activatePerVertexLightmap();
               companion.bindLightmapForInstance(0);
            } else if (uv2Loc != -1) {
               companion.restoreConstantLightmap();
               int blockU = Math.max(0, Math.min(240, packedLight & 65535));
               int skyV = Math.max(0, Math.min(240, packedLight >>> 16 & 65535));
               companion.bindVaoIfNeeded();
               GL30.glVertexAttribI2i(uv2Loc, blockU, skyV);
            }

            beginEntityMissileDepthBias();
            companion.bindVaoIfNeeded();
            GL11.glDrawElements(4, companion.getIndexCount(), 5125, 0L);
            endEntityMissileDepthBias();
            shader.clear();
            return true;
         } catch (Exception e) {
            MainRegistry.LOGGER.error("SingleMeshVboRenderer.renderWithIrisExtended failed", e);
            return false;
         } finally {
            if (companion != null) {
               companion.restoreConstantLightmap();
            }

            GL30.glBindVertexArray(0);
            RenderSystem.setShader(GameRenderer::getRendertypeSolidShader);
            GlVaoSafety.bindVertexArray(previousVao);
            GL15.glBindBuffer(34962, previousArrayBuffer);
            if (worldMissileOverlayDraw.get()) {
               RenderSystem.enableDepthTest();
               GL11.glDepthMask(true);
            }

            if (previousCullFaceEnabled) {
               RenderSystem.enableCull();
            } else {
               RenderSystem.disableCull();
            }

            RenderSystem.setShader(GameRenderer::getRendertypeSolidShader);
            IrisExtendedShaderAccess.restoreCurrentRenderedBlockEntity(previousBlockEntityId);
         }
      }
   }

   private float calculateBrightness(int packedLight) {
      int blockLight = LightTexture.block(packedLight);
      int skyLight = LightTexture.sky(packedLight);
      ClientLevel level = Minecraft.getInstance().level;
      if (level == null) {
         return Math.max(0.05F, Math.max(blockLight, skyLight) / 15.0F);
      }

      float skyDarken = level.getSkyDarken(1.0F);
      float skyBrightness = 0.05F + skyDarken * 0.95F;
      float effectiveSkyLight = skyLight * skyBrightness;
      float maxLight = Math.max(blockLight, effectiveSkyLight);
      return 0.05F + maxLight / 15.0F * 0.95F;
   }

   @Override
   public void cleanup() {
      super.cleanup();
      IrisCompanionMesh toDestroy = this.irisCompanion;
      this.irisCompanion = null;
      if (toDestroy != null) {
         toDestroy.destroy();
      }
   }

   static final class TextureBinder {
      static void bindForModelIfNeeded(ShaderInstance shader) {
         Minecraft minecraft = Minecraft.getInstance();
         TextureManager textureManager = minecraft.getTextureManager();
         RenderSystem.activeTexture(33984);
         AbstractTexture blockAtlas = textureManager.getTexture(TextureAtlas.LOCATION_BLOCKS);
         RenderSystem.bindTexture(blockAtlas.getId());
      }
   }

   public static class VboData implements AutoCloseable {
      public final ByteBuffer byteBuffer;
      public final IntBuffer indices;
      public final float minX;
      public final float minY;
      public final float minZ;
      public final float maxX;
      public final float maxY;
      public final float maxZ;
      public final int bytesPerVertex;
      private boolean consumed = false;

      public VboData(ByteBuffer byteBuffer, IntBuffer indices) {
         this(byteBuffer, indices, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 36);
      }

      public VboData(ByteBuffer byteBuffer, IntBuffer indices, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
         this(byteBuffer, indices, minX, minY, minZ, maxX, maxY, maxZ, 36);
      }

      public VboData(ByteBuffer byteBuffer, IntBuffer indices, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int bytesPerVertex) {
         this.byteBuffer = byteBuffer;
         this.indices = indices;
         this.minX = minX;
         this.minY = minY;
         this.minZ = minZ;
         this.maxX = maxX;
         this.maxY = maxY;
         this.maxZ = maxZ;
         this.bytesPerVertex = bytesPerVertex;
      }

      public boolean isConsumed() {
         return this.consumed;
      }

      @Override
      public void close() {
         if (this.consumed) {
            assert false : "VboData.close() called twice (already consumed)";
         } else {
            this.consumed = true;
            if (this.byteBuffer != null) {
               MemoryUtil.memFree(this.byteBuffer);
            }

            if (this.indices != null) {
               MemoryUtil.memFree(this.indices);
            }
         }
      }
   }
}
