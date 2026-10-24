package fr.heralisz.sylvorn.client;

import fr.heralisz.sylvorn.entity.Sylvorn;
import fr.heraliz.boss.client.HudBoss;
import fr.heraliz.boss.client.ParTick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.List;

/**
 * Barre de vie de Sylvorn (barre commune des boss, {@link HudBoss.Barre}) et habillage des cinématiques (bandes noires,
 * carte de titre {@link HudBoss#carteTitre}).
 */
public final class Hud {
   private static final HudBoss.Barre BARRE = new HudBoss.Barre();

   private Hud() {}

   /** Boss le plus proche, cherché au plus une fois par tick (barre, musique, ambiance, effets). */
   private static final ParTick<Sylvorn> BOSS = new ParTick<>(Hud::chercher);

   static Sylvorn findBoss(Minecraft mc) {
      return BOSS.get(mc);
   }

   private static Sylvorn chercher(Minecraft mc) {
      if (mc.level == null || mc.player == null) return null;
      List<Sylvorn> list = mc.level.getEntitiesOfClass(Sylvorn.class, mc.player.getBoundingBox().inflate(90), e -> !e.dormant() && !(e.isDeadOrDying() && e.deathTime > Sylvorn.DEATH_TICKS));
      Sylvorn best = null;
      double bd = Double.MAX_VALUE;
      for (Sylvorn s : list) {
         double d = s.distanceToSqr(mc.player);
         if (d < bd) {
            bd = d;
            best = s;
         }
      }
      return best;
   }

   public static void renderBar(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
      Minecraft mc = Minecraft.getInstance();
      Sylvorn boss = findBoss(mc);
      boolean visible = boss != null && !Cinematic.active() && !(boss.isDeadOrDying() && boss.deathTime > 60);
      if (BARRE.auRepos(boss, visible)) return;
      int phase = boss != null ? boss.phase() : 1;
      BARRE.dessiner(g, partial, w, boss, visible, phase, Component.translatable("hud.heralisz_sylvorn.name").getString(),
         Component.translatable("hud.heralisz_sylvorn.epithet").getString(), boss != null ? boss.accentColor() : 0xD8A850,
         boss != null ? boss.hudTop(phase) : 0xE0B45A, boss != null ? boss.hudBottom(phase) : 0x6A1A12);
   }

   public static void renderCinematic(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
      if (!Cinematic.active()) return;
      float age = Cinematic.ageTicks(partial), dur = Cinematic.duration();
      float k = Cinematic.progress(partial);
      float bars = Mth.clamp(Math.min(age / 10F, (dur - age) / 10F), 0, 1);
      bars = bars * bars * (3 - 2 * bars);
      int bh = (int) (h * 0.12F * bars);
      g.fill(0, 0, w, bh, 0xFF000000);
      g.fill(0, h - bh, w, h, 0xFF000000);
      int vg = (int) (70 * bars);
      g.fillGradient(0, bh, w, bh + 40, (vg << 24) | 0x1A0C06, 0x000E2236);
      g.fillGradient(0, h - bh - 40, w, h - bh, 0x000E2236, (vg << 24) | 0x1A0C06);
      String[] keys = Cinematic.titles();
      if (keys == null) return;
      String top = Component.translatable(keys[0]).getString(), title = Component.translatable(keys[1]).getString(),
         sub = Component.translatable(keys[2]).getString();
      float at = Cinematic.titleAt();
      int accent = Cinematic.accent() & 0xFFFFFF;
      float flash = Mth.clamp(1 - Math.abs(k - at) * dur / 2.5F, 0, 1);
      if (flash > 0) g.fill(0, 0, w, h, argb(0xFFF0D8, 0.25F * flash));
      if (k < at) return;
      float tt = (k - at) * dur;
      float out = Mth.clamp((dur - age) / 8F, 0, 1);
      HudBoss.carteTitre(g, w, h, tt, out, top, title, sub, accent, 0xE8D2A8);
   }

   private static int argb(int rgb, float a) {
      return (Mth.clamp((int) (a * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
   }
}
