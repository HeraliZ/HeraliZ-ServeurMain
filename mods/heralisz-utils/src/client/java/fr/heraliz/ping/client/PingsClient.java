package fr.heraliz.ping.client;

import fr.heraliz.ping.PingConfig;
import fr.heraliz.ping.PingSons;
import fr.heraliz.ping.PingType;
import fr.heraliz.ping.client.hud.Texte;
import fr.heraliz.ping.net.Paquets;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * Pings affiches par ce client : 32 places creees une fois et reutilisees (aucun objet par ping hors le pseudo recu),
 * tableau compact (les places 0..n-1 sont actives). Le temps est en millisecondes (animations fluides, independantes
 * des ticks). Fil du client uniquement.
 */
public final class PingsClient {
    private PingsClient() {
    }

    public static final int CAPACITE = 32;

    /** Un ping a l'ecran. Les champs « image » sont recalcules a chaque image par le rendu. */
    public static final class Visuel {
        public int id;
        public PingType type = PingType.ALLER;
        public boolean moi;
        public int entiteId = -1;
        @Nullable
        public Entity entite;
        public long prochaineRecherche;
        public double x, y, z;
        public int couleur;
        public String nom = "";
        /** Largeur du pseudo a la taille 1 (pixels d'interface par pixel de capitale). */
        public float largeurNom;
        public long naissance, fin;
        // image courante
        public float sx, sy, clipX, clipY, clipW, dist;
        /** Fleche au bord de l'ecran : place et direction. */
        public float fx, fy, fux, fuy;
        public boolean enEcran;
    }

    public static final Visuel[] PINGS = new Visuel[CAPACITE];
    public static int n;
    /** Regles du serveur. */
    public static int portee = 300;
    public static boolean actif = true;
    /** Banc : nombre de pings recus. */
    public static int recus;
    /** Banc : chaque ping recu est ecrit dans le journal. */
    public static boolean journal;

    static {
        for (int i = 0; i < CAPACITE; i++) PINGS[i] = new Visuel();
    }

    // ------------------------------------------------------------------ reseau
    public static void recevoir(Paquets.PingS2C m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        PingType type = PingType.parId(m.type());
        if (type == null) return;
        recus++;
        if (journal) fr.heraliz.ping.HeralizPing.LOG.info("[ping-banc] recu #{} {} de {}{} en {} {} {} (entite {})", m.id(), type.nom, m.nom(),
                m.moi() ? " (moi)" : "", (float) m.x(), (float) m.y(), (float) m.z(), m.entite());
        long maintenant = Util.getMillis();
        Visuel v = null;
        for (int i = 0; i < n; i++) if (PINGS[i].id == m.id()) v = PINGS[i];
        if (v == null) {
            if (n < CAPACITE) {
                v = PINGS[n++];
            } else {
                v = PINGS[0]; // plein : le ping qui finit le plus tot laisse sa place
                for (int i = 1; i < n; i++) if (PINGS[i].fin < v.fin) v = PINGS[i];
            }
        }
        v.id = m.id();
        v.type = type;
        v.moi = m.moi();
        v.entiteId = m.entite();
        v.entite = m.entite() >= 0 ? mc.level.getEntity(m.entite()) : null;
        v.prochaineRecherche = maintenant + 250;
        v.x = m.x();
        v.y = m.y();
        v.z = m.z();
        v.couleur = m.couleur();
        v.nom = m.nom();
        v.largeurNom = Texte.largeur(v.nom, 1f, 0f);
        v.naissance = maintenant;
        v.fin = maintenant + m.dureeS() * 1000L;
        v.enEcran = false;
        son(mc, v);
    }

    public static void maj(Paquets.MajS2C m) {
        if (journal) fr.heraliz.ping.HeralizPing.LOG.info("[ping-banc] maj #{} {}", m.id(), m.fin() ? "fin" : "position");
        for (int i = 0; i < n; i++) {
            Visuel v = PINGS[i];
            if (v.id != m.id()) continue;
            if (m.fin()) {
                v.fin = Math.min(v.fin, Util.getMillis() + 300); // court fondu
            } else if (v.entite == null || v.entite.isRemoved()) {
                v.x = m.x();
                v.y = m.y();
                v.z = m.z();
            }
            return;
        }
    }

    public static void reglages(Paquets.ReglagesS2C m) {
        portee = m.portee();
        actif = m.actif();
    }

    /** Deconnexion ou changement de monde. */
    public static void vider() {
        for (int i = 0; i < CAPACITE; i++) {
            PINGS[i].entite = null;
            PINGS[i].nom = "";
        }
        n = 0;
    }

    /** Retire les pings finis (compacte le tableau). */
    public static void expirer(long maintenant) {
        for (int i = n - 1; i >= 0; i--) {
            if (PINGS[i].fin > maintenant) continue;
            Visuel mort = PINGS[i];
            mort.entite = null;
            PINGS[i] = PINGS[n - 1];
            PINGS[n - 1] = mort;
            n--;
        }
    }

    /** Id de mon ping le plus proche du viseur (moins de {@code rayon} pixels d'interface), ou -1. */
    public static int monPingSousViseur(float cx, float cy, float rayon) {
        int id = -1;
        float best = rayon * rayon;
        for (int i = 0; i < n; i++) {
            Visuel v = PINGS[i];
            if (!v.moi || !v.enEcran) continue;
            float dx = v.sx - cx, dy = v.sy - cy, d = dx * dx + dy * dy;
            if (d < best) {
                best = d;
                id = v.id;
            }
        }
        return id;
    }

    // ------------------------------------------------------------------ son
    /** Son court dans la direction du ping (place a 2,5 blocs de l'oreille : toujours audible, toujours oriente). */
    private static void son(Minecraft mc, Visuel v) {
        float volume = PingConfig.VOLUME.get() / 100f * (v.moi ? 0.6f : 1f);
        if (volume <= 0f) return;
        Player p = mc.player;
        double ex = p.getX(), ey = p.getEyeY(), ez = p.getZ();
        double dx = v.x - ex, dy = v.y - ey, dz = v.z - ez, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double k = d > 2.5 ? 2.5 / d : 1.0;
        SoundEvent s = (v.type.alerte ? PingSons.ALERTE : PingSons.PING).get();
        float hauteur = switch (v.type) {
            case DANGER -> 0.88f;
            case LOOT -> 1.12f;
            case DEFENDRE -> 0.9f;
            case AIDE -> 1.2f;
            default -> 1f;
        };
        mc.getSoundManager().play(new SimpleSoundInstance(s, SoundSource.PLAYERS, volume, hauteur, RandomSource.create(0L),
                ex + dx * k, ey + dy * k, ez + dz * k));
    }
}
