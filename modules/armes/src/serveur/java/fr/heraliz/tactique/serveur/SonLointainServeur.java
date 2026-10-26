package fr.heraliz.tactique.serveur;

import com.tacz.guns.config.common.GunConfig;
import fr.heralisz.tactical.config.ServerConfig;
import fr.heraliz.tactique.son.PaquetTirLointain;
import fr.heraliz.tactique.son.ReseauSon;
import fr.heraliz.tactique.son.SonLointain;
import java.util.Random;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Tirs silencieux lointains (voir {@link SonLointain}) : pour chaque tir silencieux, les joueurs entre la portée nette
 * du silencieux et la portée d'un tir normal reçoivent le son, plus grave, d'un point flou autour du tireur. TaCZ joue
 * le son net jusqu'au double de la portée nette (outils/silencieux.py) en le faisant baisser : entre la portée nette et
 * ce double, le son lointain monte pendant que le son net s'éteint.
 * <p>25/10 : seulement les joueurs qui ont le tronçon du tireur dans leur distance de vue, comme TaCZ pour un tir normal
 * (SoundManager.sendSoundToNearby). Avant, tous les joueurs de la dimension jusqu'à 256 blocs : au-delà de la distance de
 * vue (8 tronçons, environ 128 blocs), un tir silencieux s'entendait alors qu'un tir sans silencieux ne s'entendait pas.
 */
final class SonLointainServeur {
   /** Le point flou d'un tireur reste le même pour un joueur pendant cette durée (en ticks : 30 s). */
   private static final long FENETRE_FLOU = 600L;
   /** Écart maximal du point flou, en blocs. */
   private static final double FLOU_MAX = 96.0;

   private SonLointainServeur() {
   }

   static void brancher() {
      SonLointain.TIR_SERVEUR.brancher(SonLointainServeur::tir);
   }

   private static void tir(SonLointain.Tir tir) {
      if (!ServerConfig.SON_LOINTAIN.get() || !(tir.tireur().level() instanceof ServerLevel niveau)) return;
      double nette = tir.distance() / 2.0;
      double loin = Math.max(GunConfig.DEFAULT_GUN_FIRE_SOUND_DISTANCE.get(), 2 * nette);
      if (nette <= 0) return;
      Vec3 source = tir.tireur().position();
      double volumeMax = ServerConfig.SON_LOINTAIN_VOLUME.get();
      double flou = ServerConfig.SON_LOINTAIN_FLOU.get();
      long fenetre = niveau.getGameTime() / FENETRE_FLOU;
      float hauteur = tir.hauteur() * SonLointain.GRAVE;
      for (ServerPlayer joueur : niveau.getChunkSource().chunkMap.getPlayers(new ChunkPos(tir.tireur().blockPosition()), false)) {
         if (joueur == tir.tireur()) continue;
         double d = joueur.position().distanceTo(source);
         if (d <= nette || d >= loin) continue;
         // baisse jusqu'à zéro à la portée d'un tir normal, comme les sons de TaCZ (volume mis au carré) ; monte entre la
         // portée nette et son double, pendant que le son net de TaCZ s'éteint
         double v = volumeMax * (1.0 - (d - nette) / (loin - nette));
         float volume = (float) (v * v * Math.min(1.0, (d - nette) / nette));
         if (volume < 0.002F) continue;
         Random r = new Random(tir.tireur().getUUID().getMostSignificantBits() * 31L
            ^ joueur.getUUID().getLeastSignificantBits() ^ fenetre * 0x9E3779B97F4A7C15L);
         double angle = r.nextDouble() * Math.PI * 2.0;
         double ecart = Math.min(d * flou, FLOU_MAX) * (0.5 + 0.5 * r.nextDouble());
         ReseauSon.envoyer(joueur, new PaquetTirLointain(tir.arme(), tir.affichage(), source.x + Math.cos(angle) * ecart,
            source.y + 1.0 + (r.nextDouble() - 0.5) * 6.0, source.z + Math.sin(angle) * ecart, volume, hauteur));
      }
   }
}
