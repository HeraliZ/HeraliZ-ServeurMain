package fr.heraliz.vehicules.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;
import org.ywzj.vehicle.network.Channel;
import org.ywzj.vehicle.network.message.ClientVehicleAction;

/**
 * On sort d'un véhicule avec la touche « S'accroupir » du joueur, comme d'une monture de Minecraft (14/11, demande du
 * propriétaire : « que la touche pour sortir soit directement la touche de sneak »).
 * <ul>
 *   <li><b>Avant</b> : Limitless Vehicle (moteur des véhicules) empêche la descente de Minecraft par S'accroupir (son
 *   mixin {@code PlayerMixin} sur {@code wantsToStopRiding}) et fait sortir par sa propre touche « Sortir du véhicule »
 *   (J à l'origine). Nous l'avions mise sur Maj gauche ({@link ClientVehicules}) mais, dans notre contexte « à bord »,
 *   Forge ne déclenchait aucune touche sans modificateur tant que Maj était tenue : Maj gauche ne faisait pas sortir, et
 *   un joueur qui avait mis S'accroupir ailleurs devait trouver la touche dans les options.</li>
 *   <li><b>Maintenant</b> : S'accroupir enfoncée à bord (touche réglée par le joueur dans ses options, et telle que le jeu
 *   la lit pour s'accroupir : {@code input.shiftKeyDown}, donc sans effet là où le jeu bloque l'accroupi : joueur à terre
 *   ou qu'on réanime, cinématiques…) : le jeu envoie au serveur le MÊME paquet que la touche du moteur
 *   ({@code ClientVehicleAction}, {@code leaveVehicle}). Le serveur fait descendre le joueur par le chemin du moteur
 *   (place et conduite libérées, endroit de sortie du moteur, vérifié par le serveur : {@code SortieSure}), puis
 *   Minecraft replace le joueur chez lui. Le jeu ne fait rien descendre lui-même : le serveur et le joueur sont toujours
 *   d'accord. Une demande à l'appui, puis une toutes les {@value #RELANCE} ticks si la touche reste enfoncée et que le
 *   joueur est encore à bord (réponse du serveur en retard).</li>
 *   <li>En montant dans un véhicule : rappel « [touche] : sortir du véhicule » dans la barre d'action (le moteur y efface
 *   le message de Minecraft « Appuyez sur Maj gauche pour descendre »).</li>
 * </ul>
 * Seulement à bord (rien d'autre à chaque tick qu'un test du véhicule du joueur). La touche « Sortir du véhicule » du
 * moteur reste dans les options, sans touche par défaut : un joueur peut lui en donner une en plus.
 */
public final class SortieAccroupi {
    /** Ticks entre deux demandes de sortie tant que la touche reste enfoncée à bord (le serveur répond en 1 ou 2 ticks). */
    static final int RELANCE = 10;
    /** À bord d'un véhicule au tick précédent (rappel de la touche à la montée). */
    private static boolean aBord;
    /** Ticks avant de redemander la sortie, touche toujours enfoncée. */
    private static int attente;

    private SortieAccroupi() {
    }

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(SortieAccroupi::tick);
    }

    /** Fin du tick du jeu : la touche S'accroupir vient d'être lue par le joueur (input.shiftKeyDown à jour). */
    private static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer joueur = mc.player;
        if (joueur == null || !(joueur.getVehicle() instanceof AbstractVehicle vehicule)) {
            aBord = false;
            attente = 0;
            return;
        }
        if (!aBord) {
            aBord = true;
            mc.gui.setOverlayMessage(Component.translatable("heraliz_vehicules.sortie.aide",
                    mc.options.keyShift.getTranslatedKeyMessage()), false);
        }
        if (mc.screen != null || !mc.options.keyShift.isDown() || !joueur.input.shiftKeyDown) {
            attente = 0;
            return;
        }
        if (attente > 0) {
            attente--;
            return;
        }
        attente = RELANCE;
        ClientVehicleAction sortie = new ClientVehicleAction();
        sortie.vehicleEntityId = vehicule.getId();
        sortie.leaveVehicle = true;
        Channel.CHANNEL.sendToServer(sortie);
    }
}
