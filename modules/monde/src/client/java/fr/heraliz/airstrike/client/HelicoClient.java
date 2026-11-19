package fr.heraliz.airstrike.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.airstrike.client.fx.Flares;
import fr.heraliz.airstrike.client.fx.FxQuality;
import fr.heraliz.airstrike.client.model.GeoModel;
import fr.heraliz.airstrike.client.model.GeoPart;
import fr.heraliz.airstrike.client.model.ModModels;
import fr.heraliz.airstrike.client.render.HelicopterRenderer;
import fr.heraliz.airstrike.client.render.ModRenderTypes;
import fr.heraliz.airstrike.client.sound.HelicoSound;
import fr.heraliz.airstrike.entity.Helicopter;
import fr.heraliz.airstrike.entity.HelicopterWreck;
import fr.heraliz.airstrike.network.HelicoPositionPacket;
import fr.heraliz.airstrike.network.HelicoRoquettePacket;
import fr.heraliz.airstrike.network.HelicoTirPacket;
import fr.heraliz.airstrike.particle.SmokeParticleOptions;
import fr.heraliz.airstrike.registry.ModParticles;
import fr.heraliz.airstrike.registry.ModSounds;
import fr.heraliz.airstrike.strike.FlightPath;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * L'hélicoptère chez le joueur, hors du modèle lui-même ({@code HelicopterRenderer}) : les sons en boucle (rotor proche,
 * rotor lointain, alarme), la fumée et le feu des moteurs selon la santé, la traînée de la chute, les traçantes et
 * étincelles du minigun, les roquettes (19/11), les flammes et la fumée de l'épave. Un hélicoptère est suivi par son entité
 * quand le serveur l'envoie (64 blocs, spigot.yml), sinon par la position reçue 4 fois par seconde
 * ({@link HelicoPositionPacket}), prolongée et lissée entre deux paquets : on l'entend avant de le voir.
 * <p>19/11 (demande du propriétaire : « à 60-70 blocs je ne le vois plus, il faudrait le voir à 200-300 ») : sans son entité,
 * le modèle est dessiné à cette position jusqu'à {@link #LOIN_MAX} blocs ({@link #rendreLointains}), sans le brouillard de la
 * distance de vue (sauf brouillard de la météo) ; dès que le serveur envoie l'entité, c'est elle qu'on voit, au même endroit
 * (même position, même cap) : pas de saut. Le dessin lointain n'alloue rien de plus que celui de l'entité (listes
 * parcourues par leur rang, rotations et points réutilisés). Sans hélicoptère ni roquette, {@link #tick()} et
 * {@link #rendre} sortent tout de suite.</p>
 */
public final class HelicoClient {
    private HelicoClient() {
    }

    /** Un hélicoptère connu de ce jeu. */
    public static final class Suivi {
        final int id;
        /** Position affichée (son, modèle lointain) ; celle du tick d'avant (lissage à l'image). */
        public double x, y, z;
        double xo, yo, zo;
        /** Vitesse (blocs par tick). */
        double vx, vy, vz;
        /** Position du serveur prolongée depuis le dernier paquet : la position affichée la rejoint en douceur. */
        double sx, sy, sz;
        /** Cap, tangage et roulis affichés (degrés), au tick d'avant, et visés (dernier paquet, cap prolongé). */
        float lacet, lacetO, lacetS, vLacet, tangage, tangageO, tangageS, roulis, roulisO, roulisS;
        /** Cap du dernier paquet et ticks depuis (vitesse de virage). */
        float lacetPaquet;
        int depuisPaquet;
        /** Angles des pales et du rotor de queue (radians), et au tick d'avant. */
        float pales, palesO, queue, queueO;
        public float rotor = 1f, chute, sante = 1f;
        byte phase;
        int sansNouvelles;
        /** A une position (entité ou paquet) : le modèle lointain peut être dessiné. */
        boolean place;
        /** Vu par son entité ce tick ; dernières nouvelles venues de l'entité (pas d'un paquet). */
        boolean vuEntite, parEntite;
        boolean fini;
        /** Tick client du dernier coup de chaque minigun. */
        long tirG = -1000, tirD = -1000;
        @Nullable
        HelicoSound pres, loin, alarme;

        Suivi(int id) {
            this.id = id;
        }

        public boolean fini() {
            return fini;
        }

        /** En feu : l'alarme de la cabine sonne (en vol comme en chute). */
        public boolean alarme() {
            return sante < 0.25f;
        }
    }

    /** Une traçante : trajet du coup, âge et durée de vie (ticks). */
    private static final class Tracante {
        final double x0, y0, z0, dx, dy, dz, longueur;
        final float vie;
        int age;

        Tracante(HelicoTirPacket p) {
            x0 = p.x0;
            y0 = p.y0;
            z0 = p.z0;
            double ex = p.x1 - p.x0, ey = p.y1 - p.y0, ez = p.z1 - p.z0;
            longueur = Math.max(1e-3, Math.sqrt(ex * ex + ey * ey + ez * ez));
            dx = ex / longueur;
            dy = ey / longueur;
            dz = ez / longueur;
            // une balle de minigun fait ~40 blocs par tick
            vie = (float) Mth.clamp(longueur / 40.0, 1.0, 4.0);
        }
    }

    /** Une roquette en vol (19/11) : position, au tick d'avant, vitesse (blocs par tick), direction et cap du modèle. */
    private static final class Roquette {
        final int id;
        double x, y, z, xo, yo, zo;
        final double vx, vy, vz, ux, uy, uz;
        final float lacet, tangage;
        int age;

        Roquette(HelicoRoquettePacket p) {
            id = p.id;
            x = xo = p.x;
            y = yo = p.y;
            z = zo = p.z;
            vx = p.vx;
            vy = p.vy;
            vz = p.vz;
            double v = Math.max(1e-6, Math.sqrt(vx * vx + vy * vy + vz * vz));
            ux = vx / v;
            uy = vy / v;
            uz = vz / v;
            lacet = (float) FlightPath.yawOf(vx, vz);
            tangage = (float) Math.toDegrees(Math.atan2(-vy, Math.max(1e-4, Math.sqrt(vx * vx + vz * vz))));
        }
    }

    private static final Int2ObjectOpenHashMap<Suivi> SUIVIS = new Int2ObjectOpenHashMap<>();
    /** Les mêmes, dans une liste : parcourus sans rien allouer (rendu à chaque image). */
    private static final List<Suivi> LISTE = new ArrayList<>();
    private static final List<Tracante> TRACANTES = new ArrayList<>();
    private static final List<Roquette> ROQUETTES = new ArrayList<>();
    private static final RandomSource R = RandomSource.create();
    /** Fumée et flammes au-delà de cette distance : rien (le modèle est déjà petit). */
    private static final double PARTICULES_MAX = 200;
    /** Modèle sans entité jusqu'à cette distance (blocs) : celle des paquets de position ({@code HelicopterServeur.PORTEE_SON}). */
    private static final double LOIN_MAX = 320;
    /** Part de l'écart rattrapée chaque tick entre la position affichée et celle du serveur prolongée. */
    private static final float RAPPEL = 0.25f;
    /** Lumière du modèle lointain et des roquettes : ciel plein (assombri la nuit), comme les avions des frappes. */
    private static final int LUMIERE_CIEL = LightTexture.pack(0, 15);
    private static final ResourceLocation TEX_MUNITIONS = ModModels.texture("ordnance"), TEX_FLAMME = ModModels.texture("designator_display");
    /** Roquette : le missile du modèle des munitions (1,9 bloc) à cette taille ; hauteur de son corps dans le modèle (blocs). */
    private static final float ROQUETTE_ECHELLE = 0.6f, ROQUETTE_AXE = 6.3f / 16f;
    /** Vie d'une roquette chez le joueur s'il n'apprend pas sa fin (ticks ; 100 au serveur). */
    private static final int ROQUETTE_VIE = 110;
    private static final Quaternionf ROT = new Quaternionf();
    private static final Vector3f CENTRE = new Vector3f();
    private static long ticks;

    // ------------------------------------------------------------------ suivi
    private static Suivi suivi(int id) {
        Suivi s = SUIVIS.get(id);
        if (s == null) {
            s = new Suivi(id);
            SUIVIS.put(id, s);
            LISTE.add(s);
        }
        return s;
    }

    private static void sons(Suivi s) {
        if (s.pres != null) return;
        Minecraft mc = Minecraft.getInstance();
        s.pres = new HelicoSound(s, HelicoSound.Couche.PRES, ModSounds.HELICO_ROTOR.get());
        s.loin = new HelicoSound(s, HelicoSound.Couche.LOIN, ModSounds.HELICO_ROTOR_LOIN.get());
        s.alarme = new HelicoSound(s, HelicoSound.Couche.ALARME, ModSounds.HELICO_ALARME.get());
        mc.getSoundManager().play(s.pres);
        mc.getSoundManager().play(s.loin);
        mc.getSoundManager().play(s.alarme);
    }

    /**
     * Chaque tick, pour chaque hélicoptère que le serveur envoie (Helicopter.TICK_CLIENT) : tout est repris de l'entité
     * (position, cap, assiette, pales), pour que le modèle lointain reparte d'ici sans saut si l'entité s'en va.
     */
    public static void entite(Helicopter h) {
        Suivi s = suivi(h.getId());
        s.xo = h.xo;
        s.yo = h.yo;
        s.zo = h.zo;
        s.x = s.sx = h.getX();
        s.y = s.sy = h.getY();
        s.z = s.sz = h.getZ();
        s.vx = h.getX() - h.xo;
        s.vy = h.getY() - h.yo;
        s.vz = h.getZ() - h.zo;
        s.lacetO = h.yRotO;
        s.lacet = s.lacetS = s.lacetPaquet = h.getYRot();
        s.vLacet = Mth.wrapDegrees(h.getYRot() - h.yRotO);
        s.depuisPaquet = 0;
        s.tangageO = h.tangage(0f);
        s.tangage = s.tangageS = h.tangage(1f);
        s.roulisO = h.roulis(0f);
        s.roulis = s.roulisS = h.roulis(1f);
        s.palesO = h.pales(0f);
        s.pales = h.pales(1f);
        s.queueO = h.rotorQueue(0f);
        s.queue = h.rotorQueue(1f);
        s.rotor = h.rotor();
        s.sante = h.sante();
        s.phase = h.phase();
        float c = h.chute(0f);
        s.chute = Math.max(0f, c);
        s.place = true;
        s.vuEntite = true;
        s.parEntite = true;
        s.sansNouvelles = 0;
        sons(s);
        particules(h, s);
    }

    /**
     * Position reçue (4 fois par seconde) : seulement pour un hélicoptère que le serveur n'envoie pas (trop loin). La
     * première fois, il apparaît là ; ensuite, la position du serveur est prolongée à sa vitesse et la position affichée la
     * rejoint en douceur ({@link #avancer}) ; le cap tourne à la vitesse des deux derniers paquets.
     */
    public static void position(HelicoPositionPacket p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level.getEntity(p.entite) instanceof Helicopter) return;
        Suivi s = suivi(p.entite);
        if (!s.place) {
            s.x = s.xo = s.sx = p.x;
            s.y = s.yo = s.sy = p.y;
            s.z = s.zo = s.sz = p.z;
            s.lacet = s.lacetO = s.lacetS = p.lacet;
            s.vLacet = 0f;
            s.tangage = s.tangageO = p.tangage;
            s.roulis = s.roulisO = p.roulis;
            s.place = true;
        } else {
            s.vLacet = Mth.clamp(Mth.wrapDegrees(p.lacet - s.lacetPaquet) / Math.max(1, s.depuisPaquet), -8f, 8f);
            s.sx = p.x;
            s.sy = p.y;
            s.sz = p.z;
            s.lacetS = p.lacet;
        }
        s.lacetPaquet = p.lacet;
        s.depuisPaquet = 0;
        s.vx = p.vx;
        s.vy = p.vy;
        s.vz = p.vz;
        s.tangageS = p.tangage;
        s.roulisS = p.roulis;
        s.rotor = p.rotor;
        s.sante = p.sante;
        s.phase = p.phase;
        // en chute, vue de loin : avancement d'après le rotor, qui ralentit de 1 à 0,45 pendant la chute (son plus grave) ;
        // le modèle tombe jusqu'au sol, puis la marque « retiré » de l'impact l'efface (HelicoCarte)
        s.chute = p.phase == Helicopter.CHUTE ? Mth.clamp((1f - p.rotor) / 0.55f, 0f, 1f) : 0f;
        s.parEntite = false;
        s.sansNouvelles = 0;
        sons(s);
    }

    /**
     * Fin de tick client : prolonge les hélicoptères sans entité, oublie ceux dont on n'a plus de nouvelles, vieillit les
     * traçantes, fait voler les roquettes.
     */
    public static void tick() {
        if (LISTE.isEmpty() && TRACANTES.isEmpty() && ROQUETTES.isEmpty()) return;
        ticks++;
        for (int i = LISTE.size() - 1; i >= 0; i--) {
            Suivi s = LISTE.get(i);
            if (s.vuEntite) {
                s.vuEntite = false;
                continue;
            }
            // écrasé (fin de la chute vue de près) : le rotor se tait tout de suite ; plus de nouvelles : oublié
            if (s.phase == Helicopter.CHUTE && s.chute > 0.9f && s.parEntite || ++s.sansNouvelles > 50) {
                s.fini = true;
                LISTE.remove(i);
                SUIVIS.remove(s.id);
                continue;
            }
            avancer(s);
        }
        if (!TRACANTES.isEmpty()) TRACANTES.removeIf(t -> ++t.age > t.vie + 1);
        if (!ROQUETTES.isEmpty()) roquettes();
    }

    /** Un tick sans entité : position et cap prolongés, rappelés vers ceux du serveur ; assiette lissée ; pales. */
    private static void avancer(Suivi s) {
        s.xo = s.x;
        s.yo = s.y;
        s.zo = s.z;
        s.lacetO = s.lacet;
        s.tangageO = s.tangage;
        s.roulisO = s.roulis;
        s.sx += s.vx;
        s.sy += s.vy;
        s.sz += s.vz;
        s.x += s.vx;
        s.y += s.vy;
        s.z += s.vz;
        s.x += (s.sx - s.x) * RAPPEL;
        s.y += (s.sy - s.y) * RAPPEL;
        s.z += (s.sz - s.z) * RAPPEL;
        s.lacetS += s.vLacet;
        s.lacet = Mth.wrapDegrees(s.lacet + s.vLacet + Mth.wrapDegrees(s.lacetS - s.lacet) * RAPPEL);
        s.tangage += (s.tangageS - s.tangage) * 0.2f;
        s.roulis += (s.roulisS - s.roulis) * 0.2f;
        s.depuisPaquet++;
        // pales : comme l'entité (Helicopter.tickClient)
        float v = s.rotor * Helicopter.VITESSE_PALES;
        s.palesO = s.pales;
        s.queueO = s.queue;
        s.pales = (s.pales + v) % (Mth.TWO_PI * 64);
        s.queue = (s.queue + v * 1.7f) % (Mth.TWO_PI * 64);
        if (s.palesO > s.pales) s.palesO -= Mth.TWO_PI * 64;
        if (s.queueO > s.queue) s.queueO -= Mth.TWO_PI * 64;
    }

    /** Ticks depuis le dernier coup de ce minigun (grand s'il n'a pas tiré). */
    public static long depuisTir(int id, boolean droite) {
        Suivi s = SUIVIS.get(id);
        return s == null ? 1000 : ticks - (droite ? s.tirD : s.tirG);
    }

    /** Hélicoptère retiré (carte M : impact, départ, annulation) : ses sons et son modèle lointain s'arrêtent tout de suite. */
    public static void retire(int id) {
        Suivi s = SUIVIS.remove(id);
        if (s != null) {
            s.fini = true;
            LISTE.remove(s);
        }
    }

    public static void vider() {
        for (Suivi s : LISTE) s.fini = true;
        SUIVIS.clear();
        LISTE.clear();
        TRACANTES.clear();
        ROQUETTES.clear();
    }

    // ------------------------------------------------------------------ tirs
    public static void tir(HelicoTirPacket p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Suivi s = SUIVIS.get(p.entite);
        if (s != null) {
            if (p.droite) s.tirD = ticks;
            else s.tirG = ticks;
        }
        if (TRACANTES.size() < 64) TRACANTES.add(new Tracante(p));
        if (p.impact == HelicoTirPacket.AIR) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double d = Math.sqrt(cam.distanceToSqr(p.x1, p.y1, p.z1));
        if (d > 140) return;
        float q = FxQuality.at(d);
        if (p.impact == HelicoTirPacket.BLOC) {
            for (int i = 0, n = FxQuality.count(4, q, 1); i < n; i++) {
                level.addParticle(ModParticles.SPARK.get(), p.x1, p.y1 + 0.1, p.z1, R.nextGaussian() * 0.3, 0.25 + R.nextFloat() * 0.3, R.nextGaussian() * 0.3);
            }
            level.addParticle(SmokeParticleOptions.of(0x7A6E5E, 0.9f + R.nextFloat() * 0.6f, 28, 0.04f, 0.75f), p.x1, p.y1 + 0.2, p.z1,
                    R.nextGaussian() * 0.04, 0.06, R.nextGaussian() * 0.04);
            if (d < 40 && R.nextInt(2) == 0) {
                mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.BULLET_IMPACT.get().getLocation(), SoundSource.HOSTILE, 0.8f,
                        0.9f + R.nextFloat() * 0.4f, R, false, 0, SoundInstance.Attenuation.LINEAR, p.x1, p.y1, p.z1, false));
            }
        }
    }

    // ------------------------------------------------------------------ roquettes (19/11)
    /** Départ d'une roquette (elle vole en ligne droite chez le joueur) ou sa fin (le jeu cesse de la dessiner). */
    public static void roquette(HelicoRoquettePacket p) {
        if (p.type == HelicoRoquettePacket.LANCEE) {
            if (ROQUETTES.size() < 48) ROQUETTES.add(new Roquette(p));
            return;
        }
        for (int i = 0; i < ROQUETTES.size(); i++) {
            if (ROQUETTES.get(i).id == p.id) {
                ROQUETTES.remove(i);
                return;
            }
        }
    }

    /** Chaque tick : les roquettes avancent (arrêtées au premier bloc, l'explosion vient du serveur), traînée de fumée. */
    private static void roquettes() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            ROQUETTES.clear();
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (int i = ROQUETTES.size() - 1; i >= 0; i--) {
            Roquette r = ROQUETTES.get(i);
            r.xo = r.x;
            r.yo = r.y;
            r.zo = r.z;
            Vec3 de = new Vec3(r.x, r.y, r.z), vers = new Vec3(r.x + r.vx, r.y + r.vy, r.z + r.vz);
            if (++r.age > ROQUETTE_VIE || level.clip(new ClipContext(de, vers, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, mc.player))
                    .getType() != HitResult.Type.MISS) {
                ROQUETTES.remove(i);
                continue;
            }
            r.x = vers.x;
            r.y = vers.y;
            r.z = vers.z;
            double d = Math.sqrt(cam.distanceToSqr(r.x, r.y, r.z));
            if (d > 160) continue;
            float q = FxQuality.at(d);
            // traînée de fumée claire, en deux bouffées par tick (une roquette fait 2 blocs par tick)
            for (int k = 0, n = FxQuality.count(2, q, 1); k < n; k++) {
                double f = (k + R.nextFloat()) / n;
                level.addParticle(SmokeParticleOptions.of(0xBDB8B0, (0.7f + R.nextFloat() * 0.5f) * FxQuality.grow(q), 30 + R.nextInt(25), 0.01f, 0.7f),
                        r.x - r.vx * f, r.y - r.vy * f, r.z - r.vz * f, R.nextGaussian() * 0.01, 0.01, R.nextGaussian() * 0.01);
            }
            if (d < 64 && R.nextFloat() < q) level.addParticle(ParticleTypes.FLAME, r.x - r.ux * 0.6, r.y - r.uy * 0.6, r.z - r.uz * 0.6, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ rendu
    /**
     * Après les entités (RenderLevelStageEvent) : hélicoptères lointains, roquettes (missile, flamme), traçantes et
     * traînées lumineuses. Rien sans hélicoptère, roquette ni traçante.
     */
    public static void rendre(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (LISTE.isEmpty() && TRACANTES.isEmpty() && ROQUETTES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        float pt = e.getPartialTick();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        if (!LISTE.isEmpty()) rendreLointains(e.getCamera(), mc, level, cam, ps, pt, buffers);
        if (!ROQUETTES.isEmpty()) rendreRoquettes(e.getCamera(), cam, ps, pt, buffers);
        if (TRACANTES.isEmpty() && ROQUETTES.isEmpty()) return;
        VertexConsumer vc = buffers.getBuffer(ModRenderTypes.GLOW);
        Matrix4f mat = ps.last().pose();
        for (int i = 0; i < TRACANTES.size(); i++) {
            Tracante t = TRACANTES.get(i);
            float p = (t.age + pt) / t.vie;
            if (p > 1f) continue;
            double tete = p * t.longueur, queue = Math.max(0, tete - 7);
            double hx = t.x0 + t.dx * tete - cam.x, hy = t.y0 + t.dy * tete - cam.y, hz = t.z0 + t.dz * tete - cam.z;
            double qx = t.x0 + t.dx * queue - cam.x, qy = t.y0 + t.dy * queue - cam.y, qz = t.z0 + t.dz * queue - cam.z;
            ruban(vc, mat, qx, qy, qz, hx, hy, hz, 0.06f, 1f, 0.86f, 0.5f, 0.95f);
            ruban(vc, mat, qx, qy, qz, hx, hy, hz, 0.2f, 1f, 0.5f, 0.12f, 0.35f);
        }
        // roquettes : flamme qui file derrière elles
        for (int i = 0; i < ROQUETTES.size(); i++) {
            Roquette r = ROQUETTES.get(i);
            double bx = Mth.lerp(pt, r.xo, r.x) - r.ux * 0.6 - cam.x, by = Mth.lerp(pt, r.yo, r.y) - r.uy * 0.6 - cam.y,
                    bz = Mth.lerp(pt, r.zo, r.z) - r.uz * 0.6 - cam.z;
            ruban(vc, mat, bx - r.ux * 3.5, by - r.uy * 3.5, bz - r.uz * 3.5, bx, by, bz, 0.16f, 1f, 0.62f, 0.2f, 0.85f);
        }
        buffers.endBatch(ModRenderTypes.GLOW);
    }

    /**
     * Hélicoptères sans entité chez ce joueur (au-delà de 64 blocs) : leur modèle à la position lissée, jusqu'à
     * {@link #LOIN_MAX} blocs. Le brouillard de la distance de vue (6 tronçons sur le serveur, ~96 blocs) l'effacerait :
     * pendant son dessin, le brouillard commence au-delà de {@link #LOIN_MAX} (réduit d'autant si la météo en met un plus
     * proche), puis il est remis tel quel. Au-delà de la portée de la caméra, il est dessiné plus près et plus petit, dans
     * la même direction (même taille à l'écran). Derrière la caméra : rien.
     */
    private static void rendreLointains(Camera camera, Minecraft mc, ClientLevel level, Vec3 cam, PoseStack ps, float pt,
                                        MultiBufferSource.BufferSource buffers) {
        GeoModel m = null;
        float debut = 0f, fin = 0f;
        Vector3f regard = camera.getLookVector();
        float vue = mc.options.getEffectiveRenderDistance() * 16f;
        // plan lointain de la caméra de Minecraft : 4 fois la distance de vue
        double limite = Math.max(48.0, vue * 4.0 * 0.8);
        for (int i = 0; i < LISTE.size(); i++) {
            Suivi s = LISTE.get(i);
            // l'entité est là : c'est elle qu'on voit ; chute vue de près : l'entité vient de s'écraser, rien de plus
            if (s.fini || !s.place || level.getEntity(s.id) instanceof Helicopter || s.parEntite && s.phase == Helicopter.CHUTE) continue;
            double x = Mth.lerp(pt, s.xo, s.x) - cam.x, y = Mth.lerp(pt, s.yo, s.y) - cam.y, z = Mth.lerp(pt, s.zo, s.z) - cam.z;
            double d2 = x * x + y * y + z * z;
            if (d2 > LOIN_MAX * LOIN_MAX || d2 < 1) continue;
            double d = Math.sqrt(d2);
            if (x * regard.x() + y * regard.y() + z * regard.z() < -0.35 * d - 12) continue;
            if (m == null) {
                m = ModModels.helicopter();
                if (m.topLevel.isEmpty()) return;
                buffers.endLastBatch();   // ce qui attend encore garde le brouillard normal
                debut = RenderSystem.getShaderFogStart();
                fin = RenderSystem.getShaderFogEnd();
                float k = vue > 1f ? Mth.clamp(fin / vue, 0.15f, 1f) : 1f;   // < 1 : brouillard de la météo, plus proche
                RenderSystem.setShaderFogStart((float) LOIN_MAX * 1.1f * k);
                RenderSystem.setShaderFogEnd((float) LOIN_MAX * 1.6f * k);
            }
            float echelle = 1f;
            if (d > limite) {
                echelle = (float) (limite / d);
                x *= echelle;
                y *= echelle;
                z *= echelle;
            }
            ps.pushPose();
            ps.translate(x, y, z);
            if (echelle != 1f) ps.scale(echelle, echelle, echelle);
            HelicopterRenderer.dessiner(m, ps, buffers, LUMIERE_CIEL, Mth.rotLerp(pt, s.lacetO, s.lacet), Mth.lerp(pt, s.tangageO, s.tangage),
                    Mth.lerp(pt, s.roulisO, s.roulis), Mth.lerp(pt, s.palesO, s.pales), Mth.lerp(pt, s.queueO, s.queue), s.rotor, ticks,
                    ticks - s.tirG, ticks - s.tirD, ticks + pt);
            ps.popPose();
        }
        if (m != null) {
            HelicopterRenderer.finir(buffers);
            RenderSystem.setShaderFogStart(debut);
            RenderSystem.setShaderFogEnd(fin);
        }
    }

    /** Roquettes : le missile du modèle des munitions (sans son rail), tourné dans le sens du vol, et l'éclat du moteur. */
    private static void rendreRoquettes(Camera camera, Vec3 cam, PoseStack ps, float pt, MultiBufferSource.BufferSource buffers) {
        GeoPart missile = ModModels.ordnance().topLevel.get("missile");
        if (missile != null) {
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(TEX_MUNITIONS));
            for (int i = 0; i < ROQUETTES.size(); i++) {
                Roquette r = ROQUETTES.get(i);
                ps.pushPose();
                ps.translate(Mth.lerp(pt, r.xo, r.x) - cam.x, Mth.lerp(pt, r.yo, r.y) - cam.y, Mth.lerp(pt, r.zo, r.z) - cam.z);
                ps.mulPose(ROT.rotationY((float) Math.toRadians(180f - r.lacet)));
                ps.mulPose(ROT.rotationX((float) Math.toRadians(-r.tangage)));
                ps.scale(-ROQUETTE_ECHELLE, -ROQUETTE_ECHELLE, ROQUETTE_ECHELLE);
                ps.translate(0f, -ROQUETTE_AXE, 0f);
                missile.renderChildren(ps, vc, LUMIERE_CIEL, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
                ps.popPose();
            }
            buffers.endBatch(RenderType.entityCutout(TEX_MUNITIONS));
        }
        VertexConsumer flamme = buffers.getBuffer(RenderType.eyes(TEX_FLAMME));
        Matrix4f mat = ps.last().pose();
        for (int i = 0; i < ROQUETTES.size(); i++) {
            Roquette r = ROQUETTES.get(i);
            CENTRE.set((float) (Mth.lerp(pt, r.xo, r.x) - r.ux * 0.6 - cam.x), (float) (Mth.lerp(pt, r.yo, r.y) - r.uy * 0.6 - cam.y),
                    (float) (Mth.lerp(pt, r.zo, r.z) - r.uz * 0.6 - cam.z));
            float k = 0.75f + 0.25f * Mth.abs(Mth.sin((ticks + pt) * 5.1f + r.id));
            Flares.billboard(flamme, mat, ps.last().normal(), CENTRE, camera.getLeftVector(), camera.getUpVector(), 1.7f * k, k, 0.62f * k, 0.22f * k);
        }
        buffers.endBatch(RenderType.eyes(TEX_FLAMME));
    }

    /** Quad face à la caméra entre deux points (relatifs à la caméra) : transparent en a, opaque en b. */
    private static void ruban(VertexConsumer vc, Matrix4f mat, double ax, double ay, double az, double bx, double by, double bz,
                              float largeur, float r, float g, float b, float alpha) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        // côté = direction × vers la caméra
        double sx = dy * az - dz * ay, sy = dz * ax - dx * az, sz = dx * ay - dy * ax;
        double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1e-6) return;
        sx = sx / l * largeur;
        sy = sy / l * largeur;
        sz = sz / l * largeur;
        vc.vertex(mat, (float) (ax - sx), (float) (ay - sy), (float) (az - sz)).color(r, g, b, 0f).endVertex();
        vc.vertex(mat, (float) (ax + sx), (float) (ay + sy), (float) (az + sz)).color(r, g, b, 0f).endVertex();
        vc.vertex(mat, (float) (bx + sx), (float) (by + sy), (float) (bz + sz)).color(r, g, b, alpha).endVertex();
        vc.vertex(mat, (float) (bx - sx), (float) (by - sy), (float) (bz - sz)).color(r, g, b, alpha).endVertex();
    }

    // ------------------------------------------------------------------ fumée, feu, épave
    /** Point du modèle (repère naturel, 1/16 de bloc), sinon la valeur par défaut (blocs). */
    private static Vec3 point(GeoModel m, String nom, double[] defaut) {
        Vec3 p = m.meta.points.get(nom);
        return p != null ? p.scale(1.0 / 16.0) : new Vec3(defaut[0], defaut[1], defaut[2]);
    }

    /** Fumée grise sous 50 %, noire et flammes sous 25 %, traînée épaisse pendant la chute. */
    private static void particules(Helicopter h, Suivi s) {
        boolean chute = s.phase == Helicopter.CHUTE;
        if (s.sante >= 0.5f && !chute) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        double d = Math.sqrt(mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(h.getX(), h.getY(), h.getZ()));
        if (d > PARTICULES_MAX) return;
        float q = FxQuality.at(d);
        float g = FxQuality.grow(q);
        GeoModel m = ModModels.helicopter();
        float noir = chute ? 1f : Mth.clamp((0.5f - s.sante) / 0.25f, 0f, 1f);
        int gris = (int) Mth.lerp(noir, 0x70, 0x1C);
        int couleur = (gris << 16) | (gris << 8) | gris;
        boolean feu = chute || s.sante < 0.25f;
        for (int cote = 0; cote < 2; cote++) {
            Vec3 l = cote == 0 ? point(m, "engine_l", Helicopter.MOTEUR_G) : point(m, "engine_r", Helicopter.MOTEUR_D);
            Vec3 w = h.position().add(Helicopter.versMonde(l.x, l.y, l.z, h.getYRot(), h.tangage(0f), h.roulis(0f)));
            if (R.nextFloat() < (chute ? 1f : 0.5f) * q) {
                level.addParticle(SmokeParticleOptions.of(couleur, (1.4f + R.nextFloat()) * g * (chute ? 1.7f : 1f), 70 + R.nextInt(50), 0.05f, 0.85f),
                        w.x, w.y, w.z, R.nextGaussian() * 0.02, 0.03, R.nextGaussian() * 0.02);
            }
            if (feu && R.nextFloat() < (chute ? 0.9f : 0.45f) * q) {
                level.addParticle(ModParticles.FIREBALL.get(), w.x, w.y, w.z, R.nextGaussian() * 0.02, 0.04, R.nextGaussian() * 0.02);
            }
        }
        if (chute && d < 120 && R.nextFloat() < 0.5f * q) {
            // morceaux qui se détachent : étincelles qui tombent
            level.addParticle(ModParticles.SPARK.get(), h.getX() + R.nextGaussian(), h.getY() + 2, h.getZ() + R.nextGaussian(),
                    R.nextGaussian() * 0.25, -0.1 - R.nextFloat() * 0.2, R.nextGaussian() * 0.25);
        }
    }

    /** Foyers de l'épave (points {@code fire_0} à {@code fire_3}) si le modèle ne les donne pas, en blocs. */
    private static final double[][] FOYERS = {{-0.845, 2.724, 2.093}, {1.478, 2.438, 2.093}, {0.145, 1.180, 4.406}, {3.526, 1.428, 7.229}};

    /** Chaque tick, pour chaque épave proche : flammes et fumée des foyers pendant le feu, puis une fumée légère. */
    public static void epave(HelicopterWreck w) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        double d = Math.sqrt(mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(w.getX(), w.getY(), w.getZ()));
        if (d > PARTICULES_MAX) return;
        float q = FxQuality.at(d);
        boolean feu = w.enFeu();
        if (!feu && R.nextFloat() > 0.25f * q) return;
        GeoModel m = ModModels.helicopterWreck();
        int i = R.nextInt(FOYERS.length);
        Vec3 l = point(m, "fire_" + i, FOYERS[i]);
        Vec3 p = w.position().add(Helicopter.versMonde(l.x, l.y, l.z, w.getYRot(), 0, 0));
        if (feu) {
            if (R.nextFloat() < 0.8f * q) level.addParticle(ModParticles.FIREBALL.get(), p.x, p.y, p.z, R.nextGaussian() * 0.02, 0.05, R.nextGaussian() * 0.02);
            if (d < 32 && R.nextFloat() < 0.6f) level.addParticle(ParticleTypes.FLAME, p.x + R.nextGaussian() * 0.4, p.y, p.z + R.nextGaussian() * 0.4, 0, 0.04, 0);
            if (R.nextFloat() < 0.5f * q) {
                level.addParticle(SmokeParticleOptions.of(0x1E1B19, (2.2f + R.nextFloat() * 1.5f) * FxQuality.grow(q), 110 + R.nextInt(60), 0.08f, 0.85f),
                        p.x, p.y + 0.8, p.z, 0, 0.03, 0);
            }
        } else {
            level.addParticle(SmokeParticleOptions.of(0x5A5652, 1.2f + R.nextFloat(), 90 + R.nextInt(40), 0.05f, 0.6f), p.x, p.y + 0.3, p.z, 0, 0.02, 0);
        }
    }
}
