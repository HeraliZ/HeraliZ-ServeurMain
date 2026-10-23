package fr.heraliz.tactique.jambe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Jambe cassée (effet {@code geckolib:broken_bone}, soigné par l'attelle) : on ne saute plus.
 * <p>
 * Le saut est décidé chez le joueur (Minecraft) : l'élan vers le haut y est retiré tout de suite, sans à-coup. Le serveur
 * voit le même saut quand il reçoit le déplacement (ServerGamePacketListenerImpl appelle jumpFromGround) : il retire
 * aussi l'élan et le renvoie au joueur (hurtMarked, vraie correction de vitesse suivie par Spartan), pour un jeu modifié
 * qui garderait son saut.
 */
public final class JambeCassee {
   private static final ResourceLocation EFFET = new ResourceLocation("geckolib", "broken_bone");
   private static MobEffect effet;
   private static boolean cherche;

   private JambeCassee() {
   }

   public static void init() {
      MinecraftForge.EVENT_BUS.addListener(JambeCassee::saut);
   }

   private static MobEffect effet() {
      if (!cherche) {
         cherche = true;
         effet = ForgeRegistries.MOB_EFFECTS.getValue(EFFET);
      }
      return effet;
   }

   /** Le joueur a-t-il la jambe cassée ? */
   public static boolean cassee(Player p) {
      MobEffect e = effet();
      return e != null && p.hasEffect(e);
   }

   private static void saut(LivingEvent.LivingJumpEvent ev) {
      if (!(ev.getEntity() instanceof Player p) || p.isCreative() || p.isSpectator() || !cassee(p)) {
         return;
      }
      Vec3 v = p.getDeltaMovement();
      if (v.y <= 0) {
         return;
      }
      p.setDeltaMovement(v.x, 0, v.z);
      if (!p.level().isClientSide) {
         p.hurtMarked = true;
      }
   }
}
