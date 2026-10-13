package fr.heraliz.tactique.serveur;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import fr.heralisz.tactical.config.ServerConfig;
import fr.heraliz.tactique.vitres.Vitres;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * Vitres cassées par les balles de TaCZ, décisions du serveur (fr.heraliz.tactique.vitres.Vitres).
 * <ul>
 *    <li><b>Ce qui casse</b> : le verre au sens de TaCZ ({@link Vitres#estVerre}) et les blocs de la liste
 *    {@code blocs_en_plus} : « bloc » (il disparaît) ou « bloc > version cassée » (fenêtre de meubles {@code cfm:vb_4},
 *    qui devient {@code cfm:vb_5}, même orientation).</li>
 *    <li><b>Casse</b> : sur l'événement public {@code AmmoHitBlockEvent} de TaCZ (une balle touche un bloc, avant que
 *    TaCZ ne la retire), si le bloc casse à cet endroit : l'état complet du bloc est noté (VitresCassees), puis le bloc
 *    devient de l'air (ou sa version cassée), avec les éclats et le bruit du verre, sans objet lâché et SANS prévenir les
 *    blocs voisins (rien ne tombe ni ne se décroche : la carte reste telle quelle autour du trou) ; seules les vitres
 *    fines voisines qui s'y raccordaient perdent leur raccord vers le trou, pour un bord net.</li>
 *    <li><b>Zones où rien ne casse</b> ({@link Raison}) : réglage {@code [vitres] casser} à false, verre gorgé d'eau,
 *    blocs de la liste {@code blocs_proteges}, dimensions protégées (spawn {@code heralisz:spawn}, matchs, zombies,
 *    bunkers), rayon autour du point d'apparition de l'overworld (0 par défaut : le spawn est un monde à part, et le
 *    point d'apparition de la carte est au milieu d'une ville), claims de CRac (lus par réflexion, CRac facultatif). Là,
 *    tout reste comme avant. {@code /vitres pourquoi} (CommandeVitres) dit au staff laquelle s'applique à un bloc.</li>
 *    <li><b>Repousse</b> : chaque seconde, les vitres dont le délai est passé sont reposées dans leur état exact (sans
 *    prévenir les voisins non plus), raccordées aux vitres fines voisines comme dans le jeu (une vitre fine voisine
 *    encore cassée : pas de raccord vers le trou ; elle s'y raccordera en repoussant), si leur chunk est chargé, si leur
 *    place est encore vide (ou tient encore la version cassée) et si personne (joueur, créature, véhicule) ne s'y tient.
 *    Du feu, de la neige ou de l'eau à sa place : elle attend. Un bloc posé à sa place (la vitre reposée à la main
 *    comprise, par un joueur ou par WorldEdit), ou la version cassée enlevée : la main l'emporte, la vitre est oubliée
 *    (avant : elle restait en attente pour toujours et revenait le jour où la place se libérait). Au démarrage du
 *    serveur, les vitres en retard sont reposées tout de suite, chunks chargés exprès (au plus
 *    {@link #MAX_CHARGEMENTS_DEMARRAGE}, les autres au chargement de leur chunk).</li>
 * </ul>
 * Filet : si une vitre a déjà disparu pendant l'événement (DestroyGlass de TaCZ remis à true par erreur), elle est
 * notée quand même pour repousser.
 */
public final class VitresServeur {
   private static final Logger LOG = LogUtils.getLogger();
   /** Événement « bloc cassé » (éclats + bruit du bloc), celui de Level.destroyBlock (LevelEvent.PARTICLES_DESTROY_BLOCK). */
   private static final int EVENEMENT_BLOC_CASSE = 2001;
   /** Ni objet lâché, ni voisins prévenus : seulement le bloc, envoyé aux joueurs. */
   private static final int DRAPEAUX = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
   /** Une passe de repousse toutes les 20 ticks (1 s). */
   private static final int PERIODE_TICKS = 20;
   /** Au démarrage : nombre maximal de vitres en retard dont le chunk est chargé exprès. */
   static final int MAX_CHARGEMENTS_DEMARRAGE = 2048;
   private static final String CRAC_CLAIMS = "fr.heraliz.crac.claim.ClaimManager";

   /** Pourquoi une vitre ne casse pas à un endroit (dans l'ordre où le serveur les regarde). */
   enum Raison {
      /** {@code [vitres] casser = false}. */
      DESACTIVE {
         @Override
         boolean empeche(ServerLevel level, BlockPos pos, BlockState etat) {
            return !lire(ServerConfig.VITRES_CASSER, true);
         }
      },
      /** Verre gorgé d'eau : l'eau se répandrait dans la carte. */
      GORGEE_EAU {
         @Override
         boolean empeche(ServerLevel level, BlockPos pos, BlockState etat) {
            return !etat.getFluidState().isEmpty();
         }
      },
      /** Bloc de la liste {@code blocs_proteges}. */
      BLOC_PROTEGE {
         @Override
         boolean empeche(ServerLevel level, BlockPos pos, BlockState etat) {
            return blocsProteges().contains(BuiltInRegistries.BLOCK.getKey(etat.getBlock()));
         }
      },
      /** Dimension de la liste {@code dimensions_protegees}. */
      DIMENSION {
         @Override
         boolean empeche(ServerLevel level, BlockPos pos, BlockState etat) {
            return dimensionsProtegees().contains(level.dimension().location());
         }
      },
      /** Dans {@code rayon_spawn} autour du point d'apparition de l'overworld. */
      SPAWN {
         @Override
         boolean empeche(ServerLevel level, BlockPos pos, BlockState etat) {
            int rayon = rayonSpawn();
            return rayon > 0 && level.dimension() == Level.OVERWORLD && distanceSpawnCarre(level, pos) <= (long) rayon * rayon;
         }
      },
      /** Dans un claim de CRac ({@code proteger_claims_crac}). */
      CLAIM_CRAC {
         @Override
         boolean empeche(ServerLevel level, BlockPos pos, BlockState etat) {
            return lire(ServerConfig.VITRES_CLAIMS_CRAC, true) && claimCrac(level, pos) != null;
         }
      };

      abstract boolean empeche(ServerLevel level, BlockPos pos, BlockState etat);
   }

   private static int compteur;
   // réglages lus en liste : recalculés seulement quand Forge rend une autre liste (rechargement de la config)
   private static List<? extends String> sourceDimensions;
   private static Set<ResourceLocation> dimensions = Set.of();
   private static List<? extends String> sourceBlocs;
   private static Set<ResourceLocation> blocs = Set.of();
   private static List<? extends String> sourceEnPlus;
   /** blocs_en_plus : bloc -> sa version cassée (Blocks.AIR : il disparaît). */
   private static Map<Block, Block> enPlus = Map.of();
   // CRac : ClaimManager.getAt(ServerLevel, BlockPos), cherché une fois
   private static boolean cracCherche;
   @Nullable
   private static Method cracClaimA;

   private VitresServeur() {
   }

   /** Appelé par la partie serveur (ServeurTactique), pendant le chargement du mod. */
   static void brancher() {
      Vitres.brancherRegle(VitresServeur::cassable);
      // après TaCZ et tout autre écouteur : une balle « annulée » par quelqu'un d'autre ne casse rien
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, AmmoHitBlockEvent.class, VitresServeur::touche);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ServerTickEvent.class, VitresServeur::tick);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerStartedEvent.class, VitresServeur::demarrage);
      // un bloc posé ou cassé à la main à la place d'une vitre en attente : la main l'emporte
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, BlockEvent.EntityPlaceEvent.class, VitresServeur::pose);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, BlockEvent.BreakEvent.class, VitresServeur::casseeALaMain);
      MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> CommandeVitres.enregistrer(e.getDispatcher()));
   }

   // ------------------------------------------------------------------ ce qui casse, et où

   /** Le bloc casse-t-il sous les balles : verre au sens de TaCZ, ou bloc de la liste blocs_en_plus ? */
   static boolean estCible(BlockState etat) {
      return Vitres.estVerre(etat) || blocsEnPlus().containsKey(etat.getBlock());
   }

   /** Version cassée d'un bloc de blocs_en_plus (Blocks.AIR : il disparaît ; null : pas dans la liste). */
   @Nullable
   static Block versionCassee(Block bloc) {
      return blocsEnPlus().get(bloc);
   }

   /**
    * Le bloc casse-t-il ici ? (aussi appelé par le mixin du trajet des balles, pour CHAQUE bloc traversé : tout ce qui
    * n'est pas une vitre est écarté d'abord.)
    */
   static boolean cassable(ServerLevel level, BlockPos pos, BlockState etat) {
      return estCible(etat) && raisons(level, pos, etat, true).isEmpty();
   }

   /** Ce qui empêche ce bloc de casser ici (vide : il casse) ; seulement la première si demandé. */
   static List<Raison> raisons(ServerLevel level, BlockPos pos, BlockState etat, boolean seulementLaPremiere) {
      List<Raison> l = null;
      for (Raison r : Raison.values()) {
         if (r.empeche(level, pos, etat)) {
            if (seulementLaPremiere) {
               return List.of(r);
            }
            if (l == null) {
               l = new ArrayList<>();
            }
            l.add(r);
         }
      }
      return l == null ? List.of() : l;
   }

   /** Ce que devient un bloc touché : de l'air, ou sa version cassée (même orientation, mêmes propriétés communes). */
   static BlockState trou(BlockState etat) {
      Block casse = versionCassee(etat.getBlock());
      if (casse == null || casse == Blocks.AIR) {
         return Blocks.AIR.defaultBlockState();
      }
      return memesProprietes(etat, casse.defaultBlockState());
   }

   @SuppressWarnings({"unchecked", "rawtypes"})
   private static BlockState memesProprietes(BlockState de, BlockState vers) {
      for (Property q : de.getProperties()) {
         Property p = vers.getBlock().getStateDefinition().getProperty(q.getName());
         if (p != null) {
            Optional v = p.getValue(q.getName(de.getValue(q)));
            if (v.isPresent()) {
               vers = vers.setValue(p, (Comparable) v.get());
            }
         }
      }
      return vers;
   }

   // ------------------------------------------------------------------ casse

   private static void touche(AmmoHitBlockEvent e) {
      if (!(e.getLevel() instanceof ServerLevel level)) {
         return;
      }
      // état du bloc noté par TaCZ au moment de l'impact, avant tout écouteur : c'est l'état d'origine
      BlockState avant = e.getState();
      if (!estCible(avant)) {
         return;
      }
      BlockPos pos = e.getHitResult().getBlockPos().immutable();
      BlockState maintenant = level.getBlockState(pos);
      if (maintenant != avant) {
         // déjà cassée pendant l'événement (DestroyGlass de TaCZ) : notée quand même, elle repoussera
         if (maintenant.isAir() && Vitres.estVerre(avant)) {
            noter(level, pos, avant, maintenant);
         }
         return;
      }
      if (!cassable(level, pos, avant)) {
         return;
      }
      Entity tireur = e.getAmmo().getOwner();
      BlockState trou = trou(avant);
      // éclats et bruit du verre (ceux du bloc pour le verre de TaCZ, ceux du verre pour les autres : une fenêtre de
      // meubles sonne comme de la pierre), puis le bloc seul, sans objet ni voisins prévenus
      level.levelEvent(EVENEMENT_BLOC_CASSE, pos, Block.getId(Vitres.estVerre(avant) ? avant : Blocks.GLASS.defaultBlockState()));
      if (level.setBlock(pos, trou, DRAPEAUX)) {
         level.gameEvent(tireur, GameEvent.BLOCK_DESTROY, pos);
         noter(level, pos, avant, trou);
         detacher(level, pos, avant);
      }
   }

   private static void noter(ServerLevel level, BlockPos pos, BlockState etat, BlockState trou) {
      VitresCassees.de(level).noter(pos, etat, trou, System.currentTimeMillis());
   }

   // ------------------------------------------------------------------ la main l'emporte

   private static void pose(BlockEvent.EntityPlaceEvent e) {
      if (!(e.getLevel() instanceof ServerLevel level)) {
         return;
      }
      VitresCassees d = VitresCassees.de(level);
      if (d.vide()) {
         return;
      }
      if (e instanceof BlockEvent.EntityMultiPlaceEvent plusieurs) {
         for (BlockSnapshot s : plusieurs.getReplacedBlockSnapshots()) {
            d.oublier(s.getPos());
         }
      } else {
         d.oublier(e.getPos());
      }
   }

   private static void casseeALaMain(BlockEvent.BreakEvent e) {
      if (e.getLevel() instanceof ServerLevel level) {
         VitresCassees d = VitresCassees.de(level);
         if (!d.vide()) {
            d.oublier(e.getPos());
         }
      }
   }

   // ------------------------------------------------------------------ repousse

   private static void tick(TickEvent.ServerTickEvent e) {
      if (e.phase != TickEvent.Phase.END || ++compteur < PERIODE_TICKS) {
         return;
      }
      compteur = 0;
      MinecraftServer serveur = ServerLifecycleHooks.getCurrentServer();
      if (serveur == null) {
         return;
      }
      long maintenant = System.currentTimeMillis();
      int[] aucunChargement = {0};
      for (ServerLevel level : serveur.getAllLevels()) {
         passe(level, maintenant, aucunChargement);
      }
   }

   private static void demarrage(ServerStartedEvent e) {
      long maintenant = System.currentTimeMillis();
      int[] chargements = {MAX_CHARGEMENTS_DEMARRAGE};
      for (ServerLevel level : e.getServer().getAllLevels()) {
         VitresCassees d = VitresCassees.de(level);
         if (d.vide()) {
            continue;
         }
         int avant = d.taille();
         int remises = passe(level, maintenant, chargements);
         LOG.info("[heraliz_tactique] vitres de {} : {} remise(s) au démarrage, {} en attente",
            level.dimension().location(), remises, d.taille());
         if (avant - remises != d.taille()) {
            LOG.info("[heraliz_tactique] vitres de {} : {} oubliée(s) (un bloc a pris leur place)",
               level.dimension().location(), avant - remises - d.taille());
         }
      }
   }

   /** Délai de repousse (millisecondes). */
   static long delaiMs() {
      return lire(ServerConfig.VITRES_DELAI_MINUTES, Vitres.DEFAUT_DELAI_MINUTES) * 60_000L;
   }

   /**
    * Remet les vitres dont le délai est passé. {@code chargements[0]} : nombre de vitres de chunks non chargés qu'on peut
    * encore aller chercher en chargeant leur chunk exprès, décompté au passage (0 en jeu : une vitre d'un chunk non
    * chargé attend que des joueurs approchent).
    *
    * @return le nombre de vitres remises
    */
   private static int passe(ServerLevel level, long maintenant, int[] chargements) {
      VitresCassees d = VitresCassees.de(level);
      if (d.vide()) {
         return 0;
      }
      long delai = delaiMs();
      int remises = 0;
      boolean change = false;
      Iterator<Map.Entry<Long, VitresCassees.Vitre>> it = d.vitres().entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<Long, VitresCassees.Vitre> en = it.next();
         VitresCassees.Vitre v = en.getValue();
         if (maintenant - v.casseeA() < delai) {
            continue;
         }
         if (v.etat().isAir()) {
            // bloc inconnu à la relecture (mod retiré) : rien à reposer
            it.remove();
            change = true;
            continue;
         }
         BlockPos pos = BlockPos.of(en.getKey());
         if (!level.isLoaded(pos)) {
            if (chargements[0] <= 0) {
               continue;
            }
            chargements[0]--;
         }
         BlockState ici = level.getBlockState(pos);
         boolean libre = v.trou().isAir() ? ici.isAir() : ici.getBlock() == v.trou().getBlock();
         if (libre) {
            // quelqu'un dans le trou : on réessaie plus tard (une version cassée est un bloc : personne dedans)
            if (v.trou().isAir() && quelquUnDedans(level, pos)) {
               continue;
            }
            reposer(level, pos, v.etat());
         } else if (!ici.isAir() && ici.canBeReplaced()) {
            // feu, neige, eau qui coule, herbe... : on attend que la place se libère
            continue;
         }
         // remise, ou un bloc a pris sa place (vitre reposée à la main, autre bloc, WorldEdit) ou la version cassée a été
         // enlevée : la main l'emporte, la vitre est oubliée
         it.remove();
         change = true;
         if (libre) {
            remises++;
         }
      }
      if (change) {
         d.setDirty();
      }
      return remises;
   }

   /** Un joueur, une créature ou un véhicule touche la case (les spectateurs et les objets au sol ne comptent pas). */
   private static boolean quelquUnDedans(ServerLevel level, BlockPos pos) {
      return !level.getEntities((Entity) null, new AABB(pos), en -> en.blocksBuilding && !en.isSpectator()).isEmpty();
   }

   // ------------------------------------------------------------------ raccords des vitres fines

   /**
    * Vitre fine cassée : les vitres fines (et barreaux) voisines qui s'y raccordaient ne pointent plus vers le trou. Seul
    * leur raccord de ce côté change, sans prévenir personne d'autre.
    */
   private static void detacher(ServerLevel level, BlockPos pos, BlockState cassee) {
      for (Direction cote : Direction.Plane.HORIZONTAL) {
         if (!raccordee(cassee, cote)) {
            continue;
         }
         BlockPos voisin = pos.relative(cote);
         BlockState v = level.getBlockState(voisin);
         if (raccordee(v, cote.getOpposite())) {
            level.setBlock(voisin, v.setValue(raccord(cote.getOpposite()), false), DRAPEAUX);
         }
      }
   }

   /**
    * Repose une vitre : son état d'avant la casse, raccordée aux vitres fines voisines comme le ferait le jeu (vers une
    * vitre fine ou des barreaux : raccordée, et eux à elle ; vers une case vide, une vitre voisine encore cassée : pas de
    * raccord). Vers les autres blocs (murs, blocs pleins), les raccords d'avant la casse sont gardés tels quels.
    */
   private static void reposer(ServerLevel level, BlockPos pos, BlockState etat) {
      BlockState propre = etat;
      if (etat.getBlock() instanceof IronBarsBlock) {
         for (Direction cote : Direction.Plane.HORIZONTAL) {
            BlockState v = level.getBlockState(pos.relative(cote));
            if (v.getBlock() instanceof IronBarsBlock) {
               propre = propre.setValue(raccord(cote), true);
            } else if (v.isAir()) {
               propre = propre.setValue(raccord(cote), false);
            }
         }
      }
      level.setBlock(pos, propre, DRAPEAUX);
      if (!(propre.getBlock() instanceof IronBarsBlock)) {
         return;
      }
      for (Direction cote : Direction.Plane.HORIZONTAL) {
         BlockPos voisin = pos.relative(cote);
         BlockState v = level.getBlockState(voisin);
         if (v.getBlock() instanceof IronBarsBlock && !raccordee(v, cote.getOpposite())) {
            level.setBlock(voisin, v.setValue(raccord(cote.getOpposite()), true), DRAPEAUX);
         }
      }
   }

   private static BooleanProperty raccord(Direction cote) {
      return switch (cote) {
         case NORTH -> CrossCollisionBlock.NORTH;
         case SOUTH -> CrossCollisionBlock.SOUTH;
         case EAST -> CrossCollisionBlock.EAST;
         default -> CrossCollisionBlock.WEST;
      };
   }

   /** Vrai si ce bloc est une vitre fine ou des barreaux raccordés de ce côté. */
   private static boolean raccordee(BlockState etat, Direction cote) {
      return etat.getBlock() instanceof IronBarsBlock && etat.hasProperty(raccord(cote)) && etat.getValue(raccord(cote));
   }

   // ------------------------------------------------------------------ réglages

   static int rayonSpawn() {
      return lire(ServerConfig.VITRES_RAYON_SPAWN, Vitres.DEFAUT_RAYON_SPAWN);
   }

   /** Carré de la distance horizontale au point d'apparition du monde. */
   static long distanceSpawnCarre(ServerLevel level, BlockPos pos) {
      BlockPos spawn = level.getSharedSpawnPos();
      long dx = pos.getX() - spawn.getX();
      long dz = pos.getZ() - spawn.getZ();
      return dx * dx + dz * dz;
   }

   private static Set<ResourceLocation> dimensionsProtegees() {
      List<? extends String> l = lire(ServerConfig.VITRES_DIMENSIONS, Vitres.DEFAUT_DIMENSIONS);
      if (l != sourceDimensions) {
         dimensions = identifiants(l);
         sourceDimensions = l;
      }
      return dimensions;
   }

   private static Set<ResourceLocation> blocsProteges() {
      List<? extends String> l = lire(ServerConfig.VITRES_BLOCS_PROTEGES, List.of());
      if (l != sourceBlocs) {
         blocs = identifiants(l);
         sourceBlocs = l;
      }
      return blocs;
   }

   /** blocs_en_plus lu : « bloc » ou « bloc > version cassée » ; un bloc inconnu (mod absent) est sauté, avec un avertissement. */
   private static Map<Block, Block> blocsEnPlus() {
      List<? extends String> l = lire(ServerConfig.VITRES_BLOCS_EN_PLUS, Vitres.DEFAUT_BLOCS_EN_PLUS);
      if (l != sourceEnPlus) {
         Map<Block, Block> m = new IdentityHashMap<>();
         for (String t : l) {
            String[] parties = t.split(">", -1);
            if (parties.length > 2) {
               LOG.warn("[heraliz_tactique] [vitres] blocs_en_plus illisible : {}", t);
               continue;
            }
            Block bloc = bloc(parties[0]);
            Block casse = parties.length == 2 ? bloc(parties[1]) : Blocks.AIR;
            if (bloc != null && casse != null && bloc != Blocks.AIR) {
               m.put(bloc, casse);
            }
         }
         enPlus = m;
         sourceEnPlus = l;
      }
      return enPlus;
   }

   @Nullable
   private static Block bloc(String texte) {
      ResourceLocation id = ResourceLocation.tryParse(texte.trim());
      if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
         LOG.warn("[heraliz_tactique] [vitres] blocs_en_plus : bloc inconnu (mod absent ?) : {}", texte.trim());
         return null;
      }
      return BuiltInRegistries.BLOCK.get(id);
   }

   private static Set<ResourceLocation> identifiants(List<? extends String> l) {
      Set<ResourceLocation> s = new HashSet<>();
      for (String t : l) {
         ResourceLocation id = ResourceLocation.tryParse(t.trim());
         if (id != null) {
            s.add(id);
         } else {
            LOG.warn("[heraliz_tactique] [vitres] identifiant illisible : {}", t);
         }
      }
      return Set.copyOf(s);
   }

   private static <T> T lire(@Nullable ForgeConfigSpec.ConfigValue<T> valeur, T defaut) {
      try {
         if (valeur != null) {
            T v = valeur.get();
            if (v != null) {
               return v;
            }
         }
      } catch (IllegalStateException | NullPointerException pasEncoreChargee) {
         // config serveur pas encore lue : valeur par défaut
      }
      return defaut;
   }

   // ------------------------------------------------------------------ CRac (facultatif, par réflexion)

   /** Le claim de CRac qui couvre cet endroit (null : aucun ; CRac absent ou changé : null, et un avertissement une seule fois). */
   @Nullable
   static Object claimCrac(ServerLevel level, BlockPos pos) {
      Method m = methodeCrac();
      if (m == null) {
         return null;
      }
      try {
         return m.invoke(null, level, pos);
      } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
         LOG.warn("[heraliz_tactique] [vitres] claims de CRac illisibles, le verre n'y est plus protégé : {}", ex.toString());
         cracClaimA = null;
         return null;
      }
   }

   /** Bloc de claim (centre) d'un claim de CRac, lu par réflexion (null si illisible). */
   @Nullable
   static BlockPos ancreClaim(Object claim) {
      try {
         return claim.getClass().getMethod("anchor").invoke(claim) instanceof BlockPos p ? p : null;
      } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
         return null;
      }
   }

   @Nullable
   private static Method methodeCrac() {
      if (!cracCherche) {
         cracCherche = true;
         if (ModList.get().isLoaded("crac")) {
            try {
               cracClaimA = Class.forName(CRAC_CLAIMS).getMethod("getAt", ServerLevel.class, BlockPos.class);
            } catch (ReflectiveOperationException | LinkageError ex) {
               LOG.warn("[heraliz_tactique] [vitres] {}.getAt introuvable, le verre des claims de CRac n'est pas protégé", CRAC_CLAIMS);
            }
         }
      }
      return cracClaimA;
   }
}
