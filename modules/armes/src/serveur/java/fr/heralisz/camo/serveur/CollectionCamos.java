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
 * <p>Fichier illisible au démarrage (audit du 02/11, point 30) : il n'est JAMAIS réécrit tant que le serveur tourne
 * (avant : le premier camo ajouté écrivait une liste presque vide à sa place, et au redémarrage suivant, le fichier
 * redevenu « lisible », tous les camos à vie posés étaient retirés des armes). Copie datée à côté
 * ({@code collections.json.illisible-<date>}), les changements de la session vont dans
 * {@code collections.json.secours-<date>} (à reprendre à la main), aucune arme n'est dépouillée, et le staff connecté
 * est prévenu (journal et message aux op).</p>
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
   /** Faux si le fichier était illisible au démarrage : les armes des propriétaires ne sont alors jamais dépouillées. */
   private static boolean fiable = true;
   /** Fichier illisible : les changements de cette session sont écrits ici, jamais dans le fichier principal. */
   @Nullable
   private static Path secours;

   private CollectionCamos() {
   }

   // ------------------------------------------------------------------ cycle de vie
   static void charger(MinecraftServer server) {
      Path dossier = server.getServerDirectory().toPath().resolve("heralisz_camo");
      fichier = dossier.resolve("collections.json");
      JOUEURS.clear();
      modifie = false;
      ticksAvantSauvegarde = -1;
      fiable = true;
      secours = null;
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
         String date = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
         secours = fichier.resolveSibling("collections.json.secours-" + date);
         LOG.error("[Camos] {} ILLISIBLE : il ne sera pas réécrit ; copie datée gardée à côté, changements de cette session"
            + " dans {} (à reprendre à la main), aucun camo à vie n'est retiré des armes. Réparer le fichier puis redémarrer",
            fichier, secours.getFileName(), e);
         fiable = false;
         try {
            Files.copy(fichier, fichier.resolveSibling("collections.json.illisible-" + date), StandardCopyOption.REPLACE_EXISTING);
         } catch (IOException ex) {
            LOG.error("[Camos] copie de {} impossible : {}", fichier, ex.toString());
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
      // fichier illisible au démarrage : jamais réécrit, la session va dans le fichier de secours
      Path cible = fiable ? fichier : secours;
      if (cible == null) return;
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
      Path tmp = cible.resolveSibling(cible.getFileName() + ".tmp");
      try {
         Files.createDirectories(cible.getParent());
         Files.writeString(tmp, GSON.toJson(racine), StandardCharsets.UTF_8);
         try {
            Files.move(tmp, cible, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (IOException atomique) {
            Files.move(tmp, cible, StandardCopyOption.REPLACE_EXISTING);
         }
         modifie = false;
      } catch (IOException e) {
         LOG.error("[Camos] sauvegarde impossible : {}", cible, e);
      }
   }

   /** Vrai si les collections ont été lues correctement (sinon : aucune arme n'est dépouillée de son camo à vie). */
   static boolean fiable() {
      return fiable;
   }

   /** Un op (niveau 2) qui se connecte alors que le fichier était illisible au démarrage est prévenu. */
   static void prevenirStaff(net.minecraft.server.level.ServerPlayer p) {
      if (fiable || fichier == null || !p.hasPermissions(2)) return;
      p.sendSystemMessage(net.minecraft.network.chat.Component.translatableWithFallback("msg.heralisz_camo.fichier_illisible",
         "[Camos] Le fichier des camos à vie (%s) était illisible au démarrage : il n'est pas réécrit, rien n'est retiré des armes. Voir le journal du serveur.",
         fichier.toString()).withStyle(net.minecraft.ChatFormatting.RED));
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

   /**
    * Wipe de la réouverture (05/11) : la collection entière (camos et skins à vie, ids inconnus du catalogue compris) de
    * chaque joueur pour qui {@code epargne} est faux est effacée, et le fichier réécrit tout de suite. Renvoie le nombre
    * de collections effacées ; -1 si le fichier était illisible au démarrage (rien n'est touché).
    */
   static int oublierSauf(java.util.function.Predicate<UUID> epargne) {
      if (!fiable || fichier == null) return -1;
      int n = 0;
      for (java.util.Iterator<UUID> it = JOUEURS.keySet().iterator(); it.hasNext(); ) {
         if (!epargne.test(it.next())) {
            it.remove();
            n++;
         }
      }
      if (n > 0) {
         modifie = true;
         sauvegarder();
      }
      LOG.warn("[Camos] wipe de la réouverture : {} collection(s) de camos à vie effacée(s)", n);
      return n;
   }
}
