package fr.heraliz.noyau.serveur.perf;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import fr.heraliz.noyau.NoyauConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import org.slf4j.Logger;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Premières fois après un démarrage (19/11, audit des lenteurs, point C7 ; F7 de l'audit des réglages) : une classe lue
 * pour la première fois l'est sur le fil qui en a besoin (lecture du jar, décompression, mixins, traduction des noms par
 * Mohist pour un plugin). Les premiers ticks après « Done » et la première connexion le payaient : {@code VueTableau},
 * {@code MatchApi}, classes de Spartan et de la voix (272 et 227 ms le 19/11), 1,4 s à la première connexion (Mohist qui
 * traduit LuckPerms, AuthMe, WorldGuard), 0,2 à 0,8 s au premier WorldEdit.
 * <ul>
 *    <li><b>Classes des mods</b> ({@code [perf] classes_prechargees}) : dès {@code ServerAboutToStart}, un fil à part, de
 *    priorité basse, lit toutes les classes des jars de nos mods (ceux qui portent {@code LICENCE-HeraliZ.txt} ; jamais
 *    les mods des autres auteurs, ni Minecraft, Forge ou Mohist) : {@code Class.forName(nom, false, chargeur)}, c'est-à-dire
 *    lues et préparées (mixins compris) mais jamais lancées (aucun bloc {@code static} joué). Les paquets de mixins (lus
 *    dans les configurations de chaque jar) sont sautés. Une classe qui refuse de se charger (mod facultatif absent, classe
 *    du jeu du joueur) est comptée et laissée.</li>
 *    <li><b>Plugins</b> ({@code [perf] plugins_prechauffes}) : 20 s après « Done », les plugins nommés sont relevés sur le
 *    fil du serveur (plugin, son chargeur, son jar ; et le chargeur interne d'un plugin qui charge son vrai jar lui-même,
 *    comme LuckPerms), puis un fil à part lit leurs classes par leur propre chargeur, quelques-unes à la fois : Mohist les
 *    traduit alors avant l'arrivée des joueurs.</li>
 * </ul>
 * Rien ne tourne sur le fil du serveur à part le relevé des plugins. Arrêt du serveur : les fils s'arrêtent.
 */
public final class Prechauffage {
   private Prechauffage() {
   }

   private static final Logger LOG = LogUtils.getLogger();
   /** Délai entre « Done » et le préchauffage des plugins (ticks) : 20 s. */
   private static final int DELAI_PLUGINS = 400;
   private static final Set<String> JAMAIS = Set.of("minecraft", "forge", "mohist");

   private static volatile boolean arret;
   private static volatile String etatClasses = "pas lancé", etatPlugins = "pas lancé";
   private static volatile long classes, echecs, plugins, classesPlugins, echecsPlugins;
   private static long tickPlugins = -1;

   public static void enregistrer() {
      MinecraftForge.EVENT_BUS.addListener((ServerAboutToStartEvent e) -> {
         arret = false;
         if (NoyauConfig.classesPrechargees) lancer("HeraliZ-classes", Prechauffage::classesDesMods);
      });
      MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> tickPlugins = e.getServer().getTickCount() + DELAI_PLUGINS);
      MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
         if (tickPlugins >= 0 && e.phase == TickEvent.Phase.END && e.getServer().getTickCount() >= tickPlugins) {
            tickPlugins = -1;
            plugins(e.getServer());
         }
      });
      MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
         arret = true;
         tickPlugins = -1;
      });
   }

   /** Une ligne pour {@code /diag etat}. */
   public static String etat() {
      return String.format(Locale.ROOT, "Préchauffage du démarrage : classes des mods %s (%d lues, %d refusées) ; plugins %s (%d plugins, %d classes, %d refusées)%n",
            etatClasses, classes, echecs, etatPlugins, plugins, classesPlugins, echecsPlugins);
   }

   private static void lancer(String nom, Runnable travail) {
      Thread t = new Thread(() -> {
         try {
            travail.run();
         } catch (Throwable ex) {
            LOG.warn("[noyau] préchauffage {} arrêté : {}", nom, ex.toString());
         }
      }, nom);
      t.setDaemon(true);
      t.setPriority(Thread.MIN_PRIORITY);
      t.start();
   }

   // ------------------------------------------------------------------ classes des mods
   private static void classesDesMods() {
      long debut = System.nanoTime();
      etatClasses = "en cours";
      ClassLoader chargeur = Prechauffage.class.getClassLoader();
      List<Path> nos = new ArrayList<>();
      for (IModFileInfo f : ModList.get().getModFiles()) {
         boolean jamais = false;
         for (IModInfo m : f.getMods()) jamais |= JAMAIS.contains(m.getModId());
         if (jamais) continue;
         Path jar = f.getFile().getFilePath();
         if (jar == null || !Files.isRegularFile(jar)) continue;
         Path licence = f.getFile().findResource("LICENCE-HeraliZ.txt");
         if (licence != null && Files.exists(licence)) nos.add(jar);   // nos mods seulement
      }
      int jars = 0;
      for (Path jar : nos) if (lireJar(jar, chargeur, false)) jars++;
      etatClasses = arret ? "arrêté" : String.format(Locale.ROOT, "fait en %.1f s (%d jars)", (System.nanoTime() - debut) / 1e9, jars);
      LOG.info("[noyau] préchauffage : {} classes des mods lues à l'avance ({} refusées, {} jars, {} s)", classes, echecs, jars,
            Math.round((System.nanoTime() - debut) / 1e9));
   }

   /** Lit les classes d'un jar par {@code chargeur} ; vrai si le jar a été parcouru. */
   private static boolean lireJar(Path jar, ClassLoader chargeur, boolean plugin) {
      try (JarFile j = new JarFile(jar.toFile())) {
         List<String> sauter = plugin ? List.of() : paquetsMixins(j);
         int n = 0;
         Enumeration<JarEntry> it = j.entries();
         while (it.hasMoreElements()) {
            if (arret) return false;
            String e = it.nextElement().getName();
            if (!e.endsWith(".class") || e.startsWith("META-INF/") || e.endsWith("module-info.class") || e.endsWith("package-info.class")) continue;
            String nom = e.substring(0, e.length() - 6).replace('/', '.');
            boolean mixin = false;
            for (String p : sauter) mixin |= nom.startsWith(p);
            if (mixin) continue;
            try {
               Class.forName(nom, false, chargeur);
               if (plugin) classesPlugins++;
               else classes++;
            } catch (Throwable refus) {
               if (plugin) echecsPlugins++;
               else echecs++;
            }
            if (++n % (plugin ? 20 : 50) == 0) Thread.sleep(plugin ? 2 : 1);   // laisser le processeur au serveur
         }
         return true;
      } catch (InterruptedException ex) {
         Thread.currentThread().interrupt();
         return false;
      } catch (Exception ex) {
         LOG.debug("[noyau] préchauffage : {} illisible ({})", jar.getFileName(), ex.toString());
         return false;
      }
   }

   /** Paquets des mixins du jar (configurations nommées par le manifeste) : jamais chargés à la main. */
   private static List<String> paquetsMixins(JarFile j) {
      List<String> l = new ArrayList<>();
      try {
         Manifest m = j.getManifest();
         String configs = m == null ? null : m.getMainAttributes().getValue("MixinConfigs");
         if (configs == null) return l;
         for (String c : configs.split(",")) {
            JarEntry e = j.getJarEntry(c.trim());
            if (e == null) continue;
            try (InputStreamReader r = new InputStreamReader(j.getInputStream(e), StandardCharsets.UTF_8)) {
               JsonElement racine = JsonParser.parseReader(r);
               if (racine.isJsonObject()) {
                  JsonObject o = racine.getAsJsonObject();
                  if (o.has("package")) l.add(o.get("package").getAsString() + ".");
               }
            }
         }
      } catch (Exception ex) {
         l.add("");   // configuration illisible : par prudence, aucune classe de ce jar
      }
      return l;
   }

   // ------------------------------------------------------------------ plugins
   /** Fil du serveur : relève les plugins nommés (plugin, chargeurs, jars), puis les fait lire par un fil à part. */
   private static void plugins(MinecraftServer srv) {
      List<? extends String> noms = NoyauConfig.liste(NoyauConfig.PLUGINS_PRECHAUFFES, NoyauConfig.PLUGINS_PRECHAUFFES_DEFAUT);
      if (noms.isEmpty()) return;
      Map<ClassLoader, List<Path>> aLire = new LinkedHashMap<>();
      int trouves = 0;
      try {
         Class<?> bukkit = Class.forName("org.bukkit.Bukkit", false, MinecraftServer.class.getClassLoader());
         Object pm = bukkit.getMethod("getPluginManager").invoke(null);
         java.lang.reflect.Method plugin = pm.getClass().getMethod("getPlugin", String.class);
         for (String nom : noms) {
            Object p = plugin.invoke(pm, nom);
            if (p == null) continue;
            trouves++;
            ClassLoader cl = p.getClass().getClassLoader();
            Path jar = jarDe(p.getClass());
            if (cl != null && jar != null) aLire.computeIfAbsent(cl, k -> new ArrayList<>()).add(jar);
            internes(p, cl, aLire);
         }
      } catch (ClassNotFoundException | LinkageError absent) {
         etatPlugins = "sans Bukkit";
         return;
      } catch (Exception ex) {
         LOG.warn("[noyau] préchauffage des plugins impossible : {}", ex.toString());
         etatPlugins = "impossible";
         return;
      }
      plugins = trouves;
      if (aLire.isEmpty()) {
         etatPlugins = "aucun jar trouvé";
         return;
      }
      etatPlugins = "en cours";
      lancer("HeraliZ-plugins", () -> {
         long debut = System.nanoTime();
         for (Map.Entry<ClassLoader, List<Path>> e : aLire.entrySet()) {
            for (Path jar : e.getValue()) lireJar(jar, e.getKey(), true);
         }
         etatPlugins = arret ? "arrêté" : String.format(Locale.ROOT, "fait en %.1f s", (System.nanoTime() - debut) / 1e9);
         LOG.info("[noyau] préchauffage : {} classes de {} plugins lues à l'avance ({} refusées)", classesPlugins, plugins, echecsPlugins);
      });
   }

   /** Le jar d'où vient une classe, ou null. */
   private static Path jarDe(Class<?> c) {
      try {
         java.security.CodeSource s = c.getProtectionDomain().getCodeSource();
         URL u = s == null ? null : s.getLocation();
         if (u == null) return null;
         Path p = Path.of(u.toURI());
         return Files.isRegularFile(p) ? p : null;
      } catch (Exception ex) {
         return null;
      }
   }

   /**
    * Plugin qui charge son vrai jar par un chargeur à lui (LuckPerms) : un champ du plugin dont la valeur vient d'un autre
    * chargeur d'URL ; ses jars sont lus par ce chargeur-là.
    */
   private static void internes(Object plugin, ClassLoader sien, Map<ClassLoader, List<Path>> aLire) {
      for (Class<?> c = plugin.getClass(); c != null && !c.getName().startsWith("org.bukkit."); c = c.getSuperclass()) {
         for (Field f : c.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
            try {
               f.setAccessible(true);
               Object v = f.get(plugin);
               ClassLoader cl = v == null ? null : v.getClass().getClassLoader();
               if (cl instanceof URLClassLoader u && cl != sien && !aLire.containsKey(cl)) {
                  List<Path> jars = new ArrayList<>();
                  for (URL url : u.getURLs()) {
                     try {
                        Path p = Path.of(url.toURI());
                        if (Files.isRegularFile(p)) jars.add(p);
                     } catch (Exception ignore) {
                        // adresse qui n'est pas un fichier : laissée
                     }
                  }
                  if (!jars.isEmpty()) aLire.put(cl, jars);
               }
            } catch (Exception | LinkageError ignore) {
               // champ illisible (module fermé) : laissé
            }
         }
      }
   }
}
