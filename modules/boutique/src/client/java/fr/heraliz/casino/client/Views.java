package fr.heraliz.casino.client;

import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.client.render.scene.Designs;
import fr.heraliz.casino.client.render.scene.MachineDesign;
import fr.heraliz.casino.client.render.scene.Scene;
import net.minecraft.client.Minecraft;

/**
 * Traduction de l'etat synchronise d'une machine (entite Minecraft) en "vue" Java pur lue par son modele 3D.
 */
public final class Views {
    private Views() {
    }

    /** Vue a jour de la machine (creee au premier appel et reutilisee). */
    public static Object view(MachineBlockEntity be, Scene s) {
        MachineDesign<?> d = Designs.get(be.type.id);
        if (be.clientView == null) be.clientView = d.newView();
        s.mine = Minecraft.getInstance().player != null && isPlayer(be, Minecraft.getInstance().player.getUUID());
        ViewBinder.fill(be, be.clientView, s);
        return be.clientView;
    }

    private static boolean isPlayer(MachineBlockEntity be, java.util.UUID me) {
        return ViewBinder.player(be) != null && ViewBinder.player(be).equals(me);
    }
}
