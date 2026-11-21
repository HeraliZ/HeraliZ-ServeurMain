package com.Spartan.integrity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Règles d'expulsion « en rodage » (19/11) : les règles ajoutées ce jour-là (bibliothèque d'agent Java ou de débogueur
 * chargée dans le jeu, classe modifiée en mémoire après son chargement, la même vue par le launcher, clé du Gardien
 * réutilisée) n'ont jamais tourné sur de vrais PC de joueurs. Si l'une d'elles se trompait, elle expulserait tout le monde.
 *
 * <p>Disjoncteur : quand {@code seuil} PC DIFFÉRENTS la déclenchent en moins de {@code fenetreMs}, la règle est
 * suspendue (alerte au staff seulement, plus d'expulsion), puis remise en service toute seule au bout de
 * {@code repriseMs} ({@link #reprendre}) ; le staff est prévenu les deux fois. Un tricheur est seul ; une règle qui se
 * trompe touche tous les joueurs en quelques minutes : au plus {@code seuil - 1} PC honnêtes expulsés à tort, puis plus
 * aucun pendant la suspension.
 *
 * <p>21/11 (renforcé, choix du propriétaire) : on compte des PC, plus des comptes. Un PC = ses clés : le compte, le bloc
 * d'adresses IP ({@link #blocIp}), les identifiants matériels envoyés par le jeu et (fin du 21/11) la clé du Gardien du
 * lancement, prouvée par le jeton du site. Deux déclenchements qui partagent une
 * clé viennent du même PC : plusieurs comptes sur un PC, ou plusieurs PC derrière la même connexion, ne comptent qu'une
 * fois. Un PC qui a déjà fait suspendre une règle ne la coupe plus seul jusqu'au redémarrage : il compte encore, mais il
 * faut au moins un PC nouveau parmi les {@code seuil}. Les mêmes PC ne peuvent donc pas la couper à chaque remise en
 * service (ils restent expulsés tant qu'ils sont seuls à la déclencher) ; une règle qui se trompe touche aussi d'autres
 * PC et se recoupe au premier nouveau : au plus {@code seuil - 1} PC nouveaux expulsés à tort à chaque fois. (Audit du
 * 21/11 : avant, ces PC ne comptaient plus du tout ; ils étaient expulsés à chaque connexion jusqu'au redémarrage, même
 * quand la règle touchait d'autres PC.)
 *
 * <p>Java pur (testé hors du jeu). Appelé sur le fil du serveur ; synchronisé par prudence.
 */
public final class Rodage {
    /** Ce que la règle doit faire pour ce joueur. */
    public enum Decision {
        /** Expulser comme d'habitude. */
        APPLIQUER,
        /** Ce PC est le {@code seuil}-ième différent : règle suspendue maintenant (ne pas l'expulser, prévenir le staff). */
        SUSPENDRE,
        /** Règle suspendue : alerte seulement. */
        SUSPENDUE
    }

    /** Clés gardées au plus pour un même PC (un jeu modifié peut changer d'identifiant matériel à chaque connexion). */
    static final int MAX_CLES = 64;

    /** Un PC qui a déclenché la règle : ses clés, l'heure de la dernière fois, et s'il l'a déjà fait suspendre. */
    private static final class Pc {
        final Set<String> cles;
        long quand;
        boolean ancien;

        Pc(Set<String> cles, long quand) {
            this.cles = cles;
            this.quand = quand;
        }
    }

    private final Map<String, List<Pc>> touches = new HashMap<>();
    /** Règle suspendue → heure de la suspension (ordre d'arrivée). */
    private final Map<String, Long> suspendues = new LinkedHashMap<>();
    /** Clés des PC qui ont déjà fait suspendre la règle (jusqu'au redémarrage). */
    private final Map<String, Set<String>> dejaComptes = new HashMap<>();
    /** Lu à chaque tick sans verrou : vrai tant qu'une règle est suspendue. */
    private volatile boolean desSuspendues;

    /**
     * Motif de la liste {@code rodage} par lequel commence la clé d'un indice (minuscules), ou null : règle établie, jamais
     * suspendue.
     */
    public static String motif(String cle, List<? extends String> rodage) {
        if (cle == null || rodage == null) {
            return null;
        }
        String c = cle.toLowerCase(Locale.ROOT);
        for (String m : rodage) {
            String t = m == null ? "" : m.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty() && c.startsWith(t)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Clé « bloc d'adresses » d'une adresse IP (octets de l'adresse) : IPv4 en /24, IPv6 en /48 (une box ou un forfait
     * mobile change d'adresse dans son bloc) ; IPv4 écrite en IPv6 (::ffff:a.b.c.d) comme IPv4. Null si inconnue.
     */
    public static String blocIp(byte[] adresse) {
        if (adresse == null) {
            return null;
        }
        byte[] a = adresse;
        if (a.length == 16 && ipv4EnIpv6(a)) {
            a = Arrays.copyOfRange(a, 12, 16);
        }
        if (a.length == 4) {
            return "ip4:" + (a[0] & 0xff) + "." + (a[1] & 0xff) + "." + (a[2] & 0xff);
        }
        if (a.length == 16) {
            StringBuilder sb = new StringBuilder("ip6:");
            for (int i = 0; i < 6; i += 2) {
                if (i > 0) {
                    sb.append(':');
                }
                sb.append(Integer.toHexString(((a[i] & 0xff) << 8) | (a[i + 1] & 0xff)));
            }
            return sb.toString();
        }
        return null;
    }

    private static boolean ipv4EnIpv6(byte[] a) {
        for (int i = 0; i < 10; i++) {
            if (a[i] != 0) {
                return false;
            }
        }
        return (a[10] & 0xff) == 0xff && (a[11] & 0xff) == 0xff;
    }

    /**
     * Un PC déclenche la règle (motif de {@link #motif}).
     *
     * @param pc        clés du PC (« compte:… », « ip4:… », « pc:… », « gardien:… ») ; aucune clé : tous les PC sans
     *                  clé ensemble
     * @param seuil     PC différents qui suspendent la règle (0 ou moins : disjoncteur coupé, toujours APPLIQUER)
     * @param fenetreMs durée pendant laquelle on les compte
     */
    public synchronized Decision noter(String regle, Collection<String> pc, long maintenant, int seuil, long fenetreMs) {
        if (suspendues.containsKey(regle)) {
            return Decision.SUSPENDUE;
        }
        if (seuil <= 0) {
            return Decision.APPLIQUER;
        }
        Set<String> cles = new HashSet<>();
        if (pc != null) {
            for (String c : pc) {
                if (c != null && !c.isBlank() && cles.size() < MAX_CLES) {
                    cles.add(c);
                }
            }
        }
        if (cles.isEmpty()) {
            cles.add("inconnu");
        }
        Set<String> deja = dejaComptes.get(regle);
        boolean ancien = deja != null && !Collections.disjoint(deja, cles); // il a déjà fait suspendre la règle
        List<Pc> d = touches.computeIfAbsent(regle, k -> new ArrayList<>());
        d.removeIf(t -> maintenant - t.quand > fenetreMs);
        Pc meme = null;
        for (Iterator<Pc> it = d.iterator(); it.hasNext(); ) {
            Pc t = it.next();
            if (Collections.disjoint(t.cles, cles)) {
                continue;
            }
            if (meme == null) {
                meme = t;
            } else {
                ajouter(meme.cles, t.cles); // ce déclenchement relie deux PC notés à part : c'était le même
                meme.ancien |= t.ancien;
                it.remove();
            }
        }
        if (meme == null) {
            meme = new Pc(cles, maintenant);
            d.add(meme);
        } else {
            ajouter(meme.cles, cles);
            meme.quand = maintenant;
        }
        meme.ancien |= ancien;
        // les PC qui l'ont déjà fait suspendre ne la coupent plus seuls : il en faut au moins un nouveau
        if (d.size() >= seuil && unNouveau(d)) {
            suspendues.put(regle, maintenant);
            desSuspendues = true;
            Set<String> dj = dejaComptes.computeIfAbsent(regle, k -> new HashSet<>());
            for (Pc t : d) {
                dj.addAll(t.cles);
            }
            touches.remove(regle);
            return Decision.SUSPENDRE;
        }
        return Decision.APPLIQUER;
    }

    /** Au moins un PC qui n'a encore jamais fait suspendre la règle. */
    private static boolean unNouveau(List<Pc> d) {
        for (Pc t : d) {
            if (!t.ancien) {
                return true;
            }
        }
        return false;
    }

    private static void ajouter(Set<String> dans, Set<String> cles) {
        for (String c : cles) {
            if (dans.size() >= MAX_CLES) {
                return;
            }
            dans.add(c);
        }
    }

    /**
     * Remet en service les règles suspendues depuis au moins {@code repriseMs} (0 ou moins : jamais, jusqu'au redémarrage).
     * Rend les règles remises en service maintenant (le staff doit être prévenu).
     */
    public synchronized List<String> reprendre(long maintenant, long repriseMs) {
        if (suspendues.isEmpty() || repriseMs <= 0) {
            return List.of();
        }
        List<String> out = new ArrayList<>(1);
        for (Iterator<Map.Entry<String, Long>> it = suspendues.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Long> e = it.next();
            if (maintenant - e.getValue() >= repriseMs) {
                out.add(e.getKey());
                it.remove();
            }
        }
        desSuspendues = !suspendues.isEmpty();
        return out;
    }

    /** Une règle est-elle suspendue ? Sans verrou : lu à chaque tick. */
    public boolean desSuspendues() {
        return desSuspendues;
    }

    /** Règle suspendue en ce moment ? */
    public synchronized boolean suspendue(String regle) {
        return suspendues.containsKey(regle);
    }

    /** Règles suspendues (pour /spartan status et le staff). */
    public synchronized List<String> suspendues() {
        return new ArrayList<>(suspendues.keySet());
    }

    /** Heure de la suspension d'une règle, ou -1 si elle est en service. */
    public synchronized long depuis(String regle) {
        Long t = suspendues.get(regle);
        return t == null ? -1L : t;
    }
}
