package fr.heraliz.crac.client.render;

import fr.heraliz.crac.entity.ExplosiveChargeEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class ExplosiveChargeModel extends GeoModel<ExplosiveChargeEntity> {
    public ResourceLocation getModelResource(ExplosiveChargeEntity animatable) {
        return new ResourceLocation("crac", "geo/explosive_charge.geo.json");
    }

    public ResourceLocation getTextureResource(ExplosiveChargeEntity animatable) {
        return new ResourceLocation("crac", "textures/entity/explosive_charge.png");
    }

    public ResourceLocation getAnimationResource(ExplosiveChargeEntity animatable) {
        return new ResourceLocation("crac", "animations/explosive_charge.animation.json");
    }
}
