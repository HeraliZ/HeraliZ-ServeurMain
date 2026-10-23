package fr.heraliz.interfaces.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.heraliz.interfaces.HeralizInterface;
import java.lang.reflect.Method;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Regard libre façon Rust (23/10, demande du propriétaire) : touche maintenue (Alt gauche par défaut, réglable dans
 * Options &gt; Commandes) : la souris tourne seulement la caméra, pas le joueur ; le corps, la marche et la visée gardent
 * leur direction (un tir part toujours droit devant le corps). Au relâchement, la vue revient en douceur. Seulement une
 * rotation de la tête (lacet ±100°, tangage ±80° autour du regard du joueur) : rien ne passe à travers les murs.
 * Coupé pendant la visée d'une arme TaCZ, une emote, une cinématique (caméra posée sur une autre entité), à bord d'un
 * véhicule (le moteur des véhicules a son propre regard libre) et dès qu'un écran est ouvert. Rien côté serveur : les
 * autres joueurs voient le joueur regarder droit devant lui. Le mouvement de la souris est pris par
 * {@code mixin.RegardLibreMixin}.
 */
@Mod.EventBusSubscriber(modid = HeralizInterface.MODID, value = Dist.CLIENT)
public final class RegardLibre {
    private RegardLibre() {
    }

    public static final KeyMapping TOUCHE = new KeyMapping("key.heraliz_interface.regard_libre", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, "key.categories.heraliz_interface");

    private static final float LACET_MAX = 100F;
    private static final float TANGAGE_MAX = 80F;
    /** Durée caractéristique du retour en douceur (secondes). */
    private static final double RETOUR = 0.08;

    private static boolean actif;
    private static float lacet, tangage;
    private static long derniere;

    /** Vrai pendant que le regard libre tient la souris. */
    public static boolean actif() {
        return actif;
    }

    /** Mouvement de la souris pendant le regard libre (mêmes facteurs que {@code Entity.turn}). */
    public static void tourner(double dx, double dy) {
        lacet = Mth.clamp(lacet + (float) dx * 0.15F, -LACET_MAX, LACET_MAX);
        tangage = Mth.clamp(tangage + (float) dy * 0.15F, -TANGAGE_MAX, TANGAGE_MAX);
    }

    @Mod.EventBusSubscriber(modid = HeralizInterface.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Enregistrement {
        private Enregistrement() {
        }

        @SubscribeEvent
        public static void touches(RegisterKeyMappingsEvent e) {
            e.register(TOUCHE);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        actif = permis(mc) && tenue(mc);
    }

    private static boolean tenue(Minecraft mc) {
        InputConstants.Key k = TOUCHE.getKey();
        if (k.getType() == InputConstants.Type.KEYSYM && k.getValue() != InputConstants.UNKNOWN.getValue()) {
            return InputConstants.isKeyDown(mc.getWindow().getWindow(), k.getValue());
        }
        return TOUCHE.isDown();
    }

    private static boolean permis(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.screen != null || mc.getCameraEntity() != p || !mc.isWindowActive()) return false;
        Entity v = p.getVehicle();
        if (v != null && "ywzj_vehicle".equals(EntityType.getKey(v.getType()).getNamespace())) return false;
        if (InterfaceClient.enEmote()) return false;
        return !viseTacz(p);
    }

    /** Angles de la caméra : regard du joueur + décalage du regard libre (ramené en douceur vers 0 hors regard libre). */
    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        long t = System.nanoTime();
        double dt = derniere == 0 ? 0 : Math.min(0.25, (t - derniere) / 1.0E9);
        derniere = t;
        if (!actif && (lacet != 0 || tangage != 0)) {
            float f = (float) Math.exp(-dt / RETOUR);
            lacet *= f;
            tangage *= f;
            if (Math.abs(lacet) < 0.05F && Math.abs(tangage) < 0.05F) {
                lacet = 0;
                tangage = 0;
            }
        }
        if (lacet == 0 && tangage == 0) return;
        e.setYaw(e.getYaw() + lacet);
        e.setPitch(Mth.clamp(e.getPitch() + tangage, -90F, 90F));
    }

    private static boolean taczCherche;
    private static Method depuisJoueur, enVisee;

    /** Vrai si le joueur vise avec une arme TaCZ (API client de TaCZ, par réflexion ; faux sans TaCZ). */
    private static boolean viseTacz(LocalPlayer p) {
        if (!taczCherche) {
            taczCherche = true;
            try {
                Class<?> c = Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
                depuisJoueur = c.getMethod("fromLocalPlayer", LocalPlayer.class);
                enVisee = c.getMethod("isAim");
            } catch (ReflectiveOperationException | LinkageError ex) {
                depuisJoueur = null;
            }
        }
        if (depuisJoueur == null) return false;
        try {
            Object op = depuisJoueur.invoke(null, p);
            return op != null && Boolean.TRUE.equals(enVisee.invoke(op));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }
}
