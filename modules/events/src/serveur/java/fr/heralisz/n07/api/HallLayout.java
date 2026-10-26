package fr.heralisz.n07.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reperes d'une variante de salle, en coordonnees LOCALES de la structure non tournee (x est, y haut, z sud ; +z = du sas
 * vers le fond du hall). Lus dans data/heralisz_n07/structures/&lt;variante&gt;.json (meme fichier que le generateur de carte).
 * Pour des coordonnees du monde : PlacedHall.
 */
public final class HallLayout {
   private static final Map<String, HallLayout> CACHE = new ConcurrentHashMap<>();

   public final String variant;
   /** Id de la structure (.nbt) du hall et du puits du monte-charge. */
   public final String structure, liftStructure;
   public final Vec3i size, liftSize;
   /** Decalage (local hall) de l'origine du puits ; meme rotation que le hall. */
   public final BlockPos liftOffset;
   /** Chute du monte-charge (blocs). */
   public final int liftDrop;
   /** Pieds du boss (dans sa cuve) et son lacet local. */
   public final BlockPos boss;
   public final float bossYaw;
   public final BlockPos tank, terminal, gate, pad;
   public final float padRadius;
   /** Camera de la galerie (vitre au-dessus du sas) et son point vise. */
   public final Vec3 gallery, galleryLook;
   public final BlockPos vestibule, drop;
   /** Arrivee du monte-charge (bas du puits / vestibule), marques des joueurs dans le hall (lacet local). */
   public final List<BlockPos> liftArrival, playerMarks, reinforcements;
   public final float marksYaw;
   /** Interieur du hall (arene) : min / max inclus. */
   public final BlockPos interiorMin, interiorMax;
   /** Buses dans l'ordre de mise en route ; rigoles, lumieres (plafonniers + suspensions), sirenes. */
   public final List<BlockPos> pipes, channels, lights, sirens;
   /** Puits (local puits) : pieds en haut / en bas, cabine (min / max). */
   public final BlockPos liftTopFeet, liftBottomFeet, cageMin, cageMax;

   private HallLayout(String variant, JsonObject hall, JsonObject lift) {
      this.variant = variant;
      structure = hall.get("structure").getAsString();
      size = vec(hall.getAsJsonArray("taille"));
      JsonObject r = hall.getAsJsonObject("reperes");
      boss = pos(r.getAsJsonObject("boss").getAsJsonArray("pos"));
      bossYaw = r.getAsJsonObject("boss").get("yaw").getAsFloat();
      tank = pos(r.getAsJsonObject("cuve").getAsJsonArray("pos"));
      terminal = pos(r.getAsJsonObject("terminal").getAsJsonArray("pos"));
      gate = pos(r.getAsJsonObject("grille").getAsJsonArray("pos"));
      pad = pos(r.getAsJsonObject("teleporteur").getAsJsonArray("pos"));
      padRadius = r.getAsJsonObject("teleporteur").get("rayon").getAsFloat();
      gallery = v3(r.getAsJsonObject("galerie").getAsJsonArray("pos"));
      galleryLook = v3(r.getAsJsonObject("galerie").getAsJsonArray("vise"));
      vestibule = pos(r.getAsJsonObject("vestibule").getAsJsonArray("pos"));
      drop = pos(r.getAsJsonArray("largage"));
      liftArrival = list(r.getAsJsonArray("arrivee_ascenseur"));
      playerMarks = list(r.getAsJsonObject("marques_joueurs").getAsJsonArray("positions"));
      marksYaw = r.getAsJsonObject("marques_joueurs").get("yaw").getAsFloat();
      reinforcements = list(r.getAsJsonArray("apparitions_renforts"));
      interiorMin = pos(r.getAsJsonObject("interieur_hall").getAsJsonArray("min"));
      interiorMax = pos(r.getAsJsonObject("interieur_hall").getAsJsonArray("max"));
      JsonObject l = hall.getAsJsonObject("listes");
      pipes = list(l.getAsJsonArray("tuyaux_ordre"));
      channels = list(l.getAsJsonArray("rigoles"));
      lights = list(l.getAsJsonArray("lumieres"));
      sirens = list(l.getAsJsonArray("sirenes"));
      JsonObject asc = hall.getAsJsonObject("ascenseur");
      liftStructure = asc.get("structure").getAsString();
      liftOffset = pos(asc.getAsJsonArray("decalage"));
      liftSize = vec(lift.getAsJsonArray("taille"));
      liftDrop = lift.get("chute").getAsInt();
      JsonObject lr = lift.getAsJsonObject("reperes");
      liftTopFeet = pos(lr.getAsJsonArray("haut_pieds"));
      liftBottomFeet = pos(lr.getAsJsonArray("bas_pieds"));
      cageMin = pos(lr.getAsJsonObject("cabine").getAsJsonArray("min"));
      cageMax = pos(lr.getAsJsonObject("cabine").getAsJsonArray("max"));
   }

   /** Variante connue : « n07_hall_zombies ». Null si absente. */
   public static HallLayout get(String variant) {
      return CACHE.computeIfAbsent(variant, v -> {
         JsonObject hall = read(v);
         if (hall == null) return null;
         String liftId = hall.getAsJsonObject("ascenseur").get("structure").getAsString();
         JsonObject lift = read(liftId.substring(liftId.indexOf(':') + 1));
         return lift == null ? null : new HallLayout(v, hall, lift);
      });
   }

   private static JsonObject read(String name) {
      String path = "/data/heralisz_n07/structures/" + name + ".json";
      try (InputStream in = fr.heralisz.n07.hall.ModResources.open(path.substring(1))) {
         if (in == null) return null;
         return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
      } catch (Exception e) {
         fr.heralisz.n07.HeraliszN07.LOGGER.error("[n07] reperes illisibles : {}", path, e);
         return null;
      }
   }

   private static BlockPos pos(JsonArray a) {
      return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
   }

   private static Vec3i vec(JsonArray a) {
      return new Vec3i(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
   }

   private static Vec3 v3(JsonArray a) {
      return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
   }

   private static List<BlockPos> list(JsonArray a) {
      List<BlockPos> out = new ArrayList<>();
      for (JsonElement e : a) out.add(pos(e.getAsJsonArray()));
      return Collections.unmodifiableList(out);
   }
}
