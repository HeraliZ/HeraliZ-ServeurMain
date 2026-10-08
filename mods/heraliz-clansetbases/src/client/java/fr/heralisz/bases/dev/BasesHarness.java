package fr.heralisz.bases.dev;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import fr.heralisz.bases.HeraliszBases;
import fr.heralisz.bases.client.BasesClient;
import fr.heralisz.bases.client.ClientBases;
import fr.heralisz.bases.model.Base;
import fr.heralisz.bases.model.BaseVue;
import fr.heralisz.bases.monde.Grille;
import fr.heralisz.bases.network.Packets;
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
import java.util.List;
import java.util.Locale;

/**
 * Banc d'essai des bases (client de dev seulement, active par -Dheralisz.bases.harness=scenario.json, lance par
 * {@code python tools/lancer.py client src/heralisz-bases <scenario.json absolu>}) : ouvre un monde plat
 * « bases_harness » (creatif, commandes permises), joue un scenario puis quitte. Sans la propriete, rien ne se passe.
 * Moteur d'etapes repris de {@code fr.heralisz.clans.dev.ClansHarness}.
 * <p>« t » = ticks depuis l'etape PRECEDENTE (delai relatif). Etapes communes : {"cmd": "..."} (sans /), {"shot": "nom"}
 * (run/screenshots/bases_&lt;nom&gt;.png), {"click": [x, y]}, {"mouse": [x, y]}, {"key": "escape"|["backspace", 3]},
 * {"type": "texte"}, {"wait": "NomEcran|none"}, {"view": "fp|back|front"}, {"hud": false}, {"fps": "etiquette"},
 * {"scroll": [x, y, d]}, {"use": true} (clic droit sur le bloc vise), {"attaque": true} (clic gauche sur le bloc vise),
 * {"regard": [yaw, pitch]}, {"log": true}, {"quit": true}, {"chat": "vider"} (efface le chat avant une capture),
 * {"accroupi": true|false} (touche d'accroupissement tenue), {"sonde": "etiquette"} (hauteur sous plafond, surface et lumiere de la zone du joueur, journalisees).
 * <p>Etapes propres : {"faux": "base|pose|sansbase|sansclan|pillables|pillables_ferme"} (dev/FauxBases),
 * {"ecran": "base|piller"} (BasesClient.ouvrir), {"action": "OUVRIR|ETAT|PLAN|PILLABLES|CREER|ENTRER|SORTIR|DEBLOQUER|PILLER",
 * "arg": "..."} (paquet reel au serveur), {"toast": "INFO|ERREUR|REFUS|BASE_CREEE|SALLE_DEBLOQUEE|ATTAQUEE|PILLAGE_TERMINE|
 * PILLAGE_FIN_PROCHE"} (notice factice), {"hudBase": {"mode": "BASE|PILLAGE|AUCUN", "tag": "NOVA", "zone": "3"}},
 * {"until": "dans_bunkers|hors_bunkers|base_prete|base_existe", "max": ticks} : le scenario (et son horloge) attend la
 * condition (600 ticks au plus par defaut ; la ligne « until ... depasse » est journalisee sinon).
 * <p>Etapes des interfaces : {"tp": "ENTREE|SORTIE|PILLAGE|ANNULEE|FAITE"} (teleportation factice : compte a rebours du
 * HUD), {"salle": "4"} (choisit une salle dans l'ecran BASE ouvert), {"maintien": 0.6, "index": 0} (fige la barre de
 * DEBLOQUER / CREER, ou de PILLER de la carte index, a 60 % pour la capture ; l'action ne part pas).
 * Chaque etape executee est journalisee « [bases] etape i/n ... » ; l'etat client est journalise par {"log": true}.
 * <p>Etapes du correctif du 01/10 : {"utiliser": true|false} (touche « utiliser » tenue / relachee : lancer un C4),
 * {"utiliserObjet": true} (detonateur), {"avancer": true|false} (marche reelle), {"onglet": "permissions"} (onglet de
 * l'ecran BASE), {"position": "etiquette"} (position exacte, sol sous les pieds, vehicule, ecran) ; conditions « until »
 * {@code tp_en_cours}, {@code tp_fini}, {@code ecran_clan}. Le vehicule « collant » qui imite le train :
 * {@link VehiculeCollant} (tag {@value VehiculeCollant#TAG}).
 */
@Mod.EventBusSubscriber(modid = HeraliszBases.MODID, value = Dist.CLIENT)
public final class BasesHarness {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String FILE = System.getProperty("heralisz.bases.harness");
    private static boolean launched;
    private static long startTick = -1, horloge;
    private static final List<JsonObject> STEPS = new ArrayList<>();
    private static final List<Long> AT = new ArrayList<>();
    private static int next, waited;

    private BasesHarness() {
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
        // pas de tutoriel vanilla (« Move with … ») par-dessus les captures
        Minecraft.getInstance().options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        // fenetre sans focus (machine chargee, autre client devant) : pas d'ecran de pause qui bloquerait le scenario
        Minecraft.getInstance().options.pauseOnLostFocus = false;
        try {
            long t = 0;
            for (JsonElement el : JsonParser.parseString(Files.readString(Path.of(FILE))).getAsJsonObject().getAsJsonArray("steps")) {
                JsonObject o = el.getAsJsonObject();
                t += o.has("t") ? o.get("t").getAsLong() : 0;
                STEPS.add(o);
                AT.add(t);
            }
        } catch (Exception ex) {
            LOG.error("[bases] scenario illisible : {}", FILE, ex);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String name = "bases_harness";
        if (mc.getLevelSource().levelExists(name)) {
            mc.createWorldOpenFlows().loadLevel(e.getScreen(), name);
        } else {
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, net.minecraft.world.Difficulty.PEACEFUL, true, rules,
                    WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(4321L, false, false),
                    reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (FILE == null || e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        // banc : aucun toast vanilla (« Nouvelle(s) recette(s) ! » au ramassage) sur les captures ; les toasts du mod
        // sont dessines par BasesHud, pas par ce gestionnaire
        mc.getToasts().clear();
        if (!stepsAllowed(mc.screen)) return;
        // horloge propre au banc (ticks client comptes ici) : le temps du monde saute quand un monde existant se
        // synchronise apres la connexion, ce qui jouait d'un coup les premieres etapes
        long now = ++horloge;
        if (startTick < 0) startTick = now + 40;
        while (next < STEPS.size() && now - startTick >= AT.get(next)) {
            JsonObject step = STEPS.get(next);
            if (step.has("wait") && waited < 200) {
                String want = step.get("wait").getAsString();
                boolean ok = want.equals("none") ? mc.screen == null : mc.screen != null && mc.screen.getClass().getSimpleName().equals(want);
                if (!ok) {
                    waited++;
                    startTick++;
                    return;
                }
            }
            if (step.has("until")) {
                int max = step.has("max") ? step.get("max").getAsInt() : 600;
                if (!condition(mc, step.get("until").getAsString())) {
                    if (waited < max) {
                        waited++;
                        startTick++;
                        return;
                    }
                    LOG.warn("[bases] until {} depasse apres {} ticks", step.get("until").getAsString(), waited);
                }
            }
            waited = 0;
            LOG.info("[bases] etape {}/{} (t={}, jeu={}) : {}", next + 1, STEPS.size(), AT.get(next), now, step);
            run(mc, STEPS.get(next++));
        }
    }

    /**
     * Mesure objective d'une zone (validation du modele) : parcours en largeur, au niveau des pieds, des colonnes d'air
     * ayant la MEME hauteur libre que celle du joueur (les passages de salle, plus bas, arretent le parcours) ; journalise
     * la hauteur sous plafond, la surface, et la lumiere de bloc aux pieds et a hauteur d'yeux (min / moyenne / max, part
     * des cases sous 8). Etape {"sonde": "etiquette"}, une seule fois : aucune allocation par image.
     */
    private static void sonde(Minecraft mc, String etiquette) {
        net.minecraft.world.level.Level lv = mc.level;
        net.minecraft.core.BlockPos depart = mc.player.blockPosition();
        int h = hauteurLibre(lv, depart);
        java.util.ArrayDeque<net.minecraft.core.BlockPos> file = new java.util.ArrayDeque<>();
        java.util.HashSet<Long> vus = new java.util.HashSet<>();
        file.add(depart);
        vus.add(depart.asLong());
        int n = 0, sombresPieds = 0, minP = 99, maxP = 0, minY = 99, maxY = 0, hMin = h, hMax = h;
        long somP = 0, somY = 0;
        int x0 = depart.getX(), x1 = x0, z0 = depart.getZ(), z1 = z0;
        while (!file.isEmpty() && n < 4000) {
            net.minecraft.core.BlockPos p = file.poll();
            n++;
            int lp = lv.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p);
            int ly = lv.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p.above());
            somP += lp;
            somY += ly;
            minP = Math.min(minP, lp);
            maxP = Math.max(maxP, lp);
            minY = Math.min(minY, ly);
            maxY = Math.max(maxY, ly);
            if (lp < 8) sombresPieds++;
            x0 = Math.min(x0, p.getX());
            x1 = Math.max(x1, p.getX());
            z0 = Math.min(z0, p.getZ());
            z1 = Math.max(z1, p.getZ());
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                net.minecraft.core.BlockPos q = p.relative(d);
                if (Math.abs(q.getX() - depart.getX()) > 40 || Math.abs(q.getZ() - depart.getZ()) > 40) continue;
                if (!vus.add(q.asLong())) continue;
                int hq = hauteurLibre(lv, q);
                // salles / hall / couloirs : 7 sous les poutres, 8 sous les caissons ; passages (5) et portes (3) arretent
                if (h >= 6 ? hq >= 6 && Math.abs(hq - h) <= 1 : hq == h) {
                    file.add(q);
                    hMin = Math.min(hMin, hq);
                    hMax = Math.max(hMax, hq);
                }
            }
        }
        LOG.info("[bases] sonde {} : hauteur libre {}-{} blocs, {} cases ({}x{}), lumiere pieds min {} moy {} max {} ({} cases < 8), yeux min {} moy {} max {}",
                etiquette, hMin, hMax, n, x1 - x0 + 1, z1 - z0 + 1, minP, String.format(Locale.ROOT, "%.1f", somP / (double) Math.max(1, n)), maxP,
                sombresPieds, minY, String.format(Locale.ROOT, "%.1f", somY / (double) Math.max(1, n)), maxY);
    }

    /** Nombre de blocs « vides » (sans collision) empiles a partir de p, sol plein exige dessous (0 sinon). */
    private static int hauteurLibre(net.minecraft.world.level.Level lv, net.minecraft.core.BlockPos p) {
        if (lv.getBlockState(p.below()).getCollisionShape(lv, p.below()).isEmpty()) return 0;
        int h = 0;
        while (h < 16 && lv.getBlockState(p.above(h)).getCollisionShape(lv, p.above(h)).isEmpty()) h++;
        return h;
    }

    private static boolean condition(Minecraft mc, String c) {
        BaseVue v = ClientBases.etat();
        return switch (c) {
            case "dans_bunkers" -> mc.level.dimension() == Grille.BUNKERS;
            case "hors_bunkers" -> mc.level.dimension() != Grille.BUNKERS;
            case "base_prete" -> v != null && v.existe() && v.etat() == Base.Etat.PRETE;
            case "base_existe" -> v != null && v.existe();
            case "tp_en_cours" -> ClientBases.teleport() != null;
            case "tp_fini" -> ClientBases.teleport() == null;
            case "ecran_clan" -> mc.screen != null && mc.screen.getClass().getName().startsWith("fr.heralisz.clans");
            default -> true;
        };
    }

    /** Les etapes continuent sous les ecrans du mod, le chat et les conteneurs (pas sous la pause ni le chargement). */
    private static boolean stepsAllowed(Screen screen) {
        return screen == null || screen instanceof ChatScreen || screen.getClass().getName().startsWith("fr.heralisz.bases")
                || screen.getClass().getName().startsWith("fr.heralisz.clans")
                || screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>;
    }

    private static int keyCode(String name) {
        return switch (name) {
            case "space" -> GLFW.GLFW_KEY_SPACE;
            case "enter" -> GLFW.GLFW_KEY_ENTER;
            case "escape" -> GLFW.GLFW_KEY_ESCAPE;
            case "backspace" -> GLFW.GLFW_KEY_BACKSPACE;
            case "tab" -> GLFW.GLFW_KEY_TAB;
            default -> GLFW.GLFW_KEY_UNKNOWN;
        };
    }

    private static void run(Minecraft mc, JsonObject s) {
        if (s.has("cmd")) mc.player.connection.sendCommand(s.get("cmd").getAsString());
        // ---- etapes propres aux bases
        if (s.has("faux")) {
            FauxBases.appliquer(s.get("faux").getAsString());
            LOG.info("[bases] faux etat : {}", s.get("faux").getAsString());
        }
        if (s.has("ecran")) {
            BasesClient.ouvrir(s.get("ecran").getAsString().equalsIgnoreCase("piller") ? Packets.OuvrirS2C.Ecran.PILLER : Packets.OuvrirS2C.Ecran.BASE);
        }
        if (s.has("action")) {
            try {
                Packets.ActionC2S.Genre g = Packets.ActionC2S.Genre.valueOf(s.get("action").getAsString().toUpperCase(Locale.ROOT));
                BasesClient.demander(g, s.has("arg") ? s.get("arg").getAsString() : "");
            } catch (IllegalArgumentException ex) {
                LOG.warn("[bases] action inconnue : {}", s.get("action"));
            }
        }
        if (s.has("toast")) {
            Packets.NoticeS2C.Genre g;
            try {
                g = Packets.NoticeS2C.Genre.valueOf(s.get("toast").getAsString().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                g = Packets.NoticeS2C.Genre.INFO;
            }
            BasesClient.notice(switch (g) {
                case ATTAQUEE -> new Packets.NoticeS2C(g, "notice.heralisz_bases.attaquee", List.of("RVN", "3"), "alerte");
                case SALLE_DEBLOQUEE -> new Packets.NoticeS2C(g, "notice.heralisz_bases.salle_debloquee", List.of("Salle 3", "Room 3"), "succes");
                case BASE_CREEE -> new Packets.NoticeS2C(g, "notice.heralisz_bases.base_prete", List.of(), "succes");
                case PILLAGE_TERMINE -> new Packets.NoticeS2C(g, "notice.heralisz_bases.pillage_termine", List.of("RVN"), "");
                case PILLAGE_FIN_PROCHE -> new Packets.NoticeS2C(g, "notice.heralisz_bases.pillage_fin_proche", List.of("2"), "alerte");
                case ERREUR -> new Packets.NoticeS2C(g, "msg.heralisz_bases.fonds", List.of("10 000 hérals", "4 250 hérals"), "refus");
                case REFUS -> new Packets.NoticeS2C(g, "refus.heralisz_bases.casser", List.of(), "");
                default -> new Packets.NoticeS2C(g, "notice.heralisz_bases.pose_en_cours", List.of(), "");
            });
        }
        if (s.has("hudBase")) {
            JsonObject o = s.getAsJsonObject("hudBase");
            Packets.HudS2C.Mode m;
            try {
                m = Packets.HudS2C.Mode.valueOf(o.has("mode") ? o.get("mode").getAsString().toUpperCase(Locale.ROOT) : "BASE");
            } catch (IllegalArgumentException ex) {
                m = Packets.HudS2C.Mode.BASE;
            }
            ClientBases.setHud(new Packets.HudS2C(m, o.has("tag") ? o.get("tag").getAsString() : "NOVA", 0xB8402E,
                    o.has("zone") ? o.get("zone").getAsString() : "1", m == Packets.HudS2C.Mode.PILLAGE ? System.currentTimeMillis() + 3_600_000 : 0));
        }
        // ---- etapes des interfaces (client/gui)
        if (s.has("tp")) {
            String v = s.get("tp").getAsString().toUpperCase(Locale.ROOT);
            Packets.TeleportS2C t;
            if (v.equals("ANNULEE")) {
                t = new Packets.TeleportS2C(Packets.TeleportS2C.Genre.ENTREE, Packets.TeleportS2C.Etat.ANNULEE, 200, "msg.heralisz_bases.tp_bouge");
            } else if (v.equals("FAITE")) {
                t = new Packets.TeleportS2C(Packets.TeleportS2C.Genre.ENTREE, Packets.TeleportS2C.Etat.FAITE, 0, "");
            } else {
                Packets.TeleportS2C.Genre g;
                try {
                    g = Packets.TeleportS2C.Genre.valueOf(v);
                } catch (IllegalArgumentException ex) {
                    g = Packets.TeleportS2C.Genre.ENTREE;
                }
                t = new Packets.TeleportS2C(g, Packets.TeleportS2C.Etat.DEBUT, 200, "");
            }
            ClientBases.setTeleport(t);
            BasesClient.teleport(t);
        }
        if (s.has("salle") && mc.screen instanceof fr.heralisz.bases.client.gui.BaseScreen bs) bs.choisir(s.get("salle").getAsString());
        if (s.has("maintien")) {
            float f = s.get("maintien").getAsFloat();
            if (mc.screen instanceof fr.heralisz.bases.client.gui.BaseScreen bs) bs.figerMaintien(f);
            else if (mc.screen instanceof fr.heralisz.bases.client.gui.PillerScreen ps) ps.figerMaintien(s.has("index") ? s.get("index").getAsInt() : 0, f);
        }
        if (s.has("regard")) {
            var a = s.getAsJsonArray("regard");
            mc.player.setYRot(a.get(0).getAsFloat());
            mc.player.setXRot(a.get(1).getAsFloat());
        }
        // ---- etapes communes
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
                LOG.warn("[bases] souris non deplacee", ex);
            }
        }
        Screen screen = mc.screen;
        if (screen != null) {
            if (s.has("click")) {
                var a = s.getAsJsonArray("click");
                double x = a.get(0).getAsDouble(), y = a.get(1).getAsDouble();
                screen.mouseClicked(x, y, 0);
                if (!s.has("hold")) screen.mouseReleased(x, y, 0);
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
                for (int i = 0; i < times && mc.screen == screen; i++) {
                    screen.keyPressed(keyCode(name), 0, 0);
                    screen.keyReleased(keyCode(name), 0, 0);
                }
            }
            if (s.has("type")) {
                for (char c : s.get("type").getAsString().toCharArray()) screen.charTyped(c, 0);
            }
        }
        if (s.has("use") && mc.gameMode != null) {
            if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bh);
            } else {
                LOG.warn("[bases] use : aucun bloc vise");
            }
        }
        if (s.has("attaque") && mc.gameMode != null) {
            if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
                mc.gameMode.startDestroyBlock(bh.getBlockPos(), bh.getDirection());
            } else {
                LOG.warn("[bases] attaque : aucun bloc vise");
            }
        }
        if (s.has("view")) {
            String v = s.get("view").getAsString();
            mc.options.setCameraType(v.equals("back") ? CameraType.THIRD_PERSON_BACK : v.equals("front") ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
        }
        if (s.has("hud")) mc.options.hideGui = !s.get("hud").getAsBoolean();
        if (s.has("chat")) mc.gui.getChat().clearMessages(false);
        if (s.has("accroupi")) mc.options.keyShift.setDown(s.get("accroupi").getAsBoolean());
        // touche « utiliser » tenue puis relachee (lancer un C4 : maintenir plus que le temps de preparation)
        if (s.has("utiliser")) {
            boolean b = s.get("utiliser").getAsBoolean();
            mc.options.keyUse.setDown(b);
            if (b) net.minecraft.client.KeyMapping.click(mc.options.keyUse.getKey());
            LOG.info("[bases] touche utiliser {} (objet en main : {})", b ? "tenue" : "relachee", mc.player.getMainHandItem());
        }
        // utilisation immediate de l'objet en main (detonateur)
        if (s.has("utiliserObjet") && mc.gameMode != null) {
            LOG.info("[bases] utilise {} : {}", mc.player.getMainHandItem(), mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
        }
        // marche reelle (touche avancer tenue) : annule un compte a rebours par le mouvement du joueur lui-meme
        if (s.has("avancer")) mc.options.keyUp.setDown(s.get("avancer").getAsBoolean());
        if (s.has("onglet") && mc.screen instanceof fr.heralisz.bases.client.gui.BaseScreen bs) {
            LOG.info("[bases] onglet {} : visible {}, clic {}", s.get("onglet").getAsString(), bs.ongletsVisibles(), bs.cliquerOngletPermissions());
        }
        if (s.has("position")) {
            net.minecraft.core.BlockPos pieds = mc.player.blockPosition();
            LOG.info("[bases] position {} : {} {} {} {} (sol {} = {}), vehicule {}, ecran {}", s.get("position").getAsString(),
                    mc.level.dimension().location(), String.format(Locale.ROOT, "%.2f", mc.player.getX()),
                    String.format(Locale.ROOT, "%.2f", mc.player.getY()), String.format(Locale.ROOT, "%.2f", mc.player.getZ()), pieds.below(),
                    mc.level.getBlockState(pieds.below()), mc.player.getVehicle(), mc.screen == null ? "aucun" : mc.screen.getClass().getSimpleName());
        }
        if (s.has("sonde")) sonde(mc, s.get("sonde").getAsString());
        if (s.has("log")) {
            BaseVue v = ClientBases.etat();
            LOG.info("[bases] etat client : dimension {}, position {}, etat {}, plan {}, hud {}, pillables {}", mc.level.dimension().location(),
                    mc.player.blockPosition(), v == null ? "aucun" : (v.tag() + " existe=" + v.existe() + " " + v.etat() + " salles=" + v.salles()
                            + " perms=" + v.perms() + " banque=" + v.banque() + " dedans=" + v.dansLaBase()),
                    ClientBases.plan() == null ? "absent" : ClientBases.plan().salles.size() + " salles", ClientBases.hud(), ClientBases.pillables().size());
            if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
                LOG.info("[bases] bloc vise {} = {}", bh.getBlockPos(), mc.level.getBlockState(bh.getBlockPos()));
            }
        }
        if (s.has("shot")) {
            Screenshot.grab(mc.gameDirectory, "bases_" + s.get("shot").getAsString() + ".png", mc.getMainRenderTarget(), msg -> {
            });
        }
        if (s.has("fps")) LOG.info("[bases] fps {} : {}", s.get("fps").getAsString(), mc.getFps());
        if (s.has("quit")) mc.stop();
    }
}
