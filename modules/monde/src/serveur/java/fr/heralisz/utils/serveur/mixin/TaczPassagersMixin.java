package fr.heralisz.utils.serveur.mixin;

import com.tacz.guns.entity.EntityKineticBullet;
import fr.heraliz.vehicules.Passagers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;

/**
 * Passagers d'un véhicule visés par une balle de TaCZ (11/11, demande du propriétaire : dans ou sur un véhicule, on prend
 * les dégâts comme à pied ; fr.heraliz.vehicules.Passagers). TaCZ ne garde que la première entité sur le trajet de la
 * balle : la boîte du véhicule (Limitless Vehicle) entoure le passager du fourgon, de la cabine du camion et les jambes et
 * le buste du motard, donc la balle tapait toujours la carrosserie et le passager n'était jamais touché. Ici, quand la
 * balle s'arrête sur un véhicule qui porte des passagers et que son trajet de ce tick passe aussi par le corps de l'un
 * d'eux (même test que pour un joueur à pied : boîte corrigée du ping de TaCZ, puis hitbox en morceaux de HeraliZ
 * Tactique, tête comprise, et le mur entre les deux), c'est le passager le plus proche du tireur qui est touché, au lieu du
 * véhicule. Ailleurs, rien ne change : le véhicule prend la balle et garde ses propres PV. Réglage
 * {@code balles_traversent} de heraliz_vehicules-server.toml.
 * <p>Seulement la recherche d'une seule cible ({@code findEntityOnPath}, balle normale ou explosive) : une balle qui perce
 * ({@code findEntitiesOnPath}) touche déjà le véhicule puis le passager qu'elle traverse. Le tireur assis dans ce véhicule
 * n'est jamais concerné (TaCZ écarte déjà son propre véhicule et ses voisins de banquette).
 * <p>Cible : EntityUtil de TaCZ 1.1.8 (noms de TaCZ, non obfusqués : remap = false ; types de Minecraft dans la signature :
 * mêmes noms de classes en jeu). require = 0 : si TaCZ change cette méthode, rien ne plante, la carrosserie arrête la balle
 * comme avant.
 */
@Mixin(targets = "com.tacz.guns.util.EntityUtil", remap = false)
public abstract class TaczPassagersMixin {
    @Shadow
    protected static EntityKineticBullet.EntityResult getHitResult(Projectile balle, Entity cible, Vec3 de, Vec3 a) {
        throw new AssertionError();
    }

    @Inject(method = "findEntityOnPath(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Lcom/tacz/guns/entity/EntityKineticBullet$EntityResult;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private static void heralizVehicules$passager(Projectile balle, Vec3 de, Vec3 a, CallbackInfoReturnable<EntityKineticBullet.EntityResult> cir) {
        EntityKineticBullet.EntityResult touche = cir.getReturnValue();
        if (touche == null || !(touche.getEntity() instanceof AbstractVehicle)) return;
        Entity vehicule = touche.getEntity();
        if (!vehicule.isVehicle() || vehicule.level().isClientSide() || !Passagers.ballesTraversent()) return;
        Entity tireur = balle.getOwner();
        EntityKineticBullet.EntityResult mieux = null;
        double dMieux = Double.MAX_VALUE;
        for (Entity passager : vehicule.getPassengers()) {
            if (!(passager instanceof LivingEntity) || passager == tireur || !passager.isAlive() || !passager.isPickable() || passager.isSpectator()) {
                continue;
            }
            EntityKineticBullet.EntityResult r = getHitResult(balle, passager, de, a);
            if (r == null) continue;
            double d = de.distanceToSqr(r.getHitPos());
            if (d < dMieux) {
                dMieux = d;
                mieux = r;
            }
        }
        if (mieux != null) cir.setReturnValue(mieux);
    }
}
