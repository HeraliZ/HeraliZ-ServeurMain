package fr.heralisz.n07.serveur;

import fr.heraliz.boss.pont.Partie;
import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.block.ContainmentVatBlockEntityServeur;
import fr.heralisz.n07.decor.DecorBlockServeur;
import fr.heralisz.n07.decor.DecorPartBlockServeur;
import fr.heralisz.n07.decor.DecorRegistry;
import fr.heralisz.n07.dungeon.DungeonRegistry;
import fr.heralisz.n07.dungeon.PropBlockEntityServeur;
import fr.heralisz.n07.dungeon.RadChannelBlockServeur;
import fr.heralisz.n07.dungeon.ShrineBlockEntityServeur;
import fr.heralisz.n07.dungeon.SludgeBlockServeur;
import fr.heralisz.n07.dungeon.Teleporter935BlockServeur;
import fr.heralisz.n07.effect.IrradiatedEffectServeur;
import fr.heralisz.n07.entity.BossRemainsServeur;
import fr.heralisz.n07.entity.N07BoulderServeur;
import fr.heralisz.n07.entity.N07IsotopeServeur;
import fr.heralisz.n07.entity.N07ShellServeur;
import fr.heralisz.n07.entity.SujetN07Serveur;
import fr.heralisz.n07.scene.SceneRunner;
import fr.heralisz.n07.scene.ScenePackets;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie SERVEUR du Sujet N-07 (logique de jeu) : chargée par le @Mod via Parties.serveur si elle est dans le jar (-serveur ou
 * -complet ; aussi pour le serveur intégré du solo). Branche les versions serveur des entités (boss, projectiles, statue), des
 * entités de bloc (terminal, décors animés, cuve de confinement), des blocs à effets (rigoles, boue, téléporteur 935, décors
 * du spawn qui posent leurs cases), de
 * l'effet Irradié, et la réception de l'appui « passer » des scènes. Les commandes (/n07dev, donjons, décors, salle Zombies),
 * l'API pour les autres mods (N07Api), les salles, combats et scènes s'inscrivent seuls (@Mod.EventBusSubscriber) ou sont
 * appelés par le code serveur.
 */
public final class PartieServeur implements Partie {
   @Override
   public void init(IEventBus busMod) {
      // entités
      HeraliszN07.SUJET_N07_USINE.serveur(SujetN07Serveur::new);
      HeraliszN07.N07_SHELL_USINE.serveur(N07ShellServeur::new);
      HeraliszN07.N07_BOULDER_USINE.serveur(N07BoulderServeur::new);
      HeraliszN07.N07_ISOTOPE_USINE.serveur(N07IsotopeServeur::new);
      HeraliszN07.BOSS_REMAINS_USINE.serveur(BossRemainsServeur::new);
      // effet, entités de bloc, blocs
      HeraliszN07.IRRADIATED_USINE.serveur(a -> new IrradiatedEffectServeur());
      HeraliszN07.CONTAINMENT_VAT_BE_USINE.serveur(ContainmentVatBlockEntityServeur::new);
      DungeonRegistry.SHRINE_USINE.serveur(ShrineBlockEntityServeur::new);
      DungeonRegistry.PROP_USINE.serveur(PropBlockEntityServeur::new);
      DungeonRegistry.CHANNEL_USINE.serveur(RadChannelBlockServeur::new);
      DungeonRegistry.SLUDGE_USINE.serveur(SludgeBlockServeur::new);
      DungeonRegistry.TELEPORTER_935_USINE.serveur(a -> new Teleporter935BlockServeur());
      DecorRegistry.DECOR_USINE.serveur(DecorBlockServeur::new);
      DecorRegistry.PART_USINE.serveur(DecorPartBlockServeur::new);
      // réseau : appui « passer » (client -> serveur)
      ScenePackets.HOLD_SERVEUR.brancher(h -> SceneRunner.onHold(h.player(), h.run(), h.on()));
   }
}
