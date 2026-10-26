package fr.heraliz.tactique.mixin.client;

import com.mojang.blaze3d.audio.SoundBuffer;
import fr.heraliz.tactique.client.son.SonsMono;
import net.minecraft.client.sounds.SoundBufferLibrary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Chargement d'un son : ceux des gunpacks sont gardés en mono dans le cache des sons ({@link SonsMono}), une seule fois.
 */
@Mixin(SoundBufferLibrary.class)
public abstract class SonsMonoMixin {
    @SuppressWarnings("unchecked")
    @Redirect(method = "getCompleteBuffer", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private Object heraliz$mono(Map<Object, Object> cache, Object cle, Function<Object, Object> charger) {
        if (!SonsMono.viser(cle)) return cache.computeIfAbsent(cle, charger);
        return cache.computeIfAbsent(cle, k -> ((CompletableFuture<SoundBuffer>) charger.apply(k)).thenApply(SonsMono::mono));
    }
}
