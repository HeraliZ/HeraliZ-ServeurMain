package com.hbm_m.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public interface VanillaInstancedMeshRenderer {
   void renderSingle(PoseStack var1, int var2, BlockPos var3, @Nullable BlockEntity var4, @Nullable MultiBufferSource var5);

   void addInstance(PoseStack var1, int var2, BlockPos var3, @Nullable BlockEntity var4, @Nullable MultiBufferSource var5);

   void flush(Matrix4f var1);

   void flush();

   int getInstanceCount();

   boolean isInitialized();

   void cleanup();
}
