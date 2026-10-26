package fr.heraliz.capture.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lecteur du script {@code <jeu>/capture/script.json} : {@code {"depart": ticks après l'entrée dans le monde, "etapes": [...]}}.
 * Une étape par objet, jouée une fois par tick (les attentes comptent en ticks). Étapes :
 * <ul>
 *     <li>{@code {"commande": "boutique"}} : commande envoyée au serveur (sans « / ») ; {@code {"chat": "texte"}} ;</li>
 *     <li>{@code {"touche": "key.heraliz_quetes.journal"}} : un appui sur une touche des options ;
 *     {@code {"maintenir": "key.use", "ticks": 40}} : touche tenue ;</li>
 *     <li>{@code {"ecran": "classe.Complete"}} : écran ouvert chez le joueur (constructeur sans argument ou avec l'écran
 *     parent) ;</li>
 *     <li>{@code {"entite": "heraliz_quetes:pnj", "nom": "Mila"}} : clic droit sur l'entité de ce type la plus proche (8 blocs,
 *     nom qui contient le texte, facultatif) ; {@code {"bloc": [x, y, z]}} : clic droit sur ce bloc ;</li>
 *     <li>{@code {"regard": [lacet, tangage]}} ; {@code {"echelle": 3}} : taille de l'interface ;</li>
 *     <li>{@code {"maintenir": ["key.forward", "key.jump"], "ticks": 20}} : une ou plusieurs touches tenues ;
 *     {@code {"position": "nom"}} : position, pose et appui du joueur dans le journal ; {@code {"plan": "ramp",
 *     "rotation": 0}} : pièce choisie dans la roue du plan de construction ;</li>
 *     <li>{@code {"attendre": 40}} ; {@code {"attendre_ecran": "EcranBoutique", "max": 100}} : jusqu'à ce que l'écran ouvert
 *     ait ce nom de classe ;</li>
 *     <li>{@code {"capture": "nom"}} : image {@code screenshots/nom.png} et une ligne dans {@code capture/journal.txt} (classe
 *     de l'écran ouvert) ; {@code {"fermer": true}} ; {@code {"fin": true}} : le jeu se ferme.</li>
 * </ul>
 */
final class Script {
    private Script() {
    }

    private static final Logger LOG = LogUtils.getLogger();
    private static List<JsonObject> etapes;
    private static boolean charge;
    private static int index, attente, depart = 200, dansLeMonde;
    private static final List<KeyMapping> tenues = new ArrayList<>();
    private static int tenueTicks;
    private static String ecranAttendu;

    static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (!charge) charger(mc);
        if (etapes == null || index >= etapes.size() || mc.player == null || mc.level == null) return;
        if (++dansLeMonde < depart) return;
        if (!tenues.isEmpty() && --tenueTicks <= 0) {
            for (KeyMapping k : tenues) k.setDown(false);
            tenues.clear();
        }
        if (ecranAttendu != null) {
            boolean ok = mc.screen != null && mc.screen.getClass().getSimpleName().contains(ecranAttendu);
            if (!ok && attente-- > 0) return;
            if (!ok) journal(mc, "ecran attendu absent : " + ecranAttendu + " (ouvert : " + nomEcran(mc.screen) + ")");
            ecranAttendu = null;
            attente = 0;
        }
        if (attente > 0) {
            attente--;
            return;
        }
        JsonObject o = etapes.get(index++);
        try {
            jouer(mc, o);
        } catch (RuntimeException ex) {
            journal(mc, "etape " + index + " en erreur (" + o + ") : " + ex);
        }
    }

    private static void jouer(Minecraft mc, JsonObject o) {
        if (o.has("commande")) {
            mc.player.connection.sendCommand(o.get("commande").getAsString());
        } else if (o.has("chat")) {
            mc.player.connection.sendChat(o.get("chat").getAsString());
        } else if (o.has("touche")) {
            KeyMapping k = touche(mc, o.get("touche").getAsString());
            if (k != null) KeyMapping.click(k.getKey());
            else journal(mc, "touche inconnue : " + o.get("touche").getAsString());
        } else if (o.has("clic")) {
            // clic gauche dans l'écran ouvert, en pixels de la fenêtre (comme sur les captures)
            JsonArray a = o.getAsJsonArray("clic");
            if (mc.screen != null) {
                double k = (double) mc.getWindow().getGuiScaledWidth() / mc.getWindow().getWidth();
                double x = a.get(0).getAsDouble() * k, y = a.get(1).getAsDouble() * k;
                mc.screen.mouseMoved(x, y);
                mc.screen.mouseClicked(x, y, 0);
                mc.screen.mouseReleased(x, y, 0);
            }
        } else if (o.has("texte")) {
            if (mc.screen != null) for (char c : o.get("texte").getAsString().toCharArray()) mc.screen.charTyped(c, 0);
        } else if (o.has("touche_ecran")) {
            // code GLFW : 257 Entrée, 258 Tab, 256 Échap, 259 Retour arrière, 262/263 flèches droite/gauche
            if (mc.screen != null) mc.screen.keyPressed(o.get("touche_ecran").getAsInt(), 0, 0);
        } else if (o.has("souris")) {
            // survol (bulles d'aide) en pixels de la fenêtre
            JsonArray a = o.getAsJsonArray("souris");
            double k = (double) mc.getWindow().getGuiScaledWidth() / mc.getWindow().getWidth();
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), a.get(0).getAsDouble(), a.get(1).getAsDouble());
            if (mc.screen != null) mc.screen.mouseMoved(a.get(0).getAsDouble() * k, a.get(1).getAsDouble() * k);
        } else if (o.has("reapparaitre")) {
            mc.player.respawn();
            mc.setScreen(null);
        } else if (o.has("maintenir")) {
            // une touche ou plusieurs à la fois : {"maintenir": ["key.forward", "key.jump"], "ticks": 20}
            JsonElement m = o.get("maintenir");
            List<String> noms = new ArrayList<>();
            if (m.isJsonArray()) for (JsonElement el : m.getAsJsonArray()) noms.add(el.getAsString());
            else noms.add(m.getAsString());
            for (KeyMapping k : tenues) k.setDown(false);
            tenues.clear();
            for (String n : noms) {
                KeyMapping k = touche(mc, n);
                if (k == null) {
                    journal(mc, "touche inconnue : " + n);
                    continue;
                }
                k.setDown(true);
                KeyMapping.click(k.getKey());
                tenues.add(k);
            }
            tenueTicks = o.has("ticks") ? o.get("ticks").getAsInt() : 20;
        } else if (o.has("position")) {
            // {"position": "nom"} : position, pose et appui au sol du joueur dans le journal
            journal(mc, String.format(Locale.ROOT, "position %s : %.3f %.3f %.3f pose=%s sol=%s accroupi=%s", o.get("position").getAsString(),
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getPose(), mc.player.onGround(), mc.player.isShiftKeyDown()));
        } else if (o.has("plan")) {
            // {"plan": "triangle_floor", "rotation": 0} : pièce choisie dans la roue du plan de construction (CRac)
            try {
                Class<?> kind = Class.forName("fr.heraliz.crac.build.model.PieceKind");
                Object k = kind.getMethod("byId", String.class).invoke(null, o.get("plan").getAsString());
                Class<?> etat = Class.forName("fr.heraliz.crac.build.client.PlanState");
                etat.getMethod("select", kind).invoke(null, k);
                int r = o.has("rotation") ? o.get("rotation").getAsInt() : 0;
                for (int i = 0; i < r; i++) etat.getMethod("rotate").invoke(null);
            } catch (ReflectiveOperationException ex) {
                journal(mc, "plan impossible : " + ex);
            }
        } else if (o.has("ecran")) {
            mc.setScreen(ecran(mc, o.get("ecran").getAsString()));
        } else if (o.has("entite")) {
            entite(mc, o.get("entite").getAsString(), o.has("nom") ? o.get("nom").getAsString() : "");
        } else if (o.has("bloc")) {
            JsonArray a = o.getAsJsonArray("bloc");
            BlockPos ici = mc.player.blockPosition();
            BlockPos p = new BlockPos(coord(a.get(0), ici.getX()), coord(a.get(1), ici.getY()), coord(a.get(2), ici.getZ()));
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(p), Direction.UP, p, false));
        } else if (o.has("regard")) {
            JsonArray a = o.getAsJsonArray("regard");
            mc.player.setYRot(a.get(0).getAsFloat());
            mc.player.setXRot(a.get(1).getAsFloat());
        } else if (o.has("echelle")) {
            mc.options.guiScale().set(o.get("echelle").getAsInt());
            mc.resizeDisplay();
        } else if (o.has("attendre")) {
            attente = o.get("attendre").getAsInt();
        } else if (o.has("attendre_ecran")) {
            ecranAttendu = o.get("attendre_ecran").getAsString();
            attente = o.has("max") ? o.get("max").getAsInt() : 100;
        } else if (o.has("capture")) {
            String nom = o.get("capture").getAsString().replaceAll("[^a-zA-Z0-9_-]", "_");
            Screenshot.grab(mc.gameDirectory, nom + ".png", mc.getMainRenderTarget(), c -> { });
            journal(mc, "capture " + nom + " : " + nomEcran(mc.screen));
        } else if (o.has("fermer")) {
            if (mc.screen instanceof AbstractContainerScreen<?>) mc.player.closeContainer();
            else mc.setScreen(null);
        } else if (o.has("fin")) {
            journal(mc, "fin du script");
            mc.stop();
        }
    }

    /** Coordonnée absolue (nombre) ou relative au joueur (« ~2 »). */
    private static int coord(JsonElement e, int ici) {
        String s = e.getAsString();
        return s.startsWith("~") ? ici + (s.length() > 1 ? Integer.parseInt(s.substring(1)) : 0) : Integer.parseInt(s);
    }

    private static KeyMapping touche(Minecraft mc, String nom) {
        for (KeyMapping k : mc.options.keyMappings) if (k.getName().equals(nom)) return k;
        return null;
    }

    private static Screen ecran(Minecraft mc, String classe) {
        try {
            Class<?> c = Class.forName(classe);
            try {
                return (Screen) c.getConstructor(Screen.class).newInstance(mc.screen);
            } catch (NoSuchMethodException ex) {
                var k = c.getDeclaredConstructor();
                k.setAccessible(true);
                return (Screen) k.newInstance();
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            journal(mc, "ecran impossible : " + classe + " (" + ex + ")");
            return null;
        }
    }

    private static void entite(Minecraft mc, String type, String nom) {
        ResourceLocation rl = ResourceLocation.tryParse(type);
        Entity meilleur = null;
        double d = 8 * 8;
        for (Entity en : mc.level.entitiesForRendering()) {
            if (en == mc.player || rl == null || !rl.equals(ForgeRegistries.ENTITY_TYPES.getKey(en.getType()))) continue;
            if (!nom.isEmpty() && !en.getDisplayName().getString().toLowerCase(Locale.ROOT).contains(nom.toLowerCase(Locale.ROOT))) continue;
            double dd = en.distanceToSqr(mc.player);
            if (dd < d) {
                d = dd;
                meilleur = en;
            }
        }
        if (meilleur == null) {
            journal(mc, "entite introuvable : " + type + " " + nom);
            return;
        }
        mc.gameMode.interact(mc.player, meilleur, InteractionHand.MAIN_HAND);
    }

    private static String nomEcran(Screen s) {
        return s == null ? "aucun" : s.getClass().getName();
    }

    private static void charger(Minecraft mc) {
        charge = true;
        Path f = mc.gameDirectory.toPath().resolve("capture").resolve("script.json");
        if (!Files.exists(f)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            if (o.has("depart")) depart = o.get("depart").getAsInt();
            etapes = new ArrayList<>();
            for (JsonElement el : o.getAsJsonArray("etapes")) etapes.add(el.getAsJsonObject());
            LOG.info("[capture] script : {} etapes", etapes.size());
        } catch (IOException | RuntimeException ex) {
            LOG.error("[capture] script illisible", ex);
        }
    }

    private static void journal(Minecraft mc, String ligne) {
        LOG.info("[capture] {}", ligne);
        try {
            Path f = mc.gameDirectory.toPath().resolve("capture").resolve("journal.txt");
            Files.createDirectories(f.getParent());
            Files.writeString(f, ligne + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignore) {
            // le journal du jeu suffit
        }
    }
}
