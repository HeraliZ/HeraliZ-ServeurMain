package fr.heraliz.wonder.serveur;

import fr.heraliz.wonder.HeralizWonder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;

import javax.annotation.Nullable;

/**
 * Degats des effets de zone, toujours cote serveur. Trois types de degats du mod (data/heraliz_wonder/damage_type) :
 * <ul>
 *   <li>{@link #ECLABOUSSURE} : eclaboussure du Pistolet a rayons, dans {@code #minecraft:is_explosion} ; en partie de
 *   zombies le mode multiplie donc ces degats par le niveau de Pack-a-Punch de l'arme tenue, et plafonne ceux du tireur
 *   sur lui-meme a 3 PV ;</li>
 *   <li>{@link #FOUDRE} : arcs de la Wunderwaffe (sans recul : la cible se fige, foudroyee) ;</li>
 *   <li>{@link #SOUFFLE} : onde de choc du Thundergun (le recul de Minecraft est annule, {@link Tonnerre} projette la
 *   creature lui-meme).</li>
 * </ul>
 * Source directe nulle, source causale = le tireur : les kills et les points lui reviennent, et le mode zombies ne
 * confond jamais ces degats avec un coup de couteau.
 */
public final class Degats {
    private Degats() {
    }

    public static final ResourceKey<DamageType> ECLABOUSSURE = cle("eclaboussure");
    public static final ResourceKey<DamageType> FOUDRE = cle("foudre");
    /** Souffle du Thundergun (1.0.2) : le recul est donne a part ({@link Tonnerre}), la creature s'envole. */
    public static final ResourceKey<DamageType> SOUFFLE = cle("souffle");

    /** Vrai pendant l'application d'un degat sans recul (lu par {@link #recul}). */
    private static boolean sansRecul;

    private static ResourceKey<DamageType> cle(String n) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(HeralizWonder.MODID, n));
    }

    public static DamageSource source(ServerLevel niveau, ResourceKey<DamageType> type, @Nullable Entity tireur) {
        return new DamageSource(niveau.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), null, tireur);
    }

    /** Blesse la cible tout de suite (invulnerabilite remise a zero comme pour une balle TaCZ). */
    public static boolean blesser(LivingEntity cible, DamageSource src, float montant, boolean recul) {
        if (montant <= 0 || !cible.isAlive()) return false;
        cible.invulnerableTime = 0;
        sansRecul = !recul;
        try {
            return cible.hurt(src, montant);
        } finally {
            sansRecul = false;
        }
    }

    public static void recul(LivingKnockBackEvent e) {
        if (sansRecul) e.setCanceled(true);
    }
}
