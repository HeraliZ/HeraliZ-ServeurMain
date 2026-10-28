package fr.heraliz.noyau.serveur.mixin;

import fr.heraliz.noyau.NoyauConfig;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Faim plus lente (25/10, demande du propriétaire : « la nourriture diminue trop vite ») : toute fatigue de Minecraft
 * (course, sauts, coups, soins naturels de la vie) est multipliée par {@code [survie] faim_facteur} de
 * {@code heraliz_noyau-server.toml} (0,35 depuis le 28/10 : la barre de faim dure environ trois fois plus longtemps). Les règles de Minecraft ne
 * changent pas (saturation, vie qui remonte quand on a mangé) : seulement leur vitesse. Décidé par le serveur.
 */
@Mixin(FoodData.class)
public abstract class FaimMixin {
    @ModifyVariable(method = "addExhaustion(F)V", at = @At("HEAD"), argsOnly = true)
    private float heraliz$faim(float fatigue) {
        return fatigue * NoyauConfig.faimFacteur;
    }
}
