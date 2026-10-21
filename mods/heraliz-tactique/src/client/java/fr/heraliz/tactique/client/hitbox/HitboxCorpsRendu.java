package fr.heraliz.tactique.client.hitbox;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.tactique.hitbox.HitboxCorps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * F3+B : en plus de la boîte blanche de Minecraft (murs, déplacements), la hitbox « en morceaux » des joueurs, celle que
 * le serveur utilise pour les balles de TaCZ (HitboxCorps) : tête en rouge, reste du corps en jaune. Soi-même compris
 * en 3e personne.
 */
public final class HitboxCorpsRendu {
   private HitboxCorpsRendu() {
   }

   public static void brancher() {
      MinecraftForge.EVENT_BUS.addListener(HitboxCorpsRendu::dessiner);
   }

   private static void dessiner(RenderLevelStageEvent e) {
      Minecraft mc = Minecraft.getInstance();
      if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || mc.level == null
         || !mc.getEntityRenderDispatcher().shouldRenderHitBoxes()) {
         return;
      }
      Vec3 cam = e.getCamera().getPosition();
      float partiel = e.getPartialTick();
      MultiBufferSource.BufferSource tampons = mc.renderBuffers().bufferSource();
      VertexConsumer lignes = tampons.getBuffer(RenderType.lines());
      for (Player p : mc.level.players()) {
         if (p.isSleeping() || p.isSpectator() || (p == mc.player && mc.options.getCameraType().isFirstPerson())) continue;
         Vec3 pieds = p.getPosition(partiel).subtract(cam);
         for (HitboxCorps.Morceau mo : HitboxCorps.morceaux(p, pieds, partiel)) {
            PoseStack pile = new PoseStack();
            pile.mulPoseMatrix(e.getPoseStack().last().pose());
            pile.mulPoseMatrix(mo.versMonde());
            float[] c = mo.cube();
            float r = 1.0F, v = mo.tete() ? 0.2F : 0.85F, b = 0.1F;
            LevelRenderer.renderLineBox(pile, lignes, c[0], c[1], c[2], c[3], c[4], c[5], r, v, b, 1.0F);
         }
      }
      tampons.endBatch(RenderType.lines());
   }
}
