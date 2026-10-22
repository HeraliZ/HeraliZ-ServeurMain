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
    /** Identifiants des objets vendus par au moins un marchand (refait à chaque appel : les marchands changent en jeu). */
    static Set<String> vendus() {
        Set<String> s = new HashSet<>();
        for (Defs.Pnj d : Catalogue.tous()) {
            if (d.marchand == null || d.marchand.rachat) continue;
            for (Defs.ArticleDef a : d.marchand.articles) s.add(a.objet());
        }
        return s;
    }

    static boolean vendu(Set<String> vendus, ItemStack s) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        return id != null && vendus.contains(id.toString());
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
