package fr.heraliz.grades.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.heraliz.grades.Grade;
import fr.heraliz.grades.reseau.ReseauGrades;
import fr.heraliz.grades.reseau.ReseauStaff;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

/**
 * Panel du staff chez le joueur (25/10) : la touche P (« Panel du staff ») demande l'ouverture au serveur, qui ne répond
 * qu'au staff connecté sur AuthMe ; un joueur hors du staff n'envoie rien. L'écran ({@link EcranStaff}) n'affiche que ce
 * que le serveur envoie. Reçoit aussi « vider le chat » (action du staff).
 */
public final class PanelClient {
    private PanelClient() {
    }

    static final KeyMapping TOUCHE = new KeyMapping("key.heraliz_grades.panel_staff", net.minecraftforge.client.settings.KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P,
            "key.categories.heraliz_grades");
    private static long derniere;

    static void init(IEventBus busMod) {
        busMod.addListener((RegisterKeyMappingsEvent e) -> e.register(TOUCHE));
        MinecraftForge.EVENT_BUS.addListener(PanelClient::tick);
        ReseauStaff.RECEPTION_ETAT.brancher(EcranStaff::recevoir);
        ReseauStaff.RECEPTION_FICHE.brancher(EcranStaff::recevoirFiche);
        ReseauStaff.RECEPTION_VIDER.brancher(m -> Minecraft.getInstance().gui.getChat().clearMessages(false));
    }

    /** Joueur du staff d'après le tab (grade envoyé par le serveur) ou op de niveau 4 : le serveur revérifie de toute façon. */
    static boolean staff() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (mc.player.hasPermissions(4)) return true;
        ReseauGrades.LigneTab l = TabHeraliz.info(mc.player.getUUID());
        Grade g = l == null ? null : Grade.parId(l.grade());
        return g != null && g.type == Grade.Type.STAFF;
    }

    private static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (TOUCHE.consumeClick()) {
            long t = System.currentTimeMillis();
            if (mc.screen != null || mc.getConnection() == null || !staff() || t - derniere < 500) continue;
            derniere = t;
            ReseauStaff.CANAL.sendToServer(new ReseauStaff.Ouvrir());
        }
    }
}
