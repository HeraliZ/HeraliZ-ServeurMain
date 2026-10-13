package fr.heraliz.tactique.vitres;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Vitres cassées par les balles de TaCZ, qui repoussent seules (la carte est construite à la main : elle ne doit pas
 * s'abîmer).
 * <ul>
 *    <li>Ce qui compte comme du verre : la même règle que TaCZ ({@code com.tacz.guns.event.ammo.DestroyGlassBlock}) :
 *    blocs de verre (verre, verre coloré, verre teinté : {@link AbstractGlassBlock}), vitres colorées
 *    ({@link StainedGlassPaneBlock}) et vitre simple (barreaux à son de verre : {@link IronBarsBlock} + instrument
 *    « hat » ; les barreaux de fer n'en sont pas). Pas de tag : TaCZ teste la classe du bloc. En plus, les blocs de la
 *    liste {@code blocs_en_plus} de la config (fenêtres de meubles : {@code cfm:vb_4}, qui devient sa version cassée
 *    {@code cfm:vb_5}).</li>
 *    <li>Qui casse : la partie serveur ({@code fr.heraliz.tactique.serveur.VitresServeur}), sur l'événement public
 *    {@code AmmoHitBlockEvent} de TaCZ, hors zones protégées ; elle note la vitre (état complet) juste avant de la
 *    casser et la remet au bout du délai. Le réglage {@code DestroyGlass} de TaCZ reste à false : TaCZ casserait sans
 *    regarder les zones protégées et en prévenant les blocs voisins (vitres voisines qui se rétractent, sable qui
 *    tombe...).</li>
 *    <li>Les blocs de verre pleins (et les fenêtres {@code cfm:vb_*}) sont dans la liste {@code PassThroughBlocks} de
 *    TaCZ (les balles les traversent sans les toucher) : le mixin {@code TaczVerreBalleMixin} les rend « touchables »
 *    là où ils cassent, pour que la balle les touche (et les casse). Dans les zones protégées, rien ne change : traversés
 *    ou non comme avant.</li>
 * </ul>
 * Ici, ce que lisent les deux côtés (règle du verre, calcul du point touché, réglages par défaut). La décision « ce bloc
 * casse-t-il ici ? » est branchée par la partie serveur ; sans elle (jar des joueurs), le mixin ne fait rien.
 */
public final class Vitres {
   /** Réglage par défaut : délai avant qu'une vitre cassée repousse (minutes). */
   public static final int DEFAUT_DELAI_MINUTES = 5;
   /**
    * Réglage par défaut : rayon autour du point d'apparition de l'overworld où le verre ne casse pas (blocs). 0 : le
    * spawn des joueurs est un monde à part ({@code heralisz:spawn}, protégé en entier par {@link #DEFAUT_DIMENSIONS}) ;
    * le point d'apparition de la carte est au milieu d'une ville (-435 1118 sur la carte v3) : avec 64, le verre de
    * cette ville ne cassait jamais.
    */
   public static final int DEFAUT_RAYON_SPAWN = 0;
   /** Réglage par défaut : dimensions où le verre ne casse jamais (spawn, arènes des matchs, zombies, bunkers des clans). */
   public static final List<String> DEFAUT_DIMENSIONS = List.of("heralisz:spawn", "heralisz_matchs:arenes", "heralisz_matchs:zombies",
      "heralisz_bases:bunkers");
   /**
    * Réglage par défaut : autres blocs qui cassent comme une vitre, « bloc » (il disparaît) ou « bloc > version cassée ».
    * {@code cfm:vb_4} : fenêtre à croisée grise des meubles (posée sur la carte), {@code cfm:vb_5} : la même, vitre brisée.
    */
   public static final List<String> DEFAUT_BLOCS_EN_PLUS = List.of("cfm:vb_4 > cfm:vb_5");

   /** Décision du serveur : ce bloc (verre, ou bloc de la liste blocs_en_plus) casse-t-il ici sous les balles ? */
   @FunctionalInterface
   public interface Regle {
      boolean cassable(ServerLevel level, BlockPos pos, BlockState etat);
   }

   @Nullable
   private static volatile Regle regle;
   /** Vrai dès que le mixin du trajet des balles a été appelé au moins une fois (il est donc bien en place dans TaCZ). */
   private static volatile boolean crochetVu;

   private Vitres() {
   }

   /** Branche la décision du serveur (appelé par la partie serveur, pendant le chargement du mod). */
   public static void brancherRegle(Regle nouvelle) {
      regle = nouvelle;
   }

   /** Vrai si la partie serveur est là (sinon le verre ne casse jamais : jar des joueurs). */
   public static boolean branche() {
      return regle != null;
   }

   /** Le verre au sens de TaCZ (même règle que son DestroyGlassBlock). */
   public static boolean estVerre(BlockState etat) {
      Block bloc = etat.getBlock();
      return bloc instanceof AbstractGlassBlock
         || bloc instanceof StainedGlassPaneBlock
         || bloc instanceof IronBarsBlock && etat.instrument() == NoteBlockInstrument.HAT;
   }

   /**
    * Vrai si ce bloc casse sous les balles à cet endroit : verre ou bloc de la liste blocs_en_plus, hors zones protégées
    * (réglages : partie serveur). Appelé pour chaque bloc du trajet d'une balle : la partie serveur écarte d'abord, sans
    * rien lire d'autre, tout ce qui n'est pas une vitre.
    */
   public static boolean cassable(Level level, BlockPos pos, BlockState etat) {
      Regle r = regle;
      return r != null && level instanceof ServerLevel serveur && r.cassable(serveur, pos, etat);
   }

   /** Noté par le mixin du trajet des balles à chaque appel (diagnostic {@code /vitres pourquoi}). */
   public static void crochetAppele() {
      if (!crochetVu) {
         crochetVu = true;
      }
   }

   /** Vrai si le mixin du trajet des balles a déjà été appelé (sinon : TaCZ a changé, les blocs de verre pleins ne cassent pas). */
   public static boolean crochetVu() {
      return crochetVu;
   }

   /**
    * Point où le rayon de la balle touche ce bloc (null s'il passe à côté de sa forme, une vitre fine par exemple) : le
    * même calcul que TaCZ pour les blocs qui ne sont pas dans PassThroughBlocks ({@code BlockRayTrace.getBlockHitResult},
    * privé : refait ici à l'identique).
    */
   @Nullable
   public static BlockHitResult toucher(Level level, ClipContext contexte, BlockPos pos, BlockState etat) {
      Vec3 depart = contexte.getFrom();
      Vec3 arrivee = contexte.getTo();
      BlockHitResult bloc = level.clipWithInteractionOverride(depart, arrivee, pos, contexte.getBlockShape(etat, level, pos), etat);
      BlockHitResult fluide = contexte.getFluidShape(level.getFluidState(pos), level, pos).clip(depart, arrivee, pos);
      double dBloc = bloc == null ? Double.MAX_VALUE : depart.distanceToSqr(bloc.getLocation());
      double dFluide = fluide == null ? Double.MAX_VALUE : depart.distanceToSqr(fluide.getLocation());
      return dBloc <= dFluide ? bloc : fluide;
   }
}
