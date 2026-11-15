package fr.heraliz.armory.compat;

import fr.heraliz.noyau.api.ATerre;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Effets de geckolib222 (contenu ArkaniaZ, jar d'un autre auteur, jamais modifié), côté serveur (audit du 15/11).
 * <ul>
 *   <li>Combat M2 : jambe cassée ({@code geckolib:broken_bone}) et saignement ({@code geckolib:bleeding}) : notre message
 *   traduit dans la barre d'action, UNE fois, quand la blessure commence (effet nouveau, pas un effet renouvelé), avec les
 *   vrais objets qui soignent. Les phrases anglaises que geckolib222 répétait chaque seconde (jambe) ou toutes les 10 s
 *   (saignement) sont coupées par {@code fr.heraliz.armory.serveur.mixin.GeckolibMessagesMixin} : nos autres messages de
 *   la barre ne sont plus effacés. Rien pour un joueur à terre (il ne peut pas se soigner) ; en match, ces effets sont
 *   déjà refusés par le noyau.</li>
 * </ul>
 * Fil du serveur ; rien ne tourne sans effet ajouté.
 */
public final class GeckolibServeur {
    private static final ResourceLocation JAMBE_CASSEE = new ResourceLocation("geckolib", "broken_bone");
    private static final ResourceLocation SAIGNEMENT = new ResourceLocation("geckolib", "bleeding");

    private GeckolibServeur() {
    }

    public static void brancher() {
        MinecraftForge.EVENT_BUS.addListener(GeckolibServeur::effetAjoute);
    }

    private static void effetAjoute(MobEffectEvent.Added e) {
        if (e.getOldEffectInstance() != null || !(e.getEntity() instanceof ServerPlayer p) || p.hasDisconnected() || ATerre.aTerre(p)) {
            return;
        }
        ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(e.getEffectInstance().getEffect());
        if (JAMBE_CASSEE.equals(id)) {
            p.displayClientMessage(Component.translatableWithFallback("message.heraliz_armory.blessure.jambe",
                    "Jambe cassée : utilise une attelle."), true);
        } else if (SAIGNEMENT.equals(id)) {
            p.displayClientMessage(Component.translatableWithFallback("message.heraliz_armory.blessure.saignement",
                    "Tu saignes : utilise un bandage ou une trousse de soins."), true);
        }
    }
}
