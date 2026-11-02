package fr.heraliz.noyau.serveur.interactions;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * Clic droit sur un bloc avec un objet de mod en main (31/10, demande du propriétaire : on ne fermait pas une porte avec
 * une arme, un soin ou de la nourriture en main ; manger ou se soigner en visant un bloc donnait un geste sans fin).
 * <p><b>Cause</b> (lue dans WorldGuard 7.0.9, {@code EventAbstractionListener.onPlayerInteract} et {@code Materials}) :
 * sur le serveur hybride (Mohist), chaque clic droit sur un bloc passe aussi par l'événement Bukkit
 * {@code PlayerInteractEvent}. WorldGuard y range chaque objet par son type ; il connaît tous ceux de Minecraft, mais
 * pas ceux des mods (aucune fiche) : il les croit capables de modifier le bloc visé (comme un seau ou une pelle), et les
 * soumet au droit de CONSTRUIRE de la région. Les joueurs, qui ne construisent pas dans les régions de la carte (les op,
 * si), voient alors : le bloc refusé (porte qui revient, conteneur qui ne s'ouvre pas) ET l'objet refusé. Ce dernier
 * refus est gardé par le serveur pour le paquet « utiliser l'objet » qui suit aussitôt (Bukkit, {@code firedInteract}) :
 * le jeu du joueur commence à manger ou à soigner, le serveur jamais. Dans les airs, aucun refus : on mange.</p>
 * <p><b>Correction</b> : pour un objet de mod qui n'agit jamais sur un bloc ({@link ObjetsNeutres} : armes de TaCZ,
 * mêlée, soins, nourriture...), l'objet est caché à l'événement Bukkit le temps qu'il passe chez les plugins (priorité
 * LOWEST, rendu en MONITOR). WorldGuard juge donc le clic comme une main vide : la porte s'ouvre là où une main vide
 * l'ouvre (drapeaux interact / use de la région, inchangés), et l'objet n'est plus refusé. Les objets qui posent ou
 * modifient un bloc (blocs, seaux, outils de CRac, objets qui agissent sur le bloc visé) et ceux de Minecraft restent
 * jugés par WorldGuard comme avant. Rien ne change pour les règles de nos mods (événements de Forge : bases, salles à
 * carte, conteneurs scellés...).</p>
 * <p>Écouteurs Bukkit créés par réflexion (aucune dépendance de compilation), rattachés à un plugin déjà chargé (le mod
 * n'est pas un plugin), comme le chat de HeraliZ Grades. Sans Bukkit (solo, Forge seul) : rien. Coût : deux appels
 * par clic droit sur un bloc.</p>
 */
public final class ClicObjetBukkit {
   private static final Logger LOG = LogUtils.getLogger();
   /** Plugins qui peuvent porter les écouteurs, dans l'ordre de préférence. */
   private static final String[] PORTEURS = {"WorldGuard", "LuckPerms", "AuthMe"};
   /** Objets cachés aux plugins pendant l'événement en cours (fil serveur), rendus en MONITOR. */
   private static final Map<Object, Object> CACHES = Collections.synchronizedMap(new IdentityHashMap<>());

   private static boolean fini;
   private static MinecraftServer serveur;
   private static Field champObjet;
   private static Method getAction, getHand, getPlayer, getUniqueId;

   private ClicObjetBukkit() {
   }

   public static void enregistrer() {
      MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> brancher(e.getServer()));
      // plugins pas encore prêts au démarrage : nouvel essai à la première connexion
      MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
         if (!fini && e.getEntity() instanceof ServerPlayer p) brancher(p.server);
      });
   }

   private static synchronized void brancher(MinecraftServer srv) {
      if (fini) return;
      Class<?> bukkit;
      try {
         bukkit = Class.forName("org.bukkit.Bukkit", false, MinecraftServer.class.getClassLoader());
      } catch (ClassNotFoundException | LinkageError ex) {
         fini = true;   // pas de partie Bukkit : WorldGuard n'existe pas ici
         return;
      }
      try {
         ClassLoader cl = bukkit.getClassLoader();
         Class<?> ecouteur = Class.forName("org.bukkit.event.Listener", false, cl);
         Class<?> executeur = Class.forName("org.bukkit.plugin.EventExecutor", false, cl);
         Class<?> priorite = Class.forName("org.bukkit.event.EventPriority", false, cl);
         Class<?> plugin = Class.forName("org.bukkit.plugin.Plugin", false, cl);
         Class<?> evenement = Class.forName("org.bukkit.event.player.PlayerInteractEvent", false, cl);
         Class<?> objetBukkit = Class.forName("org.bukkit.inventory.ItemStack", false, cl);
         Object pm = bukkit.getMethod("getPluginManager").invoke(null);
         Object proprio = porteur(pm);
         if (proprio == null) return;   // nouvel essai plus tard
         Field champ = null;
         for (Field f : evenement.getDeclaredFields()) {
            if (f.getType() == objetBukkit) {
               champ = f;
               break;
            }
         }
         if (champ == null) throw new NoSuchFieldException("PlayerInteractEvent : aucun champ d'objet");
         champ.setAccessible(true);
         champObjet = champ;
         getAction = evenement.getMethod("getAction");
         getHand = evenement.getMethod("getHand");
         getPlayer = evenement.getMethod("getPlayer");
         getUniqueId = Class.forName("org.bukkit.entity.Entity", false, cl).getMethod("getUniqueId");
         serveur = srv;
         Object e = Proxy.newProxyInstance(cl, new Class<?>[]{ecouteur}, (px, m, a) -> objet(px, m, a));
         Object cacher = Proxy.newProxyInstance(cl, new Class<?>[]{executeur}, (px, m, a) -> {
            if (m.getName().equals("execute") && a != null && a.length == 2 && evenement.isInstance(a[1])) {
               cacher(a[1]);
               return null;
            }
            return objet(px, m, a);
         });
         Object rendre = Proxy.newProxyInstance(cl, new Class<?>[]{executeur}, (px, m, a) -> {
            if (m.getName().equals("execute") && a != null && a.length == 2 && evenement.isInstance(a[1])) {
               rendre(a[1]);
               return null;
            }
            return objet(px, m, a);
         });
         Method enregistrer = pm.getClass().getMethod("registerEvent", Class.class, ecouteur, priorite, executeur, plugin, boolean.class);
         @SuppressWarnings({"unchecked", "rawtypes"})
         Object basse = Enum.valueOf((Class) priorite, "LOWEST");
         @SuppressWarnings({"unchecked", "rawtypes"})
         Object derniere = Enum.valueOf((Class) priorite, "MONITOR");
         // ignoreCancelled = false : l'objet doit toujours revenir, même si un plugin a annulé le clic entre-temps
         enregistrer.invoke(pm, evenement, e, basse, cacher, proprio, false);
         enregistrer.invoke(pm, evenement, e, derniere, rendre, proprio, false);
         fini = true;
         LOG.info("[noyau] clic droit avec un objet de mod : jugé par les plugins comme une main vide (portes, manger, soins)");
      } catch (Throwable ex) {
         fini = true;
         champObjet = null;
         LOG.warn("[noyau] clic droit avec un objet de mod : correctif WorldGuard impossible à brancher ({})", ex.toString());
      }
   }

   /** LOWEST : objet de mod sans effet sur les blocs, sur un clic droit de bloc : caché aux plugins. */
   private static void cacher(Object ev) {
      try {
         if (!(getAction.invoke(ev) instanceof Enum<?> action) || !"RIGHT_CLICK_BLOCK".equals(action.name())) return;
         Object objet = champObjet.get(ev);
         if (objet == null) return;
         ServerPlayer sp = joueur(ev);
         if (sp == null) return;
         InteractionHand main = getHand.invoke(ev) instanceof Enum<?> h && "OFF_HAND".equals(h.name())
               ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
         if (!ObjetsNeutres.neutre(sp.getItemInHand(main))) return;
         CACHES.put(ev, objet);
         champObjet.set(ev, null);
      } catch (Throwable ex) {
         Object objet = CACHES.remove(ev);
         if (objet != null) remettre(ev, objet);
      }
   }

   /** MONITOR : l'objet revient dans l'événement (Mohist et les derniers lecteurs le retrouvent tel quel). */
   private static void rendre(Object ev) {
      Object objet = CACHES.remove(ev);
      if (objet != null) remettre(ev, objet);
   }

   private static void remettre(Object ev, Object objet) {
      try {
         champObjet.set(ev, objet);
      } catch (Throwable ex) {
         LOG.warn("[noyau] objet du clic droit impossible à remettre dans l'événement Bukkit ({})", ex.toString());
      }
   }

   @Nullable
   private static ServerPlayer joueur(Object ev) throws ReflectiveOperationException {
      Object bp = getPlayer.invoke(ev);
      if (bp == null || serveur == null || !(getUniqueId.invoke(bp) instanceof UUID u)) return null;
      return serveur.getPlayerList().getPlayer(u);
   }

   @Nullable
   private static Object porteur(Object pm) throws ReflectiveOperationException {
      Method getPlugin = pm.getClass().getMethod("getPlugin", String.class);
      for (String nom : PORTEURS) {
         Object p = getPlugin.invoke(pm, nom);
         if (p != null && Boolean.TRUE.equals(p.getClass().getMethod("isEnabled").invoke(p))) return p;
      }
      return null;
   }

   private static Object objet(Object px, Method m, Object[] a) {
      return switch (m.getName()) {
         case "hashCode" -> System.identityHashCode(px);
         case "equals" -> a != null && a.length == 1 && px == a[0];
         case "toString" -> "HeraliZ Noyau (clic droit avec un objet de mod)";
         default -> null;
      };
   }
}
