package fr.heralisz.matchs.zombies.client;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.client.hud.HudZombiesRendu;
import fr.heralisz.matchs.zombies.jeu.api.HudZombies;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ambiance visuelle cote client pendant une partie de zombies ({@link HudZombies#actif()}) : brouillard rouge sombre
 * qui se leve pendant les manches speciales (Rodeurs), et jauges vanilla masquees (coeurs, faim, experience, armure,
 * air, soif, icones d'effets) : la vie se lit sur la carte du joueur du HUD zombies. Le chat remonte au-dessus de la
 * carte du joueur ; la boussole est alimentee toutes les 5 ticks. La musique et le fond sonore sont dans
 * {@code zombies/client/musique/MusiqueZombies}.
 */
@Mod.EventBusSubscriber(modid = HeraliszMatchs.MODID, value = Dist.CLIENT)
public final class ZbClientForge {
    private ZbClientForge() {
    }

    /** 0..1 : intensite du brouillard des manches speciales (lissee). */
    private static float brume, brumeAvant;
    private static int ticksBoussole;
    /** Banc d'essai (etape {@code zbChat}) : chat non dessine. */
    public static boolean chatMasque;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean actif = HudZombies.actif() && mc.level != null;
        brumeAvant = brume;
        float cible = actif && HudZombies.speciale() ? 1 : 0;
        brume += Mth.clamp(cible - brume, -0.02F, 0.02F);
        // boussole : zombies proches et boss a portee, 4 fois par seconde
        if (++ticksBoussole >= 5) {
            ticksBoussole = 0;
            HudZombiesRendu.tickBoussole(mc);
        }
    }

    /**
     * Chat de Minecraft remonte pendant une partie : son bas passe 4 unites au-dessus du bloc du joueur (carte et
     * coequipiers, en bas a gauche), soit h - 41 seul et h - 81 a quatre.
     */
    @SubscribeEvent
    public static void chat(CustomizeGuiOverlayEvent.Chat e) {
        if (!HudZombies.actif()) return;
        Minecraft mc = Minecraft.getInstance();
        int coequipiers = Mth.clamp(HudZombies.joueurs().size() - 1, 0, 3);
        int cible = HudZombiesRendu.hautBloc(e.getWindow().getGuiScaledHeight(), coequipiers) - 4;
        if (mc.player != null && cible < e.getPosY()) e.setPosY(cible);
    }

    /** Une barre de boss vanilla est affichee : la boussole (meme place) se cache. */
    @SubscribeEvent
    public static void barreBoss(CustomizeGuiOverlayEvent.BossEventProgress e) {
        if (HudZombies.actif()) HudZombiesRendu.barreBossVanilla();
    }

    /**
     * Jauges masquees en partie : faim, experience, coeurs, armure, air, nom de l'objet tenu (texte vanilla au-dessus de
     * la barre), les icones d'effets (en haut a droite, sur le numero de manche) et la soif de ThirstWasTaken (espace
     * de noms {@code thirst}).
     */
    @SubscribeEvent
    public static void jauges(RenderGuiOverlayEvent.Pre e) {
        var id = e.getOverlay().id();
        // banc d'essai : chat masque a l'ecran (les commandes restent permises, contrairement a l'option « chat cache »)
        if (chatMasque && id.equals(VanillaGuiOverlay.CHAT_PANEL.id())) {
            e.setCanceled(true);
            return;
        }
        if (!HudZombies.actif()) return;
        if (id.equals(VanillaGuiOverlay.FOOD_LEVEL.id()) || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
                || id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id()) || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id())
                || id.equals(VanillaGuiOverlay.AIR_LEVEL.id()) || id.equals(VanillaGuiOverlay.ITEM_NAME.id())
                || id.equals(VanillaGuiOverlay.MOUNT_HEALTH.id()) || id.equals(VanillaGuiOverlay.POTION_ICONS.id())
                || "thirst".equals(id.getNamespace())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void brouillard(ViewportEvent.RenderFog e) {
        float b = Mth.lerp((float) e.getPartialTick(), brumeAvant, brume);
        if (b <= 0.001F) return;
        e.setNearPlaneDistance(Mth.lerp(b, e.getNearPlaneDistance(), 0.5F));
        e.setFarPlaneDistance(Mth.lerp(b, e.getFarPlaneDistance(), 20F));
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void couleurBrouillard(ViewportEvent.ComputeFogColor e) {
        float b = Mth.lerp((float) e.getPartialTick(), brumeAvant, brume);
        if (b <= 0.001F) return;
        e.setRed(Mth.lerp(b, e.getRed(), 0.16F));
        e.setGreen(Mth.lerp(b, e.getGreen(), 0.04F));
        e.setBlue(Mth.lerp(b, e.getBlue(), 0.04F));
    }
}
