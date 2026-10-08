package com.dayzhotbar.harness;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Banc d'essai de la barre (client de dev seulement, mod a part jamais livre ; actif avec -Ddayzhotbar.harness=scenario.json,
 * voir {@code python tools/lancer.py client src/dayzhotbar <scenario>}) : ouvre un monde plat « hotbar_harness »
 * (commandes permises), joue les etapes, puis quitte.
 * <p>Etapes : {"t": ticks depuis l'etape precedente, "cmd": "...", "slot": 0..8, "shot": "nom", "view": "fp|back|front",
 * "hud": bool, "guiScale": n, "zbHud": "partie|aucun" (HUD du mode zombies des matchs, par reflexion), "combat": secondes
 * (indicateur EN COMBAT), "spectator": bool (spectateur de match), "log": true,
 * "quit": true}. Captures dans run/screenshots/hotbar_&lt;nom&gt;.png.
 */
@Mod("dayzhotbar_harness")
public final class HotbarHarness {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String FILE = System.getProperty("dayzhotbar.harness");
    private final List<JsonObject> steps = new ArrayList<>();
    private final List<Long> at = new ArrayList<>();
    private boolean launched;
    private long start = -1;
    private int next;

    public HotbarHarness() {
        if (FILE != null) {
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    @SubscribeEvent
    public void onTitle(ScreenEvent.Init.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.getScreen() instanceof AccessibilityOnboardingScreen) {
            mc.options.onboardAccessibility = false;
            mc.options.save();
            mc.execute(() -> mc.setScreen(new TitleScreen()));
            return;
        }
        if (launched || !(e.getScreen() instanceof TitleScreen)) {
            return;
        }
        launched = true;
        // Le banc ne doit jamais s'arreter sur l'ecran de pause quand la fenetre perd le focus.
        mc.options.pauseOnLostFocus = false;
        try {
            long t = 0;
            for (JsonElement el : JsonParser.parseString(Files.readString(Path.of(FILE))).getAsJsonObject().getAsJsonArray("steps")) {
                JsonObject o = el.getAsJsonObject();
                t += o.has("t") ? o.get("t").getAsLong() : 0;
                steps.add(o);
                at.add(t);
            }
        } catch (Exception ex) {
            LOG.error("[hotbar] scenario illisible : {}", FILE, ex);
            return;
        }
        String name = "hotbar_harness";
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
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
            return;
        }
        long now = mc.level.getGameTime();
        if (start < 0) {
            start = now + 60;
        }
        while (next < steps.size() && now - start >= at.get(next)) {
            JsonObject s = steps.get(next);
            LOG.info("[hotbar] etape {}/{} : {}", next + 1, steps.size(), s);
            next++;
            try {
                run(mc, s);
            } catch (RuntimeException ex) {
                LOG.error("[hotbar] etape en erreur : {}", s, ex);
            }
        }
    }

    private static void run(Minecraft mc, JsonObject s) {
        if (s.has("cmd")) {
            mc.player.connection.sendCommand(s.get("cmd").getAsString());
        }
        if (s.has("slot")) {
            mc.player.getInventory().selected = s.get("slot").getAsInt();
        }
        if (s.has("zbHud")) {
            zombies(mc, s.get("zbHud").getAsString());
        }
        if (s.has("combat")) {
            // indicateur « EN COMBAT » du moteur des matchs (en bas a droite, au-dessus des munitions TaCZ)
            call("fr.heralisz.matchs.moteur.client.MoteurClient", "combat", new Class<?>[]{int.class}, s.get("combat").getAsInt());
        }
        if (s.has("spectator")) {
            // spectateur d'un match : le moteur cache la barre vanilla, la notre doit suivre
            call("fr.heralisz.matchs.moteur.client.MoteurClient", "spectateur", new Class<?>[]{boolean.class, String.class, int.class, int.class},
                    s.get("spectator").getAsBoolean(), "Joueur", 0x6FA8FF, 3);
        }
        if (s.has("guiScale")) {
            mc.options.guiScale().set(s.get("guiScale").getAsInt());
            mc.resizeDisplay();
        }
        if (s.has("view")) {
            String v = s.get("view").getAsString();
            mc.options.setCameraType(v.equals("back") ? CameraType.THIRD_PERSON_BACK : v.equals("front") ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
        }
        if (s.has("hud")) {
            mc.options.hideGui = !s.get("hud").getAsBoolean();
        }
        if (s.has("log")) {
            LOG.info("[hotbar] fenetre {} x {} (interface {} x {}, echelle {}), case {}, main : {}", mc.getWindow().getWidth(),
                    mc.getWindow().getHeight(), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                    mc.getWindow().getGuiScale(), mc.player.getInventory().selected, mc.player.getMainHandItem());
        }
        if (s.has("shot")) {
            Screenshot.grab(mc.gameDirectory, "hotbar_" + s.get("shot").getAsString() + ".png", mc.getMainRenderTarget(), msg -> {
            });
        }
        if (s.has("quit")) {
            mc.stop();
        }
    }

    private static void call(String cls, String method, Class<?>[] types, Object... args) {
        try {
            Method m = Class.forName(cls).getMethod(method, types);
            m.invoke(null, args);
        } catch (ReflectiveOperationException | LinkageError ex) {
            LOG.warn("[hotbar] {}.{} indisponible : {}", cls, method, ex.toString());
        }
    }

    /** HUD du mode zombies (heralisz_matchs) pilote cote client, comme l'etape "zbHud" du banc des matchs. */
    private static void zombies(Minecraft mc, String what) {
        try {
            Class<?> c = Class.forName("fr.heralisz.matchs.zombies.client.ZombiesClient");
            Method hud = c.getDeclaredMethod("hud", Minecraft.class, JsonElement.class, JsonObject.class);
            hud.setAccessible(true);
            hud.invoke(null, mc, new JsonPrimitive(what), new JsonObject());
        } catch (ReflectiveOperationException | LinkageError ex) {
            LOG.warn("[hotbar] HUD zombies indisponible : {}", ex.toString());
        }
    }
}
