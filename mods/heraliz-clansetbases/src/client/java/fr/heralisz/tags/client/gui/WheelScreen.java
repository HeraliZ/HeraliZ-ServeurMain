package fr.heralisz.tags.client.gui;

import fr.heralisz.tags.catalogue.TagDef;
import fr.heralisz.tags.catalogue.TagCatalog;
import fr.heralisz.tags.client.ClientTags;
import fr.heralisz.tags.client.TagPlacer;
import fr.heralisz.tags.client.TagsKeys;
import fr.heralisz.tags.client.gui.kit.Ui;
import fr.heralisz.tags.registry.Sons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;

/**
 * Roue radiale des tags (touche J maintenue) : ecran ouvert au franchissement du seuil, ferme au relachement
 * (keyReleased ou mouseReleased de la touche, selon sa liaison) avec pose du quartier sous la souris. A l'echelle du joueur (lisible a l'echelle 4) :
 * verre + 4 quartiers avec l'image du tag (case de l'atlas), quartier survole eclaire au lisere de sa rarete,
 * centre « COLLECTION » cliquable → IndexScreen. Echap ferme sans taguer.
 */
public class WheelScreen extends Screen implements EcranTags {
    /** Rayon interieur (zone morte / centre), rayon exterieur, ecart angulaire entre quartiers (radians). */
    private float rIn, rOut, cx, cy;
    private static final float ECART = 0.035f;
    private int survol = -1;
    private boolean centreSurvole;
    private boolean ferme;
    private long ouvertLe;

    public WheelScreen() {
        super(Component.translatable("gui.heralisz_tags.roue"));
    }

    @Override
    protected void init() {
        cx = width / 2f;
        cy = height / 2f;
        rOut = Math.min(112f, Math.min(width, height) * 0.40f);
        rIn = Math.max(26f, rOut * 0.30f);
        if (ouvertLe == 0) {
            ouvertLe = System.currentTimeMillis();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(Sons.ROUE_OUVRIR.get(), 1f, 0.9f));
        }
    }

    /** Quartier sous la souris : 0 haut, 1 droite, 2 bas, 3 gauche ; -1 dans la zone centrale ou hors roue. */
    private int quartier(double mx, double my) {
        double dx = mx - cx, dy = my - cy;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d < rIn) return -1;
        // angle depuis le haut, sens horaire
        double a = Math.atan2(dx, -dy);
        if (a < 0) a += Math.PI * 2;
        return (int) Math.floor((a + Math.PI / 4) / (Math.PI / 2)) % 4;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int q = quartier(mouseX, mouseY);
        if (q != survol) {
            survol = q;
            if (q >= 0) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(Sons.ROUE_CHOISIR.get(), 0.45f, 1.1f));
        }
        double dcx = mouseX - cx, dcy = mouseY - cy;
        centreSurvole = dcx * dcx + dcy * dcy < rIn * rIn;

        // apparition : la roue s'ouvre en 120 ms
        float t = Math.min(1f, (System.currentTimeMillis() - ouvertLe) / 120f);
        float ease = 1 - (1 - t) * (1 - t);
        // fond : degrade diagonal plus leger que celui des ecrans pleins (le monde reste tres lisible)
        Ui.fondDegrade(g, 0, 0, width, height, Ui.argb(0x000000, (int) (70 * ease)), Ui.argb(0x000000, (int) (120 * ease)));

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(0.85f + 0.15f * ease, 0.85f + 0.15f * ease, 1);
        g.pose().translate(-cx, -cy, 0);

        boolean atlas = Ui.atlasPresent();
        for (int i = 0; i < 4; i++) {
            String id = ClientTags.roue(i);
            TagDef d = TagCatalog.get(id);
            boolean hover = i == survol;
            int rarete = d != null ? d.rarete().couleur : 0xFFFFFF;
            float a0 = (float) (i * Math.PI / 2 - Math.PI / 4 + ECART), a1 = (float) (i * Math.PI / 2 + Math.PI / 4 - ECART);
            // verre
            int in = Ui.argb(Ui.VERRE, hover ? 215 : 185), out = Ui.argb(hover ? Ui.mix(Ui.VERRE, rarete, 0.22f) : Ui.VERRE, hover ? 225 : 200);
            Ui.secteur(g, cx, cy, rIn + 3, rOut, a0, a1, in, out);
            // lisere exterieur (rarete si survole), lisere interieur fin
            Ui.secteur(g, cx, cy, rOut - 1.5f, rOut, a0, a1, Ui.argb(hover ? rarete : 0xFFFFFF, hover ? 230 : 40), Ui.argb(hover ? rarete : 0xFFFFFF, hover ? 230 : 40));
            Ui.secteur(g, cx, cy, rIn + 3, rIn + 4, a0, a1, Ui.argb(0xFFFFFF, hover ? 60 : 22), Ui.argb(0xFFFFFF, hover ? 60 : 22));
            // image du tag au milieu du quartier
            float am = (float) (i * Math.PI / 2), rm = (rIn + rOut) / 2f + 2;
            float ix = cx + (float) Math.sin(am) * rm, iy = cy - (float) Math.cos(am) * rm;
            float taille = Math.min(46f, (rOut - rIn) * 0.62f);
            if (d != null && atlas) {
                Ui.caseAtlasF(g, d.cas(), ix - taille / 2, iy - taille / 2, taille, hover ? 1f : 0.88f);
            } else if (d != null) {
                Ui.soft(g, Math.round(ix - taille / 4), Math.round(iy - taille / 4), Math.round(taille / 2), Math.round(taille / 2), Ui.argb(rarete, 60));
            } else {
                Ui.spacedCentered(g, font, Component.translatable("gui.heralisz_tags.vide").getString(), Ui.SMALL, ix, iy - 3, Ui.argb(Ui.FAINT, 255), 1.2f);
            }
            // nom du tag survole, sous l'image
            if (hover && d != null) {
                Ui.centered(g, font, Ui.lit(d.nom(Ui.french()), Ui.SMALL), ix, iy + taille / 2 + 3, Ui.argb(Ui.TEXT, 235));
            }
        }

        // centre : « COLLECTION »
        Ui.disque(g, cx, cy, rIn - 1, Ui.argb(Ui.VERRE, centreSurvole ? 230 : 200), Ui.argb(Ui.VERRE, centreSurvole ? 230 : 190));
        Ui.secteur(g, cx, cy, rIn - 2, rIn - 1, 0, (float) (Math.PI * 2), Ui.argb(centreSurvole ? Ui.accent : 0xFFFFFF, centreSurvole ? 200 : 50), Ui.argb(centreSurvole ? Ui.accent : 0xFFFFFF, centreSurvole ? 200 : 50));
        String titre = Component.translatable("gui.heralisz_tags.collection").getString();
        Ui.spacedCentered(g, font, titre, Ui.SMALL, cx, cy - 6, Ui.argb(centreSurvole ? Ui.TEXT : Ui.MUTED, 255), 1.1f);
        String compteur = ClientTags.nombreDebloques() + " / " + TagCatalog.taille();
        Ui.centered(g, font, Ui.lit(compteur, Ui.SMALL), cx, cy + 3, Ui.argb(Ui.FAINT, 255));
        if (centreSurvole) {
            float w = Ui.spacedWidth(font, titre.toUpperCase(java.util.Locale.ROOT), Ui.SMALL, 1.1f);
            g.fill(Math.round(cx - w / 2), Math.round(cy + 2), Math.round(cx + w / 2), Math.round(cy + 3), Ui.argb(Ui.accent, 160));
        }
        g.pose().popPose();

        // aide discrete sous la roue
        Ui.spacedCentered(g, font, Component.translatable("gui.heralisz_tags.roue_aide").getString(), Ui.SMALL, cx, cy + rOut + 10, Ui.argb(Ui.FAINT, 255), 1.0f);
        super.render(g, mouseX, mouseY, partial);
    }

    /** Relachement de la touche : pose du quartier survole (s'il a un tag) puis fermeture. Idempotent. */
    public void valider() {
        if (ferme) return;
        ferme = true;
        Minecraft mc = Minecraft.getInstance();
        int q = survol;
        if (mc.screen == this) mc.setScreen(null);
        if (q >= 0) {
            String id = ClientTags.roue(q);
            if (!id.isEmpty()) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(Sons.ROUE_CHOISIR.get(), 0.7f, 1f));
                TagPlacer.taguer(id);
            }
        }
    }

    /** Fermeture sans taguer (Echap, clic sur le centre). */
    private void annuler() {
        if (ferme) return;
        ferme = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == this) mc.setScreen(null);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (TagsKeys.TAG.matches(key, scan)) {
            valider();
            return true;
        }
        return super.keyReleased(key, scan, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (TagsKeys.TAG.matches(key, scan)) return true; // repetition clavier : ignoree
        return super.keyPressed(key, scan, mods);
    }

    /** Touche liee a un bouton de souris : son relachement valide comme keyReleased. */
    @Override
    public boolean mouseReleased(double mx, double my, int bouton) {
        if (TagsKeys.TAG.matchesMouse(bouton)) {
            valider();
            return true;
        }
        return super.mouseReleased(mx, my, bouton);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int bouton) {
        if (bouton == 0 && centreSurvole) {
            ferme = true;
            Minecraft.getInstance().setScreen(new IndexScreen());
            return true;
        }
        if (bouton == 0 && survol >= 0) {
            valider();
            return true;
        }
        return super.mouseClicked(mx, my, bouton);
    }

    @Override
    public void onClose() {
        annuler();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // notre degrade s'en charge
    }
}
