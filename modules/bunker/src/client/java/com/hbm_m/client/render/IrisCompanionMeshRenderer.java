package com.hbm_m.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public interface IrisCompanionMeshRenderer {
   boolean drawSingleWithIrisExtended(PoseStack var1, int var2, BlockPos var3, @Nullable BlockEntity var4);

   void flushBatchIris(Matrix4f var1);
}
