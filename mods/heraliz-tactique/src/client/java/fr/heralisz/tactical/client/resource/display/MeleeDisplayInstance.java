package fr.heralisz.tactical.client.resource.display;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.gson.annotations.SerializedName;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.Animations;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.client.animation.statemachine.LuaStateMachineFactory;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.animation.bedrock.BedrockAnimationFile;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import java.util.Map;
import java.util.Objects;
import fr.heralisz.tactical.api.animation.MeleeAnimationStateContext;
import fr.heralisz.tactical.client.audio.ICustomSoundSupplier;
import fr.heralisz.tactical.client.renderer.model.CustomBedrockModel;
import fr.heralisz.tactical.compat.player_animator.ThirdPersonAnimationConfig;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import org.luaj.vm2.LuaTable;

public class MeleeDisplayInstance implements ICustomSoundSupplier {
   private ResourceLocation id;
   private CustomBedrockModel model;
   private LuaAnimationStateMachine<MeleeAnimationStateContext> stateMachine;
   private ResourceLocation texture;
   private ResourceLocation slotTexture;
   private ItemTransforms transforms;
   private Vector3f displayOffset;
   private Map<String, ResourceLocation> sounds;
   private ThirdPersonAnimationConfig thirdPersonAnimation;

   private MeleeDisplayInstance() {
   }

   public ResourceLocation getId() {
      return this.id;
   }

   public CustomBedrockModel getModel() {
      return this.model;
   }

   public LuaAnimationStateMachine<MeleeAnimationStateContext> getStateMachine() {
      return this.stateMachine;
   }

   public ResourceLocation getTexture() {
      return this.texture;
   }

   public ResourceLocation getSlotTexture() {
      return this.slotTexture;
   }

   public ItemTransforms getTransforms() {
      return this.transforms;
   }

   public Vector3f getDisplayOffset() {
      return this.displayOffset;
   }

   public ThirdPersonAnimationConfig getThirdPersonAnimation() {
      return this.thirdPersonAnimation;
   }

   @Override
   public Map<String, ResourceLocation> getSounds() {
      return this.sounds;
   }

   @NotNull
   public static MeleeDisplayInstance create(MeleeDisplayInstance.MeleeDisplay pojo, ResourceLocation id) throws IllegalArgumentException {
      MeleeDisplayInstance display = new MeleeDisplayInstance();
      display.id = id;
      Preconditions.checkArgument(pojo.modelLocation != null, "display object missing model field");
      Preconditions.checkArgument(pojo.stateMachineLocation != null, "display object missing stateMachine field");
      Preconditions.checkArgument(pojo.textureLocation != null, "display object missing texture field");
      Preconditions.checkArgument(pojo.animationLocation != null, "display object missing animation field");
      BedrockModelPOJO modelPOJO = ClientAssetsManager.INSTANCE.getBedrockModelPOJO(pojo.modelLocation);
      Preconditions.checkArgument(modelPOJO != null, "no corresponding model found for " + pojo.modelLocation);
      if (BedrockVersion.isLegacyVersion(modelPOJO)) {
         display.model = new CustomBedrockModel(modelPOJO, BedrockVersion.LEGACY);
      }

      display.model = new CustomBedrockModel(modelPOJO, BedrockVersion.NEW);
      BedrockAnimationFile animation = ClientAssetsManager.INSTANCE.getBedrockAnimations(pojo.animationLocation);
      Preconditions.checkArgument(animation != null, "no corresponding animation found for " + pojo.modelLocation);
      AnimationController controller = Animations.createControllerFromBedrock(animation, display.model);
      LuaTable script = ClientAssetsManager.INSTANCE.getScript(pojo.stateMachineLocation);
      Preconditions.checkArgument(script != null, "no corresponding state machine found for " + pojo.modelLocation);
      display.stateMachine = new LuaStateMachineFactory().setController(controller).setLuaScripts(script).build();
      display.texture = new ResourceLocation(pojo.textureLocation.getNamespace(), "textures/" + pojo.textureLocation.getPath() + ".png");
      if (pojo.slotTextureLocation != null) {
         display.slotTexture = new ResourceLocation(pojo.slotTextureLocation.getNamespace(), "textures/" + pojo.slotTextureLocation.getPath() + ".png");
      }

      display.transforms = Objects.requireNonNullElse(pojo.transforms, ItemTransforms.NO_TRANSFORMS);
      display.displayOffset = Objects.requireNonNullElse(pojo.displayOffset, new Vector3f());
      display.sounds = Objects.requireNonNullElseGet(pojo.sounds, Maps::newHashMap);
      display.thirdPersonAnimation = pojo.thirdPersonAnimation;
      return display;
   }

   public record MeleeDisplay(
      @SerializedName("model") ResourceLocation modelLocation,
      @SerializedName("animation") ResourceLocation animationLocation,
      @SerializedName("state_machine") ResourceLocation stateMachineLocation,
      @SerializedName("texture") ResourceLocation textureLocation,
      @SerializedName("slot_texture") ResourceLocation slotTextureLocation,
      @SerializedName("transforms") ItemTransforms transforms,
      @SerializedName("display_offset") Vector3f displayOffset,
      @SerializedName("sounds") Map<String, ResourceLocation> sounds,
      @SerializedName("third_person_animation") ThirdPersonAnimationConfig thirdPersonAnimation
   ) {
   }
}
