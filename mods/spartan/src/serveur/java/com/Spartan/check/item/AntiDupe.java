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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
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
        List<String> motifs = SpartanConfig.getAntidupeItems();
        DupeIndex idx = new DupeIndex();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p instanceof FakePlayer || p.isCreative() || p.isSpectator() || p.hasDisconnected()) {
                continue;
            }
            String nom = p.getName().getString();
            Inventory inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                // numéroté seulement s'il est rangé : ni en main, ni porté (armure), ni dans la main secondaire
                boolean range = i < Inventory.INVENTORY_SIZE && i != inv.selected;
                voir(idx, inv.getItem(i), nom, "inventaire " + i, motifs, range, 0);
            }
            voir(idx, p.containerMenu.getCarried(), nom, "objet tenu par la souris", motifs, false, 0);
            Container ender = p.getEnderChestInventory();
            for (int i = 0; i < ender.getContainerSize(); i++) {
                voir(idx, ender.getItem(i), nom, "coffre de l'Ender " + i, motifs, true, 0);
            }
            List<ItemStack> curios = FlightGear.curios(p);
            for (int i = 0; i < curios.size(); i++) {
                voir(idx, curios.get(i), nom, "Curios " + i, motifs, false, 0);
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

    /** Un emplacement : numéroter l'objet si besoin, le noter, puis regarder dans son contenu (sac, boîte). */
    private static void voir(DupeIndex idx, ItemStack stack, String joueur, String endroit, List<String> motifs,
                             boolean numeroter, int profondeur) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String id = idObjet(stack);
        if (stack.getMaxStackSize() == 1 && DupeIndex.suivi(id, motifs)) {
            CompoundTag tag = stack.getTag();
            long uid = tag != null && tag.contains(CLE, Tag.TAG_LONG) ? tag.getLong(CLE) : 0L;
            if (uid == 0 && numeroter) {
                uid = nouveauNumero();
                stack.getOrCreateTag().putLong(CLE, uid);
            }
            idx.ajouter(stack, uid, id, new DupeIndex.Lieu(joueur, endroit));
        }
        CompoundTag tag = stack.getTag();
        if (tag != null && profondeur < 3) {
            contenu(idx, tag.getList("Items", Tag.TAG_COMPOUND), joueur, "sac (" + endroit + ")", motifs, profondeur);
            CompoundTag be = tag.getCompound("BlockEntityTag");
            contenu(idx, be.getList("Items", Tag.TAG_COMPOUND), joueur, "boîte (" + endroit + ")", motifs, profondeur);
        }
    }

    /** Contenu enregistré dans les données d'un objet : lu seulement (jamais numéroté ni modifié). */
    private static void contenu(DupeIndex idx, ListTag items, String joueur, String endroit, List<String> motifs,
                                int profondeur) {
        for (int i = 0; i < items.size(); i++) {
            CompoundTag e = items.getCompound(i);
            ItemStack s = ItemStack.of(e);
            if (s.isEmpty()) {
                continue;
            }
            String place = endroit + " " + (e.contains("Slot") ? e.getInt("Slot") : i);
            String id = idObjet(s);
            if (s.getMaxStackSize() == 1 && DupeIndex.suivi(id, motifs)) {
                CompoundTag t = s.getTag();
                long uid = t != null && t.contains(CLE, Tag.TAG_LONG) ? t.getLong(CLE) : 0L;
                idx.ajouter(e, uid, id, new DupeIndex.Lieu(joueur, place));
            }
            CompoundTag t = s.getTag();
            if (t != null && profondeur + 1 < 3) {
                contenu(idx, t.getList("Items", Tag.TAG_COMPOUND), joueur, "sac (" + place + ")", motifs, profondeur + 1);
            }
        }
    }

    private static long nouveauNumero() {
        long v;
        do {
            v = RNG.nextLong();
        } while (v == 0);
        return v;
    }

    static String idObjet(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? "?" : key.toString().toLowerCase(Locale.ROOT);
    }

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
