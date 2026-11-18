package fr.heraliz.quetes.client;

import fr.heraliz.quetes.objet.Bourse;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Montant d'une bourse de hérals dans le coin de sa case (18/11, maquette validée {@code maquettes/notifications/3_bourse.jpg}) :
 * à la place du nombre d'objets, en or, court (« 63 », « 1,2k ») ; le montant exact est dans l'infobulle. Police à sa
 * vraie taille (DM Sans gras 6,5 : {@code ui_bold_mini}), jamais agrandie ni réduite.
 */
final class DecorBourse {
    private static final ResourceLocation POLICE = new ResourceLocation("heraliz_quetes", "ui_bold_mini");
    private static final int OR = 0xFFFFD678;
    /** Dernier montant mis en texte (le même d'une image à l'autre : rien à refaire). */
    private static long dernier = -1;
    private static Component texte;

    private DecorBourse() {
    }

    static boolean dessiner(GuiGraphics g, Font font, ItemStack s, int x, int y) {
        long v = Bourse.herals(s);
        if (v <= 0) return false;
        if (v != dernier || texte == null) {
            dernier = v;
            texte = Component.literal(Bourse.court(v)).withStyle(st -> st.withFont(POLICE));
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);   // devant l'objet, comme le nombre d'objets
        g.drawString(font, texte, x + 17 - font.width(texte), y + 9, OR, true);
        g.pose().popPose();
        return false;
    }
}
