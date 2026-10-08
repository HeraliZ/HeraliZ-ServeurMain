package fr.heraliz.usure.client;

import fr.heraliz.usure.ClientConfig;
import fr.heraliz.usure.Usure;
import fr.heraliz.usure.regles.Etat;
import fr.heraliz.usure.regles.Profil;
import fr.heraliz.usure.regles.Regles;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.IItemDecorator;

/**
 * Barre d'usure sur l'icone des armes, a la place et a la facon de la barre de durabilite vanilla (13 x 2 px en bas de
 * la case, cachee quand l'arme est neuve) ; couleur de l'etat (vert, jaune, orange, rouge). La part d'etat perdue aux
 * reparations est marquee en rouge sombre ; une arme ruinee a un fond rouge sombre. Dessinee
 * partout ou Minecraft dessine les decorations des objets : inventaires, barre vanilla et barre de DayZ Hotbar (qui
 * garde les decorations). Aucune allocation : quelques lectures dans le NBT.
 */
public final class BarreUsure implements IItemDecorator {
    public static final BarreUsure INSTANCE = new BarreUsure();

    /** Profondeur des decorations vanilla (au-dessus de l'objet, sans pousser la pile de transformations). */
    private static final int Z = 200;

    private BarreUsure() {
    }

    @Override
    public boolean render(GuiGraphics g, Font font, ItemStack s, int x, int y) {
        if (!ClientConfig.BARRE.get()) return false;
        String id = Usure.gunId(s);
        if (id == null) return false;
        Regles r = ReglesClient.get();
        if (!r.actif) return false;
        Profil p = r.profil(id);
        if (!p.suivie) return false;
        int etat = Usure.etat(s, p);
        int max = Usure.max(s);
        if (etat >= Usure.PLEIN) return false;
        RenderType rt = RenderType.guiOverlay();
        int bx = x + 2, by = y + 13;
        g.fill(rt, bx, by, bx + 13, by + 2, Z, etat <= 0 ? 0xFF4A0E0E : 0xFF000000);
        if (max < Usure.PLEIN) {
            int mx = bx + Math.round(13f * max / Usure.PLEIN);
            g.fill(rt, mx, by, bx + 13, by + 1, Z, 0xFF5A1C1C);
        }
        int w = Math.round(13f * etat / Usure.PLEIN);
        if (w == 0 && etat > 0) w = 1;
        if (w > 0) g.fill(rt, bx, by, bx + w, by + 1, Z, 0xFF000000 | Etat.de(etat, r).couleur);
        return false;
    }
}
