package fr.heraliz.tactique.serveur;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import fr.heralisz.tactical.config.ServerConfig;
import fr.heraliz.tactique.vitres.Vitres;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * Vitres cassées par les balles de TaCZ, décisions du serveur (fr.heraliz.tactique.vitres.Vitres).
 * <ul>
 *    <li><b>Casse</b> : sur l'événement public {@code AmmoHitBlockEvent} de TaCZ (une balle touche un bloc, avant que
 *    TaCZ ne la retire), si le bloc est du verre au sens de TaCZ et que le verre casse à cet endroit : l'état complet du
 *    bloc est noté (VitresCassees), puis le bloc devient de l'air, avec les éclats et le bruit d'un bloc cassé, sans
 *    objet lâché et SANS prévenir les blocs voisins (rien ne tombe ni ne se décroche : la carte reste telle quelle
 *    autour du trou) ; seules les vitres fines voisines qui s'y raccordaient perdent leur raccord vers le trou, pour un
 *    bord net.</li>
 *    <li><b>Zones où le verre ne casse pas</b> : réglage {@code [vitres] casser} à false, dimensions protégées (matchs,
 *    zombies, bunkers), rayon autour du point d'apparition du monde (le spawn, protégé par WorldGuard, que ce mod ne
 *    peut pas lire), claims de CRac (lus par réflexion, CRac facultatif), verre gorgé d'eau, blocs de la liste
 *    {@code blocs_proteges}. Là, tout reste comme avant.</li>
 *    <li><b>Repousse</b> : chaque seconde, les vitres dont le délai est passé sont reposées dans leur état exact (sans
 *    prévenir les voisins non plus), raccordées aux vitres fines voisines comme dans le jeu (une vitre fine voisine
 *    encore cassée : pas de raccord vers le trou ; elle s'y raccordera en repoussant), si leur chunk est chargé, si leur place est encore de l'air et si personne (joueur,
 *    créature, véhicule) ne s'y tient ; sinon elles attendent. Au démarrage du serveur, les vitres en retard sont reposées
 *    tout de suite, chunks chargés exprès (au plus {@link #MAX_CHARGEMENTS_DEMARRAGE}, les autres au chargement de leur
 *    chunk).</li>
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

   private static int compteur;
   // réglages lus en liste : recalculés seulement quand Forge rend une autre liste (rechargement de la config)
   private static List<? extends String> sourceDimensions;
   private static Set<ResourceLocation> dimensions = Set.of();
   private static List<? extends String> sourceBlocs;
   private static Set<ResourceLocation> blocs = Set.of();
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
   }

   // ------------------------------------------------------------------ casse

   private static void touche(AmmoHitBlockEvent e) {
      if (!(e.getLevel() instanceof ServerLevel level)) {
         return;
      }
      // état du bloc noté par TaCZ au moment de l'impact, avant tout écouteur : c'est l'état d'origine
      BlockState avant = e.getState();
      if (!Vitres.estVerre(avant)) {
         return;
      }
      BlockPos pos = e.getHitResult().getBlockPos().immutable();
      BlockState maintenant = level.getBlockState(pos);
      if (maintenant != avant) {
         // déjà cassée pendant l'événement (DestroyGlass de TaCZ) : notée quand même, elle repoussera
         if (maintenant.isAir()) {
            noter(level, pos, avant);
         }
         return;
      }
      if (!cassable(level, pos, avant)) {
         return;
      }
      Entity tireur = e.getAmmo().getOwner();
      // éclats et bruit du bloc cassé (ce que fait Level.destroyBlock), puis le bloc seul, sans objet ni voisins prévenus
      level.levelEvent(EVENEMENT_BLOC_CASSE, pos, Block.getId(avant));
      if (level.setBlock(pos, Blocks.AIR.defaultBlockState(), DRAPEAUX)) {
         level.gameEvent(tireur, GameEvent.BLOCK_DESTROY, pos);
         noter(level, pos, avant);
         detacher(level, pos, avant);
      }
   }

   private static void noter(ServerLevel level, BlockPos pos, BlockState etat) {
      VitresCassees.de(level).noter(pos, etat, System.currentTimeMillis());
   }

   /** Le verre casse-t-il ici ? (aussi appelé par le mixin du trajet des balles, pour chaque bloc de verre traversé) */
   static boolean cassable(ServerLevel level, BlockPos pos, BlockState etat) {
      if (!lire(ServerConfig.VITRES_CASSER, true)) {
         return false;
      }
      // verre gorgé d'eau : l'eau se répandrait dans la carte
      if (!etat.getFluidState().isEmpty()) {
         return false;
      }
      if (blocsProteges().contains(BuiltInRegistries.BLOCK.getKey(etat.getBlock()))) {
         return false;
      }
      if (dimensionsProtegees().contains(level.dimension().location())) {
         return false;
      }
      int rayon = lire(ServerConfig.VITRES_RAYON_SPAWN, Vitres.DEFAUT_RAYON_SPAWN);
      if (rayon > 0 && level.dimension() == Level.OVERWORLD) {
         BlockPos spawn = level.getSharedSpawnPos();
         long dx = pos.getX() - spawn.getX();
         long dz = pos.getZ() - spawn.getZ();
         if (dx * dx + dz * dz <= (long) rayon * rayon) {
            return false;
         }
      }
      return !(lire(ServerConfig.VITRES_CLAIMS_CRAC, true) && dansUnClaimCrac(level, pos));
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
         LOG.info("[heraliz_tactique] vitres de {} : {} remise(s) au démarrage, {} à remettre plus tard",
            level.dimension().location(), remises, avant - remises);
      }
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
      long delai = lire(ServerConfig.VITRES_DELAI_MINUTES, Vitres.DEFAUT_DELAI_MINUTES) * 60_000L;
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
         // place prise (bloc posé, feu...) ou quelqu'un dedans : on réessaie plus tard
         if (!level.getBlockState(pos).isAir() || quelquUnDedans(level, pos)) {
            continue;
         }
         reposer(level, pos, v.etat());
         it.remove();
         change = true;
         remises++;
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

   /** Vrai si un claim de CRac couvre cet endroit ; CRac absent ou changé : faux (et un avertissement une seule fois). */
   private static boolean dansUnClaimCrac(ServerLevel level, BlockPos pos) {
      Method m = methodeCrac();
      if (m == null) {
         return false;
      }
      try {
         return m.invoke(null, level, pos) != null;
      } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
         LOG.warn("[heraliz_tactique] [vitres] claims de CRac illisibles, le verre n'y est plus protégé : {}", ex.toString());
         cracClaimA = null;
         return false;
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
