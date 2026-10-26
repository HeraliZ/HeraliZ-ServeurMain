package fr.heraliz.airstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.airstrike.client.fx.FxQuality;
import fr.heraliz.airstrike.client.model.GeoModel;
import fr.heraliz.airstrike.client.model.ModModels;
import fr.heraliz.airstrike.client.render.ModRenderTypes;
import fr.heraliz.airstrike.client.sound.HelicoSound;
import fr.heraliz.airstrike.entity.Helicopter;
import fr.heraliz.airstrike.entity.HelicopterWreck;
import fr.heraliz.airstrike.network.HelicoPositionPacket;
import fr.heraliz.airstrike.network.HelicoTirPacket;
import fr.heraliz.airstrike.particle.SmokeParticleOptions;
import fr.heraliz.airstrike.registry.ModParticles;
import fr.heraliz.airstrike.registry.ModSounds;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * L'hélicoptère chez le joueur, hors du modèle lui-même ({@code HelicopterRenderer}) : les sons en boucle (rotor proche,
 * rotor lointain, alarme), la fumée et le feu des moteurs selon la santé, la traînée de la chute, les traçantes et
 * étincelles du minigun, les flammes et la fumée de l'épave. Un hélicoptère est suivi par son entité quand le serveur
 * l'envoie (jusqu'à la distance de vue), sinon par la position reçue chaque seconde ({@link HelicoPositionPacket}),
 * prolongée entre deux paquets : on l'entend avant de le voir. Sans hélicoptère, {@link #tick()} sort tout de suite.
 */
public final class HelicoClient {
    private HelicoClient() {
    }

    /** Un hélicoptère connu de ce jeu. */
    public static final class Suivi {
        final int id;
        public double x, y, z;
        double vx, vy, vz;
        public float rotor = 1f, chute, sante = 1f;
        byte phase;
        int sansNouvelles;
        boolean vuEntite;
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

    private static final Int2ObjectOpenHashMap<Suivi> SUIVIS = new Int2ObjectOpenHashMap<>();
    private static final List<Tracante> TRACANTES = new ArrayList<>();
    private static final RandomSource R = RandomSource.create();
    /** Fumée et flammes au-delà de cette distance : rien (le modèle est déjà petit). */
    private static final double PARTICULES_MAX = 200;
    private static long ticks;

    // ------------------------------------------------------------------ suivi
    private static Suivi suivi(int id) {
        Suivi s = SUIVIS.get(id);
        if (s == null) {
            s = new Suivi(id);
            SUIVIS.put(id, s);
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

    /** Chaque tick, pour chaque hélicoptère que le serveur envoie (Helicopter.TICK_CLIENT). */
    public static void entite(Helicopter h) {
        Suivi s = suivi(h.getId());
        s.x = h.getX();
        s.y = h.getY();
        s.z = h.getZ();
        s.vx = h.getX() - h.xo;
        s.vy = h.getY() - h.yo;
        s.vz = h.getZ() - h.zo;
        s.rotor = h.rotor();
        s.sante = h.sante();
        s.phase = h.phase();
        float c = h.chute(0f);
        s.chute = Math.max(0f, c);
        s.vuEntite = true;
        s.sansNouvelles = 0;
        sons(s);
        particules(h, s);
    }

    /** Position reçue chaque seconde : seulement pour un hélicoptère que le serveur n'envoie pas (trop loin). */
    public static void position(HelicoPositionPacket p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level.getEntity(p.entite) instanceof Helicopter) return;
        // de loin, le rotor se tait dès la chute (de près, l'entité joue la chute et son rotor qui ralentit)
        if (p.phase == Helicopter.CHUTE) {
            retire(p.entite);
            return;
        }
        Suivi s = suivi(p.entite);
        s.x = p.x;
        s.y = p.y;
        s.z = p.z;
        s.vx = p.vx;
        s.vy = p.vy;
        s.vz = p.vz;
        s.rotor = p.rotor;
        s.phase = p.phase;
        s.sansNouvelles = 0;
        sons(s);
    }

    /** Fin de tick client : prolonge les hélicoptères lointains, oublie ceux dont on n'a plus de nouvelles, vieillit les traçantes. */
    public static void tick() {
        if (SUIVIS.isEmpty() && TRACANTES.isEmpty()) return;
        ticks++;
        if (!SUIVIS.isEmpty()) {
            Iterator<Suivi> it = SUIVIS.values().iterator();
            while (it.hasNext()) {
                Suivi s = it.next();
                if (s.vuEntite) {
                    s.vuEntite = false;
                    continue;
                }
                // écrasé (fin de la chute) : le rotor se tait tout de suite
                if (s.phase == Helicopter.CHUTE && s.chute > 0.9f || ++s.sansNouvelles > 50) {
                    s.fini = true;
                    it.remove();
                    continue;
                }
                s.x += s.vx;
                s.y += s.vy;
                s.z += s.vz;
            }
        }
        if (!TRACANTES.isEmpty()) TRACANTES.removeIf(t -> ++t.age > t.vie + 1);
    }

    /** Ticks depuis le dernier coup de ce minigun (grand s'il n'a pas tiré). */
    public static long depuisTir(int id, boolean droite) {
        Suivi s = SUIVIS.get(id);
        return s == null ? 1000 : ticks - (droite ? s.tirD : s.tirG);
    }

    /** Hélicoptère retiré (carte M) ou en chute vu de loin : ses sons s'arrêtent tout de suite. */
    public static void retire(int id) {
        Suivi s = SUIVIS.remove(id);
        if (s != null) s.fini = true;
    }

    public static void vider() {
        for (Suivi s : SUIVIS.values()) s.fini = true;
        SUIVIS.clear();
        TRACANTES.clear();
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

    /** Traçantes : une tête lumineuse et sa traînée qui filent du canon jusqu'à l'impact (RenderLevelStageEvent). */
    public static void rendre(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || TRACANTES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        float pt = e.getPartialTick();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(ModRenderTypes.GLOW);
        Matrix4f mat = ps.last().pose();
        for (Tracante t : TRACANTES) {
            float p = (t.age + pt) / t.vie;
            if (p > 1f) continue;
            double tete = p * t.longueur, queue = Math.max(0, tete - 7);
            double hx = t.x0 + t.dx * tete - cam.x, hy = t.y0 + t.dy * tete - cam.y, hz = t.z0 + t.dz * tete - cam.z;
            double qx = t.x0 + t.dx * queue - cam.x, qy = t.y0 + t.dy * queue - cam.y, qz = t.z0 + t.dz * queue - cam.z;
            ruban(vc, mat, qx, qy, qz, hx, hy, hz, 0.06f, 1f, 0.86f, 0.5f, 0.95f);
            ruban(vc, mat, qx, qy, qz, hx, hy, hz, 0.2f, 1f, 0.5f, 0.12f, 0.35f);
        }
        buffers.endBatch(ModRenderTypes.GLOW);
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
