package fr.heraliz.wonder.paralyzer;

import fr.heraliz.wonder.Armes;
import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.Regles;
import fr.heraliz.wonder.WonderConfig;
import fr.heraliz.wonder.serveur.Degats;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Le rayon du Paralyzer, cote serveur (seul juge). TaCZ annonce chaque impulsion ({@code GunShootEvent}, 10 par seconde
 * tant que la detente est tenue et que la charge le permet) ; a chaque impulsion :
 * <ol>
 *   <li>un seul lancer de rayon contre les blocs (le rayon s'arrete au premier bloc plein, jamais rien de casse) ;</li>
 *   <li>une recherche d'entites bornee par la boite du segment, un test « distance a l'axe » par candidate, les plus
 *   proches d'abord, au plus {@code cibles} ;</li>
 *   <li>chaque creature touchee accumule de l'exposition (table fixe, sans allocation) : au seuil de gel elle se FIGE
 *   (sans IA, immobile, rendue figee et lumineuse chez les clients), au seuil de poussiere elle MEURT (type de degats
 *   {@code heraliz_wonder:paralysie}, le tireur en cause : points et kills du mode zombies) et tombe en poussiere ;
 *   un boss (plus de {@code pvMaxPoussiere} PV max) est seulement ralenti et blesse ;</li>
 *   <li>si le tireur vise le sol sous ses pieds (assez bas, assez pres), il flotte (paquet {@link ReseauParalyseur.Flotte})
 *   et le serveur ne le prend ni pour un tricheur volant ni pour une chute.</li>
 * </ol>
 * Jamais un joueur touche, ni hors des creatures de la partie en zombies, ni hors des monstres ailleurs
 * ({@link Regles#estCible}). Une creature que le rayon lache degele apres {@code degelTicks}.
 */
public final class RayonServeur {
    private RayonServeur() {
    }

    public static final ResourceKey<DamageType> PARALYSIE = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(HeralizWonder.MODID, "paralysie"));

    // ------------------------------------------------------------------ exposition (table fixe)
    private static final int MAX = 256;
    private static final LivingEntity[] E = new LivingEntity[MAX];
    private static final int[] EXPO = new int[MAX];
    private static final long[] DERNIER = new long[MAX];
    private static final boolean[] GELE = new boolean[MAX], IA_AVANT = new boolean[MAX];
    private static final byte[] PAP = new byte[MAX];
    private static int n;
    private static long horloge;

    // tampons de tri des candidates (fil serveur unique)
    private static final int MAX_CANDIDATS = 128;
    private static final LivingEntity[] C_E = new LivingEntity[MAX_CANDIDATS];
    private static final double[] C_S = new double[MAX_CANDIDATS];

    private static final String NBT_SURCHAUFFE = "heraliz_wonder:pz_surchauffe";
    /** Posee sur une creature figee (avec son etat d'IA d'avant) : si le serveur s'arrete pendant le gel, elle degele au retour. */
    private static final String NBT_GELE = "heraliz_wonder:pz_gele", NBT_IA = "heraliz_wonder:pz_ia_avant";

    // ------------------------------------------------------------------ reglages (valeurs par defaut si la section manque)
    static double d(ForgeConfigSpec.DoubleValue v, double def) {
        return v == null ? def : WonderConfig.lire(v);
    }

    static int i(ForgeConfigSpec.IntValue v, int def) {
        return v == null ? def : WonderConfig.lire(v);
    }

    static boolean b(ForgeConfigSpec.BooleanValue v, boolean def) {
        return v == null ? def : WonderConfig.lire(v);
    }

    public static double portee(int pap) {
        return CalculsParalyseur.portee(pap, d(ConfigParalyseur.PORTEE, 24), d(ConfigParalyseur.PORTEE_PAP, 30));
    }

    // ------------------------------------------------------------------ une impulsion
    public static void impulsion(ServerLevel niveau, LivingEntity tireur, int pap) {
        long t0 = System.nanoTime();
        boolean zombies = Regles.enZombies(tireur);
        Vec3 o = tireur.getEyePosition();
        Vec3 d = tireur.getLookAngle();
        double portee = portee(pap);
        double r = CalculsParalyseur.rayon(pap, d(ConfigParalyseur.RAYON, 0.6), d(ConfigParalyseur.RAYON_PAP, 0.85));
        Vec3 bout = o.add(d.scale(portee));
        BlockHitResult bh = niveau.clip(new ClipContext(o, bout, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, tireur));
        boolean sol = bh.getType() == HitResult.Type.BLOCK;
        double longueur = sol ? bh.getLocation().distanceTo(o) : portee;
        // creatures le long du rayon, les plus proches d'abord
        AABB boite = new AABB(o, o.add(d.scale(longueur))).inflate(r + 1.5);
        List<LivingEntity> l = niveau.getEntitiesOfClass(LivingEntity.class, boite, e -> Regles.estCible(e, tireur, zombies));
        int nc = 0;
        for (int k = 0; k < l.size() && nc < MAX_CANDIDATS; k++) {
            LivingEntity e = l.get(k);
            double marge = r + e.getBbWidth() * 0.5 + Math.min(e.getBbHeight() * 0.35, 0.8);
            double s = CalculsParalyseur.dansRayon(e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), o.x, o.y, o.z, d.x, d.y, d.z, longueur, marge);
            if (s < 0) continue;
            int j = nc++;
            while (j > 0 && C_S[j - 1] > s) {
                C_S[j] = C_S[j - 1];
                C_E[j] = C_E[j - 1];
                j--;
            }
            C_S[j] = s;
            C_E[j] = e;
        }
        int max = pap > 0 ? i(ConfigParalyseur.CIBLES_PAP, 20) : i(ConfigParalyseur.CIBLES, 12);
        DamageSource src = null;
        int touchees = 0;
        for (int k = 0; k < nc; k++) {
            LivingEntity e = C_E[k];
            C_E[k] = null;
            if (touchees >= max || !e.isAlive()) continue;
            if (src == null) src = Degats.source(niveau, PARALYSIE, tireur);
            exposer(niveau, e, tireur, src, zombies, pap);
            touchees++;
        }
        // flotter : on vise le sol sous ses pieds, assez bas et assez pres
        if (sol && tireur instanceof ServerPlayer p && b(ConfigParalyseur.FLOTTER, true)
                && CalculsParalyseur.viseLeSol(d.y, d(ConfigParalyseur.ANGLE_SOL, 50))
                && tireur.getY() - bh.getLocation().y <= d(ConfigParalyseur.SOL_MAX, 4.0)) {
            flotter(p);
        }
        PerfParalyseur.impulsion(System.nanoTime() - t0, touchees, nc);
    }

    private static int slot(LivingEntity e) {
        for (int k = 0; k < n; k++) if (E[k] == e) return k;
        if (n >= MAX) return -1;
        E[n] = e;
        EXPO[n] = 0;
        GELE[n] = false;
        IA_AVANT[n] = false;
        return n++;
    }

    private static void exposer(ServerLevel niveau, LivingEntity e, LivingEntity tireur, DamageSource src, boolean zombies, int pap) {
        boolean boss = zombies && e.getMaxHealth() > d(ConfigParalyseur.PV_MAX, 4000);
        if (boss) {
            // un boss : ralenti et blesse a chaque impulsion, jamais fige
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 2, false, false));
            Degats.blesser(e, src, (float) d(ConfigParalyseur.DEGATS_COLOSSE, 30) * CalculsParalyseur.multiplicateurColosse(pap), false);
            if (horloge % 4 == 0) ReseauParalyseur.autour(niveau, e.getX(), e.getY(), e.getZ(), new ReseauParalyseur.Etat(e.getId(), ReseauParalyseur.COLOSSE, (byte) pap));
            return;
        }
        int k = slot(e);
        if (k < 0) return;
        EXPO[k]++;
        DERNIER[k] = horloge;
        PAP[k] = (byte) pap;
        int gel = CalculsParalyseur.gel(pap, i(ConfigParalyseur.IMPULSIONS_GEL, 3), i(ConfigParalyseur.IMPULSIONS_GEL_PAP, 2));
        if (!GELE[k] && EXPO[k] >= gel) geler(niveau, k, pap);
        if (!GELE[k]) return;
        boolean meurt;
        if (zombies) {
            int pous = CalculsParalyseur.poussiere(pap, i(ConfigParalyseur.IMPULSIONS_POUSSIERE, 10), i(ConfigParalyseur.IMPULSIONS_POUSSIERE_PAP, 6),
                    i(ConfigParalyseur.IMPULSIONS_GEL_PAP, 2));
            meurt = EXPO[k] >= pous && Degats.blesser(e, src, CalculsParalyseur.degatsPoussiere(e.getHealth()), false) && !e.isAlive();
        } else {
            Degats.blesser(e, src, (float) d(ConfigParalyseur.DEGATS_HORS_ZOMBIES, 6), false);
            meurt = !e.isAlive();
        }
        if (meurt) {
            ReseauParalyseur.autour(niveau, e.getX(), e.getY(), e.getZ(), new ReseauParalyseur.Etat(e.getId(), ReseauParalyseur.POUSSIERE, (byte) pap));
            niveau.playSound(null, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), SonsParalyseur.POUSSIERE.get(), SoundSource.HOSTILE, 1.0f,
                    0.92f + niveau.random.nextFloat() * 0.16f);
            liberer(k);
        }
    }

    private static void geler(ServerLevel niveau, int k, int pap) {
        LivingEntity e = E[k];
        GELE[k] = true;
        if (e instanceof Mob m) {
            IA_AVANT[k] = m.isNoAi();
            m.setNoAi(true);
            e.getPersistentData().putBoolean(NBT_GELE, true);
            e.getPersistentData().putBoolean(NBT_IA, IA_AVANT[k]);
        }
        e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
        e.hurtMarked = true;
        ReseauParalyseur.autour(niveau, e.getX(), e.getY(), e.getZ(), new ReseauParalyseur.Etat(e.getId(), ReseauParalyseur.GEL, (byte) pap));
        niveau.playSound(null, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), SonsParalyseur.GEL.get(), SoundSource.HOSTILE, 0.9f,
                0.95f + niveau.random.nextFloat() * 0.1f);
    }

    private static void degeler(int k) {
        LivingEntity e = E[k];
        if (e instanceof Mob m && e.isAlive() && !e.isRemoved()) m.setNoAi(IA_AVANT[k]);
        e.getPersistentData().remove(NBT_GELE);
        e.getPersistentData().remove(NBT_IA);
        if (e.level() instanceof ServerLevel niveau && e.isAlive()) {
            ReseauParalyseur.autour(niveau, e.getX(), e.getY(), e.getZ(), new ReseauParalyseur.Etat(e.getId(), ReseauParalyseur.DEGEL, PAP[k]));
        }
    }

    /** Retire l'entree k (la derniere prend sa place). */
    private static void liberer(int k) {
        n--;
        E[k] = E[n];
        EXPO[k] = EXPO[n];
        DERNIER[k] = DERNIER[n];
        GELE[k] = GELE[n];
        IA_AVANT[k] = IA_AVANT[n];
        PAP[k] = PAP[n];
        E[n] = null;
    }

    // ------------------------------------------------------------------ flotter
    private static Field compteurVol;
    private static boolean compteurCherche;

    private static void flotter(ServerPlayer p) {
        float h = (float) d(ConfigParalyseur.HAUTEUR_FLOTTE, 2.2);
        ReseauParalyseur.a(p, new ReseauParalyseur.Flotte(h));
        p.resetFallDistance();
        // le serveur ne doit pas prendre ce flottement pour un vol interdit (compteur de ticks en l'air de la connexion)
        if (!compteurCherche) {
            compteurCherche = true;
            try {
                compteurVol = ObfuscationReflectionHelper.findField(ServerGamePacketListenerImpl.class, "f_9737_");
            } catch (RuntimeException ex) {
                HeralizWonder.LOG.warn("[wonder] paralyzer : compteur de vol introuvable ({}), le flottement reste court", ex.toString());
            }
        }
        if (compteurVol != null && p.connection != null) {
            try {
                compteurVol.setInt(p.connection, 0);
            } catch (IllegalAccessException | RuntimeException ex) {
                compteurVol = null;
            }
        }
    }

    // ------------------------------------------------------------------ fin de tick : degel, menage
    public static void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        horloge++;
        if (n == 0) return;
        int degel = i(ConfigParalyseur.DEGEL, 30);
        for (int k = n - 1; k >= 0; k--) {
            LivingEntity e = E[k];
            if (e == null || e.isRemoved() || !e.isAlive()) {
                liberer(k);
                continue;
            }
            long age = horloge - DERNIER[k];
            if (GELE[k] && age > degel) {
                degeler(k);
                liberer(k);
            } else if (!GELE[k] && age > 20) {
                // exposition qui retombe : touchee une fois en passant, elle oublie
                liberer(k);
            } else if (GELE[k]) {
                // figee : elle ne glisse ni ne tombe lentement (la gravite reste, sans IA elle tomberait quand meme)
                e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
            }
        }
    }

    /** Surchauffe et charge revenue : sons joues par le serveur (tout le monde les entend). Verifie tous les 2 ticks. */
    public static void joueur(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || (p.tickCount & 1) != 0) return;
        ItemStack s = p.getMainHandItem();
        CompoundTag pd = p.getPersistentData();
        boolean avant = pd.getBoolean(NBT_SURCHAUFFE);
        boolean oh = Armes.type(s) == Armes.PARALYSEUR && s.getTag() != null && s.getTag().getBoolean("OverHeated");
        if (oh == avant) return;
        pd.putBoolean(NBT_SURCHAUFFE, oh);
        if (Armes.type(s) != Armes.PARALYSEUR) return;
        if (oh) p.level().playSound(null, p.getX(), p.getEyeY(), p.getZ(), SonsParalyseur.SURCHAUFFE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        else p.level().playSound(null, p.getX(), p.getEyeY(), p.getZ(), SonsParalyseur.PRET.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    /** Une creature revient dans le monde (rechargement de troncon, redemarrage) encore marquee figee : elle degele. */
    public static void rejoindre(net.minecraftforge.event.entity.EntityJoinLevelEvent ev) {
        if (ev.getLevel().isClientSide() || !(ev.getEntity() instanceof Mob m)) return;
        CompoundTag pd = m.getPersistentData();
        if (!pd.contains(NBT_GELE)) return;
        for (int k = 0; k < n; k++) if (E[k] == m) return;
        m.setNoAi(pd.getBoolean(NBT_IA));
        pd.remove(NBT_GELE);
        pd.remove(NBT_IA);
    }

    /** Monde decharge / serveur arrete : on oublie tout (les creatures figees degeleront a leur retour, voir {@link #rejoindre}). */
    public static void vider() {
        for (int k = 0; k < n; k++) E[k] = null;
        n = 0;
    }

    public static MutableComponent etat() {
        int g = 0;
        for (int k = 0; k < n; k++) if (GELE[k]) g++;
        return Component.translatableWithFallback("heraliz_wonder.banc.paralyzer_etat", "%s créatures exposées dont %s figées",
                String.valueOf(n), String.valueOf(g));
    }
}
