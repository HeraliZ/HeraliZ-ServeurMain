package fr.heraliz.decors.client;

import com.mojang.blaze3d.shaders.FogShape;
import fr.heraliz.decors.HeralizDecors;
import fr.heraliz.decors.ambiance.Ambiance;
import fr.heraliz.decors.ambiance.Zone;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
    /**
     * Compteur Geiger des zones irradiées (14/11). Avant (11/11) : le clic du comparateur de Minecraft (« des tics de
     * répéteur en redstone »). Le propriétaire a choisi parmi trois sons « le 1, son ArkaniaZ » : le compteur Geiger du
     * contenu ArkaniaZ, fichier {@code geckolib:geiger} du jar geckolib222 (d'un autre auteur, livré tel quel à tous les
     * joueurs). Il n'est pas copié : notre son {@code heraliz_decors:zone.geiger} ({@code sounds.json}) ne fait que le
     * désigner. Joué en boucle (55 s, de 11 à 25 crépitements par seconde) tant que le joueur est dans une zone irradiée,
     * avec un fondu à l'entrée (1,3 s) et à la sortie (1 s). Plus la radiation est forte, plus il est fort (volume 0,3 à
     * 0,8) et rapide (hauteur 0,85 à 1,25 : les crépitements se resserrent) ; sous Hazmat : volume × 0,4, hauteur 0,8.
     * Catégorie « Ambiance / environnement » (curseur du joueur). Sans le fichier (jeu de test sans geckolib222) : rien.
     */
    private static final SoundEvent GEIGER = SoundEvent.createVariableRangeEvent(new ResourceLocation(HeralizDecors.MODID, "zone.geiger"));
    private static final ResourceLocation GEIGER_FICHIER = new ResourceLocation("geckolib", "sounds/geiger.ogg");
    private static final float GEIGER_VOLUME_MIN = 0.3f, GEIGER_VOLUME_MAX = 0.8f, GEIGER_HAZMAT = 0.4f;
    private static final float GEIGER_HAUTEUR_MIN = 0.85f, GEIGER_HAUTEUR_MAX = 1.25f, GEIGER_HAUTEUR_HAZMAT = 0.8f;
    /** Crépitement en cours (null hors d'une zone irradiée). */
    private static Geiger geiger;
    /** Le fichier du son est-il chez ce joueur ? (null : pas encore regardé) */
    private static Boolean geigerDispo;
    /** Ticks avant de relancer un crépitement coupé par le jeu (changement de monde, volume général à 0...). */
    private static int geigerReprise;

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
            // compteur Geiger : en boucle dans une zone irradiée, plus fort et plus rapide quand la radiation monte
            compteurGeiger(mc, ra);
            // gaz : sifflement de fuite, de temps en temps
            if (g > 0.3f && rnd.nextFloat() < 0.01f) {
                mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.AMBIENT, 0.15f, 0.6f, false);
            }
        }

        private static void compteurGeiger(Minecraft mc, float ra) {
            if (geiger != null && (geiger.isStopped() || !mc.getSoundManager().isActive(geiger))) {
                if (!geiger.isStopped()) {
                    geigerReprise = 100;                                  // coupé par le jeu : nouvel essai dans 5 s
                }
                geiger = null;
            }
            if (ra <= 0f) {
                if (geiger != null) {
                    geiger.viser(0f, geiger.hauteurVisee);               // fondu de sortie, puis le son s'arrête
                }
                return;
            }
            float volume = GEIGER_VOLUME_MIN + (GEIGER_VOLUME_MAX - GEIGER_VOLUME_MIN) * ra;
            float hauteur = GEIGER_HAUTEUR_MIN + (GEIGER_HAUTEUR_MAX - GEIGER_HAUTEUR_MIN) * ra;
            if (pRad) {
                volume *= GEIGER_HAZMAT;
                hauteur = GEIGER_HAUTEUR_HAZMAT;
            }
            if (geiger == null) {
                if (geigerReprise > 0) {
                    geigerReprise--;
                    return;
                }
                if (geigerDispo == null) {
                    geigerDispo = mc.getResourceManager().getResource(GEIGER_FICHIER).isPresent();
                }
                if (!geigerDispo) {
                    return;
                }
                geiger = new Geiger(hauteur);
                mc.getSoundManager().play(geiger);
            }
            geiger.viser(volume, hauteur);
        }

        /**
         * Le brouillard tiré au hasard par le serveur est celui de la carte : il ne s'affiche que sur la carte (15/11,
         * audit monde M1 : il suivait aussi les joueurs dans les arènes du ClashWar et le monde du tutoriel des bases).
         * Ailleurs (spawn, arènes, tutoriel, bunker...), seul le brouillard de Minecraft (ou celui du monde) reste.
         */
        private static boolean surCarte(Minecraft mc) {
            return mc.level != null && mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD;
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public static void brouillard(ViewportEvent.RenderFog e) {
            if (e.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;
            // fondu du profil global (temps réel) et brouillard local des zones (lissé : on le voit monter en entrant) :
            // tenus à jour dans tous les mondes, pour retrouver la carte telle que les autres la voient en y revenant
            long t = System.nanoTime();
            float dt = dernierNano == 0 ? 0f : Math.min(0.25f, (t - dernierNano) / 1e9f);
            dernierNano = t;
            if (fondu < 1f) fondu = duree <= 0 ? 1f : Math.min(1f, fondu + dt / duree);
            lGaz += (zGaz - lGaz) * Math.min(1f, dt * 0.8f);
            lRad += (zRad - lRad) * Math.min(1f, dt * 0.8f);
            lFroid += (zFroid - lFroid) * Math.min(1f, dt * 0.8f);
            if (!surCarte(mc)) return;
            if (mc.player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER) || mc.player.isEyeInFluid(net.minecraft.tags.FluidTags.LAVA)) return;
            float k = fondu * fondu * (3 - 2 * fondu);
            float rd = e.getFarPlaneDistance();
            // début et fin du brouillard entre le profil de départ et le profil visé (sans tableau créé à chaque image)
            float debut = debutDe(depart, rd) + (debutDe(cible, rd) - debutDe(depart, rd)) * k;
            float fin = finDe(depart, rd) + (finDe(cible, rd) - finDe(depart, rd)) * k;
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

        /** Début du brouillard d'un profil pour une distance d'affichage rd (en blocs). */
        private static float debutDe(Ambiance.Profil p, float rd) {
            return p == Ambiance.Profil.CLAIR ? rd : Math.min(p.debut * rd, p.debutMax);
        }

        /** Fin du brouillard (tout est caché au-delà) d'un profil pour une distance d'affichage rd (en blocs). */
        private static float finDe(Ambiance.Profil p, float rd) {
            return p == Ambiance.Profil.CLAIR ? rd : Math.min(p.fin * rd, p.finMax);
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public static void couleur(ViewportEvent.ComputeFogColor e) {
            // teinte de la brume et des zones : sur la carte seulement (au spawn, elle changeait la couleur de l'horizon)
            if (!surCarte(Minecraft.getInstance())) return;
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
            if (geiger != null) {
                Minecraft.getInstance().getSoundManager().stop(geiger);
                geiger = null;
            }
            geigerReprise = 0;
        }
    }

    /** Crépitement du compteur, en boucle : volume et hauteur suivent la radiation en douceur ; fondu de sortie. */
    private static final class Geiger extends AbstractTickableSoundInstance {
        private float volumeVise, hauteurVisee;

        Geiger(float hauteur) {
            super(GEIGER, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.relative = true;                                         // sur le joueur, sans atténuation
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.x = 0;
            this.y = 0;
            this.z = 0;
            this.volume = 0.01f;                                          // fondu d'entrée
            this.pitch = hauteur;
            this.hauteurVisee = hauteur;
        }

        void viser(float volume, float hauteur) {
            this.volumeVise = volume;
            this.hauteurVisee = hauteur;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            this.volume += Mth.clamp(this.volumeVise - this.volume, -0.04f, 0.03f);
            this.pitch += Mth.clamp(this.hauteurVisee - this.pitch, -0.02f, 0.02f);
            if (this.volumeVise <= 0f && this.volume <= 0.02f) {
                stop();
            }
        }
    }
}
