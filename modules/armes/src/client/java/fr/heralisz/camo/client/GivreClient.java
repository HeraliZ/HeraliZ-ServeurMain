package fr.heralisz.camo.client;

import com.tacz.guns.api.item.IGun;
import fr.heralisz.camo.Camo;
import fr.heralisz.camo.CamoData;
import fr.heralisz.camo.GivreVivant;
import fr.heralisz.camo.network.CamoNetwork;
import fr.heralisz.camo.network.GivrePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Givre Vivant côté joueur (22/11) : l'inspection de l'arme envoie la demande de bascule au serveur, qui seul change
 * l'état gardé sur l'arme ; le dessin de chaque arme Givre Vivant (la sienne, celles des autres, au sol, en icône) lit
 * cet état et l'heure du monde pour l'avancement du gel ou du dégel (uniforme CamoEtat, posé par CamoRenderType).
 */
public final class GivreClient {
   /** Avancement (0 acier nu, 1 givrée) et sens (1 gel, -1 dégel : gouttes d'eau) de l'arme Givre Vivant à dessiner. */
   public static float pendingEtat = 0.0F;
   public static float pendingSens = 1.0F;

   private GivreClient() {
   }

   /** Début d'une inspection (son d'inspection de TaCZ, joué pour le joueur local seulement : SoundPlayManagerMixin). */
   public static void inspection(LivingEntity entity) {
      Minecraft mc = Minecraft.getInstance();
      if (entity != mc.player || mc.getConnection() == null) {
         return;
      }
      ItemStack arme = entity.getMainHandItem();
      if (CamoData.get(arme) == Camo.GIVRE_VIVANT && IGun.getIGunOrNull(arme) != null) {
         CamoNetwork.CHANNEL.sendToServer(new GivrePacket());
      }
   }

   /**
    * Avant le dessin d'une arme Givre Vivant (BedrockGunModelMixin) : son état à cette image. {@code fixe} (dessin pour
    * une interface, dont la photo de l'icône d'inventaire, IconesArmes) : l'état final, sans le passage en cours (une
    * icône photographiée en plein gel le garderait jusqu'au prochain rechargement).
    */
   public static void preparer(ItemStack arme, boolean fixe) {
      boolean givree = GivreVivant.givree(arme);
      if (fixe) {
         pendingEtat = givree ? 1.0F : 0.0F;
      } else {
         Minecraft mc = Minecraft.getInstance();
         double temps = mc.level == null ? 0.0 : mc.level.getGameTime() + (double) mc.getFrameTime();
         pendingEtat = GivreVivant.etat(arme, temps);
      }
      pendingSens = givree ? 1.0F : -1.0F;
   }
}
