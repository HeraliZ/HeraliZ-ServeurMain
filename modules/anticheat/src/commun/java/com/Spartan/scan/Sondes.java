package com.Spartan.scan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Ce que le contrôle d'intégrité cherche chez le joueur, envoyé SANS les noms (audit du 02/11, point 13 ; A329). Avant,
 * chaque demande de contrôle portait en clair les classes, programmes et DLL recherchés : il suffisait de lire le paquet
 * pour savoir quoi renommer. Maintenant le serveur n'envoie que des empreintes salées (sel tiré à chaque contrôle) ; le jeu
 * calcule les mêmes empreintes sur ce qu'il voit et ne renvoie que ce qui correspond. Java pur (testé hors du jeu).
 *
 * <ul>
 *   <li>programmes ouverts : empreinte de chaque morceau de nom recherché ; le jeu hache les morceaux de même longueur de
 *   chaque nom d'exécutable (seules les longueurs sont connues) et renvoie le nom des programmes qui correspondent,
 *   jamais la liste des programmes du joueur ;</li>
 *   <li>classes de clients de triche : empreinte de leur PAQUET ; le jeu hache les paquets qu'il a chargés (modules et
 *   chargeurs de classes) et renvoie l'empreinte trouvée, le serveur la traduit en nom.</li>
 * </ul>
 * Les DLL recherchées ne partent plus du tout : le jeu envoie déjà le nom des bibliothèques chargées, le serveur juge.
 * Limite : quelqu'un qui devine un nom peut vérifier s'il est dans la liste (en le hachant avec le sel) ; il ne peut pas
 * lire la liste.
 */
public final class Sondes {
    public static final int MAX_EMPREINTES = 256;
    public static final int MAX_LONGUEURS = 32;
    /** Longueur maximale d'un morceau de nom de programme recherché. */
    public static final int LONGUEUR_MAX = 64;
    /** Paquets du jeu, de Forge et de Java : jamais cherchés (tous les joueurs les ont). */
    private static final String[] PAQUETS_DU_JEU = {"net.minecraft", "com.mojang", "net.minecraftforge", "cpw.mods",
            "java.", "javax.", "jdk.", "sun.", "com.sun.", "org.spongepowered", "com.Spartan", "org.lwjgl"};

    public final long sel;
    public final long[] programmes;
    public final int[] longueurs;
    public final long[] paquets;
    private transient Set<Long> programmesSet;
    private transient Set<Long> paquetsSet;
    private transient MessageDigest sha;

    public Sondes(long sel, long[] programmes, int[] longueurs, long[] paquets) {
        this.sel = sel;
        this.programmes = programmes;
        this.longueurs = longueurs;
        this.paquets = paquets;
    }

    public static Sondes vides() {
        return new Sondes(0, new long[0], new int[0], new long[0]);
    }

    /** Empreinte salée : les 8 premiers octets de SHA-256(sel ‖ texte UTF-8). */
    public static long empreinte(long sel, String texte) {
        return calcul(digest(), sel, texte);
    }

    private static long calcul(MessageDigest d, long sel, String texte) {
        d.reset();
        for (int i = 7; i >= 0; i--) {
            d.update((byte) (sel >>> (i * 8)));
        }
        byte[] h = d.digest(texte.getBytes(StandardCharsets.UTF_8));
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v = (v << 8) | (h[i] & 0xFF);
        }
        return v;
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** « a.b.C » → « a.b » ; « C » (sans paquet) → « ». */
    public static String paquet(String classe) {
        int i = classe.lastIndexOf('.');
        return i <= 0 ? "" : classe.substring(0, i);
    }

    /** Paquet que tous les jeux ont (Minecraft, Forge, Java, Spartan…) : jamais une sonde. */
    public static boolean paquetDuJeu(String paquet) {
        for (String p : PAQUETS_DU_JEU) {
            if (p.endsWith(".") ? paquet.startsWith(p) : paquet.equals(p) || paquet.startsWith(p + ".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Côté serveur : sondes d'un contrôle. Les motifs vides, trop longs ou en trop sont ignorés ; les classes dont le
     * paquet est celui du jeu aussi (sinon tout le monde serait « trouvé »).
     */
    public static Sondes preparer(long sel, List<String> motifsProgrammes, List<String> classes) {
        List<Long> prog = new ArrayList<>();
        Set<Integer> longs = new TreeSet<>();
        for (String m : motifsProgrammes) {
            String t = m == null ? "" : m.trim().toLowerCase(Locale.ROOT);
            if (t.isEmpty() || t.length() > LONGUEUR_MAX || prog.size() >= MAX_EMPREINTES) {
                continue;
            }
            if (!longs.contains(t.length()) && longs.size() >= MAX_LONGUEURS) {
                continue;
            }
            longs.add(t.length());
            prog.add(empreinte(sel, t));
        }
        List<Long> paq = new ArrayList<>();
        for (String c : classes) {
            String p = paquet(c == null ? "" : c.trim());
            if (p.isEmpty() || paquetDuJeu(p) || paq.size() >= MAX_EMPREINTES) {
                continue;
            }
            long e = empreinte(sel, p);
            if (!paq.contains(e)) {
                paq.add(e);
            }
        }
        return new Sondes(sel, prog.stream().mapToLong(Long::longValue).toArray(),
                longs.stream().mapToInt(Integer::intValue).toArray(), paq.stream().mapToLong(Long::longValue).toArray());
    }

    /**
     * Côté serveur : marque de chaque paquet cherché → classes de triche de ce paquet (pour traduire le rapport).
     */
    public static Map<String, String> noms(long sel, List<String> classes) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (String c : classes) {
            String p = paquet(c == null ? "" : c.trim());
            if (!p.isEmpty() && !paquetDuJeu(p)) {
                out.merge(marque(empreinte(sel, p)), c.trim(), (a, b) -> a + ", " + b);
            }
        }
        return out;
    }

    /**
     * Côté serveur : remplace dans {@code hits} les marques renvoyées par le jeu par « paquet de &lt;classes&gt; ». Une
     * marque qui n'est pas une sonde de ce contrôle, ou un nom en clair (jeu modifié), est retiré : rien de sûr.
     */
    public static void traduire(List<String> hits, Map<String, String> noms) {
        if (hits.isEmpty()) {
            return;
        }
        List<String> out = new ArrayList<>();
        for (String c : hits) {
            String n = c.startsWith("#") ? noms.get(c) : null;
            if (n != null) {
                String t = "paquet de " + n;
                t = t.length() > 200 ? t.substring(0, 200) : t;
                if (!out.contains(t)) {
                    out.add(t);
                }
            }
        }
        hits.clear();
        hits.addAll(out);
    }

    /** Marque d'une empreinte trouvée, renvoyée dans le rapport (« #0123456789abcdef »). */
    public static String marque(long empreinte) {
        return "#" + String.format("%016x", empreinte);
    }

    private void preparerJoueur() {
        if (sha == null) {
            sha = digest();
            programmesSet = new HashSet<>();
            for (long e : programmes) {
                programmesSet.add(e);
            }
            paquetsSet = new HashSet<>();
            for (long e : paquets) {
                paquetsSet.add(e);
            }
        }
    }

    /** Côté joueur : un nom d'exécutable contient-il un des morceaux recherchés ? (minuscules, un seul fil) */
    public boolean programmeCherche(String nom) {
        if (programmes.length == 0 || nom == null) {
            return false;
        }
        preparerJoueur();
        String n = nom.toLowerCase(Locale.ROOT);
        for (int l : longueurs) {
            if (l <= 0 || l > n.length() || l > LONGUEUR_MAX) {
                continue;
            }
            for (int i = 0; i + l <= n.length(); i++) {
                if (programmesSet.contains(calcul(sha, sel, n.substring(i, i + l)))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Côté joueur : marque à renvoyer si ce paquet est recherché, sinon null (un seul fil). */
    public String paquetCherche(String paquet) {
        if (paquets.length == 0 || paquet == null || paquet.isEmpty()) {
            return null;
        }
        preparerJoueur();
        long e = calcul(sha, sel, paquet);
        return paquetsSet.contains(e) ? marque(e) : null;
    }
}
