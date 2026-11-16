package fr.heraliz.recolte.serveur;

import fr.heraliz.recolte.entites.Arbre;
import fr.heraliz.recolte.entites.Gisement;
import fr.heraliz.recolte.objets.ObjetsRecolte;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Ce que les joueurs autour voient et entendent quand on récolte : particules et sons de Minecraft, envoyés par le serveur
 * (A_FAIRE §6 ; de vrais sons viendront plus tard). Rien ici ne décide quoi que ce soit.
 */
public final class Effets {
    private Effets() {
    }

    /**
     * Un coup de pioche sur un gisement : éclats de la matière (pierre grise, écailles de rouille et étincelles du métal,
     * éclats et étincelles bleutées du titane), son de pierre ; à chaque étape un morceau s'arrache ; à la fin, il éclate.
     */
    public static void coupGisement(ServerLevel l, Gisement g, Vec3 point, boolean nouvelleEtape, boolean fini) {
        int type = g.type();
        RandomSource r = l.getRandom();
        ItemParticleOption eclats = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Coups.matiere(Reglages.PIERRE + type)));
        BlockParticleOption roche = new BlockParticleOption(ParticleTypes.BLOCK, rocheDe(type).defaultBlockState());
        l.sendParticles(eclats, point.x, point.y, point.z, 8, 0.12, 0.12, 0.12, 0.08);
        l.sendParticles(roche, point.x, point.y, point.z, 4, 0.1, 0.1, 0.1, 0.05);
        if (type != Gisement.PIERRE) {
            l.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 5, 0.08, 0.08, 0.08, 0.3);
        }
        if (type == Gisement.RARE) {
            l.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 3, 0.1, 0.1, 0.1, 0.2);
        }
        l.playSound(null, point.x, point.y, point.z, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.9F, 0.85F + r.nextFloat() * 0.2F);
        if (type == Gisement.METAL) {
            l.playSound(null, point.x, point.y, point.z, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.15F, 1.7F + r.nextFloat() * 0.2F);
        } else if (type == Gisement.RARE) {
            l.playSound(null, point.x, point.y, point.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 1.0F, 0.8F + r.nextFloat() * 0.4F);
        }
        if (nouvelleEtape || fini) {
            double x = g.getX(), y = g.getY() + g.getBbHeight() * 0.5, z = g.getZ(), w = g.getBbWidth() * 0.35;
            l.sendParticles(eclats, x, y, z, fini ? 30 : 16, w, 0.25, w, 0.12);
            l.sendParticles(roche, x, g.getY() + 0.2, z, fini ? 40 : 20, w, 0.15, w, 0.1);
            if (fini) {
                l.sendParticles(ParticleTypes.POOF, x, y, z, 6, w, 0.2, w, 0.02);
            }
            l.playSound(null, x, y, z, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.2F, fini ? 0.6F : 0.75F);
        }
    }

    /** Un coup de hache sur un arbre debout : copeaux et écorce au point touché, quelques feuilles tombent, son de bois. */
    public static void coupArbre(ServerLevel l, Arbre a, Vec3 point) {
        RandomSource r = l.getRandom();
        l.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ObjetsRecolte.BUCHE.get())), point.x, point.y, point.z,
                8, 0.08, 0.08, 0.08, 0.1);
        l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, bois(a).defaultBlockState()), point.x, point.y, point.z,
                4, 0.06, 0.06, 0.06, 0.05);
        l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, feuilles(a).defaultBlockState()), a.getX(), a.getY() + 6.0, a.getZ(),
                5, 1.6, 1.2, 1.6, 0.02);
        l.playSound(null, point.x, point.y, point.z, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 1.0F, 0.8F + r.nextFloat() * 0.2F);
        l.playSound(null, point.x, point.y, point.z, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.BLOCKS, 0.25F, 1.5F + r.nextFloat() * 0.2F);
    }

    /** Dernier coup : l'arbre craque et commence à tomber. */
    public static void craque(ServerLevel l, Arbre a) {
        double y = a.getY() + 0.8;
        l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, bois(a).defaultBlockState()), a.getX(), y, a.getZ(), 14, 0.2, 0.15, 0.2, 0.1);
        l.playSound(null, a.getX(), y, a.getZ(), SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.2F, 0.5F);
        l.playSound(null, a.getX(), y, a.getZ(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 0.5F, 0.6F);
    }

    private static Block rocheDe(int type) {
        return type == Gisement.METAL ? Blocks.IRON_ORE : type == Gisement.RARE ? Blocks.DEEPSLATE : Blocks.STONE;
    }

    private static Block bois(Arbre a) {
        return a.espece() == Arbre.EPICEA ? Blocks.SPRUCE_LOG : Blocks.BIRCH_LOG;
    }

    private static Block feuilles(Arbre a) {
        return a.espece() == Arbre.EPICEA ? Blocks.SPRUCE_LEAVES : Blocks.BIRCH_LEAVES;
    }
}
