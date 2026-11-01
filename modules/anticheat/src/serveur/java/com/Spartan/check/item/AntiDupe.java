package com.Spartan.check.item;

import com.Spartan.Spartan;
import com.Spartan.alert.AlertManager;
import com.Spartan.check.movement.FlightGear;
import com.Spartan.config.SpartanConfig;
import com.Spartan.evidence.EvidenceRecorder;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Anti-duplication (alerte seulement, rien n'est supprimé).
 *
 * <p>Chaque objet de valeur NON empilable (liste antidupe.trackedItems) reçoit un numéro unique invisible
 * ({@value #CLE}) la première fois qu'il est vu dans l'inventaire d'un joueur. Toutes les {@code scanSeconds} secondes,
 * un relevé lit EN UNE FOIS (même tick) : inventaire, objet tenu par la souris, coffre de l'Ender et slots Curios de
 * chaque joueur connecté, et le contenu des sacs (données « Items » de l'objet, jars imbriqués compris jusqu'à 3
 * niveaux). Un même numéro à deux endroits au même instant ne peut être qu'une copie : alerte au staff (Discord + preuve).
 *
 * <p>Prudence pour ne rien casser :
 * <ul>
 *    <li>seuls les objets non empilables sont numérotés (un numéro empêcherait d'empiler) ;</li>
 *    <li>jamais l'objet en main, dans la main secondaire ou porté (une arme TaCZ rejouerait son animation de sortie,
 *    une armure son bruit d'équipement) : il sera numéroté plus tard, une fois rangé ;</li>
 *    <li>jamais dans un sac (le sac réécrit son contenu) : seulement lu ;</li>
 *    <li>joueurs en créatif / spectateur ignorés (le créatif copie les objets avec le bouton du milieu).</li>
 * </ul>
 * Limite : une copie faite avant que l'objet ait reçu son numéro n'est pas vue (les deux reçoivent des numéros
 * différents). Une copie rangée dans un coffre n'est vue qu'une fois ressortie.
 */
public final class AntiDupe {
    static final String CLE = "spartan_uid";
    private static final SecureRandom RNG = new SecureRandom();
    /** Numéro déjà signalé → moment : une seule alerte par copie et par demi-heure. */
    private static final Map<Long, Long> SIGNALES = new HashMap<>();
    private static final long RAPPEL_MS = 30 * 60 * 1000L;
    private static int ticks;

    private AntiDupe() {
    }

    /** Appelé à chaque tick du serveur. */
    public static void tick(MinecraftServer server) {
        if (!SpartanConfig.ANTIDUPE_ENABLED.get() || ++ticks < SpartanConfig.ANTIDUPE_SECONDS.get() * 20) {
            return;
        }
        ticks = 0;
        try {
            releve(server);
        } catch (RuntimeException e) {
            Spartan.LOGGER.warn("[Spartan] Anti-duplication : relevé interrompu ({})", e.toString());
        }
    }

    static void releve(MinecraftServer server) {
        Releve r = new Releve(SpartanConfig.getAntidupeItems());
        DupeIndex idx = r.idx;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p instanceof FakePlayer || p.getTags().contains("heraliz_bot") || p.isCreative() || p.isSpectator() || p.hasDisconnected()) {
                continue;
            }
            String nom = p.getName().getString();
            Inventory inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (st.isEmpty()) {
                    continue; // 2.9.5 : plus de texte « inventaire N » fabriqué pour une case vide
                }
                // numéroté seulement s'il est rangé : ni en main, ni porté (armure), ni dans la main secondaire
                boolean range = i < Inventory.INVENTORY_SIZE && i != inv.selected;
                voir(r, st, nom, "inventaire " + i, range, 0);
            }
            voir(r, p.containerMenu.getCarried(), nom, "objet tenu par la souris", false, 0);
            Container ender = p.getEnderChestInventory();
            for (int i = 0; i < ender.getContainerSize(); i++) {
                ItemStack st = ender.getItem(i);
                if (!st.isEmpty()) {
                    voir(r, st, nom, "coffre de l'Ender " + i, true, 0);
                }
            }
            List<ItemStack> curios = FlightGear.curios(p);
            for (int i = 0; i < curios.size(); i++) {
                ItemStack st = curios.get(i);
                if (!st.isEmpty()) {
                    voir(r, st, nom, "Curios " + i, false, 0);
                }
            }
        }
        long maintenant = System.currentTimeMillis();
        SIGNALES.values().removeIf(t -> maintenant - t > RAPPEL_MS);
        for (DupeIndex.Copie c : idx.copies()) {
            if (SIGNALES.containsKey(c.uid())) {
                continue;
            }
            SIGNALES.put(c.uid(), maintenant);
            signaler(server, c);
        }
    }

    /**
     * Un relevé (2.9.5, perf : le relevé de toutes les 20 s lit en un seul tick tous les inventaires, coffres de l'Ender, cases
     * Curios et sacs ; à 150 joueurs, plusieurs dizaines de ms sur ce tick). Identifiant et « suivi ? » de chaque objet
     * calculés une fois par relevé ; le contenu d'un sac n'est plus recréé en piles d'objets (événement des capacités de
     * Forge pour chaque objet) que pour les objets suivis : pour les autres, seul compte ce qu'ils contiennent. Mêmes
     * numéros, mêmes endroits, mêmes alertes.
     */
    private static final class Releve {
        final DupeIndex idx = new DupeIndex();
        final List<String> motifs;
        private final Map<Item, Boolean> suivis = new IdentityHashMap<>();

        Releve(List<String> motifs) {
            this.motifs = motifs;
        }

        /** L'objet est-il dans la liste suivie (antidupe.trackedItems) ? */
        boolean suivi(Item item, String id) {
            Boolean b = suivis.get(item);
            if (b == null) {
                b = DupeIndex.suivi(id, motifs);
                suivis.put(item, b);
            }
            return b;
        }
    }

    /** Un emplacement : numéroter l'objet si besoin, le noter, puis regarder dans son contenu (sac, boîte). */
    private static void voir(Releve r, ItemStack stack, String joueur, String endroit, boolean numeroter, int profondeur) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String id = idObjet(stack.getItem());
        if (stack.getMaxStackSize() == 1 && r.suivi(stack.getItem(), id)) {
            CompoundTag tag = stack.getTag();
            long uid = tag != null && tag.contains(CLE, Tag.TAG_LONG) ? tag.getLong(CLE) : 0L;
            if (uid == 0 && numeroter) {
                uid = nouveauNumero();
                stack.getOrCreateTag().putLong(CLE, uid);
            }
            r.idx.ajouter(stack, uid, id, new DupeIndex.Lieu(joueur, endroit));
        }
        CompoundTag tag = stack.getTag();
        if (tag != null && profondeur < 3) {
            ListTag sac = tag.getList("Items", Tag.TAG_COMPOUND);
            if (!sac.isEmpty()) {
                contenu(r, sac, joueur, "sac (" + endroit + ")", profondeur);
            }
            if (tag.contains("BlockEntityTag", Tag.TAG_COMPOUND)) {
                ListTag boite = tag.getCompound("BlockEntityTag").getList("Items", Tag.TAG_COMPOUND);
                if (!boite.isEmpty()) {
                    contenu(r, boite, joueur, "boîte (" + endroit + ")", profondeur);
                }
            }
        }
    }

    /** Contenu enregistré dans les données d'un objet : lu seulement (jamais numéroté ni modifié). */
    private static void contenu(Releve r, ListTag items, String joueur, String endroit, int profondeur) {
        for (int i = 0; i < items.size(); i++) {
            CompoundTag e = items.getCompound(i);
            // ce que ItemStack.of(e) garderait : objet du registre, quantité ; vide = case ignorée, comme avant
            Item item = objetDe(e);
            if (item == null || item == Items.AIR || e.getByte("Count") <= 0) {
                continue;
            }
            String id = idObjet(item);
            CompoundTag t;
            if (r.suivi(item, id)) {
                // objet suivi : lu comme avant, en pile (sa taille maximale peut dépendre de ses données)
                ItemStack s = ItemStack.of(e);
                if (s.isEmpty()) {
                    continue;
                }
                t = s.getTag();
                if (s.getMaxStackSize() == 1) {
                    long uid = t != null && t.contains(CLE, Tag.TAG_LONG) ? t.getLong(CLE) : 0L;
                    if (uid != 0) { // sans numéro, DupeIndex.ajouter ne note rien
                        r.idx.ajouter(e, uid, id, new DupeIndex.Lieu(joueur, endroit(endroit, e, i)));
                    }
                }
            } else {
                t = e.contains("tag", Tag.TAG_COMPOUND) ? e.getCompound("tag") : null;
            }
            if (t != null && profondeur + 1 < 3) {
                ListTag dedans = t.getList("Items", Tag.TAG_COMPOUND);
                if (!dedans.isEmpty()) {
                    contenu(r, dedans, joueur, "sac (" + endroit(endroit, e, i) + ")", profondeur + 1);
                }
            }
        }
    }

    /** « sac (inventaire 2) 7 » : l'endroit du contenant, puis la case (« Slot » des données, sinon le rang). */
    private static String endroit(String contenant, CompoundTag e, int i) {
        return contenant + " " + (e.contains("Slot") ? e.getInt("Slot") : i);
    }

    /** L'objet que {@code ItemStack.of(e)} lirait (identifiant du registre, objet par défaut s'il est inconnu), null si illisible. */
    private static Item objetDe(CompoundTag e) {
        ResourceLocation rl = ResourceLocation.tryParse(e.getString("id"));
        return rl == null ? null : BuiltInRegistries.ITEM.get(rl);
    }

    private static long nouveauNumero() {
        long v;
        do {
            v = RNG.nextLong();
        } while (v == 0);
        return v;
    }

    static String idObjet(ItemStack stack) {
        return idObjet(stack.getItem());
    }

    /** Identifiant de l'objet en minuscules, « ? » s'il n'est pas dans le registre ; gardé (le registre ne change plus). */
    static String idObjet(Item item) {
        String id = IDS.get(item);
        if (id == null) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            id = key == null ? "?" : key.toString().toLowerCase(Locale.ROOT);
            IDS.put(item, id);
        }
        return id;
    }

    private static final Map<Item, String> IDS = new IdentityHashMap<>();

    private static void signaler(MinecraftServer server, DupeIndex.Copie c) {
        String ou = c.lieux().stream().map(DupeIndex.Lieu::toString).collect(Collectors.joining(", "));
        String details = c.objet() + " en " + c.lieux().size() + " exemplaires identiques au même instant : " + ou;
        JsonObject root = new JsonObject();
        root.addProperty("type", "duplication");
        root.addProperty("objet", c.objet());
        root.addProperty("numero", Long.toHexString(c.uid()));
        JsonArray lieux = new JsonArray();
        c.lieux().forEach(l -> lieux.add(l.toString()));
        root.add("lieux", lieux);
        String premier = c.lieux().get(0).joueur();
        String preuve = EvidenceRecorder.writeJson(premier, "duplication",
                new GsonBuilder().setPrettyPrinting().create().toJson(root));
        ServerPlayer joueur = server.getPlayerList().getPlayerByName(premier);
        if (joueur != null) {
            AlertManager.dispatch(joueur, "Duplication", 1, details, preuve);
        } else {
            AlertManager.info(server, "Duplication", details, AlertManager.COLOR_INFO);
        }
    }

    /** Oubli au redémarrage (tests). */
    static void reinitialiser() {
        SIGNALES.clear();
        ticks = 0;
    }
}
