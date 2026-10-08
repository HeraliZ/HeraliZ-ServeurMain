package fr.heralisz.clans.territory.supply;

import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.registry.ClansRegistry;
import fr.heralisz.clans.registry.Sons;
import fr.heralisz.clans.territory.Zone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Sequence du signal de ravitaillement (serveur) :
 * <ul>
 * <li>T+0 : son de fusee, colonne de flammes 20 s au-dessus du drapeau, fumee rouge 3 min autour ;</li>
 * <li>T+45 s : un « vol » demarre a 400 blocs du drapeau (cap aleatoire), y = 180, 1,2 bloc/tick. Le vol est
 * l'autorite : il calcule la position de l'avion a chaque tick serveur depuis son horloge et la pousse dans l'entite
 * ({@link AvionEntity#deplacer}), que le chunk de l'avion soit « entity-ticking » ou non (les chunks de bordure sont
 * suivis par les clients mais leurs entites ne tickent pas : sans cela l'avion y resterait fige). L'entite n'est
 * creee que quand la position theorique entre dans un chunk charge ; la caisse est larguee par le vol quand il passe
 * au-dessus du drapeau, avion ou pas ; l'avion est retire par le vol 400 blocs plus loin ;</li>
 * <li>la caisse ({@link CaisseParachuteEntity}) descend, se pose en bloc, se remplit et fume 3 min.</li>
 * </ul>
 * Les vols en cours ne survivent pas a un redemarrage du serveur : {@code Territoires.arreter} rend son delai a la
 * zone d'un vol qui n'a pas encore largue ({@link #zonesDelaiARendre}).
 */
public final class Ravitaillement {
    /** 45 s entre le signal et le depart de l'avion. */
    public static final int DELAI_AVION_TICKS = 900;
    public static final double DISTANCE_APPROCHE = AvionEntity.DISTANCE_APPROCHE, VITESSE = AvionEntity.VITESSE, ALTITUDE = 180;
    public static final int FUSEE_TICKS = 400, FUMEE_TICKS = 3600;

    private final MinecraftServer server;
    private final List<Vol> vols = new ArrayList<>();
    private final List<Emetteur> emetteurs = new ArrayList<>();
    private final Random random = new Random();
    private int tick, prochainId;

    public Ravitaillement(MinecraftServer server) {
        this.server = server;
    }

    /** Un largage en cours. */
    private static final class Vol {
        final int id;
        final String zoneId, clanTag;
        final ServerLevel level;
        final double cx, cz, dirX, dirZ, depX, depZ;
        final int debut;
        /** Le signal a pose le delai de la zone (faux pour un largage admin). */
        final boolean delaiPose;
        boolean avionApparu, largue;
        @Nullable
        AvionEntity avion;

        Vol(int id, String zoneId, String clanTag, ServerLevel level, double cx, double cz, double dirX, double dirZ, int debut, boolean delaiPose) {
            this.id = id;
            this.zoneId = zoneId;
            this.clanTag = clanTag;
            this.level = level;
            this.cx = cx;
            this.cz = cz;
            this.dirX = dirX;
            this.dirZ = dirZ;
            this.depX = cx - dirX * DISTANCE_APPROCHE;
            this.depZ = cz - dirZ * DISTANCE_APPROCHE;
            this.debut = debut;
            this.delaiPose = delaiPose;
        }
    }

    /** Source de particules periodique (colonne de la fusee, fumee). */
    private static final class Emetteur {
        final ServerLevel level;
        final double x, y, z, dx, dy, dz, vitesse, gigue;
        final ParticleOptions type;
        final int fin, periode, count;

        Emetteur(ServerLevel level, double x, double y, double z, ParticleOptions type, int fin, int periode, int count,
                 double dx, double dy, double dz, double vitesse, double gigue) {
            this.level = level;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.fin = fin;
            this.periode = periode;
            this.count = count;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.vitesse = vitesse;
            this.gigue = gigue;
        }

        void emettre(Random r) {
            double gx = gigue == 0 ? 0 : (r.nextDouble() - 0.5) * gigue, gz = gigue == 0 ? 0 : (r.nextDouble() - 0.5) * gigue;
            Particules.envoyer(level, type, x, y, z, count, dx + gx, dy, dz + gz, vitesse);
        }
    }

    /**
     * Lance la sequence sur une zone qui a un drapeau. delaiTicks = attente avant le depart de l'avion ; delaiPose =
     * le signal a consomme le delai de la zone (rendu si le serveur s'arrete avant le largage).
     */
    public void lancer(ServerLevel level, Zone zone, String clanTag, int delaiTicks, boolean delaiPose) {
        BlockPos d = zone.drapeau;
        if (d == null) return;
        double fx = d.getX() + 0.5, fz = d.getZ() + 0.5, fy = d.getY();
        level.playSound(null, fx, fy + 1, fz, Sons.SIGNAL_FUSEE.get(), SoundSource.PLAYERS, 3f, 1f);
        // fusee : colonne de flammes (2 par tick) et de fumee rouge (1 tick sur 2) qui montent, 20 s
        emetteurs.add(new Emetteur(level, fx, fy + 1.3, fz, ClansRegistry.FLAMME_FUSEE.get(), tick + FUSEE_TICKS, 1, 0, 0, 1.15, 0, 1, 0.05));
        emetteurs.add(new Emetteur(level, fx, fy + 1.3, fz, ClansRegistry.FLAMME_FUSEE.get(), tick + FUSEE_TICKS, 1, 0, 0, 0.95, 0, 1, 0.08));
        emetteurs.add(new Emetteur(level, fx, fy + 1.3, fz, ClansRegistry.FUMEE_ROUGE.get(), tick + FUSEE_TICKS, 2, 0, 0, 0.8, 0, 1, 0.12));
        // fumee rouge autour du drapeau, 3 min
        fumeeRouge(level, fx, fy + 1.4, fz, FUMEE_TICKS);
        double ang = random.nextDouble() * Math.PI * 2;
        Vol v = new Vol(prochainId++, zone.id, clanTag == null ? "" : clanTag, level, fx, fz, Math.cos(ang), Math.sin(ang),
                tick + Math.max(0, delaiTicks), delaiPose);
        vols.add(v);
        HeraliszClans.LOG.info("[clans] signal de ravitaillement sur {} ([{}]) : avion dans {} s depuis ({}, {})", zone.id, v.clanTag,
                delaiTicks / 20, Math.round(v.depX), Math.round(v.depZ));
    }

    /** Fumee rouge pendant dureeTicks autour d'un point (drapeau, caisse posee). */
    public void fumeeRouge(ServerLevel level, double x, double y, double z, int dureeTicks) {
        emetteurs.add(new Emetteur(level, x, y, z, ClansRegistry.FUMEE_ROUGE.get(), tick + dureeTicks, 3, 1, 0.7, 0.3, 0.7, 0.01, 0));
    }

    /** Un largage est en cours sur cette zone. */
    public boolean enCours(String zoneId) {
        for (Vol v : vols) if (v.zoneId.equals(zoneId)) return true;
        return false;
    }

    /** Zones dont le signal a pose le delai mais dont la caisse n'est pas encore larguee (arret du serveur). */
    public List<String> zonesDelaiARendre() {
        List<String> l = new ArrayList<>();
        for (Vol v : vols) if (v.delaiPose && !v.largue) l.add(v.zoneId);
        return l;
    }

    public void tick() {
        tick++;
        for (Iterator<Emetteur> it = emetteurs.iterator(); it.hasNext(); ) {
            Emetteur em = it.next();
            if (tick > em.fin) {
                it.remove();
                continue;
            }
            if (tick % em.periode == 0) em.emettre(random);
        }
        for (Iterator<Vol> it = vols.iterator(); it.hasNext(); ) {
            Vol v = it.next();
            if (tick < v.debut) continue;
            double dist = (tick - v.debut) * VITESSE;
            double x = v.depX + v.dirX * dist, z = v.depZ + v.dirZ * dist;
            if (!v.avionApparu) {
                if (dist >= DISTANCE_APPROCHE - 2) {
                    v.avionApparu = true; // personne pour le voir : pas d'avion
                } else if (v.level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
                    AvionEntity a = new AvionEntity(ClansRegistry.AVION.get(), v.level);
                    a.setPos(x, ALTITUDE, z);
                    a.initialiser(v.dirX, v.dirZ, v.level.getGameTime());
                    v.level.addFreshEntity(a);
                    v.avion = a;
                    v.avionApparu = true;
                }
            }
            if (v.avion != null) {
                if (v.avion.isRemoved()) {
                    v.avion = null; // chunk decharge : l'avion a disparu, le vol continue seul
                } else if (dist >= AvionEntity.FIN) {
                    v.avion.discard();
                    v.avion = null;
                } else {
                    v.avion.deplacer(x, ALTITUDE, z, v.level.getGameTime());
                }
            }
            if (!v.largue && dist >= DISTANCE_APPROCHE) larguer(v, v.cx, v.cz);
            if (v.largue && v.avion == null && dist >= AvionEntity.FIN) it.remove();
        }
    }

    private void larguer(Vol v, double x, double z) {
        v.largue = true;
        CaisseParachuteEntityServeur c = new CaisseParachuteEntityServeur(ClansRegistry.CAISSE_PARACHUTE.get(), v.level);
        c.setPos(x, ALTITUDE - 3, z);
        c.initialiser(v.zoneId, v.clanTag, random);
        v.level.addFreshEntity(c);
        v.level.playSound(null, x, ALTITUDE - 3, z, Sons.PARACHUTE_OUVERT.get(), SoundSource.NEUTRAL, 4f, 1f);
        HeraliszClans.LOG.info("[clans] caisse de ravitaillement larguee sur {} en ({}, {})", v.zoneId, Math.round(x), Math.round(z));
    }
}
