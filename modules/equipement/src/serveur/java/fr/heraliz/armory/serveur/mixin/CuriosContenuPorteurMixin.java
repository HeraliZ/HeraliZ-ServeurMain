package fr.heraliz.armory.serveur.mixin;

import fr.heraliz.armory.curios.ContenuReserve;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Contenu des sacs portés envoyé seulement à leur porteur ({@link ContenuReserve}, audit du 20/11). Deux envois de
 * {@code CuriosEventHandler} (curios-forge-5.14.1, jar d'un autre auteur, intact ; lus par {@code javap}) :
 * <ul>
 * <li>{@code syncCurios} (à chaque changement d'un objet d'un emplacement) envoyait l'objet complet au porteur et à tous
 * ceux qui le voient ({@code TRACKING_ENTITY_AND_SELF}) : maintenant l'objet complet au porteur, puis l'objet sans son
 * contenu aux autres ({@code TRACKING_ENTITY}) ; un objet sans contenu part comme avant, en un seul envoi ;</li>
 * <li>{@code lambda$playerStartTracking$16} (un joueur commence à voir le porteur : tous ses emplacements, à ce seul
 * joueur) : sans les contenus.</li>
 * </ul>
 * Noms de Curios et de Forge, non obfusqués : {@code remap = false}. Cibles par leur nom ({@code @Pseudo}, comme
 * {@code GeckolibMessagesMixin}) ; {@code require = 0} et config non obligatoire : si Curios change, ses envois redeviennent
 * ceux d'avant, rien ne plante. {@code syncCurios} est statique : la redirection garde ses deux premiers paramètres (le
 * porteur, l'objet). Serveur seulement (ces envois ne partent que du serveur).
 */
@Pseudo
@Mixin(targets = "top.theillusivec4.curios.common.event.CuriosEventHandler", remap = false)
public abstract class CuriosContenuPorteurMixin {
    @Redirect(method = "syncCurios", remap = false, require = 0,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/minecraftforge/network/simple/SimpleChannel;send(Lnet/minecraftforge/network/PacketDistributor$PacketTarget;Ljava/lang/Object;)V"))
    private static void heraliz$changementPorteurSeul(SimpleChannel canal, PacketDistributor.PacketTarget cible, Object paquet,
                                                      LivingEntity porteur, ItemStack pile) {
        if (porteur instanceof ServerPlayer joueur && ContenuReserve.actif()) {
            ItemStack pourLesAutres = ContenuReserve.pourLesAutres(pile, paquet);
            if (pourLesAutres != null) {
                // le paquet est mis en forme pendant chaque envoi : le même objet sert deux fois, l'objet changé entre les deux
                canal.send(PacketDistributor.PLAYER.with(() -> joueur), paquet);
                ContenuReserve.remplacerPile(paquet, pourLesAutres);
                canal.send(PacketDistributor.TRACKING_ENTITY.with(() -> porteur), paquet);
                return;
            }
        }
        canal.send(cible, paquet);
    }

    @Redirect(method = "lambda$playerStartTracking$16", remap = false, require = 0,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/minecraftforge/network/simple/SimpleChannel;send(Lnet/minecraftforge/network/PacketDistributor$PacketTarget;Ljava/lang/Object;)V"))
    private static void heraliz$arriveeSansContenu(SimpleChannel canal, PacketDistributor.PacketTarget cible, Object paquet) {
        if (ContenuReserve.actif()) ContenuReserve.allegerArrivee(paquet);
        canal.send(cible, paquet);
    }
}
