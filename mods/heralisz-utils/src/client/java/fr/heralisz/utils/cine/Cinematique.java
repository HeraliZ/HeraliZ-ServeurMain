package fr.heralisz.utils.cine;

import com.mojang.authlib.GameProfile;
import fr.heralisz.utils.HeraliszUtils;
import fr.heralisz.utils.train.PlateauRendu;
import fr.heralisz.utils.train.TrainSons;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Cinématique d'arrivée sur HeraliZ (côté client, ~57 s, ESPACE maintenu = passer), jouée sur le plateau de la vraie rame
 * (PlateauRendu : voiture de queue, voiture du joueur, motrice ; tunnel sans fin, puis la grotte du terminus), avec des
 * acteurs faits sur mesure (CineActeurs : goules articulées, corps du joueur, sang et lambeaux sur les vitres) et des sons
 * faits sur mesure (CineSons, tools/gen_cine_sons.py : aucun son vanilla). Déroulé :
 * 1. dans la voiture vide qui file dans le noir, on avance entre les banquettes, on s'approche d'une vitre ;
 * 2. une goule s'écrase contre la vitre, le visage collé au verre (éclaboussure), elle frappe, le verre s'étoile ;
 * 3. on recule et on tombe à la renverse sur une banquette : on voit ses jambes et ses mains, le cœur cogne ;
 * 4. un pilier du tunnel accroche la goule : son visage glisse sur la vitre, se râpe, la tête s'arrache petit à petit ;
 *    il reste une traînée de sang et des morceaux collés au verre ;
 * 5. on se relève, on traverse jusqu'à la motrice : la salle, des cadavres, un zombie accroupi qui dévore quelqu'un ;
 * 6. la cabine : le conducteur, de dos ; « Hé ! » ; seule sa tête se retourne, lentement ; gros plan, il hurle ;
 * 7. plans larges : le tunnel débouche dans la grotte du terminus, la rame ne freine pas ; elle déraille, laboure le
 *    ballast, percute le heurtoir et la paroi, les voitures se mettent en portefeuille, explosion ;
 * 8. on voit son personnage éjecté, au ralenti ; noir ; acouphène, cœur qui ralentit ;
 * 9. réveil au spawn (on cligne des yeux, allongé), on se relève, plan du survivant devant l'épave, retour à nos yeux.
 * Caméra = entité invisible ; rien n'est envoyé au serveur. Pour les essais : -Dheralisz.cine.debut=<tick> commence au
 * milieu (chaque scène se met en place toute seule), -Dheralisz.cine.captures=<t1,t2,...> prend des captures.
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, value = Dist.CLIENT)
public final class Cinematique {
    private static final int CHOC = Cinematique.T_DEBUT + Cinematique.POV + 120, NOIR = CHOC + 50, REVEIL = NOIR + 40, FIN = REVEIL + 205;
    private static final float OEIL = 1.62f;
    private static final double HAUT_PLATEAU = 400, VITESSE = 0.7;
    private static final float PL = 1.12f, YEUX = PL + 1.62f;
    // la vitre de la goule (voiture du joueur, côté droit) : baie de x 1,3 à 2,7, de y 2,07 à 3,37, verre en z = 2,12
    private static final double VX = 2.0, VY = PL + 1.6, VZ = 2.12;
    // la motrice (repère du plateau) : porte arrière, cloison du vestibule, porte de la cabine, siège du conducteur
    // (modèle « automotrice », tools/gen_train.py : porte 101 en x -11,07, cloison 105 en -7,3, porte de cabine 104 en
    // 8,2, fauteuil centré en 9,6 / z -0,8, assise à PL + 0,5, nez en 11,66)
    private static final double T = PlateauRendu.TETE, T_ARR = T - 11.07, T_VEST = T - 7.3, T_CAB = T + 8.2, NEZ = 11.66;
    private static final double SIEGE_X = T + 9.58, SIEGE_Z = -0.8, SIEGE_Y = PL + 0.5;
    /** Axe du fauteuil pivotant (groupe « fauteuil » de la motrice). */
    private static final double PIVOT_X = T + 9.55, PIVOT_Z = SIEGE_Z;
    private static boolean actif;
    private static int t, passer, attente;
    private static ClientLevel differe;
    private static Oeil cam;
    private static double bx, by, bz;
    private static Mannequin moi;
    private static Goule fenetre, chauffeur, mangeur;
    private static final List<Goule> CADAVRES = new ArrayList<>();
    private static CineActeurs.Trace impact, fissures, trainee, coulure;
    private static CameraType vueAvant;
    private static Vec3 reveil, dirPuits;
    private static float yawPuits;
    private static double sx, sy, sz;
    private static float flash, noir, secousse, bandes, fov = -1, titre;
    private static float pluieAvant, orageAvant;
    /** Lumière (bloc) des acteurs du plateau ; -1 hors du plateau. */
    static int lumiereActeurs = -1;
    private static String sousTitre = "";
    private static int sousTitreFin;
    private static final RandomSource R = RandomSource.create();
    private static final List<Entity> CREES = new ArrayList<>();
    private static final List<Boucle> BOUCLES = new ArrayList<>();
    private static Boucle roulement, coeur, mastication, gresille, horde, essouffle, souffle;
    private static final List<Goule> HORDE = new ArrayList<>();
    private static double sauteurX, sauteurZ;
    private static boolean porteVestSon;
    /** La caméra est dans le train (qui avance) : son lissage suit le train. */
    private static boolean embarque;
    /** Roulis de la caméra (degrés) : balancement de la course, chute, culbutes de l'éjection. */
    private static float roulisCam;

    private Cinematique() {}

    public static boolean actif() {
        return actif;
    }

    static final class Oeil extends Marker {
        Oeil(Level l) {
            super(EntityType.MARKER, l);
        }

        @Override
        protected float getEyeHeight(Pose p, EntityDimensions d) {
            return OEIL;
        }
    }

    static final class Mannequin extends RemotePlayer {
        Mannequin(ClientLevel l, GameProfile p) {
            super(l, p);
        }

        @Override
        public ResourceLocation getSkinTextureLocation() {
            AbstractClientPlayer p = Minecraft.getInstance().player;
            return p != null ? p.getSkinTextureLocation() : super.getSkinTextureLocation();
        }

        @Override
        public String getModelName() {
            AbstractClientPlayer p = Minecraft.getInstance().player;
            return p != null ? p.getModelName() : super.getModelName();
        }

        @Override
        protected void pushEntities() {
        }
    }

    /** Son qui tourne en boucle pendant la cinématique (roulement, cœur, mastication, néon), volume réglable. */
    static final class Boucle extends AbstractTickableSoundInstance {
        private boolean fini;

        Boucle(SoundEvent e, float v) {
            super(e, SoundSource.MASTER, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            volume = v;
            relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
        }

        void vol(float v) {
            volume = v;
        }

        void couper() {
            fini = true;
        }

        @Override
        public void tick() {
            if (!actif || fini) {
                stop();
            }
        }
    }

    private static Boucle boucle(SoundEvent e, float v) {
        Boucle b = new Boucle(e, v);
        BOUCLES.add(b);
        Minecraft.getInstance().getSoundManager().play(b);
        return b;
    }

    // ---------------------------------------------------------------------------------------------- démarrage / fin
    public static void demarrer() {
        Minecraft mc = Minecraft.getInstance();
        if (actif || mc.level == null || mc.player == null) {
            return;
        }
        if (mc.screen instanceof ReceivingLevelScreen) {
            differe = mc.level;                                   // lancée quand l'écran de chargement se ferme
            return;
        }
        differe = null;
        CineDecor.Etat d = CineDecor.client();
        reveil = d.reveil();
        dirPuits = d.regard();
        yawPuits = d.cap();
        sx = Math.floor(reveil.x) + 0.5;
        sy = HAUT_PLATEAU;
        sz = Math.floor(reveil.z) + 0.5;
        ClientLevel l = mc.level;
        cam = new Oeil(l);
        moi = new Mannequin(l, new GameProfile(UUID.randomUUID(), mc.player.getGameProfile().getName()));
        ajouter(l, -4200, moi, reveil.x, reveil.y, reveil.z, yawPuits);
        moi.setInvisible(true);
        CineActeurs.vider();
        CADAVRES.clear();
        fenetre = chauffeur = mangeur = null;
        impact = fissures = trainee = coulure = null;
        porteVestSon = false;
        PlateauRendu.ox = sx;
        PlateauRendu.oy = sy;
        PlateauRendu.oz = sz;
        PlateauRendu.mode = -1;
        HORDE.clear();
        java.util.Arrays.fill(PlateauRendu.portesLaterales, 0f);
        PlateauRendu.fauteuil = 0f;
        PlateauRendu.parcours = PlateauRendu.parcoursAvant = 0;
        PlateauRendu.train = PlateauRendu.trainAvant = 0;
        PlateauRendu.porteBout = PlateauRendu.porteCloison = 0;
        PlateauRendu.porteArriereTete = PlateauRendu.porteVestTete = PlateauRendu.porteCabine = 0;
        PlateauRendu.dedansTete = 7f;
        PlateauRendu.rameFixe();
        PlateauRendu.phares = 1f;
        PlateauRendu.locoVisible = true;
        PlateauRendu.visible = true;
        lumiereActeurs = 9;
        motrice();
        pluieAvant = l.getRainLevel(1f);
        orageAvant = l.getThunderLevel(1f);
        l.setRainLevel(0f);
        l.setThunderLevel(0f);
        t = 0;
        Integer debut = Integer.getInteger("heralisz.cine.debut");
        if (debut != null) {
            t = Mth.clamp(debut, 0, FIN - 1);
        }
        attente = 0;
        passer = 0;
        noir = 1f;
        flash = bandes = titre = secousse = 0f;
        fov = -1;
        sousTitre = "";
        precharger();
        actif = true;
        vueAvant = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        camera(sx - 6.4, sy + YEUX, sz + 0.2, sx, sy + YEUX - 0.2, sz, 1f);
        mc.setCameraEntity(cam);
    }

    /** Tout ce que la cinématique affichera, chargé d'un coup au départ (l'écran est encore noir) : décors et modèles du
     *  train, squelette des goules, toutes leurs textures, taches et fissures. */
    private static void precharger() {
        long d = System.nanoTime();
        PlateauRendu.precharger();
        GouleModele.get();
        var tm = Minecraft.getInstance().getTextureManager();
        for (String v : new String[]{"fenetre", "fenetre_2", "fenetre_3", "chauffeur", "mangeur", "cadavre_1", "cadavre_2", "cadavre_3",
                "rodeur_1", "rodeur_2"}) {
            for (String s : new String[]{"", "_yeux", "_yeux_rouges"}) {
                tm.getTexture(new ResourceLocation(HeraliszUtils.MODID, "textures/cine/goule_" + v + s + ".png"));
            }
        }
        for (String s : new String[]{"fissures", "lambeaux", "mare_sang", "sang_impact", "sang_trainee"}) {
            tm.getTexture(new ResourceLocation(HeraliszUtils.MODID, "textures/cine/" + s + ".png"));
        }
        HeraliszUtils.LOG.info("Cinématique : préchargée en {} ms", (System.nanoTime() - d) / 1_000_000);
    }

    private static void finir() {
        Minecraft mc = Minecraft.getInstance();
        boolean passe = t < REVEIL + 170;
        if (actif && mc.getConnection() != null) {
            // le serveur note la fin (HeraliZ Quêtes enchaîne : « Allez voir le Commandant Varga »)
            CineReseau.CANAL.sendToServer(new CineReseau.Fin());
        }
        embarque = false;
        actif = false;
        fov = -1;
        lumiereActeurs = -1;
        PlateauRendu.visible = false;
        PlateauRendu.rameFixe();
        CineActeurs.vider();
        CADAVRES.clear();
        HORDE.clear();
        java.util.Arrays.fill(PlateauRendu.portesLaterales, 0f);
        PlateauRendu.fauteuil = 0f;
        PlateauRendu.mode = 0;
        for (Boucle b : BOUCLES) {
            b.couper();
        }
        BOUCLES.clear();
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
            if (passe) {
                regarderLePuits(mc);
            }
        }
        if (vueAvant != null) {
            mc.options.setCameraType(vueAvant);
            vueAvant = null;
        }
        if (mc.level != null) {
            for (Entity e : CREES) {
                mc.level.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
            }
            mc.level.setRainLevel(pluieAvant);
            mc.level.setThunderLevel(orageAvant);
        }
        CREES.clear();
        fenetre = chauffeur = mangeur = null;
        moi = null;
        cam = null;
        roulement = coeur = mastication = gresille = horde = essouffle = souffle = null;
    }

    private static void regarderLePuits(Minecraft mc) {
        if (reveil != null && mc.player.distanceToSqr(reveil) < 64) {
            mc.player.setYRot(yawPuits);
            mc.player.yRotO = yawPuits;
            mc.player.setYHeadRot(yawPuits);
            mc.player.setXRot(-4f);
            mc.player.xRotO = -4f;
        }
    }

    private static void ajouter(ClientLevel l, int id, Entity e, double x, double y, double z, float yaw) {
        e.moveTo(x, y, z, yaw, 0f);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
        e.setId(id);
        if (e instanceof AbstractClientPlayer p) {
            l.addPlayer(id, p);
        } else {
            l.putNonPlayerEntity(id, e);
        }
        CREES.add(e);
    }

    // ---------------------------------------------------------------------------------------------- les acteurs
    /** La motrice : le conducteur assis dans la cabine, de dos ; des cadavres dans la salle, des mares de sang ; le zombie
     *  accroupi dans le coin avant droit, sur un corps. Posés dès le début (ils sont derrière deux portes fermées). */
    private static void motrice() {
        chauffeur = new Goule("chauffeur");
        assis(chauffeur);
        chauffeur.place(SIEGE_X, SIEGE_Y - 0.875, SIEGE_Z, -90f);
        chauffeur.lumiere = 8;
        chauffeur.figer();
        CineActeurs.GOULES.add(chauffeur);
        // les corps : sur les emplacements laissés libres dans la salle (les flaques et traînées sont dans le modèle) ;
        // le dernier, sous le mangeur, dans le coin avant droit
        String[] v = {"cadavre_1", "cadavre_2", "cadavre_3", "cadavre_1"};
        double[][] pos = {{T - 4.45, 0.0, 90f}, {T - 0.65, -0.25, -90f}, {T + 2.75, 0.25, 90f}, {T + 7.05, 1.4, -90f}};
        for (int i = 0; i < 4; i++) {
            Goule c = new Goule(v[i]);
            couche(c, i % 3);
            c.place(pos[i][0], PL + 0.02, pos[i][1], (float) pos[i][2]);
            c.amener("hanches", new Vec3(pos[i][0], PL + 0.14, pos[i][1]));
            c.lumiere = 6;
            c.figer();
            CADAVRES.add(c);
            CineActeurs.GOULES.add(c);
        }
        mangeur = new Goule("mangeur");
        accroupi(mangeur, 0);
        mangeur.place(T + 7.0, PL, 0.45, 0f);                                    // tourné vers le corps, dos à l'allée
        auSol(mangeur, PL);
        mangeur.lumiere = 7;
        mangeur.figer();
        CineActeurs.GOULES.add(mangeur);
    }

    private static void montrerMotrice(boolean v) {
        if (chauffeur != null) {
            chauffeur.visible = v;
        }
        if (mangeur != null) {
            mangeur.visible = v;
        }
        for (Goule g : CADAVRES) {
            g.visible = v;
        }
    }

    /** Pose la goule sur le sol : ses chevilles juste au-dessus (la semelle fait 2 unités du modèle, 0,06 bloc). */
    private static void auSol(Goule g, double sol) {
        double bas = Math.min(g.point("pied_g").y, g.point("pied_d").y);
        g.y += sol + 0.065 - bas;
    }

    private static void assis(Goule g) {
        g.raz();
        for (String c : new String[]{"g", "d"}) {
            g.a("cuisse_" + c, -1.55f, c.equals("g") ? -0.08f : 0.08f, 0);
            g.a("tibia_" + c, 1.5f, 0, 0);
            g.a("bras_" + c, -0.95f, c.equals("g") ? -0.2f : 0.2f, 0);
            g.a("avantbras_" + c, -0.55f, 0, 0);
        }
        g.a("tronc", 0.12f, 0, 0);
    }

    /** Morts allongés dans l'allée, chacun à sa façon (sur le dos, bras en croix ; sur le côté ; recroquevillé). */
    private static void couche(Goule g, int i) {
        g.raz();
        g.tangage = i == 1 ? 90f : -90f;
        g.roulis = i == 2 ? 70f : 0f;
        g.a("tete", 0, i == 0 ? 0.9f : -0.5f, i == 0 ? 0.3f : 0);
        g.a("bras_g", -0.3f, 0, i == 0 ? -1.4f : -0.4f);
        g.a("bras_d", -0.2f, 0, i == 0 ? 1.2f : 0.9f);
        g.a("cuisse_g", i == 2 ? -1.3f : -0.1f, 0, -0.1f);
        g.a("cuisse_d", i == 2 ? -1.1f : 0.15f, 0, 0.12f);
        g.a("tibia_g", i == 2 ? 1.6f : 0.2f, 0, 0);
        g.a("tibia_d", i == 2 ? 1.4f : 0.4f, 0, 0);
        g.machoire = i == 0 ? 0.5f : 0.2f;
    }

    /** Accroupi sur un corps : cuisses repliées, dos rond, la tête plongée dans la viande ; phase : mastication. */
    private static void accroupi(Goule g, float phase) {
        float m = Mth.sin(phase * 0.55f), a = Mth.sin(phase * 0.27f);
        for (String c : new String[]{"g", "d"}) {
            g.a("cuisse_" + c, -2.05f, c.equals("g") ? -0.35f : 0.35f, 0);
            g.a("tibia_" + c, 2.3f, 0, 0);
            g.a("bras_" + c, -1.2f + 0.15f * (c.equals("g") ? m : -m), c.equals("g") ? -0.3f : 0.3f, 0);
            g.a("avantbras_" + c, -0.9f + 0.25f * a, 0, 0);
        }
        g.a("tronc", 0.85f + 0.06f * m, 0, 0.05f * a);
        g.a("cou", 0.5f, 0, 0);
        g.a("tete", 0.35f - 0.35f * Math.max(0, m), 0.1f * a, 0);                // il arrache, il relève la tête, il mâche
        g.machoire = 0.35f + 0.3f * Math.abs(Mth.sin(phase * 1.1f));
        g.d("hanches", 0, 9, 0);                                                    // les hanches au ras des talons
    }

    /** La goule de la vitre, accrochée au flanc de la voiture : bras levés, mains à plat sur le verre, le visage collé. */
    private static void accrochee(Goule g, float rage) {
        float f = Mth.sin(t * 0.9f) * rage;
        for (String c : new String[]{"g", "d"}) {
            g.a("main_" + c, 0.4f, 0, 0);
            for (int i = 0; i < 4; i++) {
                g.a("doigt_" + c + i, -0.2f, 0, (c.equals("g") ? -1 : 1) * 0.12f * (i - 1.5f));
            }
            g.a("cuisse_" + c, -0.5f, 0, 0);
            g.a("tibia_" + c, 0.9f, 0, 0);
        }
        g.a("tronc", 0.2f, 0, 0.04f * f);
        g.a("cou", 0.05f, 0, 0);
        g.a("tete", 0.15f, 0.06f * f, 0.05f * f);
    }

    /** Les mains de la goule sur la vitre : de part et d'autre du visage ; frappe (0..1) : la main se décolle et revient. */
    private static void mainsVitre(Goule g, float frappeG, float frappeD) {
        g.bras("g", new Vec3(VX - 0.38, VY + 0.16, VZ + 0.24 + 0.35 * frappeG));
        g.bras("d", new Vec3(VX + 0.62, VY + 0.12, VZ + 0.24 + 0.35 * frappeD));
    }

    // ---------------------------------------------------------------------------------------------- outils
    private static float lisse(float a) {
        a = Mth.clamp(a, 0f, 1f);
        return a * a * (3 - 2 * a);
    }

    private static float k(int a, int b) {
        return lisse((t - a) / (float) (b - a));
    }

    private static void plan(double x, double y, double z, double tx, double ty, double tz, float lissage) {
        camera(sx + x, sy + y, sz + z, sx + tx, sy + ty, sz + tz, lissage);
    }

    private static void camera(double x, double y, double z, double tx, double ty, double tz, float lissage) {
        boolean coupe = lissage >= 1f;
        if (embarque && !coupe) {                                   // dans le train qui roule : on lisse par rapport au train
            double d = PlateauRendu.train - PlateauRendu.trainAvant;
            bx += d;
        }
        bx = coupe ? x : Mth.lerp(lissage, bx, x);
        by = coupe ? y : Mth.lerp(lissage, by, y);
        bz = coupe ? z : Mth.lerp(lissage, bz, z);
        double nx = bx, ny = by, nz = bz;
        if (secousse > 0) {                                         // tremblement continu (3-5 Hz), interpolé entre les ticks
            double s = secousse * 0.035, u = t;
            nx += Math.sin(u * 1.05 + 1.3) * Math.sin(u * 0.37) * s;
            ny += Math.sin(u * 1.31 + 0.4) * Math.sin(u * 0.53 + 2.0) * s;
            nz += Math.sin(u * 0.93 + 2.6) * Math.sin(u * 0.41 + 1.0) * s;
        }
        double dx = tx - bx, dy = ty - by, dz = tz - bz;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) (-(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG));
        orienter(nx, ny, nz, yaw, pitch, coupe ? 1f : Math.min(1f, lissage * 1.5f), coupe);
    }

    private static void orienter(double x, double y, double z, float yaw, float pitch, float f, boolean coupe) {
        float cy = cam.getYRot(), cp = cam.getXRot();
        float ny = cy + Mth.wrapDegrees(yaw - cy) * f, np = Mth.lerp(f, cp, pitch);
        cam.setPos(x, y - OEIL, z);
        cam.setYRot(ny);
        cam.setXRot(np);
        if (coupe) {
            cam.xo = cam.getX();
            cam.yo = cam.getY();
            cam.zo = cam.getZ();
            cam.yRotO = ny;
            cam.xRotO = np;
        }
    }

    private static void avancerCamera() {
        cam.xo = cam.getX();
        cam.yo = cam.getY();
        cam.zo = cam.getZ();
        cam.yRotO = cam.getYRot();
        cam.xRotO = cam.getXRot();
    }

    private static void son(SoundEvent e, float pitch, float vol) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, vol));
    }

    private static void son(net.minecraftforge.registries.RegistryObject<SoundEvent> e, float pitch, float vol) {
        son(e.get(), pitch, vol);
    }

    private static void dire(String s, int duree) {
        sousTitre = s;
        sousTitreFin = t + duree;
    }

    private static void particule(net.minecraft.core.particles.ParticleOptions p, double x, double y, double z, double vx, double vy, double vz) {
        Minecraft.getInstance().level.addParticle(p, sx + x, sy + y, sz + z, vx, vy, vz);
    }

    private static void explosion(double x, double y, double z) {
        for (int i = 0; i < 40; i++) {
            double ex = x + R.nextFloat() * 5 - 2.5, ey = y + R.nextFloat() * 3, ez = z + R.nextFloat() * 5 - 2.5;
            particule(ParticleTypes.FLAME, ex, ey, ez, (R.nextFloat() - 0.5) * 0.6, R.nextFloat() * 0.45, (R.nextFloat() - 0.5) * 0.6);
            particule(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, ex, ey, ez, (R.nextFloat() - 0.5) * 0.08, 0.06 + R.nextFloat() * 0.06,
                    (R.nextFloat() - 0.5) * 0.08);
            particule(ParticleTypes.LARGE_SMOKE, ex, ey, ez, (R.nextFloat() - 0.5) * 0.3, 0.12, (R.nextFloat() - 0.5) * 0.3);
            particule(ParticleTypes.LAVA, ex, ey, ez, 0, 0, 0);
        }
    }

    private static void etincelles(double x, double z, int n) {
        for (int i = 0; i < n; i++) {
            particule(ParticleTypes.ELECTRIC_SPARK, x + R.nextFloat() - 0.5, 0.1, z + (R.nextFloat() - 0.5) * 2.2,
                    (R.nextFloat() - 0.8) * 0.6, R.nextFloat() * 0.35, (R.nextFloat() - 0.5) * 0.5);
            if (i % 2 == 0) {
                particule(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAVEL.defaultBlockState()), x + R.nextFloat() - 0.5, 0.05,
                        z + (R.nextFloat() - 0.5) * 2.4, (R.nextFloat() - 0.5) * 0.4, R.nextFloat() * 0.5, (R.nextFloat() - 0.5) * 0.6);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------- le déroulé
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        long d = System.nanoTime();
        tickCorps(e);
        if (MESURE && actif && e.phase == TickEvent.Phase.END) {
            tickNs = System.nanoTime() - d;
            noterTick();
        }
    }

    // ---------------------------------------------------------------------------------------------- mesure des images (essais)
    /** -Dheralisz.cine.perf : un CSV par tick (cine_perf.csv du dossier du jeu) : images, pire image, nos rendus, notre
     *  tick, ramasse-miettes (ms) ; pour trouver les saccades. */
    private static final boolean MESURE = System.getProperty("heralisz.cine.perf") != null;
    private static long imagePrec, imagePire, rendusNs, tickNs, gcPrec;
    private static int images;
    private static java.io.PrintWriter journal;

    @SubscribeEvent
    public static void imageCine(TickEvent.RenderTickEvent e) {
        if (!MESURE || !actif || e.phase != TickEvent.Phase.END) {
            return;
        }
        long n = System.nanoTime();
        if (imagePrec != 0) {
            images++;
            imagePire = Math.max(imagePire, n - imagePrec);
        }
        imagePrec = n;
    }

    /** Temps passé dans nos rendus (plateau, acteurs, épave), ajouté par TrainClient. */
    public static void rendus(long ns) {
        if (MESURE && actif) {
            rendusNs += ns;
        }
    }

    private static void noterTick() {
        try {
            if (journal == null) {
                journal = new java.io.PrintWriter(new java.io.FileWriter(new java.io.File(Minecraft.getInstance().gameDirectory, "cine_perf.csv")));
                journal.println("t;images;pire_ms;rendus_ms;tick_ms;gc_ms");
            }
            long gc = 0;
            for (var b : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
                gc += Math.max(0, b.getCollectionTime());
            }
            journal.printf(java.util.Locale.ROOT, "%d;%d;%.1f;%.2f;%.2f;%d%n", t, images, imagePire / 1e6, rendusNs / 1e6, tickNs / 1e6,
                    gcPrec == 0 ? 0 : gc - gcPrec);
            journal.flush();
            gcPrec = gc;
        } catch (java.io.IOException ex) {
            HeraliszUtils.LOG.warn("Mesure de la cinématique : {}", ex.toString());
        }
        images = 0;
        imagePire = 0;
        rendusNs = 0;
    }

    private static void tickCorps(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (differe != null && !(mc.screen instanceof ReceivingLevelScreen)) {
            boolean memeMonde = differe == mc.level;
            differe = null;
            if (memeMonde) {
                demarrer();
            }
        }
        if (!actif) {
            return;
        }
        if (mc.level == null || mc.player == null || cam == null || mc.getCameraEntity() != cam) {
            finir();
            return;
        }
        avancerCamera();
        CineActeurs.tick();
        BrasFP.tick();
        BrasFP.pose = BrasFP.Pose.AUCUNE;
        roulisCam = 0f;
        PlateauRendu.rameTick();
        PlateauRendu.parcoursAvant = PlateauRendu.parcours;
        PlateauRendu.trainAvant = PlateauRendu.train;
        if (attente >= 0) {                                        // écran noir le temps que le spawn arrive
            boolean pret = mc.level.getChunkSource().hasChunk(Mth.floor(reveil.x) >> 4, Mth.floor(reveil.z) >> 4);
            if (!pret && attente < 240) {
                attente++;
                return;
            }
            attente = -20;
        }
        if (attente < -1) {
            attente++;
            return;
        }
        if (mc.options.keyJump.isDown()) {
            if (++passer > 25) {
                finir();
                return;
            }
        } else {
            passer = 0;
        }
        if (roulement == null && t >= V_DEBUT && t < CHOC) {
            roulement = boucle(TrainSons.ROULEMENT.get(), 0.55f);
        }
        t++;
        String cap = System.getProperty("heralisz.cine.captures");
        if (cap != null && ("," + cap + ",").contains("," + t + ",")) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, "cine_" + t + ".png", mc.getMainRenderTarget(), m -> {});
        }
        secousse = Math.max(0f, secousse * 0.85f - 0.05f);
        bandes = t < 20 ? t / 20f : t > FIN - 20 ? (FIN - t) / 20f : 1f;
        if (t <= 30) {
            noir = 1f - k(0, 30);
        } else if (t < NOIR - 30) {
            noir = 0f;
        }
        if (t >= V_DEBUT && t < T_DEBUT) {                          // la rame file dans le tunnel
            PlateauRendu.parcours += VITESSE;
            if (t % 19 == 0) {
                secousse = Math.max(secousse, 0.7f);                // joints des rails
            }
            float passe = PlateauRendu.lampeProche(0.0);
            float vacille = (t / 7) % 23 == 0 ? -4f : 0f;
            PlateauRendu.dedans = 10f + 2.5f * passe + vacille;
            PlateauRendu.dehors = 3f + 7f * passe;
            lumiereActeurs = (int) (6 + 5 * passe);
        }
        String dbg = System.getProperty("heralisz.cine.camdebug");          // essais : caméra fixe (repère du plateau)
        if (t < V_DEBUT) {
            gare(mc);
        } else if (t < V_FIN) {
            voiture(mc);
        } else if (t < C_DEBUT) {
            salle(mc);
        } else if (t < T_DEBUT) {
            cabine(mc);
        } else if (t < NOIR) {
            terminus(mc);
        } else if (t < REVEIL) {
            sombre(mc);
        } else {
            reveil(mc);
        }
        if (t < V_DEBUT) {                                                  // à la gare, la rame bouge : les acteurs de la
            montrerMotrice(false);                                          // motrice (posés à l'arrêt) resteraient en l'air
        }
        if (t >= V_DEBUT) {                                                 // dans le train : jamais les bras à l'écran
            BrasFP.pose = BrasFP.Pose.AUCUNE;
        }
        if (t >= V_DEBUT && t < NOIR) {
            peur();
        }
        if (dbg != null && t < NOIR) {                                      // « t0:x,y,z,tx,ty,tz;t1:... »
            String[] v = null;
            for (String morceau : dbg.split(";")) {
                String[] tv = morceau.split(":");
                if (t >= Integer.parseInt(tv[0])) {
                    v = tv[1].split(",");
                }
            }
            if (v != null) {
                plan(Double.parseDouble(v[0]), Double.parseDouble(v[1]), Double.parseDouble(v[2]), Double.parseDouble(v[3]),
                        Double.parseDouble(v[4]), Double.parseDouble(v[5]), 1f);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------- la peur (sons)
    private static float phaseCoeur;

    /** Dans le train : le cœur bat un battement à la fois, de plus en plus vite quand il a peur, et son souffle suit. */
    private static void peur() {
        float bpm, vol, souffle;
        if (t < V_VITRE) {                                          // il reprend son souffle, le cœur ralentit
            bpm = Mth.lerp(k(V_DEBUT, V_DEBUT + 300), 118f, 76f);
            vol = 0.45f * k(V_DEBUT, V_DEBUT + 60) - 0.15f * k(V_DEBUT + 200, V_VITRE);
            souffle = Mth.lerp(k(V_DEBUT, V_DEBUT + 260), 0.75f, 0.1f);
        } else if (t < M_DEBUT) {                                   // la vitre : il s'emballe d'un coup, puis s'apaise un peu
            bpm = Mth.lerp(k(V_VITRE, V_VITRE + 70), 150f, 178f) - 55f * k(V_VITRE + 150, V_VITRE + 320);
            vol = 1f - 0.45f * k(V_VITRE + 150, V_VITRE + 320);
            souffle = Mth.lerp(k(V_VITRE + 10, V_VITRE + 40), 0.1f, 0.85f) - 0.45f * k(V_VITRE + 160, V_VITRE + 320);
        } else if (t < C_DEBUT) {                                   // la motrice : il s'arrête net, le cœur de plus en plus vite
            int pres = M_FURTIF + 108;                               // le mangeur lève la tête
            bpm = Mth.lerp(k(M_STOP, M_STOP + 90), 112f, 168f) + 18f * k(pres, pres + 6) * (1f - k(pres + 40, pres + 80));
            vol = Mth.lerp(k(M_STOP, M_STOP + 50), 0.5f, 1f);
            souffle = t < M_STOP ? 0.3f : t < M_FURTIF ? Mth.lerp(k(M_STOP, M_STOP + 30), 0.3f, 0.7f)
                    : t < M_PORTE ? Mth.lerp(k(M_FURTIF, M_FURTIF + 20), 0.7f, 0.18f)        // accroupi : il retient son souffle
                    : 0.45f;
        } else if (t < T_DEBUT) {                                   // la cabine : le « Hé », le fauteuil qui pivote, le cri
            bpm = Mth.lerp(k(C_DEBUT, C_CRI + 12), 150f, 196f);
            vol = 1f;
            souffle = t < C_CRI ? 0.25f : Mth.lerp(k(C_CRI + 10, C_CRI + 25), 0.25f, 1f);
        } else {                                                    // le terminus : vu de loin, il s'éteint
            bpm = 196f;
            vol = 1f - 0.75f * k(T_DEBUT + POV - 10, T_DEBUT + POV + 10);
            souffle = 1f - 0.8f * k(T_DEBUT + POV - 10, T_DEBUT + POV + 10);
        }
        if (coeur != null) {                                        // la boucle de la course s'efface
            coeur.vol(0.5f * (1f - k(V_DEBUT, V_DEBUT + 60)));
            if (t >= V_DEBUT + 60) {
                coeur.couper();
                coeur = null;
            }
        }
        if (essouffle != null) {
            essouffle.vol(Math.max(0f, souffle));
        }
        phaseCoeur += bpm / 1200f;                                  // 20 ticks par seconde
        if (phaseCoeur >= 1f) {
            phaseCoeur -= 1f;
            if (vol > 0.02f) {
                son(CineSons.BATTEMENT, 0.97f + R.nextFloat() * 0.06f, vol);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------- animations du corps
    /** Le corps du joueur vu de dehors (tête visible) ; créé à la demande. */
    private static CineActeurs.Corps perso() {
        if (CineActeurs.corps == null) {
            CineActeurs.corps = new CineActeurs.Corps();
        }
        CineActeurs.Corps c = CineActeurs.corps;
        c.visible = true;
        c.tete = true;
        return c;
    }

    /** Pas (marche ou course) : jambes et bras en opposition ; amp : ampleur ; bras : bras levés en garde (peur). */
    private static void pas(CineActeurs.Corps c, float phase, float amp, float bras) {
        float s = Mth.sin(phase);
        c.a("jambe_g", -amp * s, 0, -0.03f);
        c.a("jambe_d", amp * s, 0, 0.03f);
        c.a("bras_g", amp * 0.85f * s - bras, -0.1f * bras, -0.08f);
        c.a("bras_d", -amp * 0.85f * s - bras, 0.1f * bras, 0.08f);
    }

    /** Il court, terrifié : grandes enjambées, buste penché, regard par-dessus l'épaule (arriere 0..1). */
    private static void course(CineActeurs.Corps c, float phase, float arriere) {
        pas(c, phase, 1.05f, 0.25f);
        c.pivot = 0f;
        c.tangage = 11f;
        c.a("tete", -0.15f + 0.05f * Mth.sin(phase * 2), 1.7f * arriere, 0);
        c.a("corps", 0, 0.35f * arriere, 0);
    }

    /** Il marche lentement, apeuré : petits pas, bras un peu levés, la tête qui balaie à gauche et à droite. */
    private static void marchePeur(CineActeurs.Corps c, float phase, float regard) {
        pas(c, phase, 0.38f, 0.35f);
        c.pivot = 0f;
        c.tangage = 3f;
        c.a("tete", 0.05f, regard, 0);
        c.a("corps", 0, 0.2f * regard, 0);
    }

    /** Accroupi, il longe la paroi (pas courts, buste penché, bras près du corps). */
    private static void furtif(CineActeurs.Corps c, float phase, float regard) {
        float s = Mth.sin(phase);
        c.pivot = 0.703f;
        c.tangage = 24f;
        c.a("jambe_g", -0.55f - 0.3f * s, 0, -0.05f);
        c.a("jambe_d", -0.55f + 0.3f * s, 0, 0.05f);
        c.a("bras_g", -0.6f + 0.15f * s, 0, -0.1f);
        c.a("bras_d", -0.6f - 0.15f * s, 0, 0.1f);
        c.a("tete", -0.35f, regard, 0);
        c.a("corps", 0, 0, 0);
    }

    /** Debout, immobile, le souffle court ; regard (radians, lacet de la tête). */
    private static void debout(CineActeurs.Corps c, float regard, float haletement) {
        c.pivot = 0f;
        c.tangage = 2f + 2f * haletement * Mth.sin(t * 0.8f);
        c.a("jambe_g", 0, 0, -0.04f);
        c.a("jambe_d", 0, 0, 0.04f);
        c.a("bras_g", -0.15f, 0, -0.12f);
        c.a("bras_d", -0.1f, 0, 0.12f);
        c.a("tete", 0.08f * haletement * Mth.sin(t * 0.8f), regard, 0);
        c.a("corps", 0, 0, 0);
    }

    /** Une goule qui court : jambes qui moulinent, buste plongé, bras tendus devant, mâchoire ouverte. */
    private static void courseGoule(Goule g, float phase, float rage) {
        float s = Mth.sin(phase), c = Mth.cos(phase);
        g.a("cuisse_g", -1.0f * s, 0, 0);
        g.a("cuisse_d", 1.0f * s, 0, 0);
        g.a("tibia_g", 0.9f * Math.max(0, -c) + 0.2f, 0, 0);
        g.a("tibia_d", 0.9f * Math.max(0, c) + 0.2f, 0, 0);
        g.a("bras_g", -1.6f + 0.35f * s, 0, -0.15f);
        g.a("bras_d", -1.6f - 0.35f * s, 0, 0.15f);
        g.a("avantbras_g", -0.35f, 0, 0);
        g.a("avantbras_d", -0.35f, 0, 0);
        g.a("tronc", 0.55f + 0.05f * c, 0, 0.06f * s);
        g.a("cou", 0.15f, 0, 0);
        g.a("tete", -0.35f + 0.08f * Mth.sin(phase * 2.3f) * rage, 0.15f * Mth.sin(phase * 0.7f), 0);
        g.machoire = 0.45f + 0.35f * Math.abs(Mth.sin(phase * 0.9f));
    }

    /** Une goule immobile qui regarde partir le train : voûtée, bras ballants, elle se balance. */
    private static void regardeGoule(Goule g, float phase) {
        float s = Mth.sin(phase);
        g.a("cuisse_g", -0.1f, 0, -0.05f);
        g.a("cuisse_d", 0.1f, 0, 0.05f);
        g.a("tibia_g", 0.2f, 0, 0);
        g.a("tibia_d", 0.15f, 0, 0);
        g.a("bras_g", -0.35f + 0.08f * s, 0, -0.08f);
        g.a("bras_d", -0.3f - 0.08f * s, 0, 0.08f);
        g.a("avantbras_g", -0.3f, 0, 0);
        g.a("avantbras_d", -0.2f, 0, 0);
        g.a("tronc", 0.45f, 0, 0.05f * s);
        g.a("cou", 0.1f, 0, 0);
        g.a("tete", -0.2f, 0.1f * s, 0.15f * s);
        g.machoire = 0.3f + 0.15f * Math.abs(s);
    }

    // ---------------------------------------------------------------------------------------------- 0 : la gare (ouverture)
    // Par ses yeux. Il court dans un brouillard épais, de nuit, essoufflé ; il tourne la tête : rien que des yeux rouges qui
    // le poursuivent, loin dans la brume ; la gare et le train sortent du brouillard ; il fonce dans la voiture de queue,
    // jusqu'à la vitre du bout : la horde arrive ; le train démarre avant qu'elle soit là, lentement : ils le rattrapent
    // pendant qu'il prend de la vitesse, s'empilent, tombent, l'un saute la main tendue… et rate ; la brume les avale ;
    // il se retourne et avance, épuisé, jusqu'à sa voiture (le tunnel arrive pendant qu'il marche : aucune coupure).
    private static final int G_PORTE = 221, G_FERME = 250, G_DEPART = 258, G_SAUT = 296, G_RETOURNE = 470;
    private static final double PORTE_X = PlateauRendu.QUEUE - 9.6, PORTE_Z = 2.16;      // porte arrière droite, voiture de queue
    private static final double BOUT_X = PlateauRendu.QUEUE - 11.07;                     // vitre du bout de la voiture de queue
    private static final double DEPART_X = -95, COURSE = (PORTE_X - DEPART_X) / G_PORTE;  // ~0,28 bloc/tick : un sprint
    private static final float FOULEE = 0.46f;                                            // radians de foulée par tick

    /** La horde : 24 goules aux yeux rouges, à 35-45 blocs derrière lui, sur la voie et le bas-côté. */
    private static void horde() {
        if (!HORDE.isEmpty()) {
            return;
        }
        String[] v = {"rodeur_1", "fenetre", "rodeur_2", "mangeur", "fenetre_2", "rodeur_1", "rodeur_2", "fenetre"};
        for (int i = 0; i < 24; i++) {
            Goule g = new Goule(v[i % v.length]);
            g.yeuxRouges();
            double x = i == 0 ? DEPART_X - 54 : DEPART_X - 44 - R.nextFloat() * 12;
            double z = -2.2 + R.nextFloat() * 4.4;
            g.place(x, -0.72, z, -90f);
            g.lumiere = 8;
            g.figer();
            HORDE.add(g);
            CineActeurs.GOULES.add(g);
        }
    }

    /** Hauteur du sol à l'abscisse x pour quelqu'un sur le quai (le ballast avant la rampe). */
    private static double solQuai(double x, double z) {
        if (z < 2.6) {
            return -0.72;
        }
        double q0 = PlateauRendu.GARE_QUAI0;
        return x >= q0 ? PlateauRendu.QUAI : x <= q0 - 7 ? -0.72 : Mth.lerp((x - (q0 - 7)) / 7.0, -0.72, PlateauRendu.QUAI);
    }

    /** Par ses yeux : la caméra aux yeux du corps, le regard selon son lacet + celui de la tête (degrés) et un tangage. */
    private static void yeux(CineActeurs.Corps c, float lacetTete, float tangage, float lissage) {
        Vec3 o = c.oeil(1f);
        double y = Math.toRadians(c.lacet + lacetTete), p = Math.toRadians(tangage);
        double dx = -Math.sin(y) * Math.cos(p), dz = Math.cos(y) * Math.cos(p), dy = -Math.sin(p);
        plan(o.x, o.y, o.z, o.x + dx * 6, o.y + dy * 6, o.z + dz * 6, lissage);
    }

    /** Par ses yeux, depuis (x, y, z) du plateau : lacet (convention des entités : 0 = +z, -90 = +x) et tangage (degrés). */
    private static void regard(double x, double y, double z, float lacet, float tangage, float lissage) {
        double ly = Math.toRadians(lacet), p = Math.toRadians(tangage);
        plan(x, y, z, x - Math.sin(ly) * Math.cos(p) * 6, y - Math.sin(p) * 6, z + Math.cos(ly) * Math.cos(p) * 6, lissage);
    }

    /** Paupières lourdes : de courts noirs (il s'endort debout) aux instants donnés (début de chaque clignement). */
    private static void paupieres(int... debuts) {
        for (int d : debuts) {
            if (t >= d && t < d + 18) {
                float a = (t - d) / 18f;
                noir = Math.max(noir, a < 0.45f ? a / 0.45f : 1f - (a - 0.45f) / 0.55f);
            }
        }
    }

    /** Regard par-dessus l'épaule, centré sur `centre` : il tourne la tête doucement, regarde, revient (0..1). */
    private static float regardEpaule(int centre) {
        float d = Math.abs(t - centre);
        return d >= 22 ? 0f : lisse(Math.min(1f, (22 - d) / 12f));
    }

    private static void gare(Minecraft mc) {
        if (PlateauRendu.mode != 2) {                              // mise en place
            PlateauRendu.mode = 2;
            PlateauRendu.train = PlateauRendu.trainAvant = 0;
            PlateauRendu.parcours = PlateauRendu.parcoursAvant = 0;
            PlateauRendu.dedans = 11f;
            PlateauRendu.dehors = 4f;
            PlateauRendu.phares = 1f;
            PlateauRendu.portesLaterales_queue[3] = 1f;
            lumiereActeurs = 7;
        }
        horde();
        if (horde == null) {
            horde = boucle(CineSons.HORDE.get(), 0.25f);
            souffle = boucle(CineSons.SOUFFLE_COURSE.get(), 1f);          // le halètement de la course
            coeur = boucle(CineSons.COEUR_RAPIDE.get(), 0.7f);
        }
        if (essouffle == null && t >= G_PORTE + 6) {                 // dans le train : il reprend son souffle
            essouffle = boucle(CineSons.ESSOUFFLEMENT.get(), 0f);
        }
        if (souffle != null && t >= G_PORTE + 6) {
            float a = k(G_PORTE + 6, G_PORTE + 46);
            souffle.vol(1f - a);
            if (a >= 1f) {
                souffle.couper();
                souffle = null;
            }
        }
        double xt = PlateauRendu.train;
        CineActeurs.Corps c = perso();
        c.tete = false;
        c.visible = false;
        c.lumiere = 8;
        embarque = t >= G_PORTE;
        double rearX = BOUT_X - 0.45 + xt;                          // arrière du train (tampons), qui s'éloigne au départ
        // --------------------------------------------------- lui
        float regardArriere = 0f, tangageVue = 3f;
        if (t < G_PORTE) {                                         // il court, il court
            float ph = t * FOULEE;
            c.x = DEPART_X + COURSE * t;
            c.z = c.x < -62 ? 4.4 : Mth.lerp(Mth.clamp((c.x + 62) / 25.0, 0, 1), 4.4, 3.3);
            float bob = Mth.sin(ph);
            c.y = solQuai(c.x, c.z) + 0.045 * bob * bob;            // le pas qui secoue, sans à-coup
            c.lacet = -90f;
            course(c, ph, 0f);
            regardArriere = Math.max(regardEpaule(70), regardEpaule(150));
            roulisCam = 1.6f * Mth.sin(ph) * (1 - regardArriere);
            tangageVue = 3f + 1.2f * Mth.cos(ph * 2) + 4f * regardArriere;
            BrasFP.pose = BrasFP.Pose.COURSE;
            BrasFP.phase = ph;
            BrasFP.ampleur = 1f - 0.7f * regardArriere;
            if ((int) (ph / Mth.PI) != (int) ((ph - FOULEE) / Mth.PI)) {        // un pas à chaque demi-foulée
                son(CineSons.PAS_COURSE, 0.95f + R.nextFloat() * 0.1f, 0.75f);
            }
        } else if (t < 266) {                                      // il tourne dans la porte, fonce vers la vitre du bout
            float a = k(G_PORTE, G_PORTE + 12);                     // le virage dans la porte (vers l'intérieur)
            float b = k(G_PORTE + 8, 264);                           // la course jusqu'au bout de la voiture
            c.x = Mth.lerp(b, PORTE_X, BOUT_X + 0.55) + xt;
            c.z = Mth.lerp(a, 3.3, 1.1) - 0.72 * b;
            c.y = Mth.lerp(a, PlateauRendu.QUAI, PL);
            c.lacet = -90f - 90f * a - 90f * lisse(k(G_PORTE + 10, 258));   // face à la porte, puis vers le bout
            BrasFP.pose = BrasFP.Pose.COURSE;
            BrasFP.phase = t * FOULEE;
            BrasFP.ampleur = 1f - 0.6f * b;
            tangageVue = 3f + 4f * b;
            if (t == G_PORTE + 3) {
                son(CineSons.PAS_COURSE, 0.8f, 1f);
            }
        } else if (t < G_RETOURNE) {                               // à la vitre du bout : la horde arrive ; le départ
            c.x = BOUT_X + 0.55 + xt;
            c.z = 0.38;
            c.y = PL;
            c.lacet = -270f;
            BrasFP.pose = t < 420 ? BrasFP.Pose.VITRE : BrasFP.Pose.AUCUNE;
            BrasFP.phase = t;
            tangageVue = t >= 285 && t < 340 ? 12f : 5f;            // il baisse les yeux sur ceux qui s'agrippent
        } else {                                                   // il recule, se retourne, et s'en va, épuisé
            float a = k(G_RETOURNE, G_RETOURNE + 30);
            c.x = BOUT_X + 0.55 + 1.2 * a + Math.max(0, (t - G_RETOURNE - 30) * 0.08) + xt;
            c.z = 0.38 - 0.38 * a;
            c.y = PL;
            c.lacet = Mth.lerp(lisse(a), -270f, -450f);
            BrasFP.pose = BrasFP.Pose.MARCHE;
            BrasFP.phase = t * 0.28f;
            BrasFP.ampleur = 0.5f;
            tangageVue = 9f + 3f * Mth.sin(t * 0.07f);
        }
        PlateauRendu.portesLaterales_queue[3] = t < G_FERME ? 1f : 1f - k(G_FERME, G_FERME + 14);
        if (t == G_FERME) {
            son(CineSons.PORTE_PNEUMATIQUE, 1f, 1f);
        }
        if (t >= G_RETOURNE + 30) {                                // la cloison du vestibule s'ouvre devant lui
            PlateauRendu.cloisonArQueue = Mth.clamp((float) ((c.x - xt) - (PlateauRendu.QUEUE - 7.3 - 1.8)) / 1.4f, 0f, 1f);
        }
        // --------------------------------------------------- le train : il démarre avant qu'ils soient là, lentement
        double vTrain = t >= G_DEPART ? Math.min(0.55, 0.004 * (t - G_DEPART)) : 0;
        if (t >= G_DEPART) {
            if (t == G_DEPART) {
                son(CineSons.DEMARRAGE, 1f, 1f);
                secousse = 4f;
            }
            PlateauRendu.train += vTrain;
            PlateauRendu.parcours += vTrain;
            if (roulement == null && t > G_DEPART + 30) {
                roulement = boucle(TrainSons.ROULEMENT.get(), 0.25f);
            }
        }
        // --------------------------------------------------- la horde : elle court, rattrape, s'empile, tombe, renonce
        for (int i = 0; i < HORDE.size(); i++) {
            Goule g = HORDE.get(i);
            if (i == 0 && t >= G_SAUT) {
                continue;                                         // le sauteur : traité à part
            }
            double vitesse = i == 0 ? 0.42 : 0.31 + 0.022 * (i % 4);
            int couche = i % 3;                                    // une fois collés au train : au sol, grimpés, au sommet
            double place = rearX - 0.55 - 0.7 * ((i / 3) % 4);
            boolean colle = g.x >= place - 0.15;
            boolean lache = vTrain > vitesse + 0.02 || (t > G_SAUT + 10 && couche > 0);
            if (t < 20) {
                continue;
            }
            if (!lache && !colle) {                                // la course
                g.x = Math.min(place, g.x + vitesse);
                g.z += (Mth.clamp(g.z, -1.4, 1.4) - g.z) * 0.05;
                g.y = -0.72 + 0.05 * Math.abs(Mth.sin(t * 0.55f + i));
                g.lacet = -90f + 6f * Mth.sin(t * 0.1f + i);
                g.tangage = 0f;
                courseGoule(g, t * 0.55f + i * 1.7f, 1f);
                g.compteur = 0;
            } else if (!lache) {                                   // collés à l'arrière : ils suivent, grimpent, griffent
                g.x = place;
                double haut = couche == 0 ? 0 : couche == 1 ? 0.95 : 1.7;
                g.y = Mth.lerp(0.2, g.y, -0.72 + haut);
                g.lacet = -90f;
                g.tangage = couche == 0 ? 6f : 32f + 16f * couche;
                courseGoule(g, t * 0.45f + i, 0.7f);
                g.a("bras_g", -2.7f + 0.35f * Mth.sin(t * 0.6f + i), 0, -0.25f);
                g.a("bras_d", -2.7f - 0.35f * Mth.sin(t * 0.55f + i), 0, 0.25f);
                g.machoire = 0.6f + 0.35f * Mth.sin(t * 0.4f + i);
                if ((t + i * 7) % 61 == 0) {                       // il y en a qui tombent, et qui se relèvent
                    g.y -= 0.6;
                }
            } else {                                               // le train prend trop de vitesse : ils retombent, renoncent
                g.compteur++;
                float a = Math.min(1f, g.compteur / 20f);
                g.y = Mth.lerp(a, g.y, -0.72);
                g.tangage = couche > 0 && g.compteur < 18 ? 70f * Mth.sin(a * Mth.PI) : Mth.lerp(a, g.tangage, 0f);
                if (g.compteur < 50) {
                    g.x += vitesse * (1 - g.compteur / 50f);
                    courseGoule(g, t * 0.5f + i, 1f);
                } else {
                    regardeGoule(g, t * 0.08f + i);
                    g.a("tete", -0.35f, 0.1f * Mth.sin(t * 0.05f + i), 0);
                }
            }
        }
        if (t >= G_SAUT && !HORDE.isEmpty()) {
            sauteur(HORDE.get(0), rearX, vTrain);
        }
        // --------------------------------------------------- sons
        if (horde != null) {
            float loin = (float) Mth.clamp(0.25 + 0.75 * (t - 20) / 300.0, 0.25, 1.0);
            horde.vol(loin * (t > 360 ? 1f - 0.85f * k(360, G_RETOURNE) : 1f));
        }
        if (t == 120 || t == 268 || t == 312 || t == G_SAUT) {
            son(CineSons.GOULE_HURLEMENT, 0.85f + R.nextFloat() * 0.3f, t < 200 ? 0.45f : 0.8f);
        }
        if (t >= 285 && t < 325 && t % 9 == 0) {
            son(CineSons.FRAPPE_VITRE, 0.8f + R.nextFloat() * 0.3f, 0.5f);
        }
        if (essouffle != null) {
            essouffle.vol(Math.min(k(G_PORTE + 6, G_PORTE + 46), t < G_RETOURNE ? 1f : 0.85f));
        }
        if (coeur != null) {
            coeur.vol(t < 360 ? 0.85f : Mth.lerp(k(360, G_RETOURNE + 60), 0.85f, 0.5f));
        }
        paupieres(520);
        // --------------------------------------------------- par ses yeux
        yeux(c, 150f * regardArriere, tangageVue, t < 2 ? 1f : 0.99f);
    }

    /** Le plus rapide de la horde : il rattrape l'arrière du train, bondit la main tendue vers la vitre… le train a pris
     *  trop de vitesse : il retombe sur la voie et roule. */
    private static void sauteur(Goule g, double rearX, double vTrain) {
        int u = t - G_SAUT;
        if (u == 0) {
            son(CineSons.SAUT_ZOMBIE, 1f, 1f);
            sauteurX = g.x;
            sauteurZ = g.z;
        }
        if (u < 18) {                                             // le bond : il monte vers la vitre, le bras tendu
            float a = u / 18f;
            g.x = Mth.lerp(0.35, g.x, rearX - 0.9 - 0.014 * u * u);  // tout près… et la vitre s'éloigne
            sauteurX = g.x;
            g.z = Mth.lerp(a, sauteurZ, 0.3);
            g.y = -0.72 + 1.9 * Mth.sin(a * Mth.PI * 0.85f);
            g.lacet = -90f;
            g.tangage = 35f * a;
            courseGoule(g, t * 0.6f, 1f);
            g.a("bras_d", -3.0f, 0, 0.15f);                          // la main tendue vers la vitre qui s'éloigne
            g.a("bras_g", -2.4f, 0, -0.3f);
            g.a("avantbras_d", -0.1f, 0, 0);
            g.machoire = 0.9f;
            return;
        }
        float a = k(G_SAUT + 18, G_SAUT + 40);                     // il retombe, roule sur le ballast, reste à terre
        g.x = sauteurX + 1.6 * a;
        g.y = Mth.lerp(a, -0.72 + 0.5, -0.72) + 0.25 * Mth.sin(a * Mth.PI);
        g.tangage = 35f + 55f * a;
        g.roulis = 50f * Mth.sin(a * 5f) * (1 - a);
        courseGoule(g, t * 0.3f * (1 - a), 0.4f);
        g.a("bras_d", -2.6f * (1 - a), 0, 0.2f);
    }

    // ---------------------------------------------------------------------------------------------- 1 : la voiture, le tunnel
    private static final int V_DEBUT = 560, V_VITRE = 960, V_FIN = V_VITRE + 380, DV = V_VITRE - 229;

    private static float kv(int a, int b) {
        return k(a + DV, b + DV);
    }

    private static void voiture(Minecraft mc) {
        embarque = false;
        if (PlateauRendu.mode != 0) {                              // la rame entre dans le tunnel : le tunnel sans fin
            PlateauRendu.mode = 0;
            double xt = PlateauRendu.train;
            PlateauRendu.train = PlateauRendu.trainAvant = 0;
            PlateauRendu.portesLaterales_queue[3] = 0f;
            CineActeurs.GOULES.removeAll(HORDE);
            HORDE.clear();
            if (horde != null) {
                horde.couper();
                horde = null;
            }
            if (roulement != null) {
                roulement.vol(0.55f);
            }
            montrerMotrice(true);                                  // la rame est recalée sur 0 : ils sont à leur place
            CineActeurs.Corps c = perso();
            c.x -= xt;                                             // même place dans le train
            c.xo -= xt;
            bx -= xt;
            cam.setPos(cam.getX() - xt, cam.getY(), cam.getZ());
            cam.xo -= xt;
        }
        CineActeurs.Corps c = perso();
        c.tete = false;
        c.lumiere = -1;
        if (t < V_VITRE) {                                        // par ses yeux : il traverse, épuisé, jusqu'à sa voiture
            double x0 = PlateauRendu.QUEUE - 11.07 + 0.55 + 1.2 + (V_DEBUT - G_RETOURNE - 30) * 0.08;
            float a = (t - V_DEBUT) / (float) (V_VITRE - 22 - V_DEBUT);
            c.x = Mth.lerp(Math.min(1f, a), x0, 1.0);
            c.z = 0.1 * Mth.sin(t * 0.02f);
            if (t >= V_VITRE - 22) {                              // il ralentit, s'arrête dans l'allée
                float b = k(V_VITRE - 22, V_VITRE - 6);
                c.x = Mth.lerp(b, 1.0, 1.5);
                c.z = Mth.lerp(b, 0.0, 0.1);
            }
            c.y = PL;
            c.lacet = -90f;
            marchePeur(c, t * 0.3f, 0f);
            c.a("tete", 0.2f, 0, 0);
            paupieres(620, 700, 780, 860);
            double qx = c.x - PlateauRendu.QUEUE;                   // les portes s'ouvrent devant lui
            PlateauRendu.cloisonArQueue = 1f;
            PlateauRendu.cloisonAvQueue = Mth.clamp((float) (qx - 5.2) / 1.4f, 0f, 1f);
            PlateauRendu.porteAvQueue = Mth.clamp((float) (qx - 9.2) / 1.4f, 0f, 1f);
            PlateauRendu.porteArJoueur = Mth.clamp((float) (c.x - (-13.0)) / 1.4f, 0f, 1f);
            PlateauRendu.cloisonArJoueur = Mth.clamp((float) (c.x - (-9.4)) / 1.4f, 0f, 1f);
            for (double seuil : new double[]{PlateauRendu.QUEUE + 5.2, PlateauRendu.QUEUE + 9.2, -13.0, -9.4}) {
                if (Math.abs(c.x - seuil) < 0.045) {
                    son(CineSons.PORTE_COULISSANTE, 0.92f + R.nextFloat() * 0.1f, 0.8f);
                }
            }
            if (t % 15 == 0) {
                son(CineSons.PAS, 0.88f + R.nextFloat() * 0.1f, 0.4f);
            }
            c.visible = false;
            BrasFP.pose = BrasFP.Pose.MARCHE;
            BrasFP.phase = t * 0.3f;
            BrasFP.ampleur = 0.55f;
            if (t >= V_VITRE - 12) {                              // d'un coup, il tourne la tête vers la vitre
                BrasFP.pose = BrasFP.Pose.AUCUNE;
                float r = lisse(k(V_VITRE - 12, V_VITRE - 2));
                regard(c.x, YEUX, c.z, Mth.lerp(r, -90f, -14f), Mth.lerp(r, 6f, -6f), 0.99f);
            } else {
                float r = 35f * Mth.sin((t - V_DEBUT) / 38f);           // il regarde les vitres noires, à gauche, à droite
                yeux(c, r, 7f + 2f * Mth.sin(t * 0.09f), 0.12f);
            }
            return;
        }
        vitre(mc);                                                 // la goule de la vitre (choc, frappes, arrachement)
        int v = t - V_VITRE;
        c.tete = false;
        c.visible = false;
        if (v < 14) {                                              // le choc : il lève les mains, toujours debout
            BrasFP.pose = BrasFP.Pose.TREMBLE;
            plan(1.5, YEUX - 0.04 * k(V_VITRE, V_VITRE + 6), 0.1 - 0.12 * k(V_VITRE, V_VITRE + 14), VX, VY, VZ, 0.6f);
            return;
        }
        if (v < 80) {                                              // il recule en fixant la vitre, trébuche, tombe assis
            float r = k(V_VITRE + 14, V_VITRE + 46), f = k(V_VITRE + 46, V_VITRE + 64);
            double z = f > 0 ? Mth.lerp(f, -0.4, -0.75) : Mth.lerp(r, -0.02, -0.4);
            double y = Mth.lerp(f, YEUX - 0.04, PL + 0.98) + (f > 0 ? 0 : 0.03 * Mth.sin(v * 0.9f));
            float bascule = Mth.sin(f * Mth.PI);
            roulisCam = -16f * bascule;
            BrasFP.pose = f > 0.1f && f < 0.95f ? BrasFP.Pose.AUCUNE : BrasFP.Pose.TREMBLE;
            BrasFP.phase = t;
            if (v >= 64) {                                         // assis : ses jambes devant lui
                reculChute(c, 1f);
                c.visible = true;
                c.bras = false;
            }
            if (v == 20) {
                son(CineSons.SOUFFLE_PANIQUE, 1f, 0.9f);
            }
            if (v == 52) {                                         // le talon accroche
                son(CineSons.PAS, 0.7f, 1f);
            }
            if (v == 63) {
                son(CineSons.CHUTE_BANQUETTE, 1f, 1f);
                secousse = 7f;
            }
            double ty = v < 64 ? VY + 2.6 * bascule : v < 72 ? PL + 0.25 : VY;
            double tz = v < 64 ? VZ - 0.8 * bascule : v < 72 ? 0.4 : VZ;
            plan(1.5, y, z, v >= 64 && v < 72 ? 1.6 : VX, ty, tz, 0.35f);
            return;
        }
        if (v < 150) {                                             // assis par terre : la goule s'arrache sous ses yeux
            reculChute(c, 1f);
            c.visible = true;
            c.bras = false;
            BrasFP.pose = BrasFP.Pose.TREMBLE;
            BrasFP.phase = t;
            Vec3 o = c.oeil(1f);
            double suit = Math.max(0, Math.min(1, (t - (318 + DV)) / 32.0));
            plan(o.x, o.y, o.z, VX - 0.45 * suit, VY - 0.1 - 0.3 * suit, VZ, 0.25f);
            return;
        }
        fov = -1;
        c.bras = true;
        if (v < 230) {                                             // il se relève, regarde partout, effrayé
            float a = k(V_VITRE + 150, V_VITRE + 176);
            if (v == 152) {
                son(CineSons.TISSU, 1f, 0.9f);
            }
            double y = Mth.lerp(a, PL + 0.98, YEUX) + 0.02 * Mth.sin(t * 0.8f);       // il se relève
            float lacet = v < 178 ? 0f : v < 192 ? 70f * k(V_VITRE + 178, V_VITRE + 190)
                    : v < 210 ? Mth.lerp(k(V_VITRE + 192, V_VITRE + 206), 70f, -75f) : Mth.lerp(k(V_VITRE + 210, V_VITRE + 228), -75f, -90f);
            float tangage = Mth.lerp(a, -12f, 4f);
            BrasFP.pose = a < 1f ? BrasFP.Pose.TREMBLE : BrasFP.Pose.AUCUNE;
            BrasFP.phase = t;
            regard(1.65, y, Mth.lerp(a, -0.75, -0.3), lacet, tangage, 0.2f);
            return;
        }
        // il traverse jusqu'à la motrice, lentement, les portes s'ouvrent devant lui
        float a = (t - (V_VITRE + 230)) / (float) (V_FIN - (V_VITRE + 230));
        double x = Mth.lerp(a, 1.65, T_ARR + 0.6), z = Mth.lerp(Math.min(1, (t - (V_VITRE + 230)) / 20f), -0.3, 0.0);
        PlateauRendu.porteCloison = Mth.clamp((float) (x - 4.8) / 1.6f, 0f, 1f);
        PlateauRendu.porteBout = Mth.clamp((float) (x - 8.6) / 1.6f, 0f, 1f);
        PlateauRendu.porteArriereTete = Mth.clamp((float) (x - 9.6) / 1.6f, 0f, 1f);
        for (double seuil : new double[]{4.8, 8.6, 9.6}) {
            if (Math.abs(x - seuil) < 0.045) {
                son(CineSons.PORTE_COULISSANTE, 0.92f + R.nextFloat() * 0.1f, 0.8f);
            }
        }
        if (t % 14 == 0) {
            son(CineSons.PAS, 0.95f, 0.4f);
        }
        BrasFP.pose = BrasFP.Pose.MARCHE;
        BrasFP.phase = t * 0.33f;
        BrasFP.ampleur = 0.5f;
        regard(x, YEUX + 0.03 * Mth.sin(t * 0.33f), z, -90f + 32f * Mth.sin(t * 0.04f), 4f, v == 230 ? 0.6f : 0.15f);
    }

    /** Il recule devant la vitre (a 0..0,55), son talon accroche (0,55..0,7), il tombe assis dans l'allée (0,7..1), les
     *  jambes vers la vitre, en appui sur les mains. */
    private static void reculChute(CineActeurs.Corps c, float a) {
        c.lacet = 0f;
        c.x = 1.6;
        c.z = Mth.lerp(lisse(Math.min(1, a / 0.7f)), 0.1, -0.75);
        if (a < 0.55f) {
            float b = a / 0.55f;
            pas(c, b * 9f, -0.45f, 1.3f);                          // pas en arrière, les mains en garde
            c.pivot = 0f;
            c.tangage = -6f;
            c.y = PL;
            c.a("tete", -0.2f, 0.15f * Mth.sin(b * 6f), 0);
        } else {
            float b = lisse((a - 0.55f) / 0.45f);
            c.pivot = 0.703f;
            c.y = PL + (0.12f - 0.703f) * b;
            c.tangage = -38f * Mth.sin(b * Mth.PI) - 14f * b;
            float jambes = Mth.sin(b * Mth.PI) * 0.6f + b * 1.45f;
            c.a("jambe_g", -jambes, 0, -0.08f);
            c.a("jambe_d", -jambes * 0.92f, 0, 0.1f);
            c.a("bras_g", Mth.lerp(b, -1.3f, 0.55f), 0, -0.35f);    // les mains partent en arrière pour se rattraper
            c.a("bras_d", Mth.lerp(b, -1.3f, 0.55f), 0, 0.35f);
            c.a("tete", 0.25f * b, 0, 0);
        }
    }

    /** Il se relève (a 0..1) : ramène les jambes, pousse sur les mains, se redresse face à la vitre. */
    private static void releve(CineActeurs.Corps c, float a) {
        float l = lisse(a);
        c.lacet = 0f;
        c.x = 1.65;
        c.z = -0.1;
        c.pivot = 0.703f;
        c.y = PL + (0.12f - 0.703f) * (1 - l);
        c.tangage = Mth.lerp(l, -14f, 0f) + 18f * Mth.sin(l * Mth.PI);
        float jambes = Mth.lerp(l, 1.45f, 0f);
        c.a("jambe_g", -jambes, 0, -0.08f);
        c.a("jambe_d", -jambes * 0.92f, 0, 0.1f);
        c.a("bras_g", Mth.lerp(l, 0.55f, -0.15f), 0, -0.2f);
        c.a("bras_d", Mth.lerp(l, 0.55f, -0.1f), 0, 0.2f);
        c.a("tete", 0.2f * (1 - l), 0, 0);
    }

    /** La goule de la vitre : t 229 le choc, frappes ; 318 le pilier l'accroche, le visage glisse et se râpe sur le verre,
     *  la tête s'arrache ; il reste la traînée, l'éclaboussure, les fissures et des morceaux collés. */
    private static void vitre(Minecraft mc) {
        int t = Cinematique.t - DV;                                           // temps de la scène d'origine (choc à 229)
        Vec3 face = new Vec3(VX + 0.15, VY - 0.19, VZ + 0.24);                // pivot de la tête quand le visage touche
        if (fenetre == null && t < 340) {
            fenetre = new Goule("fenetre");
            fenetre.place(VX, PL - 0.4, VZ + 0.7, 180f);
            accrochee(fenetre, 1f);
            fenetre.a("bras_g", -2.2f, 0, 0.5f);
            fenetre.a("bras_d", -2.2f, 0, -0.5f);
            fenetre.a("avantbras_g", -1.2f, 0, 0);
            fenetre.a("avantbras_d", -1.2f, 0, 0);
            fenetre.amener("tete", face);
            mainsVitre(fenetre, 0, 0);
            fenetre.ecrase = 1f;
            fenetre.machoire = 0.3f;
            fenetre.lumiere = 12;                                           // éclairée par les plafonniers de la voiture
            fenetre.figer();
            CineActeurs.GOULES.add(fenetre);
            // l'éclaboussure du choc, autour et sous le visage (le visage reste lisible) ; elle s'épaissit à chaque coup
            impact = CineActeurs.trace("sang_impact", new Vec3(VX - 0.35, VY - 0.42, VZ + 0.012), new Vec3(VX + 0.6, VY - 0.42, VZ + 0.012),
                    new Vec3(0, 0.45, 0));
            impact.alpha = impact.alphaO = 0.35f;
            if (t <= 230) {
                son(CineSons.IMPACT_VITRE, 1f, 1f);
                son(CineSons.GOULE_VITRE_CRI, 1f, 1f);                       // entendu à travers le verre
                secousse = 9f;
                flash = 0.18f;
            }
        }
        if (fenetre == null) {
            return;
        }
        if (t < 318) {                                              // collée à la vitre : elle frappe, elle hurle
            accrochee(fenetre, 1f);
            fenetre.machoire = 0.35f + 0.45f * Math.abs(Mth.sin(t * 0.21f));
            fenetre.amener("tete", face.add(0, 0.02 * Mth.sin(t * 0.7f), 0));
            float fg = 0, fd = 0;                                            // la main qui frappe se décolle puis claque
            for (int f : new int[]{244, 270, 298}) {
                fg = Math.max(fg, Mth.clamp(1 - Math.abs(t - (f - 3)) / 3f, 0, 1));
            }
            for (int f : new int[]{285, 309}) {
                fd = Math.max(fd, Mth.clamp(1 - Math.abs(t - (f - 3)) / 3f, 0, 1));
            }
            mainsVitre(fenetre, fg, fd);
            for (int f : new int[]{244, 270, 285, 298, 309}) {
                if (t == f) {
                    son(CineSons.FRAPPE_VITRE, 0.9f + R.nextFloat() * 0.2f, 1f);
                    secousse = Math.max(secousse, 4f);
                    impact.alpha = Math.min(0.75f, impact.alpha + 0.08f);
                    if (fissures == null && t >= 270) {
                        // l'étoile part de l'impact des poings (à côté du visage, pas dessus)
                        fissures = CineActeurs.trace("fissures", new Vec3(VX + 0.05, VY + 0.12, VZ - 0.005),
                                new Vec3(VX + 0.95, VY + 0.12, VZ - 0.005), new Vec3(0, 0.45, 0));
                        son(CineSons.VITRE_FISSURE, 1f, 1f);
                    }
                }
            }
            if (t == 252 || t == 292) {
                son(CineSons.GOULE_VITRE_RALE, 0.95f + R.nextFloat() * 0.1f, 1f);
            }
            return;
        }
        // 318 : un pilier du tunnel accroche son corps. A : elle est traînée vers l'arrière le long de la vitre, visage plaqué,
        // il se râpe et laisse la traînée ; B : la tête se coince contre le montant, le corps continue, la tête s'arrache ;
        // C : la tête seule glisse lentement sur le verre en laissant une coulure, puis tombe. On zoome sur la vitre.
        if (t == 318) {
            son(CineSons.CHOC_PILIER, 1f, 1f);
            son(CineSons.RACLEMENT_VITRE, 1f, 1f);
            secousse = 8f;
            PlateauRendu.dehors = 14f;
            trainee = CineActeurs.trace("sang_trainee", new Vec3(VX + 0.3, VY - 0.02, VZ + 0.01), new Vec3(VX + 0.3, VY - 0.02, VZ + 0.01),
                    new Vec3(0, 0.26, 0));
        }
        fov = -1;
        double pin = VX - 0.55;                                              // le montant de la fenêtre arrête la tête
        if (t < 340) {                                                       // A : traînée le long du verre
            float a = kv(318, 340);
            double dx = -0.55 * a;
            accrochee(fenetre, 1.6f);
            fenetre.machoire = 0.7f + 0.25f * Mth.sin(t * 0.9f);
            fenetre.amener("tete", face.add(dx, -0.05 * a + 0.015 * Mth.sin(t * 2.3f), 0));
            fenetre.bras("g", new Vec3(VX - 0.38 + dx * 0.6, VY + 0.15 - 0.1 * Mth.sin(t * 1.1f), VZ + 0.24));
            fenetre.bras("d", new Vec3(VX + 0.62 + dx * 1.4 + 0.08 * Mth.sin(t * 1.3f), VY + 0.1 - 0.15 * a, VZ + 0.24));
            fenetre.ecrase = 1f;
            if (trainee != null) {
                trainee.p1 = new Vec3(face.x + dx - 0.05, VY - 0.02 - 0.05 * a, VZ + 0.01);
            }
            if (t == 328) {
                fenetre.texture("fenetre_2");
            }
            if (t % 3 == 0) {
                lambeau(face.x + dx + 0.05, VY - 0.05 + R.nextFloat() * 0.25, 0.04 + R.nextFloat() * 0.05);
            }
            return;
        }
        if (t == 340) {                                                      // B : la tête se coince, le corps continue
            son(CineSons.DECHIREMENT, 1f, 1f);
            fenetre.teteLibre = true;
            Vec3 p = fenetre.point("tete");
            fenetre.tx = fenetre.txo = p.x;
            fenetre.ty = fenetre.tyo = p.y;
            fenetre.tz = fenetre.tzo = p.z;
            fenetre.tLacet = fenetre.tLacetO = 180f;
        }
        if (t < 352) {
            float b = kv(340, 352);
            fenetre.tx = pin + 0.02 * Mth.sin(t * 3f);
            fenetre.tRoulis = -14f * b;
            fenetre.x = fenetre.x - 0.11 * b - 0.02;                          // le corps part vers l'arrière, et plonge
            fenetre.y -= 0.035 * b;
            fenetre.roulis = 30f * b;
            if (t == 346) {
                fenetre.texture("fenetre_3");
                fenetre.caches.add("machoire");
                for (int i = 0; i < 7; i++) {
                    if (fenetre.caches.contains("dent_b" + i)) {
                        break;
                    }
                    fenetre.caches.add("dent_b" + i);
                }
                lambeau(pin - 0.05, VY - 0.2, 0.12);                          // la mâchoire est restée collée au verre
                son(CineSons.CHOC_PILIER, 1.3f, 0.6f);
            }
            if (t % 2 == 0) {
                lambeau(pin - 0.1 + R.nextFloat() * 0.25, VY - 0.25 + R.nextFloat() * 0.4, 0.03 + R.nextFloat() * 0.06);
            }
            if (t >= 350) {
                fenetre.visible = false;
            }
            return;
        }
        if (t == 352) {                                                      // C : une coulure sous la tête
            coulure = CineActeurs.trace("sang_trainee", new Vec3(pin, VY + 0.1, VZ + 0.009), new Vec3(pin, VY + 0.1, VZ + 0.009),
                    new Vec3(0.12, 0, 0));
        }
        float c = kv(352, 368), chute = kv(366, 374);
        fenetre.tx = pin - 0.04 * c;
        fenetre.ty = face.y - 0.05 - 0.5 * c - 1.6 * chute * chute;
        fenetre.tRoulis = -14f - 20f * c - 150f * chute;
        fenetre.tz = face.z + 0.4 * chute;
        if (coulure != null) {
            coulure.p1 = new Vec3(pin, Math.max(VY - 0.75, fenetre.ty + 0.1), VZ + 0.009);
        }
        if (t >= 374) {
            CineActeurs.GOULES.remove(fenetre);
            fenetre = null;
        }
    }

    /** Un morceau resté collé dehors sur la vitre (planche « lambeaux » : 4 × 2 morceaux tirés au sort). */
    private static void lambeau(double x, double y, double s) {
        int i = R.nextInt(8);
        CineActeurs.Trace l = CineActeurs.trace("lambeaux", new Vec3(x - s, y, VZ + 0.008), new Vec3(x + s, y, VZ + 0.008), new Vec3(0, s, 0));
        l.u0 = (i % 4) / 4f;
        l.u1 = l.u0 + 0.25f;
        l.v0 = (i / 4) / 2f;
        l.v1 = l.v0 + 0.5f;
    }

    // ---------------------------------------------------------------------------------------------- 2 : la motrice
    // Il entre ; il s'arrête net : des corps partout, le cœur qui s'emballe ; il avance, prudent, en enjambant les corps ;
    // à quelques mètres du mangeur il hésite, puis longe la paroi accroupi, lentement ; la porte de la cabine s'ouvre.
    private static final int M_DEBUT = V_FIN, M_STOP = M_DEBUT + 40, M_AVANCE = M_DEBUT + 95, M_HESITE = M_DEBUT + 230,
            M_FURTIF = M_DEBUT + 260, M_PORTE = M_DEBUT + 420, C_DEBUT = M_DEBUT + 460;

    private static void salle(Minecraft mc) {
        PlateauRendu.porteCloison = PlateauRendu.porteBout = PlateauRendu.porteArriereTete = 1f;
        if (gresille == null) {
            gresille = boucle(CineSons.LAMPE_GRESILLE.get(), 0.3f);
            mastication = boucle(CineSons.MASTICATION.get(), 0.0f);
        }
        boolean coupe = (t / 4) % 29 == 0;                                  // un néon qui vacille, de temps en temps
        PlateauRendu.dedansTete = coupe ? 3.5f : 8.5f + 1.5f * Mth.sin(t * 1.7f);
        int lum = (int) PlateauRendu.dedansTete;
        for (Goule g : CADAVRES) {
            g.lumiere = Math.max(2, lum - 1);
        }
        mangeur.lumiere = Math.max(2, lum);
        CineActeurs.Corps c = perso();
        c.tete = false;
        c.visible = false;
        double x, z, oeil = YEUX - 0.06, tx, ty, tz;
        float lissage = 0.1f;
        BrasFP.pose = BrasFP.Pose.AUCUNE;
        if (t < M_STOP) {                                         // il passe la porte du vestibule
            float a = (t - M_DEBUT) / (float) (M_STOP - M_DEBUT);
            x = Mth.lerp(a, T_ARR + 0.6, T_VEST + 0.9);
            z = 0.0;
            PlateauRendu.porteVestTete = Mth.clamp((float) (x - (T_VEST - 1.8)) / 1.4f, 0f, 1f);
            if (PlateauRendu.porteVestTete > 0f && !porteVestSon) {
                porteVestSon = true;
                son(CineSons.PORTE_COULISSANTE, 0.9f, 0.8f);
            }
            BrasFP.pose = BrasFP.Pose.MARCHE;
            BrasFP.phase = t * 0.3f;
            BrasFP.ampleur = 0.45f;
            tx = x + 4;
            ty = PL + 1.3;
            tz = 0.0;
            oeil += 0.03 * Mth.sin(t * 0.3f);
            if (t % 15 == 0) {
                son(CineSons.PAS, 0.95f, 0.3f);
            }
        } else if (t < M_AVANCE) {                                // il s'arrête net : les corps ; le cœur s'emballe
            x = T_VEST + 0.9;
            z = 0.0;
            float r = k(M_STOP, M_STOP + 12);
            tx = Mth.lerp(r, x + 4, T - 4.45);
            ty = Mth.lerp(r, PL + 1.3, PL + 0.25);
            tz = 0.05 * Mth.sin(t * 0.07f);
            oeil -= 0.04 * r;
            lissage = 0.08f;
            if (t == M_STOP) {
                son(CineSons.COEUR_LENT, 0.9f, 1f);
                son(CineSons.SOUFFLE_PANIQUE, 1f, 0.6f);
            }
        } else if (t < M_HESITE) {                                // il avance, prudent, il enjambe les corps
            float a = (t - M_AVANCE) / (float) (M_HESITE - M_AVANCE);
            x = Mth.lerp(a, T_VEST + 0.9, T - 1.2);
            z = 0.05 * Mth.sin(t * 0.03f);
            BrasFP.pose = BrasFP.Pose.MARCHE;
            BrasFP.phase = t * 0.22f;
            BrasFP.ampleur = 0.3f;
            final double xx = x;
            double proche = CADAVRES.stream().mapToDouble(g -> Math.abs(g.x - xx)).min().orElse(9);
            tx = x + 2.5;
            ty = proche < 2.5 ? PL + 0.2 : PL + 0.8;               // il regarde où il met les pieds
            tz = 0.3 * Mth.sin(t * 0.04f);
            oeil += 0.02 * Mth.sin(t * 0.22f);
            if (t % 20 == 0) {
                son(CineSons.PAS, 0.9f, 0.22f);
            }
        } else if (t < M_FURTIF) {                                // le mangeur, là-bas : il hésite
            x = T - 1.2;
            z = Mth.lerp(k(M_HESITE + 10, M_FURTIF), 0.05, -0.55);
            tx = mangeur.x;
            ty = PL + 0.6;
            tz = mangeur.z + 0.3;
            lissage = 0.08f;
        } else if (t < M_PORTE) {                                 // accroupi, il longe la paroi, lentement, sans le quitter des yeux
            float a = (t - M_FURTIF) / (float) (M_PORTE - M_FURTIF);
            x = Mth.lerp(a, T - 1.2, T_CAB - 0.5);
            z = -0.62;
            oeil = PL + 1.18 + 0.025 * Mth.sin(t * 0.25f);
            boolean devant = a > 0.8f;
            tx = devant ? T_CAB + 2 : mangeur.x;
            ty = devant ? PL + 1.2 : PL + 0.6;
            tz = devant ? 0.0 : mangeur.z + 0.3;
            if (t % 24 == 0) {
                son(CineSons.PAS, 0.85f, 0.12f);
            }
        } else {                                                  // il se relève devant la porte de la cabine, qui s'ouvre
            x = T_CAB - 0.5;
            z = Mth.lerp(k(M_PORTE, M_PORTE + 20), -0.62, 0.0);
            oeil = Mth.lerp(k(M_PORTE, M_PORTE + 15), PL + 1.18, YEUX - 0.06);
            PlateauRendu.porteCabine = k(M_PORTE + 12, C_DEBUT);
            if (t == M_PORTE + 12) {
                son(CineSons.PORTE_CABINE, 1f, 1f);
            }
            tx = T_CAB + 2;
            ty = YEUX - 0.2;
            tz = 0.0;
        }
        if (mastication != null) {
            mastication.vol((float) Mth.clamp(1.2 - Math.abs(x - mangeur.x) / 9.0, 0.15, 1.0));
        }
        // le mangeur : il arrache, mâche ; quand il passe tout près, il s'arrête et lève la tête
        int pres = M_FURTIF + 108;
        boolean regarde = t >= pres && t <= pres + 30;
        if (regarde) {
            accroupi(mangeur, 0);
            float v = k(pres, pres + 8) * (1 - k(pres + 22, pres + 30));
            mangeur.a("tete", -0.45f * v, -0.7f * v, 0.15f * v);
            mangeur.a("tronc", 0.6f, 0, 0);
            mangeur.machoire = 0.6f * v;
            if (t == pres + 2) {
                son(CineSons.GOULE_RALE, 0.8f, 0.9f);
            }
        } else {
            accroupi(mangeur, t * 0.9f);
        }
        plan(x, oeil, z, tx, ty, tz, t == M_DEBUT ? 0.6f : lissage);
    }

    // ---------------------------------------------------------------------------------------------- 3 : la cabine
    private static final int C_HE = C_DEBUT + 40, C_PIVOT = C_DEBUT + 55, C_CRI = C_DEBUT + 110, T_DEBUT = C_DEBUT + 150;
    /** Le fauteuil pivote d'environ 70 % d'un demi-tour ; la tête rattrape le reste (radians). */
    private static final float ANGLE_FAUTEUIL = -125f, TETE_CHAUFFEUR = 0.5f;
    /** Terminus : durée du plan par ses yeux, sans coupure depuis la cabine. */
    private static final int POV = 75;

    /** Le conducteur sur son fauteuil pivotant : angle du fauteuil (degrés) ; le corps tourne avec lui autour de l'axe. */
    private static void fauteuil(float angle) {
        PlateauRendu.fauteuil = angle;
        double ox = SIEGE_X - PIVOT_X, oz = SIEGE_Z - PIVOT_Z;
        double r = Math.toRadians(angle);
        double cx = PIVOT_X + ox * Math.cos(r) + oz * Math.sin(r), cz = PIVOT_Z - ox * Math.sin(r) + oz * Math.cos(r);
        chauffeur.place(PlateauRendu.train + cx, SIEGE_Y - 0.875, cz, -90f - angle);           // il roule avec le train
    }

    private static void cabine(Minecraft mc) {
        if (gresille != null) {
            gresille.vol(Mth.lerp(k(C_DEBUT, C_DEBUT + 15), 0.3f, 0.08f));
        }
        if (mastication != null) {
            mastication.vol(Mth.lerp(k(C_DEBUT, C_DEBUT + 15), 0.3f, 0.05f));
        }
        PlateauRendu.porteCabine = 1f;
        PlateauRendu.dedansTete = (t / 3) % 13 == 0 ? 3f : 7f;
        lumiereActeurs = 8;
        chauffeur.lumiere = 8;
        CineActeurs.Corps c = perso();
        c.visible = false;
        // le fauteuil pivote à 65-70 % d'un demi-tour (~120°), lentement ; la tête rattrape le reste en se penchant
        float piv = lisse(k(C_PIVOT, C_PIVOT + 40));
        fauteuil(ANGLE_FAUTEUIL * piv);
        if (t == C_PIVOT + 2) {
            son(CineSons.CRAQUEMENT_COU, 0.85f, 0.7f);                    // le grincement du pied du fauteuil
        }
        if (t == C_PIVOT + 34) {
            son(CineSons.CRAQUEMENT_COU, 1f, 1f);
        }
        float tete = lisse(k(C_PIVOT + 30, C_PIVOT + 50));
        chauffeur.a("tete", -0.06f * tete, TETE_CHAUFFEUR * tete, 0.28f * Mth.sin(tete * Mth.PI));
        if (t == C_HE) {
            dire(I18n.get("heralisz_utils.cine.he"), 40);
        }
        if (t < C_CRI) {                                           // entré dans la cabine : juste derrière le grand dossier
            float e = lisse(k(C_DEBUT, C_DEBUT + 24));
            double x = Mth.lerp(e, T_CAB - 0.5, T + 8.7), z = Mth.lerp(e, 0.0, -0.45);
            Vec3 tt = chauffeur.point("tete").add(0, 0.18, 0);
            float vers = lisse(k(C_PIVOT + 8, C_PIVOT + 40));         // le regard suit le fauteuil qui pivote
            plan(x, YEUX - 0.03 + 0.015 * Mth.sin(t * 0.1f), z, Mth.lerp(vers, T + 11.5, tt.x), Mth.lerp(vers, PL + 1.6, tt.y),
                    Mth.lerp(vers, SIEGE_Z + 0.1, tt.z), t == C_DEBUT ? 0.6f : 0.12f);
            if (t == C_PIVOT + 52) {
                son(CineSons.GOULE_RALE, 0.7f, 1f);
            }
            chauffeur.machoire = 0.15f + 0.35f * k(C_PIVOT + 50, C_CRI);
            return;
        }
        // par ses yeux, le regard rivé sur le visage (sans zoom) : la mâchoire qui se décroche, le hurlement ; il recule
        fov = -1;
        if (t == C_CRI) {
            secousse = 3f;
        }
        if (t == C_CRI + 12) {
            son(CineSons.HURLEMENT_CHAUFFEUR, 1f, 1f);
            secousse = 7f;
        }
        chauffeur.machoire = t >= C_CRI + 10 ? k(C_CRI + 10, C_CRI + 18) : 0.5f;
        Vec3 tt = chauffeur.point("tete").add(0, 0.18, 0);
        double rec = 0.4 * k(C_CRI + 12, T_DEBUT - 10);
        if (t == C_CRI + 16) {
            son(CineSons.SOUFFLE_PANIQUE, 1f, 0.9f);
        }
        plan(T + 8.7 - rec, YEUX - 0.05, -0.45 + 0.1 * rec, tt.x, tt.y, tt.z, 0.3f);
    }

    // ---------------------------------------------------------------------------------------------- 4 : le terminus
    private static void terminus(Minecraft mc) {
        double P = PlateauRendu.PORTAIL, H = PlateauRendu.HEURTOIR;
        double xHeurtoir = P + H - 0.7 - T - NEZ;                     // train quand le nez touche le heurtoir
        double v = VITESSE * 1.07;
        embarque = t < T_DEBUT + POV;                               // la vue depuis la cabine suit le train
        if (PlateauRendu.mode != 1) {                              // mise en place (aussi en commençant au milieu)
            fov = -1;
            PlateauRendu.mode = 1;
            // toujours dans la cabine : la rame sort du tunnel une seconde et demie plus tard ; caméra et conducteur suivent
            double saut = P - 30.5 - T - 8.6 - PlateauRendu.train;           // sortie du tunnel vers T_DEBUT + 41
            PlateauRendu.train = PlateauRendu.trainAvant = P - 30.5 - T - 8.6;
            bx += saut;
            cam.setPos(cam.getX() + saut, cam.getY(), cam.getZ());
            cam.xo += saut;
            PlateauRendu.dedans = 11f;
            PlateauRendu.dedansTete = 6f;
            PlateauRendu.dehors = 8f;
            lumiereActeurs = 7;
            if (roulement != null) {
                roulement.vol(0.45f);
            }
            for (Boucle b : new Boucle[]{gresille, mastication}) {
                if (b != null) {
                    b.couper();
                }
            }
            son(CineSons.GRAVE_TERMINUS, 1f, 1f);
            mangeur.visible = false;
            for (Goule g : CADAVRES) {
                g.visible = false;
            }
            if (CineActeurs.corps != null) {
                CineActeurs.corps.visible = false;
            }
            fauteuil(ANGLE_FAUTEUIL);
            chauffeur.figer();
        }
        if (t == T_DEBUT + POV) {                                    // de loin : la rame revient au fond du tunnel
            PlateauRendu.train = PlateauRendu.trainAvant = xHeurtoir - v * (CHOC - t);
        }
        double nez = PlateauRendu.train + T + NEZ;
        if (t < CHOC) {
            PlateauRendu.train += v;
            PlateauRendu.parcours += v;
            float pres = (float) Mth.clamp(1 - (P + H - nez) / 90.0, 0, 1);
            if (roulement != null) {
                roulement.vol(0.45f + 1.0f * pres);
            }
            secousse = Math.max(secousse, 2.5f * pres * pres);
            if (t == T_DEBUT + 34 || t == T_DEBUT + POV + 30) {
                son(CineSons.KLAXON_PROCHE, 1f, 1f);
            }
        }
        // déraillement : la motrice saute des rails, laboure le ballast, la rame se tord
        int dDer = CHOC - 40;
        if (t >= dDer && t < CHOC) {
            float a = k(dDer, CHOC);
            if (t == dDer) {
                son(CineSons.DERAILLEMENT, 1f, 1f);
            }
            float[] m = PlateauRendu.rame[2], j = PlateauRendu.rame[1], q = PlateauRendu.rame[0];
            float sursaut = t < dDer + 6 ? 0.25f * Mth.sin((t - dDer) / 6f * Mth.PI) : 0f;
            m[2] = 2.6f * a * a;
            m[1] = sursaut - 0.45f * a;
            m[3] = 16f * a * a;
            m[4] = -13f * a;
            m[5] = 2f * sursaut;
            j[2] = 1.0f * a * a;
            j[1] = -0.2f * a;
            j[3] = -8f * a * a;
            j[4] = -5f * a;
            q[3] = 4f * a * a;
            etincelles(PlateauRendu.train + T + 8, m[2] * 0.9, 7);
            etincelles(PlateauRendu.train + T - 6, m[2] * 0.4, 4);
            etincelles(PlateauRendu.train + 6, j[2], 2);
            secousse = Math.max(secousse, 5f + 6f * a);
        }
        if (t == CHOC) {
            flash = 1f;
            secousse = 18f;
            if (roulement != null) {
                roulement.couper();
            }
            son(CineSons.CRASH, 1f, 1f);
            son(CineSons.DEBRIS, 1f, 0.9f);
            explosion(P + H - 1, 1.0, 1.2);
            PlateauRendu.phares = 0f;
        }
        if (t == CHOC + 5) {
            son(CineSons.EXPLOSION, 1f, 1f);
            explosion(P + H - 4, 1.5, 1.5);
        }
        if (t > CHOC && t <= CHOC + 18) {                            // la rame se met en portefeuille contre la paroi
            float c = k(CHOC, CHOC + 16);
            float[] m = PlateauRendu.rame[2], j = PlateauRendu.rame[1], q = PlateauRendu.rame[0];
            m[0] = -2.5f * c;
            m[2] = 2.6f + 1.2f * c;
            m[3] = 16f + 22f * c;
            m[4] = -13f - 60f * c;
            m[5] = 8f * c;
            j[0] = -3.5f * c;
            j[2] = 1.0f - 2.4f * c;
            j[3] = -8f - 30f * c;
            j[4] = -5f - 16f * c;
            j[5] = -12f * c;
            q[0] = -4.2f * c;
            q[2] = 1.1f * c;
            q[3] = 4f + 22f * c;
            q[4] = 9f * c;
            if (t % 3 == 0) {
                explosion(P + H - 8 + R.nextFloat() * 10, 1.0, R.nextFloat() * 3 - 1.5);
            }
        }
        flash = Math.max(0f, flash - 0.06f);
        double xt = PlateauRendu.train;
        // le conducteur, toujours tourné vers lui, gronde (placé APRÈS l'avance du train : sinon il a un tick de retard)
        if (t < T_DEBUT + POV) {
            fauteuil(ANGLE_FAUTEUIL);
            chauffeur.machoire = 0.75f + 0.25f * Mth.sin(t * 0.6f);
            chauffeur.a("tete", -0.06f + 0.05f * Mth.sin(t * 0.23f), TETE_CHAUFFEUR, 0.06f * Mth.sin(t * 0.17f));
        } else if (t == T_DEBUT + POV) {
            chauffeur.visible = false;
            fauteuil(0f);
        }
        if (t < T_DEBUT + POV) {                                    // par ses yeux, sans coupure : il recule, se décale à
            PlateauRendu.dedansTete = (t / 3) % 13 == 0 ? 3f : 6f;  // droite et se tourne vers le pare-brise ; la grotte, la
            fov = -1;                                               // voie qui finit sur le heurtoir, droit devant
            float u = lisse(k(T_DEBUT + 6, T_DEBUT + 38));
            // il se penche au-dessus du pupitre, contre le pare-brise : la voie se voit par-dessus le bas de la vitre
            double x = xt + T + Mth.lerp(u, 8.3, 9.85), y = YEUX - 0.06 + 0.32 * u + 0.02 * Mth.sin(t * 0.21f), z = Mth.lerp(u, -0.41, 0.62);
            Vec3 tt = chauffeur.point("tete").add(0, 0.18, 0);
            double dx = tt.x - x, dz = tt.z - z;
            float lacetA = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            float tangA = (float) -(Mth.atan2(tt.y - y, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
            float lacet = lacetA + Mth.wrapDegrees(-87f - lacetA) * u + 1.2f * Mth.sin(t * 0.13f) + 0.6f * Mth.sin(t * 0.31f);
            float tang = Mth.lerp(u, tangA, 8.5f) + 0.8f * Mth.sin(t * 0.17f);
            regard(x, y, z, lacet, tang, t == T_DEBUT ? 0.3f : 0.3f);
        } else if (t < dDer) {                                      // de loin : elle sort du nuage de brouillard, fonce
            plan(P + H - 4, 4.5 - 1.5 * k(T_DEBUT + POV, dDer), -11, nez - 6, 1.8, 0.5, t == T_DEBUT + POV ? 1f : 0.25f);
        } else if (t < CHOC + 14) {                                 // de trois quarts avant : elle déraille, percute
            plan(P + H - 6, 1.2, -7.5, PlateauRendu.train + T + 6, 1.4, 1.2, t == dDer ? 1f : 0.3f);
        } else {
            ejection(mc, P + H);
        }
    }

    /** 8 : vu de dehors : il est projeté par le pare-brise, tournoie au-dessus du ballast, retombe ; quand il touche le
     *  sol, le noir (sombre). */
    private static void ejection(Minecraft mc, double xMur) {
        CineActeurs.Corps c = perso();
        c.tete = true;
        c.visible = true;
        c.bras = true;
        c.lumiere = 10;
        c.pivot = 0.95f;
        if (t == CHOC + 14) {
            son(CineSons.EJECTION, 1f, 1f);
            c.tick();
        }
        float a = (t - (CHOC + 14)) / (float) (NOIR - CHOC - 14);
        c.x = Mth.lerp(a, xMur - 7.5, xMur - 2.6);
        c.z = Mth.lerp(a, 1.0, -6.2);
        c.y = Mth.lerp(a, 2.4, -1.52) + 2.2 * Mth.sin(a * Mth.PI);         // à plat : le dos sur le ballast (sol -0,72)
        c.lacet = -60f + 50f * a;
        c.tangage = -30f - 240f * a;                                // il finit à plat sur le ballast
        c.roulis = 25f * Mth.sin(a * Mth.PI * 2f);
        c.a("corps", 0, 0, 0);
        c.a("bras_g", -2.6f + 1.2f * Mth.sin(a * 7f), 0, -0.9f);
        c.a("bras_d", -2.2f - 1.0f * Mth.sin(a * 6f), 0, 1.0f);
        c.a("jambe_g", -0.8f + 0.6f * Mth.sin(a * 5f), 0, -0.3f);
        c.a("jambe_d", 0.4f - 0.6f * Mth.sin(a * 5f), 0, 0.3f);
        c.a("tete", 0.5f * Mth.sin(a * 4f), 0.4f, 0);
        if (t % 2 == 0) {
            particule(ParticleTypes.LARGE_SMOKE, c.x + R.nextFloat() * 3 - 1.5, c.y, c.z + R.nextFloat() * 2, 0, 0.05, 0);
            particule(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), c.x, c.y + 0.5, c.z,
                    (R.nextFloat() - 0.5) * 0.3, R.nextFloat() * 0.2, (R.nextFloat() - 0.5) * 0.3);
        }
        float r = lisse(a);                                         // la caméra se rapproche : il finit à 2,5 blocs
        plan(xMur - 2.4 - 1.0 * r, Mth.lerp(r, 1.3, -0.05), Mth.lerp(r, -9.8, -8.6), c.x, c.y + 0.9, c.z, t == CHOC + 14 ? 1f : 0.35f);
    }

    // ---------------------------------------------------------------------------------------------- 9 : le réveil
    private static void sombre(Minecraft mc) {
        noir = 1f;
        fov = -1;
        flash = 0f;
        if (t == NOIR) {                                            // il touche le sol : le choc, puis plus rien
            son(CineSons.CHUTE_BANQUETTE, 0.6f, 1f);
            son(CineSons.DEBRIS, 0.8f, 0.6f);
            PlateauRendu.visible = false;
            lumiereActeurs = -1;
            CineActeurs.vider();
            CADAVRES.clear();
            HORDE.clear();
            son(CineSons.ACOUPHENE, 1f, 1f);
            if (coeur != null) {
                coeur.couper();
                coeur = null;
            }
        }
        if (t == NOIR + 14 || t == NOIR + 34) {
            son(CineSons.COEUR_LENT, 1f, 0.9f);
        }
        orienter(reveil.x - dirPuits.x * 0.6, reveil.y + 0.22, reveil.z - dirPuits.z * 0.6, yawPuits, -62f, 1f, true);
    }

    private static void reveil(Minecraft mc) {
        int r = REVEIL;
        if (t <= r + 80) {                                          // allongé, on cligne des yeux
            if (t == r + 1) {
                son(CineSons.SOUFFLE_REVEIL, 1f, 1f);
            }
            float[][] seq = {{r, 1f}, {r + 16, 0.35f}, {r + 24, 1f}, {r + 36, 1f}, {r + 48, 0.15f}, {r + 56, 0.95f}, {r + 62, 0.9f},
                    {r + 76, 0f}};
            for (int i = 0; i < seq.length - 1; i++) {
                if (t >= seq[i][0] && t < seq[i + 1][0]) {
                    noir = Mth.lerp((t - seq[i][0]) / (seq[i + 1][0] - seq[i][0]), seq[i][1], seq[i + 1][1]);
                }
            }
            if (t >= r + 76) {
                noir = 0f;
            }
            orienter(reveil.x - dirPuits.x * 0.6, reveil.y + 0.22, reveil.z - dirPuits.z * 0.6,
                    yawPuits + 6f * Mth.sin(t / 17f), -62f + 3f * Mth.sin(t / 11f), t == r + 1 ? 1f : 0.25f, t == r + 1);
        } else if (t <= r + 115) {                                  // on se relève
            if (t == r + 81) {
                son(CineSons.TISSU, 0.9f, 1f);
                regarderLePuits(mc);
            }
            float a = k(r + 81, r + 112);
            orienter(reveil.x - dirPuits.x * 0.6 * (1 - a), Mth.lerp(a, reveil.y + 0.22, reveil.y + OEIL),
                    reveil.z - dirPuits.z * 0.6 * (1 - a), yawPuits, Mth.lerp(a, -62f, -3f), 0.5f, false);
        } else if (t <= r + 180) {                                  // debout, par ses yeux : l'épave, le titre
            if (t == r + 116) {
                son(CineSons.CARILLON_TITRE, 1f, 1f);
            }
            titre = t < r + 135 ? k(r + 116, r + 135) : 1f - k(r + 160, r + 176);
            float balaye = Mth.lerp(k(r + 116, r + 178), -18f, 14f);
            orienter(reveil.x, reveil.y + OEIL, reveil.z, yawPuits + balaye, 6f, 0.15f, false);
            if (t >= r + 165) {
                noir = k(r + 165, r + 179);
            }
        } else {                                                    // retour à nos yeux
            if (t == r + 181) {
                titre = 0f;
                regarderLePuits(mc);
            }
            Vec3 yeux = mc.player.getEyePosition();
            orienter(yeux.x, yeux.y, yeux.z, mc.player.getYRot(), mc.player.getXRot(), 1f, true);
            noir = 1f - k(r + 181, r + 205);
            if (t >= FIN) {
                finir();
            }
        }
    }

    // ---------------------------------------------------------------------------------------------- rendu et verrous
    @SubscribeEvent
    public static void overlays(RenderGuiOverlayEvent.Pre e) {
        if (actif) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void mains(RenderHandEvent e) {
        if (actif) {
            e.setCanceled(true);
            if (e.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
                BrasFP.dessiner(e.getPoseStack(), e.getMultiBufferSource(), e.getPackedLight(), e.getPartialTick());
            }
        }
    }

    @SubscribeEvent
    public static void nom(RenderNameTagEvent e) {
        // aucun nom de joueur pendant la cinématique (ni le sien, ni celui des autres arrivants)
        if (actif && (e.getEntity() == moi || e.getEntity() instanceof net.minecraft.world.entity.player.Player)) {
            e.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void gui(RenderGuiEvent.Post e) {
        if (!actif) {
            return;
        }
        GuiGraphics g = e.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        int bar = (int) (h * 0.11f * bandes);
        if (noir > 0.004f) {
            g.fill(0, 0, w, h, ((int) (Mth.clamp(noir, 0f, 1f) * 255) << 24));
        }
        if (flash > 0.004f) {
            g.fill(0, 0, w, h, ((int) (Mth.clamp(flash, 0f, 1f) * 255) << 24) | 0xFFF4E6);
        }
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        if (t < sousTitreFin && !sousTitre.isEmpty()) {
            g.pose().pushPose();
            g.pose().scale(2f, 2f, 1f);
            g.drawCenteredString(mc.font, Component.literal(sousTitre), w / 4, (h - bar) / 2 - 16, 0xFFFFFF);
            g.pose().popPose();
        }
        int a = (int) (Mth.clamp(titre, 0f, 1f) * 255);
        if (a > 8) {
            g.pose().pushPose();
            g.pose().scale(4f, 4f, 1f);
            g.drawCenteredString(mc.font, Component.literal("HERALIZ"), w / 8, h / 8 - 12, (a << 24) | 0xF2E6D0);
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().scale(1.5f, 1.5f, 1f);
            g.drawCenteredString(mc.font, Component.translatable("heralisz_utils.cine.sous_titre"), (int) (w / 3f), (int) (h / 3f) - 2,
                    (a << 24) | 0xB8B0A0);
            g.pose().popPose();
        }
        if (attente == -1) {
            g.drawString(mc.font, Component.translatable("heralisz_utils.cine.passer",
                    mc.options.keyJump.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT)), 6, h - 11, 0x70FFFFFF);
        }
    }

    @SubscribeEvent
    public static void cacherJoueur(RenderPlayerEvent.Pre e) {
        // seul dans sa cinématique : tous les vrais joueurs sont cachés (soi et les autres arrivants), seuls les
        // acteurs de la scène (entités du client, numéros négatifs) restent
        if (actif && e.getEntity().getId() >= 0) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void bloquer(MovementInputUpdateEvent e) {
        if (actif) {
            e.getInput().forwardImpulse = 0;
            e.getInput().leftImpulse = 0;
            e.getInput().up = e.getInput().down = e.getInput().left = e.getInput().right = false;
            e.getInput().jumping = false;
            e.getInput().shiftKeyDown = false;
        }
    }

    @SubscribeEvent
    public static void clics(InputEvent.InteractionKeyMappingTriggered e) {
        if (actif) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void angles(ViewportEvent.ComputeCameraAngles e) {
        if (actif) {
            float r = roulisCam;
            if (secousse > 0.5f) {                                  // roulis du tremblement : lisse, pas un tirage par image
                float u = t + (float) e.getPartialTick();
                r += Mth.sin(u * 1.17f) * Mth.sin(u * 0.43f + 1f) * secousse * 0.3f;
            }
            e.setRoll(e.getRoll() + r);
        }
    }

    /** Brume de la scène en cours : nuit épaisse à la gare, noir dans les tunnels, grotte sombre au terminus ; au réveil,
     *  celle du monde. */
    public static void brume(ViewportEvent.RenderFog e) {
        if (t >= NOIR || e.getType() != net.minecraft.world.level.material.FogType.NONE) {
            return;
        }
        float pres = t < V_DEBUT ? 0.5f : 6f, loin = PlateauRendu.mode == 1 ? 90f : 70f;
        if (t < G_PORTE) {
            double x = DEPART_X + COURSE * t;
            loin = (float) Mth.lerp(Mth.clamp((x + 78) / 32.0, 0, 1), 14, 32);
        } else if (t < V_DEBUT) {
            loin = t < 330 ? 32f : Mth.lerp(k(330, 380), 32f, 24f);
        } else if (PlateauRendu.mode == 1) {                         // le terminus : le nuage de brouillard clair
            boolean cabine = t < T_DEBUT + POV;
            pres = cabine ? 4f : 1.5f;
            loin = cabine ? 72f : 46f;
        }
        e.setNearPlaneDistance(pres);
        e.setFarPlaneDistance(loin);
        e.setFogShape(com.mojang.blaze3d.shaders.FogShape.SPHERE);
        e.setCanceled(true);
    }

    public static void couleurBrume(ViewportEvent.ComputeFogColor e) {
        if (t >= NOIR) {
            return;
        }
        float[] c = t < V_DEBUT ? new float[]{0.045f, 0.055f, 0.075f} : PlateauRendu.mode == 1 ? new float[]{0.55f, 0.6f, 0.67f}
                : new float[]{0.012f, 0.012f, 0.015f};
        e.setRed(c[0]);
        e.setGreen(c[1]);
        e.setBlue(c[2]);
    }

    @SubscribeEvent
    public static void champ(ViewportEvent.ComputeFov e) {
        if (actif && fov > 0) {
            e.setFOV(fov);
        }
    }
}
