package fr.heralisz.n07.client;

import fr.heralisz.n07.api.BossInfo;
import fr.heraliz.boss.client.HudBoss;
import fr.heraliz.boss.client.ParTick;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.List;

/**
 * Barre de vie du boss (barre commune des boss, {@link HudBoss.Barre}) et habillage des cinématiques (bandes noires,
 * carte de titre {@link HudBoss#carteTitre}).
 */
public final class BossHud {
   private static final HudBoss.Barre BARRE = new HudBoss.Barre();
   private static String lastName = "";
   /** Couleur du bandeau au-dessus des titres (« BRÈCHE DE CONFINEMENT »). */
   static final int BANDEAU = 0xFFD23A;

   private BossHud() {}

   /** Boss le plus proche, cherché au plus une fois par tick (la barre est dessinée à chaque image). */
   private static final ParTick<LivingEntity> BOSS = new ParTick<>(BossHud::chercher);

   private static LivingEntity findBoss(Minecraft mc) {
      return BOSS.get(mc);
   }

   private static LivingEntity chercher(Minecraft mc) {
      if (mc.level == null || mc.player == null) return null;
      List<LivingEntity> list = mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(72), e -> e instanceof BossInfo bi && !bi.dormant());
      LivingEntity best = null;
      double bd = Double.MAX_VALUE;
      for (LivingEntity s : list) {
         double d = s.distanceToSqr(mc.player);
         if (d < bd) {
            bd = d;
            best = s;
         }
      }
      return best;
   }

   // ------------------------------------------------------------------ barre de vie
   public static void renderBar(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
      Minecraft mc = Minecraft.getInstance();
      LivingEntity boss = findBoss(mc);
      BossInfo info = boss instanceof BossInfo bi ? bi : null;
      boolean visible = boss != null && !Cinematic.active() && !ScenePlayer.active() && !(boss.isDeadOrDying() && boss.deathTime > 70);
      if (BARRE.auRepos(boss, visible)) return;
      int phase = info != null ? info.phase() : 1;
      if (info != null) lastName = tr(info.hudName());
      BARRE.dessiner(g, partial, w, boss, visible, phase, lastName, Component.translatable("hud.heralisz_n07.epithet").getString(),
         info != null ? info.accentColor() : 0x9CFF5A, info != null ? info.hudTop(phase) : 0xA8FF7A, info != null ? info.hudBottom(phase) : 0x2F9A2A);
   }

   // ------------------------------------------------------------------ cinématique : bandes noires + titres
   public static void renderCinematic(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
      if (!Cinematic.active()) return;
      float age = Cinematic.ageTicks(partial), dur = Cinematic.duration();
      float k = Cinematic.progress(partial);
      float bars = Mth.clamp(Math.min(age / 10F, (dur - age) / 10F), 0, 1);
      bars = bars * bars * (3 - 2 * bars);
      int bh = (int) (h * 0.12F * bars);
      g.fill(0, 0, w, bh, 0xFF000000);
      g.fill(0, h - bh, w, h, 0xFF000000);
      // voile vert sur les bords
      int vg = (int) (60 * bars);
      g.fillGradient(0, bh, w, bh + 40, (vg << 24) | 0x1A3A10, 0x001A3A10);
      g.fillGradient(0, h - bh - 40, w, h - bh, 0x001A3A10, (vg << 24) | 0x1A3A10);

      BossInfo bi = Cinematic.boss();
      String[] tt3 = bi != null ? bi.cinematicTitles(Cinematic.kind()) : new String[] {"", "", ""};
      String top = tr(tt3[0]), title = tr(tt3[1]), sub = tr(tt3[2]);
      float at = bi != null ? bi.cinematicTitleAt(Cinematic.kind()) : 0.5F;
      int accent = bi != null ? bi.accentColor() & 0xFFFFFF : 0x9CFF5A;
      // flash blanc au moment fort
      float flash = Mth.clamp(1 - Math.abs(k - at) * dur / 2.5F, 0, 1);
      if (flash > 0) g.fill(0, 0, w, h, argb(0xE8FFD8, 0.22F * flash));
      if (k < at) return;
      float tt = (k - at) * dur;               // ticks depuis l'apparition du titre
      float out = Mth.clamp((dur - age) / 8F, 0, 1);
      drawTitleCard(g, w, h, tt, out, top, title, sub, accent);
   }

   /** Carte de titre (filets, bandeau, titre lettre par lettre, sous-titre) ; tt = ticks depuis son apparition. */
   static void drawTitleCard(GuiGraphics g, int w, int h, float tt, float out, String top, String title, String sub, int accent) {
      HudBoss.carteTitre(g, w, h, tt, out, top, title, sub, accent, BANDEAU);
   }

   /** Cle de traduction -> texte (un texte deja traduit passe tel quel). */
   static String tr(String key) {
      return key != null && net.minecraft.client.resources.language.I18n.exists(key) ? net.minecraft.client.resources.language.I18n.get(key) : key == null ? "" : key;
   }

   static int argb(int rgb, float a) {
      return (Mth.clamp((int) (a * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
   }
}
