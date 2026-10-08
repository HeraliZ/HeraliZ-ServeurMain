package fr.heralisz.tags.dev;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import fr.heralisz.tags.HeraliszTags;
import fr.heralisz.tags.catalogue.TagCatalog;
import fr.heralisz.tags.catalogue.TagDef;
import fr.heralisz.tags.client.ClientTags;
import fr.heralisz.tags.client.TagPlacer;
import fr.heralisz.tags.client.render.Decal;
import fr.heralisz.tags.client.render.DecalBaker;
import fr.heralisz.tags.client.render.DecalRenderer;
import fr.heralisz.tags.client.gui.EcranTags;
import fr.heralisz.tags.client.gui.IndexScreen;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
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
 * Banc d'essai des tags (client de dev seulement, active par -Dheralisz.tags.harness=scenario.json, soit
 * ./gradlew runClient -Pharness=…) : ouvre le monde plat « tags_harness », joue un scenario, puis quitte.
 * Moteur d'etapes repris de fr.heraliz.crates.dev.CrateHarness.
 * <p>Etapes generiques : {"t": ticks depuis l'etape precedente, "cmd": "...", "view": "fp|back|front", "hud": false,
 * "shot": "nom", "click": [x, y], "mouse": [x, y], "key": "escape|…" ou ["backspace", 6], "type": "texte",
 * "scroll": [x, y, delta], "drag": [x0, y0, x1, y1], "chat": true, "log": true, "wait": "IndexScreen|none", "fps": "etiquette",
 * "recharger": true (F3+T), "shader": "texte|peinture" (rendu v1 force / shader v2), "gpu": true (temps GPU de la
 * passe dans les lignes "fps"), "quit": true}.
 * <p>Etapes propres : "ecran": "index|roue" (ouvre l'ecran), "faux": n (les n premiers tags du catalogue debloques
 * en local, roue = 4 premiers), "roueLocale": ["id", "", ...] (roue locale), "tag": {"id": "...", "ecran": [sx, sy]
 * (rayon par ce point d'ecran, coordonnees d'interface) | "point": [x, y, z], "face": "north" | "bloc": [x, y, z],
 * "face": "north" (centre de cette face), "local": true (pose locale sans serveur, pour les scenes de performance),
 * "angle": roulis en degres (local)}. "fps" journalise aussi les decalques dessines / testes et la derniere cuisson.
 * Captures dans run/screenshots/tags_&lt;nom&gt;.png.
 */
@Mod.EventBusSubscriber(modid = HeraliszTags.MODID, value = Dist.CLIENT)
public final class TagsHarness {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String FILE = System.getProperty("heralisz.tags.harness");
    private static final String MONDE = "tags_harness";
    private static boolean launched;
    private static long startTick = -1;
    private static final List<JsonObject> STEPS = new ArrayList<>();
    private static final List<Long> AT = new ArrayList<>();
    private static int next, waited;
    private static long derniereAlloc = -1, dernieresImages, dernierePasseNs, dernierGpuNs, dernieresMesuresGpu;

    private TagsHarness() {
    }

    /** Octets alloues par le thread courant depuis son demarrage (−1 si la JVM ne le mesure pas). */
    private static long allocationThread() {
        try {
            java.lang.management.ThreadMXBean mx = java.lang.management.ManagementFactory.getThreadMXBean();
            if (mx instanceof com.sun.management.ThreadMXBean sun && sun.isThreadAllocatedMemorySupported()) {
                return sun.getThreadAllocatedBytes(Thread.currentThread().getId());
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    public static boolean actif() {
        return FILE != null;
    }

    @SubscribeEvent
    public static void onTitle(ScreenEvent.Init.Post e) {
        if (FILE == null || launched) return;
        if (e.getScreen() instanceof AccessibilityOnboardingScreen) {
            // premier lancement d'un dossier run/ vierge : l'ecran d'accueil accessibilite precede l'ecran titre
            Minecraft mc = Minecraft.getInstance();
            mc.options.onboardAccessibility = false;
            mc.options.save();
            mc.setScreen(new TitleScreen(true));
            return;
        }
        if (!(e.getScreen() instanceof TitleScreen)) return;
        launched = true;
        try {
            long t = 0;
            // chemin relatif : par rapport au dossier du mod (le client tourne dans run/)
            Path chemin = Path.of(FILE);
            if (!chemin.isAbsolute() && !Files.exists(chemin)) chemin = Path.of("..").resolve(FILE);
            for (JsonElement el : JsonParser.parseString(Files.readString(chemin)).getAsJsonObject().getAsJsonArray("steps")) {
                JsonObject o = el.getAsJsonObject();
                t += o.has("t") ? o.get("t").getAsLong() : 0;
                STEPS.add(o);
                AT.add(t);
            }
        } catch (Exception ex) {
            LOG.error("[tags] scenario illisible : {}", FILE, ex);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // mesures de fps significatives : pas de synchronisation verticale ni de plafond d'images
        mc.options.enableVsync().set(false);
        mc.options.framerateLimit().set(260);
        mc.getWindow().updateVsync(false);
        mc.getWindow().setFramerateLimit(260);
        if (mc.getLevelSource().levelExists(MONDE)) {
            mc.createWorldOpenFlows().loadLevel(e.getScreen(), MONDE);
        } else {
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            LevelSettings settings = new LevelSettings(MONDE, GameType.CREATIVE, false, net.minecraft.world.Difficulty.PEACEFUL, true, rules,
                    WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(MONDE, settings, new WorldOptions(1234L, false, false),
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

    /** Les etapes continuent sous nos ecrans et le chat (pas sous l'ecran de pause ni de chargement). */
    private static boolean stepsAllowed(Screen screen) {
        return screen == null || screen instanceof EcranTags || screen instanceof ChatScreen;
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
            case "j" -> GLFW.GLFW_KEY_J;
            default -> GLFW.GLFW_KEY_UNKNOWN;
        };
    }

    private static void run(Minecraft mc, JsonObject s) {
        if (s.has("cmd")) mc.player.connection.sendCommand(s.get("cmd").getAsString());
        if (s.has("sneak")) mc.options.keyShift.setDown(s.get("sneak").getAsBoolean());
        if (s.has("chat")) mc.setScreen(new ChatScreen(""));
        if (s.has("faux")) {
            int n = Math.max(0, Math.min(TagCatalog.taille(), s.get("faux").getAsInt()));
            List<String> ids = new ArrayList<>();
            for (TagDef d : TagCatalog.tous().subList(0, n)) ids.add(d.id());
            List<String> roue = new ArrayList<>();
            for (int i = 0; i < 4; i++) roue.add(i < ids.size() ? ids.get(i) : "");
            ClientTags.etat(ids, roue);
            if (mc.screen instanceof IndexScreen is) is.rafraichir();
            LOG.info("[tags] faux etat : {} tags debloques", n);
        }
        if (s.has("ecran")) {
            String ecran = s.get("ecran").getAsString();
            switch (ecran) {
                case "index" -> mc.setScreen(new IndexScreen());
                case "roue" -> mc.setScreen(new fr.heralisz.tags.client.gui.WheelScreen());
                default -> LOG.warn("[tags] ecran inconnu : {}", ecran);
            }
        }
        if (s.has("roueLocale")) {
            List<String> roue = new ArrayList<>();
            for (JsonElement el : s.getAsJsonArray("roueLocale")) roue.add(el.getAsString());
            ClientTags.etat(new ArrayList<>(ClientTags.debloques()), roue);
            if (mc.screen instanceof IndexScreen is) is.rafraichir();
        }
        if (s.has("tag")) poserTag(mc, s.getAsJsonObject("tag"));
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
                LOG.warn("[tags] souris non deplacee", ex);
            }
        }
        Screen screen = mc.screen;
        if (screen != null) {
            if (s.has("click")) {
                // [x, y] ou [x, y, bouton] (0 gauche, 1 droit)
                var a = s.getAsJsonArray("click");
                double x = a.get(0).getAsDouble(), y = a.get(1).getAsDouble();
                int bouton = a.size() > 2 ? a.get(2).getAsInt() : 0;
                screen.mouseClicked(x, y, bouton);
                screen.mouseReleased(x, y, bouton);
                LOG.info("[tags] clic interface {} {} (bouton {})", x, y, bouton);
            }
            if (s.has("drag")) {
                // glisser-deposer : [x0, y0, x1, y1]
                var a = s.getAsJsonArray("drag");
                double x0 = a.get(0).getAsDouble(), y0 = a.get(1).getAsDouble(), x1 = a.get(2).getAsDouble(), y1 = a.get(3).getAsDouble();
                screen.mouseClicked(x0, y0, 0);
                screen.mouseDragged((x0 + x1) / 2, (y0 + y1) / 2, 0, (x1 - x0) / 2, (y1 - y0) / 2);
                screen.mouseDragged(x1, y1, 0, (x1 - x0) / 2, (y1 - y0) / 2);
                screen.mouseReleased(x1, y1, 0);
                LOG.info("[tags] glisser {} {} -> {} {}", x0, y0, x1, y1);
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
                LOG.info("[tags] ecran {} ({} x {}, echelle {})", screen.getClass().getSimpleName(), screen.width, screen.height,
                        mc.getWindow().getGuiScale());
                for (var child : screen.children()) {
                    if (child instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                        LOG.info("[tags]   {} '{}' {} {} {} {}", w.getClass().getSimpleName(), w.getMessage().getString(),
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
        if (s.has("shot")) {
            Screenshot.grab(mc.gameDirectory, "tags_" + s.get("shot").getAsString() + ".png", mc.getMainRenderTarget(), msg -> {});
        }
        if (s.has("fps")) {
            // allocation du thread de rendu par image depuis la mesure precedente (le tick client tourne sur ce thread)
            long alloc = allocationThread();
            long images = DecalRenderer.images, passeNs = DecalRenderer.passeNs;
            long nImages = images - dernieresImages;
            long parImage = alloc >= 0 && derniereAlloc >= 0 && nImages > 0 ? (alloc - derniereAlloc) / nImages : -1;
            // temps CPU de la passe des tags par image (ns), moyenne depuis la mesure precedente
            long passeParImage = nImages > 0 ? (passeNs - dernierePasseNs) / nImages : -1;
            derniereAlloc = alloc;
            dernieresImages = images;
            dernierePasseNs = passeNs;
            // temps GPU de la passe des tags par image (ns), si "gpu": true
            long nGpu = DecalRenderer.gpuMesures - dernieresMesuresGpu;
            long gpuParImage = nGpu > 0 ? (DecalRenderer.gpuNs - dernierGpuNs) / nGpu : -1;
            long gpuMin = DecalRenderer.gpuMinNs == Long.MAX_VALUE ? -1 : DecalRenderer.gpuMinNs;
            DecalRenderer.gpuMinNs = Long.MAX_VALUE;
            dernierGpuNs = DecalRenderer.gpuNs;
            dernieresMesuresGpu = DecalRenderer.gpuMesures;
            LOG.info("[tags] FPS {} : {} (tags actifs : {}, decalques dessines {} / testes {} en {} lot(s) sur {}, {} reconstructions, passe tags {} ns CPU / {} ns GPU par image (GPU min {} ns, shader {}), derniere cuisson {} triangles en {} µs, {} o alloues par image)",
                    s.get("fps").getAsString(), mc.getFps(), ClientTags.actifs().size(), DecalRenderer.dessines, DecalRenderer.testes,
                    DecalRenderer.lotsDessines, DecalRenderer.lotsTotal, DecalRenderer.reconstructions, passeParImage, gpuParImage, gpuMin,
                    DecalRenderer.forcerTexte || fr.heralisz.tags.client.render.PeintureShader.obtenir() == null ? "rendertype_text (v1)" : "peinture (v2)",
                    DecalBaker.derniersTriangles, DecalBaker.derniereDureeNs / 1000, parImage);
        }
        if (s.has("slot")) mc.player.getInventory().selected = s.get("slot").getAsInt();
        if (s.has("shader")) {
            // "texte" = rendu v1 force (rendertype_text), "peinture" = shader v2 : comparaison de cout a scene egale
            DecalRenderer.forcerTexte = s.get("shader").getAsString().equals("texte");
            LOG.info("[tags] shader des decalques : {}", DecalRenderer.forcerTexte ? "rendertype_text (v1)" : "peinture (v2)");
        }
        if (s.has("gpu")) DecalRenderer.mesureGpu = s.get("gpu").getAsBoolean();
        if (s.has("vider")) {
            ClientTags.vider();
            LOG.info("[tags] tags actifs vides");
        }
        if (s.has("recharger")) {
            // F3+T : rechargement des ressources (atlas recharge, decalques recuits)
            mc.reloadResourcePacks();
            LOG.info("[tags] rechargement des ressources demande");
        }
        if (s.has("quit")) mc.stop();
    }

    /**
     * Etape "tag" : determine la face visee (point d'ecran, point monde ou centre d'une face de bloc) puis pose par le
     * serveur (TagC2S) ou en local ("local": true : cuisson directe, id negatif).
     */
    private static void poserTag(Minecraft mc, JsonObject t) {
        String id = t.has("id") ? t.get("id").getAsString() : ClientTags.roue(0);
        boolean local = t.has("local") && t.get("local").getAsBoolean();
        float angle = t.has("angle") ? t.get("angle").getAsFloat() : 0f;
        Vec3 point = null;
        Direction face = null;
        if (t.has("ecran")) {
            var a = t.getAsJsonArray("ecran");
            BlockHitResult hr = TagPlacer.viserEcran(a.get(0).getAsDouble(), a.get(1).getAsDouble());
            if (hr == null) {
                LOG.warn("[tags] pose de '{}' : rien de vise par le point d'ecran", id);
                return;
            }
            point = hr.getLocation();
            face = hr.getDirection();
        } else if (t.has("point")) {
            var a = t.getAsJsonArray("point");
            point = new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
            face = Direction.byName(t.has("face") ? t.get("face").getAsString() : "north");
        } else if (t.has("bloc")) {
            var a = t.getAsJsonArray("bloc");
            face = Direction.byName(t.has("face") ? t.get("face").getAsString() : "north");
            if (face == null) face = Direction.NORTH;
            Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
            point = new Vec3(a.get(0).getAsInt() + 0.5, a.get(1).getAsInt() + 0.5, a.get(2).getAsInt() + 0.5).add(n.scale(0.5));
        } else {
            BlockHitResult hr = TagPlacer.viser();
            if (hr == null) {
                LOG.warn("[tags] pose de '{}' : rien de vise", id);
                return;
            }
            point = hr.getLocation();
            face = hr.getDirection();
        }
        if (face == null) face = Direction.NORTH;
        if (local) {
            ClientTags.TagActif actif = TagPlacer.local(id, point, face, angle);
            LOG.info("[tags] pose locale de '{}' en {} face {} : {} triangles ({} µs)", id, point, face,
                    actif != null && actif.rendu instanceof Decal d ? d.triangles : 0, DecalBaker.derniereDureeNs / 1000);
        } else {
            boolean ok = TagPlacer.taguer(id, new BlockHitResult(point, face, BlockPos.containing(point.subtract(Vec3.atLowerCornerOf(face.getNormal()).scale(0.01))), false));
            LOG.info("[tags] pose de '{}' en {} face {} : {}", id, point, face, ok ? "envoyee" : "refusee");
        }
    }
}
