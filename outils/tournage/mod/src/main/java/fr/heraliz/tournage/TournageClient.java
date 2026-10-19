package fr.heraliz.tournage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import fr.heraliz.tournage.mixin.KeyboardHandlerAccessor;
import fr.heraliz.tournage.mixin.MouseHandlerAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tournage des vidéos : joue un script (touches, regard, commandes, caméra libre) sur le joueur de ce jeu.
 * Le directeur (outils/tournage/directeur.py) écrit go.json dans le dossier de tournage ; chaque jeu y lit le script et
 * joue la partie de son pseudo. Les touches passent par les vraies entrées du jeu (comme un clavier et une souris).
 */
public final class TournageClient {
   private static final Path DOSSIER = Path.of(System.getProperty("tournage.dossier", "/root/tournage"));

   private static long goModifie;
   private static List<JsonObject> evenements = new ArrayList<>();
   private static int prochain;
   private static int tick = -1;
   private static int fin;
   private static final Set<String> tenues = new HashSet<>();

   // regard : interpolation de (yaw0, pitch0) vers (yaw1, pitch1) entre t0 et t1, ou suivi d'une entité
   private static float yaw0, pitch0, yaw1, pitch1;
   private static double rt0, rt1 = -1;
   private static String suivi;
   private static double suiviFin, suiviHauteur, suiviReac;
   private static float suiviYaw, suiviPitch;

   // marche vers une cible (pseudo ou [x, y, z]) : avancer en la regardant jusqu'à « arret » blocs
   private static JsonElement marcheVers;
   private static double marcheArret;
   private static boolean marcheCourir;

   // caméra libre : clés {t, pos, yaw, pitch}, temps relatif au début ; viser = pseudo à regarder
   private static JsonArray camCles;
   private static double camDebut;
   private static String camVise;
   private static double camViseHauteur;

   static void brancher() {
      MinecraftForge.EVENT_BUS.addListener(TournageClient::tickClient);
      MinecraftForge.EVENT_BUS.addListener(TournageClient::image);
   }

   private static double maintenant(float partial) {
      return tick + partial;
   }

   private static void tickClient(TickEvent.ClientTickEvent e) {
      if (e.phase != TickEvent.Phase.START) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer p = mc.player;
      if (p == null) {
         return;
      }
      lireGo(p.getGameProfile().getName());
      if (tick < 0) {
         return;
      }
      while (prochain < evenements.size() && evenements.get(prochain).get("t").getAsInt() <= tick) {
         try {
            jouer(mc, p, evenements.get(prochain));
         } catch (Exception ex) {
            System.out.println("[tournage] evenement refuse " + evenements.get(prochain) + " : " + ex);
         }
         prochain++;
      }
      if (marcheVers != null && marcheVers.isJsonPrimitive() && entite(mc, marcheVers.getAsString()) == null) {
         marcheVers = null;
         touche(mc, "key.forward", false);
      }
      if (marcheVers != null) {
         Vec3 c = cible(mc, marcheVers, 0);
         double d = Math.sqrt(p.distanceToSqr(c.x, p.getY(), c.z));
         boolean avance = d > marcheArret;
         if (avance) {
            float yaw = angles(p.position(), c)[0];
            p.setYRot(p.getYRot() + Mth.wrapDegrees(yaw - p.getYRot()) * 0.35F);
            p.setYHeadRot(p.getYRot());
         }
         if (avance != mc.options.keyUp.isDown()) {
            touche(mc, "key.forward", avance);
         }
         if (marcheCourir && avance != mc.options.keySprint.isDown()) {
            touche(mc, "key.sprint", avance);
         }
         if (!avance) {
            marcheVers = null;
         }
      }
      // caméra libre : le joueur (spectateur) suit la caméra pour que le monde se charge autour d'elle
      double[] c = camera(0F);
      if (c != null && p.isSpectator()) {
         p.setPos(c[0], c[1] - p.getEyeHeight(), c[2]);
         p.setDeltaMovement(Vec3.ZERO);
      }
      ecrireEtat(p.getGameProfile().getName(), tick >= fin ? "fini" : "joue");
      if (tick > fin + 20 && fin > 0) {
         relacherTout(mc);
         tick = -1;
         return;
      }
      tick++;
   }

   private static void lireGo(String nom) {
      try {
         Path go = DOSSIER.resolve("go.json");
         if (!Files.exists(go)) {
            return;
         }
         long m = Files.getLastModifiedTime(go).toMillis();
         if (m == goModifie) {
            return;
         }
         goModifie = m;
         JsonObject o = JsonParser.parseString(Files.readString(go, StandardCharsets.UTF_8)).getAsJsonObject();
         JsonObject script = JsonParser.parseString(Files.readString(Path.of(o.get("script").getAsString()), StandardCharsets.UTF_8)).getAsJsonObject();
         relacherTout(Minecraft.getInstance());
         evenements = new ArrayList<>();
         fin = 0;
         JsonArray a = script.has(nom) ? script.getAsJsonArray(nom) : new JsonArray();
         for (JsonElement el : a) {
            JsonObject ev = el.getAsJsonObject();
            evenements.add(ev);
            fin = Math.max(fin, ev.get("t").getAsInt() + (ev.has("duree") ? ev.get("duree").getAsInt() : 0));
         }
         fin = Math.max(fin, script.has("duree") ? script.get("duree").getAsInt() : 0);
         evenements.sort((x, y) -> Integer.compare(x.get("t").getAsInt(), y.get("t").getAsInt()));
         prochain = 0;
         tick = 0;
         rt1 = -1;
         suivi = null;
         camCles = null;
         System.out.println("[tournage] script " + o.get("script").getAsString() + " pour " + nom + " : " + evenements.size() + " evenements");
      } catch (Exception ex) {
         System.out.println("[tournage] go.json illisible : " + ex);
      }
   }

   private static void ecrireEtat(String nom, String etat) {
      if (tick % 10 != 0) {
         return;
      }
      try {
         Files.writeString(DOSSIER.resolve("etat_" + nom + ".txt"), etat + " " + tick + "\n");
      } catch (Exception ignored) {
      }
   }

   private static void jouer(Minecraft mc, LocalPlayer p, JsonObject ev) {
      if (ev.has("touche")) {
         String etat = ev.has("etat") ? ev.get("etat").getAsString() : "tape";
         String nom = ev.get("touche").getAsString();
         switch (etat) {
            case "bas" -> touche(mc, nom, true);
            case "haut" -> touche(mc, nom, false);
            default -> {
               touche(mc, nom, true);
               touche(mc, nom, false);
            }
         }
      }
      if (ev.has("regard")) {
         JsonObject r = ev.getAsJsonObject("regard");
         float y1, p1;
         if (r.has("vers")) {
            Vec3 cible = cible(mc, r.get("vers"), r.has("hauteur") ? r.get("hauteur").getAsDouble() : 1.4);
            float[] yp = angles(p.getEyePosition(), cible);
            y1 = yp[0];
            p1 = yp[1];
         } else {
            y1 = r.get("yaw").getAsFloat();
            p1 = r.get("pitch").getAsFloat();
         }
         int d = r.has("duree") ? r.get("duree").getAsInt() : 0;
         yaw0 = p.getYRot();
         pitch0 = p.getXRot();
         yaw1 = yaw0 + Mth.wrapDegrees(y1 - yaw0);
         pitch1 = p1;
         rt0 = tick;
         rt1 = tick + Math.max(d, 0.001);
         suivi = null;
      }
      if (ev.has("suivre")) {
         JsonObject s = ev.getAsJsonObject("suivre");
         suivi = s.get("cible").getAsString();
         suiviFin = tick + s.get("duree").getAsInt();
         suiviHauteur = s.has("hauteur") ? s.get("hauteur").getAsDouble() : 1.2;
         suiviReac = s.has("reactivite") ? s.get("reactivite").getAsDouble() : 0.3;
         suiviYaw = p.getYRot();
         suiviPitch = p.getXRot();
         rt1 = -1;
      }
      if (ev.has("cmd")) {
         p.connection.sendCommand(ev.get("cmd").getAsString());
      }
      if (ev.has("hud")) {
         mc.options.hideGui = !ev.get("hud").getAsBoolean();
      }
      if (ev.has("slot")) {
         p.getInventory().selected = ev.get("slot").getAsInt();
      }
      if (ev.has("camera")) {
         JsonElement c = ev.get("camera");
         if (c.isJsonPrimitive()) {
            camCles = null;
         } else {
            JsonObject co = c.getAsJsonObject();
            camCles = co.getAsJsonArray("cles");
            camDebut = tick;
            camVise = co.has("viser") ? co.get("viser").getAsString() : null;
            camViseHauteur = co.has("hauteur") ? co.get("hauteur").getAsDouble() : 1.2;
         }
      }
      if (ev.has("marcher")) {
         JsonObject m = ev.getAsJsonObject("marcher");
         marcheVers = m.get("vers");
         marcheArret = m.has("arret") ? m.get("arret").getAsDouble() : 1.5;
         marcheCourir = m.has("courir") && m.get("courir").getAsBoolean();
         suivi = null;
         rt1 = -1;
      }
      if (ev.has("renaitre") && (p.isDeadOrDying() || mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen)) {
         p.respawn();
         mc.setScreen(null);
      }
      if (ev.has("debug")) {
         String n = ev.get("debug").getAsString();
         KeyMapping km = null;
         for (KeyMapping k : mc.options.keyMappings) {
            if (k.getName().equals(n)) {
               km = k;
            }
         }
         System.out.println("[tournage] debug visee=" + mc.hitResult + " type=" + (mc.hitResult == null ? null : mc.hitResult.getType())
            + (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult eh ? " entite=" + eh.getEntity() : "")
            + " touche " + n + "=" + (km == null ? "absente" : km.isDown() + " (" + km.getKey().getName() + ")") + " ecran=" + mc.screen);
      }
      if (ev.has("ips")) {
         // images par seconde au plus : un jeu qui ne filme pas laisse la machine à celui qui filme
         mc.options.framerateLimit().set(ev.get("ips").getAsInt());
      }
      if (ev.has("souris")) {
         mc.mouseHandler.grabMouse();
      }
   }

   private static Vec3 cible(Minecraft mc, JsonElement vers, double hauteur) {
      if (vers.isJsonArray()) {
         JsonArray a = vers.getAsJsonArray();
         return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
      }
      Entity e = entite(mc, vers.getAsString());
      return e == null ? Vec3.ZERO : e.position().add(0, hauteur, 0);
   }

   private static Entity entite(Minecraft mc, String nom) {
      if (mc.level == null) {
         return null;
      }
      for (Player pl : mc.level.players()) {
         if (pl.getGameProfile().getName().equals(nom)) {
            return pl;
         }
      }
      for (Entity e : mc.level.entitiesForRendering()) {
         if (e.hasCustomName() && e.getCustomName().getString().equals(nom)) {
            return e;
         }
      }
      return null;
   }

   private static float[] angles(Vec3 de, Vec3 vers) {
      Vec3 d = vers.subtract(de);
      float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
      float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
      return new float[]{yaw, pitch};
   }

   /** Appuie ou relâche la touche liée à un réglage du jeu (key.forward, key.attack, touches des mods...). */
   private static void touche(Minecraft mc, String nom, boolean bas) {
      KeyMapping km = null;
      for (KeyMapping k : mc.options.keyMappings) {
         if (k.getName().equals(nom)) {
            km = k;
         }
      }
      InputConstants.Key key = km != null ? km.getKey() : InputConstants.getKey(nom);
      long w = mc.getWindow().getWindow();
      int action = bas ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE;
      if (key.getType() == InputConstants.Type.MOUSE) {
         ((MouseHandlerAccessor) mc.mouseHandler).tournage$onPress(w, key.getValue(), action, 0);
      } else {
         ((KeyboardHandlerAccessor) mc.keyboardHandler).tournage$keyPress(w, key.getValue(), 0, action, 0);
      }
      if (km != null) {
         // filet : l'état de la touche, même si l'entrée a été filtrée (fenêtre sans focus)
         km.setDown(bas);
      }
      if (bas) {
         tenues.add(nom);
      } else {
         tenues.remove(nom);
      }
   }

   private static void relacherTout(Minecraft mc) {
      for (String t : new ArrayList<>(tenues)) {
         touche(mc, t, false);
      }
      camCles = null;
      suivi = null;
      marcheVers = null;
      rt1 = -1;
   }

   /** À chaque image : regard du joueur exact (pas d'à-coups entre deux ticks). */
   private static void image(TickEvent.RenderTickEvent e) {
      if (e.phase != TickEvent.Phase.START || tick < 0) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer p = mc.player;
      if (p == null) {
         return;
      }
      double t = maintenant(e.renderTickTime);
      float yaw, pitch;
      if (suivi != null && t <= suiviFin) {
         Entity c = entite(mc, suivi);
         if (c == null) {
            return;
         }
         Vec3 pos = c.getPosition(e.renderTickTime).add(0, suiviHauteur, 0);
         float[] yp = angles(p.getEyePosition(e.renderTickTime), pos);
         suiviYaw += Mth.wrapDegrees(yp[0] - suiviYaw) * (float) suiviReac;
         suiviPitch += (yp[1] - suiviPitch) * (float) suiviReac;
         yaw = suiviYaw;
         pitch = suiviPitch;
      } else if (rt1 >= 0) {
         double k = Mth.clamp((t - rt0) / (rt1 - rt0), 0, 1);
         k = k * k * (3 - 2 * k);
         yaw = (float) (yaw0 + (yaw1 - yaw0) * k);
         pitch = (float) (pitch0 + (pitch1 - pitch0) * k);
      } else {
         return;
      }
      p.setYRot(yaw);
      p.setXRot(pitch);
      p.yRotO = yaw;
      p.xRotO = pitch;
      p.setYHeadRot(yaw);
      p.yHeadRotO = yaw;
   }

   /** Caméra libre (x, y, z, yaw, pitch) à cet instant, ou null. Catmull-Rom entre les clés. */
   public static double[] camera(float partial) {
      JsonArray k = camCles;
      if (k == null || k.size() == 0 || tick < 0) {
         return null;
      }
      double t = maintenant(partial) - camDebut;
      int i = 0;
      while (i < k.size() - 2 && k.get(i + 1).getAsJsonObject().get("t").getAsDouble() <= t) {
         i++;
      }
      JsonObject a = k.get(i).getAsJsonObject();
      JsonObject b = k.get(Math.min(i + 1, k.size() - 1)).getAsJsonObject();
      JsonObject a0 = k.get(Math.max(i - 1, 0)).getAsJsonObject();
      JsonObject b1 = k.get(Math.min(i + 2, k.size() - 1)).getAsJsonObject();
      double ta = a.get("t").getAsDouble(), tb = b.get("t").getAsDouble();
      double u = tb > ta ? Mth.clamp((t - ta) / (tb - ta), 0, 1) : 1;
      if (b.has("lisse") && b.get("lisse").getAsBoolean()) {
         u = u * u * (3 - 2 * u);
      }
      double[] r = new double[5];
      for (int c = 0; c < 3; c++) {
         r[c] = cr(v(a0, "pos", c), v(a, "pos", c), v(b, "pos", c), v(b1, "pos", c), u);
      }
      Minecraft mc = Minecraft.getInstance();
      Entity vise = camVise != null ? entite(mc, camVise) : null;
      if (vise != null) {
         float[] yp = angles(new Vec3(r[0], r[1], r[2]), vise.getPosition(partial).add(0, camViseHauteur, 0));
         r[3] = yp[0];
         r[4] = yp[1];
      } else {
         double ya = a.get("yaw").getAsDouble(), yb = ya + Mth.wrapDegrees(b.get("yaw").getAsDouble() - ya);
         double y0 = ya + Mth.wrapDegrees(a0.get("yaw").getAsDouble() - ya), y1 = yb + Mth.wrapDegrees(b1.get("yaw").getAsDouble() - yb);
         r[3] = cr(y0, ya, yb, y1, u);
         r[4] = cr(a0.get("pitch").getAsDouble(), a.get("pitch").getAsDouble(), b.get("pitch").getAsDouble(), b1.get("pitch").getAsDouble(), u);
      }
      return r;
   }

   private static double v(JsonObject o, String n, int i) {
      return o.getAsJsonArray(n).get(i).getAsDouble();
   }

   private static double cr(double p0, double p1, double p2, double p3, double u) {
      double u2 = u * u, u3 = u2 * u;
      return 0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
   }
}
