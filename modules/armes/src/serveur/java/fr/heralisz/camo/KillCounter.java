package fr.heralisz.camo;

import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Compteur de kills des armes équipées du module StatTrack (côté serveur) : chaque kill ajoute 1 à leur NBT
 * (CamoData.KILLS), que le client affiche sur la plaquette. (Avant le 04/11, les armes skinnées comptaient aussi, pour le
 * skin Trophée, supprimé.)
 */
@Mod.EventBusSubscriber(modid = HeraliszCamo.MODID)
public final class KillCounter {
   /** Drapeau posé par HeraliZ Matchs dans persistentData d'un joueur en match (ses kills ne comptent pas). */
   public static final String EN_MATCH = "heralisz:en_match";

   private KillCounter() {
   }

   @SubscribeEvent
   public static void onKill(EntityKillByGunEvent event) {
      if (!event.getLogicalSide().isServer()) return;
      LivingEntity attacker = event.getAttacker();
      if (attacker == null || event.getKilledEntity() == null || event.getKilledEntity() == attacker) return;
      // tueur en match (HeraliZ Matchs : ClashWar, duels, zombies) : le kill ne compte pas
      if (attacker.getPersistentData().contains(EN_MATCH)) return;
      ItemStack gun = attacker.getMainHandItem();
      if (IGun.getIGunOrNull(gun) == null || !fr.heralisz.camo.decor.DecorData.hasStatTrak(gun)) return;
      CamoData.addKill(gun);
   }
}
