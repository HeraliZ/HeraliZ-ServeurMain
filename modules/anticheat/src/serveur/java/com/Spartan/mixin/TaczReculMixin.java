package com.Spartan.mixin;

import com.Spartan.check.movement.LatenceCheck;
import net.minecraft.server.level.ServerPlayer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Recul des hitbox de TaCZ (19/11, « backtrack ») : quand un joueur tire, TaCZ replace chaque cible à l'endroit où elle
 * était il y a « ping du tireur » (ServerPlayer.latency, mesure keepalive de Minecraft, jusqu'à
 * {@code ServerHitboxLatencyMaxSaveMs} = 350 ms). Un jeu modifié qui retient ses réponses keepalive gonfle ce ping et touche
 * des joueurs déjà à couvert. Ici, la latence lue par TaCZ est celle de Spartan ({@link LatenceCheck#pourRecul}) : jamais
 * plus que la latence mesurée par les transactions + 100 ms. Joueur honnête : rien ne change.
 * <p>
 * Cible : HitboxHelper.getFixedBoundingBox(Entity, Entity) de TaCZ 1.1.8 (balles : EntityUtil.getHitResult ; explosions :
 * ProjectileExplosion), seule lecture de {@code ServerPlayer.latency} (f_8943_, nom du jeu en production) dans TaCZ, vérifié
 * avec javap. Noms de TaCZ et du jeu écrits tels quels : remap = false. Ajouté par SpartanMixinPlugin seulement avec TaCZ ;
 * require = 0 : un TaCZ changé garde son calcul, rien ne plante.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.util.HitboxHelper", remap = false)
public abstract class TaczReculMixin {
    @Redirect(method = "getFixedBoundingBox(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/AABB;",
            at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerPlayer;f_8943_:I", opcode = Opcodes.GETFIELD,
                    remap = false),
            require = 0, remap = false)
    private static int spartan$latenceDuRecul(ServerPlayer tireur) {
        return LatenceCheck.pourRecul(tireur);
    }
}
