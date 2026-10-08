package fr.heralisz.tactical.client.renderer.model;

import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.FunctionalBedrockPart;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.bedrock.ModelRendererWrapper;
import com.tacz.guns.client.model.functional.LeftHandRender;
import com.tacz.guns.client.model.functional.RightHandRender;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

public class CustomBedrockModel extends BedrockAnimatedModel {
   private static final Pattern FIRSTPERSON_EFFECT_PATTERN = Pattern.compile("^1p_effect(_(\\d+))?$");
   private static final Pattern ENTITY_HIDE_PATTERN = Pattern.compile("^entity_hide(_(\\d+))?$");
   private boolean effectVisible = false;
   private boolean entityRendering = false;

   public CustomBedrockModel(BedrockModelPOJO pojo, BedrockVersion version) {
      super(pojo, version);
      this.setFunctionalRenderer("lefthand_pos", bedrockPart -> new LeftHandRender(this));
      this.setFunctionalRenderer("righthand_pos", bedrockPart -> new RightHandRender(this));

      for (Entry<String, ModelRendererWrapper> entry : this.modelMap.entrySet()) {
         if (FIRSTPERSON_EFFECT_PATTERN.matcher(entry.getKey()).find()) {
            if (entry.getValue().getModelRenderer() instanceof FunctionalBedrockPart functionalPart) {
               functionalPart.functionalRenderer = this::renderEffect;
            }
         } else if (ENTITY_HIDE_PATTERN.matcher(entry.getKey()).find() && entry.getValue().getModelRenderer() instanceof FunctionalBedrockPart functionalPart) {
            functionalPart.functionalRenderer = this::renderEntityHide;
         }
      }
   }

   @Nullable
   private IFunctionalRenderer renderEffect(BedrockPart part) {
      part.visible = this.effectVisible;
      return null;
   }

   @Nullable
   private IFunctionalRenderer renderEntityHide(BedrockPart part) {
      part.visible = !this.entityRendering;
      return null;
   }

   public void setEffectVisible(boolean visible) {
      this.effectVisible = visible;
   }

   public boolean isEffectVisible() {
      return this.effectVisible;
   }

   public void setEntityRendering(boolean entityRendering) {
      this.entityRendering = entityRendering;
   }

   public boolean isEntityRendering() {
      return this.entityRendering;
   }
}
