package fr.heraliz.quetes.serveur;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Points de saison des clans gagnés avec HeraliZ Quêtes (HeraliZ Clans 3.6.0, {@code SaisonApi.pve}, par réflexion ;
 * absent : rien). Le barème est chez Clans ; règles contre les abus ici :
 * <ul>
 * <li>quête principale finie (suite d'un PNJ, jamais refaite) : {@code quete_principale} ;</li>
 * <li>vente à un marchand : {@code vente_1000} par tranche de 1 000 hérals, seulement pour des objets qu'AUCUN marchand ne
 * vend (sinon acheter puis revendre ferait des points) ; plafond par jour chez Clans ;</li>
 * <li>arme fabriquée à la Table d'armurier de TaCZ : {@code craft_<rareté>} pour chaque arme qui apparaît dans
 * l'inventaire pendant que la table est ouverte, sauf celles ramassées au sol entre-temps ; plafond par jour chez Clans.</li>
 * </ul>
 */
final class SaisonQuetes {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    private SaisonQuetes() {
    }

    private static boolean cherche;
    private static Method pve;

    static void pve(ServerPlayer p, String source, double quantite) {
        if (!cherche) {
            cherche = true;
            try {
                pve = Class.forName("fr.heralisz.clans.saison.SaisonApi").getMethod("pve", ServerPlayer.class, String.class, double.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                pve = null;
            }
        }
        if (pve == null || quantite <= 0 || p.isCreative() || p.isSpectator()) return;
        try {
            pve.invoke(null, p, source, quantite);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warn("[heraliz_quetes] saison des clans : {}", e.toString());
        }
    }

    // ------------------------------------------------------------------ ventes
    /**
     * Prix de vente le plus bas des objets qu'on obtient d'un marchand (25/10, règle des propriétaires : un objet qu'un PNJ
     * vend au prix P n'est jamais racheté plus de 50 % de P) : clé « id » (objet sans données) ou « id{données} » (24/10 :
     * avant, l'identifiant seul ; Jack vendant quelques accessoires, plus AUCUN accessoire n'était racheté, même trouvé
     * sur la carte) -> plus petit prix à l'unité chez tous les vendeurs (fichiers et changements en jeu au Registre). Un
     * objet reçu par un échange (1.11.0) vaut 0 : il ne se rachète ni ne se recycle jamais (on l'a eu sans payer de
     * hérals, échanger puis revendre ferait de l'argent). Refait à chaque appel : les marchands changent en jeu.
     */
    static Map<String, Long> ventes() {
        Map<String, Long> v = new HashMap<>();
        for (Defs.Pnj d : Catalogue.tous()) {
            if (d.marchand == null) continue;
            for (Defs.EchangeDef e : d.marchand.echanges) v.put(cle(e.recu(), e.nbtRecu()), 0L);
            if (d.marchand.rachat) continue;
            for (Defs.ArticleDef a : d.marchand.articles) v.merge(cle(a.objet(), a.nbt()), Math.max(0, a.prix()), Math::min);
        }
        return v;
    }

    private static String cle(String objet, String nbt) {
        return nbt == null || nbt.isBlank() ? objet : objet + nbt.trim();
    }

    /** Données des articles déjà lues (« id{données} » -> données), pour ne pas relire le texte à chaque case. */
    private static final Map<String, net.minecraft.nbt.CompoundTag> DONNEES = new java.util.concurrent.ConcurrentHashMap<>();

    /** L'objet s'obtient-il chez un marchand (vente ou échange) ? */
    static boolean vendu(Map<String, Long> ventes, ItemStack s) {
        return prixVente(ventes, s) >= 0;
    }

    /**
     * Plus petit prix à l'unité auquel un marchand vend cet objet (0 : reçu par un échange), ou -1 si aucun marchand ne le
     * vend. Un objet correspond à un article s'il porte au moins les données de l'article.
     */
    static long prixVente(Map<String, Long> ventes, ItemStack s) {
        if (s.isEmpty() || ventes.isEmpty()) return -1;
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(s.getItem());
        if (rl == null) return -1;
        String id = rl.toString();
        long min = ventes.getOrDefault(id, -1L);
        for (Map.Entry<String, Long> en : ventes.entrySet()) {
            String v = en.getKey();
            if (v.length() <= id.length() || !v.startsWith(id) || v.charAt(id.length()) != '{') continue;
            if (min >= 0 && en.getValue() >= min) continue;
            net.minecraft.nbt.CompoundTag voulu = DONNEES.computeIfAbsent(v, k -> {
                try {
                    // 25/10 : sans les données qui changent à l'usage (munitions, mode de tir, accessoires posés, usure,
                    // marque anti-copie) : une arme ajoutée en jeu au Registre, achetée puis tirée une fois, n'était plus
                    // reconnue et se revendait à Mila
                    net.minecraft.nbt.CompoundTag t = net.minecraft.nbt.TagParser.parseTag(k.substring(id.length()));
                    Echanges.retirerVolatiles(t);
                    t.remove("HzUsureEtat");
                    return t;
                } catch (Exception ex) {
                    // données illisibles : ne doivent jamais correspondre (une étiquette vide correspondrait à tout)
                    net.minecraft.nbt.CompoundTag jamais = new net.minecraft.nbt.CompoundTag();
                    jamais.putBoolean("heraliz_illisible", true);
                    return jamais;
                }
            });
            if (voulu.isEmpty() || s.hasTag() && net.minecraft.nbt.NbtUtils.compareNbt(voulu, s.getTag(), true)) min = en.getValue();
        }
        return min;
    }

    /** Hérals touchés pour des objets qu'aucun marchand ne vend. */
    static void vente(ServerPlayer p, long herals) {
        if (herals > 0) pve(p, "vente_1000", herals / 1000.0);
    }

    // ------------------------------------------------------------------ fabrication
    /** Armes (identifiant TaCZ -> nombre) au dernier relevé, table ouverte ; absent = table fermée. */
    private static final Map<UUID, Map<String, Integer>> RELEVES = new HashMap<>();
    /** Armes ramassées au sol depuis le dernier relevé (pas fabriquées). */
    private static final Map<UUID, Map<String, Integer>> RAMASSEES = new HashMap<>();

    static void oublier(UUID u) {
        RELEVES.remove(u);
        RAMASSEES.remove(u);
    }

    static void ramassage(EntityItemPickupEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !RELEVES.containsKey(p.getUUID())) return;
        String g = Ponts.gunId(e.getItem().getItem());
        if (g != null) RAMASSEES.computeIfAbsent(p.getUUID(), k -> new HashMap<>()).merge(g, e.getItem().getItem().getCount(), Integer::sum);
    }

    /** Toutes les 10 ticks : armes apparues dans l'inventaire, table d'armurier ouverte. */
    static void fabrication(ServerPlayer p) {
        if (!Ponts.tableArmes(p)) {
            oublier(p.getUUID());
            return;
        }
        Map<String, Integer> now = new HashMap<>();
        Map<String, ItemStack> exemple = new HashMap<>();
        for (ItemStack s : p.getInventory().items) {
            String g = Ponts.gunId(s);
            if (g == null || Ponts.couteau(s)) continue;
            now.merge(g, s.getCount(), Integer::sum);
            exemple.putIfAbsent(g, s);
        }
        Map<String, Integer> avant = RELEVES.put(p.getUUID(), now);
        Map<String, Integer> ram = RAMASSEES.remove(p.getUUID());
        if (avant == null) return;
        for (Map.Entry<String, Integer> e : now.entrySet()) {
            int plus = e.getValue() - avant.getOrDefault(e.getKey(), 0) - (ram == null ? 0 : ram.getOrDefault(e.getKey(), 0));
            if (plus <= 0) continue;
            int r = Ponts.rarete(exemple.get(e.getKey()));
            String nom = Ponts.RARETES.get(Math.max(0, Math.min(5, r)));
            pve(p, "craft_" + nom, plus);
        }
    }
}
