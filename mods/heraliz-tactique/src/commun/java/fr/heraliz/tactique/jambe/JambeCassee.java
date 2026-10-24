package fr.heraliz.tactique.jambe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Jambe cassée (effet {@code geckolib:broken_bone}, soigné par l'attelle) : on peut sauter, mais sans aucun élan
 * en l'air (demande du propriétaire, 24/10 : avant, le saut était retiré) : le saut ne pousse plus vers l'avant (le
 * bond de la course est retiré, vitesse au sol d'avant le saut seulement, réduite de moitié) et la course est coupée.
 * <p>
 * Le saut est décidé chez le joueur (Minecraft) : la vitesse y est corrigée tout de suite, sans à-coup. Le serveur
 * voit le même saut quand il reçoit le déplacement (ServerGamePacketListenerImpl appelle jumpFromGround) : il corrige
 * aussi et le renvoie au joueur (hurtMarked, vraie correction de vitesse suivie par Spartan), pour un jeu modifié.
 */
public final class JambeCassee {
   private static final ResourceLocation EFFET = new ResourceLocation("geckolib", "broken_bone");
   private static MobEffect effet;
   private static boolean cherche;

   private JambeCassee() {
   }

   public static void init() {
      MinecraftForge.EVENT_BUS.addListener(JambeCassee::saut);
      MinecraftForge.EVENT_BUS.addListener(JambeCassee::enLAir);
   }

   /** En l'air avec la jambe cassée : jamais de course (elle donne plus de vitesse en l'air). Des deux côtés. */
   private static void enLAir(net.minecraftforge.event.TickEvent.PlayerTickEvent ev) {
      Player p = ev.player;
      if (ev.phase == net.minecraftforge.event.TickEvent.Phase.START && !p.onGround() && p.isSprinting() && !p.isCreative()
            && !p.isSpectator() && !p.isInWater() && cassee(p)) {
         p.setSprinting(false);
      }
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
      double x = v.x, z = v.z;
      if (p.isSprinting()) {
         // jumpFromGround vient d'ajouter le bond de la course (0,2 dans le sens du regard) : il est retiré
         float a = p.getYRot() * ((float) Math.PI / 180F);
         x += Math.sin(a) * 0.2;
         z -= Math.cos(a) * 0.2;
         p.setSprinting(false);
      }
      p.setDeltaMovement(x * 0.5, v.y, z * 0.5);
      if (!p.level().isClientSide) {
         p.hurtMarked = true;
      }
   }
}
