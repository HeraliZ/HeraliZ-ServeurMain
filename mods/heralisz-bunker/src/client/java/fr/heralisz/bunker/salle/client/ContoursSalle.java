package fr.heralisz.bunker.salle.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.bunker.keycard.KeycardRegistry;
import fr.heralisz.bunker.salle.ContoursPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.List;

/**
 * Contours des salles à carte chez le staff : la sélection de la baguette (jaune, coin 1 orange, coin 2 cyan) et les
 * salles proches (couleur de leur carte), tant qu'il tient la baguette ; une salle demandée par /salle voir, 30 s.
 * Tout vient du serveur (ContoursPacket) : le jeu du joueur ne connaît aucune salle sans la baguette ni la commande.
 */
public final class ContoursSalle {
   private static final int DUREE_VOIR = 600;
   private static List<ContoursPacket.Boite> baguette = List.of(), voir = List.of();
   private static long voirFin;
   private static ClientLevel niveau;

   private ContoursSalle() {}

   public static void recevoir(ContoursPacket p) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) return;
      if (mc.level != niveau) {
         niveau = mc.level;
         baguette = List.of();
         voir = List.of();
      }
      if (p.voir()) {
         voir = List.copyOf(p.boites());
         voirFin = mc.level.getGameTime() + DUREE_VOIR;
      } else {
         baguette = List.copyOf(p.boites());
      }
   }

   public static void rendu(RenderLevelStageEvent e) {
      if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null || mc.level != niveau) return;
      boolean tient = mc.player.isHolding(KeycardRegistry.BAGUETTE_SALLE.get());
      boolean montre = mc.level.getGameTime() < voirFin;
      if ((!tient || baguette.isEmpty()) && (!montre || voir.isEmpty())) return;
      PoseStack ps = e.getPoseStack();
      Vec3 cam = e.getCamera().getPosition();
      MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
      VertexConsumer vc = buf.getBuffer(RenderType.lines());
      ps.pushPose();
      ps.translate(-cam.x, -cam.y, -cam.z);
      if (tient) for (ContoursPacket.Boite b : baguette) dessiner(ps, vc, b);
      if (montre) for (ContoursPacket.Boite b : voir) dessiner(ps, vc, b);
      ps.popPose();
      buf.endBatch(RenderType.lines());
   }

   private static void dessiner(PoseStack ps, VertexConsumer vc, ContoursPacket.Boite b) {
      boolean coin = b.genre() == ContoursPacket.COIN1 || b.genre() == ContoursPacket.COIN2;
      double m = coin ? 0.01 : 0.004;                                // un peu hors des faces des blocs (pas de scintillement)
      AABB a = new AABB(b.min().getX() - m, b.min().getY() - m, b.min().getZ() - m,
         b.max().getX() + 1 + m, b.max().getY() + 1 + m, b.max().getZ() + 1 + m);
      float r, g, bl;
      switch (b.genre()) {
         case ContoursPacket.SELECTION -> { r = 1.0F; g = 0.85F; bl = 0.2F; }
         case ContoursPacket.COIN1 -> { r = 1.0F; g = 0.45F; bl = 0.1F; }
         case ContoursPacket.COIN2 -> { r = 0.2F; g = 0.9F; bl = 1.0F; }
         case ContoursPacket.SALLE_VERTE -> { r = 0.25F; g = 1.0F; bl = 0.35F; }
         case ContoursPacket.SALLE_BLEUE -> { r = 0.3F; g = 0.55F; bl = 1.0F; }
         default -> { r = 1.0F; g = 0.25F; bl = 0.25F; }
      }
      LevelRenderer.renderLineBox(ps, vc, a, r, g, bl, 1.0F);
   }
}
