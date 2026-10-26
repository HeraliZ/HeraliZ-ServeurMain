package fr.heraliz.tactique.mixin.commun;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.entity.shooter.LivingEntityShoot;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Diagnostic (25/10, « balles qui disparaissent ») : le jeu du joueur tire (animation, son, flamme) et envoie le tir ;
 * le serveur peut encore le refuser sans rien lui dire (TaCZ : écart d'horloge du tir de plus de 300 ms, « réseau » ;
 * cadence ; course ; rechargement ; culasse ; refus d'un autre mod...). Le tir est alors perdu : aucune balle, aucun
 * dégât. Ici, rien ne change dans le jeu : les refus sont seulement comptés par joueur et par raison, et écrits dans le
 * journal du serveur au plus une fois toutes les 30 s (« [armes] tirs refusés par le serveur »).
 * <p>
 * Cible : LivingEntityShoot.shoot(Supplier, Supplier, long, float, boolean) de TaCZ 1.1.8 (privée, par laquelle passent
 * tous les tirs reçus des joueurs). Noms de TaCZ : remap = false. require = 0 : si TaCZ change, pas de diagnostic.
 */
@Mixin(value = LivingEntityShoot.class, remap = false)
public abstract class TaczTirsRefusesMixin {
   @Unique
   private static final Logger HERALIZ$LOG = LogUtils.getLogger();
   /** Un bilan au plus toutes les 30 s. */
   @Unique
   private static final long HERALIZ$BILAN_MS = 30_000L;
   @Unique
   private static final ShootResult[] HERALIZ$RAISONS = ShootResult.values();
   /** Refus depuis le dernier bilan : joueur -> nombre par raison (ordinal de ShootResult). Fil du serveur seulement. */
   @Unique
   private static final Map<UUID, int[]> HERALIZ$REFUS = new HashMap<>();
   @Unique
   private static final Map<UUID, String> HERALIZ$NOMS = new HashMap<>();
   @Unique
   private static long heraliz$prochainBilan;

   @Shadow
   @Final
   private LivingEntity shooter;

   @Inject(
      method = "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)Lcom/tacz/guns/api/entity/ShootResult;",
      at = @At("RETURN"),
      require = 0
   )
   private void heraliz$compterRefus(Supplier<Float> tangage, Supplier<Float> lacet, long heure, float charge, boolean avecCharge,
                                     CallbackInfoReturnable<ShootResult> cir) {
      ShootResult r = cir.getReturnValue();
      if (r == null || r == ShootResult.SUCCESS || !(this.shooter instanceof ServerPlayer joueur) || !joueur.server.isSameThread()) {
         return;
      }
      UUID id = joueur.getUUID();
      HERALIZ$REFUS.computeIfAbsent(id, k -> new int[HERALIZ$RAISONS.length])[r.ordinal()]++;
      HERALIZ$NOMS.put(id, joueur.getGameProfile().getName());
      long maintenant = System.currentTimeMillis();
      if (maintenant < heraliz$prochainBilan) {
         return;
      }
      heraliz$prochainBilan = maintenant + HERALIZ$BILAN_MS;
      StringBuilder texte = new StringBuilder();
      for (Map.Entry<UUID, int[]> e : HERALIZ$REFUS.entrySet()) {
         texte.append("\n  ").append(HERALIZ$NOMS.getOrDefault(e.getKey(), e.getKey().toString())).append(" :");
         int[] n = e.getValue();
         for (int i = 0; i < n.length; i++) {
            if (n[i] > 0) texte.append(' ').append(HERALIZ$RAISONS[i].name()).append('=').append(n[i]);
         }
      }
      HERALIZ$REFUS.clear();
      HERALIZ$NOMS.clear();
      HERALIZ$LOG.info("[armes] tirs refusés par le serveur depuis le dernier bilan (le joueur a vu son tir partir, aucune "
         + "balle n'est partie ; NETWORK_FAIL = écart d'horloge du tir, IS_SPRINTING = encore en course pour le serveur, "
         + "COOL_DOWN = cadence) :{}", texte);
   }
}
