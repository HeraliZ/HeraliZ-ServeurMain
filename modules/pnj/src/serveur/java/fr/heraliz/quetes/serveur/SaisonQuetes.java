package fr.heraliz.quetes.serveur;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Points de saison des clans gagnés avec HeraliZ Quêtes (HeraliZ Clans 3.6.0, {@code SaisonApi.pve}, par réflexion ;
 * absent : rien). Le barème est chez Clans ; règles contre les abus ici :
 * <ul>
 * <li>quête principale finie (suite d'un PNJ, jamais refaite) : {@code quete_principale} ;</li>
 * <li>vente à un marchand : {@code vente_1000} par tranche de 1 000 hérals, seulement pour des objets qu'AUCUN marchand ne
 * vend (sinon acheter puis revendre ferait des points) ; plafond par jour chez Clans ;</li>
 * <li>arme fabriquée à la Table d'armurier de TaCZ : {@code craft_<rareté>} pour chaque arme fabriquée (11/11 : voir
 * {@link #ramassage}) ; plafond par jour chez Clans.</li>
 * </ul>
 * Joueur en créatif ou en spectateur : refusé par la saison elle-même (11/11 : Clans le décide pour toutes les sources et
 * prévient le joueur ; avant, filtré ici en silence).
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
        if (pve == null || quantite <= 0) return;
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
     * hérals, échanger puis revendre ferait de l'argent). Les marchands changent en jeu : table refaite au plus une fois
     * par seconde (02/11, audit mods-3 §13 : avant, à chaque appel, soit à chaque tick par écran du recycleur de la carte
     * ouvert) ; lecture seule.
     */
    static Map<String, Long> ventes() {
        long t = System.currentTimeMillis();
        Map<String, Long> c = cacheVentes;
        if (c != null && t - cacheVentesQuand >= 0 && t - cacheVentesQuand < 1000) return c;
        c = java.util.Collections.unmodifiableMap(calculerVentes());
        cacheVentes = c;
        cacheVentesQuand = t;
        return c;
    }

    private static Map<String, Long> cacheVentes;
    private static long cacheVentesQuand;

    /** Les marchands viennent de changer (édition en jeu, rechargement) : la table est refaite au prochain appel. */
    static void oublierVentes() {
        cacheVentes = null;
    }

    private static Map<String, Long> calculerVentes() {
        Map<String, Long> v = new HashMap<>();
        for (Defs.Pnj d : Catalogue.tous()) {
            if (d.marchand == null) continue;
            for (Defs.EchangeDef e : d.marchand.echanges) v.put(cle(e.recu(), e.nbtRecu()), 0L);
            if (d.marchand.rachat) continue;
            // prix payé (économie de la bêta, 03/11) : le plafond des 50 % du rachat et du recycleur part du prix réduit
            for (Defs.ArticleDef a : d.marchand.articles) v.merge(cle(a.objet(), a.nbt()), Math.max(0, Ponts.prixAchat(a.prix())), Math::min);
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
    /**
     * Âge (ticks) en dessous duquel un objet au sol ramassé est l'arme que la table vient de fabriquer : TaCZ la pose aux
     * pieds du joueur SANS délai de ramassage ; tout autre objet au sol attend au moins 10 ticks avant de pouvoir être
     * ramassé (objet d'un bloc 10, jeté par un joueur ou à sa mort 40, inventaire plein des mods 40).
     */
    private static final int AGE_FABRIQUEE = 10;
    /** Joueurs dont la Table d'armurier était ouverte au dernier relevé (toutes les 10 ticks). */
    private static final Set<UUID> TABLE = new HashSet<>();
    /** Armes au sol déjà comptées (identifiant de l'objet au sol) : l'évènement revient à chaque tick où on la touche. */
    private static final Set<UUID> COMPTEES = new java.util.LinkedHashSet<>();

    static void oublier(UUID u) {
        TABLE.remove(u);
    }

    /**
     * Arme fabriquée (11/11, retour du propriétaire : « fabriquer une arme » ne donnait jamais de points). Cause : TaCZ
     * 1.1.8 (GunSmithTableMenu.doCraft) et TaCZ Addon (CraftingTransaction.spawnOutput) ne mettent JAMAIS l'arme
     * fabriquée dans l'inventaire : ils la posent au sol aux pieds du joueur, sans délai de ramassage, et le joueur la
     * ramasse au tick suivant. L'ancien relevé de l'inventaire retirait toute arme ramassée au sol table ouverte (contre la
     * triche d'une arme jetée par un ami) : il retirait donc aussi chaque arme fabriquée, et rien n'était jamais donné.
     * Maintenant : une arme touchée au sol pendant que la table est ouverte (ou l'était au dernier relevé : la table fermée
     * juste après le clic), dont l'objet au sol a moins de {@link #AGE_FABRIQUEE} ticks, est une arme fabriquée, comptée
     * une seule fois (même inventaire plein : elle a bien été fabriquée) ; une arme jetée par quelqu'un d'autre ne se
     * ramasse qu'après 40 ticks et ne compte jamais.
     */
    static void ramassage(EntityItemPickupEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (!TABLE.contains(p.getUUID()) && !Ponts.tableArmes(p)) return;
        ItemEntity sol = e.getItem();
        ItemStack s = sol.getItem();
        if (s.isEmpty() || sol.getAge() >= AGE_FABRIQUEE || Ponts.gunId(s) == null || Ponts.couteau(s)) return;
        if (!COMPTEES.add(sol.getUUID())) return;
        if (COMPTEES.size() > 256) COMPTEES.remove(COMPTEES.iterator().next());
        int r = Ponts.rarete(s);
        pve(p, "craft_" + Ponts.RARETES.get(Math.max(0, Math.min(5, r))), s.getCount());
    }

    /** Toutes les 10 ticks : la Table d'armurier est-elle ouverte ? (seul relevé, aucun parcours de l'inventaire). */
    static void fabrication(ServerPlayer p) {
        if (Ponts.tableArmes(p)) TABLE.add(p.getUUID());
        else if (!TABLE.isEmpty()) TABLE.remove(p.getUUID());
    }
}
