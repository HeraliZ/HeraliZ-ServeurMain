package fr.heraliz.armory.client;

import fr.heraliz.armory.suit.Diving;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Nages animees de la tenue de plongee (0.9.0, reprises du DivingPlayerModel du mod heraliz_plongee du proprietaire) :
 * crawl pres de la surface (bras alternes, battements de palmes, tete tournee pour respirer), dauphin sous l'eau
 * (palmes jointes, bras le long du corps), surplace a l'arret (bras qui godillent, jambes qui pedalent).
 * Appele a la fin de HumanoidModel.setupAnim (mixin HumanoidModelMixin) au lieu de remplacer le modele du joueur :
 * les manches, jambes de pantalon et veste du PlayerModel sont recopiees juste apres par le jeu, puis les emotes et
 * les poses de Reanimation passent par-dessus.
 */
public final class DivingPoses {
    private DivingPoses() {}

    public static void apply(HumanoidModel<?> model, LivingEntity entity, float ageInTicks) {
        // bras a la premiere personne : le jeu appelle setupAnim avec tout a zero (pose de repos du bras), on n'y touche pas
        if (ageInTicks == 0.0F) return;
        if (!(entity instanceof Player p) || !p.isInWater() || !Diving.wears(p)) return;
        float swim = model.swimAmount;
        if (swim > 0.05F) {
            if (nearSurface(p)) crawl(model, ageInTicks, swim);
            else dolphin(model, ageInTicks, swim);
        } else {
            if (p.onGround()) return;
            tread(model, ageInTicks);
        }
        model.hat.copyFrom(model.head);
    }

    private static boolean nearSurface(Player p) {
        return !p.isEyeInFluid(FluidTags.WATER) || p.level().getFluidState(BlockPos.containing(p.getX(), p.getEyeY() + 0.6, p.getZ())).isEmpty();
    }

    private static void mix(ModelPart part, float x, float y, float z, float a) {
        part.xRot = Mth.lerp(a, part.xRot, x);
        part.yRot = Mth.lerp(a, part.yRot, y);
        part.zRot = Mth.lerp(a, part.zRot, z);
    }

    /** Sous l'eau : battements de palmes jambes jointes, bras le long du corps. */
    private static void dolphin(HumanoidModel<?> m, float t, float a) {
        float kick = Mth.sin(t * 0.32F);
        float legs = 0.1F + 0.55F * kick;
        mix(m.rightLeg, legs, 0.0F, 0.04F, a);
        mix(m.leftLeg, legs, 0.0F, -0.04F, a);
        float arms = 0.08F + 0.06F * Mth.sin(t * 0.32F + 1.2F);
        mix(m.rightArm, arms, 0.0F, 0.1F, a);
        mix(m.leftArm, arms, 0.0F, -0.1F, a);
    }

    /** Pres de la surface : crawl, battements de palmes alternes, tete tournee pour respirer. */
    private static void crawl(HumanoidModel<?> m, float t, float a) {
        float s = t * 0.2F % (float) (Math.PI * 2);
        float r = s - (float) Math.PI;
        mix(m.rightArm, r, 0.0F, 0.45F * Math.max(0.0F, Mth.sin(r)), a);
        mix(m.leftArm, s, 0.0F, -0.45F * Math.max(0.0F, Mth.sin(s)), a);
        float flutter = 0.32F * Mth.sin(t * 0.9F);
        mix(m.rightLeg, flutter, 0.0F, 0.02F, a);
        mix(m.leftLeg, -flutter, 0.0F, -0.02F, a);
        float breathe = Math.max(0.0F, Mth.sin(r));
        m.head.yRot += a * 0.7F * breathe * breathe * breathe;
    }

    /** A l'arret dans l'eau (pas au fond) : surplace, bras qui godillent, jambes qui pedalent. */
    private static void tread(HumanoidModel<?> m, float t) {
        float ph = t * 0.16F;
        mix(m.rightArm, -0.35F + 0.12F * Mth.sin(ph), 0.25F * Mth.sin(ph * 2.0F), 0.55F + 0.18F * Mth.sin(ph), 1.0F);
        mix(m.leftArm, -0.35F + 0.12F * Mth.sin(ph), -0.25F * Mth.sin(ph * 2.0F), -0.55F - 0.18F * Mth.sin(ph), 1.0F);
        float k = 0.28F * Mth.sin(ph * 1.6F);
        mix(m.rightLeg, k, 0.0F, 0.05F, 1.0F);
        mix(m.leftLeg, -k, 0.0F, -0.05F, 1.0F);
    }
}
