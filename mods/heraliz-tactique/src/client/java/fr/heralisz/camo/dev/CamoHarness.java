package fr.heralisz.camo.dev;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.gui.GunRefitScreen;
import fr.heralisz.camo.HeraliszCamo;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Banc d'essai des skins (client de dev seulement, activé par -Dheralisz.camo.harness=scenario.json) : ouvre un monde plat,
 * joue un scénario (commandes, vue 1re / 3e personne, visée, tir, inspection, écran des accessoires, captures), puis quitte.
 * Sert à juger en jeu ce que voit le porteur de l'arme et ce que voient les autres.
 */
@Mod.EventBusSubscriber(modid = HeraliszCamo.MODID, value = Dist.CLIENT)
public final class CamoHarness {
   private static final Logger LOG = LogUtils.getLogger();
   private static final String FILE = System.getProperty("heralisz.camo.harness");
   private static boolean launched;
   private static long startTick = -1;
   private static final List<JsonObject> STEPS = new ArrayList<>();
   private static int next;
   /** false : plus aucune interface (barre, viseur, HUD TaCZ) mais la main reste visible, contrairement à F1 */
   private static boolean gui = true;

   private CamoHarness() {
   }

   @SubscribeEvent
   public static void onGui(RenderGuiEvent.Pre e) {
      if (enabled() && !gui) e.setCanceled(true);
   }

   public static boolean enabled() {
      return FILE != null;
   }

   @SubscribeEvent
   public static void onTitle(ScreenEvent.Init.Post e) {
      if (!enabled() || launched || !(e.getScreen() instanceof TitleScreen)) return;
      launched = true;
      try {
         JsonObject root = JsonParser.parseString(Files.readString(Path.of(FILE))).getAsJsonObject();
         for (JsonElement el : root.getAsJsonArray("steps")) STEPS.add(el.getAsJsonObject());
      } catch (Exception ex) {
         LOG.error("Scénario illisible : {}", FILE, ex);
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      String name = "camo_harness";
      if (mc.getLevelSource().levelExists(name)) {
         mc.createWorldOpenFlows().loadLevel(e.getScreen(), name);
      } else {
         GameRules rules = new GameRules();
         rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
         rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
         rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
         LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
         mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(1234L, false, false),
            reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
      }
   }

   @SubscribeEvent
   public static void onTick(TickEvent.ClientTickEvent e) {
      if (!enabled() || e.phase != TickEvent.Phase.END) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
      if (startTick < 0) {
         startTick = mc.level.getGameTime() + 80;   // laisse le monde et les packs d'armes se charger
         return;
      }
      long t = mc.level.getGameTime() - startTick;
      while (next < STEPS.size() && STEPS.get(next).get("t").getAsLong() <= t) {
         try {
            run(mc, STEPS.get(next++));
         } catch (RuntimeException ex) {
            LOG.error("Banc d'essai : étape {} en échec", STEPS.get(next - 1), ex);
         }
      }
   }

   private static void run(Minecraft mc, JsonObject s) {
      if (s.has("cmd")) {
         String cmd = s.get("cmd").getAsString();
         MinecraftServer server = mc.getSingleplayerServer();
         server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
            var src = (p != null ? p.createCommandSourceStack() : server.createCommandSourceStack()).withPermission(4).withSuppressedOutput();
            server.getCommands().performPrefixedCommand(src, cmd);
         });
      }
      if (s.has("view")) {
         String v = s.get("view").getAsString();
         mc.options.setCameraType(v.equals("back") ? CameraType.THIRD_PERSON_BACK : v.equals("front") ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
      }
      IClientPlayerGunOperator op = IClientPlayerGunOperator.fromLocalPlayer(mc.player);
      if (s.has("aim")) {
         // TaCZ recale la visée sur l'état de la touche à chaque tick : on « tient » la touche
         com.tacz.guns.client.input.AimKey.AIM_KEY.setDown(s.get("aim").getAsBoolean());
         op.aim(s.get("aim").getAsBoolean());
      }
      if (s.has("slot")) mc.player.getInventory().selected = s.get("slot").getAsInt();
      if (s.has("shoot")) op.shoot();
      if (s.has("inspect")) {
         com.tacz.guns.api.entity.IGunOperator g = com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(mc.player);
         LOG.info("[camo-inspect] inspection demandée : reload={} shootCD={} drawCD={} bolting={} meleeCD={} aim={}", g.getSynReloadState().getStateType(),
            g.getSynShootCoolDown(), g.getSynDrawCoolDown(), g.getSynIsBolting(), g.getSynMeleeCoolDown(), g.getSynAimingProgress());
         op.inspect();
      }
      if (s.has("screen")) {
         String sc = s.get("screen").getAsString();
         mc.setScreen(sc.equals("refit") ? new GunRefitScreen() : sc.equals("inv") ? new InventoryScreen(mc.player) : null);
      }
      if (s.has("walk")) {
         // marche / sprint en avant : on « tient » les touches (tenues animées en mouvement)
         String w = s.get("walk").getAsString();
         mc.options.keyUp.setDown(!w.equals("stop"));
         mc.options.keySprint.setDown(w.equals("sprint"));
         mc.player.setSprinting(w.equals("sprint"));
      }
      if (s.has("hud")) mc.options.hideGui = !s.get("hud").getAsBoolean();
      if (s.has("gui")) gui = s.get("gui").getAsBoolean();
      if (s.has("fov")) mc.options.fov().set(s.get("fov").getAsInt());
      if (s.has("craftTest")) {
         // recette d'établi testée côté serveur, avec la vraie liste de recettes : a + b -> ?
         JsonObject c = s.getAsJsonObject("craftTest");
         MinecraftServer server = mc.getSingleplayerServer();
         server.execute(() -> {
            var menu = new net.minecraft.world.inventory.AbstractContainerMenu(null, -1) {
               @Override
               public net.minecraft.world.item.ItemStack quickMoveStack(net.minecraft.world.entity.player.Player p, int i) {
                  return net.minecraft.world.item.ItemStack.EMPTY;
               }

               @Override
               public boolean stillValid(net.minecraft.world.entity.player.Player p) {
                  return true;
               }
            };
            var grid = new net.minecraft.world.inventory.TransientCraftingContainer(menu, 3, 3);
            int slot = 0;
            for (String key : new String[] {"a", "b", "c"}) {
               if (!c.has(key)) continue;
               try {
                  var res = net.minecraft.commands.arguments.item.ItemParser.parseForItem(server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ITEM),
                     new com.mojang.brigadier.StringReader(c.get(key).getAsString()));
                  var st = new net.minecraft.world.item.ItemStack(res.item());
                  if (res.nbt() != null) st.setTag(res.nbt());
                  grid.setItem(slot++, st);
               } catch (Exception ex) {
                  LOG.error("[craft] objet illisible {}", c.get(key), ex);
               }
            }
            var level = server.overworld();
            var out = server.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, grid, level)
               .map(r -> r.assemble(grid, server.registryAccess())).orElse(net.minecraft.world.item.ItemStack.EMPTY);
            LOG.info("[craft] {} -> {} {}", c, out, out.getTag());
         });
      }
      if (s.has("decorTab")) {
         // onglet Personnaliser du menu de modification
         fr.heralisz.camo.decor.client.RefitDecorTab.open = s.get("decorTab").getAsBoolean();
         if (mc.screen instanceof GunRefitScreen gr) gr.init(mc, gr.width, gr.height);
      }
      if (s.has("decor")) {
         // pose un élément comme le ferait le joueur : on vise l'arme au point (sx, sy) de l'écran, puis demande au serveur
         JsonObject d = s.getAsJsonObject("decor");
         String kind = d.get("kind").getAsString();
         int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
         var hit = d.has("auto") ? null : fr.heralisz.camo.decor.client.DecorPick.pick(d.get("sx").getAsDouble() * w, d.get("sy").getAsDouble() * h, w, h);
         if (d.has("auto")) {
            // StatTrack posé automatiquement, comme au clic sur le module
            var fit = fr.heralisz.camo.decor.client.DecorPick.autoPlate(w, h,
               fr.heralisz.camo.decor.client.RefitDecorTab.placedOn(mc.player.getMainHandItem()));
            LOG.info("[decor] place auto -> {}", fit);
            if (fit != null) {
               var a = fit.hit();
               hit = a;
               d.addProperty("scale", fit.scale());
            }
         }
         LOG.info("[decor] visée {} -> {}", kind, hit);
         if (hit != null) {
            var at = d.has("auto") ? new fr.heralisz.camo.decor.DecorData.Placement(hit.bone(), hit.point().x, hit.point().y, hit.point().z, hit.normal().x,
               hit.normal().y, hit.normal().z, hit.up().x, hit.up().y, hit.up().z, d.get("scale").getAsFloat(), 0)
               : fr.heralisz.camo.decor.client.RefitDecorTab.placementFor(kind.equals("sticker") ? 0 : kind.equals("charm") ? 1 : 2, hit,
               d.has("scale") ? d.get("scale").getAsFloat() : 1, d.has("rot") ? d.get("rot").getAsFloat() : 0);
            LOG.info("[decor] pose {} -> {}", kind, at);
            String item = d.get("item").getAsString();
            int slot = -1;
            for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
               var st = mc.player.getInventory().getItem(i);
               if (!st.isEmpty() && net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(st.getItem()).toString().equals(item)) slot = i;
            }
            int action = kind.equals("sticker") ? fr.heralisz.camo.network.DecorPacket.ADD_STICKER
               : kind.equals("charm") ? fr.heralisz.camo.network.DecorPacket.SET_CHARM : fr.heralisz.camo.network.DecorPacket.SET_STATTRACK;
            if (at != null) fr.heralisz.camo.network.CamoNetwork.CHANNEL.sendToServer(new fr.heralisz.camo.network.DecorPacket(action, slot, -1, at));
         }
      }
      if (s.has("shot")) {
         Screenshot.grab(mc.gameDirectory, "camo_" + s.get("shot").getAsString() + ".png", mc.getMainRenderTarget(), msg -> {});
      }
      if (s.has("tooltip")) {
         // infobulle telle que le joueur la voit (emplacement Curios, stats…) : écrite dans le journal
         for (JsonElement id : s.getAsJsonArray("tooltip")) {
            var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(id.getAsString()));
            var lines = new net.minecraft.world.item.ItemStack(item).getTooltipLines(mc.player, net.minecraft.world.item.TooltipFlag.Default.NORMAL);
            LOG.info("[tooltip] {} : {}", id.getAsString(), String.join(" | ", lines.stream().map(net.minecraft.network.chat.Component::getString).toList()));
         }
      }
      if (s.has("click")) {
         // clic dans l'écran ouvert, comme la souris : { slot : case du menu, button : 0 gauche / 1 droit }
         JsonObject k = s.getAsJsonObject("click");
         mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, k.get("slot").getAsInt(), k.get("button").getAsInt(),
            net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
      }
      if (s.has("slotReport")) {
         // contenu de cases du menu ouvert (id + étiquettes) : résultat d'un craft, objets posés dans la grille
         StringBuilder sb = new StringBuilder();
         for (JsonElement e : s.getAsJsonArray("slotReport")) {
            int i = e.getAsInt();
            var st = mc.player.containerMenu.getSlot(i).getItem();
            sb.append(" | case ").append(i).append(" = ").append(st.isEmpty() ? "vide" : net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(st.getItem())
               + " x" + st.getCount() + (st.hasTag() ? " " + st.getTag() : ""));
         }
         LOG.info("[cases]{}", sb);
      }
      if (s.has("tooltipHand")) {
         // infobulle de l'objet en main (avec ses étiquettes : camo posé…)
         var lines = mc.player.getMainHandItem().getTooltipLines(mc.player, net.minecraft.world.item.TooltipFlag.Default.NORMAL);
         LOG.info("[tooltip] {} (en main) : {}", s.get("tooltipHand").getAsString(),
            String.join(" | ", lines.stream().map(net.minecraft.network.chat.Component::getString).toList()));
      }
      if (s.has("use")) {
         // clic droit avec l'objet en main (équipement par clic droit)
         mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
      }
      if (s.has("press")) {
         // appui sur une touche du jeu par son nom (ex. key.backpack)
         for (net.minecraft.client.KeyMapping k : mc.options.keyMappings) {
            if (k.getName().equals(s.get("press").getAsString())) {
               k.setDown(true);   // certains mods demandent la touche enfoncée ET un clic
               net.minecraft.client.KeyMapping.click(k.getKey());
            }
         }
      }
      if (s.has("release")) {
         for (net.minecraft.client.KeyMapping k : mc.options.keyMappings) {
            if (k.getName().equals(s.get("release").getAsString())) {
               k.setDown(false);
            }
         }
      }
      if (s.has("gunbench")) {
         JsonObject b = s.getAsJsonObject("gunbench");
         CamoBench.gun(mc, b.get("label").getAsString(), b.get("camo").getAsString(), b.get("decor").getAsBoolean(), b.get("n").getAsInt());
      }
      if (s.has("backpackKey")) {
         CamoBench.pressBackpackKey(mc);
      }
      if (s.has("report")) {
         CamoBench.report(mc, s.get("report").getAsString());
      }
      if (s.has("bench")) {
         // coût du rendu d'un équipement (classe séparée : Curios n'est chargé que pour cette étape)
         JsonObject b = s.getAsJsonObject("bench");
         CamoBench.run(mc, b.get("item").getAsString(), b.get("slot").getAsString(), b.get("n").getAsInt(),
               b.has("dist") ? b.get("dist").getAsDouble() : 0);
      }
      if (s.has("quit")) mc.stop();
   }
}
