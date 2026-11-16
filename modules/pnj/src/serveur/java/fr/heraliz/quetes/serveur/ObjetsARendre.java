package fr.heraliz.quetes.serveur;

import fr.heraliz.quetes.Texte;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Objets donnés par les quêtes, les quêtes du jour et le staff qui ne tiennent pas dans l'inventaire (02/11, audit points
 * 8 et 23) : gardés AVEC le joueur ({@code PlayerPersisted.HeralizQuetesARendre}, enregistrés avec son inventaire :
 * jamais à deux endroits, jamais au sol, objets à vie compris) et rendus dès qu'il y a de la place : à la connexion, à
 * la réapparition et toutes les 5 s. Fil serveur seulement.
 * <p>15/11 (audit n° 1) : rien n'est livré pendant un match du ClashWar ni dans ses arènes, ni tant que son inventaire est
 * mis de côté par le ClashWar ({@link #retenuParMatch}) : l'inventaire du match est effacé au retour. Les objets attendent
 * la fin du match et le retour, puis sont livrés (essai toutes les 5 s).
 */
final class ObjetsARendre {
    private ObjetsARendre() {
    }

    private static final String PERSISTE = "PlayerPersisted", CLE = "HeralizQuetesARendre";
    /** Drapeau « en match » et marque du Coffre du ClashWar : clés de données partagées, lues sans dépendance. */
    private static final String DRAPEAU_MATCH = "heralisz:en_match", MARQUE_COFFRE = "HeralizClashWarDepot";
    /** Joueurs connectés qui ont des objets à rendre (pour ne pas parcourir tout le monde toutes les 5 s). */
    private static final Set<UUID> EN_ATTENTE = new HashSet<>();

    /** Donne l'objet ; ce qui ne tient pas est gardé avec le joueur et rendu plus tard (le joueur est prévenu). */
    static void donner(ServerPlayer p, ItemStack s) {
        if (s == null || s.isEmpty()) return;
        // 15/11 : en match (ou inventaire mis de côté par le ClashWar), jamais dans l'inventaire du match : il attend
        boolean enMatch = retenuParMatch(p);
        if (!enMatch) {
            p.getInventory().add(s);
            p.getInventory().setChanged();
        }
        if (s.isEmpty()) return;
        CompoundTag d = p.getPersistentData();
        if (!d.contains(PERSISTE, Tag.TAG_COMPOUND)) d.put(PERSISTE, new CompoundTag());
        CompoundTag t = d.getCompound(PERSISTE);
        ListTag l = t.getList(CLE, Tag.TAG_COMPOUND);
        l.add(s.save(new CompoundTag()));
        t.put(CLE, l);
        EN_ATTENTE.add(p.getUUID());
        Moteur.notif(p, Texte.cle(enMatch ? "heraliz_quetes.notif.attente_match" : "heraliz_quetes.notif.attente_place",
                Texte.brut(s.getCount()), Ponts.texteObjet(s)), 0xFFB060);
    }

    /**
     * Le ClashWar tient l'inventaire de ce joueur (15/11) : en match (salle d'attente comprise), dans un monde de ses arènes,
     * ou inventaire mis de côté par son Coffre (marque posée au départ, retirée au retour ; elle couvre aussi la reconnexion
     * après un plantage, avant que le Coffre soit rendu). Ce qui y serait livré serait effacé au retour.
     */
    static boolean retenuParMatch(ServerPlayer p) {
        if (fr.heraliz.noyau.api.Matchs.enMatch(p)) return true;
        CompoundTag d = p.getPersistentData();
        if (d.getBoolean(DRAPEAU_MATCH) || d.getCompound(PERSISTE).contains(MARQUE_COFFRE)) return true;
        return "heraliz_clashwar".equals(p.level().dimension().location().getNamespace());
    }

    /** Rend ce qui tient maintenant ; le reste continue d'attendre. */
    static void rendre(ServerPlayer p) {
        CompoundTag t = p.getPersistentData().getCompound(PERSISTE);
        if (!t.contains(CLE, Tag.TAG_LIST)) {
            EN_ATTENTE.remove(p.getUUID());
            return;
        }
        if (!p.isAlive() || p.isSpectator()) return;
        if (retenuParMatch(p)) {
            EN_ATTENTE.add(p.getUUID());   // réessayé toutes les 5 s : livré après le retour
            return;
        }
        ListTag l = t.getList(CLE, Tag.TAG_COMPOUND), reste = new ListTag();
        int rendus = 0;
        for (int i = 0; i < l.size(); i++) {
            ItemStack s = ItemStack.of(l.getCompound(i));
            if (s.isEmpty()) continue;
            int avant = s.getCount();
            p.getInventory().add(s);
            rendus += avant - s.getCount();
            if (!s.isEmpty()) reste.add(s.save(new CompoundTag()));
        }
        if (reste.isEmpty()) {
            t.remove(CLE);
            EN_ATTENTE.remove(p.getUUID());
        } else {
            t.put(CLE, reste);
            EN_ATTENTE.add(p.getUUID());
        }
        if (rendus > 0) {
            p.getInventory().setChanged();
            Moteur.notif(p, Texte.cle("heraliz_quetes.notif.attente_rendue", Texte.brut(rendus)), 0x7BD88F);
        }
    }

    /** Toutes les 5 s : seulement les joueurs connectés qui ont des objets à rendre. */
    static void tick(MinecraftServer srv) {
        if (EN_ATTENTE.isEmpty() || srv.getTickCount() % 100 != 0) return;
        for (UUID u : List.copyOf(EN_ATTENTE)) {
            ServerPlayer p = srv.getPlayerList().getPlayer(u);
            if (p == null) EN_ATTENTE.remove(u);
            else rendre(p);
        }
    }

    static void oublier(UUID u) {
        EN_ATTENTE.remove(u);
    }

    static void vider() {
        EN_ATTENTE.clear();
    }

    /**
     * Ces piles tiendraient toutes dans l'inventaire, comme {@link Inventory#add} les y mettrait : sur les piles pareilles
     * (36 cases et main gauche), puis dans les cases vides des 36 cases.
     */
    static boolean tiennent(Inventory inv, List<ItemStack> piles) {
        int vides = 0;
        for (ItemStack s : inv.items) if (s.isEmpty()) vides++;
        // groupes d'objets qui s'empilent ensemble (même objet, mêmes données)
        List<ItemStack> refs = new ArrayList<>();
        List<Long> besoins = new ArrayList<>();
        for (ItemStack s : piles) {
            if (s.isEmpty()) continue;
            if (!s.isStackable()) {
                vides--;
                continue;
            }
            int k = 0;
            while (k < refs.size() && !ItemStack.isSameItemSameTags(refs.get(k), s)) k++;
            if (k == refs.size()) {
                refs.add(s);
                besoins.add(0L);
            }
            besoins.set(k, besoins.get(k) + s.getCount());
        }
        if (vides < 0) return false;
        for (int k = 0; k < refs.size(); k++) {
            ItemStack ref = refs.get(k);
            int max = ref.getMaxStackSize();
            long place = 0;
            for (ItemStack s : inv.items) if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, ref)) place += Math.max(0, max - s.getCount());
            for (ItemStack s : inv.offhand) if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, ref)) place += Math.max(0, max - s.getCount());
            long manque = Math.max(0, besoins.get(k) - place);
            vides -= (int) ((manque + max - 1) / max);
            if (vides < 0) return false;
        }
        return true;
    }
}
