package fr.heraliz.crates.dev;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import fr.heraliz.crates.HeralizCrates;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import fr.heraliz.crates.client.gui.CaseOpenScreen;
import fr.heraliz.crates.client.gui.LootEditorScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Banc d'essai des caisses (client de dev seulement, active par -Dheraliz.crates.harness=scenario.json) : ouvre un monde
 * plat, joue un scenario (commandes, clic droit sur un bloc, vue, captures), puis quitte. Sans la propriete, rien ne se passe.
 * <p>Etapes : {"t": ticks depuis l'etape precedente, "cmd": "...", "use": [x, y, z], "view": "fp|back|front",
 * "hud": false, "shot": "nom", "quit": true}. Captures dans run/screenshots/crate_&lt;nom&gt;.png.
 * <p>Interfaces du mod (elles n'arretent pas le scenario) : "sneak": true|false, "click": [x, y] (coordonnees d'interface),
 * "key": "space|enter|escape|backspace|end|home|tab" ou ["backspace", 6] ou ["space", 1, "shift"], "type": "texte",
 * "scroll": [x, y, delta],
 * "chat": true (ouvre le chat), "log": true (ecran et widgets dans la console), "mouse": [x, y] (infobulles),
 * "wait": "CaseOpenScreen|LootEditorScreen|none" (attend cet ecran, 10 s au plus), "fps": "etiquette" (images par
 * seconde dans la console), "slot": n (case de la barre d'objets en main), "vbo": true|false (carte graphique ou non).
 */
@Mod.EventBusSubscriber(modid = HeralizCrates.MODID, value = Dist.CLIENT)
public final class CrateHarness {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String FILE = System.getProperty("heraliz.crates.harness");
    private static boolean launched;
    private static long startTick = -1;
    private static final List<JsonObject> STEPS = new ArrayList<>();
    private static final List<Long> AT = new ArrayList<>();
    private static int next, waited;

    private CrateHarness() {
    }

    @SubscribeEvent
    public static void onTitle(ScreenEvent.Init.Post e) {
        if (FILE == null || launched || !(e.getScreen() instanceof TitleScreen)) return;
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
            LOG.error("Scenario illisible : {}", FILE, ex);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String name = "crate_harness";
        if (mc.getLevelSource().levelExists(name)) {
            mc.createWorldOpenFlows().loadLevel(e.getScreen(), name);
        } else {
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, net.minecraft.world.Difficulty.PEACEFUL, true, rules,
                    WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(1234L, false, false),
                    reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (FILE == null || e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !stepsAllowed(mc.screen)) return;
        long now = mc.level.getGameTime();
        if (startTick < 0) startTick = now + 40;
        while (next < STEPS.size() && now - startTick >= AT.get(next)) {
            JsonObject step = STEPS.get(next);
            // "wait": "NomEcran" : le scenario s'arrete (et son horloge avec) jusqu'a l'ouverture de cet ecran
            if (step.has("wait") && waited < 200) {
                String want = step.get("wait").getAsString();
                boolean ok = want.equals("none") ? mc.screen == null : mc.screen != null && mc.screen.getClass().getSimpleName().equals(want);
                if (!ok) {
                    waited++;
                    startTick++;
                    return;
                }
            }
            waited = 0;
            run(mc, STEPS.get(next++));
        }
    }

    /** Les etapes continuent sous les interfaces du mod et le chat (pas sous l'ecran de pause ni de chargement). */
    private static boolean stepsAllowed(Screen screen) {
        return screen == null || screen instanceof CaseOpenScreen || screen instanceof LootEditorScreen || screen instanceof ChatScreen;
    }

    private static int keyCode(String name) {
        return switch (name) {
            case "space" -> GLFW.GLFW_KEY_SPACE;
            case "enter" -> GLFW.GLFW_KEY_ENTER;
            case "escape" -> GLFW.GLFW_KEY_ESCAPE;
            case "backspace" -> GLFW.GLFW_KEY_BACKSPACE;
            case "end" -> GLFW.GLFW_KEY_END;
            case "home" -> GLFW.GLFW_KEY_HOME;
            case "tab" -> GLFW.GLFW_KEY_TAB;
            default -> GLFW.GLFW_KEY_UNKNOWN;
        };
    }

    private static void run(Minecraft mc, JsonObject s) {
        if (s.has("cmd")) mc.player.connection.sendCommand(s.get("cmd").getAsString());
        if (s.has("sneak")) mc.options.keyShift.setDown(s.get("sneak").getAsBoolean());
        if (s.has("chat")) mc.setScreen(new ChatScreen(""));
        if (s.has("mouse")) {
            // position de la souris en coordonnees d'interface (infobulles), sans dependre du focus de la fenetre
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
                LOG.warn("[crates] souris non deplacee", ex);
            }
        }
        Screen screen = mc.screen;
        if (screen != null) {
            if (s.has("click")) {
                var a = s.getAsJsonArray("click");
                double x = a.get(0).getAsDouble(), y = a.get(1).getAsDouble();
                screen.mouseClicked(x, y, 0);
                screen.mouseReleased(x, y, 0);
                LOG.info("[crates] clic interface {} {}", x, y);
            }
            if (s.has("scroll")) {
                var a = s.getAsJsonArray("scroll");
                screen.mouseScrolled(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
            }
            if (s.has("key")) {
                JsonElement k = s.get("key");
                String name = k.isJsonArray() ? k.getAsJsonArray().get(0).getAsString() : k.getAsString();
                int times = k.isJsonArray() ? k.getAsJsonArray().get(1).getAsInt() : 1;
                int mods = k.isJsonArray() && k.getAsJsonArray().size() > 2 && k.getAsJsonArray().get(2).getAsString().equals("shift")
                        ? GLFW.GLFW_MOD_SHIFT : 0;
                for (int i = 0; i < times && mc.screen == screen; i++) {
                    screen.keyPressed(keyCode(name), 0, mods);
                    screen.keyReleased(keyCode(name), 0, mods);
                }
            }
            if (s.has("type")) {
                for (char c : s.get("type").getAsString().toCharArray()) screen.charTyped(c, 0);
            }
            if (s.has("log")) {
                LOG.info("[crates] ecran {} ({} x {}, echelle {})", screen.getClass().getSimpleName(), screen.width, screen.height,
                        mc.getWindow().getGuiScale());
                for (var child : screen.children()) {
                    if (child instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                        LOG.info("[crates]   {} '{}' {} {} {} {}", w.getClass().getSimpleName(), w.getMessage().getString(),
                                w.getX(), w.getY(), w.getWidth(), w.getHeight());
                    }
                }
            }
        }
        if (s.has("view")) {
            String v = s.get("view").getAsString();
            mc.options.setCameraType(v.equals("back") ? CameraType.THIRD_PERSON_BACK : v.equals("front") ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
        }
        if (s.has("hud")) mc.options.hideGui = !s.get("hud").getAsBoolean();
        if (s.has("use")) {
            var a = s.getAsJsonArray("use");
            BlockPos pos = new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            LOG.info("[crates] clic droit sur {}", pos);
        }
        if (s.has("shot")) {
            Screenshot.grab(mc.gameDirectory, "crate_" + s.get("shot").getAsString() + ".png", mc.getMainRenderTarget(), msg -> {});
        }
        if (s.has("fps")) LOG.info("[crates] FPS {} : {}", s.get("fps").getAsString(), mc.getFps());
        if (s.has("vbo")) fr.heraliz.crates.config.ClientConfig.STATIC_VBO.set(s.get("vbo").getAsBoolean());
        if (s.has("slot")) mc.player.getInventory().selected = s.get("slot").getAsInt();
        if (s.has("quit")) mc.stop();
    }
}
