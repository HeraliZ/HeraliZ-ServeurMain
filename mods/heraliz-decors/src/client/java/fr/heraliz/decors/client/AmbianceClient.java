package fr.heraliz.decors.client;

import com.mojang.blaze3d.shaders.FogShape;
import fr.heraliz.decors.HeralizDecors;
import fr.heraliz.decors.ambiance.Ambiance;
import fr.heraliz.decors.ambiance.Zone;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Vector3f;

/**
 * Ambiance côté client (1.4.0) : brouillard dynamique (fondu vers le profil annoncé par le serveur) et zones
 * dangereuses (brouillard local teinté vert pour le gaz, jaunâtre pour la radiation, blanc bleuté pour le froid ;
 * particules ; compteur Geiger ; voile coloré à l'écran quand le joueur est atteint ; message en entrant). Tout est
 * calculé une fois par image ou par tick : aucun coût visible.
 */
public final class AmbianceClient {
    private AmbianceClient() {}

    // brouillard global : profil visé, valeur courante (0 = clair, 1 = profil plein) pour le fondu
    private static Ambiance.Profil cible = Ambiance.Profil.CLAIR, depart = Ambiance.Profil.CLAIR;
    private static float fondu = 1f, duree = 0f;
    private static long dernierNano = 0;
    // zones et exposition
    private static List<Zone> zones = new ArrayList<>();
    private static float zGaz, zRad, zFroid;                    // présence (0..1) de chaque genre là où est le joueur
    private static float eGaz, eRad, eFroid;                    // exposition annoncée par le serveur (0..1)
    private static boolean pGaz, pRad, pFroid;
    private static float lGaz, lRad, lFroid;                    // valeurs lissées (fondu du brouillard local)
    private static String derniereZone = "";

    static void recevoir(Object m) {
        if (m instanceof Ambiance.Brume b) {
            Ambiance.Profil p = Ambiance.Profil.values()[Math.max(0, Math.min(Ambiance.Profil.values().length - 1, b.profil()))];
            depart = courant();
            cible = p;
            duree = b.transition();
            fondu = duree <= 0 ? 1f : 0f;
        } else if (m instanceof Ambiance.Zones z) {
            zones = new ArrayList<>(z.zones());
        } else if (m instanceof Ambiance.Exposition e) {
            eGaz = e.gaz();
            eRad = e.radiation();
            eFroid = e.froid();
            pGaz = e.pGaz();
            pRad = e.pRad();
            pFroid = e.pFroid();
        }
    }

    /** Profil « actuel » pour démarrer un fondu (le plus proche de l'état affiché). */
    private static Ambiance.Profil courant() {
        return fondu >= 0.5f ? cible : depart;
    }

    @Mod.EventBusSubscriber(modid = HeralizDecors.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Mod_ {
        private Mod_() {}

        @SubscribeEvent
        public static void init(FMLClientSetupEvent e) {
            Ambiance.RECEPTION_CLIENT = AmbianceClient::recevoir;
        }

        @SubscribeEvent
        public static void calques(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("contamination", (gui, g, partial, w, h) -> {
                float a = 0f;
                int r = 0, v = 0, b = 0;
                if (eGaz > 0.02f) {
                    a = Math.max(a, eGaz);
                    r = 70;
                    v = 140;
                    b = 30;
                }
                if (eRad > 0.02f && eRad >= a) {
                    a = eRad;
                    r = 150;
                    v = 160;
                    b = 20;
                }
                if (eFroid > 0.02f && eFroid >= a) {
                    a = eFroid;
                    r = 190;
                    v = 220;
                    b = 255;
                }
                if (a <= 0.02f) return;
                int al = (int) (Math.min(0.45f, a * 0.45f) * 255);
                int c = (al << 24) | (r << 16) | (v << 8) | b;
                int bord = Math.max(16, (int) (Math.min(w, h) * 0.18f));
                // voile sur les bords (vignette), plus léger au centre
                g.fillGradient(0, 0, w, bord, c, c & 0x00FFFFFF);
                g.fillGradient(0, h - bord, w, h, c & 0x00FFFFFF, c);
                g.fill(0, 0, bord / 2, h, (al / 2 << 24) | (r << 16) | (v << 8) | b);
                g.fill(w - bord / 2, 0, w, h, (al / 2 << 24) | (r << 16) | (v << 8) | b);
            });
        }
    }

    @Mod.EventBusSubscriber(modid = HeralizDecors.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Forge_ {
        private Forge_() {}

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            Player p = mc.player;
            if (p == null || mc.level == null || mc.isPaused()) return;
            // présence des zones autour du joueur
            float g = 0, ra = 0, fr = 0;
            String nom = "";
            // les zones sont celles de la carte : jamais dans un autre monde (bases, spawn, matchs) aux mêmes coordonnées
            boolean carte = mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD;
            for (Zone z : carte ? zones : List.<Zone>of()) {
                float n = z.niveau(p.getX(), p.getY(), p.getZ());
                if (n <= 0) continue;
                switch (z.genre()) {
                    case "gaz" -> g = Math.max(g, n);
                    case "radiation" -> ra = Math.max(ra, n);
                    case "froid" -> fr = Math.max(fr, n);
                    default -> { }
                }
                if (n > 0.45f) nom = z.genre();
            }
            zGaz = g;
            zRad = ra;
            zFroid = fr;
            if (!nom.equals(derniereZone)) {                              // on entre dans une zone : avertissement
                if (!nom.isEmpty()) {
                    mc.gui.setOverlayMessage(Component.translatable("msg.heraliz_decors.zone." + nom), false);
                    mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.AMBIENT, 0.6f, 0.5f, false);
                }
                derniereZone = nom;
            }
            var rnd = mc.level.random;
            // particules : spores vertes (gaz), poussières jaunes (radiation), flocons (froid)
            int n = (int) (g * 6);
            for (int i = 0; i < n; i++) {
                mc.level.addParticle(new DustParticleOptions(new Vector3f(0.45f, 0.62f, 0.18f), 1.4f),
                        p.getX() + (rnd.nextDouble() - 0.5) * 16, p.getY() + rnd.nextDouble() * 5 - 1, p.getZ() + (rnd.nextDouble() - 0.5) * 16,
                        0, 0.005, 0);
            }
            n = (int) (ra * 3);
            for (int i = 0; i < n; i++) {
                mc.level.addParticle(new DustParticleOptions(new Vector3f(0.75f, 0.8f, 0.2f), 0.8f),
                        p.getX() + (rnd.nextDouble() - 0.5) * 12, p.getY() + rnd.nextDouble() * 4, p.getZ() + (rnd.nextDouble() - 0.5) * 12,
                        0, 0.01, 0);
            }
            n = (int) (fr * 5);
            for (int i = 0; i < n; i++) {
                mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                        p.getX() + (rnd.nextDouble() - 0.5) * 18, p.getY() + 3 + rnd.nextDouble() * 4, p.getZ() + (rnd.nextDouble() - 0.5) * 18,
                        (rnd.nextDouble() - 0.5) * 0.05, -0.05, (rnd.nextDouble() - 0.5) * 0.05);
            }
            // compteur Geiger : clics d'autant plus serrés que la radiation est forte (sauf sous Hazmat : rares)
            if (ra > 0 && rnd.nextFloat() < (pRad ? 0.08f : 0.15f + ra * 0.7f)) {
                mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.AMBIENT,
                        0.25f + ra * 0.35f, 1.6f + rnd.nextFloat() * 0.4f, false);
            }
            // gaz : sifflement de fuite, de temps en temps
            if (g > 0.3f && rnd.nextFloat() < 0.01f) {
                mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.AMBIENT, 0.15f, 0.6f, false);
            }
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public static void brouillard(ViewportEvent.RenderFog e) {
            if (e.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;
            if (mc.player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER) || mc.player.isEyeInFluid(net.minecraft.tags.FluidTags.LAVA)) return;
            // fondu du profil global (temps réel)
            long t = System.nanoTime();
            float dt = dernierNano == 0 ? 0f : Math.min(0.25f, (t - dernierNano) / 1e9f);
            dernierNano = t;
            if (fondu < 1f) fondu = duree <= 0 ? 1f : Math.min(1f, fondu + dt / duree);
            float k = fondu * fondu * (3 - 2 * fondu);
            float rd = e.getFarPlaneDistance();
            float[] a = valeurs(depart, rd), b = valeurs(cible, rd);
            float debut = a[0] + (b[0] - a[0]) * k, fin = a[1] + (b[1] - a[1]) * k;
            // brouillard local des zones (lissé : on le voit monter en entrant)
            lGaz += (zGaz - lGaz) * Math.min(1f, dt * 0.8f);
            lRad += (zRad - lRad) * Math.min(1f, dt * 0.8f);
            lFroid += (zFroid - lFroid) * Math.min(1f, dt * 0.8f);
            float local = Math.max(lGaz, Math.max(lRad * 0.7f, lFroid * 0.8f));
            if (local > 0.01f) {
                float fl = 24 + (1 - local) * 120, dl = 0;
                fin = Math.min(fin, fl + (rd - fl) * (1 - local));
                debut = Math.min(debut, dl + (rd * 0.5f) * (1 - local));
            }
            if (fin >= rd - 0.5f && debut >= rd - 0.5f) return;              // clair : le brouillard de Minecraft seul
            e.setNearPlaneDistance(Math.max(0f, Math.min(debut, fin - 1)));
            e.setFarPlaneDistance(Math.max(2f, Math.min(rd, fin)));
            e.setFogShape(FogShape.SPHERE);
            e.setCanceled(true);
        }

        /** {début, fin} d'un profil pour une distance d'affichage rd (en blocs). */
        private static float[] valeurs(Ambiance.Profil p, float rd) {
            if (p == Ambiance.Profil.CLAIR) return new float[]{rd, rd};
            return new float[]{Math.min(p.debut * rd, p.debutMax), Math.min(p.fin * rd, p.finMax)};
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public static void couleur(ViewportEvent.ComputeFogColor e) {
            float local = Math.max(lGaz, Math.max(lRad, lFroid));
            float brume = cible == Ambiance.Profil.CLAIR ? 0 : fondu * (cible.ordinal() / 4f);
            float r = e.getRed(), g = e.getGreen(), b = e.getBlue();
            if (brume > 0) {                                                // brouillard : gris doux
                float gris = (r + g + b) / 3f;
                r += (gris * 0.95f + 0.04f - r) * brume * 0.6f;
                g += (gris * 0.97f + 0.04f - g) * brume * 0.6f;
                b += (gris + 0.05f - b) * brume * 0.6f;
            }
            if (local > 0.01f) {
                float tr, tg, tb;
                if (lGaz >= lRad && lGaz >= lFroid) {
                    tr = 0.38f;
                    tg = 0.48f;
                    tb = 0.2f;
                } else if (lRad >= lFroid) {
                    tr = 0.55f;
                    tg = 0.56f;
                    tb = 0.25f;
                } else {
                    tr = 0.82f;
                    tg = 0.88f;
                    tb = 0.95f;
                }
                float m = Math.min(0.85f, local);
                r += (tr - r) * m;
                g += (tg - g) * m;
                b += (tb - b) * m;
            }
            e.setRed(r);
            e.setGreen(g);
            e.setBlue(b);
        }

        @SubscribeEvent
        public static void deconnexion(ClientPlayerNetworkEvent.LoggingOut e) {
            zones = new ArrayList<>();
            cible = depart = Ambiance.Profil.CLAIR;
            fondu = 1f;
            eGaz = eRad = eFroid = 0;
            lGaz = lRad = lFroid = 0;
            derniereZone = "";
        }
    }
}
