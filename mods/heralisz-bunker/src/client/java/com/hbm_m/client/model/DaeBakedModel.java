package com.hbm_m.client.model;

import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

public class DaeBakedModel implements BakedModel {
   private final List<BakedQuad> quads;
   private final TextureAtlasSprite particleIcon;
   private final ItemTransforms transforms;

   public DaeBakedModel(List<BakedQuad> quads, TextureAtlasSprite particleIcon, ItemTransforms transforms) {
      this.quads = List.copyOf(quads);
      this.particleIcon = particleIcon;
      this.transforms = transforms;
   }

   public boolean useAmbientOcclusion() {
      return true;
   }

   public boolean isGui3d() {
      return true;
   }

   public boolean usesBlockLight() {
      return true;
   }

   public boolean isCustomRenderer() {
      return false;
   }

   public TextureAtlasSprite getParticleIcon() {
      return this.particleIcon;
   }

   public ItemOverrides getOverrides() {
      return ItemOverrides.EMPTY;
   }

   public ItemTransforms getTransforms() {
      return this.transforms;
   }

   public List<BakedQuad> getQuads(
      @Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData modelData, @Nullable RenderType renderType
   ) {
      return side == null ? this.quads : List.of();
   }

   @Deprecated
   public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
      return side == null ? this.quads : List.of();
   }
}
