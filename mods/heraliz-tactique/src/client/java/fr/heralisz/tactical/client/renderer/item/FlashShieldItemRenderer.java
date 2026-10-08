package fr.heralisz.tactical.client.renderer.item;

import com.google.gson.annotations.SerializedName;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.Animations;
import com.tacz.guns.api.client.animation.statemachine.LuaStateMachineFactory;
import com.tacz.guns.api.client.event.BeforeRenderHandEvent;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.model.functional.LeftHandRender;
import com.tacz.guns.client.model.functional.RightHandRender;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.animation.bedrock.BedrockAnimationFile;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import fr.heralisz.tactical.api.animation.FlashShieldAnimationStateContext;
import fr.heralisz.tactical.client.resource.TacticalClientAssetsManager;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.luaj.vm2.LuaTable;

public class FlashShieldItemRenderer extends AnimateGeoItemRenderer<BedrockAnimatedModel, FlashShieldAnimationStateContext> {
   private static final SlotModel SLOT_MODEL = new SlotModel();
   private ItemTransforms transforms = ItemTransforms.NO_TRANSFORMS;
   private Vector3f displayOffset = new Vector3f();
   private ResourceLocation slotTexture = null;

   public FlashShieldAnimationStateContext initContext(ItemStack stack, Player player, float partialTick) {
      FlashShieldAnimationStateContext context = new FlashShieldAnimationStateContext();
      this.updateContext(context, stack, player, partialTick);
      return context;
   }

   public void updateContext(FlashShieldAnimationStateContext context, ItemStack stack, Player player, float partialTick) {
      context.setPartialTicks(partialTick);
      context.setUsing(player.isUsingItem());
      context.setUsingTick(player.getTicksUsingItem());
      context.setPartialTicks(partialTick);
      context.setCurrentItem(stack);
   }

   public long getPutAwayTime(ItemStack stack) {
      return 320L;
   }

   public void applyItemInHandCameraAnimation(BeforeRenderHandEvent event, ItemStack stack, float multiplier) {
      super.applyItemInHandCameraAnimation(event, stack, multiplier);
      this.model.cleanCameraAnimationTransform();
   }

   public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay) {
      if (!ctx.firstPerson()) {
         if (ctx != ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            if (ctx == ItemDisplayContext.GUI && this.slotTexture != null) {
               poseStack.translate(0.5, 1.5, 0.5);
               poseStack.mulPose(Axis.ZN.rotationDegrees(180.0F));
               VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucent(this.slotTexture));
               SLOT_MODEL.renderToBuffer(poseStack, buffer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
            } else {
               poseStack.pushPose();
               if (this.transforms != null) {
                  poseStack.translate(0.5F, 0.5F, 0.5F);
                  ItemTransform transform = this.transforms.getTransform(ctx);
                  transform.apply(false, poseStack);
                  poseStack.translate(-0.5F, -0.5F, -0.5F);
               }

               if (this.displayOffset != null) {
                  poseStack.translate(this.displayOffset.x(), this.displayOffset.y(), this.displayOffset.z());
               }

               poseStack.translate(0.5, 1.5, 0.5);
               poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
               if (this.model != null) {
                  this.model.render(poseStack, ctx, RenderType.entityCutout(this.getTextureLocation(stack)), light, overlay);
               } else {
                  VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucent(MissingTextureAtlasSprite.getLocation()));
                  SLOT_MODEL.renderToBuffer(poseStack, buffer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
               }

               poseStack.popPose();
            }
         }
      }
   }

   public void init(FlashShieldItemRenderer.ShieldDisplay display) {
      BedrockModelPOJO modelPOJO = ClientAssetsManager.INSTANCE.getBedrockModelPOJO(display.modelLocation);
      this.model = new BedrockAnimatedModel(modelPOJO, BedrockVersion.NEW);
      this.model.setFunctionalRenderer("lefthand_pos", bedrockPart -> new LeftHandRender(this.model));
      this.model.setFunctionalRenderer("righthand_pos", bedrockPart -> new RightHandRender(this.model));
      this.textureLocation = new ResourceLocation(display.textureLocation.getNamespace(), "textures/" + display.textureLocation.getPath() + ".png");
      if (display.slotTextureLocation != null) {
         this.slotTexture = new ResourceLocation(display.slotTextureLocation.getNamespace(), "textures/" + display.slotTextureLocation.getPath() + ".png");
      }

      this.transforms = display.transforms == null ? ItemTransforms.NO_TRANSFORMS : display.transforms;
      this.displayOffset = display.displayOffset == null ? new Vector3f() : display.displayOffset;
      BedrockAnimationFile animation = ClientAssetsManager.INSTANCE.getBedrockAnimations(display.animationLocation);
      AnimationController controller = null;
      if (animation != null) {
         controller = Animations.createControllerFromBedrock(animation, this.model);
      }

      LuaTable script = ClientAssetsManager.INSTANCE.getScript(display.stateMachineLocation);
      this.stateMachine = new LuaStateMachineFactory().setController(controller).setLuaScripts(script).build();
   }

   public void onResourceManagerReload(ResourceManager manager) {
      manager.getResource(new ResourceLocation("heralisz_tactical", "display/shield/flash_shield.json"))
         .map(
            resource -> {
               try (InputStream stream = resource.open()) {
                  return (FlashShieldItemRenderer.ShieldDisplay)TacticalClientAssetsManager.GSON
                     .fromJson(new InputStreamReader(stream), FlashShieldItemRenderer.ShieldDisplay.class);
               } catch (IOException e) {
                  throw new RuntimeException(e);
               }
            }
         )
         .ifPresent(this::init);
   }

   public record ShieldDisplay(
      @SerializedName("model") ResourceLocation modelLocation,
      @SerializedName("animation") ResourceLocation animationLocation,
      @SerializedName("state_machine") ResourceLocation stateMachineLocation,
      @SerializedName("texture") ResourceLocation textureLocation,
      @SerializedName("slot_texture") ResourceLocation slotTextureLocation,
      @SerializedName("transforms") ItemTransforms transforms,
      @SerializedName("display_offset") Vector3f displayOffset
   ) {
   }
}
