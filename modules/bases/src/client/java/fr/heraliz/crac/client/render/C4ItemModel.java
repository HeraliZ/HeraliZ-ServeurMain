package fr.heraliz.crac.client.render;

import fr.heraliz.crac.item.C4Item;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class C4ItemModel extends GeoModel<C4Item> {
    public ResourceLocation getModelResource(C4Item animatable) {
        return new ResourceLocation("crac", "geo/c4.geo.json");
    }

    public ResourceLocation getTextureResource(C4Item animatable) {
        return new ResourceLocation("crac", "textures/entity/c4.png");
    }

    public ResourceLocation getAnimationResource(C4Item animatable) {
        return new ResourceLocation("crac", "animations/c4.animation.json");
    }
}
