package fr.heraliz.airstrike.client.helico;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.airstrike.AirstrikeMod;
import fr.heraliz.airstrike.client.fx.Flares;
import fr.heraliz.airstrike.client.fx.FxQuality;
import fr.heraliz.airstrike.client.model.ModModels;
import fr.heraliz.airstrike.client.render.ModRenderTypes;
import fr.heraliz.airstrike.helico.CaisseHelicoEntity;
import fr.heraliz.airstrike.helico.EpaveHelicoEntity;
import fr.heraliz.airstrike.helico.HelicoEntity;
import fr.heraliz.airstrike.network.FouilleEtatPacket;
import fr.heraliz.airstrike.network.FouillePacket;
import fr.heraliz.airstrike.network.HelicoPositionPacket;
import fr.heraliz.airstrike.network.Network;
import fr.heraliz.airstrike.particle.SmokeBudget;
import fr.heraliz.airstrike.particle.SmokeParticleOptions;
import fr.heraliz.airstrike.registry.ModParticles;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * L'hélicoptère de patrouille chez le joueur (24/10) :
 * <ul><li>état de chaque hélicoptère connu (l'entité quand elle est suivie, sinon la position envoyée par le serveur
 * jusqu'à porteeSuivi blocs : dessin et son au-delà de la distance de vue) ;</li>
 * <li>son des rotors (RotorSon), fumée de plus en plus épaisse et sombre avec les dégâts, flammes à la fin ;</li>
 * <li>traçantes de la mitrailleuse (depuis la bouche jusqu'au point d'impact envoyé par le serveur), éclair à la bouche ;</li>
 * <li>épave : flammes et fumée jusqu'à FEU_FIN, fumerolles ensuite, éclat sur les morceaux encore à fouiller ;</li>
 * <li>fouille : clic droit tenu sur un morceau d'épave ou une caisse (pris avant TaCZ : pas de visée), maintien envoyé
 * toutes les 4 ticks, barre d'avancement sous le viseur, aide quand on vise.</li></ul>
 * Particules au plus près de la qualité réglée par le joueur (FxQuality) et du budget de fumée (SmokeBudget).
 */
@Mod.EventBusSubscriber(modid = AirstrikeMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HelicoClient {
    private static final RandomSource R = RandomSource.create();
    private static final ResourceLocation FLASH = ModModels.texture("designator_display");
    private static final ResourceLocation POLICE = new ResourceLocation(AirstrikeMod.MODID, "ui_small");
    private static final int CIEL = LightTexture.pack(0, 15);

    /** Ce que le joueur sait d'un hélicoptère. */
    public static final class Etat {
        final int id;
        double px, py, pz, x, y, z;
        float lacetP, lacet, vie = 1f;
        byte etat;
        long recu;
        boolean lointain = true;
        int age;
        long flash = -100;
        @Nullable
        RotorSon son;

        Etat(int id) {
            this.id = id;
        }

        public boolean chute() {
            return etat == HelicoEntity.CHUTE;
        }

        /** Position (entité si elle est là, sinon entre les deux derniers paquets, avec 10 ticks de retard). */
        public Vec3 position(float pt) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.getEntity(id) instanceof HelicoEntity e) {
                return new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
            }
            double t = mc.level == null ? 1 : Mth.clamp((mc.level.getGameTime() + pt - recu) / 10.0, 0, 1);
            return new Vec3(Mth.lerp(t, px, x), Mth.lerp(t, py, y), Mth.lerp(t, pz, z));
        }

        float lacet(float pt) {
            Minecraft mc = Minecraft.getInstance();
            double t = mc.level == null ? 1 : Mth.clamp((mc.level.getGameTime() + pt - recu) / 10.0, 0, 1);
            return lacetP + Mth.wrapDegrees(lacet - lacetP) * (float) t;
        }
    }

    private record Traceur(Vec3 a, Vec3 b, float duree, int[] age) {
    }

    private static final Map<Integer, Etat> ETATS = new HashMap<>();
    private static final List<Traceur> TRACEURS = new ArrayList<>();

    // fouille
    private static int fouilleCible = -1, fouilleTicks;
    private static float progres = -1f;
    private static int dureeFouille = 160;
    private static long progresRecu, finiA = -100;

    private HelicoClient() {
    }

    @Nullable
    static Etat etat(int id) {
        return ETATS.get(id);
    }

    // ================================================================================================ réception
    public static void reception(HelicoPositionPacket pk) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!pk.present) {
            Etat s = ETATS.remove(pk.id);
            if (s != null && s.son != null) mc.getSoundManager().stop(s.son);
            return;
        }
        Etat s = ETATS.computeIfAbsent(pk.id, Etat::new);
        long now = mc.level.getGameTime();
        if (!(mc.level.getEntity(pk.id) instanceof HelicoEntity)) {
            boolean neuf = s.recu == 0 || !s.lointain;
            Vec3 ici = s.position(0f);
            s.px = neuf ? pk.x : ici.x;
            s.py = neuf ? pk.y : ici.y;
            s.pz = neuf ? pk.z : ici.z;
            s.lacetP = neuf ? pk.yaw : s.lacet(0f);
            s.x = pk.x;
            s.y = pk.y;
            s.z = pk.z;
            s.lacet = pk.yaw;
            s.lointain = true;
        }
        s.vie = pk.vie;
        s.etat = pk.etat;
        s.recu = now;
    }

    public static void etatFouille(FouilleEtatPacket pk) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        dureeFouille = Math.max(1, pk.duree);
        if (pk.progres == FouilleEtatPacket.FINI) {
            progres = -1f;
            finiA = now;
        } else if (pk.progres < 0f) {
            progres = -1f;
        } else {
            progres = pk.progres;
            progresRecu = now;
        }
    }

    // ================================================================================================ ticks des entités
    /** Chaque tick de l'entité chez le joueur (HelicoEntity.TICK_CLIENT). */
    public static void tickHelico(HelicoEntity e) {
        Etat s = ETATS.computeIfAbsent(e.getId(), Etat::new);
        s.px = s.x = e.getX();
        s.py = s.y = e.getY();
        s.pz = s.z = e.getZ();
        s.lacetP = s.lacet = e.getYRot();
        s.vie = e.vie();
        s.etat = e.etat();
        s.recu = e.level().getGameTime();
        s.lointain = false;
        fumee(e.level(), e.position(), e.getYRot(), e.tangage, e.roulis, e.vie(), e.enChute(), e.tickCount);
    }

    public static void tickEpave(EpaveHelicoEntity e) {
        Minecraft mc = Minecraft.getInstance();
        Level l = e.level();
        double dist = mc.gameRenderer.getMainCamera().getPosition().distanceTo(e.position());
        if (dist > 320) return;
        long now = l.getGameTime();
        float q = FxQuality.at(dist);
        AABB b = e.getBoundingBox();
        if (now < e.feuFin()) {
            float reste = Mth.clamp((e.feuFin() - now) / 600f, 0.35f, 1f);
            if (R.nextFloat() < 0.85f * q * reste) {
                Particle f = spawn(ModParticles.FIREBALL.get(), b.minX + R.nextDouble() * b.getXsize(), b.minY + R.nextDouble() * b.getYsize() * 0.6,
                        b.minZ + R.nextDouble() * b.getZsize(), 0, 0.03, 0);
                if (f != null) f.scale(1.0f + R.nextFloat() * 1.5f);
            }
            if (dist < 48 && R.nextInt(3) == 0) {
                spawn(ParticleTypes.FLAME, b.minX + R.nextDouble() * b.getXsize(), b.minY + 0.2, b.minZ + R.nextDouble() * b.getZsize(), 0, 0.03, 0);
            }
            int per = e.piece() == 0 ? 2 : 7;
            if (e.tickCount % per == 0 && R.nextFloat() < Math.max(0.35f, SmokeBudget.factor())) {
                spawn(SmokeParticleOptions.of(0x1C1A17, (3.2f + R.nextFloat() * 2.8f) * FxQuality.grow(q), 200 + R.nextInt(80), 0.12f, 0.9f),
                        b.getCenter().x + R.nextGaussian() * 0.6, b.maxY, b.getCenter().z + R.nextGaussian() * 0.6,
                        R.nextGaussian() * 0.02, 0.16, R.nextGaussian() * 0.02);
            }
            if (dist < 40 && (e.tickCount + e.getId()) % 30 == 0) {
                l.playLocalSound(e.getX(), e.getY() + 0.5, e.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1.3f, 0.7f + R.nextFloat() * 0.2f, false);
            }
        } else if (now < e.feuFin() + 2400) {
            if (e.tickCount % (e.piece() == 0 ? 6 : 14) == 0 && dist < 200) {
                spawn(SmokeParticleOptions.of(0x6E6A64, 1.4f + R.nextFloat(), 120, 0.06f, 0.45f), b.getCenter().x, b.maxY - 0.2,
                        b.getCenter().z, 0, 0.05, 0);
            }
        }
        if (now >= e.feuFin() && e.fouillable() && dist < 40 && (e.tickCount + e.getId()) % 20 == 0) {
            spawn(ParticleTypes.END_ROD, b.minX + R.nextDouble() * b.getXsize(), b.maxY + 0.1, b.minZ + R.nextDouble() * b.getZsize(), 0, 0.02, 0);
        }
    }

    public static void tickCaisse(CaisseHelicoEntity e) {
        if (!e.brule() || e.tickCount % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(e.position()) > 120 * 120) return;
        spawn(SmokeParticleOptions.of(0x4A4642, 1.0f + R.nextFloat() * 0.6f, 80, 0.06f, 0.55f), e.getX(), e.getY() + 0.9, e.getZ(), 0, 0.06, 0);
    }

    @Nullable
    private static Particle spawn(ParticleOptions o, double x, double y, double z, double vx, double vy, double vz) {
        return Minecraft.getInstance().particleEngine.createParticle(o, x, y, z, vx, vy, vz);
    }

    /**
     * Fumée de l'hélicoptère abîmé : rien au-dessus de 70 % de vie, puis grise et fine, de plus en plus épaisse, haute et
     * noire jusqu'à 10 % ; flammes aux échappements à la fin et pendant la chute. Visible de loin (grosses bouffées).
     */
    static void fumee(Level l, Vec3 pos, float lacet, float tangage, float roulis, float vie, boolean chute, int tick) {
        float k = chute ? 1f : Mth.clamp((0.7f - vie) / 0.6f, 0f, 1f);
        if (k <= 0f) return;
        int periode = k < 0.3f ? 4 : k < 0.6f ? 2 : 1;
        if (tick % periode != 0) return;
        double dist = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(pos);
        float q = FxQuality.at(dist) * Math.max(0.35f, SmokeBudget.factor());
        int n = FxQuality.count(chute ? 3 : k > 0.8f ? 2 : 1, q, 1);
        int gris = (int) Mth.lerp(k, 0x8C, 0x1A);
        int rgb = (gris << 16) | (gris << 8) | (int) (gris * 0.92f);
        float taille = (1.3f + 3.8f * k) * FxQuality.grow(q);
        for (int i = 0; i < n; i++) {
            Vec3 ech = ((i + tick) & 1) == 0 ? HelicoEntity.ECHAPPEMENT_G : HelicoEntity.ECHAPPEMENT_D;
            Vec3 w = HelicoRendu.pointMonde(ech, pos.x, pos.y, pos.z, lacet, tangage, roulis);
            spawn(SmokeParticleOptions.of(rgb, taille * (0.8f + R.nextFloat() * 0.4f), (int) (70 + 200 * k), 0.04f + 0.10f * k, 0.45f + 0.5f * k),
                    w.x, w.y, w.z, R.nextGaussian() * 0.04, 0.05 + 0.1 * k, R.nextGaussian() * 0.04);
        }
        if ((k > 0.75f || chute) && (tick & 1) == 0 && dist < 280) {
            Vec3 ech = (tick & 2) == 0 ? HelicoEntity.ECHAPPEMENT_G : HelicoEntity.ECHAPPEMENT_D;
            Vec3 w = HelicoRendu.pointMonde(ech, pos.x, pos.y, pos.z, lacet, tangage, roulis);
            Particle f = spawn(ModParticles.FIREBALL.get(), w.x, w.y, w.z, 0, 0.02, 0);
            if (f != null) f.scale(0.7f + 1.1f * k);
        }
    }

    /** Une balle de la mitrailleuse est arrivée en p (ImpactPacket HELICO_BALLE) : traçante depuis l'hélicoptère le plus proche. */
    public static void traceur(Vec3 p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Etat proche = null;
        double best = 200 * 200;
        for (Etat s : ETATS.values()) {
            double d = s.position(0f).distanceToSqr(p);
            if (d < best) {
                best = d;
                proche = s;
            }
        }
        if (proche == null) return;
        Vec3 a;
        if (mc.level.getEntity(proche.id) instanceof HelicoEntity e) {
            a = HelicoRendu.bouche(e.getX(), e.getY(), e.getZ(), e.getYRot(), e.tangage, e.roulis, e.tourelleLacet, e.canonTangage);
        } else {
            Vec3 pos = proche.position(0f);
            a = HelicoRendu.pointMonde(HelicoEntity.BOUCHE, pos.x, pos.y, pos.z, proche.lacet(0f), 8f, 0f);
        }
        proche.flash = mc.level.getGameTime();
        if (TRACEURS.size() < 64) TRACEURS.add(new Traceur(a, p, (float) Math.max(1.0, a.distanceTo(p) / 45.0), new int[]{0}));
    }

    // ================================================================================================ tick, rendu
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel l = mc.level;
        if (l == null) {
            if (!ETATS.isEmpty()) vider();
            return;
        }
        long now = l.getGameTime();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Iterator<Etat> it = ETATS.values().iterator();
        while (it.hasNext()) {
            Etat s = it.next();
            boolean entite = l.getEntity(s.id) instanceof HelicoEntity;
            if (!entite && now - s.recu > 40) {
                if (s.son != null) mc.getSoundManager().stop(s.son);
                it.remove();
                continue;
            }
            s.age++;
            Vec3 p = s.position(0f);
            if ((s.son == null || !mc.getSoundManager().isActive(s.son)) && p.distanceToSqr(cam) < 520 * 520) {
                s.son = new RotorSon(s.id, p);
                mc.getSoundManager().play(s.son);
            }
            if (!entite && s.lointain) fumee(l, p, s.lacet(0f), 8f, 0f, s.vie, s.chute(), s.age);
        }
        TRACEURS.removeIf(t -> ++t.age()[0] > t.duree() + 1);
        tickFouille(mc);
    }

    @SubscribeEvent
    public static void rendre(RenderLevelStageEvent ev) {
        if (ev.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || (ETATS.isEmpty() && TRACEURS.isEmpty())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Camera camera = ev.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack ps = ev.getPoseStack();
        float pt = ev.getPartialTick();
        long now = mc.level.getGameTime();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        try {
            // hélicoptères lointains (au-delà de la distance de vue : pas d'entité)
            for (Etat s : ETATS.values()) {
                // seulement d'après les paquets du serveur (une entité qui vient de disparaître : abattue ou hors de vue)
                if (!s.lointain || mc.level.getEntity(s.id) instanceof HelicoEntity) continue;
                Vec3 p = s.position(pt);
                if (p.distanceToSqr(cam) > 700 * 700 || !ev.getFrustum().isVisible(new AABB(p, p).inflate(9))) continue;
                ps.pushPose();
                ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
                HelicoRendu.dessiner(ps, buf, CIEL, s.lacet(pt), s.chute() ? 18f : 8f, 0f, s.age + pt, 0f, -8f, 0f, s.chute());
                ps.popPose();
            }
            Matrix4f mat = ps.last().pose();
            if (!TRACEURS.isEmpty()) {
                VertexConsumer vc = buf.getBuffer(ModRenderTypes.GLOW);
                for (Traceur t : TRACEURS) {
                    float f = Mth.clamp((t.age()[0] + pt) / t.duree(), 0f, 1f);
                    Vec3 dir = t.b().subtract(t.a());
                    Vec3 tete = t.a().add(dir.scale(f));
                    Vec3 queue = t.a().add(dir.scale(Math.max(0f, f - 6.0 / Math.max(1.0, dir.length()))));
                    ruban(vc, mat, queue.subtract(cam), tete.subtract(cam), 0.07f, 1.0f, 0.85f, 0.45f, 0.95f);
                    ruban(vc, mat, queue.subtract(cam), tete.subtract(cam), 0.22f, 1.0f, 0.5f, 0.12f, 0.35f);
                }
            }
            // éclair à la bouche pendant les rafales
            VertexConsumer lueur = null;
            for (Etat s : ETATS.values()) {
                if (now - s.flash > 1 || !(mc.level.getEntity(s.id) instanceof HelicoEntity e)) continue;
                if (lueur == null) lueur = buf.getBuffer(RenderType.eyes(FLASH));
                Vec3 b = HelicoRendu.bouche(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()),
                        Mth.rotLerp(pt, e.yRotO, e.getYRot()), e.tangage, e.roulis, e.tourelleLacet, e.canonTangage).subtract(cam);
                float k = 0.6f + 0.4f * R.nextFloat();
                Flares.billboard(lueur, mat, ps.last().normal(), new Vector3f((float) b.x, (float) b.y, (float) b.z),
                        camera.getLeftVector(), camera.getUpVector(), 2.6f * k, k, 0.7f * k, 0.3f * k);
            }
        } finally {
            buf.endBatch();
        }
    }

    private static void ruban(VertexConsumer vc, Matrix4f mat, Vec3 a, Vec3 b, float largeur, float r, float g, float bl, float alpha) {
        Vec3 cote = b.subtract(a).cross(a).normalize().scale(largeur);
        if (!Double.isFinite(cote.x)) return;
        vc.vertex(mat, (float) (a.x - cote.x), (float) (a.y - cote.y), (float) (a.z - cote.z)).color(r, g, bl, 0f).endVertex();
        vc.vertex(mat, (float) (a.x + cote.x), (float) (a.y + cote.y), (float) (a.z + cote.z)).color(r, g, bl, 0f).endVertex();
        vc.vertex(mat, (float) (b.x + cote.x), (float) (b.y + cote.y), (float) (b.z + cote.z)).color(r, g, bl, alpha).endVertex();
        vc.vertex(mat, (float) (b.x - cote.x), (float) (b.y - cote.y), (float) (b.z - cote.z)).color(r, g, bl, alpha).endVertex();
    }

    // ================================================================================================ fouille
    @Nullable
    private static Entity visee(Minecraft mc) {
        if (mc.player == null || mc.screen != null || mc.player.isSpectator()) return null;
        if (!(mc.hitResult instanceof EntityHitResult h)) return null;
        Entity e = h.getEntity();
        return e instanceof EpaveHelicoEntity || e instanceof CaisseHelicoEntity ? e : null;
    }

    /** Touche « Utiliser » réellement enfoncée (le clic est pris avant Minecraft : son état n'est pas suivi par lui). */
    private static boolean tenue(Minecraft mc) {
        InputConstants.Key k = mc.options.keyUse.getKey();
        long w = mc.getWindow().getWindow();
        if (k.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(w, k.getValue()) == GLFW.GLFW_PRESS;
        return k.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(w, k.getValue());
    }

    private static void appui(Entity e) {
        fouilleCible = e.getId();
        fouilleTicks = 0;
        Network.CHANNEL.sendToServer(new FouillePacket(e.getId(), FouillePacket.APPUI));
    }

    /** Clic droit sur une épave ou une caisse : pris avant tout le monde (ni visée de TaCZ, ni coup de mêlée). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void souris(InputEvent.MouseButton.Pre ev) {
        if (ev.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.keyUse.isActiveAndMatches(InputConstants.Type.MOUSE.getOrCreate(ev.getButton()))) return;
        Entity e = visee(mc);
        if (e == null) return;
        ev.setCanceled(true);
        appui(e);
    }

    /** Touche « Utiliser » mise sur le clavier : même chose. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void touche(InputEvent.InteractionKeyMappingTriggered ev) {
        if (!ev.isUseItem()) return;
        Entity e = visee(Minecraft.getInstance());
        if (e == null) return;
        ev.setCanceled(true);
        ev.setSwingHand(false);
        if (fouilleCible != e.getId()) appui(e);
    }

    private static void tickFouille(Minecraft mc) {
        if (fouilleCible < 0) return;
        Entity e = visee(mc);
        if (!tenue(mc) || e == null || e.getId() != fouilleCible) {
            Network.CHANNEL.sendToServer(new FouillePacket(fouilleCible, FouillePacket.RELACHE));
            fouilleCible = -1;
            progres = -1f;
        } else if (++fouilleTicks % 4 == 0) {
            Network.CHANNEL.sendToServer(new FouillePacket(fouilleCible, FouillePacket.MAINTIEN));
        }
    }

    /** Barre de fouille sous le viseur, ou aide quand on vise un morceau d'épave ou une caisse. */
    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null || mc.screen != null) return;
        int cx = w / 2, y = h / 2 + 16;
        long now = mc.level.getGameTime();
        if (progres >= 0f || now - finiA < 12) {
            float p = progres >= 0f ? Mth.clamp(progres + (now + pt - progresRecu) / dureeFouille, 0f, 1f) : 1f;
            int larg = 104;
            int x0 = cx - larg / 2;
            g.fill(x0 - 1, y - 1, x0 + larg + 1, y + 5, 0xB0101214);
            g.fill(x0, y, x0 + Math.round(larg * p), y + 4, progres >= 0f ? 0xFFE0A440 : 0xFF6BD06B);
            int reste = Math.max(0, (int) Math.ceil((1f - p) * dureeFouille / 20f));
            Component c = progres >= 0f
                    ? Component.translatable("airstrike.helico.fouille", reste).withStyle(st -> st.withFont(POLICE))
                    : Component.translatable("airstrike.helico.fouille_finie").withStyle(st -> st.withFont(POLICE));
            g.drawString(mc.font, c, cx - mc.font.width(c) / 2, y + 8, 0xFFEDE6D2, true);
            return;
        }
        Entity e = visee(mc);
        if (e == null) return;
        Component c;
        int couleur;
        if (e instanceof EpaveHelicoEntity ep) {
            if (ep.brule()) {
                c = Component.translatable("airstrike.helico.chaud_court");
                couleur = 0xFFFFB347;
            } else if (ep.fouillable()) {
                c = Component.translatable("airstrike.helico.aide_fouille");
                couleur = 0xFFEDE6D2;
            } else {
                c = Component.translatable("airstrike.helico.aide_vide");
                couleur = 0xFF9A9A9A;
            }
        } else if (e instanceof CaisseHelicoEntity ca) {
            c = ca.brule() ? Component.translatable("airstrike.helico.chaud_court") : Component.translatable("airstrike.helico.aide_caisse");
            couleur = ca.brule() ? 0xFFFFB347 : 0xFFEDE6D2;
        } else {
            return;
        }
        Component t = c.copy().withStyle(st -> st.withFont(POLICE));
        g.drawString(mc.font, t, cx - mc.font.width(t) / 2, y, couleur, true);
    }

    // ================================================================================================ fin de session
    @SubscribeEvent
    public static void deconnexion(ClientPlayerNetworkEvent.LoggingOut ev) {
        vider();
    }

    private static void vider() {
        Minecraft mc = Minecraft.getInstance();
        for (Etat s : ETATS.values()) if (s.son != null) mc.getSoundManager().stop(s.son);
        ETATS.clear();
        TRACEURS.clear();
        fouilleCible = -1;
        progres = -1f;
    }
}
