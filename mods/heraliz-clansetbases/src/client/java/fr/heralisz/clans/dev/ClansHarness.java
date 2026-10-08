package fr.heralisz.clans.dev;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.client.ClansClient;
import fr.heralisz.clans.client.ClientClans;
import fr.heralisz.clans.client.gui.AtelierScreen;
import fr.heralisz.clans.client.gui.ClanScreen;
import fr.heralisz.clans.client.gui.PermissionsScreen;
import fr.heralisz.clans.client.gui.perms.EditeurJoueurs;
import fr.heralisz.clans.client.gui.perms.ListeGrades;
import fr.heralisz.clans.client.gui.tabs.PermissionsTab;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.Calque;
import fr.heralisz.clans.model.ClanSnapshot;
import fr.heralisz.clans.model.Formes;
import fr.heralisz.clans.model.Perm;
import fr.heralisz.clans.network.Packets;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
 * Banc d'essai des clans (client de dev seulement, active par -Dheralisz.clans.harness=scenario.json) : ouvre un monde
 * plat « clans_harness », joue un scenario, puis quitte. Sans la propriete, rien ne se passe. Moteur d'etapes repris de
 * fr.heraliz.crates.dev.CrateHarness.
 * <p>Etapes communes : {"t": ticks depuis l'etape precedente, "cmd": "...", "shot": "nom", "click": [x, y],
 * "mouse": [x, y], "key": "space|enter|escape|backspace|end|home|tab|k" ou ["backspace", 6] ou ["space", 1, "shift"],
 * "type": "texte", "wait": "ClanScreen|ClanCreateScreen|AtelierScreen|FlagPanelScreen|none", "view": "fp|back|front", "hud": false,
 * "fps": "etiquette", "scroll": [x, y, delta], "log": true, "use": true (clic droit sur le bloc vise), "quit": true}.
 * Captures dans run/screenshots/clans_&lt;nom&gt;.png. Chaque etape executee est journalisee avec l'heure, le temps de
 * jeu et son contenu ; toutes les 100 ticks (et a chaque etape "fps") une ligne « cadence » donne les ticks par seconde
 * reels et les FPS, pour mesurer les lenteurs du banc.
 * <p>Etapes propres : "faux": "clan|membre|haut_grade|invitations|invitations_6|aucun|drapeau|drapeau_attente|drapeau_libre|
 * drapeau_visiteur" (ClientClans.faux) ou "reel" (retour a l'etat du serveur integre), "ecran": "clan|creation|banniere|
 * atelier|drapeau|permissions|permissions_joueurs|permissions_base" (ClansClient.ouvrir), etapes d'atelier (voir
 * {@link #atelier}), etapes des permissions (voir {@link #perms}), "testPerms": "regles|preparer|nettoyer"
 * ({@code TestPerms}, partie serveur, fil du serveur integre), "onglet": "apercu|membres|banque|
 * quetes|classement|permissions|parametres", "hudZone": {"etat": "capture", "progression": 0.42} ou "hudZone": null,
 * "toast": "invitation|quete_finie|niveau|capture|zone_perdue|ravitaillement|info|erreur" (NoticeS2C factice avec son).
 */
@Mod.EventBusSubscriber(modid = HeraliszClans.MODID, value = Dist.CLIENT)
public final class ClansHarness {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String FILE = System.getProperty("heralisz.clans.harness");
    private static boolean launched;
    private static long startTick = -1;
    private static final List<JsonObject> STEPS = new ArrayList<>();
    private static final List<Long> AT = new ArrayList<>();
    private static int next, waited;
    /** Mesure de cadence : ticks comptes et dernier releve. */
    private static long ticks, cadenceDepuisMs, ticksAuReleve;

    private ClansHarness() {
    }

    /** Ligne « cadence » : ticks par seconde depuis le dernier releve, et FPS du client. */
    private static void cadence(Minecraft mc, String etiquette) {
        long now = System.currentTimeMillis();
        long dt = Math.max(1, now - cadenceDepuisMs), n = ticks - ticksAuReleve;
        LOG.info("[clans] cadence {} : {} ticks en {} ms = {} ticks/s, {} FPS", etiquette, n, dt,
                String.format(java.util.Locale.ROOT, "%.1f", n * 1000.0 / dt), mc.getFps());
        cadenceDepuisMs = now;
        ticksAuReleve = ticks;
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
        String name = "clans_harness";
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
        glisserLent(mc);
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
            LOG.info("[clans] etape {}/{} (t={}, jeu={}) : {}", next + 1, STEPS.size(), AT.get(next), now, step);
            run(mc, STEPS.get(next++));
        }
    }

    /** Les etapes continuent sous nos ecrans et le chat (pas sous l'ecran de pause ni de chargement). */
    private static boolean stepsAllowed(Screen screen) {
        return screen == null || ClansClient.ecranANous(screen) || screen instanceof ChatScreen;
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
            case "k" -> GLFW.GLFW_KEY_K;
            default -> GLFW.GLFW_KEY_UNKNOWN;
        };
    }

    /** Notice factice par genre, avec des arguments realistes et le son maison correspondant. */
    private static Packets.NoticeS2C fauxToast(String genre) {
        Packets.NoticeS2C.Genre g;
        try {
            g = Packets.NoticeS2C.Genre.valueOf(genre);
        } catch (IllegalArgumentException ex) {
            g = Packets.NoticeS2C.Genre.INFO;
        }
        Banniere nova = ClientClans.snapshot() != null ? ClientClans.snapshot().banniere() : ClientClans.fauxNova();
        Banniere rvn = ClientClans.fauxRaven();
        return switch (g) {
            case INVITATION -> new Packets.NoticeS2C(g, List.of("RVN", "Ravenhold"), "invitation", rvn);
            case QUETE_FINIE -> new Packets.NoticeS2C(g, List.of("Fouiller 60 caches"), "quete_finie", null);
            case NIVEAU -> new Packets.NoticeS2C(g, List.of("8"), "niveau_sup", null);
            case CAPTURE -> new Packets.NoticeS2C(g, List.of("Kamenka", "NOVA"), "capture_reussie", nova);
            case ZONE_PERDUE -> new Packets.NoticeS2C(g, List.of("Lesnoi"), "zone_perdue", null);
            case RAVITAILLEMENT -> new Packets.NoticeS2C(g, List.of("Kamenka", "NOVA"), "signal_fusee", nova);
            case ERREUR -> new Packets.NoticeS2C(g, List.of("Fonds insuffisants."), "", null);
            default -> new Packets.NoticeS2C(g, List.of("Yvann a rejoint le clan"), "", null);
        };
    }

    /** Glisser lent en cours dans l'atelier (etape "atelierGlisser" avec "duree") : un pas par tick. */
    private static AtelierScreen glisseEcran;
    private static double gx0, gy0, gx1, gy1, gpx, gpy;
    private static int gDuree, gTick;

    private static void glisserLent(Minecraft mc) {
        if (glisseEcran == null) return;
        if (mc.screen != glisseEcran) {
            glisseEcran = null;
            return;
        }
        gTick++;
        double t = Math.min(1, gTick / (double) gDuree);
        double x = gx0 + (gx1 - gx0) * t, y = gy0 + (gy1 - gy0) * t;
        glisseEcran.mouseDragged(x, y, 0, x - gpx, y - gpy);
        souris(mc, x, y);
        gpx = x;
        gpy = y;
        if (gTick >= gDuree) {
            glisseEcran.mouseReleased(x, y, 0);
            LOG.info("[clans] atelier glisser lent termine : {}", glisseEcran.etat());
            glisseEcran = null;
        }
    }

    /** Place le curseur (coordonnees d'interface) : survols et poignees dessines comme sous une vraie souris. */
    private static void souris(Minecraft mc, double x, double y) {
        double scale = mc.getWindow().getGuiScale();
        try {
            var fx = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            var fy = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            fx.setAccessible(true);
            fy.setAccessible(true);
            fx.setDouble(mc.mouseHandler, x * scale);
            fy.setDouble(mc.mouseHandler, y * scale);
        } catch (ReflectiveOperationException ex) {
            LOG.warn("[clans] souris non deplacee", ex);
        }
    }

    /**
     * Etapes de l'atelier de banniere : "atelierModele": "creeper" (applique un modele), "atelierAjouter": "carre" (ajoute
     * un calque de cette forme au centre), "atelierSelection": n (-1 = rien), "atelierCalque": {"couleur": "#0E1A0C",
     * "w": 16, ...} (champs fusionnes dans le calque selectionne), "atelierGlisser": [x0, y0, x1, y1] (appui, glisser en
     * "pas" etapes (8 par defaut), relachement ; coordonnees d'interface, ou unites du canevas 64 x 96 avec
     * "repere": "toile"), "atelierTouche": "ctrl+z" ou ["delete", 4] (voir AtelierScreen.touche). Chaque etape
     * d'atelier journalise la banniere obtenue ; "log": true journalise aussi la mise en page (rectangles).
     */
    private static void atelier(Minecraft mc, AtelierScreen at, JsonObject s) {
        boolean agi = false;
        if (s.has("atelierModele")) {
            String id = s.get("atelierModele").getAsString();
            if (!at.appliquerModele(id)) LOG.warn("[clans] modele inconnu : {}", id);
            agi = true;
        }
        if (s.has("atelierAjouter")) {
            String cle = s.get("atelierAjouter").getAsString();
            int f = Formes.id(cle);
            if (f < 0) LOG.warn("[clans] forme inconnue : {}", cle);
            else at.ajouterForme(f);
            agi = true;
        }
        if (s.has("atelierSelection")) {
            at.selectionner(s.get("atelierSelection").getAsInt());
            agi = true;
        }
        if (s.has("atelierCalque")) {
            Calque c = at.banniere().calque(at.selection());
            if (c == null) LOG.warn("[clans] atelierCalque : aucun calque selectionne");
            else {
                JsonObject o = c.toJson();
                for (var e : s.getAsJsonObject("atelierCalque").entrySet()) o.add(e.getKey(), e.getValue());
                Calque n = Calque.fromJson(o);
                if (n == null || !at.remplacerSelection(n)) LOG.warn("[clans] atelierCalque refuse : {}", o);
            }
            agi = true;
        }
        if (s.has("atelierGlisser")) {
            var a = s.getAsJsonArray("atelierGlisser");
            double x0 = a.get(0).getAsDouble(), y0 = a.get(1).getAsDouble(), x1 = a.get(2).getAsDouble(), y1 = a.get(3).getAsDouble();
            if (s.has("repere") && s.get("repere").getAsString().equals("toile")) {
                x0 = at.toileX(x0);
                y0 = at.toileY(y0);
                x1 = at.toileX(x1);
                y1 = at.toileY(y1);
            }
            if (s.has("duree")) {
                // glisser lent : la souris avance a chaque tick (images rendues entre deux pas : mesure « atelier ms/image »)
                souris(mc, x0, y0);
                at.mouseClicked(x0, y0, 0);
                glisseEcran = at;
                gx0 = x0;
                gy0 = y0;
                gx1 = x1;
                gy1 = y1;
                gpx = x0;
                gpy = y0;
                gDuree = Math.max(1, s.get("duree").getAsInt());
                gTick = 0;
                LOG.info("[clans] atelier glisser lent {} {} -> {} {} en {} ticks", x0, y0, x1, y1, gDuree);
                return;
            }
            int pas = s.has("pas") ? Math.max(1, s.get("pas").getAsInt()) : 8;
            souris(mc, x0, y0);
            at.mouseClicked(x0, y0, 0);
            double px = x0, py = y0;
            for (int i = 1; i <= pas; i++) {
                double x = x0 + (x1 - x0) * i / pas, y = y0 + (y1 - y0) * i / pas;
                at.mouseDragged(x, y, 0, x - px, y - py);
                px = x;
                py = y;
            }
            at.mouseReleased(x1, y1, 0);
            souris(mc, x1, y1);
            LOG.info("[clans] atelier glisser {} {} -> {} {} (interface)", x0, y0, x1, y1);
            agi = true;
        }
        if (s.has("atelierTouche")) {
            JsonElement k = s.get("atelierTouche");
            String nom = k.isJsonArray() ? k.getAsJsonArray().get(0).getAsString() : k.getAsString();
            int fois = k.isJsonArray() && k.getAsJsonArray().size() > 1 ? k.getAsJsonArray().get(1).getAsInt() : 1;
            for (int i = 0; i < fois && mc.screen == at; i++) {
                if (!at.touche(nom)) LOG.warn("[clans] touche d'atelier inconnue ou sans effet : {}", nom);
            }
            agi = true;
        }
        if (s.has("log")) LOG.info("[clans] {}", at.miseEnPage());
        if (agi || s.has("log")) LOG.info("[clans] atelier banniere : {}", at.etat());
    }

    /** Clic gauche complet (appui + relachement) en coordonnees d'interface, souris placee au meme endroit. */
    private static void clic(Minecraft mc, Screen ecran, double[] c, String quoi) {
        if (c == null) {
            LOG.warn("[clans] {} : cible introuvable", quoi);
            return;
        }
        souris(mc, c[0], c[1]);
        ecran.mouseClicked(c[0], c[1], 0);
        ecran.mouseReleased(c[0], c[1], 0);
        LOG.info("[clans] {} : clic {} {}", quoi, c[0], c[1]);
    }

    /**
     * Etapes des permissions (onglet Permissions de l'ecran du clan ou ecran autonome) : "permsMode": "grade|joueur"
     * (clic sur la bascule), "permsMembre": "Nom" (clic sur sa ligne, liste defilee au besoin), "permsClic": ["PERM",
     * "HERITE|AUTORISE|REFUSE"] (clic sur la pastille du membre choisi), "permsGrade": ["PERM", 0..2] (pastille de la
     * matrice par grade : 0 haut grade, 1 membre, 2 recrue), "permsSurvol": ["PERM", "ETAT"] (souris sur la pastille,
     * pour l'infobulle), "permsDefiler": ["membres|perms|grades", delta] (molette au centre de la liste),
     * "permsPaquet": ["Nom", "PERM", "ETAT"] (paquet envoye tel quel, comme un client modifie : le serveur doit refuser
     * ce qui n'est pas permis). Chaque etape journalise les reglages connus du client.
     */
    private static void perms(Minecraft mc, JsonObject s) {
        Screen ecran = mc.screen;
        if (s.has("permsPaquet")) {
            var a = s.getAsJsonArray("permsPaquet");
            ClanSnapshot snap = ClientClans.snapshot();
            ClanSnapshot.MembreVue cible = null;
            if (snap != null) for (ClanSnapshot.MembreVue m : snap.membres()) if (m.nom().equalsIgnoreCase(a.get(0).getAsString())) cible = m;
            Perm p = Perm.parNom(a.get(1).getAsString());
            Perm.Etat e = Perm.Etat.parNom(a.get(2).getAsString());
            if (cible == null || p == null || e == null || mc.getConnection() == null) LOG.warn("[clans] permsPaquet invalide : {}", a);
            else {
                fr.heralisz.clans.network.ClansNet.toServer(new Packets.PermsJoueurC2S(cible.uuid(), p.bit(),
                        e == Perm.Etat.AUTORISE ? p.bit() : 0, e == Perm.Etat.REFUSE ? p.bit() : 0));
                LOG.info("[clans] permsPaquet envoye : {} {} {}", cible.nom(), p, e);
            }
        }
        if (ecran == null) {
            if (s.has("permsMode") || s.has("permsMembre") || s.has("permsClic") || s.has("permsGrade") || s.has("permsSurvol")
                    || s.has("permsDefiler")) LOG.warn("[clans] etape de permissions sans ecran");
            return;
        }
        if (s.has("permsMode")) {
            if (ecran instanceof ClanScreen cs && cs.tab() instanceof PermissionsTab pt) {
                PermissionsTab.Mode m = s.get("permsMode").getAsString().equalsIgnoreCase("grade") ? PermissionsTab.Mode.GRADE : PermissionsTab.Mode.JOUEUR;
                clic(mc, ecran, pt.centreBascule(m), "permsMode " + m);
            } else LOG.warn("[clans] permsMode : l'onglet Permissions n'est pas ouvert");
        }
        if (s.has("permsMembre")) {
            EditeurJoueurs ed = editeur(ecran);
            clic(mc, ecran, ed == null ? null : ed.centreMembre(s.get("permsMembre").getAsString()), "permsMembre " + s.get("permsMembre").getAsString());
        }
        if (s.has("permsClic")) {
            var a = s.getAsJsonArray("permsClic");
            EditeurJoueurs ed = editeur(ecran);
            Perm p = Perm.parNom(a.get(0).getAsString());
            Perm.Etat e = Perm.Etat.parNom(a.get(1).getAsString());
            clic(mc, ecran, ed == null || p == null || e == null ? null : ed.centreEtat(p, e), "permsClic " + a);
        }
        if (s.has("permsGrade")) {
            var a = s.getAsJsonArray("permsGrade");
            ListeGrades lg = ecran instanceof ClanScreen cs && cs.tab() instanceof PermissionsTab pt ? pt.grades() : null;
            Perm p = Perm.parNom(a.get(0).getAsString());
            clic(mc, ecran, lg == null || p == null ? null : lg.centrePastille(p, a.get(1).getAsInt()), "permsGrade " + a);
        }
        if (s.has("permsSurvol")) {
            var a = s.getAsJsonArray("permsSurvol");
            EditeurJoueurs ed = editeur(ecran);
            Perm p = Perm.parNom(a.get(0).getAsString());
            Perm.Etat e = Perm.Etat.parNom(a.get(1).getAsString());
            double[] c = ed == null || p == null || e == null ? null : ed.centreEtat(p, e);
            if (c != null) souris(mc, c[0], c[1]);
            else LOG.warn("[clans] permsSurvol : cible introuvable {}", a);
        }
        if (s.has("permsDefiler")) {
            var a = s.getAsJsonArray("permsDefiler");
            String quoi = a.get(0).getAsString();
            double delta = a.get(1).getAsDouble();
            double[] c = null;
            if (quoi.equals("grades")) {
                ListeGrades lg = ecran instanceof ClanScreen cs && cs.tab() instanceof PermissionsTab pt ? pt.grades() : null;
                if (lg != null) c = new double[]{lg.getX() + lg.getWidth() / 2.0, lg.getY() + lg.getHeight() / 2.0};
            } else {
                EditeurJoueurs ed = editeur(ecran);
                if (ed != null) c = ed.centreListe(quoi.equals("perms"));
            }
            if (c == null) LOG.warn("[clans] permsDefiler : liste introuvable ({})", quoi);
            else {
                souris(mc, c[0], c[1]);
                ecran.mouseScrolled(c[0], c[1], delta);
            }
        }
        ClanSnapshot snap = ClientClans.snapshot();
        LOG.info("[clans] perms : reglages connus {}", snap == null ? "(pas de clan)" : snap.reglages());
    }

    @javax.annotation.Nullable
    private static EditeurJoueurs editeur(Screen ecran) {
        if (ecran instanceof ClanScreen cs && cs.tab() instanceof PermissionsTab pt) return pt.editeur();
        if (ecran instanceof PermissionsScreen ps) return ps.editeur();
        return null;
    }

    private static void run(Minecraft mc, JsonObject s) {
        if (s.has("cmd")) mc.player.connection.sendCommand(s.get("cmd").getAsString());
        // ---- etapes propres aux clans
        if (s.has("testPerms")) testPerms(mc.getSingleplayerServer(), s.get("testPerms").getAsString());
        if (s.has("faux")) {
            String sc = s.get("faux").getAsString();
            if (sc.equals("reel")) {
                // retour a l'etat reel (serveur integre) : redemande le snapshot
                ClientClans.reel();
                if (mc.getConnection() != null) fr.heralisz.clans.network.ClansNet.toServer(new Packets.SimpleC2S(Packets.SimpleC2S.Genre.OUVRIR, "clan"));
            } else {
                ClientClans.faux(sc);
            }
            LOG.info("[clans] faux etat : {}", sc);
        }
        if (s.has("ecran")) ClansClient.ouvrir(s.get("ecran").getAsString());
        if (s.has("onglet")) {
            String nom = s.get("onglet").getAsString().toUpperCase();
            if (mc.screen instanceof ClanScreen cs) {
                try {
                    cs.onglet(ClanScreen.Onglet.valueOf(nom));
                } catch (IllegalArgumentException ex) {
                    LOG.warn("[clans] onglet inconnu : {}", nom);
                }
            } else {
                LOG.warn("[clans] onglet {} : l'ecran du clan n'est pas ouvert", nom);
            }
        }
        if (s.has("hudZone")) {
            JsonElement hz = s.get("hudZone");
            if (hz.isJsonNull()) ClientClans.setHudZone(null);
            else {
                JsonObject o = hz.getAsJsonObject();
                ClientClans.fauxHudZone(o.has("etat") ? o.get("etat").getAsString() : "capture", o.has("progression") ? o.get("progression").getAsFloat() : 0.42f);
            }
            LOG.info("[clans] hudZone : {}", hz);
        }
        if (s.has("toast")) {
            Packets.NoticeS2C n = fauxToast(s.get("toast").getAsString().toUpperCase());
            fr.heralisz.clans.client.ClientHandlers.notice(n);
        }
        if (s.has("permsMode") || s.has("permsMembre") || s.has("permsClic") || s.has("permsGrade") || s.has("permsSurvol")
                || s.has("permsDefiler") || s.has("permsPaquet")) perms(mc, s);
        if (mc.screen instanceof AtelierScreen at) atelier(mc, at, s);
        else if (s.has("atelierModele") || s.has("atelierAjouter") || s.has("atelierSelection") || s.has("atelierCalque")
                || s.has("atelierGlisser") || s.has("atelierTouche")) {
            LOG.warn("[clans] etape d'atelier ignoree : l'atelier n'est pas ouvert ({})", mc.screen == null ? "aucun ecran" : mc.screen.getClass().getSimpleName());
        }
        // ---- etapes communes (moteur des caisses)
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
                LOG.warn("[clans] souris non deplacee", ex);
            }
        }
        Screen screen = mc.screen;
        if (screen != null) {
            if (s.has("click")) {
                var a = s.getAsJsonArray("click");
                double x = a.get(0).getAsDouble(), y = a.get(1).getAsDouble();
                screen.mouseClicked(x, y, 0);
                if (!s.has("hold")) screen.mouseReleased(x, y, 0);
                LOG.info("[clans] clic interface {} {}", x, y);
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
                LOG.info("[clans] ecran {} ({} x {}, echelle {})", screen.getClass().getSimpleName(), screen.width, screen.height,
                        mc.getWindow().getGuiScale());
                for (var child : screen.children()) {
                    if (child instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                        LOG.info("[clans]   {} '{}' {} {} {} {}", w.getClass().getSimpleName(), w.getMessage().getString(),
                                w.getX(), w.getY(), w.getWidth(), w.getHeight());
                    }
                }
            }
        } else if (s.has("key") && s.get("key").isJsonPrimitive() && s.get("key").getAsString().equals("k")) {
            // touche K sans ecran : ouverture comme en jeu
            ClansClient.ouvrirDepuisTouche();
        }
        // "use": true : clic droit sur le bloc vise (panneau du drapeau, caisse), comme en jeu
        if (s.has("use") && mc.gameMode != null) {
            if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bh);
            } else {
                LOG.warn("[clans] use : aucun bloc vise");
            }
        }
        if (s.has("view")) {
            String v = s.get("view").getAsString();
            mc.options.setCameraType(v.equals("back") ? CameraType.THIRD_PERSON_BACK : v.equals("front") ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
        }
        if (s.has("hud")) mc.options.hideGui = !s.get("hud").getAsBoolean();
        // "chat": true ouvre le chat (20 lignes visibles : reponses des commandes), false le ferme
        if (s.has("chat")) mc.setScreen(s.get("chat").getAsBoolean() ? new ChatScreen("") : null);
        if (s.has("shot")) {
            Screenshot.grab(mc.gameDirectory, "clans_" + s.get("shot").getAsString() + ".png", mc.getMainRenderTarget(), msg -> {});
        }
        if (s.has("fps")) cadence(mc, s.get("fps").getAsString());
        if (s.has("quit")) mc.stop();
    }

    /**
     * Essais des permissions ({@code dev.TestPerms}, partie serveur) : appeles par leur nom, le code client ne voit pas
     * la partie serveur ; seulement avec le jar -complet (client de dev).
     */
    private static void testPerms(net.minecraft.server.MinecraftServer server, String etape) {
        try {
            Class.forName("fr.heralisz.clans.dev.TestPerms").getMethod("lancer", net.minecraft.server.MinecraftServer.class, String.class)
                    .invoke(null, server, etape);
        } catch (ReflectiveOperationException e) {
            fr.heralisz.clans.HeraliszClans.LOG.warn("[clans] banc : TestPerms indisponible ({})", e.toString());
        }
    }
}
