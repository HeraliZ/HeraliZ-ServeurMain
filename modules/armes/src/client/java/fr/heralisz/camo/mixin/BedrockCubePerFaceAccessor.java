package fr.heralisz.camo.mixin;

import com.tacz.guns.client.model.bedrock.BedrockCubePerFace;
import com.tacz.guns.client.model.bedrock.BedrockPolygon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Stickers : faces d'un cube TaCZ à UV par face (sommets en pixels du repère de la pièce, UV de la texture de l'arme). */
@Mixin(value = BedrockCubePerFace.class, remap = false)
public interface BedrockCubePerFaceAccessor {
   @Accessor("polygons")
   BedrockPolygon[] heralisz$polygons();
}
