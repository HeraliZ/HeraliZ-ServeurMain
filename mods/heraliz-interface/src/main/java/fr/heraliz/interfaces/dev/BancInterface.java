package fr.heraliz.interfaces.dev;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.interfaces.HeralizInterface;
import fr.heraliz.interfaces.InterfaceConfig;
import fr.heraliz.interfaces.client.EcranInformations;
import fr.heraliz.interfaces.client.EcranPause;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Banc d'essai du menu Echap (client de dev seulement, actif avec -Dheraliz.interface.harness=scenario.json, soit
 * python tools/lancer.py client src/heraliz-interface scenario.json) : langue du client, monde plat « interface_banc »
 * (creatif, commandes), scenario, puis sortie. L'horloge du scenario compte les ticks du client (elle avance aussi
 * quand le monde solo est en pause derriere le menu).
 * <p>Etapes : {"t": ticks depuis l'etape precedente, "cmd": "...", "pause": true (Echap : Minecraft#pauseGame(false)),
 * "pauseF3": true (F3+Echap : pause sans menu), "sousClasse": true (un « autre mod » ouvre sa sous-classe de
 * PauseScreen), "souris": [x, y] (coordonnees d'interface), "survol": "LIBELLE" (souris au centre du bouton),
 * "clic": [x, y] | "clicLibelle": "LIBELLE", "touche": "escape|tab|enter|left|right", "molette": [x, y, delta],
 * "onglet": n (ecran Informations), "gui": n (echelle d'interface des options, 0 = auto), "config": {"echelle_auto":
 * false, "flou": true, "boutique_lien": "..."}, "fenetre": [l, h], "panne": true (exception au rendu : retour au
 * vanilla), "etat": true (journal de l'ecran, des widgets et de l'alignement), "shot": "nom", "hud": false, "quit": true}.
 * Captures : run/screenshots/interface_&lt;nom&gt;.png ; lignes « [interface-banc] » dans run/logs/latest.log.
 */
@Mod.EventBusSubscriber(modid = HeralizInterface.MODID, value = Dist.CLIENT)
public final class BancInterface {
    private static final String FICHIER = System.getProperty("heraliz.interface.harness");
    /** Monde du banc ; a part quand les mods du serveur sont charges (leurs dimensions restent dans la sauvegarde). */
    private static final String MONDE = net.minecraftforge.fml.ModList.get().isLoaded("heralisz_matchs") ? "interface_banc_mods" : "interface_banc";
    private static boolean lance;
    private static final List<JsonObject> ETAPES = new ArrayList<>();
    private static final List<Long> A = new ArrayList<>();
    private static int suivante;
    private static long horloge = Long.MIN_VALUE;
    private static String langue = "fr_fr";
    /** Banc : force une exception au prochain rendu du menu (test du retour au vanilla). */
    public static volatile boolean panne;

    private BancInterface() {
    }

    public static boolean actif() {
        return FICHIER != null;
    }

    private static void log(String f, Object... a) {
        HeralizInterface.LOG.info("[interface-banc] " + f, a);
    }

    @SubscribeEvent
    public static void titre(ScreenEvent.Init.Post e) {
        if (FICHIER == null || lance) return;
        Minecraft mc = Minecraft.getInstance();
        if (e.getScreen() instanceof AccessibilityOnboardingScreen) {
            mc.options.onboardAccessibility = false;
            mc.options.save();
            mc.setScreen(new TitleScreen(true));
            return;
        }
        if (!(e.getScreen() instanceof TitleScreen)) return;
        if (ETAPES.isEmpty()) {
            try {
                Path chemin = Path.of(FICHIER);
                if (!chemin.isAbsolute() && !Files.exists(chemin)) chemin = Path.of("..").resolve(FICHIER);
                JsonObject racine = JsonParser.parseString(Files.readString(chemin)).getAsJsonObject();
                if (racine.has("langue")) langue = racine.get("langue").getAsString();
                long t = 0;
                for (JsonElement el : racine.getAsJsonArray("steps")) {
                    JsonObject o = el.getAsJsonObject();
                    t += o.has("t") ? o.get("t").getAsLong() : 0;
                    ETAPES.add(o);
                    A.add(t);
                }
            } catch (Exception ex) {
                HeralizInterface.LOG.error("[interface-banc] scenario illisible : {}", FICHIER, ex);
                lance = true;
                return;
            }
        }
        // client dans la langue voulue (francais par defaut) avant d'ouvrir le monde
        if (!langue.equals(mc.options.languageCode)) {
            log("langue {} -> {}", mc.options.languageCode, langue);
            mc.options.languageCode = langue;
            mc.getLanguageManager().setSelected(langue);
            mc.options.save();
            mc.reloadResourcePacks();
            return;
        }
        lance = true;
        mc.options.enableVsync().set(false);
        mc.options.pauseOnLostFocus = false;
        // pas de bulle du tutoriel (« Bougez avec ZQSD ») par-dessus les captures
        mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        if (mc.getLevelSource().levelExists(MONDE)) {
            mc.createWorldOpenFlows().loadLevel(e.getScreen(), MONDE);
        } else {
            GameRules regles = new GameRules();
            regles.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            regles.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            regles.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            LevelSettings reglages = new LevelSettings(MONDE, GameType.CREATIVE, false, net.minecraft.world.Difficulty.PEACEFUL, true, regles,
                    WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(MONDE, reglages, new WorldOptions(20261004L, false, false),
                    reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions());
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (FICHIER == null || e.phase != TickEvent.Phase.END || !lance) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (horloge == Long.MIN_VALUE) horloge = -60; // le monde se pose (3 s)
        horloge++;
        while (suivante < ETAPES.size() && horloge >= A.get(suivante)) {
            JsonObject s = ETAPES.get(suivante++);
            try {
                executer(mc, s);
            } catch (Exception ex) {
                HeralizInterface.LOG.error("[interface-banc] etape en echec : {}", s, ex);
            }
        }
    }

    private static int touche(String nom) {
        return switch (nom) {
            case "escape" -> GLFW.GLFW_KEY_ESCAPE;
            case "tab" -> GLFW.GLFW_KEY_TAB;
            case "enter" -> GLFW.GLFW_KEY_ENTER;
            case "left" -> GLFW.GLFW_KEY_LEFT;
            case "right" -> GLFW.GLFW_KEY_RIGHT;
            case "down" -> GLFW.GLFW_KEY_DOWN;
            default -> GLFW.GLFW_KEY_UNKNOWN;
        };
    }

    private static void souris(Minecraft mc, double x, double y) {
        double k = mc.getWindow().getGuiScale();
        try {
            var fx = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            var fy = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            fx.setAccessible(true);
            fy.setAccessible(true);
            fx.setDouble(mc.mouseHandler, x * k);
            fy.setDouble(mc.mouseHandler, y * k);
        } catch (ReflectiveOperationException ex) {
            HeralizInterface.LOG.warn("[interface-banc] souris non deplacee", ex);
        }
    }

    private static AbstractWidget parLibelle(Screen ecran, String libelle) {
        for (GuiEventListener c : ecran.children()) {
            if (c instanceof AbstractWidget w && w.getMessage().getString().equalsIgnoreCase(libelle)) return w;
        }
        return null;
    }

    private static void executer(Minecraft mc, JsonObject s) {
        if (s.has("cmd")) mc.player.connection.sendCommand(s.get("cmd").getAsString());
        if (s.has("hud")) mc.options.hideGui = !s.get("hud").getAsBoolean();
        if (s.has("config")) config(s.getAsJsonObject("config"));
        if (s.has("gui")) {
            mc.options.guiScale().set(s.get("gui").getAsInt());
            mc.resizeDisplay();
            log("echelle des options = {} -> echelle effective {}", s.get("gui").getAsInt(), mc.getWindow().getGuiScale());
        }
        if (s.has("fenetre")) {
            var a = s.getAsJsonArray("fenetre");
            GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), a.get(0).getAsInt(), a.get(1).getAsInt());
            log("fenetre demandee {} x {}", a.get(0).getAsInt(), a.get(1).getAsInt());
        }
        if (s.has("pause")) mc.pauseGame(false);
        if (s.has("pauseF3")) mc.pauseGame(true);
        if (s.has("sousClasse")) mc.setScreen(new PauseScreen(true) {
        });
        if (s.has("panne")) panne = true;
        if (s.has("souris")) {
            var a = s.getAsJsonArray("souris");
            souris(mc, a.get(0).getAsDouble(), a.get(1).getAsDouble());
        }
        Screen ecran = mc.screen;
        if (ecran != null) {
            if (s.has("survol")) {
                AbstractWidget w = parLibelle(ecran, s.get("survol").getAsString());
                if (w != null) souris(mc, w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0);
                else log("survol : bouton '{}' introuvable", s.get("survol").getAsString());
            }
            if (s.has("clic")) {
                var a = s.getAsJsonArray("clic");
                ecran.mouseClicked(a.get(0).getAsDouble(), a.get(1).getAsDouble(), 0);
                ecran.mouseReleased(a.get(0).getAsDouble(), a.get(1).getAsDouble(), 0);
            }
            if (s.has("clicLibelle")) {
                AbstractWidget w = parLibelle(ecran, s.get("clicLibelle").getAsString());
                if (w != null) {
                    double x = w.getX() + w.getWidth() / 2.0, y = w.getY() + w.getHeight() / 2.0;
                    ecran.mouseClicked(x, y, 0);
                    ecran.mouseReleased(x, y, 0);
                    log("clic sur '{}' -> ecran {}", w.getMessage().getString(), mc.screen == null ? "aucun" : mc.screen.getClass().getSimpleName());
                } else {
                    log("clic : bouton '{}' introuvable", s.get("clicLibelle").getAsString());
                }
            }
            if (s.has("molette")) {
                var a = s.getAsJsonArray("molette");
                ecran.mouseScrolled(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
            }
            if (s.has("touche")) {
                int k = touche(s.get("touche").getAsString());
                ecran.keyPressed(k, 0, 0);
                ecran.keyReleased(k, 0, 0);
            }
            if (s.has("onglet") && ecran instanceof EcranInformations ei) ei.onglet(s.get("onglet").getAsInt());
        }
        if (s.has("etat")) etat(mc);
        if (s.has("shot")) {
            String nom = "interface_" + s.get("shot").getAsString() + ".png";
            Screenshot.grab(mc.gameDirectory, nom, mc.getMainRenderTarget(), m -> {
            });
            log("capture {} ({} x {}, echelle {})", nom, mc.getWindow().getWidth(), mc.getWindow().getHeight(), mc.getWindow().getGuiScale());
        }
        if (s.has("quit")) mc.stop();
    }

    private static void config(JsonObject c) {
        for (String k : c.keySet()) {
            JsonElement v = c.get(k);
            switch (k) {
                case "echelle_auto" -> InterfaceConfig.ECHELLE_AUTO.set(v.getAsBoolean());
                case "flou" -> InterfaceConfig.FLOU.set(v.getAsBoolean());
                case "flou_force" -> InterfaceConfig.FLOU_FORCE.set(v.getAsInt());
                case "boutique_lien" -> InterfaceConfig.BOUTIQUE_LIEN.set(v.getAsString());
                case "discord_lien" -> InterfaceConfig.DISCORD_LIEN.set(v.getAsString());
                case "actif" -> InterfaceConfig.MENU_ACTIF.set(v.getAsBoolean());
                case "sous_titre" -> InterfaceConfig.SOUS_TITRE.set(v.getAsString());
                default -> log("config inconnue : {}", k);
            }
            log("config {} = {}", k, v);
        }
    }

    /** Journal : ecran courant, echelle, widgets, et verification de l'alignement (centre, bords gauches/droits). */
    private static void etat(Minecraft mc) {
        Screen ecran = mc.screen;
        log("ETAT ecran={} fenetre={}x{} echelle={} interface={}x{}", ecran == null ? "aucun" : ecran.getClass().getName(),
                mc.getWindow().getWidth(), mc.getWindow().getHeight(), mc.getWindow().getGuiScale(),
                ecran == null ? 0 : ecran.width, ecran == null ? 0 : ecran.height);
        if (ecran == null) return;
        if (ecran instanceof PauseScreen ps) log("ETAT menu vanilla complet={}", fr.heraliz.interfaces.client.InterfaceClient.menuComplet(ps));
        int gauche = Integer.MAX_VALUE, droite = Integer.MIN_VALUE, n = 0;
        java.util.Set<Integer> bordsG = new java.util.TreeSet<>(), bordsD = new java.util.TreeSet<>();
        for (GuiEventListener c : ecran.children()) {
            if (c instanceof AbstractWidget w) {
                log("  {} '{}' x={} y={} l={} h={} actif={}", w.getClass().getSimpleName(), w.getMessage().getString(), w.getX(), w.getY(),
                        w.getWidth(), w.getHeight(), w.active);
                if (ecran instanceof EcranPause && (w instanceof fr.heraliz.interfaces.kit.MenuLigne || w instanceof fr.heraliz.interfaces.kit.Tuile
                        || w instanceof fr.heraliz.interfaces.kit.Bascule)) {
                    gauche = Math.min(gauche, w.getX());
                    droite = Math.max(droite, w.getX() + w.getWidth());
                    bordsG.add(w.getX());
                    bordsD.add(w.getX() + w.getWidth());
                    n++;
                }
            }
        }
        if (n > 0) {
            double centre = (gauche + droite) / 2.0;
            log("ALIGNEMENT colonne {}..{} centre={} centre ecran={} ecart={} | bords gauches {} | bords droits {}", gauche, droite, centre,
                    ecran.width / 2.0, centre - ecran.width / 2.0, bordsG, bordsD);
        }
    }
}
