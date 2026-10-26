package fr.heraliz.reanimation.client;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class ScriptFx {
   private static final DustParticleOptions MIST = new DustParticleOptions(new Vector3f(0.5F, 0.02F, 0.02F), 1.25F);
   private static final DustParticleOptions DARK_MIST = new DustParticleOptions(new Vector3f(0.3F, 0.0F, 0.0F), 1.6F);
   private static final Map<ExecutionScenes.Scene, SoundInstance> SPIN_SOUNDS = new IdentityHashMap<>();
   private static BlockParticleOption chunk;
   private static Method taczPlay;
   private static boolean taczTried;
   private static final int[] HEART_BEATS = new int[]{51, 57, 62, 67, 71, 75, 78, 81, 84, 87, 90, 93};

   private ScriptFx() {
   }

   private static void run(ExecutionScenes.Scene s, int before, int now, ScriptFx.Beats beats) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == s.level && s.victim != null && s.choreo() instanceof Script script) {
         Script.Frame var8 = (Script.Frame)s.state(0.0F);

         for (int t = Math.max(before + 1, now - 2); t <= now; t++) {
            beats.beat(s, var8, script, t);
         }

         drip(s, var8, now);
      }
   }

   static void karambit(ExecutionScenes.Scene s, int before, int now) {
      KarambitDance k = KarambitDance.INSTANCE;
      run(
         s,
         before,
         now,
         (sc, f, script, t) -> {
            Vec3 up = sc.world(at(0.0F, 0.0F, 1.0F)).subtract(sc.world(at(0.0F, 0.0F, 0.0F)));
            switch (t) {
               case 2:
                  tacz(sc, "arkaniaz:knife_deploy", 0.9F, 1.0F);
                  break;
               case 3:
               case 4:
               case 5:
               case 7:
               case 9:
               case 10:
               case 12:
               case 13:
               case 15:
               case 16:
               case 18:
               case 19:
               case 21:
               case 22:
               case 24:
               case 27:
               case 28:
               case 30:
               case 31:
               case 33:
               case 34:
               case 35:
               case 37:
               case 38:
               case 39:
               case 40:
               case 41:
               case 42:
               case 43:
               case 44:
               case 45:
               case 46:
               case 48:
               case 50:
               case 51:
               case 53:
               case 54:
               case 55:
               case 56:
               case 57:
               case 59:
               case 60:
               case 61:
               case 62:
               case 63:
               case 65:
               case 66:
               case 67:
               case 68:
               case 69:
               case 70:
               case 71:
               case 73:
               case 74:
               case 75:
               case 76:
               case 80:
               case 82:
               case 83:
               case 85:
               case 87:
               case 90:
               case 91:
               case 93:
               case 95:
               case 97:
               case 98:
               case 99:
               case 101:
               case 102:
               case 103:
               case 105:
               case 106:
               case 107:
               case 108:
               case 110:
               case 112:
               case 114:
               case 116:
               default:
                  if (t > 49.0F && t < 72.0F) {
                     Vec3 atx = sc.world(k.tearTip(f, t));
                     if (t % 2 == 0) {
                        sound(sc, atx, SoundEvents.HONEY_BLOCK_SLIDE, 0.7F, 0.55F + 0.01F * (t - 49));
                        blood(sc.level, atx, up, 3, 0.12F);
                     }

                     mist(sc.level, atx, 2, 0.04F);
                  }

                  if (t > 96 && t < 112 && t % 2 == 1) {
                     Vec3 atx = sc.world(k.temple(f));
                     float fade = 1.0F - (t - 96) / 16.0F;
                     blood(sc.level, atx, up, 2 + Math.round(5.0F * fade), 0.18F * fade + 0.05F);
                     if (t % 4 == 1) {
                        sound(sc, atx, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, 0.8F, 0.6F);
                     }
                  }
                  break;
               case 6:
                  spinSound(sc, tacz(sc, "arkaniaz:karambit/karambit_inspect", 1.0F, 1.0F));
                  break;
               case 8:
               case 11:
               case 17:
               case 20:
               case 29:
               case 32:
               case 113:
               case 115:
                  whoosh(sc, tip(sc, f, script), 0.25F);
                  break;
               case 14:
               case 23:
               case 84:
               case 117:
                  shine(sc, tip(sc, f, script));
                  break;
               case 25:
                  stopSpinSound(sc);
                  break;
               case 26:
                  sound(sc, tip(sc, f, script), SoundEvents.TRIDENT_THROW, 0.8F, 1.5F);
                  whoosh(sc, tip(sc, f, script), 0.4F);
                  break;
               case 36:
               case 118:
                  catchSound(sc, f, script);
                  break;
               case 47:
                  whoosh(sc, tip(sc, f, script), 0.6F);
                  break;
               case 49:
                  Vector3f in = k.tearTip(f, 49.0F);
                  stab(sc, f, in, "arkaniaz:knife_hit", 0.85F, 0.35F);
                  break;
               case 52:
               case 58:
               case 64: {
                  Vector3f at = k.tearTip(f, t + 1.0F);
                  slash(
                     sc,
                     sc.world(at),
                     up.add(sc.world(f.actor.scene(0.0F, 0.0F, 0.0F)).subtract(sc.world(at)).normalize().scale(0.6)),
                     0.75F + 0.1F * (t - 52) / 6.0F
                  );
                  hurt(sc, 0.8F + 0.12F * (t - 52) / 6.0F);
                  ExecutionFx.kick(0.25F, 0.12F);
                  break;
               }
               case 72: {
                  Vec3 at = sc.world(k.tearTip(f, 72.0F));
                  slash(sc, at, up, 0.8F);
                  gore(sc, at, up.add(sc.world(f.actor.scene(0.0F, 0.0F, 0.0F)).subtract(at).normalize().scale(0.5)), 24, 0.32F);
                  break;
               }
               case 77:
               case 79:
               case 81:
                  whoosh(sc, tip(sc, f, script), 0.3F);
                  blood(sc.level, tip(sc, f, script), new Vec3(sc.level.getRandom().nextDouble() - 0.5, 0.3, sc.level.getRandom().nextDouble() - 0.5), 6, 0.25F);
                  break;
               case 78:
                  sound(sc, sc.world(script.victimPoint(f, Knife.Part.HEAD, KarambitDance.HAIR_AT)), SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 0.7F);
                  hurt(sc, 1.35F);
                  break;
               case 86:
                  slowIn(sc, tip(sc, f, script));
                  heartbeat(sc, 0.7F);
                  break;
               case 88:
                  sound(sc, sc.world(script.victimPoint(f, Knife.Part.HEAD, KarambitDance.HAIR_AT)), SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 0.6F);
                  hurt(sc, 1.1F);
                  break;
               case 89:
               case 92:
                  heartbeat(sc, 0.8F);
                  break;
               case 94:
                  whoosh(sc, tip(sc, f, script), 1.0F);
                  tacz(sc, "arkaniaz:knife_slash", 1.0F, 0.8F);
                  break;
               case 96: {
                  Vector3f temple = k.temple(f);
                  Vec3 at = sc.world(temple);
                  stab(sc, f, temple, "arkaniaz:knife_stab_01", 0.6F, 0.9F);
                  skull(sc, at, 1.0F);
                  boom(sc, at);
                  gore(sc, at, sc.world(f.actor.scene(0.0F, 0.0F, 0.0F)).subtract(at).normalize().add(up.scale(0.8)), 40, 0.42F);
                  heartbeat(sc, 1.0F);
                  break;
               }
               case 100: {
                  Vec3 at = sc.world(f.victim.scene(0.0F, -4.0F, -4.0F));
                  BlockState ground = ground(sc.level, at);
                  sound(sc, at, SoundEvents.PLAYER_BIG_FALL, 1.0F, 0.7F);
                  sound(sc, at, ground.getSoundType().getBreakSound(), 1.0F, 0.6F);
                  skull(sc, at, 0.6F);
                  dust(sc.level, at, ground, 26, 0.45F);
                  gore(sc, at, up, 16, 0.25F);
                  ExecutionFx.kick(0.7F, 0.3F);
                  break;
               }
               case 104: {
                  Vec3 at = sc.world(k.temple(f));
                  slash(sc, at, up, 0.9F);
                  gore(sc, at, up.add(sc.world(f.actor.scene(0.0F, 0.0F, 0.0F)).subtract(at).normalize()), 26, 0.35F);
                  break;
               }
               case 109:
                  whoosh(sc, tip(sc, f, script), 0.7F);
                  break;
               case 111:
                  flick(sc, f, script);
            }
         }
      );
   }

   static void seaGleam(ExecutionScenes.Scene s, int before, int now) {
      run(s, before, now, (sc, f, script, t) -> {
         switch (t) {
            case 2:
            case 114:
               sound(sc, hand(sc, f), SoundEvents.ARMOR_EQUIP_IRON, 1.0F, 0.8F);
               break;
            case 9:
            case 13:
               sound(sc, tip(sc, f, script), SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 0.75F);
               break;
            case 26:
               sound(sc, sc.world(SeaGleamExecution.INSTANCE.collar(f)), SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 0.8F);
               hurt(sc, 1.0F);
               break;
            case 36:
               hurt(sc, 1.3F);
               break;
            case 50:
            case 58:
            case 66:
            case 76:
            case 84:
               sound(sc, sc.world(SeaGleamExecution.INSTANCE.collar(f)), SoundEvents.ARMOR_EQUIP_LEATHER, 0.6F, 1.1F);
               break;
            case 70:
            case 86:
               hurt(sc, 1.45F);
               break;
            case 92:
               sound(sc, tip(sc, f, script), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.6F);
               break;
            case 96:
               Vec3 neck = sc.world(f.victim.scene(SeaGleamExecution.NECK));
               sound(sc, neck, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.6F);
               sound(sc, neck, SoundEvents.HONEY_BLOCK_BREAK, 1.0F, 0.7F);
               sound(sc, neck, SoundEvents.SLIME_SQUISH, 1.0F, 0.5F);
               blood(sc.level, neck, new Vec3(0.0, 1.0, 0.0).add(sc.world(at(0.0F, 1.0F, 0.0F)).subtract(sc.world(at(0.0F, 0.0F, 0.0F)))), 26, 0.35F);
               mist(sc.level, neck, 18, 0.12F);
               ExecutionFx.kick(0.65F, 0.45F);
               break;
            case 104:
               sound(sc, sc.world(SeaGleamExecution.INSTANCE.collar(f)), SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 0.7F);
               break;
            case 112:
               Vec3 at = sc.world(SeaGleamExecution.INSTANCE.corpse(1));
               BlockState ground = ground(sc.level, at);
               sound(sc, at, SoundEvents.PLAYER_BIG_FALL, 1.0F, 0.85F);
               sound(sc, at, ground.getSoundType().getFallSound(), 1.0F, 0.8F);
               dust(sc.level, at, ground, 20, 0.7F);
               ExecutionFx.kick(0.25F, 0.0F);
               break;
            default:
               if (t > 96 && t <= 110 && t % 2 == 0) {
                  float k = 1.0F - (t - 96) / 16.0F;
                  Vec3 neckx = sc.world(f.victim.scene(0.0F, -0.5F, 0.0F));
                  Vec3 up = sc.world(f.victim.scene(0.0F, -6.0F, 0.0F)).subtract(neckx).normalize();
                  blood(sc.level, neckx, up, Math.round(8.0F * k) + 1, 0.22F * k + 0.05F);
                  mist(sc.level, neckx, 3, 0.04F);
               }

               if (t > 96 && t < 118) {
                  Vector3f head = f.head.transformPosition(new Vector3f(Script.HEAD_CENTRE).div(16.0F));
                  if (head.y < 0.27F && t % 4 == 1) {
                     sound(sc, sc.world(head), SoundEvents.PLAYER_SMALL_FALL, 0.7F, 1.2F);
                     mist(sc.level, sc.world(head), 4, 0.1F);
                  }
               }
         }
      });
   }

   static void butterfly(ExecutionScenes.Scene s, int before, int now) {
      ButterflyExecution b = ButterflyExecution.INSTANCE;
      run(s, before, now, (sc, f, script, t) -> {
         Vec3 up = sc.world(at(0.0F, 0.0F, 1.0F)).subtract(sc.world(at(0.0F, 0.0F, 0.0F)));
         switch (t) {
            case 2:
               tacz(sc, "arkaniaz:knife_deploy", 0.9F, 1.1F);
               break;
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 11:
            case 12:
            case 15:
            case 16:
            case 17:
            case 19:
            case 22:
            case 23:
            case 24:
            case 26:
            case 28:
            case 30:
            case 32:
            case 36:
            case 38:
            case 39:
            case 42:
            case 43:
            case 45:
            case 46:
            case 48:
            case 49:
            case 51:
            case 53:
            case 55:
            case 56:
            case 57:
            case 58:
            case 59:
            case 61:
            case 62:
            case 63:
            case 64:
            case 65:
            case 66:
            case 67:
            case 68:
            case 69:
            case 70:
            case 71:
            case 72:
            case 73:
            case 74:
            case 76:
            case 77:
            case 78:
            case 79:
            case 81:
            case 84:
            case 87:
            case 90:
            case 92:
            case 93:
            case 95:
            case 97:
            case 98:
            case 99:
            case 100:
            case 101:
            case 102:
            case 103:
            case 105:
            case 106:
            case 107:
            case 109:
            case 111:
            case 113:
            case 115:
            case 117:
            default:
               if (t > 96 && t < 112 && t % 2 == 0) {
                  Vec3 atx = sc.world(b.nape(f));
                  float fade = 1.0F - (t - 96) / 16.0F;
                  blood(sc.level, atx, up, 2 + Math.round(5.0F * fade), 0.16F * fade + 0.05F);
                  if (t % 4 == 0) {
                     sound(sc, atx, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, 0.8F, 0.6F);
                  }
               }
               break;
            case 10:
            case 18:
            case 20:
            case 27:
            case 29:
            case 31:
            case 33:
            case 35:
            case 37:
            case 52:
            case 108:
               whoosh(sc, tip(sc, f, script), 0.3F);
               break;
            case 13:
            case 21:
            case 25:
            case 40:
            case 47:
            case 54:
            case 110:
            case 112:
            case 114:
            case 116:
               click(sc, tip(sc, f, script));
               break;
            case 14:
            case 34:
            case 118:
               shine(sc, tip(sc, f, script));
               break;
            case 41:
            case 80:
               sound(sc, tip(sc, f, script), SoundEvents.TRIDENT_THROW, 0.7F, 1.6F);
               whoosh(sc, tip(sc, f, script), 0.45F);
               break;
            case 44:
            case 83:
            case 86:
               whoosh(sc, tip(sc, f, script), 0.25F);
               break;
            case 50:
               catchSound(sc, f, script);
               break;
            case 60:
               sound(sc, sc.world(script.victimPoint(f, Knife.Part.HEAD, ButterflyExecution.HAIR_AT)), SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 0.75F);
               hurt(sc, 1.25F);
               break;
            case 75:
               click(sc, tip(sc, f, script));
               shine(sc, tip(sc, f, script));
               hurt(sc, 1.5F);
               break;
            case 82: {
               Vec3 at = sc.world(f.victim.scene(0.0F, -4.0F, -4.0F));
               BlockState ground = ground(sc.level, at);
               sound(sc, at, SoundEvents.PLAYER_BIG_FALL, 1.0F, 0.75F);
               sound(sc, at, ground.getSoundType().getBreakSound(), 1.0F, 0.6F);
               skull(sc, at, 0.4F);
               dust(sc.level, at, ground, 22, 0.4F);
               hurt(sc, 0.7F);
               ExecutionFx.kick(0.45F, 0.2F);
               break;
            }
            case 85:
               slowIn(sc, tip(sc, f, script));
               heartbeat(sc, 0.7F);
               break;
            case 88:
               heartbeat(sc, 0.8F);
               break;
            case 89:
               click(sc, tip(sc, f, script));
               break;
            case 91:
               sound(sc, tip(sc, f, script), SoundEvents.ARMOR_EQUIP_CHAIN, 1.0F, 0.9F);
               sound(sc, tip(sc, f, script), SoundEvents.CHAIN_HIT, 0.6F, 0.8F);
               glint(sc.level, tip(sc, f, script));
               heartbeat(sc, 0.9F);
               break;
            case 94:
               whoosh(sc, tip(sc, f, script), 1.0F);
               tacz(sc, "arkaniaz:knife_slash", 1.0F, 0.9F);
               break;
            case 96: {
               Vector3f nape = b.nape(f);
               Vec3 at = sc.world(nape);
               stab(sc, f, nape, "arkaniaz:knife_stab_01", 0.55F, 0.9F);
               skull(sc, at, 1.0F);
               boom(sc, at);
               gore(sc, at, up.add(sc.world(f.actor.scene(0.0F, 0.0F, 0.0F)).subtract(at).normalize().scale(0.4)), 36, 0.4F);
               heartbeat(sc, 1.0F);
               break;
            }
            case 104: {
               Vec3 at = sc.world(b.nape(f));
               slash(sc, at, up, 0.85F);
               gore(sc, at, up, 22, 0.3F);
            }
         }
      });
   }

   static void m9(ExecutionScenes.Scene s, int before, int now) {
      M9Execution m = M9Execution.INSTANCE;
      run(s, before, now, (sc, f, script, t) -> {
         Vec3 up = sc.world(at(0.0F, 0.0F, 1.0F)).subtract(sc.world(at(0.0F, 0.0F, 0.0F)));

         for (int i = 0; i < HEART_BEATS.length; i++) {
            if (t == HEART_BEATS[i]) {
               heartbeat(sc, 0.45F + 0.5F * i / (HEART_BEATS.length - 1));
            }
         }

         switch (t) {
            case 2:
               tacz(sc, "arkaniaz:knife_deploy", 1.0F, 0.9F);
               break;
            case 9:
            case 13:
               shine(sc, tip(sc, f, script));
               break;
            case 14:
               whoosh(sc, tip(sc, f, script), 0.35F);
               break;
            case 17:
               whoosh(sc, tip(sc, f, script), 0.25F);
               break;
            case 20:
               catchSound(sc, f, script);
               break;
            case 23:
               whoosh(sc, tip(sc, f, script), 0.3F);
               break;
            case 26:
               sound(sc, tip(sc, f, script), SoundEvents.ARMOR_EQUIP_IRON, 1.0F, 1.3F);
               shine(sc, tip(sc, f, script));
               break;
            case 32: {
               Vec3 at = sc.world(f.victim.scene(-3.0F, 6.0F, 0.0F));
               sound(sc, at, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.7F);
               sound(sc, at, SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.8F);
               hurt(sc, 0.9F);
               ExecutionFx.kick(0.35F, 0.15F);
               break;
            }
            case 38: {
               Vec3 at = sc.world(f.victim.scene(0.0F, 6.0F, 0.0F));
               BlockState ground = ground(sc.level, at);
               sound(sc, at, SoundEvents.PLAYER_BIG_FALL, 1.0F, 0.8F);
               sound(sc, at, ground.getSoundType().getFallSound(), 1.0F, 0.8F);
               dust(sc.level, at, ground, 20, 0.5F);
               break;
            }
            case 51:
            case 55: {
               Vec3 at = sc.world(f.victim.scene(M9Execution.CHEST));
               sound(sc, at, SoundEvents.ARMOR_EQUIP_CHAIN, 0.8F, 1.6F);
               sound(sc, at, SoundEvents.CHAIN_HIT, 0.4F, 1.9F);
               break;
            }
            case 60:
               shine(sc, tip(sc, f, script));
               break;
            case 66:
               whoosh(sc, tip(sc, f, script), 0.8F);
               break;
            case 68:
               sound(sc, sc.world(f.victim.scene(M9Execution.CHEST)).add(up.scale(0.5)), SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 0.6F);
               hurt(sc, 1.3F);
               ExecutionFx.kick(0.2F, 0.1F);
               break;
            case 74:
            case 80:
               hurt(sc, 1.45F);
               break;
            case 86:
               slowIn(sc, tip(sc, f, script));
               break;
            case 94:
               whoosh(sc, tip(sc, f, script), 1.0F);
               tacz(sc, "arkaniaz:knife_slash", 1.0F, 0.75F);
               break;
            case 96: {
               Vector3f heart = m.heart(f);
               Vec3 at = sc.world(heart);
               stab(sc, f, heart, "arkaniaz:knife_stab_01", 0.5F, 1.0F);
               boom(sc, at);
               sound(sc, at, SoundEvents.BONE_BLOCK_BREAK, 1.0F, 0.6F);
               gore(sc, at, up, 40, 0.42F);
               heartbeat(sc, 1.0F);
               break;
            }
            case 100: {
               Vec3 at = sc.world(m.heart(f));
               sound(sc, at, SoundEvents.HONEY_BLOCK_BREAK, 1.0F, 0.5F);
               sound(sc, at, SoundEvents.SLIME_SQUISH, 1.0F, 0.45F);
               blood(sc.level, at, up, 10, 0.2F);
               break;
            }
            case 106: {
               Vec3 at = sc.world(m.heart(f));
               slash(sc, at, up, 0.8F);
               gore(sc, at, up, 30, 0.45F);
               break;
            }
            case 113:
            case 115:
               whoosh(sc, tip(sc, f, script), 0.3F);
               break;
            case 117:
               shine(sc, tip(sc, f, script));
               break;
            default:
               if (t > 96 && t < 116 && t % 2 == 0) {
                  Vec3 atx = sc.world(m.heart(f));
                  float fade = 1.0F - (t - 96) / 20.0F;
                  blood(sc.level, atx, up, 1 + Math.round(4.0F * fade), 0.12F * fade + 0.04F);
                  if (t % 4 == 2) {
                     sound(sc, atx, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, 0.7F, 0.55F);
                  }
               }
         }
      });
   }

   static void homeRun(ExecutionScenes.Scene s, int before, int now) {
      run(s, before, now, (sc, f, script, t) -> {
         switch (t) {
            case 11:
               woodHit(sc, at(-0.02F, -0.62F, 0.05F));
               break;
            case 16:
               woodHit(sc, at(-0.02F, 0.62F, 0.05F));
               break;
            case 23: {
               Vec3 at = sc.world(at(-0.02F, 0.0F, 0.9F));
               sound(sc, at, SoundEvents.WOOD_FALL, 1.0F, 0.8F);
               sound(sc, at, SoundEvents.WOOD_PLACE, 1.0F, 0.9F);
               break;
            }
            case 26:
               sound(sc, sc.world(f.victim.scene(0.0F, 1.0F, 0.0F)), SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 0.8F);
               break;
            case 32:
               hurt(sc, 1.2F);
               break;
            case 40: {
               Vec3 at = sc.world(at(-0.02F, 0.0F, 1.1F));
               sound(sc, at, SoundEvents.WOODEN_DOOR_CLOSE, 1.0F, 0.7F);
               sound(sc, at, SoundEvents.WOOD_PLACE, 1.0F, 0.6F);
               hurt(sc, 0.9F);
               ExecutionFx.kick(0.2F, 0.0F);
               break;
            }
            case 61:
            case 66:
               sound(sc, sc.world(f.victim.scene(0.0F, -8.0F, 0.0F)), SoundEvents.WOOD_HIT, 1.0F, 1.6F);
               hurt(sc, 1.5F);
               break;
            case 93:
               sound(sc, tip(sc, f, script), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.55F);
               break;
            case 96:
               Vec3 head = sc.world(f.head.transformPosition(new Vector3f(Script.HEAD_CENTRE).div(16.0F)));
               sound(sc, head, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.6F);
               sound(sc, head, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.8F);
               sound(sc, head, SoundEvents.WOOD_BREAK, 1.0F, 1.4F);
               sound(sc, head, SoundEvents.HONEY_BLOCK_BREAK, 1.0F, 0.8F);
               Vec3 neck = sc.world(f.victim.scene(0.0F, -0.5F, 0.0F));
               blood(sc.level, neck, new Vec3(0.0, 1.0, 0.0), 20, 0.3F);
               mist(sc.level, neck, 16, 0.12F);
               ExecutionFx.kick(0.8F, 0.6F);
               break;
            case 99:
               sound(sc, hand(sc, f), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
               break;
            case 100:
               sound(sc, hand(sc, f), SoundEvents.FIREWORK_ROCKET_BLAST, 0.6F, 1.0F);
               break;
            case 111:
            case 114:
               sound(sc, tip(sc, f, script), SoundEvents.PLAYER_ATTACK_SWEEP, 0.4F, 1.3F);
               break;
            case 117:
               sound(sc, hand(sc, f), SoundEvents.WOOD_HIT, 0.8F, 1.2F);
               break;
            default:
               if (t > 96 && t <= 112 && f.headOff) {
                  Vec3 headx = sc.world(f.head.transformPosition(new Vector3f(Script.HEAD_CENTRE).div(16.0F)));
                  mist(sc.level, headx, 3, 0.08F);
                  sc.level.addParticle(chunk(), headx.x, headx.y, headx.z, 0.0, -0.05, 0.0);
               }

               if (t > 96 && t <= 106 && t % 2 == 0) {
                  Vec3 neckx = sc.world(f.victim.scene(0.0F, -0.5F, 0.0F));
                  blood(sc.level, neckx, new Vec3(0.0, 1.0, 0.0), 5, 0.15F);
               }
         }
      });
   }

   private static void woodHit(ExecutionScenes.Scene s, Vector3f base) {
      Vec3 at = s.world(base);
      sound(s, at, SoundEvents.WOOD_PLACE, 1.0F, 0.8F);
      sound(s, at, SoundEvents.WOOD_HIT, 1.0F, 0.6F);
      BlockState ground = ground(s.level, at);
      dust(s.level, at, ground, 14, 0.25F);
      s.level.addParticle(ParticleTypes.POOF, at.x, at.y + 0.1, at.z, 0.0, 0.05, 0.0);
      ExecutionFx.kick(0.15F, 0.0F);
   }

   private static void stab(ExecutionScenes.Scene s, Script.Frame f, Vector3f wound, String taczSound, float hurtPitch, float kick) {
      Vec3 at = s.world(wound);
      tacz(s, taczSound, 1.0F, 1.0F);
      sound(s, at, SoundEvents.TRIDENT_HIT, 0.9F, 0.95F);
      sound(s, at, SoundEvents.HONEY_BLOCK_BREAK, 1.0F, 0.65F);
      sound(s, at, SoundEvents.MUD_BREAK, 1.0F, 0.75F);
      sound(s, at, SoundEvents.SLIME_BLOCK_BREAK, 0.8F, 0.6F);
      hurt(s, hurtPitch);
      Vec3 out = s.world(f.actor.scene(0.0F, 4.0F, 0.0F)).subtract(at).normalize();
      blood(s.level, at, out.add(0.0, 0.6, 0.0), 18, 0.24F);
      mist(s.level, at, 18, 0.12F);
      ExecutionFx.kick(kick, kick * 0.5F);
   }

   private static void slash(ExecutionScenes.Scene s, Vec3 at, Vec3 dir, float power) {
      sound(s, at, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F + 0.3F * power, 0.85F);
      sound(s, at, SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 1.0F, 0.6F);
      sound(s, at, SoundEvents.HONEY_BLOCK_BREAK, 1.0F, 0.9F);
      sound(s, at, SoundEvents.SLIME_SQUISH, 0.8F * power, 0.55F);
      tacz(s, "arkaniaz:knife_slash", 0.8F, 1.1F);
      blood(s.level, at, dir, Math.round(12.0F + 14.0F * power), 0.2F + 0.15F * power);
      mist(s.level, at, Math.round(8.0F + 8.0F * power), 0.1F);
   }

   private static void skull(ExecutionScenes.Scene s, Vec3 at, float power) {
      sound(s, at, SoundEvents.TURTLE_EGG_CRACK, 1.0F, 0.5F);
      sound(s, at, SoundEvents.BONE_BLOCK_BREAK, 1.0F, 0.55F);
      sound(s, at, SoundEvents.SKELETON_HURT, 0.6F * power, 0.5F);
      sound(s, at, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 0.45F * power, 0.8F);
   }

   private static void gore(ExecutionScenes.Scene s, Vec3 at, Vec3 dir, int count, float speed) {
      sound(s, at, SoundEvents.GENERIC_SPLASH, 0.6F, 0.6F);
      sound(s, at, SoundEvents.SLIME_SQUISH, 1.0F, 0.5F);
      blood(s.level, at, dir, count, speed);
      mist(s.level, at, count / 2 + 4, 0.16F);
   }

   private static void boom(ExecutionScenes.Scene s, Vec3 at) {
      sound(s, at, SoundEvents.WARDEN_ATTACK_IMPACT, 1.0F, 0.8F);
      sound(s, at, SoundEvents.GENERIC_EXPLODE, 0.35F, 0.55F);
      sound(s, at, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.7F);
      ExecutionFx.kick(1.0F, 0.75F);
   }

   private static void whoosh(ExecutionScenes.Scene s, Vec3 at, float power) {
      sound(s, at, SoundEvents.PLAYER_ATTACK_SWEEP, 0.2F + 0.6F * power, 1.8F - 0.9F * power);
   }

   private static void shine(ExecutionScenes.Scene s, Vec3 at) {
      sound(s, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.9F);
      sound(s, at, SoundEvents.AMETHYST_BLOCK_HIT, 0.4F, 2.0F);
      glint(s.level, at);
   }

   private static void slowIn(ExecutionScenes.Scene s, Vec3 at) {
      sound(s, at, SoundEvents.BEACON_DEACTIVATE, 1.0F, 0.6F);
      sound(s, at, SoundEvents.ELDER_GUARDIAN_CURSE, 0.15F, 1.6F);
   }

   private static void heartbeat(ExecutionScenes.Scene s, float strength) {
      if (s.victim != null) {
         sound(s, s.victim.position().add(0.0, 0.4, 0.0), SoundEvents.WARDEN_HEARTBEAT, 1.0F, 0.85F + 0.25F * strength);
      }

      if (ExecutionScenes.local() == s) {
         ExecutionFx.heartbeat(strength);
      }
   }

   private static void rip(ExecutionScenes.Scene s, Vector3f wound, Vector3f toward) {
      Vec3 at = s.world(wound);
      sound(s, at, SoundEvents.HONEY_BLOCK_BREAK, 1.0F, 1.3F);
      tacz(s, "arkaniaz:knife_slash", 0.5F, 1.3F);
      Vec3 dir = s.world(toward).subtract(at).normalize().add(0.0, 0.5, 0.0);
      blood(s.level, at, dir, 18, 0.3F);
      mist(s.level, at, 10, 0.1F);
   }

   private static void flick(ExecutionScenes.Scene s, Script.Frame f, Script script) {
      Vec3 tip = tip(s, f, script);
      sound(s, tip, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 1.4F);
      sound(s, tip, SoundEvents.HONEY_BLOCK_BREAK, 0.4F, 1.6F);
      Vec3 right = s.world(f.actor.scene(-12.0F, 12.0F, 0.0F)).subtract(s.world(f.actor.scene(0.0F, 12.0F, 0.0F))).normalize();
      blood(s.level, tip, right.add(0.0, -0.4, 0.0), 16, 0.35F);
   }

   private static void catchSound(ExecutionScenes.Scene s, Script.Frame f, Script script) {
      Vec3 tip = tip(s, f, script);
      sound(s, tip, SoundEvents.ARMOR_EQUIP_CHAIN, 0.9F, 1.8F);
      sound(s, tip, SoundEvents.CHAIN_HIT, 0.5F, 1.6F);
      glint(s.level, tip);
   }

   private static void click(ExecutionScenes.Scene s, Vec3 at) {
      sound(s, at, SoundEvents.LEVER_CLICK, 0.5F, 2.0F);
      sound(s, at, SoundEvents.ARMOR_EQUIP_CHAIN, 0.35F, 2.0F);
   }

   private static BlockParticleOption chunk() {
      if (chunk == null) {
         chunk = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
      }

      return chunk;
   }

   private static void blood(ClientLevel level, Vec3 at, Vec3 dir, int count, float speed) {
      RandomSource r = level.getRandom();
      Vec3 d = dir.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : dir.normalize();

      for (int i = 0; i < count; i++) {
         double k = speed * (0.5 + r.nextDouble());
         level.addParticle(
            chunk(),
            at.x,
            at.y,
            at.z,
            d.x * k + (r.nextDouble() - 0.5) * 0.12,
            d.y * k + (r.nextDouble() - 0.5) * 0.12,
            d.z * k + (r.nextDouble() - 0.5) * 0.12
         );
      }
   }

   private static void mist(ClientLevel level, Vec3 at, int count, float spread) {
      RandomSource r = level.getRandom();

      for (int i = 0; i < count; i++) {
         level.addParticle(
            i % 4 == 0 ? DARK_MIST : MIST,
            at.x + (r.nextDouble() - 0.5) * 2.0 * spread,
            at.y + (r.nextDouble() - 0.5) * 2.0 * spread,
            at.z + (r.nextDouble() - 0.5) * 2.0 * spread,
            0.0,
            0.0,
            0.0
         );
      }
   }

   private static void dust(ClientLevel level, Vec3 at, BlockState ground, int count, float spread) {
      RandomSource r = level.getRandom();
      ParticleOptions p = new BlockParticleOption(ParticleTypes.BLOCK, ground);

      for (int i = 0; i < count; i++) {
         double a = r.nextDouble() * Math.PI * 2.0;
         level.addParticle(
            p,
            at.x + Math.cos(a) * spread * r.nextDouble(),
            at.y + 0.05,
            at.z + Math.sin(a) * spread * r.nextDouble(),
            Math.cos(a) * 0.1,
            0.12 + r.nextDouble() * 0.1,
            Math.sin(a) * 0.1
         );
      }
   }

   private static void glint(ClientLevel level, Vec3 at) {
      RandomSource r = level.getRandom();

      for (int i = 0; i < 7; i++) {
         level.addParticle(
            ParticleTypes.ELECTRIC_SPARK,
            at.x,
            at.y,
            at.z,
            (r.nextDouble() - 0.5) * 0.35,
            (r.nextDouble() - 0.2) * 0.3,
            (r.nextDouble() - 0.5) * 0.35
         );
      }

      level.addParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, 0.0, 0.02, 0.0);
   }

   private static void drip(ExecutionScenes.Scene s, Script.Frame f, int t) {
      if (t % 3 == 0 && !(f.knife.blood < 0.3F) && f.knife.holder != Knife.Holder.HIDDEN) {
         Vec3 tip = KnifeTrail.lastTip(s);
         if (tip != null) {
            s.level.addParticle(chunk(), tip.x, tip.y - 0.03, tip.z, 0.0, -0.05, 0.0);
         }
      }
   }

   private static BlockState ground(ClientLevel level, Vec3 at) {
      BlockState state = level.getBlockState(BlockPos.containing(at.x, at.y - 0.3, at.z));
      return state.isAir() ? Blocks.DIRT.defaultBlockState() : state;
   }

   private static Vector3f at(float fwd, float side, float up) {
      return CoupDeGrace.at(fwd, side, up);
   }

   private static Vec3 tip(ExecutionScenes.Scene s, Script.Frame f, Script script) {
      Vec3 seen = KnifeTrail.lastTip(s);
      if (seen != null) {
         return seen;
      }

      Matrix4f knife = script.knifeInScene(f);
      return knife == null ? hand(s, f) : s.world(Knife.through(knife, f.knife.blade.tip));
   }

   private static Vec3 hand(ExecutionScenes.Scene s, Script.Frame f) {
      return s.world(f.actor.scene(-4.0F, 4.0F, -8.0F));
   }

   private static void sound(ExecutionScenes.Scene s, Vec3 at, SoundEvent sound, float volume, float pitch) {
      s.level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch, false);
   }

   private static void hurt(ExecutionScenes.Scene s, float pitch) {
      if (s.victim != null && s.victim.isAlive()) {
         sound(s, s.victim.position().add(0.0, 0.6, 0.0), SoundEvents.PLAYER_HURT, 1.0F, pitch);
      }
   }

   @Nullable
   private static SoundInstance tacz(ExecutionScenes.Scene s, String id, float volume, float pitch) {
      Entity at = s.actor != null ? s.actor : s.victim;
      if (at == null) {
         return null;
      }

      if (!taczTried) {
         taczTried = true;
         if (ModList.get().isLoaded("tacz")) {
            try {
               taczPlay = Class.forName("com.tacz.guns.client.sound.SoundPlayManager")
                  .getMethod("playClientSound", Entity.class, ResourceLocation.class, float.class, float.class, int.class);
            } catch (ReflectiveOperationException | LinkageError e) {
               taczPlay = null;
            }
         }
      }

      if (taczPlay == null) {
         return null;
      }

      try {
         return taczPlay.invoke(null, at, new ResourceLocation(id), volume, pitch, 24) instanceof SoundInstance instance ? instance : null;
      } catch (ReflectiveOperationException | RuntimeException e) {
         taczPlay = null;
         return null;
      }
   }

   private static void spinSound(ExecutionScenes.Scene s, @Nullable SoundInstance sound) {
      stopSpinSound(s);
      if (sound != null) {
         SPIN_SOUNDS.put(s, sound);
      }
   }

   private static void stopSpinSound(ExecutionScenes.Scene s) {
      SoundInstance old = SPIN_SOUNDS.remove(s);
      if (old != null) {
         Minecraft.getInstance().getSoundManager().stop(old);
      }
   }

   static void clear() {
      for (SoundInstance sound : SPIN_SOUNDS.values()) {
         Minecraft.getInstance().getSoundManager().stop(sound);
      }

      SPIN_SOUNDS.clear();
   }

   private interface Beats {
      void beat(ExecutionScenes.Scene var1, Script.Frame var2, Script var3, int var4);
   }
}
