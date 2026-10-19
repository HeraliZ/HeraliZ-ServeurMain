package fr.heraliz.tournage;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Mod de tournage des vidéos : le jeu joue un script (TournageClient), le serveur tourne au ralenti (Lent). */
@Mod("tournage")
public class Tournage {
   public Tournage() {
      if (FMLEnvironment.dist == Dist.CLIENT) {
         TournageClient.brancher();
      }
   }
}
