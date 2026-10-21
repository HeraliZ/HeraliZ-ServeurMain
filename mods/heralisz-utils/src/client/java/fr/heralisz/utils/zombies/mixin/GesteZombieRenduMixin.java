package fr.heralisz.utils.zombies.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heralisz.utils.zombies.AttaquesZombies;
import fr.heralisz.utils.zombies.client.GestesZombies;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.petemc.mutantszombies.client.model.BlisterZombieModel;
import net.petemc.mutantszombies.client.model.CrawlerModel;
import net.petemc.mutantszombies.client.model.ZombieBruteModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Geste d'attaque des zombies de Mutants & Zombies, pose juste apres l'animation de marche de leur modele (setupAnim),
 * dans le rendu de Minecraft : le jar du mod reste intact. Les classes du mod ne sont touchees que pour leurs propres
 * zombies (genre lu par l'identifiant de l'entite).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class GesteZombieRenduMixin<T extends LivingEntity, M extends EntityModel<T>> {
    @Shadow
    protected M model;

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", shift = At.Shift.AFTER))
    private void heraliz$gesteZombie(T entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        AttaquesZombies.Genre g = AttaquesZombies.genre(entity);
        if (g == null) return;
        float age = entity.tickCount + partialTicks;
        Object m = model;
        switch (g) {
            case CRAWLER -> {
                if (m instanceof CrawlerModel<?> c) GestesZombies.crawler(entity, age, c.head, c.right_arm, c.left_arm, c.right_leg, c.left_leg);
            }
            case BLISTER -> {
                if (m instanceof BlisterZombieModel<?> b) GestesZombies.blister(entity, age, b.head, b.right_arm, b.left_arm);
            }
            case BRUTE -> {
                if (m instanceof ZombieBruteModel<?> b) GestesZombies.brute(entity, age, b.head, b.right_arm, b.left_arm);
            }
        }
    }
}
