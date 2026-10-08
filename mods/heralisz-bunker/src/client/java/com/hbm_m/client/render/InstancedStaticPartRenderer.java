package com.hbm_m.client.render;

import com.hbm_m.client.render.culling.OcclusionCullingHelper;
import com.hbm_m.client.render.shader.ShaderCompatibilityDetector;
import com.hbm_m.config.ModClothConfig;
import com.hbm_m.main.MainRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.lang.ref.Cleaner;
import java.lang.ref.Cleaner.Cleanable;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

@OnlyIn(Dist.CLIENT)
public class InstancedStaticPartRenderer extends AbstractGpuMesh implements VanillaInstancedMeshRenderer, IrisCompanionMeshRenderer {
   final int maxInstances = ClientRenderFlags.maxInstances();
   private static final AtomicInteger OVERFLOW_ADD_COUNT = new AtomicInteger();
   static final int INSTANCE_ATTRIB_FIRST = 4;
   static final int LIGHT_FLOAT_OFFSET = 14;
   private static final int BASE_INSTANCE_DATA_SIZE = 30;
   private static final int SLICED_INSTANCE_DATA_SIZE = 46;
   final boolean useSlicedLight;
   final boolean storesPerInstancePartBone;
   final int instanceDataSize;
   final int instanceAttribLast;
   final int instanceFadeFloatOffset;
   final int lightFloatCount;
   int instanceCount = 0;
   final int[] instanceCullIndices = new int[this.maxInstances];
   final long[] instanceOcclusionKeys = new long[this.maxInstances];
   float batchSkyDarken = -1.0F;
   private boolean overflowLogged = false;
   static volatile boolean warnedInstancedShaderNullFlush;
   final float[] instanceLightUV = new float[this.maxInstances * 2];
   final Vector3f posTmp = new Vector3f();
   final Quaternionf rotTmp = new Quaternionf();
   final Matrix4f tmpLocalPose = new Matrix4f();
   final Matrix4f tmpInvViewRot = new Matrix4f();
   final float[] tmpCornerUV;
   int instanceVboId = -1;
   FloatBuffer instanceBuffer;
   private long instanceBufferAddress;
   ByteBuffer atlasVertexBytesRetained;
   IntBuffer atlasIndicesRetained;
   int atlasIndexCountRetained;
   @Nullable
   private String mdiTraceTag;
   private static final Cleaner CLEANER = Cleaner.create();
   private Cleanable instanceBufferCleanable;
   final List<BakedQuad> quadsForIris;
   final VanillaInstancedBatchRenderer vanillaHelper;
   private final IrisInstancedBatchRenderer irisHelper;
   private final Matrix4f tmpInstanceMat = new Matrix4f();

   public static int drainOverflowAddCount() {
      return OVERFLOW_ADD_COUNT.getAndSet(0);
   }

   public InstancedStaticPartRenderer(SingleMeshVboRenderer.VboData data) {
      this(data, null, false, false);
   }

   public InstancedStaticPartRenderer(SingleMeshVboRenderer.VboData data, List<BakedQuad> quadsForIris) {
      this(data, quadsForIris, false, false);
   }

   public InstancedStaticPartRenderer(SingleMeshVboRenderer.VboData data, List<BakedQuad> quadsForIris, boolean useSlicedLight) {
      this(data, quadsForIris, useSlicedLight, false);
   }

   public InstancedStaticPartRenderer(
      SingleMeshVboRenderer.VboData data, List<BakedQuad> quadsForIris, boolean useSlicedLight, boolean storesPerInstancePartBone
   ) {
      this.quadsForIris = quadsForIris;
      this.useSlicedLight = useSlicedLight;
      this.storesPerInstancePartBone = storesPerInstancePartBone;
      this.instanceDataSize = useSlicedLight ? 46 : 30;
      this.instanceAttribLast = useSlicedLight ? 15 : 11;
      this.instanceFadeFloatOffset = 13;
      this.lightFloatCount = useSlicedLight ? 32 : 16;
      this.tmpCornerUV = new float[this.lightFloatCount];
      this.vanillaHelper = new VanillaInstancedBatchRenderer(this);
      this.irisHelper = new IrisInstancedBatchRenderer(this);
      if (data == null) {
         MainRegistry.LOGGER.error("InstancedStaticPartRenderer: Received NULL VboData! Cannot create renderer.");
         this.initialized = false;
      } else if (!RenderSystem.isOnRenderThread()) {
         MainRegistry.LOGGER.warn("InstancedStaticPartRenderer: Skipping initialization because this is not render thread.");
         data.close();
         this.initialized = false;
      } else if (GLFW.glfwGetCurrentContext() == 0L) {
         MainRegistry.LOGGER.warn("InstancedStaticPartRenderer: No current GLFW OpenGL context; falling back to non-instanced render path.");
         data.close();
         this.initialized = false;
      } else if (!InstancedGlCompat.supportsInstancedAttributeDivisor()) {
         MainRegistry.LOGGER.warn("InstancedStaticPartRenderer: Instancing entrypoints unavailable. Falling back to non-instanced render path.");
         data.close();
         this.initialized = false;
      } else {
         int previousVao = GL11.glGetInteger(34229);
         int previousArrayBuffer = GL11.glGetInteger(34964);

         try {
            this.vaoId = GL30.glGenVertexArrays();
            this.vboId = GL15.glGenBuffers();
            if (this.vaoId == 0 || this.vboId == 0) {
               throw new IllegalStateException("Failed to generate VAO/VBO!");
            }

            this.indexCount = data.indices != null ? data.indices.remaining() : 0;
            this.setObjBboxFrom(data);
            if (data.bytesPerVertex != 36) {
               throw new IllegalStateException("InstancedStaticPartRenderer expects VboData.bytesPerVertex=36 got " + data.bytesPerVertex);
            }

            int meshStride = data.bytesPerVertex;
            GL30.glBindVertexArray(this.vaoId);
            GL15.glBindBuffer(34962, this.vboId);
            GL15.glBufferData(34962, data.byteBuffer, 35044);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 3, 5126, false, meshStride, 0L);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 3, 5126, false, meshStride, 12L);
            GL20.glEnableVertexAttribArray(2);
            GL20.glVertexAttribPointer(2, 2, 5126, false, meshStride, 24L);
            GL30.glEnableVertexAttribArray(3);
            GL30.glVertexAttribIPointer(3, 1, 5124, meshStride, 32L);
            if (data.indices != null && data.indices.remaining() > 0) {
               this.eboId = GL15.glGenBuffers();
               if (this.eboId == 0) {
                  throw new IllegalStateException("Failed to generate EBO!");
               }

               GL15.glBindBuffer(34963, this.eboId);
               GL15.glBufferData(34963, data.indices, 35044);
            }

            this.instanceVboId = GL15.glGenBuffers();
            if (this.instanceVboId == 0) {
               throw new IllegalStateException("Failed to generate instance VBO!");
            }

            GL15.glBindBuffer(34962, this.instanceVboId);
            GL15.glBufferData(34962, (long)this.maxInstances * this.instanceDataSize * 4L, 35040);
            int stride = this.instanceDataSize * 4;
            GL20.glEnableVertexAttribArray(4);
            GL20.glVertexAttribPointer(4, 3, 5126, false, stride, 0L);
            InstancedGlCompat.glVertexAttribDivisorCompat(4, 1);
            GL20.glEnableVertexAttribArray(5);
            GL20.glVertexAttribPointer(5, 4, 5126, false, stride, 12L);
            InstancedGlCompat.glVertexAttribDivisorCompat(5, 1);
            GL20.glEnableVertexAttribArray(6);
            GL20.glVertexAttribPointer(6, 3, 5126, false, stride, 28L);
            InstancedGlCompat.glVertexAttribDivisorCompat(6, 1);
            GL20.glEnableVertexAttribArray(7);
            GL20.glVertexAttribPointer(7, 4, 5126, false, stride, 40L);
            InstancedGlCompat.glVertexAttribDivisorCompat(7, 1);
            if (!useSlicedLight) {
               GL20.glEnableVertexAttribArray(8);
               GL20.glVertexAttribPointer(8, 4, 5126, false, stride, 56L);
               InstancedGlCompat.glVertexAttribDivisorCompat(8, 1);
               GL20.glEnableVertexAttribArray(9);
               GL20.glVertexAttribPointer(9, 4, 5126, false, stride, 72L);
               InstancedGlCompat.glVertexAttribDivisorCompat(9, 1);
               GL20.glEnableVertexAttribArray(10);
               GL20.glVertexAttribPointer(10, 4, 5126, false, stride, 88L);
               InstancedGlCompat.glVertexAttribDivisorCompat(10, 1);
               GL20.glEnableVertexAttribArray(11);
               GL20.glVertexAttribPointer(11, 4, 5126, false, stride, 104L);
               InstancedGlCompat.glVertexAttribDivisorCompat(11, 1);
            } else {
               for (int a = 0; a < 8; a++) {
                  int loc = 8 + a;
                  GL20.glEnableVertexAttribArray(loc);
                  GL20.glVertexAttribPointer(loc, 4, 5126, false, stride, (14 + a * 4) * 4L);
                  InstancedGlCompat.glVertexAttribDivisorCompat(loc, 1);
               }
            }

            GL30.glBindVertexArray(0);
            this.instanceBuffer = MemoryUtil.memAllocFloat(this.maxInstances * this.instanceDataSize);
            this.instanceBufferAddress = MemoryUtil.memAddress0(this.instanceBuffer);
            long bufferAddress = MemoryUtil.memAddress(this.instanceBuffer);
            this.instanceBufferCleanable = CLEANER.register(this, () -> {
               try {
                  if (bufferAddress != 0L) {
                     MemoryUtil.nmemFree(bufferAddress);
                  }
               } catch (Throwable t) {
                  MainRegistry.LOGGER.error("Failed to free instanceBuffer via Cleaner", t);
               }
            });
            if (!storesPerInstancePartBone && !useSlicedLight && data.byteBuffer != null && data.indices != null && data.indices.remaining() > 0) {
               try {
                  ByteBuffer srcVb = data.byteBuffer.duplicate();
                  this.atlasVertexBytesRetained = MemoryUtil.memAlloc(srcVb.remaining());
                  this.atlasVertexBytesRetained.put(srcVb);
                  this.atlasVertexBytesRetained.flip();
                  IntBuffer srcIb = data.indices.duplicate();
                  this.atlasIndexCountRetained = srcIb.remaining();
                  this.atlasIndicesRetained = MemoryUtil.memAllocInt(this.atlasIndexCountRetained);
                  this.atlasIndicesRetained.put(srcIb);
                  this.atlasIndicesRetained.flip();
               } catch (Throwable t) {
                  MainRegistry.LOGGER.warn("InstancedStaticPartRenderer: failed to retain MDI atlas copy ({}); MDI path will skip this renderer", t.toString());
                  if (this.atlasVertexBytesRetained != null) {
                     MemoryUtil.memFree(this.atlasVertexBytesRetained);
                     this.atlasVertexBytesRetained = null;
                  }

                  if (this.atlasIndicesRetained != null) {
                     MemoryUtil.memFree(this.atlasIndicesRetained);
                     this.atlasIndicesRetained = null;
                  }

                  this.atlasIndexCountRetained = 0;
               }
            }

            data.close();
            this.initialized = true;
         } catch (Exception e) {
            MainRegistry.LOGGER.error("Failed to initialize InstancedStaticPartRenderer", e);
            this.cleanup();
            this.initialized = false;
         } finally {
            GL15.glBindBuffer(34962, previousArrayBuffer);
            GL30.glBindVertexArray(previousVao);
         }
      }
   }

   void memPutInstanceRecordAtBaseFloat(int baseFloatIndex) {
      long a = this.instanceBufferAddress + baseFloatIndex * 4L;
      MemoryUtil.memPutFloat(a, this.posTmp.x);
      MemoryUtil.memPutFloat(a + 4L, this.posTmp.y);
      MemoryUtil.memPutFloat(a + 8L, this.posTmp.z);
      MemoryUtil.memPutFloat(a + 12L, this.rotTmp.x);
      MemoryUtil.memPutFloat(a + 16L, this.rotTmp.y);
      MemoryUtil.memPutFloat(a + 20L, this.rotTmp.z);
      MemoryUtil.memPutFloat(a + 24L, this.rotTmp.w);
      MemoryUtil.memPutFloat(a + 28L, this.objBbox[0]);
      MemoryUtil.memPutFloat(a + 32L, this.objBbox[1]);
      MemoryUtil.memPutFloat(a + 36L, this.objBbox[2]);
      float sx = this.objBbox[3] - this.objBbox[0];
      float sy = this.objBbox[4] - this.objBbox[1];
      float sz = this.objBbox[5] - this.objBbox[2];
      MemoryUtil.memPutFloat(a + 40L, sx);
      MemoryUtil.memPutFloat(a + 44L, sy);
      MemoryUtil.memPutFloat(a + 48L, sz);
      MemoryUtil.memPutFloat(a + 52L, SingleMeshVboRenderer.getFadeAlpha());
      long lightA = a + 56L;

      for (int i = 0; i < this.lightFloatCount; i++) {
         MemoryUtil.memPutFloat(lightA + i * 4L, this.tmpCornerUV[i]);
      }
   }

   void uploadInstanceStreamToBoundVbo() {
      ModClothConfig cfg = ModClothConfig.get();
      if (cfg.instanceVboOrphanBeforeUpload) {
         GL15.glBufferData(34962, (long)this.maxInstances * this.instanceDataSize * 4L, 35040);
      }

      GL15.glBufferSubData(34962, 0L, this.instanceBuffer);
   }

   protected List<BakedQuad> getQuadsForIrisPath() {
      return this.quadsForIris;
   }

   public void renderSingle(PoseStack poseStack, int packedLight, BlockPos blockPos, @Nullable BlockEntity blockEntity) {
      this.renderSingle(poseStack, packedLight, blockPos, blockEntity, null);
   }

   @Override
   public void renderSingle(
      PoseStack poseStack, int packedLight, BlockPos blockPos, @Nullable BlockEntity blockEntity, @Nullable MultiBufferSource bufferSource
   ) {
      if (this.initialized && this.vaoId > 0 && this.eboId > 0 && this.indexCount > 0 && this.instanceVboId > 0 && this.instanceBuffer != null) {
         if (!ShaderCompatibilityDetector.isExternalShaderActive()) {
            this.vanillaHelper.renderSingleVanilla(poseStack, packedLight, blockPos, blockEntity, bufferSource);
         } else if (!this.irisHelper.drawSingleWithIrisExtended(poseStack, packedLight, blockPos, blockEntity)) {
            if (this.quadsForIris != null && !this.quadsForIris.isEmpty() && bufferSource != null) {
               float fade = SingleMeshVboRenderer.getFadeAlpha();
               VertexConsumer consumer = bufferSource.getBuffer(fade < 0.99F ? RenderType.translucent() : RenderType.solid());
               Pose pose = poseStack.last();

               for (BakedQuad quad : this.quadsForIris) {
                  consumer.putBulkData(pose, quad, 1.0F, 1.0F, 1.0F, fade, packedLight, OverlayTexture.NO_OVERLAY, false);
               }
            }
         }
      }
   }

   public void addInstance(PoseStack poseStack, int packedLight, BlockPos blockPos, @Nullable BlockEntity blockEntity) {
      this.addInstance(poseStack, packedLight, blockPos, blockEntity, null);
   }

   @Override
   public void addInstance(PoseStack poseStack, int packedLight, BlockPos blockPos, @Nullable BlockEntity blockEntity, @Nullable MultiBufferSource bufferSource) {
      this.addInstance(poseStack, packedLight, blockPos, blockEntity, bufferSource, null);
   }

   public void addInstance(
      PoseStack poseStack,
      int packedLight,
      BlockPos blockPos,
      @Nullable BlockEntity blockEntity,
      @Nullable MultiBufferSource bufferSource,
      @Nullable float[] sharedCornerUV8
   ) {
      if (this.initialized) {
         if (ShaderCompatibilityDetector.isRenderingShadowPass()) {
            if (!this.irisHelper.drawSingleWithIrisExtended(poseStack, packedLight, blockPos, blockEntity)) {
               if (this.quadsForIris != null && !this.quadsForIris.isEmpty() && bufferSource != null) {
                  float fade = SingleMeshVboRenderer.getFadeAlpha();
                  VertexConsumer consumer = bufferSource.getBuffer(fade < 0.99F ? RenderType.translucent() : RenderType.solid());
                  Pose pose = poseStack.last();

                  for (BakedQuad quad : this.quadsForIris) {
                     consumer.putBulkData(pose, quad, 1.0F, 1.0F, 1.0F, fade, packedLight, OverlayTexture.NO_OVERLAY, false);
                  }
               }
            }
         } else if (this.instanceCount >= this.maxInstances) {
            OVERFLOW_ADD_COUNT.incrementAndGet();
            if (!this.overflowLogged) {
               this.overflowLogged = true;
               MainRegistry.LOGGER
                  .warn(
                     "InstancedStaticPartRenderer overflow: maxInstances={} reached for tag={}, skipping extra instances until next flush",
                     this.maxInstances,
                     this.mdiTraceTag
                  );
            }
         } else {
            if (this.instanceCount == 0) {
               this.overflowLogged = false;
               ClientLevel level = Minecraft.getInstance().level;
               this.batchSkyDarken = level != null ? level.getSkyDarken(1.0F) : -1.0F;
            }

            Matrix4f mat = poseStack.last().pose();
            mat.getTranslation(this.posTmp);
            mat.getNormalizedRotation(this.rotTmp);
            this.fillInstanceCornerLight(blockEntity, packedLight, blockPos, mat, sharedCornerUV8);
            this.instanceCullIndices[this.instanceCount] = -1;
            this.instanceOcclusionKeys[this.instanceCount] = OcclusionCullingHelper.occlusionKeyForBlock(blockPos);
            int baseFloat = this.instanceCount * this.instanceDataSize;
            this.memPutInstanceRecordAtBaseFloat(baseFloat);
            this.instanceCount++;
            ((Buffer)this.instanceBuffer).position(this.instanceDataSize * this.instanceCount);
         }
      }
   }

   public void addInstanceGpuBones(
      PoseStack baseBlockPose,
      Matrix4f partLocalToBlock,
      int packedLight,
      BlockPos blockPos,
      @Nullable BlockEntity blockEntity,
      @Nullable MultiBufferSource bufferSource
   ) {
      this.addInstanceGpuBones(baseBlockPose, partLocalToBlock, packedLight, blockPos, blockEntity, bufferSource, null);
   }

   public void addInstanceGpuBones(
      PoseStack baseBlockPose,
      Matrix4f partLocalToBlock,
      int packedLight,
      BlockPos blockPos,
      @Nullable BlockEntity blockEntity,
      @Nullable MultiBufferSource bufferSource,
      @Nullable float[] sharedCornerUV8
   ) {
      if (!this.initialized || !this.storesPerInstancePartBone) {
         this.addInstance(baseBlockPose, packedLight, blockPos, blockEntity, bufferSource, sharedCornerUV8);
      } else if (ShaderCompatibilityDetector.isRenderingShadowPass()) {
         PoseStack composed = new PoseStack();
         composed.pushPose();
         composed.last().pose().set(baseBlockPose.last().pose()).mul(partLocalToBlock);

         try {
            if (this.irisHelper.drawSingleWithIrisExtended(composed, packedLight, blockPos, blockEntity)) {
               return;
            }

            if (this.quadsForIris != null && !this.quadsForIris.isEmpty() && bufferSource != null) {
               float fade = SingleMeshVboRenderer.getFadeAlpha();
               VertexConsumer consumer = bufferSource.getBuffer(fade < 0.99F ? RenderType.translucent() : RenderType.solid());
               Pose pose = composed.last();

               for (BakedQuad quad : this.quadsForIris) {
                  consumer.putBulkData(pose, quad, 1.0F, 1.0F, 1.0F, fade, packedLight, OverlayTexture.NO_OVERLAY, false);
               }
            }
         } finally {
            composed.popPose();
         }
      } else if (ShaderCompatibilityDetector.isExternalShaderActive()) {
         PoseStack composed = new PoseStack();
         composed.pushPose();
         composed.last().pose().set(baseBlockPose.last().pose()).mul(partLocalToBlock);

         try {
            if (this.irisHelper.drawSingleWithIrisExtended(composed, packedLight, blockPos, blockEntity)) {
               return;
            }

            if (this.quadsForIris != null && !this.quadsForIris.isEmpty() && bufferSource != null) {
               float fade = SingleMeshVboRenderer.getFadeAlpha();
               VertexConsumer consumer = bufferSource.getBuffer(fade < 0.99F ? RenderType.translucent() : RenderType.solid());
               Pose pose = composed.last();

               for (BakedQuad quad : this.quadsForIris) {
                  consumer.putBulkData(pose, quad, 1.0F, 1.0F, 1.0F, fade, packedLight, OverlayTexture.NO_OVERLAY, false);
               }
            }
         } finally {
            composed.popPose();
         }
      } else if (this.instanceCount >= this.maxInstances) {
         OVERFLOW_ADD_COUNT.incrementAndGet();
         if (!this.overflowLogged) {
            this.overflowLogged = true;
            MainRegistry.LOGGER
               .warn(
                  "InstancedStaticPartRenderer overflow: maxInstances={} reached for tag={}, skipping extra instances until next flush",
                  this.maxInstances,
                  this.mdiTraceTag
               );
         }
      } else {
         if (this.instanceCount == 0) {
            this.overflowLogged = false;
            ClientLevel level = Minecraft.getInstance().level;
            this.batchSkyDarken = level != null ? level.getSkyDarken(1.0F) : -1.0F;
         }

         Matrix4f mat = baseBlockPose.last().pose();
         this.tmpInstanceMat.set(mat).mul(partLocalToBlock);
         this.tmpInstanceMat.getTranslation(this.posTmp);
         this.tmpInstanceMat.getNormalizedRotation(this.rotTmp);
         this.fillInstanceCornerLight(blockEntity, packedLight, blockPos, mat, sharedCornerUV8);
         int slot = this.instanceCount;
         int baseFloat = slot * this.instanceDataSize;
         this.memPutInstanceRecordAtBaseFloat(baseFloat);
         this.instanceCount++;
         ((Buffer)this.instanceBuffer).position(this.instanceDataSize * this.instanceCount);
      }
   }

   public boolean usesGpuPartBonePath() {
      return this.storesPerInstancePartBone && this.isInitialized();
   }

   private void fillInstanceCornerLight(
      @Nullable BlockEntity blockEntity, int packedLight, BlockPos blockPos, Matrix4f worldPose, @Nullable float[] sharedCornerUV8
   ) {
      boolean hasSharedCorners = sharedCornerUV8 != null && sharedCornerUV8.length >= 16;
      boolean needsIrisInstanceLight = ShaderCompatibilityDetector.canUseIrisExtendedShader() || ShaderCompatibilityDetector.isExternalShaderActive();
      if (needsIrisInstanceLight) {
         int sampleBase = this.instanceCount * 2;
         LightSampleCache.getOrSample(blockEntity, packedLight, this.instanceLightUV, sampleBase);
      }

      if (hasSharedCorners) {
         System.arraycopy(sharedCornerUV8, 0, this.tmpCornerUV, 0, 16);
      } else {
         if (LightSampleCache.BASE_POSE_SET.get()) {
            this.tmpLocalPose.set((Matrix4fc)LightSampleCache.BASE_POSE.get()).invert().mul(worldPose);
         } else {
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            this.tmpInvViewRot.identity().set(RenderSystem.getInverseViewRotationMatrix());
            this.tmpLocalPose.set(this.tmpInvViewRot).mul(worldPose);
            this.tmpLocalPose.m30(this.tmpLocalPose.m30() - (float)(blockPos.getX() - cam.x));
            this.tmpLocalPose.m31(this.tmpLocalPose.m31() - (float)(blockPos.getY() - cam.y));
            this.tmpLocalPose.m32(this.tmpLocalPose.m32() - (float)(blockPos.getZ() - cam.z));
         }

         long partHash = System.identityHashCode(this);
         if (this.useSlicedLight) {
            LightSampleCache.getOrSample16(blockEntity, partHash, this.objBbox, blockPos, this.tmpLocalPose, packedLight, this.tmpCornerUV);
         } else {
            LightSampleCache.getOrSample8(blockEntity, partHash, this.objBbox, blockPos, this.tmpLocalPose, packedLight, this.tmpCornerUV);
         }
      }
   }

   @Override
   public void flush() {
      this.flush(RenderSystem.getProjectionMatrix());
   }

   public void flush(RenderLevelStageEvent event) {
      this.flush(event.getProjectionMatrix());
   }

   static void bindBlockLitTexturesBeforeDraw(ShaderInstance shader) {
      SingleMeshVboRenderer.bindBlockLitSamplerTextures(shader);
   }

   @Override
   public void flush(Matrix4f projectionMatrix) {
      if (this.instanceCount != 0) {
         if (this.initialized && this.vaoId > 0 && this.eboId > 0 && this.instanceVboId > 0 && this.instanceBuffer != null) {
            boolean useIrisFlush = ShaderCompatibilityDetector.canUseIrisExtendedShader();
            if (useIrisFlush) {
               this.irisHelper.flushBatchIris(projectionMatrix);
            } else if (!ShaderCompatibilityDetector.isExternalShaderActive()) {
               this.vanillaHelper.flushBatchVanilla(projectionMatrix);
            }

            this.instanceCount = 0;
            this.instanceBuffer.clear();
            this.overflowLogged = false;
         } else {
            this.instanceCount = 0;
            if (this.instanceBuffer != null) {
               this.instanceBuffer.clear();
            }
         }
      }
   }

   @Override
   public void flushBatchIris(Matrix4f projectionMatrix) {
      this.irisHelper.flushBatchIris(projectionMatrix);
   }

   @Override
   public boolean drawSingleWithIrisExtended(PoseStack poseStack, int packedLight, BlockPos blockPos, @Nullable BlockEntity blockEntity) {
      return this.irisHelper.drawSingleWithIrisExtended(poseStack, packedLight, blockPos, blockEntity);
   }

   @Override
   public boolean isInitialized() {
      return this.initialized && this.vaoId > 0 && this.vboId > 0 && this.eboId > 0;
   }

   @Override
   public int getInstanceCount() {
      return this.instanceCount;
   }

   public void setMdiTraceTag(@Nullable String tag) {
      this.mdiTraceTag = tag;
   }

   @Nullable
   public String getMdiTraceTag() {
      return this.mdiTraceTag;
   }

   @Override
   public void cleanup() {
      super.cleanup();
      int instanceVboToDelete = this.instanceVboId;
      Cleanable bufferCleanable = this.instanceBufferCleanable;
      ByteBuffer atlasVbToFree = this.atlasVertexBytesRetained;
      IntBuffer atlasIbToFree = this.atlasIndicesRetained;
      this.atlasVertexBytesRetained = null;
      this.atlasIndicesRetained = null;
      this.atlasIndexCountRetained = 0;
      this.instanceVboId = -1;
      this.instanceBuffer = null;
      this.instanceBufferCleanable = null;
      this.vanillaHelper.invalidateShaderCache();
      this.irisHelper.cleanup();
      InstancedStaticPartRenderer mdiEvictTarget = this;
      RenderSystem.recordRenderCall(() -> {
         try {
            MdiGeometryAtlas.evictRendererIfRegistered(mdiEvictTarget);
            if (instanceVboToDelete != -1) {
               GL15.glDeleteBuffers(instanceVboToDelete);
            }

            if (bufferCleanable != null) {
               bufferCleanable.clean();
            }

            if (atlasVbToFree != null) {
               MemoryUtil.memFree(atlasVbToFree);
            }

            if (atlasIbToFree != null) {
               MemoryUtil.memFree(atlasIbToFree);
            }
         } catch (Exception e) {
            MainRegistry.LOGGER.error("InstancedStaticPartRenderer.cleanup failed", e);
         }
      });
   }
}
