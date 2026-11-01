package fr.heralisz.utils.zombies.cibles;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Recherche de cibles des créatures (01/11, « 150 joueurs ») : pour choisir sa cible (joueur à attaquer, joueur à
 * regarder), une créature passe en revue TOUS les joueurs du monde, et Minecraft juge d'abord, pour chacun, l'équipe des
 * deux, puis sa visibilité (avec un événement de Forge créé et envoyé à chaque fois), et seulement ensuite la distance.
 * Zombie sans cible : environ une recherche toutes les 10 ticks ; avec 150 joueurs, presque tous à des centaines de blocs.
 * <p>
 * Ici, un joueur (ou une créature) hors de portée est écarté AVANT ces calculs, par sa seule distance. Même résultat : la
 * visibilité ne fait que réduire la portée (jamais au-dessus de 1 : aucun mod du serveur ne l'augmente), et Minecraft
 * refuse de toute façon une cible au-delà de max(portée, 2) blocs. Touche toutes les créatures (zombies HeraliZ, Mutants &
 * Zombies, monstres de Minecraft, animaux attirés par la nourriture). Serveur seulement ; config non obligatoire et
 * {@code require = 0} : si Minecraft ou un autre mod change ces appels, la recherche de Minecraft reste telle quelle.
 */
@Mixin(TargetingConditions.class)
public abstract class CiblesPorteeMixin {
    @Shadow
    private double range;

    /** Conditions de combat : refus par la distance avant l'équipe et le reste (même réponse que plus loin). */
    @Redirect(method = "test(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z"),
            require = 0)
    private boolean heraliz$peutAttaquer(LivingEntity attaquant, LivingEntity cible) {
        return !heraliz$horsDePortee(attaquant, cible) && attaquant.canAttack(cible);
    }

    /**
     * Visibilité (événement de Forge) demandée seulement à portée ; hors de portée : 0, et Minecraft refuse alors la cible
     * (au-delà de 2 blocs, ce qui est le cas : elle est au-delà de max(portée, 2)).
     */
    @Redirect(method = "test(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getVisibilityPercent(Lnet/minecraft/world/entity/Entity;)D"),
            require = 0)
    private double heraliz$visibilite(LivingEntity cible, Entity regardeur) {
        if (regardeur instanceof LivingEntity attaquant && heraliz$horsDePortee(attaquant, cible)) return 0.0D;
        return cible.getVisibilityPercent(regardeur);
    }

    /** Au-delà de max(portée, 2) blocs (distance de Minecraft : pieds à pieds), avec une portée. */
    @Unique
    private boolean heraliz$horsDePortee(LivingEntity attaquant, LivingEntity cible) {
        if (this.range <= 0.0D) return false;
        double r = Math.max(this.range, 2.0D);
        return attaquant.distanceToSqr(cible.getX(), cible.getY(), cible.getZ()) > r * r;
    }
}
