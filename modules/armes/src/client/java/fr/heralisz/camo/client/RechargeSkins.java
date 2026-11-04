package fr.heralisz.camo.client;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IGun;
import fr.heralisz.camo.Camo;
import fr.heralisz.camo.HeraliszCamo;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rechargement des skins qui en ont un (Camo.rechargeAnime : Faille, Accès Rouge), pour tous les joueurs visibles.
 * L'état du rechargement vient de TaCZ (getSynReloadState, envoyé par le serveur) : phase « chargement » jusqu'au moment
 * où les munitions entrent dans l'arme (le chargeur est enclenché), puis « fin ». On en tire une avancée u de 0 à 1 sur
 * tout le rechargement (enclenché à 0,88), que lisent le dessin du skin (phase du chargeur, CamoReload) et ses effets 3D.
 * Seuls les joueurs qui tiennent une arme avec un de ces skins sont suivis ; rien d'autre ne tourne.
 */
@Mod.EventBusSubscriber(modid = HeraliszCamo.MODID, value = Dist.CLIENT)
public final class RechargeSkins {
   /** Avancée au moment où le chargeur est enclenché, et durée (ms) de la fin de l'effet après lui. */
   public static final float CLIC = 0.88F;
   private static final long FIN_MS = 450;
   private static final Map<UUID, Etat> PAR_JOUEUR = new HashMap<>();
   private static final Map<ItemStack, Etat> PAR_ARME = new IdentityHashMap<>();

   private static final class Etat {
      boolean charge;
      long debut, duree, clic = -1;
   }

   private RechargeSkins() {
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) return;
      Minecraft mc = Minecraft.getInstance();
      PAR_ARME.clear();
      if (mc.level == null) {
         PAR_JOUEUR.clear();
         return;
      }
      long now = Util.getMillis();
      for (AbstractClientPlayer p : mc.level.players()) {
         ItemStack s = p.getMainHandItem();
         Camo camo = IGun.getIGunOrNull(s) != null ? CamoClient.camoFor(s) : null;
         if (camo == null || !camo.rechargeAnime()) {
            if (!PAR_JOUEUR.isEmpty()) PAR_JOUEUR.remove(p.getUUID());
            continue;
         }
         // l'objet en main change d'instance quand le serveur met à jour les munitions (au moment même de l'enclenchement) :
         // on garde l'état ; changer d'arme annule le rechargement dans TaCZ, ce qui est vu plus bas
         Etat e = PAR_JOUEUR.computeIfAbsent(p.getUUID(), k -> new Etat());
         ReloadState rs;
         try {
            rs = IGunOperator.fromLivingEntity(p).getSynReloadState();
         } catch (RuntimeException | LinkageError err) {
            continue;
         }
         ReloadState.StateType st = rs == null ? null : rs.getStateType();
         boolean chargement = st != null && st.isReloading() && !st.isReloadFinishing();
         if (chargement) {
            if (!e.charge) {
               e.charge = true;
               e.debut = now;
               e.duree = Math.max(300, rs.getCountDown());
               e.clic = -1;
            }
         } else if (e.charge) {
            e.charge = false;
            // fin du chargement : enclenché (munitions entrées), sauf rechargement annulé bien avant la fin
            boolean annule = st == null || !st.isReloading() && now - e.debut < e.duree * 0.8F;
            e.clic = annule ? -1 : now;
         }
         PAR_ARME.put(s, e);
      }
   }

   /** Avancée du rechargement de cette arme (0..1, enclenchée à CLIC) ; -1 : pas de rechargement en cours. */
   public static float avancee(ItemStack gun) {
      Etat e = PAR_ARME.get(gun);
      if (e == null) return -1;
      long now = Util.getMillis();
      if (e.charge) {
         return CLIC * Math.min(1F, (now - e.debut) / (float) e.duree);
      }
      if (e.clic >= 0 && now - e.clic < FIN_MS) {
         return CLIC + (1 - CLIC) * (now - e.clic) / (float) FIN_MS;
      }
      return -1;
   }

   /**
    * Phase du chargeur pour le dessin du skin (CamoReload) : -1 aucune ; 0..1 l'ancien chargeur disparaît ; 1..2 le nouveau
    * arrive ; 2..3 enclenché. Mêmes temps que les rendus validés (maquettes/skins_premium).
    */
   public static float phase(Camo camo, float u) {
      if (u < 0 || camo == null) return -1;
      if (camo == Camo.FAILLE) {
         if (u < 0.08F) return -1;
         if (u < 0.45F) return (u - 0.08F) / 0.37F;
         if (u < 0.55F) return 1;
         if (u < 0.80F) return 1 + 0.85F * (u - 0.55F) / 0.25F;
         if (u < CLIC) return 1.85F + 0.14F * (u - 0.80F) / 0.08F;
         return 2 + (u - CLIC) / (1 - CLIC) * 0.6F;
      }
      if (camo == Camo.ACCES_ROUGE) {
         if (u < 0.08F) return -1;
         if (u < 0.40F) return (u - 0.08F) / 0.32F;
         if (u < 0.44F) return 1;
         if (u < 0.74F) return 1 + 0.97F * (u - 0.44F) / 0.30F;
         if (u < CLIC) return 1.99F;
         return 2 + (u - CLIC) / (1 - CLIC) * 0.95F;
      }
      return -1;
   }
}
