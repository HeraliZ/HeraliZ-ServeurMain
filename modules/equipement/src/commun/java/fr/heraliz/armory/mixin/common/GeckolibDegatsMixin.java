package fr.heraliz.armory.mixin.common;

import java.io.PrintStream;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Coupe les calculs de dégâts de l'ancien gestionnaire « Curios » d'ArkaniaZ du jar geckolib222 (audit du 02/11, point 9 ;
 * jar intact). Sur chaque balle de TaCZ et chaque coup d'épée reçu par un joueur, il refaisait les dégâts :
 * <ul>
 *   <li>« le dernier tir était à la tête » gardé dans une variable commune à TOUT le serveur, puis dégâts multipliés par
 *   (multiplicateur de tête ÷ 2) : nos multiplicateurs vont de 1,25 à 2, un tir à la tête faisait donc MOINS que prévu
 *   (fusils à pompe, tête 1,25 : un plomb à la tête = 0,62 x, soit moins qu'un plomb au corps), et un coup de batte qui
 *   suivait n'importe quel tir à la tête sur le serveur était réduit aussi ;</li>
 *   <li>ses anciens gilets (protection écrite dans l'objet à sa fabrication, plus distribués) : une 2e protection en plus
 *   de l'Armurerie, qui fait le seul calcul des armures ;</li>
 *   <li>deux nombres écrits dans la console à chaque coup.</li>
 * </ul>
 * Gardé : sa chance de saignement (le reste de la méthode). Les dégâts et le tir à la tête restent ceux de Tactique
 * ({@code DegatsArmes}, multiplicateur « tete » de armes.json appliqué par TaCZ) et de l'Armurerie.
 */
@Mixin(targets = "software.bernie.example.client.handler.CuriosEventHandler", remap = false)
public abstract class GeckolibDegatsMixin {
    @Redirect(method = "onLivingHurt", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/event/entity/living/LivingHurtEvent;setAmount(F)V", remap = false))
    private static void heraliz$sansNouveauMontant(LivingHurtEvent event, float montant) {
        // rien : les dégâts du coup ne changent pas
    }

    @Redirect(method = "onLivingHurt", remap = false,
            at = @At(value = "INVOKE", target = "Ljava/io/PrintStream;print(D)V", remap = false))
    private static void heraliz$sansTraceGilet(PrintStream sortie, double valeur) {
        // rien dans la console
    }

    @Redirect(method = "onLivingHurt", remap = false,
            at = @At(value = "INVOKE", target = "Ljava/io/PrintStream;print(F)V", remap = false))
    private static void heraliz$sansTraceMontant(PrintStream sortie, float valeur) {
        // rien dans la console
    }
}
