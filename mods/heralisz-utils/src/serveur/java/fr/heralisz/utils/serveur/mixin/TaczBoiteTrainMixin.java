package fr.heralisz.utils.serveur.mixin;

import fr.heralisz.utils.train.BallesTrain;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Passagers du train visés par une balle de TaCZ (fr.heralisz.utils.train.BallesTrain#boite) : TaCZ recule la boîte de
 * chaque joueur visé de plusieurs ticks de sa vitesse (2,5 de plus pour un passager), une correction faite pour les
 * entités que le jeu du tireur affiche en retard. Un passager du train est placé chez chacun d'après l'horaire, sans ce
 * retard : à pleine vitesse sa boîte se retrouvait 2 à 4 blocs derrière lui (on ne pouvait pas toucher quelqu'un dans un
 * train qui roule). Pour un passager, la boîte est celle de sa place dans la voiture, au même instant que les murs du
 * train ; pour tout autre cible, TaCZ décide comme avant.
 * <p>
 * Cible : HitboxHelper.getFixedBoundingBox(Entity, Entity) de TaCZ 1.1.8 (noms de TaCZ, non obfusqués : remap = false ;
 * types de Minecraft dans la signature : mêmes noms de classes en jeu). require = 0 : si TaCZ change cette méthode, rien
 * ne plante, la boîte reste celle de TaCZ.
 */
@Mixin(targets = "com.tacz.guns.util.HitboxHelper", remap = false)
public abstract class TaczBoiteTrainMixin {
    @Inject(method = "getFixedBoundingBox(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/AABB;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void heraliszUtils$passagerDuTrain(Entity cible, Entity tireur, CallbackInfoReturnable<AABB> cir) {
        AABB boite = BallesTrain.boite(cible, tireur);
        if (boite != null) cir.setReturnValue(boite);
    }
}
