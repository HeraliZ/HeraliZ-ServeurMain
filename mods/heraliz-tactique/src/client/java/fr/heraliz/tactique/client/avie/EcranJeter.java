package fr.heraliz.tactique.client.avie;

import fr.heraliz.tactique.avie.ObjetsAVie;
import fr.heraliz.tactique.avie.PaquetJeterQuestion;
import fr.heraliz.tactique.avie.PaquetJeterReponse;
import fr.heraliz.tactique.client.kit.SonsUi;
import fr.heraliz.tactique.client.kit.Ui;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * « Jeter cet objet ? » (objets à vie, {@link ObjetsAVie}) : petit panneau de verre par-dessus l'inventaire, l'objet et
 * son nom, « Non, le garder » et « Oui, jeter ». Échap, Entrée ou fermeture = non (on ne jette jamais sans le vouloir).
 * Le serveur a mis l'objet de côté et décide de tout ; l'écran d'avant revient après la réponse.
 */
public final class EcranJeter extends Screen {
   private static final int W = 220, H = 104, BTN_W = 96, BTN_H = 20;
   @Nullable
   private final Screen avant;
   private final ItemStack objet;
   private boolean repondu;

   private EcranJeter(@Nullable Screen avant, ItemStack objet) {
      super(Component.translatable("heraliz_tactique.a_vie.titre"));
      this.avant = avant;
      this.objet = objet;
   }

   public static void brancher() {
      ObjetsAVie.QUESTION_CLIENT.brancher(EcranJeter::ouvrir);
   }

   private static void ouvrir(PaquetJeterQuestion q) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return;
      Screen avant = mc.screen instanceof EcranJeter e ? e.avant : mc.screen;
      if (mc.screen instanceof EcranJeter ancien) ancien.repondu = true; // remplacé : l'ancien objet a déjà été rendu par le serveur
      mc.setScreen(new EcranJeter(avant, q.objet()));
      SonsUi.ouvrir(0.6f);
   }

   private int x0() {
      return (this.width - W) / 2;
   }

   private int y0() {
      return (this.height - H) / 2;
   }

   private boolean sur(double mx, double my, int x, int y) {
      return mx >= x && mx < x + BTN_W && my >= y && my < y + BTN_H;
   }

   private int btnY() {
      return y0() + H - BTN_H - 12;
   }

   private int nonX() {
      return this.width / 2 - BTN_W - 4;
   }

   private int ouiX() {
      return this.width / 2 + 4;
   }

   @Override
   public void render(GuiGraphics g, int mx, int my, float pt) {
      if (this.avant != null) this.avant.render(g, -1, -1, pt);
      g.pose().pushPose();
      g.pose().translate(0, 0, 400);
      g.fill(0, 0, this.width, this.height, Ui.argb(0, 120));
      int x = x0(), y = y0();
      Ui.glass(g, x, y, W, H, 235);
      float cx = this.width / 2f;
      Ui.spacedCentered(g, this.font, Component.translatable("heraliz_tactique.a_vie.titre").getString(), Ui.SMALL, cx, y + 11, Ui.argb(Ui.MUTED, 255), 0.8f);
      // objet et son nom, centrés ensemble
      Component nom = Ui.in(this.objet.getHoverName(), Ui.BOLD);
      int wn = Math.min(this.font.width(nom), W - 48);
      int bloc = 16 + 6 + wn, bx = Math.round(cx - bloc / 2f);
      g.renderItem(this.objet, bx, y + 26);
      g.drawString(this.font, Ui.lit(Ui.ellipsis(this.font, nom.getString(), Ui.BOLD, W - 48), Ui.BOLD), bx + 22, y + 30, Ui.argb(Ui.TEXT, 255), false);
      Ui.centered(g, this.font, Ui.tr(Ui.FONT, "heraliz_tactique.a_vie.texte"), cx, y + 50, Ui.argb(Ui.MUTED, 255));
      bouton(g, nonX(), btnY(), Ui.tr(Ui.BOLD, "heraliz_tactique.a_vie.non"), Ui.OK, sur(mx, my, nonX(), btnY()));
      bouton(g, ouiX(), btnY(), Ui.tr(Ui.BOLD, "heraliz_tactique.a_vie.oui"), Ui.DANGER, sur(mx, my, ouiX(), btnY()));
      g.pose().popPose();
   }

   private void bouton(GuiGraphics g, int x, int y, Component texte, int rgb, boolean survol) {
      Ui.soft(g, x, y, BTN_W, BTN_H, Ui.argb(rgb, survol ? 70 : 34));
      Ui.softOutline(g, x + 1, y + 1, BTN_W - 2, BTN_H - 2, Ui.argb(rgb, survol ? 200 : 110));
      Ui.centered(g, this.font, texte, x + BTN_W / 2f, y + (BTN_H - 8) / 2f, Ui.argb(Ui.mix(rgb, 0xFFFFFF, 0.55f), 255));
   }

   @Override
   public boolean mouseClicked(double mx, double my, int bouton) {
      if (bouton == 0) {
         if (sur(mx, my, nonX(), btnY())) {
            repondre(false);
            return true;
         }
         if (sur(mx, my, ouiX(), btnY())) {
            repondre(true);
            return true;
         }
      }
      return true;
   }

   @Override
   public boolean keyPressed(int touche, int scan, int mods) {
      if (touche == GLFW.GLFW_KEY_ESCAPE || touche == GLFW.GLFW_KEY_ENTER || touche == GLFW.GLFW_KEY_KP_ENTER) {
         repondre(false);
         return true;
      }
      return true;
   }

   private void repondre(boolean jeter) {
      if (!this.repondu) {
         this.repondu = true;
         ObjetsAVie.envoyerAuServeur(new PaquetJeterReponse(jeter));
         SonsUi.clic(0.6f);
      }
      this.minecraft.setScreen(this.avant);
   }

   @Override
   public void removed() {
      // fermé autrement (mort, changement de monde, autre écran) : non
      if (!this.repondu) {
         this.repondu = true;
         ObjetsAVie.envoyerAuServeur(new PaquetJeterReponse(false));
      }
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }
}
