package fr.heraliz.armory.serveur.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Jambe cassée et saignement de geckolib222 (contenu ArkaniaZ, jar d'un autre auteur, intact) : leurs phrases anglaises
 * au-dessus de la barre d'objets sont coupées (audit du 15/11, combat M2). {@code BROKEN_BONE} écrivait « You have a broken
 * bone ! Use Splint Item » chaque seconde, {@code BLEEDING} « You are bleeding! Use Field Dressing Item » toutes les 10 s,
 * pour tout le monde (français, russe), avec un objet qui n'existe plus chez nous (le pansement d'ArkaniaZ, changé en
 * Bandage) ; chaque seconde, elles effaçaient aussi nos propres messages de la barre (combat, départ, réanimation...).
 * <p>À la place : notre message traduit, une seule fois quand la blessure commence, avec les vrais objets (attelle,
 * bandage) : {@link fr.heraliz.armory.compat.GeckolibServeur}. La bande d'effets du joueur montre l'état tout du long.
 * Le reste des deux effets ne change pas (lenteur et faiblesse de la jambe cassée, 1 dégât toutes les 10 s du saignement).
 * <p>Cible : l'appel {@code Player.m_5661_} (displayClientMessage) dans {@code m_6742_} (applyEffectTick) des deux
 * classes, vu par {@code javap} sur geckolib222-forge-1.20.1-4.4.7 ; noms d'exécution, {@code remap = false} comme
 * {@code GeckolibDegatsMixin}. {@code require = 0} et config non obligatoire : si le jar change, ses phrases reviennent,
 * rien ne plante. Serveur seulement (ces effets n'écrivent que côté serveur).</p>
 */
@Pseudo
@Mixin(targets = {"software.bernie.example.effect.BROKEN_BONE", "software.bernie.example.effect.BLEEDING"}, remap = false)
public abstract class GeckolibMessagesMixin {
    @Redirect(method = "m_6742_", remap = false, require = 0,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/minecraft/world/entity/player/Player;m_5661_(Lnet/minecraft/network/chat/Component;Z)V"))
    private void heraliz$sansPhraseAnglaise(Player joueur, Component message, boolean barre) {
        // rien : notre message traduit part une fois, au début de la blessure (GeckolibServeur)
    }
}
