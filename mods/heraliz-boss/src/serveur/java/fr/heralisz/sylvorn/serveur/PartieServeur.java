package fr.heralisz.sylvorn.serveur;

import fr.heraliz.boss.pont.Partie;
import fr.heralisz.sylvorn.HeraliszSylvorn;
import fr.heralisz.sylvorn.dungeon.ModBlocks;
import fr.heralisz.sylvorn.dungeon.TreeHallBEServeur;
import fr.heralisz.sylvorn.entity.SapNodeServeur;
import fr.heralisz.sylvorn.entity.SeedPodServeur;
import fr.heralisz.sylvorn.entity.SylvornLegsServeur;
import fr.heralisz.sylvorn.entity.SylvornServeur;
import fr.heralisz.sylvorn.entity.ThornSpearServeur;
import fr.heralisz.sylvorn.entity.ThrownLogServeur;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie SERVEUR de Sylvorn (logique de jeu) : chargée par le @Mod via Parties.serveur si elle est dans le jar (-serveur ou
 * -complet ; aussi pour le serveur intégré du solo). Branche les versions serveur des entités (le boss, ses jambes arrachées,
 * ses projectiles, les nœuds de sève) et de la salle de l'Arbre-Monde. La commande /sylvorn (TreeCommand) s'inscrit seule
 * (@Mod.EventBusSubscriber).
 */
public final class PartieServeur implements Partie {
   @Override
   public void init(IEventBus busMod) {
      HeraliszSylvorn.SYLVORN_USINE.serveur(SylvornServeur::new);
      HeraliszSylvorn.SYLVORN_LEGS_USINE.serveur(SylvornLegsServeur::new);
      HeraliszSylvorn.THROWN_LOG_USINE.serveur(ThrownLogServeur::new);
      HeraliszSylvorn.SEED_POD_USINE.serveur(SeedPodServeur::new);
      HeraliszSylvorn.SAP_NODE_USINE.serveur(SapNodeServeur::new);
      HeraliszSylvorn.THORN_SPEAR_USINE.serveur(ThornSpearServeur::new);
      ModBlocks.TREE_USINE.serveur(TreeHallBEServeur::new);
   }
}
