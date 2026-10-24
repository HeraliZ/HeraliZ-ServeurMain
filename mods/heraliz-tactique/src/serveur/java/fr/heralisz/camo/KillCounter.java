package fr.heralisz.camo;

import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Compteur de kills des armes skinnées (côté serveur) : chaque kill fait avec une arme qui porte un camo ajoute 1 à son NBT
 * (CamoData.KILLS). Le compteur suit l'arme comme le camo ; le client le reçoit avec l'objet (skin Trophée : paliers et
 * compteur façon StatTrak).
 */
@Mod.EventBusSubscriber(modid = HeraliszCamo.MODID)
public final class KillCounter {
   private KillCounter() {
   }

   @SubscribeEvent
   public static void onKill(EntityKillByGunEvent event) {
      if (!event.getLogicalSide().isServer()) return;
      LivingEntity attacker = event.getAttacker();
      if (attacker == null || event.getKilledEntity() == null || event.getKilledEntity() == attacker) return;
      ItemStack gun = attacker.getMainHandItem();
      // armes skinnées (Trophée...) et armes équipées du module StatTrack
      if (IGun.getIGunOrNull(gun) == null || CamoData.get(gun) == null && !fr.heralisz.camo.decor.DecorData.hasStatTrak(gun)) return;
      CamoData.addKill(gun);
   }
}
