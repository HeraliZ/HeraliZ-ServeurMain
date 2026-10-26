package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import fr.heraliz.reanimation.client.kit.Echelle;
import fr.heraliz.reanimation.client.kit.HzButton;
import fr.heraliz.reanimation.client.kit.SonsUi;
import fr.heraliz.reanimation.client.kit.Ui;
import fr.heraliz.reanimation.execution.ExecutionType;
import fr.heraliz.reanimation.network.ActionPacket;
import fr.heraliz.reanimation.network.Network;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class ExecutionScreen extends Screen {
   private static final ExecutionType[] TYPES = Arrays.stream(ExecutionType.values()).filter(ExecutionType::selectable).toArray(ExecutionType[]::new);
   private static final int[] ROWS = rows();
   private static final int ROW_H = 24;
   private static final int HEADER_H = 14;
   private static final ResourceLocation FLOOR = new ResourceLocation("textures/block/coarse_dirt.png");
   /** DM Sans gras 14 (nom de l'exécution choisie), à sa vraie taille. */
   private static final ResourceLocation TITRE = new ResourceLocation("reanimation", "ui_titre");
   private static final int GOLD = -863622;
   private static final int RED = -2084822;
   private static final int GREEN = -11214726;
   private static final int GREY = -4671304;
   private int shown;
   private int ticks;
   private int x0;
   private int y0;
   private int w;
   private int h;
   private HzButton equip;
   private int closeX;
   private int closeY;
   private boolean opened;
   private PlayerModel<LivingEntity> actorModel;
   private PlayerModel<LivingEntity> victimModel;
   private ResourceLocation actorSkin;
   private ResourceLocation victimSkin;
   private final Vector3f camCenter = new Vector3f();
   private float camZoom;
   private boolean camReady;
   private long camNanos;
   private long lastClick;
   private int lastClicked = -1;
   private int scroll;

   private static int[] rows() {
      List<Integer> out = new ArrayList<>();

      for (ExecutionType.Group group : ExecutionType.Group.values()) {
         boolean any = false;

         for (int i = 0; i < TYPES.length; i++) {
            if (TYPES[i].group() == group) {
               if (!any) {
                  out.add(-1 - group.ordinal());
                  any = true;
               }

               out.add(i);
            }
         }
      }

      return out.stream().mapToInt(Integer::intValue).toArray();
   }

   public ExecutionScreen() {
      super(Component.translatable("menu.reanimation.title"));
      this.shown = Math.max(0, indexOf(ClientUnlocks.selected()));
   }

   static void open() {
      Minecraft.getInstance().setScreen(new ExecutionScreen());
      Network.sendToServer(new ActionPacket(4));
   }

   private static int indexOf(int ordinal) {
      for (int i = 0; i < TYPES.length; i++) {
         if (TYPES[i].ordinal() == ordinal) {
            return i;
         }
      }

      return -1;
   }

   private static boolean equipped(int i) {
      return ClientUnlocks.selected() == TYPES[i].ordinal();
   }

   protected void init() {
      Echelle.forcer(this);
      if (!this.opened) {
         this.opened = true;
         SonsUi.ouvrir(0.5F);
      }
      this.w = Math.min(this.width - 24, 580);
      this.h = Math.min(this.height - 12, 332);
      this.x0 = (this.width - this.w) / 2;
      this.y0 = (this.height - this.h) / 2;
      Minecraft mc = Minecraft.getInstance();
      boolean slim = mc.player != null && "slim".equals(mc.player.getModelName());
      this.actorModel = ExecutionFx.playerModel(slim);
      this.victimModel = ExecutionFx.playerModel(false);
      this.actorSkin = mc.player != null ? mc.player.getSkinTextureLocation() : BotRenderer.skin(4);
      this.victimSkin = BotRenderer.skin(this.actorSkin.equals(BotRenderer.skin(0)) ? 4 : 0);
      this.equip = (HzButton)this.addRenderableWidget(
         new HzButton(this.listX(), this.y0 + this.h - 34, this.listW(), 20, Component.empty(), Ui.accent(), this::equipShown).look(HzButton.Look.PRIMARY)
      );
      this.closeX = this.x0 + this.w - 16 - 9;
      this.closeY = this.y0 + 15;
      this.refreshButton();
      this.scrollBy(0);
      this.reveal();
   }

   private int listX() {
      return this.x0 + 16;
   }

   private int listW() {
      return Math.min(176, this.w * 2 / 5);
   }

   private int listTop() {
      return this.y0 + 44;
   }

   private int listBottom() {
      return this.y0 + this.h - 44;
   }

   private static int rowH(int row) {
      return ROWS[row] < 0 ? 14 : 24;
   }

   private int rowY(int row) {
      int y = this.listTop() - this.scroll;

      for (int i = 0; i < row; i++) {
         y += rowH(i);
      }

      return y;
   }

   private int contentH() {
      int total = 0;

      for (int i = 0; i < ROWS.length; i++) {
         total += rowH(i);
      }

      return total;
   }

   private void scrollBy(int dy) {
      this.scroll = Mth.clamp(this.scroll + dy, 0, Math.max(0, this.contentH() - (this.listBottom() - this.listTop())));
   }

   private void reveal() {
      for (int row = 0; row < ROWS.length; row++) {
         if (ROWS[row] == this.shown) {
            int y = this.rowY(row);
            int top = this.listTop();
            int bottom = this.listBottom();
            if (y - (row > 0 && ROWS[row - 1] < 0 ? 14 : 0) < top) {
               this.scrollBy(y - top - (row > 0 && ROWS[row - 1] < 0 ? 14 : 0));
            } else if (y + 24 > bottom) {
               this.scrollBy(y + 24 - bottom);
            }
         }
      }
   }

   private int previewX() {
      return this.listX() + this.listW() + 18;
   }

   private int previewY() {
      return this.y0 + 44;
   }

   private int previewW() {
      return this.x0 + this.w - 16 - this.previewX();
   }

   private int previewH() {
      return this.h - 44 - 96;
   }

   private void refreshButton() {
      if (this.equip != null) {
         ExecutionType type = TYPES[this.shown];
         boolean owned = ClientUnlocks.owns(type);
         boolean equipped = equipped(this.shown);
         this.equip.active = owned && !equipped;
         this.equip.setMessage(Component.translatable(!owned ? "menu.reanimation.locked" : (equipped ? "menu.reanimation.equipped_btn" : "menu.reanimation.equip")));
      }
   }

   private void equipShown() {
      ExecutionType type = TYPES[this.shown];
      if (ClientUnlocks.owns(type)) {
         ClientUnlocks.select(type);
         Network.sendToServer(new ActionPacket(3, type.ordinal()));
         Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_NETHERITE, 1.0F));
         this.refreshButton();
      }
   }

   private void show(int index) {
      index = Math.floorMod(index, TYPES.length);
      if (index != this.shown) {
         this.shown = index;
         this.ticks = 0;
         this.camReady = false;
         this.reveal();
      }

      this.refreshButton();
   }

   public void tick() {
      this.ticks++;
      if (this.ticks > 118 + 30) {
         this.ticks = 0;
      }

      this.refreshButton();
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void onClose() {
      SonsUi.fermer(0.5F);
      super.onClose();
   }

   public void removed() {
      Echelle.restaurer();
      super.removed();
   }

   public boolean mouseScrolled(double mx, double my, double delta) {
      if (mx >= this.listX() - 4 && mx < this.listX() + 6 + this.listW()) {
         this.scrollBy((int)(-delta * 24.0));
         return true;
      } else {
         return super.mouseScrolled(mx, my, delta);
      }
   }

   public boolean mouseClicked(double mx, double my, int button) {
      if (button == 0 && mx >= this.closeX - 3 && mx < this.closeX + 10 && my >= this.closeY - 3 && my < this.closeY + 10) {
         SonsUi.clic(0.6F);
         this.onClose();
         return true;
      }

      for (int row = 0; row < ROWS.length; row++) {
         int i = ROWS[row];
         int y = this.rowY(row);
         if (i >= 0 && my >= this.listTop() && my < this.listBottom() && mx >= this.listX() && mx < this.listX() + this.listW() && my >= y && my < y + 24 - 3) {
            long now = Util.getMillis();
            boolean twice = i == this.lastClicked && now - this.lastClick < 350L;
            this.lastClicked = i;
            this.lastClick = now;
            this.show(i);
            SonsUi.clic(0.6F);
            if (twice) {
               this.equipShown();
            }

            return true;
         }
      }

      return super.mouseClicked(mx, my, button);
   }

   public boolean keyPressed(int key, int scan, int modifiers) {
      if (ClientSetup.MENU.matches(key, scan)) {
         this.onClose();
         return true;
      }

      if (key == 265 || key == 264) {
         this.show(this.shown + (key == 265 ? -1 : 1));
         return true;
      }

      if (key != 257 && key != 335) {
         return super.keyPressed(key, scan, modifiers);
      }

      this.equipShown();
      return true;
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      Ui.fond(g, this.width, this.height);
      Ui.glass(g, this.x0, this.y0, this.w, this.h);
      int accent = Ui.accent();
      // ---- header: title, progress, close cross
      String title = this.title.getString();
      Ui.spaced(g, this.font, title, Ui.BOLD, this.x0 + 16, this.y0 + 16, Ui.argb(Ui.TEXT, 255), 1.6F);
      int tw = (int)Ui.spacedWidth(this.font, title.toUpperCase(Locale.ROOT), Ui.BOLD, 1.6F);
      int owned = 0;

      for (ExecutionType type : TYPES) {
         if (ClientUnlocks.owns(type)) {
            owned++;
         }
      }

      int sel = indexOf(ClientUnlocks.selected());
      Component sub = sel >= 0
         ? Component.translatable("menu.reanimation.subtitle", owned, TYPES.length, TYPES[sel].displayName())
         : Component.translatable("menu.reanimation.subtitle_none", owned, TYPES.length);
      g.drawString(this.font, Ui.in(sub, Ui.FONT), this.x0 + 16 + tw + 16, this.y0 + 16, Ui.argb(Ui.MUTED, 255), false);
      boolean overClose = mx >= this.closeX - 3 && mx < this.closeX + 10 && my >= this.closeY - 3 && my < this.closeY + 10;
      int cc = overClose ? Ui.argb(Ui.TEXT, 255) : Ui.argb(Ui.MUTED, 255);

      for (int i = 0; i < 7; i++) {
         g.fill(this.closeX + i, this.closeY + i, this.closeX + i + 1, this.closeY + i + 1, cc);
         g.fill(this.closeX + 6 - i, this.closeY + i, this.closeX + 7 - i, this.closeY + i + 1, cc);
      }

      Ui.hairline(g, this.x0 + 16, this.x0 + this.w - 16, this.y0 + 34, 0xFFFFFF, 36, false, true);
      // ---- list
      int top = this.listTop();
      int bottom = this.listBottom();
      g.enableScissor(this.listX() - 4, top, this.listX() + 6 + this.listW(), bottom);

      for (int row = 0; row < ROWS.length; row++) {
         int y = this.rowY(row);
         if (y + rowH(row) >= top && y <= bottom) {
            if (ROWS[row] < 0) {
               ExecutionType.Group group = ExecutionType.Group.values()[-1 - ROWS[row]];
               String name = Component.translatable("menu.reanimation.group." + group.name().toLowerCase(Locale.ROOT)).getString();
               if (group == ExecutionType.Group.NOEL) {
                  Ui.spaced(g, this.font, name, Ui.SMALL, this.listX(), y + 4, Ui.argb(0xE04848, 255), 1.0F);
                  int nw = (int)Ui.spacedWidth(this.font, name.toUpperCase(Locale.ROOT), Ui.SMALL, 1.0F);
                  Ui.hairline(g, this.listX() + nw + 8, this.listX() + this.listW(), y + 8, 0xE04848, 60, false, true);
               } else {
                  Ui.section(g, this.font, name, this.listX(), y + 4, this.listW());
               }
            } else {
               this.renderEntry(g, ROWS[row], y, mx, my, accent);
            }
         }
      }

      g.disableScissor();
      int content = this.contentH();
      int view = bottom - top;
      if (content > view) {
         int bx = this.listX() + 4 + this.listW();
         int barH = Math.max(12, view * view / content);
         int barY = top + (view - barH) * this.scroll / Math.max(1, content - view);
         g.fill(bx, top, bx + 2, bottom, Ui.argb(0xFFFFFF, 18));
         g.fill(bx, barY, bx + 2, barY + barH, Ui.argb(accent, 200));
      }

      // ---- preview stage
      int px = this.previewX();
      int py = this.previewY();
      int pw = this.previewW();
      int ph = this.previewH();
      Ui.soft(g, px, py, pw, ph, Ui.argb(0, 70));
      g.fillGradient(px + 1, py + 1, px + pw - 1, py + ph - 1, Ui.argb(Ui.mix(0x1A1E2A, accent, 0.1F), 255), Ui.argb(Ui.mix(0x07080B, accent, 0.06F), 255));
      this.renderPreview(g, px + 1, py + 1, pw - 2, ph - 2, pt);
      Ui.softOutline(g, px + 1, py + 1, pw - 2, ph - 2, Ui.argb(0xFFFFFF, 22));
      g.fill(px + 2, py + 1, px + pw - 2, py + 2, Ui.argb(0xFFFFFF, 34));
      // ---- details
      ExecutionType type = TYPES[this.shown];
      boolean have = ClientUnlocks.owns(type);
      boolean on = equipped(this.shown);
      int dy = py + ph + 9;
      // nom en gras 14 à sa vraie taille (avant : gras 11 agrandi 1,3 fois), milieu des capitales au même endroit
      String name = Ui.ellipsis(this.font, type.displayName().getString(), TITRE, (int)(pw * 0.55F));
      this.texteNet(g, Ui.lit(name, TITRE), px, dy + 1.3F * milieu(11.0F) - milieu(14.0F), Ui.argb(0xFFFFFF, 255));
      int cx = px + pw;
      String state = Component.translatable(on ? "menu.reanimation.badge_equipped" : (have ? "menu.reanimation.badge_owned" : "menu.reanimation.badge_locked")).getString();
      int stateRgb = on ? Ui.OK : (have ? Ui.MUTED : Ui.DANGER);
      cx -= (int)Ui.spacedWidth(this.font, state.toUpperCase(Locale.ROOT), Ui.SMALL, 0.6F) + 10;
      Ui.chip(g, this.font, state, cx, dy + 1, stateRgb);
      String group = Component.translatable("menu.reanimation.group." + type.group().name().toLowerCase(Locale.ROOT)).getString();
      cx -= (int)Ui.spacedWidth(this.font, group.toUpperCase(Locale.ROOT), Ui.SMALL, 0.6F) + 14;
      Ui.chip(g, this.font, group, cx, dy + 1, type.group() == ExecutionType.Group.NOEL ? 0xE04848 : accent);
      dy += 17;
      dy = this.paragraph(g, type.description(), px, dy, pw, 3, Ui.argb(Ui.TEXT, 215));
      if (!have) {
         this.paragraph(g, Component.translatable("menu.reanimation.locked_hint"), px, dy + 2, pw, 2, Ui.argb(Ui.DANGER, 255));
      } else if (type.headroom > 0) {
         this.paragraph(g, Component.translatable("menu.reanimation.air"), px, dy + 2, pw, 2, Ui.argb(Ui.AMBRE, 255));
      }

      // ---- footer: weapons hint next to the equip button
      Ui.hairline(g, this.x0 + 16, this.x0 + this.w - 16, this.y0 + this.h - 41, 0xFFFFFF, 30, true, true);
      List<FormattedCharSequence> hint = this.font.split(Ui.tr(Ui.SMALL, "menu.reanimation.weapons"), px + pw - this.previewX());

      for (int i = 0; i < Math.min(2, hint.size()); i++) {
         g.drawString(this.font, hint.get(i), px, this.y0 + this.h - 33 + i * 9 + (hint.size() == 1 ? 5 : 0), Ui.argb(Ui.FAINT, 255), false);
      }

      super.render(g, mx, my, pt);
   }

   private void renderEntry(GuiGraphics g, int i, int ey, int mx, int my, int accent) {
      ExecutionType type = TYPES[i];
      int ex = this.listX();
      int ew = this.listW();
      int eh = 21;
      boolean hover = mx >= ex && mx < ex + ew && my >= ey && my < ey + eh && my >= this.listTop() && my < this.listBottom();
      boolean owned = ClientUnlocks.owns(type);
      boolean equipped = equipped(i);
      if (i == this.shown) {
         Ui.soft(g, ex, ey, ew, eh, Ui.argb(accent, hover ? 44 : 30));
         Ui.softOutline(g, ex + 1, ey + 1, ew - 2, eh - 2, Ui.argb(accent, 200));
      } else {
         Ui.soft(g, ex, ey, ew, eh, Ui.argb(0xFFFFFF, hover ? 16 : 6));
      }

      g.fill(ex + 1, ey + 3, ex + 3, ey + eh - 3, Ui.argb(equipped ? Ui.OK : (owned ? Ui.mix(accent, 0xFFFFFF, 0.2F) : Ui.DANGER), owned || equipped ? 255 : 150));
      g.drawString(this.font, Ui.in(type.displayName(), Ui.FONT), ex + 9, ey + 2, owned ? Ui.argb(0xFFFFFF, 255) : Ui.argb(Ui.MUTED, 255), false);
      String status = Component.translatable(
            equipped ? "menu.reanimation.badge_equipped" : (owned ? "menu.reanimation.badge_owned" : "menu.reanimation.badge_locked")
         )
         .getString();
      Ui.spaced(g, this.font, status, Ui.SMALL, ex + 9, ey + 12, equipped ? Ui.argb(Ui.OK, 255) : (owned ? Ui.argb(Ui.FAINT, 255) : Ui.argb(Ui.DANGER, 200)), 0.6F);
   }

   private int paragraph(GuiGraphics g, Component text, int x, int y, int width, int max, int color) {
      List<FormattedCharSequence> lines = this.font.split(Ui.in(text, Ui.FONT), width);

      for (int i = 0; i < Math.min(max, lines.size()); i++) {
         g.drawString(this.font, lines.get(i), x, y, color, false);
         y += 10;
      }

      return y;
   }

   private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
      g.fill(x, y, x + w, y + 1, color);
      g.fill(x, y + h - 1, x + w, y + h, color);
      g.fill(x, y, x + 1, y + h, color);
      g.fill(x + w - 1, y, x + w, y + h, color);
   }

   private void renderPreview(GuiGraphics g, int px, int py, int pw, int ph, float pt) {
      ExecutionType type = TYPES[this.shown];
      Choreography ch = Choreography.of(type);
      float time = this.ticks + pt;
      float end = 118.0F + 8.0F;
      float t = Math.min(time, end);
      Vector3f start = new Vector3f(0.0F, 0.0F, ch.reach());
      CoupDeGrace.State s = ch.at(t, 1, 90.0F, start, 180.0F);
      Vector3f min = new Vector3f(Float.MAX_VALUE);
      Vector3f max = new Vector3f(-Float.MAX_VALUE);

      for (float ahead = -4.0F; ahead <= 10.0F; ahead += 2.0F) {
         CoupDeGrace.State q = ahead == 0.0F ? s : ch.at(Mth.clamp(t + ahead, 0.0F, end), 1, 90.0F, start, 180.0F);
         extend(min, max, q.actor);
         extend(min, max, q.victim);
         if (q instanceof Spectacle.Frame f && f.soul != null) {
            extend(min, max, f.soul);
         }
      }

      if (min.y < 3.5F) {
         min.y = Math.min(min.y, 0.0F);
      }

      if (max.y - min.y > 11.0F) {
         min.y = max.y - 11.0F;
      }

      float tall = max.y - min.y + 1.0F;
      float wide = Math.max(max.x - min.x, max.z - min.z) + 1.4F;
      Vector3f center = new Vector3f((min.x + max.x) / 2.0F, (min.y + max.y) / 2.0F + 0.1F, (min.z + max.z) / 2.0F);
      float zoom = Mth.clamp(Math.min(ph / tall, pw / wide), ph / 12.5F, ph / 2.9F);
      long now = System.nanoTime();
      float dt = this.camNanos == 0L ? 0.0F : Mth.clamp((float)(now - this.camNanos) / 1.0E9F, 0.0F, 0.1F);
      this.camNanos = now;
      if (!this.camReady) {
         this.camCenter.set(center);
         this.camZoom = zoom;
         this.camReady = true;
      } else {
         float k = 1.0F - (float)Math.exp(-6.0F * dt);
         this.camCenter.lerp(center, k);
         this.camZoom = this.camZoom + (zoom - this.camZoom) * k;
      }

      g.enableScissor(px, py, px + pw, py + ph);
      PoseStack ps = g.pose();
      ps.pushPose();
      ps.translate(px + pw / 2.0F, py + ph / 2.0F, 400.0F);
      ps.scale(this.camZoom, this.camZoom, -this.camZoom);
      float orbit = 38.0F + 14.0F * Mth.sin(time * 0.02F);
      ps.mulPose(Axis.ZP.rotationDegrees(180.0F));
      ps.mulPose(Axis.XP.rotationDegrees(-16.0F));
      ps.mulPose(Axis.YP.rotationDegrees(orbit));
      ps.translate(-this.camCenter.x, -this.camCenter.y, -this.camCenter.z);
      RenderSystem.enableDepthTest();
      Lighting.setupForEntityInInventory();
      BufferSource buffers = g.bufferSource();
      floor(ps, buffers);
      if (ch.actorAlpha(s) >= 1.0F) {
         ch.poseActor(this.actorModel, s, time);
         Spectacle.copyLayers(this.actorModel);
         body(ps, buffers, this.actorModel, this.actorSkin, s.actor);
      }

      if (ch.victimAlpha(s) >= 1.0F) {
         ch.poseVictim(this.victimModel, s, time);
         Spectacle.copyLayers(this.victimModel);
         body(ps, buffers, this.victimModel, this.victimSkin, s.victim);
      }

      Quaternionf facing = new Quaternionf().rotationZ((float) Math.PI).rotateX(-0.27925268F).rotateY(orbit * (float) (Math.PI / 180.0)).conjugate();
      Minecraft mc = Minecraft.getInstance();
      boolean slim = mc.player != null && "slim".equals(mc.player.getModelName());
      ch.renderExtras(
         ps,
         buffers,
         s,
         time,
         new Choreography.Extras(ExecutionFx.soulModel(false), this.ticks, pt, true, facing, 15728880, this.actorSkin, slim, this.victimSkin, false)
      );
      g.flush();
      ps.popPose();
      Lighting.setupFor3DItems();
      g.disableScissor();
   }

   private static void extend(Vector3f min, Vector3f max, CoupDeGrace.Body b) {
      for (float height : new float[]{0.0F, 0.9F, 1.85F}) {
         Vector3f p = b.axis(height);
         min.min(p);
         max.max(p);
      }
   }

   private static void body(PoseStack ps, MultiBufferSource buffers, PlayerModel<LivingEntity> model, ResourceLocation skin, CoupDeGrace.Body b) {
      ps.pushPose();
      ps.translate(b.pos.x, b.pos.y, b.pos.z);
      ps.mulPose(b.rot);
      ps.scale(-1.0F, -1.0F, 1.0F);
      ps.scale(0.9375F, 0.9375F, 0.9375F);
      ps.translate(0.0F, -1.501F, 0.0F);
      model.renderToBuffer(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(skin)), 15728880, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
      ps.popPose();
   }

   private static void floor(PoseStack ps, MultiBufferSource buffers) {
      VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(FLOOR));
      Pose last = ps.last();
      Matrix4f m = last.pose();
      Matrix3f n = last.normal();
      int r = 4;

      for (int i = -r; i < r; i++) {
         for (int j = -r; j < r; j++) {
            float c = Mth.clamp(1.15F - Mth.sqrt((i + 0.5F) * (i + 0.5F) + (j + 0.5F) * (j + 0.5F)) / r, 0.12F, 1.0F);
            vertex(vc, m, n, i, j, 0.0F, 0.0F, c);
            vertex(vc, m, n, i, j + 1, 0.0F, 1.0F, c);
            vertex(vc, m, n, i + 1, j + 1, 1.0F, 1.0F, c);
            vertex(vc, m, n, i + 1, j, 1.0F, 0.0F, c);
         }
      }
   }

   private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float z, float u, float v, float c) {
      vc.vertex(m, x, 0.0F, z)
         .color(c, c, c, 1.0F)
         .uv(u, v)
         .overlayCoords(OverlayTexture.NO_OVERLAY)
         .uv2(15728880)
         .normal(n, 0.0F, 1.0F, 0.0F)
         .endVertex();
   }

   // ------------------------------------------------------------------ texte à sa vraie taille, posé net

   /** Milieu des capitales sous le point de dessin, pour une police DM Sans du mod de cette taille (décalage 1). */
   private static float milieu(float taille) {
      return taille * 0.49309F - 1.0F;
   }

   /** Pose le dessin qui suit sur le point d'écran le plus proche (lettres nettes ; repris du kit des Caisses). */
   private static void placer(GuiGraphics g, float x, float y) {
      Matrix4f m = g.pose().last().pose();
      double s = Minecraft.getInstance().getWindow().getGuiScale();
      float ex = m.m00() * x + m.m30(), ey = m.m11() * y + m.m31();
      float rx = (float)(Math.round(ex * s) / s), ry = (float)(Math.round(ey * s) / s);
      float kx = m.m00() == 0.0F ? 1.0F : m.m00(), ky = m.m11() == 0.0F ? 1.0F : m.m11();
      g.pose().translate(x + (rx - ex) / kx, y + (ry - ey) / ky, 0.0F);
   }

   /** Texte à sa vraie taille : bord gauche x, point de dessin y, posé sur un point de l'écran. */
   private void texteNet(GuiGraphics g, Component c, float x, float y, int argb) {
      g.pose().pushPose();
      placer(g, x, y);
      g.drawString(this.font, c, 0, 0, argb, false);
      g.pose().popPose();
   }
}
