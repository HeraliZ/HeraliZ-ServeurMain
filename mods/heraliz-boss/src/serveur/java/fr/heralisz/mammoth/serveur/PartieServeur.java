package fr.heralisz.mammoth.serveur;

import fr.heraliz.boss.pont.Partie;
import fr.heralisz.mammoth.HeraliszMammoth;
import fr.heralisz.mammoth.dungeon.HornAltarBlockEntityServeur;
import fr.heralisz.mammoth.dungeon.ModBlocks;
import fr.heralisz.mammoth.entity.DraugrServeur;
import fr.heralisz.mammoth.entity.GuardianSpearServeur;
import fr.heralisz.mammoth.entity.MammothServeur;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie SERVEUR d'Ymirak (logique de jeu) : chargée par le @Mod via Parties.serveur si elle est dans le jar (-serveur ou
 * -complet ; aussi pour le serveur intégré du solo). Branche les versions serveur des entités et de l'autel. La commande
 * /mammouth (MammothCommand) et le garde des apparitions (SpawnGuard) s'inscrivent seuls (@Mod.EventBusSubscriber).
 */
public final class PartieServeur implements Partie {
   @Override
   public void init(IEventBus busMod) {
      HeraliszMammoth.MAMMOTH_USINE.serveur(MammothServeur::new);
      HeraliszMammoth.DRAUGR_USINE.serveur(DraugrServeur::new);
      HeraliszMammoth.GUARDIAN_SPEAR_USINE.serveur(GuardianSpearServeur::new);
      ModBlocks.ALTAR_USINE.serveur(HornAltarBlockEntityServeur::new);
   }
}
