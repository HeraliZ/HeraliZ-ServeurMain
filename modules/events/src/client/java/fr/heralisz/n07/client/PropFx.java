package fr.heralisz.n07.client;

import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.dungeon.PropBlockEntity;
import fr.heralisz.n07.particle.ModParticles;
import fr.heralisz.n07.sound.BossSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/** Vie des décors côté client : éclaboussures, brume et gouttes des buses, braises des cuves, sons d'ambiance en boucle. */
@Mod.EventBusSubscriber(modid = HeraliszN07.MODID, value = Dist.CLIENT)
public final class PropFx {
   private PropFx() {}

   private static final Map<BlockPos, Loop> LOOPS = new HashMap<>();

   public static void tick(PropBlockEntity be) {
      Minecraft mc = Minecraft.getInstance();
      Level l = be.getLevel();
      if (mc.player == null || l == null) return;
      Vec3 c = Vec3.atCenterOf(be.getBlockPos());
      double d2 = mc.player.distanceToSqr(c);
      if (d2 > 56 * 56) return;
      RandomSource r = l.random;
      switch (be.kind) {
         case GIANT_FAN -> {
            // neige qui tombe dans les puits de lumière du ventilateur géant
            for (int i = 0; i < 2; i++) {
               double a = r.nextDouble() * Math.PI * 2, rr = Math.sqrt(r.nextDouble()) * 3.6;
               l.addParticle(ParticleTypes.SNOWFLAKE, c.x + Math.cos(a) * rr, c.y - 1 - r.nextDouble() * 14, c.z + Math.sin(a) * rr,
                  (r.nextDouble() - 0.5) * 0.02, -0.03, (r.nextDouble() - 0.5) * 0.02);
            }
         }
         case WASTE_PIPE, SLUDGE_PIPE -> {
            if (be.flowAmount(0) < 0.99F) return;   // buse coupee (ou qui demarre) : ni eclaboussures ni son
            boolean sl = be.kind.sludge();
            double[] ld = PropRenderer.landing(be);
            for (int i = 0; i < 2; i++) {
               double a = r.nextDouble() * Math.PI * 2, s = 0.06 + r.nextDouble() * 0.08;
               l.addParticle(ModParticles.ACID_SPLASH.get(), ld[0] + Math.cos(a) * 0.3, ld[1] + 0.05, ld[2] + Math.sin(a) * 0.3, Math.cos(a) * s,
                  0.12 + r.nextDouble() * 0.12, Math.sin(a) * s);
            }
            if (r.nextInt(sl ? 2 : 3) == 0) {
               l.addParticle(ModParticles.TOXIC_SMOKE.get(), ld[0] + (r.nextDouble() - 0.5) * 1.2, ld[1] + 0.15, ld[2] + (r.nextDouble() - 0.5) * 1.2,
                  0, 0.025 + r.nextDouble() * 0.02, 0);
            }
            if (r.nextInt(8) == 0) {
               Vec3 lip = c.add(be.front().scale(PropRenderer.MOUTH - 0.1)).add(0, -0.85, 0);
               l.addParticle(ModParticles.ACID_DROP.get(), lip.x + (r.nextDouble() - 0.5) * 0.8, lip.y, lip.z + (r.nextDouble() - 0.5) * 0.8, 0, -0.05, 0);
            }
            if (!sl && r.nextInt(14) == 0) l.addParticle(ModParticles.RAD_EMBER.get(), ld[0], ld[1] + 0.4, ld[2], 0, 0.04, 0);
            if (d2 < 32 * 32) loop(be, BossSounds.DG_POUR.get(), 1.0F, new Vec3(ld[0], ld[1], ld[2]));
         }
         case CAGE_LAMP, CHANDELIER, SPORE_LANTERN, BROOD_LAMP -> lamp(be, l, r, c, d2);
         case CONTAINMENT_TANK -> tank(be, l, r, d2);
         case RAD_VAT, SLUDGE_VAT -> {
            if (r.nextInt(5) == 0) {
               l.addParticle(be.kind.sludge() ? ModParticles.TOXIC_SMOKE.get() : ModParticles.RAD_EMBER.get(), c.x + (r.nextDouble() - 0.5) * 3,
                  c.y + 7.2, c.z + (r.nextDouble() - 0.5) * 3, 0, 0.03, 0);
            }
            if (r.nextInt(30) == 0) {
               Vec3 v = c.add(be.front().scale(2.4)).add(0, 1.2, 0);
               l.addParticle(ParticleTypes.CLOUD, v.x, v.y, v.z, be.front().x * 0.05, 0.02, be.front().z * 0.05);
            }
            if (d2 < 24 * 24) loop(be, BossSounds.DG_VAT.get(), 0.8F, c.add(0, 2, 0));
         }
      }
   }

   /** Cuve de confinement : bulles, vapeur, grésillement des arcs, sons de charge et de remplissage. */
   private static void tank(PropBlockEntity be, Level l, RandomSource r, double d2) {
      BlockPos p = be.getBlockPos();
      double cx = p.getX() + 0.5, cz = p.getZ() + 0.5, y0 = p.getY() + 1;
      if (l.getGameTime() % 20 == 0 || be.clientBoss != null && !be.clientBoss.isAlive()) {
         var list = l.getEntitiesOfClass(fr.heralisz.n07.entity.SujetN07.class, new net.minecraft.world.phys.AABB(p).inflate(8));
         be.clientBoss = list.isEmpty() ? null : list.get(0);
      }
      TankView v = TankView.of(be, 0);
      int code = v.broken ? (v.glass < 1 && v.shrineState == fr.heralisz.n07.dungeon.ShrineBlockEntity.COOLDOWN ? 4 : 3)
         : v.charge > 0.02F ? (v.spawnT >= 0 ? 2 : 1) : v.glass < 1 ? 4 : 0;
      if (code != be.clientTankState) {
         if (code == 1 && be.clientTankState == 0) l.playLocalSound(cx, y0 + 6, cz, BossSounds.DG_COIL_CHARGE.get(), SoundSource.BLOCKS, 3F, 1F, false);
         if (code == 4 && be.clientTankState == 3) l.playLocalSound(cx, y0 + 3, cz, BossSounds.DG_TANK_REFILL.get(), SoundSource.BLOCKS, 2.5F, 1F, false);
         be.clientTankState = code;
      }
      if (!v.broken && v.level > 0.2F) {
         for (int i = 0; i < 2; i++) {
            double a = r.nextDouble() * Math.PI * 2, rr = r.nextDouble() * 2.6;
            l.addParticle(ModParticles.RAD_EMBER.get(), cx + Math.cos(a) * rr, y0 + 1 + r.nextDouble() * 8 * v.level, cz + Math.sin(a) * rr, 0, 0.05, 0);
         }
         if (d2 < 32 * 32) loop(be, BossSounds.DG_VAT.get(), 1.0F, new Vec3(cx, y0 + 4, cz));
      }
      if (v.charge > 0.05F) {
         if (r.nextFloat() < 0.15F + 0.35F * v.charge) {
            double a = r.nextDouble() * Math.PI * 2;
            l.addParticle(ParticleTypes.ELECTRIC_SPARK, cx + Math.cos(a) * 3.1, y0 + 2 + r.nextDouble() * 7, cz + Math.sin(a) * 3.1, 0, 0, 0);
         }
         if (r.nextFloat() < 0.04F + 0.12F * v.charge) l.playLocalSound(cx, y0 + 6, cz, BossSounds.DG_ARC_ZAP.get(), SoundSource.BLOCKS, 1.2F + v.charge,
            0.8F + r.nextFloat() * 0.4F, false);
         if (r.nextInt(3) == 0) l.addParticle(ParticleTypes.CLOUD, cx + (r.nextDouble() - 0.5) * 3, y0 + 10.4, cz + (r.nextDouble() - 0.5) * 3, 0, 0.05, 0);
      }
      if (v.broken && v.shrineState != fr.heralisz.n07.dungeon.ShrineBlockEntity.COOLDOWN || v.broken && v.glass <= 0) {
         // câbles arrachés qui crachent des étincelles, liquide qui goutte des dents de verre
         if (r.nextInt(12) == 0) l.addParticle(ParticleTypes.ELECTRIC_SPARK, cx + (r.nextDouble() - 0.5) * 1.5, y0 + 9.8, cz + (r.nextDouble() - 0.5) * 1.5,
            (r.nextDouble() - 0.5) * 0.2, -0.1, (r.nextDouble() - 0.5) * 0.2);
         if (r.nextInt(6) == 0) {
            double a = r.nextDouble() * Math.PI * 2;
            l.addParticle(ModParticles.ACID_DROP.get(), cx + Math.cos(a) * 3.0, y0 + 9.8, cz + Math.sin(a) * 3.0, 0, -0.05, 0);
         }
      }
   }

   /** Luminaires : étincelles et grésillement quand une ampoule saute, fumée des bougies, spores, gouttes d'ambre. */
   private static void lamp(PropBlockEntity be, Level l, RandomSource r, Vec3 c, double d2) {
      BlockPos p = be.getBlockPos();
      double by = p.getY() + be.kind.bulbY();
      switch (be.kind) {
         case CAGE_LAMP -> {
            var st = be.getBlockState();
            int pw = st.hasProperty(fr.heralisz.n07.dungeon.LampBlock.POWER) ? st.getValue(fr.heralisz.n07.dungeon.LampBlock.POWER) : 2;
            if (be.clientPower >= 0 && pw != be.clientPower) {
               if (pw < be.clientPower) {
                  for (int i = 0; i < 2 + r.nextInt(4); i++) {
                     l.addParticle(ParticleTypes.ELECTRIC_SPARK, c.x + (r.nextDouble() - 0.5) * 0.5, by - 0.1, c.z + (r.nextDouble() - 0.5) * 0.5,
                        (r.nextDouble() - 0.5) * 0.2, -0.05 - r.nextDouble() * 0.1, (r.nextDouble() - 0.5) * 0.2);
                  }
                  if (r.nextInt(3) == 0 && d2 < 24 * 24) l.playLocalSound(c.x, by, c.z, BossSounds.DG_SPARK.get(), SoundSource.BLOCKS, 0.35F,
                     0.9F + r.nextFloat() * 0.3F, false);
               }
               if (r.nextInt(4) == 0 && d2 < 28 * 28) l.playLocalSound(c.x, by, c.z, BossSounds.DG_LAMP_BUZZ.get(), SoundSource.BLOCKS, 0.45F,
                  0.9F + r.nextFloat() * 0.2F, false);
            }
            be.clientPower = pw;
            if (pw == 2 && r.nextInt(900) == 0) l.addParticle(ParticleTypes.ELECTRIC_SPARK, c.x, by - 0.2, c.z, 0, -0.05, 0);
         }
         case CHANDELIER -> {
            if (r.nextInt(5) == 0) {
               double a = Math.toRadians(r.nextInt(8) * 45 + 22.5);
               l.addParticle(ParticleTypes.SMOKE, c.x + Math.cos(a) * 1.25, p.getY() + 1.05, c.z + Math.sin(a) * 1.25, 0, 0.02, 0);
            }
            if (r.nextInt(40) == 0) {
               double a = Math.toRadians(r.nextInt(8) * 45 + 22.5);
               l.addParticle(ParticleTypes.FALLING_DRIPSTONE_WATER, c.x + Math.cos(a) * 1.3, p.getY() + 0.3, c.z + Math.sin(a) * 1.3, 0, 0, 0);
            }
         }
         case SPORE_LANTERN -> {
            if (r.nextInt(6) == 0) l.addParticle(ParticleTypes.GLOW, c.x + (r.nextDouble() - 0.5) * 1.2, by + (r.nextDouble() - 0.5) * 0.8,
               c.z + (r.nextDouble() - 0.5) * 1.2, (r.nextDouble() - 0.5) * 0.01, -0.01, (r.nextDouble() - 0.5) * 0.01);
         }
         case BROOD_LAMP -> {
            if (r.nextInt(50) == 0) l.addParticle(ParticleTypes.FALLING_HONEY, c.x + (r.nextDouble() - 0.5) * 0.3, p.getY() - 0.2,
               c.z + (r.nextDouble() - 0.5) * 0.3, 0, 0, 0);
         }
         default -> {
         }
      }
   }

   private static void loop(PropBlockEntity be, SoundEvent s, float vol, Vec3 at) {
      Loop lp = LOOPS.get(be.getBlockPos());
      if (lp != null && !lp.isStopped()) return;
      lp = new Loop(be, s, vol, at);
      LOOPS.put(be.getBlockPos(), lp);
      Minecraft.getInstance().getSoundManager().play(lp);
   }

   @SubscribeEvent
   public static void clientTick(TickEvent.ClientTickEvent e) {
      if (e.phase != TickEvent.Phase.END) return;
      if (Minecraft.getInstance().level == null && !LOOPS.isEmpty()) LOOPS.clear();
      LOOPS.values().removeIf(AbstractTickableSoundInstance::isStopped);
   }

   /** Son en boucle attaché à un décor : s'arrête s'il disparaît ou si l'on s'éloigne. */
   private static final class Loop extends AbstractTickableSoundInstance {
      private final PropBlockEntity be;
      private final float target;
      private float fade;

      Loop(PropBlockEntity be, SoundEvent s, float vol, Vec3 at) {
         super(s, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
         this.be = be;
         this.looping = true;
         this.delay = 0;
         this.volume = 0.01F;
         this.x = at.x;
         this.y = at.y;
         this.z = at.z;
         this.attenuation = SoundInstance.Attenuation.LINEAR;
         this.fade = 0;
         this.pitch = 0.95F + be.phase * 0.1F;
         this.target = vol;
      }

      @Override
      public void tick() {
         Minecraft mc = Minecraft.getInstance();
         boolean gone = be.isRemoved() || mc.player == null || mc.player.distanceToSqr(this.x, this.y, this.z) > 40 * 40 || be.kind.pipe() && !be.flowing();
         fade = gone ? fade - 0.05F : Math.min(1F, fade + 0.05F);
         this.volume = Math.max(0.001F, target * fade);
         if (gone && fade <= 0) this.stop();
      }
   }
}
