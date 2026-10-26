package fr.heralisz.camo.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.bedrock.BedrockCubePerFace;
import com.tacz.guns.client.model.bedrock.BedrockPolygon;
import com.tacz.guns.client.model.bedrock.BedrockVertex;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Rendu des cubes TaCZ à UV par face, sans rien créer par sommet (TaCZ crée un vecteur par sommet et par face, des
 * dizaines de milliers par image avec plusieurs armes à l'écran). Les faces vides du modèle (4 sommets au même point,
 * environ une face sur cinq dans nos gunpacks) ne sont plus envoyées au tampon de dessin : elles ne se voient pas. Elles
 * restent envoyées aux relevés de sommets (cartes des camos, icônes, effets), qui n'écrivent pas dans un vrai tampon.
 */
@Mixin(value = BedrockCubePerFace.class, remap = false)
public abstract class BedrockCubePerFaceRenduMixin {
   @Shadow @Final private static BedrockVertex[] EMPTY_VERTEX;
   @Shadow @Final private BedrockPolygon[] polygons;

   /**
    * @author HeraliZ
    * @reason même dessin que TaCZ, sans allocation et sans les faces vides
    */
   @Overwrite
   public void compile(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, float red, float green, float blue, float alpha) {
      Matrix4f m = pose.pose();
      Matrix3f n = pose.normal();
      boolean tampon = consumer instanceof BufferBuilder;
      for (BedrockPolygon polygon : this.polygons) {
         if (tampon && polygon.vertices == EMPTY_VERTEX) {
            continue;
         }
         float px = polygon.normal.x(), py = polygon.normal.y(), pz = polygon.normal.z();
         float nx = n.m00() * px + n.m10() * py + n.m20() * pz;
         float ny = n.m01() * px + n.m11() * py + n.m21() * pz;
         float nz = n.m02() * px + n.m12() * py + n.m22() * pz;
         for (BedrockVertex vertex : polygon.vertices) {
            float x = vertex.pos.x() / 16.0F;
            float y = vertex.pos.y() / 16.0F;
            float z = vertex.pos.z() / 16.0F;
            consumer.vertex(m.m00() * x + m.m10() * y + m.m20() * z + m.m30(),
               m.m01() * x + m.m11() * y + m.m21() * z + m.m31(),
               m.m02() * x + m.m12() * y + m.m22() * z + m.m32(),
               red, green, blue, alpha, vertex.u, vertex.v, overlay, light, nx, ny, nz);
         }
      }
   }
}
