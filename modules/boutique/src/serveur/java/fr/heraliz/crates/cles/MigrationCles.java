package fr.heraliz.crates.cles;

import fr.heraliz.crates.HeralizCrates;
import fr.heraliz.crates.crate.CrateType;
import fr.heraliz.crates.item.CrateKeyItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Conversion des clés physiques (objets {@code heralizcrates:*_key}) en clés du compte ({@link ComptesCles}), 25/10.
 * <p>
 * Où : l'inventaire du joueur (36 cases, armure, main gauche), l'objet tenu à la souris, le coffre de l'Ender, le sac à
 * dos porté (son inventaire) et les sacs rangés dans l'inventaire (leur liste « Items ») ; à la connexion, toutes les
 * secondes, et à la fermeture de chaque conteneur (une clé sortie d'un coffre, d'une planque, du coffre du clan, d'un
 * cadavre... est convertie dès qu'elle arrive dans l'inventaire). La planque et le coffre VIP (HeraliZ Utils) sont
 * convertis à leur ouverture par leur propriétaire. Une clé physique qui apparaît plus tard (vieux butin, kit, bon...)
 * suit le même chemin. Une clé posée au sol ne peut être ramassée que par celui qui l'a jetée ou reçue.
 * <p>
 * Ordre sûr, sans copie ni perte même si le serveur s'arrête au milieu : (1) les objets sont retirés ET une note
 * « conversion n° k : tant de clés » est posée dans les données persistantes du joueur ({@code PlayerPersisted}) ;
 * (2) le joueur est sauvegardé tout de suite (inventaire sans les clés + note, dans le même fichier) ; (3) le compte est
 * crédité avec le numéro k ; (4) la note est retirée. Plantage avant (2) : rien n'a changé sur le disque, les clés sont
 * toujours là ; entre (2) et (3) : la note est rejouée à la connexion suivante ; après (3) : la note est retrouvée mais
 * le compte connaît déjà le numéro k, rien n'est compté deux fois.
 */
@Mod.EventBusSubscriber(modid = HeralizCrates.MODID)
public final class MigrationCles {
    private MigrationCles() {
    }

    /** Note posée dans {@code PlayerPersisted} pendant une conversion. */
    private static final String NOTE = "HeralizClesMigration";
    private static final String MENU_PLANQUE = "fr.heralisz.utils.coffres.CoffresPersonnels$MenuCoffre";
    private static final String COFFRE_PLANQUE = "fr.heralisz.utils.coffres.CoffresPersonnels$Coffre";
    private static int ticks;

    // =================================================================== évènements
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        convertir(p);
        ComptesCles.envoyer(p);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++ticks % 20 != 0) return;
        // une fois par seconde, sans rien créer tant qu'aucune clé n'est trouvée (cas de tous les joueurs, presque toujours)
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            if (aDesCles(p) || persistees(p).contains(NOTE, Tag.TAG_COMPOUND)) convertir(p);
        }
    }

    /** Vérification rapide : une clé physique quelque part sur le joueur ? */
    private static boolean aDesCles(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof CrateKeyItem) return true;
            if (!s.isEmpty() && s.hasTag() && s.getTag().contains("Items", Tag.TAG_LIST) && sacACle(s)) return true;
        }
        Container ender = p.getEnderChestInventory();
        for (int i = 0; i < ender.getContainerSize(); i++) if (ender.getItem(i).getItem() instanceof CrateKeyItem) return true;
        if (p.containerMenu.getCarried().getItem() instanceof CrateKeyItem) return true;
        Container sac = sacPorte(p);
        if (sac != null) {
            for (int i = 0; i < sac.getContainerSize(); i++) if (sac.getItem(i).getItem() instanceof CrateKeyItem) return true;
        }
        return false;
    }

    /** La liste « Items » d'un objet (sac à dos) contient-elle une clé ? */
    private static boolean sacACle(ItemStack sac) {
        ListTag l = sac.getTag().getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            String id = l.getCompound(i).getString("id");
            if (id.startsWith("heralizcrates:") && id.endsWith("_key")) return true;
        }
        return false;
    }

    /** Conteneur fermé : une clé sortie du conteneur est convertie tout de suite (avant tout autre conteneur). */
    @SubscribeEvent
    public static void fermeture(PlayerContainerEvent.Close e) {
        if (e.getEntity() instanceof ServerPlayer p) convertir(p);
    }

    /** Planque ou coffre VIP ouvert par son propriétaire : ses vieilles clés sont rangées dans son compte. */
    @SubscribeEvent
    public static void ouverture(PlayerContainerEvent.Open e) {
        if (e.getEntity() instanceof ServerPlayer p && MENU_PLANQUE.equals(e.getContainer().getClass().getName())) {
            planque(p, e.getContainer());
        }
    }

    /**
     * Clé physique posée au sol (jetée, ou donnée avec un inventaire plein) : seul celui qui l'a jetée (sinon le joueur le
     * plus proche, celui qui la recevait) peut la ramasser ; elle ne passe jamais à un autre joueur.
     */
    @SubscribeEvent
    public static void auSol(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || e.loadedFromDisk() || !(e.getEntity() instanceof ItemEntity ie)) return;
        if (!(ie.getItem().getItem() instanceof CrateKeyItem)) return;
        Entity lanceur = ie.getOwner();
        UUID cible = lanceur instanceof Player ? lanceur.getUUID() : null;
        if (cible == null) {
            Player proche = e.getLevel().getNearestPlayer(ie, 4);
            if (proche != null) cible = proche.getUUID();
        }
        if (cible != null) ie.setTarget(cible);
    }

    @SubscribeEvent
    public static void arret(ServerStoppedEvent e) {
        ComptesCles.vider();
    }

    // =================================================================== conversion
    @Nullable
    private static CrateType type(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof CrateKeyItem k ? k.type : null;
    }

    private static CompoundTag persistees(ServerPlayer p) {
        return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static void poserPersistees(ServerPlayer p, CompoundTag pers) {
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers);
    }

    /** Note d'une conversion restée en route (serveur arrêté au milieu) : appliquée une fois. Faux si impossible. */
    private static boolean resoudre(ServerPlayer p) {
        CompoundTag pers = persistees(p);
        if (!pers.contains(NOTE, Tag.TAG_COMPOUND)) return true;
        CompoundTag note = pers.getCompound(NOTE);
        Map<CrateType, Integer> cles = new EnumMap<>(CrateType.class);
        CompoundTag c = note.getCompound("cles");
        for (String k : c.getAllKeys()) {
            CrateType t = CrateType.byId(k);
            if (t != null && c.getInt(k) > 0) cles.merge(t, c.getInt(k), Integer::sum);
        }
        if (!ComptesCles.appliquerMigration(p.server, p.getUUID(), p.getGameProfile().getName(), note.getInt("n"), cles, "conversion")) {
            return false;
        }
        pers.remove(NOTE);
        poserPersistees(p, pers);
        return true;
    }

    /**
     * Cherche les clés physiques du joueur et les range dans son compte (ordre sûr : voir la classe). Ne fait rien (et ne
     * touche à rien) si le compte est fermé ou si une conversion précédente n'a pas pu être terminée.
     */
    public static void convertir(ServerPlayer p) {
        Map<CrateType, Integer> trouve = new EnumMap<>(CrateType.class);
        List<Runnable> retraits = new ArrayList<>();
        Inventory inv = p.getInventory();
        chercher(inv, trouve, retraits);
        chercher(p.getEnderChestInventory(), trouve, retraits);
        AbstractContainerMenu menu = p.containerMenu;
        ItemStack souris = menu.getCarried();
        CrateType ts = type(souris);
        if (ts != null) {
            trouve.merge(ts, souris.getCount(), Integer::sum);
            retraits.add(() -> menu.setCarried(ItemStack.EMPTY));
        }
        Container sac = sacPorte(p);
        if (sac != null) chercher(sac, trouve, retraits);
        // sacs rangés dans l'inventaire (pas celui qui est porté) : leur contenu est dans l'objet, liste « Items »
        if (menu == p.inventoryMenu) {
            ItemStack porte = sac == null ? ItemStack.EMPTY : pileDuSac(sac);
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s != porte && !s.isEmpty() && s.hasTag() && s.getTag().contains("Items", Tag.TAG_LIST) && estSac(s)) chercherDansSac(s, trouve, retraits);
            }
        }
        boolean note = persistees(p).contains(NOTE, Tag.TAG_COMPOUND);
        if (trouve.isEmpty() && !note) return;
        if (!ComptesCles.ouvert(p.getUUID()) || !resoudre(p)) return;
        if (trouve.isEmpty()) return;
        int numero = ComptesCles.migration(p.getUUID()) + 1;
        for (Runnable r : retraits) r.run();
        inv.setChanged();
        CompoundTag pers = persistees(p);
        CompoundTag n = new CompoundTag();
        n.putInt("n", numero);
        CompoundTag cles = new CompoundTag();
        trouve.forEach((t, c) -> cles.putInt(t.id, c));
        n.put("cles", cles);
        pers.put(NOTE, n);
        poserPersistees(p, pers);
        sauverJoueur(p);
        menu.broadcastFullState();
        int total = trouve.values().stream().mapToInt(Integer::intValue).sum();
        HeralizCrates.LOGGER.info("[HeralizCrates] {} : {} clé(s) physique(s) converties ({})", p.getGameProfile().getName(), total, trouve);
        // compte crédité ; sinon la note reste et sera rejouée (les clés ne sont ni perdues ni copiées)
        resoudre(p);
        dire(p, total);
    }

    private static void chercher(Container c, Map<CrateType, Integer> trouve, List<Runnable> retraits) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            CrateType t = type(s);
            if (t == null) continue;
            trouve.merge(t, s.getCount(), Integer::sum);
            final int slot = i;
            retraits.add(() -> c.setItem(slot, ItemStack.EMPTY));
        }
    }

    private static boolean estSac(ItemStack s) {
        var id = ForgeRegistries.ITEMS.getKey(s.getItem());
        return id != null && "backpacked".equals(id.getNamespace());
    }

    private static void chercherDansSac(ItemStack sac, Map<CrateType, Integer> trouve, List<Runnable> retraits) {
        ListTag l = sac.getTag().getList("Items", Tag.TAG_COMPOUND);
        List<Integer> aRetirer = new ArrayList<>();
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            String id = t.getString("id");
            if (!id.startsWith("heralizcrates:") || !id.endsWith("_key")) continue;
            ItemStack s = ItemStack.of(t);
            CrateType ty = type(s);
            if (ty == null) continue;
            trouve.merge(ty, s.getCount(), Integer::sum);
            aRetirer.add(i);
        }
        if (aRetirer.isEmpty()) return;
        retraits.add(() -> {
            ListTag liste = sac.getTag().getList("Items", Tag.TAG_COMPOUND);
            for (int k = aRetirer.size() - 1; k >= 0; k--) liste.remove((int) aRetirer.get(k));
            sac.getTag().put("Items", liste);
        });
    }

    // ------------------------------------------------------------------ sac porté (Backpacked, par réflexion)
    private static Method inventaireSac, pileSac;
    private static boolean sacCherche;

    /** Inventaire du sac porté (celui que Backpacked garde en mémoire et réécrit dans l'objet) ; null sans sac. */
    @Nullable
    private static Container sacPorte(ServerPlayer p) {
        if (!sacCherche) {
            sacCherche = true;
            try {
                inventaireSac = p.getClass().getMethod("backpacked$GetBackpackInventory");
            } catch (Throwable t) {
                inventaireSac = null;
            }
        }
        if (inventaireSac == null) return null;
        try {
            return inventaireSac.invoke(p) instanceof Container c ? c : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static ItemStack pileDuSac(Container sac) {
        try {
            if (pileSac == null) pileSac = sac.getClass().getMethod("getBackpackStack");
            return pileSac.invoke(sac) instanceof ItemStack s ? s : ItemStack.EMPTY;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    // ------------------------------------------------------------------ planque et coffre VIP (HeraliZ Utils)
    /**
     * Ouverture de sa planque (ou de son coffre VIP) : ses clés sont retirées, le fichier des coffres est écrit (dans
     * l'ordre, avant le compte), puis le compte est crédité ; si le compte refuse, les clés sont remises.
     */
    private static void planque(ServerPlayer p, AbstractContainerMenu menu) {
        Container coffre = null;
        for (Slot s : menu.slots) {
            if (COFFRE_PLANQUE.equals(s.container.getClass().getName())) {
                coffre = s.container;
                break;
            }
        }
        if (coffre == null) return;
        Map<CrateType, Integer> trouve = new EnumMap<>(CrateType.class);
        List<Integer> cases = new ArrayList<>();
        for (int i = 0; i < coffre.getContainerSize(); i++) {
            CrateType t = type(coffre.getItem(i));
            if (t == null) continue;
            trouve.merge(t, coffre.getItem(i).getCount(), Integer::sum);
            cases.add(i);
        }
        if (trouve.isEmpty() || !ComptesCles.ouvert(p.getUUID()) || !resoudre(p)) return;
        List<ItemStack> anciens = new ArrayList<>();
        for (int i : cases) {
            anciens.add(coffre.getItem(i));
            coffre.setItem(i, ItemStack.EMPTY);
        }
        coffre.setChanged();
        MinecraftServer srv = p.server;
        srv.overworld().getDataStorage().save();
        int numero = ComptesCles.migration(p.getUUID()) + 1;
        if (!ComptesCles.appliquerMigration(srv, p.getUUID(), p.getGameProfile().getName(), numero, trouve, "conversion planque")) {
            for (int k = 0; k < cases.size(); k++) coffre.setItem(cases.get(k), anciens.get(k));
            coffre.setChanged();
            srv.overworld().getDataStorage().save();
            return;
        }
        menu.broadcastChanges();
        int total = trouve.values().stream().mapToInt(Integer::intValue).sum();
        HeralizCrates.LOGGER.info("[HeralizCrates] {} : {} clé(s) de sa planque converties ({})", p.getGameProfile().getName(), total, trouve);
        dire(p, total);
    }

    private static void dire(ServerPlayer p, int total) {
        p.sendSystemMessage(Component.translatableWithFallback("message.heralizcrates.cles.converties",
                "🗝 %s clé(s) rangée(s) dans ton compte : elles ne prennent plus de place et ne s'échangent pas. Tes clés : /boutique, onglet « Mes clés ».",
                total).withStyle(net.minecraft.ChatFormatting.GOLD));
    }

    // ------------------------------------------------------------------ sauvegarde du joueur
    private static Method sauver;

    /** Sauvegarde tout de suite ce joueur seulement ({@code PlayerList.save}, protégée : par réflexion, comme /trade). */
    static void sauverJoueur(ServerPlayer p) {
        try {
            if (sauver == null) {
                sauver = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(net.minecraft.server.players.PlayerList.class,
                        "m_6765_", ServerPlayer.class);
            }
            sauver.invoke(p.server.getPlayerList(), p);
        } catch (Exception ex) {
            HeralizCrates.LOGGER.warn("[HeralizCrates] sauvegarde ciblée impossible, sauvegarde de tous les joueurs : {}", ex.toString());
            p.server.getPlayerList().saveAll();
        }
    }
}
