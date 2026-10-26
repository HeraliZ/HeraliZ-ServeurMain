package com.hbm_m.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractMultipartBakedModel implements BakedModel {
   protected final Map<String, BakedModel> parts;
   protected final ItemTransforms transforms;
   private TextureAtlasSprite cachedParticleIcon;
   private List<BakedQuad>[] sideQuadsCache;

   protected AbstractMultipartBakedModel(Map<String, BakedModel> parts, ItemTransforms transforms) {
      this.parts = parts;
      this.transforms = transforms;
   }

   public BakedModel getPart(String name) {
      return this.parts.get(name);
   }

   public Map<String, BakedModel> getPartModels() {
      return Collections.unmodifiableMap(this.parts);
   }

   protected String[] getPartNamesInternal() {
      return this instanceof AbstractMultipartBakedModel.PartNamesProvider provider
         ? provider.getPartNames()
         : this.parts.keySet().stream().sorted().toArray(String[]::new);
   }

   @Deprecated
   public TextureAtlasSprite getParticleIcon() {
      if (this.cachedParticleIcon == null) {
         if (!this.parts.isEmpty()) {
            this.cachedParticleIcon = this.parts.values().iterator().next().getParticleIcon();
         } else {
            this.cachedParticleIcon = Minecraft.getInstance().getModelManager().getMissingModel().getParticleIcon();
         }
      }

      return this.cachedParticleIcon;
   }

   public TextureAtlasSprite getParticleIcon(ModelData data) {
      if (this.cachedParticleIcon == null) {
         String[] partNames = this.getPartNamesInternal();
         String[] iconParts = new String[]{"Base", "base", "frame", "Frame", "main", "Main"};

         for (String partName : iconParts) {
            BakedModel part = this.parts.get(partName);
            if (part != null) {
               this.cachedParticleIcon = part.getParticleIcon(data);
               break;
            }
         }

         if (this.cachedParticleIcon == null && partNames.length > 0) {
            BakedModel part = this.parts.get(partNames[0]);
            if (part != null) {
               this.cachedParticleIcon = part.getParticleIcon(data);
            }
         }

         if (this.cachedParticleIcon == null && !this.parts.isEmpty()) {
            this.cachedParticleIcon = this.parts.values().iterator().next().getParticleIcon(data);
         }

         if (this.cachedParticleIcon == null) {
            this.cachedParticleIcon = Minecraft.getInstance().getModelManager().getMissingModel().getParticleIcon(data);
         }
      }

      return this.cachedParticleIcon;
   }

   protected List<BakedQuad> getQuadsForModelData(
      @Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData modelData, @Nullable RenderType renderType
   ) {
      if (renderType == null) {
         int idx = side == null ? 0 : side.ordinal() + 1;
         List<BakedQuad>[] cache = this.sideQuadsCache;
         if (cache == null) {
            cache = new List[7];
            this.sideQuadsCache = cache;
         }

         List<BakedQuad> cached = cache[idx];
         if (cached != null) {
            return cached;
         }

         List<BakedQuad> quads = new ArrayList<>();

         for (BakedModel part : this.parts.values()) {
            quads.addAll(part.getQuads(state, side, rand, modelData, renderType));
         }

         List<BakedQuad> result = quads.isEmpty() ? List.of() : List.copyOf(quads);
         cache[idx] = result;
         return result;
      } else {
         List<BakedQuad> quads = new ArrayList<>();

         for (BakedModel part : this.parts.values()) {
            quads.addAll(part.getQuads(state, side, rand, modelData, renderType));
         }

         return quads;
      }
   }

   public List<BakedQuad> getQuads(
      @Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData modelData, @Nullable RenderType renderType
   ) {
      return this.getQuadsForModelData(state, side, rand, modelData, renderType);
   }

   @Deprecated
   public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
      return this.getQuads(state, side, rand, ModelData.EMPTY, null);
   }

   protected abstract boolean shouldSkipWorldRendering(@Nullable BlockState var1);

   protected boolean shouldSkipSideRendering(@Nullable BlockState state, @Nullable Direction side) {
      return state == null && side != null;
   }

   protected BlockState getStateForPart(@Nullable BlockState state) {
      return state;
   }

   protected List<String> getItemRenderPartNames() {
      String[] allPartNames = this.getPartNamesInternal();
      String[] priorityParts = new String[]{"frame", "Frame", "doorLeft", "doorRight", "Base", "base", "main", "Main"};
      List<String> result = new ArrayList<>();

      for (String priorityPart : priorityParts) {
         if (this.parts.containsKey(priorityPart)) {
            result.add(priorityPart);
         }
      }

      for (String partName : allPartNames) {
         if (!result.contains(partName) && this.parts.containsKey(partName)) {
            result.add(partName);
         }
      }

      return result;
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

   public ItemOverrides getOverrides() {
      return ItemOverrides.EMPTY;
   }

   public ItemTransforms getTransforms() {
      return this.transforms;
   }

   public BakedModel applyTransform(ItemDisplayContext transformType, PoseStack poseStack, boolean applyLeftHandTransform) {
      if (this.isCustomRenderer()) {
         return this;
      }

      this.getTransforms().getTransform(transformType).apply(applyLeftHandTransform, poseStack);
      return this;
   }

   public void clearCaches() {
      this.cachedParticleIcon = null;
   }

   public interface PartNamesProvider {
      String[] getPartNames();
   }
}
