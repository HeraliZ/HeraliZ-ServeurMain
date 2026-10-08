package fr.heraliz.wonder.wintershowl;

import fr.heraliz.wonder.Calculs;
import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.Regles;
import fr.heraliz.wonder.serveur.Degats;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

import java.util.List;
import java.util.UUID;

/**
 * Souffle de givre du Winter's Howl, decide par le serveur seul.
 * <p><b>Tir</b> : un cone court ({@code portee} blocs, {@code demiAngle}) devant le tireur ; chaque creature valable
 * ({@link Regles#estCible} : jamais un joueur ; en zombies seulement les creatures de la partie, ailleurs les monstres) dans
 * le cone et en vue directe recoit du <b>gel</b> quand le front de givre l'atteint (0 a 6 ticks selon la distance) : tout
 * de suite gelee pres de la bouche, moins au bout du cone (deux tirs). Une seule recherche d'entites bornee par la boite du
 * cone, un test de cone par candidate, un lancer de rayon par creature retenue, un seul paquet.
 * <p><b>Gel</b> : sous le gel complet la creature ralentit (modificateur de vitesse transitoire, un par niveau, crees une
 * fois) puis le gel fond apres {@code dureeFroid} ; au gel complet elle se fige sur place (sans IA), et vole en eclats
 * apres {@code delaiEclat}, ou tout de suite si un joueur la touche (balle, couteau, explosion, autre souffle) : degats
 * mortels en partie de zombies (type {@code heraliz_wonder:givre}, source causale = le tireur ou celui qui la brise : le
 * mode zombies paie le kill). Les boss (PV max &gt; {@code pvMaxGel}) ne gelent jamais : ralentis et {@code degatsColosse}.
 * <p>Tables de taille fixe (256 creatures suivies, file de 512 coups), aucune allocation par tick ; aucun bloc casse.
 */
public final class Givre {
    private Givre() {
    }

    public static final ResourceKey<DamageType> GIVRE = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(HeralizWonder.MODID, "givre"));

    // ------------------------------------------------------------------ creatures suivies (refroidies ou gelees)
    static final int MAX = 256;
    private static final LivingEntity[] E = new LivingEntity[MAX];
    private static final LivingEntity[] AUTEUR = new LivingEntity[MAX];
    private static final float[] GEL = new float[MAX];
    private static final long[] DERNIER = new long[MAX];
    private static final long[] FIN = new long[MAX];
    private static final byte[] PAP = new byte[MAX];
    private static final byte[] NIVEAU = new byte[MAX];
    private static final boolean[] GELEE = new boolean[MAX], AVAIT_NO_AI = new boolean[MAX], ZOMBIES = new boolean[MAX], BOSS = new boolean[MAX];
    private static int n;
    private static long horloge;
    /** Vrai pendant nos propres degats (eclatement, coup sur un boss) : {@link #attaque} les laisse passer. */
    private static boolean nosDegats;

    // ------------------------------------------------------------------ file des coups (arrivee du front de givre)
    private static final int FILE = 512;
    private static final LivingEntity[] F_E = new LivingEntity[FILE], F_T = new LivingEntity[FILE];
    private static final float[] F_GAIN = new float[FILE];
    private static final byte[] F_PAP = new byte[FILE];
    private static final boolean[] F_Z = new boolean[FILE];
    private static final long[] F_TICK = new long[FILE];
    private static int fDebut, fTaille;

    // ------------------------------------------------------------------ tampons du tir (fil serveur unique)
    private static final int MAX_CANDIDATS = 256;
    private static final LivingEntity[] C_E = new LivingEntity[MAX_CANDIDATS];
    private static final double[] C_S = new double[MAX_CANDIDATS];

    // ------------------------------------------------------------------ ralentissement : un modificateur par niveau
    private static final UUID UUID_RALENTI = UUID.fromString("6c1e0b3a-5f2d-4c8e-9a71-2b4f8d0e7c15");
    private static final AttributeModifier[] MODS = new AttributeModifier[11];

    static {
        for (int k = 1; k <= 10; k++) {
            MODS[k] = new AttributeModifier(UUID_RALENTI, "heraliz_wonder:givre", k == 10 ? -1.0 : -0.1 * k, AttributeModifier.Operation.MULTIPLY_TOTAL);
        }
    }

    public static double portee(int pap) {
        return CalculsGivre.portee(pap, ConfigGivre.d(ConfigGivre.PORTEE, 11), ConfigGivre.d(ConfigGivre.PORTEE_PAP, 15));
    }

    public static double angle(int pap) {
        return CalculsGivre.angle(pap, ConfigGivre.d(ConfigGivre.ANGLE, 16), ConfigGivre.d(ConfigGivre.ANGLE_PAP, 22));
    }

    public static int delaiEclat(int pap) {
        return CalculsGivre.delaiEclat(pap, ConfigGivre.d(ConfigGivre.DELAI_ECLAT, 2.5), ConfigGivre.d(ConfigGivre.DELAI_ECLAT_PAP, 2.0));
    }

    // ================================================================== tir
    /** Un tir : {@code depart} = yeux du tireur (depart de la balle TaCZ), {@code dir} = direction du regard. */
    public static void tirer(ServerLevel niveau, LivingEntity tireur, Vec3 depart, Vec3 dir, int pap) {
        long t0 = System.nanoTime();
        boolean zombies = Regles.enZombies(tireur);
        double portee = portee(pap), angle = angle(pap), r0 = ConfigGivre.d(ConfigGivre.BOUCHE, 0.9);
        Vec3 d = dir.lengthSqr() < 1.0E-6 ? tireur.getLookAngle() : dir.normalize();
        double rmax = r0 + portee * Math.tan(Math.toRadians(angle));
        AABB boite = new AABB(depart, depart.add(d.scale(portee))).inflate(rmax + 1.0);
        List<LivingEntity> l = niveau.getEntitiesOfClass(LivingEntity.class, boite, e -> Regles.estCible(e, tireur, zombies));
        int nc = 0;
        for (int i = 0; i < l.size() && nc < MAX_CANDIDATS; i++) {
            LivingEntity e = l.get(i);
            double cy = e.getY() + e.getBbHeight() * 0.5;
            double marge = e.getBbWidth() * 0.5 + Math.min(e.getBbHeight() * 0.25, 0.6);
            double s = Calculs.dansCone(e.getX() - depart.x, cy - depart.y, e.getZ() - depart.z, d.x, d.y, d.z, portee, angle, r0, marge);
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
        int max = Math.min(ConfigGivre.i(ConfigGivre.CIBLES, 32), ReseauGivre.Souffle.MAX);
        double zone = pap > 0 ? ConfigGivre.d(ConfigGivre.ZONE_GEL_PAP, 0.6) : ConfigGivre.d(ConfigGivre.ZONE_GEL, 0.4);
        double bout = pap > 0 ? ConfigGivre.d(ConfigGivre.GAIN_LOIN_PAP, 0.85) : ConfigGivre.d(ConfigGivre.GAIN_LOIN, 0.5);
        double seuil = ConfigGivre.d(ConfigGivre.PV_MAX_GEL, 4000);
        int k = 0, lancers = 0, gelees = 0;
        int m = Math.min(nc, max);
        int[] ids = new int[m];
        byte[] gels = new byte[m], arr = new byte[m];
        for (int c = 0; c < nc && k < max; c++) {
            LivingEntity e = C_E[c];
            C_E[c] = null;
            if (!e.isAlive()) continue;
            lancers++;
            if (!aDecouvert(niveau, depart, e, tireur)) continue;
            double s = C_S[c];
            float g = CalculsGivre.gain(s, portee, zone, bout);
            boolean peut = CalculsGivre.peutGeler(e.getMaxHealth(), seuil);
            int i = trouver(e);
            boolean dejaGelee = i >= 0 && GELEE[i];
            float apres = dejaGelee ? 1f : CalculsGivre.cumuler(i >= 0 ? GEL[i] : 0f, g, peut);
            int a = CalculsGivre.arrivee(s, portee);
            programmer(e, tireur, g, pap, zombies, a);
            ids[k] = e.getId();
            gels[k] = (byte) (dejaGelee || apres >= 1f ? 255 : Math.round(apres * 254));
            arr[k] = (byte) a;
            if (apres >= 1f) gelees++;
            k++;
        }
        for (int c = 0; c < nc; c++) C_E[c] = null;
        ReseauGivre.autour(niveau, tireur.getX(), tireur.getY(), tireur.getZ(), new ReseauGivre.Souffle(tireur.getId(), (byte) pap, (float) depart.x,
                (float) depart.y, (float) depart.z, (float) d.x, (float) d.y, (float) d.z, (float) portee, (float) angle, (float) r0, delaiEclat(pap), k, ids,
                gels, arr));
        PerfGivre.tir(System.nanoTime() - t0, k, gelees, lancers);
    }

    private static void programmer(LivingEntity e, LivingEntity tireur, float gain, int pap, boolean zombies, int delai) {
        if (delai <= 0 || fTaille >= FILE) {
            appliquer(e, tireur, gain, pap, zombies);
            return;
        }
        int i = (fDebut + fTaille) % FILE;
        F_E[i] = e;
        F_T[i] = tireur;
        F_GAIN[i] = gain;
        F_PAP[i] = (byte) pap;
        F_Z[i] = zombies;
        F_TICK[i] = horloge + delai;
        fTaille++;
    }

    /** Le front de givre atteint la creature : gel cumule, ralentissement, gel complet ; une creature deja gelee se brise. */
    private static void appliquer(LivingEntity e, LivingEntity tireur, float gain, int pap, boolean zombies) {
        if (!e.isAlive() || e.isRemoved() || !(e.level() instanceof ServerLevel niveau)) return;
        int i = trouver(e);
        if (i >= 0 && GELEE[i]) {
            AUTEUR[i] = tireur;
            FIN[i] = horloge;
            return;
        }
        boolean peut = CalculsGivre.peutGeler(e.getMaxHealth(), ConfigGivre.d(ConfigGivre.PV_MAX_GEL, 4000));
        if (i < 0) {
            if (n >= MAX) return;
            i = n++;
            E[i] = e;
            GEL[i] = 0;
            GELEE[i] = false;
            FIN[i] = 0;
            NIVEAU[i] = 0;
            AVAIT_NO_AI[i] = e instanceof Mob mob && mob.isNoAi();
            BOSS[i] = !peut;
        }
        AUTEUR[i] = tireur;
        PAP[i] = (byte) pap;
        DERNIER[i] = horloge;
        ZOMBIES[i] = zombies;
        GEL[i] = CalculsGivre.cumuler(GEL[i], gain, peut);
        if (!peut) {
            blesser(e, Degats.source(niveau, GIVRE, tireur), CalculsGivre.degatsColosse((float) ConfigGivre.d(ConfigGivre.DEGATS_COLOSSE, 300), pap));
            if (!e.isAlive()) {
                retirer(i, false);
                return;
            }
            ralentir(i, CalculsGivre.niveauRalenti(GEL[i], ConfigGivre.d(ConfigGivre.RALENTI_COLOSSE, 0.5)));
            return;
        }
        if (GEL[i] >= 1f) geler(i);
        else ralentir(i, CalculsGivre.niveauRalenti(GEL[i], ConfigGivre.d(ConfigGivre.RALENTI_MAX, 0.8)));
    }

    /** Gel complet : la creature se fige (sans IA, vitesse nulle) jusqu'a l'eclatement. */
    private static void geler(int i) {
        LivingEntity e = E[i];
        GELEE[i] = true;
        GEL[i] = 1f;
        FIN[i] = horloge + delaiEclat(PAP[i]);
        // le mode zombies fait lui-meme arracher les planches et enjamber les fenetres : gele la, il eclate presque tout de suite
        if (ZOMBIES[i] && MatchsGivre.contreFenetre(e)) FIN[i] = Math.min(FIN[i], horloge + FENETRE_ECLAT);
        if (e instanceof Mob mob) {
            mob.setNoAi(true);
            mob.getNavigation().stop();
        }
        e.setDeltaMovement(0, 0, 0);
        e.hurtMarked = true;
        ralentir(i, 10);
        PerfGivre.gel();
    }

    /** Eclatement : degats (mortels en zombies) ; morte -> paquet d'eclats, sinon elle degele (hors zombies). */
    private static void briser(int i) {
        LivingEntity e = E[i];
        LivingEntity auteur = AUTEUR[i];
        if (!(e.level() instanceof ServerLevel niveau)) {
            retirer(i, true);
            return;
        }
        long t0 = System.nanoTime();
        float dmg = CalculsGivre.degatsEclat(ZOMBIES[i], e.getMaxHealth(), e.getHealth(), (float) ConfigGivre.d(ConfigGivre.DEGATS_HORS_ZOMBIES, 40), PAP[i]);
        blesser(e, Degats.source(niveau, GIVRE, auteur), dmg);
        if (!e.isAlive()) {
            double h = e.getBbHeight();
            ReseauGivre.autour(niveau, e.getX(), e.getY(), e.getZ(), new ReseauGivre.Eclat(e.getId(), (float) e.getX(), (float) (e.getY() + h * 0.5), (float) e.getZ(),
                    e.getBbWidth(), (float) h, PAP[i]));
            // le corps n'est plus qu'eclats : il disparait au tick suivant (pas d'animation de mort ni de fumee)
            if (nRetirer < A_RETIRER.length) A_RETIRER[nRetirer++] = e;
            retirer(i, false);
            PerfGivre.eclat(System.nanoTime() - t0);
        } else {
            ReseauGivre.autour(niveau, e.getX(), e.getY(), e.getZ(), new ReseauGivre.Degel(e.getId()));
            retirer(i, true);
        }
    }

    /** Ticks avant l'eclatement d'un zombie gele contre une fenetre (le mode zombies le deplace sans son IA). */
    static final int FENETRE_ECLAT = 10;

    private static final LivingEntity[] A_RETIRER = new LivingEntity[128];
    private static int nRetirer;

    private static void blesser(LivingEntity e, DamageSource src, float montant) {
        nosDegats = true;
        try {
            Degats.blesser(e, src, montant, false);
        } finally {
            nosDegats = false;
        }
    }

    private static void ralentir(int i, int niveau) {
        if (NIVEAU[i] == niveau) return;
        AttributeInstance a = E[i].getAttribute(Attributes.MOVEMENT_SPEED);
        NIVEAU[i] = (byte) niveau;
        if (a == null) return;
        a.removeModifier(UUID_RALENTI);
        if (niveau > 0) a.addTransientModifier(MODS[niveau]);
    }

    /** Retire l'entree i (la derniere prend sa place) ; {@code rendre} : la creature vivante retrouve vitesse et IA. */
    private static void retirer(int i, boolean rendre) {
        LivingEntity e = E[i];
        if (e != null && rendre && e.isAlive()) {
            ralentir(i, 0);
            if (GELEE[i] && e instanceof Mob mob) mob.setNoAi(AVAIT_NO_AI[i]);
        } else if (e != null && NIVEAU[i] != 0) {
            AttributeInstance a = e.getAttribute(Attributes.MOVEMENT_SPEED);
            if (a != null) a.removeModifier(UUID_RALENTI);
        }
        int j = --n;
        E[i] = E[j];
        AUTEUR[i] = AUTEUR[j];
        GEL[i] = GEL[j];
        DERNIER[i] = DERNIER[j];
        FIN[i] = FIN[j];
        PAP[i] = PAP[j];
        NIVEAU[i] = NIVEAU[j];
        GELEE[i] = GELEE[j];
        AVAIT_NO_AI[i] = AVAIT_NO_AI[j];
        ZOMBIES[i] = ZOMBIES[j];
        BOSS[i] = BOSS[j];
        E[j] = null;
        AUTEUR[j] = null;
    }

    private static int trouver(Entity e) {
        for (int i = 0; i < n; i++) if (E[i] == e) return i;
        return -1;
    }

    /** La creature est-elle gelee ? (banc, client integre) */
    public static boolean estGelee(Entity e) {
        int i = trouver(e);
        return i >= 0 && GELEE[i];
    }

    /** Pas de bloc plein entre la bouche et le milieu de la creature. */
    private static boolean aDecouvert(ServerLevel niveau, Vec3 depuis, Entity e, Entity contexte) {
        Vec3 vers = new Vec3(e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ());
        if (depuis.distanceToSqr(vers) < 0.36) return true;
        return niveau.clip(new ClipContext(depuis, vers, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, contexte != null ? contexte : e)).getType()
                == HitResult.Type.MISS;
    }

    // ================================================================== evenements
    /**
     * Une creature gelee touchee par un joueur (balle, couteau, explosion, autre souffle) vole en eclats tout de suite : le
     * coup est remplace par l'eclatement (a la fin du tick), credite a celui qui frappe.
     */
    public static void attaque(LivingAttackEvent ev) {
        if (n == 0 || nosDegats) return;
        LivingEntity cible = ev.getEntity();
        if (cible.level().isClientSide) return;
        int i = trouver(cible);
        if (i < 0 || !GELEE[i]) return;
        DamageSource s = ev.getSource();
        if (s.is(GIVRE)) return;
        Entity auteur = s.getEntity();
        boolean coup = auteur instanceof Player || s.getDirectEntity() instanceof Player || s.is(DamageTypeTags.IS_PROJECTILE) || s.is(DamageTypeTags.IS_EXPLOSION);
        if (!coup) return;
        ev.setCanceled(true);
        if (auteur instanceof ServerPlayer sp) AUTEUR[i] = sp;
        FIN[i] = horloge;
    }

    public static void sortie(EntityLeaveLevelEvent ev) {
        if (n == 0 || ev.getLevel().isClientSide() || !(ev.getEntity() instanceof LivingEntity e)) return;
        int i = trouver(e);
        if (i >= 0) retirer(i, true);
    }

    public static void arret(ServerStoppingEvent ev) {
        while (n > 0) retirer(n - 1, true);
        for (int k = 0; k < FILE; k++) {
            F_E[k] = null;
            F_T[k] = null;
        }
        fTaille = 0;
        nRetirer = 0;
    }

    /** Fin de tick serveur : coups arrives, gel qui fond, eclatements. */
    public static void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        horloge++;
        if (nRetirer > 0) {
            for (int k = 0; k < nRetirer; k++) {
                LivingEntity e = A_RETIRER[k];
                A_RETIRER[k] = null;
                if (e != null && !e.isRemoved() && !e.isAlive()) e.remove(Entity.RemovalReason.KILLED);
            }
            nRetirer = 0;
        }
        if (fTaille == 0 && n == 0) return;
        long t0 = System.nanoTime();
        if (fTaille > 0) {
            int nf = fTaille, garde = 0;
            for (int k = 0; k < nf; k++) {
                int i = (fDebut + k) % FILE;
                if (F_TICK[i] <= horloge) {
                    try {
                        appliquer(F_E[i], F_T[i], F_GAIN[i], F_PAP[i], F_Z[i]);
                    } catch (RuntimeException ex) {
                        HeralizWonder.LOG.error("[wonder] givre du Winter's Howl", ex);
                    }
                } else {
                    int j = (fDebut + garde) % FILE;
                    F_E[j] = F_E[i];
                    F_T[j] = F_T[i];
                    F_GAIN[j] = F_GAIN[i];
                    F_PAP[j] = F_PAP[i];
                    F_Z[j] = F_Z[i];
                    F_TICK[j] = F_TICK[i];
                    garde++;
                }
            }
            for (int k = garde; k < nf; k++) {
                int i = (fDebut + k) % FILE;
                F_E[i] = null;
                F_T[i] = null;
            }
            fTaille = garde;
        }
        double froid = ConfigGivre.d(ConfigGivre.DUREE_FROID, 3.0) * 20, fonte = ConfigGivre.d(ConfigGivre.FONTE, 0.35) / 20;
        double rMax = ConfigGivre.d(ConfigGivre.RALENTI_MAX, 0.8), rBoss = ConfigGivre.d(ConfigGivre.RALENTI_COLOSSE, 0.5);
        for (int i = 0; i < n; ) {
            LivingEntity e = E[i];
            if (e == null || e.isRemoved() || !e.isAlive()) {
                retirer(i, false);
                continue;
            }
            if (GELEE[i]) {
                if (horloge >= FIN[i]) {
                    try {
                        briser(i);
                    } catch (RuntimeException ex) {
                        HeralizWonder.LOG.error("[wonder] eclatement du Winter's Howl", ex);
                        retirer(i, true);
                    }
                    continue;
                }
                i++;
                continue;
            }
            float g = CalculsGivre.fondre(GEL[i], horloge - DERNIER[i], froid, fonte);
            if (g <= 0f) {
                retirer(i, true);
                continue;
            }
            GEL[i] = g;
            ralentir(i, CalculsGivre.niveauRalenti(g, BOSS[i] ? rBoss : rMax));
            i++;
        }
        PerfGivre.suivi(System.nanoTime() - t0, n);
    }

    /** Etat pour le banc : creatures suivies, gelees, file. */
    public static String etat() {
        int g = 0;
        for (int i = 0; i < n; i++) if (GELEE[i]) g++;
        return n + " creatures suivies (" + g + " gelees), " + fTaille + " coups en route";
    }
}
