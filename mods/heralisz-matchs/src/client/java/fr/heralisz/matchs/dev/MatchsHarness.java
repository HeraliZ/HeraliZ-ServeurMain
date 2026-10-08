package fr.heralisz.matchs.dev;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.Modules;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Banc d'essai des matchs (client de dev seulement, active par -Dheralisz.matchs.harness=scenario.json, voir
 * {@code tools/lancer.py client} et {@code tools/matchs_harness.py}) : ouvre un monde plat creatif « matchs_harness »
 * (commandes permises), joue un scenario, puis quitte. Sans la propriete, rien ne se passe. Moteur d'etapes repris de
 * {@code ClansHarness}.
 * <p>Etapes communes : {"t": ticks depuis l'etape PRECEDENTE (delai relatif), "cmd": "...", "shot": "nom",
 * "click": [x, y] (+ "hold": true = sans relacher), "release": [x, y], "mouse": [x, y],
 * "key": "space|enter|escape|backspace|end|home|tab|up|down|left|right|f" ou ["backspace", 6] ou ["space", 1, "shift"],
 * "type": "texte", "wait": "NomSimpleDeLEcran|none" (le scenario et son horloge s'arretent jusqu'a l'ouverture, 200
 * ticks au plus), "view": "fp|back|front", "hud": false, "fps": "etiquette", "scroll": [x, y, delta], "log": true,
 * "use": true (clic droit sur le bloc vise), "quit": true}. Captures dans run/screenshots/matchs_&lt;nom&gt;.png.
 * <p><b>Etapes des modes</b> : chaque classe client de {@link Modules#CLIENTS} peut definir
 * {@code public static void etapesBanc(MatchsHarness.Registre r)} et y appeler {@code r.ajouter("cle", etape)} ;
 * une etape du scenario qui contient cette cle declenche {@link Etape#executer} (avant les etapes communes). Prefixer
 * les cles par le mode ("cw", "duel", "zb", "moteur") pour eviter les collisions ; une cle deja prise est refusee.
 * <p>Chaque etape executee est journalisee ({@code [matchs] etape i/n}) ; toutes les 100 ticks (et a chaque etape
 * "fps") une ligne « cadence » donne les ticks par seconde et les FPS.
 */
@Mod.EventBusSubscriber(modid = HeraliszMatchs.MODID, value = Dist.CLIENT)
public final class MatchsHarness {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String FILE = System.getProperty("heralisz.matchs.harness");
    private static boolean launched;
    private static long startTick = -1;
    private static final List<JsonObject> STEPS = new ArrayList<>();
    private static final List<Long> AT = new ArrayList<>();
    private static int next, waited;
    private static long ticks, cadenceDepuisMs, ticksAuReleve;
    private static final Map<String, Etape> ETAPES = new LinkedHashMap<>();

    private MatchsHarness() {
    }

    /** Etape propre a un mode : recoit la valeur de sa cle et l'etape complete (pour ses options). */
    @FunctionalInterface
    public interface Etape {
        void executer(Minecraft mc, JsonElement valeur, JsonObject etape);
    }

    /** Enregistreur passe aux modules clients. */
    public static final class Registre {
        private final String module;

        private Registre(String module) {
            this.module = module;
        }

        /** Ajoute une etape ; refusee (avertissement) si la cle est deja prise ou est une cle commune. */
        public void ajouter(String cle, Etape etape) {
            if (COMMUNES.contains(cle) || ETAPES.containsKey(cle)) {
                LOG.warn("[matchs] etape de banc '{}' deja prise, refusee pour {}", cle, module);
                return;
            }
            ETAPES.put(cle, etape);
        }
    }

    private static final List<String> COMMUNES = List.of("t", "cmd", "shot", "click", "hold", "release", "mouse", "key", "type", "wait",
            "view", "hud", "fps", "scroll", "log", "use", "quit");

    /** Vrai si le banc est actif (propriete systeme presente). */
    public static boolean actif() {
        return FILE != null;
    }

    /** Ligne « cadence » : ticks par seconde depuis le dernier releve, et FPS du client. */
    private static void cadence(Minecraft mc, String etiquette) {
        long now = System.currentTimeMillis();
        long dt = Math.max(1, now - cadenceDepuisMs), n = ticks - ticksAuReleve;
        LOG.info("[matchs] cadence {} : {} ticks en {} ms = {} ticks/s, {} FPS", etiquette, n, dt,
                String.format(java.util.Locale.ROOT, "%.1f", n * 1000.0 / dt), mc.getFps());
        cadenceDepuisMs = now;
        ticksAuReleve = ticks;
    }

    /** Charge les etapes des modules clients presents. */
    private static void chargerEtapes() {
        for (String c : Modules.CLIENTS) {
            Modules.appeler(List.of(c), "etapesBanc", new Class<?>[]{Registre.class}, new Registre(c));
        }
        LOG.info("[matchs] banc : etapes des modes {}", ETAPES.keySet());
    }

    @SubscribeEvent
    public static void onTitle(ScreenEvent.Init.Post e) {
        if (FILE == null || launched) return;
        // dossier run neuf : l'ecran d'accueil accessibilite remplace l'ecran titre et attend un clic ; on le saute
        if (e.getScreen() instanceof AccessibilityOnboardingScreen) {
            Minecraft mc = Minecraft.getInstance();
            mc.options.onboardAccessibility = false;
            mc.options.save();
            mc.execute(() -> mc.setScreen(new TitleScreen()));
            return;
        }
        if (!(e.getScreen() instanceof TitleScreen)) return;
        launched = true;
        chargerEtapes();
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
        String name = "matchs_harness";
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
        if (mc.player == null || mc.level == null) return;
        if (ticks++ == 0) cadenceDepuisMs = System.currentTimeMillis();
        if (ticks % 100 == 0) cadence(mc, "tick " + ticks);
        if (!stepsAllowed(mc.screen)) return;
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
            LOG.info("[matchs] etape {}/{} (t={}, jeu={}) : {}", next + 1, STEPS.size(), AT.get(next), now, step);
            run(mc, STEPS.get(next++));
        }
    }

    /**
     * Les etapes continuent sous nos ecrans (paquet fr.heralisz.matchs), ceux de Reanimation (fouille, banc
     * Reanimation x moteur) et le chat, pas sous la pause ni le chargement.
     */
    private static boolean stepsAllowed(Screen screen) {
        return screen == null || screen.getClass().getName().startsWith("fr.heralisz.matchs.")
                || screen.getClass().getName().startsWith("fr.heraliz.reanimation.") || screen instanceof ChatScreen;
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
            case "up" -> GLFW.GLFW_KEY_UP;
            case "down" -> GLFW.GLFW_KEY_DOWN;
            case "left" -> GLFW.GLFW_KEY_LEFT;
            case "right" -> GLFW.GLFW_KEY_RIGHT;
            case "f" -> GLFW.GLFW_KEY_F;
            default -> GLFW.GLFW_KEY_UNKNOWN;
        };
    }

    /** Place le curseur (coordonnees d'interface) : survols dessines comme sous une vraie souris. */
    public static void souris(Minecraft mc, double x, double y) {
        double scale = mc.getWindow().getGuiScale();
        try {
            var fx = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            var fy = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            fx.setAccessible(true);
            fy.setAccessible(true);
            fx.setDouble(mc.mouseHandler, x * scale);
            fy.setDouble(mc.mouseHandler, y * scale);
        } catch (ReflectiveOperationException ex) {
            LOG.warn("[matchs] souris non deplacee", ex);
        }
    }

    private static void run(Minecraft mc, JsonObject s) {
        if (s.has("cmd")) mc.player.connection.sendCommand(s.get("cmd").getAsString());
        // ---- etapes des modes
        for (Map.Entry<String, JsonElement> en : s.entrySet()) {
            Etape et = ETAPES.get(en.getKey());
            if (et == null) continue;
            try {
                et.executer(mc, en.getValue(), s);
            } catch (RuntimeException ex) {
                LOG.error("[matchs] etape '{}' en erreur", en.getKey(), ex);
            }
        }
        // ---- etapes communes (moteur des caisses)
        if (s.has("mouse")) {
            var a = s.getAsJsonArray("mouse");
            souris(mc, a.get(0).getAsDouble(), a.get(1).getAsDouble());
        }
        Screen screen = mc.screen;
        if (screen != null) {
            if (s.has("click")) {
                var a = s.getAsJsonArray("click");
                double x = a.get(0).getAsDouble(), y = a.get(1).getAsDouble();
                screen.mouseClicked(x, y, 0);
                if (!s.has("hold")) screen.mouseReleased(x, y, 0);
                LOG.info("[matchs] clic interface {} {}", x, y);
            }
            if (s.has("release")) {
                var a = s.getAsJsonArray("release");
                screen.mouseReleased(a.get(0).getAsDouble(), a.get(1).getAsDouble(), 0);
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
                LOG.info("[matchs] ecran {} ({} x {}, echelle {})", screen.getClass().getSimpleName(), screen.width, screen.height,
                        mc.getWindow().getGuiScale());
                for (var child : screen.children()) {
                    if (child instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                        LOG.info("[matchs]   {} '{}' {} {} {} {}", w.getClass().getSimpleName(), w.getMessage().getString(),
                                w.getX(), w.getY(), w.getWidth(), w.getHeight());
                    }
                }
            }
        } else if (s.has("log")) {
            LOG.info("[matchs] aucun ecran ; joueur en {} {} dans {}", mc.player.position(), mc.player.getYRot(), mc.level.dimension().location());
        }
        // "use": true : clic droit sur le bloc vise, comme en jeu
        if (s.has("use") && mc.gameMode != null) {
            if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bh);
            } else {
                LOG.warn("[matchs] use : aucun bloc vise");
            }
        }
        if (s.has("view")) {
            String v = s.get("view").getAsString();
            mc.options.setCameraType(v.equals("back") ? CameraType.THIRD_PERSON_BACK : v.equals("front") ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
        }
        if (s.has("hud")) mc.options.hideGui = !s.get("hud").getAsBoolean();
        if (s.has("shot")) {
            Screenshot.grab(mc.gameDirectory, "matchs_" + s.get("shot").getAsString() + ".png", mc.getMainRenderTarget(), msg -> {});
        }
        if (s.has("fps")) cadence(mc, s.get("fps").getAsString());
        if (s.has("quit")) mc.stop();
    }
}
