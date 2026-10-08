package fr.heraliz.crac.client.render;

import fr.heraliz.crac.entity.C4Entity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class C4Model extends GeoModel<C4Entity> {
    public ResourceLocation getModelResource(C4Entity animatable) {
        return new ResourceLocation("crac", "geo/c4.geo.json");
    }

    public ResourceLocation getTextureResource(C4Entity animatable) {
        return new ResourceLocation("crac", "textures/entity/c4.png");
    }

    public ResourceLocation getAnimationResource(C4Entity animatable) {
        return new ResourceLocation("crac", "animations/c4.animation.json");
    }
}
