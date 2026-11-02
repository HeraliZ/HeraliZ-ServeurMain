package fr.heraliz.crac.build.client;

import fr.heraliz.crac.client.PlacableGhostRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * HeraliZ (03/11, demande du propriétaire : « supprimer les messages Minecraft classiques en double des notifications de
 * notre interface ») : un refus que le serveur envoie dans la barre d'action (texte rouge au-dessus de la barre d'objets)
 * pendant que le joueur tient le plan, le marteau ou un objet que CRac pose n'est plus montré par Minecraft. Il passe dans
 * l'interface de construction, à la place de son verdict : carte sous le viseur (plan), message au-dessus du viseur
 * (marteau), ligne sous le viseur (objet posé). Avant, le même refus s'affichait deux fois (carte du plan + barre
 * d'action), ou seulement dans la barre d'action quand le joueur croyait la pose possible. Les autres messages de la barre
 * d'action (pas rouges, ou sans outil de construction en main) restent à Minecraft.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "crac", value = Dist.CLIENT)
public final class RefusServeur {
    private static final TextColor ROUGE = TextColor.fromLegacyFormat(ChatFormatting.RED);

    private RefusServeur() {
    }

    @SubscribeEvent
    public static void onSystemMessage(ClientChatReceivedEvent.System event) {
        if (!event.isOverlay()) {
            return;
        }
        LocalPlayer p = Minecraft.getInstance().player;
        Component msg = event.getMessage();
        if (p == null || msg == null || !ROUGE.equals(msg.getStyle().getColor())) {
            return;
        }
        if (BuildClient.holdingPlan(p)) {
            PlanController.refusServeur(msg);
        } else if (BuildClient.holdingHammer(p)) {
            HammerController.refusServeur(msg);
        } else if (PlacableGhostRenderer.objetEnMain(p)) {
            PlacableGhostRenderer.refusServeur(msg);
        } else {
            return;
        }
        event.setCanceled(true);
    }
}
