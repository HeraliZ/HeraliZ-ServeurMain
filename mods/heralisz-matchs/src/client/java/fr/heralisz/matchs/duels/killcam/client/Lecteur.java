package fr.heralisz.matchs.duels.killcam.client;

import com.mojang.authlib.GameProfile;
import fr.heralisz.matchs.duels.killcam.Clip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Lecture d'un {@link Clip} cote client : joueurs factices (skins reels) places image par image avec interpolation,
 * camera par-dessus l'epaule du tueur (entite camera factice, collision avec les blocs, lissage), tirs (sons et
 * traceurs), ralenti sur la fin, chute de la victime, fondus. Le temps avance en temps reel (independant des ticks).
 * <p>Deroulement : fondu d'entree {@link #ENTREE_MS} pendant que la lecture commence ; lecture a vitesse 1 ; les
 * {@link #RALENTI} dernieres images a {@link #VITESSE_RALENTI} ; tenue {@link #TENUE_MS} sur la mort (la victime
 * tombe) ; fondu de sortie {@link #SORTIE_MS}.
 */
final class Lecteur {
    static final int ENTREE_MS = 280, TENUE_MS = 900, SORTIE_MS = 320;
    static final int RALENTI = 8;
    static final double VITESSE_RALENTI = 0.42;
    /** Ecart entre deux images au-dela duquel on ne lisse pas (teleportation, reapparition). */
    private static final double SAUT = 4.0;
    /** Camera : recul derriere l'oeil, decalage a droite, au-dessus. */
    private static final double RECUL = 3.1, DROITE = 1.05, DESSUS = 0.32;

    enum Etat {LECTURE, TENUE, SORTIE, FINI}

    final Clip clip;
    final ClientLevel niveau;
    final FauxJoueur[] faux;
    final FauxJoueur camera;
    final Traceurs traceurs = new Traceurs();
    /** Premiere image lue (le tueur y est present). */
    final int debut;
    final int derniere;

    Etat etat = Etat.LECTURE;
    /** Temps de lecture en images (fractions comprises) : pose des joueurs. */
    double temps;
    /** Horloge des effets (avance aussi pendant la tenue, au ralenti). */
    double horloge;
    private int imageVue = -1;
    private int tirSuivant;
    private long dernierNs;
    private long debutMs;
    private double tenueMs, sortieMs;
    private double tenueMort;
    /** Image et fraction courantes (pour le rendu). */
    int i0, i1;
    float fraction;

    // camera lissee
    private boolean camInit;
    private double camX, camY, camZ, recul = 1, reculCible = 1;
    private float camLacet, camTangage;
    private double oeilLisse = 1.62;

    Lecteur(Clip clip, ClientLevel niveau, Pose poseCamera) {
        this.clip = clip;
        this.niveau = niveau;
        this.derniere = Math.max(0, clip.images - 1);
        faux = new FauxJoueur[clip.participants.size()];
        Minecraft mc = Minecraft.getInstance();
        for (int k = 0; k < faux.length; k++) {
            Clip.Participant p = clip.participants.get(k);
            GameProfile profil = new GameProfile(p.uuid, p.nom.isEmpty() ? "?" : p.nom);
            if (p.joueur && mc.getConnection() != null) {
                PlayerInfo info = mc.getConnection().getPlayerInfo(p.uuid);
                if (info != null) profil = info.getProfile();
            }
            FauxJoueur f = new FauxJoueur(niveau, profil);
            // participant qui n'est pas un joueur (seul cas : le mannequin d'entrainement) : sa vraie texture
            if (!p.joueur) f.peau(fr.heralisz.matchs.HeraliszMatchs.id("textures/entity/duel_cible.png"));
            ItemStack[] tenue = new ItemStack[5];
            for (int t = 0; t < 5; t++) tenue[t] = p.tenue[t] >= 0 ? clip.objets.get(p.tenue[t]) : ItemStack.EMPTY;
            f.setItemSlot(EquipmentSlot.HEAD, tenue[0]);
            f.setItemSlot(EquipmentSlot.CHEST, tenue[1]);
            f.setItemSlot(EquipmentSlot.LEGS, tenue[2]);
            f.setItemSlot(EquipmentSlot.FEET, tenue[3]);
            f.setItemSlot(EquipmentSlot.OFFHAND, tenue[4]);
            faux[k] = f;
            combler(p);
        }
        camera = new FauxJoueur(niveau, new GameProfile(java.util.UUID.randomUUID(), "killcam"));
        camera.setPose(poseCamera);
        camera.setInvisible(true);
        // la lecture commence a la premiere image ou le tueur existe
        Clip.Participant t = clip.participants.get(clip.tueur);
        int d = 0;
        while (d < derniere && !t.present(d)) d++;
        debut = d;
        temps = debut;
        horloge = debut;
        tirSuivant = 0;
        while (tirSuivant < clip.tirs.size() && clip.tirs.get(tirSuivant).temps() < debut) tirSuivant++;
        dernierNs = System.nanoTime();
        debutMs = System.currentTimeMillis();
        poser(0);
    }

    /**
     * Comble les images absentes avec la plus proche presente (le drapeau PRESENT reste faux : le faux joueur n'est pas
     * dessine, mais la camera garde une position).
     */
    private static void combler(Clip.Participant p) {
        int n = p.x.length;
        int premier = -1;
        for (int i = 0; i < n; i++) {
            if (p.present(i)) {
                premier = i;
                break;
            }
        }
        if (premier < 0) return;
        for (int i = 0; i < premier; i++) copier(p, premier, i);
        int dernier = premier;
        for (int i = premier + 1; i < n; i++) {
            if (p.present(i)) dernier = i;
            else copier(p, dernier, i);
        }
    }

    private static void copier(Clip.Participant p, int de, int vers) {
        p.x[vers] = p.x[de];
        p.y[vers] = p.y[de];
        p.z[vers] = p.z[de];
        p.corps[vers] = p.corps[de];
        p.tete[vers] = p.tete[de];
        p.tangage[vers] = p.tangage[de];
        p.objet[vers] = p.objet[de];
    }

    /** Millisecondes depuis le debut de la lecture. */
    long ecouleMs() {
        return System.currentTimeMillis() - debutMs;
    }

    /** Opacite du bandeau (0..1). */
    float opacite() {
        float a = Math.min(1f, ecouleMs() / (float) ENTREE_MS);
        if (etat == Etat.SORTIE) a = Math.min(a, 1f - (float) (sortieMs / SORTIE_MS));
        if (etat == Etat.FINI) a = 0;
        return Mth.clamp(a, 0, 1);
    }

    /** Voile noir des fondus (0..1). */
    float voile() {
        float entree = 1f - Math.min(1f, ecouleMs() / (float) ENTREE_MS);
        float sortie = etat == Etat.SORTIE ? (float) (sortieMs / SORTIE_MS) : etat == Etat.FINI ? 1 : 0;
        return Mth.clamp(Math.max(entree * entree, sortie), 0, 1);
    }

    /** Progression de la lecture (0..1). */
    float progression() {
        double total = derniere - debut;
        if (total <= 0) return 1;
        return (float) Mth.clamp((temps - debut) / total, 0, 1);
    }

    // ------------------------------------------------------------------ temps
    /** Avance le temps reel ecoule depuis l'appel precedent (une fois par image rendue, avant la camera). */
    void avancer() {
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - dernierNs) / 1e9);
        dernierNs = now;
        double vitesse = temps >= derniere - RALENTI ? VITESSE_RALENTI : 1.0;
        switch (etat) {
            case LECTURE -> {
                temps += dt * 20 * vitesse;
                horloge += dt * 20 * vitesse;
                if (temps >= derniere) {
                    temps = derniere;
                    etat = Etat.TENUE;
                }
            }
            case TENUE -> {
                horloge += dt * 20 * VITESSE_RALENTI;
                tenueMs += dt * 1000;
                tenueMort += dt * 20;
                if (tenueMs >= TENUE_MS) etat = Etat.SORTIE;
            }
            case SORTIE -> {
                horloge += dt * 20 * VITESSE_RALENTI;
                tenueMort += dt * 20;
                sortieMs += dt * 1000;
                if (sortieMs >= SORTIE_MS) etat = Etat.FINI;
            }
            case FINI -> {
            }
        }
        poser(dt);
    }

    /** Passe directement au fondu de sortie (touche « passer » : la sortie est immediate, voir KillcamClient). */
    void finir() {
        etat = Etat.FINI;
    }

    // ------------------------------------------------------------------ pose
    private void poser(double dt) {
        int i = Mth.clamp((int) Math.floor(temps), 0, derniere);
        // images franchies : animations a 20 Hz (marche, bras, blessure) et collision de la camera
        while (imageVue < i) {
            imageVue++;
            if (imageVue >= debut) imageFranchie(imageVue);
        }
        i0 = i;
        i1 = Math.min(i + 1, derniere);
        fraction = etat == Etat.LECTURE ? (float) (temps - i) : 0f;
        if (etat != Etat.LECTURE) i1 = i0;
        for (int k = 0; k < faux.length; k++) poserFaux(k);
        // la victime tombe pendant la tenue
        if (etat != Etat.LECTURE && clip.victime != clip.tueur) {
            FauxJoueur v = faux[clip.victime];
            v.deathTime = Math.min(20, (int) tenueMort + 1);
            v.hurtTime = Math.max(v.hurtTime, 1);
        }
        // tirs
        while (tirSuivant < clip.tirs.size() && clip.tirs.get(tirSuivant).temps() <= temps) {
            tirer(clip.tirs.get(tirSuivant++));
        }
        poserCamera(dt);
    }

    private void poserFaux(int k) {
        Clip.Participant p = clip.participants.get(k);
        FauxJoueur f = faux[k];
        int a = i0, b = i1;
        f.visible = p.present(a);
        if (!p.present(b) || distance2(p, a, b) > SAUT * SAUT) b = a;
        f.xo = f.xOld = p.x[a];
        f.yo = f.yOld = p.y[a];
        f.zo = f.zOld = p.z[a];
        f.setPosRaw(p.x[b], p.y[b], p.z[b]);
        f.yBodyRotO = p.corps[a];
        f.yBodyRot = p.corps[b];
        f.yHeadRotO = p.tete[a];
        f.yHeadRot = p.tete[b];
        f.yRotO = p.tete[a];
        f.setYRot(p.tete[b]);
        f.xRotO = p.tangage[a];
        f.setXRot(p.tangage[b]);
        Pose pose = p.a(a, Clip.NAGE) ? Pose.SWIMMING : p.a(a, Clip.ACCROUPI) ? Pose.CROUCHING : Pose.STANDING;
        if (f.getPose() != pose) {
            f.setPose(pose);
            f.setShiftKeyDown(pose == Pose.CROUCHING);
        }
        f.setSprinting(p.a(a, Clip.SPRINT));
        int objet = p.objet[a];
        if (objet != f.objetPose) {
            f.objetPose = objet;
            f.setItemSlot(EquipmentSlot.MAINHAND, objet >= 0 && objet < clip.objets.size() ? clip.objets.get(objet) : ItemStack.EMPTY);
        }
    }

    private static double distance2(Clip.Participant p, int a, int b) {
        double dx = p.x[b] - p.x[a], dy = p.y[b] - p.y[a], dz = p.z[b] - p.z[a];
        return dx * dx + dy * dy + dz * dz;
    }

    /** Une image de jeu franchie : equivalent du tick pour l'animation des faux joueurs. */
    private void imageFranchie(int i) {
        for (int k = 0; k < faux.length; k++) {
            Clip.Participant p = clip.participants.get(k);
            FauxJoueur f = faux[k];
            f.tickCount++;
            if (!p.present(i)) continue;
            int prec = Math.max(debut, i - 1);
            double dx = p.x[i] - p.x[prec], dz = p.z[i] - p.z[prec];
            double d = Math.sqrt(dx * dx + dz * dz);
            f.walkAnimation.update(d > SAUT ? 0f : (float) Math.min(d * 4, 1), 0.4f);
            boolean frappe = p.a(i, Clip.FRAPPE), frappait = i > debut && p.a(i - 1, Clip.FRAPPE);
            if (frappe && !frappait) f.swing(InteractionHand.MAIN_HAND);
            f.avancerBras();
            f.hurtTime = p.a(i, Clip.BLESSE) ? 6 : Math.max(0, f.hurtTime - 1);
            f.hurtDuration = 10;
        }
        majCollisionCamera(i);
    }

    // ------------------------------------------------------------------ tirs
    private void tirer(Clip.Tir t) {
        int k = t.participant();
        if (k < 0 || k >= faux.length) return;
        Clip.Participant p = clip.participants.get(k);
        int i = Mth.clamp((int) t.temps(), 0, derniere);
        if (!p.present(i)) return;
        FauxJoueur f = faux[k];
        double oeil = p.a(i, Clip.ACCROUPI) ? 1.27 : 1.62;
        float lacet = t.lacet() * Mth.DEG_TO_RAD, tangage = t.tangage() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(lacet) * Mth.cos(tangage), fy = -Mth.sin(tangage), fz = Mth.cos(lacet) * Mth.cos(tangage);
        double rx = -Mth.cos(lacet), rz = -Mth.sin(lacet);
        double mx = p.x[i] + fx * 0.75 + rx * 0.24, my = p.y[i] + oeil - 0.16 + fy * 0.75, mz = p.z[i] + fz * 0.75 + rz * 0.24;
        double portee = 120;
        Vec3 depart = new Vec3(mx, my, mz), arrivee = new Vec3(mx + fx * portee, my + fy * portee, mz + fz * portee);
        BlockHitResult bloc = niveau.clip(new ClipContext(depart, arrivee, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, f));
        Vec3 fin = bloc.getType() == HitResult.Type.MISS ? arrivee : bloc.getLocation();
        boolean surJoueur = false;
        double meilleur = depart.distanceToSqr(fin);
        for (int j = 0; j < faux.length; j++) {
            if (j == k || !faux[j].visible) continue;
            FauxJoueur cible = faux[j];
            AABB boite = cible.getDimensions(cible.getPose()).makeBoundingBox(cible.getX(), cible.getY(), cible.getZ()).inflate(0.1);
            Optional<Vec3> h = boite.clip(depart, fin);
            if (h.isPresent() && depart.distanceToSqr(h.get()) < meilleur) {
                meilleur = depart.distanceToSqr(h.get());
                fin = h.get();
                surJoueur = true;
            }
        }
        traceurs.ajouter(mx, my, mz, fin.x, fin.y, fin.z, surJoueur, t.temps());
        ItemStack arme = p.objet[i] >= 0 && p.objet[i] < clip.objets.size() ? clip.objets.get(p.objet[i]) : ItemStack.EMPTY;
        SonsTir.jouer(f, arme, mx, my, mz);
    }

    // ------------------------------------------------------------------ camera
    /** Collision de la camera avec les blocs, a 20 Hz (lissee ensuite a chaque image). */
    private void majCollisionCamera(int i) {
        Clip.Participant t = clip.participants.get(clip.tueur);
        double oeil = t.a(i, Clip.ACCROUPI) ? 1.27 : 1.62;
        float lacet = t.tete[i] * Mth.DEG_TO_RAD, tangage = t.tangage[i] * Mth.DEG_TO_RAD;
        double ex = t.x[i], ey = t.y[i] + oeil, ez = t.z[i];
        double fx = -Mth.sin(lacet) * Mth.cos(tangage), fy = -Mth.sin(tangage), fz = Mth.cos(lacet) * Mth.cos(tangage);
        double rx = -Mth.cos(lacet), rz = -Mth.sin(lacet);
        double dx = -fx * RECUL + rx * DROITE, dy = -fy * RECUL + DESSUS, dz = -fz * RECUL + rz * DROITE;
        Vec3 de = new Vec3(ex, ey, ez), vers = new Vec3(ex + dx * 1.15, ey + dy * 1.15, ez + dz * 1.15);
        BlockHitResult h = niveau.clip(new ClipContext(de, vers, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, camera));
        if (h.getType() == HitResult.Type.MISS) {
            reculCible = 1;
        } else {
            double total = Math.sqrt(dx * dx + dy * dy + dz * dz) * 1.15;
            reculCible = Mth.clamp((h.getLocation().distanceTo(de) - 0.25) / total * 1.15, 0.12, 1);
        }
    }

    private void poserCamera(double dt) {
        Clip.Participant t = clip.participants.get(clip.tueur);
        int a = i0, b = i1;
        if (distance2(t, a, b) > SAUT * SAUT) b = a;
        float fr = fraction;
        double x = Mth.lerp(fr, t.x[a], t.x[b]), y = Mth.lerp(fr, t.y[a], t.y[b]), z = Mth.lerp(fr, t.z[a], t.z[b]);
        double oeilCible = t.a(a, Clip.ACCROUPI) ? 1.27 : 1.62;
        float lacetD = Mth.rotLerp(fr, t.tete[a], t.tete[b]), tangD = Mth.lerp(fr, t.tangage[a], t.tangage[b]);
        if (!camInit) {
            majCollisionCamera(a);
            recul = reculCible;
            oeilLisse = oeilCible;
        }
        double alpha = 1 - Math.exp(-dt * 9);
        oeilLisse += (oeilCible - oeilLisse) * (1 - Math.exp(-dt * 14));
        recul += (reculCible - recul) * (1 - Math.exp(-dt * (reculCible < recul ? 22 : 5)));
        float lacet = lacetD * Mth.DEG_TO_RAD, tangage = tangD * Mth.DEG_TO_RAD;
        double ex = x, ey = y + oeilLisse, ez = z;
        double fx = -Mth.sin(lacet) * Mth.cos(tangage), fy = -Mth.sin(tangage), fz = Mth.cos(lacet) * Mth.cos(tangage);
        double rx = -Mth.cos(lacet), rz = -Mth.sin(lacet);
        double cx = ex + (-fx * RECUL + rx * DROITE) * recul, cy = ey + (-fy * RECUL + DESSUS) * recul, cz = ez + (-fz * RECUL + rz * DROITE) * recul;
        // la camera regarde le point vise par le tueur (30 blocs devant son oeil)
        double vx = ex + fx * 30 - cx, vy = ey + fy * 30 - cy, vz = ez + fz * 30 - cz;
        float lacetC = (float) (Mth.atan2(vz, vx) * Mth.RAD_TO_DEG) - 90f;
        float tangC = (float) -(Mth.atan2(vy, Math.sqrt(vx * vx + vz * vz)) * Mth.RAD_TO_DEG);
        if (!camInit) {
            camInit = true;
            camX = cx;
            camY = cy;
            camZ = cz;
            camLacet = lacetC;
            camTangage = tangC;
        } else {
            camX += (cx - camX) * alpha;
            camY += (cy - camY) * alpha;
            camZ += (cz - camZ) * alpha;
            camLacet += Mth.wrapDegrees(lacetC - camLacet) * (float) Math.min(1, alpha * 1.4);
            camTangage += (tangC - camTangage) * (float) Math.min(1, alpha * 1.4);
        }
        double hauteur = camera.getEyeHeight();
        camera.xo = camera.xOld = camX;
        camera.yo = camera.yOld = camY - hauteur;
        camera.zo = camera.zOld = camZ;
        camera.setPosRaw(camX, camY - hauteur, camZ);
        camera.setYRot(camLacet);
        camera.yRotO = camLacet;
        camera.setYHeadRot(camLacet);
        camera.yHeadRotO = camLacet;
        camera.setXRot(camTangage);
        camera.xRotO = camTangage;
    }

    /** Position interpolee d'un faux joueur pour le rendu (x, y ou z selon axe 0, 1, 2). */
    double pos(int k, int axe) {
        Clip.Participant p = clip.participants.get(k);
        int a = i0, b = i1;
        if (!p.present(b) || distance2(p, a, b) > SAUT * SAUT) b = a;
        double[] v = axe == 0 ? p.x : axe == 1 ? p.y : p.z;
        return Mth.lerp(fraction, v[a], v[b]);
    }

    /** Partial a donner aux renderers pour ce faux joueur. */
    float partiel(int k) {
        Clip.Participant p = clip.participants.get(k);
        if (!p.present(i1) || distance2(p, i0, i1) > SAUT * SAUT) return 1f;
        return fraction;
    }

    /** Libere les faux joueurs (sons TaCZ qui les suivent compris). */
    void liberer() {
        for (FauxJoueur f : faux) f.discard();
        camera.discard();
        traceurs.vider();
    }
}
