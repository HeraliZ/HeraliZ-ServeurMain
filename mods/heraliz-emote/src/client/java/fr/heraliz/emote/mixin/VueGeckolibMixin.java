package fr.heraliz.emote.mixin;

import fr.heraliz.emote.client.CameraEmote;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * geckolib222 (GeckoLib modifié avec le contenu ArkaniaZ, jar d'un autre auteur, pas touché) remet la vue à la
 * 1re personne à CHAQUE tick hors du créatif : {@code PlayerShaderHandler.onClientTick} et
 * {@code BleedingEventHandler.onPlayerTick}. C'était ce qui empêchait la 3e personne pendant une emote. Ici, seul ce
 * changement de vue est sauté tant que le joueur joue une emote ({@link CameraEmote#actif()}) ; le reste de leurs
 * handlers ne change pas. Cible : nom du jeu des joueurs ({@code m_92157_} = {@code Options.setCameraType}). Absents ou changés dans une autre version : rien n'est appliqué (require = 0).
 */
@Mixin(targets = {"software.bernie.example.client.handler.PlayerShaderHandler",
        "software.bernie.example.client.handler.BleedingEventHandler"}, remap = false)
public abstract class VueGeckolibMixin {
    @Redirect(method = {"onClientTick", "onPlayerTick"}, require = 0, remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;m_92157_(Lnet/minecraft/client/CameraType;)V", remap = false))
    private void heraliz$vueEmote(Options options, CameraType type) {
        if (type == CameraType.FIRST_PERSON && CameraEmote.actif()) return;
        options.setCameraType(type);
    }
}
