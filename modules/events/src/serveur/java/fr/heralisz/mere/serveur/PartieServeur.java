package fr.heralisz.mere.serveur;

import fr.heraliz.boss.pont.Partie;
import fr.heralisz.mere.HeraliszMere;
import fr.heralisz.mere.dungeon.ModBlocks;
import fr.heralisz.mere.dungeon.NestHallBEServeur;
import fr.heralisz.mere.entity.RuinMotherServeur;
import fr.heralisz.mere.entity.SpiderlingServeur;
import fr.heralisz.mere.entity.WebGlobServeur;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie SERVEUR de la Mère des Ruines (logique de jeu) : chargée par le @Mod via Parties.serveur si elle est dans le jar
 * (-serveur ou -complet ; aussi pour le serveur intégré du solo). Branche les versions serveur de la Mère, des rejetons, des
 * globes de toile et du cœur du nid. La commande /boss mere (NestCommand) et le garde des apparitions (SpawnGuard) s'inscrivent
 * seuls (@Mod.EventBusSubscriber).
 */
public final class PartieServeur implements Partie {
   @Override
   public void init(IEventBus busMod) {
      HeraliszMere.RUIN_MOTHER_USINE.serveur(RuinMotherServeur::new);
      HeraliszMere.SPIDERLING_USINE.serveur(SpiderlingServeur::new);
      HeraliszMere.WEB_GLOB_USINE.serveur(WebGlobServeur::new);
      ModBlocks.NEST_USINE.serveur(NestHallBEServeur::new);
   }
}
