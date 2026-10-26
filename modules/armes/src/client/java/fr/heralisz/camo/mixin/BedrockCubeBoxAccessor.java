package fr.heralisz.camo.mixin;

import com.tacz.guns.client.model.bedrock.BedrockCubeBox;
import com.tacz.guns.client.model.bedrock.BedrockPolygon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Stickers : faces d'un cube TaCZ (sommets en pixels du repère de la pièce, UV de la texture de l'arme). */
@Mixin(value = BedrockCubeBox.class, remap = false)
public interface BedrockCubeBoxAccessor {
   @Accessor("polygons")
   BedrockPolygon[] heralisz$polygons();
}
