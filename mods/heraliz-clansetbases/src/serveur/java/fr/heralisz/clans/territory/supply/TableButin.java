package fr.heralisz.clans.territory.supply;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.model.Json;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Table de butin de la caisse de ravitaillement. Format maison (pas une loot table vanilla : les armes TaCZ passent par
 * du NBT et les objets d'autres mods absents en dev doivent etre ignores sans casser la table) :
 * <pre>{"tirages": [10, 14], "entrees": [{"item": "tacz:ammo", "nbt": "{AmmoId:\"tacz:762x39\"}", "min": 30, "max": 60, "poids": 80}, ...]}</pre>
 * Source : {@code config/heralisz_clans/ravitaillement.json} (ecrit au premier demarrage a partir de la ressource
 * {@code data/heralisz_clans/butin/ravitaillement.json} du jar, puis editable par l'admin) ; rechargee par
 * {@code /territoire recharger}. Un objet inconnu est ignore (journal une fois par id) ; les poids sont plafonnes a
 * 1 000 000 et les quantites a 64 pour qu'une edition absurde ne casse jamais le tirage.
 */
public final class TableButin {
    private TableButin() {
    }

    private static final ResourceLocation RESSOURCE = HeraliszClans.id("butin/ravitaillement.json");

    private record Entree(String item, @Nullable String nbt, int min, int max, int poids) {
    }

    /** Poids maximal d'une entree (la somme reste tres loin du debordement d'un int, meme avec des milliers d'entrees). */
    private static final int POIDS_MAX = 1_000_000;
    /** Quantite maximale par tirage (taille de pile maximale du jeu). */
    private static final int QUANTITE_MAX = 64;

    private static final List<Entree> ENTREES = new ArrayList<>();
    private static final Set<String> INCONNUS = new HashSet<>();
    private static int tiragesMin = 10, tiragesMax = 14, poidsTotal;
    private static boolean chargee;

    /** A relire au prochain remplissage (demarrage du serveur, /territoire recharger). */
    public static synchronized void recharger() {
        chargee = false;
    }

    private static synchronized void charger(MinecraftServer server) {
        if (chargee) return;
        chargee = true;
        ENTREES.clear();
        INCONNUS.clear();
        poidsTotal = 0;
        Path f = FMLPaths.CONFIGDIR.get().resolve("heralisz_clans").resolve("ravitaillement.json");
        String texte = null;
        try {
            if (Files.exists(f)) {
                texte = Files.readString(f, StandardCharsets.UTF_8);
            } else {
                Optional<Resource> res = server.getResourceManager().getResource(RESSOURCE);
                if (res.isPresent()) {
                    try (Reader r = res.get().openAsReader()) {
                        StringBuilder sb = new StringBuilder();
                        char[] buf = new char[4096];
                        int n;
                        while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
                        texte = sb.toString();
                    }
                    Files.createDirectories(f.getParent());
                    Files.writeString(f, texte, StandardCharsets.UTF_8);
                    HeraliszClans.LOG.info("[clans] ravitaillement.json ecrit dans la config (editable)");
                } else {
                    HeraliszClans.LOG.error("[clans] table de butin absente : {}", RESSOURCE);
                }
            }
        } catch (IOException ex) {
            HeraliszClans.LOG.error("[clans] table de butin illisible", ex);
        }
        if (texte == null) return;
        try {
            JsonObject o = JsonParser.parseString(texte).getAsJsonObject();
            JsonArray t = Json.arr(o, "tirages");
            if (t.size() == 2) {
                tiragesMin = Math.max(1, t.get(0).getAsInt());
                tiragesMax = Math.max(tiragesMin, t.get(1).getAsInt());
            }
            for (JsonElement e : Json.arr(o, "entrees")) {
                if (!e.isJsonObject()) continue;
                JsonObject eo = e.getAsJsonObject();
                String item = Json.str(eo, "item", "");
                String nbt = Json.str(eo, "nbt", null);
                long poidsLu = Json.lng(eo, "poids", 1);
                if (item.isEmpty() || poidsLu <= 0) continue;
                String[] conv = resoudre(item) == null ? convertirAncien(item, nbt) : null;
                if (conv != null && resoudre(conv[0]) != null) {
                    if (INCONNUS.add("conv:" + item)) HeraliszClans.LOG.info("[clans] table de butin : {} lu comme {} {}", item, conv[0], conv[1]);
                    item = conv[0];
                    nbt = conv[1];
                }
                // objet d'un mod absent (client de dev) : ecarte de la table, sinon ses tirages donneraient des cases vides
                if (resoudre(item) == null) {
                    if (INCONNUS.add(item)) HeraliszClans.LOG.warn("[clans] objet inconnu dans la table de butin, ignore : {}", item);
                    continue;
                }
                if (poidsLu > POIDS_MAX) HeraliszClans.LOG.warn("[clans] table de butin : poids {} de {} ramene a {}", poidsLu, item, POIDS_MAX);
                int poids = (int) Math.min(POIDS_MAX, poidsLu);
                if ((long) poidsTotal + poids > Integer.MAX_VALUE) {
                    HeraliszClans.LOG.warn("[clans] table de butin : somme des poids trop grande, entrees suivantes ignorees a partir de {}", item);
                    break;
                }
                int min = (int) Math.max(1, Math.min(QUANTITE_MAX, Json.lng(eo, "min", 1)));
                int max = (int) Math.max(min, Math.min(QUANTITE_MAX, Json.lng(eo, "max", min)));
                ENTREES.add(new Entree(item, nbt, min, max, poids));
                poidsTotal += poids;
            }
            HeraliszClans.LOG.info("[clans] table de butin du ravitaillement : {} entree(s), {} a {} tirages", ENTREES.size(), tiragesMin, tiragesMax);
        } catch (RuntimeException ex) {
            HeraliszClans.LOG.error("[clans] table de butin invalide", ex);
            ENTREES.clear();
            poidsTotal = 0;
        }
        if (ENTREES.isEmpty() || poidsTotal <= 0) HeraliszClans.LOG.warn("[clans] table de butin vide : les caisses de ravitaillement seront vides");
    }

    /** Remplit un conteneur (27 cases) : tirages ponderes places dans des cases libres au hasard. */
    public static void remplir(MinecraftServer server, Container c, RandomSource r) {
        charger(server);
        if (ENTREES.isEmpty() || poidsTotal <= 0) return;
        List<Integer> cases = new ArrayList<>(c.getContainerSize());
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).isEmpty()) cases.add(i);
        int n = tiragesMin + r.nextInt(tiragesMax - tiragesMin + 1);
        for (int i = 0; i < n && !cases.isEmpty(); i++) {
            ItemStack st = tirer(r);
            if (st == null) continue;
            int k = cases.remove(r.nextInt(cases.size()));
            c.setItem(k, st);
        }
    }

    @Nullable
    private static ItemStack tirer(RandomSource r) {
        int tirage = r.nextInt(poidsTotal);
        Entree choisi = ENTREES.get(ENTREES.size() - 1);
        for (Entree e : ENTREES) {
            tirage -= e.poids;
            if (tirage < 0) {
                choisi = e;
                break;
            }
        }
        return creer(choisi, r);
    }

    /**
     * Anciens identifiants de la premiere table (encore dans les copies de config/heralisz_clans/ravitaillement.json) :
     * les soins et grenades HeraliZ ne sont pas des objets du registre mais des piles de heralisz_tactical:consumable /
     * throwable portant leur id en NBT ({@code ConsumableId} / {@code ThrowableId}, comme les recettes des packs).
     * Renvoie {item, nbt} convertis, ou null si l'entree n'est pas concernee.
     */
    @Nullable
    private static String[] convertirAncien(String item, @Nullable String nbt) {
        if (nbt != null && !nbt.isEmpty()) return null;
        if (item.startsWith("heralisz_heal:")) return new String[]{"heralisz_tactical:consumable", "{ConsumableId:\"" + item + "\"}"};
        if (item.startsWith("heralisz_grenade:")) return new String[]{"heralisz_tactical:throwable", "{ThrowableId:\"" + item + "\"}"};
        return null;
    }

    /** L'objet du registre, ou null s'il est inconnu. */
    @Nullable
    private static Item resoudre(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item it = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        return it == null || it == Items.AIR ? null : it;
    }

    @Nullable
    private static ItemStack creer(Entree e, RandomSource r) {
        Item it = resoudre(e.item);
        if (it == null) return null;
        int count = e.min + (e.max > e.min ? r.nextInt(e.max - e.min + 1) : 0);
        ItemStack st = new ItemStack(it, Math.min(count, it.getMaxStackSize()));
        if (e.nbt != null && !e.nbt.isEmpty()) {
            try {
                st.setTag(TagParser.parseTag(e.nbt));
            } catch (Exception ex) {
                if (INCONNUS.add(e.item + e.nbt)) HeraliszClans.LOG.warn("[clans] NBT invalide dans la table de butin pour {} : {}", e.item, e.nbt);
            }
        }
        return st;
    }
}
