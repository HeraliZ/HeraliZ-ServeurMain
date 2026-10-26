package fr.heralisz.tactical.serveur;

import fr.heralisz.tactical.entity.EffectCloudGrenadeEntityServeur;
import fr.heralisz.tactical.entity.GrenadeEntityServeur;
import fr.heralisz.tactical.entity.SmokeGrenadeEntityServeur;
import fr.heralisz.tactical.entity.StickyGrenadeEntityServeur;
import fr.heralisz.tactical.entity.StunGrenadeEntityServeur;
import fr.heralisz.tactical.entity.sp.SpEffectCloudEntityServeur;
import fr.heralisz.tactical.init.ModEntities;
import fr.heralisz.tactical.init.ModItems;
import fr.heralisz.tactical.item.ConsumableItemServeur;
import fr.heralisz.tactical.item.DetonatorItemServeur;
import fr.heralisz.tactical.item.FlashShieldItemServeur;
import fr.heralisz.tactical.item.MeleeItemServeur;
import fr.heralisz.tactical.item.ThrowableItemServeur;
import fr.heraliz.tactique.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de heralisz_tactical (docs/METHODE_CLIENT_SERVEUR.md §4) : branche les versions serveur des objets (soins,
 * lancers, coups de mêlée, bouclier flash, détonateur) et des lancers (explosions, C4, flash, molotov / gaz, nuages au sol).
 * Les écouteurs serveur (ChargementServeur : index des packs et envoi aux joueurs ; EntityHurtEventHandler : brûlure ;
 * ShieldBlockEventHandler : bouclier) s'inscrivent seuls par leur @Mod.EventBusSubscriber(modid = "heralisz_tactical").
 * Appelée pendant le constructeur du @Mod, avant les événements d'enregistrement (piège n°6).
 */
public final class PartieServeur implements Partie {
   @Override
   public void init(IEventBus busMod) {
      ModItems.CONSUMABLE_USINE.serveur(a -> new ConsumableItemServeur());
      ModItems.THROWABLE_USINE.serveur(a -> new ThrowableItemServeur());
      ModItems.MELEE_USINE.serveur(a -> new MeleeItemServeur());
      ModItems.FLASH_SHIELD_USINE.serveur(a -> new FlashShieldItemServeur());
      ModItems.DETONATOR_USINE.serveur(a -> new DetonatorItemServeur());
      ModEntities.GRENADE_USINE.serveur(GrenadeEntityServeur::new);
      ModEntities.STICKY_GRENADE_USINE.serveur(StickyGrenadeEntityServeur::new);
      ModEntities.SMOKE_GRENADE_USINE.serveur(SmokeGrenadeEntityServeur::new);
      ModEntities.STUN_GRENADE_USINE.serveur(StunGrenadeEntityServeur::new);
      ModEntities.EFFECT_GRENADE_USINE.serveur(EffectCloudGrenadeEntityServeur::new);
      ModEntities.EFFECT_CLOUD_USINE.serveur(SpEffectCloudEntityServeur::new);
   }
}
