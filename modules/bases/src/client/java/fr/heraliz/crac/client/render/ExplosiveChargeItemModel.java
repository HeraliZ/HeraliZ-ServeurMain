package fr.heraliz.crac.client.render;

import fr.heraliz.crac.item.ExplosiveChargeItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class ExplosiveChargeItemModel extends GeoModel<ExplosiveChargeItem> {
    public ResourceLocation getModelResource(ExplosiveChargeItem animatable) {
        return new ResourceLocation("crac", "geo/explosive_charge.geo.json");
    }

    public ResourceLocation getTextureResource(ExplosiveChargeItem animatable) {
        return new ResourceLocation("crac", "textures/entity/explosive_charge.png");
    }

    public ResourceLocation getAnimationResource(ExplosiveChargeItem animatable) {
        return new ResourceLocation("crac", "animations/explosive_charge.animation.json");
    }
}
