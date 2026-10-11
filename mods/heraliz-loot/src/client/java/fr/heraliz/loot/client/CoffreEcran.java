package fr.heraliz.loot.client;

import fr.heraliz.loot.CoffreBlockEntity;
import fr.heraliz.loot.CoffreMenu;
import fr.heraliz.loot.HeralizLoot;
import fr.heraliz.loot.Rarete;
import fr.heraliz.loot.SonsLoot;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Écran d'un coffre T7, sobre : panneau sombre à liseré rouge, cases fines. Les objets pas encore identifiés sont un
 * « ? » rouge ; un carré rouge se trace autour de celui qu'on identifie (la durée dépend de sa rareté, inconnue avant la
 * fin) ; à la révélation, l'objet apparaît en fondu et sa case prend doucement la couleur de sa rareté (halo léger pour
 * légendaire et plus, reflet qui passe pour l'unique).
 */
public class CoffreEcran extends AbstractContainerScreen<CoffreMenu> {
    private static final int FOND = 0xF01A1819, BORD = 0xFF7E2716, BARRE = 0xFFB4391F, BARRE_CRAN = 0xFF6A1E10,
            TITRE = 0xFFECE6DF, TEXTE = 0xFFB9B2AB, FILET = 0xFFE0552F, SEPARATION = 0xFF2E2220,
            CASE = 0xFF141314, CASE_BORD = 0xFF2F2220, INCONNU_BORD = 0xFF5E2216,
            COURANT = 0xFFFF6A3D;
    private static final int Y_COFFRE = 22, Y_INV = 94;
    private final Map<Integer, float[]> revelations = new HashMap<>();   // case -> {début, rareté}
    private final Set<Integer> vus = new HashSet<>();
    private boolean pret;
    private int ticks;

    public CoffreEcran(CoffreMenu menu, Inventory inv, Component titre) {
        super(menu, inv, titre);
        imageWidth = 176;
        imageHeight = 176;
        inventoryLabelY = Y_INV - 13;
    }

    private static boolean reel(ItemStack st) {
        return !st.isEmpty() && !st.is(HeralizLoot.OBJET_INCONNU.get());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ticks++;
        for (int i = 0; i < CoffreBlockEntity.TAILLE; i++) {
            ItemStack st = menu.getSlot(i).getItem();
            if (reel(st) && vus.add(i) && pret) {
                Rarete r = Rarete.de(st);
                r = r == null ? Rarete.COMMUN : r;
                revelations.put(i, new float[]{ticks, r.ordinal()});
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SonsLoot.revelation(r), 1f,
                        r.haute() ? 0.75f : 0.45f));
            }
        }
        pret = true;
        int c = menu.donnees.get(CoffreMenu.D_COURANT), p = menu.donnees.get(CoffreMenu.D_PROGRES),
                d = Math.max(1, menu.donnees.get(CoffreMenu.D_DUREE));
        if (c >= 0 && p > 0 && p % 10 == 0) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SonsLoot.TIC.get(), 0.9f + 0.5f * p / d, 0.18f));
        }
    }

    // ------------------------------------------------------------------------------------------ fond, cadres
    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        g.fill(x, y, x + w, y + h, FOND);
        cadre(g, x, y, w, h, BORD);
        g.fill(x, y, x + w, y + 2, BARRE);
        for (int k = 10; k < w - 6; k += 10) g.fill(x + k, y, x + k + 1, y + 2, BARRE_CRAN);
        g.fill(x + 6, y + Y_INV - 18, x + w - 6, y + Y_INV - 17, SEPARATION);
        float h2 = ticks + pt;
        int courant = menu.donnees.get(CoffreMenu.D_COURANT);
        for (Slot s : menu.slots) {
            int sx = x + s.x - 1, sy = y + s.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, CASE);
            boolean coffre = s.index < CoffreBlockEntity.TAILLE;
            if (!coffre || !s.hasItem()) {
                cadre(g, sx, sy, 18, 18, CASE_BORD);
                continue;
            }
            ItemStack st = s.getItem();
            if (!reel(st)) {
                cadre(g, sx, sy, 18, 18, INCONNU_BORD);
                if (s.index == courant) {
                    float f = Mth.clamp(menu.donnees.get(CoffreMenu.D_PROGRES) / (float) Math.max(1, menu.donnees.get(CoffreMenu.D_DUREE)), 0, 1);
                    int a = (int) (22 + 14 * Mth.sin(h2 * 0.25f));
                    g.fill(sx + 1, sy + 1, sx + 17, sy + 17, (a << 24) | (COURANT & 0xFFFFFF));
                    trace(g, sx, sy, f, COURANT);
                }
                continue;
            }
            Rarete r = Rarete.de(st);
            int coul = r == null ? 0x8A8A8A : r.couleur;
            float[] rev = revelations.get(s.index);
            float age = rev == null ? 999 : h2 - rev[0];
            float vif = Mth.clamp(1 - age / 14f, 0, 1);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, ((int) (0x16 + 0x30 * vif) << 24) | coul);
            cadre(g, sx, sy, 18, 18, ((int) (0xB0 + 0x4F * vif) << 24) | coul);
        }
    }

    /** Carré tracé autour de la case (sens horaire depuis le coin haut gauche), fraction f du périmètre. */
    private static void trace(GuiGraphics g, int x, int y, float f, int c) {
        float l = f * 68;                                                  // 4 côtés de 17 px
        int a = (int) Math.min(17, l);
        g.fill(x, y, x + a + 1, y + 1, c);
        if (l > 17) g.fill(x + 17, y, x + 18, y + (int) Math.min(17, l - 17) + 1, c);
        if (l > 34) {
            int b = (int) Math.min(17, l - 34);
            g.fill(x + 17 - b, y + 17, x + 18, y + 18, c);
        }
        if (l > 51) {
            int b = (int) Math.min(17, l - 51);
            g.fill(x, y + 17 - b, x + 1, y + 18, c);
        }
    }

    private static void cadre(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y + 1, x + 1, y + h - 1, c);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, c);
    }

    // ------------------------------------------------------------------------------------------ objets
    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        String nom = title.getString().replaceFirst("^\\[T\\d] ", "");
        g.drawString(font, nom, 8, 7, TITRE, false);
        g.fill(8, 17, 8 + font.width(nom), 18, FILET);
        int n = menu.donnees.get(CoffreMenu.D_RESTANTS);
        String droite = n > 0 ? I18n.get("heraliz_loot.coffre.a_identifier", n) : I18n.get("heraliz_loot.coffre.tout_identifie");
        g.drawString(font, droite, imageWidth - 8 - font.width(droite), 7, TEXTE, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, TEXTE, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        float h = ticks + pt;
        // révélations : l'objet apparaît en fondu ; halo doux pour légendaire et plus ; reflet pour l'unique
        for (Map.Entry<Integer, float[]> e : revelations.entrySet()) {
            float age = h - e.getValue()[0];
            if (age > 26) continue;
            Rarete r = Rarete.values()[(int) e.getValue()[1]];
            Slot s = menu.getSlot(e.getKey());
            int sx = leftPos + s.x - 1, sy = topPos + s.y - 1;
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            float voile = Mth.clamp(1 - age / 7f, 0, 1);
            if (voile > 0.01f) g.fill(sx + 1, sy + 1, sx + 17, sy + 17, ((int) (voile * 235) << 24) | (CASE & 0xFFFFFF));
            if (r.haute()) {
                float k = Mth.clamp(age / 18f, 0, 1);
                int a = (int) (110 * (1 - k));
                int m = 1 + (int) (k * 3);
                if (a > 4) cadre(g, sx - m, sy - m, 18 + 2 * m, 18 + 2 * m, (a << 24) | r.couleur);
            }
            if (r == Rarete.UNIQUE && age > 4 && age < 20) {
                int px = (int) ((age - 4) / 16f * 26) - 4;
                for (int i = 0; i < 16; i++) {
                    int xx = sx + 1 + px + i / 3 - 4;
                    if (xx > sx && xx < sx + 17) g.fill(xx, sy + 1 + i, xx + 2, sy + 2 + i, 0x40FFFFFF);
                }
            }
            g.pose().popPose();
        }
        renderTooltip(g, mx, my);
    }
}
