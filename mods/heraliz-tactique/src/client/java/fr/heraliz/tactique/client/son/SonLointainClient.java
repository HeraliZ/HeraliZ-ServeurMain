package fr.heraliz.tactique.client.son;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.sound.GunSoundInstance;
import com.tacz.guns.init.ModSounds;
import com.tacz.guns.sound.SoundManager;
import fr.heraliz.tactique.son.PaquetTirLointain;
import fr.heraliz.tactique.son.ReseauSon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/**
 * Joue un tir silencieux lointain reçu du serveur (fr.heraliz.tactique.son.SonLointain) : le son du silencieux de
 * l'arme, comme TaCZ le joue (ses fichiers de son, sans atténuation du jeu : volume déjà calculé par le serveur), mais
 * au point flou donné par le serveur et plus grave.
 */
public final class SonLointainClient {
   private SonLointainClient() {
   }

   public static void brancher() {
      ReseauSon.RECEPTION_CLIENT.brancher(SonLointainClient::jouer);
   }

   private static void jouer(PaquetTirLointain p) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer joueur = mc.player;
      if (joueur == null) return;
      TimelessAPI.getGunDisplay(p.affichage(), p.arme()).ifPresent(affichage -> {
         ResourceLocation son = affichage.getSounds(SoundManager.SILENCE_3P_SOUND);
         if (son != null) mc.getSoundManager().play(new Son(joueur, son, p));
      });
   }

   /** Son de TaCZ placé au point flou, au volume du serveur (le constructeur de TaCZ le calculerait d'après le tireur). */
   private static final class Son extends GunSoundInstance {
      Son(LocalPlayer joueur, ResourceLocation son, PaquetTirLointain p) {
         super(ModSounds.GUN.get(), SoundSource.PLAYERS, p.volume(), p.hauteur(), joueur, 1, son, true, false);
         this.x = p.x();
         this.y = p.y();
         this.z = p.z();
         this.volume = p.volume();
      }
   }
}
