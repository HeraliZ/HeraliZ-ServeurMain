package fr.heraliz.noyau.serveur.sons;

import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.serveur.perf.VoirBukkit;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Bruits de pas des autres joueurs et des monstres (03/11, retour du propriétaire après son premier passage en jeu : « on
 * n'entend pas assez les bruits de pas des ennemis »). Réglages : section {@code [pas]} de {@code heraliz_noyau-server.toml}.
 * <p><b>D'où viennent ces sons dans Minecraft.</b> Le jeu d'un joueur ne fait jamais marcher les autres joueurs : il les
 * déplace d'un point reçu au suivant ({@code RemotePlayer}), sans bruit. C'est le SERVEUR qui refait le pas de chaque
 * joueur (il rejoue son déplacement, {@code Entity.move}) et qui envoie le son du pas (volume 0,15 x celui du bloc) à tous
 * les autres joueurs à moins de 16 blocs ; le joueur qui marche n'en reçoit pas : son propre jeu joue ses pas lui-même.
 * Chez celui qui l'entend, le son baisse en ligne droite jusqu'à 0 à 16 blocs (atténuation de Minecraft) : à 8 blocs, il
 * ne reste que 0,075. Les pas des zombies sont envoyés de même (0,15, entendus jusqu'à 11 blocs par le pack de ressources
 * des joueurs, qui baisse aussi tous les sons des zombies de moitié depuis le 24/10).
 * <p><b>Ce qui change ici</b> (rien dans le jeu des joueurs, aucun paquet nouveau, ses propres pas jamais touchés) :
 * <ul>
 * <li>pas d'un joueur : envoyé aux autres jusqu'à {@code portee_joueurs} blocs (32), entendu {@code volume_joueurs} fois
 * (2) plus fort tout près, puis de moins en moins fort jusqu'à 0 à cette distance. Le jeu d'un joueur ne sait pas étendre
 * son atténuation de 16 blocs : chaque joueur reçoit donc son propre envoi. Jusqu'à {@link #EXACT} blocs, le son garde sa
 * vraie place et son volume est relevé pour compenser l'atténuation du jeu ; plus loin, il est rapproché à {@link #EXACT}
 * blocs sur la même ligne (même direction, donc on sait d'où il vient) avec le volume voulu à sa vraie distance. Mêmes
 * destinataires que Minecraft sinon : même monde, jamais l'auteur, et le vanish du staff (test de Bukkit sous Mohist,
 * {@link VoirBukkit#entend}) ;</li>
 * <li>pas d'un monstre : volume x {@code volume_monstres} (2), distance inchangée.</li>
 * </ul>
 * Rien ne tourne entre deux sons : seulement au son d'un pas, comme l'envoi de Minecraft qu'il remplace (un passage sur
 * les joueurs du monde). Fil serveur seulement.
 */
public final class PasEntendus {
   /** Jusqu'à cette distance (blocs), le son est joué à sa vraie place ; au-delà, rapproché à cette distance. */
   static final double EXACT = 8.0;
   /** Distance à laquelle le jeu d'un joueur éteint un son de volume 1 au plus (attenuation_distance de Minecraft). */
   static final double ATTENUATION_JEU = 16.0;

   private PasEntendus() {}

   public static void enregistrer() {
      // le plus tard possible : les autres mods ont déjà vu (ou changé, ou annulé) le son
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, PasEntendus::son);
   }

   /** Le son d'un pas : tous les sons de pas de Minecraft et des mods finissent par « .step ». */
   static boolean estUnPas(SoundEvent son) {
      return son.getLocation().getPath().endsWith(".step");
   }

   private static void son(PlayLevelSoundEvent.AtPosition e) {
      SoundSource source = e.getSource();
      if (source != SoundSource.PLAYERS && source != SoundSource.HOSTILE || !NoyauConfig.pasActif) return;
      // seulement le monde du serveur (le jeu complet en solo voit aussi les sons de son monde à lui)
      if (!(e.getLevel() instanceof ServerLevel monde) || !monde.getServer().isSameThread()) return;
      Holder<SoundEvent> son = e.getSound();
      if (son == null || !estUnPas(son.value())) return;
      if (source == SoundSource.HOSTILE) {
         float v = e.getNewVolume();
         // au plus 1 : au-delà, Minecraft l'enverrait plus loin (16 x volume)
         if (v < 1.0F) e.setNewVolume(Math.min(1.0F, v * NoyauConfig.pasVolumeMonstres));
         return;
      }
      if (!VoirBukkit.pret()) return;   // vanish impossible à respecter : envoi de Minecraft
      Vec3 p = e.getPosition();
      double x = p.x, y = p.y, z = p.z;
      List<ServerPlayer> joueurs = monde.players();
      int n = joueurs.size();
      // l'auteur : Minecraft joue le pas d'un joueur exactement à sa position (Player.playSound)
      ServerPlayer auteur = null;
      for (int i = 0; i < n; i++) {
         ServerPlayer j = joueurs.get(i);
         if (j.getX() == x && j.getY() == y && j.getZ() == z) {
            auteur = j;
            break;
         }
      }
      if (auteur == null) return;   // son de pas posé ailleurs par un mod : envoi de Minecraft
      double portee = NoyauConfig.pasPortee;
      float voulu0 = e.getNewVolume() * NoyauConfig.pasVolumeJoueurs, hauteur = e.getNewPitch();
      long graine = ThreadLocalRandom.current().nextLong();
      for (int i = 0; i < n; i++) {
         ServerPlayer j = joueurs.get(i);
         if (j == auteur) continue;
         double ox = j.getX(), oy = j.getEyeY(), oz = j.getZ();   // oreilles : le jeu écoute depuis la caméra
         double dx = x - ox, dy = y - oy, dz = z - oz;
         double d2 = dx * dx + dy * dy + dz * dz;
         if (d2 >= portee * portee || !VoirBukkit.entend(j, auteur)) continue;
         double d = Math.sqrt(d2);
         double voulu = voulu0 * (1.0 - d / portee);
         double place = Math.min(d, EXACT);
         float volume = (float) Math.min(1.0, voulu / (1.0 - place / ATTENUATION_JEU));
         double f = d > EXACT ? EXACT / d : 1.0;
         j.connection.send(new ClientboundSoundPacket(son, source, ox + dx * f, oy + dy * f, oz + dz * f, volume, hauteur, graine));
      }
      e.setCanceled(true);   // envoyé ici à la place de Minecraft
   }
}
