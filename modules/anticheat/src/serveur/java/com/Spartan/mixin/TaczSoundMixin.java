package com.Spartan.mixin;

import com.Spartan.esp.Noise;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TaCZ envoie le son d'un tir (rechargement, culasse…) aux joueurs proches avec
 * l'identifiant du tireur, et leur client ne le joue QUE si le tireur existe chez
 * eux. Juste avant l'envoi, l'anti-ESP montre donc le tireur aux joueurs à portée
 * du son (distance exacte de TaCZ, silencieux compris). Absent si TaCZ n'est pas
 * installé (@Pseudo, require = 0).
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.sound.SoundManager", remap = false)
public abstract class TaczSoundMixin {
    @Inject(method = "sendSoundToNearby(Lnet/minecraft/world/entity/LivingEntity;ILnet/minecraft/resources/ResourceLocation;"
            + "Lnet/minecraft/resources/ResourceLocation;Ljava/lang/String;FF)V",
            at = @At("HEAD"), require = 0, remap = false)
    private static void spartan$revealShooter(LivingEntity source, int distance, ResourceLocation gunId,
                                              ResourceLocation displayId, String sound, float volume, float pitch,
                                              CallbackInfo ci) {
        Noise.taczSound(source, distance);
    }
}
