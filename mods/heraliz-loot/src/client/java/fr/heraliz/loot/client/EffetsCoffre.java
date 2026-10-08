package fr.heraliz.loot.client;

import fr.heraliz.loot.CoffreBlock;
import fr.heraliz.loot.CoffreBlockEntity;
import fr.heraliz.loot.Encombrement;
import fr.heraliz.loot.SonsLoot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * Client : sons et particules de l'animation des coffres T7 (apparition, ouverture, disparition). Corps d'origine de
 * CoffreBlockEntity.tickClient, tels quels ; branche sur CoffreBlockEntity.TICK_CLIENT par la partie client.
 */
public final class EffetsCoffre {
    private EffetsCoffre() {
    }

    private static final DustParticleOptions VAPEUR = new DustParticleOptions(new Vector3f(0.92f, 0.93f, 0.95f), 0.6f),
            GIVRE = new DustParticleOptions(new Vector3f(0.72f, 0.88f, 1f), 0.8f),
            POUSSIERE = new DustParticleOptions(new Vector3f(0.55f, 0.53f, 0.5f), 0.9f),
            ETINCELLE = new DustParticleOptions(new Vector3f(1f, 0.35f, 0.2f), 0.7f);

    public static void tick(Level l, BlockPos pos, BlockState st, CoffreBlockEntity be) {
        long now = l.getGameTime();
        float t = be.tempsAnimation(now, 0);
        float a = be.tPrecedent;
        be.tPrecedent = t;
        String n = be.type().nom;
        RandomSource r = l.random;
        // centre de l'objet au sol (les coffres sur plusieurs blocs ne sont pas centrés sur leur bloc principal)
        double[] bo = be.type().collision.get(0);
        net.minecraft.world.phys.Vec3 c = Encombrement.toWorld(pos, st.getValue(CoffreBlock.FACING), (float) (bo[0] + bo[3]) / 2, 0, (float) (bo[2] + bo[5]) / 2);
        double x = c.x, y = pos.getY(), z = c.z, haut = bo[4] / 16.0, large = Math.max(bo[3] - bo[0], bo[5] - bo[2]) / 16.0;
        boolean ouvre = be.etat() == CoffreBlockEntity.OUVERTURE;
        if (be.etat() == CoffreBlockEntity.APPARITION || be.etat() == CoffreBlockEntity.DISPARITION) {
            float f = be.avancement(now, 0);
            boolean largage = n.equals("t7_largage");
            if (be.etat() == CoffreBlockEntity.APPARITION && largage) {
                if (f >= 0.8f && a < 0.8f) {                    // atterrissage : poussière tout autour
                    son(be, SonsLoot.PANNEAUX.get(), 0.7f);
                    for (int k = 0; k < 28; k++) {
                        double a2 = r.nextFloat() * Math.PI * 2, d2 = large * (0.5 + r.nextFloat() * 0.5);
                        l.addParticle(POUSSIERE, x + Math.cos(a2) * d2, y + 0.1, z + Math.sin(a2) * d2, Math.cos(a2) * 0.04, 0.01, Math.sin(a2) * 0.04);
                    }
                }
            } else if (now % 2 == 0) {                                      // matérialisation : étincelles rouges
                double a2 = r.nextFloat() * Math.PI * 2, d2 = 0.3 + r.nextFloat() * 0.5;
                l.addParticle(ETINCELLE, x + Math.cos(a2) * d2, y + 0.05 + r.nextFloat() * 0.8, z + Math.sin(a2) * d2, 0, 0.02, 0);
            }
            if (be.etat() == CoffreBlockEntity.APPARITION && a <= 0.01f && f > 0) son(be, largage ? SonsLoot.HYDRAULIQUE.get() : SonsLoot.RUNES.get(), largage ? 0.5f : 1.4f);
            if (be.etat() == CoffreBlockEntity.DISPARITION && a <= 0.01f && f > 0) son(be, SonsLoot.RUNES.get(), 0.8f);
            be.tPrecedent = f;
            return;
        }
        switch (n) {
            case "t7_coffre_fort" -> {
                if (ouvre && passe(a, t, 4)) son(be, SonsLoot.VOLANT.get(), 1f);
                if (ouvre && passe(a, t, 14)) son(be, SonsLoot.VERROUS.get(), 1f);
                if (ouvre && passe(a, t, 20)) son(be, SonsLoot.PORTE_LOURDE.get(), 1f);
            }
            case "t7_coffre_armes" -> {
                if (ouvre && passe(a, t, 2.5f)) son(be, SonsLoot.LOQUETS.get(), 1f);
                if (ouvre && passe(a, t, 12)) son(be, SonsLoot.HYDRAULIQUE.get(), 1f);
                if (ouvre && t > 12 && t < 22) {                         // détente des vérins : jets de vapeur fins
                    for (int k = -1; k <= 1; k += 2) {
                        l.addParticle(VAPEUR, x + k * large * 0.46, y + 0.36, z - 0.2f + r.nextFloat() * 0.2f, k * 0.02f, 0.03f, 0);
                    }
                }
            }
            case "t7_largage" -> {
                if (be.etat() == CoffreBlockEntity.FERME && now % 3 == 0) {                 // fumigène rouge au-dessus de la balise
                    l.addParticle(new DustParticleOptions(new Vector3f(0.95f, 0.15f, 0.1f), 2.2f), x + (r.nextFloat() - 0.5) * 0.2,
                            y + haut + 0.1, z + (r.nextFloat() - 0.5) * 0.2, (r.nextFloat() - 0.5) * 0.02, 0.06, (r.nextFloat() - 0.5) * 0.02);
                    l.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + haut + 0.15, z, 0, 0.04, 0);
                }
                if (ouvre && passe(a, t, 1)) son(be, SonsLoot.LOQUETS.get(), 0.8f);
                if (ouvre && passe(a, t, 9)) son(be, SonsLoot.PANNEAUX.get(), 1f);
                for (int k = 0; k < 4; k++) {                             // poussière là où chaque panneau touche le sol
                    if (ouvre && passe(a, t, 9 + k * 2.5f)) {
                        double ang = Math.toRadians(-be.getBlockState().getValue(CoffreBlock.FACING).toYRot() + k * 90);
                        double dx = Math.sin(ang), dz = Math.cos(ang);
                        for (int q = 0; q < 10; q++) {
                            double s = large * (0.5 + r.nextFloat() * 0.6), lat = (r.nextFloat() - 0.5) * large * 0.8;
                            l.addParticle(POUSSIERE, x + dx * s + dz * lat, y + 0.1 + r.nextFloat() * 0.1, z + dz * s - dx * lat, dx * 0.015, 0.012, dz * 0.015);
                        }
                    }
                }
            }
            case "t7_mallette" -> {
                if (ouvre && passe(a, t, 1)) son(be, SonsLoot.SCAN.get(), 1f);
                if (ouvre && passe(a, t, 18)) son(be, SonsLoot.LOQUETS.get(), 1.15f);
                if (ouvre && passe(a, t, 24)) son(be, SonsLoot.HYDRAULIQUE.get(), 0.6f);
            }
            case "t7_reliquaire" -> {
                if (ouvre && passe(a, t, 1)) son(be, SonsLoot.RUNES.get(), 1f);
                if (ouvre && passe(a, t, 12)) son(be, SonsLoot.HYDRAULIQUE.get(), 1.25f);
                if (t > 40 && now % 3 == 0) {
                    l.addParticle(ParticleTypes.END_ROD, x + (r.nextFloat() - 0.5) * 0.25, y + 0.6, z + (r.nextFloat() - 0.5) * 0.25,
                            0, 0.05 + r.nextFloat() * 0.03, 0);
                }
            }
            case "t7_capsule" -> {
                if (ouvre && passe(a, t, 1)) son(be, SonsLoot.GLACE.get(), 1f);
                if (ouvre && passe(a, t, 10)) son(be, SonsLoot.HYDRAULIQUE.get(), 0.8f);
                if (ouvre && t > 4 && t < 30) {                           // givre qui s'échappe du joint, retombe en flocons
                    l.addParticle(ParticleTypes.SNOWFLAKE, x + (r.nextFloat() - 0.5) * 0.8, y + 0.42, z + (r.nextFloat() - 0.5) * 0.55,
                            (r.nextFloat() - 0.5) * 0.04, 0.015, (r.nextFloat() - 0.5) * 0.04);
                    if (now % 2 == 0) {
                        l.addParticle(GIVRE, x + (r.nextFloat() - 0.5) * 0.8, y + 0.4, z + (r.nextFloat() - 0.5) * 0.4,
                                (r.nextFloat() - 0.5) * 0.03, 0.01, (r.nextFloat() - 0.5) * 0.03);
                    }
                }
            }
            default -> {                                                  // conteneur HeraliZ
                if (ouvre && passe(a, t, 1)) son(be, SonsLoot.RUNES.get(), 1.2f);
                if (ouvre && passe(a, t, 8)) son(be, SonsLoot.HYDRAULIQUE.get(), 0.8f);
                if (ouvre && passe(a, t, 24)) son(be, SonsLoot.VERROUS.get(), 0.7f);
                if (t > 34 && now % 3 == 0) {
                    l.addParticle(ParticleTypes.ELECTRIC_SPARK, x + (r.nextFloat() - 0.5) * 0.4, y + 0.85 + r.nextFloat() * 0.2,
                            z + (r.nextFloat() - 0.5) * 0.4, 0, 0.02, 0);
                }
            }
        }
    }

    private static boolean passe(float avant, float apres, float seuil) {
        return avant < seuil && apres >= seuil;
    }

    private static void son(CoffreBlockEntity be, SoundEvent s, float hauteur) {
        BlockPos worldPosition = be.getBlockPos();
        be.getLevel().playLocalSound(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, s,
                SoundSource.BLOCKS, 1f, hauteur, false);
    }
}
