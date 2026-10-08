package fr.heraliz.emote.client.dev;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import fr.heraliz.emote.EmoteRules;
import fr.heraliz.emote.HeralizEmote;
import fr.heraliz.emote.client.catalog.EmoteCatalog;
import io.github.kosmx.emotes.api.events.client.ClientEmoteAPI;
import io.github.kosmx.emotes.executor.emotePlayer.IEmotePlayerEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Banc d'essai HeraliZ (client de dev seulement, jamais dans le jar : build.gradle exclut client/dev) :
 * {@code python tools/lancer.py client src/heraliz-emote <scenario.json>} = runClient -Pharness=... Monde plat
 * « emote_harness » (commandes permises), scenario, puis sortie. Etapes : {"t", "cmd", "shot", "press"/"release" (touche
 * physique : "0", "grave", "h", "b"), "screenkey": "escape", "play": "floss" (emote Heraliz par son id) ou "" (arreter),
 * "status": true (emote en cours ?), "keys": true, "view": "fp|back|front", "chat": true|false, "quit": true}.
 * Cote serveur : {@code /emotetest combat <s>} et {@code /emotetest match 0|1} posent les cles de HeraliZ Matchs
 * ({@code heralisz:combat_jusqua}, {@code heralisz:en_match}). Captures : run/screenshots/emote_&lt;nom&gt;.png.
 */
@Mod.EventBusSubscriber(modid = HeralizEmote.MODID, value = Dist.CLIENT)
public final class EmoteHarness {
    private static final String FILE = System.getProperty("heraliz.emote.harness");
    private static boolean launched;
    private static long startTick = -1;
    /** Horloge du banc en ticks client (avance aussi sous un ecran qui met le jeu en pause, ex. reglages). */
    private static long horloge;
    private static final List<JsonObject> STEPS = new ArrayList<>();
    private static final List<Long> AT = new ArrayList<>();
    private static int next;

    private EmoteHarness() {
    }

    private static void log(String s, Object... a) {
        HeralizEmote.LOGGER.info("[emotetest] " + s, a);
    }

    @SubscribeEvent
    public static void onTitle(ScreenEvent.Init.Post e) {
        if (FILE == null || launched) return;
        if (e.getScreen() instanceof AccessibilityOnboardingScreen) {
            Minecraft mc = Minecraft.getInstance();
            mc.options.onboardAccessibility = false;
            mc.options.save();
            mc.execute(() -> mc.setScreen(new TitleScreen()));
            return;
        }
        if (!(e.getScreen() instanceof TitleScreen)) return;
        launched = true;
        try {
            long t = 0;
            for (JsonElement el : JsonParser.parseString(Files.readString(Path.of(FILE))).getAsJsonObject().getAsJsonArray("steps")) {
                JsonObject o = el.getAsJsonObject();
                t += o.has("t") ? o.get("t").getAsLong() : 0;
                STEPS.add(o);
                AT.add(t);
            }
        } catch (Exception ex) {
            HeralizEmote.LOGGER.error("Scenario illisible : {}", FILE, ex);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String name = "emote_harness";
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
        if (FILE == null || e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Screen screen = mc.screen;
        boolean allowed = screen == null || screen instanceof ChatScreen || screen.getClass().getName().startsWith("fr.heraliz.")
                || screen.getClass().getName().startsWith("io.github.kosmx.") || screen.getClass().getName().startsWith("fr.heraliz.reanimation.");
        if (!allowed) return;
        long now = horloge++;
        if (startTick < 0) startTick = now + 40;
        while (next < STEPS.size() && now - startTick >= AT.get(next)) {
            JsonObject step = STEPS.get(next);
            log("etape {}/{} (t={}) : {}", next + 1, STEPS.size(), AT.get(next), step);
            next++;
            try {
                run(mc, step);
            } catch (RuntimeException ex) {
                HeralizEmote.LOGGER.error("[emotetest] etape en erreur", ex);
            }
        }
    }

    private static int key(String name) {
        return switch (name) {
            case "0" -> GLFW.GLFW_KEY_0;
            case "grave" -> GLFW.GLFW_KEY_GRAVE_ACCENT;
            case "h" -> GLFW.GLFW_KEY_H;
            case "b" -> GLFW.GLFW_KEY_B;
            case "escape" -> GLFW.GLFW_KEY_ESCAPE;
            default -> GLFW.GLFW_KEY_UNKNOWN;
        };
    }

    private static boolean playing(Minecraft mc) {
        return mc.player instanceof IEmotePlayerEntity<?> h && h.isPlayingEmote();
    }

    private static void run(Minecraft mc, JsonObject s) {
        if (s.has("cmd")) mc.player.connection.sendCommand(s.get("cmd").getAsString());
        if (s.has("keys")) {
            for (KeyMapping k : mc.options.keyMappings) {
                if (k.getName().contains("emote")) log("touche {} = {} (defaut {})", k.getName(), k.getKey().getName(), k.getDefaultKey().getName());
            }
        }
        if (s.has("press")) {
            InputConstants.Key k = InputConstants.Type.KEYSYM.getOrCreate(key(s.get("press").getAsString()));
            KeyMapping.click(k);
            KeyMapping.set(k, true);
            // comme le vrai clavier : Emotecraft ouvre sa roue sur l'evenement InputEvent.Key
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.InputEvent.Key(k.getValue(),
                    GLFW.glfwGetKeyScancode(k.getValue()), GLFW.GLFW_PRESS, 0));
        }
        if (s.has("release")) KeyMapping.set(InputConstants.Type.KEYSYM.getOrCreate(key(s.get("release").getAsString())), false);
        if (s.has("screenkey") && mc.screen != null) {
            int k = key(s.get("screenkey").getAsString());
            mc.screen.keyPressed(k, GLFW.glfwGetKeyScancode(k), 0);
        }
        if (s.has("play")) {
            String id = s.get("play").getAsString();
            if (id.isEmpty()) {
                log("arret : {}", ClientEmoteAPI.playEmote(null));
            } else {
                EmoteCatalog.EmoteInfo info = EmoteCatalog.all().get(id);
                UUID uuid = info == null ? null : info.uuid();
                KeyframeAnimation anim = null;
                for (KeyframeAnimation a : ClientEmoteAPI.clientEmoteList()) if (a.getUuid().equals(uuid)) anim = a;
                log("emote {} : {}", id, anim == null ? "INTROUVABLE" : ClientEmoteAPI.playEmote(anim) ? "lancee" : "REFUSEE");
            }
        }
        if (s.has("status")) log("emote en cours : {}, ecran {}", playing(mc), mc.screen == null ? "aucun" : mc.screen.getClass().getSimpleName());
        if (s.has("view")) {
            String v = s.get("view").getAsString();
            mc.options.setCameraType(v.equals("back") ? CameraType.THIRD_PERSON_BACK : v.equals("front") ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
        }
        if (s.has("chat")) {
            if (s.get("chat").getAsBoolean()) mc.setScreen(new ChatScreen(""));
            else if (mc.screen instanceof ChatScreen) mc.setScreen(null);
        }
        // ---- audit des interfaces (2.5.2) : ancien / nouveau menu, echelle d'interface, souris
        if (s.has("guiscale")) {
            mc.options.guiScale().set(s.get("guiscale").getAsInt());
            mc.resizeDisplay();
            log("echelle d'interface {} -> {}", s.get("guiscale").getAsInt(), mc.getWindow().getGuiScale());
        }
        if (s.has("menu")) {
            boolean avant = s.get("menu").getAsString().equals("avant");
            mc.setScreen(avant ? new AncienMenu() : new fr.heraliz.emote.client.gui.EmoteBrowserScreen());
        }
        if (s.has("config")) {
            boolean avant = s.get("config").getAsString().equals("avant");
            mc.setScreen(avant ? new AncienReglages(null) : new fr.heraliz.emote.client.HeralizConfigScreen(null));
        }
        if (s.has("select") && mc.screen instanceof fr.heraliz.emote.client.gui.EmoteBrowserScreen m) {
            EmoteCatalog.EmoteInfo info = EmoteCatalog.all().get(s.get("select").getAsString());
            if (info != null) m.select(info.uuid());
        }
        if (s.has("mouse")) {
            var a = s.getAsJsonArray("mouse");
            double scale = mc.getWindow().getGuiScale();
            try {
                var fx = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
                var fy = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
                fx.setAccessible(true);
                fy.setAccessible(true);
                fx.setDouble(mc.mouseHandler, a.get(0).getAsDouble() * scale);
                fy.setDouble(mc.mouseHandler, a.get(1).getAsDouble() * scale);
            } catch (ReflectiveOperationException ex) {
                log("souris non deplacee : {}", ex.toString());
            }
        }
        if (s.has("click") && mc.screen != null) {
            var a = s.getAsJsonArray("click");
            mc.screen.mouseClicked(a.get(0).getAsDouble(), a.get(1).getAsDouble(), 0);
            mc.screen.mouseReleased(a.get(0).getAsDouble(), a.get(1).getAsDouble(), 0);
        }
        if (s.has("type") && mc.screen != null) {
            for (char c : s.get("type").getAsString().toCharArray()) mc.screen.charTyped(c, 0);
        }
        if (s.has("log") && mc.screen != null) {
            log("ecran {} ({} x {}, echelle {})", mc.screen.getClass().getSimpleName(), mc.screen.width, mc.screen.height, mc.getWindow().getGuiScale());
            for (var child : mc.screen.children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                    log("   {} '{}' {} {} {} {}", w.getClass().getSimpleName(), w.getMessage().getString(), w.getX(), w.getY(), w.getWidth(), w.getHeight());
                }
            }
        }
        if (s.has("shot")) Screenshot.grab(mc.gameDirectory, "emote_" + s.get("shot").getAsString() + ".png", mc.getMainRenderTarget(), msg -> {});
        if (s.has("quit")) mc.stop();
    }

    /** Commandes serveur du banc (monde integre du client de dev). */
    @Mod.EventBusSubscriber(modid = HeralizEmote.MODID)
    public static final class Server {
        private Server() {
        }

        @SubscribeEvent
        public static void commands(RegisterCommandsEvent e) {
            if (FILE == null) return;
            e.getDispatcher().register(Commands.literal("emotetest")
                    .then(Commands.literal("combat").then(Commands.argument("s", IntegerArgumentType.integer(0)).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        int sec = IntegerArgumentType.getInteger(c, "s");
                        if (sec == 0) p.getPersistentData().remove(EmoteRules.COMBAT_JUSQUA);
                        else p.getPersistentData().putLong(EmoteRules.COMBAT_JUSQUA, System.currentTimeMillis() + sec * 1000L);
                        log("cle {} = +{} s", EmoteRules.COMBAT_JUSQUA, sec);
                        return 1;
                    })))
                    .then(Commands.literal("match").then(Commands.argument("v", IntegerArgumentType.integer(0, 1)).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        boolean on = IntegerArgumentType.getInteger(c, "v") == 1;
                        if (on) p.getPersistentData().putBoolean(EmoteRules.EN_MATCH, true);
                        else p.getPersistentData().remove(EmoteRules.EN_MATCH);
                        log("drapeau {} = {}", EmoteRules.EN_MATCH, on);
                        return 1;
                    }))));
        }
    }
}
