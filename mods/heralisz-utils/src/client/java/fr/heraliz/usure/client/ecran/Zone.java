package fr.heraliz.usure.client.ecran;

import fr.heraliz.usure.client.kit.HzField;
import fr.heraliz.usure.client.kit.HzScroll;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.ArrayList;
import java.util.List;

/**
 * Contenu defilant d'un onglet : des dessins (titres, libelles, fonds de lignes) sous des widgets (champs, interrupteurs,
 * boutons), tous en coordonnees de contenu. Les champs recoivent leur tick (curseur qui clignote).
 */
public class Zone extends HzScroll {
    /** Un dessin : souris deja en coordonnees de contenu (hors zone : -10000). */
    public interface Dessin {
        void dessiner(GuiGraphics g, int mouseX, int mouseY);
    }

    private final List<Dessin> dessins = new ArrayList<>();
    private final List<HzField> champs = new ArrayList<>();

    public Zone(int x, int y, int w, int h) {
        super(x, y, w, h);
    }

    public void dessin(Dessin d) {
        dessins.add(d);
    }

    @Override
    public <T extends AbstractWidget> T add(T w) {
        if (w instanceof HzField f) champs.add(f);
        return super.add(w);
    }

    @Override
    public void clear() {
        super.clear();
        dessins.clear();
        champs.clear();
    }

    public void tickChamps() {
        for (HzField f : champs) f.tick();
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        for (Dessin d : dessins) d.dessiner(g, mouseX, mouseY);
        super.renderContent(g, mouseX, mouseY, partialTick);
    }
}
