package fr.heraliz.armory.serveur.mixin;

import com.tacz.guns.entity.EntityKineticBullet;
import fr.heraliz.armory.cible.CibleEssaiServeur;
import fr.heraliz.armory.cible.HitboxCible;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Balles de TaCZ contre la cible d'essai (03/11) : le corps en morceaux d'un joueur debout (HitboxCible, le même que
 * fr.heraliz.tactique.hitbox.HitboxCorps pour les joueurs) remplace la boîte droite de TaCZ et sa « tête » jugée sur la
 * hauteur des yeux. Tête ou corps : exactement comme sur un joueur. Toute autre entité : TaCZ (et le mixin des joueurs du
 * module armes) décident comme avant.
 * <p>Cible : EntityUtil.getHitResult de TaCZ 1.1.8 (noms de TaCZ, non obfusqués : remap = false ; types de Minecraft dans
 * la signature : mêmes noms de classes en jeu). require = 0 : si TaCZ change cette fonction, son calcul revient, rien ne
 * plante. Serveur seulement (config heraliz_armory_serveur.mixins.json, jars -serveur et -complet).</p>
 */
@Mixin(targets = "com.tacz.guns.util.EntityUtil", remap = false)
public abstract class TaczHitboxCibleMixin {
    @Inject(method = "getHitResult(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Lcom/tacz/guns/entity/EntityKineticBullet$EntityResult;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void heraliz$cibleEssai(Projectile balle, Entity cible, Vec3 de, Vec3 a, CallbackInfoReturnable<EntityKineticBullet.EntityResult> cir) {
        if (!(cible instanceof CibleEssaiServeur c) || cible.level().isClientSide) return;
        HitboxCible.Touche t = c.toucherBalle(de, a);
        if (t == null && balle.getOwner() instanceof net.minecraft.server.level.ServerPlayer p) {
            // 04/11 : journal des essais (chiffres non vus) : la balle est passée à côté du corps de la cible
            fr.heraliz.armory.cible.CiblesServeur.journal("balle de {} passée à côté du corps de la cible", p.getGameProfile().getName());
        }
        cir.setReturnValue(t == null ? null : new EntityKineticBullet.EntityResult(cible, t.point(), t.tete()));
    }
}
