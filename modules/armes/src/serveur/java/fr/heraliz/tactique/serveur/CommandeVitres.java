package fr.heraliz.tactique.serveur;

import com.mojang.brigadier.CommandDispatcher;
import com.tacz.guns.util.block.BlockRayTrace;
import fr.heraliz.tactique.vitres.Vitres;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /vitres pourquoi [x y z]} (staff, niveau 2) : dit en clair si un bloc casse sous les balles et, sinon, pourquoi.
 * Sans position : le bloc visé (jusqu'à {@link #PORTEE_VISEE} blocs). Réponse en message direct, dans la langue du staff :
 * <ul>
 *    <li>le bloc : verre pour HeraliZ, bloc de {@code blocs_en_plus} (et ce qu'il devient), ou pas une vitre ;</li>
 *    <li>chaque raison qui l'empêche de casser ici ({@link VitresServeur.Raison} : réglage, eau, bloc protégé, dimension,
 *    rayon du spawn, claim de CRac), ou « il casse » ;</li>
 *    <li>une vitre en attente de repousse à cet endroit, et dans combien de temps elle revient ;</li>
 *    <li>pour un joueur : ce que ferait une balle tirée de ses yeux vers ce bloc (le vrai calcul de TaCZ, crochet de
 *    HeraliZ compris) : elle le touche, s'arrête d'abord sur un autre bloc, ou le traverse ;</li>
 *    <li>pour un joueur aussi : si le crochet de HeraliZ dans le trajet des balles de TaCZ ne répond pas (TaCZ
 *    changé).</li>
 * </ul>
 * Rien n'est changé.
 */
final class CommandeVitres {
   /** Distance maximale du bloc visé, en blocs. */
   private static final double PORTEE_VISEE = 128.0D;

   private CommandeVitres() {
   }

   static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
      d.register(Commands.literal("vitres").requires(s -> s.hasPermission(2))
         .then(Commands.literal("pourquoi")
            .executes(c -> viser(c.getSource()))
            .then(Commands.argument("position", BlockPosArgument.blockPos())
               .executes(c -> expliquer(c.getSource(), c.getSource().getLevel(), BlockPosArgument.getLoadedBlockPos(c, "position"),
                  null)))));
   }

   private static int viser(CommandSourceStack s) {
      ServerPlayer p = s.getPlayer();
      if (p == null) {
         s.sendFailure(tr("heraliz_tactique.vitres.console", "Depuis la console : /vitres pourquoi <x> <y> <z>."));
         return 0;
      }
      Vec3 oeil = p.getEyePosition();
      Vec3 fin = oeil.add(p.getLookAngle().scale(PORTEE_VISEE));
      BlockHitResult vise = p.serverLevel().clip(new ClipContext(oeil, fin, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
      if (vise.getType() == HitResult.Type.MISS) {
         s.sendFailure(tr("heraliz_tactique.vitres.rien", "Aucun bloc visé à moins de %s blocs.", String.valueOf((int) PORTEE_VISEE)));
         return 0;
      }
      return expliquer(s, p.serverLevel(), vise.getBlockPos(), vise.getLocation());
   }

   /**
    * @param point endroit visé sur le bloc (null : son centre), pour le trajet d'une balle tirée des yeux du joueur
    * @return 1 si le bloc casse ici, 0 sinon
    */
   private static int expliquer(CommandSourceStack s, ServerLevel level, BlockPos pos, @Nullable Vec3 point) {
      BlockState etat = level.getBlockState(pos);
      dire(s, tr("heraliz_tactique.vitres.titre", "Vitres : %s (%s) en %s, %s", etat.getBlock().getName(),
         BuiltInRegistries.BLOCK.getKey(etat.getBlock()).toString(), position(pos), level.dimension().location().toString())
         .withStyle(ChatFormatting.GOLD));

      // 1. le bloc est-il une vitre pour HeraliZ ?
      boolean verre = Vitres.estVerre(etat);
      Block casse = VitresServeur.versionCassee(etat.getBlock());
      if (verre) {
         dire(s, tr("heraliz_tactique.vitres.verre", "Du verre pour HeraliZ (verre, verre coloré ou teinté, vitre).")
            .withStyle(ChatFormatting.GRAY));
      } else if (casse != null && casse != Blocks.AIR) {
         dire(s, tr("heraliz_tactique.vitres.en_plus_casse",
            "Bloc de blocs_en_plus : une balle le change en %s, il redevient lui-même au bout du délai.", casse.getName())
            .withStyle(ChatFormatting.GRAY));
      } else if (casse != null) {
         dire(s, tr("heraliz_tactique.vitres.en_plus", "Bloc de blocs_en_plus : une balle le fait disparaître, il revient au bout du délai.")
            .withStyle(ChatFormatting.GRAY));
      } else if (etat.isAir()) {
         dire(s, tr("heraliz_tactique.vitres.vide", "Pas de bloc à cet endroit.").withStyle(ChatFormatting.GRAY));
      } else {
         dire(s, tr("heraliz_tactique.vitres.pas_vitre",
            "Pas une vitre pour HeraliZ : il ne casse jamais. Cassent : le verre, les vitres et les blocs de blocs_en_plus ([vitres] de heralisz_tactical-server.toml).")
            .withStyle(ChatFormatting.RED));
      }

      // 2. ce qui l'empêche de casser ici
      boolean cassable = false;
      if (verre || casse != null) {
         List<VitresServeur.Raison> raisons = VitresServeur.raisons(level, pos, etat, false);
         cassable = raisons.isEmpty();
         if (cassable) {
            dire(s, tr("heraliz_tactique.vitres.casse", "Il casse sous les balles ici et revient au bout de %s min.",
               String.valueOf(VitresServeur.delaiMs() / 60_000L)).withStyle(ChatFormatting.GREEN));
         }
         for (VitresServeur.Raison r : raisons) {
            dire(s, raison(r, level, pos).withStyle(ChatFormatting.RED));
         }
      }

      // 3. une vitre en attente de repousse à cet endroit
      VitresCassees.Vitre attente = VitresCassees.de(level).vitres().get(pos.asLong());
      if (attente != null) {
         long ecoule = Math.max(0L, System.currentTimeMillis() - attente.casseeA());
         long reste = Math.max(0L, VitresServeur.delaiMs() - ecoule);
         dire(s, tr("heraliz_tactique.vitres.attente",
            "En attente de repousse depuis %s min : %s revient ici dans %s min, si la place est libre.",
            String.valueOf(ecoule / 60_000L), attente.etat().getBlock().getName(), String.valueOf((reste + 59_999L) / 60_000L))
            .withStyle(ChatFormatting.YELLOW));
      }

      // 4. le trajet d'une balle tirée des yeux du joueur (vrai calcul de TaCZ, crochet de HeraliZ compris)
      ServerPlayer p = s.getPlayer();
      if (p != null && p.level() == level) {
         Vec3 oeil = p.getEyePosition();
         Vec3 vers = point != null ? point : Vec3.atCenterOf(pos);
         Vec3 dir = vers.subtract(oeil);
         if (dir.lengthSqr() > 1.0E-6D) {
            Vec3 fin = vers.add(dir.normalize().scale(0.5D));
            BlockHitResult balle = BlockRayTrace.rayTraceBlocks(level, new ClipContext(oeil, fin, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            // arrêtée sur un autre bloc, mais derrière le point visé : elle est passée au travers de ce bloc
            boolean derriere = balle.getType() != HitResult.Type.MISS && !balle.getBlockPos().equals(pos)
               && oeil.distanceToSqr(balle.getLocation()) > oeil.distanceToSqr(vers);
            if (balle.getType() == HitResult.Type.MISS || derriere) {
               dire(s, tr("heraliz_tactique.vitres.balle_traverse",
                  "Une balle tirée d'ici le traverse sans le toucher (PassThroughBlocks de tacz-common.toml ou tag tacz:bullet_ignore).")
                  .withStyle(ChatFormatting.YELLOW));
            } else if (balle.getBlockPos().equals(pos)) {
               dire(s, tr("heraliz_tactique.vitres.balle_touche", "Une balle tirée d'ici le touche bien.").withStyle(ChatFormatting.GRAY));
            } else {
               BlockState avant = level.getBlockState(balle.getBlockPos());
               dire(s, tr("heraliz_tactique.vitres.balle_avant", "Une balle tirée d'ici s'arrête d'abord sur %s en %s.",
                  avant.getBlock().getName(), position(balle.getBlockPos())).withStyle(ChatFormatting.YELLOW));
            }
            // 5. le crochet de HeraliZ dans le trajet des balles de TaCZ : le trajet ci-dessus l'a forcément appelé
            if (!Vitres.crochetVu()) {
               dire(s, tr("heraliz_tactique.vitres.crochet",
                  "Le crochet de HeraliZ dans le trajet des balles de TaCZ ne répond pas (TaCZ a changé ?) : les blocs que les balles traversent (verre plein, fenêtres de meubles) ne cassent pas.")
                  .withStyle(ChatFormatting.RED));
            }
         }
      }
      return cassable ? 1 : 0;
   }

   private static MutableComponent raison(VitresServeur.Raison r, ServerLevel level, BlockPos pos) {
      return switch (r) {
         case DESACTIVE -> tr("heraliz_tactique.vitres.desactive", "Réglage [vitres] casser = false : rien ne casse.");
         case GORGEE_EAU -> tr("heraliz_tactique.vitres.eau",
            "Vitre gorgée d'eau : elle ne casse pas (l'eau se répandrait). La reposer à sec pour qu'elle casse.");
         case BLOC_PROTEGE -> tr("heraliz_tactique.vitres.protege", "Bloc de la liste blocs_proteges : il ne casse jamais.");
         case DIMENSION -> tr("heraliz_tactique.vitres.dimension", "Dimension protégée (%s, dimensions_protegees) : rien n'y casse.",
            level.dimension().location().toString());
         case SPAWN -> {
            BlockPos spawn = level.getSharedSpawnPos();
            yield tr("heraliz_tactique.vitres.spawn",
               "À %s blocs du point d'apparition du monde (%s) : dans le rayon_spawn de %s blocs, rien ne casse.",
               String.valueOf((long) Math.sqrt((double) VitresServeur.distanceSpawnCarre(level, pos))), position(spawn),
               String.valueOf(VitresServeur.rayonSpawn()));
         }
         case CLAIM_CRAC -> {
            Object claim = VitresServeur.claimCrac(level, pos);
            BlockPos ancre = claim == null ? null : VitresServeur.ancreClaim(claim);
            yield tr("heraliz_tactique.vitres.claim", "Dans un claim de CRac (bloc de claim en %s) : rien n'y casse (proteger_claims_crac).",
               ancre == null ? "?" : position(ancre));
         }
      };
   }

   private static String position(BlockPos p) {
      return p.getX() + " " + p.getY() + " " + p.getZ();
   }

   private static MutableComponent tr(String cle, String francais, Object... args) {
      return Component.translatableWithFallback(cle, francais, args);
   }

   /** En message direct au staff qui a tapé la commande (jamais diffusé). */
   private static void dire(CommandSourceStack s, Component texte) {
      s.sendSuccess(() -> texte, false);
   }
}
