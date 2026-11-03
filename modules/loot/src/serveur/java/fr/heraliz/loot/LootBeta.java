package fr.heraliz.loot;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Loot de la bêta (03/11, demande du propriétaire : « que tout soit un peu plus simple à avoir, sauf le couteau du T7 ») :
 * section [beta] de {@code heraliz_loot-server.toml}, relue toute seule quand le fichier change. Appliqué au moment du
 * tirage ({@link TablesCoffres}) : les tables ne sont pas réécrites, et {@code actif = false} rend le loot d'avant.
 * <ul>
 * <li><b>Quantité</b> : le nombre d'objets d'un conteneur (ou d'un zombie : 1 tirage) est multiplié, arrondi au hasard
 * (moyenne exacte), jamais au-delà de {@code objets_max} (sauf si la table en donnait déjà plus) ;</li>
 * <li><b>Raretés</b> : le poids des objets Rare, Épique et Légendaire (et Peu commun si demandé) est multiplié ; la place
 * est prise sur les autres objets (Commun…) : le poids total d'une table ne change pas, donc la case vide (« rien » des
 * zombies) garde sa chance par tirage ;</li>
 * <li><b>Exclus</b> (Uniques, objets à vie, liste {@code exclus} : couteaux) : leur poids est divisé par la hausse du
 * nombre d'objets de la table, pour garder EXACTEMENT leur chance par conteneur (ou par zombie) d'aujourd'hui ;</li>
 * <li><b>Plafonds</b> : une rareté ne passe jamais devant celle d'en dessous (Épique &lt;= Rare, Légendaire &lt;= Épique)
 * et les autres objets gardent au moins {@link #plancher} de leur place (au moins 1 / hausse du nombre d'objets : aucun
 * objet ordinaire n'est plus rare qu'avant dans un conteneur) ; jamais plus de 100 % ;</li>
 * <li><b>Fragments</b> des zombies (tirés à part) : chance multipliée comme leur rareté, plafond par jour inchangé.</li>
 * </ul>
 * Rien n'est touché aux prix, au recyclage ni aux raretés des objets : seulement ce qu'on trouve. Calcul des poids une fois
 * par table, au premier tirage après un changement (tables, raretés ou réglage) ; ensuite, un tirage coûte autant qu'avant.
 * Même calcul en Python : {@code outils/loot_beta.py} (chiffres avant / après) — les garder ensemble.
 */
final class LootBeta {
    private LootBeta() {
    }

    /** Raretés dans l'ordre (Unique jamais touché). */
    private static final Rarete[] PALIERS = {Rarete.COMMUN, Rarete.PEU_COMMUN, Rarete.RARE, Rarete.EPIQUE, Rarete.LEGENDAIRE};
    /** Numéro du réglage : change avec les tables, les raretés ou le fichier ; les poids calculés avant sont refaits. */
    private static final AtomicInteger GENERATION = new AtomicInteger();

    /** Poids d'une table avec le réglage (tableau dans l'ordre des objets de la table), refaits à chaque changement. */
    record Poids(int generation, double[] poids, double total) {
    }

    /** Place des poids calculés d'une table (une par table, créée avec elle). */
    static final class Cache {
        volatile Poids poids;
    }

    /** Tables, raretés ou réglage changés : poids à refaire au prochain tirage. */
    static void invalider() {
        GENERATION.incrementAndGet();
    }

    static boolean actif() {
        return ConfigLoot.betaActif();
    }

    /**
     * Nombre d'objets d'un conteneur ou de tirages d'un zombie avec la quantité de la bêta : n x quantité, arrondi au hasard
     * (1,5 x 3 = 4 ou 5, 4,5 en moyenne), au plus {@code objets_max} (ou n s'il était déjà plus grand).
     */
    static int nombre(int n, RandomSource r) {
        if (n <= 0 || !actif()) return n;
        double q = ConfigLoot.betaQuantite();
        if (q <= 1) return n;
        double x = n * q;
        int m = (int) x;
        if (r.nextDouble() < x - m) m++;
        return Math.min(m, Math.max(n, ConfigLoot.betaObjetsMax()));
    }

    /** Multiplicateur des chances d'une rareté (1 : inchangée ; jamais Commun ni Unique). */
    static double multiplicateur(@Nullable Rarete r) {
        if (r == null || !actif()) return 1;
        return switch (r) {
            case PEU_COMMUN -> ConfigLoot.betaPeuCommun();
            case RARE, EPIQUE, LEGENDAIRE -> ConfigLoot.betaHautes();
            default -> 1;
        };
    }

    /** Chance d'un objet tiré à part (fragments des zombies) avec le réglage : x le multiplicateur de sa rareté, 1 au plus. */
    static double chance(double chance, ItemStack st) {
        if (chance <= 0 || !actif() || exclu(st)) return chance;
        return Math.min(1, chance * multiplicateur(RaretesObjets.de(st)));
    }

    /**
     * Part minimale de leur place gardée par les objets non boostés d'une table : 1 / (hausse du nombre d'objets de la
     * table, {@code fq}), entre 50 et 90 % : un objet ordinaire n'est jamais plus rare qu'avant dans un conteneur.
     */
    private static double plancher(double fq) {
        return Math.max(0.5, Math.min(0.9, 1.0 / fq));
    }

    /** Poids des objets de la table avec le réglage (null : bêta coupée, on garde les poids de la table). */
    @Nullable
    static Poids poids(TablesCoffres.Table t) {
        if (!actif()) return null;
        int g = GENERATION.get();
        Poids p = t.beta().poids;
        if (p == null || p.generation() != g) {
            p = calculer(t, g);
            t.beta().poids = p;
        }
        return p;
    }

    private static Poids calculer(TablesCoffres.Table t, int g) {
        List<TablesCoffres.Entree> objets = t.objets();
        int n = objets.size();
        double[] w = new double[n];
        // classe : -1 jamais touché (case vide), -2 exclu, 0 autre objet, sinon 1 + indice de la rareté boostée dans PALIERS
        int[] classe = new int[n];
        double[] h = new double[PALIERS.length];
        double[] m = new double[PALIERS.length];
        for (int i = 0; i < PALIERS.length; i++) m[i] = multiplicateur(PALIERS[i]);
        double autres = 0, total = 0, exclus = 0;
        for (int i = 0; i < n; i++) {
            TablesCoffres.Entree e = objets.get(i);
            w[i] = e.poids();
            total += w[i];
            if (e.item() == Items.AIR) {
                classe[i] = -1;
                continue;
            }
            ItemStack st = modele(e);
            Rarete ra = RaretesObjets.de(st);
            if (exclu(st, ra)) {
                classe[i] = -2;
                exclus += w[i];
                continue;
            }
            // PALIERS suit l'ordre de Rarete : l'indice est le rang de la rareté (Unique : hors du tableau)
            int k = ra == null ? -1 : ra.ordinal();
            if (k >= 0 && k < PALIERS.length && m[k] > 1) {
                classe[i] = 1 + k;
                h[k] += w[i];
            } else {
                autres += w[i];
            }
        }
        double[] apres = new double[PALIERS.length];
        boolean un = false;
        for (int k = 0; k < PALIERS.length; k++) {
            apres[k] = h[k];
            if (h[k] <= 0 || m[k] <= 1) continue;
            double v = h[k] * m[k];
            // une rareté ne passe jamais devant celle d'en dessous (si elle n'y était pas déjà)
            if (PALIERS[k] == Rarete.EPIQUE || PALIERS[k] == Rarete.LEGENDAIRE) v = Math.min(v, Math.max(h[k], apres[k - 1]));
            apres[k] = v;
            un |= v > h[k];
        }
        // exclus : chance par conteneur gardée (plus d'objets tirés : chacun un peu moins souvent) ; leur place va aux autres
        double fq = facteurQuantite(t), rendu = exclus > 0 && fq > 1 ? exclus - exclus / fq : 0;
        if ((un || rendu > 0) && autres > 0) {
            double extra = 0;
            for (int k = 0; k < PALIERS.length; k++) extra += apres[k] - h[k];
            double lim = autres * (1 - plancher(fq));
            if (extra > lim) {
                double f = lim / extra;
                for (int k = 0; k < PALIERS.length; k++) apres[k] = h[k] + (apres[k] - h[k]) * f;
                extra = lim;
            }
            double s = (autres - extra + rendu) / autres;
            for (int i = 0; i < n; i++) {
                if (classe[i] == 0) w[i] *= s;
                else if (classe[i] == -2) w[i] /= fq;
                else if (classe[i] > 0) w[i] *= apres[classe[i] - 1] / h[classe[i] - 1];
            }
        }
        return new Poids(g, w, total);
    }

    /**
     * Objets tirés en moyenne dans un conteneur de cette table avec la quantité, divisés par ceux d'avant (« cases_poids »,
     * sinon de min à max ; un zombie : 1 tirage) : la quantité, un peu moins quand le plafond objets_max joue (T7).
     */
    private static double facteurQuantite(TablesCoffres.Table t) {
        double q = ConfigLoot.betaQuantite();
        if (q <= 1) return 1;
        int cap = ConfigLoot.betaObjetsMax();
        double avant = 0, apres = 0;
        int[] p = t.poidsCases();
        int total = 0;
        if (p != null) for (int w : p) total += w;
        if (total > 0) {
            for (int k = 1; k < p.length; k++) {
                avant += (double) p[k] * k;
                apres += p[k] * moyenne(k, q, cap);
            }
        } else {
            for (int k = Math.max(1, t.min()); k <= Math.max(1, t.max()); k++) {
                avant += k;
                apres += moyenne(k, q, cap);
            }
        }
        return avant > 0 ? apres / avant : q;
    }

    /** Moyenne de {@link #nombre} pour n objets : n x q (l'arrondi au hasard garde la moyenne), plafonnée. */
    private static double moyenne(int n, double q, int cap) {
        return Math.min(n * q, Math.max(n, cap));
    }

    /** L'objet d'une entrée tel qu'il sortira (identifiant, NBT, rareté inscrite), pour lire sa rareté. */
    private static ItemStack modele(TablesCoffres.Entree e) {
        ItemStack st = new ItemStack(e.item());
        if (e.nbt() != null) {
            try {
                st.setTag(TagParser.parseTag(e.nbt()));
            } catch (Exception ex) {
                // NBT illisible : déjà signalé au tirage (TablesCoffres.objet)
            }
        }
        if (e.rarete() != null) st.getOrCreateTag().putString(Rarete.CLE, e.rarete().id);
        return st;
    }

    static boolean exclu(ItemStack st) {
        return exclu(st, RaretesObjets.de(st));
    }

    /** Uniques, objets à vie et liste « exclus » : chances jamais changées. */
    private static boolean exclu(ItemStack st, @Nullable Rarete ra) {
        if (ra == Rarete.UNIQUE || aVie(st)) return true;
        List<? extends String> motifs = ConfigLoot.betaExclus();
        if (motifs.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
        RaretesObjets.Cle c = RaretesObjets.cle(st);
        for (String m : motifs) {
            if (correspond(id == null ? null : id.toString(), m) || c != null && correspond(c.id(), m)) return true;
        }
        return false;
    }

    private static boolean correspond(@Nullable String id, String motif) {
        if (id == null) return false;
        return motif.endsWith("*") ? id.startsWith(motif.substring(0, motif.length() - 1)) : id.equals(motif);
    }

    private static Method estAVie;
    private static boolean estAVieCherche;

    /** Objet à vie (HeraliZ Tactique, {@code ObjetsAVie.estAVie}, par réflexion ; absent : non). */
    private static boolean aVie(ItemStack st) {
        if (!estAVieCherche) {
            estAVieCherche = true;
            try {
                estAVie = Class.forName("fr.heraliz.tactique.avie.ObjetsAVie").getMethod("estAVie", ItemStack.class);
            } catch (ReflectiveOperationException | LinkageError ex) {
                estAVie = null;
            }
        }
        if (estAVie == null) return false;
        try {
            return Boolean.TRUE.equals(estAVie.invoke(null, st));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return true;   // doute : chances gardées
        }
    }

    /**
     * Parts des raretés hautes d'une table, avant et après le réglage : {Rare, Épique, Légendaire avant, puis après}, en
     * part du total (case vide comprise). Pour /heralizloot beta.
     */
    static double[] parts(TablesCoffres.Table t) {
        double[] out = new double[6];
        Poids p = poids(t);
        List<TablesCoffres.Entree> objets = t.objets();
        double total = 0;
        for (TablesCoffres.Entree e : objets) total += e.poids();
        if (total <= 0) return out;
        for (int i = 0; i < objets.size(); i++) {
            TablesCoffres.Entree e = objets.get(i);
            if (e.item() == Items.AIR) continue;
            Rarete ra = RaretesObjets.de(modele(e));
            int k = ra == Rarete.RARE ? 0 : ra == Rarete.EPIQUE ? 1 : ra == Rarete.LEGENDAIRE ? 2 : -1;
            if (k < 0) continue;
            out[k] += e.poids() / total;
            out[3 + k] += (p == null ? e.poids() : p.poids()[i]) / total;
        }
        return out;
    }
}
