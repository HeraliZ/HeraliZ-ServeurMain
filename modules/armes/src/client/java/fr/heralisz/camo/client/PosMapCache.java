package fr.heralisz.camo.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import fr.heralisz.camo.HeraliszCamo;
import fr.heralisz.camo.knife.KnifeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Une carte de positions par modèle d'arme, vidée au rechargement des ressources. Elle n'est jamais calculée pendant une
 * image (un skin de couteau pris en main la première fois figeait le jeu : carte 1024², 50 à 150 ms d'un coup) :
 * <ol>
 *    <li>premier affichage avec camo : le modèle est relevé sur le fil de rendu (pièces au repos ; texture de l'arme
 *    seulement si elle a des pièces lumineuses), une nouvelle demande par image au plus ;</li>
 *    <li>le calcul part sur le fil « HeraliZ-camo-carte » (un calcul à la fois, un seul par forme : les skins d'un même
 *    couteau attendent le même) ;</li>
 *    <li>la carte est envoyée à la carte graphique au début d'une image suivante (une par image), puis rangée.</li>
 * </ol>
 * En attendant, {@link #get} rend null : l'arme est dessinée sans camo pendant quelques images. Les armes skinnées du
 * joueur sont demandées à l'avance (PrechauffageCamo). Les tables de cette classe ne sont lues et écrites que par le fil de
 * rendu ; le fil de calcul ne reçoit que des copies (PosMap.Releve) et rend son résultat par la file PRETES.
 */
@Mod.EventBusSubscriber(modid = HeraliszCamo.MODID, value = Dist.CLIENT)
public final class PosMapCache {
   private static final Logger LOG = LogUtils.getLogger();
   private static final Map<BedrockModel, Map<String, PosMap>> CACHE = new WeakHashMap<>();
   private static final Map<BedrockModel, Boolean> FAILED = new WeakHashMap<>();
   /** Cartes demandées, pas encore prêtes (modèle -> genres : « gun » ou id du couteau) : une seule demande à la fois. */
   private static final Map<BedrockModel, Set<String>> DEMANDES = new WeakHashMap<>();
   /** Calculs en cours, par empreinte de forme : modèles (et genres) qui attendent chacun d'eux. */
   private static final Map<Long, List<Attente>> EN_COURS = new HashMap<>();
   /** Cartes calculées, à envoyer à la carte graphique : remplie par le fil de calcul, vidée par le fil de rendu. */
   private static final Queue<Resultat> PRETES = new ConcurrentLinkedQueue<>();
   /** Un seul fil, un peu moins prioritaire que le jeu : une carte à la fois. */
   private static final ExecutorService CALCUL = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "HeraliZ-camo-carte");
      t.setDaemon(true);
      t.setPriority(Thread.NORM_PRIORITY - 1);
      return t;
   });
   /**
    * Numéro du rechargement des ressources (écrit par le fil de rendu, lu par le fil de calcul) : un calcul demandé avant un
    * rechargement n'est pas fait, ou son résultat est jeté.
    */
   private static volatile int generation;
   /** Numéro de l'image en cours et image de la dernière demande : une nouvelle demande par image au plus. */
   private static long image, imageDemande = -1;

   private record Attente(BedrockModel model, String kind) {}

   private record Resultat(long cle, int gen, @Nullable PosMap.Preparee prep, @Nullable Throwable erreur, long releveMs, long calculMs) {}

   /** @param knife type de couteau (rôles lame / manche / métal), ou null pour une arme à feu */
   @Nullable
   public static PosMap get(BedrockModel model, @Nullable KnifeType knife) {
      return get(model, knife, null);
   }

   /**
    * Carte du modèle si elle est prête ; sinon null (demandée si besoin : l'arme est dessinée sans camo en attendant).
    * @param texture texture de l'arme (relue une fois au relevé si l'arme a des pièces lumineuses : faces lumineuses vives
    *                gardées, faces ternes des pièces lumineuses camouflées), ou null
    */
   @Nullable
   public static PosMap get(BedrockModel model, @Nullable KnifeType knife, @Nullable ResourceLocation texture) {
      if (!RenderSystem.isOnRenderThread()) {
         return null;
      }
      String kind = kind(knife);
      Map<String, PosMap> perKind = CACHE.get(model);
      PosMap map = perKind == null ? null : perKind.get(kind);
      if (map != null || FAILED.containsKey(model) || demandee(model, kind) || imageDemande == image) {
         return map;
      }
      imageDemande = image;
      return demander(model, knife, kind, texture);
   }

   /**
    * Préchauffage : demande la carte si elle n'est ni prête, ni impossible, ni déjà demandée, et si aucune demande n'a été
    * faite dans cette image. Vrai si une demande est partie.
    */
   public static boolean prechauffer(BedrockModel model, @Nullable KnifeType knife, @Nullable ResourceLocation texture) {
      if (!RenderSystem.isOnRenderThread() || !pasPrete(model, knife) || demandee(model, kind(knife)) || imageDemande == image) {
         return false;
      }
      get(model, knife, texture);
      return true;
   }

   /** Vrai tant que la carte n'est ni prête ni impossible (à demander, ou en calcul) : une photo d'icône attend. */
   public static boolean pasPrete(BedrockModel model, @Nullable KnifeType knife) {
      Map<String, PosMap> perKind = CACHE.get(model);
      return (perKind == null || !perKind.containsKey(kind(knife))) && !FAILED.containsKey(model);
   }

   /** Vrai si un calcul de carte est en cours ou attend son envoi (le préchauffage attend qu'il soit fini). */
   public static boolean occupe() {
      return !EN_COURS.isEmpty() || !PRETES.isEmpty();
   }

   private static String kind(@Nullable KnifeType knife) {
      return knife == null ? "gun" : knife.id;
   }

   private static boolean demandee(BedrockModel model, String kind) {
      Set<String> kinds = DEMANDES.get(model);
      return kinds != null && kinds.contains(kind);
   }

   /** Relevé du modèle (fil de rendu) puis calcul à part ; rend la carte tout de suite si une carte de même forme est prête. */
   @Nullable
   private static PosMap demander(BedrockModel model, @Nullable KnifeType knife, String kind, @Nullable ResourceLocation texture) {
      try {
         long t = System.nanoTime();
         PosMap.Releve releve = PosMap.relever(model, knife, texture);
         PosMap partagee = PosMap.partagee(releve.cle);
         if (partagee != null) {
            // même forme qu'une carte prête (autre skin du même couteau, variante d'une arme) : rien à calculer
            ranger(model, kind, partagee);
            return partagee;
         }
         long releveMs = (System.nanoTime() - t) / 1_000_000;
         List<Attente> attentes = EN_COURS.get(releve.cle);
         if (attentes == null) {
            int gen = generation;
            CALCUL.execute(() -> calculer(releve, gen, releveMs));
            attentes = new ArrayList<>(2);
            EN_COURS.put(releve.cle, attentes);
         }
         attentes.add(new Attente(model, kind));
         DEMANDES.computeIfAbsent(model, m -> new HashSet<>(2)).add(kind);
      } catch (RuntimeException e) {
         FAILED.put(model, Boolean.TRUE);
         LOG.warn("Camo : impossible de préparer ce modèle d'arme", e);
      }
      return null;
   }

   /** Fil de calcul : la carte, sans rien lire du jeu ; le résultat (ou l'erreur) part toujours dans PRETES. */
   private static void calculer(PosMap.Releve releve, int gen, long releveMs) {
      if (gen != generation) {
         return;   // ressources rechargées entre-temps : plus personne n'attend ce calcul
      }
      long t = System.nanoTime();
      PosMap.Preparee prep = null;
      Throwable erreur = null;
      try {
         prep = PosMap.preparer(releve);
      } catch (RuntimeException | Error e) {
         // jamais une demande sans réponse : le modèle passe en échec, il est dessiné sans camo
         erreur = e;
      }
      PRETES.add(new Resultat(releve.cle, gen, prep, erreur, releveMs, (System.nanoTime() - t) / 1_000_000));
   }

   /** Début de chaque image : une carte calculée au plus est envoyée et rangée ; celles d'avant un rechargement sont jetées. */
   @SubscribeEvent
   public static void onRenderTick(TickEvent.RenderTickEvent event) {
      if (event.phase != TickEvent.Phase.START) {
         return;
      }
      image++;
      Resultat res;
      while ((res = PRETES.poll()) != null) {
         if (res.gen() == generation) {
            publier(res);
            return;
         }
         if (res.prep() != null) {
            res.prep().liberer();
         }
      }
   }

   private static void publier(Resultat res) {
      List<Attente> attentes = EN_COURS.remove(res.cle());
      PosMap map = null;
      Throwable erreur = res.erreur();
      long t = System.nanoTime();
      if (res.prep() != null) {
         try {
            map = PosMap.televerser(res.prep(), res.cle());
         } catch (RuntimeException e) {
            erreur = e;
         }
      }
      if (attentes != null) {
         for (Attente a : attentes) {
            Set<String> kinds = DEMANDES.get(a.model());
            if (kinds != null) {
               kinds.remove(a.kind());
               if (kinds.isEmpty()) {
                  DEMANDES.remove(a.model());
               }
            }
            if (map != null) {
               ranger(a.model(), a.kind(), map);
            } else {
               FAILED.put(a.model(), Boolean.TRUE);
            }
         }
      }
      if (map != null) {
         LOG.debug("Carte de positions camo {}² prête : relevé {} ms, calcul à part {} ms, envoi {} ms", map.size, res.releveMs(),
            res.calculMs(), (System.nanoTime() - t) / 1_000_000);
      } else {
         LOG.warn("Camo : impossible de préparer ce modèle d'arme", erreur);
      }
   }

   private static void ranger(BedrockModel model, String kind, PosMap map) {
      CACHE.computeIfAbsent(model, m -> new HashMap<>(2)).put(kind, map);
   }

   public static void clear() {
      if (!RenderSystem.isOnRenderThread()) {
         RenderSystem.recordRenderCall(PosMapCache::clear);
         return;
      }
      // calculs en cours : abandonnés (pas faits, ou résultat jeté à l'arrivée et texels libérés)
      generation++;
      EN_COURS.clear();
      DEMANDES.clear();
      CACHE.values().forEach(m -> m.values().forEach(PosMap::release));
      CACHE.clear();
      FAILED.clear();
      PosMap.libererPartagees();
      CamoRenderType.clear();
      CamoFx.clearCache();
      BagCamo.clear();
      IconesArmes.vider();
      IconesPack.vider();
   }

   private PosMapCache() {}
}
