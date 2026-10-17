package fr.heraliz.quetes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.quetes.client.kit.Ui;
import fr.heraliz.quetes.entite.Pnj;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Phrases d'ambiance des PNJ : une bulle au-dessus de la tête pendant quelques secondes. */
final class Bulles {
    private Bulles() {
    }

    private record Bulle(String texte, long fin) {
    }

    private static final Map<Integer, Bulle> BULLES = new HashMap<>();

    static void recevoir(Reseau.Bulle b) {
        String s = TexteClient.str(b.texte());
        if (s.isEmpty()) return;
        long duree = Math.min(9000, 3500 + s.length() * 45L);
        BULLES.put(b.entite(), new Bulle(s, System.currentTimeMillis() + duree));
    }

    static void vider() {
        BULLES.clear();
    }

    static void dessiner(Pnj e, PoseStack ps, MultiBufferSource buf, int lumiere, Quaternionf camera, Font font) {
        Bulle b = BULLES.get(e.getId());
        if (b == null) return;
        long reste = b.fin() - System.currentTimeMillis();
        if (reste <= 0) {
            BULLES.remove(e.getId());
            return;
        }
        float alpha = Math.min(1f, reste / 400f);
        List<FormattedCharSequence> lignes = font.split(Ui.lit(b.texte(), Ui.FONT), 150);
        ps.pushPose();
        ps.translate(0.0D, e.getBbHeight() + 0.85D + lignes.size() * 0.25D, 0.0D);
        ps.mulPose(camera);
        ps.scale(-0.025F, -0.025F, 0.025F);
        int fond = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.45F) * 255 * alpha) << 24;
        int couleur = Ui.argb(Ui.TEXT, Math.max(8, (int) (255 * alpha)));
        for (int i = 0; i < lignes.size(); i++) {
            FormattedCharSequence l = lignes.get(i);
            float x = -font.width(l) / 2f;
            font.drawInBatch(l, x, i * 10, couleur, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, fond, lumiere);
        }
        ps.popPose();
    }
}
