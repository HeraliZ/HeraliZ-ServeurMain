package fr.heraliz.airstrike.client;

import fr.heraliz.airstrike.network.HelicoCartePacket;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.Util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Marques de l'hélicoptère pour la carte M (lues par {@code fr.heraliz.carte.client.EcranCarte}) : position en direct de
 * chaque hélicoptère et endroit de chaque épave, reçus du serveur toutes les 2 s ({@link HelicoCartePacket}). Une marque
 * d'hélicoptère pas rafraîchie depuis 6 s disparaît, une épave depuis 12 s ; « retiré » l'efface tout de suite. Vidé à
 * la déconnexion. Entre deux paquets, l'hélicoptère est prolongé d'après ses deux dernières positions (2,5 s au plus).
 */
public final class HelicoCarte {
    private HelicoCarte() {
    }

    private static final long HELICO_MS = 6_000, EPAVE_MS = 12_000, PROLONGE_MS = 2_500;

    /** Une marque : épave ou hélicoptère, position, cap (degrés de Minecraft), heure de réception et vitesse (blocs/ms). */
    public static final class Marque {
        public final int id;
        public final boolean epave;
        private float x, z, vx, vz;
        private float lacet;
        private long recu;

        Marque(int id, boolean epave) {
            this.id = id;
            this.epave = epave;
        }

        public double x(long maintenant) {
            return x + vx * Math.min(PROLONGE_MS, maintenant - recu);
        }

        public double z(long maintenant) {
            return z + vz * Math.min(PROLONGE_MS, maintenant - recu);
        }

        public float lacet() {
            return lacet;
        }
    }

    private static final Int2ObjectOpenHashMap<Marque> MARQUES = new Int2ObjectOpenHashMap<>();

    public static void recevoir(HelicoCartePacket p) {
        if (p.type == HelicoCartePacket.RETIRE) {
            MARQUES.remove(p.id);
            HelicoClient.retire(p.id);
            return;
        }
        long maintenant = Util.getMillis();
        boolean epave = p.type == HelicoCartePacket.EPAVE;
        Marque m = MARQUES.get(p.id);
        if (m == null || m.epave != epave) {
            m = new Marque(p.id, epave);
            MARQUES.put(p.id, m);
        } else if (!epave && maintenant > m.recu) {
            float dt = maintenant - m.recu;
            m.vx = (p.x - m.x) / dt;
            m.vz = (p.z - m.z) / dt;
        }
        m.x = p.x;
        m.z = p.z;
        m.lacet = p.lacet;
        m.recu = maintenant;
    }

    /** Les marques encore fraîches (les autres sont oubliées). Vide sans hélicoptère. */
    public static List<Marque> marques() {
        if (MARQUES.isEmpty()) return List.of();
        long maintenant = Util.getMillis();
        List<Marque> l = new ArrayList<>(MARQUES.size());
        Iterator<Marque> it = MARQUES.values().iterator();
        while (it.hasNext()) {
            Marque m = it.next();
            if (maintenant - m.recu > (m.epave ? EPAVE_MS : HELICO_MS)) {
                it.remove();
                continue;
            }
            l.add(m);
        }
        return l;
    }

    public static void vider() {
        MARQUES.clear();
    }
}
