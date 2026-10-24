package fr.heralisz.utils.diag;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Relevé de l'état du serveur : joueurs, entités par monde et par type, tronçons chargés. Pris sur le fil du serveur
 * (une boucle sur les entités, sans rien écrire) toutes les minutes, au début et à la fin d'un profil et pour
 * {@code /diag etat} ; lu ensuite par les autres fils (objet figé).
 */
record Instantane(long quand, int joueurs, List<Monde> mondes, Map<String, Integer> types) {

    record Monde(String nom, int entites, int troncons, Map<String, Integer> types) {
    }

    /** Dernier relevé (lu par les rapports et le résumé). */
    static volatile Instantane dernier;

    /** Fil du serveur. */
    static Instantane prendre(MinecraftServer srv) {
        List<Monde> mondes = new ArrayList<>();
        Map<String, Integer> tous = new HashMap<>();
        for (ServerLevel l : srv.getAllLevels()) {
            Map<EntityType<?>, int[]> parType = new HashMap<>();
            int n = 0;
            for (Entity e : l.getAllEntities()) {
                if (e == null) continue;
                parType.computeIfAbsent(e.getType(), k -> new int[1])[0]++;
                n++;
            }
            Map<String, Integer> types = new HashMap<>();
            parType.forEach((t, c) -> {
                String nom = nom(t);
                types.merge(nom, c[0], Integer::sum);
                tous.merge(nom, c[0], Integer::sum);
            });
            int troncons;
            try {
                troncons = l.getChunkSource().getLoadedChunksCount();
            } catch (RuntimeException ex) {
                troncons = -1;
            }
            mondes.add(new Monde(l.dimension().location().toString(), n, troncons, trie(types)));
        }
        Instantane i = new Instantane(System.currentTimeMillis(), srv.getPlayerCount(), List.copyOf(mondes), trie(tous));
        dernier = i;
        return i;
    }

    static String nom(EntityType<?> t) {
        ResourceLocation r = EntityType.getKey(t);
        return r == null ? String.valueOf(t) : r.toString();
    }

    int entites() {
        int n = 0;
        for (Monde m : mondes) n += m.entites;
        return n;
    }

    int troncons() {
        int n = 0;
        for (Monde m : mondes) n += Math.max(0, m.troncons);
        return n;
    }

    /** Les {@code max} types les plus nombreux : « zombie 120, item 80… ». */
    String plusNombreux(int max) {
        StringBuilder b = new StringBuilder();
        int k = 0;
        for (Map.Entry<String, Integer> e : types.entrySet()) {
            if (k++ >= max) break;
            if (b.length() > 0) b.append(", ");
            b.append(court(e.getKey())).append(' ').append(e.getValue());
        }
        return b.length() == 0 ? "-" : b.toString();
    }

    String mondesCourt() {
        StringBuilder b = new StringBuilder();
        for (Monde m : mondes) {
            if (b.length() > 0) b.append(", ");
            b.append(court(m.nom)).append(' ').append(m.entites).append(" ent. / ").append(m.troncons).append(" tr.");
        }
        return b.toString();
    }

    static String court(String id) {
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    static Map<String, Integer> trie(Map<String, Integer> m) {
        List<Map.Entry<String, Integer>> l = new ArrayList<>(m.entrySet());
        l.sort((a, b) -> b.getValue() != a.getValue().intValue() ? Integer.compare(b.getValue(), a.getValue()) : a.getKey().compareTo(b.getKey()));
        Map<String, Integer> r = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : l) r.put(e.getKey(), e.getValue());
        return r;
    }

    /** Bloc de texte pour un rapport. */
    void decrire(StringBuilder b, String titre) {
        b.append("== ").append(titre).append(" ==\n");
        b.append(String.format(Locale.ROOT, "Relevé à %s : %d joueur(s), %d entités, %d tronçons chargés%n",
                Rapports.heure(quand), joueurs, entites(), troncons()));
        for (Monde m : mondes) {
            b.append(String.format(Locale.ROOT, "  %-28s %6d entités  %6d tronçons%n", m.nom, m.entites, m.troncons));
        }
        b.append("Types les plus nombreux (tous les mondes) :\n");
        int k = 0;
        for (Map.Entry<String, Integer> e : types.entrySet()) {
            if (k++ >= 25) break;
            b.append(String.format(Locale.ROOT, "  %6d  %s%n", e.getValue(), e.getKey()));
        }
        b.append('\n');
    }
}
