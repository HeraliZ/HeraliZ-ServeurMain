package fr.heraliz.quetes.client;

import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;

import java.util.List;

/**
 * Lueurs des points de quête : de petites étincelles sur le bloc à fouiller (la quête en cours, quand on est près), et,
 * pour le staff qui tient la baguette, sur tous les points posés autour de lui.
 */
final class Lueurs {
    private Lueurs() {
    }

    private static List<BlockPos> quete = List.of(), staff = List.of();

    static void recevoir(Reseau.Lueurs l) {
        quete = l.quete();
        staff = l.staff();
    }

    static void vider() {
        quete = List.of();
        staff = List.of();
    }

    static void tick(Minecraft mc) {
        if (mc.level == null || mc.isPaused() || mc.player.tickCount % 6 != 0) return;
        var r = mc.level.random;
        for (BlockPos p : quete) {
            mc.level.addParticle(ParticleTypes.END_ROD, p.getX() + 0.2 + r.nextDouble() * 0.6, p.getY() + 0.9 + r.nextDouble() * 0.4,
                    p.getZ() + 0.2 + r.nextDouble() * 0.6, 0, 0.015, 0);
        }
        for (BlockPos p : staff) {
            mc.level.addParticle(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 0, 0, 0);
        }
    }
}
