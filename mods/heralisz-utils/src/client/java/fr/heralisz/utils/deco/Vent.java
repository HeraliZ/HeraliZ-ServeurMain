package fr.heralisz.utils.deco;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Le vent de la faille de Durn-Hald : une brise permanente (les ponts et les lanternes bougent toujours un peu) et, toutes
 * les 2 à 4 minutes, une rafale venue du dehors qui s'engouffre par un bout de la faille et la traverse jusqu'à l'autre
 * (≈ 30 blocs/s) : grondement qui monte puis s'éloigne, nappes blanches et traits de vent qui balaient la faille
 * (VentParticules, RafaleParticle), ponts qui se couchent et roulent.
 * Sur un pont, la rafale pousse et secoue : on peut passer par-dessus la rambarde (on tombe dans la rivière) — sauf en
 * restant accroupi, cramponné. Tout est calculé à partir de l'heure du monde : chaque joueur vit la même rafale au même
 * moment, rien ne passe par le serveur (le joueur se déplace lui-même, comme toujours dans Minecraft).
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, value = Dist.CLIENT)
public final class Vent {
    // la faille (axe nord-sud) : boîte de la zone et bouts nord/sud
    private static final int X0 = -1925, X1 = -1665, Z0 = -2190, Z1 = -1862, Y0 = 22, Y1 = 200;
    private static final int ZN = -2168, ZS = -1885;
    private static final long CYCLE = 3600;              // une rafale toutes les ~3 min (± 40 s)
    private static final float VITESSE = 1.5f;           // le front avance de 1,5 bloc par tick
    private static final float TRAINE = 70f;             // la rafale dure ~3 s en un point
    private static final RandomSource HASARD = RandomSource.create();
    private static SonVent son;

    private Vent() {}

    public static boolean dansZone(double x, double y, double z) {
        return x >= X0 && x <= X1 && z >= Z0 && z <= Z1 && y >= Y0 && y <= Y1;
    }

    private static long debut(long k) {
        return k * CYCLE + (long) (hash(k) * 1600);
    }

    private static int sensDe(long k) {
        return hash(k * 7 + 3) < 0.5f ? 1 : -1;               // +1 : du nord vers le sud
    }

    private static float forceDe(long k) {
        return 0.7f + 0.3f * hash(k * 13 + 5);
    }

    /** Intensité de la rafale (0..1) au point (z) au temps t (ticks du monde), et son sens dans SENS. */
    public static float rafale(double x, double y, double z, double t) {
        if (!dansZone(x, y, z)) {
            return 0f;
        }
        long k = (long) Math.floor(t / CYCLE);
        float g = 0f;
        for (long j = k - 1; j <= k; j++) {
            double dt = t - debut(j);
            if (dt < 0 || dt * VITESSE > (ZS - ZN) + 260) {
                continue;
            }
            int s = sensDe(j);
            double front = (s > 0 ? ZN - 40 : ZS + 40) + s * dt * VITESSE;
            double d = (z - front) * s;                       // > 0 : pas encore atteint ; < 0 : derrière le front
            float p = d > 0 ? (float) Math.exp(-(d / 10) * (d / 10)) : (float) Math.exp(d / TRAINE);
            p *= forceDe(j);
            if (p > g) {
                g = p;
                SENS = s;
            }
        }
        return g;
    }

    /** Sens de la dernière rafale évaluée (+1 vers le sud, -1 vers le nord). */
    public static int SENS = 1;

    /** Brise permanente dans la faille (0,1..0,3), lente. */
    public static float brise(double t) {
        return 0.18f + 0.07f * Mth.sin((float) (t * 0.013)) + 0.05f * Mth.sin((float) (t * 0.031 + 1.3));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase != TickEvent.Phase.END || mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        LocalPlayer p = mc.player;
        Level l = mc.level;
        double t = l.getGameTime();
        float g = rafale(p.getX(), p.getY(), p.getZ(), t);
        int s = SENS;
        if (g > 0.02f && !p.isSpectator() && !abrite(l, p.blockPosition())) {
            boolean pont = surUnPont(p);
            float acc = g * (pont ? 0.06f : 0.03f) * (p.isShiftKeyDown() ? 0.25f : 1f);
            p.setDeltaMovement(p.getDeltaMovement().add(0, 0, s * acc));
            if (pont && !p.isShiftKeyDown() && g > 0.55f && HASARD.nextFloat() < 0.03f * g) {
                // secousse : le tablier se dérobe et projette (on peut passer la rambarde)
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(v.x * 0.5, 0.40, v.z + s * (0.30 + 0.25 * g));
            }
            if (pont) {
                p.resetFallDistance();
            }
        }
        // la rafale se voit : nappes blanches et traits de vent là où elle passe (on la voit arriver par un bout de la
        // faille, elle balaie le joueur puis file), jamais dans la roche
        if (dansZone(p.getX(), p.getY(), p.getZ())) {
            particules(l, p, t);
        }
        // grondement du vent : un son qui suit l'intensité de la rafale autour du joueur
        if (g > 0.03f && (son == null || son.isStopped())) {
            son = new SonVent();
            mc.getSoundManager().play(son);
        }
    }

    private static final int ESSAIS = 90;                 // points tirés par tick autour du joueur
    private static final int NAPPES = 6, TRAITS = 14;     // quotas par tick : au plus ~270 nappes et ~280 traits en vol

    /** Particules de la rafale autour du joueur : des points tirés au hasard dans la largeur de la faille (±12 en x,
     *  ±32 en z : au-delà de 32 blocs le jeu ne les affiche pas) ; là où la rafale souffle, une nappe ou un trait de vent
     *  qui file dans son sens, avec une probabilité égale à son intensité. Quotas fixes par tick (le nombre en vol est
     *  borné par quota × durée de vie) ; le réglage « Particules » du jeu s'applique en plus. */
    private static void particules(Level l, LocalPlayer p, double t) {
        int nappes = 0, traits = 0;
        for (int i = 0; i < ESSAIS && (nappes < NAPPES || traits < TRAITS); i++) {
            double x = p.getX() + (HASARD.nextDouble() - 0.5) * 24;
            double z = p.getZ() + (HASARD.nextDouble() - 0.5) * 64;
            double y = p.getY() - 5 + HASARD.nextDouble() * 17;
            float gi = rafale(x, y, z, t);
            if (gi < 0.1f || HASARD.nextFloat() > gi * 1.3f) {
                continue;
            }
            int si = SENS;
            BlockPos bp = BlockPos.containing(x, y, z);
            if (!l.isLoaded(bp) || !l.getBlockState(bp).isAir()) {
                continue;
            }
            boolean nappe = HASARD.nextFloat() < 0.35f;          // un tiers de nappes, le reste en traits
            if (nappe && nappes >= NAPPES) nappe = false;
            if (!nappe && traits >= TRAITS) nappe = nappes < NAPPES;
            if (nappe ? nappes >= NAPPES : traits >= TRAITS) {
                continue;
            }
            if (nappe) {
                nappes++;
                l.addParticle(VentParticules.RAFALE.get(), x, y, z, (HASARD.nextDouble() - 0.5) * 0.05,
                        (HASARD.nextDouble() - 0.4) * 0.035, si * (0.5 + 0.5 * gi));
            } else {
                traits++;
                l.addParticle(VentParticules.TRAIT.get(), x, y, z, (HASARD.nextDouble() - 0.5) * 0.03,
                        (HASARD.nextDouble() - 0.5) * 0.02, si * (1.1 + 0.8 * gi));
            }
        }
    }

    private static boolean surUnPont(LocalPlayer p) {
        for (PontBlockEntity be : PontBlockEntity.CHARGES) {
            if (be.getLevel() == p.level() && !be.isRemoved() && !Float.isNaN(be.surLePont(p.getX(), p.getY(), p.getZ()))) {
                return true;
            }
        }
        return false;
    }

    /** Sous un toit (maison, galerie) : à l'abri du vent. */
    private static boolean abrite(Level l, BlockPos pos) {
        for (int dy = 2; dy <= 4; dy++) {
            if (l.getBlockState(pos.above(dy)).isSolidRender(l, pos.above(dy))) {
                return true;
            }
        }
        return false;
    }

    static float hash(long i) {
        long h = i * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }

    /** Souffle du vent (son des élytres, grave) dont le volume suit la rafale au joueur. */
    private static final class SonVent extends AbstractTickableSoundInstance {
        private int calme;

        SonVent() {
            super(SoundEvents.ELYTRA_FLYING, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.pitch = 0.6f;
            this.relative = true;
            this.x = 0;
            this.y = 0;
            this.z = 0;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) {
                stop();
                return;
            }
            float g = rafale(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.level.getGameTime());
            this.volume = Mth.clamp(g * 1.1f, 0.01f, 1f);
            this.pitch = 0.55f + 0.3f * g;
            calme = g < 0.02f ? calme + 1 : 0;
            if (calme > 40) {
                stop();
            }
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
