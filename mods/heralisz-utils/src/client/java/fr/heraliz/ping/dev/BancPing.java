package fr.heraliz.ping.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.ping.HeralizPing;
import fr.heraliz.ping.PingConfig;
import fr.heraliz.ping.PingType;
import fr.heraliz.ping.client.EcranReglages;
import fr.heraliz.ping.client.PingClient;
import fr.heraliz.ping.client.PingTouches;
import fr.heraliz.ping.client.PingsClient;
import fr.heraliz.ping.client.hud.Mesure;
import fr.heraliz.ping.client.hud.RenduPings;
import fr.heraliz.ping.client.hud.Roue;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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
import java.util.Locale;

/**
 * Banc d'essai en jeu (client de dev seulement, actif avec -Dheraliz.ping.harness=scenario.json, soit
 * {@code python tools/lancer.py client src/heraliz-ping <scenario.json>}) : client muet (volume general a 0, pas de pause
 * hors focus), monde plat « ping_banc » (creatif, commandes), scenario, bilan, sortie.
 * <p>Etapes ({"t": ticks depuis l'etape precedente, ...}) : "cmd" (commande), "regarder" [lacet, tangage], "viser"
 * [x, y, z], "ping" (type ou "auto" : comme la roue / l'appui bref), "touche" vrai/faux (touche des pings enfoncee ou
 * non), "curseur" [dx, dy] (curseur de la roue, pixels d'interface), "serveur" (verification cote serveur, voir
 * BancServeur, partie serveur), "faux" {qui, type, point}, "config" {volume, taille, noms, fleches, max}, "verif_type" (type du
 * dernier ping recu), "verif_suivi" (le marqueur suit l'entite), "verif_roue" (roue ouverte ou non), "perf" n (images
 * mesurees), "ecran" "reglages", "fermer", "etat", "shot", "quit".
 * Captures : run/screenshots/ping_&lt;nom&gt;.png ; lignes « [ping-banc] » dans run/logs/latest.log.
 */
@Mod.EventBusSubscriber(modid = HeralizPing.MODID, value = Dist.CLIENT)
public final class BancPing {
    private static final String FICHIER = System.getProperty("heraliz.ping.harness");
    private static final String MONDE = "ping_banc";
    private static boolean lance;
    private static final List<JsonObject> ETAPES = new ArrayList<>();
    private static final List<Long> A = new ArrayList<>();
    private static int suivante;
    private static long horloge = Long.MIN_VALUE;
    private static String langue = "fr_fr";
    private static int ok, echecs;
    private static final List<String> ECHECS = new ArrayList<>();
    private static int perfReste;
    private static long octetsVide;

    private BancPing() {
    }

    public static boolean actif() {
        return FICHIER != null;
    }

    private static void log(String f, Object... a) {
        HeralizPing.LOG.info("[ping-banc] " + f, a);
    }

    private static void verifier(boolean cond, String quoi, Object... a) {
        String t = String.format(Locale.ROOT, quoi.replace("{}", "%s"), a);
        if (cond) {
            ok++;
            log("OK     {}", t);
        } else {
            echecs++;
            ECHECS.add(t);
            log("ECHEC  {}", t);
        }
    }

    /**
     * Verifications cote serveur ({@code dev.BancServeur}, partie serveur, fil du serveur integre) : appelees par leur nom,
     * le code client ne voit pas la partie serveur (banc lance avec le jar -complet). Une erreur du banc serveur remonte
     * telle quelle, comme avant.
     */
    private static void bancServeur(String methode, Class<?>[] types, Object... args) {
        try {
            java.lang.reflect.Method m = Class.forName("fr.heraliz.ping.dev.BancServeur").getDeclaredMethod(methode, types);
            m.setAccessible(true);
            m.invoke(null, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            if (e.getCause() instanceof Error r) throw r;
            log("BancServeur.{} : erreur {}", methode, e.getCause());
        } catch (ReflectiveOperationException e) {
            log("BancServeur indisponible ({})", e.toString());
        }
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
        // client muet et sans pause : les tests ne doivent rien faire entendre
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        mc.options.pauseOnLostFocus = false;
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
                HeralizPing.LOG.error("[ping-banc] scenario illisible : {}", FICHIER, ex);
                lance = true;
                return;
            }
        }
        if (!langue.equals(mc.options.languageCode)) {
            mc.options.languageCode = langue;
            mc.getLanguageManager().setSelected(langue);
            mc.options.save();
            mc.reloadResourcePacks();
            return;
        }
        lance = true;
        PingTouches.banc = true;
        PingsClient.journal = true;
        mc.options.enableVsync().set(false);
        // chat invisible sur les captures, mais actif (avec « masque », le jeu refuse aussi les commandes)
        mc.options.chatVisibility().set(ChatVisiblity.FULL);
        mc.options.chatScale().set(0.0);
        mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        mc.options.save();
        if (mc.getLevelSource().levelExists(MONDE)) {
            mc.createWorldOpenFlows().loadLevel(e.getScreen(), MONDE);
        } else {
            GameRules regles = new GameRules();
            regles.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            regles.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            regles.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            regles.getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(false, null);
            LevelSettings reglages = new LevelSettings(MONDE, GameType.CREATIVE, false, net.minecraft.world.Difficulty.EASY, true, regles,
                    WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(MONDE, reglages, new WorldOptions(20261004L, false, false),
                    reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (FICHIER == null || e.phase != TickEvent.Phase.END || !lance) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (horloge == Long.MIN_VALUE) horloge = -60; // le monde se pose (3 s)
        horloge++;
        if (perfReste > 0 && Mesure.images >= perfReste) finPerf();
        while (suivante < ETAPES.size() && horloge >= A.get(suivante)) {
            JsonObject s = ETAPES.get(suivante++);
            try {
                executer(mc, s);
            } catch (Exception ex) {
                echecs++;
                ECHECS.add("etape " + s + " : " + ex);
                HeralizPing.LOG.error("[ping-banc] etape en echec : {}", s, ex);
            }
        }
    }

    private static void regarder(Player p, float lacet, float tangage) {
        p.setYRot(lacet);
        p.setXRot(tangage);
        p.yRotO = lacet;
        p.xRotO = tangage;
        p.setYHeadRot(lacet);
        p.yHeadRotO = lacet;
    }

    private static void viser(Player p, double x, double y, double z) {
        Vec3 oeil = p.getEyePosition();
        double dx = x - oeil.x, dy = y - oeil.y, dz = z - oeil.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float lacet = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float tangage = (float) -Math.toDegrees(Math.atan2(dy, h));
        regarder(p, lacet, tangage);
    }

    private static int type(String nom) {
        if (nom.equals("auto")) return PingType.AUTO;
        for (PingType t : PingType.values()) if (t.nom.equals(nom)) return t.id();
        throw new IllegalArgumentException("type inconnu : " + nom);
    }

    private static void executer(Minecraft mc, JsonObject s) {
        Player p = mc.player;
        if (s.has("cmd")) mc.player.connection.sendCommand(s.get("cmd").getAsString());
        if (s.has("config")) config(s.getAsJsonObject("config"));
        if (s.has("regarder")) {
            JsonArray a = s.getAsJsonArray("regarder");
            regarder(p, a.get(0).getAsFloat(), a.get(1).getAsFloat());
        }
        if (s.has("viser")) {
            JsonArray a = s.getAsJsonArray("viser");
            viser(p, a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
        }
        if (s.has("viser_entite")) {
            Entity cible = null;
            String id = s.get("viser_entite").getAsString();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).toString().equals(id)) cible = e;
            }
            if (cible != null) viser(p, cible.getX(), cible.getY() + cible.getBbHeight() * 0.5, cible.getZ());
            else log("viser_entite : aucune entite {}", id);
        }
        if (s.has("ping")) {
            PingClient.sansEcart();
            String nom = s.get("ping").getAsString();
            if (nom.equals("rapide")) PingClient.pingRapide();
            else PingClient.ping(type(nom));
        }
        if (s.has("touche")) {
            boolean bas = s.get("touche").getAsBoolean();
            KeyMapping.set(PingTouches.PING.getKey(), bas);
            log("touche des pings {} ({})", bas ? "enfoncee" : "relachee", PingTouches.PING.getTranslatedKeyMessage().getString());
        }
        if (s.has("curseur")) {
            JsonArray a = s.getAsJsonArray("curseur");
            Roue.banc = true;
            Roue.forcerCurseur(a.get(0).getAsFloat(), a.get(1).getAsFloat());
        }
        if (s.has("verif_roue")) {
            boolean voulu = s.get("verif_roue").getAsBoolean();
            verifier(Roue.ouverte() == voulu, "roue {} (attendu {})", Roue.ouverte() ? "ouverte" : "fermee", voulu ? "ouverte" : "fermee");
            if (Roue.ouverte()) log("secteur survole : {}", Roue.choix() < 0 ? "centre" : PingType.parId(Roue.choix()));
        }
        if (s.has("serveur")) {
            String nom = s.get("serveur").getAsString();
            MinecraftServer srv = mc.getSingleplayerServer();
            java.util.UUID moi = p.getUUID();
            if (srv != null) srv.execute(() -> bancServeur("etape", new Class<?>[]{MinecraftServer.class, String.class, java.util.UUID.class}, srv, nom, moi));
        }
        if (s.has("faux")) {
            JsonObject f = s.getAsJsonObject("faux");
            JsonArray pt = f.getAsJsonArray("point");
            String qui = f.get("qui").getAsString();
            int t = type(f.get("type").getAsString());
            double x = pt.get(0).getAsDouble(), y = pt.get(1).getAsDouble(), z = pt.get(2).getAsDouble();
            MinecraftServer srv = mc.getSingleplayerServer();
            if (srv != null) srv.execute(() -> bancServeur("fauxPing", new Class<?>[]{String.class, int.class, double.class, double.class, double.class},
                    qui, t, x, y, z));
        }
        if (s.has("verif_type")) {
            String voulu = s.get("verif_type").getAsString();
            PingsClient.Visuel dernier = dernier();
            verifier(dernier != null && dernier.type.nom.equals(voulu), "type du dernier ping recu : {} (attendu {})",
                    dernier == null ? "aucun" : dernier.type.nom, voulu);
        }
        if (s.has("verif_suivi")) verifSuivi(mc, s.get("verif_suivi").getAsString());
        if (s.has("verif_absent")) {
            JsonArray a = s.getAsJsonArray("verif_absent");
            double x = a.get(0).getAsDouble(), y = a.get(1).getAsDouble(), z = a.get(2).getAsDouble();
            boolean present = false;
            for (int i = 0; i < PingsClient.n; i++) {
                PingsClient.Visuel v = PingsClient.PINGS[i];
                if (Math.abs(v.x - x) < 1 && Math.abs(v.y - y) < 1 && Math.abs(v.z - z) < 1) present = true;
            }
            verifier(!present, "mon ping retire par un appui bref dessus : {}", present ? "encore la" : "parti");
        }
        if (s.has("verif_nb")) {
            int voulu = s.get("verif_nb").getAsInt();
            verifier(PingsClient.n == voulu, "pings affiches : {} (attendu {})", PingsClient.n, voulu);
        }
        if (s.has("verif_fleches")) {
            verifier(RenduPings.dernieresFleches >= s.get("verif_fleches").getAsInt(), "fleches au bord de l'ecran : {} (au moins {})",
                    RenduPings.dernieresFleches, s.get("verif_fleches").getAsInt());
        }
        if (s.has("perf")) {
            perfReste = s.get("perf").getAsInt();
            octetsVide = Mesure.octetsAVide();
            Mesure.zero();
            Mesure.active = true;
        }
        if (s.has("ecran")) {
            if (s.get("ecran").getAsString().equals("reglages")) mc.setScreen(new EcranReglages(null));
        }
        if (s.has("fermer")) mc.setScreen(null);
        if (s.has("etat")) etat(mc);
        if (s.has("viseur")) {
            float cx = mc.getWindow().getGuiScaledWidth() * 0.5f, cy = mc.getWindow().getGuiScaledHeight() * 0.5f;
            log("VISEUR lacet {} tangage {} ; centre ({}, {}) ; position {} {} {}", p.getYRot(), p.getXRot(), cx, cy,
                    String.format(Locale.ROOT, "%.1f", p.getX()), String.format(Locale.ROOT, "%.1f", p.getY()), String.format(Locale.ROOT, "%.1f", p.getZ()));
            for (int i = 0; i < PingsClient.n; i++) {
                PingsClient.Visuel v = PingsClient.PINGS[i];
                log("  #{} {} ecran ({}, {}) a {} px du centre", v.id, v.moi ? "moi" : v.nom, (int) v.sx, (int) v.sy,
                        (int) Math.sqrt((v.sx - cx) * (v.sx - cx) + (v.sy - cy) * (v.sy - cy)));
            }
        }
        if (s.has("shot")) {
            String nom = "ping_" + s.get("shot").getAsString() + ".png";
            Screenshot.grab(mc.gameDirectory, nom, mc.getMainRenderTarget(), m -> {
            });
            log("capture {} ({} x {}, echelle {}) : {} pings, {} marqueurs, {} fleches", nom, mc.getWindow().getWidth(), mc.getWindow().getHeight(),
                    mc.getWindow().getGuiScale(), PingsClient.n, RenduPings.derniersMarqueurs, RenduPings.dernieresFleches);
        }
        if (s.has("bilan")) log("BILAN client : {} verifications OK, {} en echec {}", ok, echecs, ECHECS);
        if (s.has("quit")) mc.stop();
    }

    private static PingsClient.Visuel dernier() {
        PingsClient.Visuel d = null;
        for (int i = 0; i < PingsClient.n; i++) {
            PingsClient.Visuel v = PingsClient.PINGS[i];
            if (d == null || v.naissance > d.naissance || v.naissance == d.naissance && v.id > d.id) d = v;
        }
        return d;
    }

    /** Le marqueur d'un ping sur entite est sur l'entite (au-dessus de sa tete), ou elle est. */
    private static void verifSuivi(Minecraft mc, String typeEntite) {
        for (int i = 0; i < PingsClient.n; i++) {
            PingsClient.Visuel v = PingsClient.PINGS[i];
            if (v.entiteId < 0) continue;
            Entity e = mc.level.getEntity(v.entiteId);
            if (e == null || !net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).toString().equals(typeEntite)) continue;
            double dx = v.x - e.getX(), dz = v.z - e.getZ(), dy = v.y - (e.getY() + e.getBbHeight());
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            verifier(d < 0.3, "le marqueur suit l'entite {} : ecart {} bloc (entite en {} {} {})", e.getType().toShortString(),
                    String.format(Locale.ROOT, "%.2f", d), String.format(Locale.ROOT, "%.1f", e.getX()), String.format(Locale.ROOT, "%.1f", e.getY()),
                    String.format(Locale.ROOT, "%.1f", e.getZ()));
            return;
        }
        verifier(false, "aucun ping sur une entite {}", typeEntite);
    }

    private static void finPerf() {
        Mesure.active = false;
        long n = Math.max(1, Mesure.images);
        double us = Mesure.nanos / 1000.0 / n;
        double octets = Math.max(0, (Mesure.octets - octetsVide * Mesure.images) / (double) n);
        log("MESURE client : {} images, {} us par image pour le HUD des pings, {} quads par image, {} octets alloues par image ({} pings)",
                Mesure.images, String.format(Locale.ROOT, "%.1f", us), Mesure.quads / n, String.format(Locale.ROOT, "%.1f", octets), PingsClient.n);
        verifier(octets < 1.0, "aucune allocation par image dans le HUD des pings ({} octets)", String.format(Locale.ROOT, "%.1f", octets));
        perfReste = 0;
    }

    private static void config(JsonObject c) {
        for (String k : c.keySet()) {
            JsonElement v = c.get(k);
            switch (k) {
                case "volume" -> PingConfig.VOLUME.set(v.getAsInt());
                case "taille" -> PingConfig.TAILLE.set(v.getAsInt());
                case "noms" -> PingConfig.NOMS_TOUJOURS.set(v.getAsBoolean());
                case "fleches" -> PingConfig.FLECHES.set(v.getAsBoolean());
                case "max" -> PingConfig.MAX_VISIBLES.set(v.getAsInt());
                default -> log("config inconnue : {}", k);
            }
        }
    }

    private static void etat(Minecraft mc) {
        log("ETAT {} pings ; roue {} ; portee serveur {} ; ecran {}", PingsClient.n, Roue.ouverte() ? "ouverte" : "fermee", PingsClient.portee,
                mc.screen == null ? "aucun" : mc.screen.getClass().getSimpleName());
        for (int i = 0; i < PingsClient.n; i++) {
            PingsClient.Visuel v = PingsClient.PINGS[i];
            log("  #{} {} de {}{} en {} {} {} ; entite {} ; ecran {} ({} ; {}) ; {} m", v.id, v.type.nom, v.nom, v.moi ? " (moi)" : "",
                    String.format(Locale.ROOT, "%.1f", v.x), String.format(Locale.ROOT, "%.1f", v.y), String.format(Locale.ROOT, "%.1f", v.z),
                    v.entiteId, v.enEcran ? "oui" : "non", (int) v.sx, (int) v.sy, (int) v.dist);
        }
    }
}
