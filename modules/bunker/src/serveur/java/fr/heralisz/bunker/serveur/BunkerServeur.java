package fr.heralisz.bunker.serveur;

import com.mojang.logging.LogUtils;
import fr.heralisz.bunker.keycard.BunkerPont;
import fr.heralisz.bunker.keycard.KeycardConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Règles des portes de bunker (17/11, demande du propriétaire : « quand une porte de bunker est ouverte, un message dans
 * le tchat ; elle reste ouverte 30 minutes puis se ferme ; qui est dedans peut rouvrir de dedans s'il a la carte, sinon
 * c'est fini pour lui »). Branchées dans le code commun ({@link BunkerPont}) ; portes désignées par le staff
 * ({@code /salle bunker ajouter [nom]}) ; réglages : section [bunkers] de config/heralisz_bunker-common.toml (durée,
 * avertissement).
 */
public final class BunkerServeur implements BunkerPont.Regles {
   private static final Logger LOG = LogUtils.getLogger();
   /** Joueurs prévenus de la fermeture : à ce rayon (blocs) de la porte, tout le bunker derrière. */
   private static final double RAYON_AVERTI = 48.0;

   @Override
   public long ouvertureMs() {
      return 60_000L * (KeycardConfig.SPEC.isLoaded() ? KeycardConfig.BUNKER_MINUTES.get() : 30);
   }

   @Override
   public long avertirMs() {
      return 1000L * (KeycardConfig.SPEC.isLoaded() ? KeycardConfig.BUNKER_AVERTIR.get() : 60);
   }

   @Override
   public void ouverte(ServerLevel level, BlockPos porte, String nom) {
      level.getServer().getPlayerList().broadcastSystemMessage(annonce(nom, this.ouvertureMs()), false);
      LOG.info("Porte de bunker ouverte en {} {} {} ({} min)", porte.getX(), porte.getY(), porte.getZ(), this.ouvertureMs() / 60_000L);
   }

   /** Annonce faite à tout le serveur quand la porte s'ouvre (montrée aussi au staff par /salle bunker). */
   public static Component annonce(String nom, long ouvertureMs) {
      String minutes = String.valueOf(ouvertureMs / 60_000L);
      return (nom == null || nom.isBlank()
         ? Component.translatableWithFallback("heralisz_bunker.bunker.ouverte",
            "La porte du bunker a été ouverte ! Elle reste ouverte %s min.", minutes)
         : Component.translatableWithFallback("heralisz_bunker.bunker.ouverte_nom",
            "La porte du %1$s a été ouverte ! Elle reste ouverte %2$s min.", nom, minutes)).withStyle(ChatFormatting.GOLD);
   }

   @Override
   public void bientotFermee(ServerLevel level, BlockPos porte, int secondes) {
      Component msg = Component.translatableWithFallback("heralisz_bunker.bunker.fermeture",
         "La porte du bunker se ferme dans %s s : sans carte, plus de sortie !", String.valueOf(secondes)).withStyle(ChatFormatting.RED);
      Vec3 c = Vec3.atCenterOf(porte);
      for (ServerPlayer p : level.players()) {
         if (p.distanceToSqr(c) <= RAYON_AVERTI * RAYON_AVERTI) p.sendSystemMessage(msg);
      }
   }
}
