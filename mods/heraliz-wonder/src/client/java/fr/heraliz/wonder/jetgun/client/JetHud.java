package fr.heraliz.wonder.jetgun.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.jetgun.JetGun;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Jauge de chaleur du Jet Gun (à la place du compteur de munitions et de la barre de chaleur de TaCZ, masqués tant que le
 * Jet Gun est en main) :
 * <ul>
 *   <li>en bas à droite, au même endroit que le compteur de TaCZ : l'icône de l'arme, la chaleur en grands chiffres
 *   (blanc, ambre, rouge), une barre de 12 segments ; en surchauffe, « SURCHAUFFE » qui clignote et la barre du
 *   refroidissement ; l'invite de purge quand elle est possible (touche de rechargement de TaCZ) ;</li>
 *   <li>sous le viseur, un arc de 21 points qui se remplit avec la chaleur (visible dès qu'elle monte), rouge au-delà
 *   de 85 %.</li>
 * </ul>
 * Aucune allocation par image : textes des chiffres préparés une fois, composants traduits gardés.
 */
public final class JetHud {
    private JetHud() {
    }

    private static final ResourceLocation ICONE = new ResourceLocation(HeralizWonder.MODID, "textures/gun/hud/jetgun.png");
    private static final ResourceLocation TACZ_HUD = new ResourceLocation("tacz", "tac_gun_hud_overlay");
    private static final ResourceLocation TACZ_CHALEUR = new ResourceLocation("tacz", "tac_heat_bar");
    private static final String[] CHIFFRES = new String[101];
    private static final Component CHALEUR = Component.translatable("heraliz_wonder.jetgun.chaleur");
    private static final Component SURCHAUFFE = Component.translatable("heraliz_wonder.jetgun.surchauffe");
    private static KeyMapping touchePurge;
    private static boolean toucheCherchee;
    private static Component invite;
    private static String inviteTouche = "";
    private static float affichee;

    static {
        for (int i = 0; i <= 100; i++) CHIFFRES[i] = String.format("%03d", i);
    }

    /** TaCZ : compteur de munitions et barre de chaleur masqués tant que le Jet Gun est en main. */
    public static void masquerTacz(RenderGuiOverlayEvent.Pre e) {
        ResourceLocation id = e.getOverlay().id();
        if (!TACZ_HUD.equals(id) && !TACZ_CHALEUR.equals(id)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && JetGun.estJetGun(mc.player.getMainHandItem())) e.setCanceled(true);
    }

    public static void rendu(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        ItemStack arme = mc.player.getMainHandItem();
        if (!JetGun.estJetGun(arme)) {
            affichee = 0;
            return;
        }
        float ch = JetGun.chaleur(arme);
        affichee += (ch - affichee) * 0.25f;
        if (Math.abs(ch - affichee) < 0.002f) affichee = ch;
        boolean verrou = JetGun.surchauffe(arme);
        int tick = gui.getGuiTicks();
        Font font = mc.font;
        int pct = Mth.clamp(Math.round(affichee * 100), 0, 100);
        int coul = couleur(affichee, verrou, tick);
        // ---- en bas à droite
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (verrou) RenderSystem.setShaderColor(1f, 0.35f, 0.3f, 1f);
        g.blit(ICONE, w - 117, h - 44, 0, 0, 39, 13, 39, 13);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        g.fill(w - 75, h - 43, w - 74, h - 22, 0xCCFFFFFF);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.scale(1.5f, 1.5f, 1f);
        g.drawString(font, CHIFFRES[pct], (int) ((w - 70) / 1.5f), (int) ((h - 43) / 1.5f), coul, false);
        ps.popPose();
        int xp = (int) (w - 69 + font.width(CHIFFRES[pct]) * 1.5f);
        g.drawString(font, "%", xp, h - 39, coul, false);
        ps.pushPose();
        ps.scale(0.5f, 0.5f, 1f);
        g.drawString(font, verrou ? SURCHAUFFE : CHALEUR, (int) ((w - 70) / 0.5f), (int) ((h - 29.5f) / 0.5f),
                verrou ? ((tick / 5) % 2 == 0 ? 0xFFFF3020 : 0xFFFFD020) : 0xFFAAAAAA, false);
        ps.popPose();
        // barre de 12 segments
        int x0 = w - 70, y0 = h - 25, seg = 12;
        for (int k = 0; k < seg; k++) {
            float a = (k + 1) / (float) seg;
            int c = affichee >= a - 0.5f / seg ? couleur(a, verrou, tick) : 0x55FFFFFF;
            g.fill(x0 + k * 5, y0, x0 + k * 5 + 4, y0 + 3, c);
        }
        if (verrou) {
            // refroidissement : la chaleur redescend après le blocage (barre qui se vide)
            g.fill(x0, y0 + 4, x0 + (int) (59 * affichee), y0 + 5, 0xAAFF6A30);
        } else if (affichee >= JetGun.SEUIL_PURGE) {
            ps.pushPose();
            ps.scale(0.5f, 0.5f, 1f);
            g.drawString(font, invite(), (int) ((w - 70) / 0.5f), (int) ((h - 20.5f) / 0.5f), 0xFFB8C4CC, false);
            ps.popPose();
        }
        // ---- arc sous le viseur
        float sp = JetFx.spinLocal();
        if (affichee > 0.02f || sp > 0.05f || verrou) {
            int cx = w / 2, cy = h / 2;
            int n = 21;
            for (int k = 0; k < n; k++) {
                float a = k / (float) (n - 1);
                float ang = Mth.PI * (0.15f + 0.7f * (1 - a));
                int px = cx + Math.round(Mth.cos(ang) * 17), py = cy + 6 + Math.round(Mth.sin(ang) * 12);
                boolean plein = affichee >= a - 0.02f;
                int c = plein ? couleur(a, verrou, tick) : 0x40FFFFFF;
                g.fill(px - 1, py - 1, px + 1, py + 1, c);
            }
        }
    }

    private static int couleur(float a, boolean verrou, int tick) {
        if (verrou) return (tick / 5) % 2 == 0 ? 0xFFFF3020 : 0xFFFFC020;
        if (a < 0.55f) return 0xFFF2F4F6;
        if (a < 0.8f) return 0xFFFFB030;
        return 0xFFFF4A26;
    }

    private static Component invite() {
        if (!toucheCherchee) {
            toucheCherchee = true;
            for (KeyMapping k : Minecraft.getInstance().options.keyMappings) {
                if ("key.tacz.reload.desc".equals(k.getName())) {
                    touchePurge = k;
                    break;
                }
            }
        }
        String t = touchePurge == null ? "R" : touchePurge.getTranslatedKeyMessage().getString();
        if (invite == null || !t.equals(inviteTouche)) {
            inviteTouche = t;
            invite = Component.translatable("heraliz_wonder.jetgun.purge", t);
        }
        return invite;
    }
}
