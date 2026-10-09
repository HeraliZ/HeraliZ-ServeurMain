package fr.heralisz.camo.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import fr.heralisz.camo.Camo;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Collections de camos à vie : {@code <dossier serveur>/heralisz_camo/collections.json}, hors du monde (les camos achetés
 * survivent au changement de carte). uuid → {camos: [ids], recharges: {id: fin du délai (ms)}, jetons: {id: jeton de
 * pose}}. Un id inconnu (camo retiré du catalogue) est gardé tel quel dans le fichier : rien de payé n'est jamais effacé.
 * Écriture atomique (fichier temporaire puis renommage), une seconde après chaque changement et à l'arrêt. Fil serveur.
 */
final class CollectionCamos {
   private static final Logger LOG = LogUtils.getLogger();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
   /** Délai avant la sauvegarde qui suit un changement (ticks) : court (un objet vient d'être consommé), mais groupe les rafales. */
   private static final int DELAI_SAUVEGARDE_TICKS = 20;

   /** Données d'un joueur. */
   static final class Joueur {
      final Set<Camo> camos = EnumSet.noneOf(Camo.class);
      /** Camo -> fin du délai après une mort (heure système, ms). */
      final Map<Camo, Long> recharges = new EnumMap<>(Camo.class);
      /** Camo -> jeton de la seule arme qui le porte en ce moment (absent : sur aucune arme). */
      final Map<Camo, Long> jetons = new EnumMap<>(Camo.class);
      /** Ids inconnus du catalogue, recopiés tels quels. */
      final Set<String> inconnus = new LinkedHashSet<>();
   }

   private static final Map<UUID, Joueur> JOUEURS = new HashMap<>();
   @Nullable
   private static Path fichier;
   private static boolean modifie;
   private static int ticksAvantSauvegarde = -1;

   private CollectionCamos() {
   }

   // ------------------------------------------------------------------ cycle de vie
   static void charger(MinecraftServer server) {
      Path dossier = server.getServerDirectory().toPath().resolve("heralisz_camo");
      fichier = dossier.resolve("collections.json");
      JOUEURS.clear();
      modifie = false;
      ticksAvantSauvegarde = -1;
      try {
         Files.createDirectories(dossier);
         if (Files.exists(fichier)) {
            JsonObject racine = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject joueurs = racine.getAsJsonObject("joueurs");
            if (joueurs != null) {
               for (Map.Entry<String, JsonElement> e : joueurs.entrySet()) {
                  UUID uuid;
                  try {
                     uuid = UUID.fromString(e.getKey());
                  } catch (IllegalArgumentException ex) {
                     continue;
                  }
                  JOUEURS.put(uuid, lire(e.getValue().getAsJsonObject()));
               }
            }
         }
         LOG.info("[Camos] {} collection(s) de camos à vie chargée(s) depuis {}", JOUEURS.size(), fichier);
      } catch (Exception e) {
         LOG.error("[Camos] {} illisible : on repart vide, l'ancien fichier est gardé en .bad", fichier, e);
         try {
            Files.copy(fichier, fichier.resolveSibling("collections.json.bad"), StandardCopyOption.REPLACE_EXISTING);
         } catch (IOException ignored) {
         }
         JOUEURS.clear();
      }
   }

   private static Joueur lire(JsonObject o) {
      Joueur j = new Joueur();
      if (o.has("camos")) {
         for (JsonElement el : o.getAsJsonArray("camos")) {
            String id = el.getAsString();
            Camo c = Camo.byId(id);
            if (c != null) j.camos.add(c);
            else j.inconnus.add(id);
         }
      }
      if (o.has("recharges")) {
         for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("recharges").entrySet()) {
            Camo c = Camo.byId(e.getKey());
            if (c != null) j.recharges.put(c, e.getValue().getAsLong());
         }
      }
      if (o.has("jetons")) {
         for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("jetons").entrySet()) {
            Camo c = Camo.byId(e.getKey());
            if (c != null) j.jetons.put(c, e.getValue().getAsLong());
         }
      }
      return j;
   }

   /** À chaque tick serveur : la sauvegarde rapide qui suit un changement. */
   static void tick() {
      if (fichier != null && ticksAvantSauvegarde >= 0 && --ticksAvantSauvegarde < 0 && modifie) {
         sauvegarder();
      }
   }

   static void arreter() {
      if (fichier != null && modifie) sauvegarder();
      fichier = null;
      JOUEURS.clear();
   }

   /** Programme la sauvegarde (une seconde plus tard). */
   static void changer() {
      modifie = true;
      if (ticksAvantSauvegarde < 0) ticksAvantSauvegarde = DELAI_SAUVEGARDE_TICKS;
   }

   private static void sauvegarder() {
      if (fichier == null) return;
      long now = System.currentTimeMillis();
      JsonObject racine = new JsonObject();
      racine.addProperty("version", 1);
      JsonObject joueurs = new JsonObject();
      for (Map.Entry<UUID, Joueur> e : JOUEURS.entrySet()) {
         Joueur j = e.getValue();
         j.recharges.values().removeIf(fin -> fin <= now);
         if (j.camos.isEmpty() && j.inconnus.isEmpty()) continue;
         JsonObject o = new JsonObject();
         JsonArray camos = new JsonArray();
         for (Camo c : j.camos) camos.add(c.id);
         for (String id : j.inconnus) camos.add(id);
         o.add("camos", camos);
         if (!j.recharges.isEmpty()) {
            JsonObject r = new JsonObject();
            j.recharges.forEach((c, fin) -> r.addProperty(c.id, fin));
            o.add("recharges", r);
         }
         if (!j.jetons.isEmpty()) {
            JsonObject t = new JsonObject();
            j.jetons.forEach((c, jeton) -> t.addProperty(c.id, jeton));
            o.add("jetons", t);
         }
         joueurs.add(e.getKey().toString(), o);
      }
      racine.add("joueurs", joueurs);
      Path tmp = fichier.resolveSibling("collections.json.tmp");
      try {
         Files.createDirectories(fichier.getParent());
         Files.writeString(tmp, GSON.toJson(racine), StandardCharsets.UTF_8);
         try {
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (IOException atomique) {
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING);
         }
         modifie = false;
      } catch (IOException e) {
         LOG.error("[Camos] sauvegarde impossible : {}", fichier, e);
      }
   }

   // ------------------------------------------------------------------ accès
   /** Données du joueur (créées à la volée). */
   static Joueur get(UUID joueur) {
      return JOUEURS.computeIfAbsent(joueur, u -> new Joueur());
   }

   /** Données du joueur, ou null s'il n'a jamais rien eu. */
   @Nullable
   static Joueur trouver(UUID joueur) {
      return JOUEURS.get(joueur);
   }
}
