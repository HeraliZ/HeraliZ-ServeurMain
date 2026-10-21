package fr.heraliz.tactique.serveur;

import fr.heraliz.tactique.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur du mod porteur : les règles de « s'allonger » (AllongeServeur), les vitres cassées par les balles,
 * qui repoussent (VitresServeur, diagnostic du staff {@code /vitres pourquoi} : CommandeVitres), les tirs silencieux
 * entendus de loin (SonLointainServeur), les coups des couteaux TaCZ (CouteauxServeur) et les dégâts des armes TaCZ
 * contre les joueurs (DegatsArmes, config heraliz_tactique/armes.json).
 */
public final class ServeurTactique implements Partie {
   @Override
   public void init(IEventBus busMod) {
      AllongeServeur.brancher();
      VitresServeur.brancher();
      SonLointainServeur.brancher();
      CouteauxServeur.brancher();
      DegatsArmes.brancher();
      ObjetsAVieServeur.brancher();
   }
}
