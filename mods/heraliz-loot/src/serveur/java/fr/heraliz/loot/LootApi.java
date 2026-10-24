package fr.heraliz.loot;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Contenu d'un conteneur tiré dans une table de HeraliZ Loot, pour les conteneurs des autres mods (0.12.2, 24/10 :
 * caisses de l'hélicoptère de patrouille de l'Airstrike). Mêmes règles qu'un conteneur de la carte : nombre d'objets de
 * la table (« cases_poids »), première case jamais vide, pas de doublon, bonus de loot de celui qui ouvre (annoncé), points
 * de saison d'un conteneur de ce palier. Lue par réflexion par l'Airstrike : ne jamais renommer la classe ni la méthode,
 * ni changer sa signature.
 */
public final class LootApi {
    private LootApi() {
    }

    /**
     * Objets d'un conteneur de cette table (« t6 », « t7 », nom d'un conteneur...), au plus max ; liste vide si la table
     * n'existe pas. joueur : celui qui ouvre (bonus de loot, saison), ou null.
     */
    public static List<ItemStack> tirer(ServerLevel l, String table, @Nullable ServerPlayer joueur, int max) {
        List<ItemStack> out = new ArrayList<>();
        TablesCoffres.Choix c = TablesCoffres.pourConteneur(table);
        if (c == null || c.table().objets().isEmpty()) {
            HeralizLoot.LOG.warn("LootApi : aucune table « {} »", table);
            return out;
        }
        int bonus = joueur != null ? BonusLoot.pourcent(joueur) : 0;
        int n = Math.min(Math.max(0, max), TablesCoffres.nombre(c.table(), c.min(), c.max(), l.random));
        Set<String> deja = new HashSet<>();
        for (int i = 0; i < n; i++) {
            ItemStack st = i == 0 ? TablesCoffres.tirerPlein(c.table(), l.random, bonus, deja)
                    : TablesCoffres.tirerUnique(c.table(), l.random, bonus, deja);
            if (!st.isEmpty()) out.add(st);
        }
        if (joueur != null) {
            BonusLoot.annoncer(joueur, bonus);
            int palier = ConteneurBlock.palier(table);
            if (palier > 0) SaisonLoot.conteneur(joueur, palier);
        }
        return out;
    }
}
