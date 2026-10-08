package fr.heralisz.plague.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.plague.HeraliszPlague;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Banc d'essai (client de dev uniquement, activé par -Dheralisz.harness=scenario.json) : ouvre/crée un monde plat,
 * joue un scénario (commandes, caméra fixe, captures d'écran), puis quitte. Sert à juger le rendu réel en jeu.
 */
@Mod.EventBusSubscriber(modid = HeraliszPlague.MODID, value = Dist.CLIENT)
public final class Harness {
   private static final String FILE = System.getProperty("heralisz.harness");
   private static boolean launched;
   private static long startTick = -1;
   private static final List<JsonObject> STEPS = new ArrayList<>();
   private static int next;
   /** Caméra imposée par le scénario : [position, point visé] ou null. */
   public static Vec3[] camera;

   private Harness() {}

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
         HeraliszPlague.LOGGER.error("Scénario illisible : {}", FILE, ex);
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      String name = "harness";
      if (mc.getLevelSource().levelExists(name)) {
         mc.createWorldOpenFlows().loadLevel(e.getScreen(), name);
      } else {
         GameRules rules = new GameRules();
         rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
         rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
         rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
         LevelSettings settings = new LevelSettings(name, GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT);
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
         startTick = mc.level.getGameTime() + 60;   // laisse le monde se charger
         return;
      }
      long t = mc.level.getGameTime() - startTick;
      while (next < STEPS.size() && STEPS.get(next).get("t").getAsLong() <= t) {
         run(mc, STEPS.get(next++));
      }
   }

   private static void run(Minecraft mc, JsonObject s) {
      if (s.has("cmd")) {
         String cmd = s.get("cmd").getAsString();
         MinecraftServer server = mc.getSingleplayerServer();
         server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
            var src = (p != null ? p.createCommandSourceStack() : server.createCommandSourceStack()).withPermission(4);
            server.getCommands().performPrefixedCommand(src, cmd);
         });
      }
      if (s.has("cam")) {
         JsonArray a = s.getAsJsonArray("cam");
         camera = new Vec3[] {new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()),
            new Vec3(a.get(3).getAsDouble(), a.get(4).getAsDouble(), a.get(5).getAsDouble())};
      }
      if (s.has("camOff")) camera = null;
      if (s.has("tpBoss")) {
         // se placer près du repère du boss de la salle la plus proche (pour faire partie du rituel)
         MinecraftServer server = mc.getSingleplayerServer();
         server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
            if (p == null) return;
            var h = fr.heralisz.plague.dungeon.StationHallBE.nearest(p.serverLevel(), p.blockPosition(), 400);
            if (h == null) {
               HeraliszPlague.LOGGER.warn("Banc d'essai : aucune salle près de {}", p.blockPosition());
               return;
            }
            HeraliszPlague.LOGGER.info("Banc d'essai : salle en {}, boss en {}", h.getBlockPos(), h.bossPos());
            var b = h.bossPos();
            p.teleportTo(p.serverLevel(), b.getX() + 0.5, b.getY() + 6, b.getZ() + 14.5, 180F, 10F);   // face au bassin
         });
      }
      if (s.has("hud")) mc.options.hideGui = !s.get("hud").getAsBoolean();
      if (s.has("shot")) {
         Screenshot.grab(mc.gameDirectory, "harness_" + s.get("shot").getAsString() + ".png", mc.getMainRenderTarget(), msg -> {});
      }
      if (s.has("quit")) mc.stop();
   }
}
